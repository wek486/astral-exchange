<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { Orbit,ArrowUpRight,ArrowDownRight,ChevronDown,Radio,Wallet,ChartNoAxesCombined,Trophy,Compass,ArrowRight,Plus,LogOut,Info,X,Newspaper,ShieldCheck,Clock3,Activity,ExternalLink } from 'lucide-vue-next'
import { api,useExchange,type Asset,type Candle } from './store'
import PriceChart from './components/PriceChart.vue'
const store=useExchange(),route=useRoute()
const selected=ref('IPC'), universe=ref('全部'), side=ref('LONG'), leverage=ref(5), margin=ref<number>(1000)
const busy=ref(false), notice=ref(''), orderError=ref(''), authError=ref(''), authMode=ref('register'),username=ref(''),password=ref('')
const tutorial=ref(!localStorage.getItem('astral-onboarded')), now=ref(Date.now()), ranking=ref<any[]>([]), days=ref<any[]>([]), rankDay=ref('')
const quote=ref<any>(null),quoteBusy=ref(false)
let quoteVersion=0,quoteTimer:ReturnType<typeof setTimeout>,timer:ReturnType<typeof setInterval>,toastTimer:ReturnType<typeof setTimeout>
let pendingOrder:{key:string;id:string}|undefined
let rankingVersion=0,lastRanking=0
const asset=computed(()=>store.market?.assets.find(a=>a.symbol===selected.value))
const filtered=computed(()=>store.market?.assets.filter(a=>universe.value==='全部'||a.universe===universe.value) || [])
const isMarket=computed(()=>route.path==='/')
const changeClass=(n:number)=>Number(n)>=0?'up':'down'
const money=(n:unknown,digits=2)=>Number(n??0).toLocaleString('zh-CN',{minimumFractionDigits:digits,maximumFractionDigits:digits})
const signed=(n:unknown)=>`${Number(n)>=0?'+':''}${money(n)}`
const short=(n:number)=>n>=1e8?(n/1e8).toFixed(2)+' 亿':n>=1e4?(n/1e4).toFixed(2)+' 万':money(n)
const countdown=computed(()=>{const seconds=Math.max(0,Math.floor(((store.market?.nextReset||now.value)-now.value)/1000));return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':')})
const totalVolume=computed(()=>store.market?.assets.reduce((sum,a)=>sum+Number(a.volume),0)||0)
const averageChange=computed(()=>(store.market?.assets.reduce((sum,a)=>sum+Number(a.change),0)??0)/(store.market?.assets.length||1))
const connection=computed(()=>store.connected?'市场实时连接':'正在重新连接')
function toast(message:string) {notice.value=message;clearTimeout(toastTimer);toastTimer=setTimeout(()=>notice.value='',5000)}
function onboardDone(){tutorial.value=false;localStorage.setItem('astral-onboarded','1')}
function payload():{requestId:string;symbol:string;side:string;margin:number;leverage:number}{return {requestId:crypto.randomUUID(),symbol:selected.value,side:side.value,margin:Number(margin.value),leverage:leverage.value}}
async function refreshQuote(){
  const version=++quoteVersion; quote.value=null
  if(!store.account || Number(margin.value)<100 || !Number.isFinite(Number(margin.value))) return
  quoteBusy.value=true
  try {const q=await api('/orders/preview','POST',payload());if(version===quoteVersion){quote.value=q;orderError.value=''}}
  catch(e:any){if(version===quoteVersion) orderError.value=e.message}
  finally{if(version===quoteVersion)quoteBusy.value=false}
}
watch([selected,side,leverage,margin,()=>!!store.account],()=>{quoteVersion++;quote.value=null;clearTimeout(quoteTimer);quoteTimer=setTimeout(refreshQuote,200)})
watch(()=>store.market?.serverTime,()=>{if(store.account&&!quoteBusy.value)void refreshQuote()})
async function submit(){
  if(!store.account){store.authOpen=true;return}
  if(busy.value||!quote.value) return
  busy.value=true;orderError.value=''
  const data=payload(),key=JSON.stringify({...data,requestId:''})
  if(pendingOrder?.key!==key)pendingOrder={key,id:data.requestId}
  data.requestId=pendingOrder.id
  try {await api('/orders','POST',data);pendingOrder=undefined;await store.refreshAccount();toast('委托已成交，请在下方查看持仓');void refreshQuote()}
  catch(e:any){orderError.value=e.message;if(e.status)pendingOrder=undefined}
  finally{busy.value=false}
}
const closeRequests=new Map<string,string>()
async function closePosition(id:string){
  if(busy.value)return
  busy.value=true
  if(!closeRequests.has(id))closeRequests.set(id,crypto.randomUUID())
  try{await api('/positions/'+id+'/close','POST',{requestId:closeRequests.get(id)});closeRequests.delete(id);await store.refreshAccount();toast('持仓已全部平仓')}
  catch(e:any){toast(e.message);if(e.status)closeRequests.delete(id)}finally{busy.value=false}
}
async function auth(){
  if(busy.value)return
  busy.value=true;authError.value=''
  try {store.account=await api('/auth/'+authMode.value,'POST',{username:username.value,password:password.value});store.authOpen=false;password.value='';toast(authMode.value==='register'?'账户已创建，100,000 信用点已到账':'欢迎返回交易所');void refreshQuote()}
  catch(e:any){authError.value=e.message}finally{busy.value=false}
}
async function logout(){try{await store.logout();toast('已退出登录')}catch(e:any){toast(e.message)}}
async function loadRanking(){const version=++rankingVersion;lastRanking=Date.now();try{const r=await api('/ranking'+(rankDay.value?'?day='+rankDay.value:''));if(version===rankingVersion){ranking.value=r.entries;days.value=r.days}}catch(e:any){toast(e.message)}}
watch([()=>route.path,rankDay],()=>{if(route.path==='/ranking')void loadRanking()})
onMounted(()=>{void store.start();timer=setInterval(()=>{now.value=Date.now();if(route.path==='/ranking'&&!rankDay.value&&Date.now()-lastRanking>=5000)void loadRanking()},1000);if(route.path==='/ranking')void loadRanking()})
onBeforeUnmount(()=>{store.stop();clearInterval(timer);clearTimeout(quoteTimer);clearTimeout(toastTimer)})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink to="/" class="brand"><span class="brand-icon"><Orbit :size="27"/></span><span>星际交易所<small>ASTRAL EXCHANGE</small></span><span class="beta">模拟</span></RouterLink>
      <nav><RouterLink to="/"><ChartNoAxesCombined :size="16"/>交易市场</RouterLink><RouterLink to="/assets"><Wallet :size="16"/>我的资产</RouterLink><RouterLink to="/ranking"><Trophy :size="16"/>赛季排行</RouterLink></nav>
      <div class="header-right"><span class="live-label"><i :class="{offline:!store.connected}"/>{{store.connected?'交易进行中':'连接中'}}</span><template v-if="store.account"><span class="user-avatar">{{store.account.username.slice(0,1)}}</span><span class="user-name">{{store.account.username}}</span><button class="icon-button" aria-label="退出登录" @click="logout"><LogOut :size="16"/></button></template><button v-else class="primary small" @click="store.authOpen=true">登录 / 开始交易 <ArrowRight :size="14"/></button></div>
    </header>
    <div class="disclaimer"><ShieldCheck :size="13"/><span>同人虚构市场 · 纯模拟交易 · 无充值提现</span><span class="disclaimer-right">以好奇入场，带着经验离开。</span></div>
    <main>
      <section class="page-heading"><div><div class="eyebrow">TEYVAT × ASTRAL EXPRESS <span> / {{store.market?.season || '连接市场中'}}</span></div><h1>{{isMarket?'在星海之间，发现下一次机会。':route.path==='/assets'?'每一笔选择，都有迹可循。':'让成绩，成为你的航行坐标。'}}</h1><p>{{isMarket?'提瓦特的商路，银河间的资本。你的第一笔星际投资，从这里开始。':route.path==='/assets'?'查看你的信用点、持仓与成交记录。所有收益与风险，尽在掌握。':'与所有开拓者共用一个市场，每日零点重新出发。'}}</p></div><div class="season-clock"><Clock3 :size="18"/><div><span>距离本日赛季结算 · 北京时间</span><strong>{{countdown}}</strong></div><span class="season-tag">每日赛季</span></div></section>
      <div v-if="store.error" class="error-banner" role="alert">{{store.error}}</div>
      <RouterLink v-if="isMarket" to="/challenge" class="challenge-link"><span><strong>限时剧本 · AI 股「空中飞人」</strong><small>致敬《FX 战士久留美》，试试你的仓位能撑过几根 K 线。</small></span><span>免登录试玩 →</span></RouterLink>
      <section class="stats-grid"><div class="stat-card"><span><Activity :size="15"/>市场平均涨幅</span><strong :class="changeClass(averageChange)">{{signed(averageChange)}}<small>%</small></strong><em>八大资产 · 全市场共享</em></div><div class="stat-card"><span><ChartNoAxesCombined :size="15"/>本日成交规模</span><strong>{{short(totalVolume)}}<small> Cr.</small></strong><em>玩家交易推动市场价格</em></div><div class="stat-card"><span><Wallet :size="15"/>{{store.account?'我的账户净值':'新手初始信用点'}}</span><strong>{{money(store.account?.equity??100000)}}<small> Cr.</small></strong><em v-if="store.account" :class="changeClass(store.account.return_pct)">本日收益 {{signed(store.account.return_pct)}}%</em><em v-else>注册即领 · 每日重置</em></div><div class="stat-card accent-stat"><span><Compass :size="15"/>跨越两个世界</span><strong>08<small> 支虚构资产</small></strong><em>原神 × 崩坏：星穹铁道</em><Orbit class="stat-orbit" :size="85"/></div></section>

      <template v-if="isMarket">
        <div class="trading-grid">
          <aside class="panel market-panel"><div class="panel-title"><h2>交易市场</h2><span class="muted micro">MARKETS</span></div><div class="segmented universe-filter"><button v-for="u in ['全部','原神','星穹铁道']" :key="u" :class="{active:universe===u}" @click="universe=u">{{u}}</button></div><div class="market-columns"><span>资产 / 产业</span><span>价格 / 涨跌</span></div><button v-for="(a,index) in filtered" :key="a.symbol" class="asset-row" :class="{selected:selected===a.symbol}" @click="selected=a.symbol"><span class="asset-emblem" :class="a.universe==='原神'?'teyvat':'astral'">{{a.symbol.slice(0,1)}}<span class="emblem-dot"/></span><span class="asset-identity"><b>{{a.name}}</b><small>{{a.symbol}} <span>· {{a.universe}}</span></small></span><span class="asset-price"><b>{{money(a.price)}}</b><small :class="changeClass(a.change)">{{signed(a.change)}}%</small></span></button><div v-if="!filtered.length" class="empty compact">正在等待市场数据…</div><div class="market-foot"><i class="tiny-dot"/>所有玩家共享实时行情</div></aside>

          <section class="panel chart-panel"><div class="chart-header"><div class="instrument"><span class="large-emblem">{{selected.slice(0,1)}}</span><div><h2>{{asset?.name || '正在连接'}} <span class="instrument-symbol">{{selected}}</span></h2><p>{{asset?.sector || '共享公共市场'}} <span class="fiction-tag">同人虚构</span></p></div></div><div class="instrument-price"><strong :class="changeClass(asset?.change||0)">{{money(asset?.price)}}</strong><span :class="changeClass(asset?.change||0)">{{signed(asset?.change||0)}}%</span></div></div><div class="chart-tools"><span class="timeframe active">1 分钟</span><span class="muted micro">实时 K 线</span><span class="chart-volume">本日成交 <b>{{short(asset?.volume||0)}}</b></span></div><PriceChart v-if="asset && store.market" :key="selected" :symbol="selected" :price="Number(asset.price)" :server-time="store.market.serverTime"/><div v-else class="empty chart-placeholder">行情连接中…</div><div class="chart-footer"><span><i :class="['tiny-dot',{offline:!store.connected}]"/>{{connection}}</span><span>行情为虚构模拟 · 分钟聚合</span></div></section>

          <aside class="panel order-panel"><div class="panel-title"><h2>下单交易</h2><span class="credit-tag">信用点</span></div><div class="direction-switch"><button :class="{active:side==='LONG'}" @click="side='LONG'"><ArrowUpRight :size="17"/>做多</button><button :class="{active:side==='SHORT'}" @click="side='SHORT'"><ArrowDownRight :size="17"/>做空</button></div><div class="available"><span>可用资金</span><b>{{store.account?money(store.account.balance):'—'}} <small>Cr.</small></b></div><label class="field-label" for="margin">投入保证金 <span>逐仓模式</span></label><div class="amount-field"><input id="margin" v-model.number="margin" type="number" min="100" step="100" inputmode="decimal"><span>Cr.</span></div><div class="quick-amount"><button v-for="pct in [10,25,50,75]" :key="pct" @click="margin=store.account?Math.max(100,Math.floor(Number(store.account.balance)*pct/100/(1+leverage*.0005))):pct*100">{{pct}}%</button></div><label class="field-label">交易杠杆 <b :class="{'high-leverage':leverage>=50}">{{leverage}}×</b></label><div class="leverage-options"><button v-for="l in [1,5,10,20,50,100]" :key="l" :class="{active:leverage===l}" @click="leverage=l">{{l}}×</button></div><div class="quote-details"><div><span>名义仓位</span><b>{{money(Number(margin)*leverage)}} Cr.</b></div><div><span>预计成交均价</span><b>{{quote?money(quote.price,4):'—'}}</b></div><div><span>手续费 · 0.05%</span><b>{{quote?money(quote.fee):'—'}} Cr.</b></div><div class="liq-line"><span><Info :size="12"/>预计强平价格</span><b>{{quote?money(quote.liquidationPrice,4):'—'}}</b></div></div><div class="risk-note"><Info :size="14"/><span>{{leverage>=50?'高杠杆下，小幅波动即可触发强平。':'价格向不利方向变动可能损失全部保证金。'}}单笔损失不超过该笔保证金。</span></div><p v-if="orderError" class="form-error" role="alert">{{orderError}}</p><button class="submit-order" :class="side==='SHORT'?'short':'long'" :disabled="busy || (!!store.account && (!quote || quoteBusy || store.account.bankrupt))" @click="submit">{{busy?'正在提交…':!store.account?'登录后开始交易':store.account.bankrupt?'本日资金不足，明日再出发':`${side==='LONG'?'做多 / 买入':'做空 / 卖出'} ${selected}`}}<ArrowRight v-if="!busy" :size="16"/></button><p class="execution-note">市价成交 · 实际价格以成交时为准</p></aside>
        </div>
        <div class="bottom-grid"><section class="panel positions-panel"><div class="panel-title"><h2>当前持仓 <span class="count">{{store.account?.positions.length||0}}</span></h2><RouterLink to="/assets" class="text-link">全部资产 <ArrowUpRight :size="13"/></RouterLink></div><div v-if="!store.account" class="empty"><Wallet :size="26"/><b>你的星际投资旅程，尚未启航</b><span>注册领取 100,000 信用点，尝试你的第一笔交易。</span><button class="text-link" @click="store.authOpen=true">创建交易账户 <ArrowRight :size="14"/></button></div><div v-else-if="!store.account.positions.length" class="empty"><Compass :size="27"/><b>{{store.account.bankrupt?'本日资金不足，暂时休整':'目前没有持仓'}}</b><span>{{store.account.bankrupt?'零点将补充初始资金，行情仍可继续观察。':'选择一支资产，从一笔小额交易开始。'}}</span></div><div v-else class="table-scroll"><table><thead><tr><th>资产 / 方向</th><th>保证金</th><th>开仓均价</th><th>浮动盈亏</th><th>操作</th></tr></thead><tbody><tr v-for="p in store.account.positions" :key="p.id"><td><b>{{p.name}}</b><small :class="p.side==='LONG'?'up':'down'">{{p.side==='LONG'?'做多':'做空'}} · {{p.leverage}}×</small></td><td>{{money(p.margin)}}</td><td>{{money(p.entry_price,4)}}</td><td :class="changeClass(p.pnl)">{{signed(p.pnl)}}</td><td><button class="outline small" :disabled="busy" @click="closePosition(p.id)">平仓</button></td></tr></tbody></table></div></section><section class="panel news-panel"><div class="panel-title"><h2><Newspaper :size="15"/>星际快讯</h2><span class="micro muted">虚构事件</span></div><div class="news-list"><article v-for="n in store.market?.news.slice(0,3)" :key="n.id"><div class="news-meta"><span class="news-sentiment" :class="n.sentiment==='BULLISH'?'up':n.sentiment==='BEARISH'?'down':''">{{n.sentiment==='BULLISH'?'利好':n.sentiment==='BEARISH'?'利空':'市场'}}</span><span>{{new Date(n.created_at).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}}</span><span>{{n.symbol}}</span></div><p>{{n.title}}</p></article><div v-if="!store.market?.news.length" class="empty compact">等待星际通讯…</div></div></section></div>
      </template>

      <template v-else-if="route.path==='/assets'">
        <section v-if="!store.account" class="panel empty large-empty"><Wallet :size="36"/><h2>登录后查看你的星际资产</h2><p>持仓与成交记录会保存在账户中。</p><button class="primary" @click="store.authOpen=true">登录 / 注册</button></section>
        <template v-else><section class="panel"><div class="panel-title"><h2>当前持仓</h2><span class="muted">可用资金 {{money(store.account.balance)}} Cr.</span></div><div class="table-scroll"><table><thead><tr><th>资产 / 方向</th><th>保证金</th><th>数量</th><th>开仓均价</th><th>标记价格</th><th>强平价格</th><th>浮动盈亏</th><th>操作</th></tr></thead><tbody><tr v-for="p in store.account.positions" :key="p.id"><td><b>{{p.name}}</b><small :class="p.side==='LONG'?'up':'down'">{{p.side==='LONG'?'做多':'做空'}} {{p.leverage}}×</small></td><td>{{money(p.margin)}}</td><td>{{money(p.quantity,4)}}</td><td>{{money(p.entry_price,4)}}</td><td>{{money(p.mark_price,4)}}</td><td class="gold">{{money(p.liquidation_price,4)}}</td><td :class="changeClass(p.pnl)">{{signed(p.pnl)}}</td><td><button class="outline small" :disabled="busy" @click="closePosition(p.id)">全部平仓</button></td></tr></tbody></table><div v-if="!store.account.positions.length" class="empty">暂无持仓，前往交易市场发现机会。</div></div></section><section class="panel history-panel"><div class="panel-title"><h2>最近成交记录</h2><span class="muted micro">最近 60 笔</span></div><div class="table-scroll"><table><thead><tr><th>时间</th><th>资产</th><th>操作</th><th>数量</th><th>成交价</th><th>手续费</th><th>已实现盈亏（含平仓费）</th></tr></thead><tbody><tr v-for="t in store.account.trades" :key="t.id"><td>{{new Date(t.created_at).toLocaleString('zh-CN')}}</td><td>{{t.symbol}}</td><td><span :class="t.action==='LIQUIDATION'?'up':''">{{({OPEN:'开仓',CLOSE:'平仓',LIQUIDATION:'强制平仓',SETTLEMENT:'赛季结算'} as Record<string,string>)[t.action]}} · {{t.side==='LONG'?'多':'空'}}</span></td><td>{{money(t.quantity,4)}}</td><td>{{money(t.price,4)}}</td><td>{{money(t.fee,4)}}</td><td :class="t.action==='OPEN'?'muted':changeClass(t.pnl)">{{t.action==='OPEN'?'—':signed(t.pnl)}}</td></tr></tbody></table><div v-if="!store.account.trades.length" class="empty">暂无成交记录</div></div></section></template>
      </template>
      <section v-else class="panel ranking-panel"><div class="panel-title"><h2><Trophy :size="18"/>开拓者排行榜</h2><select v-model="rankDay" aria-label="选择赛季"><option value="">本日实时排行</option><option v-for="d in days" :key="d.season_day" :value="d.season_day">{{d.season_day}}</option></select></div><div class="rank-intro">每位开拓者以 100,000 信用点启航，按账户净值排序。历史榜单为结算后成绩。</div><div class="table-scroll"><table><thead><tr><th>排名</th><th>开拓者</th><th>账户净值</th><th>本日收益率</th><th>状态</th></tr></thead><tbody><tr v-for="(r,index) in ranking" :key="r.username" :class="{'my-rank':r.username===store.account?.username}"><td><span class="rank-num" :class="{podium:index<3}">{{String(index+1).padStart(2,'0')}}</span></td><td><span class="rank-avatar">{{r.username.slice(0,1)}}</span>{{r.username}} <span v-if="r.username===store.account?.username" class="gold micro">你</span></td><td>{{money(r.equity)}} Cr.</td><td :class="changeClass(Number(r.equity)-100000)">{{signed((Number(r.equity)/100000-1)*100)}}%</td><td>{{rankDay?'已结算':r.bankrupt?'休整中':'交易中'}}</td></tr></tbody></table><div v-if="!ranking.length" class="empty"><Trophy :size="30"/><b>榜单等待第一位开拓者</b><span>完成注册即可加入本日赛季。</span></div></div></section>
      <footer><span><Orbit :size="14"/> ASTRAL EXCHANGE <span class="footer-separator">/</span> 跨越世界的虚拟交易实验</span><div><button @click="tutorial=true">新手指南 <ExternalLink :size="11"/></button><span>非官方同人作品</span><a href="https://www.tradingview.com/" target="_blank" rel="noopener">Charts by TradingView</a></div></footer>
    </main>
    <Transition name="toast"><div v-if="notice" class="toast" role="status">{{notice}}<button class="icon-button" aria-label="关闭提示" @click="notice=''"><X :size="15"/></button></div></Transition>
    <div v-if="store.authOpen" class="modal-backdrop" @click.self="store.authOpen=false"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="auth-title"><button class="modal-close icon-button" aria-label="关闭登录" @click="store.authOpen=false"><X :size="20"/></button><Orbit :size="36" class="gold"/><div class="eyebrow">YOUR JOURNEY STARTS HERE</div><h2 id="auth-title">{{authMode==='register'?'开启你的星际账户':'欢迎返回，开拓者'}}</h2><p>{{authMode==='register'?'领取 100,000 虚拟信用点，加入公共市场。':'登录后继续你的交易旅程。'}}</p><form @submit.prevent="auth"><label for="username">用户名</label><input id="username" v-model="username" autocomplete="username" minlength="2" maxlength="24" required placeholder="2–24 位文字、数字或下划线"><label for="password">密码</label><input id="password" v-model="password" type="password" :autocomplete="authMode==='register'?'new-password':'current-password'" minlength="8" maxlength="64" required placeholder="8–64 位密码"><p v-if="authError" class="form-error" role="alert">{{authError}}</p><button class="primary" :disabled="busy">{{busy?'请稍候…':authMode==='register'?'创建账户，开始探索':'登录账户'}}<ArrowRight :size="16"/></button></form><button class="auth-toggle" @click="authMode=authMode==='register'?'login':'register';authError=''">{{authMode==='register'?'已有账户？登录':'还没有账户？免费注册'}}</button><small class="muted">仅用于虚构模拟交易，不涉及真实资金。</small></section></div>
    <div v-if="tutorial" class="modal-backdrop" @click.self="onboardDone"><section class="modal guide" role="dialog" aria-modal="true" aria-labelledby="guide-title"><button class="modal-close icon-button" aria-label="跳过教程" @click="onboardDone"><X :size="20"/></button><span class="guide-orbit"><Orbit :size="48"/></span><div class="eyebrow">A SMALL GUIDE TO A BIG UNIVERSE</div><h2 id="guide-title">第一次交易，也可以从容出发。</h2><p>这里的公司、新闻和价格均为同人虚构。你与所有玩家共享一个市场。</p><div class="guide-step"><b>01</b><div><h3>领取资金，选择资产</h3><p>每天 100,000 虚拟信用点。看涨选择做多，看跌选择做空。</p></div></div><div class="guide-step"><b>02</b><div><h3>杠杆放大收益，也放大风险</h3><p>100 倍杠杆下，小幅波动就可能强平。先看强平价，再确认下单。</p></div></div><div class="guide-step"><b>03</b><div><h3>逐仓承担风险，每天重新出发</h3><p>每笔最多损失该笔保证金。每日北京时间零点结算、重置资金与排行。</p></div></div><button class="primary" @click="onboardDone">了解了，进入市场 <ArrowRight :size="17"/></button></section></div>
  </div>
</template>
