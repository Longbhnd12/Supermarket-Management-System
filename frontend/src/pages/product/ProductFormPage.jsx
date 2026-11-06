/**
 * ProductFormPage — UC10: Manage Products (Create, Edit)
 *
 * Implements the full master product form matching the UC10 mockup specification:
 * - Identification & Classification (SKU, Barcode, Name, Category)
 * - Pricing & Measurement (Selling Price, Base Unit with quick presets)
 * - Inventory Rules & Lifecycle (Low-stock threshold, Expiration tracking)
 * - Real-time Live POS Cashier Simulation Card
 * - SRS 3.5.3 Specs Summary & Manager Tips
 *
 * Mode:
 *   /products/create   -> Create Mode (Selling Price is editable)
 *   /products/:id/edit -> Edit Mode (Selling Price is read-only per UC12 thong.txt rule)
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import CategoryService from '../../services/categoryService';
import { useAuth } from '../../context/authContext';
import './ProductFormPage.css';

const UNIT_PRESETS = ['Hộp', 'Chai', 'Lon', 'Gói', 'Thùng', 'Kg', 'Túi'];

export default function ProductFormPage() {
    const navigate = useNavigate();
    const { id } = useParams();
    const isEdit = Boolean(id);

    const { hasPermission, hasRole } = useAuth();
    const canManage = hasPermission('PRODUCT_MANAGE') || hasRole('ADMIN', 'MANAGER');

    // ── Form State ────────────────────────────────────────────────────────────
    const [formData, setFormData] = useState({
        productCode: '',
        barcode: '',
        productName: '',
        categoryId: '',
        sellingPrice: '',
        unit: 'Hộp',
        minimumStockLevel: '15',
        trackExpiry: false,
    });

    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(isEdit);
    const [submitting, setSubmitting] = useState(false);
    const [serverError, setServerError] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const [saveSuccess, setSaveSuccess] = useState(false);

    // ── Load Categories & Existing Product ────────────────────────────────────
    useEffect(() => {
        CategoryService.getAll()
            .then(res => {
                const cats = res.data?.data || [];
                setCategories(cats);
                // If creating and no category selected yet, pick the first one
                if (!isEdit && cats.length > 0) {
                    setFormData(prev => ({
                        ...prev,
                        categoryId: prev.categoryId || String(cats[0].categoryId),
                    }));
                }
            })
            .catch(() => setCategories([]));
    }, [isEdit]);

    useEffect(() => {
        if (!isEdit) return;

        setLoading(true);
        setServerError('');

        ProductService.getById(id)
            .then(res => {
                const p = res.data?.data;
                if (p) {
                    setFormData({
                        productCode: p.productCode || '',
                        barcode: p.barcode || '',
                        productName: p.productName || '',
                        categoryId: p.categoryId ? String(p.categoryId) : '',
                        sellingPrice: p.sellingPrice != null ? String(p.sellingPrice) : '',
                        unit: p.unit || 'Hộp',
                        minimumStockLevel: p.minimumStockLevel != null ? String(p.minimumStockLevel) : '0',
                        trackExpiry: Boolean(p.trackExpiry),
                    });
                }
            })
            .catch(err => {
                setServerError(err.response?.data?.message || 'Không thể tải thông tin sản phẩm. Vui lòng kiểm tra lại ID.');
            })
            .finally(() => setLoading(false));
    }, [id, isEdit]);

    // ── Change Handlers ───────────────────────────────────────────────────────
    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        // Clear field error on edit
        if (fieldErrors[field]) {
            setFieldErrors(prev => {
                const next = { ...prev };
                delete next[field];
                return next;
            });
        }
    };

    // ── Client-side Validation ────────────────────────────────────────────────
    const validate = () => {
        const errors = {};
        if (!formData.productCode.trim()) {
            errors.productCode = 'Mã SKU không được để trống';
        } else if (formData.productCode.trim().length > 50) {
            errors.productCode = 'Mã SKU tối đa 50 ký tự';
        }

        if (formData.barcode && formData.barcode.trim().length > 100) {
            errors.barcode = 'Mã vạch tối đa 100 ký tự';
        }

        if (!formData.productName.trim()) {
            errors.productName = 'Tên sản phẩm không được để trống';
        } else if (formData.productName.trim().length > 200) {
            errors.productName = 'Tên sản phẩm tối đa 200 ký tự';
        }

        if (!formData.categoryId) {
            errors.categoryId = 'Vui lòng chọn danh mục sản phẩm';
        }

        if (!formData.unit.trim()) {
            errors.unit = 'Đơn vị tính không được để trống';
        } else if (formData.unit.trim().length > 30) {
            errors.unit = 'Đơn vị tính tối đa 30 ký tự';
        }

        const priceNum = parseFloat(formData.sellingPrice);
        if (isNaN(priceNum) || priceNum < 0) {
            errors.sellingPrice = 'Giá bán phải là số lớn hơn hoặc bằng 0';
        }

        const minStockNum = parseFloat(formData.minimumStockLevel);
        if (isNaN(minStockNum) || minStockNum < 0) {
            errors.minimumStockLevel = 'Ngưỡng cảnh báo tồn kho tối thiểu không được âm';
        }

        setFieldErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // ── Submit Form (Create / Edit) ───────────────────────────────────────────
    const handleSubmit = async (e) => {
        if (e) e.preventDefault();

        if (!validate()) {
            setServerError('Vui lòng kiểm tra lại các trường thông tin bị lỗi.');
            return;
        }

        setSubmitting(true);
        setServerError('');

        const payload = {
            productCode: formData.productCode.trim(),
            barcode: formData.barcode.trim() || null,
            productName: formData.productName.trim(),
            categoryId: Number(formData.categoryId),
            sellingPrice: parseFloat(formData.sellingPrice) || 0,
            unit: formData.unit.trim(),
            minimumStockLevel: parseFloat(formData.minimumStockLevel) || 0,
            trackExpiry: Boolean(formData.trackExpiry),
        };

        try {
            if (isEdit) {
                await ProductService.update(id, payload);
            } else {
                await ProductService.create(payload);
            }
            setSaveSuccess(true);
            setTimeout(() => {
                navigate('/products', {
                    state: {
                        notice: isEdit
                            ? `Cập nhật sản phẩm "${payload.productName}" thành công!`
                            : `Tạo sản phẩm mới "${payload.productName}" thành công!`
                    }
                });
            }, 700);
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Lỗi khi lưu sản phẩm. Vui lòng thử lại.';
            setServerError(msg);
            if (msg.toLowerCase().includes('sku')) {
                setFieldErrors(prev => ({ ...prev, productCode: msg }));
            } else if (msg.toLowerCase().includes('barcode') || msg.toLowerCase().includes('mã vạch')) {
                setFieldErrors(prev => ({ ...prev, barcode: msg }));
            }
        } finally {
            setSubmitting(false);
        }
    };

    // ── Helper: Category Name for Preview ─────────────────────────────────────
    const selectedCategoryName = useMemo(() => {
        const cat = categories.find(c => String(c.categoryId) === String(formData.categoryId));
        return cat?.categoryName || 'CHƯA CHỌN DANH MỤC';
    }, [categories, formData.categoryId]);

    const formattedPricePreview = useMemo(() => {
        const n = parseFloat(formData.sellingPrice);
        if (isNaN(n)) return '0 ₫';
        return n.toLocaleString('vi-VN') + ' ₫';
    }, [formData.sellingPrice]);

    if (loading) {
        return (
            <div className="prd-form-loading">
                <span className="prd-form-spinner" />
                <p>Đang tải thông tin sản phẩm...</p>
            </div>
        );
    }

    return (
        <div className="prd-form-page">
            {/* ── Breadcrumb & Top Header ───────────────────────────────────── */}
            <div className="prd-form-header">
                <div className="prd-form-header-left">
                    <div className="prd-form-breadcrumb">
                        <Link to="/products" className="prd-form-breadcrumb-link">Catalog &amp; Inventory</Link>
                        <span className="prd-form-breadcrumb-sep">/</span>
                        <Link to="/products" className="prd-form-breadcrumb-link">Products</Link>
                        <span className="prd-form-breadcrumb-sep">/</span>
                        <span className="prd-form-breadcrumb-current">
                            {isEdit ? `Edit Product #${formData.productCode || id}` : 'Create New Product'}
                        </span>
                    </div>

                    <div className="prd-form-title-wrap">
                        <h1 className="prd-form-title">
                            {isEdit ? 'Edit Product Master Data' : 'Create New Product Master Data'}
                        </h1>
                        <span className="prd-form-role-badge">
                            Role Access: Admin &amp; Manager Only
                        </span>
                    </div>
                </div>

                <div className="prd-form-header-actions">
                    <button
                        type="button"
                        className="prd-btn prd-btn--secondary"
                        onClick={() => navigate('/products')}
                        disabled={submitting}
                    >
                        ✕ Cancel
                    </button>
                    <button
                        type="button"
                        className="prd-btn prd-btn--primary"
                        onClick={handleSubmit}
                        disabled={submitting || !canManage}
                    >
                        {submitting ? (
                            <><span className="prd-btn-spinner" /> Saving...</>
                        ) : saveSuccess ? (
                            <>✓ Saved Successfully</>
                        ) : (
                            <>✔ {isEdit ? 'Save Product' : 'Create Product'}</>
                        )}
                    </button>
                </div>
            </div>

            {/* ── Global Server Error Alert ─────────────────────────────────── */}
            {serverError && (
                <div className="prd-form-alert prd-form-alert--error" role="alert">
                    <span className="prd-form-alert-icon">⚠</span>
                    <span>{serverError}</span>
                </div>
            )}

            {/* ── Main 2-Column Content ─────────────────────────────────────── */}
            <form onSubmit={handleSubmit} className="prd-form-grid">
                
                {/* ═════════ LEFT COLUMN: FORM CARDS ═════════ */}
                <div className="prd-form-col-left">

                    {/* ── Card 1: Identification & Classification ─────────────── */}
                    <div className="prd-card prd-form-card">
                        <div className="prd-card-header">
                            <div className="prd-card-header-badge">1</div>
                            <div className="prd-card-header-text">
                                <div className="prd-card-header-title-row">
                                    <h2 className="prd-card-title">Identification &amp; Classification</h2>
                                    <span className="prd-card-tag">SRS 3.5.3 REQUIRED</span>
                                </div>
                                <p className="prd-card-subtitle">
                                    Core SKUs, barcodes, official naming, and category classification
                                </p>
                            </div>
                        </div>

                        <div className="prd-card-body">
                            {/* SKU */}
                            <div className="prd-field-group">
                                <div className="prd-label-row">
                                    <label htmlFor="sku-input" className="prd-label">
                                        SKU (Stock Keeping Unit) <span className="prd-req">*</span>
                                    </label>
                                    <div className="prd-label-meta">
                                        <span className={`prd-char-counter ${formData.productCode.length > 50 ? 'prd-char-counter--warn' : ''}`}>
                                            {formData.productCode.length} / 50
                                        </span>
                                        {formData.productCode.trim() && !fieldErrors.productCode && (
                                            <span className="prd-status-pill prd-status-pill--success">
                                                ✓ Available
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <input
                                    id="sku-input"
                                    type="text"
                                    className={`prd-input prd-input--mono ${fieldErrors.productCode ? 'prd-input--error' : ''}`}
                                    placeholder="e.g. SKU-8930124"
                                    maxLength={50}
                                    value={formData.productCode}
                                    onChange={e => handleChange('productCode', e.target.value.toUpperCase())}
                                    disabled={submitting}
                                    required
                                />
                                {fieldErrors.productCode ? (
                                    <span className="prd-field-error">{fieldErrors.productCode}</span>
                                ) : (
                                    <span className="prd-field-hint">
                                        Unique stock keeping unit code across store catalog (Max 50 characters). Unique Key Constraint.
                                    </span>
                                )}
                            </div>

                            {/* Barcode */}
                            <div className="prd-field-group">
                                <div className="prd-label-row">
                                    <label htmlFor="barcode-input" className="prd-label">
                                        Barcode (GS1 EAN-13) <span className="prd-opt">(Optional)</span>
                                    </label>
                                    <div className="prd-label-meta">
                                        <span className={`prd-char-counter ${formData.barcode.length > 100 ? 'prd-char-counter--warn' : ''}`}>
                                            {formData.barcode.length} / 100
                                        </span>
                                        <span className="prd-spec-badge">GS1 VN EAN-13</span>
                                    </div>
                                </div>
                                <div className="prd-input-with-icon">
                                    <BarcodeIcon className="prd-input-left-icon" />
                                    <input
                                        id="barcode-input"
                                        type="text"
                                        className={`prd-input prd-input--mono ${fieldErrors.barcode ? 'prd-input--error' : ''}`}
                                        placeholder="e.g. 8934567890123"
                                        maxLength={100}
                                        value={formData.barcode}
                                        onChange={e => handleChange('barcode', e.target.value)}
                                        disabled={submitting}
                                    />
                                </div>
                                {fieldErrors.barcode ? (
                                    <span className="prd-field-error">{fieldErrors.barcode}</span>
                                ) : (
                                    <span className="prd-field-hint">
                                        Standard GS1 EAN-13 or manufacturer barcode. Leave blank if unscannable or bulk item.
                                    </span>
                                )}
                            </div>

                            {/* Product Name */}
                            <div className="prd-field-group">
                                <div className="prd-label-row">
                                    <label htmlFor="name-input" className="prd-label">
                                        Product Name <span className="prd-req">*</span>
                                    </label>
                                    <span className={`prd-char-counter ${formData.productName.length > 200 ? 'prd-char-counter--warn' : ''}`}>
                                        {formData.productName.length} / 200
                                    </span>
                                </div>
                                <input
                                    id="name-input"
                                    type="text"
                                    className={`prd-input ${fieldErrors.productName ? 'prd-input--error' : ''}`}
                                    placeholder="e.g. Sữa tươi tiệt trùng Vinamilk 100% Không đường 1L"
                                    maxLength={200}
                                    value={formData.productName}
                                    onChange={e => handleChange('productName', e.target.value)}
                                    disabled={submitting}
                                    required
                                />
                                {fieldErrors.productName ? (
                                    <span className="prd-field-error">{fieldErrors.productName}</span>
                                ) : (
                                    <span className="prd-field-hint">
                                        Official commercial display name on POS registers and customer receipts (Max 200 characters).
                                    </span>
                                )}
                            </div>

                            {/* Category */}
                            <div className="prd-field-group">
                                <div className="prd-label-row">
                                    <label htmlFor="category-select" className="prd-label">
                                        Category <span className="prd-req">*</span>
                                    </label>
                                    <span className="prd-spec-badge">SRS Presets</span>
                                </div>
                                <select
                                    id="category-select"
                                    className={`prd-select ${fieldErrors.categoryId ? 'prd-input--error' : ''}`}
                                    value={formData.categoryId}
                                    onChange={e => handleChange('categoryId', e.target.value)}
                                    disabled={submitting}
                                    required
                                >
                                    <option value="" disabled>-- Chọn danh mục sản phẩm --</option>
                                    {categories.map(cat => (
                                        <option key={cat.categoryId} value={cat.categoryId}>
                                            {cat.categoryName} {cat.description ? `(${cat.description})` : ''}
                                        </option>
                                    ))}
                                </select>
                                {fieldErrors.categoryId ? (
                                    <span className="prd-field-error">{fieldErrors.categoryId}</span>
                                ) : (
                                    <span className="prd-field-hint">
                                        Primary reporting hierarchy and department shelf grouping.
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* ── Card 2: Pricing & Measurement ──────────────────────── */}
                    <div className="prd-card prd-form-card">
                        <div className="prd-card-header">
                            <div className="prd-card-header-badge">2</div>
                            <div className="prd-card-header-text">
                                <div className="prd-card-header-title-row">
                                    <h2 className="prd-card-title">Pricing &amp; Measurement</h2>
                                    <span className="prd-card-tag">FINANCIAL PRECISION</span>
                                </div>
                                <p className="prd-card-subtitle">
                                    Retail cash checkout currency and base inventory counting units
                                </p>
                            </div>
                        </div>

                        <div className="prd-card-body">
                            <div className="prd-two-col">
                                {/* Selling Price */}
                                <div className="prd-field-group">
                                    <div className="prd-label-row">
                                        <label htmlFor="price-input" className="prd-label">
                                            Selling Price <span className="prd-req">*</span>
                                        </label>
                                        <span className="prd-spec-badge">
                                            {isEdit ? 'UC12 Managed' : 'VND ≥ 0'}
                                        </span>
                                    </div>
                                    <div className="prd-input-with-suffix">
                                        <input
                                            id="price-input"
                                            type="number"
                                            step="500"
                                            min="0"
                                            className={`prd-input prd-input--price ${isEdit ? 'prd-input--readonly' : ''} ${fieldErrors.sellingPrice ? 'prd-input--error' : ''}`}
                                            placeholder="38500"
                                            value={formData.sellingPrice}
                                            onChange={e => handleChange('sellingPrice', e.target.value)}
                                            disabled={submitting || isEdit}
                                            readOnly={isEdit}
                                            required={!isEdit}
                                        />
                                        <span className="prd-input-suffix">₫ (VND)</span>
                                    </div>
                                    {isEdit ? (
                                        <div className="prd-field-hint prd-field-hint--locked">
                                            <div>🔒 Giá bán cố định tại UC10. Vui lòng sử dụng tính năng <strong>UC12 Manage Prices</strong> để cập nhật bảng giá và ghi nhận vết kiểm toán.</div>
                                            <button
                                                type="button"
                                                className="prd-btn-inline-link"
                                                onClick={() => navigate(`/products/${id}/price`)}
                                                style={{ marginTop: 4, background: 'none', border: 'none', color: '#2458dc', fontWeight: 600, cursor: 'pointer', padding: 0, textDecoration: 'underline' }}
                                            >
                                                Mở màn hình điều chỉnh giá (UC12) →
                                            </button>
                                        </div>
                                    ) : fieldErrors.sellingPrice ? (
                                        <span className="prd-field-error">{fieldErrors.sellingPrice}</span>
                                    ) : (
                                        <span className="prd-field-hint">
                                            Must be a numeric value greater than or equal to 0 (SRS 3.5.3).
                                        </span>
                                    )}
                                </div>

                                {/* Base Unit */}
                                <div className="prd-field-group">
                                    <div className="prd-label-row">
                                        <label htmlFor="unit-input" className="prd-label">
                                            Base Inventory Unit <span className="prd-req">*</span>
                                        </label>
                                        <span className={`prd-char-counter ${formData.unit.length > 30 ? 'prd-char-counter--warn' : ''}`}>
                                            {formData.unit.length} / 30
                                        </span>
                                    </div>
                                    <input
                                        id="unit-input"
                                        type="text"
                                        className={`prd-input ${fieldErrors.unit ? 'prd-input--error' : ''}`}
                                        placeholder="Hộp, Chai, Lon..."
                                        maxLength={30}
                                        value={formData.unit}
                                        onChange={e => handleChange('unit', e.target.value)}
                                        disabled={submitting}
                                        required
                                    />
                                    {/* Presets */}
                                    <div className="prd-presets-row">
                                        <span className="prd-presets-label">Presets:</span>
                                        {UNIT_PRESETS.map(u => (
                                            <button
                                                type="button"
                                                key={u}
                                                className={`prd-preset-chip ${formData.unit === u ? 'prd-preset-chip--active' : ''}`}
                                                onClick={() => handleChange('unit', u)}
                                            >
                                                {u}
                                            </button>
                                        ))}
                                    </div>
                                    {fieldErrors.unit && (
                                        <span className="prd-field-error">{fieldErrors.unit}</span>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* ── Card 3: Inventory Rules & Lifecycle ────────────────── */}
                    <div className="prd-card prd-form-card">
                        <div className="prd-card-header">
                            <div className="prd-card-header-badge">3</div>
                            <div className="prd-card-header-text">
                                <div className="prd-card-header-title-row">
                                    <h2 className="prd-card-title">Inventory Rules &amp; Lifecycle</h2>
                                    <span className="prd-card-tag">UC07 &amp; UC18 TRIGGERS</span>
                                </div>
                                <p className="prd-card-subtitle">
                                    Automated stock warnings and lot expiry tracking triggers
                                </p>
                            </div>
                        </div>

                        <div className="prd-card-body">
                            {/* Low-Stock Threshold */}
                            <div className="prd-field-group">
                                <label htmlFor="min-stock-input" className="prd-label">
                                    Low-Stock Warning Threshold <span className="prd-opt">(Optional, default: 0)</span>
                                </label>
                                <div className="prd-input-with-suffix">
                                    <input
                                        id="min-stock-input"
                                        type="number"
                                        step="1"
                                        min="0"
                                        className={`prd-input ${fieldErrors.minimumStockLevel ? 'prd-input--error' : ''}`}
                                        placeholder="15"
                                        value={formData.minimumStockLevel}
                                        onChange={e => handleChange('minimumStockLevel', e.target.value)}
                                        disabled={submitting}
                                    />
                                    <span className="prd-input-suffix">{formData.unit || 'units'}</span>
                                </div>
                                {fieldErrors.minimumStockLevel ? (
                                    <span className="prd-field-error">{fieldErrors.minimumStockLevel}</span>
                                ) : (
                                    <span className="prd-field-hint">
                                        Minimum inventory quantity before triggering automated dashboard alerts (UC07 default: 15). Set to 0 to disable automated stock reorder alerts.
                                    </span>
                                )}
                            </div>

                            {/* Track Expiry Checkbox */}
                            <div className="prd-checkbox-wrap">
                                <label className="prd-checkbox-label">
                                    <input
                                        type="checkbox"
                                        className="prd-checkbox-input"
                                        checked={formData.trackExpiry}
                                        onChange={e => handleChange('trackExpiry', e.target.checked)}
                                        disabled={submitting}
                                    />
                                    <span className="prd-checkbox-text">
                                        <strong>This product has an expiration date (Hạn sử dụng / HSD)</strong>
                                        <small>
                                            When checked, stock import batches (UC10/UC18) require mandatory expiry date entry and automatically populate the store's near-expiry audit queue.
                                        </small>
                                    </span>
                                </label>

                                <div className={`prd-tracking-badge ${formData.trackExpiry ? 'prd-tracking-badge--active' : ''}`}>
                                    <span className="prd-dot" />
                                    <span>Expiry Audit Tracking: {formData.trackExpiry ? 'Activated' : 'Disabled'}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* ── Bottom Action Footer ───────────────────────────────── */}
                    <div className="prd-form-footer">
                        <div className="prd-footer-note">
                            <ShieldIcon />
                            <span>All updates are logged to Store Audit Vault (SRS 3.5.3).</span>
                        </div>
                        <div className="prd-footer-actions">
                            <button
                                type="button"
                                className="prd-btn prd-btn--secondary"
                                onClick={() => navigate('/products')}
                                disabled={submitting}
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                className="prd-btn prd-btn--primary"
                                disabled={submitting || !canManage}
                            >
                                {submitting ? 'Saving...' : 'Save Product Master'}
                            </button>
                        </div>
                    </div>
                </div>

                {/* ═════════ RIGHT COLUMN: LIVE SIMULATION & SPECS ═════════ */}
                <div className="prd-form-col-right">

                    {/* ── Card: Live POS Cashier Mockup ─────────────────────── */}
                    <div className="prd-card prd-pos-preview-card">
                        <div className="prd-pos-preview-header">
                            <div className="prd-pos-title-wrap">
                                <CashierIcon />
                                <div>
                                    <h3 className="prd-pos-title">Live POS Cashier Mockup</h3>
                                    <p className="prd-pos-subtitle">
                                        Real-time simulation of how this master item renders on POS Cashier register touchscreens and customer-facing receipts.
                                    </p>
                                </div>
                            </div>
                            <span className="prd-live-badge">Syncing</span>
                        </div>

                        {/* Visual POS Touch Card */}
                        <div className="prd-pos-ticket">
                            <div className="prd-pos-ticket-top">
                                <span className="prd-pos-category">
                                    {selectedCategoryName.toUpperCase()}
                                </span>
                                <TouchIcon />
                            </div>

                            <h4 className="prd-pos-product-name">
                                {formData.productName.trim() || 'Tên sản phẩm hiển thị tại quầy thu ngân'}
                            </h4>

                            <div className="prd-pos-metrics">
                                <div className="prd-pos-price-box">
                                    <span className="prd-pos-metric-label">Retail Price</span>
                                    <span className="prd-pos-metric-val">
                                        {formattedPricePreview} <small>/ {formData.unit || 'Đơn vị'}</small>
                                    </span>
                                </div>

                                <div className="prd-pos-alert-box">
                                    <span className="prd-pos-metric-label">Low-stock Alert</span>
                                    <span className="prd-pos-metric-val prd-pos-metric-val--alert">
                                        ≤ {formData.minimumStockLevel || '0'} {formData.unit || 'units'}
                                    </span>
                                </div>
                            </div>

                            {/* Simulated Barcode */}
                            <div className="prd-pos-barcode-wrap">
                                <div className="prd-pos-barcode-bars" />
                                <div className="prd-pos-barcode-codes">
                                    <span>{formData.productCode || 'SKU-XXXXXXX'}</span>
                                    <span>{formData.barcode || '— NO BARCODE —'}</span>
                                </div>
                            </div>

                            {/* Expiration requirement badge */}
                            {formData.trackExpiry && (
                                <div className="prd-pos-expiry-notice">
                                    <AlertTriangleIcon />
                                    <span>Requires expiration scan during stock import</span>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* ── Card: SRS 3.5.3 Specs Summary ─────────────────────── */}
                    <div className="prd-card prd-specs-card">
                        <div className="prd-specs-header">
                            <SpecsIcon />
                            <h3 className="prd-specs-title">SRS 3.5.3 Specs Summary</h3>
                        </div>
                        <ul className="prd-specs-list">
                            <li>
                                <CheckSmallIcon />
                                <span><strong>SKU:</strong> Mandatory, up to 50 chars, system-wide unique.</span>
                            </li>
                            <li>
                                <CheckSmallIcon />
                                <span><strong>Barcode:</strong> Optional, up to 100 chars, unique when entered.</span>
                            </li>
                            <li>
                                <CheckSmallIcon />
                                <span><strong>Name &amp; Category:</strong> Mandatory product classification.</span>
                            </li>
                            <li>
                                <CheckSmallIcon />
                                <span><strong>Price:</strong> Decimal number ≥ 0 VND.</span>
                            </li>
                            <li>
                                <CheckSmallIcon />
                                <span><strong>Threshold:</strong> Controls UC07 low-stock dashboard triggers.</span>
                            </li>
                        </ul>
                    </div>

                    {/* ── Card: Store Manager Tip ───────────────────────────── */}
                    <div className="prd-card prd-tip-card">
                        <div className="prd-tip-header">
                            <LightbulbIcon />
                            <h3 className="prd-tip-title">Store Manager Tip</h3>
                        </div>
                        <p className="prd-tip-content">
                            For dairy and produce, always verify that the Expiration Date checkbox is kept ON so batch rot risk is monitored continuously.
                        </p>
                    </div>

                </div>
            </form>
        </div>
    );
}

// ─── Inline SVG Icons ─────────────────────────────────────────────────────────

function BarcodeIcon({ className }) {
    return (
        <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
            <path d="M3 5v14M7 5v14M11 5v14M17 5v14M21 5v14M13 5v14" />
        </svg>
    );
}

function ShieldIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16" style={{ opacity: 0.7 }}>
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        </svg>
    );
}

function CashierIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20" style={{ color: '#2458dc' }}>
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
        </svg>
    );
}

function TouchIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16" style={{ opacity: 0.4 }}>
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v8M8 12h8" />
        </svg>
    );
}

function AlertTriangleIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" style={{ color: '#d97706', flexShrink: 0 }}>
            <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
    );
}

function SpecsIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#2458dc' }}>
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
    );
}

function CheckSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" width="14" height="14" style={{ color: '#10b981', flexShrink: 0, marginTop: '2px' }}>
            <polyline points="20 6 9 17 4 12" />
        </svg>
    );
}

function LightbulbIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18" style={{ color: '#d97706' }}>
            <line x1="9" y1="18" x2="15" y2="18" />
            <line x1="10" y1="22" x2="14" y2="22" />
            <path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14" />
        </svg>
    );
}
