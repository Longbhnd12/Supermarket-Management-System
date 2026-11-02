import { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
    ArrowLeft,
    RotateCw,
    Search,
    ArrowDownLeft,
    ArrowUpRight,
    SlidersHorizontal,
    User,
    Package,
    X,
    AlertCircle,
    Inbox
} from 'lucide-react';
import InventoryService from '../../services/inventoryService';
import ProductService from '../../services/productService';
import Pagination from '../../components/ui/Pagination';
import './StockHistoryPage.css';

function formatDateTime(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');
        return `${day}/${month}/${year} ${hours}:${minutes}:${seconds}`;
    } catch {
        return iso;
    }
}

function formatQty(val) {
    if (val == null) return '0';
    const num = Number(val);
    return isNaN(num) ? String(val) : num.toLocaleString('en-US');
}

export default function StockHistoryPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const urlProductId = searchParams.get('productId');

    // ─── Selected Product state (from URL / UC31 Navigation) ───
    const [selectedProduct, setSelectedProduct] = useState(null);

    // ─── Filter state (Single Main Search & Filters) ───
    const [keyword, setKeyword] = useState('');
    const [movementType, setMovementType] = useState('ALL');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [dateError, setDateError] = useState('');

    // ─── Pagination state ───
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    // ─── Data & Loading state ───
    const [movements, setMovements] = useState([]);
    const [loading, setLoading] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');

    // ─── Load selected product info when productId param is present ───
    useEffect(() => {
        if (!urlProductId) {
            setSelectedProduct(null);
            return;
        }

        ProductService.getById(urlProductId)
            .then(res => {
                const prod = res.data?.data;
                if (prod) {
                    setSelectedProduct(prod);
                } else {
                    setSelectedProduct(null);
                }
            })
            .catch(() => {
                setSelectedProduct(null);
            });
    }, [urlProductId]);

    // ─── Fetch Stock Movement History ───
    const fetchHistory = useCallback(async (isManualRefresh = false) => {
        // Validate date range (AT3)
        if (startDate && endDate && startDate > endDate) {
            setDateError('Start date cannot be after end date.');
            return;
        }
        setDateError('');

        if (isManualRefresh) setRefreshing(true);
        else setLoading(true);
        setError('');

        const activeProductId = urlProductId ? Number(urlProductId) : undefined;

        const params = {
            page,
            size,
            ...(activeProductId && { productId: activeProductId }),
            ...(keyword.trim() && { keyword: keyword.trim() }),
            ...(movementType !== 'ALL' && { movementType }),
            ...(startDate && { startDate }),
            ...(endDate && { endDate }),
        };

        try {
            const res = await InventoryService.getStockHistory(params);
            const data = res.data?.data;
            if (data) {
                setMovements(data.content || []);
                setTotalElements(data.totalElements || 0);
                setTotalPages(data.totalPages || 0);
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to retrieve stock movement history.');
            setMovements([]);
            setTotalElements(0);
            setTotalPages(0);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [page, size, urlProductId, keyword, movementType, startDate, endDate]);

    // Fetch on filter or pagination changes
    useEffect(() => {
        fetchHistory();
    }, [fetchHistory]);

    // ─── Manual Refresh Handler ───
    const handleRefresh = () => {
        if (urlProductId) {
            ProductService.getById(urlProductId)
                .then(res => {
                    if (res.data?.data) setSelectedProduct(res.data.data);
                })
                .catch(() => {});
        }
        fetchHistory(true);
    };

    // ─── Clear Selected Product (View All Products) ───
    const handleClearSelectedProduct = () => {
        setSelectedProduct(null);
        setPage(0);
        setSearchParams({});
    };

    // ─── Filter change handlers ───
    const handleKeywordChange = (val) => {
        setKeyword(val);
        setPage(0);
    };

    const handleMovementTypeChange = (val) => {
        setMovementType(val);
        setPage(0);
    };

    const handleStartDateChange = (val) => {
        setStartDate(val);
        setPage(0);
    };

    const handleEndDateChange = (val) => {
        setEndDate(val);
        setPage(0);
    };

    const handleResetFilters = () => {
        setKeyword('');
        setMovementType('ALL');
        setStartDate('');
        setEndDate('');
        setDateError('');
        setPage(0);
    };

    const hasActiveFilters = Boolean(keyword || movementType !== 'ALL' || startDate || endDate);

    // ─── Helper for Movement Type Badges ───
    const renderMovementTypeBadge = (type) => {
        switch (type) {
            case 'RECEIPT':
                return (
                    <span className="sms-movement-badge sms-movement-badge--receipt" title="Stock In from Stock Receipt">
                        <ArrowDownLeft size={13} />
                        <span>Stock In</span>
                    </span>
                );
            case 'SALE':
                return (
                    <span className="sms-movement-badge sms-movement-badge--sale" title="Stock Out from Customer Sale">
                        <ArrowUpRight size={13} />
                        <span>Stock Out</span>
                    </span>
                );
            case 'ADJUSTMENT':
                return (
                    <span className="sms-movement-badge sms-movement-badge--adjustment" title="Stock Adjustment">
                        <SlidersHorizontal size={13} />
                        <span>Adjustment</span>
                    </span>
                );
            default:
                return (
                    <span className="sms-movement-badge" style={{ backgroundColor: '#f1f5f9', color: '#475569' }}>
                        {type || '—'}
                    </span>
                );
        }
    };

    return (
        <div className="sms-history-page">
            {/* ─── Header ─── */}
            <div className="sms-history-header">
                <div className="sms-history-header-left">
                    <Link to="/inventory" className="sms-btn-back">
                        <ArrowLeft size={16} />
                        <span>Back to Inventory</span>
                    </Link>
                    <div>
                        <h1 className="sms-history-title">Stock History</h1>
                        <p className="sms-history-subtitle">
                            Review chronological inventory movement transactions, imports, sales, and stock adjustments.
                        </p>
                    </div>
                </div>

                <div className="sms-history-header-actions">
                    <button
                        type="button"
                        className="sms-btn-refresh"
                        onClick={handleRefresh}
                        disabled={loading || refreshing}
                        title="Refresh stock movement history"
                    >
                        <RotateCw size={15} className={refreshing ? 'animate-spin' : ''} />
                        <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
                    </button>
                </div>
            </div>

            {/* ─── Date Range Validation Error Banner (AT3) ─── */}
            {dateError && (
                <div className="sms-alert sms-alert--danger">
                    <div className="sms-alert-content">
                        <AlertCircle size={18} />
                        <span>{dateError}</span>
                    </div>
                    <button
                        type="button"
                        className="sms-alert-close"
                        onClick={() => setDateError('')}
                        aria-label="Dismiss error"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            {/* ─── API Error Banner ─── */}
            {error && (
                <div className="sms-alert sms-alert--danger">
                    <div className="sms-alert-content">
                        <AlertCircle size={18} />
                        <span>{error}</span>
                    </div>
                    <button
                        type="button"
                        className="sms-alert-close"
                        onClick={() => setError('')}
                        aria-label="Dismiss error"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            {/* ─── Read-only Selected Product Information Panel (Only when navigating from UC31 or productId param) ─── */}
            {selectedProduct && (
                <div className="sms-selected-product-card">
                    <div className="sms-selected-product-header">
                        <div className="sms-selected-product-title">
                            <Package size={18} className="sms-selected-product-icon" />
                            <span>Selected Product Information</span>
                            <span className="sms-selected-product-badge">Filtered</span>
                        </div>
                        <button
                            type="button"
                            className="sms-btn-clear-product"
                            onClick={handleClearSelectedProduct}
                            title="Clear product filter and view all records"
                        >
                            <X size={14} />
                            <span>View All Products</span>
                        </button>
                    </div>

                    <div className="sms-selected-product-grid">
                        <div className="sms-product-prop">
                            <span className="sms-prop-label">Product Name</span>
                            <span className="sms-prop-value">{selectedProduct.productName}</span>
                        </div>
                        <div className="sms-product-prop">
                            <span className="sms-prop-label">SKU / Code</span>
                            <span className="sms-prop-value sms-prop-value--code">
                                {selectedProduct.productCode}
                            </span>
                        </div>
                        <div className="sms-product-prop">
                            <span className="sms-prop-label">Barcode</span>
                            <span className="sms-prop-value sms-prop-value--code">
                                {selectedProduct.barcode || '—'}
                            </span>
                        </div>
                        <div className="sms-product-prop">
                            <span className="sms-prop-label">Unit of Measure</span>
                            <span className="sms-prop-value">{selectedProduct.unit || 'unit'}</span>
                        </div>
                        <div className="sms-product-prop">
                            <span className="sms-prop-label">Current Stock on Hand</span>
                            <span className="sms-prop-value sms-prop-value--stock">
                                {formatQty(selectedProduct.stockQuantity)} {selectedProduct.unit || ''}
                            </span>
                        </div>
                    </div>
                </div>
            )}

            {/* ─── Search & Filters Bar (Single Main Search Input) ─── */}
            <div className="sms-history-filter-bar">
                <div className="sms-filter-group">
                    {/* The ONLY Main Search input */}
                    <div className="sms-filter-search">
                        <Search size={16} className="sms-filter-search-icon" />
                        <input
                            type="text"
                            className="sms-filter-search-input"
                            placeholder="Search by SKU, Product Name, Barcode, or Reason..."
                            value={keyword}
                            onChange={(e) => handleKeywordChange(e.target.value)}
                        />
                        {keyword && (
                            <button
                                type="button"
                                className="sms-filter-search-clear"
                                onClick={() => handleKeywordChange('')}
                                title="Clear search"
                            >
                                <X size={14} />
                            </button>
                        )}
                    </div>

                    {/* Movement Type Filter Dropdown */}
                    <select
                        className="sms-filter-select"
                        value={movementType}
                        onChange={(e) => handleMovementTypeChange(e.target.value)}
                        title="Filter by movement type"
                    >
                        <option value="ALL">All Movement Types</option>
                        <option value="RECEIPT">Stock In (Receipts)</option>
                        <option value="SALE">Stock Out (Sales)</option>
                        <option value="ADJUSTMENT">Adjustments</option>
                    </select>

                    {/* Date Range Inputs */}
                    <div className="sms-date-range-box">
                        <span className="sms-date-label">From:</span>
                        <input
                            type="date"
                            className={`sms-date-input ${dateError ? 'sms-date-input--error' : ''}`}
                            value={startDate}
                            onChange={(e) => handleStartDateChange(e.target.value)}
                            title="Start Date"
                        />
                        <span className="sms-date-label">To:</span>
                        <input
                            type="date"
                            className={`sms-date-input ${dateError ? 'sms-date-input--error' : ''}`}
                            value={endDate}
                            onChange={(e) => handleEndDateChange(e.target.value)}
                            title="End Date"
                        />
                    </div>

                    {hasActiveFilters && (
                        <button
                            type="button"
                            className="sms-btn-ghost"
                            onClick={handleResetFilters}
                            title="Reset all filters"
                        >
                            Reset filters
                        </button>
                    )}
                </div>

                <div style={{ fontSize: '0.825rem', color: '#64748b', fontWeight: 500 }}>
                    Total: <strong style={{ color: '#0f172a' }}>{totalElements}</strong> movement records
                </div>
            </div>

            {/* ─── Stock Movement History Table ─── */}
            <div className="sms-history-table-card">
                {loading ? (
                    <div style={{ padding: '3.5rem', textAlign: 'center', color: '#64748b' }}>
                        <RotateCw size={24} className="animate-spin" style={{ margin: '0 auto 0.75rem' }} />
                        <div>Loading stock movement history...</div>
                    </div>
                ) : movements.length === 0 ? (
                    <div className="sms-empty-card">
                        <Inbox className="sms-empty-icon" />
                        <h3 className="sms-empty-title">
                            {selectedProduct ? 'No stock movement history found' : 'No records match selected filters'}
                        </h3>
                        <p className="sms-empty-desc">
                            {selectedProduct
                                ? `No stock movements match the specified criteria for "${selectedProduct.productName}".`
                                : hasActiveFilters
                                ? 'No history records match the selected search, movement type, or date range criteria.'
                                : 'No inventory movement records exist in the database.'}
                        </p>
                        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap', justifyContent: 'center' }}>
                            {hasActiveFilters && (
                                <button
                                    type="button"
                                    className="sms-btn-ghost"
                                    onClick={handleResetFilters}
                                >
                                    Clear Filter Criteria
                                </button>
                            )}
                            {selectedProduct && (
                                <button
                                    type="button"
                                    className="sms-btn-ghost"
                                    onClick={handleClearSelectedProduct}
                                >
                                    View All Products
                                </button>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="sms-table-wrapper">
                        <table className="sms-history-table">
                            <thead>
                                <tr>
                                    <th>Date & Time</th>
                                    <th>Product</th>
                                    <th>Reference No.</th>
                                    <th style={{ textAlign: 'center' }}>Movement Type</th>
                                    <th style={{ textAlign: 'right' }}>Quantity Change</th>
                                    <th>Reason / Notes</th>
                                    <th>Performed By</th>
                                </tr>
                            </thead>
                            <tbody>
                                {movements.map(m => {
                                    const qty = Number(m.quantityChange) || 0;
                                    const isPositive = qty > 0;
                                    const isNegative = qty < 0;

                                    return (
                                        <tr key={m.movementId}>
                                            <td style={{ whiteSpace: 'nowrap', fontSize: '0.825rem', color: '#475569' }}>
                                                {formatDateTime(m.createdAt)}
                                            </td>

                                            <td>
                                                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
                                                    <span style={{ fontWeight: 600, color: '#0f172a' }}>
                                                        {m.productName}
                                                    </span>
                                                    <span style={{ fontSize: '0.75rem', color: '#64748b', fontFamily: 'monospace' }}>
                                                        SKU: {m.productCode}
                                                    </span>
                                                </div>
                                            </td>

                                            <td>
                                                <span className="sms-ref-code" title={`Reference: ${m.referenceType} (ID: ${m.referenceId})`}>
                                                    {m.referenceCode || `${m.referenceType} #${m.referenceId}`}
                                                </span>
                                            </td>

                                            <td style={{ textAlign: 'center' }}>
                                                {renderMovementTypeBadge(m.movementType)}
                                            </td>

                                            <td style={{ textAlign: 'right' }}>
                                                <span className={`sms-qty-change ${isPositive ? 'sms-qty-change--positive' : isNegative ? 'sms-qty-change--negative' : 'sms-qty-change--neutral'}`}>
                                                    {isPositive ? `+${formatQty(qty)}` : formatQty(qty)} {m.unit || ''}
                                                </span>
                                            </td>

                                            <td style={{ color: '#475569', fontSize: '0.85rem' }}>
                                                {m.reason || '—'}
                                            </td>

                                            <td>
                                                <span className="sms-performed-by">
                                                    <User size={13} />
                                                    {m.createdByUsername || 'System'}
                                                </span>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* ─── Pagination Controls ─── */}
                {!loading && movements.length > 0 && (
                    <Pagination
                        idPrefix="history"
                        page={page}
                        size={size}
                        totalElements={totalElements}
                        totalPages={totalPages}
                        pageSizeOptions={[10, 20, 50, 100]}
                        onPageChange={setPage}
                        onSizeChange={(newSize) => {
                            setSize(newSize);
                            setPage(0);
                        }}
                        itemLabel="movements"
                    />
                )}
            </div>
        </div>
    );
}
