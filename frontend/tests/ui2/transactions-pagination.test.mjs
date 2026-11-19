import assert from 'node:assert/strict'
import { test } from 'node:test'
import { paginateTransactions, selectTransactions } from '../../src/pages/transactions-view.ts'

const defaults = { ticker: '', type: '', from: '', to: '', order: 'desc' }
const ids = rows => rows.map(row => row.id)

function operation(id, executedAt = '2026-03-08T14:00:00Z', ticker = 'ALFA', type = 'BUY') {
  return { id, executedAt, ticker, type }
}

test('filters and sorts the full response before taking the first or partial last page', () => {
  const source = Array.from({ length: 24 }, (_, i) => operation(i + 1, undefined, i % 2 ? 'ALFA' : 'GAMMA', i % 2 ? 'BUY' : 'SELL'))
  const before = structuredClone(source)
  const matches = selectTransactions(source, { ...defaults, ticker: ' alF ', type: 'BUY', order: 'asc' })
  const first = paginateTransactions(matches, 1)
  const last = paginateTransactions(matches, 2)
  assert.equal(matches.length, 12)
  assert.equal(first.pageCount, 2)
  assert.deepEqual(ids(first.rows), [2, 4, 6, 8, 10, 12, 14, 16, 18, 20])
  assert.deepEqual(ids(last.rows), [22, 24])
  assert.deepEqual([...first.rows, ...last.rows], matches)
  assert.deepEqual(source, before)
})

test('preserves submillisecond chronology and equal-offset instant ties across the page boundary', () => {
  const source = [
    operation(30, '2026-03-08T13:00:00.123100Z'),
    operation(20, '2026-03-08T09:00:00.1239-04:00'),
    operation(10, '2026-03-08T13:00:00.123900Z'),
    operation(40, '2026-03-08T13:00:00.123Z'),
    ...Array.from({ length: 9 }, (_, i) => operation(i + 100)),
  ]
  const matches = selectTransactions(source, defaults)
  assert.equal(paginateTransactions(matches, 1).rows.at(-1).id, 20)
  assert.deepEqual(ids(paginateTransactions(matches, 2).rows), [10, 30, 40])
})

test('keeps a valid selection, clamps out-of-range pages and returns no pages for no matches', () => {
  const rows = Array.from({ length: 20 }, (_, i) => i + 1)
  assert.deepEqual(paginateTransactions(rows, 1), { page: 1, pageCount: 2, rows: rows.slice(0, 10) })
  assert.deepEqual(paginateTransactions(rows, 3), { page: 2, pageCount: 2, rows: rows.slice(10) })
  assert.deepEqual(paginateTransactions([1], 2), { page: 1, pageCount: 1, rows: [1] })
  assert.deepEqual(paginateTransactions([], 2), { page: 1, pageCount: 0, rows: [] })
})
