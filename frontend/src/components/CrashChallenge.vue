<script setup lang="ts">
import { computed,ref } from 'vue'
import { ArrowLeft,ArrowRight,Orbit,RotateCcw,TriangleAlert } from 'lucide-vue-next'
import './challenge.css'

const demoOnly=import.meta.env.MODE==='demo'
const stages=[
  {price:100,headline:'黑塔科研 · 算力采购预期升温'},
  {price:103,headline:'追加算力订单，股价冲高'},
  {price:101,headline:'市场开始质疑项目回报'},
  {price:94,headline:'部分订单延期，卖盘涌入'},
  {price:88,headline:'虚构 AI 板块遭遇抛售'},
  {price:95,headline:'低位买盘入场，股价反弹'},
  {price:92,headline:'市场收盘：算力热潮退烧'},
]
const stage=ref(0), side=ref<1|-1>(1), leverage=ref(5), started=ref(false), ended=ref(false), liquidated=ref(false)
const margin=1000, entry=100, feeRate=.0005, maintenance=.005
const price=computed(()=>stages[stage.value]!.price)
const qty=computed(()=>margin*leverage.value/entry)
const pnl=computed(()=>side.value*qty.value*(price.value-entry))
const openFee=computed(()=>margin*leverage.value*feeRate)
const threshold=computed(()=>side.value===1?(entry-margin/qty.value)/(1-maintenance):(entry+margin/qty.value)/(1+maintenance))
const net=computed(()=>Math.max(-margin,pnl.value-qty.value*price.value*feeRate)-openFee.value)
const coords=computed(()=>stages.slice(0,stage.value+1).map(({price},i)=>`${32+i*94},${195-(price-85)*8}`).join(' '))
const currency=(v:number)=>v.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const signed=(v:number)=>(v>=0?'+':'')+currency(v)
function advance(){
  if(!started.value||ended.value)return
  stage.value++
  liquidated.value=margin+pnl.value<=qty.value*price.value*maintenance
  ended.value=liquidated.value||stage.value===stages.length-1
}
function reset(){stage.value=0;started.value=false;ended.value=false;liquidated.value=false}
</script>

<template>
  <div class="crash-page">
    <header class="crash-top"><span><Orbit :size="24"/> 星际交易所</span><a v-if="!demoOnly" href="/"><ArrowLeft :size="15"/> 返回多人市场</a><span v-else class="crash-tag">免登录试玩</span></header>
    <main class="crash-main">
      <div class="crash-intro"><p class="crash-kicker">ASTRAL EXCHANGE / EVENT 01</p><h1>AI 股「空中飞人」</h1><p>算力订单刚传来好消息，下一条快讯就变了天。借《FX 战士久留美》的紧张感，试试在虚构市场里，你的仓位能撑过几根 K 线。</p></div>
      <div class="crash-grid">
        <section class="crash-card crash-chart" aria-label="虚构行情走势图">
          <div class="crash-card-head"><div><small>HRT / 虚构标的</small><h2>黑塔科研</h2></div><strong>{{currency(price)}}</strong></div>
          <svg viewBox="0 0 628 220" role="img" aria-label="已揭晓的价格轨迹"><path d="M32 35H596 M32 75H596 M32 115H596 M32 155H596 M32 195H596" fill="none" stroke="#26354b"/><polyline :points="coords" fill="none" :stroke="price<100?'#54caaa':'#ed828b'" stroke-width="3" stroke-linejoin="round"/><circle v-for="(point,i) in coords.split(' ')" :key="i" :cx="point.split(',')[0]" :cy="point.split(',')[1]" r="5" fill="#e9cc8f"/></svg>
          <p class="crash-headline" aria-live="polite"><span>第 {{stage+1}} / {{stages.length}} 幕</span>{{stages[stage]!.headline}}</p>
        </section>
        <section class="crash-card crash-controls">
          <h2>你的第一笔单</h2><p>用 1,000 虚拟信用点开一笔逐仓仓位。</p>
          <div class="crash-options"><button :disabled="started" :aria-pressed="side===1" @click="side=1">看涨 · 做多</button><button :disabled="started" :aria-pressed="side===-1" @click="side=-1">看跌 · 做空</button></div>
          <div class="crash-options crash-leverage"><button v-for="n in [1,5,20,100]" :key="n" :disabled="started" :aria-pressed="leverage===n" @click="leverage=n">{{n}}×</button></div>
          <dl><dt>预计强平价</dt><dd>{{currency(threshold)}}</dd><dt>{{ended?'净盈亏（含手续费）':'浮动盈亏'}}</dt><dd :class="(ended?net:pnl)>=0?'up':'down'">{{signed(ended?net:pnl)}}</dd></dl>
          <button v-if="!started" class="primary crash-action" @click="started=true">开仓，进入行情 <ArrowRight :size="17"/></button>
          <button v-else-if="!ended" class="primary crash-action" @click="advance">揭晓下一幕 <ArrowRight :size="17"/></button>
          <button v-else class="primary crash-action" @click="reset">换个策略再试 <RotateCcw :size="17"/></button>
          <p v-if="ended" class="crash-result" role="status"><TriangleAlert :size="15"/>{{liquidated?'已强平。你没能等到后面的行情。':'成功撑到收盘。试试用不同杠杆重走同一段行情。'}}</p>
        </section>
      </div>
      <p class="crash-foot">固定虚构剧本 · 简化成交 · 不影响多人账户。非官方同人作品，未使用官方图像。<a v-if="demoOnly" href="https://github.com/wek486/astral-exchange">查看 GitHub 源码 ↗</a></p>
    </main>
  </div>
</template>
