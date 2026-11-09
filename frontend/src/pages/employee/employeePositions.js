/**
 * employeePositions.js
 * Centralized position definitions and utilities for Employee Management.
 * Employee positions represent store operational job titles (e.g., Cleaner, Security Guard, Cashier).
 * They are completely decoupled from User Roles (ADMIN, MANAGER, CASHIER, WAREHOUSE_STAFF, SUPPLIER).
 */

export const BASE_POSITIONS = [
    { value: 'Accounting', label: 'Accounting', desc: 'Finance & Store Accounting' },
    { value: 'Administrator', label: 'Administrator', desc: 'Store Systems Administration' },
    { value: 'Cashier', label: 'Cashier', desc: 'Frontline Retail / POS Cashier' },
    { value: 'Cleaner', label: 'Cleaner', desc: 'Store Sanitation & Maintenance' },
    { value: 'Customer Service Staff', label: 'Customer Service Staff', desc: 'Customer Help Desk & Service' },
    { value: 'Inventory Staff', label: 'Inventory Staff', desc: 'Warehouse & Receiving' },
    { value: 'Manager', label: 'Manager', desc: 'Store Operations Supervisor' },
    { value: 'Security Guard', label: 'Security Guard', desc: 'Store Asset Protection & Security' },
    { value: 'Shelf Stocker', label: 'Shelf Stocker', desc: 'Store Floor & Display Stocking' },
    { value: 'Stock Handler', label: 'Stock Handler', desc: 'Material Handling & Goods Inward' },
];

export const POSITION_DESCRIPTIONS = {
    'Accounting': 'Accounting — Finance & Store Accounting',
    'Administrator': 'Administrator — Store Systems Administration',
    'Cashier': 'Cashier — Frontline Retail / POS Cashier',
    'Cleaner': 'Cleaner — Store Sanitation & Maintenance',
    'Customer Service Staff': 'Customer Service Staff — Customer Help Desk & Service',
    'Inventory Staff': 'Inventory Staff — Warehouse & Receiving',
    'Manager': 'Manager — Store Operations Supervisor',
    'Security Guard': 'Security Guard — Store Asset Protection & Security',
    'Shelf Stocker': 'Shelf Stocker — Store Floor & Display Stocking',
    'Stock Handler': 'Stock Handler — Material Handling & Goods Inward',
    'POS Support': 'POS Support — Hardware & Terminal Technician',
    'Warehouse Staff': 'Warehouse Staff — Warehouse & Receiving',
    'Thu ngân': 'Thu ngân — Frontline Retail / POS Cashier',
    'Nhân viên kho': 'Nhân viên kho — Warehouse & Receiving',
    'Quản lý': 'Quản lý — Store Operations Supervisor',
    'Quản trị viên': 'Quản trị viên — Store Systems Administration',
};

export const getPositionLabel = (pos) => {
    if (!pos) return '';
    return POSITION_DESCRIPTIONS[pos] || pos;
};

/**
 * Merge baseline positions with any distinct positions present in loaded employees and any custom position.
 */
export const getMergedPositions = (employees = [], extraPosition = '') => {
    const map = new Map();
    // Add baseline standard positions
    BASE_POSITIONS.forEach((p) => {
        map.set(p.value.toLowerCase(), { value: p.value, label: p.label, desc: p.desc });
    });

    // Add positions discovered from employee database records
    if (Array.isArray(employees)) {
        employees.forEach((emp) => {
            if (emp.position && typeof emp.position === 'string' && emp.position.trim()) {
                const trimmed = emp.position.trim();
                const key = trimmed.toLowerCase();
                if (!map.has(key)) {
                    map.set(key, {
                        value: trimmed,
                        label: trimmed,
                        desc: POSITION_DESCRIPTIONS[trimmed] ? POSITION_DESCRIPTIONS[trimmed].split(' — ')[1] : 'Store Operations'
                    });
                }
            }
        });
    }

    // Add extra custom position if supplied and not yet in map
    if (extraPosition && typeof extraPosition === 'string' && extraPosition.trim()) {
        const trimmed = extraPosition.trim();
        const key = trimmed.toLowerCase();
        if (!map.has(key)) {
            map.set(key, {
                value: trimmed,
                label: trimmed,
                desc: 'Custom Position'
            });
        }
    }

    return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label));
};

