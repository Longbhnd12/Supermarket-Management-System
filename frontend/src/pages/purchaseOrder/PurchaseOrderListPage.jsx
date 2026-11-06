import { useState, useEffect, useCallback, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
    Plus,
    CheckCircle2,
    AlertCircle,
    ShoppingBag,
    Eye,
    Building2,
    Clock,
    X,
    RotateCw,
    Search,
    Calendar,
    Filter
} from 'lucide-react';
import PurchaseOrderService from '../../services/purchaseOrderService';
import SupplierService from '../../services/supplierService';
import Pagination from '../../components/ui/Pagination';
import Can from '../../common/Can';
import './PurchaseOrderListPage.css';

// VND currency formatter matching SMS standard
function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
    }).format(amount);
}

// Date/Time formatter
function formatDateTime(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        return d.toLocaleString('en-GB', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return iso;
    }
}

// 8 Status Tabs specified in SRS Section 3.15.5.1 and SDD Section 4.15.6.1
const STATUS_TABS = [
    { key: 'ALL', label: 'All', countKey: 'all' },
    { key: 'PENDING_APPROVAL', label: 'Pending Approval', countKey: 'pendingApproval' },
    { key: 'REJECTED_BY_MANAGER', label: 'Rejected by Manager', countKey: 'rejectedByManager' },
    { key: 'SENT', label: 'Sent', countKey: 'sent' },
    { key: 'ACCEPTED', label: 'Accepted', countKey: 'accepted' },
    { key: 'REJECTED_BY_SUPPLIER', label: 'Rejected by Supplier', countKey: 'rejectedBySupplier' },
    { key: 'PARTIALLY_FULFILLED', label: 'Partially Fulfilled', countKey: 'partiallyFulfilled' },
    { key: 'FULFILLED', label: 'Fulfilled', countKey: 'fulfilled' },
];

function getStatusBadge(status) {
    switch (status) {
        case 'PENDING_APPROVAL':
            return { label: 'Pending Approval', className: 'po-badge po-badge-pending' };
        case 'REJECTED_BY_MANAGER':
            return { label: 'Rejected by Manager', className: 'po-badge po-badge-rejected-mgr' };
        case 'SENT':
            return { label: 'Sent to Supplier', className: 'po-badge po-badge-sent' };
        case 'ACCEPTED':
            return { label: 'Accepted by Supplier', className: 'po-badge po-badge-accepted' };
        case 'REJECTED_BY_SUPPLIER':
            return { label: 'Rejected by Supplier', className: 'po-badge po-badge-rejected-sup' };
        case 'PARTIALLY_FULFILLED':
            return { label: 'Partially Fulfilled', className: 'po-badge po-badge-partial' };
        case 'FULFILLED':
            return { label: 'Fulfilled', className: 'po-badge po-badge-fulfilled' };
        default:
            return { label: status || 'Unknown', className: 'po-badge po-badge-default' };
    }
}

export default function PurchaseOrderListPage() {
    const location = useLocation();

    // Success banner from navigation (e.g. UC46 create request)
    const [successBanner, setSuccessBanner] = useState(location.state?.successMessage || '');

    // Active status tab
    const [activeTab, setActiveTab] = useState('ALL');

    // Filters
    const [keyword, setKeyword] = useState('');
    const [debouncedKeyword, setDebouncedKeyword] = useState('');
    const [supplierId, setSupplierId] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    // Suppliers for dropdown
    const [suppliers, setSuppliers] = useState([]);

    // Data state
    const [orders, setOrders] = useState([]);
    const [statusCounts, setStatusCounts] = useState({
        all: 0,
        pendingApproval: 0,
        rejectedByManager: 0,
        sent: 0,
        accepted: 0,
        rejectedBySupplier: 0,
        partiallyFulfilled: 0,
        fulfilled: 0
    });

    // Pagination state
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(10);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Debounce keyword input
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedKeyword(keyword);
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [keyword]);

    // Load supplier list for filter dropdown
    useEffect(() => {
        let isMounted = true;
        SupplierService.getAll()
            .then(res => {
                if (!isMounted) return;
                const list = res.data?.data || [];
                setSuppliers(list);
            })
            .catch(() => {
                // Non-critical if suppliers fail to load
            });
        return () => { isMounted = false; };
    }, []);

    // Main fetch function
    const fetchOrders = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const params = {
                page,
                size,
                keyword: debouncedKeyword.trim() || undefined,
                supplierId: supplierId ? Number(supplierId) : undefined,
                status: activeTab !== 'ALL' ? activeTab : undefined,
                startDate: startDate || undefined,
                endDate: endDate || undefined
            };

            const res = await PurchaseOrderService.getAll(params);
            const pageData = res.data?.data;
            if (pageData) {
                setOrders(pageData.content || []);
                setTotalElements(pageData.totalElements || 0);
                setTotalPages(pageData.totalPages || 0);
                if (pageData.statusCounts) {
                    setStatusCounts(pageData.statusCounts);
                }
            } else {
                setOrders([]);
                setTotalElements(0);
                setTotalPages(0);
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to load purchase orders and requests.');
        } finally {
            setLoading(false);
        }
    }, [activeTab, debouncedKeyword, supplierId, startDate, endDate, page, size]);

    useEffect(() => {
        fetchOrders();
    }, [fetchOrders]);

    // Handle tab change
    const handleTabChange = (tabKey) => {
        setActiveTab(tabKey);
        setPage(0);
    };

    // Clear filters
    const handleClearFilters = () => {
        setKeyword('');
        setDebouncedKeyword('');
        setSupplierId('');
        setStartDate('');
        setEndDate('');
        setPage(0);
    };

    const hasActiveFilters = Boolean(keyword.trim() || supplierId || startDate || endDate);

    return (
        <div className="po-list-page">
            {/* Header */}
            <div className="po-header">
                <div>
                    <h1 className="po-title">Purchase Orders & Requests</h1>
                    <p className="po-subtitle">
                        Track the status of submitted purchase requests and official supplier purchase orders (UC50)
                    </p>
                </div>
                <div className="po-header-actions">
                    <button
                        type="button"
                        className="po-btn po-btn-secondary"
                        onClick={fetchOrders}
                        title="Reload latest records and status counts"
                    >
                        <RotateCw size={16} />
                        <span>Refresh</span>
                    </button>
                    {/* Permission-based buttons (UC46 create: Admin/Inventory Staff, UC47 approve: Admin/Manager) */}
                    <Can permission="PURCHASE_REQUEST_APPROVE">
                    <Link
                        to="/purchase-orders/pending"
                        className="po-btn po-btn-outline"
                        title="View pending requests requiring Manager decision (UC47)"
                    >
                        <Clock size={16} />
                        <span>Pending Approvals</span>
                        {statusCounts.pendingApproval > 0 && (
                            <span style={{
                                padding: '0.1rem 0.45rem',
                                backgroundColor: '#fef3c7',
                                color: '#92400e',
                                borderRadius: '9999px',
                                fontSize: '0.75rem',
                                fontWeight: 700,
                                marginLeft: '0.2rem'
                            }}>
                                {statusCounts.pendingApproval}
                            </span>
                        )}
                    </Link>
                    </Can>
                    <Can permission="PURCHASE_REQUEST_CREATE">
                    <Link
                        to="/purchase-orders/create"
                        className="po-btn po-btn-primary"
                        title="Create a new purchase request (UC46)"
                    >
                        <Plus size={18} />
                        <span>Create Purchase Request</span>
                    </Link>
                    </Can>
                </div>
            </div>

            {/* Success Banner */}
            {successBanner && (
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '0.75rem',
                    padding: '0.85rem 1.25rem',
                    backgroundColor: '#ecfdf5',
                    color: '#065f46',
                    border: '1px solid #a7f3d0',
                    borderRadius: '8px',
                    fontSize: '0.9rem'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                        <CheckCircle2 size={18} style={{ color: '#059669', flexShrink: 0 }} />
                        <span><strong>Success:</strong> {successBanner}</span>
                    </div>
                    <button
                        type="button"
                        onClick={() => setSuccessBanner('')}
                        style={{ background: 'none', border: 'none', color: '#065f46', cursor: 'pointer', padding: '0.2rem' }}
                        title="Dismiss"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            {/* Error Banner */}
            {error && (
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.6rem',
                    padding: '0.85rem 1.25rem',
                    backgroundColor: '#fef2f2',
                    color: '#991b1b',
                    border: '1px solid #fecaca',
                    borderRadius: '8px',
                    fontSize: '0.9rem'
                }}>
                    <AlertCircle size={18} style={{ color: '#dc2626', flexShrink: 0 }} />
                    <span><strong>Error:</strong> {error}</span>
                </div>
            )}

            {/* Status Tabs */}
            <div className="po-tabs-container">
                {STATUS_TABS.map(tab => {
                    const count = statusCounts[tab.countKey] || 0;
                    const isActive = activeTab === tab.key;
                    return (
                        <button
                            key={tab.key}
                            type="button"
                            className={`po-tab-btn ${isActive ? 'active' : ''}`}
                            onClick={() => handleTabChange(tab.key)}
                        >
                            <span>{tab.label}</span>
                            <span className="po-tab-count">{count}</span>
                        </button>
                    );
                })}
            </div>

            {/* Search & Filters */}
            <div className="po-filter-card">
                <div className="po-filter-grid">
                    {/* Search */}
                    <div className="po-filter-group">
                        <label className="po-filter-label" htmlFor="po-search">Search</label>
                        <div className="po-search-wrap">
                            <Search size={16} className="po-search-icon" />
                            <input
                                id="po-search"
                                type="text"
                                className="po-input"
                                placeholder="PO/Request #, Supplier, Requester..."
                                value={keyword}
                                onChange={(e) => setKeyword(e.target.value)}
                            />
                            {keyword && (
                                <button
                                    type="button"
                                    onClick={() => setKeyword('')}
                                    style={{
                                        position: 'absolute',
                                        right: '0.6rem',
                                        background: 'none',
                                        border: 'none',
                                        color: '#94a3b8',
                                        cursor: 'pointer',
                                        padding: '0.2rem'
                                    }}
                                    title="Clear search"
                                >
                                    <X size={14} />
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Supplier Filter */}
                    <div className="po-filter-group">
                        <label className="po-filter-label" htmlFor="po-supplier-select">Supplier</label>
                        <select
                            id="po-supplier-select"
                            className="po-select"
                            value={supplierId}
                            onChange={(e) => {
                                setSupplierId(e.target.value);
                                setPage(0);
                            }}
                        >
                            <option value="">All Suppliers</option>
                            {suppliers.map(s => (
                                <option key={s.supplierId} value={s.supplierId}>
                                    {s.supplierName}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Date Range Filter (Last Updated) */}
                    <div className="po-filter-group">
                        <label className="po-filter-label">Last Updated Range</label>
                        <div className="po-date-range">
                            <input
                                type="date"
                                className="po-date-input"
                                value={startDate}
                                onChange={(e) => {
                                    setStartDate(e.target.value);
                                    setPage(0);
                                }}
                                title="Start Date"
                            />
                            <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>to</span>
                            <input
                                type="date"
                                className="po-date-input"
                                value={endDate}
                                onChange={(e) => {
                                    setEndDate(e.target.value);
                                    setPage(0);
                                }}
                                title="End Date"
                            />
                        </div>
                    </div>

                    {/* Clear Button */}
                    <div className="po-filter-actions">
                        {hasActiveFilters && (
                            <button
                                type="button"
                                className="po-btn-clear"
                                onClick={handleClearFilters}
                                title="Clear all search and filter conditions"
                            >
                                <X size={14} />
                                <span>Clear Filters</span>
                            </button>
                        )}
                    </div>
                </div>
            </div>

            {/* Content Table / States */}
            <div className="po-table-card">
                <div className="po-table-header-bar">
                    <span className="po-table-header-title">
                        {activeTab === 'ALL'
                            ? `All Purchase Requests & Orders (${totalElements})`
                            : `${STATUS_TABS.find(t => t.key === activeTab)?.label} (${totalElements})`}
                    </span>
                    <span className="po-table-header-subtitle">
                        Showing page {totalPages > 0 ? page + 1 : 0} of {totalPages}
                    </span>
                </div>

                {loading ? (
                    <div style={{ padding: '3.5rem 1rem', textAlign: 'center', color: '#64748b' }}>
                        <div className="po-spinner" />
                        <p style={{ margin: 0 }}>Loading purchase orders & requests...</p>
                    </div>
                ) : orders.length === 0 ? (
                    <div className="po-empty-state">
                        <ShoppingBag size={48} className="po-empty-icon" />
                        <h3 className="po-empty-title">No Purchase Requests or Orders Found</h3>
                        <p className="po-empty-desc">
                            {hasActiveFilters || activeTab !== 'ALL'
                                ? 'No records match the current filter criteria or status tab. Try clearing filters or switching tabs.'
                                : 'You have not submitted any purchase requests yet. Create your first purchase request below.'}
                        </p>
                        {hasActiveFilters ? (
                            <button
                                type="button"
                                className="po-btn po-btn-secondary"
                                onClick={handleClearFilters}
                            >
                                Clear Filters
                            </button>
                        ) : (
                            <Can permission="PURCHASE_REQUEST_CREATE">
                            <Link to="/purchase-orders/create" className="po-btn po-btn-primary">
                                <Plus size={16} />
                                <span>Create Purchase Request</span>
                            </Link>
                            </Can>
                        )}
                    </div>
                ) : (
                    <>
                        <div className="po-table-responsive">
                            <table className="po-table">
                                <thead>
                                    <tr>
                                        <th>Request / PO #</th>
                                        <th>Supplier</th>
                                        <th>Requested By</th>
                                        <th style={{ textAlign: 'center' }}>Items</th>
                                        <th style={{ textAlign: 'right' }}>Total (₫)</th>
                                        <th style={{ textAlign: 'center' }}>Status</th>
                                        <th>Last Updated</th>
                                        <th style={{ textAlign: 'center', width: '90px' }}>Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {orders.map(order => {
                                        const badge = getStatusBadge(order.status);
                                        const itemCount = order.details ? order.details.length : 0;
                                        return (
                                            <tr key={order.orderId}>
                                                <td className="po-number">
                                                    {order.poNumber}
                                                </td>
                                                <td>
                                                    <div className="po-supplier-cell">
                                                        <Building2 size={14} style={{ color: '#64748b', flexShrink: 0 }} />
                                                        <span>{order.supplierName}</span>
                                                    </div>
                                                </td>
                                                <td>
                                                    {order.requestedByUsername || '—'}
                                                </td>
                                                <td style={{ textAlign: 'center', color: '#475569' }}>
                                                    {itemCount} item{itemCount === 1 ? '' : 's'}
                                                </td>
                                                <td className="po-total-cell">
                                                    {formatCurrency(order.totalAmount)}
                                                </td>
                                                <td style={{ textAlign: 'center' }}>
                                                    <span className={badge.className}>
                                                        {badge.label}
                                                    </span>
                                                </td>
                                                <td style={{ color: '#475569', fontSize: '0.825rem' }}>
                                                    {formatDateTime(order.updatedAt || order.createdAt)}
                                                </td>
                                                <td style={{ textAlign: 'center' }}>
                                                    <Link
                                                        to={`/purchase-orders/${order.orderId}`}
                                                        className="po-btn-view"
                                                        title="View Details & Status Timeline"
                                                    >
                                                        <Eye size={13} />
                                                        <span>View</span>
                                                    </Link>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>

                        {/* Shared Reusable Pagination */}
                        <div style={{ padding: '0.75rem 1.25rem', borderTop: '1px solid #e2e8f0' }}>
                            <Pagination
                                page={page}
                                size={size}
                                totalElements={totalElements}
                                totalPages={totalPages}
                                onPageChange={(newPage) => setPage(newPage)}
                                onSizeChange={(newSize) => {
                                    setSize(newSize);
                                    setPage(0);
                                }}
                                pageSizeOptions={[5, 10, 20, 50]}
                                itemLabel="records"
                                idPrefix="po-list"
                            />
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
