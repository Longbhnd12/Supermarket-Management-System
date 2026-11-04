import { test } from 'node:test';
import assert from 'node:assert/strict';
import { paymentExpiry, resumablePaymentUrl, sandboxPaymentUrl } from './paymentNavigation.js';

const url = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_BankCode=VNPAYQR';
const now = Date.parse('2026-11-02T10:00:00+07:00');
const payment = { paymentMethod: 'QR', status: 'PENDING', reconciliationRequired: false,
    expiresAt: '2026-11-02T10:15:00', paymentUrl: url };

test('pending QR reuses the original hosted URL while it is valid', () => {
    assert.equal(resumablePaymentUrl(payment, now), url);
});
test('expiry uses Vietnam time independent of browser timezone', () => {
    assert.equal(paymentExpiry(payment.expiresAt), Date.parse('2026-11-02T03:15:00Z'));
    assert.equal(paymentExpiry('2026-11-02T03:15:00Z'), Date.parse('2026-11-02T03:15:00Z'));
});
test('closed, unresolved, expired or missing-expiry attempts cannot reopen', () => {
    for (const status of ['SUCCESS', 'FAILED', 'TIMEOUT'])
        assert.equal(resumablePaymentUrl({ ...payment, status }, now), null);
    assert.equal(resumablePaymentUrl({ ...payment, reconciliationRequired: true }, now), null);
    assert.equal(resumablePaymentUrl(payment, paymentExpiry(payment.expiresAt)), null);
    assert.equal(resumablePaymentUrl({ ...payment, expiresAt: null }, now), null);
    assert.equal(resumablePaymentUrl({ ...payment, expiresAt: 'invalid' }, now), null);
    assert.equal(resumablePaymentUrl({ ...payment, paymentMethod: 'CASH' }, now), null);
});
test('only the exact hosted Sandbox payment route is accepted', () => {
    for (const value of ['https://example.com', 'http://sandbox.vnpayment.vn/paymentv2/vpcpay.html',
        'https://sandbox.vnpayment.vn/other', 'https://user:pass@sandbox.vnpayment.vn/paymentv2/vpcpay.html', 'invalid'])
        assert.equal(sandboxPaymentUrl(value), null);
});