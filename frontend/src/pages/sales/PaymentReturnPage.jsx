import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Clock3, AlertCircle, ArrowLeft, RefreshCw } from 'lucide-react';
import PaymentService from '../../services/paymentService';
import SaleCompletionStatus from './SaleCompletionStatus';
import { getErrorMessage } from '../../utils/errorUtils';
import './POSCheckout.css';

export default function PaymentReturnPage() {
    const [params] = useSearchParams();
    const ref = params.get('vnp_TxnRef');
    const valid = Boolean(ref && /^[A-Za-z0-9_-]{1,100}$/.test(ref) && params.getAll('vnp_TxnRef').length === 1);
    const [snapshot, setSnapshot] = useState(null);
    const [failure, setFailure] = useState(null);
    const [polling, setPolling] = useState(false);
    const [version, setVersion] = useState(0);
    useEffect(() => {
        let active = true;
        if (!valid) return;
        const controller = new AbortController();
        let timer;
        let reads = 0;
        async function load() {
            reads += 1;
            try {
                const { data } = await PaymentService.byReference(ref, controller.signal);
                if (!active) return;
                const payment = data.data;
                setSnapshot({ ref, payment }); setFailure(null);
                sessionStorage.setItem('sms.pos.currentSaleId', String(payment.saleId));
                const waiting = payment.status === 'PENDING' || payment.reconciliationRequired;
                if (waiting && reads < 12) {
                    setPolling(true); timer = setTimeout(load, 3000);
                } else setPolling(false);
            } catch (err) {
                if (!active || controller.signal.aborted) return;
                setFailure({ ref, message: getErrorMessage(err) });
                if (!err.response && reads < 12) {
                    setPolling(true); timer = setTimeout(load, 3000);
                } else setPolling(false);
            }
        }
        load();
        return () => { active = false; clearTimeout(timer); controller.abort(); };
    }, [ref, valid, version]);
    const result = valid && snapshot?.ref === ref ? snapshot.payment : null;
    const error = valid && failure?.ref === ref ? failure.message : '';
    const success = result?.status === 'SUCCESS';
    const unresolved = result?.reconciliationRequired;
    const waiting = !result || result.status === 'PENDING' || unresolved;
    const Icon = unresolved ? AlertCircle : success ? CheckCircle2 : waiting ? Clock3 : AlertCircle;
    const title = unresolved ? 'Cần đối soát kết quả' : success ? 'Thanh toán thành công' : waiting ? 'Đang chờ xác nhận thanh toán' : 'Thanh toán chưa thành công';
    return <section className="pos-page pos-return-page">
        <div className={`pos-panel pos-return-card ${success && !unresolved ? 'pos-return-card--success' : ''}`}>
        <span className="pos-sandbox-label">VNPay Sandbox · TEST</span>
        <Icon size={44} className="pos-return-icon" />
        <h1>{valid ? title : 'Không tìm thấy mã giao dịch hợp lệ'}</h1>
        <p className="pos-hint">Kết quả lấy từ máy chủ. Dữ liệu trả về trên thanh địa chỉ không xác nhận thanh toán.</p>
        {!valid && <p role="alert">Thiếu mã tham chiếu hợp lệ.</p>}
        {error && <p role="alert" className="pos-payment-error">Không đọc được trạng thái: {error}. Không thanh toán lại khi chưa rõ kết quả.</p>}
        {valid && !result && !error && <p role="status">Đang đọc trạng thái…</p>}
        {result && <>
            <dl className="pos-return-details"><div><dt>Đơn bán hàng</dt><dd>#{result.saleId}</dd></div>
                <div><dt>Số tiền</dt><dd>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(result.amount)} ₫</dd></div>
                <div><dt>Mã tham chiếu</dt><dd>{result.txnRef}</dd></div>
                {result.gatewayTransactionId && <div><dt>Mã giao dịch VNPay</dt><dd>{result.gatewayTransactionId}</dd></div>}
            </dl>
            <p role="status">{unresolved ? 'Chưa thể cho phép thanh toán lại. Quay về POS để đối soát, không thu thêm tiền.'
                : success ? 'Máy chủ đã xác nhận thu tiền. Xem trạng thái hoàn tất đơn bên dưới.'
                    : result.status === 'PENDING' ? 'VNPay có thể chưa gửi IPN. Hệ thống đang chờ trạng thái đã xác minh tại BE.'
                        : 'Đơn và lịch sử thanh toán được giữ lại. Quay về POS để kiểm tra và chọn thử lại.'}</p>
            {success && <SaleCompletionStatus key={result.saleId} saleId={result.saleId} blocked={unresolved} />}
            {waiting && <p className="pos-hint">{polling ? 'Tự kiểm tra lại mỗi 3 giây…' : 'Chưa có kết quả cuối. Bạn có thể tải lại hoặc đối soát tại POS.'}</p>}
        </>}
        <div className="pos-return-actions"><button onClick={() => setVersion((value) => value + 1)} disabled={!valid}><RefreshCw size={15} /> Tải lại trạng thái</button>
            <Link className="pos-return-link" to="/sales/pos"><ArrowLeft size={16} /> Quay lại quầy bán hàng</Link></div>
        </div>
    </section>;
}
