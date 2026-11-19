import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer, createConnection } from 'node:net'
import { homedir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer as createViteServer } from 'vite'
import react from '@vitejs/plugin-react'

const frontend = fileURLToPath(new URL('../../', import.meta.url))
const scratch = path.resolve(process.env.UI3_SCRATCH || path.join(homedir(), 'AppData/Local/hermes/cache/scratch'))
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
const rowsA = sequence(1, 14, 1).map(id => ({ ...fixture(id, 101), ticker: id === 14 ? 'ALFA-P' : 'ALFA' }))
const rowsB = sequence(1, 12, 1).map(id => fixture(id, 102))
const apiLog = []
const unknown = []
const port = await freePort()
const origin = `http://127.0.0.1:${port}`
const project = path.resolve(frontend, '..').toLowerCase()
assert.ok(scratch.toLowerCase() !== project && !scratch.toLowerCase().startsWith(project + path.sep), 'Use scratch outside the project')
await mkdir(scratch, { recursive: true })
const runDir = await mkdtemp(path.join(scratch, 'ui3-run-'))
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
  console.log(`UI.3 ${origin}/transactions; CDP ${debugPort}; scratch ${runDir}`)
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
    fixtureRoutes.set(`GET /api/portfolios/${id}/metrics`, () => ({
      portfolioId: id, portfolioName: 'Synthetic', baseCurrency: 'RUB', totalValue: 0, totalCost: 0,
      profit: 0, profitPercent: null, dividendsReceived: 0, positions: [{ ticker: 'ALFA', instrumentName: 'Синтетика ALFA', quantity: 1, averagePrice: 11, currentPrice: 12, priceSource: 'fixture', currency: 'RUB', valueInBase: 12, weightPercent: 100, profit: 1, profitPercent: 9 }], calculatedAt: '2026-01-01T00:00:00Z',
    }))
    fixtureRoutes.set(`GET /api/portfolios/${id}/value-history?days=30`, () => ({
      portfolioId: id, baseCurrency: 'RUB', priceSource: 'fixture', points: [],
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
  if (demo) { console.log('Демо открыто на дашборде. Для завершения нажмите Enter.'); await Promise.race([new Promise(resolve => process.stdin.once('data', resolve)), closed]); process.stdin.pause() } else {
  const shot = await call('Page.captureScreenshot', { format: 'png' })
  const shotPath = path.join(runDir, 'dashboard.png')
  await writeFile(shotPath, Buffer.from(shot.data, 'base64'))
  const row = await evaluate(`(() => { const r = [...document.querySelectorAll('tbody tr')].find(x => x.innerText.includes('ALFA')); return { text:r?.innerText, links:[...r.querySelectorAll('a')].map(a=>({text:a.innerText.trim(),href:a.getAttribute('href')})) } })()`)
  console.log('Dashboard row actual:', JSON.stringify(row), 'screenshot:', shotPath)
  assert.ok(row.links.some(x => x.text === 'ALFA' && x.href === '/security/ALFA'), 'security link exists')
  assert.ok(row.links.some(x => x.text === 'Сделки' && x.href === '/transactions?ticker=ALFA'), 'position row needs a separate Сделки link to transactions?ticker=ALFA')
  await call('Emulation.setDeviceMetricsOverride', { width: 375, height: 720, deviceScaleFactor: 1, mobile: true })
  const dimensions = await evaluate(`({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth })`)
  assert.equal(dimensions.width, dimensions.scroll, 'No horizontal document overflow on mobile')
  const key = async (name, code) => {
    await call('Input.dispatchKeyEvent', { type: 'keyDown', key: name, code: name, windowsVirtualKeyCode: code, ...(name === 'Enter' ? { text: String.fromCharCode(13), unmodifiedText: String.fromCharCode(13) } : {}) })
    await call('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code: name, windowsVirtualKeyCode: code })
  }
  let focused = false
  for (let i = 0; i < 40; i++) {
    await key('Tab', 9)
    if (await evaluate("document.activeElement?.getAttribute('href') === '/transactions?ticker=ALFA'")) { focused = true; break }
  }
  assert.ok(focused, 'Position action is reachable by Tab')
  const activeRect = await evaluate("document.activeElement.getBoundingClientRect().toJSON()")
  assert.ok(activeRect.left >= 0 && activeRect.right <= 375 && activeRect.width > 0, 'Action fits mobile viewport')
  const mobile = await call('Page.captureScreenshot', { format: 'png' })
  await writeFile(path.join(runDir, 'mobile-dashboard.png'), Buffer.from(mobile.data, 'base64'))
  await key('Enter', 13)
  await waitFor(() => evaluate("location.pathname === '/transactions' && location.search === '?ticker=ALFA'"), 'transactions URL')
  await call('Emulation.clearDeviceMetricsOverride')
  await mustShow('Найдено в загруженном списке: 13')
  await mustShow('Точный тикер: ALFA')
  assert.deepEqual(await tickers(), Array(10).fill('ALFA'))
  await click('Далее'); await mustShow('Страница 2 из 2')
  assert.deepEqual(await tickers(), Array(3).fill('ALFA'))
  const loadedRequests = apiLog.length
  await input('Вид операции', 'BUY'); await mustShow('Найдено в загруженном списке: 6')
  await input('Вид операции', ''); await mustShow('Найдено в загруженном списке: 13')
  assert.match(await body(), /Страница 1 из 2/)
  await input('С даты', '2026-03-09'); await mustShow('По фильтрам ничего не найдено')
  await mustShow('Точный тикер: ALFA')
  await input('С даты', ''); await mustShow('Найдено в загруженном списке: 13')
  await input('Сортировка', 'asc'); await mustShow('Страница 1 из 2')
  await input('Поиск по тикеру', 'alf'); await mustShow('Найдено в загруженном списке: 14')
  assert.doesNotMatch(await body(), /Точный тикер:/)
  assert.equal(apiLog.length, loadedRequests, 'Local filters and pages do not request the API')
  await click('Сбросить фильтры'); await mustShow('Найдено в загруженном списке: 14')
  await input('Поиск по тикеру', 'alf'); await mustShow('Найдено в загруженном списке: 14')
  const before = apiLog.length
  await input('Поиск по тикеру', '');
  await evaluate("history.pushState({}, '', '/transactions?ticker=BRK.B'); dispatchEvent(new PopStateEvent('popstate'))")
  await mustShow('По фильтрам ничего не найдено'); await mustShow('Точный тикер: BRK.B')
  await evaluate("history.back()"); await mustShow('Найдено в загруженном списке: 14')
  await evaluate("history.forward()"); await mustShow('Точный тикер: BRK.B')
  assert.equal(apiLog.length, before, 'local URL navigation must not refetch list')
  await reloadAndWaitFor("document.body?.innerText.includes('Точный тикер: BRK.B') && document.body?.innerText.includes('По фильтрам ничего не найдено')", 'reload exact filter')
  await call('Page.navigate', {url: origin + '/transactions?ticker=ALFA&ticker=ALFA-P'})
  await mustShow('Найдено в загруженном списке: 14')
  assert.doesNotMatch(await body(), /Точный тикер:/)
  rowsA.push({ ...fixture(15, 101), ticker: 'BRK.B' })
  const documentBeforeTicker = await evaluate('performance.timeOrigin')
  await call('Page.navigate', { url: origin + '/transactions?ticker=brk.b' })
  await waitFor(() => evaluate(`performance.timeOrigin !== ${documentBeforeTicker} && document.body?.innerText.includes('Найдено в загруженном списке: 1') && document.body?.innerText.includes('BRK.B')`), 'punctuation ticker')
  assert.deepEqual(await tickers(), ['BRK.B'])
  const documentBeforeBlank = await evaluate('performance.timeOrigin')
  await call('Page.navigate', { url: origin + '/transactions?ticker=%20' })
  await waitFor(() => evaluate(`performance.timeOrigin !== ${documentBeforeBlank} && document.body?.innerText.includes('Найдено в загруженном списке: 15')`), 'blank ticker')
  assert.doesNotMatch(await body(), /Точный тикер:/)
  console.log('PASS exact, pagination, reset, no match, Back/Forward, reload, repeated ticker')
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
