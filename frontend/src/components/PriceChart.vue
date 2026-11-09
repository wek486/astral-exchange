<script setup lang="ts">
import { onMounted,onBeforeUnmount,ref,watch } from 'vue'
import { CandlestickSeries,ColorType,createChart,type IChartApi,type ISeriesApi,type UTCTimestamp } from 'lightweight-charts'
import { api,type Candle } from '../store'
const props=defineProps<{symbol:string;price:number;serverTime:number}>()
const el=ref<HTMLDivElement>(), failed=ref(false), empty=ref(false)
let chart:IChartApi,series:ISeriesApi<'Candlestick'>,observer:ResizeObserver,last:Candle|undefined,generation=0
async function load() {
  const token=++generation
  try {
    const candles=await api<Candle[]>('/candles/'+props.symbol)
    if(token!==generation) return
    last=candles.at(-1); series.setData(candles.map(c=>({...c,time:c.time as UTCTimestamp}))); chart.timeScale().fitContent(); failed.value=false; empty.value=candles.length<2
  } catch { if(token===generation) failed.value=true }
}
watch(()=>props.symbol,()=>{last=undefined;void load()})
watch(()=>props.serverTime,(timeNow,timeBefore)=>{
  if(timeBefore && timeNow-timeBefore>6000){void load();return}
  if(!series || !last || props.price<=0) return
  const time=Math.floor(props.serverTime/60000)*60
  if(time<last.time) return
  last=time===last.time ? {...last,close:props.price,high:Math.max(last.high,props.price),low:Math.min(last.low,props.price)} : {time,open:last.close,high:Math.max(last.close,props.price),low:Math.min(last.close,props.price),close:props.price,volume:0}
  series.update({...last,time:last.time as UTCTimestamp})
})
onMounted(()=>{
  chart=createChart(el.value!,{autoSize:true,layout:{background:{type:ColorType.Solid,color:'#0c1424'},textColor:'#71829d',fontFamily:'ui-monospace, monospace',fontSize:11,attributionLogo:true},grid:{vertLines:{color:'#152136'},horzLines:{color:'#152136'}},rightPriceScale:{borderColor:'#20304a'},timeScale:{borderColor:'#20304a',timeVisible:true,secondsVisible:false},crosshair:{vertLine:{color:'#657591'},horzLine:{color:'#657591'}}})
  series=chart.addSeries(CandlestickSeries,{upColor:'#f17b83',downColor:'#4ac6a0',wickUpColor:'#f17b83',wickDownColor:'#4ac6a0',borderVisible:false,priceFormat:{type:'price',precision:4,minMove:.0001}})
  observer=new ResizeObserver(()=>chart.applyOptions({width:el.value!.clientWidth,height:el.value!.clientHeight})); observer.observe(el.value!); void load()
})
onBeforeUnmount(()=>{generation++;observer?.disconnect();chart?.remove()})
</script>
<template><div class="chart-wrap"><div ref="el" class="chart-canvas"/><div v-if="failed" class="chart-note">历史行情暂不可用 <button @click="load">重试</button></div><div v-else-if="empty" class="chart-note">市场刚刚开市，分钟 K 线正在积累</div></div></template>
