import { useEffect, useRef, useState } from 'react';
import { ScanLine, ShoppingCart, Trash2, Store, Search, Plus, RefreshCw, ClipboardList, ShieldCheck } from 'lucide-react';
import SaleService from '../../services/saleService';
import UserService from '../../services/userService';
import PaymentPanel from './PaymentPanel';
import { getErrorMessage } from '../../utils/errorUtils';
import './POSCheckout.css';

/**
 * POS integration for UC24/UC25/UC26. Payments are handled by later use cases.
 */
const storageKey = 'sms.pos.currentSaleId';
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Number(value ?? 0))} ₫`;

function QuantityEditor({ item, disabled, onSave }) {
    // Keep the editor mounted; a new server quantity supersedes the old local draft.
    const [draft, setDraft] = useState({ base: item.quantity, value: String(item.quantity) });
    if (draft.base !== item.quantity) {
        setDraft({ base: item.quantity, value: String(item.quantity) });
    }
    const quantity = draft.base === item.quantity ? draft.value : String(item.quantity);
    const value = Number(quantity);
    const valid = quantity !== '' && Number.isFinite(value) && value >= 0.001
        && value <= 999999999.999 && Math.abs(value * 1000 - Math.round(value * 1000)) < 0.0001;
    function adjustQuantity(delta) {
        if (disabled || !valid) return;
        const next = (Math.round(value * 1000) + delta * 1000) / 1000;
        if (next < 0.001 || next > 999999999.999) return;
        onSave(item.saleItemId, next);
    }
    return <form className="pos-quantity" onSubmit={(event) => {
        event.preventDefault();
        if (Number(quantity) !== Number(item.quantity)) onSave(item.saleItemId, quantity);
    }}>
        <button type="button" className="pos-quantity-step" aria-label={`Decrease quantity for ${item.productName}`}
            title="Decrease by 1" disabled={disabled || !valid || value < 1.001}
            onClick={() => adjustQuantity(-1)}>−</button>
        <input aria-label={`Quantity for ${item.productName}`} type="number" min="0.001" max="999999999.999"
            step="0.001" required value={quantity} disabled={disabled}
            onChange={(event) => setDraft({ base: item.quantity, value: event.target.value })} />
        <button type="button" className="pos-quantity-step" aria-label={`Increase quantity for ${item.productName}`}
            title="Increase by 1" disabled={disabled || !valid || value > 999999998.999}
            onClick={() => adjustQuantity(1)}>+</button>
        <button className={`pos-save-quantity ${value === Number(item.quantity) ? 'pos-save-quantity--hidden' : ''}`}
            disabled={disabled || !valid || value === Number(item.quantity)}
            aria-hidden={value === Number(item.quantity)} tabIndex={value === Number(item.quantity) ? -1 : 0}>Lưu</button>
    </form>;
}

function DraftSales({ currentId, disabled, onOpen }) {
    const [drafts, setDrafts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [revision, setRevision] = useState(0);
    const [filter, setFilter] = useState('');
    useEffect(() => {
        let active = true;
        SaleService.getAll({ status: 'DRAFT' }).then(({ data }) => {
            if (active) setDrafts(data.data.filter((draft) => draft.status === 'DRAFT')
                .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.saleId - a.saleId));
        }).catch((err) => { if (active) setError(getErrorMessage(err)); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [revision]);
    const visible = drafts.filter((draft) => `${draft.saleNumber} ${draft.cashierUsername} ${draft.items.map((item) => item.productName).join(' ')}`
        .toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
    return <section className="pos-panel pos-drafts" aria-label="Saved draft sales">
        <div className="pos-drafts-heading"><h2>Đơn đang bán dở <span className="pos-count">{drafts.length}</span></h2>
            <button disabled={disabled || loading} onClick={() => {
                setLoading(true); setError(''); setRevision((value) => value + 1);
            }}>Làm mới</button></div>
        <label>Tìm đơn theo người tạo, sản phẩm hoặc mã đơn<input value={filter}
            placeholder="Ví dụ: tên thu ngân hoặc tên sản phẩm" onChange={(event) => setFilter(event.target.value)} /></label>
        {loading ? <p role="status">Đang tải đơn bán dở…</p> : error ? <p className="pos-error" role="alert">{error}</p> :
            visible.length === 0 ? <p>{drafts.length === 0 ? 'Chưa có đơn đang bán dở.' : 'Không tìm thấy đơn phù hợp.'}</p> :
                <div className="pos-draft-list">{visible.map((draft) => <article className="pos-draft-card" key={draft.saleId}>
                    <div className="pos-drafts-heading"><strong>Đơn #{draft.saleId}</strong>
                        {draft.saleId === currentId && <span className="pos-count">Đang mở</span>}</div>
                    <p>{draft.cashierUsername} · {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(draft.createdAt))}</p>
                    <p className="pos-draft-preview">{draft.items.length ? draft.items.slice(0, 3).map((item) => item.productName).join(', ') : 'Đơn chưa có sản phẩm'}{draft.items.length > 3 ? '…' : ''}</p>
                    <div className="pos-drafts-heading"><span>{draft.items.length} dòng hàng</span><strong>{money(draft.totalAmount)}</strong></div>
                    <button className="pos-primary" disabled={disabled || draft.saleId === currentId}
                        onClick={() => onOpen(draft.saleId)}>Tiếp tục bán</button>
                </article>)}</div>}
    </section>;
}

export default function POSPage() {
    const [sale, setSale] = useState(null);
    const [paymentGate, setPaymentGate] = useState({ saleId: null, locked: true });
    const [users, setUsers] = useState([]);
    const [cashierId, setCashierId] = useState('');
    const [showDrafts, setShowDrafts] = useState(false);
    const [mode, setMode] = useState('barcode');
    const [query, setQuery] = useState('');
    const [results, setResults] = useState(null);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(true);
    const [restoreFailed, setRestoreFailed] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [userError, setUserError] = useState('');
    const lock = useRef(false);
    const scanner = useRef(null);
    const focusScanner = useRef(false);

    useEffect(() => {
        let active = true;
        UserService.getAll({ status: 'ACTIVE' }).then(({ data }) => {
            if (active) setUsers(data.data.filter((user) => user.status === 'ACTIVE'));
        }).catch((err) => { if (active) setUserError(getErrorMessage(err)); });
        const id = sessionStorage.getItem(storageKey);
        const restore = id ? SaleService.getById(id).then(({ data }) => {
            if (active) setSale(data.data);
        }).catch((err) => {
            if (!active) return;
            if (err.response?.status === 404) {
                sessionStorage.removeItem(storageKey);
                sessionStorage.removeItem(`sms.payment.request.${id}`);
                setNotice('Đơn đã lưu không còn tồn tại. Vui lòng tạo đơn mới hoặc chọn đơn đang bán dở.');
            } else {
                setRestoreFailed(true);
                setError(getErrorMessage(err));
            }
        }) : Promise.resolve();
        restore.finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const editable = sale?.status === 'DRAFT' && paymentGate.saleId === sale.saleId && !paymentGate.locked;
    const disabled = busy || loading || restoreFailed;
    useEffect(() => {
        if (!disabled && editable && focusScanner.current) {
            scanner.current?.focus();
            focusScanner.current = false;
        }
        function handleKey(event) {
            if (event.key === 'F2' && editable && !disabled) {
                event.preventDefault(); scanner.current?.focus(); scanner.current?.select();
            }
        }
        window.addEventListener('keydown', handleKey);
        return () => window.removeEventListener('keydown', handleKey);
    }, [disabled, editable]);
    async function perform(action) {
        if (lock.current || loading || restoreFailed) return;
        lock.current = true; setBusy(true); setError(''); setNotice('');
        try { await action(); }
        catch (err) { setError(getErrorMessage(err)); }
        finally { lock.current = false; setBusy(false); }
    }
    function acceptSale(response) { setSale(response.data.data); }
    async function createSale(event) {
        event.preventDefault();
        focusScanner.current = true;
        await perform(async () => {
            const response = await SaleService.create({ cashierId: Number(cashierId) });
            acceptSale(response);
            sessionStorage.setItem(storageKey, String(response.data.data.saleId));
            setResults(null); setQuery(''); setNotice('Sale created. Scan a barcode or search for products.');
        });
    }
    async function search(event) {
        event.preventDefault();
        if (!query.trim() || !editable) return;
        focusScanner.current = true;
        await perform(async () => {
            setResults(null);
            const response = await SaleService.searchProducts({ [mode]: query.trim() });
            const found = response.data.data;
            if (mode === 'barcode' && found.length === 1) {
                acceptSale(await SaleService.addItem(sale.saleId, { productId: found[0].productId, quantity: 1 }));
                setQuery(''); setResults(null); setNotice(`Added ${found[0].productName}.`);
            } else setResults(found);
        });
    }

    const items = sale?.items ?? [];
    const quickItems = [...new Map(items.map((item) => [item.productId, item])).values()].slice(-4);
    const totalQuantity = items.reduce((total, item) => total + Number(item.quantity), 0);
    function addProduct(product) {
        return perform(async () => {
            acceptSale(await SaleService.addItem(sale.saleId, { productId: product.productId, quantity: 1 }));
            setNotice(`Đã thêm ${product.productName}.`);
        });
    }
    function startAnother() {
        if (items.length && !window.confirm('Tạo đơn mới? Đơn hiện tại được giữ trong danh sách đơn đang bán dở.')) return;
        sessionStorage.removeItem(storageKey); setSale(null); setResults(null); setQuery(''); setError('');
        setShowDrafts(false); setNotice('Đơn trước được giữ trong danh sách đơn đang bán dở.');
    }

    return <section className="pos-page">
        <header className="pos-checkout-header">
            <div className="pos-title-line"><span className="pos-brand-icon"><Store size={22} /></span>
                <div><h1>Quầy Thu Ngân Bán Lẻ <span>(POS Checkout)</span></h1>
                    <p>Mã giao dịch: <strong>{sale?.saleNumber ?? 'Chưa tạo đơn'}</strong><span className="pos-meta-dot">•</span>
                        Thu ngân: <strong>{sale?.cashierUsername ?? 'Chưa chọn'}</strong></p></div>
            </div>
            <div className="pos-header-actions">
                <button className="pos-soft-button" disabled={disabled} aria-expanded={showDrafts}
                    onClick={() => setShowDrafts((value) => !value)}><ClipboardList size={15} />
                    {showDrafts ? 'Đóng danh sách đơn' : 'Đơn đang bán dở'}</button>
                {sale && <button className="pos-soft-button" disabled={disabled} onClick={startAnother}><Plus size={15} /> Đơn mới</button>}
                {sale && <button className="pos-icon-button" title="Tải lại đơn từ máy chủ" aria-label="Tải lại đơn" disabled={disabled}
                    onClick={() => perform(async () => {
                        acceptSale(await SaleService.getById(sale.saleId)); setNotice('Đã tải lại đơn bán hàng.');
                    })}><RefreshCw size={15} /></button>}
                <span className="pos-connection-note"><span /> Tổng tiền cập nhật tự động</span>
            </div>
        </header>
        {error && <div className="pos-error" role="alert">{error}</div>}
        {notice && <div className="pos-notice" role="status">{notice}</div>}
        {loading && <p className="pos-loading" role="status">Đang tải đơn bán hàng…</p>}
        {restoreFailed && <button className="pos-soft-button" onClick={() => window.location.reload()}>Thử tải lại đơn</button>}
        {!sale && !loading && !restoreFailed && <form className="pos-panel pos-start" onSubmit={createSale}>
            <div><h2>Bắt đầu đơn bán hàng</h2><p>Chọn người thao tác để tạo đơn mới.</p></div>
            <label>Thu ngân<select value={cashierId} required disabled={disabled} onChange={(event) => setCashierId(event.target.value)}>
                <option value="">Chọn người thao tác</option>
                {users.map((user) => <option key={user.userId} value={user.userId}>{user.employeeFullName || user.username} ({user.username})</option>)}
            </select></label>
            <button className="pos-primary" disabled={disabled || !cashierId}><Plus size={16} /> Tạo đơn mới</button>
            {userError && <p className="pos-start-error" role="alert">Không tải được thu ngân: {userError} <button type="button" onClick={() => window.location.reload()}>Thử lại</button></p>}
            {!userError && users.length === 0 && <p className="pos-start-error">Chưa có tài khoản đang hoạt động để tạo đơn.</p>}
        </form>}
        {!loading && !restoreFailed && (showDrafts || !sale) && <DraftSales key={sale?.saleId ?? 'start'}
            currentId={sale?.saleId} disabled={disabled} onOpen={(id) => perform(async () => {
                const response = await SaleService.getById(id);
                acceptSale(response); sessionStorage.setItem(storageKey, String(response.data.data.saleId));
                setShowDrafts(false); setQuery(''); setResults(null); focusScanner.current = true;
                setNotice('Đã mở lại đơn. Bạn có thể tiếp tục bán hàng.');
            })} />}
        {sale && sale.status !== 'DRAFT' && <div className="pos-readonly">Đơn đã {sale.status === 'COMPLETED' ? 'hoàn tất' : 'hủy'}, không thể sửa mặt hàng.</div>}
        <div className="pos-checkout-grid">
            <div className="pos-workspace">
                <section className="pos-panel pos-finder">
                    <div className="pos-section-heading"><h2><ScanLine size={17} /> Quét mã vạch hoặc nhập tên SKU / Mã hàng hóa</h2><kbd>Focus: [F2]</kbd></div>
                    <form className="pos-search" onSubmit={search}>
                        <ScanLine size={19} className="pos-search-icon" />
                        <input aria-label={mode === 'barcode' ? 'Quét mã vạch' : 'Tìm tên hoặc SKU'} ref={scanner} value={query} required
                            maxLength={mode === 'barcode' ? 100 : 200} placeholder={mode === 'barcode' ? 'Quét mã vạch và nhấn Enter để thêm sản phẩm…' : 'Nhập tên sản phẩm hoặc SKU để tìm kiếm…'}
                            disabled={disabled || !editable} autoComplete="off" onChange={(event) => { setQuery(event.target.value); setResults(null); }} />
                        <button className="pos-primary" disabled={disabled || !editable || !query.trim()}><Search size={15} /> {mode === 'barcode' ? 'Thêm' : 'Tìm'}</button>
                    </form>
                    <div className="pos-search-options"><div className="pos-mode-switch" role="group" aria-label="Cách tìm sản phẩm">
                        <button aria-pressed={mode === 'barcode'} disabled={disabled || !editable} onClick={() => { setMode('barcode'); setQuery(''); setResults(null); scanner.current?.focus(); }}>Mã vạch</button>
                        <button aria-pressed={mode === 'keyword'} disabled={disabled || !editable} onClick={() => { setMode('keyword'); setQuery(''); setResults(null); scanner.current?.focus(); }}>Tên / SKU</button>
                    </div><span className="pos-hint">Mã vạch + Enter: thêm 1 sản phẩm</span></div>
                    {quickItems.length > 0 && <div className="pos-quick-items"><span>Thêm nhanh:</span>{quickItems.map((item) =>
                        <button key={item.productId} disabled={disabled || !editable} onClick={() => addProduct(item)}><Plus size={12} /> {item.productName}</button>)}</div>}
                    {results && <div className="pos-results" aria-label="Kết quả tìm sản phẩm">
                        {results.length === 0 && <p role="status">Không tìm thấy sản phẩm phù hợp.</p>}
                        {results.map((product) => <article className="pos-product" key={product.productId}>
                            <div><strong>{product.productName}</strong><small>SKU: {product.productCode} · {product.unit}</small>
                                <span>{money(product.sellingPrice)} · Tồn: {product.stockQuantity}</span></div>
                            <button className="pos-soft-button" disabled={disabled || !editable} onClick={() => addProduct(product)}><Plus size={14} /> Thêm</button>
                        </article>)}
                    </div>}
                </section>
                <section className="pos-panel pos-cart">
                    <div className="pos-cart-heading"><h2><ClipboardList size={18} /> Danh Sách Mặt Hàng Trong Đơn</h2>
                        <span className="pos-count">{items.length} mặt hàng</span><span className="pos-hint">Nhập số lượng để sửa nhanh</span></div>
                    {items.length === 0 ? <div className="pos-empty"><ShoppingCart size={38} /><h3>Chưa có mặt hàng trong đơn</h3>
                        <p>{sale ? 'Quét mã vạch hoặc tìm sản phẩm để bắt đầu.' : 'Tạo đơn hoặc chọn đơn đang bán dở để tiếp tục.'}</p></div> :
                        <div className="pos-table-wrap"><table><thead><tr><th>STT</th><th>SẢN PHẨM & MÃ HÀNG</th><th>ĐƠN GIÁ</th><th>SỐ LƯỢNG</th><th>THÀNH TIỀN</th><th aria-label="Thao tác" /></tr></thead>
                            <tbody>{items.map((item, index) => <tr key={item.saleItemId}>
                                <td className="pos-row-number">{String(index + 1).padStart(2, '0')}</td>
                                <td><strong>{item.productName}</strong><small>SKU: {item.productCode}</small></td><td className="pos-money">{money(item.unitPrice)}</td>
                                <td><QuantityEditor key={`${sale.saleId}-${item.saleItemId}`} item={item} disabled={disabled || !editable}
                                    onSave={(id, quantity) => perform(async () => {
                                        acceptSale(await SaleService.updateItemQuantity(sale.saleId, id, Number(quantity)));
                                        setNotice('Đã cập nhật số lượng và tổng tiền.');
                                    })} /></td><td className="pos-line-total">{money(item.subtotal)}</td>
                                <td><button className="pos-remove" title="Xóa mặt hàng" aria-label={`Xóa ${item.productName}`} disabled={disabled || !editable}
                                    onClick={() => perform(async () => {
                                        acceptSale(await SaleService.removeItem(sale.saleId, item.saleItemId)); setNotice('Đã xóa mặt hàng và cập nhật tổng tiền.');
                                    })}><Trash2 size={16} /></button></td>
                            </tr>)}</tbody></table></div>}
                    <footer className="pos-cart-footer"><span><ShieldCheck size={13} /> Đơn giá theo giao dịch đã lưu</span>
                        <span>Tổng số lượng: <strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(totalQuantity)} sản phẩm</strong></span></footer>
                </section>
            </div>
            <aside className="pos-receipt-column">
                <section className="pos-panel pos-receipt">
                    <header className="pos-receipt-heading"><div><span>ĐƠN BÁN HÀNG</span><strong>{sale ? `Đơn #${sale.saleId}` : 'Chưa tạo đơn'}</strong></div>
                        <span className="pos-status">{!sale ? 'Chờ tạo đơn' : editable ? 'Đang thực hiện' : sale.status === 'COMPLETED' ? 'Hoàn tất' : sale.status === 'CANCELLED' ? 'Đã hủy' : 'Đơn đang khóa'}</span></header>
                    <div className="pos-receipt-operator"><Store size={17} /><div><small>Thu ngân</small><strong>{sale?.cashierUsername ?? 'Chưa chọn người thao tác'}</strong></div></div>
                    <dl className="pos-totals"><div><dt>Tổng tiền hàng (Subtotal):</dt><dd>{money(sale?.subtotal)}</dd></div></dl>
                    <div className="pos-formula">Thành tiền mỗi dòng = Đơn giá × Số lượng</div>
                    <div className="pos-final-total"><span>TỔNG TIỀN ĐƠN HÀNG</span><strong>{money(sale?.totalAmount)}</strong><small>VND · Tính từ các mặt hàng trong đơn</small></div>
                    {sale && <PaymentPanel key={sale.saleId} sale={sale} busy={busy} runAction={perform} onCompleted={setSale}
                        onLock={(locked) => setPaymentGate({ saleId: sale.saleId, locked })} />}
                    <button className="pos-primary pos-recalculate" disabled={disabled || !editable} onClick={() => perform(async () => {
                        acceptSale(await SaleService.calculateTotal(sale.saleId)); setNotice('Đã tính lại tổng tiền.');
                    })}><RefreshCw size={16} /> Tính lại tổng tiền</button>
                    <button className="pos-soft-button pos-draft-action" disabled={disabled} aria-expanded={showDrafts} onClick={() => setShowDrafts((value) => !value)}>
                        <ClipboardList size={15} /> {showDrafts ? 'Đóng danh sách đơn' : 'Xem đơn đang bán dở'}</button>
                </section>
                <section className="pos-counter-note"><ShieldCheck size={17} /><div><strong>Thao tác tại quầy</strong><p>Bấm − / + để đổi số lượng. Nhập trực tiếp rồi bấm Lưu khi bán số lượng lẻ. Đơn được giữ lại khi chuyển sang đơn mới.</p></div></section>
            </aside>
        </div>
        <p className="pos-processing" role="status" aria-live="polite">{busy ? 'Đang cập nhật…' : ''}</p>
    </section>;
}
