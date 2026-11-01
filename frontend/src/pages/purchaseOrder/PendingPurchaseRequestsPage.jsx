import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
    ArrowLeft,
    RotateCw,
    Search,
    Filter,
    CheckCircle2,
    XCircle,
    AlertCircle,
    Building2,
    Calendar,
    User,
    FileText,
    Eye,
    Check,
    X,
    Clock,
    DollarSign,
    PackageCheck,
    Send
} from 'lucide-react';
import PurchaseOrderService from '../../services/purchaseOrderService';
import './PendingPurchaseRequestsPage.css';

// Consistent VND currency formatting matching project standard
function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
    }).format(amount);
}

function formatDate(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        return d.toLocaleDateString('en-GB'); // DD/MM/YYYY
    } catch {
        return iso;
    }
}

export default function PendingPurchaseRequestsPage() {
    // ─── Data State ───
    const [pendingOrders, setPendingOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');
    const [successMessage, setSuccessMessage] = useState('');

    // ─── Filter & Search State ───
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedSupplierId, setSelectedSupplierId] = useState('ALL');

    // ─── Review State ───
    const [selectedOrder, setSelectedOrder] = useState(null);
    const [approvedQuantities, setApprovedQuantities] = useState({});
    const [managerNote, setManagerNote] = useState('');
    const [qtyErrors, setQtyErrors] = useState({});

    // ─── Modal State ───
    const [showApproveModal, setShowApproveModal] = useState(false);
    const [showRejectModal, setShowRejectModal] = useState(false);
    const [rejectionReason, setRejectionReason] = useState('');
    const [rejectionError, setRejectionError] = useState('');
    const [actionLoading, setActionLoading] = useState(false);

    // ─── Fetch Pending Requests ───
    const fetchPendingOrders = async (isManualRefresh = false) => {
        if (isManualRefresh) setRefreshing(true);
        else setLoading(true);
        setError('');

        try {
            const res = await PurchaseOrderService.getPending();
            const orders = res.data?.data || [];
            setPendingOrders(orders);

            // If selected order was previously open, check if it's still pending
            if (selectedOrder) {
                const updated = orders.find(o => o.orderId === selectedOrder.orderId);
                if (updated) {
                    setSelectedOrder(updated);
                } else {
                    // It was processed or removed
                    setSelectedOrder(null);
                }
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to load pending purchase requests.');
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    };

    useEffect(() => {
        fetchPendingOrders();
    }, []);

    // ─── Unique Suppliers for Filter Dropdown ───
    const suppliers = useMemo(() => {
        const map = new Map();
        pendingOrders.forEach(o => {
            if (o.supplierId && o.supplierName) {
                map.set(o.supplierId, o.supplierName);
            }
        });
        return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
    }, [pendingOrders]);

    // ─── Filtered Requests ───
    const filteredOrders = useMemo(() => {
        return pendingOrders.filter(o => {
            // Supplier filter
            if (selectedSupplierId !== 'ALL' && String(o.supplierId) !== String(selectedSupplierId)) {
                return false;
            }
            // Search query filter (matches PO Number, Requester, or Supplier)
            if (searchQuery.trim()) {
                const q = searchQuery.toLowerCase().trim();
                const poNumber = (o.poNumber || '').toLowerCase();
                const requester = (o.requestedBy || '').toLowerCase();
                const supplier = (o.supplierName || '').toLowerCase();
                if (!poNumber.includes(q) && !requester.includes(q) && !supplier.includes(q)) {
                    return false;
                }
            }
            return true;
        });
    }, [pendingOrders, selectedSupplierId, searchQuery]);

    // ─── Select Request for Review ───
    const handleSelectForReview = (order) => {
        setSelectedOrder(order);
        setManagerNote(order.responseReason || '');
        setError('');

        // Initialize approved quantities with requested quantities
        const initialQtys = {};
        const errors = {};
        if (order.details && Array.isArray(order.details)) {
            order.details.forEach(d => {
                const qty = d.orderedQuantity != null ? d.orderedQuantity : d.requestedQuantity;
                initialQtys[d.productId] = qty != null ? qty : 1;
            });
        }
        setApprovedQuantities(initialQtys);
        setQtyErrors(errors);
    };

    const handleCloseReview = () => {
        setSelectedOrder(null);
        setApprovedQuantities({});
        setQtyErrors({});
        setManagerNote('');
    };

    // ─── Quantity Change Handler ───
    const handleQuantityChange = (productId, valStr) => {
        const val = parseFloat(valStr);
        setApprovedQuantities(prev => ({
            ...prev,
            [productId]: valStr === '' ? '' : val
        }));

        setQtyErrors(prev => {
            const next = { ...prev };
            if (valStr === '' || isNaN(val) || val <= 0) {
                next[productId] = 'Quantity must be > 0';
            } else {
                delete next[productId];
            }
            return next;
        });
    };

    // ─── Live Calculation of Approved Total ───
    const calculatedTotals = useMemo(() => {
        if (!selectedOrder || !selectedOrder.details) {
            return { originalTotal: 0, approvedTotal: 0, difference: 0, hasErrors: false };
        }

        let approvedTotal = 0;
        let hasErrors = Object.keys(qtyErrors).length > 0;

        selectedOrder.details.forEach(item => {
            const qty = approvedQuantities[item.productId];
            if (qty === '' || isNaN(qty) || qty <= 0) {
                hasErrors = true;
            } else {
                const unitPrice = item.unitPrice || 0;
                approvedTotal += qty * unitPrice;
            }
        });

        const originalTotal = selectedOrder.totalAmount || 0;
        const difference = approvedTotal - originalTotal;

        return {
            originalTotal,
            approvedTotal,
            difference,
            hasErrors
        };
    }, [selectedOrder, approvedQuantities, qtyErrors]);

    // ─── Open Approval Confirmation Modal ───
    const handleOpenApproveModal = () => {
        // Validate all quantities
        const errors = {};
        if (selectedOrder && selectedOrder.details) {
            selectedOrder.details.forEach(item => {
                const qty = approvedQuantities[item.productId];
                if (qty === '' || isNaN(qty) || qty <= 0) {
                    errors[item.productId] = 'Quantity must be > 0';
                }
            });
        }

        if (Object.keys(errors).length > 0) {
            setQtyErrors(errors);
            setError('Please correct all invalid approved quantities before approving.');
            return;
        }

        setShowApproveModal(true);
    };

    // ─── Submit Approval ───
    const handleConfirmApprove = async () => {
        if (!selectedOrder) return;
        setActionLoading(true);
        setError('');

        const itemsPayload = (selectedOrder.details || []).map(d => ({
            orderDetailId: d.orderDetailId,
            productId: d.productId,
            approvedQuantity: approvedQuantities[d.productId]
        }));

        const payload = {
            managerNote: managerNote.trim() || null,
            items: itemsPayload
        };

        try {
            await PurchaseOrderService.approve(selectedOrder.orderId, payload);
            const poNum = selectedOrder.poNumber || `PO #${selectedOrder.orderId}`;
            setSuccessMessage(`Purchase Request ${poNum} has been approved and issued as an official Purchase Order sent to ${selectedOrder.supplierName}.`);
            setShowApproveModal(false);
            handleCloseReview();
            await fetchPendingOrders();
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to approve purchase request.');
            setShowApproveModal(false);
        } finally {
            setActionLoading(false);
        }
    };

    // ─── Open Rejection Modal ───
    const handleOpenRejectModal = () => {
        setRejectionReason('');
        setRejectionError('');
        setShowRejectModal(true);
    };

    // ─── Submit Rejection ───
    const handleConfirmReject = async () => {
        if (!rejectionReason.trim()) {
            setRejectionError('Rejection reason is required.');
            return;
        }

        if (rejectionReason.length > 255) {
            setRejectionError('Rejection reason cannot exceed 255 characters.');
            return;
        }

        setActionLoading(true);
        setError('');

        const payload = {
            reason: rejectionReason.trim(),
            managerNote: managerNote.trim() || null
        };

        try {
            await PurchaseOrderService.reject(selectedOrder.orderId, payload);
            const poNum = selectedOrder.poNumber || `PO #${selectedOrder.orderId}`;
            setSuccessMessage(`Purchase Request ${poNum} has been rejected.`);
            setShowRejectModal(false);
            handleCloseReview();
            await fetchPendingOrders();
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to reject purchase request.');
            setShowRejectModal(false);
        } finally {
            setActionLoading(false);
        }
    };

    return (
        <div className="sms-pending-pr-page">
            {/* ─── Header ─── */}
            <div className="sms-pending-pr-header">
                <div className="sms-pending-pr-header-left">
                    <Link to="/purchase-orders" className="sms-btn-back">
                        <ArrowLeft size={16} />
                        <span>All Purchase Orders</span>
                    </Link>
                    <div>
                        <h1 className="sms-pending-pr-title">Pending Purchase Requests</h1>
                        <p className="sms-pending-pr-subtitle">
                            Review, adjust approved quantities, and approve or reject purchase requests awaiting manager approval.
                        </p>
                    </div>
                </div>

                <div className="sms-pending-pr-header-actions">
                    <button
                        type="button"
                        className="sms-btn-refresh"
                        onClick={() => fetchPendingOrders(true)}
                        disabled={loading || refreshing}
                        title="Refresh pending requests"
                    >
                        <RotateCw size={15} className={refreshing ? 'animate-spin' : ''} />
                        <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
                    </button>
                </div>
            </div>

            {/* ─── Alerts ─── */}
            {successMessage && (
                <div className="sms-alert sms-alert--success">
                    <div className="sms-alert-content">
                        <CheckCircle2 size={18} />
                        <span>{successMessage}</span>
                    </div>
                    <button
                        type="button"
                        className="sms-alert-close"
                        onClick={() => setSuccessMessage('')}
                        aria-label="Dismiss alert"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

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
                        aria-label="Dismiss alert"
                    >
                        <X size={16} />
                    </button>
                </div>
            )}

            {/* ─── Filter Controls Bar ─── */}
            <div className="sms-pending-filter-bar">
                <div className="sms-filter-inputs">
                    {/* Search Input */}
                    <div className="sms-search-box">
                        <Search size={16} className="sms-search-icon" />
                        <input
                            type="text"
                            className="sms-search-input"
                            placeholder="Search by Request ID, Requester, or Supplier..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>

                    {/* Supplier Filter Dropdown */}
                    <select
                        className="sms-filter-select"
                        value={selectedSupplierId}
                        onChange={(e) => setSelectedSupplierId(e.target.value)}
                    >
                        <option value="ALL">All Suppliers ({suppliers.length})</option>
                        {suppliers.map(s => (
                            <option key={s.id} value={s.id}>
                                {s.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="sms-count-badge">
                    <Clock size={14} />
                    <span>{filteredOrders.length} Pending Approval</span>
                </div>
            </div>

            {/* ─── Main Content Layout (Table + Review Panel) ─── */}
            <div className={`sms-pending-pr-layout ${selectedOrder ? 'sms-pending-pr-layout--split' : ''}`}>
                {/* ─── Pending Requests Table Card ─── */}
                <div className="sms-pending-table-card">
                    <div className="sms-table-card-header">
                        <h2 className="sms-table-card-title">
                            <FileText size={17} />
                            <span>Purchase Requests Awaiting Review</span>
                        </h2>
                    </div>

                    {loading ? (
                        <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
                            <RotateCw size={24} className="animate-spin" style={{ margin: '0 auto 0.75rem' }} />
                            <div>Loading pending purchase requests...</div>
                        </div>
                    ) : filteredOrders.length === 0 ? (
                        <div className="sms-empty-card">
                            <PackageCheck className="sms-empty-icon" />
                            <h3 className="sms-empty-title">No pending purchase requests</h3>
                            <p className="sms-empty-desc">
                                {searchQuery || selectedSupplierId !== 'ALL'
                                    ? 'No requests match your search or filter criteria.'
                                    : 'All submitted purchase requests have been reviewed, or no requests are awaiting approval.'}
                            </p>
                            <Link to="/purchase-orders" className="sms-btn-secondary">
                                View All Purchase Orders
                            </Link>
                        </div>
                    ) : (
                        <div className="sms-table-wrapper">
                            <table className="sms-pending-table">
                                <thead>
                                    <tr>
                                        <th>Request ID</th>
                                        <th>Supplier</th>
                                        <th>Requested By</th>
                                        <th>Expected Date</th>
                                        <th style={{ textAlign: 'center' }}>Items</th>
                                        <th style={{ textAlign: 'right' }}>Est. Total</th>
                                        <th>Submitted</th>
                                        <th style={{ textAlign: 'center' }}>Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredOrders.map(order => {
                                        const isSelected = selectedOrder?.orderId === order.orderId;
                                        const itemCount = order.details ? order.details.length : 0;
                                        return (
                                            <tr
                                                key={order.orderId}
                                                className={isSelected ? 'sms-row--selected' : ''}
                                                onClick={() => handleSelectForReview(order)}
                                            >
                                                <td>
                                                    <span className="sms-po-code">{order.poNumber}</span>
                                                </td>
                                                <td>
                                                    <span className="sms-supplier-cell">{order.supplierName || '—'}</span>
                                                </td>
                                                <td>
                                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', color: '#475569' }}>
                                                        <User size={13} />
                                                        {order.requestedBy || 'Staff'}
                                                    </span>
                                                </td>
                                                <td>
                                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', color: '#475569' }}>
                                                        <Calendar size={13} />
                                                        {formatDate(order.expectedDeliveryDate)}
                                                    </span>
                                                </td>
                                                <td style={{ textAlign: 'center' }}>
                                                    <span style={{
                                                        display: 'inline-block',
                                                        padding: '0.15rem 0.5rem',
                                                        backgroundColor: '#f1f5f9',
                                                        borderRadius: '9999px',
                                                        fontSize: '0.8rem',
                                                        fontWeight: 600
                                                    }}>
                                                        {itemCount}
                                                    </span>
                                                </td>
                                                <td style={{ textAlign: 'right' }}>
                                                    <span className="sms-amount-cell">
                                                        {formatCurrency(order.totalAmount)}
                                                    </span>
                                                </td>
                                                <td>
                                                    <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                                                        {formatDate(order.createdAt)}
                                                    </span>
                                                </td>
                                                <td style={{ textAlign: 'center' }}>
                                                    <button
                                                        type="button"
                                                        className={`sms-btn-review ${isSelected ? 'sms-btn-review--active' : ''}`}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleSelectForReview(order);
                                                        }}
                                                    >
                                                        <Eye size={13} />
                                                        <span>{isSelected ? 'Reviewing' : 'Review'}</span>
                                                    </button>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>

                {/* ─── Review Panel (Detail Section) ─── */}
                {selectedOrder && (
                    <div className="sms-review-panel">
                        {/* Panel Header */}
                        <div className="sms-review-panel-header">
                            <div className="sms-review-title-group">
                                <div className="sms-review-po-number">
                                    <span>{selectedOrder.poNumber}</span>
                                    <span className="sms-status-badge sms-status-badge--pending">
                                        <Clock size={12} />
                                        Pending Approval
                                    </span>
                                </div>
                                <span style={{ fontSize: '0.825rem', color: '#64748b' }}>
                                    Submitted on {formatDate(selectedOrder.createdAt)}
                                </span>
                            </div>

                            <button
                                type="button"
                                className="sms-review-panel-close"
                                onClick={handleCloseReview}
                                title="Close review panel"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        {/* Request Meta Information */}
                        <div className="sms-review-meta-grid">
                            <div className="sms-meta-item">
                                <span className="sms-meta-label">Supplier</span>
                                <span className="sms-meta-value" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                    <Building2 size={14} color="#2563eb" />
                                    {selectedOrder.supplierName || '—'}
                                </span>
                            </div>

                            <div className="sms-meta-item">
                                <span className="sms-meta-label">Requested By</span>
                                <span className="sms-meta-value" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                    <User size={14} color="#64748b" />
                                    {selectedOrder.requestedBy || 'Staff'}
                                </span>
                            </div>

                            <div className="sms-meta-item">
                                <span className="sms-meta-label">Expected Delivery</span>
                                <span className="sms-meta-value" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                    <Calendar size={14} color="#059669" />
                                    {formatDate(selectedOrder.expectedDeliveryDate)}
                                </span>
                            </div>

                            <div className="sms-meta-item" style={{ gridColumn: '1 / -1' }}>
                                <span className="sms-meta-label">Requester's Note</span>
                                <span className="sms-meta-value sms-meta-value--note">
                                    {selectedOrder.note || 'No requester note provided.'}
                                </span>
                            </div>
                        </div>

                        {/* Items Review Section */}
                        <div className="sms-review-items-section">
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <h3 className="sms-section-title">
                                    <FileText size={15} />
                                    <span>Requested Products ({selectedOrder.details?.length || 0})</span>
                                </h3>
                                <span style={{ fontSize: '0.775rem', color: '#64748b' }}>
                                    Adjust Approved Qty as needed
                                </span>
                            </div>

                            <div className="sms-review-table-wrapper">
                                <table className="sms-review-table">
                                    <thead>
                                        <tr>
                                            <th>Product</th>
                                            <th style={{ textAlign: 'right' }}>Req. Qty</th>
                                            <th style={{ textAlign: 'right' }}>Approved Qty</th>
                                            <th>Unit</th>
                                            <th style={{ textAlign: 'right' }}>Est. Cost</th>
                                            <th style={{ textAlign: 'right' }}>Subtotal</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {selectedOrder.details?.map(item => {
                                            const reqQty = item.requestedQuantity ?? 0;
                                            const appQty = approvedQuantities[item.productId];
                                            const isModified = appQty !== '' && appQty !== reqQty;
                                            const isInvalid = qtyErrors[item.productId];
                                            const subtotal = (typeof appQty === 'number' && appQty > 0)
                                                ? appQty * (item.unitPrice || 0)
                                                : 0;

                                            return (
                                                <tr key={item.orderDetailId || item.productId}>
                                                    <td>
                                                        <span className="sms-product-name">{item.productName}</span>
                                                        <span className="sms-product-sub">
                                                            SKU: {item.productCode || item.sku || `PROD-${item.productId}`}
                                                        </span>
                                                    </td>
                                                    <td style={{ textAlign: 'right', fontWeight: 500, color: '#64748b' }}>
                                                        {reqQty}
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <input
                                                            type="number"
                                                            step="any"
                                                            min="0.001"
                                                            className={`sms-input-qty ${isModified ? 'sms-input-qty--modified' : ''} ${isInvalid ? 'sms-input-qty--invalid' : ''}`}
                                                            value={appQty !== undefined ? appQty : ''}
                                                            onChange={(e) => handleQuantityChange(item.productId, e.target.value)}
                                                            title={isInvalid || 'Adjust approved quantity'}
                                                        />
                                                    </td>
                                                    <td style={{ color: '#64748b' }}>
                                                        {item.unit || 'unit'}
                                                    </td>
                                                    <td style={{ textAlign: 'right', color: '#64748b', fontVariantNumeric: 'tabular-nums' }}>
                                                        {formatCurrency(item.unitPrice)}
                                                    </td>
                                                    <td style={{ textAlign: 'right', fontWeight: 600, color: '#0f172a', fontVariantNumeric: 'tabular-nums' }}>
                                                        {formatCurrency(subtotal)}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        {/* Totals Summary Box */}
                        <div className="sms-review-totals-box">
                            <div className="sms-total-row">
                                <span>Original Estimated Total:</span>
                                <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                                    {formatCurrency(calculatedTotals.originalTotal)}
                                </span>
                            </div>

                            <div className="sms-total-row sms-total-row--highlight">
                                <span>Approved Order Total:</span>
                                <span style={{ fontVariantNumeric: 'tabular-nums', color: '#059669' }}>
                                    {formatCurrency(calculatedTotals.approvedTotal)}
                                </span>
                            </div>

                            {calculatedTotals.difference !== 0 && (
                                <div className="sms-total-row" style={{ fontSize: '0.8rem', paddingTop: '0.2rem' }}>
                                    <span>Adjustment Variance:</span>
                                    <span className={`sms-total-variance ${calculatedTotals.difference > 0 ? 'sms-total-variance--positive' : 'sms-total-variance--negative'}`}>
                                        {calculatedTotals.difference > 0 ? '+' : ''}
                                        {formatCurrency(calculatedTotals.difference)}
                                        {calculatedTotals.difference > 0 ? ' (Quantity Increased)' : ' (Quantity Reduced)'}
                                    </span>
                                </div>
                            )}
                        </div>

                        {/* Optional Manager Note */}
                        <div className="sms-manager-note-section">
                            <div className="sms-manager-note-header">
                                <label className="sms-manager-note-label">
                                    Manager Remarks / Instructions (Optional)
                                </label>
                                <span className="sms-char-counter">
                                    {managerNote.length} / 255
                                </span>
                            </div>
                            <textarea
                                className="sms-manager-textarea"
                                placeholder="Add any specific instructions or notes for the supplier or requester..."
                                maxLength={255}
                                value={managerNote}
                                onChange={(e) => setManagerNote(e.target.value)}
                            />
                        </div>

                        {/* Action Buttons */}
                        <div className="sms-review-actions">
                            <button
                                type="button"
                                className="sms-btn-secondary"
                                onClick={handleCloseReview}
                            >
                                Cancel
                            </button>

                            <button
                                type="button"
                                className="sms-btn-reject"
                                onClick={handleOpenRejectModal}
                                disabled={actionLoading}
                            >
                                <XCircle size={16} />
                                <span>Reject Request</span>
                            </button>

                            <button
                                type="button"
                                className="sms-btn-approve"
                                onClick={handleOpenApproveModal}
                                disabled={actionLoading || calculatedTotals.hasErrors}
                            >
                                <Send size={15} />
                                <span>Approve & Send PO</span>
                            </button>
                        </div>
                    </div>
                )}
            </div>

            {/* ─── Modal: Confirm Approval ─── */}
            {showApproveModal && selectedOrder && (
                <div className="sms-modal-backdrop" onClick={() => !actionLoading && setShowApproveModal(false)}>
                    <div className="sms-modal-box" onClick={(e) => e.stopPropagation()}>
                        <div className="sms-modal-header sms-modal-header--success">
                            <h3 className="sms-modal-title sms-modal-title--success">
                                <CheckCircle2 size={20} />
                                <span>Confirm Purchase Request Approval</span>
                            </h3>
                            <button
                                type="button"
                                className="sms-review-panel-close"
                                onClick={() => !actionLoading && setShowApproveModal(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="sms-modal-body">
                            <div className="sms-modal-callout sms-modal-callout--info">
                                You are about to approve <strong>{selectedOrder.poNumber}</strong> and issue the official Purchase Order to <strong>{selectedOrder.supplierName}</strong>.
                            </div>

                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', fontSize: '0.875rem' }}>
                                <div>
                                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Supplier</span>
                                    <strong>{selectedOrder.supplierName}</strong>
                                </div>
                                <div>
                                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Expected Delivery</span>
                                    <strong>{formatDate(selectedOrder.expectedDeliveryDate)}</strong>
                                </div>
                                <div>
                                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Total Items</span>
                                    <strong>{selectedOrder.details?.length || 0} Products</strong>
                                </div>
                                <div>
                                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Approved Total</span>
                                    <strong style={{ color: '#059669', fontSize: '1rem' }}>
                                        {formatCurrency(calculatedTotals.approvedTotal)}
                                    </strong>
                                </div>
                            </div>

                            {calculatedTotals.difference !== 0 && (
                                <div style={{ fontSize: '0.825rem', color: '#b45309', backgroundColor: '#fffbeb', padding: '0.5rem 0.75rem', borderRadius: '5px', border: '1px solid #fef3c7' }}>
                                    * Manager modified approved quantities from original request ({calculatedTotals.difference > 0 ? '+' : ''}{formatCurrency(calculatedTotals.difference)}).
                                </div>
                            )}

                            {managerNote && (
                                <div>
                                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '0.2rem' }}>Manager Remarks</span>
                                    <p style={{ margin: 0, fontStyle: 'italic', fontSize: '0.85rem', color: '#334155' }}>
                                        "{managerNote}"
                                    </p>
                                </div>
                            )}
                        </div>

                        <div className="sms-modal-footer">
                            <button
                                type="button"
                                className="sms-btn-secondary"
                                onClick={() => setShowApproveModal(false)}
                                disabled={actionLoading}
                            >
                                Back
                            </button>
                            <button
                                type="button"
                                className="sms-btn-approve"
                                onClick={handleConfirmApprove}
                                disabled={actionLoading}
                            >
                                {actionLoading ? (
                                    <>
                                        <RotateCw size={15} className="animate-spin" />
                                        <span>Approving...</span>
                                    </>
                                ) : (
                                    <>
                                        <Send size={15} />
                                        <span>Confirm & Send PO</span>
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ─── Modal: Reject Request ─── */}
            {showRejectModal && selectedOrder && (
                <div className="sms-modal-backdrop" onClick={() => !actionLoading && setShowRejectModal(false)}>
                    <div className="sms-modal-box" onClick={(e) => e.stopPropagation()}>
                        <div className="sms-modal-header sms-modal-header--danger">
                            <h3 className="sms-modal-title sms-modal-title--danger">
                                <XCircle size={20} />
                                <span>Reject Purchase Request</span>
                            </h3>
                            <button
                                type="button"
                                className="sms-review-panel-close"
                                onClick={() => !actionLoading && setShowRejectModal(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="sms-modal-body">
                            <div className="sms-modal-callout sms-modal-callout--danger">
                                Rejecting request <strong>{selectedOrder.poNumber}</strong> will mark it as <strong>REJECTED BY MANAGER</strong>. This action will notify the requester.
                            </div>

                            <div className="sms-modal-field">
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <label className="sms-modal-field-label">
                                        Rejection Reason <span style={{ color: '#dc2626' }}>*</span>
                                    </label>
                                    <span className="sms-char-counter">
                                        {rejectionReason.length} / 255
                                    </span>
                                </div>
                                <textarea
                                    className={`sms-modal-textarea ${rejectionError ? 'sms-modal-textarea--error' : ''}`}
                                    placeholder="Please provide the reason for rejecting this purchase request (e.g. Budget ceiling reached, redundant order, incorrect specification)..."
                                    maxLength={255}
                                    value={rejectionReason}
                                    onChange={(e) => {
                                        setRejectionReason(e.target.value);
                                        if (e.target.value.trim()) setRejectionError('');
                                    }}
                                />
                                {rejectionError && (
                                    <span className="sms-field-error">{rejectionError}</span>
                                )}
                            </div>
                        </div>

                        <div className="sms-modal-footer">
                            <button
                                type="button"
                                className="sms-btn-secondary"
                                onClick={() => setShowRejectModal(false)}
                                disabled={actionLoading}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="sms-btn-reject"
                                onClick={handleConfirmReject}
                                disabled={actionLoading}
                            >
                                {actionLoading ? (
                                    <>
                                        <RotateCw size={15} className="animate-spin" />
                                        <span>Rejecting...</span>
                                    </>
                                ) : (
                                    <>
                                        <XCircle size={15} />
                                        <span>Confirm Rejection</span>
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

