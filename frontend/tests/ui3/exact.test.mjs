import { test } from 'node:test'
import assert from 'node:assert/strict'
import { selectTransactions } from '../../src/pages/transactions-view.ts'

const rows = ['ALFA', 'ALFA-P', ' alfa ', 'BRK.B'].map((ticker, i) => ({ id: i + 1, ticker, type: 'BUY', executedAt: '2026-01-01T00:00:00Z' }))
const defaults = { ticker: '', type: '', from: '', to: '', order: 'asc' }
test('exact ticker matches trimmed whole symbol, not prefix', () => {
  assert.deepEqual(selectTransactions(rows, { ...defaults, exactTicker: ' aLfA ' }).map(x => x.id), [1, 3])
  assert.deepEqual(selectTransactions(rows, { ...defaults, exactTicker: 'brk.b' }).map(x => x.id), [4])
  assert.deepEqual(selectTransactions(rows, { ...defaults, ticker: 'alf' }).map(x => x.id), [1, 2, 3])
})
