import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import SaleCompletionStatus from './SaleCompletionStatus';
import PaymentService from '../../services/paymentService';
import { getPOSErrorMessage as getErrorMessage } from './posErrorMessage';
import useCashChangePreview from './useCashChangePreview';
import { sandboxPaymentUrl, paymentExpiry, resumablePaymentUrl } from './paymentNavigation';
import { Banknote, CreditCard, QrCode, ExternalLink, RefreshCw } from 'lucide-react';

const methodLabels = { CASH: 'Cash', CARD: 'Domestic Card', QR: 'QR Payment' };
const methodIcons = { CASH: Banknote, CARD: CreditCard, QR: QrCode };

const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value ?? 0)} ₫`;
export default function PaymentPanel({ sale, busy, runAction, onLock, onCompleted, onStepChange }) {
    const [options, setOptions] = useState([]);
    const [methodsError, setMethodsError] = useState('');
    const [methodsVersion, setMethodsVersion] = useState(0);
    const [methodsLoading, setMethodsLoading] = useState(true);
    const [selection, setSelection] = useState('CASH:');
    const [choosingPayment, setChoosingPayment] = useState(false);
    const [received, setReceived] = useState('');
    const [overview, setOverview] = useState(null);
    const [statusUnavailable, setStatusUnavailable] = useState(false);
    const [error, setError] = useState('');
    const [paymentError, setPaymentError] = useState('');
    const [version, setVersion] = useState(0);
    const [now, setNow] = useState(() => Date.now());
    const [confirmModal, setConfirmModal] = useState(null);

    useEffect(() => {
        if (!confirmModal) return;
        function onKey(event) {
            if (event.key === 'Escape') setConfirmModal(null);
        }
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [confirmModal]);
    const pendingRequest = useRef(null);
    const alive = useRef(false);
    const lockCallback = useRef(onLock);
    useEffect(() => { lockCallback.current = onLock; }, [onLock]);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; };
    }, []);
    useEffect(() => {
        let active = true;
        PaymentService.methods().then(({ data }) => {
            if (active) {
                setOptions(data.data.filter((option) => option.method === 'CASH'
                    || option.method === 'CARD' && option.bankCode === 'VNBANK'
                    || option.method === 'QR' && option.bankCode === 'VNPAYQR'));
                setMethodsError('');
            }
        }).catch((err) => { if (active) setMethodsError(getErrorMessage(err)); })
            .finally(() => { if (active) setMethodsLoading(false); });
        return () => { active = false; };
    }, [methodsVersion]);
    useEffect(() => {
        let active = true;
        let timer;
        async function load() {
            try {
                const { data } = await PaymentService.overview(sale.saleId);
                if (!active) return;
                setOverview(data.data); setStatusUnavailable(false); setError(''); lockCallback.current(data.data.locked);
                const storageKey = `sms.payment.request.${sale.saleId}`;
                let stored;
                try { stored = JSON.parse(sessionStorage.getItem(storageKey)); } catch { /* Invalid local state has no authority. */ }
                if (stored && data.data.attempts.some((attempt) => attempt.idempotencyKey === stored.key)) {
                    pendingRequest.current = null;
                    sessionStorage.removeItem(storageKey);
                }
                // Poll sequentially; never overlap requests or retry writes automatically.
                timer = setTimeout(load, 5000);
            } catch (err) {
                if (active) { setStatusUnavailable(true); setError(getErrorMessage(err)); lockCallback.current(true); }
            }
        }
        load();
        return () => { active = false; clearTimeout(timer); };
    }, [sale.saleId, version]);

    const success = overview?.attempts.find((attempt) => attempt.status === 'SUCCESS');
    const latest = success ?? overview?.attempts[0];
    const progressCallback = useRef(onStepChange);
    useEffect(() => { progressCallback.current = onStepChange; }, [onStepChange]);
    const step = sale.status === 'COMPLETED' ? 4
        : success || latest?.status === 'PENDING' || overview?.locked ? 3 : choosingPayment ? 2 : 1;
    useEffect(() => { progressCallback.current?.(step); }, [step]);
    useEffect(() => {
        if (latest?.status !== 'PENDING') return;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [latest?.status, latest?.paymentId]);
    const resumeUrl = !statusUnavailable && !busy && sale.status === 'DRAFT'
        ? resumablePaymentUrl(latest, now) : null;
    const expiresAt = paymentExpiry(latest?.expiresAt);
    const locallyExpired = latest?.status === 'PENDING' && Number.isFinite(expiresAt) && expiresAt <= now;
    const remainingSeconds = Number.isFinite(expiresAt) ? Math.max(0, Math.ceil((expiresAt - now) / 1000)) : 0;
    const selected = options.find((option) => `${option.method}:${option.bankCode ?? ''}` === selection);
    const locked = !overview || overview.locked || statusUnavailable;
    const disabled = busy || locked || sale.status !== 'DRAFT' || methodsLoading || Boolean(methodsError);
    const cashSelected = selected?.method === 'CASH';
    const qrSelected = selected?.method === 'QR';
    const cashPreview = useCashChangePreview({ saleId: sale.saleId, total: sale.totalAmount, received,
        enabled: cashSelected && !locked && sale.status === 'DRAFT' && Number(sale.totalAmount) > 0 && sale.items.length > 0,
        revision: `${version}:${selection}` });
    const canConfirmCash = cashPreview.status === 'ready' && cashPreview.result?.sufficient === true;

    function submit(event) {
        event.preventDefault();
        if (disabled || !selected?.enabled || Number(sale.totalAmount) <= 0 || !sale.items.length || cashSelected && !canConfirmCash) return;
        const confirmedCash = cashSelected ? cashPreview.result : null;
        const payload = {
            paymentMethod: selected.method,
            bankCode: selected.bankCode,
            expectedTotal: confirmedCash?.totalAmount ?? sale.totalAmount,
            receivedAmount: confirmedCash?.receivedAmount ?? null
        };
        if (cashSelected) return handleExecutePayment(payload);
        setConfirmModal({
            isCash: cashSelected,
            title: cashSelected ? 'Confirm Cash Payment' : 'Confirm Electronic Payment',
            totalAmount: confirmedCash?.totalAmount ?? sale.totalAmount,
            receivedAmount: confirmedCash?.receivedAmount ?? null,
            changeAmount: confirmedCash?.changeAmount ?? null,
            methodLabel: methodLabels[selected.method],
            payload
        });
    }

    async function handleExecutePayment(payload) {
        setConfirmModal(null);
        await runAction(async () => {
            setPaymentError('');
            // Persist a key before sending; a network timeout must not silently generate a new charge.
            const storageKey = `sms.payment.request.${sale.saleId}`;
            const fingerprint = JSON.stringify(payload);
            let stored;
            try { stored = JSON.parse(sessionStorage.getItem(storageKey)); } catch { /* Discard malformed local state. */ }
            if ((pendingRequest.current && pendingRequest.current.fingerprint !== fingerprint)
                || (stored && stored.fingerprint !== fingerprint)) {
                setPaymentError('The previous request has an unknown outcome. Refresh the status before changing payment methods.'); return;
            }
            const request = stored?.fingerprint === fingerprint ? stored
                : { fingerprint, key: crypto.randomUUID() };
            pendingRequest.current = request;
            sessionStorage.setItem(storageKey, JSON.stringify(request));
            try {
                const { data } = await PaymentService.create(sale.saleId, { ...payload, idempotencyKey: request.key });
                if (!alive.current) return;
                const payment = data.data;
                lockCallback.current(true);
                setOverview({ locked: true, attempts: [payment, ...(overview?.attempts ?? []).filter((p) => p.paymentId !== payment.paymentId)] });
                pendingRequest.current = null;
                sessionStorage.removeItem(storageKey);
                setVersion((value) => value + 1);
                if (payment.paymentUrl) {
                    // Only accept the exact Sandbox hosted origin; no arbitrary redirect from API data.
                    const url = sandboxPaymentUrl(payment.paymentUrl);
                    if (!url) {
                        setPaymentError('Invalid payment URL. Please refresh the status.'); return;
                    }
                    window.location.assign(url);
                }
            } catch (err) {
                if (!alive.current) return;
                setPaymentError(err.response?.status === 400 ? getErrorMessage(err)
                    : `${getErrorMessage(err)}. Do not retry payment while the outcome is unknown.`);
                lockCallback.current(true);
                // A definitive HTTP validation response did not create an attempt; same-key replay remains safe either way.
                if (err.response && err.response.status < 500) {
                    pendingRequest.current = null;
                    sessionStorage.removeItem(storageKey);
                }
                setVersion((value) => value + 1);
            }
        });
    }

    return <section className="pos-payment" aria-label="Payment" onFocusCapture={() => setChoosingPayment(true)}
        onBlurCapture={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) setChoosingPayment(false);
        }}>
        <div className="pos-payment-heading"><h3>Payment</h3><span className="pos-sandbox-label">VNPay Sandbox · TEST</span></div>
        {methodsError && <p role="alert" className="pos-payment-error">Unable to load payment methods: {methodsError}</p>}
        {(paymentError || error) && <p role="alert" className="pos-payment-error">{paymentError || error}</p>}
        {!overview && !error && <p role="status">Checking payment status…</p>}
        {success ? <div className="pos-payment-state" role="status"><strong>Paid</strong>
            <SaleCompletionStatus key={sale.saleId} saleId={sale.saleId} blocked={success.reconciliationRequired} onCompleted={onCompleted} english />
            {success.changeAmount != null && <p>Change Due: <strong>{money(success.changeAmount)}</strong></p>}
            {success.reconciliationRequired && <p>A late payment conflict requires manual reconciliation. Do not collect another payment.</p>}
        </div> : latest && <div className="pos-payment-state" role="status">
            <strong>{latest.reconciliationRequired ? 'Payment Reconciliation Required' : locallyExpired ? 'Payment Link Expired' : latest.status === 'PENDING' ? 'Awaiting VNPay Confirmation'
                : latest.status === 'TIMEOUT' ? 'Payment Expired' : 'Payment Unsuccessful'}</strong>
            <p>{latest.reconciliationRequired ? 'The payment outcome is unknown. This sale is locked to prevent duplicate payment.'
                : overview.locked ? 'This sale cannot be changed or paid again yet.' : 'The sale is saved. Select a payment method and try again.'}</p>
                        {latest.paymentMethod !== 'CASH' && <>
                <dl className="pos-pending-details">
                    <div><dt>Amount</dt><dd>{money(latest.amount)}</dd></div>
                    <div><dt>Transaction Reference</dt><dd>{latest.txnRef}</dd></div>
                    {Number.isFinite(expiresAt) && <div><dt>Payment Deadline</dt><dd>{new Intl.DateTimeFormat('vi-VN', {
                        timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium',
                    }).format(expiresAt)} (Vietnam time)</dd></div>}
                </dl>
                {resumeUrl && <>
                    <p>{Math.floor(remainingSeconds / 60)} minutes {remainingSeconds % 60} seconds remaining to reopen this payment.</p>
                    <a className="pos-gateway-link" href={resumeUrl}><ExternalLink size={14} />
                        {latest.paymentMethod === 'QR' ? 'Reopen VNPay QR Page' : 'Continue to VNPay Sandbox'}</a>
                </>}
                {locallyExpired && <p>Do not reopen an expired link. Wait for server verification or select Reconcile VNPay. Do not collect another payment while the outcome is unknown.</p>}
            </>}
        </div>}
        {!success && <form onSubmit={submit}>
            <div className="pos-method-grid" role="group" aria-label="Select payment method">
                {options.map((option) => {
                    const value = `${option.method}:${option.bankCode ?? ''}`;
                    const Icon = methodIcons[option.method] ?? CreditCard;
                    return <button key={value} type="button" aria-pressed={selection === value}
                        disabled={disabled || !option.enabled} className="pos-method-option" onClick={() => setSelection(value)}>
                        <Icon size={19} /><span>{methodLabels[option.method]}<small>{option.enabled ? option.method === 'CASH' ? 'At the checkout' : option.method === 'QR' ? 'Scan the code on VNPay' : 'Via VNPay Sandbox'
                            : 'Currently unavailable'}</small></span>
                    </button>;
                })}
            </div>
            {methodsLoading && <p role="status" className="pos-hint">Loading payment methods…</p>}
            {cashSelected && <>
                <label className="pos-cash-received">Cash Received (VND)<input className="pos-cash-received-input" type="number" inputMode="decimal" placeholder="0" required min="0" max="9999999999999.99" step="0.01"
                    value={received} disabled={disabled} onChange={(event) => setReceived(event.target.value)} />
                    {received !== '' && Number.isFinite(Number(received)) && Number(received) >= 0 &&
                        <span className="pos-cash-received-formatted">{money(Number(received))}</span>}
                </label>
                <div className={`pos-cash-preview ${cashPreview.result && !cashPreview.result.sufficient ? 'pos-cash-preview--short' : ''}`}
                    role="status" aria-live="polite">
                    <div><span>Total Due</span><strong>{money(sale.totalAmount)}</strong></div>
                    {cashPreview.status === 'empty' && <p>Enter cash received to see the change due.</p>}
                    {cashPreview.status === 'loading' && <p>Calculating change…</p>}
                    {cashPreview.error && <p>{cashPreview.error}</p>}
                    {cashPreview.result && <div><span>{cashPreview.result.sufficient ? 'Change Due' : 'Shortfall'}</span>
                        <strong>{money(cashPreview.result.sufficient ? cashPreview.result.changeAmount : cashPreview.result.shortfallAmount)}</strong></div>}
                    {cashPreview.status === 'error' && <button type="button" disabled={busy} onClick={() => setVersion((value) => value + 1)}>Recalculate</button>}
                </div>
            </>}
            {!cashSelected && selected?.enabled && <div className="pos-electronic-info"><ExternalLink size={15} />
                <p>You will be redirected to VNPay Sandbox to pay {money(sale.totalAmount)} using {methodLabels[selected.method].toLowerCase()}.
                    {qrSelected && 'VNPay will display a QR code for the customer to pay. '}On return, the system checks the result with the server. The sale stays locked while awaiting confirmation.</p></div>}
            <button className="pos-primary" disabled={disabled || !selected?.enabled || Number(sale.totalAmount) <= 0 || !sale.items.length || cashSelected && !canConfirmCash}>
                {cashSelected ? 'Confirm Cash Payment' : qrSelected ? 'Open VNPay QR Page' : 'Pay via VNPay'} · {money(sale.totalAmount)}</button>
        </form>}
        <div className="pos-payment-actions"><button disabled={busy} onClick={() => setVersion((value) => value + 1)}>Refresh Status</button>
            <button type="button" disabled={busy || methodsLoading} onClick={() => { setMethodsLoading(true); setMethodsVersion((value) => value + 1); }}>
                <RefreshCw size={12} /> Reload Methods</button>
            {latest && latest.paymentMethod !== 'CASH' && (latest.reconciliationRequired || latest.status === 'PENDING') &&
                <button disabled={busy} onClick={() => runAction(async () => {
                    try { await PaymentService.reconcile(latest.paymentId); }
                    catch (err) { setError(getErrorMessage(err)); }
                    finally { setVersion((value) => value + 1); }
                })}>Reconcile VNPay</button>}</div>
        {confirmModal && typeof document !== 'undefined' && createPortal(
            <div className="pos-confirm-overlay" role="dialog" aria-modal="true" onClick={() => setConfirmModal(null)}>
                <div className="pos-confirm-modal" onClick={(e) => e.stopPropagation()}>
                    <div className="pos-confirm-header">
                        <div className={`pos-confirm-icon-box ${confirmModal.isCash ? 'pos-confirm-icon-cash' : 'pos-confirm-icon-card'}`}>
                            {confirmModal.isCash ? <Banknote size={26} /> : <CreditCard size={26} />}
                        </div>
                        <div>
                            <h4>{confirmModal.title}</h4>
                            <p>Sale #{sale.saleId} • Cashier: {sale?.cashierUsername || 'Checkout'}</p>
                        </div>
                    </div>

                    <div className="pos-confirm-body">
                        {confirmModal.isCash ? (
                            <div className="pos-confirm-summary-table">
                                <div className="pos-confirm-row">
                                    <span>Total Due:</span>
                                    <strong className="pos-confirm-total">{money(confirmModal.totalAmount)}</strong>
                                </div>
                                <div className="pos-confirm-row">
                                    <span>Cash Received:</span>
                                    <strong className="pos-confirm-received">{money(confirmModal.receivedAmount)}</strong>
                                </div>
                                <div className="pos-confirm-row pos-confirm-row-highlight">
                                    <span>Change Due:</span>
                                    <strong className="pos-confirm-change">{money(confirmModal.changeAmount)}</strong>
                                </div>
                            </div>
                        ) : (
                            <div className="pos-confirm-summary-table">
                                <div className="pos-confirm-row">
                                    <span>Payment Method:</span>
                                    <strong>{confirmModal.methodLabel}</strong>
                                </div>
                                <div className="pos-confirm-row pos-confirm-row-highlight">
                                    <span>Payment Amount:</span>
                                    <strong className="pos-confirm-total">{money(confirmModal.totalAmount)}</strong>
                                </div>
                                <p className="pos-confirm-note">
                                    You will be redirected to VNPay Sandbox for QR or card payment.
                                </p>
                            </div>
                        )}
                    </div>

                    <div className="pos-confirm-footer">
                        <button
                            type="button"
                            className="pos-confirm-btn-cancel"
                            onClick={() => setConfirmModal(null)}
                        >
                            Cancel
                        </button>
                        <button
                            type="button"
                            className="pos-confirm-btn-ok"
                            autoFocus
                            disabled={busy}
                            onClick={() => handleExecutePayment(confirmModal.payload)}
                        >
                            {confirmModal.isCash ? 'Confirm Cash Payment' : 'Continue to Payment'}
                        </button>
                    </div>
                </div>
            </div>,
            document.body
        )}
    </section>;
}

