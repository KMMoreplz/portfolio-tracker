import assert from 'node:assert/strict'
import { runBrowser, until } from '../ui5/harness.mjs'
const routes = new Map([
 ['GET /api/auth/me',()=>({body:{id:1,email:'fixture@example.invalid'}})],
 ['GET /api/portfolios',()=>({body:[{id:101,name:'Синтетика A',baseCurrency:'RUB',createdAt:'2026-01-01T00:00:00Z'}]})],
 ['GET /api/portfolios/101/dividend-calendar',()=>({body:{portfolioId:101,baseCurrency:'RUB',marketDataAvailable:true,items:[]}})],
 ['GET /api/portfolios/101/transactions',()=>({body:[
  {id:1,portfolioId:101,type:'BUY',ticker:'BETA+B',quantity:42,price:11,commission:0,currency:'RUB',executedAt:'2026-03-10T13:00:00Z'},
  {id:2,portfolioId:101,type:'BUY',ticker:'OTHER',quantity:21,price:10,commission:0,currency:'RUB',executedAt:'2026-03-09T13:00:00Z'},
 ]})],
])
await runBrowser({routes,demo:true},async ({call,evaluate,api,runDir})=>{
 await until(()=>evaluate("document.body.innerText.includes('Предстоящих выплат нет')"),'calendar')
 await call('Page.navigate',{url:`${await evaluate('location.origin')}/transactions?ticker=BETA%2BB&demo=1`})
 await until(()=>evaluate("document.body.innerText.includes('Найдено в загруженном списке: 1') && document.body.innerText.includes('Снять точный тикер')"),'exact ready')
 await call('Page.bringToFront')
 const state = await evaluate(`({heading:document.querySelector('h1').innerText, rows:[...document.querySelectorAll('tbody tr')].map(row=>[row.cells[2].innerText.trim(),row.cells[3].innerText.trim()]), buttons:[...document.querySelectorAll('button')].filter(x=>x.textContent.trim()==='Снять точный тикер').length})`)
 assert.deepEqual(state,{heading:'Сделки — Синтетика A',rows:[['BETA+B','42']],buttons:1})
 assert.ok(api.some(entry=>entry.key==='GET /api/portfolios/101/transactions' && entry.fixture))
 console.log(`DEMO READY: exact BETA+B visible, button ready; ${runDir}`)
 await new Promise((resolve,reject)=>{process.stdin.setEncoding('utf8');process.stdin.once('data',data=>data.includes('\n')?resolve():reject(Error('Expected Enter')));process.stdin.once('end',()=>reject(Error('EOF before Enter')))})
 process.stdin.pause()
 console.log('DEMO ENTER ACKNOWLEDGED')
})
