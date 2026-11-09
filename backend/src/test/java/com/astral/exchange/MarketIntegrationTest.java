package com.astral.exchange;

import static com.astral.exchange.MarketMath.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketIntegrationTest {
    @Autowired MarketService market;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @BeforeEach void reset() {
        market.setClock(Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"),ZoneOffset.UTC));
        for(String table:List.of("command_receipt","trade_record","position_entry","daily_ranking","account","app_user","candle","news","asset","season")) db.update("DELETE FROM "+table);
        market.initialize();
        for(int i=1;i<=2;i++) { db.update("INSERT INTO app_user(id,username,password_hash,created_at) VALUES(?,?,?,?)",i,"player"+i,"unused",0);db.update("INSERT INTO account VALUES(?,?)",i,CAPITAL); }
    }
    private MarketService.OpenRequest order(String side,int leverage,String margin) { return new MarketService.OpenRequest(UUID.randomUUID().toString(),"IPC",side,d(margin),leverage); }
    private BigDecimal balance(int user) { return db.queryForObject("SELECT balance FROM account WHERE user_id=?",BigDecimal.class,user); }
    private BigDecimal price() {return db.queryForObject("SELECT quote_reserve/base_reserve FROM asset WHERE symbol='IPC'",BigDecimal.class);}
    @Test void bothSidesExecuteAndPushSharedPriceWithFees() {
        BigDecimal start=price();
        var buy=order("LONG",5,"1000");
        market.open(1,buy);assertThat(price()).isGreaterThan(start);assertThat(balance(1)).isEqualByComparingTo("98997.5");
        assertThat(((List<?>)market.account(1).get("positions"))).hasSize(1);
        BigDecimal beforeShort=price();market.open(2,order("SHORT",5,"1000"));assertThat(price()).isLessThan(beforeShort);
        var p=market.rows("SELECT * FROM position_entry WHERE user_id=1").getFirst();
        market.close(1,p.get("id").toString(),UUID.randomUUID().toString());
        assertThat(market.rows("SELECT * FROM position_entry WHERE user_id=1")).isEmpty();
        assertThat(balance(1)).isLessThan(CAPITAL).isGreaterThan(d("99900"));
    }
    @Test void rejectsInsufficientFundsAndInvalidOrdersWithoutSideEffects() {
        var before=price();
        assertThatThrownBy(()->market.open(1,order("LONG",1,"100000"))).hasMessageContaining("不足");
        assertThatThrownBy(()->market.open(1,order("LONG",3,"1000"))).hasMessageContaining("杠杆");
        assertThatThrownBy(()->market.open(1,order("LONG",1,"99"))).hasMessageContaining("100");
        assertThat(price()).isEqualByComparingTo(before);assertThat(balance(1)).isEqualByComparingTo(CAPITAL);
        assertThat(market.rows("SELECT * FROM trade_record")).isEmpty();
    }
    @Test void duplicateAndConcurrentCommandsExecuteExactlyOnce() throws Exception {
        var request=order("LONG",5,"1000");
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<8;i++)futures.add(executor.submit(()->market.open(1,request)));
            for(var f:futures)f.get(10,TimeUnit.SECONDS);
        }
        assertThat(market.rows("SELECT * FROM trade_record")).hasSize(1);
        assertThat(balance(1)).isEqualByComparingTo("98997.5");
        assertThatThrownBy(()->market.open(1,new MarketService.OpenRequest(request.requestId(),"IPC","SHORT",d("1000"),5))).hasMessageContaining("其他交易");
    }
    @Test void transactionFailureRollsBackAccountAndPosition() {
        var snapshot=market.market();
        assertThatThrownBy(()->market.register(()-> {
            market.open(1,order("LONG",5,"1000"));
            throw new IllegalStateException("simulated storage failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(balance(1)).isEqualByComparingTo(CAPITAL);
        assertThat(market.rows("SELECT * FROM position_entry")).isEmpty();
        assertThat(market.rows("SELECT * FROM trade_record")).isEmpty();
        assertThat(market.market()).isEqualTo(snapshot);
    }
    @Test void highLeverageLiquidationProtectsOtherMarginAndNeverCreatesDebt() {
        market.open(1,order("LONG",1,"1000"));market.open(1,order("LONG",100,"1000"));
        BigDecimal available=balance(1);
        db.update("UPDATE asset SET quote_reserve=quote_reserve*0.97 WHERE symbol='IPC'");market.tick();
        assertThat(market.rows("SELECT * FROM position_entry WHERE user_id=1")).hasSize(1);
        assertThat(market.rows("SELECT * FROM trade_record WHERE action='LIQUIDATION'")).hasSize(1);
        assertThat(balance(1)).isGreaterThanOrEqualTo(available).isGreaterThanOrEqualTo(ZERO);
        assertThat(market.rows("SELECT * FROM position_entry WHERE leverage=1")).hasSize(1);
    }
    @Test void shortLiquidationAndBankruptcy() {
        db.update("UPDATE account SET balance=1050 WHERE user_id=1");
        market.open(1,order("SHORT",100,"1000"));
        db.update("UPDATE asset SET quote_reserve=quote_reserve*1.03 WHERE symbol='IPC'");market.tick();
        assertThat(market.rows("SELECT * FROM position_entry WHERE user_id=1")).isEmpty();
        assertThat(market.account(1).get("bankrupt")).isEqualTo(true);
        assertThat(balance(1)).isGreaterThanOrEqualTo(ZERO);
        assertThatThrownBy(()->market.open(1,order("LONG",1,"100"))).hasMessageContaining("不足");
    }
    @Test void dailySettlementAndRestartAreIdempotent() {
        market.open(1,order("LONG",5,"1000"));
        market.setClock(Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"),ZoneOffset.UTC));market.initialize();
        assertThat(market.rows("SELECT * FROM daily_ranking")).hasSize(2);
        assertThat(market.rows("SELECT * FROM position_entry")).isEmpty();
        assertThat(balance(1)).isEqualByComparingTo(CAPITAL);
        assertThat(market.rows("SELECT * FROM trade_record WHERE action='SETTLEMENT'")).hasSize(1);
        market.initialize();assertThat(market.rows("SELECT * FROM daily_ranking")).hasSize(2);
        market.open(1,order("SHORT",5,"1000"));BigDecimal before=balance(1);market.initialize();assertThat(balance(1)).isEqualByComparingTo(before);
    }
    @Test void registrationSessionAndPermissions() throws Exception {
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"traveler\",\"password\":\"test-password-123\"}")).andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"traveler\",\"password\":\"test-password-123\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100000)).andReturn();
        var sessionCookie=result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        mvc.perform(get("/api/account").cookie(sessionCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("traveler"));
        String hash=db.queryForObject("SELECT password_hash FROM app_user WHERE username='traveler'",String.class);
        assertThat(hash).startsWith("$2").doesNotContain("test-password");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"traveler\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized());
    }
    @Test void cannotCloseAnotherPlayersPositionAndCannotOverdrawPool() {
        var r=market.open(1,order("LONG",5,"1000"));
        assertThatThrownBy(()->market.close(2,r.get("id").toString(),UUID.randomUUID().toString())).hasMessageContaining("不存在");
        assertThatThrownBy(()->MarketMath.open(d("1000"),d("1000"),d("20"),true)).hasMessageContaining("1%");
    }
    @Test void closeIsIdempotentAndShortRoundTripChargesBothFees() {
        var opened=market.open(1,order("SHORT",5,"1000"));
        String request=UUID.randomUUID().toString();
        var close=market.close(1,opened.get("id").toString(),request);
        var duplicate=market.close(1,opened.get("id").toString(),request);
        assertThat(duplicate.get("id")).isEqualTo(close.get("id"));
        assertThat(duplicate.get("duplicate")).isEqualTo(true);
        assertThat(balance(1)).isBetween(d("99994.99"),d("99995.01"));
        assertThat(market.rows("SELECT * FROM trade_record WHERE user_id=1")).hasSize(2);
    }
    @Test void independentConcurrentOrdersPreserveBalancesAndTradeCount() throws Exception {
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<8;i++) {
                final int user=i%2+1;
                futures.add(executor.submit(()->market.open(user,order("LONG",5,"1000"))));
            }
            for(var f:futures)f.get(10,TimeUnit.SECONDS);
        }
        assertThat(market.rows("SELECT * FROM position_entry")).hasSize(8);
        assertThat(market.rows("SELECT * FROM trade_record")).hasSize(8);
        assertThat(balance(1)).isEqualByComparingTo("95990");
        assertThat(balance(2)).isEqualByComparingTo("95990");
    }
}
