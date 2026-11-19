import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises'
import { createConnection, createServer } from 'node:net'
import { homedir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { createServer as createViteServer } from 'vite'

const frontend = fileURLToPath(new URL('../../', import.meta.url))
const loopback = '127.0.0.1'
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
const isApi = pathname => /^\/(api|market)(?:\/|$)/.test(pathname)
const denial = 'UI.5: synthetic API is only available in the intercepted test tab'

export async function until(predicate, label, timeout = 9000) {
  const end = Date.now() + timeout
  do {
    const value = await predicate()
    if (value) return value
    await pause(40)
  } while (Date.now() < end)
  assert.fail(`Timed out: ${label}`)
}

async function freePort() {
  const probe = createServer()
  await new Promise((resolve, reject) => probe.once('error', reject).listen(0, loopback, resolve))
  const { port } = probe.address()
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()))
  return port
}

function portClosed(port) {
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host: loopback, port })
    socket.once('connect', () => { socket.destroy(); resolve(false) })
    socket.once('error', error => error.code === 'ECONNREFUSED' ? resolve(true) : reject(error))
  })
}

function within(child, parent) {
  const rel = path.relative(parent, child)
  return rel !== '..' && !rel.startsWith(`..${path.sep}`) && !path.isAbsolute(rel)
}

async function connectCDP(url, onEvent) {
  const ws = new WebSocket(url)
  const pending = new Map()
  let serial = 0
  const call = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++serial
    const timer = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)) }, 6000)
    pending.set(id, { resolve, reject, timer })
    try { ws.send(JSON.stringify({ id, method, params })) }
    catch (error) { clearTimeout(timer); pending.delete(id); reject(error) }
  })
  ws.onmessage = event => {
    const message = JSON.parse(event.data)
    if (!message.id) return onEvent(message, call)
    const command = pending.get(message.id)
    if (!command) return
    pending.delete(message.id)
    clearTimeout(command.timer)
    if (message.error) command.reject(Error(JSON.stringify(message.error)))
    else command.resolve(message.result)
  }
  ws.onclose = () => {
    for (const { reject, timer } of pending.values()) { clearTimeout(timer); reject(Error('CDP disconnected')) }
    pending.clear()
  }
  try {
    await Promise.race([
      new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = () => reject(Error('CDP connection failed')) }),
      pause(6000).then(() => { throw Error('CDP connection timed out') }),
    ])
  } catch (error) {
    ws.close()
    throw error
  }
  return { call, close: () => ws.close(), open: () => ws.readyState === WebSocket.OPEN }
}

export async function runBrowser({ routes, demo }, scenario) {
  assert.ok(process.env.UI5_SCRATCH, 'Set UI5_SCRATCH to an existing external directory in Hermes scratch')
  const scratch = await realpath(process.env.UI5_SCRATCH)
  const scratchRoot = await realpath(path.resolve(homedir(), 'AppData/Local/hermes/cache/scratch'))
  const project = await realpath(path.resolve(frontend, '..'))
  assert.ok(within(scratch, scratchRoot) && path.relative(scratchRoot, scratch), 'Use a subdirectory of Hermes scratch')
  assert.ok(!within(scratch, project), 'Scratch must be outside the source project')
  const runDir = await mkdtemp(path.join(scratch, 'ui5-run-'))
  const profile = path.join(runDir, 'chrome-profile')
  const cacheDir = path.join(runDir, 'vite-cache')
  for (const name of ['TEMP', 'TMP', 'TMPDIR']) process.env[name] = runDir
  const port = await freePort()
  const origin = `http://${loopback}:${port}`
  const api = [], blocked = [], errors = [], exceptions = [], probes = [], closedPorts = [], screenshotWarnings = []
  const sha = async url => createHash('sha256').update(await readFile(url)).digest('hex')
  const sources = Object.fromEntries(await Promise.all([
    ['browser.mjs', new URL('./browser.mjs', import.meta.url)],
    ['harness.mjs', new URL(import.meta.url)],
    ['CalendarPage.tsx', new URL('../../src/pages/CalendarPage.tsx', import.meta.url)],
  ].map(async ([name, url]) => [name, await sha(url)])))
  let server, browser, closed, cdp, debugPort, save
  let listening = false
  let stopping = false
  console.log(`Run directory: ${runDir}`)

  async function intercept({ request, requestId, resourceType }, call) {
    const url = new URL(request.url)
    const key = `${request.method} ${url.href.slice(url.origin.length)}`
    const outside = url.origin !== origin
    if (outside || (!isApi(url.pathname) && request.method !== 'GET')) {
      blocked.push({ outside, url: request.url, method: request.method, resourceType })
      return call('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' })
    }
    // Only local Vite documents/assets continue; API never reaches Vite from the test tab.
    if (!isApi(url.pathname)) return call('Fetch.continueRequest', { requestId })
    const handler = routes.get(key)
    if (!handler) blocked.push({ outside, key, resourceType })
    const entry = { key, fixture: Boolean(handler), marker: String(api.length + 1) }
    api.push(entry)
    const { status = 200, body, label } = handler
      ? await handler(entry)
      : { status: 403, body: { message: 'Unexpected fixture request' } }
    Object.assign(entry, { status, label })
    await call('Fetch.fulfillRequest', {
      requestId, responseCode: status,
      responseHeaders: [
        { name: 'Content-Type', value: 'application/json; charset=utf-8' },
        { name: 'Cache-Control', value: 'no-store' },
        { name: 'X-UI5-Fixture', value: entry.marker },
      ],
      body: Buffer.from(JSON.stringify(body)).toString('base64'),
    })
    entry.fulfilled = true
  }

  try {
    server = await createViteServer({
      root: frontend, configFile: false, envFile: false, cacheDir,
      plugins: [react(), {
        name: 'ui5-deny-direct-api',
        configureServer(vite) {
          vite.middlewares.use((req, res, next) => {
            if (!isApi(new URL(req.url, origin).pathname)) return next()
            res.writeHead(403, { 'Content-Type': 'text/plain' })
            res.end(denial)
          })
        },
      }],
      server: { host: loopback, port, strictPort: true, proxy: {}, hmr: false, fs: { allow: [frontend, runDir] } },
    })
    assert.deepEqual(server.config.server.proxy, {})
    await server.listen()
    listening = true
    await writeFile(path.join(runDir, 'vite-port.txt'), String(port))

    const preflight = async () => {
      for (const [method, url] of [['GET', '/api/auth/me'], ['POST', '/api/portfolios/101/transactions'], ['GET', '/market/quotes/SYNTH']]) {
        const reply = await fetch(origin + url, { method, redirect: 'error', signal: AbortSignal.timeout(3000) })
        const body = await reply.text()
        probes.push({ method, url, status: reply.status, body })
        assert.equal(reply.status, 403, `${method} ${url}`)
        assert.equal(body, denial)
        console.log(`Preflight ${method} ${url}: 403 local`)
      }
    }
    await preflight()

    browser = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', [
      ...(demo ? [] : ['--headless=new']), '--disable-gpu', '--disable-background-networking',
      '--disable-component-update', '--disable-sync', '--no-first-run', '--no-default-browser-check',
      '--no-proxy-server', '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1',
      `--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0',
      'about:blank',
    ], { stdio: 'ignore' })
    let launchError
    browser.once('error', error => { launchError = error })
    closed = new Promise(resolve => browser.once('close', resolve))
    debugPort = await until(async () => {
      if (launchError) throw launchError
      if (browser.exitCode !== null) throw Error(`Chrome exited: ${browser.exitCode}`)
      const active = await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8').catch(error => {
        if (error.code !== 'ENOENT' && error.code !== 'EBUSY') throw error
        if (error.code === 'EBUSY') console.log('DevToolsActivePort busy during startup; polling same profile')
        return ''
      })
      const port = Number(active.split(/\r?\n/)[0])
      return Number.isInteger(port) && port > 0 && port <= 65535 ? port : false
    }, 'Chrome DevTools port')
    console.log(`URL ${origin}/calendar; CDP ${debugPort}`)
    const targets = await (await fetch(`http://${loopback}:${debugPort}/json/list`, {
      redirect: 'error', signal: AbortSignal.timeout(3000),
    })).json()
    const target = targets.find(entry => entry.type === 'page' && entry.url === 'about:blank')
    assert.ok(target, 'Fresh blank CDP target')
    const socket = new URL(target.webSocketDebuggerUrl)
    assert.equal(socket.origin, `ws://${loopback}:${debugPort}`)
    cdp = await connectCDP(socket.href, (message, call) => {
      if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails)
      if (message.method === 'Fetch.requestPaused') {
        intercept(message.params, call).catch(error => { if (!stopping) errors.push(String(error)) })
      }
    })
    const { call } = cdp
    await call('Fetch.enable', { patterns: [{ urlPattern: '*' }] })
    await call('Page.enable')
    await call('Runtime.enable')
    await call('Network.enable')
    await call('Network.setBypassServiceWorker', { bypass: true })
    await call('Network.setCacheDisabled', { cacheDisabled: true })
    await call('Emulation.setTimezoneOverride', { timezoneId: 'UTC' })
    // Observe actual consumption by client.ts, not just the acknowledgement of Fetch.fulfillRequest.
    await call('Page.addScriptToEvaluateOnNewDocument', { source: `
      if (location.origin === ${JSON.stringify(origin)}) {
        localStorage.setItem('pt.token', 'ui5-fixture-only');
        window.ui5Reads = [];
        const nativeText = Response.prototype.text;
        Response.prototype.text = async function (...args) {
          const text = await nativeText.apply(this, args);
          const marker = this.headers.get('X-UI5-Fixture');
          if (marker) window.ui5Reads.push({ marker, status: this.status, path: new URL(this.url).pathname, body: JSON.parse(text) });
          return text;
        };
      }
    ` })
    if (!demo) await call('Emulation.setDeviceMetricsOverride', { width: 375, height: 780, deviceScaleFactor: 1, mobile: true })
    const navigation = await call('Page.navigate', { url: `${origin}/calendar` })
    assert.equal(navigation.errorText, undefined)
    const evaluate = async expression => {
      const reply = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
      assert.equal(reply.exceptionDetails, undefined, JSON.stringify(reply.exceptionDetails))
      return reply.result.value
    }
    save = async name => {
      await writeFile(path.join(runDir, `${name}.html`), await evaluate('document.documentElement.outerHTML'))
      const image = await call('Page.captureScreenshot', { format: 'png' }).catch(error => {
        // In a headed run a hidden/occluded compositor can withhold a screenshot.
        // Keep DOM evidence mandatory, but don't strand the interactive fixture gate on it.
        if (!demo || error.message !== 'CDP timeout: Page.captureScreenshot') throw error
        screenshotWarnings.push({ name, message: error.message })
        console.warn(`${name}: PNG timed out; HTML preserved`)
      })
      if (!image) return
      await writeFile(path.join(runDir, `${name}.png`), Buffer.from(image.data, 'base64'))
    }
    const reload = async (ready, label) => {
      const previous = await evaluate('performance.timeOrigin')
      await preflight()
      await call('Page.reload')
      const current = await until(() => evaluate(`performance.timeOrigin !== ${previous} && (${ready}) && performance.timeOrigin`), label)
      console.log('Reload document:', previous, '→', current, label)
    }
    await scenario({ call, evaluate, save, reload, closed, api, runDir })
  } catch (error) {
    if (save) await save('failure').catch(e => console.error('Failure evidence:', e))
    throw error
  } finally {
    stopping = true
    try {
      if (browser?.pid && browser.exitCode === null && browser.signalCode === null) {
        if (cdp?.open()) await Promise.race([cdp.call('Browser.close').catch(() => {}), pause(500)])
        await Promise.race([closed, pause(1200)])
        if (browser.exitCode === null && browser.signalCode === null) {
          browser.kill() // Only the ChildProcess spawned with our unique profile.
          await Promise.race([closed, pause(2000)])
        }
        assert.ok(browser.exitCode !== null || browser.signalCode !== null, 'Isolated Chrome did not exit')
      }
    } finally {
      cdp?.close()
      try {
        await server?.close()
        for (const active of [listening && port, debugPort].filter(Boolean)) {
          await until(() => portClosed(active), `port ${active} closed`)
          closedPorts.push(active)
          console.log(`Closed ${loopback}:${active}`)
        }
        for (const directory of [profile, cacheDir]) await rm(directory, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 })
      } finally {
        await writeFile(path.join(runDir, 'run.json'), JSON.stringify({
          cwd: process.cwd(), argv: process.argv, sources, port, debugPort: debugPort ?? null,
          probes, closedPorts, api, blocked, exceptions, errors, screenshotWarnings,
        }, null, 2))
        console.log(`Synthetic API calls: ${api.filter(entry => entry.fixture).length}; blocked: ${blocked.length}`)
      }
    }
    assert.deepEqual(errors, [], 'CDP interception errors')
    assert.deepEqual(exceptions, [], 'Uncaught browser exceptions')
    assert.deepEqual(blocked, [], 'Requests outside the allowlist')
  }
}
