import { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import InventoryService from '../../services/inventoryService';
import './AdjustStockPage.css';

function formatQty(n) {
    if (n == null || isNaN(n)) return '0';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
}

const QUICK_REASONS = [
    'Damaged goods',
    'Missing items',
    'Stock count discrepancy',
    'Expired items',
    'Supplier return',
    'Found unrecorded stock',
];

export default function AdjustStockPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const urlProductId = searchParams.get('productId');
    const urlStockCheckId = searchParams.get('stockCheckId');

    // ── Products cache & selection ──
    const [allProducts, setAllProducts] = useState([]);
    const [selectedProduct, setSelectedProduct] = useState(null);
    const [loadingProducts, setLoadingProducts] = useState(true);

    // ── Search & Scan State ──
    const [searchMode, setSearchMode] = useState('keyword'); // 'keyword' | 'barcode'
    const [keyword, setKeyword] = useState('');
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const [barcodeInput, setBarcodeInput] = useState('');
    const [barcodeError, setBarcodeError] = useState('');
    const dropdownRef = useRef(null);

    // ── Form State ──
    const [adjustmentType, setAdjustmentType] = useState('INCREASE'); // 'INCREASE' | 'DECREASE'
    const [quantityInput, setQuantityInput] = useState('');
    const [reason, setReason] = useState('');
    const [stockCheckId, setStockCheckId] = useState(urlStockCheckId || '');

    // ── UI / Error / Modal State ──
    const [fieldErrors, setFieldErrors] = useState({});
    const [apiError, setApiError] = useState('');
    const [successData, setSuccessData] = useState(null);
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // ── Load Products on Mount ──
    useEffect(() => {
        let isMounted = true;
        setLoadingProducts(true);

        ProductService.getAll({ status: 'ACTIVE' })
            .then(res => {
                if (!isMounted) return;
                const prods = res.data?.data || [];
                setAllProducts(prods);

                // Auto-select if urlProductId is provided
                if (urlProductId) {
                    const match = prods.find(p => String(p.productId) === String(urlProductId));
                    if (match) {
                        setSelectedProduct(match);
                    }
                }
            })
            .catch(err => {
                if (!isMounted) return;
                setApiError('Failed to load products list. Please reload the page.');
            })
            .finally(() => {
                if (isMounted) setLoadingProducts(false);
            });

        return () => { isMounted = false; };
    }, [urlProductId]);

    // Close dropdown on outside click
    useEffect(() => {
        function handleClickOutside(event) {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setIsDropdownOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Filter matching products for manual search
    const searchResults = useMemo(() => {
        if (!keyword.trim()) return [];
        const q = keyword.toLowerCase().trim();
        return allProducts
            .filter(p =>
                (p.productName && p.productName.toLowerCase().includes(q)) ||
                (p.productCode && p.productCode.toLowerCase().includes(q)) ||
                (p.barcode && p.barcode.toLowerCase().includes(q))
            )
            .slice(0, 10);
    }, [allProducts, keyword]);

    // Handle barcode scan submission
    const handleBarcodeSubmit = (e) => {
        e.preventDefault();
        setBarcodeError('');
        const code = barcodeInput.trim();
        if (!code) return;

        const match = allProducts.find(p =>
            (p.barcode && p.barcode.toLowerCase() === code.toLowerCase()) ||
            (p.productCode && p.productCode.toLowerCase() === code.toLowerCase())
        );

        if (match) {
            setSelectedProduct(match);
            setBarcodeInput('');
            setFieldErrors(prev => ({ ...prev, product: null }));
        } else {
            setBarcodeError(`No active product found with barcode or SKU: "${code}"`);
        }
    };

    const handleSelectProduct = (product) => {
        setSelectedProduct(product);
        setKeyword('');
        setIsDropdownOpen(false);
        setFieldErrors(prev => ({ ...prev, product: null }));
    };

    // ── Calculated New Stock ──
    const currentStock = selectedProduct?.stockQuantity ? Number(selectedProduct.stockQuantity) : 0;
    const parsedQty = Number(quantityInput);
    const isValidPositiveInt = Number.isInteger(parsedQty) && parsedQty > 0;

    const calculatedNewStock = useMemo(() => {
        if (!selectedProduct || !isValidPositiveInt) return null;
        if (adjustmentType === 'INCREASE') {
            return currentStock + parsedQty;
        } else {
            return currentStock - parsedQty;
        }
    }, [selectedProduct, currentStock, parsedQty, isValidPositiveInt, adjustmentType]);

    const isStockNegative = calculatedNewStock !== null && calculatedNewStock < 0;

    const isFormReady = Boolean(
        selectedProduct &&
        isValidPositiveInt &&
        !isStockNegative &&
        reason.trim().length > 0
    );

    // ── Validation before Opening Modal ──
    const validateForm = () => {
        const errors = {};

        if (!selectedProduct) {
            errors.product = 'Please search and select a product to adjust.';
        }

        if (!quantityInput || quantityInput.trim() === '') {
            errors.quantity = 'Adjustment quantity is required.';
        } else if (!isValidPositiveInt) {
            errors.quantity = 'Adjustment quantity must be a positive whole integer (greater than 0).';
        } else if (isStockNegative) {
            errors.quantity = `Insufficient stock! Resulting stock cannot be negative (current: ${formatQty(currentStock)}).`;
        }

        if (!reason || !reason.trim()) {
            errors.reason = 'Adjustment reason is mandatory.';
        } else if (reason.trim().length > 255) {
            errors.reason = 'Adjustment reason cannot exceed 255 characters.';
        }

        setFieldErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleOpenConfirm = () => {
        setApiError('');
        if (validateForm()) {
            setShowConfirmModal(true);
        }
    };

    // ── Apply Adjustment Submission ──
    const handleConfirmSubmit = async () => {
        if (!validateForm()) {
            setShowConfirmModal(false);
            return;
        }

        setSubmitting(true);
        setApiError('');

        const payload = {
            productId: selectedProduct.productId,
            adjustmentType: adjustmentType,
            adjustmentQuantity: parsedQty,
            reason: reason.trim(),
            stockCheckId: stockCheckId ? Number(stockCheckId) : null,
        };

        try {
            const res = await InventoryService.adjustStock(payload);
            const data = res.data?.data;
            setSuccessData(data);
            setShowConfirmModal(false);

            // Update local selected product stock to reflect latest value
            if (data && data.newStock != null) {
                setSelectedProduct(prev => ({
                    ...prev,
                    stockQuantity: data.newStock,
                }));
                // Also update in allProducts cache
                setAllProducts(prev =>
                    prev.map(p => p.productId === selectedProduct.productId ? { ...p, stockQuantity: data.newStock } : p)
                );
            }

            // Reset adjustment details form to initial state (preserve selectedProduct & successData)
            setAdjustmentType('INCREASE');
            setQuantityInput('');
            setReason('');
            setStockCheckId('');
            setFieldErrors({});
        } catch (err) {
            setShowConfirmModal(false);
            const msg = err.response?.data?.message || err.message || 'An error occurred while saving the stock adjustment.';
            setApiError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const handleResetForAnother = () => {
        setSelectedProduct(null);
        setAdjustmentType('INCREASE');
        setQuantityInput('');
        setReason('');
        setStockCheckId('');
        setSuccessData(null);
        setApiError('');
        setFieldErrors({});
    };

    return (
        <div className="adj-page">
            {/* ── Breadcrumb & Header ── */}
            <header className="adj-header">
                <nav className="adj-breadcrumb" aria-label="Breadcrumb">
                    <Link to="/inventory">Inventory</Link>
                    <span>/</span>
                    <span>Adjust Stock</span>
                </nav>
                <h1 className="adj-title">Adjust Stock</h1>
                <p className="adj-subtitle">
                    Record inventory corrections for damaged, missing, or reconciled discrepancy quantities (UC33).
                </p>
            </header>

            {/* ── Global API Error Banner ── */}
            {apiError && (
                <div className="adj-alert adj-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <span>{apiError}</span>
                    <button type="button" className="adj-alert__close" onClick={() => setApiError('')} title="Dismiss">×</button>
                </div>
            )}

            {/* ── Success Alert Banner ── */}
            {successData && (
                <div className="adj-alert adj-alert--success" role="status">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    <div style={{ flex: 1 }}>
                        <strong>Stock adjustment saved successfully!</strong>
                        <div>
                            Product: <strong>{successData.productName}</strong> ({successData.productCode}) —
                            New Stock: <strong>{formatQty(successData.newStock)} {successData.unit}</strong> (Movement Ref #{successData.movementId})
                        </div>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button type="button" className="adj-btn adj-btn--ghost" onClick={handleResetForAnother}>
                            Adjust Another
                        </button>
                        <button type="button" className="adj-btn adj-btn--primary" onClick={() => navigate('/inventory')}>
                            View Inventory
                        </button>
                    </div>
                </div>
            )}

            {/* ── Main 2-Column Grid ── */}
            <div className="adj-grid">
                {/* ── LEFT COLUMN: Product Selection & Information ── */}
                <div className="adj-card">
                    <div className="adj-card__header">
                        <h2 className="adj-card__title">
                            <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" color="#3b82f6">
                                <path d="M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a2 2 0 012-2h12a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4z" />
                            </svg>
                            1. Select Product
                        </h2>
                        {stockCheckId && (
                            <span className="adj-session-badge" title="Audit session reference">
                                Stock Check #{stockCheckId}
                            </span>
                        )}
                    </div>

                    {/* Search Method Switcher */}
                    <div className="adj-search-wrap">
                        <div className="adj-search-tabs">
                            <button
                                type="button"
                                className={`adj-tab-btn ${searchMode === 'keyword' ? 'adj-tab-btn--active' : ''}`}
                                onClick={() => setSearchMode('keyword')}
                            >
                                Name / SKU Search
                            </button>
                            <button
                                type="button"
                                className={`adj-tab-btn ${searchMode === 'barcode' ? 'adj-tab-btn--active' : ''}`}
                                onClick={() => setSearchMode('barcode')}
                            >
                                Barcode Scanner
                            </button>
                        </div>

                        {searchMode === 'keyword' ? (
                            <div className="adj-input-group" ref={dropdownRef}>
                                <input
                                    type="text"
                                    id="adj-product-search"
                                    className={`adj-input ${fieldErrors.product ? 'adj-input--error' : ''}`}
                                    placeholder={loadingProducts ? 'Loading products…' : 'Type product name, SKU or barcode…'}
                                    value={keyword}
                                    onChange={(e) => {
                                        setKeyword(e.target.value);
                                        setIsDropdownOpen(true);
                                    }}
                                    onFocus={() => setIsDropdownOpen(true)}
                                    disabled={loadingProducts}
                                />
                                {isDropdownOpen && searchResults.length > 0 && (
                                    <ul className="adj-search-dropdown" role="listbox">
                                        {searchResults.map(p => (
                                            <li
                                                key={p.productId}
                                                className="adj-search-item"
                                                onClick={() => handleSelectProduct(p)}
                                                role="option"
                                                aria-selected={selectedProduct?.productId === p.productId}
                                            >
                                                <div className="adj-search-item__main">
                                                    <span className="adj-search-item__name">{p.productName}</span>
                                                    <span className="adj-search-item__code">SKU: {p.productCode} {p.barcode ? `• Barcode: ${p.barcode}` : ''}</span>
                                                </div>
                                                <span className="adj-search-item__stock">
                                                    On Hand: {formatQty(p.stockQuantity)} {p.unit}
                                                </span>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        ) : (
                            <form onSubmit={handleBarcodeSubmit} className="adj-input-group">
                                <input
                                    type="text"
                                    id="adj-barcode-input"
                                    className={`adj-input ${barcodeError ? 'adj-input--error' : ''}`}
                                    placeholder="Scan barcode or press Enter to lookup…"
                                    value={barcodeInput}
                                    onChange={(e) => setBarcodeInput(e.target.value)}
                                    autoFocus
                                />
                                <button type="submit" className="adj-btn adj-btn--primary" style={{ marginLeft: '8px' }}>
                                    Find
                                </button>
                            </form>
                        )}

                        {barcodeError && (
                            <span className="adj-field-error">{barcodeError}</span>
                        )}
                        {fieldErrors.product && (
                            <span className="adj-field-error">{fieldErrors.product}</span>
                        )}
                    </div>

                    {/* Product Details Display (SRS Group #2) */}
                    <div style={{ marginTop: '16px' }}>
                        <div className="adj-label" style={{ marginBottom: '8px' }}>Product Information (Read-only)</div>
                        {selectedProduct ? (
                            <div className="adj-product-details">
                                <div className="adj-detail-item adj-detail-item--full">
                                    <span className="adj-detail-label">Product Name</span>
                                    <span className="adj-detail-val" style={{ fontSize: '16px', fontWeight: 600 }}>
                                        {selectedProduct.productName}
                                    </span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">SKU / Code</span>
                                    <span className="adj-detail-val">{selectedProduct.productCode}</span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">Barcode</span>
                                    <span className="adj-detail-val">{selectedProduct.barcode || '—'}</span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">Category</span>
                                    <span className="adj-detail-val">{selectedProduct.category?.categoryName || 'General'}</span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">Unit of Measure</span>
                                    <span className="adj-detail-val">{selectedProduct.unit}</span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">Safety Minimum</span>
                                    <span className="adj-detail-val">{formatQty(selectedProduct.minimumStockLevel)}</span>
                                </div>
                                <div className="adj-detail-item">
                                    <span className="adj-detail-label">Current Stock on Hand</span>
                                    <span className="adj-stock-highlight">
                                        {formatQty(selectedProduct.stockQuantity)}
                                        <span className="adj-stock-unit">{selectedProduct.unit}</span>
                                    </span>
                                </div>
                            </div>
                        ) : (
                            <div className="adj-empty-product">
                                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                                </svg>
                                <span>No product selected. Use the search input or barcode scanner above.</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* ── RIGHT COLUMN: Adjustment Details & Calculation ── */}
                <div className="adj-card">
                    <div className="adj-card__header">
                        <h2 className="adj-card__title">
                            <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" color="#3b82f6">
                                <path fillRule="evenodd" d="M4 2a2 2 0 00-2 2v11a3 3 0 106 0V4a2 2 0 00-2-2H4zm1 14a1 1 0 100-2 1 1 0 000 2zm5-1.757l4.9-4.9a2 2 0 000-2.828L13.485 5.1a2 2 0 00-2.828 0L10 5.757v8.486zM16 18H9.071l6-6H16a2 2 0 012 2v2a2 2 0 01-2 2z" clipRule="evenodd" />
                            </svg>
                            2. Adjustment Details
                        </h2>
                    </div>

                    {/* Adjustment Type Selector */}
                    <div className="adj-form-group">
                        <label className="adj-label">
                            Adjustment Type <span className="adj-req">*</span>
                        </label>
                        <div className="adj-type-toggle">
                            <button
                                type="button"
                                className={`adj-type-btn adj-type-btn--inc ${adjustmentType === 'INCREASE' ? 'adj-type-btn--active' : ''}`}
                                onClick={() => setAdjustmentType('INCREASE')}
                            >
                                <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
                                </svg>
                                Increase (+)
                            </button>
                            <button
                                type="button"
                                className={`adj-type-btn adj-type-btn--dec ${adjustmentType === 'DECREASE' ? 'adj-type-btn--active' : ''}`}
                                onClick={() => setAdjustmentType('DECREASE')}
                            >
                                <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M3 10a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clipRule="evenodd" />
                                </svg>
                                Decrease (-)
                            </button>
                        </div>
                    </div>

                    {/* Adjustment Quantity Input */}
                    <div className="adj-form-group">
                        <label htmlFor="adj-qty-input" className="adj-label">
                            Adjustment Quantity <span className="adj-req">*</span>
                            <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '6px' }}>
                                (Whole positive integer)
                            </span>
                        </label>
                        <input
                            type="number"
                            id="adj-qty-input"
                            min="1"
                            step="1"
                            className={`adj-input ${fieldErrors.quantity ? 'adj-input--error' : ''}`}
                            placeholder="e.g. 5"
                            value={quantityInput}
                            onChange={(e) => {
                                setQuantityInput(e.target.value);
                                setFieldErrors(prev => ({ ...prev, quantity: null }));
                            }}
                        />
                        {fieldErrors.quantity && (
                            <span className="adj-field-error">{fieldErrors.quantity}</span>
                        )}
                    </div>

                    {/* Calculated Expected New Stock (Live Preview) */}
                    <div className={`adj-calc-box ${isStockNegative ? 'adj-calc-box--danger' : ''}`}>
                        <div className="adj-calc-flow">
                            <div className="adj-calc-item">
                                <div className="adj-calc-item__lbl">Current Stock</div>
                                <div className="adj-calc-item__val">{formatQty(currentStock)}</div>
                            </div>
                            <div className="adj-calc-op">
                                {adjustmentType === 'INCREASE' ? '+' : '−'}
                            </div>
                            <div className="adj-calc-item">
                                <div className="adj-calc-item__lbl">Adjustment</div>
                                <div className={`adj-calc-item__val ${adjustmentType === 'INCREASE' ? 'adj-calc-item__val--inc' : 'adj-calc-item__val--dec'}`}>
                                    {isValidPositiveInt ? formatQty(parsedQty) : '—'}
                                </div>
                            </div>
                            <div className="adj-calc-op">=</div>
                            <div className="adj-calc-item">
                                <div className="adj-calc-item__lbl">New Stock</div>
                                <div className={`adj-calc-item__val ${isStockNegative ? 'adj-calc-item__val--danger' : ''}`}>
                                    {calculatedNewStock !== null ? formatQty(calculatedNewStock) : '—'}
                                </div>
                            </div>
                        </div>

                        {isStockNegative && (
                            <div className="adj-calc-error">
                                ⚠ Resulting stock cannot be negative! Maximum allowed decrease is {formatQty(currentStock)}.
                            </div>
                        )}
                    </div>

                    {/* Audit Session Reference (Optional) */}
                    <div className="adj-form-group">
                        <label htmlFor="adj-session-ref" className="adj-label">
                            Audit Session Reference (Optional)
                            <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '6px' }}>
                                (Linked Stock Check session ID)
                            </span>
                        </label>
                        <input
                            type="text"
                            id="adj-session-ref"
                            className="adj-input"
                            placeholder="Leave blank for direct manual adjustment"
                            value={stockCheckId}
                            onChange={(e) => setStockCheckId(e.target.value)}
                        />
                    </div>

                    {/* Adjustment Reason */}
                    <div className="adj-form-group">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                            <label htmlFor="adj-reason-input" className="adj-label">
                                Adjustment Reason <span className="adj-req">*</span>
                            </label>
                            <span className="adj-char-counter">
                                {reason.length} / 255
                            </span>
                        </div>
                        <textarea
                            id="adj-reason-input"
                            className={`adj-textarea ${fieldErrors.reason ? 'adj-input--error' : ''}`}
                            placeholder="Provide a mandatory explanation (e.g. damaged during unloading, count discrepancy, expired)..."
                            maxLength={255}
                            value={reason}
                            onChange={(e) => {
                                setReason(e.target.value);
                                setFieldErrors(prev => ({ ...prev, reason: null }));
                            }}
                        />
                        {fieldErrors.reason && (
                            <span className="adj-field-error">{fieldErrors.reason}</span>
                        )}

                        {/* Quick Selection Tags */}
                        <div className="adj-quick-reasons">
                            {QUICK_REASONS.map(tag => (
                                <button
                                    key={tag}
                                    type="button"
                                    className="adj-reason-chip"
                                    onClick={() => {
                                        setReason(tag);
                                        setFieldErrors(prev => ({ ...prev, reason: null }));
                                    }}
                                >
                                    + {tag}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="adj-actions">
                        <button
                            type="button"
                            className="adj-btn adj-btn--ghost"
                            onClick={() => navigate('/inventory')}
                            disabled={submitting}
                        >
                            Cancel
                        </button>
                        <button
                            type="button"
                            id="adj-confirm-btn"
                            className="adj-btn adj-btn--primary"
                            onClick={handleOpenConfirm}
                            disabled={submitting || !isFormReady}
                        >
                            Confirm Adjustment
                        </button>
                    </div>
                </div>
            </div>

            {/* ── Confirmation Modal Dialog ── */}
            {showConfirmModal && (
                <div className="adj-modal-backdrop" role="dialog" aria-modal="true">
                    <div className="adj-modal">
                        <div className="adj-modal__header">
                            <h3 className="adj-modal__title">Confirm Stock Adjustment</h3>
                            <button
                                type="button"
                                className="adj-alert__close"
                                onClick={() => setShowConfirmModal(false)}
                                disabled={submitting}
                            >
                                ×
                            </button>
                        </div>
                        <div className="adj-modal__body">
                            <div className="adj-modal-summary">
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">Product:</span>
                                    <span className="adj-modal-row__val">{selectedProduct?.productName}</span>
                                </div>
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">SKU:</span>
                                    <span className="adj-modal-row__val">{selectedProduct?.productCode}</span>
                                </div>
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">Adjustment:</span>
                                    <span className="adj-modal-row__val" style={{ color: adjustmentType === 'INCREASE' ? '#16a34a' : '#dc2626' }}>
                                        {adjustmentType === 'INCREASE' ? `+${formatQty(parsedQty)}` : `-${formatQty(parsedQty)}`} {selectedProduct?.unit}
                                    </span>
                                </div>
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">Current Stock:</span>
                                    <span className="adj-modal-row__val">{formatQty(currentStock)} {selectedProduct?.unit}</span>
                                </div>
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">New Stock:</span>
                                    <span className="adj-modal-row__val" style={{ color: '#0284c7' }}>
                                        {formatQty(calculatedNewStock)} {selectedProduct?.unit}
                                    </span>
                                </div>
                                <div className="adj-modal-row">
                                    <span className="adj-modal-row__label">Reason:</span>
                                    <span className="adj-modal-row__val" style={{ maxWidth: '240px', textAlign: 'right' }}>
                                        {reason}
                                    </span>
                                </div>
                                {stockCheckId && (
                                    <div className="adj-modal-row">
                                        <span className="adj-modal-row__label">Audit Session:</span>
                                        <span className="adj-modal-row__val">#{stockCheckId}</span>
                                    </div>
                                )}
                            </div>

                            <p className="adj-modal-warning">
                                ⚠ This action will immediately update the recorded inventory on hand and create an immutable Stock Movement entry in the system.
                            </p>
                        </div>
                        <div className="adj-modal__footer">
                            <button
                                type="button"
                                className="adj-btn adj-btn--ghost"
                                onClick={() => setShowConfirmModal(false)}
                                disabled={submitting}
                            >
                                Back / Review
                            </button>
                            <button
                                type="button"
                                id="adj-modal-submit-btn"
                                className="adj-btn adj-btn--primary"
                                onClick={handleConfirmSubmit}
                                disabled={submitting}
                            >
                                {submitting ? 'Applying…' : 'Yes, Apply Adjustment'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

