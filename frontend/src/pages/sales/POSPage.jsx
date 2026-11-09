import { useEffect, useRef, useState } from 'react';
import { ScanLine, ShoppingCart, Trash2, Store, Search, Plus, RefreshCw, ClipboardList, ShieldCheck, AlertTriangle } from 'lucide-react';
import { useAuth } from '../../context/authContext';
import SaleService from '../../services/saleService';
import UserService from '../../services/userService';
import PaymentPanel from './PaymentPanel';
import CheckoutSteps from './CheckoutSteps';
import { getPOSErrorMessage as getErrorMessage } from './posErrorMessage';
import './POSCheckout.css';

/**
 * POS integration for UC24–30, including automatic line promotions.
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
            aria-hidden={value === Number(item.quantity)} tabIndex={value === Number(item.quantity) ? -1 : 0}>Save</button>
    </form>;
}


function POSConfirmDialog({ title, subMessage, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', danger = false, icon: Icon, onConfirm, onCancel }) {
    useEffect(() => {
        function onKey(event) {
            if (event.key === 'Escape') onCancel();
        }
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onCancel]);

    return (
        <div className="pos-confirm-overlay" role="dialog" aria-modal="true" onClick={onCancel}>
            <div className="pos-confirm-modal" onClick={(e) => e.stopPropagation()}>
                <div className="pos-confirm-header">
                    <div className={`pos-confirm-icon-box ${danger ? 'pos-confirm-icon-danger' : 'pos-confirm-icon-card'}`}>
                        {Icon ? <Icon size={24} /> : danger ? <Trash2 size={24} /> : <AlertTriangle size={24} />}
                    </div>
                    <div>
                        <h4>{title}</h4>
                        {subMessage && <p>{subMessage}</p>}
                    </div>
                </div>

                <div className="pos-confirm-body">
                    <p className="pos-confirm-modal-message">{message}</p>
                </div>

                <div className="pos-confirm-footer">
                    <button
                        type="button"
                        className="pos-confirm-btn-cancel"
                        onClick={onCancel}
                    >
                        {cancelLabel}
                    </button>
                    <button
                        type="button"
                        className={danger ? 'pos-confirm-btn-danger' : 'pos-confirm-btn-ok'}
                        autoFocus
                        onClick={onConfirm}
                    >
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}

function DraftSales({ currentId, disabled, onOpen, onCancelled }) {
    const [drafts, setDrafts] = useState([]);
    const [cleaningDrafts, setCleaningDrafts] = useState(false);
    const [cancellingDraftId, setCancellingDraftId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [confirmModal, setConfirmModal] = useState(null);

    const [error, setError] = useState('');
    const [revision, setRevision] = useState(0);
    const [filter, setFilter] = useState('');
    useEffect(() => {
        let active = true;
        SaleService.getDrafts().then((drafts) => {
            if (active) setDrafts(drafts.filter((draft) => draft.status === 'DRAFT')
                .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.saleId - a.saleId));
        }).catch((err) => { if (active) setError(getErrorMessage(err)); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [revision]);
    const emptyDrafts = drafts.filter((d) => (d.items?.length ?? 0) === 0 && Number(d.totalAmount ?? 0) === 0);

    function handleCancel(draftId) {
        setConfirmModal({
            title: `Confirm Draft Sale Cancellation #${draftId}`,
            subMessage: 'This action cannot be undone',
            message: `Are you sure you want to cancel draft sale #${draftId}? Its status will change to Cancelled.`,
            danger: true,
            confirmLabel: 'Confirm Cancellation',
            onConfirm: async () => {
                setConfirmModal(null);
                setCancellingDraftId(draftId);
                setError('');
                try {
                    await SaleService.cancel(draftId);
                    onCancelled?.(draftId);
                    setRevision((value) => value + 1);
                } catch (err) {
                    setError(getErrorMessage(err));
                } finally {
                    setCancellingDraftId(null);
                }
            }
        });
    }

    function handleCleanEmptyDrafts() {
        if (emptyDrafts.length === 0) return;
        setConfirmModal({
            title: 'Clear Empty Sales (0 VND)',
            subMessage: `${emptyDrafts.length} sales have no products`,
            message: `Clear ${emptyDrafts.length} empty draft sales (0 VND)? This will remove them from the draft list.`,
            danger: true,
            confirmLabel: `Clear ${emptyDrafts.length} Sales`,
            onConfirm: async () => {
                setConfirmModal(null);
                setCleaningDrafts(true);
                setError('');
                try {
                    for (const d of emptyDrafts) {
                        try {
                            await SaleService.cancel(d.saleId);
                            onCancelled?.(d.saleId);
                        } catch {
                            // Ignore errors during batch cleanup
                        }
                    }
                    setRevision((value) => value + 1);
                } catch (err) {
                    setError(getErrorMessage(err));
                } finally {
                    setCleaningDrafts(false);
                }
            }
        });
    }

    const visible = drafts.filter((draft) => `${draft.saleNumber} ${draft.cashierUsername} ${draft.items.map((item) => item.productName).join(' ')}`
        .toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
    return <section className="pos-panel pos-drafts" aria-label="Saved draft sales">
        <div className="pos-drafts-heading">            <h2>Draft Sales <span className="pos-count">{drafts.length}</span></h2>
            <div className="pos-drafts-heading-actions">
                {emptyDrafts.length > 0 && (
                    <button
                        type="button"
                        className="pos-clean-empty-btn"
                        disabled={disabled || loading || cleaningDrafts}
                        onClick={handleCleanEmptyDrafts}
                        title="Cancel all draft sales with no products"
                    >
                        <Trash2 size={13} /> {cleaningDrafts ? 'Clearing…' : `Clear ${emptyDrafts.length} Empty Sales (0 VND)`}
                    </button>
                )}
                <button disabled={disabled || loading || cleaningDrafts} onClick={() => {
                setLoading(true); setError(''); setRevision((value) => value + 1);
                }}>Refresh</button>
            </div>
        </div>
        <label>Search by cashier, product or sale number<input value={filter}
            placeholder="For example: cashier or product name" onChange={(event) => setFilter(event.target.value)} /></label>
        {loading ? <p role="status">Loading draft sales…</p> : error ? <p className="pos-error" role="alert">{error}</p> :
            visible.length === 0 ? <p>{drafts.length === 0 ? 'No draft sales yet.' : 'No matching sales found.'}</p> :
                <div className="pos-draft-list">{visible.map((draft) => <article className="pos-draft-card" key={draft.saleId}>
                    <div className="pos-drafts-heading"><strong>Sale #{draft.saleId}</strong>
                        {draft.saleId === currentId && <span className="pos-count">Currently Open</span>}</div>
                    <p>{draft.cashierUsername} · {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(draft.createdAt))}</p>
                    <p className="pos-draft-preview">{draft.items.length ? draft.items.slice(0, 3).map((item) => item.productName).join(', ') : 'No products in this sale'}{draft.items.length > 3 ? '…' : ''}</p>
                    <div className="pos-drafts-heading"><span>{draft.items.length} items</span><strong>{money(draft.totalAmount)}</strong></div>
                    <div className="pos-draft-card-actions">
                        <button className="pos-primary" disabled={disabled || draft.saleId === currentId || cancellingDraftId === draft.saleId}
                            onClick={() => onOpen(draft.saleId)}>Resume Sale</button>
                        <button type="button" className="pos-danger-btn" disabled={disabled || cancellingDraftId === draft.saleId}
                            onClick={() => handleCancel(draft.saleId)}>
                            {cancellingDraftId === draft.saleId ? 'Cancelling…' : 'Cancel Sale'}
                        </button>
                    </div>
                </article>)}</div>}
        {confirmModal && <POSConfirmDialog {...confirmModal} onCancel={() => setConfirmModal(null)} />}
    </section>;
}

export default function POSPage() {
    const { currentUser } = useAuth();
    const [sale, setSale] = useState(null);
    const [checkoutProgress, setCheckoutProgress] = useState({ saleId: null, step: 1 });
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
    const [confirmModal, setConfirmModal] = useState(null);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [userError, setUserError] = useState('');
    const lock = useRef(false);
    const scanner = useRef(null);
    const focusScanner = useRef(false);

    useEffect(() => {
        let active = true;
        if (!currentUser?.userId) {
            UserService.getAll({ status: 'ACTIVE' }).then(({ data }) => {
            if (active) setUsers(data.data.filter((user) => user.status === 'ACTIVE'));
        }).catch((err) => { if (active) setUserError(getErrorMessage(err)); });
        }
        const id = sessionStorage.getItem(storageKey);
        const restore = id ? SaleService.getById(id).then(({ data }) => {
            if (active) setSale(data.data);
        }).catch((err) => {
            if (!active) return;
            if (err.response?.status === 404) {
                sessionStorage.removeItem(storageKey);
                sessionStorage.removeItem(`sms.payment.request.${id}`);
                setNotice('The saved sale no longer exists. Create a new sale or select a draft sale.');
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
            const activeCashierId = currentUser?.userId ?? (cashierId ? Number(cashierId) : null);
            if (!activeCashierId) return;
            const response = await SaleService.create({ cashierId: Number(activeCashierId) });
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

    // Search automatically while typing a product name or SKU.
    useEffect(() => {
        if (!editable) return;
        const trimmed = query.trim();
        if (!trimmed) {
            setResults(null);
            return;
        }
        let active = true;
        const timer = setTimeout(async () => {
            try {
                const response = await SaleService.searchProducts({ [mode]: trimmed });
                if (active) {
                    setResults(response.data.data);
                }
            } catch {
                // Ignore transient live search errors
            }
        }, 200);
        return () => {
            active = false;
            clearTimeout(timer);
        };
    }, [query, mode, editable]);

    const items = sale?.items ?? [];
    const quickItems = [...new Map(items.map((item) => [item.productId, item])).values()].slice(-4);
    const totalQuantity = items.reduce((total, item) => total + Number(item.quantity), 0);
    function addProduct(product) {
        return perform(async () => {
            acceptSale(await SaleService.addItem(sale.saleId, { productId: product.productId, quantity: 1 }));
            setNotice(`Added ${product.productName}.`);
        });
    }
    function startAnother() {
        if (items.length) {
            setConfirmModal({
                title: 'Create New Sale',
                subMessage: 'Keep the current sale',
                message: 'The current sale contains items. Starting a new sale will keep it in Draft Sales so you can resume it later.',
                danger: false,
                icon: Plus,
                confirmLabel: 'Create New Sale',
                onConfirm: () => {
                    setConfirmModal(null);
                    sessionStorage.removeItem(storageKey); setSale(null); setResults(null); setQuery(''); setError('');
                    setShowDrafts(false); setNotice('The previous sale is saved in Draft Sales.');
                }
            });
            return;
        }
        sessionStorage.removeItem(storageKey); setSale(null); setResults(null); setQuery(''); setError('');
        setShowDrafts(false); setNotice('The previous sale is saved in Draft Sales.');
    }

    function cancelActiveSale() {
        if (!sale) return;
        setConfirmModal({
            title: `Confirm Sale Cancellation #${sale.saleId}`,
            subMessage: 'This action cannot be undone',
            message: `Are you sure you want to cancel sale #${sale.saleId}? The sale will be marked as Cancelled.`,
            danger: true,
            icon: Trash2,
            confirmLabel: 'Confirm Cancellation',
            onConfirm: async () => {
                setConfirmModal(null);
                await perform(async () => {
                    await SaleService.cancel(sale.saleId);
                    sessionStorage.removeItem(storageKey);
                    setSale(null);
                    setResults(null);
                    setQuery('');
                    setNotice(`Sale #${sale.saleId} has been cancelled.`);
                });
            }
        });
    }

    function handleDraftCancelled(draftId) {
        if (sale?.saleId === draftId) {
            sessionStorage.removeItem(storageKey);
            setSale(null);
            setResults(null);
            setQuery('');
        }
        setNotice(`Sale #${draftId} has been cancelled.`);
    }

    return <section className="pos-page">
        <CheckoutSteps currentStep={sale?.status === 'COMPLETED' ? 4
            : sale && checkoutProgress.saleId === sale.saleId ? checkoutProgress.step : 1} />
        <header className="pos-checkout-header">
            <div className="pos-title-line"><span className="pos-brand-icon"><Store size={22} /></span>
                <div><h1>Point of Sale <span>(POS Checkout)</span></h1>
                    <p>Sale Number: <strong>{sale?.saleNumber ?? 'No sale created'}</strong><span className="pos-meta-dot">•</span>
                        Cashier: <strong>{sale?.cashierUsername ?? (currentUser?.fullName || currentUser?.username) ?? 'Not selected'}</strong></p></div>
            </div>
            <div className="pos-header-actions">
                <button className="pos-soft-button" disabled={disabled} aria-expanded={showDrafts}
                    onClick={() => setShowDrafts((value) => !value)}><ClipboardList size={15} />
                    {showDrafts ? 'Close Draft Sales' : 'Draft Sales'}</button>
                {sale && <button className="pos-soft-button" disabled={disabled} onClick={startAnother}><Plus size={15} /> New Sale</button>}
                {sale && editable && <button className="pos-soft-button pos-btn-cancel-active" disabled={disabled} onClick={cancelActiveSale} title="Cancel the current sale"><Trash2 size={15} /> Cancel Sale</button>}
                {sale && <button className="pos-icon-button" title="Reload sale from server" aria-label="Reload sale" disabled={disabled}
                    onClick={() => perform(async () => {
                        acceptSale(await SaleService.getById(sale.saleId)); setNotice('Sale reloaded.');
                    })}><RefreshCw size={15} /></button>}
                <span className="pos-connection-note"><span /> Totals update automatically</span>
            </div>
        </header>
        {error && <div className="pos-error" role="alert">{error}</div>}
        {notice && <div className="pos-notice" role="status">{notice}</div>}
        {loading && <p className="pos-loading" role="status">Loading sale…</p>}
        {restoreFailed && <button className="pos-soft-button" onClick={() => window.location.reload()}>Retry Loading Sale</button>}
        {!sale && !loading && !restoreFailed && <form className="pos-panel pos-start" onSubmit={createSale}>
            <div><h2>Start a Sale</h2><p>{currentUser ? 'Create a new sale with your current account or select a draft sale below.' : 'Select a cashier to create a new sale.'}</p></div>
            {currentUser ? (
                <div className="pos-current-cashier">
                    <span>Cashier</span>
                    <strong>{currentUser.fullName || currentUser.username} ({currentUser.username})</strong>
                </div>
            ) : (
                <label>Cashier<select value={cashierId} required disabled={disabled} onChange={(event) => setCashierId(event.target.value)}>
                    <option value="">Select a cashier</option>
                    {users.map((user) => <option key={user.userId} value={user.userId}>{user.employeeFullName || user.username} ({user.username})</option>)}
                </select></label>
            )}
            <button className="pos-primary" disabled={disabled || (!currentUser?.userId && !cashierId)}><Plus size={16} /> Create New Sale</button>
            {!currentUser && userError && <p className="pos-start-error" role="alert">Unable to load cashiers: {userError} <button type="button" onClick={() => window.location.reload()}>Retry</button></p>}
            {!currentUser && !userError && users.length === 0 && <p className="pos-start-error">No active accounts are available to create a sale.</p>}
        </form>}
        {!loading && !restoreFailed && (showDrafts || !sale) && <DraftSales key={sale?.saleId ?? 'start'}
            currentId={sale?.saleId} disabled={disabled} onCancelled={handleDraftCancelled} onOpen={(id) => perform(async () => {
                const response = await SaleService.getById(id);
                acceptSale(response); sessionStorage.setItem(storageKey, String(response.data.data.saleId));
                setShowDrafts(false); setQuery(''); setResults(null); focusScanner.current = true;
                setNotice('Sale reopened. You can continue checkout.');
            })} />}
        {sale && sale.status !== 'DRAFT' && <div className="pos-readonly">This sale is {sale.status === 'COMPLETED' ? 'completed' : 'cancelled'}; items cannot be edited.</div>}
        <div className="pos-checkout-grid">
            <div className="pos-workspace">
                <section className="pos-panel pos-finder">
                    <div className="pos-section-heading"><h2><ScanLine size={17} /> Scan Barcode or Search by SKU / Product Name</h2><kbd>Focus: [F2]</kbd></div>
                    <form className="pos-search" onSubmit={search}>
                        <ScanLine size={19} className="pos-search-icon" />
                        <input aria-label={mode === 'barcode' ? 'Scan barcode' : 'Search by name or SKU'} ref={scanner} value={query} required
                            maxLength={mode === 'barcode' ? 100 : 200} placeholder={mode === 'barcode' ? 'Scan a barcode and press Enter to add a product…' : 'Enter a product name or SKU to search…'}
                            disabled={disabled || !editable} autoComplete="off" onChange={(event) => { setQuery(event.target.value); if (!event.target.value.trim()) setResults(null); }} />
                        <button className="pos-primary" disabled={disabled || !editable || !query.trim()}><Search size={15} /> {mode === 'barcode' ? 'Add' : 'Search'}</button>
                    </form>
                    <div className="pos-search-options"><div className="pos-mode-switch" role="group" aria-label="Product search mode">
                        <button aria-pressed={mode === 'barcode'} disabled={disabled || !editable} onClick={() => { setMode('barcode'); setQuery(''); setResults(null); scanner.current?.focus(); }}>Barcode</button>
                        <button aria-pressed={mode === 'keyword'} disabled={disabled || !editable} onClick={() => { setMode('keyword'); setQuery(''); setResults(null); scanner.current?.focus(); }}>Name / SKU</button>
                    </div><span className="pos-hint">Barcode + Enter: add 1 product</span></div>
                    {quickItems.length > 0 && <div className="pos-quick-items"><span>Quick Add:</span>{quickItems.map((item) =>
                        <button key={item.productId} disabled={disabled || !editable} onClick={() => addProduct(item)}><Plus size={12} /> {item.productName}</button>)}</div>}
                    {results && <div className="pos-results" aria-label="Product search results">
                        {results.length === 0 && <p role="status">No matching products found.</p>}
                        {results.map((product) => <article className="pos-product" key={product.productId}>
                            <div><strong>{product.productName}</strong><small>SKU: {product.productCode} · {product.unit}</small>
                                <span>{money(product.sellingPrice)} · Stock: {product.stockQuantity}</span></div>
                            <button className="pos-soft-button" disabled={disabled || !editable} onClick={() => addProduct(product)}><Plus size={14} /> Add</button>
                        </article>)}
                    </div>}
                </section>
                <section className="pos-panel pos-cart">
                    <div className="pos-cart-heading"><h2><ClipboardList size={18} /> Order Items</h2>
                        <span className="pos-count">{items.length} items</span><span className="pos-hint">Enter a quantity to update</span></div>
                    {items.length === 0 ? <div className="pos-empty"><ShoppingCart size={38} /><h3>No items in this sale</h3>
                        <p>{sale ? 'Scan a barcode or search for products to get started.' : 'Create a sale or select a draft sale to continue.'}</p></div> :
                        <div className="pos-table-wrap"><table><colgroup><col style={{ width: '48px' }} /><col /><col style={{ width: '120px' }} /><col style={{ width: '140px' }} /><col style={{ width: '130px' }} /><col style={{ width: '48px' }} /></colgroup><thead><tr><th className="pos-col-stt">No.</th><th className="pos-col-product">PRODUCT & SKU</th><th className="pos-col-price">UNIT PRICE</th><th className="pos-col-qty">QUANTITY</th><th className="pos-col-total">AMOUNT</th><th className="pos-col-action" aria-label="Actions" /></tr></thead>
                            <tbody>{items.map((item, index) => <tr key={item.saleItemId}>
                                <td className="pos-row-number">{String(index + 1).padStart(2, '0')}</td>
                                <td className="pos-cell-product"><strong>{item.productName}</strong><small>SKU: {item.productCode}</small></td><td className="pos-money">{money(item.unitPrice)}</td>
                                <td className="pos-cell-qty"><QuantityEditor key={`${sale.saleId}-${item.saleItemId}`} item={item} disabled={disabled || !editable}
                                    onSave={(id, quantity) => perform(async () => {
                                        acceptSale(await SaleService.updateItemQuantity(sale.saleId, id, Number(quantity)));
                                        setNotice('Quantity and totals updated.');
                                    })} /></td><td className="pos-line-total">{money(Number(item.subtotal) - Number(item.discountAmount))}
                                        {Number(item.discountAmount) > 0 && <small className="pos-line-discount">
                                            Before discount: {money(item.subtotal)}<br />Discount: −{money(item.discountAmount)}
                                        </small>}
                                    </td>
                                <td className="pos-cell-action"><button className="pos-remove" title="Remove item" aria-label={`Remove ${item.productName}`} disabled={disabled || !editable}
                                    onClick={() => perform(async () => {
                                        acceptSale(await SaleService.removeItem(sale.saleId, item.saleItemId)); setNotice('Item removed and totals updated.');
                                    })}><Trash2 size={16} /></button></td>
                            </tr>)}</tbody></table></div>}
                    <footer className="pos-cart-footer"><span><ShieldCheck size={13} /> Unit prices from the saved sale</span>
                        <span>Total Quantity: <strong>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(totalQuantity)} products</strong></span></footer>
                </section>
            </div>
            <aside className="pos-receipt-column">
                <section className="pos-panel pos-receipt">
                    <header className="pos-receipt-heading"><div><span>SALE</span><strong>{sale ? `Sale #${sale.saleId}` : 'No sale created'}</strong></div>
                        <span className="pos-status">{!sale ? 'Awaiting Sale' : editable ? 'In Progress' : sale.status === 'COMPLETED' ? 'Completed' : sale.status === 'CANCELLED' ? 'Cancelled' : 'Sale Locked'}</span></header>
                    <div className="pos-receipt-operator"><Store size={17} /><div><small>Cashier</small><strong>{sale?.cashierUsername ?? (currentUser?.fullName || currentUser?.username) ?? 'No cashier selected'}</strong></div></div>
                    <dl className="pos-totals"><div><dt>Subtotal:</dt><dd>{money(sale?.subtotal)}</dd></div>
                        <div className="pos-discount-total"><dt>Promotional Discount:</dt><dd>−{money(sale?.discountAmount)}</dd></div></dl>
                    <div className="pos-formula">Total Due = Subtotal − Discount</div>
                    <div className="pos-final-total"><span>ORDER TOTAL</span><strong>{money(sale?.totalAmount)}</strong><small>VND · Calculated from order items</small></div>
                    {sale && <PaymentPanel key={sale.saleId} sale={sale} busy={busy} runAction={perform} onCompleted={setSale}
                        onStepChange={(step) => setCheckoutProgress({ saleId: sale.saleId, step })}
                        onLock={(locked) => setPaymentGate({ saleId: sale.saleId, locked })} />}
                    <button className="pos-primary pos-recalculate" disabled={disabled || !editable} onClick={() => perform(async () => {
                        acceptSale(await SaleService.calculateTotal(sale.saleId)); setNotice('Totals recalculated.');
                    })}><RefreshCw size={16} /> Recalculate Total and Promotions</button>
                    {editable && <p className="pos-promotion-note">Promotions may change over time. If payment reports a total change, recalculate and confirm the updated amount.</p>}
                    <button className="pos-soft-button pos-draft-action" disabled={disabled} aria-expanded={showDrafts} onClick={() => setShowDrafts((value) => !value)}>
                        <ClipboardList size={15} /> {showDrafts ? 'Close Draft Sales' : 'View Draft Sales'}</button>
                </section>
                <section className="pos-counter-note"><ShieldCheck size={17} /><div><strong>At the Checkout</strong><p>Press − / + to change quantity. Enter a fractional quantity directly and press Save. The sale is saved when you start a new sale.</p></div></section>
            </aside>
        </div>
        <p className="pos-processing" role="status" aria-live="polite">{busy ? 'Updating…' : ''}</p>
        {confirmModal && <POSConfirmDialog {...confirmModal} onCancel={() => setConfirmModal(null)} />}
    </section>;
}
