// UC01 - Login: where each role lands after a successful login.
// Admin, Manager, Cashier, Inventory Staff -> Dashboard of their role ("/").
// Supplier -> Purchase Order List of the Supplier portal (SRS 4.1.1).
const ROLE_HOME = {
    ADMIN: '/',
    MANAGER: '/',
    CASHIER: '/',
    WAREHOUSE_STAFF: '/',
    SUPPLIER: '/supplier/purchase-orders'
};

export function getHomePathByRole(role) {
    return ROLE_HOME[role] || '/';
}
