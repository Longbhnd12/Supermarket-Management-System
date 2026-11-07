import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
    ArrowLeft,
    RotateCw,
    Search,
    AlertTriangle,
    AlertCircle,
    PackageX,
    Clock,
    X,
    Inbox,
    Eye,
    Package,
    History,
    ShieldAlert,
    ExternalLink
} from 'lucide-react';
import InventoryService from '../../services/inventoryService';
import CategoryService from '../../services/categoryService';
import Pagination from '../../components/ui/Pagination';
import './StockAlertsPage.css';

function formatDate(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        return `${day}/${month}/${year}`;
    } catch {
        return iso;
    }
}

function formatQty(val) {
    if (val == null) return '0';
    const num = Number(val);
    return isNaN(num) ? String(val) : num.toLocaleString('en-US');
}

export default function StockAlertsPage() {
    const navigate = useNavigate();

    // ─── Filter state ───
    const [keyword, setKeyword] = useState('');
    const [alertType, setAlertType] = useState('ALL');
    const [categoryId, setCategoryId] = useState('');
    const [categories, setCategories] = useState([]);

    // ─── Summary counts ───
    const [summary, setSummary] = useState({
        lowStockCount: 0,
        outOfStockCount: 0,
        nearExpiryCount: 0,
        expiredCount: 0,
        totalAlerts: 0
    });

    // ─── Pagination state ───
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    // ─── Data & Loading state ───
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');

    // ─── Selected Alert for View Details Modal ───
    const [detailAlert, setDetailAlert] = useState(null);

    // ─── Load categories once on mount ───
    useEffect(() => {
        CategoryService.getAll()
            .then(res => setCategories(res.data?.data || []))
            .catch(() => setCategories([]));
    }, []);

    // ─── Fetch Stock Alerts ───
    const fetchAlerts = useCallback(async (isManualRefresh = false) => {
        if (isManualRefresh) setRefreshing(true);
        else setLoading(true);
        setError('');

        const params = {
            page,
            size,
            ...(keyword.trim() && { keyword: keyword.trim() }),
            ...(categoryId && { categoryId }),
            ...(alertType !== 'ALL' && { alertType })
        };

        try {
            const res = await InventoryService.getStockAlerts(params);
            const data = res.data?.data;
            if (data) {
                setAlerts(data.alerts || []);
                setTotalElements(data.totalElements || 0);
                setTotalPages(data.totalPages || 0);
                setSummary({
                    lowStockCount: data.lowStockCount || 0,
                    outOfStockCount: data.outOfStockCount || 0,
                    nearExpiryCount: data.nearExpiryCount || 0,
                    expiredCount: data.expiredCount || 0,
                    totalAlerts: data.totalAlerts || 0
                });
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to retrieve inventory stock alerts.');
            setAlerts([]);
            setTotalElements(0);
            setTotalPages(0);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [page, size, keyword, categoryId, alertType]);

    useEffect(() => {
        fetchAlerts();
    }, [fetchAlerts]);

    // ─── Filter change handlers ───
    const handleKeywordChange = (val) => {
        setKeyword(val);
        setPage(0);
    };

    const handleCategoryChange = (val) => {
        setCategoryId(val);
        setPage(0);
    };

    const handleAlertTypeChange = (val) => {
        setAlertType(val);
        setPage(0);
    };

    const handleCardClick = (type) => {
        if (alertType === type) {
            setAlertType('ALL');
        } else {
            setAlertType(type);
        }
        setPage(0);
    };

    const handleResetFilters = () => {
        setKeyword('');
        setCategoryId('');
        setAlertType('ALL');
        setPage(0);
    };

    const hasActiveFilters = Boolean(keyword || categoryId || alertType !== 'ALL');

    // ─── Alert Type Badge Renderer ───
    const renderAlertBadge = (type) => {
        switch (type) {
            case 'EXPIRED':
                return (
                    <span className="sms-alert-badge sms-alert-badge--expired" title="Product batch is past its expiration date">
                        <AlertCircle size={13} />
                        <span>Expired</span>
                    </span>
                );
            case 'OUT_OF_STOCK':
                return (
                    <span className="sms-alert-badge sms-alert-badge--out-of-stock" title="Inventory stock is currently 0">
                        <PackageX size={13} />
                        <span>Out of Stock</span>
                    </span>
                );
            case 'NEAR_EXPIRY':
                return (
                    <span className="sms-alert-badge sms-alert-badge--near-expiry" title="Batch is nearing its expiration date">
                        <Clock size={13} />
                        <span>Near Expiry</span>
                    </span>
                );
            case 'LOW_STOCK':
                return (
                    <span className="sms-alert-badge sms-alert-badge--low-stock" title="Current stock is below safety minimum threshold">
                        <AlertTriangle size={13} />
                        <span>Low Stock</span>
                    </span>
                );
            default:
                return (
                    <span className="sms-alert-badge" style={{ backgroundColor: '#f1f5f9', color: '#475569' }}>
                        {type}
                    </span>
                );
        }
    };

    return (
        <div className="sms-alerts-page">
            {/* ─── Header ─── */}
            <div className="sms-alerts-header">
                <div className="sms-alerts-header-left">
                    <Link to="/inventory" className="sms-btn-back">
                        <ArrowLeft size={16} />
                        <span>Back to Inventory</span>
                    </Link>
                    <div>
                        <h1 className="sms-alerts-title">Stock Alerts</h1>
                        <p className="sms-alerts-subtitle">
                            Monitor critical inventory conditions including low stock, out of stock, near expiry, and expired batches.
                        </p>
                    </div>
                </div>

                <div className="sms-alerts-header-actions">
                    <button
                        type="button"
                        className="sms-btn-refresh"
                        onClick={() => fetchAlerts(true)}
                        disabled={loading || refreshing}
                        title="Reload latest stock alerts from inventory"
                    >
                        <RotateCw size={15} className={refreshing ? 'animate-spin' : ''} />
                        <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
                    </button>
                </div>
            </div>

            {/* ─── Error Banner ─── */}
            {error && (
                <div className="sms-alert-banner sms-alert-banner--danger">
                    <div className="sms-alert-banner-content">
                        <ShieldAlert size={18} />
                        <span>{error}</span>
                    </div>
                    <button
                        type="button"
                        className="sms-alert-banner-close"
                        onClick={() => setError('')}
                        aria-label="Dismiss error"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            {/* ─── Summary Cards (SRS #1, UC35) ─── */}
            <div className="sms-summary-grid">
                {/* Low Stock Card */}
                <div
                    className={`sms-summary-card sms-summary-card--low ${alertType === 'LOW_STOCK' ? 'sms-summary-card--active' : ''}`}
                    onClick={() => handleCardClick('LOW_STOCK')}
                    role="button"
                    tabIndex={0}
                    title="Click to filter by Low Stock alerts"
                >
                    <div className="sms-summary-card-top">
                        <div className="sms-summary-icon-wrap sms-summary-icon-wrap--low">
                            <AlertTriangle size={20} />
                        </div>
                        <div className="sms-summary-count-group">
                            <span className="sms-summary-count">{summary.lowStockCount}</span>
                            <span className="sms-summary-unit">{summary.lowStockCount === 1 ? 'Product' : 'Products'}</span>
                        </div>
                    </div>
                    <div className="sms-summary-info">
                        <span className="sms-summary-label">Low Stock — {summary.lowStockCount} {summary.lowStockCount === 1 ? 'Product' : 'Products'}</span>
                        <span className="sms-summary-sub">Stock ≤ Safety Minimum</span>
                    </div>
                </div>

                {/* Out of Stock Card */}
                <div
                    className={`sms-summary-card sms-summary-card--out ${alertType === 'OUT_OF_STOCK' ? 'sms-summary-card--active' : ''}`}
                    onClick={() => handleCardClick('OUT_OF_STOCK')}
                    role="button"
                    tabIndex={0}
                    title="Click to filter by Out of Stock alerts"
                >
                    <div className="sms-summary-card-top">
                        <div className="sms-summary-icon-wrap sms-summary-icon-wrap--out">
                            <PackageX size={20} />
                        </div>
                        <div className="sms-summary-count-group">
                            <span className="sms-summary-count">{summary.outOfStockCount}</span>
                            <span className="sms-summary-unit">{summary.outOfStockCount === 1 ? 'Product' : 'Products'}</span>
                        </div>
                    </div>
                    <div className="sms-summary-info">
                        <span className="sms-summary-label">Out of Stock — {summary.outOfStockCount} {summary.outOfStockCount === 1 ? 'Product' : 'Products'}</span>
                        <span className="sms-summary-sub">Quantity on hand = 0</span>
                    </div>
                </div>

                {/* Near Expiry Card */}
                <div
                    className={`sms-summary-card sms-summary-card--near ${alertType === 'NEAR_EXPIRY' ? 'sms-summary-card--active' : ''}`}
                    onClick={() => handleCardClick('NEAR_EXPIRY')}
                    role="button"
                    tabIndex={0}
                    title="Click to filter by Near Expiry alerts"
                >
                    <div className="sms-summary-card-top">
                        <div className="sms-summary-icon-wrap sms-summary-icon-wrap--near">
                            <Clock size={20} />
                        </div>
                        <div className="sms-summary-count-group">
                            <span className="sms-summary-count">{summary.nearExpiryCount}</span>
                            <span className="sms-summary-unit">{summary.nearExpiryCount === 1 ? 'Batch' : 'Batches'}</span>
                        </div>
                    </div>
                    <div className="sms-summary-info">
                        <span className="sms-summary-label">Near Expiry — {summary.nearExpiryCount} {summary.nearExpiryCount === 1 ? 'Batch' : 'Batches'}</span>
                        <span className="sms-summary-sub">Expiring within warning period</span>
                    </div>
                </div>

                {/* Expired Card */}
                <div
                    className={`sms-summary-card sms-summary-card--expired ${alertType === 'EXPIRED' ? 'sms-summary-card--active' : ''}`}
                    onClick={() => handleCardClick('EXPIRED')}
                    role="button"
                    tabIndex={0}
                    title="Click to filter by Expired product alerts"
                >
                    <div className="sms-summary-card-top">
                        <div className="sms-summary-icon-wrap sms-summary-icon-wrap--expired">
                            <AlertCircle size={20} />
                        </div>
                        <div className="sms-summary-count-group">
                            <span className="sms-summary-count">{summary.expiredCount}</span>
                            <span className="sms-summary-unit">{summary.expiredCount === 1 ? 'Batch' : 'Batches'}</span>
                        </div>
                    </div>
                    <div className="sms-summary-info">
                        <span className="sms-summary-label">Expired — {summary.expiredCount} {summary.expiredCount === 1 ? 'Batch' : 'Batches'}</span>
                        <span className="sms-summary-sub">Past batch expiration date</span>
                    </div>
                </div>
            </div>

            {/* ─── Search & Filters Bar (SRS #2, #3, #4) ─── */}
            <div className="sms-alerts-filter-bar">
                <div className="sms-filter-group">
                    {/* Search by Name, SKU, Barcode */}
                    <div className="sms-filter-search">
                        <Search size={16} className="sms-filter-search-icon" />
                        <input
                            type="text"
                            className="sms-filter-search-input"
                            placeholder="Search by Product Name, SKU, or Barcode..."
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

                    {/* Alert Type Filter */}
                    <select
                        className="sms-filter-select"
                        value={alertType}
                        onChange={(e) => handleAlertTypeChange(e.target.value)}
                        title="Filter by alert type"
                    >
                        <option value="ALL">All Alert Types</option>
                        <option value="LOW_STOCK">Low Stock</option>
                        <option value="OUT_OF_STOCK">Out of Stock</option>
                        <option value="NEAR_EXPIRY">Near Expiry</option>
                        <option value="EXPIRED">Expired</option>
                    </select>

                    {/* Category Filter */}
                    <select
                        className="sms-filter-select"
                        value={categoryId}
                        onChange={(e) => handleCategoryChange(e.target.value)}
                        title="Filter by category"
                    >
                        <option value="">All Categories</option>
                        {categories.map((cat) => (
                            <option key={cat.categoryId} value={cat.categoryId}>
                                {cat.categoryName}
                            </option>
                        ))}
                    </select>

                    {hasActiveFilters && (
                        <button
                            type="button"
                            className="sms-btn-ghost"
                            onClick={handleResetFilters}
                            title="Reset all search and filter options"
                        >
                            Reset filters
                        </button>
                    )}
                </div>

                <div style={{ fontSize: '0.825rem', color: '#64748b', fontWeight: 500 }}>
                    Total: <strong style={{ color: '#0f172a' }}>{totalElements}</strong> alert records
                </div>
            </div>

            {/* ─── Stock Alerts Table (SRS #5, #6, #7, #8, #9, #10) ─── */}
            <div className="sms-alerts-table-card">
                {loading ? (
                    <div style={{ padding: '3.5rem', textAlign: 'center', color: '#64748b' }}>
                        <RotateCw size={24} className="animate-spin" style={{ margin: '0 auto 0.75rem' }} />
                        <div>Evaluating stock conditions and loading alerts...</div>
                    </div>
                ) : alerts.length === 0 ? (
                    <div className="sms-empty-card">
                        <Inbox className="sms-empty-icon" />
                        <h3 className="sms-empty-title">
                            {hasActiveFilters ? 'No alerts match selected filters' : 'No active stock alerts'}
                        </h3>
                        <p className="sms-empty-desc">
                            {hasActiveFilters
                                ? 'No inventory records match the selected alert type, category, or search criteria.'
                                : 'All product stock levels and remaining batch expiration dates are within safe operating limits.'}
                        </p>
                        {hasActiveFilters && (
                            <button
                                type="button"
                                className="sms-btn-ghost"
                                onClick={handleResetFilters}
                            >
                                Clear All Filters
                            </button>
                        )}
                    </div>
                ) : (
                    <div className="sms-table-wrapper">
                        <table className="sms-alerts-table">
                            <thead>
                                <tr>
                                    <th>SKU</th>
                                    <th>Product Name</th>
                                    <th style={{ textAlign: 'center' }}>Alert Type</th>
                                    <th>Batch Number</th>
                                    <th style={{ textAlign: 'right' }}>Batch Remaining Qty</th>
                                    <th style={{ textAlign: 'right' }}>Total Stock</th>
                                    <th style={{ textAlign: 'right' }}>Safety Min</th>
                                    <th>Expiry Date</th>
                                    <th style={{ textAlign: 'center' }}>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {alerts.map((item) => {
                                    const stockNum = Number(item.currentStock) || 0;
                                    const isOutOfStock = stockNum <= 0;
                                    const isLowStock = item.alertType === 'LOW_STOCK';
                                    const isBatchAlert = item.alertType === 'NEAR_EXPIRY' || item.alertType === 'EXPIRED';

                                    return (
                                        <tr key={item.alertId}>
                                            <td style={{ fontFamily: 'monospace', fontWeight: 600, color: '#1e293b' }}>
                                                {item.productCode}
                                            </td>

                                            <td>
                                                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
                                                    <span style={{ fontWeight: 600, color: '#0f172a' }}>
                                                        {item.productName}
                                                    </span>
                                                    <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                                        {item.categoryName} {item.barcode ? `• ${item.barcode}` : ''}
                                                    </span>
                                                </div>
                                            </td>

                                            <td style={{ textAlign: 'center' }}>
                                                {renderAlertBadge(item.alertType)}
                                            </td>

                                            {/* Batch Number */}
                                            <td>
                                                {isBatchAlert && item.batchNumber ? (
                                                    <span style={{ fontFamily: 'monospace', fontWeight: 600, color: '#334155' }}>
                                                        {item.batchNumber}
                                                    </span>
                                                ) : (
                                                    <span style={{ color: '#94a3b8' }}>—</span>
                                                )}
                                            </td>

                                            {/* Batch Remaining Qty */}
                                            <td style={{ textAlign: 'right' }}>
                                                {isBatchAlert && item.batchRemainingQuantity != null ? (
                                                    <span style={{ fontWeight: 600, color: '#0f172a' }}>
                                                        {formatQty(item.batchRemainingQuantity)} {item.unit || ''}
                                                    </span>
                                                ) : (
                                                    <span style={{ color: '#94a3b8' }}>—</span>
                                                )}
                                            </td>

                                            {/* Total Stock */}
                                            <td style={{ textAlign: 'right' }}>
                                                <span
                                                    className={`sms-stock-qty ${
                                                        isOutOfStock
                                                            ? 'sms-stock-qty--out'
                                                            : isLowStock
                                                            ? 'sms-stock-qty--low'
                                                            : ''
                                                    }`}
                                                >
                                                    {formatQty(item.currentStock)} {item.unit || ''}
                                                </span>
                                            </td>

                                            {/* Safety Min */}
                                            <td style={{ textAlign: 'right', color: '#64748b', fontWeight: 500 }}>
                                                {formatQty(item.safetyMin)} {item.unit || ''}
                                            </td>

                                            {/* Expiry Date */}
                                            <td>
                                                {item.expiryDate ? (
                                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.1rem' }}>
                                                        <span style={{ fontWeight: 600, color: item.alertType === 'EXPIRED' ? '#dc2626' : '#d97706' }}>
                                                            {formatDate(item.expiryDate)}
                                                        </span>
                                                        <span style={{ fontSize: '0.725rem', color: '#64748b' }}>
                                                            {item.daysToExpiry != null && (
                                                                item.daysToExpiry < 0
                                                                    ? `Expired ${Math.abs(item.daysToExpiry)} days ago`
                                                                    : item.daysToExpiry === 0
                                                                    ? 'Expires today'
                                                                    : `Expires in ${item.daysToExpiry} days`
                                                            )}
                                                        </span>
                                                    </div>
                                                ) : (
                                                    <span style={{ color: '#94a3b8' }}>—</span>
                                                )}
                                            </td>

                                            <td style={{ textAlign: 'center' }}>
                                                <button
                                                    type="button"
                                                    className="sms-btn-view-details"
                                                    onClick={() => setDetailAlert(item)}
                                                    title="View detailed product & alert inspection"
                                                >
                                                    <Eye size={14} />
                                                    <span>View Details</span>
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* ─── Pagination Controls (SRS #11) ─── */}
                {!loading && alerts.length > 0 && (
                    <Pagination
                        idPrefix="stock-alerts"
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
                        itemLabel="alerts"
                    />
                )}
            </div>

            {/* ─── View Details Inspection Modal (SRS #10, BR-07) ─── */}
            {detailAlert && (
                <div className="sms-modal-backdrop" onClick={() => setDetailAlert(null)}>
                    <div className="sms-modal-card" onClick={(e) => e.stopPropagation()}>
                        <div className="sms-modal-header">
                            <div className="sms-modal-header-left">
                                <Package size={20} className="sms-modal-icon" />
                                <div>
                                    <h2 className="sms-modal-title">Stock Alert Details</h2>
                                    <span className="sms-modal-subtitle">Read-only inventory inspection</span>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="sms-modal-close-btn"
                                onClick={() => setDetailAlert(null)}
                                aria-label="Close modal"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="sms-modal-body">
                            {/* Alert Type Header Banner */}
                            <div className="sms-modal-alert-box">
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                                    {renderAlertBadge(detailAlert.alertType)}
                                    <span style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.9rem' }}>
                                        {detailAlert.alertType === 'EXPIRED' && 'This product batch has expired and should be removed from shelves.'}
                                        {detailAlert.alertType === 'OUT_OF_STOCK' && 'This product has zero stock remaining in inventory.'}
                                        {detailAlert.alertType === 'NEAR_EXPIRY' && 'This batch is approaching its expiration date and needs attention.'}
                                        {detailAlert.alertType === 'LOW_STOCK' && 'Product stock has dropped below its configured safety minimum threshold.'}
                                    </span>
                                </div>
                            </div>

                            {/* Product Info Grid */}
                            <div className="sms-modal-section">
                                <h4 className="sms-modal-section-title">Product Information</h4>
                                <div className="sms-modal-grid">
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Product Name</span>
                                        <span className="sms-modal-prop-val">{detailAlert.productName}</span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">SKU / Code</span>
                                        <span className="sms-modal-prop-val" style={{ fontFamily: 'monospace' }}>
                                            {detailAlert.productCode}
                                        </span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Barcode</span>
                                        <span className="sms-modal-prop-val" style={{ fontFamily: 'monospace' }}>
                                            {detailAlert.barcode || '—'}
                                        </span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Category</span>
                                        <span className="sms-modal-prop-val">{detailAlert.categoryName}</span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Unit of Measure</span>
                                        <span className="sms-modal-prop-val">{detailAlert.unit || 'unit'}</span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Total Stock</span>
                                        <span className="sms-modal-prop-val" style={{ color: Number(detailAlert.currentStock) <= 0 ? '#dc2626' : '#d97706', fontWeight: 700 }}>
                                            {formatQty(detailAlert.currentStock)} {detailAlert.unit || ''}
                                        </span>
                                    </div>
                                    <div className="sms-modal-prop">
                                        <span className="sms-modal-prop-label">Safety Minimum</span>
                                        <span className="sms-modal-prop-val">
                                            {formatQty(detailAlert.safetyMin)} {detailAlert.unit || ''}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Batch & Expiry Details (if available) */}
                            {detailAlert.expiryDate && (
                                <div className="sms-modal-section">
                                    <h4 className="sms-modal-section-title">Batch & Expiration Details</h4>
                                    <div className="sms-modal-grid">
                                        <div className="sms-modal-prop">
                                            <span className="sms-modal-prop-label">Batch Number</span>
                                            <span className="sms-modal-prop-val" style={{ fontFamily: 'monospace' }}>
                                                {detailAlert.batchNumber || '—'}
                                            </span>
                                        </div>
                                        <div className="sms-modal-prop">
                                            <span className="sms-modal-prop-label">Batch Remaining Qty</span>
                                            <span className="sms-modal-prop-val" style={{ fontWeight: 600 }}>
                                                {formatQty(detailAlert.batchRemainingQuantity)} {detailAlert.unit || ''}
                                            </span>
                                        </div>
                                        <div className="sms-modal-prop">
                                            <span className="sms-modal-prop-label">Expiration Date</span>
                                            <span className="sms-modal-prop-val" style={{ fontWeight: 600, color: detailAlert.alertType === 'EXPIRED' ? '#dc2626' : '#d97706' }}>
                                                {formatDate(detailAlert.expiryDate)}
                                            </span>
                                        </div>
                                        <div className="sms-modal-prop">
                                            <span className="sms-modal-prop-label">Remaining Time</span>
                                            <span className="sms-modal-prop-val">
                                                {detailAlert.daysToExpiry != null
                                                    ? detailAlert.daysToExpiry < 0
                                                        ? `Expired ${Math.abs(detailAlert.daysToExpiry)} days ago`
                                                        : detailAlert.daysToExpiry === 0
                                                        ? 'Expires today'
                                                        : `Expires in ${detailAlert.daysToExpiry} days`
                                                    : '—'}
                                            </span>
                                        </div>
                                        {detailAlert.receiptNumber && (
                                            <div className="sms-modal-prop">
                                                <span className="sms-modal-prop-label">Stock Receipt Ref</span>
                                                <span className="sms-modal-prop-val" style={{ fontFamily: 'monospace' }}>
                                                    {detailAlert.receiptNumber}
                                                </span>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className="sms-modal-footer">
                            <div className="sms-modal-footer-left">
                                <button
                                    type="button"
                                    className="sms-btn-modal-action"
                                    onClick={() => navigate(`/inventory/stock-history?productId=${detailAlert.productId}`)}
                                    title="View chronological movement history for this product"
                                >
                                    <History size={14} />
                                    <span>Stock History</span>
                                </button>
                                <button
                                    type="button"
                                    className="sms-btn-modal-action"
                                    onClick={() => navigate(`/inventory?keyword=${encodeURIComponent(detailAlert.productCode)}`)}
                                    title="Inspect in main inventory table"
                                >
                                    <ExternalLink size={14} />
                                    <span>View in Inventory</span>
                                </button>
                            </div>
                            <button
                                type="button"
                                className="sms-btn-modal-close"
                                onClick={() => setDetailAlert(null)}
                            >
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

