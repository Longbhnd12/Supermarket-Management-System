import { useCallback, useEffect, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, RotateCcw, Search } from 'lucide-react';
import saleService from '../../services/saleService';
import './SaleListPage.css';

const INITIAL_FILTERS = { search: '', status: '', fromDate: '', toDate: '' };
const STATUS_LABELS = { DRAFT: 'Pending', COMPLETED: 'Completed', CANCELLED: 'Cancelled' };
const STATUS_CLASSES = { DRAFT: 'sale-status sale-status--pending', COMPLETED: 'sale-status sale-status--completed', CANCELLED: 'sale-status sale-status--cancelled' };

export default function SaleListPage() {
    const [draftFilters, setDraftFilters] = useState(INITIAL_FILTERS);
    const [filters, setFilters] = useState(INITIAL_FILTERS);
    const [sales, setSales] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadSales = useCallback(async (page = 0, size = pagination.size) => {
        setLoading(true);
        setError(null);
        try {
            const params = { page, size, ...(filters.search.trim() && { search: filters.search.trim() }), ...(filters.status && { status: filters.status }) };
            if (filters.fromDate) params.fromDate = `${filters.fromDate}T00:00:00`;
            if (filters.toDate) params.toDate = `${filters.toDate}T23:59:59`;
            const response = await saleService.getSales(params);
            if (!response?.success) throw new Error(response?.message || 'Could not load sales.');
            setSales(response.data.content || []);
            setPagination({ page: response.data.page, size: response.data.size, totalElements: response.data.totalElements, totalPages: response.data.totalPages });
        } catch (requestError) {
            setSales([]);
            setError(requestError.response?.data?.message || requestError.message || 'Could not load sales.');
        } finally { setLoading(false); }
    }, [filters, pagination.size]);

    useEffect(() => {
        const timer = window.setTimeout(() => { void loadSales(); }, 0);
        return () => window.clearTimeout(timer);
    }, [loadSales]);

    const applyFilters = (event) => {
        event.preventDefault();
        if (draftFilters.fromDate && draftFilters.toDate && draftFilters.fromDate > draftFilters.toDate) {
            setError('From date cannot be after To date.');
            return;
        }
        setFilters(draftFilters);
        setPagination(current => ({ ...current, page: 0 }));
    };
    const changeFilter = (key, value) => setDraftFilters(current => ({ ...current, [key]: value }));
    const resetFilters = () => { setDraftFilters(INITIAL_FILTERS); setFilters(INITIAL_FILTERS); setPagination(current => ({ ...current, page: 0 })); };
    const formatAmount = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value ?? 0);
    const formatDate = (value) => value ? new Date(value).toLocaleString('vi-VN') : '—';

    return <div className="sale-list-page">
        <header className="sale-list-header"><div><h1>Sales List</h1><p>View pending, completed and cancelled sales.</p></div><strong>{pagination.totalElements.toLocaleString()} sales</strong></header>
        <form className="sale-filters" onSubmit={applyFilters}>
            <label className="sale-search-field"><span>Search</span><div><Search size={17} /><input value={draftFilters.search} onChange={(event) => changeFilter('search', event.target.value)} placeholder="Sale no., invoice or cashier" /></div></label>
            <label><span>Status</span><select value={draftFilters.status} onChange={(event) => changeFilter('status', event.target.value)}><option value="">All statuses</option><option value="PENDING">Pending</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option></select></label>
            <label><span>From date</span><input type="date" value={draftFilters.fromDate} onChange={(event) => changeFilter('fromDate', event.target.value)} /></label>
            <label><span>To date</span><input type="date" value={draftFilters.toDate} onChange={(event) => changeFilter('toDate', event.target.value)} /></label>
            <div className="sale-filter-actions"><button className="sale-button sale-button--secondary" type="button" onClick={resetFilters}><RotateCcw size={16} /> Reset</button><button className="sale-button sale-button--primary" type="submit">Apply filters</button></div>
        </form>
        {error && <div role="alert" className="sale-list-error">{error}</div>}
        <section className="sale-table-card"><div className="sale-table-scroll"><table><thead><tr><th>Sale number</th><th>Invoice number</th><th>Cashier</th><th>Created at</th><th>Total</th><th>Status</th></tr></thead><tbody>
            {loading ? <tr><td className="sale-empty" colSpan="6">Loading sales…</td></tr> : sales.length === 0 ? <tr><td className="sale-empty" colSpan="6"><CalendarDays size={24} /> No sales match the selected filters.</td></tr> : sales.map((sale) => <tr key={sale.saleId}><td className="sale-number">{sale.saleNumber || `#${sale.saleId}`}</td><td>{sale.invoiceNumber || '—'}</td><td>{sale.cashierUsername || 'System'}</td><td>{formatDate(sale.createdAt)}</td><td className="sale-amount">{formatAmount(sale.totalAmount)}</td><td><span className={STATUS_CLASSES[sale.status] || 'sale-status'}>{STATUS_LABELS[sale.status] || sale.status}</span></td></tr>)}
        </tbody></table></div><footer className="sale-pagination"><span>{pagination.totalElements === 0 ? 'No sales' : `Page ${pagination.page + 1} of ${pagination.totalPages} · ${pagination.totalElements} total`}</span><div><select aria-label="Rows per page" value={pagination.size} disabled={loading} onChange={(event) => void loadSales(0, Number(event.target.value))}>{[10, 20, 50, 100].map((size) => <option key={size} value={size}>{size} / page</option>)}</select><button aria-label="Previous page" disabled={pagination.page === 0 || loading} onClick={() => void loadSales(pagination.page - 1)}><ChevronLeft size={18} /></button><button aria-label="Next page" disabled={pagination.page >= pagination.totalPages - 1 || loading} onClick={() => void loadSales(pagination.page + 1)}><ChevronRight size={18} /></button></div></footer></section>
    </div>;
}
