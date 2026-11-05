import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import InventoryService from '../../services/inventoryService';
import './StockDifferencePage.css';

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

export default function StockDifferencePage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const sessionParam = searchParams.get('session');

    // ── State ──
    const [sessionData, setSessionData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'DISCREPANCIES' | 'BALANCED'

    // ── Unresolved Warning Modal State ──
    const [showLeaveModal, setShowLeaveModal] = useState(false);
    const [leaveDestination, setLeaveDestination] = useState('/inventory');

    // ── Edit Physical Count Modal State ──
    const [editingItem, setEditingItem] = useState(null);
    const [editPhysicalCount, setEditPhysicalCount] = useState('');
    const [editReason, setEditReason] = useState('');
    const [editSubmitting, setEditSubmitting] = useState(false);
    const [editError, setEditError] = useState('');
    const [successMessage, setSuccessMessage] = useState('');

    // ── Fetch Session Data ──
    const fetchSession = useCallback(async () => {
        if (!sessionParam) {
            setError('No audit session number provided in URL.');
            setLoading(false);
            return;
        }

        setLoading(true);
        setError('');

        try {
            const res = await InventoryService.getStockCheckSession(sessionParam);
            setSessionData(res.data?.data);
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Failed to load stock difference session.';
            setError(msg);
        } finally {
            setLoading(false);
        }
    }, [sessionParam]);

    useEffect(() => {
        fetchSession();
    }, [fetchSession]);

    // ── Filtered Items in Table ──
    const filteredItems = useMemo(() => {
        if (!sessionData?.items) return [];

        return sessionData.items.filter(item => {
            // Keyword filter
            if (searchKeyword.trim()) {
                const q = searchKeyword.toLowerCase().trim();
                const matchName = item.productName && item.productName.toLowerCase().includes(q);
                const matchSku = item.productCode && item.productCode.toLowerCase().includes(q);
                const matchBarcode = item.barcode && item.barcode.toLowerCase().includes(q);
                if (!matchName && !matchSku && !matchBarcode) return false;
            }

            // Status filter
            const diff = item.difference != null ? Number(item.difference) : 0;
            if (statusFilter === 'DISCREPANCIES' && diff === 0) return false;
            if (statusFilter === 'BALANCED' && diff !== 0) return false;

            return true;
        });
    }, [sessionData, searchKeyword, statusFilter]);

    // ── Unresolved Discrepancies Count (Items with variance != 0 and not COMPLETED) ──
    const unresolvedDiscrepancies = useMemo(() => {
        if (!sessionData?.items) return [];
        return sessionData.items.filter(item => {
            const diff = item.difference != null ? Number(item.difference) : 0;
            const isDone = item.status === 'COMPLETED' || item.status === 'ADJUSTED';
            return diff !== 0 && !isDone;
        });
    }, [sessionData]);

    // ── Stale Discrepancies Count (Discrepancies where current stock differs from audit system quantity) ──
    const staleDiscrepancies = useMemo(() => {
        if (!sessionData?.items) return [];
        return sessionData.items.filter(item => {
            const isDone = item.status === 'COMPLETED' || item.status === 'ADJUSTED';
            const diff = item.difference != null ? Number(item.difference) : 0;
            if (isDone || diff === 0) return false;
            return Boolean(item.isStale) || (item.systemQuantity != null && item.currentStock != null && Number(item.currentStock) !== Number(item.systemQuantity));
        });
    }, [sessionData]);

    const handleRequestLeave = (destination) => {
        if (unresolvedDiscrepancies.length > 0) {
            setLeaveDestination(destination);
            setShowLeaveModal(true);
        } else {
            navigate(destination);
        }
    };

    const handleConfirmLeave = () => {
        setShowLeaveModal(false);
        navigate(leaveDestination || '/inventory');
    };

    const handleStartRecount = (item) => {
        navigate(`/inventory/stock-check?tab=new&productId=${item.productId}`);
    };

    const handleAdjustDiscrepancy = (item) => {
        const isStale = Boolean(item.isStale) || (
            item.status !== 'COMPLETED' &&
            item.status !== 'ADJUSTED' &&
            item.systemQuantity != null &&
            item.currentStock != null &&
            Number(item.currentStock) !== Number(item.systemQuantity)
        );

        if (isStale) {
            setError(`Adjustment blocked: Discrepancy for "${item.productName}" is stale because master stock has moved since the audit. Please start a recount.`);
            return;
        }

        const diff = item.difference != null ? Number(item.difference) : 0;
        const type = diff > 0 ? 'INCREASE' : 'DECREASE';
        const qty = Math.abs(diff);
        const reason = item.reason || `Discrepancy reconciliation for session ${sessionData.checkNumber}`;

        const url = `/inventory/adjust?productId=${item.productId}`
            + `&stockCheckId=${item.stockCheckId}`
            + `&type=${type}`
            + `&quantity=${qty}`
            + `&reason=${encodeURIComponent(reason)}`
            + `&session=${encodeURIComponent(sessionData.checkNumber)}`;

        navigate(url);
    };

    // ── Live Variance Preview for Edit Modal ──
    const previewVariance = useMemo(() => {
        if (!editingItem) return 0;
        const countVal = Number(editPhysicalCount);
        if (isNaN(countVal)) return 0;
        const sysVal = Number(editingItem.systemQuantity || 0);
        return countVal - sysVal;
    }, [editingItem, editPhysicalCount]);

    // ── Open Edit Count Modal ──
    const handleOpenEditModal = (item) => {
        if (item.status === 'COMPLETED' || item.status === 'ADJUSTED') {
            setError('Cannot edit physical count for an item that has already been reconciled.');
            return;
        }
        setEditingItem(item);
        setEditPhysicalCount(item.actualQuantity != null ? String(item.actualQuantity) : '0');
        setEditReason(item.reason || '');
        setEditError('');
    };

    // ── Save Corrected Physical Count ──
    const handleSaveEditCount = async (e) => {
        if (e) e.preventDefault();
        if (!editingItem) return;

        if (editingItem.status === 'COMPLETED' || editingItem.status === 'ADJUSTED') {
            setEditError('Cannot edit count for an already reconciled item.');
            return;
        }

        const countVal = Number(editPhysicalCount);
        if (editPhysicalCount === '' || isNaN(countVal) || countVal < 0 || !Number.isInteger(countVal)) {
            setEditError('Physical count must be a non-negative whole integer (0, 1, 2, ...).');
            return;
        }

        setEditSubmitting(true);
        setEditError('');

        try {
            await InventoryService.updateStockCheckCount(editingItem.stockCheckId, {
                actualQuantity: countVal,
                reason: editReason?.trim() || null,
            });

            const updatedProductName = editingItem.productName;
            setEditingItem(null);
            setSuccessMessage(`Physical count for "${updatedProductName}" was updated successfully.`);
            setTimeout(() => setSuccessMessage(''), 5000);

            // Re-fetch session to recalculate telemetry cards, summary stats and table in place
            await fetchSession();
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Failed to update physical count.';
            setEditError(msg);
        } finally {
            setEditSubmitting(false);
        }
    };

    return (
        <div className="sd-page">
            {/* ── Breadcrumb & Navigation ── */}
            <header className="sd-header">
                <nav className="sd-breadcrumb" aria-label="Breadcrumb">
                    <Link to="/inventory">Inventory</Link>
                    <span>/</span>
                    <Link to="/inventory/stock-check">Stock Check</Link>
                    <span>/</span>
                    <span>Stock Difference</span>
                </nav>
                <div className="sd-header__row">
                    <div>
                        <h1 className="sd-title">Stock Difference Reconciliation</h1>
                        <p className="sd-subtitle">
                            Review variance between on-shelf physical counts and book records (UC32 Screen 32).
                        </p>
                    </div>
                    <div className="sd-header__actions">
                        <button
                            type="button"
                            className="sd-btn sd-btn--secondary"
                            onClick={() => handleRequestLeave('/inventory/stock-check?tab=history')}
                            title="View all previous stock check audit sessions"
                        >
                            📋 Audit History
                        </button>
                        <button
                            type="button"
                            className="sd-btn sd-btn--secondary"
                            onClick={fetchSession}
                            disabled={loading}
                        >
                            ↻ Refresh
                        </button>
                        <button
                            type="button"
                            className="sd-btn sd-btn--primary"
                            onClick={() => handleRequestLeave('/inventory')}
                            title="Return to inventory management"
                        >
                            Done / View Inventory
                        </button>
                    </div>
                </div>
            </header>

            {/* ── Error Banner ── */}
            {error && (
                <div className="sd-alert sd-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <span>{error}</span>
                    <button type="button" className="sd-alert__close" onClick={() => setError('')}>×</button>
                </div>
            )}

            {/* ── Success Banner ── */}
            {successMessage && (
                <div className="sd-alert sd-alert--success" role="status">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    <span>{successMessage}</span>
                    <button type="button" className="sd-alert__close" onClick={() => setSuccessMessage('')}>×</button>
                </div>
            )}

            {loading ? (
                <div className="sd-loading">
                    <div className="sd-spinner" />
                    <p>Loading audit session reconciliation data…</p>
                </div>
            ) : !sessionData ? (
                <div className="sd-empty-session">
                    <p>No audit session data found.</p>
                    <button
                        type="button"
                        className="sd-btn sd-btn--primary"
                        onClick={() => navigate('/inventory/stock-check')}
                    >
                        Start New Stock Check
                    </button>
                </div>
            ) : (
                <>
                    {/* ── Telemetry Summary Cards ── */}
                    <div className="sd-telemetry-grid">
                        <div className="sd-card sd-card--telemetry">
                            <span className="sd-card__label">Session Reference</span>
                            <strong className="sd-card__val sd-card__val--code">{sessionData.checkNumber}</strong>
                            <span className="sd-card__sub">
                                Checked by: <strong>{sessionData.checkedByUsername || 'Staff'}</strong>
                            </span>
                        </div>

                        <div className="sd-card sd-card--telemetry">
                            <span className="sd-card__label">Audit Timestamp</span>
                            <strong className="sd-card__val">{formatDateTime(sessionData.checkedAt)}</strong>
                            <span className="sd-card__sub">Audit verification date</span>
                        </div>

                        <div className="sd-card sd-card--telemetry">
                            <span className="sd-card__label">SKUs Audited</span>
                            <strong className="sd-card__val">{sessionData.totalSkus}</strong>
                            <span className="sd-card__sub">
                                <span className="sd-text--success">{sessionData.matchedCount} balanced</span>
                            </span>
                        </div>

                        <div className="sd-card sd-card--telemetry">
                            <span className="sd-card__label">Discrepancies</span>
                            <strong className={`sd-card__val ${sessionData.discrepancyCount > 0 ? 'sd-text--danger' : 'sd-text--success'}`}>
                                {sessionData.discrepancyCount}
                            </strong>
                            <span className="sd-card__sub">
                                {sessionData.discrepancyCount === 0 ? 'Zero variances' : 'Require adjustment'}
                            </span>
                        </div>

                        <div className="sd-card sd-card--telemetry">
                            <span className="sd-card__label">Estimated Financial Impact</span>
                            <strong className={`sd-card__val ${Number(sessionData.netFinancialVariance || 0) < 0 ? 'sd-text--danger' : Number(sessionData.netFinancialVariance || 0) > 0 ? 'sd-text--primary' : 'sd-text--success'}`}>
                                {formatVND(sessionData.netFinancialVariance)}
                            </strong>
                            <span className="sd-card__sub">Net monetary variance</span>
                        </div>
                    </div>

                    {/* ── AT1: All Balanced Confirmation Banner ── */}
                    {sessionData.discrepancyCount === 0 && (
                        <div className="sd-banner sd-banner--perfect" role="status">
                            <div className="sd-banner__icon">✓</div>
                            <div className="sd-banner__body">
                                <h3>All physical counts match system balances perfectly!</h3>
                                <p>
                                    Every item counted in audit session <strong>{sessionData.checkNumber}</strong> has zero variance. No stock adjustments are required.
                                </p>
                            </div>
                            <button
                                type="button"
                                className="sd-btn sd-btn--success"
                                onClick={() => navigate('/inventory')}
                            >
                                Finish Session
                            </button>
                        </div>
                    )}

                    {/* ── Stale Discrepancies Warning Banner ── */}
                    {staleDiscrepancies.length > 0 && (
                        <div className="sd-banner sd-banner--stale" role="alert">
                            <div className="sd-banner__icon">⚠️</div>
                            <div className="sd-banner__body">
                                <h3>{staleDiscrepancies.length} Stale Discrepancy Record(s) Detected</h3>
                                <p>
                                    Master inventory for {staleDiscrepancies.length} item(s) has changed since audit session <strong>{sessionData.checkNumber}</strong> was recorded (e.g., via sales or manual adjustments). Reconciling with outdated variances is blocked. A new physical count is required.
                                </p>
                            </div>
                            <button
                                type="button"
                                className="sd-btn sd-btn--warning"
                                onClick={() => navigate('/inventory/stock-check?tab=new')}
                                title="Start a fresh physical stock audit session"
                            >
                                Start New Stock Check →
                            </button>
                        </div>
                    )}

                    {/* ── Reconciliation Table Card ── */}
                    <div className="sd-table-card">
                        <div className="sd-table-card__header">
                            <div className="sd-filters-left">
                                <input
                                    type="text"
                                    className="sd-input sd-input--search"
                                    placeholder="Filter by SKU, name or barcode…"
                                    value={searchKeyword}
                                    onChange={(e) => setSearchKeyword(e.target.value)}
                                />
                                <div className="sd-segmented">
                                    <button
                                        type="button"
                                        className={`sd-seg-btn ${statusFilter === 'ALL' ? 'sd-seg-btn--active' : ''}`}
                                        onClick={() => setStatusFilter('ALL')}
                                    >
                                        All ({sessionData.items?.length || 0})
                                    </button>
                                    <button
                                        type="button"
                                        className={`sd-seg-btn ${statusFilter === 'DISCREPANCIES' ? 'sd-seg-btn--active' : ''}`}
                                        onClick={() => setStatusFilter('DISCREPANCIES')}
                                    >
                                        Discrepancies ({sessionData.discrepancyCount})
                                    </button>
                                    <button
                                        type="button"
                                        className={`sd-seg-btn ${statusFilter === 'BALANCED' ? 'sd-seg-btn--active' : ''}`}
                                        onClick={() => setStatusFilter('BALANCED')}
                                    >
                                        Balanced ({sessionData.matchedCount})
                                    </button>
                                </div>
                            </div>

                            <span className="sd-table-count">
                                Showing {filteredItems.length} of {sessionData.items?.length || 0} items
                            </span>
                        </div>

                        <div className="sd-table-responsive">
                            <table className="sd-table">
                                <thead>
                                    <tr>
                                        <th>SKU & Barcode</th>
                                        <th>Product Name</th>
                                        <th style={{ textAlign: 'right' }}>System Qty</th>
                                        <th style={{ textAlign: 'right' }}>Physical Count</th>
                                        <th style={{ textAlign: 'center' }}>Variance</th>
                                        <th style={{ textAlign: 'right' }}>Financial Impact</th>
                                        <th style={{ textAlign: 'center' }}>Status</th>
                                        <th>Discrepancy Reason</th>
                                        <th style={{ textAlign: 'center', width: '150px' }}>Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredItems.length === 0 ? (
                                        <tr>
                                            <td colSpan="9" className="sd-cell--empty">
                                                No items match the selected filter.
                                            </td>
                                        </tr>
                                    ) : (
                                        filteredItems.map(item => {
                                            const diff = item.difference != null ? Number(item.difference) : 0;
                                            const isAdjusted = item.status === 'COMPLETED' || item.status === 'ADJUSTED';
                                            const isDiscrepancy = diff !== 0;
                                            const isStale = !isAdjusted && isDiscrepancy && (
                                                Boolean(item.isStale) || (
                                                    item.systemQuantity != null &&
                                                    item.currentStock != null &&
                                                    Number(item.currentStock) !== Number(item.systemQuantity)
                                                )
                                            );

                                            return (
                                                <tr key={item.stockCheckId} className={`sd-row ${isDiscrepancy && !isAdjusted ? 'sd-row--pending' : ''} ${isStale ? 'sd-row--stale' : ''}`}>
                                                    <td>
                                                        <div className="sd-cell-sku">
                                                            <strong>{item.productCode}</strong>
                                                            {item.barcode && <span className="sd-barcode-sub">{item.barcode}</span>}
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <div className="sd-cell-prod">
                                                            <span className="sd-prod-name">{item.productName}</span>
                                                            <span className="sd-prod-cat">{item.categoryName || 'General'}</span>
                                                        </div>
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <span className="sd-qty-val">{formatQty(item.systemQuantity)}</span>
                                                        <span className="sd-qty-unit"> {item.unit}</span>
                                                        {isStale && item.currentStock != null && (
                                                            <div className="sd-current-stock-sub" title="Current master stock on hand">
                                                                Now: <strong>{formatQty(item.currentStock)}</strong>
                                                            </div>
                                                        )}
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <span className="sd-qty-val font-semibold">{formatQty(item.actualQuantity)}</span>
                                                        <span className="sd-qty-unit"> {item.unit}</span>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        {diff === 0 ? (
                                                            <span className="sd-badge sd-badge--balanced">0</span>
                                                        ) : diff > 0 ? (
                                                            <span className="sd-badge sd-badge--surplus">+{formatQty(diff)}</span>
                                                        ) : (
                                                            <span className="sd-badge sd-badge--shortage">{formatQty(diff)}</span>
                                                        )}
                                                    </td>
                                                    <td style={{ textAlign: 'right' }}>
                                                        <span className={item.financialImpact < 0 ? 'sd-text--danger' : item.financialImpact > 0 ? 'sd-text--primary' : 'sd-text--muted'}>
                                                            {formatVND(item.financialImpact)}
                                                        </span>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <div className="sd-status-cell">
                                                            {diff === 0 ? (
                                                                <span className="sd-status sd-status--balanced">Balanced</span>
                                                            ) : diff > 0 ? (
                                                                <span className="sd-status sd-status--surplus">Surplus</span>
                                                            ) : (
                                                                <span className="sd-status sd-status--shortage">Shortage</span>
                                                            )}
                                                            {isStale && (
                                                                <span className="sd-badge sd-badge--stale" title={`Audit balance was ${formatQty(item.systemQuantity)} ${item.unit}, but current master stock is ${formatQty(item.currentStock)} ${item.unit}. Discrepancy is stale.`}>
                                                                    ⚠️ Stale Count
                                                                </span>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <div className="sd-reason-wrap">
                                                            <span className="sd-reason-text">
                                                                {item.reason || <span className="sd-text--muted">—</span>}
                                                            </span>
                                                            {isStale && (
                                                                <span className="sd-stale-note">
                                                                    ⚠️ Stock changed ({formatQty(item.systemQuantity)} → {formatQty(item.currentStock)}). Recount required.
                                                                </span>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <div className="sd-actions-wrap">
                                                            {isAdjusted ? (
                                                                <span className="sd-tag--adjusted" title="Adjustment has already been recorded in Stock Movements">
                                                                    ✓ Adjusted
                                                                </span>
                                                            ) : (
                                                                <>
                                                                    {diff === 0 ? (
                                                                        <span className="sd-tag--matched" title="System matches actual">
                                                                            ✓ Matched
                                                                        </span>
                                                                    ) : isStale ? (
                                                                        <>
                                                                            <button
                                                                                type="button"
                                                                                className="sd-btn sd-btn--recount"
                                                                                onClick={() => handleStartRecount(item)}
                                                                                title="Master stock changed since audit. Start a new Stock Check to recount this product against current inventory."
                                                                            >
                                                                                🔄 Recount Stock
                                                                            </button>
                                                                            <span className="sd-tag--blocked" title="Adjustment blocked: Master stock has moved since audit. Cannot reconcile with stale variance.">
                                                                                🔒 Adjust Blocked
                                                                            </span>
                                                                        </>
                                                                    ) : (
                                                                        <button
                                                                            type="button"
                                                                            className="sd-btn sd-btn--adjust"
                                                                            onClick={() => handleAdjustDiscrepancy(item)}
                                                                            title="Open UC33 to reconcile this discrepancy"
                                                                        >
                                                                            Adjust Stock →
                                                                        </button>
                                                                    )}
                                                                    <button
                                                                        type="button"
                                                                        className="sd-btn sd-btn--edit-count"
                                                                        onClick={() => handleOpenEditModal(item)}
                                                                        title="Correct physical count for this item"
                                                                    >
                                                                        ✏️ Edit Count
                                                                    </button>
                                                                </>
                                                            )}
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </>
            )}

            {/* ── Edit Physical Count Modal ── */}
            {editingItem && (
                <div className="sd-modal-backdrop" role="dialog" aria-modal="true">
                    <div className="sd-modal sd-modal--edit-count">
                        <div className="sd-modal__header">
                            <h3 className="sd-modal__title">✏️ Correct Physical Count</h3>
                            <button
                                type="button"
                                className="sd-modal__close"
                                onClick={() => setEditingItem(null)}
                                disabled={editSubmitting}
                            >
                                ×
                            </button>
                        </div>
                        <form onSubmit={handleSaveEditCount}>
                            <div className="sd-modal__body">
                                {editError && (
                                    <div className="sd-alert sd-alert--error" style={{ marginBottom: '14px' }}>
                                        <span>{editError}</span>
                                    </div>
                                )}

                                <div className="sd-edit-info">
                                    <div className="sd-edit-info-row">
                                        <span className="sd-edit-label">Product Name:</span>
                                        <strong className="sd-edit-value">{editingItem.productName}</strong>
                                    </div>
                                    <div className="sd-edit-info-row">
                                        <span className="sd-edit-label">SKU / Code:</span>
                                        <span className="sd-edit-value">{editingItem.productCode} {editingItem.barcode ? `(${editingItem.barcode})` : ''}</span>
                                    </div>
                                    <div className="sd-edit-info-row">
                                        <span className="sd-edit-label">System Quantity (Book):</span>
                                        <span className="sd-edit-value font-semibold">
                                            {formatQty(editingItem.systemQuantity)} {editingItem.unit}
                                        </span>
                                    </div>
                                </div>

                                <div className="sd-edit-field-group">
                                    <label htmlFor="edit-physical-count" className="sd-edit-field-label">
                                        Correct Physical Count <span style={{ color: '#dc2626' }}>*</span>
                                        <span style={{ fontWeight: 400, color: '#64748b', fontSize: '12px', marginLeft: '6px' }}>
                                            (Non-negative whole integer)
                                        </span>
                                    </label>
                                    <input
                                        id="edit-physical-count"
                                        type="number"
                                        min="0"
                                        step="1"
                                        className="sd-input"
                                        value={editPhysicalCount}
                                        onChange={(e) => setEditPhysicalCount(e.target.value)}
                                        disabled={editSubmitting}
                                        autoFocus
                                        required
                                    />
                                </div>

                                {/* Live Variance Preview */}
                                <div className="sd-edit-preview-card">
                                    <div className="sd-edit-preview-title">Live Variance Calculation Preview:</div>
                                    <div className="sd-edit-preview-grid">
                                        <div>
                                            <span className="sd-edit-preview-sub">New Physical:</span>
                                            <strong>{formatQty(editPhysicalCount || 0)} {editingItem.unit}</strong>
                                        </div>
                                        <div>
                                            <span className="sd-edit-preview-sub">Variance (Actual − Book):</span>
                                            <strong className={previewVariance === 0 ? 'sd-text--success' : previewVariance > 0 ? 'sd-text--primary' : 'sd-text--danger'}>
                                                {previewVariance > 0 ? `+${formatQty(previewVariance)}` : formatQty(previewVariance)} {editingItem.unit}
                                            </strong>
                                        </div>
                                        <div>
                                            <span className="sd-edit-preview-sub">Status:</span>
                                            <strong className={previewVariance === 0 ? 'sd-text--success' : previewVariance > 0 ? 'sd-text--primary' : 'sd-text--danger'}>
                                                {previewVariance === 0 ? '✓ Balanced' : previewVariance > 0 ? 'Surplus' : 'Shortage'}
                                            </strong>
                                        </div>
                                    </div>
                                </div>

                                <div className="sd-edit-field-group" style={{ marginTop: '14px' }}>
                                    <label htmlFor="edit-count-reason" className="sd-edit-field-label">
                                        Correction Reason / Note (Optional)
                                    </label>
                                    <input
                                        id="edit-count-reason"
                                        type="text"
                                        maxLength="255"
                                        className="sd-input"
                                        placeholder="e.g. Recount verified on shelf, corrected counting typo"
                                        value={editReason}
                                        onChange={(e) => setEditReason(e.target.value)}
                                        disabled={editSubmitting}
                                    />
                                </div>

                                <div className="sd-modal-notice" style={{ marginTop: '12px', fontSize: '12px' }}>
                                    ℹ️ <strong>Note:</strong> Correcting the physical count will update this audit record. It will <strong>NOT</strong> modify master product inventory or create stock movements until reconciled via Adjust Stock.
                                </div>
                            </div>

                            <div className="sd-modal__footer">
                                <button
                                    type="button"
                                    className="sd-btn sd-btn--secondary"
                                    onClick={() => setEditingItem(null)}
                                    disabled={editSubmitting}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="sd-btn sd-btn--primary"
                                    disabled={editSubmitting || isNaN(Number(editPhysicalCount)) || Number(editPhysicalCount) < 0 || !Number.isInteger(Number(editPhysicalCount))}
                                >
                                    {editSubmitting ? 'Saving Correction…' : 'Save Correction'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* ── Unresolved Discrepancies Warning Modal ── */}
            {showLeaveModal && (
                <div className="sd-modal-backdrop" role="dialog" aria-modal="true">
                    <div className="sd-modal">
                        <div className="sd-modal__header">
                            <h3 className="sd-modal__title">⚠️ Unresolved Discrepancies Warning</h3>
                            <button
                                type="button"
                                className="sd-modal__close"
                                onClick={() => setShowLeaveModal(false)}
                            >
                                ×
                            </button>
                        </div>
                        <div className="sd-modal__body">
                            <p>
                                Audit Session <strong>{sessionData?.checkNumber}</strong> still contains{' '}
                                <strong className="sd-text--danger">{unresolvedDiscrepancies.length} discrepancy item(s)</strong> that have not been reconciled into inventory.
                            </p>
                            <div className="sd-modal-notice">
                                <strong>Important:</strong> Inventory quantities will <strong>NOT</strong> be automatically adjusted when leaving. These variances will remain pending until adjusted via <em>Adjust Stock (UC33)</em>.
                            </div>
                            <p style={{ marginTop: '12px', fontSize: '13px', color: '#64748b' }}>
                                Are you sure you want to navigate away from this audit reconciliation screen?
                            </p>
                        </div>
                        <div className="sd-modal__footer">
                            <button
                                type="button"
                                className="sd-btn sd-btn--secondary"
                                onClick={() => setShowLeaveModal(false)}
                            >
                                Stay & Reconcile
                            </button>
                            <button
                                type="button"
                                className="sd-btn sd-btn--danger"
                                onClick={handleConfirmLeave}
                            >
                                Leave Anyway
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

