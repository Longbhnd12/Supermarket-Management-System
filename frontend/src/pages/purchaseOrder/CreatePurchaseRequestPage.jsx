import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    ArrowLeft,
    Send,
    Plus,
    Trash2,
    Search,
    AlertCircle,
    CheckCircle2,
    Check,
    Package,
    AlertTriangle,
    Layers,
    Calendar,
    User,
    Building2,
    FileText,
    Hash
} from 'lucide-react';
import PurchaseOrderService from '../../services/purchaseOrderService';
import SupplierService from '../../services/supplierService';
import ProductService from '../../services/productService';
import CategoryService from '../../services/categoryService';
import './CreatePurchaseRequestPage.css';

// ─── Format Helpers ────────────────────────────────────────────────────────────

// Consistent VND currency formatting matching project standard
function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
    }).format(amount);
}

function formatNumber(n) {
    if (n == null || isNaN(n)) return '0';
    return new Intl.NumberFormat('en-US', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 3,
    }).format(n);
}

// Tomorrow's date formatted as YYYY-MM-DD
function getTomorrowStr() {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    return tomorrow.toISOString().split('T')[0];
}

const TODAY_STR = new Date().toISOString().split('T')[0];

export default function CreatePurchaseRequestPage() {
    const navigate = useNavigate();

    // ── Form State ──
    const [requestId, setRequestId]                       = useState('');
    const [supplierId, setSupplierId]                     = useState('');
    const [expectedDeliveryDate, setExpectedDeliveryDate] = useState('');
    const [note, setNote]                                 = useState('');
    const [items, setItems]                               = useState([]);

    // ── Requester State (Boundary-preserving authentication resolution) ──
    const [requesterDisplay, setRequesterDisplay]         = useState('');
    const [authPending, setAuthPending]                   = useState(false);

    // ── Reference Data ──
    const [suppliers, setSuppliers]                       = useState([]);
    const [allProducts, setAllProducts]                   = useState([]);
    const [categories, setCategories]                     = useState([]);
    const [lowStockSuggestions, setLowStockSuggestions]   = useState([]);

    // ── Product Search & Filter State ──
    const [searchKeyword, setSearchKeyword]               = useState('');
    const [selectedCategory, setSelectedCategory]         = useState('');
    const [isSearchOpen, setIsSearchOpen]                 = useState(false);
    const searchContainerRef                              = useRef(null);

    // ── Page Status State ──
    const [loadingInit, setLoadingInit]                   = useState(true);
    const [submitting, setSubmitting]                     = useState(false);
    const [pageError, setPageError]                       = useState('');
    const [fieldErrors, setFieldErrors]                   = useState({});

    // ── Discard Confirmation Modal State ──
    const [showCancelModal, setShowCancelModal]           = useState(false);

    // Track whether user modified anything
    const isDirty = useMemo(() => {
        return Boolean(
            supplierId ||
            expectedDeliveryDate ||
            note.trim() ||
            items.length > 0
        );
    }, [supplierId, expectedDeliveryDate, note, items]);

    // ── Resolve Authenticated User & Load Initial Data ──
    useEffect(() => {
        let isMounted = true;

        // 1. Resolve authenticated requester from storage if available
        try {
            const rawUser = localStorage.getItem('currentUser') ||
                            localStorage.getItem('user') ||
                            sessionStorage.getItem('user');
            if (rawUser) {
                const u = JSON.parse(rawUser);
                if (u && (u.username || u.fullName)) {
                    const name = u.fullName || u.username;
                    const role = u.roleName || u.role || 'Staff';
                    setRequesterDisplay(`${name} (${role}) • ${TODAY_STR}`);
                    setAuthPending(false);
                } else {
                    setRequesterDisplay(`Authenticated Requester (Session Pending) • ${TODAY_STR}`);
                    setAuthPending(true);
                }
            } else {
                setRequesterDisplay(`Authenticated Requester (Session Pending) • ${TODAY_STR}`);
                setAuthPending(true);
            }
        } catch {
            setRequesterDisplay(`Authenticated Requester (Session Pending) • ${TODAY_STR}`);
            setAuthPending(true);
        }

        // 2. Load reference data
        Promise.all([
            PurchaseOrderService.getNextNumber().catch(() => ({ data: { data: 'PR0001' } })),
            SupplierService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
            ProductService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
            CategoryService.getAll().catch(() => ({ data: { data: [] } })),
            PurchaseOrderService.getLowStockSuggestions().catch(() => ({ data: { data: [] } })),
        ]).then(([numRes, supRes, prodRes, catRes, sugRes]) => {
            if (!isMounted) return;

            const nextNum = numRes.data?.data || 'PR0001';
            setRequestId(nextNum);

            const supList = (supRes.data?.data || []).filter(s => s.status !== 'DISABLED');
            setSuppliers(supList);

            const prodList = (prodRes.data?.data || []).filter(p => p.status === 'ACTIVE');
            setAllProducts(prodList);

            const catList = catRes.data?.data || [];
            setCategories(catList);

            const sugList = sugRes.data?.data || [];
            setLowStockSuggestions(sugList);
        }).catch((err) => {
            if (isMounted) {
                setPageError(err.response?.data?.message || 'Failed to initialize purchase request data.');
            }
        }).finally(() => {
            if (isMounted) setLoadingInit(false);
        });

        return () => { isMounted = false; };
    }, []);

    // ── Close Search Dropdown on Outside Click ──
    useEffect(() => {
        function handleClickOutside(e) {
            if (searchContainerRef.current && !searchContainerRef.current.contains(e.target)) {
                setIsSearchOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // ── Filter Products for Search Dropdown ──
    const filteredSearchProducts = useMemo(() => {
        if (!searchKeyword.trim() && !selectedCategory) return [];

        let list = allProducts;
        if (selectedCategory) {
            list = list.filter(p => String(p.categoryId) === String(selectedCategory));
        }

        if (searchKeyword.trim()) {
            const kw = searchKeyword.toLowerCase().trim();
            list = list.filter(p =>
                p.productName.toLowerCase().includes(kw) ||
                p.productCode.toLowerCase().includes(kw) ||
                (p.barcode && p.barcode.toLowerCase().includes(kw)) ||
                (p.categoryName && p.categoryName.toLowerCase().includes(kw))
            );
        }

        return list.slice(0, 10);
    }, [searchKeyword, selectedCategory, allProducts]);

    // Track added product IDs for 1-click state in low-stock suggestions
    const addedProductIds = useMemo(() => {
        return new Set(items.map(it => it.productId));
    }, [items]);

    // ── Add Product from Search (Manual selection) ──
    const handleAddProduct = useCallback((product) => {
        setItems(prevItems => {
            const existingIndex = prevItems.findIndex(it => it.productId === product.productId);
            if (existingIndex >= 0) {
                // If already added from search, do not duplicate line
                return prevItems;
            }

            const defaultCost = product.sellingPrice != null ? Number(product.sellingPrice) : 0;
            return [
                ...prevItems,
                {
                    productId: product.productId,
                    productCode: product.productCode,
                    productName: product.productName,
                    categoryName: product.categoryName || 'General',
                    barcode: product.barcode,
                    unit: product.unit || 'pcs',
                    stockQuantity: product.stockQuantity != null ? Number(product.stockQuantity) : 0,
                    minimumStockLevel: product.minimumStockLevel != null ? Number(product.minimumStockLevel) : 0,
                    quantity: 1,
                    unitCost: defaultCost,
                }
            ];
        });

        // Clear search inputs and table error
        setSearchKeyword('');
        setIsSearchOpen(false);
        setFieldErrors(prev => {
            const next = { ...prev };
            delete next.items;
            return next;
        });
        setPageError('');
    }, []);

    // ── Add Product from Low-Stock Suggestions (Adds once with stock shortfall) ──
    const handleAddLowStockProduct = useCallback((product) => {
        setItems(prevItems => {
            const existingIndex = prevItems.findIndex(it => it.productId === product.productId);
            if (existingIndex >= 0) {
                // Do not increment repeatedly; product is already in the table
                return prevItems;
            }

            const stock = Number(product.stockQuantity || 0);
            const minLevel = Number(product.minimumStockLevel || 0);
            // Initialize requested quantity from the stock shortfall with a minimum of 1
            const shortfall = Math.max(1, Math.round(minLevel - stock));
            const defaultCost = product.sellingPrice != null ? Number(product.sellingPrice) : 0;

            return [
                ...prevItems,
                {
                    productId: product.productId,
                    productCode: product.productCode,
                    productName: product.productName,
                    categoryName: product.categoryName || 'General',
                    barcode: product.barcode,
                    unit: product.unit || 'pcs',
                    stockQuantity: stock,
                    minimumStockLevel: minLevel,
                    quantity: shortfall,
                    unitCost: defaultCost,
                }
            ];
        });

        setFieldErrors(prev => {
            const next = { ...prev };
            delete next.items;
            return next;
        });
        setPageError('');
    }, []);

    // ── Modify Item Attributes (Quantity or Estimated Unit Cost) ──
    const handleItemChange = (index, field, value) => {
        setItems(prev => {
            const next = [...prev];
            next[index] = { ...next[index], [field]: value };
            return next;
        });

        // Clear item-specific field error
        setFieldErrors(prev => {
            const next = { ...prev };
            delete next[`item_${index}_${field}`];
            return next;
        });
    };

    // ── Remove Item ──
    const handleRemoveItem = (index) => {
        setItems(prev => prev.filter((_, i) => i !== index));
    };

    // ── Recalculate Totals (Live update on quantity or estimated cost changes) ──
    const { totalUnits, overallEstimatedTotal } = useMemo(() => {
        let units = 0;
        let total = 0;
        for (const it of items) {
            const q = parseFloat(it.quantity) || 0;
            const c = parseFloat(it.unitCost) || 0;
            units += q;
            total += (q * c);
        }
        return { totalUnits: units, overallEstimatedTotal: total };
    }, [items]);

    // ── Validate Inputs ──
    const validateForm = () => {
        const errors = {};

        if (!supplierId) {
            errors.supplierId = 'Supplier is mandatory. Please select a supplier.';
        }

        if (!expectedDeliveryDate) {
            errors.expectedDeliveryDate = 'Expected delivery date is mandatory.';
        } else if (expectedDeliveryDate <= TODAY_STR) {
            errors.expectedDeliveryDate = 'Expected delivery date must be in the future (after today).';
        }

        if (note && note.length > 255) {
            errors.note = 'Note must not exceed 255 characters.';
        }

        if (items.length === 0) {
            errors.items = 'At least one product must be added to the purchase request.';
        }

        items.forEach((item, index) => {
            const q = parseFloat(item.quantity);
            if (isNaN(q) || q <= 0) {
                errors[`item_${index}_quantity`] = 'Quantity must be greater than zero.';
            }

            const c = parseFloat(item.unitCost);
            if (isNaN(c) || c < 0) {
                errors[`item_${index}_unitCost`] = 'Estimated unit cost must not be negative.';
            }
        });

        setFieldErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // ── Submit for Approval ──
    const handleSubmit = async (e) => {
        if (e) e.preventDefault();
        setPageError('');

        if (!validateForm()) {
            setPageError('Please correct the highlighted errors before submitting.');
            return;
        }

        setSubmitting(true);

        const payload = {
            poNumber: requestId || null,
            supplierId: Number(supplierId),
            expectedDeliveryDate,
            note: note.trim() || null,
            details: items.map(it => ({
                productId: it.productId,
                requestedQuantity: Number(it.quantity),
                unitPrice: Number(it.unitCost) || 0,
            })),
        };

        try {
            const res = await PurchaseOrderService.createRequest(payload);
            const created = res.data?.data;
            const code = created?.poNumber || requestId;

            // Navigate to Purchase Orders & Requests list with success confirmation
            navigate('/purchase-orders', {
                state: {
                    successMessage: `Purchase Request ${code} submitted for approval successfully!`
                }
            });
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Failed to submit purchase request.';
            setPageError(msg);
            setSubmitting(false);
        }
    };

    // ── Cancel Button Handler ──
    const handleCancelClick = () => {
        if (isDirty) {
            setShowCancelModal(true);
        } else {
            navigate('/purchase-orders');
        }
    };

    if (loadingInit) {
        return (
            <div className="sms-create-pr-page">
                <div className="sms-create-pr-loading">
                    <div className="sms-loading-spinner" />
                    <p>Loading purchase request form...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="sms-create-pr-page">
            {/* Top Navigation & Header */}
            <div className="sms-create-pr-header">
                <div className="sms-create-pr-header-left">
                    <button
                        type="button"
                        className="sms-btn-back"
                        onClick={handleCancelClick}
                        title="Back to Purchase Orders"
                    >
                        <ArrowLeft size={18} />
                        <span>Back</span>
                    </button>
                    <div>
                        <h1 className="sms-create-pr-title">Create Purchase Request</h1>
                        <p className="sms-create-pr-subtitle">
                            Fill out supplier and product stock requirements, then submit for manager approval (UC46)
                        </p>
                    </div>
                </div>
                <div className="sms-create-pr-header-right">
                    <span className="sms-status-badge sms-status-badge--pending">
                        Status: Pending Approval
                    </span>
                </div>
            </div>

            {/* Notification Banners */}
            {pageError && (
                <div className="sms-alert sms-alert--danger">
                    <AlertCircle size={18} className="sms-alert-icon" />
                    <div className="sms-alert-content">
                        <strong>Error:</strong> {pageError}
                    </div>
                </div>
            )}

            <form onSubmit={handleSubmit} noValidate>
                {/* ── SECTION 1: General Request Information ── */}
                <div className="sms-card sms-pr-card">
                    <div className="sms-card-header">
                        <div className="sms-card-header-title">
                            <FileText size={18} />
                            <span>Request Information</span>
                        </div>
                    </div>
                    <div className="sms-card-body">
                        <div className="sms-form-grid">
                            {/* Request ID (Read-only) */}
                            <div className="sms-form-group">
                                <label className="sms-form-label" htmlFor="pr-request-id">
                                    <Hash size={14} />
                                    <span>Request ID (Auto-generated)</span>
                                </label>
                                <input
                                    id="pr-request-id"
                                    type="text"
                                    className="sms-input sms-input--readonly"
                                    value={requestId}
                                    readOnly
                                    title="Auto-generated purchase request identifier"
                                />
                                <span className="sms-form-hint">Unique identifier generated by system</span>
                            </div>

                            {/* Supplier Selection */}
                            <div className="sms-form-group">
                                <label className="sms-form-label sms-form-label--required" htmlFor="pr-supplier">
                                    <Building2 size={14} />
                                    <span>Supplier</span>
                                </label>
                                <select
                                    id="pr-supplier"
                                    className={`sms-select ${fieldErrors.supplierId ? 'sms-input--error' : ''}`}
                                    value={supplierId}
                                    onChange={(e) => {
                                        setSupplierId(e.target.value);
                                        setFieldErrors(prev => {
                                            const next = { ...prev };
                                            delete next.supplierId;
                                            return next;
                                        });
                                    }}
                                >
                                    <option value="">-- Select Supplier --</option>
                                    {suppliers.map(s => (
                                        <option key={s.supplierId} value={s.supplierId}>
                                            {s.supplierName} ({s.supplierCode})
                                        </option>
                                    ))}
                                </select>
                                {fieldErrors.supplierId ? (
                                    <span className="sms-field-error">{fieldErrors.supplierId}</span>
                                ) : (
                                    <span className="sms-form-hint">Select the supplier products will be ordered from</span>
                                )}
                            </div>

                            {/* Requested By & Request Date (Read-only) */}
                            <div className="sms-form-group">
                                <label className="sms-form-label" htmlFor="pr-requester">
                                    <User size={14} />
                                    <span>Requested By / Request Date</span>
                                </label>
                                <input
                                    id="pr-requester"
                                    type="text"
                                    className="sms-input sms-input--readonly"
                                    value={requesterDisplay}
                                    readOnly
                                />
                                <span className="sms-form-hint">
                                    {authPending
                                        ? 'Security integration boundary: Requester is resolved by backend SecurityContextHolder'
                                        : 'Authenticated user and today timestamp'}
                                </span>
                            </div>

                            {/* Expected Delivery Date */}
                            <div className="sms-form-group">
                                <label className="sms-form-label sms-form-label--required" htmlFor="pr-delivery-date">
                                    <Calendar size={14} />
                                    <span>Expected Delivery Date</span>
                                </label>
                                <input
                                    id="pr-delivery-date"
                                    type="date"
                                    min={getTomorrowStr()}
                                    className={`sms-input ${fieldErrors.expectedDeliveryDate ? 'sms-input--error' : ''}`}
                                    value={expectedDeliveryDate}
                                    onChange={(e) => {
                                        setExpectedDeliveryDate(e.target.value);
                                        setFieldErrors(prev => {
                                            const next = { ...prev };
                                            delete next.expectedDeliveryDate;
                                            return next;
                                        });
                                    }}
                                />
                                {fieldErrors.expectedDeliveryDate ? (
                                    <span className="sms-field-error">{fieldErrors.expectedDeliveryDate}</span>
                                ) : (
                                    <span className="sms-form-hint">Date by which goods are required (must be in future)</span>
                                )}
                            </div>

                            {/* Note / Remarks */}
                            <div className="sms-form-group sms-form-group--full">
                                <div className="sms-form-label-row">
                                    <label className="sms-form-label" htmlFor="pr-note">
                                        <span>Requester Note (Optional remarks for approving Manager)</span>
                                    </label>
                                    <span className={`sms-char-counter ${note.length > 255 ? 'sms-char-counter--exceeded' : ''}`}>
                                        {note.length} / 255
                                    </span>
                                </div>
                                <textarea
                                    id="pr-note"
                                    rows={2}
                                    maxLength={255}
                                    className={`sms-textarea ${fieldErrors.note ? 'sms-input--error' : ''}`}
                                    placeholder="Enter any justifications, requirements, or urgency notes for the manager..."
                                    value={note}
                                    onChange={(e) => {
                                        setNote(e.target.value);
                                        setFieldErrors(prev => {
                                            const next = { ...prev };
                                            delete next.note;
                                            return next;
                                        });
                                    }}
                                />
                                {fieldErrors.note && <span className="sms-field-error">{fieldErrors.note}</span>}
                            </div>
                        </div>
                    </div>
                </div>

                {/* ── SECTION 2: Low-Stock Suggestions Panel ── */}
                <div className="sms-card sms-pr-card">
                    <div className="sms-card-header sms-card-header--alert">
                        <div className="sms-card-header-title">
                            <AlertTriangle size={18} className="sms-color-warning" />
                            <span>Low-Stock Product Suggestions</span>
                            <span className="sms-badge sms-badge--pill">
                                {lowStockSuggestions.length} Available
                            </span>
                        </div>
                        <span className="sms-header-caption">
                            Active products at or below minimum stock level (1-click add with shortfall quantity)
                        </span>
                    </div>
                    <div className="sms-card-body">
                        {lowStockSuggestions.length === 0 ? (
                            <div className="sms-empty-suggestions">
                                <CheckCircle2 size={24} className="sms-color-success" />
                                <span>All products currently satisfy minimum inventory thresholds.</span>
                            </div>
                        ) : (
                            <div className="sms-suggestions-grid">
                                {lowStockSuggestions.map(p => {
                                    const isAdded = addedProductIds.has(p.productId);
                                    const stock = Number(p.stockQuantity || 0);
                                    const min = Number(p.minimumStockLevel || 0);
                                    const isCritical = stock <= 0;
                                    const shortfall = Math.max(1, Math.round(min - stock));

                                    return (
                                        <div
                                            key={p.productId}
                                            className={`sms-suggestion-chip ${isCritical ? 'sms-suggestion-chip--critical' : ''}`}
                                        >
                                            <div className="sms-suggestion-main">
                                                <div className="sms-suggestion-title-row">
                                                    <span className="sms-suggestion-code">{p.productCode}</span>
                                                    <span className="sms-suggestion-category">{p.categoryName || 'General'}</span>
                                                </div>
                                                <div className="sms-suggestion-name" title={p.productName}>
                                                    {p.productName}
                                                </div>
                                                <div className="sms-suggestion-metrics">
                                                    <span className={`sms-stock-pill ${isCritical ? 'sms-stock-pill--out' : 'sms-stock-pill--low'}`}>
                                                        Stock: {formatNumber(stock)} / Min: {formatNumber(min)} {p.unit}
                                                    </span>
                                                    <span className="sms-suggestion-price">
                                                        Est: {formatCurrency(p.sellingPrice)}
                                                    </span>
                                                </div>
                                            </div>
                                            <button
                                                type="button"
                                                className={`sms-btn sms-btn--sm ${isAdded ? 'sms-btn--added' : 'sms-btn--outline-primary'}`}
                                                onClick={() => {
                                                    if (!isAdded) handleAddLowStockProduct(p);
                                                }}
                                                disabled={isAdded}
                                                title={isAdded ? 'Product already in request' : `Add to request with shortfall (${shortfall} ${p.unit})`}
                                            >
                                                {isAdded ? (
                                                    <>
                                                        <Check size={14} />
                                                        <span>Added</span>
                                                    </>
                                                ) : (
                                                    <>
                                                        <Plus size={14} />
                                                        <span>Add</span>
                                                    </>
                                                )}
                                            </button>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>

                {/* ── SECTION 3: Product Search & Filter Bar ── */}
                <div className="sms-card sms-pr-card">
                    <div className="sms-card-header">
                        <div className="sms-card-header-title">
                            <Package size={18} />
                            <span>Add Products to Request</span>
                        </div>
                    </div>
                    <div className="sms-card-body">
                        <div className="sms-search-row" ref={searchContainerRef}>
                            {/* Category Filter */}
                            <div className="sms-search-category-col">
                                <div className="sms-select-wrapper">
                                    <Layers size={14} className="sms-select-icon" />
                                    <select
                                        className="sms-select sms-select--with-icon"
                                        value={selectedCategory}
                                        onChange={(e) => setSelectedCategory(e.target.value)}
                                    >
                                        <option value="">All Categories</option>
                                        {categories.map(c => (
                                            <option key={c.categoryId} value={c.categoryId}>
                                                {c.categoryName}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            {/* Search Input with Autocomplete Dropdown */}
                            <div className="sms-search-input-col">
                                <div className="sms-search-input-wrapper">
                                    <Search size={16} className="sms-search-icon" />
                                    <input
                                        type="text"
                                        className="sms-input sms-input--with-icon"
                                        placeholder="Search products by Name, SKU / Code, or Barcode..."
                                        value={searchKeyword}
                                        onChange={(e) => {
                                            setSearchKeyword(e.target.value);
                                            setIsSearchOpen(true);
                                        }}
                                        onFocus={() => setIsSearchOpen(true)}
                                    />
                                    {searchKeyword && (
                                        <button
                                            type="button"
                                            className="sms-search-clear"
                                            onClick={() => setSearchKeyword('')}
                                        >
                                            ×
                                        </button>
                                    )}
                                </div>

                                {/* Autocomplete Results Dropdown */}
                                {isSearchOpen && filteredSearchProducts.length > 0 && (
                                    <div className="sms-autocomplete-menu">
                                        <div className="sms-autocomplete-header">
                                            <span>Matching Products ({filteredSearchProducts.length})</span>
                                        </div>
                                        <div className="sms-autocomplete-list">
                                            {filteredSearchProducts.map(p => {
                                                const isAdded = addedProductIds.has(p.productId);
                                                return (
                                                    <div
                                                        key={p.productId}
                                                        className="sms-autocomplete-item"
                                                        onClick={() => {
                                                            if (!isAdded) handleAddProduct(p);
                                                        }}
                                                    >
                                                        <div className="sms-autocomplete-item-info">
                                                            <div className="sms-autocomplete-title-row">
                                                                <span className="sms-autocomplete-code">{p.productCode}</span>
                                                                <span className="sms-autocomplete-name">{p.productName}</span>
                                                            </div>
                                                            <div className="sms-autocomplete-meta">
                                                                <span>Cat: {p.categoryName || 'General'}</span>
                                                                <span>Stock: {formatNumber(p.stockQuantity)} {p.unit}</span>
                                                                <span>Ref Price: {formatCurrency(p.sellingPrice)}</span>
                                                            </div>
                                                        </div>
                                                        <button
                                                            type="button"
                                                            className={`sms-btn sms-btn--xs ${isAdded ? 'sms-btn--added' : 'sms-btn--primary'}`}
                                                            disabled={isAdded}
                                                        >
                                                            {isAdded ? (
                                                                <>
                                                                    <Check size={12} />
                                                                    <span>Added</span>
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <Plus size={12} />
                                                                    <span>Select</span>
                                                                </>
                                                            )}
                                                        </button>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>

                        {fieldErrors.items && (
                            <div className="sms-field-error sms-table-error">
                                <AlertCircle size={14} />
                                <span>{fieldErrors.items}</span>
                            </div>
                        )}

                        {/* ── SECTION 4: Requested Items Table ── */}
                        <div className="sms-table-responsive sms-items-table-container">
                            <table className="sms-table sms-items-table">
                                <thead>
                                    <tr>
                                        <th style={{ width: '4%' }}>#</th>
                                        <th style={{ width: '28%' }}>Product Details</th>
                                        <th style={{ width: '10%' }} className="text-right">Current Stock</th>
                                        <th style={{ width: '10%' }} className="text-right">Min Level</th>
                                        <th style={{ width: '14%' }} className="text-center">Requested Qty *</th>
                                        <th style={{ width: '8%' }}>Unit</th>
                                        <th style={{ width: '14%' }} className="text-right">Est. Unit Cost (₫)</th>
                                        <th style={{ width: '14%' }} className="text-right">Subtotal (₫)</th>
                                        <th style={{ width: '5%' }} className="text-center">Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.length === 0 ? (
                                        <tr>
                                            <td colSpan={9} className="sms-table-empty">
                                                <div className="sms-table-empty-box">
                                                    <Package size={36} className="sms-color-muted" />
                                                    <p className="sms-empty-title">No products added yet</p>
                                                    <p className="sms-empty-desc">
                                                        Use the product search bar above or click "Add" on any low-stock suggestion
                                                    </p>
                                                </div>
                                            </td>
                                        </tr>
                                    ) : (
                                        items.map((item, index) => {
                                            const qty = parseFloat(item.quantity) || 0;
                                            const cost = parseFloat(item.unitCost) || 0;
                                            const subtotal = qty * cost;
                                            const qtyErr = fieldErrors[`item_${index}_quantity`];
                                            const costErr = fieldErrors[`item_${index}_unitCost`];

                                            return (
                                                <tr key={item.productId} className="sms-item-row">
                                                    <td className="sms-cell-index">{index + 1}</td>
                                                    <td>
                                                        <div className="sms-product-cell">
                                                            <span className="sms-product-cell-name">{item.productName}</span>
                                                            <div className="sms-product-cell-meta">
                                                                <span className="sms-code-tag">{item.productCode}</span>
                                                                <span className="sms-cat-tag">{item.categoryName}</span>
                                                                {item.barcode && (
                                                                    <span className="sms-barcode-tag">{item.barcode}</span>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td className="text-right">
                                                        <span className="sms-stock-num">{formatNumber(item.stockQuantity)}</span>
                                                    </td>
                                                    <td className="text-right">
                                                        <span className="sms-min-num">{formatNumber(item.minimumStockLevel)}</span>
                                                    </td>
                                                    <td className="text-center">
                                                        <div className="sms-inline-input-wrapper">
                                                            <input
                                                                type="number"
                                                                min="0.001"
                                                                step="any"
                                                                className={`sms-input sms-input--sm sms-input--num ${qtyErr ? 'sms-input--error' : ''}`}
                                                                value={item.quantity}
                                                                onChange={(e) => handleItemChange(index, 'quantity', e.target.value)}
                                                                required
                                                            />
                                                            {qtyErr && <span className="sms-cell-error">{qtyErr}</span>}
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <span className="sms-unit-badge">{item.unit}</span>
                                                    </td>
                                                    <td className="text-right">
                                                        <div className="sms-inline-input-wrapper">
                                                            <input
                                                                type="number"
                                                                min="0"
                                                                step="any"
                                                                className={`sms-input sms-input--sm sms-input--num text-right ${costErr ? 'sms-input--error' : ''}`}
                                                                value={item.unitCost}
                                                                onChange={(e) => handleItemChange(index, 'unitCost', e.target.value)}
                                                                placeholder="0"
                                                            />
                                                            {costErr && <span className="sms-cell-error">{costErr}</span>}
                                                        </div>
                                                    </td>
                                                    <td className="text-right sms-cell-subtotal">
                                                        <strong>{formatCurrency(subtotal)}</strong>
                                                    </td>
                                                    <td className="text-center">
                                                        <button
                                                            type="button"
                                                            className="sms-btn-icon sms-btn-icon--danger"
                                                            onClick={() => handleRemoveItem(index)}
                                                            title="Remove product"
                                                        >
                                                            <Trash2 size={16} />
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    )}
                                </tbody>
                            </table>
                        </div>

                        {/* Estimated Summary Footer (Recalculated live on quantity & unit cost changes) */}
                        {items.length > 0 && (
                            <div className="sms-items-summary-bar">
                                <div className="sms-items-summary-left">
                                    <span className="sms-summary-item">
                                        Total Products: <strong>{items.length}</strong>
                                    </span>
                                    <span className="sms-summary-divider">•</span>
                                    <span className="sms-summary-item">
                                        Total Requested Units: <strong>{formatNumber(totalUnits)}</strong>
                                    </span>
                                </div>
                                <div className="sms-items-summary-right">
                                    <span className="sms-total-label">Estimated Total:</span>
                                    <span className="sms-total-amount">{formatCurrency(overallEstimatedTotal)}</span>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                {/* ── SECTION 5: Form Actions ── */}
                <div className="sms-pr-actions-bar">
                    <button
                        type="button"
                        className="sms-btn sms-btn--secondary"
                        onClick={handleCancelClick}
                        disabled={submitting}
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        className="sms-btn sms-btn--primary sms-btn--lg"
                        disabled={submitting}
                    >
                        {submitting ? (
                            <>
                                <div className="sms-btn-spinner" />
                                <span>Submitting Request...</span>
                            </>
                        ) : (
                            <>
                                <Send size={18} />
                                <span>Submit for Approval</span>
                            </>
                        )}
                    </button>
                </div>
            </form>

            {/* Cancel / Discard Confirmation Modal */}
            {showCancelModal && (
                <div className="sms-modal-backdrop">
                    <div className="sms-modal">
                        <div className="sms-modal-header">
                            <AlertTriangle size={20} className="sms-color-warning" />
                            <h3>Discard Purchase Request?</h3>
                        </div>
                        <div className="sms-modal-body">
                            <p>You have unsaved changes in this purchase request. Are you sure you want to discard them and return to the purchase orders list?</p>
                        </div>
                        <div className="sms-modal-footer">
                            <button
                                type="button"
                                className="sms-btn sms-btn--secondary"
                                onClick={() => setShowCancelModal(false)}
                            >
                                Keep Editing
                            </button>
                            <button
                                type="button"
                                className="sms-btn sms-btn--danger"
                                onClick={() => {
                                    setShowCancelModal(false);
                                    navigate('/purchase-orders');
                                }}
                            >
                                Discard & Exit
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
