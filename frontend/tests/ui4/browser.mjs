import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises'
import { createServer, createConnection } from 'node:net'
import { homedir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer as createViteServer } from 'vite'
import react from '@vitejs/plugin-react'

const frontend = fileURLToPath(new URL('../../', import.meta.url))
const scratchRoot = path.resolve(homedir(), 'AppData/Local/hermes/cache/scratch')
assert.ok(process.env.UI4_SCRATCH, 'Set UI4_SCRATCH to an existing directory in Hermes scratch')
const scratch = path.resolve(process.env.UI4_SCRATCH)
const chromePath = process.env.UI3_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const demo = process.argv.includes('--demo')
const only = process.argv.find(arg => arg.startsWith('--case='))?.slice(7)
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
const isApi = pathname => /^\/(api|market)(?:\/|$)/.test(pathname)
const denied = 'UI.3: intercepted synthetic API only'

async function freePort() {
  const probe = createServer()
  await new Promise((resolve, reject) => probe.once('error', reject).listen(0, '127.0.0.1', resolve))
  const { port } = probe.address()
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()))
  return port
}

async function waitFor(predicate, label) {
  for (let i = 0; i < 150; i++) {
    if (await predicate()) return
    await pause(60)
  }
  assert.fail(`Timed out: ${label}`)
}

function portClosed(port) {
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host: '127.0.0.1', port })
    socket.once('connect', () => { socket.destroy(); resolve(false) })
    socket.once('error', error => error.code === 'ECONNREFUSED' ? resolve(true) : reject(error))
  })
}

const aTicker = n => `A-${n % 2 ? 'SELL' : 'BUY'}-${String(n).padStart(2, '0')}`
const bTicker = n => `B-${String(n).padStart(2, '0')}`
const sequence = (first, last, step = -1) => Array.from({ length: 1 + (last - first) / step }, (_, i) => first + i * step)
const fixture = (id, portfolioId) => ({
  id, portfolioId, type: id % 2 ? 'SELL' : 'BUY', ticker: (portfolioId === 101 ? aTicker : bTicker)(id),
  quantity: 2, price: 11, commission: 0.5, currency: 'RUB',
  executedAt: `2026-03-${id <= 14 ? '08' : '09'}T13:${String(id).padStart(2, '0')}:00Z`,
})
const rowsA = sequence(1, 27, 1).map(id => fixture(id, 101))
const rowsB = [{...fixture(1,102),ticker:'BETA',quantity:31},{...fixture(2,102),ticker:'BETA-P',quantity:47}]
const apiLog = []
const unknown = []
const port = await freePort()
const origin = `http://127.0.0.1:${port}`
const within = (child, parent) => { const rel = path.relative(parent.toLowerCase(), child.toLowerCase()); return rel === '' || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel)) }
const actualScratch = await realpath(scratch)
const actualRoot = await realpath(scratchRoot)
const actualProject = await realpath(path.resolve(frontend, '..'))
assert.ok(within(actualScratch, actualRoot) && actualScratch !== actualRoot, 'Scratch must be inside Hermes scratch')
assert.ok(!within(actualScratch, actualProject), 'Scratch must be outside the project')
const runDir = await mkdtemp(path.join(actualScratch, 'ui4-run-'))
const profile = path.join(runDir, 'chrome-profile')
const cacheDir = path.join(runDir, 'vite-cache')
let server, browser, closed, ws, call, debugPort
let listening = false

try {
  server = await createViteServer({
    root: frontend, configFile: false, envFile: false, cacheDir,
    plugins: [react(), {
      name: 'ui2-deny-direct-api',
      configureServer(vite) {
        vite.middlewares.use((req, res, next) => {
          if (!isApi(new URL(req.url, origin).pathname)) return next()
          res.writeHead(403, { 'Content-Type': 'text/plain' })
          res.end(denied)
        })
      },
    }],
    server: { host: '127.0.0.1', port, strictPort: true, proxy: {}, hmr: false, fs: { allow: [frontend, runDir] } },
  })
  assert.deepEqual(server.config.server.proxy, {})
  await server.listen()
  listening = true
  for (const [method, url] of [['GET', '/api/auth/me'], ['POST', '/api/portfolios/101/transactions'], ['GET', '/market/quotes/SYNTH']]) {
    const reply = await fetch(origin + url, { method, redirect: 'error' })
    assert.equal(reply.status, 403, `${method} ${url}`)
    assert.equal(await reply.text(), denied)
    console.log(`Preflight ${method} ${url}: 403 local`)
  }

  browser = spawn(chromePath, [
    ...(demo ? [] : ['--headless=new']), '--disable-gpu', '--disable-background-networking',
    '--no-first-run', '--no-default-browser-check', '--no-proxy-server',
    '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1',
    `--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', 'about:blank',
  ], { stdio: 'ignore', env: { ...process.env, TEMP: runDir, TMP: runDir } })
  let launchError
  browser.once('error', error => { launchError = error })
  closed = new Promise(resolve => browser.once('exit', resolve))
  await waitFor(async () => {
    if (launchError) throw launchError
    if (browser.exitCode !== null) throw Error(`Chrome exited: ${browser.exitCode}`)
    const activePort = await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8').catch(error => {
      if (error.code !== 'ENOENT') throw error
      return ''
    })
    debugPort = Number(activePort.split(/\r?\n/)[0])
    return debugPort > 0
  }, 'Chrome DevTools port')
  console.log(`UI.4 dashboard ${origin}/; CDP ${debugPort}; scratch ${runDir}`)
  const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`, { redirect: 'error' })).json()
  const target = targets.find(entry => entry.type === 'page' && entry.url === 'about:blank')
  assert.ok(target, 'Isolated blank Chrome target')
  assert.equal(new URL(target.webSocketDebuggerUrl).host, `127.0.0.1:${debugPort}`)
  ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject })

  const pending = new Map()
  let serial = 0
  call = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++serial
    const timer = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)) }, 5000)
    pending.set(id, { resolve, reject, timer })
    ws.send(JSON.stringify({ id, method, params }))
  })
  ws.onclose = () => {
    for (const { reject, timer } of pending.values()) { clearTimeout(timer); reject(Error('Chrome CDP closed')) }
    pending.clear()
  }
  let pendingMetrics = new Map()
  let rewriteA
  let getA = () => rowsA
  let getB = () => rowsB
  const fixtureRoutes = new Map([
    ['GET /api/auth/me', () => ({ id: 1, email: 'fixture@example.invalid' })],
    ['GET /api/portfolios', () => [101, 102].map(id => ({ id, name: `Синтетика ${id === 101 ? 'A' : 'B'}`, baseCurrency: 'RUB', createdAt: '2026-01-01T00:00:00Z' }))],
    ['GET /api/portfolios/101/transactions', () => getA()],
    ['GET /api/portfolios/102/transactions', () => getB()],
  ])
  for (const id of [101, 102]) {
    fixtureRoutes.set(`GET /api/portfolios/${id}/metrics`, async () => { if (pendingMetrics.has(id)) await pendingMetrics.get(id).promise; return ({
      portfolioId: id, portfolioName: 'Synthetic', baseCurrency: 'RUB', totalValue: 1234, totalCost: 1000,
      profit: 234, profitPercent: 23.4, dividendsReceived: 55, positions: (id === 101 ? [['ALFA','Синтетика ALFA'],['BRK.B',null],['GAMMA','Лунный фонд']] : [['BETA','Синтетика BETA'],['DELTA',null],['OMEGA','Северный фонд']]).map(([ticker,instrumentName]) => ({ticker,instrumentName,quantity:1,averagePrice:11,currentPrice:12,priceSource:'fixture',currency:'RUB',valueInBase:12,weightPercent:10,profit:1,profitPercent:9})), calculatedAt: '2026-01-01T00:00:00Z',
    }) })
    fixtureRoutes.set(`GET /api/portfolios/${id}/value-history?days=30`, () => ({
      portfolioId: id, baseCurrency: 'RUB', priceSource: 'fixture', points: [{date:'2026-01-01',totalValue:100,invested:80},{date:'2026-01-02',totalValue:160,invested:120},{date:'2026-01-03',totalValue:130,invested:125}],
    }))
  }
  for (const { id } of rowsA) {
    fixtureRoutes.set(`DELETE /api/portfolios/101/transactions/${id}`, () => {
      const index = rowsA.findIndex(row => row.id === id)
      assert.notEqual(index, -1, `Fixture ${id} must exist before deleting it`)
      rowsA.splice(index, 1)
      if (rewriteA) {
        rowsA.splice(0, rowsA.length, ...rewriteA)
        rewriteA = undefined
      }
      return undefined
    })
  }
  class FixtureError {
    constructor(message) { this.message = message }
  }
  const fulfilled = []
  const interceptionErrors = []
  const clientErrors = []
  ws.onmessage = async event => {
    const message = JSON.parse(event.data)
    if (message.id) {
      const command = pending.get(message.id)
      if (!command) return
      pending.delete(message.id)
      clearTimeout(command.timer)
      if (message.error) command.reject(Error(JSON.stringify(message.error)))
      else command.resolve(message.result)
      return
    }
    if (message.method === 'Runtime.exceptionThrown') clientErrors.push(message.params.exceptionDetails)
    if (message.method !== 'Fetch.requestPaused') return
    const { request, requestId } = message.params
    const url = new URL(request.url)
    try {
      if (url.origin !== origin) return await call('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' })
      if (!isApi(url.pathname)) return await call('Fetch.continueRequest', { requestId })
      const key = `${request.method} ${url.pathname}${url.search}`
      const handler = fixtureRoutes.get(key)
      if (!handler) unknown.push(key)
      else apiLog.push(key)
      const body = handler ? await handler() : { message: 'Unexpected fixture request' }
      const responseCode = !handler ? 403 : body instanceof FixtureError ? 500 : body === undefined ? 204 : 200
      await call('Fetch.fulfillRequest', {
        requestId, responseCode,
        ...(responseCode === 204 ? {} : {
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify(body)).toString('base64'),
        }),
      })
      fulfilled.push({ key, responseCode })
    } catch (error) { interceptionErrors.push(String(error)) }
  }
  await call('Fetch.enable', { patterns: [{ urlPattern: '*' }] })
  await call('Page.enable')
  await call('Runtime.enable')
  await call('Emulation.setTimezoneOverride', { timezoneId: 'America/New_York' })
  await call('Page.addScriptToEvaluateOnNewDocument', { source: `
    if (location.origin === ${JSON.stringify(origin)}) {
      localStorage.setItem('pt.token', 'ui2-fixture-only');
      if (!localStorage.getItem('pt.portfolioId')) localStorage.setItem('pt.portfolioId', '101');
    }
  ` })
  const navigation = await call('Page.navigate', { url: `${origin}/` })
  assert.equal(navigation.errorText, undefined)
  const evaluate = async expression => {
    const reply = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    assert.equal(reply.exceptionDetails, undefined, JSON.stringify(reply.exceptionDetails))
    return reply.result.value
  }
  const body = () => evaluate('document.body.innerText')
  const tickers = () => evaluate(`[...document.querySelectorAll('tbody .ticker-link')].map(x => x.textContent)`)
  const mustShow = text => waitFor(async () => (await body()).includes(text), text)
  const reloadAndWaitFor = async (ready, label) => {
    const previousDocument = await evaluate('performance.timeOrigin')
    await call('Page.reload')
    // The old document can still satisfy the readiness predicate after Page.reload returns.
    await waitFor(() => evaluate(`performance.timeOrigin !== ${previousDocument} && (${ready})`), label)
  }
  const state = text => evaluate(`Boolean([...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(text)})?.disabled)`)
  const click = text => evaluate(`([...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(text)})).click()`)
  const input = (label, value) => evaluate(`(() => {
    const element = [...document.querySelectorAll('label')].find(label => label.textContent.includes(${JSON.stringify(label)})).querySelector('input,select');
    const select = element.tagName === 'SELECT';
    const prototype = select ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, ${JSON.stringify(value)});
    element.dispatchEvent(new Event(select ? 'change' : 'input', { bubbles: true }));
  })()`)
  const removeFirstVisible = async () => {
    await evaluate("document.querySelector('tbody .row-actions .btn-danger').click()")
    await evaluate("document.querySelector('[role=dialog] .btn-danger').click()")
  }
  const matches = n => `Найдено в загруженном списке: ${n}`
  const page = (n, total) => `Страница ${n} из ${total}`
  await mustShow('Синтетика ALFA')
  await waitFor(() => evaluate(`document.querySelectorAll('.recharts-area path').length >= 2`), 'nonempty chart')
  await pause(1800)
  const saveEvidence = async name => { await writeFile(path.join(runDir, `${name}.html`), await evaluate('document.documentElement.outerHTML')); const image = await call('Page.captureScreenshot', {format:'png'}); await writeFile(path.join(runDir, `${name}.png`), Buffer.from(image.data, 'base64')) }
  if (demo) { await evaluate(`document.querySelector('.positions-search').scrollIntoView({block:'center'})`); await saveEvidence('demo-dashboard'); console.log('Демо открыто на дашборде. Для завершения нажмите Enter.'); await Promise.race([new Promise(resolve => process.stdin.once('data', resolve)), closed]); process.stdin.pause() } else {
  const shot = await call('Page.captureScreenshot', { format: 'png' }); await writeFile(path.join(runDir, 'red.png'), Buffer.from(shot.data, 'base64')); await writeFile(path.join(runDir, 'red-dom.txt'), await body());
  assert.ok(await evaluate(`Boolean([...document.querySelectorAll('label')].find(x => x.textContent.includes('Поиск по позициям'))?.querySelector('input'))`), 'ready dashboard must have labeled position search');
  const invariant = () => evaluate(`({url:location.href, selected:document.querySelector('.portfolio-select select').value, stats:document.querySelector('.stats-grid').innerText, graph:[...document.querySelectorAll('.recharts-area path')].map(x=>x.getAttribute('d'))})`)
  const beforeFilter = apiLog.length
  const initialInvariant = await invariant()
  assert.equal(initialInvariant.selected, '101'); assert.ok(initialInvariant.graph.length >= 2 && initialInvariant.graph.every(Boolean), 'nonempty chart paths')
  const checkInvariant = async () => { assert.deepEqual(await invariant(), initialInvariant); assert.equal(apiLog.length, beforeFilter) }
  assert.deepEqual(await tickers(), ['ALFA','BRK.B','GAMMA'])
  await input('Поиск по позициям', '  лУнНыЙ  '); await waitFor(async () => JSON.stringify(await tickers()) === JSON.stringify(['GAMMA']), 'name filter'); await checkInvariant()
  await input('Поиск по позициям', '  brk.b '); await waitFor(async () => JSON.stringify(await tickers()) === JSON.stringify(['BRK.B']), 'null name ticker'); await checkInvariant()
  const links = await evaluate(`[...document.querySelectorAll('tbody tr a')].map(a => a.getAttribute('href'))`)
  assert.deepEqual(links, ['/security/BRK.B','/transactions?ticker=BRK.B'])
  await input('Поиск по позициям', 'null'); await mustShow('По запросу позиции не найдены'); assert.deepEqual(await tickers(), []); await checkInvariant()
  await click('Сбросить поиск'); await waitFor(async () => JSON.stringify(await tickers()) === JSON.stringify(['ALFA','BRK.B','GAMMA']), 'reset')
  await checkInvariant(); console.log('PASS unchanged URL, selected portfolio, stats, nonempty chart and API', JSON.stringify(initialInvariant))
  await call('Emulation.setDeviceMetricsOverride', {width:375,height:720,deviceScaleFactor:1,mobile:true})
  await pause(600) // Allow ResponsiveContainer's resize observer to apply the new viewport.
  await input('Поиск по позициям','нет-совпадений'); await mustShow('По запросу позиции не найдены')
  await evaluate(`document.querySelector('.positions-search').scrollIntoView({block:'center'})`)
  const noMatch = 'По запросу позиции не найдены. Сбросьте поиск, чтобы показать все позиции.'
  const mobileState = await evaluate(`(() => {
    const search = document.querySelector('.positions-search');
    const card = search.closest('.card');
    const heading = card?.querySelector('h2')?.textContent.trim();
    const statuses = [...(card?.querySelectorAll('[role=status]') ?? [])];
    const messages = statuses.map(node => node.textContent.trim());
    if (heading !== 'Позиции' || messages.length !== 1 || messages[0] !== ${JSON.stringify(noMatch)}) return {heading,messages};
    const box = node => {
      const {left,right,top,bottom,width,height} = node.getBoundingClientRect();
      const {visibility,display} = getComputedStyle(node);
      return {left,right,top,bottom,width,height,visible:visibility,display};
    };
    return {heading,messages,width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,
      input:box(search.querySelector('input')),button:box(search.querySelector('button')),result:box(statuses[0])};
  })()`)
  console.log('375px geometry',JSON.stringify(mobileState)); await saveEvidence('mobile-no-match')
  assert.equal(mobileState.heading, 'Позиции')
  assert.deepEqual(mobileState.messages, [noMatch], 'no-match status belongs to the positions card')
  assert.equal(mobileState.width,375); assert.equal(mobileState.scroll,375)
  for (const name of ['input','button','result']) { const r=mobileState[name]; assert.ok(r.width > 0 && r.height > 0 && r.left >= 0 && r.right <= 375 && r.top >= 0 && r.bottom <= 720 && r.visible === 'visible' && r.display !== 'none', `${name} visible at 375px`) }
  assert.deepEqual(await tickers(), [])
  assert.doesNotMatch(await body(), /Портфель пуст/)
  const mobileKey = async (name, code) => { await call('Input.dispatchKeyEvent', {type:'keyDown',key:name,code:name,windowsVirtualKeyCode:code,...(name === 'Enter' ? {text:String.fromCharCode(13),unmodifiedText:String.fromCharCode(13)} : {})}); await call('Input.dispatchKeyEvent', {type:'keyUp',key:name,code:name,windowsVirtualKeyCode:code}) }
  await evaluate('document.activeElement.blur()')
  let resetFocused = false
  for (let i=0;i<50;i++) { await mobileKey('Tab',9); if (await evaluate(`document.activeElement === document.querySelector('.positions-search button')`)) { resetFocused=true; break } }
  assert.ok(resetFocused, 'reset reachable by Tab'); await mobileKey('Enter',13)
  await waitFor(async () => JSON.stringify(await tickers()) === JSON.stringify(['ALFA','BRK.B','GAMMA']), 'keyboard reset')
  assert.equal(await evaluate(`document.querySelector('.positions-search input').value`), '')
  assert.equal(apiLog.length, beforeFilter, 'mobile search and keyboard reset do not request the API')
  await saveEvidence('mobile-keyboard-reset'); console.log('PASS mobile input, reset, no-match, Tab/Enter reset')
  await call('Emulation.clearDeviceMetricsOverride')
  await input('Поиск по позициям', 'ALFA')
  console.log('PASS search name ticker null no-match reset and mobile')
  const hold = () => { let resolve; const promise = new Promise(r => { resolve = r }); return {promise, resolve} }
  let b = hold(); pendingMetrics.set(102,b)
  await input('Портфель:', '102')
  await waitFor(() => apiLog.some(x => x === 'GET /api/portfolios/102/metrics'), 'B metrics request')
  assert.doesNotMatch(await body(), /Синтетика ALFA/)
  assert.equal(await evaluate("document.querySelectorAll('tbody a').length"),0)
  b.resolve(); await mustShow('Синтетика BETA'); pendingMetrics.delete(102)
  assert.equal(await evaluate(`document.querySelector('.positions-search input').value`), '')
  assert.deepEqual(await tickers(), ['BETA','DELTA','OMEGA'])
  let a = hold(); pendingMetrics.set(101,a)
  const n = apiLog.filter(x => x === 'GET /api/portfolios/101/metrics').length
  await input('Портфель:', '101')
  await waitFor(() => apiLog.filter(x => x === 'GET /api/portfolios/101/metrics').length > n, 'delayed A')
  await input('Портфель:', '102')
  await mustShow('Синтетика BETA')
  const done = fulfilled.filter(x => x.key === 'GET /api/portfolios/101/metrics').length
  a.resolve(); await waitFor(() => fulfilled.filter(x => x.key === 'GET /api/portfolios/101/metrics').length > done, 'late A delivered')
  await evaluate('new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => r(true))))')
  assert.match(await body(), /Синтетика BETA/); assert.doesNotMatch(await body(), /Синтетика ALFA/)
  assert.deepEqual(await tickers(), ['BETA','DELTA','OMEGA'])
  console.log('PASS dashboard B held and late A delivered, no stale actions')
  await input('Поиск по позициям','beta'); await waitFor(async () => JSON.stringify(await tickers()) === JSON.stringify(['BETA']), 'filtered BETA')
  const key = async (name, code) => { await call('Input.dispatchKeyEvent', {type:'keyDown',key:name,code:name,windowsVirtualKeyCode:code,...(name === 'Enter' ? {text:String.fromCharCode(13),unmodifiedText:String.fromCharCode(13)} : {})}); await call('Input.dispatchKeyEvent', {type:'keyUp',key:name,code:name,windowsVirtualKeyCode:code}) }
  await evaluate('document.activeElement.blur()')
  let focused = false
  for (let i=0;i<50;i++) { await key('Tab',9); if (await evaluate(`document.activeElement?.getAttribute('href') === '/transactions?ticker=BETA'`)) {focused=true;break} }
  assert.ok(focused, 'filtered transactions link keyboard reachable')
  await key('Enter',13); await waitFor(() => evaluate(`location.pathname === '/transactions' && location.search === '?ticker=BETA'`), 'exact journal URL')
  await mustShow('Точный тикер: BETA'); await mustShow('Найдено в загруженном списке: 1')
  assert.deepEqual(await tickers(), ['BETA']); assert.match(await body(), /31/); assert.doesNotMatch(await body(), /BETA-P/)
  assert.match(await body(), /Сделки — Синтетика B/); assert.ok(apiLog.includes('GET /api/portfolios/102/transactions'))
  assert.equal(await evaluate(`document.querySelector('tbody tr').cells[3].innerText.trim()`), '31')
  await saveEvidence('exact-journal-b')
  console.log('PASS Tab Enter exact current portfolio journal')
  }
  assert.deepEqual(clientErrors, [])
  assert.deepEqual(interceptionErrors, [])
  assert.deepEqual(unknown, [])
  console.log(`Synthetic API calls: ${apiLog.length}; unknown: ${unknown.length}`)
} finally {
  try {
    if (browser?.pid && browser.exitCode === null && browser.signalCode === null) {
      if (ws?.readyState === WebSocket.OPEN) await Promise.race([call('Browser.close').catch(() => {}), pause(500)])
      await Promise.race([closed, pause(1200)])
      if (browser.exitCode === null && browser.signalCode === null) {
        browser.kill()
        await Promise.race([closed, pause(2000)])
      }
      assert.ok(browser.exitCode !== null || browser.signalCode !== null, 'Isolated Chrome did not exit')
    }
  } finally {
    ws?.close()
    await server?.close()
    for (const activePort of [listening ? port : undefined, debugPort || undefined].filter(Boolean)) {
      await waitFor(() => portClosed(activePort), `port closed: ${activePort}`)
      console.log(`Closed 127.0.0.1:${activePort}`)
    }
    for (const directory of [profile, cacheDir]) await rm(directory, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 })
  }
}
