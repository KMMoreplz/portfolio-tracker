import assert from 'node:assert/strict'
const { runBrowser, until } = await import(process.env.UI7_HARNESS || '../ui5/harness.mjs')
const tx = (id,ticker,type='BUY',day='10') => ({id,portfolioId:101,type,ticker,quantity:id,price:11,commission:0,currency:'RUB',executedAt:`2026-03-${day}T13:00:00Z`})
const rows = [...Array.from({length:12},(_,i)=>tx(i+1,'BETA+B')),tx(13,'BETA+B-P'),tx(14,'OTHER'),tx(15,'BETA+B','SELL'),tx(16,'BETA+B','BUY','01'),tx(17,'BETA+B','BUY','30')]
const routes = new Map([
 ['GET /api/auth/me',()=>({body:{id:1,email:'fixture@example.invalid'}})],
 ['GET /api/portfolios',()=>({body:[{id:101,name:'Синтетика A',baseCurrency:'RUB',createdAt:'2026-01-01T00:00:00Z'}]})],
 ['GET /api/portfolios/101/dividend-calendar',()=>({body:{portfolioId:101,baseCurrency:'RUB',marketDataAvailable:true,items:[]}})],
 ['GET /api/portfolios/101/transactions',()=>({body:rows})],
])
await runBrowser({routes,demo:process.argv.includes('--demo')},async ({call,evaluate,save,api})=>{
 const go=async q=>{ await call('Page.navigate',{url:`${await evaluate('location.origin')}/transactions${q}`}); await until(()=>evaluate("location.pathname==='/transactions' && document.body.innerText.includes('Найдено в загруженном списке:')"),'loaded transactions') }
 const set=async (selector,value)=>evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(e.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))})()`)
 const key=async (name,code)=>{const e={key:name,code:name,windowsVirtualKeyCode:code};await call('Input.dispatchKeyEvent',{type:'keyDown',...e,...(name==='Enter'?{text:'\r',unmodifiedText:'\r'}:{})});await call('Input.dispatchKeyEvent',{type:'keyUp',...e})}
 await until(()=>evaluate("document.body.innerText.includes('Предстоящих выплат нет')"),'calendar loaded')
 await go('?ticker=BETA%2BB&other=kept')
 await set('.transactions-filters select','BUY')
 // Inputs are nested in labels; address each date explicitly.
 await set('.transactions-filters label:nth-of-type(3) input','2026-03-05')
 await set('.transactions-filters label:nth-of-type(4) input','2026-03-20')
 await set('.transactions-filters label:nth-of-type(5) select','asc')
 await until(()=>evaluate("document.body.innerText.includes('Найдено в загруженном списке: 12')"),'12 exact rows')
 await evaluate("[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='Далее').click()")
 await until(()=>evaluate("document.body.innerText.includes('Страница 2 из 2')"),'page 2')
 assert.equal(await evaluate("[...document.querySelectorAll('button')].filter(x=>x.textContent.trim()==='Снять точный тикер').length"),1,'exact removal action')
 const count=api.length
 await call('Page.bringToFront'); await evaluate("document.querySelector('.transactions-filters input').focus()"); await key('Tab',9)
 assert.equal(await evaluate("document.activeElement?.textContent.trim()"),'Снять точный тикер')
 await key('Enter',13)
 await until(()=>evaluate("location.search==='?other=kept' && document.body.innerText.includes('Найдено в загруженном списке: 14')"),'removed exact')
 assert.equal(api.length,count,'no requests')
 const state=await evaluate(`({focus:document.activeElement.tagName, heading:document.querySelector('h1').innerText, text:document.querySelector('main').innerText, rows:[...document.querySelectorAll('tbody tr')].map(x=>[x.cells[2].innerText.trim(),x.cells[3].innerText.trim()]), dates:[...document.querySelectorAll('input[type=date]')].map(x=>x.value), selects:[...document.querySelectorAll('.transactions-filters select')].map(x=>x.value)})`)
 assert.notEqual(state.focus,'BODY'); assert.equal(state.heading,'Сделки — Синтетика A'); assert.match(state.text,/Страница 1 из 2/); assert.doesNotMatch(state.text,/Точный тикер:/); assert.deepEqual(state.rows,Array.from({length:10},(_,i)=>['BETA+B',String(i+1)])); assert.deepEqual(state.dates,['2026-03-05','2026-03-20']);assert.deepEqual(state.selects,['BUY','asc'])
 await evaluate("[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='Далее').click()")
 await until(()=>evaluate("document.body.innerText.includes('Страница 2 из 2')"),'removed tail')
 assert.deepEqual(await evaluate("[...document.querySelectorAll('tbody tr')].map(x=>[x.cells[2].innerText.trim(),x.cells[3].innerText.trim()])"),[['BETA+B','11'],['BETA+B','12'],['BETA+B-P','13'],['OTHER','14']])
 await save('removed')
 await evaluate('history.back()'); await until(()=>evaluate("location.search==='?ticker=BETA%2BB&other=kept'"),'back exact link')
 assert.ok(await evaluate("document.body.innerText.includes('Найдено в загруженном списке: 12')"))
 // Set ordinary search, then Back restores exact while preserving component state.
 await set('.transactions-filters input','BETA'); await until(()=>evaluate("location.search==='?other=kept'"),'search clears exact')
 await evaluate('history.back()'); await until(()=>evaluate("location.search==='?ticker=BETA%2BB&other=kept'"),'back with ordinary search')
 assert.equal(await evaluate("document.querySelector('.transactions-filters input').value"),'BETA')
 const priorSearch=api.length
 await evaluate("[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='Снять точный тикер').click()")
 await until(()=>evaluate("location.search==='?other=kept' && document.body.innerText.includes('Найдено в загруженном списке: 13')"),'remove preserves search')
 assert.equal(await evaluate("document.querySelector('.transactions-filters input').value"),'BETA'); assert.equal(api.length,priorSearch)
 await go('?ticker=NO-MATCH&other=kept');assert.ok(await evaluate("document.body.innerText.includes('По фильтрам ничего не найдено')"));
 const priorEmpty=api.length
 await evaluate("[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='Снять точный тикер').click()")
 await until(()=>evaluate("location.search==='?other=kept' && document.body.innerText.includes('Найдено в загруженном списке: 17')"),'remove empty exact')
 assert.equal(api.length,priorEmpty); assert.equal(await evaluate("document.activeElement===document.querySelector('.transactions-filters input')"),true)
 for(const q of ['?ticker=','?ticker=A&ticker=B','']) {await go(q); assert.equal(await evaluate("[...document.querySelectorAll('button')].filter(x=>x.textContent.trim()==='Снять точный тикер').length"),0)}
 console.log('PASS exact removal, pagination, preservation, history, empty and invalid')
 if(process.argv.includes('--demo')) {console.log('Demo ready; press Enter to exit');await new Promise(resolve=>process.stdin.once('data',resolve));console.log('Demo exit acknowledged')}
})
