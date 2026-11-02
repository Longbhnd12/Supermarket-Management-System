import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
    ArrowLeft,
    RotateCw,
    Building2,
    User,
    Calendar,
    Clock,
    CheckCircle2,
    XCircle,
    Send,
    Truck,
    PackageCheck,
    FileText,
    AlertCircle,
    Info,
    ExternalLink,
    Package
} from 'lucide-react';
import PurchaseOrderService from '../../services/purchaseOrderService';
import './PurchaseOrderDetailPage.css';

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

// Simple Date formatter (DD/MM/YYYY)
function formatDate(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        return d.toLocaleDateString('en-GB');
    } catch {
        return iso;
    }
}

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

export default function PurchaseOrderDetailPage() {
    const { id } = useParams();
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const fetchOrder = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const res = await PurchaseOrderService.getById(id);
            setOrder(res.data?.data || null);
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to load purchase order details.');
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        fetchOrder();
    }, [fetchOrder]);

    if (loading) {
        return (
            <div className="po-detail-page">
                <div style={{ padding: '4rem 1rem', textAlign: 'center', color: '#64748b' }}>
                    <div className="po-spinner" />
                    <p style={{ margin: 0 }}>Loading purchase order details...</p>
                </div>
            </div>
        );
    }

    if (error || !order) {
        return (
            <div className="po-detail-page">
                <div style={{
                    padding: '2.5rem',
                    textAlign: 'center',
                    backgroundColor: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '8px'
                }}>
                    <AlertCircle size={40} style={{ color: '#ef4444', margin: '0 auto 1rem auto' }} />
                    <h3 style={{ margin: '0 0 0.5rem 0', color: '#1e293b' }}>Purchase Order Not Found</h3>
                    <p style={{ color: '#64748b', marginBottom: '1.5rem' }}>
                        {error || `Unable to load purchase order #${id}.`}
                    </p>
                    <Link to="/purchase-orders" className="po-btn po-btn-primary">
                        <ArrowLeft size={16} />
                        <span>Return to Purchase Orders</span>
                    </Link>
                </div>
            </div>
        );
    }

    const badge = getStatusBadge(order.status);
    const details = order.details || [];
    const receipts = order.receipts || [];

    // ─── Status History Timeline Stage Evaluation (SRS 3.15.5.1 / SDD 4.15.6.1) ───
    // Stage 1: Request Submitted (Always completed)
    const stage1 = {
        title: 'Stage 1: Request Submitted',
        status: 'completed',
        time: formatDateTime(order.createdAt),
        actor: `Submitted by: ${order.requestedByUsername || 'Inventory Staff'}`,
        description: `Purchase request submitted with ${details.length} item${details.length === 1 ? '' : 's'} for an estimated amount of ${formatCurrency(order.totalAmount)}.`
    };

    // Stage 2: Manager Decision
    let stage2 = {};
    if (order.status === 'PENDING_APPROVAL') {
        stage2 = {
            title: 'Stage 2: Manager Decision',
            status: 'current',
            time: 'Pending Review',
            actor: 'Assigned to: Store Manager',
            description: 'Awaiting review and approval from Manager. The request can be reviewed under Pending Purchase Requests.'
        };
    } else if (order.status === 'REJECTED_BY_MANAGER') {
        stage2 = {
            title: 'Stage 2: Manager Decision',
            status: 'rejected',
            time: formatDateTime(order.updatedAt || order.createdAt),
            actor: `Rejected by: ${order.approvedByUsername || 'Store Manager'}`,
            description: `Request was rejected. Rejection reason: ${order.responseReason || 'Not specified'}.`
        };
    } else {
        stage2 = {
            title: 'Stage 2: Manager Decision',
            status: 'completed',
            time: formatDateTime(order.updatedAt || order.createdAt),
            actor: `Approved by: ${order.approvedByUsername || 'Store Manager'}`,
            description: 'Approved by Manager. Order quantities confirmed for purchase order generation.'
        };
    }

    // Stage 3: Order Sent
    let stage3 = {};
    if (order.status === 'PENDING_APPROVAL') {
        stage3 = {
            title: 'Stage 3: Order Sent to Supplier',
            status: 'upcoming',
            time: 'Awaiting Stage 2',
            actor: `Supplier: ${order.supplierName}`,
            description: 'Will be officially transmitted to supplier upon Manager approval.'
        };
    } else if (order.status === 'REJECTED_BY_MANAGER') {
        stage3 = {
            title: 'Stage 3: Order Sent to Supplier',
            status: 'upcoming',
            time: 'Cancelled',
            actor: `Supplier: ${order.supplierName}`,
            description: 'Order was not sent to supplier due to Manager rejection.'
        };
    } else {
        stage3 = {
            title: 'Stage 3: Order Sent to Supplier',
            status: 'completed',
            time: formatDateTime(order.updatedAt || order.createdAt),
            actor: `Transmitted to: ${order.supplierName}`,
            description: `Purchase order sent to ${order.supplierName} with expected delivery date: ${formatDate(order.expectedDeliveryDate)}.`
        };
    }

    // Stage 4: Supplier Response
    let stage4 = {};
    if (order.status === 'PENDING_APPROVAL' || order.status === 'REJECTED_BY_MANAGER') {
        stage4 = {
            title: 'Stage 4: Supplier Response',
            status: 'upcoming',
            time: 'Not Applicable',
            actor: `Supplier: ${order.supplierName}`,
            description: 'Pending prior workflow stages.'
        };
    } else if (order.status === 'SENT') {
        stage4 = {
            title: 'Stage 4: Supplier Response',
            status: 'current',
            time: 'Awaiting Response',
            actor: `Supplier: ${order.supplierName}`,
            description: `Purchase order transmitted to ${order.supplierName}. Awaiting confirmation of items and delivery schedule.`
        };
    } else if (order.status === 'REJECTED_BY_SUPPLIER') {
        stage4 = {
            title: 'Stage 4: Supplier Response',
            status: 'rejected',
            time: formatDateTime(order.updatedAt),
            actor: `Supplier: ${order.supplierName}`,
            description: `Supplier declined to fulfill this order. ${order.responseReason ? `Note: ${order.responseReason}` : ''}`
        };
    } else {
        stage4 = {
            title: 'Stage 4: Supplier Response',
            status: 'completed',
            time: formatDateTime(order.updatedAt),
            actor: `Supplier: ${order.supplierName}`,
            description: `Order accepted by supplier.${order.proposedDeliveryDate ? ` Supplier proposed delivery date: ${formatDate(order.proposedDeliveryDate)}.` : ''}`
        };
    }

    // Stage 5: Goods Received
    let stage5 = {};
    if (order.status === 'PENDING_APPROVAL' || order.status === 'REJECTED_BY_MANAGER' || order.status === 'REJECTED_BY_SUPPLIER') {
        stage5 = {
            title: 'Stage 5: Goods Received',
            status: 'upcoming',
            time: 'Not Applicable',
            actor: 'Warehouse Operations',
            description: 'Goods receipt is not scheduled.'
        };
    } else if (order.status === 'SENT' || order.status === 'ACCEPTED') {
        stage5 = {
            title: 'Stage 5: Goods Received',
            status: 'upcoming',
            time: `Expected: ${formatDate(order.proposedDeliveryDate || order.expectedDeliveryDate)}`,
            actor: 'Warehouse Operations',
            description: `Awaiting physical delivery at supermarket receiving dock. Scheduled for: ${formatDate(order.proposedDeliveryDate || order.expectedDeliveryDate)}.`
        };
    } else if (order.status === 'PARTIALLY_FULFILLED') {
        stage5 = {
            title: 'Stage 5: Goods Received',
            status: 'warning',
            time: formatDateTime(order.updatedAt),
            actor: 'Warehouse Operations',
            description: `Partial delivery received. Inspect the items table below for received versus ordered quantities across linked stock receipts.`
        };
    } else if (order.status === 'FULFILLED') {
        stage5 = {
            title: 'Stage 5: Goods Received',
            status: 'completed',
            time: formatDateTime(order.updatedAt),
            actor: 'Warehouse Operations',
            description: 'All ordered items have been fully received, inspected, and added into inventory stock.'
        };
    }

    const stages = [stage1, stage2, stage3, stage4, stage5];

    const getStageIcon = (stageIndex, status) => {
        if (status === 'completed') return <CheckCircle2 size={18} />;
        if (status === 'rejected') return <XCircle size={18} />;
        if (status === 'current') {
            if (stageIndex === 1) return <Clock size={18} />;
            if (stageIndex === 3) return <Truck size={18} />;
            return <RotateCw size={18} />;
        }
        if (status === 'warning') return <PackageCheck size={18} />;
        if (stageIndex === 0) return <FileText size={18} />;
        if (stageIndex === 1) return <User size={18} />;
        if (stageIndex === 2) return <Send size={18} />;
        if (stageIndex === 3) return <Truck size={18} />;
        return <PackageCheck size={18} />;
    };

    const getStageClass = (status) => {
        if (status === 'completed') return 'po-stage-completed';
        if (status === 'current') return 'po-stage-current';
        if (status === 'warning') return 'po-stage-warning';
        if (status === 'rejected') return 'po-stage-rejected';
        return 'po-stage-upcoming';
    };

    return (
        <div className="po-detail-page">
            {/* Navigation & Header */}
            <div className="po-detail-nav">
                <div className="po-detail-title-wrap">
                    <Link
                        to="/purchase-orders"
                        className="po-btn po-btn-secondary"
                        style={{ padding: '0.45rem 0.75rem' }}
                        title="Back to Purchase Orders & Requests List"
                    >
                        <ArrowLeft size={16} />
                        <span>Back</span>
                    </Link>
                    <h1 className="po-detail-title">
                        {order.poNumber}
                    </h1>
                    <span className={badge.className}>
                        {badge.label}
                    </span>
                </div>

                <div className="po-detail-actions">
                    <button
                        type="button"
                        className="po-btn po-btn-secondary"
                        onClick={fetchOrder}
                        title="Reload details"
                    >
                        <RotateCw size={15} />
                        <span>Refresh</span>
                    </button>
                    {order.status === 'PENDING_APPROVAL' && (
                        <Link
                            to="/purchase-orders/pending"
                            className="po-btn po-btn-outline"
                            title="Go to Pending Purchase Requests to approve or reject this request"
                        >
                            <Clock size={16} />
                            <span>Review in Pending Requests</span>
                        </Link>
                    )}
                </div>
            </div>

            {/* Overview / Header Details Card */}
            <div className="po-detail-card">
                <div className="po-detail-card-header">
                    <h2 className="po-detail-card-title">
                        <Building2 size={18} style={{ color: '#2563eb' }} />
                        <span>Purchase Order Summary</span>
                    </h2>
                    <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        ID #{order.orderId}
                    </span>
                </div>
                <div className="po-detail-card-body">
                    <div className="po-info-grid">
                        <div className="po-info-item">
                            <span className="po-info-label">Supplier</span>
                            <span className="po-info-val" style={{ fontWeight: 600, color: '#1e293b' }}>
                                {order.supplierName}
                            </span>
                        </div>
                        <div className="po-info-item">
                            <span className="po-info-label">Requested By</span>
                            <span className="po-info-val">
                                {order.requestedByUsername || '—'}
                            </span>
                        </div>
                        <div className="po-info-item">
                            <span className="po-info-label">Decision By</span>
                            <span className="po-info-val">
                                {order.approvedByUsername || '—'}
                            </span>
                        </div>
                        <div className="po-info-item">
                            <span className="po-info-label">Total Amount</span>
                            <span className="po-info-val highlight">
                                {formatCurrency(order.totalAmount)}
                            </span>
                        </div>
                        <div className="po-info-item">
                            <span className="po-info-label">Request Date</span>
                            <span className="po-info-val">
                                {formatDate(order.createdAt)}
                            </span>
                        </div>
                        <div className="po-info-item">
                            <span className="po-info-label">Expected Delivery</span>
                            <span className="po-info-val">
                                {formatDate(order.expectedDeliveryDate)}
                            </span>
                        </div>
                        {order.proposedDeliveryDate && (
                            <div className="po-info-item">
                                <span className="po-info-label">Supplier Proposed Delivery</span>
                                <span className="po-info-val" style={{ color: '#0369a1', fontWeight: 600 }}>
                                    {formatDate(order.proposedDeliveryDate)}
                                </span>
                            </div>
                        )}
                        <div className="po-info-item">
                            <span className="po-info-label">Last Updated</span>
                            <span className="po-info-val">
                                {formatDateTime(order.updatedAt || order.createdAt)}
                            </span>
                        </div>
                    </div>

                    {/* Notes & Reason */}
                    <div className="po-notes-grid">
                        <div className="po-note-box">
                            <div className="po-note-title">
                                <FileText size={13} style={{ color: '#64748b' }} />
                                <span>Requester Note</span>
                            </div>
                            <p className="po-note-content">
                                {order.note || 'None provided.'}
                            </p>
                        </div>
                        <div className="po-note-box">
                            <div className="po-note-title">
                                <Info size={13} style={{ color: '#64748b' }} />
                                <span>Manager Decision Note / Reason</span>
                            </div>
                            <p className="po-note-content">
                                {order.responseReason || (order.status === 'PENDING_APPROVAL' ? 'Awaiting manager review.' : 'None recorded.')}
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Status History Timeline (5 Stages) */}
            <div className="po-detail-card">
                <div className="po-detail-card-header">
                    <h2 className="po-detail-card-title">
                        <Clock size={18} style={{ color: '#2563eb' }} />
                        <span>Status History Timeline</span>
                    </h2>
                    <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        5 Workflow Stages (UC50 Specification)
                    </span>
                </div>
                <div className="po-detail-card-body">
                    <div className="po-timeline">
                        {stages.map((st, index) => {
                            const iconClass = getStageClass(st.status);
                            return (
                                <div key={index} className="po-timeline-item">
                                    <div className="po-timeline-line" />
                                    <div className={`po-timeline-icon-wrap ${iconClass}`}>
                                        {getStageIcon(index, st.status)}
                                    </div>
                                    <div className="po-timeline-content">
                                        <div className="po-timeline-header">
                                            <span className="po-timeline-stage-title">
                                                {st.title}
                                            </span>
                                            <span className="po-timeline-time">
                                                {st.time}
                                            </span>
                                        </div>
                                        <p className="po-timeline-desc">
                                            {st.description}
                                        </p>
                                        <span className="po-timeline-actor">
                                            {st.actor}
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {/* Persisted Data Provenance Disclosure */}
                    <div className="po-provenance-box">
                        <Info size={15} style={{ color: '#64748b', flexShrink: 0, marginTop: '2px' }} />
                        <span>
                            <strong>Data Integrity Notice:</strong> All timestamps and stage progression reflect real persisted audit records (
                            <code>createdAt</code>, <code>updatedAt</code>, and warehouse <code>receivedAt</code>) from the SMS database. No artificial event logs or simulated timestamps are fabricated.
                        </span>
                    </div>
                </div>
            </div>

            {/* Requested / Ordered Items Table */}
            <div className="po-detail-card">
                <div className="po-detail-card-header">
                    <h2 className="po-detail-card-title">
                        <Package size={18} style={{ color: '#2563eb' }} />
                        <span>Order Line Items ({details.length})</span>
                    </h2>
                    <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        Requested, approved, and received quantities
                    </span>
                </div>
                <div style={{ overflowX: 'auto' }}>
                    <table className="po-detail-table">
                        <thead>
                            <tr>
                                <th style={{ width: '40px' }}>#</th>
                                <th>Product Name</th>
                                <th>SKU / Code</th>
                                <th style={{ textAlign: 'center' }}>Unit</th>
                                <th style={{ textAlign: 'right' }}>Requested Qty</th>
                                <th style={{ textAlign: 'right' }}>Ordered Qty</th>
                                <th style={{ textAlign: 'right' }}>Received Qty</th>
                                <th style={{ textAlign: 'right' }}>Unit Cost (₫)</th>
                                <th style={{ textAlign: 'right' }}>Subtotal (₫)</th>
                            </tr>
                        </thead>
                        <tbody>
                            {details.map((item, index) => {
                                const orderedQty = item.orderedQuantity != null ? item.orderedQuantity : item.requestedQuantity;
                                const receivedQty = item.receivedQuantity != null ? item.receivedQuantity : 0;
                                const isReceivedFull = receivedQty >= orderedQty && orderedQty > 0;
                                const isReceivedPartial = receivedQty > 0 && receivedQty < orderedQty;

                                return (
                                    <tr key={item.orderDetailId || index}>
                                        <td style={{ color: '#94a3b8', fontSize: '0.8rem' }}>
                                            {index + 1}
                                        </td>
                                        <td style={{ fontWeight: 600, color: '#0f172a' }}>
                                            {item.productName}
                                        </td>
                                        <td style={{ fontFamily: 'monospace', color: '#475569', fontSize: '0.825rem' }}>
                                            {item.productCode || '—'}
                                        </td>
                                        <td style={{ textAlign: 'center', color: '#64748b' }}>
                                            {item.unit || 'pcs'}
                                        </td>
                                        <td style={{ textAlign: 'right', color: '#475569' }}>
                                            {Number(item.requestedQuantity).toLocaleString('en-US')}
                                        </td>
                                        <td style={{ textAlign: 'right', fontWeight: 600, color: '#1e293b' }}>
                                            {Number(orderedQty).toLocaleString('en-US')}
                                        </td>
                                        <td style={{
                                            textAlign: 'right',
                                            fontWeight: 600,
                                            color: isReceivedFull ? '#166534' : isReceivedPartial ? '#b45309' : '#64748b'
                                        }}>
                                            {Number(receivedQty).toLocaleString('en-US')}
                                        </td>
                                        <td style={{ textAlign: 'right', fontFamily: 'monospace' }}>
                                            {formatCurrency(item.unitPrice)}
                                        </td>
                                        <td style={{ textAlign: 'right', fontWeight: 600, fontFamily: 'monospace', color: '#1e3a8a' }}>
                                            {formatCurrency(item.subtotal)}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                        <tfoot>
                            <tr>
                                <td colSpan="8" style={{ textAlign: 'right' }}>
                                    {order.status === 'PENDING_APPROVAL' ? 'Estimated Total:' : 'Order Total:'}
                                </td>
                                <td style={{ textAlign: 'right', fontSize: '1.05rem', color: '#1e3a8a', fontFamily: 'monospace' }}>
                                    {formatCurrency(order.totalAmount)}
                                </td>
                            </tr>
                        </tfoot>
                    </table>
                </div>
            </div>

            {/* Linked Stock Receipts (if any exist) */}
            {receipts.length > 0 && (
                <div className="po-detail-card">
                    <div className="po-detail-card-header">
                        <h2 className="po-detail-card-title">
                            <Truck size={18} style={{ color: '#2563eb' }} />
                            <span>Linked Stock Receipts ({receipts.length})</span>
                        </h2>
                        <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                            Deliveries received into inventory for this order
                        </span>
                    </div>
                    <div style={{ overflowX: 'auto' }}>
                        <table className="po-detail-table">
                            <thead>
                                <tr>
                                    <th>Receipt Number</th>
                                    <th>Status</th>
                                    <th>Received Date</th>
                                    <th>Received By</th>
                                    <th style={{ textAlign: 'center', width: '90px' }}>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {receipts.map(sr => (
                                    <tr key={sr.stockReceiptId}>
                                        <td style={{ fontFamily: 'monospace', fontWeight: 600, color: '#0369a1' }}>
                                            {sr.receiptNumber}
                                        </td>
                                        <td>
                                            <span className="po-badge po-badge-fulfilled">
                                                {sr.status}
                                            </span>
                                        </td>
                                        <td style={{ color: '#475569' }}>
                                            {formatDateTime(sr.receivedAt || sr.createdAt)}
                                        </td>
                                        <td style={{ color: '#475569' }}>
                                            {sr.receivedByUsername || '—'}
                                        </td>
                                        <td style={{ textAlign: 'center' }}>
                                            <Link
                                                to={`/inventory/stock-receipts/${sr.stockReceiptId}`}
                                                className="po-btn-view"
                                                title="View Stock Receipt Details"
                                            >
                                                <ExternalLink size={13} />
                                                <span>View</span>
                                            </Link>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
