import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Check, CircleCheck, ReceiptText, Printer, Download, ShoppingCart, CreditCard, UserRound, RefreshCw } from 'lucide-react';
import SaleService from '../../services/saleService';
import { getErrorMessage } from '../../utils/errorUtils';
import './InvoicePage.css';

const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value ?? 0)} ₫`;
const paymentNames = { CASH: 'Tiền mặt', CARD: 'Thẻ / ngân hàng qua VNPay', QR: 'Mã QR qua VNPay', EWALLET: 'Ví điện tử' };
const dateTime = (value) => value ? value.replace('T', ' ').replace(/\.\d+$/, '') : '—';

export default function InvoicePage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [snapshot, setSnapshot] = useState(null);
    const [failure, setFailure] = useState(null);
    const [busy, setBusy] = useState(false);
    const [revision, setRevision] = useState(0);
    const alive = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        let active = true;
        SaleService.invoice(id).then(({ data }) => { if (active) { setSnapshot({ id, invoice: data.data }); setFailure(null); } })
            .catch((err) => { if (active) setFailure({ id, message: getErrorMessage(err) }); });
        return () => { active = false; };
    }, [id, revision]);
    const invoice = snapshot?.id === id ? snapshot.invoice : null;
    const error = failure?.id === id ? failure.message : '';
    const nextSale = () => {
        sessionStorage.removeItem('sms.pos.currentSaleId');
        navigate('/sales/pos');
    };
    useEffect(() => {
        const shortcuts = (event) => {
            if (event.target instanceof HTMLElement && (event.target.matches('input, textarea, select') || event.target.isContentEditable)) return;
            if (event.key === 'F10' && invoice) { event.preventDefault(); window.print(); }
            if (event.key === 'F2') { event.preventDefault(); sessionStorage.removeItem('sms.pos.currentSaleId'); navigate('/sales/pos'); }
        };
        window.addEventListener('keydown', shortcuts);
        return () => window.removeEventListener('keydown', shortcuts);
    }, [invoice, navigate]);
    const download = async () => {
        if (!invoice || busy) return;
        setBusy(true); setFailure(null);
        try {
            const { data } = await SaleService.invoicePdf(id);
            const url = URL.createObjectURL(data);
            const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${invoice.invoiceNumber}.pdf`;
            document.body.appendChild(anchor); anchor.click(); anchor.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (err) {
            let message = getErrorMessage(err);
            if (err.response?.data instanceof Blob) {
                try { const body = JSON.parse(await err.response.data.text()); message = body.message || message; } catch { /* Keep fallback. */ }
            }
            if (alive.current) setFailure({ id, message });
        }
        finally { if (alive.current) setBusy(false); }
    };
    const quantity = invoice?.items.reduce((sum, item) => sum + Number(item.quantity), 0) ?? 0;
    return <section className="invoice-page">
        <nav className="invoice-steps" aria-label="Tiến trình bán hàng">
            {['Quét mã & Giỏ hàng', 'Chọn phương thức', 'Xử lý thanh toán', 'Kết quả & Hóa đơn'].map((label, index) =>
                <div key={label} className={index === 3 ? 'is-current' : ''}><span>{index === 3 ? '4' : <Check size={14} />}</span><div>Bước {index + 1}: {label}<small>{index === 3 ? 'UC30 · Hoàn tất đơn' : 'Đã hoàn thành'}</small></div></div>)}
        </nav>
        {error && <div className="invoice-error" role="alert"><p>{error}</p>
            {!invoice && <p>Hóa đơn chỉ có sau khi đơn hoàn tất và cập nhật kho. Payment SUCCESS vẫn được giữ.</p>}
            <button onClick={() => setRevision((value) => value + 1)}><RefreshCw size={15} /> Thử tải lại</button>
            <Link to="/sales/pos">Quay lại quầy để kiểm tra đơn</Link></div>}
        {!invoice && !error && <p role="status">Đang tải hóa đơn đã phát hành…</p>}
        {invoice && <>
            <div className="invoice-workspace">
                <div className="invoice-summary">
                    <section className="invoice-success">
                        <CircleCheck className="invoice-success-icon" size={40} />
                        <div><span className="invoice-success-badge">GIAO DỊCH THÀNH CÔNG</span><h1>Thanh toán hoàn tất & Hóa đơn đã lập!</h1>
                            <p>Đơn {invoice.saleNumber} đã hoàn tất và cập nhật tồn kho.</p></div>
                        <div className="invoice-paid"><small>TỔNG TIỀN THANH TOÁN</small><strong>{money(invoice.totalAmount)}</strong><span>Đã ghi nhận thanh toán</span></div>
                    </section>
                    <div className="invoice-info-grid">
                        <section className="invoice-info-card"><ReceiptText size={18} /><small>HÓA ĐƠN BÁN LẺ</small><strong>{invoice.invoiceNumber}</strong><p>{dateTime(invoice.completedAt)}</p><span className="invoice-tag">Đã phát hành</span></section>
                        <section className="invoice-info-card"><CreditCard size={18} /><small>PHƯƠNG THỨC THANH TOÁN</small><strong>{paymentNames[invoice.paymentMethod] ?? invoice.paymentMethod}</strong><p>Mã tham chiếu: {invoice.transactionReference || '—'}</p><span className="invoice-tag">Đã nhận đủ {money(invoice.totalAmount)}</span></section>
                        <section className="invoice-info-card"><UserRound size={18} /><small>THÔNG TIN QUẦY</small><strong>Thu ngân: {invoice.cashier}</strong><p>Đơn hàng #{invoice.saleId}</p><span className="invoice-tag">Tồn kho đã cập nhật</span></section>
                    </div>
                    <section className="invoice-detail-card">
                        <header><h2>Chi tiết hạch toán đơn hàng <span>{invoice.items.length} dòng hàng · {new Intl.NumberFormat('vi-VN').format(quantity)} sản phẩm</span></h2><small><CircleCheck size={13} /> Đã trừ kho theo FEFO</small></header>
                        <div className="invoice-table-scroll"><table className="invoice-items"><thead><tr><th>SẢN PHẨM / SKU</th><th>ĐVT</th><th>ĐƠN GIÁ</th><th>SL</th><th>GIẢM GIÁ</th><th>THÀNH TIỀN</th></tr></thead>
                            <tbody>{invoice.items.map((item) => <tr key={item.saleItemId}><td><strong>{item.productName}</strong><small>{item.productCode}</small></td><td>{item.unit}</td><td>{money(item.unitPrice)}</td><td>{item.quantity}</td><td>{money(item.discountAmount)}</td><td><strong>{money(Number(item.subtotal) - Number(item.discountAmount))}</strong></td></tr>)}</tbody></table></div>
                        <div className="invoice-totals"><p><span>Tiền hàng (Subtotal)</span><strong>{money(invoice.subtotal)}</strong></p><p><span>Giảm giá</span><strong>{money(invoice.discountAmount)}</strong></p><p className="invoice-total-final"><span>Tổng thanh toán</span><strong>{money(invoice.totalAmount)}</strong></p>
                            {invoice.receivedAmount != null && <><p><span>Tiền khách đưa</span><strong>{money(invoice.receivedAmount)}</strong></p><p><span>Tiền trả lại</span><strong>{money(invoice.changeAmount)}</strong></p></>}
                        </div>
                        <p className="invoice-internal-note">Chứng từ bán hàng nội bộ. Không phải hóa đơn thuế GTGT.</p>
                    </section>
                </div>
                <aside className="invoice-preview">
                    <header><h2><ReceiptText size={18} /> Bản in hóa đơn nhiệt (80mm)</h2><span>Xem trước</span></header>
                    <p className="invoice-preview-hint">Nội dung từ hóa đơn đã lưu. Chọn máy in và khổ giấy 80mm trong hộp thoại in.</p>
                    <article className="invoice-document">
                        <header><strong className="receipt-store">{invoice.storeName}</strong><h2>HÓA ĐƠN BÁN LẺ</h2><strong>{invoice.invoiceNumber}</strong><p>{dateTime(invoice.completedAt)}</p><p>Thu ngân: {invoice.cashier}</p><p>Đơn: {invoice.saleNumber}</p></header>
                        <table className="receipt-items"><thead><tr><th>Tên sản phẩm</th><th>Thành tiền</th></tr></thead><tbody>{invoice.items.map((item, index) => <tr key={item.saleItemId}><td>{index + 1}. {item.productName}<small>{item.quantity} {item.unit} × {money(item.unitPrice)}</small>{Number(item.discountAmount) > 0 && <small>Giảm: {money(item.discountAmount)}</small>}</td><td>{money(Number(item.subtotal) - Number(item.discountAmount))}</td></tr>)}</tbody></table>
                        <div className="receipt-totals"><p><span>Tiền hàng</span><span>{money(invoice.subtotal)}</span></p><p><span>Giảm giá</span><span>{money(invoice.discountAmount)}</span></p><p className="receipt-final"><strong>THANH TOÁN</strong><strong>{money(invoice.totalAmount)}</strong></p><p><span>Hình thức</span><span>{paymentNames[invoice.paymentMethod] ?? invoice.paymentMethod}</span></p>
                            {invoice.receivedAmount != null && <><p><span>Khách đưa</span><span>{money(invoice.receivedAmount)}</span></p><p><span>Tiền trả lại</span><span>{money(invoice.changeAmount)}</span></p></>}
                        </div><footer><p>Cảm ơn Quý khách và hẹn gặp lại!</p><p>Chứng từ bán hàng nội bộ.<br />Không phải hóa đơn thuế GTGT.</p></footer>
                    </article>
                    <div className="invoice-preview-footer"><span>In theo lựa chọn của thu ngân</span><button onClick={() => window.print()}><Printer size={13} /> In lại</button></div>
                </aside>
            </div>
            <nav className="invoice-actions" aria-label="Thao tác hóa đơn">
                <button className="invoice-next" onClick={nextSale}><ShoppingCart size={20} /><span>TẠO ĐƠN HÀNG MỚI<small>Bắt đầu lượt bán tiếp theo</small></span><kbd>F2</kbd></button>
                <button onClick={() => window.print()}><Printer size={18} /> In hóa đơn <kbd>F10</kbd></button>
                <button disabled={busy} onClick={download}><Download size={18} /> {busy ? 'Đang xuất…' : 'Xuất PDF'}</button>
            </nav>
        </>}
    </section>;
}
