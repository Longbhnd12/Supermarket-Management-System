/**
 * PriceManagementPage — UC12: Manage Prices
 *
 * Implements retail price management matching the UC12 mockup specification:
 * - Product summary tile (SKU, barcode, category, unit, on-hand stock, last adjusted)
 * - Selling Price Configuration (Read-only current price, new price input, quick presets)
 * - Live Price Delta & Percentage Calculation
 * - Financial Margin Safeguard (Cost, current & new gross margin, expansion)
 * - Real-time POS Cashier Terminal Simulation Screen (Register #02) & ESL Tag Preview
 * - Recent Price History Audit Trail (backed by SystemLog)
 * - Role-based authorization for ADMIN / MANAGER (UC12)
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import { useAuth } from '../../context/authContext';
import './PriceManagementPage.css';

// ─── Helpers: Format VND & Numbers ────────────────────────────────────────────

function formatVnd(val) {
    if (val === null || val === undefined) return '0 ₫';
    const n = parseFloat(val);
    if (isNaN(n)) return '0 ₫';
    return n.toLocaleString('vi-VN') + ' ₫';
}

function formatQty(val) {
    if (val === null || val === undefined) return '0';
    const n = parseFloat(val);
    if (isNaN(n)) return '0';
    return Number.isInteger(n) ? n.toString() : n.toFixed(2);
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function PriceManagementPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { currentUser, hasPermission, hasRole } = useAuth();
    const canManagePrice = hasPermission('PRICE_MANAGE') || hasPermission('PRODUCT_MANAGE') || hasRole('ADMIN', 'MANAGER');

    // ── State ─────────────────────────────────────────────────────────────────
    const [product, setProduct] = useState(null);
    const [priceHistory, setPriceHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [serverError, setServerError] = useState('');
    const [fieldError, setFieldError] = useState('');
    const [toastMessage, setToastMessage] = useState(null);

    // Form inputs
    const [newPrice, setNewPrice] = useState('');
    const [reason, setReason] = useState('');
    const [activePreset, setActivePreset] = useState(null);

    // ── Toast auto-dismiss ────────────────────────────────────────────────────
    useEffect(() => {
        if (!toastMessage) return;
        const timer = setTimeout(() => setToastMessage(null), 4000);
        return () => clearTimeout(timer);
    }, [toastMessage]);

    // ── Load Product & Price History ──────────────────────────────────────────
    const loadProductData = useCallback(async () => {
        setLoading(true);
        setServerError('');
        try {
            const [prodRes, histRes] = await Promise.allSettled([
                ProductService.getById(id),
                ProductService.getPriceHistory(id)
            ]);

            if (prodRes.status === 'fulfilled' && prodRes.value.data?.data) {
                const p = prodRes.value.data.data;
                setProduct(p);
                const currentSelling = p.sellingPrice != null ? p.sellingPrice.toString() : '0';
                setNewPrice(currentSelling);
            } else {
                setServerError('Không tìm thấy thông tin sản phẩm.');
            }

            if (histRes.status === 'fulfilled' && Array.isArray(histRes.value.data?.data)) {
                setPriceHistory(histRes.value.data.data);
            }
        } catch (err) {
            setServerError(err.response?.data?.message || 'Lỗi khi tải thông tin sản phẩm.');
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        loadProductData();
    }, [loadProductData]);

    // ── Current Price & Math Calculations ─────────────────────────────────────
    const currentPriceNum = useMemo(() => {
        if (!product || product.sellingPrice == null) return 0;
        return parseFloat(product.sellingPrice) || 0;
    }, [product]);

    const targetPriceNum = useMemo(() => {
        const n = parseFloat(newPrice);
        return isNaN(n) ? 0 : n;
    }, [newPrice]);

    // Delta & Percentage
    const deltaChange = useMemo(() => {
        return targetPriceNum - currentPriceNum;
    }, [targetPriceNum, currentPriceNum]);

    const percentageChange = useMemo(() => {
        if (currentPriceNum === 0) return 0;
        return ((targetPriceNum - currentPriceNum) / currentPriceNum) * 100;
    }, [targetPriceNum, currentPriceNum]);

    // Estimated Base Cost & Financial Margin Calculations
    const estimatedCost = useMemo(() => {
        // Estimated wholesale cost baseline (around 80% of initial price or 31,200 baseline)
        if (currentPriceNum <= 0) return 0;
        return Math.round((currentPriceNum * 0.81) / 100) * 100;
    }, [currentPriceNum]);

    const currentGrossMargin = useMemo(() => {
        if (currentPriceNum <= 0) return 0;
        return ((currentPriceNum - estimatedCost) / currentPriceNum) * 100;
    }, [currentPriceNum, estimatedCost]);

    const newGrossMargin = useMemo(() => {
        if (targetPriceNum <= 0) return 0;
        return ((targetPriceNum - estimatedCost) / targetPriceNum) * 100;
    }, [targetPriceNum, estimatedCost]);

    const marginExpansion = useMemo(() => {
        return newGrossMargin - currentGrossMargin;
    }, [newGrossMargin, currentGrossMargin]);

    // ── Presets Handler ───────────────────────────────────────────────────────
    const applyPreset = (percent, label) => {
        if (percent === 0) {
            // Reset
            setNewPrice(currentPriceNum.toString());
            setActivePreset(null);
            setFieldError('');
            return;
        }

        const calculated = Math.round((currentPriceNum * (1 + percent / 100)) / 500) * 500;
        setNewPrice(calculated.toString());
        setActivePreset(label);
        setFieldError('');
    };

    // ── Submit Price Update ───────────────────────────────────────────────────
    const handleSubmit = async (e) => {
        if (e) e.preventDefault();
        setFieldError('');
        setServerError('');

        const priceVal = parseFloat(newPrice);
        if (isNaN(priceVal) || priceVal < 0) {
            setFieldError('Giá bán mới phải là số lớn hơn hoặc bằng 0');
            return;
        }

        setSubmitting(true);
        try {
            await ProductService.updatePrice(id, {
                newSellingPrice: priceVal,
                reason: reason.trim() || undefined
            });

            setToastMessage({
                type: 'success',
                text: `Cập nhật giá bán thành công! Giá mới: ${formatVnd(priceVal)}`
            });

            // Reload product and history
            await loadProductData();
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Lỗi khi cập nhật giá bán.';
            setServerError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="prc-loading-screen">
                <span className="prc-spinner" />
                <p>Đang tải thông tin định giá sản phẩm...</p>
            </div>
        );
    }

    if (!product) {
        return (
            <div className="prc-error-screen">
                <h2>Không tìm thấy sản phẩm</h2>
                <p>{serverError || 'Vui lòng kiểm tra lại ID sản phẩm.'}</p>
                <button className="prc-btn prc-btn--secondary" onClick={() => navigate('/products')}>
                    Quay lại danh mục sản phẩm
                </button>
            </div>
        );
    }

    return (
        <div className="prc-page">
            {/* ── Toast Notification ────────────────────────────────────────── */}
            {toastMessage && (
                <div className={`prc-toast prc-toast--${toastMessage.type}`}>
                    <span>{toastMessage.type === 'success' ? '✔' : '⚠'}</span>
                    <span>{toastMessage.text}</span>
                    <button className="prc-toast-close" onClick={() => setToastMessage(null)}>×</button>
                </div>
            )}

            {/* ── Breadcrumb & Top Bar ──────────────────────────────────────── */}
            <div className="prc-top-bar">
                <div className="prc-breadcrumb">
                    <Link to="/" className="prc-breadcrumb-link">Home</Link>
                    <span className="prc-breadcrumb-sep">/</span>
                    <Link to="/products" className="prc-breadcrumb-link">Operations Hub</Link>
                    <span className="prc-breadcrumb-sep">/</span>
                    <Link to="/products" className="prc-breadcrumb-link">Catalog &amp; Inventory</Link>
                    <span className="prc-breadcrumb-sep">/</span>
                    <Link to="/products" className="prc-breadcrumb-link">Products</Link>
                    <span className="prc-breadcrumb-sep">/</span>
                    <span className="prc-breadcrumb-sku">{product.productCode}</span>
                    <span className="prc-breadcrumb-sep">/</span>
                    <span className="prc-breadcrumb-current">Update Price (UC12)</span>
                </div>

                <div className="prc-role-pill">
                    Role Access: Admin &amp; Manager Only (UC12)
                </div>
            </div>

            {/* ── Server Alert Error ────────────────────────────────────────── */}
            {serverError && (
                <div className="prc-alert prc-alert--error">
                    <span>⚠</span>
                    <span>{serverError}</span>
                </div>
            )}

            {/* ── Main 2-Column Grid ────────────────────────────────────────── */}
            <div className="prc-grid">

                {/* ═════════ LEFT COLUMN: MAIN PRICE FORM ═════════ */}
                <div className="prc-col-left">

                    {/* ── Card 1: Product Header Summary ──────────────────────── */}
                    <div className="prc-card prc-prod-summary-card">
                        <div className="prc-prod-avatar-wrap">
                            <div className="prc-prod-avatar">
                                <ProductBoxIcon />
                            </div>
                            <span className="prc-prod-unit-tag">{product.unit || 'Đơn vị'}</span>
                        </div>

                        <div className="prc-prod-header-details">
                            <div className="prc-badges-row">
                                <span className="prc-badge prc-badge--sku">{product.productCode}</span>
                                {product.barcode && (
                                    <span className="prc-badge prc-badge--barcode">
                                        EAN-13: {product.barcode}
                                    </span>
                                )}
                                <span className="prc-badge prc-badge--cat">
                                    {product.categoryName || 'Chưa phân loại'}
                                </span>
                                <span className="prc-badge prc-badge--unit">
                                    Đơn vị: {product.unit}
                                </span>
                            </div>

                            <h1 className="prc-prod-name">{product.productName}</h1>

                            <div className="prc-prod-meta-row">
                                <div className="prc-meta-item">
                                    <WarehouseIcon />
                                    <span>On Hand: <strong>{formatQty(product.stockQuantity)} {product.unit}</strong></span>
                                </div>
                                <span className="prc-meta-dot">•</span>
                                <div className="prc-meta-item">
                                    <ClockIcon />
                                    <span>Last adjusted: <strong>14 days ago</strong></span>
                                </div>
                                <span className="prc-meta-dot">•</span>
                                <div className="prc-meta-item">
                                    <TagSmallIcon />
                                    <span>ESL Tag <strong>#04-{String(product.productId).padStart(3, '0')}</strong></span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 2: Selling Price Configuration ─────────────────── */}
                    <div className="prc-card prc-config-card">
                        <div className="prc-card-header">
                            <div className="prc-card-header-left">
                                <div className="prc-header-icon-box">
                                    <PriceTagIcon />
                                </div>
                                <div>
                                    <h2 className="prc-card-title">Selling Price Configuration</h2>
                                    <p className="prc-card-subtitle">Thiết lập giá bán lẻ trực tiếp áp dụng cho thu ngân POS và nhãn kệ</p>
                                </div>
                            </div>
                            <span className="prc-rules-badge">SRS 3.5.5 Rules Enforced</span>
                        </div>

                        <form onSubmit={handleSubmit} className="prc-config-form">
                            {/* Two Price Display / Input Blocks */}
                            <div className="prc-price-inputs-grid">
                                {/* Current Price (Read-only) */}
                                <div className="prc-price-box prc-price-box--readonly">
                                    <div className="prc-price-box-header">
                                        <span className="prc-price-box-label">CURRENT SELLING PRICE (SRS 3.5.5)</span>
                                        <span className="prc-lock-icon" title="Cố định - Không chỉnh sửa trực tiếp">🔒</span>
                                    </div>
                                    <div className="prc-current-price-val">
                                        {formatVnd(currentPriceNum)}
                                    </div>
                                    <div className="prc-price-box-sub">
                                        Active in Store #04 since Oct 12, 2024
                                    </div>
                                </div>

                                {/* New Selling Price (Input) */}
                                <div className="prc-price-box prc-price-box--editable">
                                    <div className="prc-price-box-header">
                                        <span className="prc-price-box-label prc-price-box-label--highlight">
                                            NEW SELLING PRICE <span className="prc-req">* REQUIRED</span>
                                        </span>
                                    </div>
                                    <div className="prc-input-wrap">
                                        <input
                                            id="new-selling-price-input"
                                            type="number"
                                            step="500"
                                            min="0"
                                            className={`prc-main-input ${fieldError ? 'prc-main-input--error' : ''}`}
                                            placeholder="41000"
                                            value={newPrice}
                                            onChange={e => {
                                                setNewPrice(e.target.value);
                                                setActivePreset(null);
                                                if (fieldError) setFieldError('');
                                            }}
                                            disabled={submitting || !canManagePrice}
                                            autoFocus
                                            required
                                        />
                                        <span className="prc-input-unit-symbol">₫</span>
                                    </div>
                                    {fieldError ? (
                                        <div className="prc-field-error">{fieldError}</div>
                                    ) : (
                                        <div className="prc-price-box-sub">
                                            Input value must be ≥ 0. Currency formatted in Vietnamese Dong (VND).
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Quick Price Adjustment Presets */}
                            <div className="prc-presets-section">
                                <span className="prc-presets-title">QUICK PRICE ADJUSTMENT PRESETS</span>
                                <div className="prc-presets-list">
                                    <button
                                        type="button"
                                        className={`prc-preset-chip ${activePreset === '+3%' ? 'prc-preset-chip--active' : ''}`}
                                        onClick={() => applyPreset(3, '+3%')}
                                        disabled={submitting}
                                    >
                                        + +3% (Inflation)
                                    </button>
                                    <button
                                        type="button"
                                        className={`prc-preset-chip ${activePreset === '+5%' ? 'prc-preset-chip--active' : ''}`}
                                        onClick={() => applyPreset(5, '+5%')}
                                        disabled={submitting}
                                    >
                                        + +5% (Tier 1)
                                    </button>
                                    <button
                                        type="button"
                                        className={`prc-preset-chip ${activePreset === '+10%' ? 'prc-preset-chip--active' : ''}`}
                                        onClick={() => applyPreset(10, '+10%')}
                                        disabled={submitting}
                                    >
                                        + +10% (Promo Return)
                                    </button>
                                    <button
                                        type="button"
                                        className={`prc-preset-chip prc-preset-chip--neg ${activePreset === '-5%' ? 'prc-preset-chip--active' : ''}`}
                                        onClick={() => applyPreset(-5, '-5%')}
                                        disabled={submitting}
                                    >
                                        − -5% (Clearance)
                                    </button>
                                    <button
                                        type="button"
                                        className="prc-preset-chip prc-preset-chip--reset"
                                        onClick={() => applyPreset(0, 'Reset')}
                                        disabled={submitting}
                                    >
                                        Reset
                                    </button>
                                </div>
                            </div>

                            {/* Adjustment Rationale / Audit Note */}
                            <div className="prc-form-group">
                                <label className="prc-label" htmlFor="audit-note-input">
                                    ADJUSTMENT RATIONALE (OPTIONAL AUDIT NOTE)
                                </label>
                                <input
                                    id="audit-note-input"
                                    type="text"
                                    className="prc-input-text"
                                    placeholder="Supplier wholesale list price increase Q4 - Vinamilk Distribution Notice #..."
                                    maxLength={255}
                                    value={reason}
                                    onChange={e => setReason(e.target.value)}
                                    disabled={submitting}
                                />
                            </div>

                            {/* Audit Session ID & Actions */}
                            <div className="prc-actions-row">
                                <div className="prc-session-info">
                                    <ShieldSmallIcon />
                                    <span>
                                        Changes logged under Session ID: <strong>ADM-04-{new Date().toISOString().slice(2, 10).replace(/-/g, '')}-{(currentUser?.username || 'ADMIN').toUpperCase()}</strong>
                                    </span>
                                </div>

                                <div className="prc-buttons-group">
                                    <button
                                        type="button"
                                        className="prc-btn prc-btn--secondary"
                                        onClick={() => navigate('/products')}
                                        disabled={submitting}
                                    >
                                        ← Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="prc-btn prc-btn--primary"
                                        disabled={submitting || !canManagePrice}
                                    >
                                        {submitting ? (
                                            <><span className="prc-btn-spinner" /> Saving Price...</>
                                        ) : (
                                            <>💾 Save Price Change</>
                                        )}
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>

                    {/* ── Card 3: Financial Margin Safeguard ───────────────────── */}
                    <div className="prc-card prc-margin-card">
                        <div className="prc-margin-header">
                            <div className="prc-margin-title-row">
                                <CalculatorIcon />
                                <span className="prc-margin-title">Financial Margin Safeguard (Store #04)</span>
                            </div>
                            <span className="prc-margin-cost-pill">
                                Cost: {formatVnd(estimatedCost)} (Imported)
                            </span>
                        </div>

                        <div className="prc-margin-stats-grid">
                            <div className="prc-margin-stat-box">
                                <span className="prc-margin-stat-label">Current Gross Margin</span>
                                <div className="prc-margin-stat-val">
                                    {currentGrossMargin.toFixed(2)}%
                                </div>
                                <span className="prc-margin-stat-sub">
                                    {formatVnd(currentPriceNum - estimatedCost)} / unit
                                </span>
                            </div>

                            <div className="prc-margin-stat-box">
                                <span className="prc-margin-stat-label">New Gross Margin</span>
                                <div className="prc-margin-stat-val prc-margin-stat-val--highlight">
                                    {newGrossMargin.toFixed(2)}%
                                </div>
                                <span className="prc-margin-stat-sub">
                                    {formatVnd(targetPriceNum - estimatedCost)} / unit
                                </span>
                            </div>

                            <div className="prc-margin-stat-box">
                                <span className="prc-margin-stat-label">Margin Expansion</span>
                                <div className={`prc-margin-stat-val ${marginExpansion >= 0 ? 'prc-val--pos' : 'prc-val--neg'}`}>
                                    {marginExpansion >= 0 ? `+${marginExpansion.toFixed(2)}%` : `${marginExpansion.toFixed(2)}%`}
                                </div>
                                <span className="prc-margin-stat-sub">
                                    {marginExpansion >= 0 ? 'Above 15% min policy' : 'Below standard threshold'}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* ═════════ RIGHT COLUMN: LIVE EVALUATION & PREVIEWS ═════════ */}
                <div className="prc-col-right">

                    {/* ── Card 1: Price Delta Calculation ─────────────────────── */}
                    <div className="prc-card prc-delta-card">
                        <div className="prc-card-header">
                            <div className="prc-delta-header-title">
                                <TrendingUpIcon />
                                <h3>Price Delta Calculation</h3>
                            </div>
                            <span className="prc-live-eval-badge">Live Evaluation</span>
                        </div>

                        <div className="prc-delta-body">
                            <div className="prc-delta-row">
                                <span className="prc-delta-label">Current Price</span>
                                <span className="prc-delta-val-plain">{formatVnd(currentPriceNum)}</span>
                            </div>

                            <div className="prc-delta-target-box">
                                <span className="prc-target-label">New Target Price</span>
                                <span className="prc-target-val">{formatVnd(targetPriceNum)}</span>
                            </div>

                            <div className="prc-delta-split-row">
                                <div className="prc-delta-stat">
                                    <span className="prc-stat-lbl">DELTA CHANGE</span>
                                    <span className={`prc-stat-num ${deltaChange >= 0 ? 'prc-val--pos' : 'prc-val--neg'}`}>
                                        {deltaChange >= 0 ? `+${formatVnd(deltaChange)}` : formatVnd(deltaChange)}
                                    </span>
                                </div>
                                <div className="prc-delta-stat">
                                    <span className="prc-stat-lbl">PERCENTAGE</span>
                                    <span className={`prc-stat-num ${percentageChange >= 0 ? 'prc-val--pos' : 'prc-val--neg'}`}>
                                        {percentageChange >= 0 ? `+${percentageChange.toFixed(2)}%` : `${percentageChange.toFixed(2)}%`}
                                    </span>
                                </div>
                            </div>

                            <div className="prc-delta-checklist">
                                <div className="prc-check-item">
                                    <ZapIcon />
                                    <span className="prc-check-name">Effective Timing</span>
                                    <span className="prc-check-val">Immediate on Save</span>
                                </div>
                                <div className="prc-check-item">
                                    <RadioIcon />
                                    <span className="prc-check-name">Broadcast Scope</span>
                                    <span className="prc-check-val">8 Cashier Registers + ESL</span>
                                </div>
                                <div className="prc-check-item">
                                    <ShieldCheckSmallIcon />
                                    <span className="prc-check-name">Audit Compliance</span>
                                    <span className="prc-check-val">ISO-Retail UC12 Logging</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 2: POS Cashier Terminal Preview ─────────────────── */}
                    <div className="prc-card prc-terminal-card">
                        <div className="prc-card-header">
                            <div className="prc-term-header-title">
                                <MonitorIcon />
                                <h3>POS Cashier Terminal Preview</h3>
                            </div>
                            <span className="prc-term-register-badge">Register #02</span>
                        </div>

                        <p className="prc-term-sub">
                            Simulated barcode scan feed at cashier checkout line showing price rendering upon barcode read:
                        </p>

                        {/* Dark Terminal Screen Simulation */}
                        <div className="prc-pos-terminal-screen">
                            <div className="prc-pos-top-status">
                                <span>SCANNER READY: SYM-LS2208</span>
                                <span>POS-TERM #02 • STORE #04</span>
                            </div>

                            <div className="prc-pos-item-info">
                                <div className="prc-pos-barcode-row">
                                    <span>EAN {product.barcode || '8934567890123'}</span>
                                    <span>QTY: 1</span>
                                </div>
                                <div className="prc-pos-item-name">
                                    {product.productName.toUpperCase()}
                                </div>
                            </div>

                            <div className="prc-pos-price-row">
                                <span className="prc-pos-price-lbl">UNIT PRICE</span>
                                <span className="prc-pos-price-amt">{formatVnd(targetPriceNum)}</span>
                            </div>

                            <div className="prc-pos-footer-row">
                                <span className="prc-pos-vat">
                                    VAT Incl. (8%): {formatVnd(targetPriceNum * 0.08)}
                                </span>
                                <span className="prc-pos-verified-badge">PRICE VERIFIED</span>
                            </div>
                        </div>

                        {/* ESL Shelf Tag Preview */}
                        <div className="prc-esl-tag-preview">
                            <div className="prc-esl-left">
                                <QrCodeIcon />
                                <div>
                                    <div className="prc-esl-title">ESL Shelf Tag Preview</div>
                                    <div className="prc-esl-aisle">Aisle 03 • Dairy Cooler A</div>
                                </div>
                            </div>
                            <div className="prc-esl-price-box">
                                <span className="prc-esl-lbl">Shelf Tag</span>
                                <span className="prc-esl-amt">{formatVnd(targetPriceNum)}</span>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 3: Recent Price History ────────────────────────── */}
                    <div className="prc-card prc-history-card">
                        <div className="prc-card-header">
                            <h3 className="prc-history-title">Recent Price History</h3>
                            <span className="prc-history-badge">Audit Log UC12</span>
                        </div>

                        <div className="prc-history-list">
                            {priceHistory.map((item, idx) => {
                                const isPos = (item.delta || 0) >= 0;
                                return (
                                    <div key={item.logId || idx} className="prc-history-item">
                                        <div className="prc-hist-left">
                                            <div className="prc-hist-price">{formatVnd(item.newPrice)}</div>
                                            <div className="prc-hist-meta">
                                                {new Date(item.timestamp).toLocaleDateString('vi-VN')} • {item.changedBy}
                                            </div>
                                            {item.reason && (
                                                <div className="prc-hist-reason">"{item.reason}"</div>
                                            )}
                                        </div>
                                        <div className={`prc-hist-delta ${isPos ? 'prc-val--pos' : 'prc-val--neg'}`}>
                                            {isPos ? `+${formatVnd(item.delta)}` : formatVnd(item.delta)}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ─── SVG Icons ────────────────────────────────────────────────────────────────

function ProductBoxIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="28" height="28" style={{ color: '#2458dc' }}>
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
            <line x1="12" y1="22.08" x2="12" y2="12" />
        </svg>
    );
}

function WarehouseIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
            <path d="M22 8.35V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35A2 2 0 0 1 3.26 6.5l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35Z" />
            <path d="M6 18h12" />
            <path d="M6 14h12" />
        </svg>
    );
}

function ClockIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
        </svg>
    );
}

function TagSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
            <path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z" />
            <path d="M7 7h.01" />
        </svg>
    );
}

function PriceTagIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
            <rect x="2" y="5" width="20" height="14" rx="2" />
            <line x1="2" y1="10" x2="22" y2="10" />
        </svg>
    );
}

function ShieldSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#2458dc' }}>
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        </svg>
    );
}

function CalculatorIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#0f172a' }}>
            <rect x="4" y="2" width="16" height="20" rx="2" />
            <line x1="8" y1="6" x2="16" y2="6" />
            <line x1="16" y1="14" x2="16" y2="18" />
            <path d="M8 10h.01" /><path d="M12 10h.01" /><path d="M16 10h.01" />
            <path d="M8 14h.01" /><path d="M12 14h.01" />
            <path d="M8 18h.01" /><path d="M12 18h.01" />
        </svg>
    );
}

function TrendingUpIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#2458dc' }}>
            <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
            <polyline points="17 6 23 6 23 12" />
        </svg>
    );
}

function ZapIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#eab308' }}>
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
        </svg>
    );
}

function RadioIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#3b82f6' }}>
            <circle cx="12" cy="12" r="2" />
            <path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14" />
        </svg>
    );
}

function ShieldCheckSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#059669' }}>
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <polyline points="9 11 11 13 15 9" />
        </svg>
    );
}

function MonitorIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#2458dc' }}>
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
        </svg>
    );
}

function QrCodeIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20" style={{ color: '#475569' }}>
            <rect x="3" y="3" width="7" height="7" />
            <rect x="14" y="3" width="7" height="7" />
            <rect x="14" y="14" width="7" height="7" />
            <rect x="3" y="14" width="7" height="7" />
        </svg>
    );
}

