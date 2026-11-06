import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Eye, RefreshCw, Search } from 'lucide-react';
import SupplierPortalService from '../../services/supplierPortalService';
import Pagination from '../../components/ui/Pagination';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { formatDate, formatVnd } from '../../utils/formatters';
import StatusBadge from './StatusBadge';
import { SUPPLIER_STATUSES } from './supplierStatuses';
import '../user/UserManagement.css';
import './SupplierPortal.css';

// UC48 - View Purchase Orders (Supplier), SRS Table 3-44
const EMPTY_RESULT = { content: [], page: 0, totalElements: 0, totalPages: 0, counts: null };
const COUNT_KEYS = {
    SENT: 'awaitingResponse',
    ACCEPTED: 'accepted',
    REJECTED_BY_SUPPLIER: 'rejectedBySupplier',
    PARTIALLY_FULFILLED: 'partiallyFulfilled',
    FULFILLED: 'fulfilled'
};

export default function SupplierOrderListPage() {
    const [keywordInput, setKeywordInput] = useState('');
    const [filters, setFilters] = useState({ keyword: '', status: '', fromDate: '', toDate: '' });
    const [paging, setPaging] = useState({ page: 0, size: 10 });
    const [reloadKey, setReloadKey] = useState(0);
    const [loaded, setLoaded] = useState({ key: null, result: EMPTY_RESULT, error: '' });
    const [dateError, setDateError] = useState('');

    const queryKey = useMemo(() => JSON.stringify({ ...filters, ...paging, reloadKey }), [filters, paging, reloadKey]);
    const loading = loaded.key !== queryKey;
    const { result } = loaded;

    useEffect(() => {
        let active = true;
        SupplierPortalService.getOrders({
            keyword: filters.keyword || undefined,
            status: filters.status || undefined,
            fromDate: filters.fromDate || undefined,
            toDate: filters.toDate || undefined,
            page: paging.page,
            size: paging.size
        })
            .then(({ data }) => active && setLoaded({ key: queryKey, result: data.data, error: '' }))
            .catch((err) => active && setLoaded({ key: queryKey, result: EMPTY_RESULT, error: getAccessAwareErrorMessage(err) }));
        return () => { active = false; };
    }, [queryKey, filters, paging]);

    const applyFilters = (changes) => {
        setPaging((current) => ({ ...current, page: 0 }));
        setFilters((current) => ({ ...current, ...changes }));
    };

    const submitSearch = (event) => {
        event.preventDefault();
        applyFilters({ keyword: keywordInput.trim() });
    };

    const changeDate = (field) => (event) => {
        const next = { ...filters, [field]: event.target.value };
        if (next.fromDate && next.toDate && next.fromDate > next.toDate) {
            setDateError('Start date cannot be later than end date.');
            return;
        }
        setDateError('');
        applyFilters({ [field]: event.target.value });
    };

    const orders = result?.content || [];
    const counts = result?.counts;
    const nothingAtAll = !loading && !loaded.error && counts
        && Object.values(counts).every((value) => value === 0);

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>Purchase Orders</h1>
                    <p>Purchase orders issued to your company. Open an order to accept, reject or propose changes.</p>
                </div>
                <button type="button" className="um-btn um-btn-outline" onClick={() => setReloadKey((key) => key + 1)} disabled={loading}>
                    <RefreshCw size={16} /> Refresh
                </button>
            </div>

            {loaded.error && <p className="um-alert um-alert-error" role="alert">{loaded.error}</p>}

            <div className="sp-summary">
                {SUPPLIER_STATUSES.map(({ value, label }) => (
                    <button
                        key={value}
                        type="button"
                        className={`sp-summary-card${filters.status === value ? ' is-active' : ''}`}
                        onClick={() => applyFilters({ status: filters.status === value ? '' : value })}
                        title={filters.status === value ? 'Show all statuses' : `Show only: ${label}`}
                    >
                        <span>{label}</span>
                        <strong>{counts ? counts[COUNT_KEYS[value]] : '–'}</strong>
                    </button>
                ))}
            </div>

            <form className="um-card sp-filters" onSubmit={submitSearch}>
                <label className="um-field um-search">
                    Search
                    <Search size={16} />
                    <input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} placeholder="PO number" maxLength={100} />
                </label>
                <label className="um-field">
                    Status
                    <select value={filters.status} onChange={(event) => applyFilters({ status: event.target.value })}>
                        <option value="">All statuses</option>
                        {SUPPLIER_STATUSES.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
                <label className="um-field">
                    Issued from
                    <input type="date" value={filters.fromDate} onChange={changeDate('fromDate')} />
                </label>
                <label className="um-field">
                    Issued to
                    <input type="date" value={filters.toDate} onChange={changeDate('toDate')} />
                    {dateError && <span className="um-field-error">{dateError}</span>}
                </label>
                <button type="submit" className="um-btn um-btn-primary"><Search size={16} /> Search</button>
            </form>

            <div className="um-card">
                <div className="um-table-wrap">
                    <table className="um-table">
                        <thead>
                            <tr>
                                <th>PO Number</th>
                                <th>Issued Date</th>
                                <th>Expected Delivery</th>
                                <th className="sp-number">Items</th>
                                <th className="sp-number">Total Amount</th>
                                <th>Status</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            {loading && <tr><td colSpan={7} className="um-empty">Loading purchase orders…</td></tr>}
                            {!loading && orders.length === 0 && (
                                <tr><td colSpan={7} className="um-empty">
                                    {nothingAtAll ? 'No purchase orders available.' : 'No search results found.'}
                                </td></tr>
                            )}
                            {!loading && orders.map((order) => (
                                <tr key={order.orderId}>
                                    <td className="um-username">{order.poNumber}</td>
                                    <td>{formatDate(order.issuedAt)}</td>
                                    <td>{formatDate(order.expectedDeliveryDate)}</td>
                                    <td className="sp-number">{order.itemCount}</td>
                                    <td className="sp-number">{formatVnd(order.totalAmount)}</td>
                                    <td>
                                        <StatusBadge status={order.status} />
                                        {order.hasProposal && <span className="sp-flag">Changes proposed</span>}
                                    </td>
                                    <td>
                                        <Link to={`/supplier/purchase-orders/${order.orderId}`} className="um-btn um-btn-outline">
                                            <Eye size={15} /> {order.status === 'SENT' ? 'Respond' : 'View Details'}
                                        </Link>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                <Pagination
                    page={result?.page ?? paging.page}
                    size={paging.size}
                    totalElements={result?.totalElements ?? 0}
                    totalPages={result?.totalPages ?? 0}
                    pageSizeOptions={[10, 20, 50]}
                    onPageChange={(page) => setPaging((current) => ({ ...current, page }))}
                    onSizeChange={(size) => setPaging({ page: 0, size })}
                    itemLabel="orders"
                    idPrefix="supplier-orders-pagination"
                    disabled={loading}
                />
            </div>
        </div>
    );
}
