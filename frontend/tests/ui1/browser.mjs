import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import path from 'node:path'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { createServer as createViteServer } from 'vite'
import react from '@vitejs/plugin-react'
import assert from 'node:assert/strict'

const scratch = process.env.UI1_SCRATCH || path.join(homedir(), 'AppData/Local/hermes/cache/scratch')
const chrome = process.env.UI1_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const frontend = fileURLToPath(new URL('../../', import.meta.url))
const isApi = pathname => /^\/(api|market)(?:\/|$)/.test(pathname)
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
async function freePort() {
  const s = createServer(); await new Promise(resolve => s.listen(0, '127.0.0.1', resolve))
  const port = s.address().port; await new Promise(resolve => s.close(resolve)); return port
}
async function waitFor(fn) {
  let last
  for (let i = 0; i < 100; i++) { try { const x = await fn(); if (x) return x } catch (e) { last = e }; await pause(100) }
  throw last ?? Error('timeout')
}
const port = await freePort()
const origin = `http://127.0.0.1:${port}`
const demo = process.argv.includes('--demo')
await mkdir(scratch, { recursive: true })
const runDir = await mkdtemp(path.join(scratch, 'ui1-run-'))
const profile = path.join(runDir, 'chrome-profile')
const cacheDir = path.join(runDir, 'vite-cache')
let server, browser, ws, call
try {
  // Never load the normal Vite configuration: its API proxy points at live services.
  server = await createViteServer({
    configFile: false,
    envFile: false,
    root: frontend,
    cacheDir,
    plugins: [react(), {
      name: 'ui1-deny-unintercepted-api',
      configureServer(vite) {
        vite.middlewares.use((req, res, next) => {
          if (!isApi(new URL(req.url, origin).pathname)) return next()
          res.writeHead(403, { 'Content-Type': 'text/plain' })
          res.end('UI.1: synthetic API requires browser interception')
        })
      },
    }],
    server: { host: '127.0.0.1', port, strictPort: true, proxy: {}, fs: { allow: [frontend, runDir] } },
  })
  assert.deepEqual(server.config.server.proxy, {})
  await server.listen()
  for (const [pathname, method] of [['/api/auth/me', 'GET'], ['/api/portfolios/101/transactions', 'POST'], ['/market/quotes/ALFA', 'GET']]) {
    const refused = await fetch(origin + pathname, { method, redirect: 'error' })
    assert.equal(refused.status, 403, `unintercepted ${method} ${pathname} must be denied locally`)
    assert.equal(await refused.text(), 'UI.1: synthetic API requires browser interception')
  }
  console.log('PASS: прямые GET/POST к /api и GET к /market без CDP отклонены локально (403)')

  browser = spawn(chrome, [...(demo ? [] : ['--headless=new']), '--disable-gpu', '--disable-background-networking', '--no-first-run', '--no-default-browser-check', '--no-proxy-server', '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1', `--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', 'about:blank'], { stdio: 'ignore' })
  let launchError
  browser.on('error', e => { launchError = e })
  const debugPort = await waitFor(async () => {
    if (launchError) throw launchError
    return Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split(/\r?\n/)[0])
  })
  console.log(`UI.1 test frontend ${origin}; Chrome CDP port ${debugPort}`)
  const target = await waitFor(async () => (await fetch(`http://127.0.0.1:${debugPort}/json/new?about:blank`, {method:'PUT'})).json())
  ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject })
  let id = 0; const pending = new Map(); let requests = []
  let emptyPortfolios = false, failTransactions = false, delayA = false, delayB = false
  let delayed = [], delayedB = []
  const unknown = []
  call = (method, params = {}) => new Promise((resolve, reject) => { const n = ++id; pending.set(n, {resolve,reject}); ws.send(JSON.stringify({id:n, method, params})) })
  const data = [
    {id:1,portfolioId:101,type:'BUY',ticker:'ALFA',quantity:2,price:11,commission:0.5,currency:'RUB',executedAt:'2026-03-08T00:00:00-05:00'},
    {id:2,portfolioId:101,type:'SELL',ticker:'BETA',quantity:1,price:20,commission:0,currency:'RUB',executedAt:'2026-03-09T03:59:59Z'},
  ]
  ws.onmessage = async event => {
    const m = JSON.parse(event.data)
    if (m.id) { const p = pending.get(m.id); pending.delete(m.id); if (p) m.error ? p.reject(Error(JSON.stringify(m.error))) : p.resolve(m.result); return }
    if (m.method !== 'Fetch.requestPaused') return
    const r = m.params.request, u = new URL(r.url); requests.push(`${r.method} ${u.href}`)
    try {
      if (u.origin !== origin) { await call('Fetch.failRequest',{requestId:m.params.requestId,errorReason:'BlockedByClient'}); return }
      if (!isApi(u.pathname)) { await call('Fetch.continueRequest',{requestId:m.params.requestId}); return }
      let body, status = 200
      if (u.pathname === '/api/auth/me' && r.method === 'GET') body = {id:1,email:'fixture@example.invalid'}
      else if (u.pathname === '/api/portfolios' && r.method === 'GET') body = emptyPortfolios ? [] : [{id:101,name:'Синтетика A',baseCurrency:'RUB',createdAt:'2026-01-01T00:00:00Z'},{id:102,name:'Синтетика B',baseCurrency:'RUB',createdAt:'2026-01-01T00:00:00Z'}]
      else if (u.pathname === '/api/portfolios/101/transactions' && r.method === 'GET') {
        const failure = delayA ? await new Promise(resolve => delayed.push(resolve)) : failTransactions
        if (failure) { status=500; body={message:'Синтетическая ошибка'} } else body = data
      }
      else if (u.pathname === '/api/securities' && r.method === 'GET') body = []
      else if (u.pathname === '/api/portfolios/101/transactions' && r.method === 'POST') {
        const v = JSON.parse(r.postData); body = { ...v, id: 3, portfolioId:101, quantity:Number(v.quantity),price:Number(v.price),commission:Number(v.commission || 0),currency:v.currency || 'RUB',executedAt:v.executedAt || '2026-03-10T00:00:00Z' }; data.push(body)
      }
      else if (/^\/api\/portfolios\/101\/transactions\/\d+$/.test(u.pathname) && ['PUT','DELETE'].includes(r.method)) {
        const n = Number(u.pathname.split('/').pop()), i = data.findIndex(x => x.id === n)
        if (i < 0) { status = 404; body = {message:'missing fixture'} }
        else if (r.method === 'DELETE') { data.splice(i,1); status = 204; body = null }
        else { const v = JSON.parse(r.postData); body = {...data[i],...v,quantity:Number(v.quantity),price:Number(v.price),commission:Number(v.commission || 0)}; data[i] = body }
      }
      else if (u.pathname === '/api/portfolios/102/transactions' && r.method === 'GET') { if (delayB) await new Promise(resolve => delayedB.push(resolve)); body = [] }
      else if (/^\/api\/portfolios\/10[12]\/metrics$/.test(u.pathname) && r.method === 'GET') body = {portfolioId:101,portfolioName:'fixture',baseCurrency:'RUB',totalValue:0,totalCost:0,profit:0,profitPercent:null,dividendsReceived:0,positions:[],calculatedAt:'2026-01-01T00:00:00Z'}
      else if (/^\/api\/portfolios\/10[12]\/value-history$/.test(u.pathname) && r.method === 'GET') body = {portfolioId:101,baseCurrency:'RUB',priceSource:'fixture',points:[]}
      else { status = 599; body = {message:'Unexpected fixture request'}; unknown.push(`${r.method} ${u.pathname}`) }
      await call('Fetch.fulfillRequest',{requestId:m.params.requestId,responseCode:status,responseHeaders:[{name:'Content-Type',value:'application/json'}],body:Buffer.from(JSON.stringify(body)).toString('base64')})
    } catch (e) { console.error('interception error', e) }
  }
  await call('Fetch.enable',{patterns:[{urlPattern:'*'}]})
  await call('Page.enable')
  await call('Emulation.setTimezoneOverride',{timezoneId:'America/New_York'})
  await call('Page.addScriptToEvaluateOnNewDocument',{source:"localStorage.setItem('pt.token','fixture-only'); if (!localStorage.getItem('pt.portfolioId')) localStorage.setItem('pt.portfolioId','101')"})
  await call('Page.navigate',{url:`${origin}/transactions`})
  const evaluate = async expression => { const r = await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true}); if (r.exceptionDetails) throw Error(JSON.stringify(r.exceptionDetails)); return r.result.value }
  await waitFor(async () => (await evaluate('document.body.innerText')).includes('ALFA'))
  if (demo) {
    console.log(`Синтетическая демонстрация: ${origin}/transactions. Используйте открывшуюся вкладку; для завершения нажмите Enter.`)
    await new Promise(resolve => { process.stdin.once('data', resolve); browser.once('exit', resolve) })
    process.stdin.pause()
  } else {
  const search = await evaluate(`!![...document.querySelectorAll('input')].find(x => /тикер/i.test(x.labels?.[0]?.textContent || ''))`)
  assert.equal(search, true, 'Страница должна иметь подписанное поле поиска по тикеру')
  console.log('PASS: подписанное поле поиска на настоящем React-маршруте')
  const input = async (label, value) => evaluate(`(() => { const el = [...document.querySelectorAll('label')].find(x => x.textContent.includes(${JSON.stringify(label)})).querySelector('input,select'); const proto = el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(proto,'value').set.call(el,${JSON.stringify(value)}); el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input',{bubbles:true})); })()`)
  const body = async () => evaluate('document.body.innerText')
  const click = async text => evaluate(`([...document.querySelectorAll('button')].find(x => x.textContent.trim() === ${JSON.stringify(text)}) || (()=>{throw Error('button missing')})()).click()`)
  const rows = async () => evaluate(`[...document.querySelectorAll('tbody tr')].map(x => x.innerText)`)
  await input('Поиск по тикеру','  aLf ')
  await waitFor(async () => (await rows()).length === 1)
  assert.match((await rows())[0], /ALFA/)
  await input('Вид операции','SELL')
  await waitFor(async () => (await body()).includes('По фильтрам ничего не найдено'))
  await click('Сбросить фильтры')
  await waitFor(async () => (await rows()).length === 2)
  assert.match((await rows())[0], /BETA/)
  await input('Сортировка','asc')
  await waitFor(async () => (await rows())[0]?.includes('ALFA'))
  await input('С даты','2026-03-08'); await input('По дату','2026-03-08')
  await waitFor(async () => (await rows()).length === 2)
  await input('С даты','2026-03-09'); await input('По дату','')
  await waitFor(async () => (await body()).includes('По фильтрам ничего не найдено'))
  await input('С даты','2026-03-10'); await input('По дату','2026-03-09')
  await waitFor(async () => (await body()).includes('Дата начала позже даты окончания'))
  await click('Сбросить фильтры')
  assert.match((await rows())[1], /22,00/)
  console.log('PASS: поиск/тип, пустая выборка, сброс, порядок, неверный период, сумма fixture')
  await input('Поиск по тикеру','NEW')
  await click('Добавить операцию')
  const modalInput = async (label,value) => evaluate(`(() => {const el=[...document.querySelectorAll('[role=dialog] label')].find(x=>x.textContent.trim()===${JSON.stringify(label)}).querySelector('input,select'); const proto=el.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(proto,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}))})()`)
  await modalInput('Тикер','NEW'); await modalInput('Количество','2'); await modalInput('Цена за бумагу','5')
  await click('Добавить')
  await waitFor(async () => (await rows()).some(x => x.includes('NEW')))
  await evaluate("document.querySelector('tbody tr .row-actions button').click()")
  await waitFor(async () => (await body()).includes('Изменение операции'))
  await modalInput('Цена за бумагу','7')
  await click('Сохранить')
  await waitFor(async () => (await rows()).some(x => x.includes('14,00')))
  await evaluate("document.querySelectorAll('tbody tr .row-actions button')[1].click()")
  await evaluate("[...document.querySelectorAll('[role=dialog] button')].find(x => x.textContent.trim() === 'Удалить').click()")
  await waitFor(async () => (await body()).includes('По фильтрам ничего не найдено'))
  await click('Сбросить фильтры')
  console.log('PASS: create/edit/delete на синтетике с сохранением фильтра')
  data.push({id:9,portfolioId:101,type:'DIVIDEND',ticker:'GAMMA',quantity:1,price:1,commission:0,currency:'RUB',executedAt:'2026-03-08T00:00:00-05:00'})
  await call('Page.reload')
  await waitFor(async () => (await rows()).length === 3)
  assert.match((await rows())[1], /GAMMA/); assert.match((await rows())[2], /ALFA/)
  await input('Сортировка','asc')
  await waitFor(async () => (await rows())[0]?.includes('ALFA'))
  assert.match((await rows())[1], /GAMMA/)
  data.pop()
  console.log('PASS: равное время, tie-break по id в обе стороны на странице')
  data.push(
    { ...data[0], id: 30, ticker: 'EARLIER', executedAt: '2026-03-08T13:00:00.123100Z' },
    { ...data[0], id: 20, ticker: 'LATER', executedAt: '2026-03-08T13:00:00.123900Z' },
    { ...data[0], id: 10, ticker: 'LATER_OFFSET', executedAt: '2026-03-08T09:00:00.1239-04:00' },
  )
  await call('Page.reload')
  await waitFor(async () => (await rows()).length === 5)
  const tickers = async () => evaluate(`[...document.querySelectorAll('tbody .ticker-link')].map(x => x.textContent)`)
  assert.deepEqual(await tickers(), ['BETA', 'LATER', 'LATER_OFFSET', 'EARLIER', 'ALFA'])
  await input('Сортировка', 'asc')
  await waitFor(async () => (await rows())[0]?.includes('ALFA'))
  assert.deepEqual(await tickers(), ['ALFA', 'EARLIER', 'LATER_OFFSET', 'LATER', 'BETA'])
  data.splice(-3)
  console.log('PASS: порядок разных instant внутри миллисекунды и равных instant с разными offset')
  failTransactions = true
  await call('Page.reload')
  await waitFor(async () => (await body()).includes('Синтетическая ошибка'))
  failTransactions = false; delayA = true
  await evaluate("{const b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='Повторить'); b.click(); b.click()}")
  await waitFor(() => delayed.length >= 2)
  const concurrent = delayed.splice(0)
  concurrent[concurrent.length - 1](false)
  await waitFor(async () => (await body()).includes('ALFA'))
  concurrent.slice(0,-1).forEach(resolve => resolve(true))
  await pause(300)
  assert.doesNotMatch(await body(), /Синтетическая ошибка/)
  assert.match(await body(), /ALFA/)
  delayA = false
  console.log('PASS: ошибка и конкурирующие повторные запросы: поздняя ошибка не перезаписывает успех')
  await call('Emulation.setDeviceMetricsOverride',{width:375,height:720,deviceScaleFactor:1,mobile:true})
  await evaluate('document.body.focus()')
  const focused = new Set()
  for (let i = 0; i < 25; i++) {
    await call('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9})
    await call('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9})
    focused.add(await evaluate("document.activeElement.closest('label')?.innerText || document.activeElement.innerText"))
  }
  for (const text of ['Поиск по тикеру','Вид операции','С даты','По дату','Сортировка','Сбросить фильтры','Добавить операцию']) assert.equal([...focused].some(x => x?.includes(text)),true, `Tab: ${text}`)
  const shot = await call('Page.captureScreenshot',{format:'png'})
  const screenshot = path.join(runDir, 'ui1-mobile.png')
  await writeFile(screenshot, Buffer.from(shot.data, 'base64'))
  console.log(`Снимок UI.1: ${screenshot}`)
  await call('Emulation.clearDeviceMetricsOverride')
  console.log('PASS: Tab и снимок viewport 375px')
  delayA = true
  await call('Page.reload')
  await waitFor(() => delayed.length > 0)
  await waitFor(async () => (await body()).includes('Загрузка операций'))
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Дашборд').click()")
  await waitFor(async () => (await body()).includes('Портфель:'))
  await input('Портфель:', '102')
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Сделки').click()")
  await waitFor(async () => (await body()).includes('Операций пока нет'))
  delayA = false; delayed.splice(0).forEach(resolve => resolve())
  await pause(500)
  assert.match(await body(), /Синтетика B/)
  assert.doesNotMatch(await body(), /ALFA|BETA/)
  await call('Page.reload')
  await waitFor(async () => (await body()).includes('Синтетика B'))
  assert.match(await body(), /Операций пока нет/)
  console.log('PASS: отложенный ответ A после навигации к B, пустой B, выбор после reload')
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Дашборд').click()")
  await waitFor(async () => (await body()).includes('Портфель:'))
  await input('Портфель:', '101')
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Сделки').click()")
  await waitFor(async () => (await body()).includes('ALFA'))
  await input('Поиск по тикеру', 'ALF')
  await click('Добавить операцию')
  assert.equal(await evaluate("!!document.querySelector('[role=dialog]')"), true)
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Дашборд').click()")
  await waitFor(async () => (await body()).includes('Портфель:'))
  delayA = true
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Сделки').click()")
  await waitFor(() => delayed.length > 0)
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Дашборд').click()")
  await waitFor(async () => (await body()).includes('Портфель:'))
  await input('Портфель:', '102')
  delayB = true
  await evaluate("[...document.querySelectorAll('a')].find(x => x.textContent === 'Сделки').click()")
  await waitFor(() => delayedB.length > 0)
  delayed.splice(0).forEach(resolve => resolve(true)); delayA = false
  await pause(300)
  assert.match(await body(), /Синтетика B/)
  assert.match(await body(), /Загрузка операций/)
  assert.doesNotMatch(await body(), /Синтетическая ошибка/)
  assert.equal(await evaluate("!!document.querySelector('[role=dialog]')"), false)
  assert.equal(await evaluate("[...document.querySelectorAll('label')].find(x=>x.textContent.includes('Поиск по тикеру')).querySelector('input').value"), '')
  delayB = false; delayedB.splice(0).forEach(resolve => resolve())
  await waitFor(async () => (await body()).includes('Операций пока нет'))
  console.log('PASS: поздняя ошибка A при загрузке B; модалка закрыта, фильтр сброшен')
  emptyPortfolios = true
  await call('Page.reload')
  await waitFor(async () => (await body()).includes('Нет портфеля'))
  console.log('PASS: отсутствие портфелей')
  console.log(`fixture URL ${origin}/transactions; перехвачено запросов ${requests.length}`)
  }
  assert.deepEqual(unknown, [])
} finally {
  try {
    if (browser?.pid && browser.exitCode === null && browser.signalCode === null) {
      const closed = new Promise(resolve => browser.once('exit', resolve))
      if (ws?.readyState === WebSocket.OPEN) await Promise.race([call('Browser.close').catch(() => {}), pause(500)])
      await Promise.race([closed, pause(500)])
      if (browser.exitCode === null && browser.signalCode === null) {
        browser.kill()
        await Promise.race([closed, pause(2000)])
      }
      assert.ok(browser.exitCode !== null || browser.signalCode !== null, 'isolated Chrome did not stop')
    }
  } finally {
    ws?.close()
    await server?.close()
    await rm(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
    await rm(cacheDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
    if (demo) await rm(runDir, { recursive: true, force: true })
  }
}
