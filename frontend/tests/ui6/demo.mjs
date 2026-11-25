import { createInterface } from 'node:readline'
import { runBrowser, until } from '../ui5/harness.mjs'

const portfolios = [101,102].map(id => ({ id, name: `Синтетика ${id === 101 ? 'A' : 'B'}`, baseCurrency: 'RUB', createdAt: '2026-01-01T00:00:00Z' }))
const calendar = id => ({ portfolioId: id, baseCurrency: 'RUB', marketDataAvailable: true, items: [{ ticker: 'BETA+B', instrumentName: null, quantity: id === 101 ? 3 : 4, exDate: '2026-12-20', paymentDate: '2026-12-21', amountPerShare: 2.5, currency: 'EUR', payout: 10, payoutInBase: null, source: 'fixture' }] })
const transaction = id => ({ id, portfolioId: id, type:'BUY', ticker:'BETA+B', quantity: id === 101 ? 17 : 42, price:11, commission:0, currency:'RUB', executedAt:'2026-03-09T13:00:00Z' })
const routes = new Map([
  ['GET /api/auth/me', () => ({body:{id:1,email:'fixture@example.invalid'}})],
  ['GET /api/portfolios', () => ({body:portfolios})],
  ...[101,102].flatMap(id => [
    [`GET /api/portfolios/${id}/dividend-calendar`, () => ({body:calendar(id)})],
    [`GET /api/portfolios/${id}/transactions`, () => ({body:[transaction(id)]})],
  ]),
])
await runBrowser({ routes, demo:true }, async ({evaluate, call, closed}) => {
  await until(() => evaluate("document.querySelector('.portfolio-select select')?.value === '101' && document.querySelector('tbody tr')?.innerText.includes('BETA+B')"), 'demo A ready')
  console.log('Демо: выберите Синтетика B, затем в строке BETA+B откройте Сделки; увидите 42. Назад вернёт B. Для завершения нажмите Enter в терминале.')
  if (process.argv.includes('--verify')) {
    await evaluate("(() => { const s=document.querySelector('.portfolio-select select'); Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(s,'102'); s.dispatchEvent(new Event('change',{bubbles:true})); })()")
    await until(() => evaluate("document.querySelector('.portfolio-select select')?.value === '102' && document.querySelector('tbody tr')?.cells[4]?.innerText.trim() === '4'"), 'demo B ready')
    await evaluate("document.querySelector('a[href=\"/transactions?ticker=BETA%2BB\"]').click()")
    await until(() => evaluate("document.querySelector('main h1')?.innerText === 'Сделки — Синтетика B' && document.querySelector('tbody tr')?.cells[3]?.innerText.trim() === '42'"), 'demo B transactions')
    await evaluate('history.back()')
    await until(() => evaluate("location.pathname === '/calendar' && document.querySelector('.portfolio-select select')?.value === '102' && document.querySelector('tbody tr')?.cells[4]?.innerText.trim() === '4'"), 'demo return B')
    console.log('VERIFY: B → Сделки 42 → назад B')
  }
  const readline = createInterface({input:process.stdin, crlfDelay:Infinity, terminal:false})
  void closed.then(() => readline.close())
  try { for await (const line of readline) { if (!line.trim()) { console.log('Выход по Enter.'); break } } }
  finally { readline.close(); process.stdin.pause() }
})
