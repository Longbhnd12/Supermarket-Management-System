import { useState, useEffect, useCallback, useRef } from 'react';
import StockReceiptService from '../../services/stockReceiptService';
import SupplierService from '../../services/supplierService';
import Pagination from '../../components/ui/Pagination';
import './StockReceiptListPage.css';

// ─── Status helpers ────────────────────────────────────────────────────────────

const STATUS_OPTIONS = [
    { value: '',          label: 'All Statuses' },
    { value: 'DRAFT',     label: 'Draft' },
    { value: 'CONFIRMED', label: 'Confirmed' },
    { value: 'CANCELLED', label: 'Cancelled' },
];

const STATUS_LABEL = {
    DRAFT:     'Draft',
    CONFIRMED: 'Confirmed',
    CANCELLED: 'Cancelled',
};

const STATUS_CLASS = {
    DRAFT:     'sr-badge sr-badge--draft',
    CONFIRMED: 'sr-badge sr-badge--confirmed',
    CANCELLED: 'sr-badge sr-badge--cancelled',
};

function StatusBadge({ status }) {
    return (
        <span className={STATUS_CLASS[status] || 'sr-badge sr-badge--draft'}>
            {STATUS_LABEL[status] || status}
        </span>
    );
}

// ─── Date formatter ────────────────────────────────────────────────────────────

function formatDateTime(iso) {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString('en-GB', {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    } catch {
        return iso;
    }
}

function formatCurrency(amount) {
    if (amount == null) return '—';
    return new Intl.NumberFormat('en-US', {
        style: 'currency', currency: 'USD', minimumFractionDigits: 2,
    }).format(amount);
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

export default function StockReceiptListPage() {
    // Search / filter state
    const [keyword, setKeyword]       = useState('');
    const [productSku, setProductSku] = useState('');
    const [supplierId, setSupplierId] = useState('');
    const [status, setStatus]         = useState('');
    const [dateFrom, setDateFrom]     = useState('');
    const [dateTo, setDateTo]         = useState('');

    // Pagination
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);

    // Data
    const [receipts, setReceipts]           = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages]       = useState(0);
    const [suppliers, setSuppliers]         = useState([]);

    // UI state
    const [loading, setLoading] = useState(false);
    const [error, setError]     = useState(null);
    const [dateError, setDateError] = useState('');

    // Debounce keyword
    const debounceTimer = useRef(null);
    const [debouncedKeyword, setDebouncedKeyword] = useState('');

    // Debounce productSku
    const skuTimer = useRef(null);
    const [debouncedSku, setDebouncedSku] = useState('');

    // Load suppliers once
    useEffect(() => {
        SupplierService.getAll()
            .then(res => setSuppliers(res.data?.data || []))
            .catch(() => setSuppliers([]));
    }, []);

    // Debounce keyword
    useEffect(() => {
        clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => {
            setDebouncedKeyword(keyword);
            setPage(0);
        }, 400);
        return () => clearTimeout(debounceTimer.current);
    }, [keyword]);

    // Debounce productSku
    useEffect(() => {
        clearTimeout(skuTimer.current);
        skuTimer.current = setTimeout(() => {
            setDebouncedSku(productSku);
            setPage(0);
        }, 400);
        return () => clearTimeout(skuTimer.current);
    }, [productSku]);

    // Reset page on filter change
    useEffect(() => {
        setPage(0);
    }, [supplierId, status, dateFrom, dateTo]);

    // Fetch receipts
    const fetchReceipts = useCallback(() => {
        // Validate date range before fetching
        if (dateFrom && dateTo && dateFrom > dateTo) {
            setDateError('From Date must not be later than To Date.');
            setReceipts([]);
            return;
        }
        setDateError('');
        setLoading(true);
        setError(null);

        const params = {
            page,
            size,
            ...(debouncedKeyword && { keyword: debouncedKeyword }),
            ...(debouncedSku     && { productSku: debouncedSku }),
            ...(supplierId       && { supplierId }),
            ...(status           && { status }),
            ...(dateFrom         && { dateFrom }),
            ...(dateTo           && { dateTo }),
        };

        StockReceiptService.getList(params)
            .then(res => {
                const data = res.data?.data;
                if (data) {
                    setReceipts(data.content || []);
                    setTotalElements(data.totalElements || 0);
                    setTotalPages(data.totalPages || 0);
                }
            })
            .catch(err => {
                setError(err.response?.data?.message || 'Failed to load stock receipts. Please try again.');
                setReceipts([]);
            })
            .finally(() => setLoading(false));
    }, [page, size, debouncedKeyword, debouncedSku, supplierId, status, dateFrom, dateTo]);

    useEffect(() => {
        fetchReceipts();
    }, [fetchReceipts]);

    // Reset all filters
    const handleReset = () => {
        setKeyword('');
        setProductSku('');
        setSupplierId('');
        setStatus('');
        setDateFrom('');
        setDateTo('');
        setPage(0);
        setDateError('');
    };


    return (
        <div className="sr-page">
            {/* ── Header ── */}
            <div className="sr-header">
                <div className="sr-header-left">
                    <nav className="sr-breadcrumb" aria-label="Breadcrumb">
                        <span className="sr-breadcrumb__item">Inventory</span>
                        <span className="sr-breadcrumb__sep">›</span>
                        <span className="sr-breadcrumb__item sr-breadcrumb__item--active">Stock Receipts</span>
                    </nav>
                    <h1 className="sr-title">Stock Receipt List</h1>
                    <p className="sr-subtitle">View and track all inbound stock receipts</p>
                </div>
                <div className="sr-header-actions">
                    {/* Create is unavailable until UC36 is implemented */}
                    <button
                        className="sr-btn sr-btn--primary"
                        disabled
                        title="Create Stock Receipt — not yet available"
                        aria-disabled="true"
                    >
                        <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"/>
                        </svg>
                        Create Receipt
                    </button>
                </div>
            </div>

            {/* ── Filters ── */}
            <div className="sr-filters-card">
                <div className="sr-filters-grid">
                    {/* Keyword search */}
                    <div className="sr-filter-group sr-filter-group--wide">
                        <label className="sr-filter-label" htmlFor="sr-keyword">Search</label>
                        <div className="sr-input-icon-wrap">
                            <svg className="sr-input-icon" width="16" height="16" viewBox="0 0 20 20" fill="none"
                                 stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                <circle cx="8.5" cy="8.5" r="5.5"/>
                                <path d="M15 15l-3-3" strokeLinecap="round"/>
                            </svg>
                            <input
                                id="sr-keyword"
                                className="sr-input sr-input--with-icon"
                                type="text"
                                placeholder="Receipt number or supplier name…"
                                value={keyword}
                                onChange={e => setKeyword(e.target.value)}
                            />
                        </div>
                    </div>

                    {/* Product SKU */}
                    <div className="sr-filter-group">
                        <label className="sr-filter-label" htmlFor="sr-sku">Product SKU</label>
                        <input
                            id="sr-sku"
                            className="sr-input"
                            type="text"
                            placeholder="Filter by SKU…"
                            value={productSku}
                            onChange={e => setProductSku(e.target.value)}
                        />
                    </div>

                    {/* Supplier */}
                    <div className="sr-filter-group">
                        <label className="sr-filter-label" htmlFor="sr-supplier">Supplier</label>
                        <select
                            id="sr-supplier"
                            className="sr-select"
                            value={supplierId}
                            onChange={e => setSupplierId(e.target.value)}
                        >
                            <option value="">All Suppliers</option>
                            {suppliers.map(s => (
                                <option key={s.supplierId} value={s.supplierId}>
                                    {s.supplierName}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Status */}
                    <div className="sr-filter-group">
                        <label className="sr-filter-label" htmlFor="sr-status">Status</label>
                        <select
                            id="sr-status"
                            className="sr-select"
                            value={status}
                            onChange={e => setStatus(e.target.value)}
                        >
                            {STATUS_OPTIONS.map(opt => (
                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                            ))}
                        </select>
                    </div>

                    {/* Date From */}
                    <div className="sr-filter-group">
                        <label className="sr-filter-label" htmlFor="sr-date-from">From Date</label>
                        <input
                            id="sr-date-from"
                            className={`sr-input${dateError ? ' sr-input--error' : ''}`}
                            type="date"
                            value={dateFrom}
                            max={dateTo || undefined}
                            onChange={e => setDateFrom(e.target.value)}
                        />
                    </div>

                    {/* Date To */}
                    <div className="sr-filter-group">
                        <label className="sr-filter-label" htmlFor="sr-date-to">To Date</label>
                        <input
                            id="sr-date-to"
                            className={`sr-input${dateError ? ' sr-input--error' : ''}`}
                            type="date"
                            value={dateTo}
                            min={dateFrom || undefined}
                            onChange={e => setDateTo(e.target.value)}
                        />
                    </div>
                </div>

                {/* Date validation error */}
                {dateError && (
                    <p className="sr-filter-error" role="alert">{dateError}</p>
                )}

                {/* Filter actions */}
                <div className="sr-filters-actions">
                    <button className="sr-btn sr-btn--ghost" onClick={handleReset} id="sr-btn-reset">
                        <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="currentColor"
                             strokeWidth="2" aria-hidden="true">
                            <path d="M4 10a6 6 0 1 0 1.5-4L4 4" strokeLinecap="round"/>
                            <path d="M4 4v3h3" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        Reset
                    </button>
                </div>
            </div>

            {/* ── Table Card ── */}
            <div className="sr-table-card">
                {/* Table toolbar */}
                <div className="sr-table-toolbar">
                    <div className="sr-table-info">
                        <span className="sr-table-count">
                            {loading
                                ? 'Loading…'
                                : totalElements > 0
                                ? `${totalElements.toLocaleString()} stock receipts`
                                : 'Stock Receipts'}
                        </span>
                    </div>
                </div>

                {/* Error state */}
                {error && !loading && (
                    <div className="sr-state sr-state--error" role="alert">
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             strokeWidth="1.5" aria-hidden="true">
                            <circle cx="12" cy="12" r="9"/>
                            <line x1="12" y1="8" x2="12" y2="12"/>
                            <circle cx="12" cy="16" r="0.5" fill="currentColor"/>
                        </svg>
                        <p className="sr-state__title">Something went wrong</p>
                        <p className="sr-state__msg">{error}</p>
                        <button className="sr-btn sr-btn--secondary" onClick={fetchReceipts}>
                            Try again
                        </button>
                    </div>
                )}

                {/* Loading skeleton */}
                {loading && (
                    <div className="sr-loading" role="status" aria-live="polite">
                        <div className="sr-spinner" aria-hidden="true" />
                        <span className="sr-loading__text">Loading stock receipts…</span>
                    </div>
                )}

                {/* Empty state */}
                {!loading && !error && receipts.length === 0 && (
                    <div className="sr-state sr-state--empty">
                        <svg width="52" height="52" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             strokeWidth="1.2" aria-hidden="true">
                            <rect x="3" y="3" width="18" height="18" rx="2"/>
                            <path d="M3 9h18M9 21V9"/>
                        </svg>
                        <p className="sr-state__title">No stock receipts found</p>
                        <p className="sr-state__msg">
                            Try adjusting your search filters or date range.
                        </p>
                    </div>
                )}

                {/* Data table */}
                {!loading && !error && receipts.length > 0 && (
                    <div className="sr-table-wrap">
                        <table className="sr-table" aria-label="Stock receipt list">
                            <thead>
                                <tr>
                                    <th scope="col">Receipt ID</th>
                                    <th scope="col">Receipt No.</th>
                                    <th scope="col">Supplier</th>
                                    <th scope="col">Inbound Date</th>
                                    <th scope="col">Total Cost</th>
                                    <th scope="col">Status</th>
                                    <th scope="col">Created By</th>
                                    <th scope="col" className="sr-th-actions">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {receipts.map(receipt => (
                                    <tr key={receipt.stockReceiptId} className="sr-tr">
                                        <td className="sr-td sr-td--id">
                                            #{receipt.stockReceiptId}
                                        </td>
                                        <td className="sr-td sr-td--receipt-no">
                                            <span className="sr-receipt-no">{receipt.receiptNumber}</span>
                                        </td>
                                        <td className="sr-td">
                                            <span className="sr-supplier-name">{receipt.supplierName}</span>
                                        </td>
                                        <td className="sr-td sr-td--date">
                                            {formatDateTime(receipt.receivedAt || receipt.createdAt)}
                                        </td>
                                        <td className="sr-td sr-td--cost">
                                            {formatCurrency(receipt.totalCost)}
                                        </td>
                                        <td className="sr-td">
                                            <StatusBadge status={receipt.status} />
                                        </td>
                                        <td className="sr-td sr-td--user">
                                            {receipt.createdByUsername || '—'}
                                        </td>
                                        <td className="sr-td sr-td--actions">
                                            {/* View Details — route wired once UC37 Details is implemented */}
                                            <button
                                                className="sr-action-btn sr-action-btn--view"
                                                title="View Details — coming soon"
                                                disabled
                                                aria-label={`View details for receipt ${receipt.receiptNumber}`}
                                            >
                                                <svg width="15" height="15" viewBox="0 0 20 20" fill="none"
                                                     stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                                    <path d="M1 10s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7z"/>
                                                    <circle cx="10" cy="10" r="3"/>
                                                </svg>
                                                View
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Pagination controls */}
                {!loading && !error && (
                    <Pagination
                        idPrefix="sr"
                        page={page}
                        size={size}
                        totalElements={totalElements}
                        totalPages={totalPages}
                        pageSizeOptions={[5, 10, 20, 50]}
                        onPageChange={setPage}
                        onSizeChange={(newSize) => {
                            setSize(newSize);
                            setPage(0);
                        }}
                        itemLabel="receipts"
                    />
                )}
            </div>
        </div>
    );
}
