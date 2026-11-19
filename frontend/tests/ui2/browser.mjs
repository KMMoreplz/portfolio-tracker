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
const scratch = path.resolve(process.env.UI2_SCRATCH || path.join(homedir(), 'AppData/Local/hermes/cache/scratch'))
const chromePath = process.env.UI2_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const demo = process.argv.includes('--demo')
const only = process.argv.find(arg => arg.startsWith('--case='))?.slice(7)
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
const isApi = pathname => /^\/(api|market)(?:\/|$)/.test(pathname)
const denied = 'UI.2: intercepted synthetic API only'

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
const rowsB = sequence(1, 12, 1).map(id => fixture(id, 102))
const apiLog = []
const unknown = []
const port = await freePort()
const origin = `http://127.0.0.1:${port}`
const project = path.resolve(frontend, '..').toLowerCase()
assert.ok(scratch.toLowerCase() !== project && !scratch.toLowerCase().startsWith(project + path.sep), 'Use scratch outside the project')
await mkdir(scratch, { recursive: true })
const runDir = await mkdtemp(path.join(scratch, 'ui2-run-'))
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
  console.log(`UI.2 ${origin}/transactions; CDP ${debugPort}; scratch ${runDir}`)
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
      profit: 0, profitPercent: null, dividendsReceived: 0, positions: [], calculatedAt: '2026-01-01T00:00:00Z',
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
  const navigation = await call('Page.navigate', { url: `${origin}/transactions` })
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
  await mustShow('A-SELL-27')
  if (demo) {
    await click('Далее')
    await mustShow(page(2, 3))
    console.log('Демо открыто на странице 2 из 3; для выхода нажмите Enter.')
    await Promise.race([new Promise(resolve => process.stdin.once('data', resolve)), closed])
    process.stdin.pause()
  } else {
    const scenarios = new Map()
    scenarios.set('pages', async () => {
      const requestsBefore = apiLog.length
      const expected = sequence(27, 1).map(aTicker)
      const first = await tickers()
      assert.equal(first.length, 10, 'First page must show 10 of 27 loaded operations')
      assert.deepEqual(first, expected.slice(0, 10))
      await mustShow(matches(27)); await mustShow(page(1, 3))
      assert.equal(await state('Назад'), true)
      await click('Далее'); await mustShow(page(2, 3))
      const second = await tickers()
      assert.deepEqual(second, expected.slice(10, 20))
      await click('Далее'); await mustShow(page(3, 3))
      assert.equal(await state('Далее'), true)
      const last = await tickers()
      assert.deepEqual(last, expected.slice(20))
      assert.deepEqual([...first, ...second, ...last], expected)
      await click('Назад'); await mustShow(page(2, 3))
      assert.equal(apiLog.length, requestsBefore, 'Page navigation must not call the API')
    })
    scenarios.set('filters', async () => {
      const start = apiLog.length
      const expectFirst = async (label, total, expected) => {
        assert.match(await body(), new RegExp(page(1, Math.ceil(total / 10))), `${label} must reset to page 1`)
        await mustShow(matches(total))
        assert.deepEqual(await tickers(), expected)
      }
      const next = async total => { await click('Далее'); await mustShow(page(2, total)) }
      const reset = async () => { await click('Сбросить фильтры'); await mustShow(matches(27)) }
      await next(3)
      await input('Поиск по тикеру', ' a-buy ')
      await expectFirst('Ticker', 13, sequence(26, 8, -2).map(aTicker))
      await next(2)
      assert.deepEqual(await tickers(), [6, 4, 2].map(aTicker))
      await input('Сортировка', 'asc')
      await expectFirst('Sort', 13, sequence(2, 20, 2).map(aTicker))
      await next(2)
      await reset()
      await expectFirst('Reset', 27, sequence(27, 18).map(aTicker))
      await next(3)
      await input('Вид операции', 'SELL')
      await expectFirst('Type', 14, sequence(27, 9, -2).map(aTicker))
      await reset()
      await next(3)
      await input('С даты', '2026-03-09')
      await expectFirst('Start date', 13, sequence(27, 18).map(aTicker))
      await reset()
      await next(3)
      await input('По дату', '2026-03-08')
      await expectFirst('End date', 14, sequence(14, 5).map(aTicker))
      assert.equal(apiLog.length, start, 'Filter/sort changes must not call the API')
    })
    scenarios.set('shrink', async () => {
      const original = rowsA.slice()
      try {
        await click('Далее'); await mustShow(page(2, 3))
        await click('Далее'); await mustShow(page(3, 3))
        await removeFirstVisible()
        await mustShow(matches(26)); await mustShow(page(3, 3))
        assert.equal((await tickers()).length, 6, 'A surviving partial page must remain selected')

        // Simulate concurrent changes in the list returned after the deletion.
        rewriteA = original.slice(0, 20)
        await removeFirstVisible()
        await mustShow(matches(20)); await mustShow(page(2, 2))
        assert.deepEqual(await tickers(), sequence(10, 1).map(aTicker))
        rewriteA = original
        await removeFirstVisible()
        await mustShow(matches(27))
        assert.match(await body(), /Страница 2 из 3/, 'Growing after a clamp must not restore the old page 3')

        rewriteA = original.slice(0, 1)
        await removeFirstVisible()
        await mustShow(matches(1))
        assert.deepEqual(await tickers(), [aTicker(1)])
        assert.doesNotMatch(await body(), /Страница \d/)
        await removeFirstVisible()
        await mustShow(matches(0)); await mustShow('Операций пока нет')
        assert.deepEqual(await tickers(), [])
        assert.doesNotMatch(await body(), /Страница \d|По фильтрам ничего не найдено/)
      } finally {
        rowsA.splice(0, rowsA.length, ...original)
      }
    })
    scenarios.set('states', async () => {
      await input('Поиск по тикеру', 'unknown')
      await mustShow(matches(0)); await mustShow('По фильтрам ничего не найдено')
      assert.doesNotMatch(await body(), /Операций пока нет|Страница \d/)
      await click('Сбросить фильтры')
      await input('С даты', '2026-03-10'); await input('По дату', '2026-03-09')
      await mustShow('Дата начала позже даты окончания')
      assert.doesNotMatch(await body(), /Найдено в загруженном списке|Операций пока нет|По фильтрам ничего не найдено|Страница \d/)

      let finish
      const held = new Promise(resolve => { finish = resolve })
      getA = () => held
      const observer = await call('Page.addScriptToEvaluateOnNewDocument', { source: `
        window.ui2Added = [];
        new MutationObserver(records => {
          for (const record of records) for (const node of record.addedNodes) {
            const text = node.textContent?.match(/Найдено в загруженном списке: 0|Операций пока нет/);
            if (text) window.ui2Added.push(text[0]);
          }
        }).observe(document, { childList: true, subtree: true });
      ` })
      try {
        await reloadAndWaitFor("document.body?.innerText.includes('Загрузка операций')", 'new document showing transaction loading')
        const inFlight = await body()
        assert.doesNotMatch(inFlight, /Найдено в загруженном списке|Операций пока нет|По фильтрам ничего не найдено|Страница \d/)
        assert.doesNotMatch(await evaluate('window.ui2Added.join("\\n")'), /Найдено в загруженном списке: 0|Операций пока нет/,
          'Initial loading must not insert a transient empty result')
        finish(new FixtureError('Синтетическая ошибка UI.2'))
        await mustShow('Синтетическая ошибка UI.2')
        assert.doesNotMatch(await body(), /Найдено в загруженном списке|Операций пока нет|По фильтрам ничего не найдено|Страница \d/)
        getA = () => rowsA
        await click('Повторить'); await mustShow(page(1, 3))
      } finally {
        getA = () => rowsA
        finish(rowsA)
        await call('Page.removeScriptToEvaluateOnNewDocument', { identifier: observer.identifier })
      }
    })
    scenarios.set('mobile', async () => {
      await call('Emulation.setDeviceMetricsOverride', { width: 375, height: 720, deviceScaleFactor: 1, mobile: true })
      try {
        const dimensions = await evaluate(`(() => {
          const nav = document.querySelector('nav[aria-label="Страницы операций"]');
          const rect = element => element.getBoundingClientRect().toJSON();
          return { width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth,
            summary: rect(document.querySelector('.transactions-summary')), count: rect(document.querySelector('.transactions-summary [role=status]')),
            controls: [...nav.children].map(rect) };
        })()`)
        assert.equal(dimensions.width, 375)
        assert.equal(dimensions.scroll, dimensions.width, 'The page itself must not scroll horizontally')
        const { summary, count, controls } = dimensions
        for (const { left, top, right, bottom } of [count, ...controls]) {
          assert.ok(left >= summary.left && top >= summary.top && right <= dimensions.width && bottom <= 720, 'Summary controls must fit on-screen')
        }
        for (let i = 1; i < controls.length; i++) {
          const previous = controls[i - 1], current = controls[i]
          assert.ok(current.left - previous.right >= 8 || current.top - previous.bottom >= 8,
            `Mobile controls need spacing, actual horizontal gap ${current.left - previous.right}`)
        }
        const key = async (code, value) => {
          await call('Input.dispatchKeyEvent', { type: 'keyDown', key: code, code, windowsVirtualKeyCode: value,
            ...(code === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) })
          await call('Input.dispatchKeyEvent', { type: 'keyUp', key: code, code, windowsVirtualKeyCode: value })
        }
        const tabTo = async text => {
          for (let i = 0; i < 30; i++) {
            await key('Tab', 9)
            if (await evaluate(`document.activeElement instanceof HTMLButtonElement && document.activeElement.textContent.trim() === ${JSON.stringify(text)}`)) return
          }
          assert.fail(`Not reachable by Tab: ${text}`)
        }
        await tabTo('Далее'); await key('Enter', 13); await mustShow(page(2, 3))
        await evaluate("document.querySelector('.transactions-pagination').previousElementSibling.scrollIntoView({ block: 'center' })")
        const shot = await call('Page.captureScreenshot', { format: 'png' })
        await writeFile(path.join(runDir, 'ui2-mobile-page2.png'), Buffer.from(shot.data, 'base64'))
        // Restart sequential focus before the pagination, then activate the now-enabled Back button.
        await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Сбросить фильтры').focus()")
        await tabTo('Назад'); await key('Enter', 13); await mustShow(page(1, 3))
        const money = await evaluate("[...document.querySelector('tbody tr').cells].slice(3,7).map(td => td.innerText.trim())")
        assert.deepEqual(money, ['2', '11,00 RUB', '0,50', '22,00 RUB'])
      } finally { await call('Emulation.clearDeviceMetricsOverride') }
    })
    scenarios.set('race', async () => {
      const link = text => evaluate(`([...document.querySelectorAll('a')].find(a => a.textContent.trim() === ${JSON.stringify(text)})).click()`)
      const portfolio = async id => {
        await link('Дашборд'); await mustShow('Портфель:')
        await input('Портфель:', String(id)); await link('Сделки')
      }
      const hold = () => {
        let resolve
        const response = new Promise(complete => { resolve = complete })
        return { response, resolve }
      }
      const aRequest = 'GET /api/portfolios/101/transactions'
      const bRequest = 'GET /api/portfolios/102/transactions'
      const next = async () => { await click('Далее'); await mustShow(page(2, 3)) }
      const original = rowsA.slice()
      try {
        // Exercise both orderings: stale success after B's results, stale error while B is still loading.
        for (const lateError of [false, true]) {
          await next()
          const oldA = hold()
          const currentB = hold()
          const startedA = apiLog.filter(key => key === aRequest).length
          const finishedA = fulfilled.filter(event => event.key === aRequest).length
          const startedB = apiLog.filter(key => key === bRequest).length
          getA = () => oldA.response
          getB = () => currentB.response
          try {
            await removeFirstVisible()
            await waitFor(() => apiLog.filter(key => key === aRequest).length > startedA, 'A refresh requested from page 2')
            await portfolio(102)
            await mustShow('Синтетика B'); await mustShow('Загрузка операций')
            await waitFor(() => apiLog.filter(key => key === bRequest).length > startedB, 'B list requested')
            assert.doesNotMatch(await body(), /A-(BUY|SELL)|Найдено в загруженном списке|Страница \d/)
            if (!lateError) {
              currentB.resolve(rowsB)
              await mustShow(page(1, 2)); await mustShow(matches(12))
            }
            const responseCode = lateError ? 500 : 200
            oldA.resolve(lateError ? new FixtureError('Запоздалая ошибка A') : original)
            await waitFor(() => {
              const completed = fulfilled.filter(event => event.key === aRequest).slice(finishedA)
              return completed.some(event => event.responseCode === responseCode)
            }, 'Stale A response delivered')
            await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))))')
            if (lateError) {
              assert.match(await body(), /Загрузка операций/)
              assert.doesNotMatch(await body(), /Запоздалая ошибка A|Найдено в загруженном списке|A-(BUY|SELL)/)
              currentB.resolve(rowsB)
            }
            await mustShow(page(1, 2)); await mustShow(matches(12))
            assert.deepEqual(await tickers(), sequence(12, 3).map(bTicker))
            assert.doesNotMatch(await body(), /Запоздалая ошибка A|A-(BUY|SELL)/)
          } finally {
            oldA.resolve(original); currentB.resolve(rowsB)
            getA = () => rowsA; getB = () => rowsB
          }
          rowsA.splice(0, rowsA.length, ...original)
          await portfolio(101)
          await mustShow('A-SELL-27'); await mustShow(page(1, 3))
        }
      } finally { rowsA.splice(0, rowsA.length, ...original) }
    })
    assert.ok(!only || scenarios.has(only), `Unknown scenario: ${only}`)
    for (const [name, scenario] of scenarios) {
      if (only && name !== only) continue
      await reloadAndWaitFor("document.querySelector('tbody .ticker-link')?.textContent === 'A-SELL-27'", `new document with loaded transactions for ${name}`)
      await scenario()
      console.log(`PASS ${name}`)
    }
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
