export function sandboxPaymentUrl(value) {
    try {
        const url = new URL(value);
        return url.origin === 'https://sandbox.vnpayment.vn'
            && url.pathname === '/paymentv2/vpcpay.html' && !url.username && !url.password ? url.href : null;
    } catch { return null; }
}

// BE serializes LocalDateTime in the configured Asia/Ho_Chi_Minh payment clock.
export function paymentExpiry(value) {
    if (!value) return NaN;
    return Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}+07:00`);
}

export function resumablePaymentUrl(payment, now = Date.now()) {
    if (!payment || payment.paymentMethod === 'CASH' || payment.status !== 'PENDING'
        || payment.reconciliationRequired || !(paymentExpiry(payment.expiresAt) > now)) return null;
    return sandboxPaymentUrl(payment.paymentUrl);
}