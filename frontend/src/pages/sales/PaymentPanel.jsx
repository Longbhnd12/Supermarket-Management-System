import { useEffect, useRef, useState } from 'react';
import SaleCompletionStatus from './SaleCompletionStatus';
import PaymentService from '../../services/paymentService';
import { getErrorMessage } from '../../utils/errorUtils';
import useCashChangePreview from './useCashChangePreview';
import { Banknote, CreditCard, QrCode, Wallet, ExternalLink, RefreshCw } from 'lucide-react';

const methodIcons = { CASH: Banknote, CARD: CreditCard, QR: QrCode, EWALLET: Wallet };

const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value ?? 0)} ₫`;
function sandboxUrl(value) {
    try {
        const url = new URL(value);
        return url.origin === 'https://sandbox.vnpayment.vn' && url.pathname === '/paymentv2/vpcpay.html' ? url.href : null;
    } catch { return null; }
}

export default function PaymentPanel({ sale, busy, runAction, onLock, onCompleted }) {
    const [options, setOptions] = useState([]);
    const [methodsError, setMethodsError] = useState('');
    const [methodsVersion, setMethodsVersion] = useState(0);
    const [methodsLoading, setMethodsLoading] = useState(true);
    const [selection, setSelection] = useState('CASH:');
    const [received, setReceived] = useState('');
    const [overview, setOverview] = useState(null);
    const [statusUnavailable, setStatusUnavailable] = useState(false);
    const [error, setError] = useState('');
    const [paymentError, setPaymentError] = useState('');
    const [version, setVersion] = useState(0);
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
            if (active) { setOptions(data.data); setMethodsError(''); }
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
    const selected = options.find((option) => `${option.method}:${option.bankCode ?? ''}` === selection);
    const locked = !overview || overview.locked || statusUnavailable;
    const disabled = busy || locked || sale.status !== 'DRAFT' || methodsLoading || Boolean(methodsError);
    const cashSelected = selected?.method === 'CASH';
    const cashPreview = useCashChangePreview({ saleId: sale.saleId, total: sale.totalAmount, received,
        enabled: cashSelected && !locked && sale.status === 'DRAFT' && Number(sale.totalAmount) > 0 && sale.items.length > 0,
        revision: `${version}:${selection}` });
    const canConfirmCash = cashPreview.status === 'ready' && cashPreview.result?.sufficient === true;

    async function submit(event) {
        event.preventDefault();
        if (disabled || !selected?.enabled || cashSelected && !canConfirmCash) return;
        const confirmedCash = cashSelected ? cashPreview.result : null;
        const message = confirmedCash
            ? `Xác nhận tiền mặt\nTổng cần thu: ${money(confirmedCash.totalAmount)}\nTiền khách đưa: ${money(confirmedCash.receivedAmount)}\nTiền trả lại: ${money(confirmedCash.changeAmount)}`
            : `Xác nhận thanh toán ${money(sale.totalAmount)} bằng ${selected.label}?`;
        if (!window.confirm(message)) return;
        await runAction(async () => {
            setPaymentError('');
            const payload = { paymentMethod: selected.method, bankCode: selected.bankCode,
                expectedTotal: confirmedCash?.totalAmount ?? sale.totalAmount, receivedAmount: confirmedCash?.receivedAmount ?? null };
            // Persist a key before sending; a network timeout must not silently generate a new charge.
            const storageKey = `sms.payment.request.${sale.saleId}`;
            const fingerprint = JSON.stringify(payload);
            let stored;
            try { stored = JSON.parse(sessionStorage.getItem(storageKey)); } catch { /* Discard malformed local state. */ }
            if ((pendingRequest.current && pendingRequest.current.fingerprint !== fingerprint)
                || (stored && stored.fingerprint !== fingerprint)) {
                setPaymentError('Yêu cầu trước chưa rõ kết quả. Tải lại trạng thái trước khi đổi phương thức.'); return;
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
                    const url = sandboxUrl(payment.paymentUrl);
                    if (!url) {
                        setPaymentError('URL thanh toán không hợp lệ. Vui lòng tải lại trạng thái.'); return;
                    }
                    window.location.assign(url);
                }
            } catch (err) {
                if (!alive.current) return;
                setPaymentError(err.response?.status === 400 ? getErrorMessage(err)
                    : `${getErrorMessage(err)}. Không tự thanh toán lại khi chưa rõ kết quả.`);
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

    return <section className="pos-payment" aria-label="Thanh toán">
        <div className="pos-payment-heading"><h3>Thanh toán</h3><span className="pos-sandbox-label">VNPay Sandbox · TEST</span></div>
        {methodsError && <p role="alert" className="pos-payment-error">Không tải được phương thức: {methodsError}</p>}
        {(paymentError || error) && <p role="alert" className="pos-payment-error">{paymentError || error}</p>}
        {!overview && !error && <p role="status">Đang kiểm tra thanh toán…</p>}
        {success ? <div className="pos-payment-state" role="status"><strong>Đã thanh toán</strong>
            <SaleCompletionStatus key={sale.saleId} saleId={sale.saleId} blocked={success.reconciliationRequired} onCompleted={onCompleted} />
            {success.changeAmount != null && <p>Tiền thừa: <strong>{money(success.changeAmount)}</strong></p>}
            {success.reconciliationRequired && <p>Có xung đột thanh toán đến muộn. Cần đối soát thủ công, không thu thêm tiền.</p>}
        </div> : latest && <div className="pos-payment-state" role="status">
            <strong>{latest.status === 'PENDING' ? 'Đang chờ VNPay xác nhận' : latest.reconciliationRequired ? 'Cần đối soát kết quả thanh toán'
                : latest.status === 'TIMEOUT' ? 'Giao dịch đã hết hạn' : 'Thanh toán không thành công'}</strong>
            <p>{latest.reconciliationRequired ? 'Chưa rõ kết quả thu tiền. Đơn tạm khóa để tránh thanh toán hai lần.'
                : overview.locked ? 'Chưa thể thay đổi đơn hoặc thanh toán lại.' : 'Đơn vẫn được giữ. Có thể chọn phương thức và thử lại.'}</p>
            {sandboxUrl(latest.paymentUrl) && <a className="pos-gateway-link" href={sandboxUrl(latest.paymentUrl)}><ExternalLink size={14} /> Tiếp tục tại VNPay Sandbox</a>}
        </div>}
        {!success && <form onSubmit={submit}>
            <div className="pos-method-grid" role="group" aria-label="Chọn phương thức thanh toán">
                {options.map((option) => {
                    const value = `${option.method}:${option.bankCode ?? ''}`;
                    const Icon = methodIcons[option.method] ?? CreditCard;
                    return <button key={value} type="button" aria-pressed={selection === value}
                        disabled={disabled || !option.enabled} className="pos-method-option" onClick={() => setSelection(value)}>
                        <Icon size={19} /><span>{option.label}<small>{option.enabled ? option.method === 'CASH' ? 'Tại quầy' : 'Qua VNPay Sandbox'
                            : option.method === 'EWALLET' ? 'Chưa có provider' : 'Chưa được bật trên BE'}</small></span>
                    </button>;
                })}
            </div>
            {methodsLoading && <p role="status" className="pos-hint">Đang tải phương thức…</p>}
            {cashSelected && <>
                <label className="pos-cash-received">Tiền khách đưa (VND)<input className="pos-cash-received-input" type="number" inputMode="decimal" placeholder="0" required min="0" max="9999999999999.99" step="0.01"
                    value={received} disabled={disabled} onChange={(event) => setReceived(event.target.value)} />
                    {received !== '' && Number.isFinite(Number(received)) && Number(received) >= 0 &&
                        <span className="pos-cash-received-formatted">{money(Number(received))}</span>}
                </label>
                <div className={`pos-cash-preview ${cashPreview.result && !cashPreview.result.sufficient ? 'pos-cash-preview--short' : ''}`}
                    role="status" aria-live="polite">
                    <div><span>Tổng cần thu</span><strong>{money(sale.totalAmount)}</strong></div>
                    {cashPreview.status === 'empty' && <p>Nhập tiền khách đưa để xem tiền trả lại.</p>}
                    {cashPreview.status === 'loading' && <p>Đang tính tiền trả lại…</p>}
                    {cashPreview.error && <p>{cashPreview.error}</p>}
                    {cashPreview.result && <div><span>{cashPreview.result.sufficient ? 'Tiền trả lại' : 'Còn thiếu'}</span>
                        <strong>{money(cashPreview.result.sufficient ? cashPreview.result.changeAmount : cashPreview.result.shortfallAmount)}</strong></div>}
                    {cashPreview.status === 'error' && <button type="button" disabled={busy} onClick={() => setVersion((value) => value + 1)}>Tính lại</button>}
                </div>
            </>}
            {!cashSelected && selected?.enabled && <div className="pos-electronic-info"><ExternalLink size={15} />
                <p>Bạn sẽ chuyển sang VNPay Sandbox để thanh toán {money(sale.totalAmount)} bằng {selected.label.toLowerCase()}.
                    Khi quay lại, hệ thống đọc kết quả từ máy chủ; đơn được khóa trong lúc chờ xác nhận.</p></div>}
            <button className="pos-primary" disabled={disabled || !selected?.enabled || Number(sale.totalAmount) <= 0 || !sale.items.length || cashSelected && !canConfirmCash}>
                {cashSelected ? 'Xác nhận tiền mặt' : 'Thanh toán qua VNPay'} · {money(sale.totalAmount)}</button>
        </form>}
        <div className="pos-payment-actions"><button disabled={busy} onClick={() => setVersion((value) => value + 1)}>Tải lại trạng thái</button>
            <button type="button" disabled={busy || methodsLoading} onClick={() => { setMethodsLoading(true); setMethodsVersion((value) => value + 1); }}>
                <RefreshCw size={12} /> Tải phương thức</button>
            {latest && latest.paymentMethod !== 'CASH' && (latest.reconciliationRequired || latest.status === 'PENDING') &&
                <button disabled={busy} onClick={() => runAction(async () => {
                    try { await PaymentService.reconcile(latest.paymentId); }
                    catch (err) { setError(getErrorMessage(err)); }
                    finally { setVersion((value) => value + 1); }
                })}>Đối soát VNPay</button>}</div>
    </section>;
}
