import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import StockReceiptService from '../../services/stockReceiptService';
import SupplierService from '../../services/supplierService';
import ProductService from '../../services/productService';
import PurchaseOrderService from '../../services/purchaseOrderService';
import './CreateStockReceiptPage.css';

// ─── Formatting helpers ────────────────────────────────────────────────────────

function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency', currency: 'VND', minimumFractionDigits: 0,
    }).format(amount);
}

function formatNumber(n) {
    if (n == null || isNaN(n)) return '0';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
}

const TODAY_STR = new Date().toISOString().split('T')[0];

export default function CreateStockReceiptPage() {
    const navigate = useNavigate();
    const { id } = useParams();
    const [searchParams] = useSearchParams();
    const editId = id || searchParams.get('edit');
    const isEditMode = Boolean(editId);

    // ── Header Information State ──
    const [receiptNumber, setReceiptNumber] = useState('');
    const [supplierId, setSupplierId]       = useState('');
    const [purchaseOrderId, setPurchaseOrderId] = useState('');
    const [suppliers, setSuppliers]         = useState([]);
    const [eligibleOrders, setEligibleOrders] = useState([]);
    const [loadingOrders, setLoadingOrders] = useState(false);
    const [selectedPo, setSelectedPo]       = useState(null);

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
    const [nonEditableError, setNonEditableError] = useState(null);
    const [errors, setErrors]               = useState({});

    // ── Helper to load eligible POs for a supplier ──
    const loadEligibleOrders = useCallback(async (supId, preselectedPoId = null) => {
        if (!supId) {
            setEligibleOrders([]);
            setSelectedPo(null);
            return;
        }
        setLoadingOrders(true);
        try {
            const res = await PurchaseOrderService.getEligibleForSupplier(supId);
            let orders = res.data?.data || [];

            // If a PO was already linked (e.g. in draft), ensure it is available in options
            if (preselectedPoId && !orders.some(o => o.orderId === Number(preselectedPoId))) {
                try {
                    const poRes = await PurchaseOrderService.getById(preselectedPoId);
                    if (poRes.data?.data) {
                        orders = [poRes.data.data, ...orders];
                    }
                } catch (ignored) {}
            }

            setEligibleOrders(orders);

            if (preselectedPoId) {
                const found = orders.find(o => o.orderId === Number(preselectedPoId));
                if (found) {
                    setSelectedPo(found);
                } else {
                    const poRes = await PurchaseOrderService.getById(preselectedPoId);
                    setSelectedPo(poRes.data?.data || null);
                }
            }
        } catch (err) {
            console.error('Failed to load eligible purchase orders', err);
            setEligibleOrders([]);
        } finally {
            setLoadingOrders(false);
        }
    }, []);

    // ── Load initial data: active suppliers, active products, and receipt details (edit) or next number (create) ──
    useEffect(() => {
        let isMounted = true;

        const loadData = async () => {
            try {
                const [supRes, prodRes] = await Promise.all([
                    SupplierService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
                    ProductService.getAll({ status: 'ACTIVE' }).catch(() => ({ data: { data: [] } })),
                ]);

                if (!isMounted) return;

                const supList = (supRes.data?.data || []).filter(s => s.status !== 'DISABLED');
                setSuppliers(supList);

                const prodList = (prodRes.data?.data || []).filter(p => p.status === 'ACTIVE');
                setAllProducts(prodList);

                if (isEditMode) {
                    try {
                        const receiptRes = await StockReceiptService.getById(editId);
                        if (!isMounted) return;
                        const receipt = receiptRes.data?.data;
                        if (!receipt) {
                            setNonEditableError(`Stock receipt #${editId} not found.`);
                            return;
                        }
                        if (receipt.status !== 'DRAFT') {
                            setNonEditableError(
                                `Stock receipt "${receipt.receiptNumber}" is ${receipt.status} and cannot be edited. Only DRAFT stock receipts can be edited.`
                            );
                            return;
                        }

                        setReceiptNumber(receipt.receiptNumber || '');
                        setSupplierId(receipt.supplierId ? String(receipt.supplierId) : '');
                        setPurchaseOrderId(receipt.purchaseOrderId ? String(receipt.purchaseOrderId) : '');

                        let poData = null;
                        if (receipt.purchaseOrderId) {
                            try {
                                const poRes = await PurchaseOrderService.getById(receipt.purchaseOrderId);
                                poData = poRes.data?.data || null;
                                setSelectedPo(poData);
                            } catch (ignored) {}
                        }

                        if (receipt.supplierId) {
                            loadEligibleOrders(receipt.supplierId, receipt.purchaseOrderId);
                        }

                        const mappedItems = (receipt.details || []).map(d => {
                            const matchedProd = prodList.find(p => p.productId === d.productId);
                            const poDetail = poData?.details?.find(p => p.productId === d.productId);
                            const ordered = poDetail ? (poDetail.orderedQuantity != null ? Number(poDetail.orderedQuantity) : Number(poDetail.requestedQuantity)) : null;
                            const prevRec = poDetail ? (Number(poDetail.receivedQuantity) || 0) : null;
                            const remaining = poDetail ? Math.max(0, ordered - prevRec) : null;

                            return {
                                productId: d.productId,
                                productCode: d.productCode,
                                productName: d.productName,
                                barcode: matchedProd?.barcode || '',
                                unit: matchedProd?.unit || 'pcs',
                                trackExpiry: Boolean(matchedProd?.trackExpiry || d.expirationDate || d.batchNumber),
                                quantity: d.receivedQuantity,
                                unitCost: d.unitCost,
                                batchNumber: d.batchNumber || '',
                                expirationDate: d.expirationDate ? String(d.expirationDate).split('T')[0] : '',
                                orderedQuantity: ordered,
                                previouslyReceived: prevRec,
                                remainingQuantity: remaining,
                            };
                        });
                        setItems(mappedItems);
                    } catch (err) {
                        if (!isMounted) return;
                        setNonEditableError(err.response?.data?.message || 'Failed to load stock receipt for editing.');
                    }
                } else {
                    const numRes = await StockReceiptService.getNextNumber().catch(() => ({ data: { data: 'SR0001' } }));
                    if (!isMounted) return;
                    setReceiptNumber(numRes.data?.data || 'SR0001');
                }
            } catch (err) {
                if (!isMounted) return;
                setPageError('Failed to initialize form data.');
            } finally {
                if (isMounted) setLoadingInit(false);
            }
        };

        loadData();

        return () => { isMounted = false; };
    }, [isEditMode, editId, loadEligibleOrders]);

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
        let list = allProducts;
        if (selectedPo && selectedPo.details) {
            const allowedIds = new Set(selectedPo.details.map(d => d.productId));
            list = list.filter(p => allowedIds.has(p.productId));
        }
        return list.filter(p =>
            p.productName.toLowerCase().includes(kw) ||
            p.productCode.toLowerCase().includes(kw) ||
            (p.barcode && p.barcode.toLowerCase().includes(kw)) ||
            (p.categoryName && p.categoryName.toLowerCase().includes(kw))
        ).slice(0, 8);
    }, [searchKeyword, allProducts, selectedPo]);

    // ── Add product to items ──
    const handleAddProduct = useCallback((product) => {
        if (selectedPo && selectedPo.details) {
            const inPo = selectedPo.details.some(d => d.productId === product.productId);
            if (!inPo) {
                setPageError(`Product "${product.productName}" is not included in Purchase Order ${selectedPo.poNumber}. Only products ordered in this PO can be received.`);
                setIsSearchOpen(false);
                return;
            }
        }

        const poDetail = selectedPo?.details?.find(d => d.productId === product.productId);
        const ordered = poDetail ? (poDetail.orderedQuantity != null ? Number(poDetail.orderedQuantity) : Number(poDetail.requestedQuantity)) : null;
        const prevRec = poDetail ? (Number(poDetail.receivedQuantity) || 0) : null;
        const remaining = poDetail ? Math.max(0, ordered - prevRec) : null;
        const unitCost = poDetail ? (Number(poDetail.unitPrice) || 0) : (product.sellingPrice != null ? Number(product.sellingPrice) : 0);

        setItems(prevItems => {
            const existingIndex = prevItems.findIndex(it => it.productId === product.productId);
            if (existingIndex >= 0 && !product.trackExpiry) {
                // If standard item already added, increment quantity by 1
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
                    quantity: remaining != null && remaining > 0 ? remaining : 1,
                    unitCost: unitCost,
                    batchNumber: '',
                    expirationDate: '',
                    orderedQuantity: ordered,
                    previouslyReceived: prevRec,
                    remainingQuantity: remaining,
                }
            ];
        });

        // Clear search and barcode states
        setSearchKeyword('');
        setIsSearchOpen(false);
        setBarcodeError('');
        setPageError('');
    }, [selectedPo]);

    // ── Supplier change ──
    const handleSupplierChange = (newSupId) => {
        setSupplierId(newSupId);
        setPurchaseOrderId('');
        setSelectedPo(null);
        setErrors(prev => {
            const next = { ...prev };
            delete next.supplierId;
            return next;
        });
        if (newSupId) {
            loadEligibleOrders(newSupId);
        } else {
            setEligibleOrders([]);
        }
    };

    // ── Purchase Order change ──
    const handlePurchaseOrderChange = async (e) => {
        const poIdStr = e.target.value;
        setPurchaseOrderId(poIdStr);
        setPageError('');

        if (!poIdStr) {
            setSelectedPo(null);
            return;
        }

        try {
            const res = await PurchaseOrderService.getById(poIdStr);
            const po = res.data?.data;
            setSelectedPo(po);

            if (po && po.details && po.details.length > 0) {
                const poItems = po.details
                    .filter(d => {
                        const ordered = d.orderedQuantity != null ? Number(d.orderedQuantity) : Number(d.requestedQuantity);
                        const received = Number(d.receivedQuantity) || 0;
                        return (ordered - received) > 0;
                    })
                    .map(d => {
                        const matchedProd = allProducts.find(p => p.productId === d.productId);
                        const ordered = d.orderedQuantity != null ? Number(d.orderedQuantity) : Number(d.requestedQuantity);
                        const received = Number(d.receivedQuantity) || 0;
                        const remaining = Math.max(0, ordered - received);

                        return {
                            productId: d.productId,
                            productCode: d.productCode || matchedProd?.productCode || '',
                            productName: d.productName || matchedProd?.productName || '',
                            barcode: matchedProd?.barcode || '',
                            unit: d.unit || matchedProd?.unit || 'pcs',
                            trackExpiry: Boolean(matchedProd?.trackExpiry),
                            quantity: remaining,
                            unitCost: Number(d.unitPrice) || Number(matchedProd?.sellingPrice) || 0,
                            batchNumber: '',
                            expirationDate: '',
                            orderedQuantity: ordered,
                            previouslyReceived: received,
                            remainingQuantity: remaining,
                        };
                    });

                if (poItems.length > 0) {
                    setItems(poItems);
                }
            }
        } catch (err) {
            setPageError('Failed to load purchase order details.');
        }
    };

    // ── Add another batch line for perishable product ──
    const handleAddBatchLine = (idx) => {
        const sourceItem = items[idx];
        const newBatchItem = {
            ...sourceItem,
            quantity: 1,
            batchNumber: '',
            expirationDate: '',
        };
        setItems(prev => {
            const next = [...prev];
            next.splice(idx + 1, 0, newBatchItem);
            return next;
        });
    };

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

        // Validate intra-receipt batch consistency for expiry-tracked items (Requirement 6)
        const batchExpiryMap = new Map();
        items.forEach((item, idx) => {
            if (item.trackExpiry && item.batchNumber && item.batchNumber.trim() && item.expirationDate) {
                const key = `${item.productId}:${item.batchNumber.trim().toLowerCase()}`;
                if (batchExpiryMap.has(key)) {
                    const firstDate = batchExpiryMap.get(key);
                    if (firstDate !== item.expirationDate) {
                        newErrors[`item_${idx}_expirationDate`] = `Conflicting expiration date for batch "${item.batchNumber.trim()}". Must match earlier line (${firstDate}).`;
                    }
                } else {
                    batchExpiryMap.set(key, item.expirationDate);
                }
            }
        });

        // Validate against linked Purchase Order
        if (selectedPo && selectedPo.details) {
            const allowedProductIds = new Set(selectedPo.details.map(d => d.productId));
            const qtyByProduct = {};

            items.forEach((item, idx) => {
                if (!allowedProductIds.has(item.productId)) {
                    newErrors[`item_${idx}_product`] = `Product is not part of PO ${selectedPo.poNumber}`;
                }
                const q = parseFloat(item.quantity) || 0;
                qtyByProduct[item.productId] = (qtyByProduct[item.productId] || 0) + q;
            });

            selectedPo.details.forEach(poDetail => {
                const orderedQty = poDetail.orderedQuantity != null ? Number(poDetail.orderedQuantity) : Number(poDetail.requestedQuantity);
                const prevRec = Number(poDetail.receivedQuantity) || 0;
                const remaining = Math.max(0, orderedQty - prevRec);
                const enteredTotal = qtyByProduct[poDetail.productId] || 0;

                if (enteredTotal > remaining + 0.0001) {
                    items.forEach((item, idx) => {
                        if (item.productId === poDetail.productId) {
                            newErrors[`item_${idx}_quantity`] = `Total entered (${enteredTotal}) exceeds remaining ordered quantity (${remaining})`;
                        }
                    });
                }
            });
        }

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
            purchaseOrderId: purchaseOrderId && !isNaN(Number(purchaseOrderId)) ? Number(purchaseOrderId) : null,
            status: targetStatus,
            items: items.map(item => ({
                productId: item.productId,
                quantity: parseFloat(item.quantity),
                unitCost: parseFloat(item.unitCost),
                batchNumber: item.trackExpiry ? (item.batchNumber?.trim() || null) : null,
                expirationDate: item.trackExpiry ? (item.expirationDate || null) : null,
            }))
        };

        const apiCall = isEditMode
            ? StockReceiptService.update(editId, payload)
            : StockReceiptService.create(payload);

        apiCall
            .then(res => {
                const targetId = isEditMode ? editId : res.data?.data?.stockReceiptId;
                if (targetId) {
                    navigate(`/inventory/stock-receipts/${targetId}`);
                } else {
                    navigate('/inventory/stock-receipts');
                }
            })
            .catch(err => {
                const msg = err.response?.data?.message || (isEditMode ? 'Failed to update stock receipt. Please try again.' : 'Failed to create stock receipt. Please try again.');
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

    if (nonEditableError) {
        return (
            <div className="csr-page">
                <div className="csr-card" style={{ padding: '48px 24px', textAlign: 'center' }}>
                    <div style={{ maxWidth: '480px', margin: '0 auto', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
                        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#e53e3e" strokeWidth="1.5">
                            <circle cx="12" cy="12" r="9"/>
                            <line x1="12" y1="8" x2="12" y2="12"/>
                            <circle cx="12" cy="16" r="0.5" fill="#e53e3e"/>
                        </svg>
                        <h2 style={{ margin: 0, fontSize: '18px', fontWeight: '700', color: '#19233b' }}>
                            Cannot Edit Stock Receipt
                        </h2>
                        <p style={{ margin: 0, fontSize: '14px', color: '#69748c', lineHeight: 1.5 }}>
                            {nonEditableError}
                        </p>
                        <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
                            {editId && (
                                <button
                                    type="button"
                                    className="csr-btn csr-btn--secondary"
                                    onClick={() => navigate(`/inventory/stock-receipts/${editId}`)}
                                >
                                    View Receipt Details
                                </button>
                            )}
                            <button
                                type="button"
                                className="csr-btn csr-btn--ghost"
                                onClick={() => navigate('/inventory/stock-receipts')}
                            >
                                Stock Receipts List
                            </button>
                        </div>
                    </div>
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
                            {isEditMode ? `Edit Draft (${receiptNumber})` : 'Create Stock Receipt'}
                        </span>
                    </nav>
                    <div className="csr-title-row">
                        <h1 className="csr-title">
                            {isEditMode ? `Edit Draft Receipt: ${receiptNumber}` : 'Create Stock Receipt'}
                        </h1>
                        <span className="csr-badge csr-badge--active">
                            {isEditMode ? 'Draft Mode' : 'Import Stock (UC36)'}
                        </span>
                    </div>
                    <p className="csr-subtitle">
                        {isEditMode
                            ? 'Update saved draft receipt line items and details before confirmation'
                            : 'Record and import new inbound stock batches into supermarket inventory'}
                    </p>
                </div>
                <div className="csr-header-actions">
                    <button
                        type="button"
                        className="csr-btn csr-btn--ghost"
                        onClick={() => navigate(isEditMode ? `/inventory/stock-receipts/${editId}` : '/inventory/stock-receipts')}
                        disabled={submitting}
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        className="csr-btn csr-btn--secondary"
                        onClick={() => handleSubmit('DRAFT')}
                        disabled={submitting}
                        title="Save changes to draft without updating inventory stock yet"
                    >
                        {isEditMode ? 'Save Draft' : 'Save as Draft'}
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
                        {/* Auto-generated / Locked Receipt ID */}
                        <div className="csr-field-group">
                            <label className="csr-label" htmlFor="csr-receipt-no">
                                Receipt Number <span className="csr-required">*</span>
                            </label>
                            <input
                                id="csr-receipt-no"
                                className={`csr-input csr-input--mono ${isEditMode ? 'csr-input--readonly' : ''}`}
                                type="text"
                                value={receiptNumber}
                                onChange={e => !isEditMode && setReceiptNumber(e.target.value)}
                                readOnly={isEditMode}
                                placeholder="e.g. SR0004"
                                maxLength={50}
                            />
                            <span className="csr-help-text">
                                {isEditMode
                                    ? 'Receipt number is fixed and cannot be changed for existing receipts'
                                    : 'Auto-generated sequence identifier (customizable)'}
                            </span>
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
                                onChange={e => handleSupplierChange(e.target.value)}
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

                        {/* Purchase Order Selector (Optional) */}
                        <div className="csr-field-group">
                            <label className="csr-label" htmlFor="csr-po-select">
                                Purchase Order (Optional)
                            </label>
                            <select
                                id="csr-po-select"
                                className="csr-select"
                                value={purchaseOrderId}
                                onChange={handlePurchaseOrderChange}
                                disabled={!supplierId || loadingOrders}
                            >
                                {!supplierId ? (
                                    <option value="">-- Select a supplier first --</option>
                                ) : loadingOrders ? (
                                    <option value="">Loading purchase orders...</option>
                                ) : (
                                    <>
                                        <option value="">-- Direct Stock Receipt (No PO) --</option>
                                        {eligibleOrders.map(po => {
                                            const statusLabel = po.status === 'PARTIALLY_FULFILLED'
                                                ? 'Partially Fulfilled'
                                                : po.status === 'ACCEPTED'
                                                ? 'Accepted by Supplier'
                                                : po.status;
                                            return (
                                                <option key={po.orderId} value={po.orderId}>
                                                    {po.poNumber} — {statusLabel} ({po.details?.length || 0} items)
                                                </option>
                                            );
                                        })}
                                    </>
                                )}
                            </select>
                            <span className="csr-help-text">
                                {!supplierId
                                    ? 'Select a supplier to view eligible purchase orders'
                                    : eligibleOrders.length === 0 && !loadingOrders
                                    ? 'No eligible (Accepted/Partial) orders found. Direct receipt will be created.'
                                    : 'Select an accepted or partially fulfilled order to fulfill and track received quantities'}
                            </span>
                        </div>
                    </div>

                    {/* Linked Purchase Order Info Banner */}
                    {selectedPo && (
                        <div style={{
                            marginTop: '16px',
                            padding: '12px 16px',
                            background: '#eff6ff',
                            border: '1px solid #bfdbfe',
                            borderRadius: '8px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            flexWrap: 'wrap',
                            gap: '12px'
                        }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                                <span style={{ fontWeight: 700, color: '#1e3a8a', fontSize: '14px' }}>
                                    Linked PO: {selectedPo.poNumber}
                                </span>
                                <span className={`csr-badge ${selectedPo.status === 'PARTIALLY_FULFILLED' ? 'csr-badge--warning' : 'csr-badge--active'}`}>
                                    {selectedPo.status === 'PARTIALLY_FULFILLED' ? 'Partially Fulfilled' : 'Accepted by Supplier'}
                                </span>
                                <span style={{ fontSize: '13px', color: '#475569' }}>
                                    Expected Delivery: <strong>{selectedPo.expectedDeliveryDate || '—'}</strong>
                                </span>
                            </div>
                            <span style={{ fontSize: '12px', color: '#1e40af', fontWeight: 500 }}>
                                Fulfilling approved items. Only products from this PO can be received.
                            </span>
                        </div>
                    )}
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
                                    <th scope="col" className="csr-th-cost">Unit Cost <span className="csr-required">*</span></th>
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
                                    const prodErr = errors[`item_${idx}_product`];
                                    const subtotal = (parseFloat(item.quantity) || 0) * (parseFloat(item.unitCost) || 0);

                                    return (
                                        <tr key={`${item.productId}_${idx}`} className="csr-tr">
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
                                                    {selectedPo && (
                                                        <div style={{ display: 'flex', gap: '6px', marginTop: '5px', flexWrap: 'wrap' }}>
                                                            <span className="csr-po-chip csr-po-chip--ordered">
                                                                Ordered: <strong>{item.orderedQuantity ?? '—'}</strong>
                                                            </span>
                                                            <span className="csr-po-chip csr-po-chip--received">
                                                                Prev: <strong>{item.previouslyReceived ?? 0}</strong>
                                                            </span>
                                                            <span className="csr-po-chip csr-po-chip--remaining">
                                                                Remaining: <strong>{item.remainingQuantity ?? '—'}</strong>
                                                            </span>
                                                        </div>
                                                    )}
                                                    {prodErr && <span className="csr-cell-error">{prodErr}</span>}
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
                                                {selectedPo && item.remainingQuantity != null && (
                                                    <span style={{ fontSize: '11px', color: '#64748b', display: 'block', marginTop: '2px' }}>
                                                        Max: {item.remainingQuantity}
                                                    </span>
                                                )}
                                                {qtyErr && <span className="csr-cell-error">{qtyErr}</span>}
                                            </td>

                                            {/* Unit Cost Input */}
                                            <td className="csr-td csr-td--cost">
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="1"
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

                                            {/* Action */}
                                            <td className="csr-td csr-td--action">
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                    {item.trackExpiry && (
                                                        <button
                                                            type="button"
                                                            className="csr-btn-add-batch"
                                                            onClick={() => handleAddBatchLine(idx)}
                                                            title="Add another batch line for this product"
                                                        >
                                                            + Batch
                                                        </button>
                                                    )}
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
                                                </div>
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
                    onClick={() => navigate(isEditMode ? `/inventory/stock-receipts/${editId}` : '/inventory/stock-receipts')}
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
                        {isEditMode ? 'Save Draft' : 'Save as Draft'}
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
