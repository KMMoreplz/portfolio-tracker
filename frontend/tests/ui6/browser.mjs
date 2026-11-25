import assert from 'node:assert/strict'
import { runBrowser, until } from '../ui5/harness.mjs'

const ids = [101, 102]
const portfolios = ids.map(id => ({ id, name: `Синтетика ${id === 101 ? 'A' : 'B'}`, baseCurrency: 'RUB', createdAt: '2026-01-01T00:00:00Z' }))
const calendar = id => ({ portfolioId: id, baseCurrency: 'RUB', marketDataAvailable: true, items: ['BETA+B', 'NO-MATCH', '', '   '].map(ticker => ({ ticker, instrumentName: null, quantity: id === 101 ? 3 : 4, exDate: '2026-12-20', paymentDate: '2026-12-21', amountPerShare: 2.5, currency: 'EUR', payout: 10, payoutInBase: null, source: 'fixture' })) })
const tx = (id, ticker, quantity) => ({ id, portfolioId: 102, type: 'BUY', ticker, quantity, price: 11, commission: 0, currency: 'RUB', executedAt: '2026-03-09T13:00:00Z' })
const routes = new Map([
  ['GET /api/auth/me', () => ({ body: { id: 1, email: 'fixture@example.invalid' } })],
  ['GET /api/portfolios', () => ({ body: portfolios })],
  ...ids.map(id => [`GET /api/portfolios/${id}/dividend-calendar`, () => ({ body: calendar(id) })]),
  ['GET /api/portfolios/101/transactions', () => ({ body: [{ ...tx(3, 'BETA+B', 17), portfolioId: 101 }] })],
  ['GET /api/portfolios/102/transactions', () => ({ body: [tx(1, 'BETA+B', 42), tx(2, 'BETA+B-P', 99)] })],
])
await runBrowser({ routes, demo: false }, async ({ call, evaluate, save, api }) => {
  await until(() => evaluate("document.querySelector('.portfolio-select select')?.value === '101' && document.querySelector('tbody tr')?.innerText.includes('BETA+B')"), 'A loaded')
  await evaluate("(() => { const s=document.querySelector('.portfolio-select select'); Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(s,'102'); s.dispatchEvent(new Event('change',{bubbles:true})); })()")
  await until(() => evaluate("document.querySelector('.portfolio-select select')?.value === '102' && document.querySelector('tbody tr')?.cells[4]?.innerText.trim() === '4'"), 'B loaded')
  const row = await evaluate("(() => { const tr=document.querySelector('tbody tr'); return {cells:[...tr.cells].map(x=>x.innerText.trim()), links:[...tr.querySelectorAll('a')].map(a=>({text:a.innerText.trim(),href:a.getAttribute('href')}))} })()")
  console.log('B ready:', JSON.stringify(row))
  const allRows = await evaluate("[...document.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('a')].filter(a=>a.textContent.trim()==='Сделки').map(a=>a.getAttribute('href')))")
  assert.deepEqual(allRows, [['/transactions?ticker=BETA%2BB'], ['/transactions?ticker=NO-MATCH'], [], []])
  await save('b-ready')
  assert.deepEqual(row.cells.slice(0,2), ['21.12.2026','20.12.2026'])
  assert.ok(row.links.some(a => a.text === 'BETA+B' && a.href === '/security/BETA%2BB'), 'security link')
  assert.ok(row.links.some(a => a.text === 'Сделки' && a.href === '/transactions?ticker=BETA%2BB'), 'separate exact transactions link')
  const key = async (name, code) => {
    const event = { key: name, code: name, windowsVirtualKeyCode: code }
    await call('Input.dispatchKeyEvent', { type:'keyDown', ...event, ...(name === 'Enter' ? {text:'\r',unmodifiedText:'\r'} : {}) })
    await call('Input.dispatchKeyEvent', { type:'keyUp', ...event })
  }
  for (let i=0; i<30 && !(await evaluate("document.activeElement?.getAttribute('href') === '/transactions?ticker=BETA%2BB'")); i++) await key('Tab',9)
  assert.equal(await evaluate("document.activeElement?.getAttribute('href')"), '/transactions?ticker=BETA%2BB')
  const offset=api.length
  await key('Enter',13)
  await until(() => evaluate("location.pathname === '/transactions' && location.search === '?ticker=BETA%2BB' && document.body.innerText.includes('Найдено в загруженном списке: 1')"), 'filtered transactions')
  const result = await evaluate("({heading:document.querySelector('main h1').innerText, text:document.querySelector('main').innerText, rows:[...document.querySelectorAll('tbody tr')].map(tr=>[...tr.cells].map(x=>x.innerText.trim()))})")
  console.log('Transactions:',JSON.stringify(result))
  assert.equal(result.heading,'Сделки — Синтетика B')
  assert.match(result.text,/Точный тикер: BETA\+B/)
  assert.equal(result.rows.length,1)
  assert.equal(result.rows[0][2],'BETA+B')
  assert.equal(result.rows[0][3],'42')
  assert.ok(!result.text.includes('BETA+B-P'))
  assert.ok(api.slice(offset).filter(e=>e.key.includes('/transactions')).every(e=>e.key === 'GET /api/portfolios/102/transactions'))
  assert.ok(api.slice(offset).some(e=>e.key === 'GET /api/portfolios/102/transactions'))
  await save('transactions-b')
  await evaluate('history.back()')
  await until(() => evaluate("location.pathname === '/calendar' && document.querySelector('.portfolio-select select')?.value === '102' && document.querySelector('tbody tr')?.cells[4]?.innerText.trim() === '4'"), 'return keeps B')
  await evaluate("document.querySelector('a[href=\"/transactions?ticker=NO-MATCH\"]').click()")
  await until(() => evaluate("location.search === '?ticker=NO-MATCH' && document.body.innerText.includes('По фильтрам ничего не найдено')"), 'unmatched planned payout')
  assert.ok(await evaluate("document.querySelector('main h1').innerText === 'Сделки — Синтетика B' && document.body.innerText.includes('Точный тикер: NO-MATCH') && !document.querySelector('tbody tr')"))
  await save('no-matching-transaction')
  console.log('PASS blank tickers, return to B, and no matching transaction')
})
