import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hasAllPermissions, hasAnyPermission, hasPermission, hasRole } from './permissions.js';
import { getReturnPath } from './authRedirect.js';

const cashier = { role: 'CASHIER', permissions: ['SALE_CREATE', 'INVOICE_VIEW'] };

test('hasPermission checks the permission codes from /auth/me', () => {
    assert.equal(hasPermission(cashier, 'INVOICE_VIEW'), true);
    assert.equal(hasPermission(cashier, 'USER_MANAGE'), false);
    assert.equal(hasPermission(null, 'INVOICE_VIEW'), false);
    assert.equal(hasPermission({ role: 'ADMIN' }, 'USER_VIEW'), false); // no permissions array
});

test('hasAnyPermission / hasAllPermissions', () => {
    assert.equal(hasAnyPermission(cashier, ['USER_MANAGE', 'SALE_CREATE']), true);
    assert.equal(hasAnyPermission(cashier, []), false);
    assert.equal(hasAllPermissions(cashier, ['SALE_CREATE', 'INVOICE_VIEW']), true);
    assert.equal(hasAllPermissions(cashier, ['SALE_CREATE', 'USER_MANAGE']), false);
    assert.equal(hasAllPermissions(cashier, []), false);
});

test('hasRole', () => {
    assert.equal(hasRole(cashier, 'ADMIN', 'CASHIER'), true);
    assert.equal(hasRole(cashier, 'ADMIN'), false);
    assert.equal(hasRole(null, 'ADMIN'), false);
});

test('after the session expired the user returns to the same page', () => {
    assert.equal(getReturnPath('/products?page=2', 'CASHIER'), '/products?page=2');
    assert.equal(getReturnPath('/supplier/purchase-orders/7', 'SUPPLIER'), '/supplier/purchase-orders/7');
});

test('the return page must fit the role and stay inside the app', () => {
    assert.equal(getReturnPath('/users', 'SUPPLIER'), '/supplier/purchase-orders');
    assert.equal(getReturnPath('/supplier/purchase-orders', 'ADMIN'), '/');
    assert.equal(getReturnPath('//evil.example.com', 'ADMIN'), '/');
    assert.equal(getReturnPath('https://evil.example.com', 'ADMIN'), '/');
    assert.equal(getReturnPath('/login', 'ADMIN'), '/');
    assert.equal(getReturnPath(undefined, 'CASHIER'), '/sales/pos');
    assert.equal(getReturnPath('/', 'CASHIER'), '/sales/pos');
    assert.equal(getReturnPath(undefined, 'WAREHOUSE_STAFF'), '/inventory');
});
