package com.astral.exchange;

import static com.astral.exchange.MarketMath.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MarketService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final SseHub hub;
    private final Random random = new Random();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<Integer> LEVERAGES = Set.of(1,5,10,20,50,100);
    private volatile Map<String,Object> cached = Map.of();
    private Clock clock = Clock.systemUTC();
    private boolean ready;
    @Value("${exchange.scheduler-enabled:true}") private boolean schedulerEnabled;
    public MarketService(JdbcTemplate db, TransactionTemplate tx, SseHub hub) {
        this.db = db; this.tx = tx; this.hub = hub;
    }
    static Map<String,Object> row(Map<String,Object> raw) {
        Map<String,Object> result = new LinkedHashMap<>();
        raw.forEach((k,v) -> result.put(k.toLowerCase(Locale.ROOT),v));
        return result;
    }
    List<Map<String,Object>> rows(String sql, Object... args) {
        return db.queryForList(sql,args).stream().map(MarketService::row).toList();
    }
    Map<String,Object> one(String sql, Object... args) {
        var list = rows(sql,args);
        if (list.isEmpty()) throw new IllegalArgumentException("记录不存在");
        return list.getFirst();
    }
    static BigDecimal n(Map<String,Object> row, String key) { return new BigDecimal(row.get(key).toString()); }
    long now() { return clock.millis(); }
    LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    void setClock(Clock clock) { this.clock = clock; }
    private synchronized <T> T write(Supplier<T> operation) {
        T result = tx.execute(status -> operation.get());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            cached = buildMarket();
            hub.publish(cached);
        }
        return result;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        write(() -> {
            if (rows("SELECT symbol FROM asset").isEmpty()) {
                String[][] seeds = {
                    {"NDB","北国银行","原神","金融 · 至冬","128"},
                    {"DWN","晨曦酒业","原神","消费 · 蒙德","86"},
                    {"LYS","璃月航运","原神","航运 · 璃月","216"},
                    {"FNT","枫丹发条工业","原神","工业 · 枫丹","168"},
                    {"IPC","星际和平公司","星穹铁道","金融 · 星际","328"},
                    {"HRT","黑塔科研","星穹铁道","科技 · 空间站","192"},
                    {"XNZ","仙舟物流","星穹铁道","物流 · 仙舟","112"},
                    {"PNC","匹诺康尼文旅","星穹铁道","文旅 · 匹诺康尼","76"}
                };
                for (String[] s : seeds) db.update("INSERT INTO asset(symbol,name,universe,sector,initial_price,base_reserve,quote_reserve,volume) VALUES(?,?,?,?,?,?,?,0)",s[0],s[1],s[2],s[3],d(s[4]),d("1000000"),d(s[4]).multiply(d("1000000")));
                db.update("INSERT INTO news VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),"IPC","星际交易所开市：八大同人虚构资产开启交易","NEUTRAL",now());
            }
            rollover(); recordAll(); return null;
        });
        ready = true;
    }
    public synchronized Map<String,Object> market() { return cached; }
    private Map<String,Object> buildMarket() {
        List<Map<String,Object>> assets = rows("SELECT * FROM asset ORDER BY symbol");
        assets.forEach(a -> {
            BigDecimal price = div(n(a,"quote_reserve"),n(a,"base_reserve"));
            a.put("price",price);
            a.put("change", div(price.subtract(n(a,"initial_price")),n(a,"initial_price")).multiply(d("100")));
        });
        return Map.of("assets",assets,"news",rows("SELECT * FROM news ORDER BY created_at DESC LIMIT 20"),
            "season",today().toString(),"serverTime",now(),"nextReset",today().plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli());
    }
    public synchronized <T> T register(Supplier<T> operation) { return write(() -> { rollover(); return operation.get(); }); }
    private Map<String,Object> asset(String symbol) { return one("SELECT * FROM asset WHERE symbol=?",symbol); }
    private BigDecimal mark(Map<String,Object> a) { return div(n(a,"quote_reserve"),n(a,"base_reserve")); }
    public synchronized List<Map<String,Object>> candles(String symbol) {
        asset(symbol);
        List<Map<String,Object>> candles = rows("SELECT minute_at AS time,open_price AS open, high_price AS high, low_price AS low,close_price AS close,volume FROM candle WHERE symbol=? ORDER BY minute_at DESC LIMIT 360",symbol);
        return candles.reversed();
    }
    public synchronized Map<String,Object> account(long user) {
        var account = one("SELECT a.*,u.username FROM account a JOIN app_user u ON u.id=a.user_id WHERE user_id=?",user);
        var positions = rows("SELECT p.*,a.name FROM position_entry p JOIN asset a ON a.symbol=p.symbol WHERE user_id=? ORDER BY opened_at DESC",user);
        BigDecimal equity = n(account,"balance");
        for (var p : positions) {
            BigDecimal price = mark(asset(p.get("symbol").toString()));
            BigDecimal pnl = pnl(p.get("side").toString(),n(p,"quantity"),n(p,"entry_price"),price);
            p.put("pnl",pnl); p.put("mark_price",price);
            p.put("liquidation_price",liquidation(p.get("side").toString(),n(p,"quantity"),n(p,"entry_price"),n(p,"margin")));
            equity = equity.add(n(p,"margin").add(pnl).max(ZERO));
        }
        account.put("positions",positions); account.put("equity",equity);
        account.put("return_pct",div(equity.subtract(CAPITAL),CAPITAL).multiply(d("100")));
        account.put("bankrupt",positions.isEmpty() && n(account,"balance").compareTo(d("100"))<0);
        account.put("trades",rows("SELECT * FROM trade_record WHERE user_id=? ORDER BY created_at DESC LIMIT 60",user));
        return account;
    }
    public synchronized Map<String,Object> ranking(String day) {
        if (day == null || day.equals(today().toString())) {
            List<Map<String,Object>> list = new ArrayList<>();
            for (var user : rows("SELECT id FROM app_user")) {
                var a = account(((Number) user.get("id")).longValue());
                list.add(Map.of("username",a.get("username"),"equity",a.get("equity"),"bankrupt",a.get("bankrupt")));
            }
            list.sort((a,b) -> n(b,"equity").compareTo(n(a,"equity")));
            return Map.of("entries",list.stream().limit(100).toList(),"days",rows("SELECT DISTINCT season_day FROM daily_ranking ORDER BY season_day DESC LIMIT 30"));
        }
        LocalDate parsed = LocalDate.parse(day);
        return Map.of("entries",rows("SELECT username,equity FROM daily_ranking WHERE season_day=? ORDER BY equity DESC LIMIT 100",parsed),"days",rows("SELECT DISTINCT season_day FROM daily_ranking ORDER BY season_day DESC LIMIT 30"));
    }
    public record OpenRequest(String requestId, String symbol, String side, BigDecimal margin, int leverage) {}
    private void validate(OpenRequest r) {
        requestId(r.requestId());
        if (!Set.of("LONG","SHORT").contains(r.side()==null ? "" : r.side()) || !LEVERAGES.contains(r.leverage())) throw new IllegalArgumentException("请选择有效方向与杠杆");
        if (r.margin()==null || r.margin().scale()>2 || r.margin().compareTo(d("100"))<0 || r.margin().compareTo(CAPITAL.multiply(d("1000")))>0) throw new IllegalArgumentException("保证金至少 100，最多两位小数");
    }
    private void requestId(String id) {
        try { UUID.fromString(id); } catch (Exception e) { throw new IllegalArgumentException("请求编号必须为 UUID"); }
    }
    public synchronized Map<String,Object> preview(OpenRequest r) {
        validate(r);
        var a = asset(r.symbol());
        var f = MarketMath.open(n(a,"base_reserve"),n(a,"quote_reserve"),r.margin().multiply(BigDecimal.valueOf(r.leverage())),r.side().equals("LONG"));
        checkLiquidity(r.symbol(),f,r.side().equals("SHORT")?f.quantity():ZERO);
        return Map.of("price",f.price(),"notional",f.notional(),"fee",f.notional().multiply(FEE),"quantity",f.quantity(),"liquidationPrice",liquidation(r.side(),f.quantity(),f.price(),r.margin()),"total",r.margin().add(f.notional().multiply(FEE)));
    }
    private String receipt(long user, String id, String fingerprint) {
        var receipts = rows("SELECT * FROM command_receipt WHERE user_id=? AND request_id=?",user,id);
        if (receipts.isEmpty()) return null;
        if (!fingerprint.equals(receipts.getFirst().get("fingerprint"))) throw new IllegalArgumentException("请求编号已用于其他交易");
        return receipts.getFirst().get("result_id").toString();
    }
    public Map<String,Object> open(long user, OpenRequest r) {
        validate(r);
        String fingerprint = "OPEN:"+r.symbol()+":"+r.side()+":"+r.margin().stripTrailingZeros().toPlainString()+":"+r.leverage();
        return write(() -> {
            rollover();
            String prior = receipt(user,r.requestId(),fingerprint);
            if (prior != null) return Map.of("id",prior,"duplicate",true);
            var a = asset(r.symbol());
            var f = MarketMath.open(n(a,"base_reserve"),n(a,"quote_reserve"),r.margin().multiply(BigDecimal.valueOf(r.leverage())),r.side().equals("LONG"));
            checkLiquidity(r.symbol(),f,r.side().equals("SHORT")?f.quantity():ZERO);
            BigDecimal cost = r.margin().add(f.notional().multiply(FEE));
            if (n(one("SELECT * FROM account WHERE user_id=?",user),"balance").compareTo(cost)<0) throw new IllegalArgumentException("可用资金不足（含手续费）");
            db.update("UPDATE account SET balance=balance-? WHERE user_id=?",cost,user);
            String id = UUID.randomUUID().toString();
            db.update("INSERT INTO position_entry VALUES(?,?,?,?,?,?,?,?,?)",id,user,r.symbol(),r.side(),r.margin(),r.leverage(),f.quantity(),f.price(),now());
            applyFill(r.symbol(),f);
            recordTrade(id,user,r.symbol(),"OPEN",r.side(),f,ZERO);
            db.update("INSERT INTO command_receipt VALUES(?,?,?,?)",user,r.requestId(),fingerprint,id);
            liquidate();
            return Map.of("id",id,"duplicate",false);
        });
    }
    public Map<String,Object> close(long user, String position, String request) {
        requestId(request);
        return write(() -> {
            rollover();
            String fingerprint = "CLOSE:"+position;
            String prior = receipt(user,request,fingerprint);
            if (prior!=null) return Map.of("id",prior,"duplicate",true);
            var p = one("SELECT * FROM position_entry WHERE id=? AND user_id=?",position,user);
            String id = exit(p,"CLOSE",null);
            db.update("INSERT INTO command_receipt VALUES(?,?,?,?)",user,request,fingerprint,id);
            liquidate();
            return Map.of("id",id,"duplicate",false);
        });
    }
    private void applyFill(String symbol, Fill f) {
        db.update("UPDATE asset SET base_reserve=?,quote_reserve=?,volume=volume+? WHERE symbol=?",f.base(),f.quote(),f.notional(),symbol);
        record(symbol,f.notional());
    }
    private void checkLiquidity(String symbol, Fill f, BigDecimal additionalShort) {
        BigDecimal shorts=db.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM position_entry WHERE symbol=? AND side='SHORT'",BigDecimal.class,symbol);
        if (shorts.add(additionalShort).compareTo(f.base().multiply(d("0.25")))>0)
            throw new IllegalArgumentException("市场空头仓位接近流动性上限，请减小交易规模");
    }
    private void recordTrade(String id, long user, String symbol, String action, String side, Fill f, BigDecimal pnl) {
        db.update("INSERT INTO trade_record VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,user,currentDay(),symbol,action,side,f.quantity(),f.price(),f.notional().multiply(FEE),pnl,now());
    }
    private String exit(Map<String,Object> p, String action, BigDecimal settlementPrice) {
        String symbol=p.get("symbol").toString(),side=p.get("side").toString();
        var a = asset(symbol);
        Fill f;
        if (settlementPrice==null) {
            f = MarketMath.close(n(a,"base_reserve"),n(a,"quote_reserve"),n(p,"quantity"),side.equals("SHORT"));
            applyFill(symbol,f);
        } else {
            f = new Fill(n(a,"base_reserve"),n(a,"quote_reserve"),n(p,"quantity"),n(p,"quantity").multiply(settlementPrice),settlementPrice);
        }
        BigDecimal profit = pnl(side,n(p,"quantity"),n(p,"entry_price"),f.price());
        BigDecimal payout = n(p,"margin").add(profit).subtract(f.notional().multiply(FEE)).max(ZERO);
        long user = ((Number)p.get("user_id")).longValue();
        db.update("UPDATE account SET balance=balance+? WHERE user_id=?",payout,user);
        db.update("DELETE FROM position_entry WHERE id=?",p.get("id"));
        String id = UUID.randomUUID().toString();
        recordTrade(id,user,symbol,action,side,f,payout.subtract(n(p,"margin")));
        return id;
    }
    private void liquidate() {
        // Each pass removes a position, so a cascade terminates even when a close moves another asset price.
        boolean removed;
        do {
            removed=false;
            for (var p : rows("SELECT * FROM position_entry ORDER BY opened_at,id")) {
                BigDecimal price=mark(asset(p.get("symbol").toString()));
                BigDecimal value=n(p,"margin").add(pnl(p.get("side").toString(),n(p,"quantity"),n(p,"entry_price"),price));
                if (value.compareTo(n(p,"quantity").multiply(price).multiply(MAINTENANCE))<=0) {
                    exit(p,"LIQUIDATION",null); removed=true; break;
                }
            }
        } while (removed);
    }
    private LocalDate currentDay() {
        return db.queryForObject("SELECT MAX(season_day) FROM season",LocalDate.class);
    }
    private void rollover() {
        LocalDate current=currentDay();
        if (current==null) { db.update("INSERT INTO season VALUES(?,FALSE)",today()); return; }
        if (!current.isBefore(today())) return;
        // Settle all at the same pre-reset mark without moving the pool or disadvantaging later exits.
        for (var p : rows("SELECT * FROM position_entry ORDER BY id")) exit(p,"SETTLEMENT",mark(asset(p.get("symbol").toString())));
        db.update("INSERT INTO daily_ranking(season_day,user_id,username,equity) SELECT ?,u.id,u.username,a.balance FROM app_user u JOIN account a ON a.user_id=u.id",current);
        db.update("UPDATE season SET settled=TRUE WHERE season_day=?",current);
        db.update("UPDATE account SET balance=?",CAPITAL);
        db.update("UPDATE asset SET base_reserve=1000000,quote_reserve=initial_price*1000000,volume=0");
        db.update("INSERT INTO season VALUES(?,FALSE)",today());
        recordAll();
    }
    private void record(String symbol, BigDecimal volume) {
        long minute=now()/60000*60;
        BigDecimal price=mark(asset(symbol));
        var existing=rows("SELECT minute_at FROM candle WHERE symbol=? AND minute_at=?",symbol,minute);
        if (existing.isEmpty()) db.update("INSERT INTO candle VALUES(?,?,?,?,?,?,?)",symbol,minute,price,price,price,price,volume);
        else db.update("UPDATE candle SET high_price=GREATEST(high_price,?),low_price=LEAST(low_price,?),close_price=?,volume=volume+? WHERE symbol=? AND minute_at=?",price,price,price,volume,symbol,minute);
    }
    private void recordAll() { for (var a:rows("SELECT symbol FROM asset")) record(a.get("symbol").toString(),ZERO); }
    @Scheduled(fixedDelay=2000)
    public void scheduledTick() { if (ready && schedulerEnabled) tick(); }
    void tick() {
        write(() -> {
            rollover();
            var assets=rows("SELECT * FROM asset ORDER BY symbol");
            for (var a:assets) {
                double delta=(random.nextDouble()-.5)*.001;
                BigDecimal target=mark(a).multiply(BigDecimal.valueOf(1+delta));
                reprice(a,target); record(a.get("symbol").toString(),ZERO);
            }
            Long lastNews=db.queryForObject("SELECT MAX(created_at) FROM news",Long.class);
            if (lastNews==null || now()-lastNews>60000) {
                var a=assets.get(random.nextInt(assets.size()));
                boolean positive=random.nextBoolean();
                String title=a.get("name")+(positive ? "：新航线与合作订单获批，市场情绪升温" : "：项目延期与运力检修，市场情绪承压");
                db.update("INSERT INTO news VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),a.get("symbol"),title,positive?"BULLISH":"BEARISH",now());
                var fresh=asset(a.get("symbol").toString());
                reprice(fresh,mark(fresh).multiply(d(positive?"1.006":"0.994")));
                record(a.get("symbol").toString(),ZERO);
            }
            liquidate(); return null;
        });
    }
    private void reprice(Map<String,Object> a, BigDecimal target) {
        // Virtual liquidity injection/removal preserves deep base liquidity for leveraged short exits.
        db.update("UPDATE asset SET quote_reserve=? WHERE symbol=?",n(a,"base_reserve").multiply(target),a.get("symbol"));
    }
}
