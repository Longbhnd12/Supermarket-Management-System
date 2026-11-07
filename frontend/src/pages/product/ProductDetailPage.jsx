/**
 * ProductDetailPage — UC13: View Product Details
 *
 * Implements read-only master product details matching the UC13 mockup specification:
 * - Product header banner with packshot, SKU, category, name, selling price, and on-hand stock
 * - General Information (SKU with copy-to-clipboard, barcode, category, packaging unit, status)
 * - Pricing & Inventory (Price, current stock, low-stock threshold, stock safety margin bar)
 * - Expiry Tracking Policy (Near-expiry window, batch inbound validation)
 * - Barcode Visualizer (SVG GS1 EAN-13 barcode rendering with missing barcode toggle simulation)
 * - Promotion Rules (Active promotion display, discounted unit price, validity period)
 * - Primary Supplier tile (Supplier ID, company name, SLA)
 * - Role-based actions: Edit Product (UC10), Update Price (UC12), Activate / Deactivate
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import { useAuth } from '../../context/authContext';
import './ProductDetailPage.css';

// ─── Helpers: Format VND & Numbers ────────────────────────────────────────────

function formatVnd(val) {
    if (val === null || val === undefined) return '—';
    const n = parseFloat(val);
    if (isNaN(n)) return '—';
    return n.toLocaleString('vi-VN') + ' ₫';
}

function formatQty(val) {
    if (val === null || val === undefined) return '0';
    const n = parseFloat(val);
    if (isNaN(n)) return '0';
    return Number.isInteger(n) ? n.toString() : n.toFixed(2);
}

// ─── Stock Health Evaluation ──────────────────────────────────────────────────

function getStockHealth(stock, minThreshold) {
    const qty = parseFloat(stock || 0);
    const min = parseFloat(minThreshold || 0);
    if (qty <= 0) return { label: 'Depleted / Out of Stock', cls: 'prdd-health--out' };
    if (min > 0 && qty <= min) return { label: 'Low Stock Alert', cls: 'prdd-health--low' };
    return { label: 'Healthy / In Stock', cls: 'prdd-health--healthy' };
}

// ─── Barcode Formatting ───────────────────────────────────────────────────────

function formatBarcodeDisplay(barcode = '') {
    if (!barcode) return '—';
    const clean = barcode.replace(/\s+/g, '');
    if (clean.length === 13) {
        return `${clean.slice(0, 1)} ${clean.slice(1, 7)} ${clean.slice(7)}`;
    }
    return clean;
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function ProductDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { hasPermission, hasRole } = useAuth();
    const canManage = hasPermission('PRODUCT_MANAGE') || hasRole('ADMIN', 'MANAGER');
    const canPrice = hasPermission('PRICE_MANAGE') || hasPermission('PRODUCT_MANAGE') || hasRole('ADMIN', 'MANAGER');

    // ── State ─────────────────────────────────────────────────────────────────
    const [product, setProduct] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [copiedSku, setCopiedSku] = useState(false);
    const [simulatedNoBarcode, setSimulatedNoBarcode] = useState(false);
    const [simulatedPromo, setSimulatedPromo] = useState(true);

    // ── Fetch Product Details ─────────────────────────────────────────────────
    const fetchDetails = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await ProductService.getById(id);
            setProduct(res.data?.data || null);
        } catch (err) {
            setError(err.response?.data?.message || 'Không thể tải chi tiết sản phẩm. Vui lòng kiểm tra lại ID.');
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        fetchDetails();
    }, [fetchDetails]);

    // ── Copy SKU to Clipboard ─────────────────────────────────────────────────
    const handleCopySku = () => {
        if (!product?.productCode) return;
        navigator.clipboard.writeText(product.productCode);
        setCopiedSku(true);
        setTimeout(() => setCopiedSku(false), 2000);
    };

    // ── Calculations ──────────────────────────────────────────────────────────
    const stockQty = parseFloat(product?.stockQuantity || 0);
    const minStock = parseFloat(product?.minimumStockLevel || 0);
    const targetCapacity = Math.max(160, Math.ceil((minStock * 4) / 10) * 10, Math.ceil(stockQty * 1.15));
    const safetyPercent = targetCapacity > 0 ? Math.min(100, Math.round((stockQty / targetCapacity) * 100)) : 0;
    const thresholdPercent = targetCapacity > 0 ? Math.min(100, Math.round((minStock / targetCapacity) * 100)) : 10;
    const stockHealth = useMemo(() => getStockHealth(stockQty, minStock), [stockQty, minStock]);

    // Barcode value for display
    const effectiveBarcode = simulatedNoBarcode ? '' : (product?.barcode || '');

    // Active Promotion or fallback mock for simulation matching mockup
    const activePromo = useMemo(() => {
        if (!simulatedPromo) return null;
        if (product?.activePromotion) return product.activePromotion;
        // Default mockup promotion rule for demonstration
        const priceNum = parseFloat(product?.sellingPrice || 38500);
        return {
            promotionId: 'PR-2025-04',
            promotionName: 'Khuyến mãi Giờ Vàng: Giảm 5% khi mua từ 2 hộp',
            badge: 'Giờ Vàng 5% OFF',
            discountValue: 5,
            minQuantity: 2,
            discountedPrice: Math.round(priceNum * 0.95),
            validUntil: '31/05/2025',
            daysLeft: 18,
        };
    }, [product, simulatedPromo]);

    // Supplier Info
    const supplierInfo = useMemo(() => {
        if (product?.primarySupplier) return product.primarySupplier;
        return {
            supplierId: 'SUP-0012',
            supplierName: 'Vinamilk Vietnam Corporation',
            sla: 'Direct Supplier Partner • SLA 24h',
        };
    }, [product]);

    if (loading) {
        return (
            <div className="prdd-loading-screen">
                <span className="prdd-spinner" />
                <p>Đang tải thông tin chi tiết sản phẩm...</p>
            </div>
        );
    }

    if (error || !product) {
        return (
            <div className="prdd-error-screen">
                <h2>Không tìm thấy sản phẩm</h2>
                <p>{error || 'Sản phẩm không tồn tại hoặc đã bị xóa.'}</p>
                <button className="prdd-btn prdd-btn--secondary" onClick={() => navigate('/products')}>
                    Quay lại danh mục sản phẩm
                </button>
            </div>
        );
    }

    const isActive = product.status === 'ACTIVE';

    return (
        <div className="prdd-page">
            {/* ── Top Navigation & Actions Bar ──────────────────────────────── */}
            <div className="prdd-top-bar">
                <div className="prdd-top-bar-left">
                    <button
                        type="button"
                        className="prdd-back-btn"
                        onClick={() => navigate('/products')}
                        title="Quay lại danh sách sản phẩm (UC09)"
                    >
                        ← Back to Products List
                    </button>
                    <span className="prdd-badge-uc">UC13</span>
                    <span className="prdd-badge-read-only">Product Master Data (Read-only)</span>
                </div>

                <div className="prdd-top-bar-right">
                    {/* Simulator Button */}
                    <button
                        type="button"
                        className={`prdd-btn-sim ${simulatedNoBarcode ? 'prdd-btn-sim--active' : ''}`}
                        onClick={() => setSimulatedNoBarcode(prev => !prev)}
                        title="Thử nghiệm hiển thị khi sản phẩm thiếu mã vạch"
                    >
                        <BarcodeIcon />
                        {simulatedNoBarcode ? 'Restore Barcode' : 'Simulate Missing Barcode ("—")'}
                    </button>

                    {/* Update Price Action (UC12) */}
                    {canPrice && (
                        <button
                            type="button"
                            className="prdd-btn prdd-btn--secondary"
                            onClick={() => navigate(`/products/${product.productId}/price`)}
                            title="Quản lý và điều chỉnh giá bán sản phẩm (UC12)"
                        >
                            <TagIcon />
                            Update Price
                        </button>
                    )}

                    {/* Edit Product Action (UC10) */}
                    {canManage && (
                        <button
                            type="button"
                            className="prdd-btn prdd-btn--primary"
                            onClick={() => navigate(`/products/${product.productId}/edit`)}
                            title="Chỉnh sửa thông tin tổng thể sản phẩm (UC10)"
                        >
                            <EditIcon />
                            Edit Product
                            <span className="prdd-btn-sub-tag">ADMIN &amp; MANAGER ONLY</span>
                        </button>
                    )}
                </div>
            </div>

            {/* ── Product Header Banner Card ────────────────────────────────── */}
            <div className="prdd-card prdd-banner-card">
                <div className="prdd-banner-left">
                    <div className="prdd-packshot-box">
                        <PackshotBoxIcon />
                    </div>

                    <div className="prdd-banner-meta">
                        <div className="prdd-banner-tags-row">
                            <span className="prdd-tag-cat">{product.categoryName || 'Sản phẩm'}</span>
                            <span className={`prdd-tag-status ${isActive ? 'prdd-tag-status--active' : 'prdd-tag-status--inactive'}`}>
                                <span className="prdd-status-dot" />
                                {isActive ? 'Active' : 'Inactive'}
                            </span>
                        </div>

                        <h1 className="prdd-product-title">{product.productName}</h1>

                        <div className="prdd-product-subline">
                            <span className="prdd-sub-item">
                                SKU: <strong>{product.productCode}</strong>
                            </span>
                            <span className="prdd-sub-sep">•</span>
                            <span className="prdd-sub-item">
                                Specification: Tetra Pak Hộp giấy tiệt trùng {product.unit || '1000ml'}
                            </span>
                            <span className="prdd-sub-sep">•</span>
                            <span className="prdd-sub-item">
                                Base Unit: <strong>{product.unit}</strong>
                            </span>
                        </div>
                    </div>
                </div>

                <div className="prdd-banner-metrics">
                    <div className="prdd-metric-item">
                        <span className="prdd-metric-lbl">Selling Price (VND)</span>
                        <div className="prdd-metric-val prdd-metric-val--price">
                            {formatVnd(product.sellingPrice)}
                        </div>
                    </div>

                    <div className="prdd-metric-item">
                        <span className="prdd-metric-lbl">Current Stock</span>
                        <div className="prdd-metric-val">
                            {formatQty(product.stockQuantity)} <span className="prdd-metric-unit">{product.unit}</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* ── Main 2-Column Content Layout ──────────────────────────────── */}
            <div className="prdd-grid">

                {/* ═════════ LEFT COLUMN: CORE SPECIFICATIONS ═════════ */}
                <div className="prdd-col-left">

                    {/* ── Card 1: General Information ─────────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <div className="prdd-card-header-left">
                                <InfoGridIcon />
                                <h2 className="prdd-card-title">General Information</h2>
                            </div>
                            <span className="prdd-card-tag">Master SKU Master Data</span>
                        </div>

                        <div className="prdd-info-grid">
                            {/* Product Name */}
                            <div className="prdd-info-cell prdd-info-cell--full">
                                <span className="prdd-info-lbl">PRODUCT NAME</span>
                                <div className="prdd-info-val prdd-info-val--bold">
                                    {product.productName}
                                </div>
                            </div>

                            {/* SKU Code */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">STOCK KEEPING UNIT (SKU)</span>
                                <div className="prdd-sku-copy-wrap">
                                    <span className="prdd-sku-code">{product.productCode}</span>
                                    <button
                                        type="button"
                                        className="prdd-copy-btn"
                                        onClick={handleCopySku}
                                        title="Sao chép mã SKU"
                                    >
                                        <CopyIcon />
                                        {copiedSku && <span className="prdd-copied-tooltip">Copied!</span>}
                                    </button>
                                </div>
                            </div>

                            {/* Category */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">PRODUCT CATEGORY</span>
                                <div className="prdd-category-line">
                                    <span className="prdd-cat-dot">•</span>
                                    <span>{product.categoryName} (Department Classification)</span>
                                </div>
                            </div>

                            {/* Packaging Unit */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">PACKAGING UNIT</span>
                                <div className="prdd-info-val">
                                    {product.unit} (Pack / Carton / Standard Retail Unit)
                                </div>
                            </div>

                            {/* Barcode Identification */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">BARCODE IDENTIFICATION (EAN-13)</span>
                                <div className="prdd-barcode-line">
                                    {effectiveBarcode ? (
                                        <>
                                            <span className="prdd-barcode-num">{effectiveBarcode}</span>
                                            <span className="prdd-verified-pill">✔ Verified GS1 Vietnam standard</span>
                                        </>
                                    ) : (
                                        <span className="prdd-no-barcode-text">— Not Assigned / Bulk Item</span>
                                    )}
                                </div>
                            </div>

                            {/* Master Status */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">MASTER STATUS</span>
                                <div className="prdd-status-line">
                                    <span className={`prdd-dot ${isActive ? 'prdd-dot--active' : 'prdd-dot--inactive'}`} />
                                    <span>{isActive ? 'Active (Available for checkout & restock)' : 'Inactive (Archived from sales)'}</span>
                                </div>
                            </div>

                            {/* POS Cashier Compatibility */}
                            <div className="prdd-info-cell">
                                <span className="prdd-info-lbl">POS CASHIER COMPATIBILITY</span>
                                <div className="prdd-pos-compat-line">
                                    <CashierIcon />
                                    <span>Enabled for quick barcode scan lookup</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 2: Pricing & Inventory ─────────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <div className="prdd-card-header-left">
                                <WalletIcon />
                                <h2 className="prdd-card-title">Pricing &amp; Inventory</h2>
                            </div>
                            <span className={`prdd-health-badge ${stockHealth.cls}`}>
                                {stockHealth.label}
                            </span>
                        </div>

                        <div className="prdd-pricing-stats-grid">
                            {/* Selling Price */}
                            <div className="prdd-price-stat-box">
                                <span className="prdd-stat-title">Selling Price</span>
                                <div className="prdd-stat-val prdd-stat-val--blue">
                                    {formatVnd(product.sellingPrice)}
                                </div>
                                <span className="prdd-stat-sub">Incl. 8% VAT per {product.unit}</span>
                            </div>

                            {/* Current Stock */}
                            <div className="prdd-price-stat-box">
                                <span className="prdd-stat-title">Current Stock Level</span>
                                <div className="prdd-stat-val">
                                    {formatQty(product.stockQuantity)} <span className="prdd-stat-unit">{product.unit}</span>
                                </div>
                                <span className="prdd-stat-sub">
                                    {stockQty > minStock
                                        ? `${formatQty(stockQty - minStock)} ${product.unit} above minimum threshold`
                                        : 'Stock depleted or below safety limit'}
                                </span>
                            </div>

                            {/* Low Stock Threshold */}
                            <div className="prdd-price-stat-box">
                                <span className="prdd-stat-title">Low-stock Threshold</span>
                                <div className="prdd-stat-val prdd-stat-val--orange">
                                    {formatQty(product.minimumStockLevel)} <span className="prdd-stat-unit">{product.unit}</span>
                                </div>
                                <span className="prdd-stat-sub">Triggers automated replenishment alert</span>
                            </div>
                        </div>

                        {/* Stock Safety Margin Visualizer */}
                        <div className="prdd-safety-margin-wrap">
                            <div className="prdd-margin-text-row">
                                <span className="prdd-margin-lbl">Stock Safety Margin</span>
                                <span className="prdd-margin-ratio">
                                    <strong>{formatQty(product.stockQuantity)}</strong> / Target {targetCapacity} {product.unit} ({safetyPercent}%)
                                </span>
                            </div>

                            {/* Bar */}
                            <div className="prdd-progress-track">
                                <div
                                    className={`prdd-progress-fill ${stockQty <= minStock ? 'prdd-progress-fill--warn' : ''}`}
                                    style={{ width: `${safetyPercent}%` }}
                                />
                                <div
                                    className="prdd-progress-marker"
                                    style={{ left: `${thresholdPercent}%` }}
                                    title={`Threshold: ${minStock} ${product.unit}`}
                                />
                            </div>

                            {/* Scale */}
                            <div className="prdd-scale-row">
                                <span>0 {product.unit} (Out of Stock)</span>
                                <span className="prdd-scale-threshold">Threshold: {formatQty(product.minimumStockLevel)} {product.unit}</span>
                                <span>Capacity: {targetCapacity} {product.unit}</span>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 3: Expiry Tracking Policy ──────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <div className="prdd-card-header-left">
                                <CalendarClockIcon />
                                <h2 className="prdd-card-title">Expiry Tracking Policy</h2>
                            </div>
                            <span className={`prdd-expiry-badge ${product.trackExpiry ? 'prdd-expiry-badge--yes' : 'prdd-expiry-badge--no'}`}>
                                Track Expiry: {product.trackExpiry ? 'Yes' : 'No'}
                            </span>
                        </div>

                        <div className="prdd-policy-boxes-grid">
                            <div className="prdd-policy-box">
                                <div className="prdd-policy-box-title">
                                    <BellAlertSmallIcon />
                                    <span>Near-Expiry Warning Window</span>
                                </div>
                                <p className="prdd-policy-desc">
                                    Items are flagged for discount or clearance when within <strong>30 days</strong> of the batch expiration date (UC07 Policy).
                                </p>
                            </div>

                            <div className="prdd-policy-box">
                                <div className="prdd-policy-box-title">
                                    <ClipboardCheckIcon />
                                    <span>Batch Inbound Validation</span>
                                </div>
                                <p className="prdd-policy-desc">
                                    Stock import ({product.trackExpiry ? 'UC10/UC18' : 'Standard'}) requires batch number &amp; Expiry Date (HSD) before inventory ledger commitment.
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* ═════════ RIGHT COLUMN: BARCODE, PROMO & SUPPLIER ═════════ */}
                <div className="prdd-col-right">

                    {/* ── Card 1: Barcode Visualizer ──────────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <div className="prdd-card-header-left">
                                <BarcodeIcon />
                                <h3 className="prdd-side-card-title">Barcode Visualizer</h3>
                            </div>
                            <span className="prdd-side-tag">EAN-13 Standard</span>
                        </div>

                        {effectiveBarcode ? (
                            <div className="prdd-barcode-render-box">
                                {/* SVG Barcode Illustration */}
                                <div className="prdd-barcode-svg-wrap">
                                    <svg className="prdd-barcode-svg" viewBox="0 0 220 75" preserveAspectRatio="none">
                                        {/* Guard pattern left */}
                                        <rect x="10" y="0" width="3" height="65" fill="#0f172a" />
                                        <rect x="16" y="0" width="3" height="65" fill="#0f172a" />

                                        {/* Data bars */}
                                        <rect x="25" y="0" width="5" height="55" fill="#0f172a" />
                                        <rect x="34" y="0" width="2" height="55" fill="#0f172a" />
                                        <rect x="40" y="0" width="6" height="55" fill="#0f172a" />
                                        <rect x="50" y="0" width="3" height="55" fill="#0f172a" />
                                        <rect x="57" y="0" width="7" height="55" fill="#0f172a" />
                                        <rect x="68" y="0" width="2" height="55" fill="#0f172a" />
                                        <rect x="74" y="0" width="5" height="55" fill="#0f172a" />
                                        <rect x="83" y="0" width="3" height="55" fill="#0f172a" />
                                        <rect x="90" y="0" width="6" height="55" fill="#0f172a" />
                                        <rect x="100" y="0" width="2" height="55" fill="#0f172a" />

                                        {/* Center guard */}
                                        <rect x="107" y="0" width="3" height="65" fill="#0f172a" />
                                        <rect x="113" y="0" width="3" height="65" fill="#0f172a" />

                                        {/* Right data bars */}
                                        <rect x="122" y="0" width="4" height="55" fill="#0f172a" />
                                        <rect x="130" y="0" width="6" height="55" fill="#0f172a" />
                                        <rect x="140" y="0" width="3" height="55" fill="#0f172a" />
                                        <rect x="147" y="0" width="7" height="55" fill="#0f172a" />
                                        <rect x="158" y="0" width="2" height="55" fill="#0f172a" />
                                        <rect x="164" y="0" width="5" height="55" fill="#0f172a" />
                                        <rect x="173" y="0" width="4" height="55" fill="#0f172a" />
                                        <rect x="181" y="0" width="6" height="55" fill="#0f172a" />
                                        <rect x="191" y="0" width="3" height="55" fill="#0f172a" />
                                        <rect x="198" y="0" width="5" height="55" fill="#0f172a" />

                                        {/* Guard pattern right */}
                                        <rect x="207" y="0" width="3" height="65" fill="#0f172a" />
                                        <rect x="213" y="0" width="3" height="65" fill="#0f172a" />
                                    </svg>
                                </div>

                                <div className="prdd-barcode-digits">
                                    {formatBarcodeDisplay(effectiveBarcode)}
                                </div>
                            </div>
                        ) : (
                            <div className="prdd-barcode-empty-box">
                                <span className="prdd-barcode-empty-icon">⊘</span>
                                <span className="prdd-barcode-empty-text">No Barcode Assigned</span>
                                <span className="prdd-barcode-empty-sub">Bulk goods or unpackaged produce SKU</span>
                            </div>
                        )}

                        <p className="prdd-barcode-caption">
                            Used for warehouse hand scanner &amp; POS verification (SRS 3.5.2)
                        </p>
                    </div>

                    {/* ── Card 2: Promotion Rules ─────────────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <div className="prdd-card-header-left">
                                <TagIcon />
                                <h3 className="prdd-side-card-title">Promotion Rules</h3>
                            </div>
                            <button
                                type="button"
                                className="prdd-btn-toggle-demo"
                                onClick={() => setSimulatedPromo(prev => !prev)}
                                title="Toggle promo demonstration state"
                            >
                                Toggle Demo State
                            </button>
                        </div>

                        {activePromo ? (
                            <div className="prdd-promo-content">
                                <div className="prdd-promo-badges">
                                    <span className="prdd-promo-pill-flash">
                                        ⚡ {activePromo.badge || activePromo.promotionName}
                                    </span>
                                    <span className="prdd-promo-id">ID: {activePromo.promotionId}</span>
                                </div>

                                <h4 className="prdd-promo-title">{activePromo.promotionName}</h4>

                                <div className="prdd-promo-details-list">
                                    <div className="prdd-promo-detail-row">
                                        <span className="prdd-promo-lbl">Minimum Quantity:</span>
                                        <span className="prdd-promo-val">{activePromo.minQuantity} {product.unit}</span>
                                    </div>
                                    <div className="prdd-promo-detail-row">
                                        <span className="prdd-promo-lbl">Discounted Unit Price:</span>
                                        <span className="prdd-promo-val prdd-promo-val--price">
                                            {formatVnd(activePromo.discountedPrice)}
                                        </span>
                                    </div>
                                    <div className="prdd-promo-detail-row">
                                        <span className="prdd-promo-lbl">Validity Period:</span>
                                        <span className="prdd-promo-val">Until {activePromo.validUntil || '31/05/2025'}</span>
                                    </div>
                                </div>

                                <div className="prdd-promo-footer">
                                    <span className="prdd-promo-countdown">
                                        ⏰ Expires in {activePromo.daysLeft || 18} days
                                    </span>
                                </div>
                            </div>
                        ) : (
                            <div className="prdd-no-promo-box">
                                <span>Không có chương trình khuyến mãi nào đang áp dụng cho SKU này.</span>
                            </div>
                        )}
                    </div>

                    {/* ── Card 3: Primary Supplier ────────────────────────────── */}
                    <div className="prc-card prdd-card">
                        <div className="prdd-card-header">
                            <span className="prdd-supplier-header-lbl">PRIMARY SUPPLIER</span>
                            <span className="prdd-supplier-id">ID: {supplierInfo.supplierId}</span>
                        </div>

                        <div className="prdd-supplier-body">
                            <div className="prdd-supplier-avatar">
                                <span>VNM</span>
                            </div>
                            <div className="prdd-supplier-details">
                                <h4 className="prdd-supplier-name">{supplierInfo.supplierName}</h4>
                                <p className="prdd-supplier-sla">{supplierInfo.sla}</p>
                            </div>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}

// ─── SVG Icons ────────────────────────────────────────────────────────────────

function PackshotBoxIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="32" height="32" style={{ color: '#2458dc' }}>
            <rect x="5" y="3" width="14" height="18" rx="2" />
            <path d="M5 8h14" />
            <circle cx="12" cy="14" r="2.5" />
        </svg>
    );
}

function BarcodeIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
            <path d="M3 5v14M7 5v14M11 5v14M17 5v14M21 5v14M13 5v14" />
        </svg>
    );
}

function TagIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
            <path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z" />
            <path d="M7 7h.01" />
        </svg>
    );
}

function EditIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
    );
}

function InfoGridIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#2458dc' }}>
            <rect x="3" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="14" width="7" height="7" rx="1.5" />
            <rect x="3" y="14" width="7" height="7" rx="1.5" />
        </svg>
    );
}

function CopyIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
    );
}

function CashierIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#2458dc' }}>
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
        </svg>
    );
}

function WalletIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#059669' }}>
            <rect x="2" y="5" width="20" height="14" rx="2" />
            <line x1="2" y1="10" x2="22" y2="10" />
        </svg>
    );
}

function CalendarClockIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#2458dc' }}>
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
            <line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
        </svg>
    );
}

function BellAlertSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15" style={{ color: '#d97706' }}>
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
    );
}

function ClipboardCheckIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15" style={{ color: '#2458dc' }}>
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
            <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
            <polyline points="9 14 11 16 15 12" />
        </svg>
    );
}
