import assert from 'node:assert/strict'
import { createInterface } from 'node:readline'
import { isDeepStrictEqual } from 'node:util'
import { runBrowser, until } from './harness.mjs'

const args = process.argv.slice(2)
assert.ok(args.length <= 1 && args.every(arg => ['--mismatch', '--demo'].includes(arg)), 'Use --mismatch, --demo or no argument')
const demo = args.includes('--demo')
const mismatchOnly = args.includes('--mismatch')
const A = 101, B = 102
const title = 'Дивидендный календарь'
const loading = 'Загрузка календаря…'
const wrongPortfolio = 'Сервер вернул календарь другого портфеля'
const noPayouts = 'Предстоящих выплат нет'
const noData = 'Нет данных'
const noPortfolio = 'Нет портфеля'
const unavailableWarning = 'market-data-service недоступен — данные о предстоящих выплатах временно получить нельзя. Список будет пуст не потому, что выплат нет.'
const names = { [A]: 'Синтетика A', [B]: 'Синтетика B' }
const calendarPath = id => `/api/portfolios/${id}/dividend-calendar`
const variants = {
  a: { ticker: 'ALFA.A', day: 11, currency: 'USD', base: 'RUB', share: 1.25, qty: 3, payout: 3.75, inBase: 375 },
  b: { ticker: 'BETA+B', day: 21, currency: 'EUR', base: 'GBP', share: 2.50, qty: 4, payout: 10, inBase: null },
  late: { ticker: 'ALFA.OLD', day: 13, currency: 'USD', base: 'RUB', share: 3.25, qty: 5, payout: 16.25, inBase: 1625 },
  fresh: { ticker: 'ALFA.NEW', day: 15, currency: 'USD', base: 'RUB', share: 4.25, qty: 6, payout: 25.50, inBase: 2550 },
}
// Independent literals: exact seven visible cells (including punctuation, NBSP and em dash) and link.
const expected = {
  a: { cells: ['11.12.2026', '10.12.2026', 'ALFA.A', '1,25 USD', '3', '3,75 USD', '375,00 RUB'], href: '/security/ALFA.A' },
  b: { cells: ['21.12.2026', '20.12.2026', 'BETA+B', '2,50 EUR', '4', '10,00 EUR', '—'], href: '/security/BETA%2BB' },
  late: { cells: ['13.12.2026', '12.12.2026', 'ALFA.OLD', '3,25 USD', '5', '16,25 USD', '1\u00a0625,00 RUB'], href: '/security/ALFA.OLD' },
  fresh: { cells: ['15.12.2026', '14.12.2026', 'ALFA.NEW', '4,25 USD', '6', '25,50 USD', '2\u00a0550,00 RUB'], href: '/security/ALFA.NEW' },
}
const data = (id, variant, available = true) => {
  const v = variants[variant]
  return {
    portfolioId: id, baseCurrency: v?.base ?? (id === A ? 'RUB' : 'GBP'), marketDataAvailable: available,
    items: v ? [{
      ticker: v.ticker, instrumentName: null, quantity: v.qty, exDate: `2026-12-${v.day - 1}`,
      paymentDate: `2026-12-${v.day}`, amountPerShare: v.share, currency: v.currency,
      payout: v.payout, payoutInBase: v.inBase, source: 'ui5-fixture',
    }] : [],
  }
}
const ok = (id, variant, available) => ({ status: 200, body: data(id, variant, available) })
const error = message => ({ status: 500, body: { message } })
const portfolios = [A, B].map(id => ({ id, name: names[id], baseCurrency: data(id).baseCurrency, createdAt: '2026-01-01T00:00:00Z' }))
let noPortfolios = false
const waves = new Map()
function wave(id, label, response, held = false) {
  let release
  const gate = new Promise(resolve => { release = resolve })
  const entries = []
  const group = { id, label, response, entries, release }
  group.handle = async entry => {
    entry.label = label
    entries.push(entry)
    await gate
    return { ...response, label }
  }
  if (!held) release()
  waves.set(id, group)
  return group
}
const routes = new Map([
  ['GET /api/auth/me', () => ({ body: { id: 1, email: 'ui5@example.invalid' } })],
  ['GET /api/portfolios', () => ({ body: noPortfolios ? [] : portfolios })],
  ...[A, B].map(id => [`GET ${calendarPath(id)}`, entry => {
    const group = waves.get(id)
    assert.ok(group, `No armed calendar fixture for ${id}`)
    return group.handle(entry)
  }]),
])
const ticker = variants.b.ticker
const securityHref = expected.b.href
routes.set('GET /market/quotes/BETA%2BB', () => ({ body: {
  ticker, currency: 'EUR', price: 17.50, quotedAt: '2026-12-20T09:00:00Z', source: 'ui5-fixture', cached: false,
} }))
routes.set('GET /market/quotes/BETA%2BB/history', () => ({ body: [] }))
routes.set('GET /market/dividends/BETA%2BB', () => ({ body: { ticker, source: 'ui5-fixture', upcoming: [], past: [] } }))
routes.set('GET /market/news?tickers=BETA%2BB', () => ({ body: [] }))

const dom = `(() => {
  const page = document.querySelector('main');
  const select = page?.querySelector('.portfolio-select select');
  const all = selector => [...(page?.querySelectorAll(selector) ?? [])];
  const texts = selector => all(selector).map(node => node.innerText.trim());
  return {
    path: location.pathname, search: location.search, heading: texts('h1'),
    id: select?.value ?? null, name: select?.selectedOptions[0]?.textContent.trim() ?? null,
    loading: texts('.page-loading'), errors: texts('.error-banner span'), warnings: texts('.warning-banner').map(text => text.replace(/\\s+/g, ' ')),
    empty: texts('.empty-state-title'), retry: texts('.error-banner button'),
    rows: all('tbody tr').map(tr => ({ cells: Array.from(tr.cells, td => td.innerText.trim()), href: tr.querySelector('a')?.getAttribute('href') })),
  };
})()`

await runBrowser({ routes, demo }, async ({ call, evaluate, save, reload, closed, api }) => {
  const state = () => evaluate(dom)
  const settledFrames = () => evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))))')
  const key = async (name, code) => {
    const event = { key: name, code: name, windowsVirtualKeyCode: code }
    await call('Input.dispatchKeyEvent', { type: 'keyDown', ...event,
      ...(name === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) })
    await call('Input.dispatchKeyEvent', { type: 'keyUp', ...event })
  }
  const focusWithTab = async selector => {
    const focused = () => evaluate(`document.activeElement.matches(${JSON.stringify(selector)})`)
    for (let i = 0; i < 25; i++) {
      if (await focused()) return
      await key('Tab', 9)
    }
    assert.ok(await focused(), `Not keyboard reachable: ${selector}`)
  }
  const selectWithKeys = async id => {
    const before = await state()
    assert.notEqual(before.id, String(id), 'A switch must change the selection')
    await focusWithTab('.portfolio-select select')
    await key(id === B ? 'ArrowDown' : 'ArrowUp', id === B ? 40 : 38)
    await until(async () => (await state()).id === String(id), `keyboard selection ${id}`)
  }
  const requestStarted = group => until(() => group.entries.length, `held/requested ${group.label}`)
  const calendarReads = () => evaluate('window.ui5Reads ?? []')
  async function delivered(group) {
    await requestStarted(group)
    const receipts = () => until(async () => {
      const log = await calendarReads()
      const matches = group.entries.map(entry => log.find(read => read.marker === entry.marker))
      return matches.every(Boolean) && group.entries.every(entry => entry.fulfilled) && matches
    }, `actual browser read of ${group.label}`)
    await receipts()
    // A headed window may be occluded and stop producing frames. Its visible-state
    // assertions poll the DOM instead; the race tests use the headless frame barrier.
    if (!demo) await settledFrames()
    for (const read of await receipts()) {
      assert.deepEqual(read, {
        marker: read.marker, status: group.response.status, path: calendarPath(group.id), body: group.response.body,
      })
      const entry = group.entries.find(entry => entry.marker === read.marker)
      entry.delivery = read
    }
    console.log('Delivered to client:', group.label, calendarPath(group.id), group.response.status, group.entries.map(e => e.marker).join(','))
  }
  const calendarState = (id, overrides = {}) => ({
    path: '/calendar', search: '', heading: [title], id: id === null ? null : String(id), name: names[id] ?? null,
    rows: [], loading: [], errors: [], warnings: [], empty: [], retry: [], ...overrides,
  })
  const rightRow = (id, variant) => calendarState(id, { rows: [expected[variant]] })
  const expectState = async (label, expectedState, evidence = label) => {
    const observed = demo ? await until(async () => {
      const current = await state()
      return isDeepStrictEqual(current, expectedState) && current
    }, `${label} DOM`) : await state()
    console.log(label, JSON.stringify(observed))
    if (evidence) await save(evidence)
    assert.deepEqual(observed, expectedState, label)
  }
  const mustLoad = async (group, label) => {
    await requestStarted(group)
    assert.ok(group.entries.every(entry => !entry.fulfilled), `${group.label} still held`)
    await expectState(label, calendarState(group.id, { loading: [loading] }))
  }
  const selectBox = '.portfolio-select select'
  const Mobile = async (label, selector) => {
    const measured = await evaluate(`(() => {
      const node = document.querySelector(${JSON.stringify(selector)});
      node?.scrollIntoView({ block: 'center' });
      const r = node?.getBoundingClientRect();
      return { width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth,
        box: r ? { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height, visibility: getComputedStyle(node).visibility } : null };
    })()`)
    console.log('375px:', label, JSON.stringify(measured))
    assert.equal(measured.width, 375)
    assert.equal(measured.scroll, 375)
    const r = measured.box
    assert.ok(r?.width > 0 && r.height > 0 && r.left >= 0 && r.right <= 375 && r.top >= 0 && r.bottom <= 780 && r.visibility === 'visible', label)
  }

  const initial = wave(A, 'initial-a', ok(A, 'a'))
  await delivered(initial)
  await expectState('initial-a', rightRow(A, 'a'))

  if (demo) {
    const firstB = wave(B, 'demo-held-b', ok(B, 'b'), true)
    console.log('Демо открыто. Выберите «Синтетика B», затем в этом терминале введите b и нажмите Enter.')
    const readline = createInterface({ input: process.stdin, crlfDelay: Infinity, terminal: false })
    void closed.then(() => readline.close())
    let nextB
    try {
      for await (const line of readline) {
        const command = line.trim().toLowerCase()
        if (!command) { console.log('Выход по Enter.'); break }
        if (command === 'b' && !nextB) {
          await mustLoad(firstB, 'demo-b-loading')
          firstB.release()
          await delivered(firstB)
          await expectState('demo-b-rows', rightRow(B, 'b'))
          nextB = wave(B, 'demo-unavailable-b', ok(B, null, false), true)
          console.log('Переключитесь на «Синтетика A» и обратно на B. Введите n и нажмите Enter.')
        } else if (command === 'n' && nextB) {
          await mustLoad(nextB, 'demo-b-loading-again')
          nextB.release()
          await delivered(nextB)
          await expectState('demo-b-unavailable', calendarState(B, { empty: [noData], warnings: [unavailableWarning] }))
          console.log('Данные недоступны. Нажмите Enter на пустой строке для выхода.')
        } else {
          console.log('Команды по шагам: b, n, затем пустая строка (Enter) для выхода.')
        }
      }
    } finally { readline.close(); process.stdin.pause() }
    return
  }

  await Mobile('select on A', selectBox)
  const mismatch = wave(B, 'b-returns-a', ok(A, 'a'), true)
  await selectWithKeys(B)
  await mustLoad(mismatch, 'b-held-before-mismatch')
  await Mobile('B loading', '.page-loading')
  mismatch.release()
  await delivered(mismatch)
  await expectState('b-mismatch', calendarState(B, { errors: [wrongPortfolio], retry: ['Повторить'] }))
  if (mismatchOnly) return

  const retryMismatch = wave(B, 'b-retry-mismatch', ok(B, 'b'), true)
  await focusWithTab('.error-banner button')
  await key('Enter', 13)
  await mustLoad(retryMismatch, 'b-retry-mismatch-loading')
  retryMismatch.release()
  await delivered(retryMismatch)
  await expectState('b-retry-mismatch-rows', rightRow(B, 'b'))
  await Mobile('B table stays in its container', '.table-wrap')

  async function lateA(label, response, beforeB) {
    const old = wave(A, `${label}-old-a`, response, true)
    await selectWithKeys(A)
    await mustLoad(old, `${label}-a-loading`)
    const newest = wave(B, `${label}-current-b`, ok(B, 'b'), true)
    await selectWithKeys(B)
    await mustLoad(newest, `${label}-b-loading`)
    if (beforeB) {
      old.release()
      await delivered(old)
      await mustLoad(newest, `${label}-b-still-loading`)
    }
    newest.release()
    await delivered(newest)
    await expectState(`${label}-b-ready`, rightRow(B, 'b'))
    if (!beforeB) {
      old.release()
      await delivered(old)
      await expectState(`${label}-b-after-stale`, rightRow(B, 'b'))
    }
  }
  await lateA('late-success', ok(A, 'late'), false)
  await lateA('late-error', error('Просроченная ошибка A'), false)
  await lateA('early-stale-success', ok(A, 'late'), true)

  const shownError = 'Текущая временная ошибка A'
  const failedA = wave(A, 'shown-error-a', error(shownError))
  await selectWithKeys(A)
  await delivered(failedA)
  await expectState('shown-error-a', calendarState(A, { errors: [shownError], retry: ['Повторить'] }))
  const currentError = 'Текущая временная ошибка B'
  const failedB = wave(B, 'current-error-b', error(currentError), true)
  await selectWithKeys(B)
  await mustLoad(failedB, 'b-held-after-shown-a-error')
  failedB.release()
  await delivered(failedB)
  await expectState('b-current-error', calendarState(B, { errors: [currentError], retry: ['Повторить'] }))
  await Mobile('B retry button', '.error-banner button')
  const offset = api.length
  const recoverB = wave(B, 'b-retry-http-error', ok(B, 'b'), true)
  await focusWithTab('.error-banner button')
  await key('Enter', 13)
  await mustLoad(recoverB, 'b-retry-http-loading')
  recoverB.release()
  await delivered(recoverB)
  assert.deepEqual(api.slice(offset).map(r => r.key), [`GET ${calendarPath(B)}`], 'Retry sends one request, for B only')
  await expectState('b-retry-http-rows', rightRow(B, 'b'))

  const oldA = wave(A, 'aba-old-a', ok(A, 'late'), true)
  await selectWithKeys(A)
  await mustLoad(oldA, 'aba-first-a-held')
  const middleB = wave(B, 'aba-middle-b', error('Просроченная промежуточная ошибка B'), true)
  await selectWithKeys(B)
  await mustLoad(middleB, 'aba-middle-b-held')
  const newA = wave(A, 'aba-new-a', ok(A, 'fresh'), true)
  await selectWithKeys(A)
  await mustLoad(newA, 'aba-second-a-held')
  newA.release()
  await delivered(newA)
  await expectState('aba-new-a-ready', rightRow(A, 'fresh'))
  middleB.release()
  await delivered(middleB)
  await expectState('aba-a-after-old-b-error', rightRow(A, 'fresh'))
  oldA.release()
  await delivered(oldA)
  await expectState('aba-a-after-old-a-success', rightRow(A, 'fresh'))

  const emptyB = wave(B, 'empty-current-b', ok(B))
  await selectWithKeys(B)
  await delivered(emptyB)
  await expectState('b-empty', calendarState(B, { empty: [noPayouts] }))
  await Mobile('B empty', '.empty-state-title')
  const unavailableA = wave(A, 'unavailable-current-a', ok(A, null, false))
  await selectWithKeys(A)
  await delivered(unavailableA)
  await expectState('a-unavailable', calendarState(A, { empty: [noData], warnings: [unavailableWarning] }))
  await Mobile('A warning', '.warning-banner')

  const finalB = wave(B, 'link-b', ok(B, 'b'))
  await selectWithKeys(B)
  await delivered(finalB)
  await expectState('b-link', rightRow(B, 'b'))
  await focusWithTab('tbody .ticker-link')
  assert.equal(await evaluate("document.activeElement.getAttribute('href')"), securityHref)
  await key('Enter', 13)
  await until(() => evaluate(`location.pathname === ${JSON.stringify(securityHref)} && document.querySelector('main h1')?.innerText === ${JSON.stringify(ticker)} && document.querySelector('.quote-price')?.innerText === '17,50 EUR'`), 'real security route opened by keyboard')
  await save('security-from-b')

  const backToB = wave(B, 'back-to-calendar-b', ok(B, 'b'))
  await evaluate("document.querySelector('nav a[href=\"/calendar\"]').click()")
  await delivered(backToB)
  await expectState('back-to-calendar-b', rightRow(B, 'b'))
  noPortfolios = true
  const offsetReload = api.length
  await reload("location.pathname === '/calendar' && document.querySelector('main .empty-state-title')?.textContent === 'Нет портфеля'", 'new calendar document with no portfolios')
  await settledFrames()
  const startup = api.slice(offsetReload)
  assert.deepEqual([...new Set(startup.map(r => r.key))].sort(), ['GET /api/auth/me', 'GET /api/portfolios'])
  const reads = await calendarReads()
  assert.ok(startup.every(entry => reads.some(read => read.marker === entry.marker)), 'Only startup fixtures delivered in new document')
  await expectState('no-portfolio', calendarState(null, { empty: [noPortfolio] }))
  await Mobile('no portfolio', '.empty-state-title')
})
