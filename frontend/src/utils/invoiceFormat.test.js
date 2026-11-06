import { test } from 'node:test';
import assert from 'node:assert/strict';
import { invoiceDateTime, invoiceMoney, invoiceQuantity, lineNetAmount } from './invoiceFormat.js';

test('Vietnam snapshot time keeps its calendar time without browser timezone conversion', () => {
    assert.equal(invoiceDateTime('2026-11-05T00:15:09.1234567'), '05/11/2026 00:15:09');
    assert.equal(invoiceDateTime(null), '—');
});

test('line amount subtracts discount in cents without floating point artifacts', () => {
    assert.equal(lineNetAmount({ subtotal: '0.30', discountAmount: '0.10' }), 0.2);
    assert.equal(lineNetAmount({ subtotal: '125000.25', discountAmount: '10000.15' }), 115000.1);
    assert.equal(lineNetAmount({ subtotal: '100.00', discountAmount: '100.00' }), 0);
    assert.equal(lineNetAmount({ subtotal: '9999999999999.99', discountAmount: '0.01' }), 9999999999999.98);
});

test('currency preserves cents and fractional quantities preserve three decimals', () => {
    assert.equal(invoiceMoney('1000.25'), '1.000,25 ₫');
    assert.equal(invoiceMoney(0), '0 ₫');
    assert.equal(invoiceQuantity('1.125'), '1,125');
    assert.equal(invoiceQuantity('2.000'), '2');
});
