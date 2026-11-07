import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PAGE_ACCESS, canAccess } from './pageAccess.js';
import { menuFor } from '../common/sidebarMenu.js';

// Default permissions of each role (backend RolePermissions = SRS 3.1.3)
const ROLE_DEFAULTS = {
    ADMIN: null, // every permission, filled below
    MANAGER: ['DASHBOARD_VIEW', 'NOTIFICATION_VIEW', 'PRODUCT_VIEW', 'PRODUCT_MANAGE', 'CATEGORY_MANAGE', 'PRICE_MANAGE',
        'PROMOTION_MANAGE', 'PROMOTION_VIEW_ACTIVE', 'EMPLOYEE_VIEW', 'EMPLOYEE_MANAGE', 'SUPPLIER_VIEW', 'SUPPLIER_MANAGE',
        'SUPPLIER_DISABLE', 'SALE_VIEW', 'INVOICE_VIEW', 'INVENTORY_VIEW', 'STOCK_HISTORY_VIEW', 'STOCK_ALERT_VIEW',
        'PURCHASE_REQUEST_APPROVE', 'PURCHASE_ORDER_VIEW', 'REPORT_SALES', 'REPORT_REVENUE', 'REPORT_PRODUCT_SALES',
        'REPORT_INVENTORY'],
    CASHIER: ['DASHBOARD_VIEW', 'NOTIFICATION_VIEW', 'PRODUCT_VIEW', 'PROMOTION_VIEW_ACTIVE', 'SUPPLIER_VIEW',
        'SUPPLIER_MANAGE', 'INVOICE_VIEW', 'POS_SALE'],
    WAREHOUSE_STAFF: ['DASHBOARD_VIEW', 'NOTIFICATION_VIEW', 'PRODUCT_VIEW', 'SUPPLIER_VIEW', 'SUPPLIER_MANAGE',
        'INVENTORY_VIEW', 'STOCK_CHECK', 'STOCK_ADJUST', 'STOCK_HISTORY_VIEW', 'STOCK_ALERT_VIEW', 'STOCK_IMPORT',
        'PURCHASE_REQUEST_CREATE', 'PURCHASE_ORDER_VIEW', 'REPORT_INVENTORY'],
    SUPPLIER: ['SUPPLIER_PORTAL', 'NOTIFICATION_VIEW']
};
ROLE_DEFAULTS.ADMIN = [...new Set(Object.values(ROLE_DEFAULTS).filter(Boolean).flat()),
    'USER_VIEW', 'USER_MANAGE', 'ROLE_MANAGE', 'SYSTEM_SETTINGS_MANAGE', 'SYSTEM_LOG_VIEW'];

const userOf = (role) => ({ role, permissions: ROLE_DEFAULTS[role] });
const menuLabels = (role) => menuFor(userOf(role)).flatMap((group) => group.items.map((item) => item.label));

test('not logged in: no page', () => {
    assert.equal(canAccess(null, PAGE_ACCESS.products), false);
    assert.equal(canAccess(null, undefined), false);
});

test('pages without a rule (profile, change password) are open to every account', () => {
    assert.equal(canAccess(userOf('SUPPLIER'), undefined), true);
});

test('Cashier menu: POS, invoices, products, suppliers, promotions - no purchase orders, no admin', () => {
    assert.deepEqual(menuLabels('CASHIER'), ['Dashboard', 'Product Management', 'Sales & POS', 'Search invoices',
        'Supplier Management', 'Promotion Management']);
});

test('Inventory Staff menu: stock, receipts, purchase requests, inventory report', () => {
    assert.deepEqual(menuLabels('WAREHOUSE_STAFF'), ['Dashboard', 'Product Management', 'Purchase & Inventory',
        'Purchase Orders', 'Stock Receipts', 'Stock History', 'Stock Alerts', 'Supplier Management', 'Inventory Report']);
});

test('Manager menu: approvals and all reports, no POS, no administration except employees', () => {
    const labels = menuLabels('MANAGER');
    assert.ok(labels.includes('Approve Requests'));
    assert.ok(labels.includes('Revenue Report') && labels.includes('Inventory Report'));
    assert.ok(labels.includes('Employee Management'));
    assert.ok(!labels.includes('Sales & POS'));
    assert.ok(!labels.includes('User Management') && !labels.includes('System Settings'));
});

test('Admin sees every staff page but not the Supplier portal', () => {
    const groups = menuFor(userOf('ADMIN')).map((group) => group.title);
    assert.deepEqual(groups, ['OVERVIEW', 'OPERATIONS', 'REPORTS', 'ADMINISTRATION']);
    assert.equal(canAccess(userOf('ADMIN'), PAGE_ACCESS.supplierPortal), false);
});

test('Supplier only sees the portal, even with a staff permission (UC48 BR-01)', () => {
    assert.deepEqual(menuLabels('SUPPLIER'), ['Purchase Orders', 'Notifications']);
    const supplierWithStaffPermission = { role: 'SUPPLIER', permissions: ['SUPPLIER_PORTAL', 'PRODUCT_VIEW'] };
    assert.equal(canAccess(supplierWithStaffPermission, PAGE_ACCESS.products), false);
});

test('UC06: a permission added by the Admin opens the page', () => {
    const cashier = { role: 'CASHIER', permissions: [...ROLE_DEFAULTS.CASHIER, 'REPORT_REVENUE'] };
    assert.equal(canAccess(userOf('CASHIER'), PAGE_ACCESS.reportRevenue), false);
    assert.equal(canAccess(cashier, PAGE_ACCESS.reportRevenue), true);
});

test('invoices: role Admin/Manager/Cashier AND INVOICE_VIEW (UC22/UC23)', () => {
    assert.equal(canAccess({ role: 'WAREHOUSE_STAFF', permissions: ['INVOICE_VIEW'] }, PAGE_ACCESS.invoices), false);
    assert.equal(canAccess({ role: 'CASHIER', permissions: [] }, PAGE_ACCESS.invoices), false);
    assert.equal(canAccess(userOf('CASHIER'), PAGE_ACCESS.invoices), true);
});
