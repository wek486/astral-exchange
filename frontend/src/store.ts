import { defineStore } from 'pinia'
import { ref } from 'vue'
export interface Asset { symbol:string; name:string; universe:string; sector:string; price:number; change:number; volume:number; initial_price:number }
export interface News { id:string; symbol:string; title:string; sentiment:string; created_at:number }
export interface Position { id:string; symbol:string; name:string; side:string; margin:number; leverage:number; quantity:number; entry_price:number; mark_price:number; pnl:number; liquidation_price:number }
export interface Trade { id:string; symbol:string; action:string; side:string; quantity:number; price:number; fee:number; pnl:number; created_at:number }
export interface Account { username:string; balance:number; equity:number; return_pct:number; bankrupt:boolean; positions:Position[]; trades:Trade[] }
export interface Snapshot { assets:Asset[]; news:News[]; season:string; serverTime:number; nextReset:number }
export interface Candle {time:number;open:number;high:number;low:number;close:number;volume:number}
let csrf = ''
export async function api<T=any>(path:string, method='GET', body?:unknown):Promise<T> {
  if (method !== 'GET' && !csrf) csrf = (await api<{token:string}>('/csrf')).token
  const response = await fetch('/api'+path, { method, credentials:'same-origin', headers: method==='GET' ? {} : {'Content-Type':'application/json','X-XSRF-TOKEN':csrf}, body:body===undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) {
    if (response.status===403) csrf=''
    const error=await response.json().catch(()=>({message:'服务暂时不可用'}))
    throw Object.assign(new Error(error.message || '请求失败'),{status:response.status})
  }
  return response.json()
}
export const useExchange = defineStore('exchange', () => {
  const market=ref<Snapshot|null>(null), account=ref<Account|null>(null), connected=ref(false), error=ref(''), authOpen=ref(false)
  let stream:EventSource|undefined, poll:ReturnType<typeof setInterval>|undefined, refreshing=false
  const pollingOnly=import.meta.env.VITE_POLL_ONLY==='true'
  async function refreshAccount() {
    if(refreshing) return
    refreshing=true
    try { account.value=await api<Account>('/account') }
    catch(e:any) { if(e.status===401) account.value=null; else error.value=e.message }
    finally { refreshing=false }
  }
  async function start() {
    await refreshAccount()
    try { market.value=await api<Snapshot>('/market') } catch { error.value='行情连接失败，请确认后端已启动'; }
    if(!pollingOnly){
      stream=new EventSource('/api/events')
      stream.addEventListener('market',event=>{ market.value=JSON.parse((event as MessageEvent).data); connected.value=true; error.value=''; if(account.value) void refreshAccount() })
      stream.onerror=()=>{ connected.value=false }
    }else connected.value=!!market.value
    poll=setInterval(()=>{ if(pollingOnly||!connected.value) api<Snapshot>('/market').then(m=>{market.value=m;error.value='';if(pollingOnly)connected.value=true;if(account.value)void refreshAccount()}).catch(()=>{if(pollingOnly)connected.value=false}); },pollingOnly?2000:5000)
  }
  function stop() { stream?.close(); if(poll) clearInterval(poll) }
  async function logout() { await api('/logout','POST'); csrf=''; account.value=null }
  return {market,account,connected,error,authOpen,start,stop,refreshAccount,logout}
})
