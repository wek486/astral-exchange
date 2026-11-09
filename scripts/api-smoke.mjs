import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
const base=process.env.API_BASE||'http://127.0.0.1:8080'
class Player {
  cookies=new Map()
  async request(path,method='GET',body){
    const headers={Cookie:[...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ')}
    if(method!=='GET'){
      const token=await this.request('/csrf')
      headers.Cookie=[...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ')
      headers['X-XSRF-TOKEN']=token.token;headers['Content-Type']='application/json'
    }
    const response=await fetch(base+'/api'+path,{method,headers,body:body?JSON.stringify(body):undefined})
    for(const cookie of response.headers.getSetCookie()){
      const [pair]=cookie.split(';'),index=pair.indexOf('=')
      this.cookies.set(pair.slice(0,index),pair.slice(index+1))
    }
    const data=await response.json()
    assert.equal(response.status,200,`${method} ${path}: ${JSON.stringify(data)}`)
    return data
  }
}
const a=new Player(),b=new Player(),suffix=Date.now().toString(36)
for(const [index,player] of [a,b].entries()){
  const account=await player.request('/auth/register','POST',{username:`smoke_${suffix}_${index}`,password:randomUUID()})
  assert.equal(account.balance,100000)
}
const command={requestId:randomUUID(),symbol:'IPC',side:'LONG',margin:1000,leverage:5}
const receipt=await a.request('/orders','POST',command)
const repeat=await a.request('/orders','POST',command)
assert.equal(receipt.id,repeat.id);assert.equal(repeat.duplicate,true)
const account=await a.request('/account');assert.equal(account.positions.length,1);assert.equal(account.balance,98997.5)
const theirs=await b.request('/account');assert.equal(theirs.positions.length,0)
await a.request(`/positions/${receipt.id}/close`,'POST',{requestId:randomUUID()})
assert.equal((await a.request('/account')).positions.length,0)
assert.equal((await b.request('/market')).assets.length,8)
const controller=new AbortController()
try{
  const response=await fetch(base+'/api/events',{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(10000)])})
  assert.match(response.headers.get('content-type'),/text\/event-stream/)
  const {value}=await response.body.getReader().read();assert.match(new TextDecoder().decode(value),/event:market/)
}finally{controller.abort()}
await a.request('/logout','POST')
console.log('PASS: two accounts, MySQL-backed balances, duplicate orders, close, shared market, SSE, logout.')
