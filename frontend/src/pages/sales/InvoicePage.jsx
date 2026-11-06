import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CircleCheck, Store, Printer, Download, ShoppingCart, ReceiptText, RefreshCw, ShieldCheck } from 'lucide-react';
import SaleService from '../../services/saleService';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { invoiceMoney, invoiceDateTime, lineNetAmount, invoiceQuantity } from '../../utils/invoiceFormat';
import './InvoicePage.css';

const paymentNames = { CASH: 'Tiền mặt', CARD: 'Thẻ / ngân hàng qua VNPay', QR: 'Mã QR qua VNPay', EWALLET: 'Ví điện tử' };
const money = invoiceMoney;

function readError(error) {
    const status = error.response?.status;
    if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 404) return 'Không tìm thấy hóa đơn hoặc đơn bán này.';
    if (status === 400) return error.response?.data?.message || 'Hóa đơn chưa được phát hành cho đơn bán này.';
    return getAccessAwareErrorMessage(error);
}

function ThermalReceipt({ invoice }) {
    return <article className="invoice-document">
        <header><strong className="receipt-store">{invoice.storeName}</strong><h2>HÓA ĐƠN BÁN LẺ</h2><strong>{invoice.invoiceNumber}</strong><p>{invoiceDateTime(invoice.completedAt)}</p><p>Thu ngân: {invoice.cashier}</p><p>Đơn: {invoice.saleNumber}</p></header>
        <table className="receipt-items"><thead><tr><th>Tên sản phẩm</th><th>Thành tiền</th></tr></thead><tbody>{invoice.items.map((item, index) => <tr key={item.saleItemId}><td>{index + 1}. {item.productName}<small>{invoiceQuantity(item.quantity)} {item.unit} × {money(item.unitPrice)}</small>{Number(item.discountAmount) > 0 && <small>Giảm: {money(item.discountAmount)}</small>}</td><td>{money(lineNetAmount(item))}</td></tr>)}</tbody></table>
        <div className="receipt-totals"><p><span>Tiền hàng</span><span>{money(invoice.subtotal)}</span></p><p><span>Giảm giá</span><span>{money(invoice.discountAmount)}</span></p><p className="receipt-final"><strong>THANH TOÁN</strong><strong>{money(invoice.totalAmount)}</strong></p><p><span>Hình thức</span><span>{paymentNames[invoice.paymentMethod] ?? invoice.paymentMethod}</span></p>
            {invoice.receivedAmount != null && <p><span>Khách đưa</span><span>{money(invoice.receivedAmount)}</span></p>}
            {invoice.changeAmount != null && <p><span>Tiền trả lại</span><span>{money(invoice.changeAmount)}</span></p>}
        </div><footer><p>Cảm ơn Quý khách và hẹn gặp lại!</p><p>Chứng từ bán hàng nội bộ.<br />Không phải hóa đơn thuế GTGT.</p></footer>
    </article>;
}

export default function InvoicePage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { state } = useLocation();
    const checkout = state?.fromCheckout === true;
    const [revision, setRevision] = useState(0);
    const [loaded, setLoaded] = useState(null);
    const [downloadState, setDownloadState] = useState(null);
    const currentId = useRef(id);
    useEffect(() => { currentId.current = id; return () => { currentId.current = null; }; }, [id]);
    useEffect(() => {
        let active = true;
        SaleService.invoice(id).then(({ data }) => {
            if (active) setLoaded({ id, revision, invoice: data.data });
        }).catch(error => {
            if (active) setLoaded({ id, revision, error: readError(error), status: error.response?.status });
        });
        return () => { active = false; };
    }, [id, revision]);
    const result = loaded?.id === id && loaded.revision === revision ? loaded : null;
    const invoice = result?.invoice;
    const busy = downloadState?.id === id && downloadState.busy;
    const downloadError = downloadState?.id === id ? downloadState.error : '';
    const nextSale = () => {
        sessionStorage.removeItem('sms.pos.currentSaleId');
        navigate('/sales/pos');
    };
    useEffect(() => {
        const shortcuts = event => {
            if (event.target instanceof HTMLElement && (event.target.matches('input, textarea, select') || event.target.isContentEditable)) return;
            if (event.key === 'F10' && invoice) { event.preventDefault(); window.print(); }
            if (event.key === 'F2' && checkout && invoice) {
                event.preventDefault(); sessionStorage.removeItem('sms.pos.currentSaleId'); navigate('/sales/pos');
            }
        };
        window.addEventListener('keydown', shortcuts);
        return () => window.removeEventListener('keydown', shortcuts);
    }, [invoice, checkout, navigate]);
    const download = async () => {
        if (!invoice || busy) return;
        setDownloadState({ id, busy: true, error: '' });
        try {
            const { data } = await SaleService.invoicePdf(id);
            if (currentId.current !== id) return;
            const url = URL.createObjectURL(data);
            try {
                const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${invoice.invoiceNumber}.pdf`;
                document.body.appendChild(anchor); anchor.click(); anchor.remove();
            } finally { setTimeout(() => URL.revokeObjectURL(url), 1000); }
            setDownloadState({ id, busy: false, error: '' });
        } catch (error) {
            if (error.response?.data instanceof Blob) {
                try { error.response.data = JSON.parse(await error.response.data.text()); } catch { /* Use status-aware fallback. */ }
            }
            if (currentId.current === id) setDownloadState({ id, busy: false, error: readError(error) });
        }
    };

    return <section className="invoice-detail-page">
        <header className="invoice-detail-toolbar">
            <div><Link to="/invoices" className="invoice-back"><ArrowLeft size={16} />Danh sách hóa đơn</Link><h1>Chi tiết hóa đơn</h1><p>Thông tin giao dịch và chứng từ bán hàng đã phát hành.</p></div>
            {invoice && <div className="invoice-detail-actions"><button onClick={() => window.print()}><Printer size={16} />In 80mm</button><button className="is-primary" disabled={busy} onClick={download}><Download size={16} />{busy ? 'Đang tải…' : 'Tải PDF'}</button></div>}
        </header>
        {checkout && invoice && <div className="invoice-checkout-banner"><CircleCheck size={20} /><div><strong>Thanh toán hoàn tất</strong><span>Hóa đơn đã được phát hành và tồn kho đã cập nhật.</span></div><button onClick={nextSale}><ShoppingCart size={16} />Đơn mới <kbd>F2</kbd></button></div>}
        {!result && <div className="invoice-detail-state" role="status"><ReceiptText size={28} /><h2>Đang tải hóa đơn…</h2><p>Vui lòng chờ trong giây lát.</p></div>}
        {result?.error && <div className="invoice-detail-state invoice-detail-error" role="alert"><ReceiptText size={28} /><h2>Không thể mở hóa đơn</h2><p>{result.error}</p><div>{result.status === 401 ? <Link to="/login">Đăng nhập lại</Link> : <button onClick={() => setRevision(value => value + 1)}><RefreshCw size={16} />Thử lại</button>}<Link to="/invoices">Về danh sách hóa đơn</Link></div></div>}
        {downloadError && <div className="invoice-download-error" role="alert">Không thể tải PDF: {downloadError}</div>}
        {invoice && <>
            <article className="invoice-sheet">
                <header className="invoice-sheet-header">
                    <div className="invoice-store"><span className="invoice-store-icon"><Store size={25} /></span><div><h2>{invoice.storeName}</h2><p>Chứng từ bán hàng nội bộ</p><span>Cảm ơn Quý khách đã mua sắm!</span></div></div>
                    <div className="invoice-sheet-number"><span>HÓA ĐƠN BÁN LẺ</span><h2>{invoice.invoiceNumber}</h2><p>Ngày lập: {invoiceDateTime(invoice.completedAt)}</p></div>
                </header>
                <section className="invoice-metadata" aria-label="Thông tin giao dịch">
                    <div><h3>THÔNG TIN HÓA ĐƠN</h3><dl><div><dt>Mã đơn bán</dt><dd>{invoice.saleNumber}</dd></div><div><dt>Hình thức thanh toán</dt><dd>{paymentNames[invoice.paymentMethod] ?? invoice.paymentMethod}</dd></div>{invoice.transactionReference && <div><dt>Mã tham chiếu</dt><dd>{invoice.transactionReference}</dd></div>}</dl></div>
                    <div><h3>THÔNG TIN GIAO DỊCH</h3><dl><div><dt>Thu ngân lập hóa đơn</dt><dd>{invoice.cashier}</dd></div><div><dt>Thời gian giao dịch</dt><dd>{invoiceDateTime(invoice.completedAt)}</dd></div><div><dt>Trạng thái</dt><dd className="invoice-settled"><CircleCheck size={13} />Đã hoàn tất</dd></div></dl></div>
                </section>
                <div className="invoice-sheet-table-scroll"><table className="invoice-sheet-items"><thead><tr><th>STT</th><th>Tên hàng hóa & mã SKU</th><th>ĐVT</th><th>Số lượng</th><th>Đơn giá</th><th>Giảm giá</th><th>Thành tiền</th></tr></thead><tbody>{invoice.items.map((item, index) => <tr key={item.saleItemId}><td>{index + 1}</td><td><strong>{item.productName}</strong><small>SKU: {item.productCode}</small></td><td>{item.unit}</td><td>{invoiceQuantity(item.quantity)}</td><td>{money(item.unitPrice)}</td><td className={Number(item.discountAmount) > 0 ? 'has-discount' : ''}>{Number(item.discountAmount) > 0 ? '−' : ''}{money(item.discountAmount)}</td><td><strong>{money(lineNetAmount(item))}</strong></td></tr>)}</tbody></table></div>
                <footer className="invoice-sheet-footer">
                    <div className="invoice-sheet-note"><span><ShieldCheck size={18} />Chứng từ đã phát hành</span><p>Thông tin được lưu tại thời điểm hoàn tất giao dịch.</p><p>Chứng từ bán hàng nội bộ.<br />Không phải hóa đơn thuế GTGT.</p></div>
                    <div className="invoice-sheet-totals"><p><span>Tổng tiền hàng</span><strong>{money(invoice.subtotal)}</strong></p><p className="has-discount"><span>Tổng giảm giá</span><strong>{Number(invoice.discountAmount) > 0 ? '−' : ''}{money(invoice.discountAmount)}</strong></p><p className="invoice-sheet-grand-total"><span>TỔNG THANH TOÁN</span><strong>{money(invoice.totalAmount)}</strong></p>
                        {invoice.receivedAmount != null && <p><span>Khách thanh toán</span><strong>{money(invoice.receivedAmount)}</strong></p>}
                        {invoice.changeAmount != null && <p className="invoice-sheet-change"><span>Tiền trả lại khách</span><strong>{money(invoice.changeAmount)}</strong></p>}
                    </div>
                </footer>
            </article>
            <div className="invoice-print-only"><ThermalReceipt invoice={invoice} /></div>
        </>}
    </section>;
}
