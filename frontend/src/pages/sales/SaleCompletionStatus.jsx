import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import SaleService from '../../services/saleService';
import { getErrorMessage } from '../../utils/errorUtils';
import { getPOSErrorMessage } from './posErrorMessage';

export default function SaleCompletionStatus({ saleId, blocked = false, onCompleted, english = false }) {
    const navigate = useNavigate();
    const [snapshot, setSnapshot] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const alive = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    const callback = useRef(onCompleted);
    useEffect(() => { callback.current = onCompleted; }, [onCompleted]);
    useEffect(() => {
        let active = true;
        let timer;
        const load = async () => {
            try {
                const { data } = await SaleService.getById(saleId);
                if (!active) return;
                setSnapshot(data.data);
                if (data.data.status === 'COMPLETED') {
                    callback.current?.(data.data);
                    if (!blocked) navigate(`/sales/${saleId}/invoice`, { replace: true, state: { fromCheckout: true } });
                }
                if (data.data.status !== 'COMPLETED') timer = setTimeout(load, 3000);
            } catch (err) {
                if (active) { setError(english ? getPOSErrorMessage(err) : getErrorMessage(err)); timer = setTimeout(load, 5000); }
            }
        };
        load();
        return () => { active = false; clearTimeout(timer); };
    }, [saleId, blocked, navigate, english]);
    const complete = async () => {
        setBusy(true); setError('');
        try {
            const { data } = await SaleService.complete(saleId);
            if (alive.current) {
                setSnapshot(data.data); callback.current?.(data.data);
                if (!blocked) navigate(`/sales/${saleId}/invoice`, { replace: true, state: { fromCheckout: true } });
            }
        }
        catch (err) { if (alive.current) setError(english ? getPOSErrorMessage(err) : getErrorMessage(err)); }
        finally { if (alive.current) setBusy(false); }
    };
    const done = snapshot?.saleId === saleId && snapshot.status === 'COMPLETED';
    return <div className="pos-completion" role="status">
        {done ? <><p>{english ? 'Sale completed and inventory updated.' : 'Đơn đã hoàn tất, tồn kho đã cập nhật.'}</p>
            <Link className="pos-gateway-link" to={`/sales/${saleId}/invoice`} state={{ fromCheckout: true }}>{english ? 'View, Print or Download Invoice PDF' : 'Xem, in hoặc tải hóa đơn PDF'}</Link></>
            : <><p>{blocked ? (english ? 'Reconcile the payment conflict before completing this sale.' : 'Cần đối soát xung đột thanh toán trước khi hoàn tất.') : (english ? 'Payment received. Waiting for sale completion and invoice generation. Do not collect payment again.' : 'Đã thu tiền. Đang chờ hoàn tất đơn và tạo hóa đơn; không thu tiền lần nữa.')}</p>
                <button type="button" disabled={busy || blocked} onClick={complete}>{busy ? (english ? 'Completing…' : 'Đang hoàn tất…') : (english ? 'Complete / Retry After Resolving Stock Issues' : 'Hoàn tất / thử lại sau khi xử lý kho')}</button>
                <p className="pos-hint">{english ? 'Invoice printing and PDF download will be available once the sale is completed. Successful payment does not mean an invoice has been issued yet.' : 'In hóa đơn và xuất PDF sẽ được mở khi đơn hoàn tất. Thanh toán thành công chưa đồng nghĩa hóa đơn đã được lập.'}</p></>}
        {error && !done && <p className="pos-payment-error" role="alert">{error}</p>}
    </div>;
}
