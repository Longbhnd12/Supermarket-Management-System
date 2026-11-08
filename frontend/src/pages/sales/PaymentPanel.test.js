import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { transformWithOxc } from 'vite';
import assert from 'node:assert/strict';

const source = await readFile(new URL('./PaymentPanel.jsx', import.meta.url), 'utf8');
const { code } = await transformWithOxc(source.replace(/^import .*;\r?\n/gm, '').replace('export default function', 'function'), 'PaymentPanel.jsx', { jsx: { runtime: 'classic' } });
const React = { createElement: (type, props, ...children) => ({ type, props: props ?? {}, children }) };
const find = (node, predicate) => {
    if (!node || typeof node !== 'object') return undefined;
    if (predicate(node)) return node;
    return (node.children ?? []).flat(Infinity).map((child) => find(child, predicate)).find(Boolean);
};
const text = (node) => typeof node === 'string' ? node : (node?.children ?? []).flat(Infinity).map(text).join('');
const options = ['CASH', 'CARD', 'QR'].map((method) => ({ method, bankCode: method === 'CARD' ? 'VNBANK' : method === 'QR' ? 'VNPAYQR' : null, label: 'Backend label', enabled: true }));
const sale = { saleId: 172, status: 'DRAFT', totalAmount: 50000, items: [{}] };
const session = new Map();
globalThis.sessionStorage = { getItem: (key) => session.get(key), setItem: (key, value) => session.set(key, value), removeItem: (key) => session.delete(key) };
globalThis.window = { location: { assign() {} } };

function harness({ method = 'CASH', sufficient = true, busy = false, fail = false } = {}) {
    let index = 0;
    let calls = [];
    let effects = [];
    let executing = false;
    let finish;
    const wait = new Promise((resolve) => { finish = resolve; });
    const state = [options, '', 0, false, `${method}:${method === 'CARD' ? 'VNBANK' : method === 'QR' ? 'VNPAYQR' : ''}`, false, '60000', { locked: false, attempts: [] }, false, '', '', 0, Date.now(), null];
    const useState = (initial) => {
        const i = index++;
        if (!(i in state)) state[i] = typeof initial === 'function' ? initial() : initial;
        return [state[i], (value) => { state[i] = typeof value === 'function' ? value(state[i]) : value; }];
    };
    const useRef = (initial) => ({ current: initial === false ? true : initial });
    const PaymentService = { create: async (id, payload) => { calls.push({ id, payload }); await wait; if (fail) throw { response: { status: 400, data: { message: 'Payment rejected' } } }; return { data: { data: { paymentId: 1, status: 'SUCCESS' } } }; } };
    const preview = { status: 'ready', result: { sufficient, totalAmount: 50000, receivedAmount: 60000, changeAmount: 10000 } };
    const factory = new Function('React', 'useEffect', 'useRef', 'useState', 'SaleCompletionStatus', 'PaymentService', 'getErrorMessage', 'useCashChangePreview', 'sandboxPaymentUrl', 'paymentExpiry', 'resumablePaymentUrl', 'Banknote', 'CreditCard', 'QrCode', 'ExternalLink', 'RefreshCw', `${code}; return PaymentPanel;`);
    const panel = factory(React, () => {}, useRef, useState, () => {}, PaymentService, (error) => error.response.data.message, () => preview, () => null, () => NaN, () => null, 'banknote', 'card', 'qr', 'external', 'refresh');
    const render = () => { index = 0; return panel({ sale, busy, onLock() {}, runAction: async (action) => { if (executing) return; executing = true; try { await action(); } finally { executing = false; } } }); };
    const submit = () => { const form = find(render(), (node) => node.type === 'form'); const result = form.props.onSubmit({ preventDefault() {} }); effects.push(result); return result; };
    return { state, calls, render, submit, finish, settle: () => Promise.all(effects) };
}

test('POS cash executes directly with guards; electronic methods retain confirmation', async () => {
const cash = harness();
cash.submit();
cash.submit();
assert.equal(cash.calls.length, 1, 'rapid repeated submissions use runAction lock');
assert.equal(cash.state[13], null, 'cash opens no confirmation modal');
assert.deepEqual(cash.calls[0].payload, { paymentMethod: 'CASH', bankCode: null, expectedTotal: 50000, receivedAmount: 60000, idempotencyKey: cash.calls[0].payload.idempotencyKey });
assert.ok(cash.calls[0].payload.idempotencyKey);
assert.match(text(cash.render()), /Change Due10000|Change Due10\.000/);
cash.finish(); await cash.settle();
assert.equal(cash.state[7].attempts[0].status, 'SUCCESS');
for (const settings of [{ sufficient: false }, { busy: true }]) {
    const blocked = harness(settings); blocked.submit(); assert.equal(blocked.calls.length, 0); assert.equal(blocked.state[13], null);
}
for (const method of ['CARD', 'QR']) {
    const electronic = harness({ method }); electronic.submit();
    assert.equal(electronic.calls.length, 0);
    assert.equal(electronic.state[13].payload.paymentMethod, method);
    assert.equal(electronic.state[13].isCash, false);
    assert.ok(find(electronic.render(), (node) => node.props.role === 'dialog'));
    assert.doesNotMatch(text(electronic.render()), /Backend label/);
}
const rejected = harness({ fail: true }); rejected.submit(); rejected.finish(); await rejected.settle();
assert.equal(rejected.state[10], 'Payment rejected');
assert.equal(rejected.state[7].attempts.length, 0, 'rejection never simulates success');
});
