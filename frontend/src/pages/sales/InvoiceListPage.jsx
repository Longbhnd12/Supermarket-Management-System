import { useEffect, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import { ReceiptText, Search, RotateCcw, ArrowUpRight, ChevronLeft, ChevronRight, UserRound, CalendarDays } from 'lucide-react';
import invoiceService from '../../services/invoiceService';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import './SaleListPage.css';
import './InvoiceListPage.css';

const EMPTY_FILTERS = { invoiceNumber: '', fromDate: '', toDate: '' };
const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value ?? 0);
const dateTime = (value) => value ? value.replace('T', ' ').replace(/\.\d+$/, '') : '—';

export default function InvoiceListPage() {
    const navigate = useNavigate();
    const { currentUser } = useOutletContext() ?? {};
    const cashierName = currentUser?.username || '—';
    const [draft, setDraft] = useState(EMPTY_FILTERS);
    const [query, setQuery] = useState({ ...EMPTY_FILTERS, page: 0, size: 20 });
    const [loaded, setLoaded] = useState(null);
    const [validation, setValidation] = useState('');
    const result = loaded?.query === query ? loaded : null;
    const loading = !result;

    useEffect(() => {
        const controller = new AbortController();
        let active = true;
        invoiceService.search(query, controller.signal).then((response) => {
            if (active) setLoaded({ query, data: response.data, error: '' });
        }).catch((error) => {
            if (!active) return;
            if (error.response?.status === 401) navigate('/login', { replace: true });
            setLoaded({ query, data: null, error: getAccessAwareErrorMessage(error) });
        });
        return () => { active = false; controller.abort(); };
    }, [query, navigate]);

    const apply = (event) => {
        event.preventDefault();
        if (draft.fromDate && draft.toDate && draft.fromDate > draft.toDate) {
            setValidation('From date cannot be after To date.');
            return;
        }
        setValidation('');
        setQuery({ ...draft, invoiceNumber: draft.invoiceNumber.trim(), page: 0, size: query.size });
    };
    const reset = () => {
        setDraft(EMPTY_FILTERS); setValidation('');
        setQuery({ ...EMPTY_FILTERS, page: 0, size: query.size });
    };
    const change = (name, value) => setDraft(current => ({ ...current, [name]: value }));
    const data = result?.data;

    return <div className="sale-list-page invoice-list-page">
        <header className="sale-list-header"><div className="invoice-list-heading"><span className="invoice-list-heading-icon"><ReceiptText size={24} /></span><div><h1>Search invoices</h1><p>Find and review issued invoices in one place.</p></div></div><span className="invoice-list-account"><UserRound size={16} /><span>Viewing as: {cashierName}</span></span></header>
        <form className="sale-filters" onSubmit={apply}>
            <label className="sale-search-field"><span>Invoice number</span><div><Search size={17} /><input maxLength={50} value={draft.invoiceNumber} onChange={e => change('invoiceNumber', e.target.value)} placeholder="Search by invoice number…" /></div></label>
            <label><span>From date</span><input type="date" value={draft.fromDate} onChange={e => change('fromDate', e.target.value)} /></label>
            <label><span>To date</span><input type="date" value={draft.toDate} onChange={e => change('toDate', e.target.value)} /></label>
            <div className="sale-filter-actions"><button type="button" className="sale-button sale-button--secondary" onClick={reset}><RotateCcw size={16} />Reset</button><button className="sale-button sale-button--primary" type="submit"><Search size={16} />Search</button></div>
        </form>
        {(validation || result?.error) && <div className="sale-list-error" role="alert">{validation || result.error}</div>}
        <section className="sale-table-card" aria-busy={loading}>
            <div className="invoice-list-table-heading"><div><ReceiptText size={18} /><h2>Issued invoices</h2><span className="invoice-list-count">{data?.totalElements ?? '—'}</span></div><span><CalendarDays size={14} />Vietnam time · UTC+7</span></div>
            <div className="sale-table-scroll"><table><thead><tr><th>Invoice number</th><th>Sale number</th><th>Issued at (VN)</th><th>Cashier</th><th>Total</th><th>Details</th></tr></thead><tbody>
                {loading ? <tr><td colSpan={6} className="sale-empty" role="status">Loading invoices…</td></tr>
                    : result?.error ? <tr><td colSpan={6} className="sale-empty">Could not load invoices. Search again to retry.</td></tr>
                        : !data?.content?.length ? <tr><td colSpan={6} className="sale-empty">No invoices match the selected criteria.</td></tr>
                            : data.content.map(invoice => <tr key={invoice.saleId}><td className="invoice-list-number"><span title={invoice.invoiceNumber}>{invoice.invoiceNumber}</span></td><td className="invoice-list-sale"><span title={invoice.saleNumber}>{invoice.saleNumber}</span></td><td className="invoice-list-date">{dateTime(invoice.completedAt)}</td><td><span className="invoice-list-cashier"><UserRound size={14} />{invoice.cashierUsername || '—'}</span></td><td className="sale-amount">{money(invoice.totalAmount)}</td><td><Link className="invoice-list-view" to={`/sales/${invoice.saleId}/invoice`} aria-label={`View invoice ${invoice.invoiceNumber}`}>View<ArrowUpRight size={15} /></Link></td></tr>)}
            </tbody></table></div>
            <footer className="sale-pagination"><span>{data ? `${data.totalElements} invoices · Page ${data.totalPages ? data.page + 1 : 0} of ${data.totalPages}` : '—'}</span><div>
                <select aria-label="Rows per page" value={query.size} onChange={e => setQuery(current => ({ ...current, size: Number(e.target.value), page: 0 }))}>{[10, 20, 50, 100].map(size => <option key={size} value={size}>{size} / page</option>)}</select>
                <button disabled={loading || !data || data.page === 0} onClick={() => setQuery(current => ({ ...current, page: current.page - 1 }))}><ChevronLeft size={15} />Previous</button>
                <button disabled={loading || !data || data.last} onClick={() => setQuery(current => ({ ...current, page: current.page + 1 }))}>Next<ChevronRight size={15} /></button>
            </div></footer>
        </section>
    </div>;
}
