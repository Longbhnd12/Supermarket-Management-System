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

// Message on the Login page, by the reason the user was logged out (AuthProvider "reason").
export const LOGIN_NOTICES = {
    expired: 'Your session has expired. Please log in again.',
    'other-tab': 'You have been logged out in another tab.'
};

const AUTH_PAGES = ['/login', '/forgot-password'];

// After the session expired, go back to the page the user was on, if that page fits the role:
// a Supplier only uses the Supplier portal, staff never land in it.
export function getReturnPath(from, role) {
    const home = getHomePathByRole(role);
    if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//')) return home;
    const path = from.split(/[?#]/)[0];
    if (AUTH_PAGES.includes(path)) return home;
    const isSupplierPage = path === '/supplier' || path.startsWith('/supplier/');
    if ((role === 'SUPPLIER') !== isSupplierPage) return home;
    return from;
}
