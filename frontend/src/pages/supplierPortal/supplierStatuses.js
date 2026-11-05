// Purchase order statuses visible to a Supplier (UC48 BR-02)
export const SUPPLIER_STATUSES = [
    { value: 'SENT', label: 'Awaiting response' },
    { value: 'ACCEPTED', label: 'Accepted' },
    { value: 'REJECTED_BY_SUPPLIER', label: 'Rejected' },
    { value: 'PARTIALLY_FULFILLED', label: 'Partially fulfilled' },
    { value: 'FULFILLED', label: 'Fulfilled' }
];

export function statusLabel(status) {
    return SUPPLIER_STATUSES.find((item) => item.value === status)?.label || status || '';
}
