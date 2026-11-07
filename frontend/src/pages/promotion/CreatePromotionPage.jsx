import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
    Percent, DollarSign, Layers, AlertCircle, CheckCircle2,
    Shield, ArrowLeft, Save, Calendar, Clock, Search,
    Filter, Sparkles, RotateCcw, Check, X, Tag, Package,
    FolderTree, ShoppingBag, Receipt, Terminal, Info, ChevronDown
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import PromotionService from '../../services/promotionService';
import ProductService from '../../services/productService';
import CategoryService from '../../services/categoryService';
import {
    MOCK_CATALOG_PRODUCTS,
    MOCK_CATEGORIES
} from './mockPromotionsData';
import './CreatePromotionPage.css';

export default function CreatePromotionPage() {
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    // Catalog state
    const [products, setProducts] = useState(MOCK_CATALOG_PRODUCTS);
    const [categories, setCategories] = useState(MOCK_CATEGORIES);
    const [catalogSearch, setCatalogSearch] = useState('');
    const [selectedDeptFilter, setSelectedDeptFilter] = useState('ALL');

    // Form state
    const [formData, setFormData] = useState({
        promotionName: 'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng',
        promotionType: 'PERCENTAGE', // 'PERCENTAGE' | 'FIXED_AMOUNT'
        discountValue: '15',
        scopeType: 'PRODUCTS', // 'PRODUCTS' | 'CATEGORIES'
        selectedProductIds: [1, 2, 3], // Default 3 items like mockup
        selectedCategoryId: 1, // Category id if in category mode
        startAt: '2025-06-01T08:00',
        endAt: '2025-06-07T22:00'
    });

    const [formErrors, setFormErrors] = useState({});
    const [saving, setSaving] = useState(false);
    const [toastMessage, setToastMessage] = useState(null);
    const [toastType, setToastType] = useState('success');

    // Simulator active mode
    const [simMode, setSimMode] = useState('percentage');

    const showToast = (msg, type = 'success') => {
        setToastType(type);
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Load live catalog data from API with fallback
    useEffect(() => {
        const loadCatalog = async () => {
            try {
                const prodRes = await ProductService.getAll({ status: 'ACTIVE' });
                const fetchedProds = prodRes.data?.data || prodRes.data;
                if (Array.isArray(fetchedProds) && fetchedProds.length > 0) {
                    const mapped = fetchedProds.map(p => ({
                        productId: p.productId,
                        productCode: p.productCode || `SKU-${p.productId}`,
                        productName: p.productName,
                        shortName: p.productName,
                        barcode: p.barcode || '---',
                        category: p.categoryName || 'General',
                        categoryCode: p.categoryCode || 'GEN',
                        sellingPrice: p.sellingPrice || 0,
                        status: p.status || 'ACTIVE'
                    }));
                    setProducts(mapped);
                }
            } catch {
                // Keep mock products
            }

            try {
                const catRes = await CategoryService.getAll();
                const fetchedCats = catRes.data?.data || catRes.data;
                if (Array.isArray(fetchedCats) && fetchedCats.length > 0) {
                    const mappedCats = fetchedCats.map(c => ({
                        categoryId: c.categoryId,
                        categoryName: c.categoryName,
                        code: c.categoryCode || String(c.categoryId),
                        viName: c.categoryName,
                        itemCount: c.productCount || 0
                    }));
                    setCategories(mappedCats);
                }
            } catch {
                // Keep mock categories
            }
        };

        loadCatalog();
    }, []);

    // Filter products in catalog table
    const filteredCatalog = useMemo(() => {
        return products.filter(p => {
            const matchesSearch = !catalogSearch.trim() ||
                p.productName.toLowerCase().includes(catalogSearch.toLowerCase()) ||
                p.productCode.toLowerCase().includes(catalogSearch.toLowerCase()) ||
                (p.barcode && p.barcode.includes(catalogSearch));
            const matchesDept = selectedDeptFilter === 'ALL' ||
                p.category === selectedDeptFilter ||
                p.categoryCode === selectedDeptFilter;
            return matchesSearch && matchesDept;
        });
    }, [products, catalogSearch, selectedDeptFilter]);

    // Selected products full objects
    const selectedProductObjects = useMemo(() => {
        return products.filter(p => formData.selectedProductIds.includes(p.productId));
    }, [products, formData.selectedProductIds]);

    // Format currency VND
    const formatVND = (val) => {
        const num = Number(val) || 0;
        return num.toLocaleString('vi-VN') + ' đ';
    };

    // Calculate duration string
    const activeDurationText = useMemo(() => {
        if (!formData.startAt || !formData.endAt) return 'Pending dates';
        const s = new Date(formData.startAt);
        const e = new Date(formData.endAt);
        if (isNaN(s.getTime()) || isNaN(e.getTime()) || e <= s) {
            return 'Invalid duration (End date must be after Start date)';
        }
        const diffMs = e.getTime() - s.getTime();
        const totalMinutes = Math.floor(diffMs / (1000 * 60));
        const totalHours = Math.floor(totalMinutes / 60);
        const days = Math.floor(totalHours / 24);
        const hours = totalHours % 24;
        return `${days} days ${hours} hours`;
    }, [formData.startAt, formData.endAt]);

    // Live POS preview calculation (based on Vinamilk or first selected product)
    const previewCalculations = useMemo(() => {
        const target = selectedProductObjects[0] || products[0] || {
            productName: 'Sữa tươi Vinamilk 1L',
            productCode: 'SKU-8930124',
            sellingPrice: 38500
        };

        const originalPrice = target.sellingPrice || 38500;
        const discVal = parseFloat(formData.discountValue) || 0;
        let discountAmount = 0;
        let promoLabel = '';

        if (formData.promotionType === 'PERCENTAGE') {
            const pct = Math.min(Math.max(discVal, 0), 100);
            discountAmount = Math.round(originalPrice * (pct / 100));
            promoLabel = `Promo: -${pct}%`;
        } else {
            discountAmount = Math.min(discVal, originalPrice);
            promoLabel = `Promo: -${formatVND(discVal)}`;
        }

        const netTotal = Math.max(0, originalPrice - discountAmount);

        return {
            target,
            originalPrice,
            discountAmount,
            promoLabel,
            netTotal
        };
    }, [selectedProductObjects, products, formData.promotionType, formData.discountValue]);

    // Pre-save checklist validations
    const checklist = useMemo(() => {
        const nameValid = formData.promotionName.trim().length > 0 && formData.promotionName.trim().length <= 150;
        const discNum = parseFloat(formData.discountValue);
        const discValid = !isNaN(discNum) && discNum > 0 && (formData.promotionType !== 'PERCENTAGE' || discNum <= 100);
        const scopeValid = formData.scopeType === 'PRODUCTS'
            ? formData.selectedProductIds.length > 0
            : Boolean(formData.selectedCategoryId);
        const scheduleValid = formData.startAt && formData.endAt && new Date(formData.endAt) > new Date(formData.startAt);

        return [
            {
                key: 'name',
                label: 'Promotion Name ≤ 150 chars',
                passed: nameValid,
                badge: nameValid ? 'Passed' : 'Invalid'
            },
            {
                key: 'discount',
                label: 'Discount > 0 Numeric Value',
                passed: discValid,
                badge: discValid ? 'Passed' : 'Invalid'
            },
            {
                key: 'scope',
                label: 'Scope Target Defined (≥ 1 Item)',
                passed: scopeValid,
                badge: scopeValid
                    ? (formData.scopeType === 'PRODUCTS' ? `${formData.selectedProductIds.length} Items` : '1 Dept')
                    : 'Pending'
            },
            {
                key: 'schedule',
                label: 'Schedule: End Date > Start Date',
                passed: scheduleValid,
                badge: scheduleValid ? 'Passed' : 'Invalid'
            }
        ];
    }, [formData]);

    // Handlers
    const handleToggleProduct = (productId) => {
        setFormData(prev => {
            const exists = prev.selectedProductIds.includes(productId);
            const next = exists
                ? prev.selectedProductIds.filter(id => id !== productId)
                : [...prev.selectedProductIds, productId];
            return { ...prev, selectedProductIds: next };
        });
        if (formErrors.scope) {
            setFormErrors(prev => ({ ...prev, scope: null }));
        }
    };

    const handleRemoveProduct = (productId) => {
        setFormData(prev => ({
            ...prev,
            selectedProductIds: prev.selectedProductIds.filter(id => id !== productId)
        }));
    };

    const handleSelectAllToggle = () => {
        if (formData.selectedProductIds.length === filteredCatalog.length) {
            setFormData(prev => ({ ...prev, selectedProductIds: [] }));
        } else {
            const allIds = filteredCatalog.map(p => p.productId);
            setFormData(prev => ({ ...prev, selectedProductIds: allIds }));
        }
    };

    const validateForm = () => {
        const errors = {};
        if (!formData.promotionName.trim()) {
            errors.promotionName = 'Tên chương trình khuyến mãi không được để trống';
        } else if (formData.promotionName.trim().length > 150) {
            errors.promotionName = 'Tên khuyến mãi tối đa 150 ký tự';
        }

        const disc = parseFloat(formData.discountValue);
        if (isNaN(disc) || disc <= 0) {
            errors.discountValue = 'Giá trị giảm giá phải lớn hơn 0';
        } else if (formData.promotionType === 'PERCENTAGE' && disc > 100) {
            errors.discountValue = 'Phần trăm giảm giá tối đa 100%';
        }

        if (formData.scopeType === 'PRODUCTS' && formData.selectedProductIds.length === 0) {
            errors.scope = 'Vui lòng chọn ít nhất 1 sản phẩm áp dụng';
        }
        if (formData.scopeType === 'CATEGORIES' && !formData.selectedCategoryId) {
            errors.scope = 'Vui lòng chọn 1 ngành hàng áp dụng';
        }

        if (!formData.startAt) {
            errors.startAt = 'Thời gian bắt đầu là bắt buộc';
        }
        if (!formData.endAt) {
            errors.endAt = 'Thời gian kết thúc là bắt buộc';
        }
        if (formData.startAt && formData.endAt && new Date(formData.endAt) <= new Date(formData.startAt)) {
            errors.endAt = 'Thời gian kết thúc phải sau thời gian bắt đầu';
        }

        return errors;
    };

    const handleSubmit = async () => {
        const errors = validateForm();
        if (Object.keys(errors).length > 0) {
            setFormErrors(errors);
            showToast('Vui lòng kiểm tra lại các trường bị lỗi!', 'error');
            return;
        }

        setSaving(true);
        const payload = {
            promotionName: formData.promotionName.trim(),
            promotionType: formData.promotionType,
            discountValue: parseFloat(formData.discountValue),
            categoryId: formData.scopeType === 'CATEGORIES' ? Number(formData.selectedCategoryId) : null,
            productId: formData.scopeType === 'PRODUCTS' && formData.selectedProductIds.length > 0
                ? formData.selectedProductIds[0]
                : null,
            productIds: formData.scopeType === 'PRODUCTS' ? formData.selectedProductIds : null,
            startAt: formData.startAt.length === 16 ? formData.startAt + ':00' : formData.startAt,
            endAt: formData.endAt.length === 16 ? formData.endAt + ':00' : formData.endAt
        };

        try {
            await PromotionService.create(payload);
            showToast('Chương trình khuyến mãi đã được tạo thành công! (HTTP 201 Created)', 'success');
            setTimeout(() => navigate('/promotions'), 1000);
        } catch (err) {
            const msg = err.response?.data?.message || 'Có lỗi xảy ra khi lưu chương trình khuyến mãi.';
            setFormErrors(prev => ({ ...prev, submit: msg }));
            showToast(msg, 'error');
        } finally {
            setSaving(false);
        }
    };

    // Simulator handlers
    const handleSimDefaultPercentage = () => {
        setSimMode('percentage');
        setFormData({
            promotionName: 'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng',
            promotionType: 'PERCENTAGE',
            discountValue: '15',
            scopeType: 'PRODUCTS',
            selectedProductIds: [1, 2, 3],
            selectedCategoryId: 1,
            startAt: '2025-06-01T08:00',
            endAt: '2025-06-07T22:00'
        });
        setFormErrors({});
        showToast('Đã áp dụng mẫu mặc định: Giảm theo Phần trăm (15%)', 'info');
    };

    const handleSimFixedAmount = () => {
        setSimMode('fixed');
        setFormData(prev => ({
            ...prev,
            promotionName: 'Giảm giá Đặc biệt Mì Hảo Hảo & Đồ khô',
            promotionType: 'FIXED_AMOUNT',
            discountValue: '5000',
            scopeType: 'PRODUCTS',
            selectedProductIds: [2]
        }));
        setFormErrors({});
        showToast('Đã chuyển sang chế độ Giảm Tiền Cố Định (5.000 VND)', 'info');
    };

    const handleSimCategoryScope = () => {
        setSimMode('category');
        setFormData(prev => ({
            ...prev,
            promotionName: 'Ưu đãi Lễ hội Sữa & Trứng Toàn Hệ thống',
            promotionType: 'PERCENTAGE',
            discountValue: '10',
            scopeType: 'CATEGORIES',
            selectedCategoryId: 1
        }));
        setFormErrors({});
        showToast('Đã chuyển sang chế độ Áp dụng theo Danh mục (Sữa & Trứng)', 'info');
    };

    const handleSimErrors = () => {
        setSimMode('errors');
        setFormData(prev => ({
            ...prev,
            promotionName: '',
            discountValue: '0',
            selectedProductIds: [],
            startAt: '2025-06-10T12:00',
            endAt: '2025-06-01T08:00' // Earlier than start
        }));
        setFormErrors({
            promotionName: 'Tên chương trình khuyến mãi không được để trống',
            discountValue: 'Giá trị giảm giá phải lớn hơn 0',
            scope: 'Vui lòng chọn ít nhất 1 sản phẩm áp dụng',
            endAt: 'Thời gian kết thúc phải sau thời gian bắt đầu'
        });
        showToast('Đang mô phỏng các lỗi xác thực form!', 'error');
    };

    return (
        <div className="cpp-root">
            {/* Top Toast Banner */}
            {toastMessage && (
                <div className={`cpp-toast cpp-toast--${toastType}`}>
                    {toastType === 'success' && <CheckCircle2 size={16} />}
                    {toastType === 'error' && <AlertCircle size={16} />}
                    {toastType === 'info' && <Sparkles size={16} />}
                    <span>{toastMessage}</span>
                </div>
            )}

            {/* UI STATE SIMULATOR TOOLBAR */}
            <div className="cpp-simulator-bar">
                <div className="cpp-sim-title">
                    <Sparkles size={13} />
                    <span>UI STATE SIMULATOR:</span>
                    <span className="cpp-sim-sub">SRS 3.5.6 Test Scenarios</span>
                </div>
                <div className="cpp-sim-buttons">
                    <button
                        type="button"
                        className={`cpp-sim-btn ${simMode === 'percentage' ? 'active' : ''}`}
                        onClick={handleSimDefaultPercentage}
                    >
                        % Default Form (Percentage)
                    </button>
                    <button
                        type="button"
                        className={`cpp-sim-btn ${simMode === 'fixed' ? 'active' : ''}`}
                        onClick={handleSimFixedAmount}
                    >
                        💵 Fixed Amount (VND)
                    </button>
                    <button
                        type="button"
                        className={`cpp-sim-btn ${simMode === 'category' ? 'active' : ''}`}
                        onClick={handleSimCategoryScope}
                    >
                        📁 Category Scope Mode
                    </button>
                    <button
                        type="button"
                        className={`cpp-sim-btn cpp-sim-btn--error ${simMode === 'errors' ? 'active' : ''}`}
                        onClick={handleSimErrors}
                    >
                        🔴 Validation Errors
                    </button>
                    <button
                        type="button"
                        className="cpp-sim-btn cpp-sim-btn--success"
                        onClick={() => showToast('Khuyến mãi đã được tạo thành công! (Mô phỏng HTTP 201)', 'success')}
                    >
                        ✨ Success Toast
                    </button>
                </div>
            </div>

            {/* Breadcrumb */}
            <div className="cpp-breadcrumb">
                <Link to="/">Home</Link>
                <span>/</span>
                <span>Operations Hub</span>
                <span>/</span>
                <Link to="/promotions">Promotions</Link>
                <span>/</span>
                <span className="cpp-breadcrumb-current">Create Promotion (UC14)</span>
            </div>

            {/* Page Header */}
            <div className="cpp-page-header">
                <div className="cpp-header-left">
                    <div className="cpp-title-row">
                        <h1 className="cpp-title">Create New Promotion</h1>
                        <span className="cpp-role-badge">
                            <Shield size={13} /> Role Access: Admin &amp; Manager Only (UC14)
                        </span>
                    </div>
                    <p className="cpp-subtitle">
                        Configure campaign discount rules, discount value, target product/category scope, and promotional validity windows.
                    </p>
                </div>
                <div className="cpp-header-right">
                    <button
                        type="button"
                        className="cpp-btn-back"
                        onClick={() => navigate('/promotions')}
                    >
                        <ArrowLeft size={15} /> Back to List
                    </button>
                    <button
                        type="button"
                        className="cpp-btn-save"
                        onClick={handleSubmit}
                        disabled={saving}
                    >
                        <Save size={15} /> {saving ? 'Saving...' : 'Save Promotion'}
                    </button>
                </div>
            </div>

            {/* Main Form + Sidebar Grid */}
            <div className="cpp-layout">
                {/* LEFT: 3 Form Sections */}
                <div className="cpp-main-form">

                    {/* Step 1: Campaign Identity & Discount Logic */}
                    <div className="cpp-card-section">
                        <div className="cpp-section-header">
                            <div className="cpp-header-info">
                                <div className="cpp-step-number">1</div>
                                <div>
                                    <h2 className="cpp-section-title">1. Campaign Identity &amp; Discount Logic</h2>
                                    <p className="cpp-section-desc">Define promotional naming and mathematical calculation behavior</p>
                                </div>
                            </div>
                            <span className="cpp-step-badge">Step 1 of 3</span>
                        </div>

                        <div className="cpp-section-body">
                            {/* Promotion Name */}
                            <div className="cpp-form-group">
                                <div className="cpp-label-row">
                                    <label className="cpp-label">Promotion Name *</label>
                                    <span className="cpp-counter">
                                        {formData.promotionName.length} / 150
                                    </span>
                                </div>
                                <input
                                    type="text"
                                    className={`cpp-input ${formErrors.promotionName ? 'is-error' : ''}`}
                                    placeholder="e.g. Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng"
                                    maxLength={150}
                                    value={formData.promotionName}
                                    onChange={(e) => {
                                        setFormData(prev => ({ ...prev, promotionName: e.target.value }));
                                        if (formErrors.promotionName) {
                                            setFormErrors(prev => ({ ...prev, promotionName: null }));
                                        }
                                    }}
                                />
                                {formErrors.promotionName && (
                                    <p className="cpp-field-error"><AlertCircle size={12} /> {formErrors.promotionName}</p>
                                )}
                                <p className="cpp-field-hint">Public promotion title displayed on POS receipts and price labels (Max 150 characters).</p>
                            </div>

                            {/* Two-column: Discount Type & Discount Value */}
                            <div className="cpp-two-col">
                                {/* Discount Type */}
                                <div className="cpp-form-group">
                                    <label className="cpp-label">Discount Type *</label>
                                    <div className="cpp-type-selector">
                                        <button
                                            type="button"
                                            className={`cpp-type-btn ${formData.promotionType === 'PERCENTAGE' ? 'active' : ''}`}
                                            onClick={() => setFormData(prev => ({ ...prev, promotionType: 'PERCENTAGE' }))}
                                        >
                                            <span className="type-icon">%</span>
                                            <div className="type-text">
                                                <strong>Percentage</strong>
                                                <span>(%)</span>
                                            </div>
                                        </button>
                                        <button
                                            type="button"
                                            className={`cpp-type-btn ${formData.promotionType === 'FIXED_AMOUNT' ? 'active' : ''}`}
                                            onClick={() => setFormData(prev => ({ ...prev, promotionType: 'FIXED_AMOUNT' }))}
                                        >
                                            <span className="type-icon">💵</span>
                                            <div className="type-text">
                                                <strong>Fixed Amount</strong>
                                                <span>(VND)</span>
                                            </div>
                                        </button>
                                    </div>
                                    <p className="cpp-field-hint">Determines deduction arithmetic on scanned checkout line items.</p>
                                </div>

                                {/* Discount Value */}
                                <div className="cpp-form-group">
                                    <label className="cpp-label">
                                        {formData.promotionType === 'PERCENTAGE' ? 'Discount Value (Percentage) *' : 'Discount Value (VND) *'}
                                    </label>
                                    <div className={`cpp-input-wrapper ${formErrors.discountValue ? 'is-error' : ''}`}>
                                        <input
                                            type="number"
                                            min="0"
                                            max={formData.promotionType === 'PERCENTAGE' ? 100 : undefined}
                                            className="cpp-input-naked"
                                            value={formData.discountValue}
                                            onChange={(e) => {
                                                setFormData(prev => ({ ...prev, discountValue: e.target.value }));
                                                if (formErrors.discountValue) {
                                                    setFormErrors(prev => ({ ...prev, discountValue: null }));
                                                }
                                            }}
                                        />
                                        <span className="cpp-input-suffix">
                                            {formData.promotionType === 'PERCENTAGE' ? '%' : '₫'}
                                        </span>
                                    </div>
                                    {formErrors.discountValue && (
                                        <p className="cpp-field-error"><AlertCircle size={12} /> {formErrors.discountValue}</p>
                                    )}
                                    <p className="cpp-field-hint">
                                        {formData.promotionType === 'PERCENTAGE'
                                            ? 'Enter percentage discount (1 - 100%).'
                                            : 'Enter fixed deduction amount in VND.'}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Step 2: Application Scope (Target Rules) */}
                    <div className="cpp-card-section">
                        <div className="cpp-section-header">
                            <div className="cpp-header-info">
                                <div className="cpp-step-number">2</div>
                                <div>
                                    <h2 className="cpp-section-title">2. Application Scope (Target Rules)</h2>
                                    <p className="cpp-section-desc">Define whether promotion applies to specific inventory items or full merchandise categories</p>
                                </div>
                            </div>
                            <span className="cpp-step-badge">Step 2 of 3</span>
                        </div>

                        <div className="cpp-section-body">
                            {/* Scope Selector Cards */}
                            <div className="cpp-form-group">
                                <label className="cpp-label">Applies To *</label>
                                <div className="cpp-scope-grid">
                                    <label className={`cpp-scope-card ${formData.scopeType === 'PRODUCTS' ? 'active' : ''}`}>
                                        <input
                                            type="radio"
                                            name="scopeType"
                                            checked={formData.scopeType === 'PRODUCTS'}
                                            onChange={() => setFormData(prev => ({ ...prev, scopeType: 'PRODUCTS' }))}
                                        />
                                        <div className="scope-card-content">
                                            <strong>Specific Products</strong>
                                            <span>Select individual SKUs for item-level discounting</span>
                                        </div>
                                    </label>
                                    <label className={`cpp-scope-card ${formData.scopeType === 'CATEGORIES' ? 'active' : ''}`}>
                                        <input
                                            type="radio"
                                            name="scopeType"
                                            checked={formData.scopeType === 'CATEGORIES'}
                                            onChange={() => setFormData(prev => ({ ...prev, scopeType: 'CATEGORIES' }))}
                                        />
                                        <div className="scope-card-content">
                                            <strong>Specific Categories</strong>
                                            <span>Apply to all SKUs in chosen department or hierarchy</span>
                                        </div>
                                    </label>
                                </div>
                                {formErrors.scope && (
                                    <p className="cpp-field-error"><AlertCircle size={12} /> {formErrors.scope}</p>
                                )}
                            </div>

                            {/* When PRODUCTS scope is active */}
                            {formData.scopeType === 'PRODUCTS' && (
                                <div className="cpp-products-selector-block">
                                    <div className="cpp-block-header">
                                        <h3>Search &amp; Add Applicable Products</h3>
                                        <span className="cpp-badge-selected">
                                            {formData.selectedProductIds.length} products selected
                                        </span>
                                    </div>

                                    {/* Search & Dept Filter */}
                                    <div className="cpp-search-filter-row">
                                        <div className="cpp-search-box">
                                            <Search size={15} className="search-icon" />
                                            <input
                                                type="text"
                                                placeholder="Search by SKU, product name, or barcode..."
                                                value={catalogSearch}
                                                onChange={(e) => setCatalogSearch(e.target.value)}
                                            />
                                        </div>
                                        <div className="cpp-filter-box">
                                            <select
                                                value={selectedDeptFilter}
                                                onChange={(e) => setSelectedDeptFilter(e.target.value)}
                                            >
                                                <option value="ALL">Filter by Dept</option>
                                                {categories.map(c => (
                                                    <option key={c.categoryId} value={c.categoryName}>
                                                        {c.categoryName}
                                                    </option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>

                                    {/* Currently Selected Items Pills */}
                                    <div className="cpp-selected-pills-box">
                                        <div className="selected-box-label">CURRENTLY SELECTED ITEMS:</div>
                                        <div className="selected-pills-wrap">
                                            {selectedProductObjects.length === 0 ? (
                                                <span className="no-pills">Chưa chọn sản phẩm nào. Chọn từ danh sách bên dưới.</span>
                                            ) : (
                                                selectedProductObjects.map(p => (
                                                    <div key={p.productId} className="cpp-item-pill">
                                                        <span className="pill-dot"></span>
                                                        <span className="pill-name">
                                                            {p.productName} ({p.productCode}) - {formatVND(p.sellingPrice)}
                                                        </span>
                                                        <button
                                                            type="button"
                                                            className="pill-remove"
                                                            onClick={() => handleRemoveProduct(p.productId)}
                                                        >
                                                            <X size={12} />
                                                        </button>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>

                                    {/* Quick Selection Table */}
                                    <div className="cpp-table-label">Quick Selection from Catalog:</div>
                                    <div className="cpp-catalog-table-wrap">
                                        <table className="cpp-catalog-table">
                                            <thead>
                                                <tr>
                                                    <th className="th-check">
                                                        <input
                                                            type="checkbox"
                                                            checked={
                                                                filteredCatalog.length > 0 &&
                                                                filteredCatalog.every(p => formData.selectedProductIds.includes(p.productId))
                                                            }
                                                            onChange={handleSelectAllToggle}
                                                        />
                                                    </th>
                                                    <th>SKU &amp; PRODUCT NAME</th>
                                                    <th>CATEGORY</th>
                                                    <th>STANDARD PRICE</th>
                                                    <th>STATUS</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {filteredCatalog.map(p => {
                                                    const isChecked = formData.selectedProductIds.includes(p.productId);
                                                    return (
                                                        <tr
                                                            key={p.productId}
                                                            className={isChecked ? 'row-selected' : ''}
                                                            onClick={() => handleToggleProduct(p.productId)}
                                                        >
                                                            <td className="td-check" onClick={(e) => e.stopPropagation()}>
                                                                <input
                                                                    type="checkbox"
                                                                    checked={isChecked}
                                                                    onChange={() => handleToggleProduct(p.productId)}
                                                                />
                                                            </td>
                                                            <td className="td-product">
                                                                <div className="prod-name">{p.productName}</div>
                                                                <div className="prod-meta font-mono">
                                                                    {p.productCode} • {p.barcode}
                                                                </div>
                                                            </td>
                                                            <td className="td-cat">
                                                                <span className="cat-tag">{p.category}</span>
                                                            </td>
                                                            <td className="td-price font-mono">
                                                                {formatVND(p.sellingPrice)}
                                                            </td>
                                                            <td className="td-status">
                                                                <span className="status-pill-green">Active</span>
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            )}

                            {/* When CATEGORIES scope is active */}
                            {formData.scopeType === 'CATEGORIES' && (
                                <div className="cpp-categories-selector-block">
                                    <div className="cpp-block-header">
                                        <h3>Select Target Merchandise Department</h3>
                                        <span className="cpp-badge-selected">1 Department</span>
                                    </div>
                                    <div className="cpp-category-cards-grid">
                                        {categories.map(c => {
                                            const isSelected = String(formData.selectedCategoryId) === String(c.categoryId);
                                            return (
                                                <div
                                                    key={c.categoryId}
                                                    className={`cpp-cat-card ${isSelected ? 'active' : ''}`}
                                                    onClick={() => setFormData(prev => ({ ...prev, selectedCategoryId: c.categoryId }))}
                                                >
                                                    <div className="cat-card-top">
                                                        <FolderTree size={18} className="cat-card-icon" />
                                                        <input
                                                            type="radio"
                                                            name="targetCategory"
                                                            checked={isSelected}
                                                            onChange={() => setFormData(prev => ({ ...prev, selectedCategoryId: c.categoryId }))}
                                                        />
                                                    </div>
                                                    <div className="cat-card-title">{c.categoryName}</div>
                                                    <div className="cat-card-sub">
                                                        {c.viName} • Code: {c.code}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                    <p className="cpp-field-hint" style={{ marginTop: '12px' }}>
                                        All current and newly added products within the chosen department will automatically inherit this discount at POS checkout registers.
                                    </p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Step 3: Promotion Validity Schedule */}
                    <div className="cpp-card-section">
                        <div className="cpp-section-header">
                            <div className="cpp-header-info">
                                <div className="cpp-step-number">3</div>
                                <div>
                                    <h2 className="cpp-section-title">3. Promotion Validity Schedule</h2>
                                    <p className="cpp-section-desc">Set campaign activation window and cashier POS synchronization timeline</p>
                                </div>
                            </div>
                            <span className="cpp-step-badge">Step 3 of 3</span>
                        </div>

                        <div className="cpp-section-body">
                            <div className="cpp-two-col">
                                {/* Start Date & Time */}
                                <div className="cpp-form-group">
                                    <label className="cpp-label">Start Date &amp; Time *</label>
                                    <div className={`cpp-input-wrapper ${formErrors.startAt ? 'is-error' : ''}`}>
                                        <Calendar size={15} className="cpp-prefix-icon" />
                                        <input
                                            type="datetime-local"
                                            className="cpp-input-naked"
                                            value={formData.startAt}
                                            onChange={(e) => {
                                                setFormData(prev => ({ ...prev, startAt: e.target.value }));
                                                if (formErrors.startAt) setFormErrors(prev => ({ ...prev, startAt: null }));
                                            }}
                                        />
                                    </div>
                                    {formErrors.startAt && (
                                        <p className="cpp-field-error"><AlertCircle size={12} /> {formErrors.startAt}</p>
                                    )}
                                    <p className="cpp-field-hint">Campaign activates automatically at local store opening.</p>
                                </div>

                                {/* End Date & Time */}
                                <div className="cpp-form-group">
                                    <label className="cpp-label">End Date &amp; Time *</label>
                                    <div className={`cpp-input-wrapper ${formErrors.endAt ? 'is-error' : ''}`}>
                                        <Calendar size={15} className="cpp-prefix-icon" />
                                        <input
                                            type="datetime-local"
                                            className="cpp-input-naked"
                                            value={formData.endAt}
                                            onChange={(e) => {
                                                setFormData(prev => ({ ...prev, endAt: e.target.value }));
                                                if (formErrors.endAt) setFormErrors(prev => ({ ...prev, endAt: null }));
                                            }}
                                        />
                                    </div>
                                    {formErrors.endAt && (
                                        <p className="cpp-field-error"><AlertCircle size={12} /> {formErrors.endAt}</p>
                                    )}
                                    <p className="cpp-field-hint">Campaign terminates automatically at register closing.</p>
                                </div>
                            </div>

                            {/* Validation Callout Box */}
                            <div className="cpp-schedule-callout">
                                <div className="callout-header">
                                    <strong>Validation Rule: End Date must be strictly after Start Date.</strong>
                                </div>
                                <div className="callout-content">
                                    <div className="callout-text">
                                        <Info size={15} className="callout-icon" />
                                        <span>
                                            Promotions take effect automatically on POS cashier registers at Start Date 00:00:00 and expire at End Date 23:59:59.
                                        </span>
                                    </div>
                                    <span className="callout-duration-pill">
                                        Active Duration: {activeDurationText}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Bottom Action Strip */}
                    <div className="cpp-bottom-bar">
                        <div className="cpp-audit-left">
                            <Shield size={14} />
                            <span>All changes will be audited under Operator ID: #{currentUser?.username ? currentUser.username.toUpperCase() : 'ADM-042'}</span>
                        </div>
                        <div className="cpp-bottom-actions">
                            <button
                                type="button"
                                className="cpp-btn-cancel-flat"
                                onClick={() => navigate('/promotions')}
                            >
                                Cancel &amp; Return
                            </button>
                            <button
                                type="button"
                                className="cpp-btn-save"
                                onClick={handleSubmit}
                                disabled={saving}
                            >
                                <Save size={15} /> {saving ? 'Saving...' : 'Save Promotion'}
                            </button>
                        </div>
                    </div>
                </div>

                {/* RIGHT: Sidebar Panels */}
                <div className="cpp-sidebar">

                    {/* Panel 1: POS Cashier Preview */}
                    <div className="cpp-sidebar-card pos-preview-card">
                        <div className="preview-top-bar">
                            <div className="pos-brand">
                                <Receipt size={16} />
                                <strong>POS Cashier Preview</strong>
                            </div>
                            <span className="register-badge">Register #01</span>
                        </div>

                        <div className="preview-body">
                            <div className="simulation-header">
                                <span>LINE ITEM SIMULATION</span>
                                <span className="cart-badge">Active Cart</span>
                            </div>

                            <div className="simulation-item-row">
                                <div className="item-details">
                                    <div className="item-name">{previewCalculations.target.productName}</div>
                                    <div className="item-sku font-mono">
                                        SKU: {previewCalculations.target.productCode} (Qty: 1)
                                    </div>
                                </div>
                                <div className="item-price-col">
                                    <span className="price-old font-mono">
                                        {formatVND(previewCalculations.originalPrice)}
                                    </span>
                                </div>
                            </div>

                            <div className="simulation-promo-row">
                                <span className="promo-tag-text">
                                    <Tag size={12} /> {previewCalculations.promoLabel}
                                </span>
                                <span className="promo-deduction font-mono">
                                    -{formatVND(previewCalculations.discountAmount)}
                                </span>
                            </div>

                            <div className="simulation-divider"></div>

                            <div className="simulation-total-row">
                                <div className="total-label-block">
                                    <span className="total-title">NET CASHIER TOTAL</span>
                                    <span className="promo-applied-badge">PROMO APPLIED</span>
                                </div>
                                <div className="total-amount font-mono">
                                    {formatVND(previewCalculations.netTotal)}
                                </div>
                            </div>

                            <div className="simulation-vat font-mono">
                                Tax Incl. (VAT 8%)
                            </div>
                        </div>

                        <div className="preview-footer-note">
                            Live preview reflects cashier terminal scan output according to UC03 scanning logic and tax policy.
                        </div>
                    </div>

                    {/* Panel 2: Pre-Save Validation Checklist */}
                    <div className="cpp-sidebar-card checklist-card">
                        <div className="checklist-header">
                            <span className="checklist-icon">⚖</span>
                            <strong>Pre-Save Validation Checklist</strong>
                        </div>
                        <div className="checklist-items-list">
                            {checklist.map(item => (
                                <div key={item.key} className={`checklist-item ${item.passed ? 'is-passed' : 'is-pending'}`}>
                                    <div className="item-left">
                                        <div className="check-bullet">
                                            {item.passed ? <Check size={12} strokeWidth={3} /> : <span className="bullet-empty"></span>}
                                        </div>
                                        <span className="item-label">{item.label}</span>
                                    </div>
                                    <span className={`status-pill ${item.passed ? 'passed' : 'pending'}`}>
                                        {item.badge}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Panel 3: Role Enforcement Note */}
                    <div className="cpp-sidebar-card role-note-card">
                        <div className="role-note-header">
                            <Shield size={14} className="role-note-icon" />
                            <strong>Role Enforcement Note</strong>
                        </div>
                        <p className="role-note-body">
                            Cashier &amp; Inventory Staff cannot create or amend promotional campaigns. All promotion lifecycle actions (create, schedule, terminate) are permanently logged to the <strong>UC08 Audit Trail</strong> with IP tracking and timestamp.
                        </p>
                        <div className="role-note-footer">
                            <span>Security Policy: ISO-27001</span>
                            <span>Node: Store #04 Master Sync</span>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}

