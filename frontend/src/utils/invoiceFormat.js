// invoiceFormat - currency and date formatting utilities for invoices
const currency = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });
const quantity = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 });

export const invoiceMoney = value => `${currency.format(value ?? 0)} ₫`;
export const invoiceQuantity = value => quantity.format(value ?? 0);

// A snapshot's LocalDateTime already represents Vietnam time. Do not parse as a browser-local Date.
export function invoiceDateTime(value) {
    if (!value) return '—';
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2}:\d{2})/.exec(value);
    return match ? `${match[3]}/${match[2]}/${match[1]} ${match[4]}` : value;
}

// Snapshot monetary fields have at most two decimals; subtract in integer cents.
export function lineNetAmount(item) {
    return (Math.round(Number(item.subtotal) * 100) - Math.round(Number(item.discountAmount) * 100)) / 100;
}
