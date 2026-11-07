import { useState, useEffect, useRef, useMemo, Fragment } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import InventoryService from '../../services/inventoryService';
import './AdjustStockPage.css';

function formatQty(n) {
    if (n == null || isNaN(n)) return '0';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
}

function formatDate(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        return `${day}/${month}/${year}`;
    } catch {
        return iso;
    }
}

function formatCurrency(n) {
    if (n == null || isNaN(n)) return '—';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);
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
    const urlType = searchParams.get('type');
    const urlQuantity = searchParams.get('quantity');
    const urlReason = searchParams.get('reason');
    const urlSession = searchParams.get('session');

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
    const [adjustmentType, setAdjustmentType] = useState(urlType === 'DECREASE' ? 'DECREASE' : 'INCREASE'); // 'INCREASE' | 'DECREASE'
    const [quantityInput, setQuantityInput] = useState(urlQuantity || '');
    const [reason, setReason] = useState(urlReason || '');
    const [stockCheckId, setStockCheckId] = useState(urlStockCheckId || '');

    // ── Batch Allocation State (UC33) ──
    const [batches, setBatches] = useState([]); // List of GroupedProductBatchResponse
    const [loadingBatches, setLoadingBatches] = useState(false);
    const [expandedBatches, setExpandedBatches] = useState({}); // { [groupKey]: boolean }
    const [groupedAllocations, setGroupedAllocations] = useState({}); // { [groupKey]: number } (for DECREASE)
    const [detailAllocations, setDetailAllocations] = useState({}); // { [stockReceiptItemId]: number } (for INCREASE)
    const [showEmptyReceipts, setShowEmptyReceipts] = useState({}); // { [groupKey]: boolean } (reveal 0-qty receipts in DECREASE)
    const [batchDetailLimits, setBatchDetailLimits] = useState({}); // { [groupKey]: number } (pagination limit for expanded batch)
    const INITIAL_DETAIL_LIMIT = 10;

    // ── Stock Check Link & Conflict State (UC32 -> UC33) ──
    const [linkedStockCheck, setLinkedStockCheck] = useState(null);
    const [loadingStockCheck, setLoadingStockCheck] = useState(false);
    const [stockCheckConflict, setStockCheckConflict] = useState('');
    const [alreadyAdjustedError, setAlreadyAdjustedError] = useState('');
    const isStockCheckLocked = Boolean(stockCheckId);

    // ── UI / Error / Modal State ──
    const [fieldErrors, setFieldErrors] = useState({});
    const [apiError, setApiError] = useState('');
    const [manualSwitchWarning, setManualSwitchWarning] = useState('');
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

    // Synchronize form when query parameters change
    useEffect(() => {
        if (urlType) setAdjustmentType(urlType);
        if (urlQuantity) setQuantityInput(urlQuantity);
        if (urlReason) setReason(urlReason);
        if (urlStockCheckId) setStockCheckId(urlStockCheckId);
    }, [urlType, urlQuantity, urlReason, urlStockCheckId]);

    // Fetch and validate Stock Check reference if linked (UC32 -> UC33)
    useEffect(() => {
        if (!stockCheckId) {
            setLinkedStockCheck(null);
            setStockCheckConflict('');
            setAlreadyAdjustedError('');
            return;
        }

        let isMounted = true;
        setLoadingStockCheck(true);
        setStockCheckConflict('');
        setAlreadyAdjustedError('');

        InventoryService.getCheckById(stockCheckId)
            .then(res => {
                if (!isMounted) return;
                const sc = res.data?.data;
                if (!sc) return;

                setLinkedStockCheck(sc);

                // Check if already adjusted
                if (sc.status === 'COMPLETED' || sc.status === 'ADJUSTED') {
                    setAlreadyAdjustedError(`This Stock Check discrepancy (Check #${stockCheckId}) has already been adjusted into inventory.`);
                }

                // Preselect Product if available
                if (sc.productId && (!selectedProduct || selectedProduct.productId !== sc.productId)) {
                    const match = allProducts.find(p => String(p.productId) === String(sc.productId));
                    if (match) {
                        setSelectedProduct(match);
                    } else {
                        ProductService.getById(sc.productId).then(pRes => {
                            if (isMounted && pRes.data?.data) {
                                setSelectedProduct(pRes.data.data);
                            }
                        }).catch(() => {});
                    }
                }

                // Determine adjustment type from variance:
                // Negative variance -> DECREASE; Positive variance -> INCREASE
                const diff = sc.difference != null ? Number(sc.difference) : 0;
                const expectedType = diff < 0 ? 'DECREASE' : 'INCREASE';
                const expectedQty = Math.abs(diff);

                setAdjustmentType(expectedType);
                setQuantityInput(String(expectedQty));

                if (sc.reason && !reason) {
                    setReason(sc.reason);
                }
            })
            .catch(() => {
                if (!isMounted) return;
                setApiError(`Failed to load Stock Check #${stockCheckId} details.`);
            })
            .finally(() => {
                if (isMounted) setLoadingStockCheck(false);
            });

        return () => { isMounted = false; };
    }, [stockCheckId, allProducts]);

    // Conflict detection: verify current stock still matches recorded system balance before audit
    useEffect(() => {
        if (linkedStockCheck && selectedProduct) {
            const recordedSysQty = linkedStockCheck.systemQuantity != null ? Number(linkedStockCheck.systemQuantity) : null;
            const currentMasterQty = selectedProduct.stockQuantity != null ? Number(selectedProduct.stockQuantity) : null;

            if (recordedSysQty !== null && currentMasterQty !== null && recordedSysQty !== currentMasterQty) {
                setStockCheckConflict(
                    `Stock Conflict Detected: Current master stock on hand (${formatQty(currentMasterQty)}) has changed from the system balance recorded during the audit (${formatQty(recordedSysQty)}). Master stock has moved since the audit. Revalidation is required before adjusting.`
                );
            } else {
                setStockCheckConflict('');
            }
        } else {
            setStockCheckConflict('');
        }
    }, [linkedStockCheck, selectedProduct]);

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

    // ── Helper for Grouping Key ──
    const getGroupKey = (g) => `${(g.batchNumber || '').trim().toUpperCase()}|${g.expirationDate || ''}`;

    // ── Fetch Batches for Expiry-tracked Products ──
    useEffect(() => {
        setGroupedAllocations({});
        setDetailAllocations({});
        setExpandedBatches({});
        setShowEmptyReceipts({});
        setBatchDetailLimits({});
        if (!selectedProduct || !selectedProduct.trackExpiry) {
            setBatches([]);
            return;
        }

        let isMounted = true;
        setLoadingBatches(true);
        InventoryService.getGroupedProductBatches(selectedProduct.productId)
            .then(res => {
                if (!isMounted) return;
                setBatches(res.data?.data || []);
            })
            .catch(() => {
                if (!isMounted) return;
                setBatches([]);
            })
            .finally(() => {
                if (isMounted) setLoadingBatches(false);
            });

        return () => { isMounted = false; };
    }, [selectedProduct?.productId, selectedProduct?.trackExpiry]);

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

    // ── Batch Allocation Calculations (UC33) ──
    const requiresBatches = Boolean(selectedProduct?.trackExpiry);

    const totalBatchAllocated = useMemo(() => {
        if (adjustmentType === 'DECREASE') {
            return Object.values(groupedAllocations).reduce((sum, val) => {
                const n = Number(val);
                return sum + (Number.isInteger(n) && n > 0 ? n : 0);
            }, 0);
        } else {
            return Object.values(detailAllocations).reduce((sum, val) => {
                const n = Number(val);
                return sum + (Number.isInteger(n) && n > 0 ? n : 0);
            }, 0);
        }
    }, [adjustmentType, groupedAllocations, detailAllocations]);

    const remainingToAllocate = isValidPositiveInt ? (parsedQty - totalBatchAllocated) : 0;

    const hasOverDeduction = useMemo(() => {
        if (!requiresBatches || adjustmentType !== 'DECREASE') return false;
        return batches.some(g => {
            const key = getGroupKey(g);
            const allocated = Number(groupedAllocations[key] || 0);
            return allocated > Number(g.totalRemainingQuantity || 0);
        });
    }, [requiresBatches, adjustmentType, batches, groupedAllocations]);

    const isAllocationExact = requiresBatches ? (isValidPositiveInt && totalBatchAllocated === parsedQty) : true;

    const isAllocationValid = !requiresBatches || (
        isValidPositiveInt &&
        isAllocationExact &&
        !hasOverDeduction &&
        totalBatchAllocated > 0
    );

    const toggleBatchExpand = (key) => {
        setExpandedBatches(prev => ({ ...prev, [key]: !prev[key] }));
    };

    const handleGroupAllocationChange = (key, value) => {
        setGroupedAllocations(prev => {
            const next = { ...prev };
            if (value === '' || value == null) {
                delete next[key];
            } else {
                const num = parseInt(value, 10);
                if (isNaN(num) || num <= 0) {
                    delete next[key];
                } else {
                    next[key] = num;
                }
            }
            return next;
        });
        setFieldErrors(prev => ({ ...prev, batchAllocation: null }));
    };

    const handleDetailAllocationChange = (stockReceiptItemId, value) => {
        setDetailAllocations(prev => {
            const next = { ...prev };
            if (value === '' || value == null) {
                delete next[stockReceiptItemId];
            } else {
                const num = parseInt(value, 10);
                if (isNaN(num) || num <= 0) {
                    delete next[stockReceiptItemId];
                } else {
                    next[stockReceiptItemId] = num;
                }
            }
            return next;
        });
        setFieldErrors(prev => ({ ...prev, batchAllocation: null }));
    };

    const handleGroupQuickFill = (g) => {
        if (!isValidPositiveInt) return;
        const key = getGroupKey(g);
        const currentVal = Number(groupedAllocations[key] || 0);
        const otherAllocated = totalBatchAllocated - currentVal;
        const needed = Math.max(0, parsedQty - otherAllocated);
        const avail = Number(g.totalRemainingQuantity || 0);
        const toSet = Math.min(needed, avail);
        handleGroupAllocationChange(key, toSet > 0 ? toSet : '');
    };

    const handleDetailQuickFill = (detail) => {
        if (!isValidPositiveInt) return;
        const currentVal = Number(detailAllocations[detail.stockReceiptItemId] || 0);
        const otherAllocated = totalBatchAllocated - currentVal;
        const needed = Math.max(0, parsedQty - otherAllocated);
        handleDetailAllocationChange(detail.stockReceiptItemId, needed > 0 ? needed : '');
    };

    const isFormReady = Boolean(
        selectedProduct &&
        isValidPositiveInt &&
        !isStockNegative &&
        reason.trim().length > 0 &&
        !stockCheckConflict &&
        !alreadyAdjustedError &&
        !loadingStockCheck &&
        isAllocationValid
    );

    // ── Validation before Opening Modal ──
    const validateForm = () => {
        const errors = {};

        if (alreadyAdjustedError) {
            errors.stockCheck = alreadyAdjustedError;
        }

        if (stockCheckConflict) {
            errors.stockCheck = stockCheckConflict;
        }

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

        if (requiresBatches) {
            if (batches.length === 0) {
                errors.batchAllocation = adjustmentType === 'INCREASE'
                    ? 'This product tracks expiration, but has no confirmed batches to increase. New batches must be added via Stock In (UC36).'
                    : 'This product tracks expiration, but has no confirmed batches with available stock to decrease.';
            } else if (totalBatchAllocated !== parsedQty) {
                errors.batchAllocation = `Total allocated to batches (${formatQty(totalBatchAllocated)}) must match the adjustment quantity (${formatQty(parsedQty)}). Remaining: ${formatQty(parsedQty - totalBatchAllocated)}.`;
            } else if (hasOverDeduction) {
                errors.batchAllocation = 'One or more batches has an allocated decrease exceeding its available remaining quantity.';
            }
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
            batchAllocations: requiresBatches && adjustmentType === 'INCREASE'
                ? Object.entries(detailAllocations)
                    .filter(([, qty]) => Number(qty) > 0)
                    .map(([itemId, qty]) => {
                        let found = null;
                        for (const g of batches) {
                            const m = (g.details || []).find(d => String(d.stockReceiptItemId) === String(itemId));
                            if (m) { found = { ...m, batchNumber: g.batchNumber, expirationDate: g.expirationDate }; break; }
                        }
                        return {
                            stockReceiptItemId: Number(itemId),
                            batchNumber: found?.batchNumber || null,
                            expirationDate: found?.expirationDate || null,
                            quantity: Number(qty)
                        };
                    })
                : null,
            groupedBatchAllocations: requiresBatches && adjustmentType === 'DECREASE'
                ? batches
                    .filter(g => Number(groupedAllocations[getGroupKey(g)]) > 0)
                    .map(g => ({
                        batchNumber: g.batchNumber,
                        expirationDate: g.expirationDate,
                        quantity: Number(groupedAllocations[getGroupKey(g)])
                    }))
                : null
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

            // Reload batches if product is batch-tracked
            if (selectedProduct.trackExpiry) {
                InventoryService.getGroupedProductBatches(selectedProduct.productId).then(bRes => {
                    setBatches(bRes.data?.data || []);
                }).catch(() => {});
            }

            // Reset adjustment details form to initial state (preserve selectedProduct & successData)
            setAdjustmentType('INCREASE');
            setQuantityInput('');
            setReason('');
            setStockCheckId('');
            setGroupedAllocations({});
            setDetailAllocations({});
            setExpandedBatches({});
            setShowEmptyReceipts({});
            setBatchDetailLimits({});
            setFieldErrors({});
        } catch (err) {
            setShowConfirmModal(false);
            const msg = err.response?.data?.message || err.message || 'An error occurred while saving the stock adjustment.';
            setApiError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const handleSwitchToManual = () => {
        const prevSession = urlSession || linkedStockCheck?.checkNumber || '';
        const prevItemId = stockCheckId;

        // Unlink Stock Check association
        setStockCheckId('');
        setLinkedStockCheck(null);
        setStockCheckConflict('');
        setAlreadyAdjustedError('');

        // Reset Type, Quantity, Reason, Batch Allocations to defaults
        setAdjustmentType('INCREASE');
        setQuantityInput('');
        setReason('');
        setGroupedAllocations({});
        setDetailAllocations({});
        setExpandedBatches({});
        setShowEmptyReceipts({});
        setBatchDetailLimits({});
        setFieldErrors({});

        // Display warning banner that original audit discrepancy remains unresolved
        setManualSwitchWarning(
            `Switched to Manual Adjustment: The discrepancy for Audit Session ${prevSession ? `"${prevSession}" ` : ''}(Item #${prevItemId}) remains unresolved and pending in Stock Difference. This manual adjustment will not reconcile or complete that audit item.`
        );
    };

    const handleResetForAnother = () => {
        setSelectedProduct(null);
        setAdjustmentType('INCREASE');
        setQuantityInput('');
        setReason('');
        setStockCheckId('');
        setBatches([]);
        setGroupedAllocations({});
        setDetailAllocations({});
        setExpandedBatches({});
        setShowEmptyReceipts({});
        setBatchDetailLimits({});
        setSuccessData(null);
        setApiError('');
        setFieldErrors({});
        setManualSwitchWarning('');
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

            {/* ── Already Adjusted Warning Banner ── */}
            {alreadyAdjustedError && (
                <div className="adj-alert adj-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <div style={{ flex: 1 }}>
                        <strong>Discrepancy Already Adjusted:</strong>
                        <div>{alreadyAdjustedError}</div>
                    </div>
                    {urlSession && (
                        <button
                            type="button"
                            className="adj-btn adj-btn--secondary adj-btn--sm"
                            onClick={() => navigate(`/inventory/stock-difference?session=${encodeURIComponent(urlSession)}`)}
                        >
                            Back to Stock Difference
                        </button>
                    )}
                </div>
            )}

            {/* ── Stock Conflict Error Banner ── */}
            {stockCheckConflict && (
                <div className="adj-alert adj-alert--error" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                    <div style={{ flex: 1 }}>
                        <strong>Stale Discrepancy / Stock Conflict:</strong>
                        <div>{stockCheckConflict}</div>
                    </div>
                    {urlSession && (
                        <button
                            type="button"
                            className="adj-btn adj-btn--secondary adj-btn--sm"
                            onClick={() => navigate(`/inventory/stock-difference?session=${encodeURIComponent(urlSession)}`)}
                        >
                            Return to Revalidate
                        </button>
                    )}
                </div>
            )}

            {/* ── Manual Switch Warning Banner ── */}
            {manualSwitchWarning && (
                <div className="adj-alert adj-alert--warning" role="alert">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                    <div style={{ flex: 1 }}>
                        <strong>⚠️ Discrepancy Unresolved Warning:</strong>
                        <div>{manualSwitchWarning}</div>
                    </div>
                    <button
                        type="button"
                        className="adj-alert__close"
                        onClick={() => setManualSwitchWarning('')}
                        title="Dismiss warning"
                    >
                        ×
                    </button>
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
                        {urlSession && (
                            <button
                                type="button"
                                className="adj-btn adj-btn--secondary"
                                onClick={() => navigate(`/inventory/stock-difference?session=${encodeURIComponent(urlSession)}`)}
                            >
                                Back to Stock Difference
                            </button>
                        )}
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
                                {urlSession || linkedStockCheck?.checkNumber ? `Session: ${urlSession || linkedStockCheck?.checkNumber} • ` : ''}Item #{stockCheckId}
                            </span>
                        )}
                    </div>

                    {/* Search Method Switcher or Stock Check Locked Banner */}
                    <div className="adj-search-wrap">
                        {isStockCheckLocked ? (
                            <div className="adj-lock-banner">
                                <div className="adj-lock-banner__body">
                                    <span className="adj-lock-banner__title">🔒 Locked to Audited Stock Discrepancy</span>
                                    <p className="adj-lock-banner__desc">
                                        Session: <strong>{urlSession || linkedStockCheck?.checkNumber || '—'}</strong> • Item ID: <strong>#{stockCheckId}</strong>
                                        <br />
                                        Adjusting audited variance for <strong>{selectedProduct?.productName || 'the selected product'}</strong>.
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    id="adj-switch-manual-btn"
                                    className="adj-btn adj-btn--secondary adj-btn--sm"
                                    onClick={handleSwitchToManual}
                                    title="Unlink from Stock Check and switch to manual adjustment mode"
                                >
                                    Switch to Manual Adjustment
                                </button>
                            </div>
                        ) : (
                            <>
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
                            </>
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
                                    <span className="adj-detail-label">Expiry Tracking</span>
                                    <span className="adj-detail-val">
                                        {selectedProduct.trackExpiry ? (
                                            <span style={{ color: '#0284c7', fontWeight: 600 }}>Enabled (FEFO Batches)</span>
                                        ) : (
                                            <span style={{ color: '#64748b' }}>Disabled (Direct Stock)</span>
                                        )}
                                    </span>
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
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                            <label className="adj-label" style={{ margin: 0 }}>
                                Adjustment Type <span className="adj-req">*</span>
                            </label>
                            {isStockCheckLocked && (
                                <span className="adj-lock-tag" title="Locked to audited discrepancy">
                                    🔒 Locked ({adjustmentType})
                                </span>
                            )}
                        </div>
                        <div className="adj-type-toggle">
                            <button
                                type="button"
                                className={`adj-type-btn adj-type-btn--inc ${adjustmentType === 'INCREASE' ? 'adj-type-btn--active' : ''} ${isStockCheckLocked ? 'adj-type-btn--locked' : ''}`}
                                onClick={() => {
                                    if (!isStockCheckLocked && adjustmentType !== 'INCREASE') {
                                        setAdjustmentType('INCREASE');
                                        setGroupedAllocations({});
                                        setDetailAllocations({});
                                        setFieldErrors(prev => ({ ...prev, batchAllocation: null }));
                                    }
                                }}
                                disabled={isStockCheckLocked}
                                title={isStockCheckLocked ? "Locked to audited Stock Check discrepancy" : "Increase stock"}
                            >
                                <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
                                </svg>
                                Increase (+)
                            </button>
                            <button
                                type="button"
                                className={`adj-type-btn adj-type-btn--dec ${adjustmentType === 'DECREASE' ? 'adj-type-btn--active' : ''} ${isStockCheckLocked ? 'adj-type-btn--locked' : ''}`}
                                onClick={() => {
                                    if (!isStockCheckLocked && adjustmentType !== 'DECREASE') {
                                        setAdjustmentType('DECREASE');
                                        setGroupedAllocations({});
                                        setDetailAllocations({});
                                        setFieldErrors(prev => ({ ...prev, batchAllocation: null }));
                                    }
                                }}
                                disabled={isStockCheckLocked}
                                title={isStockCheckLocked ? "Locked to audited Stock Check discrepancy" : "Decrease stock"}
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
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                            <label htmlFor="adj-qty-input" className="adj-label" style={{ margin: 0 }}>
                                Adjustment Quantity <span className="adj-req">*</span>
                                {!isStockCheckLocked && (
                                    <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '6px' }}>
                                        (Whole positive integer)
                                    </span>
                                )}
                            </label>
                            {isStockCheckLocked && (
                                <span className="adj-lock-tag" title="Locked to audited discrepancy">
                                    🔒 Locked ({quantityInput} {selectedProduct?.unit || 'items'})
                                </span>
                            )}
                        </div>
                        <input
                            type="number"
                            id="adj-qty-input"
                            min="1"
                            step="1"
                            className={`adj-input ${fieldErrors.quantity ? 'adj-input--error' : ''} ${isStockCheckLocked ? 'adj-input--locked' : ''}`}
                            placeholder="e.g. 5"
                            value={quantityInput}
                            onChange={(e) => {
                                if (!isStockCheckLocked) {
                                    setQuantityInput(e.target.value);
                                    setFieldErrors(prev => ({ ...prev, quantity: null }));
                                }
                            }}
                            readOnly={isStockCheckLocked}
                            disabled={isStockCheckLocked}
                        />
                        {isStockCheckLocked && (
                            <span className="adj-lock-hint">
                                🔒 Quantity is locked to the audited variance from Stock Check #{stockCheckId}.
                            </span>
                        )}
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

                    {/* Batch Allocation Section (UC33) - Shown when product tracks expiry */}
                    {selectedProduct && selectedProduct.trackExpiry && (
                        <div className="adj-batch-section">
                            <div className="adj-batch-header">
                                <div>
                                    <div className="adj-label" style={{ margin: 0 }}>
                                        Batch Allocation <span className="adj-req">*</span>
                                    </div>
                                    <span className="adj-batch-subtitle">
                                        {adjustmentType === 'DECREASE'
                                            ? 'Allocate decrease across physical batches (deducted from underlying receipts using earliest receipt date first).'
                                            : 'Select physical batch and exact receipt detail to restore quantity to (preserves unit cost and receipt history).'}
                                    </span>
                                </div>
                                {isValidPositiveInt && (
                                    <span className={`adj-alloc-badge ${totalBatchAllocated === parsedQty && !hasOverDeduction ? 'adj-alloc-badge--matched' : 'adj-alloc-badge--pending'}`}>
                                        {totalBatchAllocated === parsedQty && !hasOverDeduction
                                            ? '✓ Allocation Matched'
                                            : `Pending: ${formatQty(Math.abs(remainingToAllocate))} ${remainingToAllocate > 0 ? 'remaining' : 'over'}`}
                                    </span>
                                )}
                            </div>

                            {/* Live Allocation Summary Bar */}
                            {isValidPositiveInt && (
                                <div className="adj-alloc-summary-bar">
                                    <div className="adj-alloc-summary-item">
                                        <span className="adj-alloc-summary-lbl">Required:</span>
                                        <span className="adj-alloc-summary-val">{formatQty(parsedQty)}</span>
                                    </div>
                                    <span className="adj-alloc-summary-divider">|</span>
                                    <div className="adj-alloc-summary-item">
                                        <span className="adj-alloc-summary-lbl">Allocated:</span>
                                        <span className="adj-alloc-summary-val" style={{ color: totalBatchAllocated === parsedQty ? '#16a34a' : '#2563eb' }}>
                                            {formatQty(totalBatchAllocated)}
                                        </span>
                                    </div>
                                    <span className="adj-alloc-summary-divider">|</span>
                                    <div className="adj-alloc-summary-item">
                                        <span className="adj-alloc-summary-lbl">Remaining:</span>
                                        <span className="adj-alloc-summary-val" style={{ color: remainingToAllocate === 0 ? '#16a34a' : remainingToAllocate > 0 ? '#b45309' : '#dc2626' }}>
                                            {formatQty(remainingToAllocate)}
                                        </span>
                                    </div>
                                </div>
                            )}

                            {loadingBatches ? (
                                <div className="adj-batch-loading">Loading confirmed physical batches…</div>
                            ) : batches.length === 0 ? (
                                <div className="adj-batch-empty">
                                    {adjustmentType === 'INCREASE'
                                        ? 'No confirmed batches found. In accordance with inventory schema rules, new batch stock must be registered via UC36 (Stock In / Stock Receipt).'
                                        : 'No confirmed batches found for this product. Cannot deduct stock from unrecorded batches.'}
                                </div>
                            ) : (
                                <div className="adj-batch-table-wrap">
                                    <table className="adj-batch-table">
                                        <thead>
                                            <tr>
                                                <th>Batch No.</th>
                                                <th>Expiry Date</th>
                                                <th style={{ textAlign: 'right' }}>
                                                    {adjustmentType === 'INCREASE' ? 'Current Batch Qty' : 'Available'}
                                                </th>
                                                <th style={{ textAlign: 'center' }}>Source Receipts</th>
                                                <th style={{ textAlign: 'right', width: '150px' }}>Adjust Qty</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {batches.map(g => {
                                                const key = getGroupKey(g);
                                                const isExpanded = Boolean(expandedBatches[key]);
                                                const groupVal = groupedAllocations[key] || '';
                                                const groupAllocated = Number(groupVal) > 0;
                                                const isDepleted = Number(g.totalRemainingQuantity || 0) <= 0;
                                                const isOver = adjustmentType === 'DECREASE' && Number(groupVal) > (g.totalRemainingQuantity || 0);

                                                // Sum of detail allocations for this physical batch (used for INCREASE)
                                                const detailAllocSum = (g.details || []).reduce(
                                                    (sum, d) => sum + (Number(detailAllocations[d.stockReceiptItemId]) || 0),
                                                    0
                                                );

                                                // Filter source receipt details according to UX requirements:
                                                // - In DECREASE mode: show only remainingQuantity > 0 by default, toggleable to show empty receipts
                                                // - In INCREASE mode: preserve all receipt details including zero-remaining for selection
                                                const allDetails = g.details || [];
                                                const emptyReceiptsCount = allDetails.filter(d => Number(d.remainingQuantity || 0) <= 0).length;
                                                const shouldShowEmpty = adjustmentType === 'INCREASE' || Boolean(showEmptyReceipts[key]);
                                                const filteredDetails = shouldShowEmpty ? allDetails : allDetails.filter(d => Number(d.remainingQuantity || 0) > 0);

                                                // Client-side pagination/limiting for batches with many receipt details
                                                const currentLimit = batchDetailLimits[key] || INITIAL_DETAIL_LIMIT;
                                                const visibleDetails = filteredDetails.slice(0, currentLimit);
                                                const hasMore = filteredDetails.length > currentLimit;

                                                return (
                                                    <Fragment key={key}>
                                                        <tr
                                                            className={`${groupAllocated || detailAllocSum > 0 ? 'adj-batch-row--selected' : ''} ${isOver ? 'adj-batch-row--error' : ''} ${isDepleted && adjustmentType === 'DECREASE' ? 'adj-batch-row--depleted' : ''}`}
                                                        >
                                                            <td>
                                                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                                    <button
                                                                        type="button"
                                                                        className={`adj-batch-expand-btn ${isExpanded ? 'adj-batch-expand-btn--active' : ''}`}
                                                                        onClick={() => toggleBatchExpand(key)}
                                                                        title={isExpanded ? 'Collapse underlying receipt details' : 'Expand underlying receipt details'}
                                                                        aria-expanded={isExpanded}
                                                                    >
                                                                        {isExpanded ? '▼' : '▶'}
                                                                    </button>
                                                                    <span className="adj-batch-num">{g.batchNumber}</span>
                                                                    {g.expired && (
                                                                        <span className="adj-batch-expired-tag" title="Expired batch. Can be selected for disposal/loss adjustments.">
                                                                            Expired
                                                                        </span>
                                                                    )}
                                                                    {g.conflictingDates && (
                                                                        <span className="adj-batch-conflict-tag" title="Inconsistency detected: Multiple confirmed receipts for this batch number have conflicting expiration dates in history. Kept separate for safety.">
                                                                            ⚠ Date Conflict
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </td>
                                                            <td>{formatDate(g.expirationDate)}</td>
                                                            <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                                                                {formatQty(g.totalRemainingQuantity)} {selectedProduct.unit}
                                                                {isDepleted && adjustmentType === 'DECREASE' && (
                                                                    <span className="adj-batch-depleted-tag" style={{ marginLeft: '4px' }}>
                                                                        0 Avail
                                                                    </span>
                                                                )}
                                                            </td>
                                                            <td style={{ textAlign: 'center' }}>
                                                                <button
                                                                    type="button"
                                                                    className="adj-batch-source-pill"
                                                                    onClick={() => toggleBatchExpand(key)}
                                                                    title="Click to view underlying receipt details"
                                                                >
                                                                    {g.receiptCount} {g.receiptCount === 1 ? 'receipt' : 'receipts'}
                                                                    <span style={{ fontSize: '10px' }}>{isExpanded ? '▲' : '▼'}</span>
                                                                </button>
                                                            </td>
                                                            <td style={{ textAlign: 'right' }}>
                                                                {adjustmentType === 'DECREASE' ? (
                                                                    <>
                                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px', justifyContent: 'flex-end' }}>
                                                                            <input
                                                                                type="number"
                                                                                min="0"
                                                                                max={g.totalRemainingQuantity}
                                                                                step="1"
                                                                                className={`adj-input adj-batch-input ${isOver ? 'adj-input--error' : ''} ${isDepleted ? 'adj-input--locked' : ''}`}
                                                                                placeholder="0"
                                                                                value={isDepleted ? '' : groupVal}
                                                                                onChange={(e) => handleGroupAllocationChange(key, e.target.value)}
                                                                                disabled={isDepleted}
                                                                                title={isDepleted ? "This physical batch has zero available remaining stock to decrease" : undefined}
                                                                            />
                                                                            {isValidPositiveInt && !isDepleted && (
                                                                                <button
                                                                                    type="button"
                                                                                    className="adj-btn adj-btn--secondary adj-btn--sm"
                                                                                    style={{ padding: '2px 6px', fontSize: '11px', height: '28px' }}
                                                                                    onClick={() => handleGroupQuickFill(g)}
                                                                                    title="Allocate up to batch available quantity"
                                                                                >
                                                                                    Fill
                                                                                </button>
                                                                            )}
                                                                        </div>
                                                                        {isOver && (
                                                                            <div style={{ color: '#dc2626', fontSize: '11px', marginTop: '2px' }}>
                                                                                Exceeds available ({formatQty(g.totalRemainingQuantity)})
                                                                            </div>
                                                                        )}
                                                                    </>
                                                                ) : (
                                                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'flex-end' }}>
                                                                        {detailAllocSum > 0 && (
                                                                            <span style={{ color: '#16a34a', fontWeight: 600, fontSize: '12px' }}>
                                                                                +{formatQty(detailAllocSum)}
                                                                            </span>
                                                                        )}
                                                                        <button
                                                                            type="button"
                                                                            className={`adj-btn adj-btn--sm ${detailAllocSum > 0 ? 'adj-btn--secondary' : 'adj-btn--ghost'}`}
                                                                            style={{ padding: '2px 8px', fontSize: '11px', height: '28px' }}
                                                                            onClick={() => toggleBatchExpand(key)}
                                                                            title="Expand batch to select exact source receipt details to restore"
                                                                        >
                                                                            {isExpanded ? 'Close Details' : detailAllocSum > 0 ? 'Edit Details' : 'Select Receipt (+)'}
                                                                        </button>
                                                                    </div>
                                                                )}
                                                            </td>
                                                        </tr>

                                                        {/* Expanded Underlying Receipt Details Row */}
                                                        {isExpanded && (
                                                            <tr className="adj-batch-subrow">
                                                                <td colSpan="5">
                                                                    <div className="adj-batch-subtable-wrap">
                                                                        <div className="adj-batch-subtable-title">
                                                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                                                                                <span>
                                                                                    Source Receipts for Batch <strong>{g.batchNumber}</strong>
                                                                                    {g.expirationDate ? ` (Exp: ${formatDate(g.expirationDate)})` : ''}
                                                                                </span>
                                                                                {adjustmentType === 'DECREASE' && emptyReceiptsCount > 0 && (
                                                                                    <button
                                                                                        type="button"
                                                                                        className="adj-toggle-empty-btn"
                                                                                        onClick={() => setShowEmptyReceipts(prev => ({ ...prev, [key]: !prev[key] }))}
                                                                                        title={showEmptyReceipts[key] ? 'Hide empty (0 remaining) receipts' : 'Reveal empty (0 remaining) receipts for audit inspection'}
                                                                                    >
                                                                                        {showEmptyReceipts[key]
                                                                                            ? `Hide empty receipts (${emptyReceiptsCount})`
                                                                                            : `Show empty receipts (${emptyReceiptsCount})`}
                                                                                    </button>
                                                                                )}
                                                                            </div>
                                                                            <span style={{ fontWeight: 400, textTransform: 'none', color: '#64748b' }}>
                                                                                {adjustmentType === 'INCREASE'
                                                                                    ? 'Enter restoration quantity into specific receipt detail(s) below (including 0-qty receipts):'
                                                                                    : 'Underlying receipts are deducted in FIFO order (earliest received first):'}
                                                                            </span>
                                                                        </div>

                                                                        <table className="adj-batch-subtable">
                                                                            <thead>
                                                                                <tr>
                                                                                    <th>Receipt Ref</th>
                                                                                    <th>Detail ID</th>
                                                                                    <th style={{ textAlign: 'right' }}>Remaining Qty</th>
                                                                                    <th style={{ textAlign: 'right' }}>Received Qty</th>
                                                                                    <th style={{ textAlign: 'right' }}>Unit Cost</th>
                                                                                    {adjustmentType === 'INCREASE' ? (
                                                                                        <th style={{ textAlign: 'right', width: '130px' }}>Restore Qty</th>
                                                                                    ) : (
                                                                                        <th style={{ textAlign: 'right' }}>Received Date</th>
                                                                                    )}
                                                                                </tr>
                                                                            </thead>
                                                                            <tbody>
                                                                                {filteredDetails.length === 0 ? (
                                                                                    <tr>
                                                                                        <td colSpan={6} className="adj-batch-subtable-empty">
                                                                                            No active receipts with remaining stock.
                                                                                            {emptyReceiptsCount > 0 && (
                                                                                                <span>
                                                                                                    {' '}
                                                                                                    <button
                                                                                                        type="button"
                                                                                                        className="adj-inline-link-btn"
                                                                                                        onClick={() => setShowEmptyReceipts(prev => ({ ...prev, [key]: true }))}
                                                                                                    >
                                                                                                        Show {emptyReceiptsCount} empty {emptyReceiptsCount === 1 ? 'receipt' : 'receipts'}
                                                                                                    </button>
                                                                                                </span>
                                                                                            )}
                                                                                        </td>
                                                                                    </tr>
                                                                                ) : (
                                                                                    visibleDetails.map(d => {
                                                                                        const dVal = detailAllocations[d.stockReceiptItemId] || '';
                                                                                        const isDetailEmpty = Number(d.remainingQuantity || 0) <= 0;
                                                                                        return (
                                                                                            <tr key={d.stockReceiptItemId}>
                                                                                                <td>
                                                                                                    <span className="adj-batch-receipt-ref">
                                                                                                        {d.receiptNumber || (d.stockReceiptId ? `Receipt #${d.stockReceiptId}` : '—')}
                                                                                                    </span>
                                                                                                </td>
                                                                                                <td>
                                                                                                    <span style={{ fontFamily: 'monospace', color: '#64748b' }}>
                                                                                                        #{d.stockReceiptItemId}
                                                                                                    </span>
                                                                                                </td>
                                                                                                <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                                                                                                    <span style={{ color: isDetailEmpty ? '#94a3b8' : 'inherit' }}>
                                                                                                        {formatQty(d.remainingQuantity)} {selectedProduct.unit}
                                                                                                    </span>
                                                                                                    {isDetailEmpty && (
                                                                                                        <span className="adj-batch-depleted-tag" style={{ marginLeft: '4px' }}>
                                                                                                            Empty
                                                                                                        </span>
                                                                                                    )}
                                                                                                </td>
                                                                                                <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: '#64748b' }}>
                                                                                                    {formatQty(d.receivedQuantity)} {selectedProduct.unit}
                                                                                                </td>
                                                                                                <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                                                                                                    {formatCurrency(d.unitCost)}
                                                                                                </td>
                                                                                                {adjustmentType === 'INCREASE' ? (
                                                                                                    <td style={{ textAlign: 'right' }}>
                                                                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px', justifyContent: 'flex-end' }}>
                                                                                                            <input
                                                                                                                type="number"
                                                                                                                min="0"
                                                                                                                step="1"
                                                                                                                className="adj-input adj-batch-input"
                                                                                                                placeholder="0"
                                                                                                                value={dVal}
                                                                                                                onChange={(e) => handleDetailAllocationChange(d.stockReceiptItemId, e.target.value)}
                                                                                                            />
                                                                                                            {isValidPositiveInt && (
                                                                                                                <button
                                                                                                                    type="button"
                                                                                                                    className="adj-btn adj-btn--secondary adj-btn--sm"
                                                                                                                    style={{ padding: '2px 6px', fontSize: '11px', height: '28px' }}
                                                                                                                    onClick={() => handleDetailQuickFill(d)}
                                                                                                                    title="Allocate remaining quantity to this receipt"
                                                                                                                >
                                                                                                                    Fill
                                                                                                                </button>
                                                                                                            )}
                                                                                                        </div>
                                                                                                    </td>
                                                                                                ) : (
                                                                                                    <td style={{ textAlign: 'right', color: '#64748b', fontSize: '11px' }}>
                                                                                                        {formatDate(d.receivedAt)}
                                                                                                    </td>
                                                                                                )}
                                                                                            </tr>
                                                                                        );
                                                                                    })
                                                                                )}
                                                                            </tbody>
                                                                        </table>

                                                                        {/* Compact Pagination / Load More bar for large number of receipts */}
                                                                        {filteredDetails.length > INITIAL_DETAIL_LIMIT && (
                                                                            <div className="adj-batch-pagination">
                                                                                <span className="adj-batch-pagination-info">
                                                                                    Showing {visibleDetails.length} of {filteredDetails.length} source receipts
                                                                                </span>
                                                                                <div style={{ display: 'flex', gap: '6px' }}>
                                                                                    {hasMore && (
                                                                                        <button
                                                                                            type="button"
                                                                                            className="adj-btn adj-btn--secondary adj-btn--sm"
                                                                                            style={{ padding: '2px 8px', fontSize: '11px', height: '24px' }}
                                                                                            onClick={() => setBatchDetailLimits(prev => ({
                                                                                                ...prev,
                                                                                                [key]: (prev[key] || INITIAL_DETAIL_LIMIT) + 10
                                                                                            }))}
                                                                                        >
                                                                                            Load 10 more
                                                                                        </button>
                                                                                    )}
                                                                                    {hasMore && (
                                                                                        <button
                                                                                            type="button"
                                                                                            className="adj-btn adj-btn--ghost adj-btn--sm"
                                                                                            style={{ padding: '2px 8px', fontSize: '11px', height: '24px' }}
                                                                                            onClick={() => setBatchDetailLimits(prev => ({
                                                                                                ...prev,
                                                                                                [key]: filteredDetails.length
                                                                                            }))}
                                                                                        >
                                                                                            Show all ({filteredDetails.length})
                                                                                        </button>
                                                                                    )}
                                                                                    {currentLimit > INITIAL_DETAIL_LIMIT && (
                                                                                        <button
                                                                                            type="button"
                                                                                            className="adj-btn adj-btn--ghost adj-btn--sm"
                                                                                            style={{ padding: '2px 8px', fontSize: '11px', height: '24px' }}
                                                                                            onClick={() => setBatchDetailLimits(prev => ({
                                                                                                ...prev,
                                                                                                [key]: INITIAL_DETAIL_LIMIT
                                                                                            }))}
                                                                                        >
                                                                                            Show less
                                                                                        </button>
                                                                                    )}
                                                                                </div>
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                        )}
                                                    </Fragment>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            )}

                            {fieldErrors.batchAllocation && (
                                <span className="adj-field-error">{fieldErrors.batchAllocation}</span>
                            )}
                        </div>
                    )}

                    {/* Audit Session Reference */}
                    <div className="adj-form-group">
                        <label htmlFor="adj-session-ref" className="adj-label">
                            Audit Session Reference
                            <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '6px' }}>
                                {isStockCheckLocked ? '(Read-only reference to audit session item)' : '(Direct manual mode - no audit session linked)'}
                            </span>
                        </label>
                        <input
                            type="text"
                            id="adj-session-ref"
                            className={`adj-input ${isStockCheckLocked ? 'adj-input--locked' : ''}`}
                            placeholder="Direct manual adjustment (no audit session linked)"
                            value={stockCheckId ? `Session: ${urlSession || linkedStockCheck?.checkNumber || 'Audit'} • Item #${stockCheckId}` : ''}
                            readOnly
                            disabled
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

                            {requiresBatches && (
                                (adjustmentType === 'DECREASE' && Object.values(groupedAllocations).some(v => v > 0)) ||
                                (adjustmentType === 'INCREASE' && Object.values(detailAllocations).some(v => v > 0))
                            ) && (
                                <div className="adj-modal-batch-list">
                                    <div className="adj-modal-batch-title">Batch Allocations ({adjustmentType})</div>
                                    {adjustmentType === 'DECREASE' ? (
                                        batches
                                            .filter(g => (groupedAllocations[getGroupKey(g)] || 0) > 0)
                                            .map(g => {
                                                const key = getGroupKey(g);
                                                return (
                                                    <div key={key} className="adj-modal-batch-row">
                                                        <span>
                                                            <span className="adj-batch-num">{g.batchNumber}</span>
                                                            {g.expired ? ' (Expired)' : ''}
                                                            <span style={{ color: '#64748b', fontSize: '11px', marginLeft: '6px' }}>
                                                                Exp: {formatDate(g.expirationDate)} • {g.receiptCount} {g.receiptCount === 1 ? 'receipt' : 'receipts'}
                                                            </span>
                                                        </span>
                                                        <span style={{ fontWeight: 600, color: '#dc2626' }}>
                                                            −{formatQty(groupedAllocations[key])} {selectedProduct?.unit}
                                                        </span>
                                                    </div>
                                                );
                                            })
                                    ) : (
                                        Object.entries(detailAllocations)
                                            .filter(([, qty]) => Number(qty) > 0)
                                            .map(([itemId, qty]) => {
                                                let foundDetail = null;
                                                let foundGroup = null;
                                                for (const g of batches) {
                                                    const m = (g.details || []).find(d => String(d.stockReceiptItemId) === String(itemId));
                                                    if (m) {
                                                        foundDetail = m;
                                                        foundGroup = g;
                                                        break;
                                                    }
                                                }
                                                return (
                                                    <div key={itemId} className="adj-modal-batch-row">
                                                        <span>
                                                            <span className="adj-batch-num">{foundGroup?.batchNumber || '—'}</span>
                                                            <span style={{ color: '#64748b', fontSize: '11px', marginLeft: '6px' }}>
                                                                Receipt: {foundDetail?.receiptNumber || (foundDetail?.stockReceiptId ? `#${foundDetail.stockReceiptId}` : '—')}
                                                                {` • Detail #${itemId}`}
                                                                {foundDetail?.unitCost != null ? ` • Cost: ${formatCurrency(foundDetail.unitCost)}` : ''}
                                                            </span>
                                                        </span>
                                                        <span style={{ fontWeight: 600, color: '#16a34a' }}>
                                                            +{formatQty(qty)} {selectedProduct?.unit}
                                                        </span>
                                                    </div>
                                                );
                                            })
                                    )}
                                </div>
                            )}

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

