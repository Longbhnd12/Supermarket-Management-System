/**
 * Shared position definitions and formatting utilities for Employee Management.
 * Employee positions represent store operational job titles / duties.
 * Only the raw position value (e.g. "Security Guard", "Cleaner") is stored in the database.
 * Descriptions are purely UI decorations.
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
    'Accounting': 'Finance & Store Accounting',
    'Administrator': 'Store Systems Administration',
    'Cashier': 'Frontline Retail / POS Cashier',
    'Cleaner': 'Store Sanitation & Maintenance',
    'Customer Service Staff': 'Customer Help Desk & Service',
    'Inventory Staff': 'Warehouse & Receiving',
    'Manager': 'Store Operations Supervisor',
    'Security Guard': 'Store Asset Protection & Security',
    'Shelf Stocker': 'Store Floor & Display Stocking',
    'Stock Handler': 'Material Handling & Goods Inward',
};

/**
 * Return formatted display label with description for UI dropdowns.
 * E.g., "Cashier — Frontline Retail / POS Cashier".
 */
export function getPositionDisplayLabel(pos) {
    if (!pos) return '';
    const desc = POSITION_DESCRIPTIONS[pos];
    return desc ? `${pos} — ${desc}` : pos;
}

/**
 * Merge baseline positions with positions retrieved from the database,
 * plus any extra position (e.g., from an existing employee being edited or custom entered).
 * Returns a deduplicated array sorted alphabetically by position name.
 */
export function mergePositions(dbPositions = [], extraPosition = '') {
    const map = new Map();

    // 1. Add baseline known positions
    BASE_POSITIONS.forEach((p) => {
        map.set(p.value.toLowerCase(), {
            value: p.value,
            label: p.label,
            desc: p.desc
        });
    });

    // 2. Add database positions
    if (Array.isArray(dbPositions)) {
        dbPositions.forEach((pos) => {
            if (pos && typeof pos === 'string' && pos.trim()) {
                const trimmed = pos.trim();
                const key = trimmed.toLowerCase();
                if (!map.has(key)) {
                    map.set(key, {
                        value: trimmed,
                        label: trimmed,
                        desc: POSITION_DESCRIPTIONS[trimmed] || 'Store Operations'
                    });
                }
            }
        });
    }

    // 3. Add extra position if provided
    if (extraPosition && typeof extraPosition === 'string' && extraPosition.trim()) {
        const trimmed = extraPosition.trim();
        const key = trimmed.toLowerCase();
        if (!map.has(key)) {
            map.set(key, {
                value: trimmed,
                label: trimmed,
                desc: POSITION_DESCRIPTIONS[trimmed] || 'Custom Position'
            });
        }
    }

    return Array.from(map.values()).sort((a, b) => a.value.localeCompare(b.value));
}

