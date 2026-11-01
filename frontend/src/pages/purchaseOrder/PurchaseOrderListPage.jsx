import { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
    Plus,
    CheckCircle2,
    AlertCircle,
    ShoppingBag,
    Eye,
    Calendar,
    Building2,
    X
} from 'lucide-react';
import PurchaseOrderService from '../../services/purchaseOrderService';

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

// Status badge styling helper
function getStatusBadge(status) {
    switch (status) {
        case 'PENDING_APPROVAL':
            return { label: 'Pending Approval', bg: '#fef3c7', color: '#92400e', border: '#fde68a' };
        case 'REJECTED_BY_MANAGER':
            return { label: 'Rejected by Manager', bg: '#fee2e2', color: '#991b1b', border: '#fca5a5' };
        case 'SENT':
            return { label: 'Sent to Supplier', bg: '#e0f2fe', color: '#0369a1', border: '#bae6fd' };
        case 'ACCEPTED':
            return { label: 'Accepted by Supplier', bg: '#ecfdf5', color: '#065f46', border: '#a7f3d0' };
        case 'REJECTED_BY_SUPPLIER':
            return { label: 'Rejected by Supplier', bg: '#fff1f2', color: '#be123c', border: '#fecdd3' };
        case 'PARTIALLY_FULFILLED':
            return { label: 'Partially Fulfilled', bg: '#fef9c3', color: '#854d0e', border: '#fef08a' };
        case 'FULFILLED':
            return { label: 'Fulfilled', bg: '#dcfce7', color: '#166534', border: '#bbf7d0' };
        default:
            return { label: status || 'Unknown', bg: '#f1f5f9', color: '#475569', border: '#cbd5e1' };
    }
}

export default function PurchaseOrderListPage() {
    const location = useLocation();

    // Success confirmation from post-submit navigation (UC46)
    const [successBanner, setSuccessBanner] = useState(location.state?.successMessage || '');

    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let isMounted = true;
        setLoading(true);

        PurchaseOrderService.getAll()
            .then(res => {
                if (!isMounted) return;
                setOrders(res.data?.data || []);
            })
            .catch(err => {
                if (!isMounted) return;
                setError(err.response?.data?.message || 'Failed to load purchase orders and requests.');
            })
            .finally(() => {
                if (isMounted) setLoading(false);
            });

        return () => { isMounted = false; };
    }, []);

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', paddingBottom: '2.5rem' }}>
            {/* Header */}
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
                borderBottom: '1px solid #e2e8f0',
                paddingBottom: '0.75rem'
            }}>
                <div>
                    <h1 style={{ margin: '0 0 0.25rem 0', fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
                        Purchase Orders & Requests
                    </h1>
                    <p style={{ margin: 0, color: '#64748b', fontSize: '0.875rem' }}>
                        Track and review purchase requests and official supplier purchase orders
                    </p>
                </div>
                <div>
                    <Link
                        to="/purchase-orders/create"
                        style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '0.5rem',
                            padding: '0.6rem 1.25rem',
                            backgroundColor: '#2563eb',
                            color: '#ffffff',
                            borderRadius: '6px',
                            fontWeight: 600,
                            fontSize: '0.875rem',
                            textDecoration: 'none',
                            boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                        }}
                    >
                        <Plus size={18} />
                        <span>Create Purchase Request</span>
                    </Link>
                </div>
            </div>

            {/* Post-submit Success Confirmation Banner */}
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

            {/* Content Table / Empty State */}
            {loading ? (
                <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b' }}>
                    <div style={{
                        width: '32px',
                        height: '32px',
                        border: '3px solid #bfdbfe',
                        borderTopColor: '#2563eb',
                        borderRadius: '50%',
                        animation: 'spin 0.7s linear infinite',
                        margin: '0 auto 0.75rem auto'
                    }} />
                    <p style={{ margin: 0 }}>Loading purchase orders & requests...</p>
                </div>
            ) : orders.length === 0 ? (
                <div style={{
                    background: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '8px',
                    padding: '3rem 1.5rem',
                    textAlign: 'center',
                    color: '#64748b'
                }}>
                    <ShoppingBag size={48} style={{ margin: '0 auto 1rem auto', color: '#94a3b8' }} />
                    <h3 style={{ margin: '0 0 0.5rem 0', color: '#1e293b' }}>No Purchase Requests or Orders Found</h3>
                    <p style={{ margin: '0 0 1.5rem 0', maxWidth: '500px', display: 'inline-block' }}>
                        You can create the first purchase request for stock replenishment using the button below.
                    </p>
                    <div>
                        <Link
                            to="/purchase-orders/create"
                            style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '0.5rem',
                                padding: '0.55rem 1.15rem',
                                backgroundColor: '#eff6ff',
                                color: '#2563eb',
                                border: '1px solid #bfdbfe',
                                borderRadius: '6px',
                                fontWeight: 600,
                                fontSize: '0.875rem',
                                textDecoration: 'none'
                            }}
                        >
                            <Plus size={16} />
                            <span>Create Purchase Request</span>
                        </Link>
                    </div>
                </div>
            ) : (
                <div style={{
                    background: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '8px',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    overflow: 'hidden'
                }}>
                    <div style={{
                        padding: '0.85rem 1.25rem',
                        backgroundColor: '#f8fafc',
                        borderBottom: '1px solid #e2e8f0',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}>
                        <span style={{ fontWeight: 600, fontSize: '0.95rem', color: '#1e293b' }}>
                            All Submitted Requests & Orders ({orders.length})
                        </span>
                        <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                            Showing requests across all statuses
                        </span>
                    </div>

                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                            <thead>
                                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', textAlign: 'left' }}>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600 }}>Request / PO #</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600 }}>Supplier</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600 }}>Requested By</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600 }}>Expected Delivery</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600, textAlign: 'right' }}>Total Estimated (₫)</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600, textAlign: 'center' }}>Status</th>
                                    <th style={{ padding: '0.75rem 1rem', color: '#475569', fontWeight: 600, textAlign: 'center' }}>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {orders.map(order => {
                                    const badge = getStatusBadge(order.status);
                                    return (
                                        <tr key={order.orderId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                                            <td style={{ padding: '0.75rem 1rem', fontFamily: 'monospace', fontWeight: 700, color: '#0369a1' }}>
                                                {order.poNumber}
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', color: '#1e293b' }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                                    <Building2 size={14} style={{ color: '#64748b' }} />
                                                    <span>{order.supplierName}</span>
                                                </div>
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', color: '#475569' }}>
                                                {order.requestedByUsername || '—'}
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', color: '#475569' }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                                    <Calendar size={14} style={{ color: '#64748b' }} />
                                                    <span>{formatDate(order.expectedDeliveryDate)}</span>
                                                </div>
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', textAlign: 'right', fontWeight: 700, color: '#1e3a8a', fontFamily: 'monospace, sans-serif' }}>
                                                {formatCurrency(order.totalAmount)}
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', textAlign: 'center' }}>
                                                <span style={{
                                                    display: 'inline-block',
                                                    padding: '0.2rem 0.65rem',
                                                    borderRadius: '9999px',
                                                    fontSize: '0.75rem',
                                                    fontWeight: 600,
                                                    backgroundColor: badge.bg,
                                                    color: badge.color,
                                                    border: `1px solid ${badge.border}`
                                                }}>
                                                    {badge.label}
                                                </span>
                                            </td>
                                            <td style={{ padding: '0.75rem 1rem', textAlign: 'center' }}>
                                                <Link
                                                    to={`/purchase-orders/${order.orderId}`}
                                                    style={{
                                                        display: 'inline-flex',
                                                        alignItems: 'center',
                                                        gap: '0.25rem',
                                                        padding: '0.3rem 0.6rem',
                                                        backgroundColor: '#f1f5f9',
                                                        color: '#334155',
                                                        borderRadius: '4px',
                                                        fontSize: '0.775rem',
                                                        fontWeight: 500,
                                                        textDecoration: 'none'
                                                    }}
                                                    title="View Details"
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
                </div>
            )}

            {/* Shared Module Boundary Note */}
            <div style={{
                padding: '0.75rem 1rem',
                backgroundColor: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '6px',
                fontSize: '0.8rem',
                color: '#64748b'
            }}>
                <em>Integration Notice:</em> This view lists submitted requests across all statuses for UC46 post-submission review. The full Purchase Order Status screen (status count metrics, advanced filtering by date/supplier, and detailed audit history) is specified under <strong>UC50 (Author: TungNP)</strong>.
            </div>
        </div>
    );
}
