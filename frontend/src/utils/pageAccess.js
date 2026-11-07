import { hasAnyPermission } from './permissions.js';

// Who can open each page (SRS 3.1.3 Screen Authorization + UC06 permissions).
// Same permissions as the backend table (backend/security/ApiPermissionRules.java):
// a page is shown only when its main API call would be allowed.
//
// access = { roles?: [...], anyOf?: [...] }
//   roles  - the account must have one of these roles (omitted = any role)
//   anyOf  - the account must have at least ONE of these permissions (omitted = none needed)
// A page without access rule (profile, change password) is open to every logged-in account.

export const STAFF_ROLES = ['ADMIN', 'MANAGER', 'CASHIER', 'WAREHOUSE_STAFF'];

const staff = (...anyOf) => ({ roles: STAFF_ROLES, anyOf });

export const PAGE_ACCESS = {
    dashboard: { roles: ['ADMIN', 'MANAGER'], anyOf: ['DASHBOARD_VIEW'] },
    notifications: { anyOf: ['NOTIFICATION_VIEW'] }, // Supplier too

    // Products & promotions
    products: staff('PRODUCT_VIEW'),
    productForm: staff('PRODUCT_MANAGE'),
    priceManage: staff('PRICE_MANAGE', 'PRODUCT_MANAGE'),
    categories: staff('CATEGORY_MANAGE'),
    promotions: staff('PROMOTION_MANAGE', 'PROMOTION_VIEW_ACTIVE'),
    promotionForm: staff('PROMOTION_MANAGE'),
    promotionCreate: staff('PROMOTION_MANAGE'),

    // Employees, suppliers, administration
    employees: staff('EMPLOYEE_VIEW'),
    employeeDetail: staff('EMPLOYEE_VIEW'),
    employeeForm: staff('EMPLOYEE_MANAGE'),
    suppliers: staff('SUPPLIER_VIEW'),
    supplierForm: staff('SUPPLIER_MANAGE'),
    users: staff('USER_VIEW'),
    userForm: staff('USER_MANAGE'),
    roles: staff('ROLE_MANAGE'),
    systemSettings: staff('SYSTEM_SETTINGS_MANAGE'),
    systemLogs: staff('SYSTEM_LOG_VIEW'),

    // Sales
    sales: staff('SALE_VIEW', 'POS_SALE'),
    salesMonitoring: { roles: ['ADMIN', 'MANAGER'], anyOf: ['SALE_VIEW'] },
    pos: staff('POS_SALE'),
    paymentReturn: staff('POS_SALE'),
    invoices: { roles: ['ADMIN', 'MANAGER', 'CASHIER'], anyOf: ['INVOICE_VIEW'] }, // UC22/UC23

    // Inventory
    inventory: staff('INVENTORY_VIEW'),
    stockCheck: staff('STOCK_CHECK', 'INVENTORY_VIEW'),
    stockAdjust: staff('STOCK_ADJUST'),
    stockHistory: staff('STOCK_HISTORY_VIEW'),
    stockAlerts: staff('STOCK_ALERT_VIEW'),
    stockReceipts: staff('STOCK_IMPORT', 'INVENTORY_VIEW'),
    stockReceiptForm: staff('STOCK_IMPORT'),

    // Purchase orders (UC46, UC47, UC50)
    purchaseOrders: staff('PURCHASE_ORDER_VIEW', 'PURCHASE_REQUEST_CREATE', 'PURCHASE_REQUEST_APPROVE'),
    purchaseRequestCreate: staff('PURCHASE_REQUEST_CREATE'),
    purchaseRequestApprove: staff('PURCHASE_REQUEST_APPROVE'),

    // Supplier portal (UC48, UC49)
    supplierPortal: { roles: ['SUPPLIER'], anyOf: ['SUPPLIER_PORTAL'] },

    // Reports (UC38-UC41)
    reportSales: staff('REPORT_SALES'),
    reportRevenue: staff('REPORT_REVENUE'),
    reportProductSales: staff('REPORT_PRODUCT_SALES'),
    reportInventory: staff('REPORT_INVENTORY')
};

export function canAccess(user, access) {
    if (!user) return false;
    if (!access) return true;
    if (access.roles?.length && !access.roles.includes(user.role)) return false;
    if (access.anyOf?.length && !hasAnyPermission(user, access.anyOf)) return false;
    return true;
}
