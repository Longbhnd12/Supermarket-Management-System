import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Check, PencilLine, Send, X } from 'lucide-react';
import SupplierPortalService from '../../services/supplierPortalService';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { formatDate, formatDateTime, formatQuantity, formatVnd } from '../../utils/formatters';
import ConfirmDialog from '../user/ConfirmDialog';
import StatusBadge from './StatusBadge';
import '../user/UserManagement.css';
import './SupplierPortal.css';

// UC49 - Respond to Purchase Order (SRS Table 3-45). Read-only when the order is no longer SENT (AT4).
const OPTIONS = [
    { value: 'ACCEPT', label: 'Accept', icon: Check },
    { value: 'REJECT', label: 'Reject', icon: X },
    { value: 'PROPOSE_CHANGES', label: 'Propose Changes', icon: PencilLine }
];
const CONFIRM_TEXT = {
    ACCEPT: 'Accept this purchase order as it is? The store will be notified.',
    REJECT: 'Reject this purchase order? The store will be notified with your reason. This cannot be undone.',
    PROPOSE_CHANGES: 'Send your proposed changes to the store? The order stays open until the store reviews it.'
};

const toInput = (value) => (value === null || value === undefined ? '' : String(Number(value)));
const toDateInput = (value) => (value ? String(value).slice(0, 10) : '');

function initialProposals(order) {
    const map = {};
    order.items.forEach((item) => {
        map[item.orderDetailId] = {
            qty: toInput(item.proposedQuantity ?? item.orderedQuantity),
            price: toInput(item.proposedUnitPrice ?? item.unitPrice)
        };
    });
    return map;
}

export default function SupplierOrderDetailPage() {
    const { id } = useParams();
    const [order, setOrder] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [action, setAction] = useState('');
    const [reason, setReason] = useState('');
    const [deliveryDate, setDeliveryDate] = useState('');
    const [proposals, setProposals] = useState({});
    const [errors, setErrors] = useState({});
    const [submitError, setSubmitError] = useState('');
    const [notice, setNotice] = useState('');
    const [confirming, setConfirming] = useState(false);
    const [saving, setSaving] = useState(false);

    const fill = (data) => {
        setOrder(data);
        setProposals(initialProposals(data));
        setDeliveryDate(toDateInput(data.proposedDeliveryDate || data.expectedDeliveryDate));
        setReason('');
        setAction('');
        setErrors({});
    };

    useEffect(() => {
        let active = true;
        SupplierPortalService.getOrder(id)
            .then(({ data }) => active && fill(data.data))
            .catch((err) => active && setLoadError(getAccessAwareErrorMessage(err)));
        return () => { active = false; };
    }, [id]);

    const proposing = action === 'PROPOSE_CHANGES';
    const issuedDate = toDateInput(order?.issuedAt);

    const proposedTotal = useMemo(() => {
        if (!order) return 0;
        return order.items.reduce((sum, item) => {
            const p = proposals[item.orderDetailId] || {};
            const qty = proposing && p.qty !== '' ? Number(p.qty) : Number(item.proposedQuantity ?? item.orderedQuantity);
            const price = proposing && p.price !== '' ? Number(p.price) : Number(item.proposedUnitPrice ?? item.unitPrice);
            return sum + (Number.isNaN(qty) || Number.isNaN(price) ? 0 : qty * price);
        }, 0);
    }, [order, proposals, proposing]);

    const hasExistingProposal = order && (order.proposedDeliveryDate
        || order.items.some((item) => item.proposedQuantity !== null || item.proposedUnitPrice !== null));

    const updateProposal = (lineId, field) => (event) => {
        const value = event.target.value;
        setProposals((current) => ({ ...current, [lineId]: { ...current[lineId], [field]: value } }));
        setErrors((current) => ({ ...current, [`${field}-${lineId}`]: '', change: '' }));
    };

    const validate = () => {
        const next = {};
        if (!action) next.action = 'The Response Option field is required.'; // MSG02
        if ((action === 'REJECT' || proposing) && !reason.trim()) next.reason = 'The Reason / Comment field is required.';
        if (proposing) {
            let changed = false;
            order.items.forEach((item) => {
                const p = proposals[item.orderDetailId];
                const qty = Number(p.qty);
                const price = Number(p.price);
                if (p.qty === '' || Number.isNaN(qty) || qty <= 0) next[`qty-${item.orderDetailId}`] = 'Must be > 0';
                if (p.price === '' || Number.isNaN(price) || price <= 0) next[`price-${item.orderDetailId}`] = 'Must be > 0';
                if (qty !== Number(item.orderedQuantity) || price !== Number(item.unitPrice)) changed = true;
            });
            if (deliveryDate && issuedDate && deliveryDate < issuedDate) {
                next.deliveryDate = 'Proposed delivery date must not be earlier than the issued date.';
            }
            if (deliveryDate && deliveryDate !== toDateInput(order.expectedDeliveryDate)) changed = true;
            if (!changed) next.change = 'Enter at least one proposed change (quantity, unit price or delivery date).';
        }
        return next;
    };

    const askConfirm = (event) => {
        event.preventDefault();
        setSubmitError('');
        setNotice('');
        const next = validate();
        setErrors(next);
        if (Object.keys(next).length === 0) setConfirming(true);
    };

    const submit = async () => {
        setSaving(true);
        try {
            const payload = { action, reason: reason.trim() || null };
            if (proposing) {
                payload.proposedDeliveryDate = deliveryDate || null;
                payload.items = order.items.map((item) => ({
                    orderDetailId: item.orderDetailId,
                    proposedQuantity: Number(proposals[item.orderDetailId].qty),
                    proposedUnitPrice: Number(proposals[item.orderDetailId].price)
                }));
            }
            const { data } = await SupplierPortalService.respond(id, payload);
            fill(data.data);
            setNotice(data.message || 'Updated successfully.'); // MSG06
        } catch (err) {
            setSubmitError(getAccessAwareErrorMessage(err));
        } finally {
            setSaving(false);
            setConfirming(false);
        }
    };

    if (loadError) {
        return (
            <div className="um-page">
                <p className="um-alert um-alert-error" role="alert">{loadError}</p>
                <Link to="/supplier/purchase-orders" className="um-btn um-btn-outline"><ArrowLeft size={16} /> Back to List</Link>
            </div>
        );
    }
    if (!order) return <div className="um-page"><p className="um-empty">Loading purchase order…</p></div>;

    const fieldError = (key) => errors[key] && <span className="um-field-error">{errors[key]}</span>;

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>{order.poNumber} <StatusBadge status={order.status} /></h1>
                    <p>{order.canRespond ? 'Review the order, then accept, reject or propose changes.' : 'This order has already been answered and is read-only.'}</p>
                </div>
                <Link to="/supplier/purchase-orders" className="um-btn um-btn-outline"><ArrowLeft size={16} /> Back to List</Link>
            </div>

            {notice && <p className="um-alert um-alert-success" role="status">{notice}</p>}
            {submitError && <p className="um-alert um-alert-error" role="alert">{submitError}</p>}

            <div className="um-card">
                <dl className="sp-info">
                    <div><dt>Issued Date</dt><dd>{formatDate(order.issuedAt)}</dd></div>
                    <div>
                        <dt>Requested Delivery</dt>
                        <dd>
                            {formatDate(order.expectedDeliveryDate)}
                            {order.proposedDeliveryDate && <span className="sp-changed"> → {formatDate(order.proposedDeliveryDate)} (proposed)</span>}
                        </dd>
                    </div>
                    <div><dt>Approved By</dt><dd>{order.approvedBy || '—'}</dd></div>
                    <div><dt>Last Updated</dt><dd>{formatDateTime(order.lastUpdatedAt)}</dd></div>
                    {order.note && <div style={{ gridColumn: '1 / -1' }}><dt>Note from the store</dt><dd>{order.note}</dd></div>}
                </dl>
            </div>

            {hasExistingProposal && order.canRespond && (
                <p className="sp-banner">
                    You proposed changes to this order{order.responseReason ? ` ("${order.responseReason}")` : ''}. The store is reviewing them.
                    You can still accept the original order, reject it, or send a new proposal.
                </p>
            )}

            <div className="um-card">
                <h2 className="sp-section-title">Order Items</h2>
                <div className="um-table-wrap">
                    <table className="um-table sp-table">
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th className="sp-number">Ordered Qty</th>
                                <th className="sp-number">Ordered Unit Price</th>
                                <th className="sp-number">Proposed Qty</th>
                                <th className="sp-number">Proposed Unit Price</th>
                                <th className="sp-number">Subtotal</th>
                            </tr>
                        </thead>
                        <tbody>
                            {order.items.map((item) => {
                                const p = proposals[item.orderDetailId] || {};
                                return (
                                    <tr key={item.orderDetailId}>
                                        <td>
                                            <div className="um-username">{item.productName}</div>
                                            <div className="um-muted">{item.productCode}{item.unit ? ` · ${item.unit}` : ''}</div>
                                        </td>
                                        <td className="sp-number">{formatQuantity(item.orderedQuantity)}</td>
                                        <td className="sp-number">{formatVnd(item.unitPrice)}</td>
                                        <td className="sp-number">
                                            {proposing ? (
                                                <>
                                                    <input type="number" min="0" step="any" value={p.qty} onChange={updateProposal(item.orderDetailId, 'qty')} aria-invalid={Boolean(errors[`qty-${item.orderDetailId}`])} />
                                                    {fieldError(`qty-${item.orderDetailId}`)}
                                                </>
                                            ) : <span className={item.proposedQuantity !== null ? 'sp-changed' : ''}>{formatQuantity(item.proposedQuantity)}</span>}
                                        </td>
                                        <td className="sp-number">
                                            {proposing ? (
                                                <>
                                                    <input type="number" min="0" step="any" value={p.price} onChange={updateProposal(item.orderDetailId, 'price')} aria-invalid={Boolean(errors[`price-${item.orderDetailId}`])} />
                                                    {fieldError(`price-${item.orderDetailId}`)}
                                                </>
                                            ) : <span className={item.proposedUnitPrice !== null ? 'sp-changed' : ''}>{formatVnd(item.proposedUnitPrice)}</span>}
                                        </td>
                                        <td className="sp-number">{formatVnd(item.subtotal)}</td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
                <div className="sp-totals">
                    <span>Order total: <strong>{formatVnd(order.totalAmount)}</strong></span>
                    <span>Proposed total: <strong className={Math.round(proposedTotal) !== Math.round(Number(order.totalAmount)) ? 'sp-changed' : ''}>{formatVnd(proposedTotal)}</strong></span>
                </div>
            </div>

            {order.canRespond ? (
                <form className="um-card" onSubmit={askConfirm} noValidate>
                    <h2 className="sp-section-title">Your Response</h2>
                    <div className="sp-options" role="radiogroup" aria-label="Response option">
                        {OPTIONS.map(({ value, label, icon: Icon }) => (
                            <label key={value} className={`sp-option${action === value ? ' is-selected' : ''}`}>
                                <input
                                    type="radio"
                                    name="response"
                                    value={value}
                                    checked={action === value}
                                    onChange={() => { setAction(value); setErrors({}); }}
                                />
                                <Icon size={16} /> {label}
                            </label>
                        ))}
                    </div>
                    {fieldError('action')}

                    {action && (
                        <div className="sp-response-grid">
                            <label className="um-field">
                                Proposed Delivery Date
                                <input
                                    type="date"
                                    value={deliveryDate}
                                    min={issuedDate || undefined}
                                    onChange={(event) => { setDeliveryDate(event.target.value); setErrors((c) => ({ ...c, deliveryDate: '', change: '' })); }}
                                    disabled={!proposing}
                                    aria-invalid={Boolean(errors.deliveryDate)}
                                />
                                {fieldError('deliveryDate')}
                            </label>
                            <label className="um-field">
                                {action === 'ACCEPT' ? 'Comment (optional)' : 'Reason / Comment'}
                                <textarea
                                    value={reason}
                                    onChange={(event) => { setReason(event.target.value); setErrors((c) => ({ ...c, reason: '' })); }}
                                    maxLength={255}
                                    placeholder={action === 'REJECT' ? 'Why can you not fulfil this order?' : action === 'PROPOSE_CHANGES' ? 'Explain the changes you propose' : ''}
                                    aria-invalid={Boolean(errors.reason)}
                                />
                                {fieldError('reason')}
                            </label>
                        </div>
                    )}
                    {fieldError('change')}

                    <div className="um-form-actions">
                        <Link to="/supplier/purchase-orders" className="um-btn um-btn-outline">Cancel</Link>
                        <button type="submit" className="um-btn um-btn-primary" disabled={saving}><Send size={16} /> Submit Response</button>
                    </div>
                </form>
            ) : (
                order.responseReason && (
                    <div className="um-card">
                        <h2 className="sp-section-title">Your Response</h2>
                        <p style={{ margin: 0 }}>{order.responseReason}</p>
                    </div>
                )
            )}

            {confirming && (
                <ConfirmDialog
                    title="Submit response"
                    message={CONFIRM_TEXT[action]}
                    confirmLabel="Submit"
                    danger={action === 'REJECT'}
                    busy={saving}
                    onConfirm={submit}
                    onCancel={() => setConfirming(false)}
                />
            )}
        </div>
    );
}
