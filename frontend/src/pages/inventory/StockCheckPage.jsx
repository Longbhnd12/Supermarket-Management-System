import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import InventoryService from '../../services/inventoryService';
import './StockCheckPage.css';

function formatQty(n) {
    if (n == null || isNaN(n)) return '0';
    return Number(n).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 });
}

function formatVND(n) {
    if (n == null || isNaN(n)) return '0 ₫';
    const num = Number(n);
    const prefix = num > 0 ? '+' : '';
    return prefix + num.toLocaleString('vi-VN') + ' ₫';
}

function formatDateTime(dtStr) {
    if (!dtStr) return '—';
    try {
        const d = new Date(dtStr);
        return d.toLocaleDateString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return dtStr;
    }
}

export default function StockCheckPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') === 'history' ? 'history' : 'new';

    // ── Session State for New Count ──
    const [sessionNumber, setSessionNumber] = useState('');
    const [loadingSession, setLoadingSession] = useState(true);

    // ── Products Directory ──
    const [allProducts, setAllProducts] = useState([]);
    const [loadingProducts, setLoadingProducts] = useState(true);

    // ── Input & Barcode State ──
    const [barcodeInput, setBarcodeInput] = useState('');
    const [barcodeAlert, setBarcodeAlert] = useState({ type: '', message: '' });
    const barcodeInputRef = useRef(null);

    // ── Manual Search State ──
    const [searchKeyword, setSearchKeyword] = useState('');
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const searchDropdownRef = useRef(null);

    // ── Audit Items List ──
    // Each item: { productId, productCode, productName, barcode, categoryName, unit, systemQuantity, actualQuantity, reason, error }
    const [items, setItems] = useState([]);
    const [highlightedRowId, setHighlightedRowId] = useState(null);

    // ── Page & Submission State ──
    const [pageError, setPageError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // ── Audit Sessions History State ──
    const [pastSessions, setPastSessions] = useState([]);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [historySearch, setHistorySearch] = useState('');

    // ── 1. Fetch History Sessions ──
    const fetchHistory = useCallback(() => {
        setLoadingHistory(true);
        InventoryService.getAllStockCheckSessions()
            .then(res => {
                setPastSessions(res.data?.data || []);
            })
            .catch(() => {
                setPageError('Failed to load stock check sessions history.');
            })
            .finally(() => {
                setLoadingHistory(false);
            });
    }, []);

    useEffect(() => {
        if (activeTab === 'history') {
            fetchHistory();
        }
    }, [activeTab, fetchHistory]);

    // ── 2. Initialize Session Number & Load Products ──
    useEffect(() => {
        let isMounted = true;

        // Fetch Next Session ID (SC-YYYYMMDD-001)
        InventoryService.getNextStockCheckSession()
            .then(res => {
                if (!isMounted) return;
                const nextNum = res.data?.data || `SC-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-001`;
                setSessionNumber(nextNum);
            })
            .catch(() => {
                if (!isMounted) return;
                const fallback = `SC-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-001`;
                setSessionNumber(fallback);
            })
            .finally(() => {
                if (isMounted) setLoadingSession(false);
            });

        // Load Active Products
        ProductService.getAll({ status: 'ACTIVE' })
            .then(res => {
                if (!isMounted) return;
                const prods = res.data?.data || [];
                setAllProducts(prods);
            })
            .catch(() => {
                if (!isMounted) return;
                setPageError('Failed to load product catalog. Please refresh the page.');
            })
            .finally(() => {
                if (isMounted) setLoadingProducts(false);
            });

        return () => { isMounted = false; };
    }, []);

    // ── 3. Keep Barcode Scanner Focused when in New Count tab ──
    useEffect(() => {
        if (activeTab === 'new' && !loadingSession && !loadingProducts && barcodeInputRef.current) {
            barcodeInputRef.current.focus();
        }
    }, [activeTab, loadingSession, loadingProducts]);

    // Close dropdown on outside click
    useEffect(() => {
        function handleClickOutside(e) {
            if (searchDropdownRef.current && !searchDropdownRef.current.contains(e.target)) {
                setIsDropdownOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // ── 4. Filtered Manual Search Results ──
    const searchResults = useMemo(() => {
        if (!searchKeyword.trim()) return [];
        const q = searchKeyword.toLowerCase().trim();
        return allProducts
            .filter(p =>
                (p.productName && p.productName.toLowerCase().includes(q)) ||
                (p.productCode && p.productCode.toLowerCase().includes(q)) ||
                (p.barcode && p.barcode.toLowerCase().includes(q))
            )
            .slice(0, 8);
    }, [allProducts, searchKeyword]);

    // ── 5. Add Product to Audit Session (BR-04 Prevent Duplicate) ──
    const addProductToSession = (product) => {
        setBarcodeAlert({ type: '', message: '' });
        setPageError('');

        // BR-04: Check if already added
        const exists = items.find(i => i.productId === product.productId);
        if (exists) {
            setBarcodeAlert({
                type: 'warning',
                message: `Product "${product.productName}" (${product.productCode}) is already in this stock check session.`
            });
            setHighlightedRowId(product.productId);
            setTimeout(() => setHighlightedRowId(null), 2500);

            if (barcodeInputRef.current) barcodeInputRef.current.focus();
            return;
        }

        const categoryName = product.categoryName || (product.category && product.category.categoryName) || 'General';
        const sysQty = product.stockQuantity != null ? Number(product.stockQuantity) : 0;

        const newItem = {
            productId: product.productId,
            productCode: product.productCode,
            productName: product.productName,
            barcode: product.barcode || '',
            categoryName: categoryName,
            unit: product.unit || 'pcs',
            systemQuantity: sysQty,
            actualQuantity: '', // leave empty ready for count entry
            reason: '',
            error: ''
        };

        setItems(prev => [newItem, ...prev]);
        setHighlightedRowId(product.productId);
        setTimeout(() => setHighlightedRowId(null), 2000);

        setSearchKeyword('');
        setIsDropdownOpen(false);
        setBarcodeInput('');
        if (barcodeInputRef.current) barcodeInputRef.current.focus();
    };

    // ── Auto-select product if productId query parameter is present (e.g. from recount) ──
    const urlProductId = searchParams.get('productId');
    const autoSelectedRef = useRef(false);

    useEffect(() => {
        if (urlProductId && allProducts.length > 0 && activeTab === 'new' && !autoSelectedRef.current) {
            const match = allProducts.find(p => String(p.productId) === String(urlProductId));
            if (match) {
                autoSelectedRef.current = true;
                addProductToSession(match);
            }
        }
    }, [urlProductId, allProducts, activeTab]);

    // ── 6. Handle Barcode Enter ──
    const handleBarcodeKeyDown = (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const rawCode = barcodeInput.trim();
            if (!rawCode) return;

            const matched = allProducts.find(p => p.barcode && p.barcode.trim() === rawCode);
            if (!matched) {
                setBarcodeAlert({
                    type: 'error',
                    message: `No active product found with barcode: "${rawCode}". Check SKU or use manual search.`
                });
                setBarcodeInput('');
                return;
            }

            addProductToSession(matched);
            setBarcodeInput('');
        }
    };

    // ── 7. Handle Actual Quantity Change (BR-03 Non-negative integers) ──
    const handleActualQtyChange = (productId, val) => {
        setItems(prev => prev.map(item => {
            if (item.productId !== productId) return item;

            let error = '';
            if (val === '') {
                error = 'Physical count is required';
            } else {
                const num = Number(val);
                if (isNaN(num)) {
                    error = 'Must be a valid number';
                } else if (num < 0) {
                    error = 'Must be non-negative (>= 0)';
                } else if (!Number.isInteger(num)) {
                    error = 'Must be a whole integer';
                }
            }

            return {
                ...item,
                actualQuantity: val,
                error
            };
        }));
    };

    // ── 8. Handle Reason Change ──
    const handleReasonChange = (productId, reasonVal) => {
        setItems(prev => prev.map(item =>
            item.productId === productId ? { ...item, reason: reasonVal } : item
        ));
    };

    // ── 9. Remove Item from Session ──
    const handleRemoveItem = (productId) => {
        setItems(prev => prev.filter(i => i.productId !== productId));
    };

    // ── 10. Calculate Telemetry & Stats for Current Session ──
    const totalSkus = items.length;
    let matchedCount = 0;
    let discrepancyCount = 0;
    let hasValidationErrors = false;

    items.forEach(i => {
        if (i.error || i.actualQuantity === '') {
            hasValidationErrors = true;
        } else {
            const act = Number(i.actualQuantity);
            const diff = act - i.systemQuantity;
            if (diff === 0) matchedCount++;
            else discrepancyCount++;
        }
    });

    const canSubmit = totalSkus > 0 && !hasValidationErrors && !submitting;

    // ── 11. Submit Stock Check (Compare Differences) ──
    const handleCompareDifferences = async () => {
        if (!canSubmit) return;

        setSubmitting(true);
        setPageError('');

        try {
            const payload = {
                checkNumber: sessionNumber,
                items: items.map(item => ({
                    productId: item.productId,
                    actualQuantity: Number(item.actualQuantity),
                    reason: item.reason?.trim() || null
                }))
            };

            const res = await InventoryService.performStockCheck(payload);
            const sessionData = res.data?.data;
            const targetSession = sessionData?.checkNumber || sessionNumber;

            // Navigate to Screen 32: Stock Difference
            navigate(`/inventory/stock-difference?session=${encodeURIComponent(targetSession)}`);
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Failed to submit stock check. Please try again.';
            setPageError(msg);
            setSubmitting(false);
        }
    };

    // ── Filtered Past Sessions for History Tab ──
    const filteredPastSessions = useMemo(() => {
        if (!historySearch.trim()) return pastSessions;
        const q = historySearch.toLowerCase().trim();
        return pastSessions.filter(s =>
            (s.checkNumber && s.checkNumber.toLowerCase().includes(q)) ||
            (s.checkedByUsername && s.checkedByUsername.toLowerCase().includes(q))
        );
    }, [pastSessions, historySearch]);

    return (
        <div className="sc-page">
            {/* ── Header & Breadcrumbs ── */}
            <header className="sc-header">
                <nav className="sc-breadcrumb" aria-label="Breadcrumb">
                    <Link to="/inventory">Inventory</Link>
                    <span>/</span>
                    <span>Stock Check</span>
                </nav>
                <div className="sc-header__row">
                    <div>
                        <h1 className="sc-title">
                            {activeTab === 'history' ? 'Stock Check Audit Sessions' : 'Stock Check (Physical Count)'}
                        </h1>
                        <p className="sc-subtitle">
                            {activeTab === 'history'
                                ? 'Review previous physical stock count sessions and reopen their variance reconciliation screens (UC32).'
                                : 'Scan barcodes or select products to record on-shelf physical inventory and detect variances (UC32).'}
                        </p>
                    </div>
                    {activeTab === 'new' && (
                        <div className="sc-session-tag">
                            <span className="sc-session-tag__label">Active Session:</span>
                            <strong className="sc-session-tag__code">{sessionNumber || 'Loading…'}</strong>
                        </div>
                    )}
                </div>
            </header>

            {/* ── Top Tabs Switcher ── */}
            <div className="sc-tabs-bar">
                <button
                    type="button"
                    className={`sc-tab-btn ${activeTab === 'new' ? 'sc-tab-btn--active' : ''}`}
                    onClick={() => setSearchParams({ tab: 'new' })}
                >
                    + New Physical Count
                </button>
                <button
                    type="button"
                    className={`sc-tab-btn ${activeTab === 'history' ? 'sc-tab-btn--active' : ''}`}
                    onClick={() => setSearchParams({ tab: 'history' })}
                >
                    📋 Audit Sessions History
                    {pastSessions.length > 0 && <span className="sc-tab-count">{pastSessions.length}</span>}
                </button>
            </div>

            {/* ── Global Page Error Banner ── */}
            {pageError && (
                <div className="sc-alert sc-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <span>{pageError}</span>
                    <button type="button" className="sc-alert__close" onClick={() => setPageError('')}>×</button>
                </div>
            )}

            {/* ── TAB 1: NEW PHYSICAL COUNT ── */}
            {activeTab === 'new' ? (
                <>
                    {/* Barcode Scanner / Feedback Alert */}
                    {barcodeAlert.message && (
                        <div className={`sc-alert sc-alert--${barcodeAlert.type}`} role="alert">
                            <span>{barcodeAlert.message}</span>
                            <button type="button" className="sc-alert__close" onClick={() => setBarcodeAlert({ type: '', message: '' })}>×</button>
                        </div>
                    )}

                    {/* Input Section: Barcode Listener & Manual Search */}
                    <div className="sc-inputs-card">
                        <div className="sc-inputs-grid">
                            {/* Barcode Scanner Input */}
                            <div className="sc-input-group">
                                <label className="sc-label" htmlFor="barcode-scanner-input">
                                    <span className="sc-label__icon">📷</span>
                                    Barcode Scanner Listener
                                </label>
                                <div className="sc-barcode-wrap">
                                    <input
                                        id="barcode-scanner-input"
                                        ref={barcodeInputRef}
                                        type="text"
                                        className="sc-input sc-input--barcode"
                                        placeholder="Scan barcode with reader or enter barcode and press Enter…"
                                        value={barcodeInput}
                                        onChange={(e) => setBarcodeInput(e.target.value)}
                                        onKeyDown={handleBarcodeKeyDown}
                                        autoFocus
                                    />
                                    <span className="sc-barcode-hint">Auto-focus active</span>
                                </div>
                            </div>

                            {/* Fallback Manual Search */}
                            <div className="sc-input-group" ref={searchDropdownRef}>
                                <label className="sc-label" htmlFor="manual-search-input">
                                    <span className="sc-label__icon">🔍</span>
                                    Manual Product Search (SKU or Name)
                                </label>
                                <div className="sc-search-wrap">
                                    <input
                                        id="manual-search-input"
                                        type="text"
                                        className="sc-input"
                                        placeholder="Search by SKU code or product name…"
                                        value={searchKeyword}
                                        onChange={(e) => {
                                            setSearchKeyword(e.target.value);
                                            setIsDropdownOpen(true);
                                        }}
                                        onFocus={() => {
                                            if (searchKeyword.trim()) setIsDropdownOpen(true);
                                        }}
                                    />
                                    {isDropdownOpen && searchResults.length > 0 && (
                                        <ul className="sc-search-dropdown">
                                            {searchResults.map(p => {
                                                const isAlreadyIn = items.some(i => i.productId === p.productId);
                                                return (
                                                    <li
                                                        key={p.productId}
                                                        className={`sc-search-item ${isAlreadyIn ? 'sc-search-item--added' : ''}`}
                                                        onClick={() => addProductToSession(p)}
                                                    >
                                                        <div className="sc-search-item__main">
                                                            <span className="sc-search-item__sku">{p.productCode}</span>
                                                            <span className="sc-search-item__name">{p.productName}</span>
                                                        </div>
                                                        <div className="sc-search-item__meta">
                                                            <span>Stock: {formatQty(p.stockQuantity)} {p.unit}</span>
                                                            {isAlreadyIn && <span className="sc-tag--in-session">Already added</span>}
                                                        </div>
                                                    </li>
                                                );
                                            })}
                                        </ul>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Counting Table Section */}
                    <div className="sc-table-card">
                        <div className="sc-table-card__header">
                            <div className="sc-table-card__title">
                                <h2>Counted Items in Session ({totalSkus})</h2>
                                {totalSkus > 0 && (
                                    <div className="sc-table-card__badges">
                                        <span className="sc-summary-badge sc-summary-badge--matched">
                                            {matchedCount} Balanced
                                        </span>
                                        <span className="sc-summary-badge sc-summary-badge--diff">
                                            {discrepancyCount} Discrepancies
                                        </span>
                                    </div>
                                )}
                            </div>
                            {totalSkus > 0 && (
                                <button
                                    type="button"
                                    className="sc-btn sc-btn--ghost sc-btn--sm"
                                    onClick={() => {
                                        if (window.confirm('Clear all items from this stock check session?')) {
                                            setItems([]);
                                        }
                                    }}
                                >
                                    Clear All
                                </button>
                            )}
                        </div>

                        {items.length === 0 ? (
                            <div className="sc-empty">
                                <div className="sc-empty__icon">📋</div>
                                <h3>No items in audit session yet</h3>
                                <p>Scan a product barcode or use manual search above to begin recording physical counts.</p>
                            </div>
                        ) : (
                            <div className="sc-table-responsive">
                                <table className="sc-table">
                                    <thead>
                                        <tr>
                                            <th style={{ width: '40px' }}>#</th>
                                            <th>Product / SKU</th>
                                            <th style={{ width: '130px', textAlign: 'right' }}>System Qty</th>
                                            <th style={{ width: '150px' }}>Physical Count</th>
                                            <th style={{ width: '140px', textAlign: 'center' }}>Variance</th>
                                            <th>Discrepancy Reason / Note</th>
                                            <th style={{ width: '60px', textAlign: 'center' }}>Action</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {items.map((item, idx) => {
                                            const hasQty = item.actualQuantity !== '' && !isNaN(Number(item.actualQuantity));
                                            const diff = hasQty ? (Number(item.actualQuantity) - item.systemQuantity) : null;
                                            const isRowHighlighted = highlightedRowId === item.productId;

                                            return (
                                                <tr
                                                    key={item.productId}
                                                    className={`sc-row ${isRowHighlighted ? 'sc-row--highlight' : ''}`}
                                                >
                                                    <td className="sc-cell--muted">{idx + 1}</td>
                                                    <td>
                                                        <div className="sc-prod-info">
                                                            <span className="sc-prod-name">{item.productName}</span>
                                                            <div className="sc-prod-meta">
                                                                <span className="sc-prod-sku">{item.productCode}</span>
                                                                {item.barcode && <span className="sc-prod-barcode">|| {item.barcode}</span>}
                                                                <span className="sc-prod-category">{item.categoryName}</span>
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <span className="sc-system-qty">
                                                            {formatQty(item.systemQuantity)}
                                                        </span>
                                                        <span className="sc-unit"> {item.unit}</span>
                                                    </td>
                                                    <td>
                                                        <div className="sc-count-input-wrap">
                                                            <input
                                                                type="number"
                                                                min="0"
                                                                step="1"
                                                                className={`sc-input sc-input--count ${item.error ? 'sc-input--error' : ''}`}
                                                                placeholder="Qty (>= 0)"
                                                                value={item.actualQuantity}
                                                                onChange={(e) => handleActualQtyChange(item.productId, e.target.value)}
                                                            />
                                                            <span className="sc-count-unit">{item.unit}</span>
                                                        </div>
                                                        {item.error && <span className="sc-field-error">{item.error}</span>}
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        {diff === null ? (
                                                            <span className="sc-badge sc-badge--pending">Pending input</span>
                                                        ) : diff === 0 ? (
                                                            <span className="sc-badge sc-badge--balanced">Balanced (0)</span>
                                                        ) : diff > 0 ? (
                                                            <span className="sc-badge sc-badge--surplus">+{formatQty(diff)} Surplus</span>
                                                        ) : (
                                                            <span className="sc-badge sc-badge--shortage">{formatQty(diff)} Shortage</span>
                                                        )}
                                                    </td>
                                                    <td>
                                                        <input
                                                            type="text"
                                                            className="sc-input sc-input--reason"
                                                            placeholder={diff && diff !== 0 ? 'Explain variance (optional)…' : 'Notes (optional)…'}
                                                            value={item.reason}
                                                            onChange={(e) => handleReasonChange(item.productId, e.target.value)}
                                                        />
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <button
                                                            type="button"
                                                            className="sc-btn-icon sc-btn-icon--danger"
                                                            title="Remove item from audit"
                                                            onClick={() => handleRemoveItem(item.productId)}
                                                        >
                                                            ✕
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {/* Footer Actions */}
                        <div className="sc-table-card__footer">
                            <div className="sc-footer-left">
                                <button
                                    type="button"
                                    className="sc-btn sc-btn--secondary"
                                    onClick={() => navigate('/inventory')}
                                >
                                    Cancel / Back to Inventory
                                </button>
                            </div>

                            <div className="sc-footer-right">
                                {hasValidationErrors && (
                                    <span className="sc-footer-warning">
                                        Please correct invalid counts before comparing differences.
                                    </span>
                                )}
                                <button
                                    type="button"
                                    className="sc-btn sc-btn--primary sc-btn--lg"
                                    disabled={!canSubmit}
                                    onClick={handleCompareDifferences}
                                >
                                    {submitting ? 'Comparing Differences…' : 'Compare Differences →'}
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            ) : (
                /* ── TAB 2: AUDIT SESSIONS HISTORY ── */
                <div className="sc-history-container">
                    {/* Clear distinction notice from UC34 Stock Movement History */}
                    <div className="sc-history-notice">
                        <span className="sc-notice-icon">ℹ️</span>
                        <div className="sc-notice-text">
                            <strong>Stock Check Audit Sessions</strong> records snapshots of on-shelf physical counts and reconciled variances (UC32).
                            To view individual transactional inventory movements (Goods Receipts, Sales, Adjustments), open{' '}
                            <Link to="/inventory/stock-history" className="sc-notice-link">
                                Stock Movement History (UC34) →
                            </Link>
                        </div>
                    </div>

                    <div className="sc-table-card">
                        <div className="sc-table-card__header">
                            <div className="sc-history-search-wrap">
                                <input
                                    type="text"
                                    className="sc-input sc-input--search"
                                    placeholder="Search by session ID or auditor…"
                                    value={historySearch}
                                    onChange={(e) => setHistorySearch(e.target.value)}
                                />
                                <button
                                    type="button"
                                    className="sc-btn sc-btn--secondary sc-btn--sm"
                                    onClick={fetchHistory}
                                    disabled={loadingHistory}
                                >
                                    ↻ Refresh History
                                </button>
                            </div>
                            <span className="sc-history-total">
                                Total Sessions: <strong>{filteredPastSessions.length}</strong>
                            </span>
                        </div>

                        {loadingHistory ? (
                            <div className="sc-empty">
                                <div className="sc-spinner" />
                                <p>Loading audit sessions history…</p>
                            </div>
                        ) : filteredPastSessions.length === 0 ? (
                            <div className="sc-empty">
                                <div className="sc-empty__icon">📋</div>
                                <h3>No previous audit sessions found</h3>
                                <p>Perform and submit your first physical count to see audit session history here.</p>
                                <button
                                    type="button"
                                    className="sc-btn sc-btn--primary"
                                    style={{ marginTop: '12px' }}
                                    onClick={() => setSearchParams({ tab: 'new' })}
                                >
                                    Start New Stock Check
                                </button>
                            </div>
                        ) : (
                            <div className="sc-table-responsive">
                                <table className="sc-table">
                                    <thead>
                                        <tr>
                                            <th>Session ID</th>
                                            <th>Audit Date</th>
                                            <th>Auditor</th>
                                            <th style={{ textAlign: 'center' }}>SKUs Audited</th>
                                            <th style={{ textAlign: 'center' }}>Discrepancies</th>
                                            <th style={{ textAlign: 'right' }}>Net Financial Impact</th>
                                            <th style={{ textAlign: 'center' }}>Reconciliation Status</th>
                                            <th style={{ textAlign: 'center', width: '190px' }}>Action</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredPastSessions.map(session => {
                                            const hasDiscrepancy = session.discrepancyCount > 0;
                                            const unresolvedCount = (session.items || []).filter(
                                                i => Number(i.difference || 0) !== 0 && i.status !== 'COMPLETED' && i.status !== 'ADJUSTED'
                                            ).length;

                                            return (
                                                <tr key={session.checkNumber} className="sc-row">
                                                    <td>
                                                        <strong className="sc-code-link">{session.checkNumber}</strong>
                                                    </td>
                                                    <td>{formatDateTime(session.checkedAt)}</td>
                                                    <td>
                                                        <span className="sc-auditor-name">{session.checkedByUsername || 'Staff'}</span>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <strong>{session.totalSkus}</strong>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        {hasDiscrepancy ? (
                                                            <span className="sc-summary-badge sc-summary-badge--diff">
                                                                {session.discrepancyCount} item(s)
                                                            </span>
                                                        ) : (
                                                            <span className="sc-summary-badge sc-summary-badge--matched">
                                                                0 (All Matched)
                                                            </span>
                                                        )}
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <span className={Number(session.netFinancialVariance || 0) < 0 ? 'sc-text--danger' : Number(session.netFinancialVariance || 0) > 0 ? 'sc-text--primary' : 'sc-text--success'}>
                                                            {formatVND(session.netFinancialVariance)}
                                                        </span>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        {!hasDiscrepancy ? (
                                                            <span className="sc-status-pill sc-status-pill--success">
                                                                ✓ All Balanced
                                                            </span>
                                                        ) : unresolvedCount === 0 ? (
                                                            <span className="sc-status-pill sc-status-pill--success">
                                                                ✓ Fully Reconciled
                                                            </span>
                                                        ) : (
                                                            <span className="sc-status-pill sc-status-pill--warning">
                                                                Pending Reconcile ({unresolvedCount})
                                                            </span>
                                                        )}
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <button
                                                            type="button"
                                                            className="sc-btn sc-btn--primary sc-btn--sm"
                                                            onClick={() => navigate(`/inventory/stock-difference?session=${encodeURIComponent(session.checkNumber)}`)}
                                                            title="Reopen Stock Difference reconciliation results"
                                                        >
                                                            Reopen Difference →
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
