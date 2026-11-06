// SRS 4.2.6: amounts in VND with thousands separators, dates dd/MM/yyyy, 24-hour time
const pad = (n) => String(n).padStart(2, '0');

export function formatVnd(value) {
    if (value === null || value === undefined || value === '') return '—';
    const number = Number(value);
    if (Number.isNaN(number)) return '—';
    return `${Math.round(number).toLocaleString('en-US')} ₫`;
}

export function formatQuantity(value) {
    if (value === null || value === undefined || value === '') return '—';
    const number = Number(value);
    return Number.isNaN(number) ? '—' : number.toLocaleString('en-US', { maximumFractionDigits: 3 });
}

export function formatDate(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

export function formatDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return `${formatDate(value)} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
