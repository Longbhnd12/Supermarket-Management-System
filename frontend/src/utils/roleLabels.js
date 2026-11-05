// Display names for the role codes stored in the database
export const ROLE_LABELS = {
    ADMIN: 'Admin',
    MANAGER: 'Manager',
    CASHIER: 'Cashier',
    WAREHOUSE_STAFF: 'Inventory Staff',
    SUPPLIER: 'Supplier'
};

export function roleLabel(roleName) {
    return ROLE_LABELS[roleName] || roleName || '';
}
