import { test } from 'node:test'
import assert from 'node:assert/strict'
import { selectTransactions } from '../../src/pages/transactions-view.ts'

process.env.TZ = 'America/New_York'
const rows = [
  { id: 2, ticker: 'ALFA', type: 'BUY', executedAt: '2026-03-08T00:00:00-05:00' },
  { id: 1, ticker: 'BETA', type: 'SELL', executedAt: '2026-03-08T00:00:00-05:00' },
  { id: 3, ticker: 'ALF', type: 'SELL', executedAt: '2026-03-09T03:59:59Z' },
  { id: 4, ticker: 'ALFA', type: 'BUY', executedAt: '2026-03-09T04:00:00Z' },
]
const defaults = { ticker: '', type: '', from: '', to: '', order: 'desc' }

test('ticker search and type work together without changing source', () => {
  const before = structuredClone(rows)
  assert.deepEqual(selectTransactions(rows, { ...defaults, ticker: '  aLf ', type: 'SELL' }).map(x => x.id), [3])
  assert.deepEqual(rows, before)
})

test('local inclusive calendar day covers DST transition and open bounds', () => {
  assert.equal(process.env.TZ, 'America/New_York')
  assert.deepEqual(selectTransactions(rows, { ...defaults, from: '2026-03-08', to: '2026-03-08' }).map(x => x.id), [3, 2, 1])
  assert.deepEqual(selectTransactions(rows, { ...defaults, from: '2026-03-09' }).map(x => x.id), [4])
  assert.deepEqual(selectTransactions(rows, { ...defaults, to: '2026-03-08', order: 'asc' }).map(x => x.id), [1, 2, 3])
})

test('sorts by instant with deterministic id ties in both directions', () => {
  assert.deepEqual(selectTransactions(rows, defaults).map(x => x.id), [4, 3, 2, 1])
  assert.deepEqual(selectTransactions(rows, { ...defaults, order: 'asc' }).map(x => x.id), [1, 2, 3, 4])
})

test('preserves submillisecond chronology and ties equal instants across offsets', () => {
  const candidates = [
    { ...rows[0], id: 30, executedAt: '2026-03-08T13:00:00.123100Z' },
    { ...rows[0], id: 20, executedAt: '2026-03-08T09:00:00.1239-04:00' },
    { ...rows[0], id: 10, executedAt: '2026-03-08T13:00:00.123900Z' },
    { ...rows[0], id: 40, executedAt: '2026-03-08T13:00:00.123Z' },
  ]
  const before = structuredClone(candidates)
  assert.deepEqual(selectTransactions(candidates, defaults).map(x => x.id), [20, 10, 30, 40])
  assert.deepEqual(selectTransactions(candidates, { ...defaults, order: 'asc' }).map(x => x.id), [40, 30, 10, 20])
  assert.deepEqual(candidates, before)
})
