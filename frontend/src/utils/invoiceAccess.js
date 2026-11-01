export function canViewInvoices(user) {
    return ['ADMIN', 'MANAGER', 'CASHIER'].includes(user?.role)
        && user?.permissions?.includes('INVOICE_VIEW') === true;
}
