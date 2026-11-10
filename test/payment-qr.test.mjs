import assert from 'node:assert/strict'
import test from 'node:test'
import QRCode from 'qrcode'
import { paymentUrl, isLoopbackUrl } from '../src/utils/paymentQr.ts'

test('QR contains only an authenticated bill link, not an amount or credentials', async () => {
  const url = paymentUrl(17, 'http://localhost:5173')
  assert.equal(url, 'http://localhost:5173/pay/17')
  assert.equal(new URL(url).search, '')
  assert.equal(isLoopbackUrl(url), true)
  const png = await QRCode.toBuffer(url, { margin: 4, errorCorrectionLevel: 'M' })
  assert.equal(png.subarray(1, 4).toString(), 'PNG')
  assert.ok(png.length > 300)
})
test('configured deployment address and explicit localhost warning', () => {
  assert.equal(paymentUrl(1, 'http://localhost:5173', 'https://water.example'), 'https://water.example/pay/1')
  assert.equal(isLoopbackUrl('https://water.example/pay/1'), false)
  assert.equal(isLoopbackUrl('http://[::1]:5173/pay/1'), true)
})
test('invalid IDs and unsafe base URLs fail closed', () => {
  for (const id of [0, -1, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => paymentUrl(id, 'http://localhost'))
  for (const base of ['javascript:alert(1)', 'https://user:secret@example.com', 'https://a.example/?token=x', 'https://a.example/#x', 'https://a.example/sub'])
    assert.throws(() => paymentUrl(1, 'http://localhost', base))
})
