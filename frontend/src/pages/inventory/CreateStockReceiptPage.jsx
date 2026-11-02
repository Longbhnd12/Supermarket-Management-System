import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import StockReceiptService from '../../services/stockReceiptService';
import SupplierService from '../../services/supplierService';
import ProductService from '../../services/productService';
import './CreateStockReceiptPage.css';

// ─── Formatting helpers ────────────────────────────────────────────────────────

function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '$0.00';
    return new Intl.NumberFormat('en-US', {
        style: 'currency', currency: 'USD', minimumFractionDigits: 2,
    }).format(amount);
}

function formatNumber(n) {
    if (n == null || isNaN(n)) return '0';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
}

const TODAY_STR = new Date().toISOString().split('T')[0];

export default function CreateStockReceiptPage() {
    const navigate = useNavigate();

    // ── Header Information State ──
    const [receiptNumber, setReceiptNumber] = useState('');
    const [supplierId, setSupplierId]       = useState('');
    const [purchaseOrderId, setPurchaseOrderId] = useState('');
    const [suppliers, setSuppliers]         = useState([]);

    // ── Products List & Selection State ──
    const [allProducts, setAllProducts]     = useState([]);
    const [items, setItems]                 = useState([]);

    // ── Barcode scanning state ──
    const [barcodeInput, setBarcodeInput]   = useState('');
    const [barcodeError, setBarcodeError]   = useState('');

    // ── Manual product search state ──
    const [searchKeyword, setSearchKeyword] = useState('');
    const [isSearchOpen, setIsSearchOpen]   = useState(false);
    const searchContainerRef = useRef(null);

    // ── UI / Loading / Error states ──
    const [loadingInit, setLoadingInit]     = useState(true);
    const [submitting, setSubmitting]       = useState(false);
    const [pageError, setPageError]         = useState('');
    const [errors, setErrors]               = useState({});

    // ── Load initial data: next receipt number, active suppliers, active products ──
    useEffect(() => {
        let isMounted = true;

        Promise.all([
            StockReceiptService.getNextNumber().catch(() => ({ data: { data: 'SR0001' } })),
            SupplierService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
            ProductService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
        ]).then(([numRes, supRes, prodRes]) => {
            if (!isMounted) return;
            const nextNum = numRes.data?.data || 'SR0001';
            setReceiptNumber(nextNum);

            const supList = (supRes.data?.data || []).filter(s => s.status !== 'DISABLED');
            setSuppliers(supList);

            const prodList = (prodRes.data?.data || []).filter(p => p.status === 'ACTIVE');
            setAllProducts(prodList);
        }).finally(() => {
            if (isMounted) setLoadingInit(false);
        });

        return () => { isMounted = false; };
    }, []);

    // ── Close search dropdown when clicking outside ──
    useEffect(() => {
        function handleClickOutside(e) {
            if (searchContainerRef.current && !searchContainerRef.current.contains(e.target)) {
                setIsSearchOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // ── Filtered products for manual search ──
    const searchResults = useMemo(() => {
        if (!searchKeyword.trim()) return [];
        const kw = searchKeyword.toLowerCase().trim();
        return allProducts.filter(p =>
            p.productName.toLowerCase().includes(kw) ||
            p.productCode.toLowerCase().includes(kw) ||
            (p.barcode && p.barcode.toLowerCase().includes(kw)) ||
            (p.categoryName && p.categoryName.toLowerCase().includes(kw))
        ).slice(0, 8);
    }, [searchKeyword, allProducts]);

    // ── Add product to items ──
    const handleAddProduct = useCallback((product) => {
        setItems(prevItems => {
            const existingIndex = prevItems.findIndex(it => it.productId === product.productId);
            if (existingIndex >= 0) {
                // If already added, increment quantity by 1
                const updated = [...prevItems];
                const item = updated[existingIndex];
                updated[existingIndex] = {
                    ...item,
                    quantity: Number(item.quantity) + 1,
                };
                return updated;
            }

            // New line item
            return [
                ...prevItems,
                {
                    productId: product.productId,
                    productCode: product.productCode,
                    productName: product.productName,
                    barcode: product.barcode,
                    unit: product.unit || 'pcs',
                    trackExpiry: Boolean(product.trackExpiry),
                    quantity: 1,
                    unitCost: product.sellingPrice != null ? Number(product.sellingPrice) : 0,
                    batchNumber: '',
                    expirationDate: '',
                }
            ];
        });

        // Clear search and barcode states
        setSearchKeyword('');
        setIsSearchOpen(false);
        setBarcodeError('');
        setPageError('');
    }, []);

    // ── Barcode scan submission ──
    const handleBarcodeSubmit = (e) => {
        if (e) e.preventDefault();
        const code = barcodeInput.trim();
        if (!code) return;

        // Try local list first
        const found = allProducts.find(p => p.barcode && p.barcode.toLowerCase() === code.toLowerCase());
        if (found) {
            handleAddProduct(found);
            setBarcodeInput('');
            setBarcodeError('');
            return;
        }

        // Try remote API lookup if not loaded locally
        ProductService.getByBarcode(code)
            .then(res => {
                const prod = res.data?.data;
                if (prod && prod.status === 'ACTIVE') {
                    handleAddProduct(prod);
                    setBarcodeInput('');
                    setBarcodeError('');
                } else {
                    setBarcodeError(`Product with barcode "${code}" is inactive or not found.`);
                }
            })
            .catch(() => {
                setBarcodeError(`No active product found with barcode "${code}".`);
            });
    };

    // ── Modify Item Attributes ──
    const handleItemChange = (index, field, value) => {
        setItems(prev => {
            const next = [...prev];
            next[index] = { ...next[index], [field]: value };
            return next;
        });
        // Clear specific field error
        setErrors(prev => {
            const next = { ...prev };
            delete next[`item_${index}_${field}`];
            return next;
        });
    };

    // ── Remove Item ──
    const handleRemoveItem = (index) => {
        setItems(prev => prev.filter((_, i) => i !== index));
    };

    // ── Totals Calculation ──
    const overallTotalCost = useMemo(() => {
        return items.reduce((sum, item) => {
            const q = parseFloat(item.quantity) || 0;
            const c = parseFloat(item.unitCost) || 0;
            return sum + (q * c);
        }, 0);
    }, [items]);

    const overallTotalQuantity = useMemo(() => {
        return items.reduce((sum, item) => sum + (parseFloat(item.quantity) || 0), 0);
    }, [items]);

    // ── Validate Form ──
    const validateForm = () => {
        const newErrors = {};

        if (!supplierId) {
            newErrors.supplierId = 'Supplier is required. Please select a supplier.';
        }

        if (items.length === 0) {
            newErrors.items = 'Please add at least one product item to the receipt.';
        }

        items.forEach((item, idx) => {
            const q = parseFloat(item.quantity);
            if (isNaN(q) || q <= 0) {
                newErrors[`item_${idx}_quantity`] = 'Quantity must be > 0';
            }

            const c = parseFloat(item.unitCost);
            if (isNaN(c) || c < 0) {
                newErrors[`item_${idx}_unitCost`] = 'Unit cost must be >= 0';
            }

            if (item.trackExpiry) {
                if (!item.batchNumber || !item.batchNumber.trim()) {
                    newErrors[`item_${idx}_batchNumber`] = 'Batch number is required for perishable items';
                }
                if (!item.expirationDate) {
                    newErrors[`item_${idx}_expirationDate`] = 'Expiration date is required';
                } else if (item.expirationDate < TODAY_STR) {
                    newErrors[`item_${idx}_expirationDate`] = 'Expiry date cannot be in the past';
                }
            }
        });

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // ── Form Submit: Save (DRAFT or CONFIRMED) ──
    const handleSubmit = (targetStatus) => {
        setPageError('');
        if (!validateForm()) {
            setPageError('Please review and correct the errors below before submitting.');
            return;
        }

        setSubmitting(true);

        const payload = {
            receiptNumber: receiptNumber.trim() || undefined,
            supplierId: Number(supplierId),
            purchaseOrderId: purchaseOrderId ? Number(purchaseOrderId) : null,
            status: targetStatus,
            items: items.map(item => ({
                productId: item.productId,
                quantity: parseFloat(item.quantity),
                unitCost: parseFloat(item.unitCost),
                batchNumber: item.trackExpiry ? item.batchNumber.trim() : null,
                expirationDate: item.trackExpiry ? item.expirationDate : null,
            }))
        };

        StockReceiptService.create(payload)
            .then(res => {
                const created = res.data?.data;
                if (created && created.stockReceiptId) {
                    navigate(`/inventory/stock-receipts/${created.stockReceiptId}`);
                } else {
                    navigate('/inventory/stock-receipts');
                }
            })
            .catch(err => {
                const msg = err.response?.data?.message || 'Failed to create stock receipt. Please try again.';
                setPageError(msg);
                setSubmitting(false);
            });
    };

    if (loadingInit) {
        return (
            <div className="csr-page">
                <div className="csr-loading" role="status" aria-live="polite">
                    <div className="csr-spinner" aria-hidden="true" />
                    <span className="csr-loading__text">Loading stock receipt form…</span>
                </div>
            </div>
        );
    }

    return (
        <div className="csr-page">
            {/* ── 1. Page Header ── */}
            <div className="csr-header">
                <div className="csr-header-left">
                    <nav className="csr-breadcrumb" aria-label="Breadcrumb">
                        <span className="csr-breadcrumb__item">Inventory</span>
                        <span className="csr-breadcrumb__sep">›</span>
                        <button
                            type="button"
                            className="csr-breadcrumb__link"
                            onClick={() => navigate('/inventory/stock-receipts')}
                        >
                            Stock Receipts
                        </button>
                        <span className="csr-breadcrumb__sep">›</span>
                        <span className="csr-breadcrumb__item csr-breadcrumb__item--active">
                            Create Stock Receipt
                        </span>
                    </nav>
                    <div className="csr-title-row">
                        <h1 className="csr-title">Create Stock Receipt</h1>
                        <span className="csr-badge csr-badge--active">Import Stock (UC36)</span>
                    </div>
                    <p className="csr-subtitle">Record and import new inbound stock batches into supermarket inventory</p>
                </div>
                <div className="csr-header-actions">
                    <button
                        type="button"
                        className="csr-btn csr-btn--ghost"
                        onClick={() => navigate('/inventory/stock-receipts')}
                        disabled={submitting}
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        className="csr-btn csr-btn--secondary"
                        onClick={() => handleSubmit('DRAFT')}
                        disabled={submitting}
                        title="Save as Draft without updating inventory stock yet"
                    >
                        Save as Draft
                    </button>
                    <button
                        type="button"
                        className="csr-btn csr-btn--primary"
                        onClick={() => handleSubmit('CONFIRMED')}
                        disabled={submitting}
                        title="Confirm receipt and immediately increase inventory stock"
                    >
                        {submitting ? (
                            <>
                                <span className="csr-spinner csr-spinner--sm" aria-hidden="true" />
                                Processing…
                            </>
                        ) : (
                            <>
                                <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                                </svg>
                                Confirm Receipt
                            </>
                        )}
                    </button>
                </div>
            </div>

            {/* ── Global Alert Banner ── */}
            {pageError && (
                <div className="csr-alert csr-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                    <span>{pageError}</span>
                </div>
            )}

            {/* ── 2. Receipt Details Card (Header Metadata) ── */}
            <div className="csr-card">
                <div className="csr-card__header">
                    <h2 className="csr-card__title">Receipt Information</h2>
                    <span className="csr-card__hint">General stock receipt header details</span>
                </div>
                <div className="csr-card__body">
                    <div className="csr-meta-grid">
                        {/* Auto-generated Receipt ID */}
                        <div className="csr-field-group">
                            <label className="csr-label" htmlFor="csr-receipt-no">
                                Receipt Number <span className="csr-required">*</span>
                            </label>
                            <input
                                id="csr-receipt-no"
                                className="csr-input csr-input--mono"
                                type="text"
                                value={receiptNumber}
                                onChange={e => setReceiptNumber(e.target.value)}
                                placeholder="e.g. SR0004"
                                maxLength={50}
                            />
                            <span className="csr-help-text">Auto-generated sequence identifier (customizable)</span>
                        </div>

                        {/* Supplier Selection */}
                        <div className="csr-field-group">
                            <label className="csr-label" htmlFor="csr-supplier">
                                Supplier <span className="csr-required">*</span>
                            </label>
                            <select
                                id="csr-supplier"
                                className={`csr-select ${errors.supplierId ? 'csr-input--error' : ''}`}
                                value={supplierId}
                                onChange={e => {
                                    setSupplierId(e.target.value);
                                    setErrors(prev => {
                                        const next = { ...prev };
                                        delete next.supplierId;
                                        return next;
                                    });
                                }}
                            >
                                <option value="">-- Select an active supplier --</option>
                                {suppliers.map(s => (
                                    <option key={s.supplierId} value={s.supplierId}>
                                        {s.supplierName} ({s.supplierCode})
                                    </option>
                                ))}
                            </select>
                            {errors.supplierId && (
                                <span className="csr-error-msg">{errors.supplierId}</span>
                            )}
                        </div>

                        {/* Inbound Date / Timestamp */}
                        <div className="csr-field-group">
                            <label className="csr-label">Date & Time</label>
                            <input
                                className="csr-input csr-input--readonly"
                                type="text"
                                readOnly
                                value={new Date().toLocaleString('en-GB', {
                                    day: '2-digit', month: 'short', year: 'numeric',
                                    hour: '2-digit', minute: '2-digit'
                                })}
                            />
                            <span className="csr-help-text">Current system timestamp</span>
                        </div>

                        {/* Purchase Order Reference (Optional) */}
                        <div className="csr-field-group">
                            <label className="csr-label" htmlFor="csr-po-ref">PO Reference (Optional)</label>
                            <input
                                id="csr-po-ref"
                                className="csr-input"
                                type="text"
                                placeholder="e.g. PO-2026-001 (Optional)"
                                value={purchaseOrderId}
                                onChange={e => setPurchaseOrderId(e.target.value)}
                            />
                            <span className="csr-help-text">Associated purchase order reference if any</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* ── 3. Add Products Section (Barcode Scan + Search) ── */}
            <div className="csr-card">
                <div className="csr-card__header">
                    <h2 className="csr-card__title">Add Products to Receipt</h2>
                    <span className="csr-card__hint">Scan barcode or search by product name/SKU</span>
                </div>
                <div className="csr-card__body">
                    <div className="csr-add-grid">
                        {/* Barcode Scanner Input */}
                        <form className="csr-barcode-wrap" onSubmit={handleBarcodeSubmit}>
                            <label className="csr-label" htmlFor="csr-barcode">Scan or Enter Barcode</label>
                            <div className="csr-barcode-input-group">
                                <div className="csr-input-icon-wrap">
                                    <svg className="csr-input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                        <path d="M3 5v14M7 5v14M11 5v14M15 5v14M19 5v14M21 5v14"/>
                                    </svg>
                                    <input
                                        id="csr-barcode"
                                        className={`csr-input csr-input--with-icon ${barcodeError ? 'csr-input--error' : ''}`}
                                        type="text"
                                        placeholder="Scan or enter barcode (e.g. 8931000000001)..."
                                        value={barcodeInput}
                                        onChange={e => {
                                            setBarcodeInput(e.target.value);
                                            setBarcodeError('');
                                        }}
                                    />
                                </div>
                                <button type="submit" className="csr-btn csr-btn--secondary">
                                    Add
                                </button>
                            </div>
                            {barcodeError && <span className="csr-error-msg">{barcodeError}</span>}
                        </form>

                        {/* Manual Product Search */}
                        <div className="csr-search-wrap" ref={searchContainerRef}>
                            <label className="csr-label" htmlFor="csr-product-search">Manual Product Search</label>
                            <div className="csr-input-icon-wrap">
                                <svg className="csr-input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                    <circle cx="11" cy="11" r="8"/>
                                    <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                                </svg>
                                <input
                                    id="csr-product-search"
                                    className="csr-input csr-input--with-icon"
                                    type="text"
                                    placeholder="Search by product name, SKU, or category..."
                                    value={searchKeyword}
                                    onFocus={() => setIsSearchOpen(true)}
                                    onChange={e => {
                                        setSearchKeyword(e.target.value);
                                        setIsSearchOpen(true);
                                    }}
                                />
                            </div>

                            {/* Dropdown Results */}
                            {isSearchOpen && searchKeyword.trim() && (
                                <div className="csr-search-dropdown" role="listbox">
                                    {searchResults.length === 0 ? (
                                        <div className="csr-search-dropdown__empty">
                                            No active products found matching "{searchKeyword}"
                                        </div>
                                    ) : (
                                        searchResults.map(p => (
                                            <div
                                                key={p.productId}
                                                className="csr-search-item"
                                                onClick={() => handleAddProduct(p)}
                                                role="option"
                                                aria-selected="false"
                                            >
                                                <div className="csr-search-item__main">
                                                    <span className="csr-search-item__name">{p.productName}</span>
                                                    <div className="csr-search-item__meta">
                                                        <span className="csr-search-item__sku">{p.productCode}</span>
                                                        {p.barcode && <span className="csr-search-item__barcode">{p.barcode}</span>}
                                                        {p.categoryName && <span className="csr-search-item__cat">{p.categoryName}</span>}
                                                    </div>
                                                </div>
                                                <div className="csr-search-item__tags">
                                                    {p.trackExpiry ? (
                                                        <span className="csr-tag csr-tag--perishable" title="Requires batch & expiration date">
                                                            Perishable
                                                        </span>
                                                    ) : (
                                                        <span className="csr-tag csr-tag--standard">
                                                            Standard
                                                        </span>
                                                    )}
                                                    <span className="csr-search-item__price">
                                                        {formatCurrency(p.sellingPrice)}
                                                    </span>
                                                </div>
                                            </div>
                                        ))
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {/* ── 4. Imported Products Table ── */}
            <div className="csr-card">
                <div className="csr-card__header">
                    <h2 className="csr-card__title">
                        Imported Products
                        <span className="csr-card__count">{items.length} product{items.length !== 1 ? 's' : ''}</span>
                    </h2>
                    {errors.items && <span className="csr-error-msg">{errors.items}</span>}
                </div>

                {items.length === 0 ? (
                    <div className="csr-empty-state">
                        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
                            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
                            <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16"/>
                        </svg>
                        <p className="csr-empty-state__title">No products added yet</p>
                        <p className="csr-empty-state__msg">
                            Scan a barcode or use the search field above to add products to this stock receipt.
                        </p>
                    </div>
                ) : (
                    <div className="csr-table-wrap">
                        <table className="csr-table" aria-label="Stock receipt items table">
                            <thead>
                                <tr>
                                    <th scope="col" className="csr-th-seq">#</th>
                                    <th scope="col" className="csr-th-product">Product / SKU</th>
                                    <th scope="col" className="csr-th-type">Type</th>
                                    <th scope="col" className="csr-th-qty">Quantity <span className="csr-required">*</span></th>
                                    <th scope="col" className="csr-th-cost">Unit Cost ($) <span className="csr-required">*</span></th>
                                    <th scope="col" className="csr-th-batch">Batch Number</th>
                                    <th scope="col" className="csr-th-expiry">Expiration Date</th>
                                    <th scope="col" className="csr-th-subtotal">Subtotal</th>
                                    <th scope="col" className="csr-th-action">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((item, idx) => {
                                    const qtyErr = errors[`item_${idx}_quantity`];
                                    const costErr = errors[`item_${idx}_unitCost`];
                                    const batchErr = errors[`item_${idx}_batchNumber`];
                                    const expErr = errors[`item_${idx}_expirationDate`];
                                    const subtotal = (parseFloat(item.quantity) || 0) * (parseFloat(item.unitCost) || 0);

                                    return (
                                        <tr key={item.productId} className="csr-tr">
                                            <td className="csr-td csr-td--seq">{idx + 1}</td>

                                            {/* Product Info */}
                                            <td className="csr-td csr-td--product">
                                                <div className="csr-prod-cell">
                                                    <span className="csr-prod-name">{item.productName}</span>
                                                    <div className="csr-prod-sub">
                                                        <span className="csr-sku-chip">{item.productCode}</span>
                                                        {item.barcode && <span className="csr-barcode-chip">{item.barcode}</span>}
                                                        <span className="csr-unit-badge">{item.unit}</span>
                                                    </div>
                                                </div>
                                            </td>

                                            {/* Shelf Life Type */}
                                            <td className="csr-td csr-td--type">
                                                {item.trackExpiry ? (
                                                    <span className="csr-tag csr-tag--perishable">Perishable</span>
                                                ) : (
                                                    <span className="csr-tag csr-tag--standard">Standard</span>
                                                )}
                                            </td>

                                            {/* Quantity Input */}
                                            <td className="csr-td csr-td--qty">
                                                <input
                                                    type="number"
                                                    min="0.001"
                                                    step="any"
                                                    className={`csr-input csr-input--num ${qtyErr ? 'csr-input--error' : ''}`}
                                                    value={item.quantity}
                                                    onChange={e => handleItemChange(idx, 'quantity', e.target.value)}
                                                />
                                                {qtyErr && <span className="csr-cell-error">{qtyErr}</span>}
                                            </td>

                                            {/* Unit Cost Input */}
                                            <td className="csr-td csr-td--cost">
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="0.01"
                                                    className={`csr-input csr-input--num ${costErr ? 'csr-input--error' : ''}`}
                                                    value={item.unitCost}
                                                    onChange={e => handleItemChange(idx, 'unitCost', e.target.value)}
                                                />
                                                {costErr && <span className="csr-cell-error">{costErr}</span>}
                                            </td>

                                            {/* Batch Number (Conditional) */}
                                            <td className="csr-td csr-td--batch">
                                                {item.trackExpiry ? (
                                                    <>
                                                        <input
                                                            type="text"
                                                            placeholder="e.g. BATCH-01"
                                                            maxLength={50}
                                                            className={`csr-input csr-input--mono ${batchErr ? 'csr-input--error' : ''}`}
                                                            value={item.batchNumber}
                                                            onChange={e => handleItemChange(idx, 'batchNumber', e.target.value)}
                                                        />
                                                        {batchErr && <span className="csr-cell-error">{batchErr}</span>}
                                                    </>
                                                ) : (
                                                    <span className="csr-na-text">N/A (Standard)</span>
                                                )}
                                            </td>

                                            {/* Expiration Date (Conditional) */}
                                            <td className="csr-td csr-td--expiry">
                                                {item.trackExpiry ? (
                                                    <>
                                                        <input
                                                            type="date"
                                                            min={TODAY_STR}
                                                            className={`csr-input ${expErr ? 'csr-input--error' : ''}`}
                                                            value={item.expirationDate}
                                                            onChange={e => handleItemChange(idx, 'expirationDate', e.target.value)}
                                                        />
                                                        {expErr && <span className="csr-cell-error">{expErr}</span>}
                                                    </>
                                                ) : (
                                                    <span className="csr-na-text">N/A (Standard)</span>
                                                )}
                                            </td>

                                            {/* Subtotal */}
                                            <td className="csr-td csr-td--subtotal">
                                                {formatCurrency(subtotal)}
                                            </td>

                                            {/* Remove Action */}
                                            <td className="csr-td csr-td--action">
                                                <button
                                                    type="button"
                                                    className="csr-btn-del"
                                                    onClick={() => handleRemoveItem(idx)}
                                                    title={`Remove ${item.productName}`}
                                                    aria-label={`Remove ${item.productName}`}
                                                >
                                                    <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                                    </svg>
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                            <tfoot>
                                <tr className="csr-tfoot-row">
                                    <td colSpan="3" className="csr-tfoot-label">
                                        Total Items: <strong>{items.length}</strong> (Qty: {formatNumber(overallTotalQuantity)})
                                    </td>
                                    <td colSpan="4" className="csr-tfoot-label--right">
                                        Overall Total Cost:
                                    </td>
                                    <td className="csr-tfoot-total">
                                        {formatCurrency(overallTotalCost)}
                                    </td>
                                    <td></td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                )}
            </div>

            {/* ── 5. Bottom Action Bar ── */}
            <div className="csr-bottom-bar">
                <button
                    type="button"
                    className="csr-btn csr-btn--ghost"
                    onClick={() => navigate('/inventory/stock-receipts')}
                    disabled={submitting}
                >
                    Cancel
                </button>
                <div className="csr-bottom-bar__right">
                    <button
                        type="button"
                        className="csr-btn csr-btn--secondary"
                        onClick={() => handleSubmit('DRAFT')}
                        disabled={submitting}
                    >
                        Save as Draft
                    </button>
                    <button
                        type="button"
                        className="csr-btn csr-btn--primary"
                        onClick={() => handleSubmit('CONFIRMED')}
                        disabled={submitting}
                    >
                        {submitting ? 'Confirming Receipt…' : 'Confirm Receipt'}
                    </button>
                </div>
            </div>
        </div>
    );
}

