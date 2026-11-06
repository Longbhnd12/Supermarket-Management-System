/**
 * ProductListPage — UC09: View Product List
 * Displays the master product catalog with search, category & status filters,
 * stat summary cards, and paginated table (client-side pagination over full list).
 *
 * Role-based: Create button visible only to ADMIN / MANAGER.
 * Row click navigates to UC13 (Product Detail).
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import ProductService from '../../services/productService';
import CategoryService from '../../services/categoryService';
import authService from '../../services/authService';
import Pagination from '../../components/ui/Pagination';
import './ProductListPage.css';

// ─── Constants ────────────────────────────────────────────────────────────────

const PAGE_SIZE_OPTIONS = [10, 20, 50];
const ADMIN_ROLES = ['ADMIN', 'MANAGER'];

// ─── Helper: format price ─────────────────────────────────────────────────────

function formatPrice(val) {
    if (val === null || val === undefined) return '—';
    const n = parseFloat(val);
    if (isNaN(n)) return '—';
    return n.toLocaleString('vi-VN') + ' ₫';
}

// ─── Helper: format quantity ──────────────────────────────────────────────────

function formatQty(val) {
    if (val === null || val === undefined) return '—';
    const n = parseFloat(val);
    if (isNaN(n)) return '—';
    return Number.isInteger(n) ? n.toString() : n.toFixed(3).replace(/\.?0+$/, '');
}

// ─── Status Badge ─────────────────────────────────────────────────────────────

function ProductStatusBadge({ status }) {
    const map = {
        ACTIVE: { label: 'Active', cls: 'prd-badge prd-badge--active' },
        INACTIVE: { label: 'Inactive', cls: 'prd-badge prd-badge--inactive' },
    };
    const { label, cls } = map[status] || { label: status, cls: 'prd-badge prd-badge--inactive' };
    return <span className={cls}>{label}</span>;
}

// ─── Stock status helper ──────────────────────────────────────────────────────

function getStockStatus(product) {
    const qty = parseFloat(product.stockQuantity ?? 0);
    const min = parseFloat(product.minimumStockLevel ?? 0);
    if (qty <= 0) return 'OUT_OF_STOCK';
    if (min > 0 && qty <= min) return 'LOW_STOCK';
    return 'IN_STOCK';
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function ProductListPage() {
    const navigate = useNavigate();

    // ── Auth ────────────────────────────────────────────────────────────────
    const [role, setRole] = useState(null);

    // ── Raw data ────────────────────────────────────────────────────────────
    const [allProducts, setAllProducts] = useState([]);
    const [categories, setCategories] = useState([]);

    // ── Filters ─────────────────────────────────────────────────────────────
    const [keyword, setKeyword] = useState('');
    const [debouncedKeyword, setDebouncedKeyword] = useState('');
    const [categoryId, setCategoryId] = useState('');
    const [status, setStatus] = useState('');

    // ── Pagination ───────────────────────────────────────────────────────────
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(10);

    // ── UI State ─────────────────────────────────────────────────────────────
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const debounceTimer = useRef(null);

    // ── Fetch role on mount ──────────────────────────────────────────────────
    // authService.getMe() already unwraps .data, so the shape is ApiResponse directly
    useEffect(() => {
        authService.getMe()
            .then(res => {
                // authService.getMe() = (await API.get('/auth/me')).data
                // => shape: { success, message, data: { userId, role, ... } }
                const role = res?.data?.role ?? null;
                setRole(role);
            })
            .catch(() => setRole(null));
    }, []);

    // ── Fetch categories on mount ────────────────────────────────────────────
    useEffect(() => {
        CategoryService.getAll()
            .then(res => setCategories(res.data?.data || []))
            .catch(() => setCategories([]));
    }, []);

    // ── Debounce keyword ─────────────────────────────────────────────────────
    useEffect(() => {
        clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => {
            setDebouncedKeyword(keyword);
            setPage(0);
        }, 400);
        return () => clearTimeout(debounceTimer.current);
    }, [keyword]);

    // ── Reset page when filters change ───────────────────────────────────────
    useEffect(() => {
        setPage(0);
    }, [debouncedKeyword, categoryId, status]);

    // ── Fetch products (loads catalog, filtered client-side) ─────────────────
    const fetchProducts = useCallback(() => {
        setLoading(true);
        setError(null);

        ProductService.getAll()
            .then(res => {
                setAllProducts(res.data?.data || []);
            })
            .catch(err => {
                setError(err.response?.data?.message || 'Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
                setAllProducts([]);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchProducts();
    }, [fetchProducts]);

    // ── Client-side multi-criteria filter (keyword + category + status) ───────
    const filteredProducts = useMemo(() => {
        return allProducts.filter(p => {
            // Keyword filter
            if (debouncedKeyword) {
                const q = debouncedKeyword.toLowerCase();
                const matches =
                    (p.productName && p.productName.toLowerCase().includes(q)) ||
                    (p.productCode && p.productCode.toLowerCase().includes(q)) ||
                    (p.barcode && p.barcode.toLowerCase().includes(q));
                if (!matches) return false;
            }
            // Category filter
            if (categoryId && String(p.categoryId) !== String(categoryId)) {
                return false;
            }
            // Status filter
            if (status && p.status !== status) {
                return false;
            }
            return true;
        });
    }, [allProducts, debouncedKeyword, categoryId, status]);

    // ── Pagination slice ─────────────────────────────────────────────────────
    const totalElements = filteredProducts.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));
    const safePage = Math.min(page, totalPages - 1);
    const pagedProducts = filteredProducts.slice(safePage * size, safePage * size + size);

    // ── Stat cards ───────────────────────────────────────────────────────────
    const stats = useMemo(() => {
        const total = allProducts.length;
        const inStock = allProducts.filter(p => getStockStatus(p) === 'IN_STOCK').length;
        const lowStock = allProducts.filter(p => getStockStatus(p) === 'LOW_STOCK').length;
        const outOrInactive = allProducts.filter(p =>
            getStockStatus(p) === 'OUT_OF_STOCK' || p.status === 'INACTIVE'
        ).length;
        return { total, inStock, lowStock, outOrInactive };
    }, [allProducts]);

    // ── Active filter tags ───────────────────────────────────────────────────
    const activeFilters = [];
    if (debouncedKeyword) activeFilters.push({ key: 'search', label: `"${debouncedKeyword}"`, clear: () => { setKeyword(''); setDebouncedKeyword(''); } });
    if (categoryId) {
        const cat = categories.find(c => String(c.categoryId) === String(categoryId));
        activeFilters.push({ key: 'category', label: cat?.categoryName || `Category #${categoryId}`, clear: () => setCategoryId('') });
    }
    if (status) activeFilters.push({ key: 'status', label: status === 'ACTIVE' ? 'Active' : 'Inactive', clear: () => setStatus('') });

    const handleResetFilters = () => {
        setKeyword('');
        setDebouncedKeyword('');
        setCategoryId('');
        setStatus('');
        setPage(0);
    };

    const canCreate = ADMIN_ROLES.includes(role);

    // ─────────────────────────────────────────────────────────────────────────
    return (
        <div className="prd-page">

            {/* ── Page Header ─────────────────────────────────────────────── */}
            <div className="prd-header">
                <div className="prd-header-left">
                    <div className="prd-breadcrumb">
                        <span>Catalog &amp; Inventory</span>
                        <span className="prd-breadcrumb-sep">/</span>
                        <span className="prd-breadcrumb-current">Products</span>
                    </div>
                    <h1 className="prd-title">
                        Product Catalog &amp; Master SKUs
                    </h1>
                    <p className="prd-subtitle">
                        Browse, filter, and audit supermarket SKUs, barcode mappings, stock levels, and active retail prices.
                    </p>
                </div>

                <div className="prd-header-actions">
                    {/* Scan Barcode */}
                    <button
                        className="prd-btn prd-btn--secondary"
                        title="Scan barcode to find product"
                        onClick={() => navigate('/products?scan=1')}
                    >
                        <BarcodeIcon />
                        Scan Barcode
                    </button>

                    {/* Export Catalog */}
                    <button
                        className="prd-btn prd-btn--secondary"
                        title="Export product catalog as CSV"
                        onClick={() => alert('Export feature coming soon.')}
                    >
                        <ExportIcon />
                        Export Catalog
                    </button>

                    {/* Create New Product — Admin / Manager only */}
                    {canCreate && (
                        <button
                            id="prd-create-btn"
                            className="prd-btn prd-btn--primary"
                            onClick={() => navigate('/products/create')}
                            title="Create a new product (UC10)"
                        >
                            <PlusIcon />
                            Create New Product
                        </button>
                    )}
                </div>
            </div>

            {/* ── Stat Cards ──────────────────────────────────────────────── */}
            <div className="prd-stats-row">
                <StatCard
                    icon={<SkuIcon />}
                    label="TOTAL MASTER SKUs"
                    value={`${stats.total.toLocaleString()} SKUs`}
                    color="blue"
                />
                <StatCard
                    icon={<CheckCircleIcon />}
                    label="IN STOCK / HEALTHY"
                    value={`${stats.inStock.toLocaleString()} / ${stats.total.toLocaleString()}`}
                    sub={stats.total > 0 ? `${((stats.inStock / stats.total) * 100).toFixed(1)}% compliance` : ''}
                    color="green"
                />
                <StatCard
                    icon={<AlertIcon />}
                    label="LOW STOCK ALERT"
                    value={`${stats.lowStock} SKUs flagged`}
                    sub="< min threshold"
                    color="orange"
                />
                <StatCard
                    icon={<BlockIcon />}
                    label="OUT OF STOCK / INACTIVE"
                    value={`${stats.outOrInactive} Depleted SKUs`}
                    color="red"
                />
            </div>

            {/* ── Filter Bar ──────────────────────────────────────────────── */}
            <div className="prd-filter-bar">
                {/* Search */}
                <div className="prd-search-wrap">
                    <SearchIcon className="prd-search-icon" />
                    <input
                        id="prd-search-input"
                        type="text"
                        className="prd-search-input"
                        placeholder="Search by Product name, SKU code…"
                        value={keyword}
                        onChange={e => setKeyword(e.target.value)}
                        maxLength={100}
                    />
                    {keyword && (
                        <button
                            className="prd-search-clear"
                            onClick={() => setKeyword('')}
                            title="Clear search"
                        >×</button>
                    )}
                </div>

                {/* Category filter */}
                <select
                    id="prd-category-filter"
                    className="prd-select"
                    value={categoryId}
                    onChange={e => setCategoryId(e.target.value)}
                >
                    <option value="">All Categories ({allProducts.length} SKUs)</option>
                    {categories.map(cat => (
                        <option key={cat.categoryId} value={cat.categoryId}>
                            {cat.categoryName}
                        </option>
                    ))}
                </select>

                {/* Status filter */}
                <select
                    id="prd-status-filter"
                    className="prd-select"
                    value={status}
                    onChange={e => setStatus(e.target.value)}
                >
                    <option value="">All Statuses</option>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                </select>

                {/* Reset */}
                <button
                    id="prd-reset-btn"
                    className="prd-btn prd-btn--ghost"
                    onClick={handleResetFilters}
                    title="Reset all filters"
                >
                    <ResetIcon />
                    Reset Filters
                </button>
            </div>

            {/* ── Active Filter Tags ───────────────────────────────────────── */}
            {activeFilters.length > 0 && (
                <div className="prd-active-filters">
                    <span className="prd-active-filters-label">ACTIVE FILTER:</span>
                    {activeFilters.map(f => (
                        <span key={f.key} className="prd-filter-tag">
                            {f.label}
                            <button onClick={f.clear} className="prd-filter-tag-remove" title="Remove filter">×</button>
                        </span>
                    ))}
                </div>
            )}

            {/* ── Main Table Card ──────────────────────────────────────────── */}
            <div className="prd-card">
                {/* Table info row */}
                <div className="prd-table-info-row">
                    <span className="prd-showing-text">
                        {loading
                            ? 'Loading…'
                            : `Showing ${Math.min(safePage * size + 1, totalElements)}–${Math.min((safePage + 1) * size, totalElements)} of ${totalElements.toLocaleString()} products`}
                    </span>
                </div>

                {/* Table content */}
                {loading ? (
                    <div className="prd-state-box">
                        <span className="prd-spinner" />
                        <p>Loading products…</p>
                    </div>
                ) : error ? (
                    <div className="prd-state-box prd-state-box--error">
                        <ErrorIcon />
                        <p>{error}</p>
                        <button className="prd-btn prd-btn--secondary" onClick={fetchProducts}>Thử lại</button>
                    </div>
                ) : pagedProducts.length === 0 ? (
                    <div className="prd-state-box">
                        <BoxIcon />
                        <p className="prd-empty-title">Không tìm thấy sản phẩm nào</p>
                        <p className="prd-empty-sub">
                            {activeFilters.length > 0
                                ? 'Thử điều chỉnh tìm kiếm hoặc bộ lọc.'
                                : 'Chưa có sản phẩm nào trong hệ thống.'}
                        </p>
                    </div>
                ) : (
                    <div className="prd-table-wrap">
                        <table className="prd-table" id="product-table">
                            <thead>
                                <tr>
                                    <th className="prd-th--check">
                                        <input type="checkbox" aria-label="Select all" />
                                    </th>
                                    <th>SKU CODE</th>
                                    <th>EAN-13 BARCODE</th>
                                    <th className="prd-th--name">PRODUCT NAME &amp; SPEC</th>
                                    <th>CATEGORY</th>
                                    <th className="prd-th--num">PRICE (₫)</th>
                                    <th className="prd-th--num">STOCK</th>
                                    <th>STATUS</th>
                                </tr>
                            </thead>
                            <tbody>
                                {pagedProducts.map(product => {
                                    const stockSt = getStockStatus(product);
                                    return (
                                        <tr
                                            key={product.productId}
                                            className={`prd-row${stockSt === 'LOW_STOCK' ? ' prd-row--low-stock' : stockSt === 'OUT_OF_STOCK' ? ' prd-row--out' : ''}`}
                                            onClick={() => navigate(`/products/${product.productId}`)}
                                            title="Click để xem chi tiết sản phẩm"
                                        >
                                            <td className="prd-td--check" onClick={e => e.stopPropagation()}>
                                                <input type="checkbox" aria-label={`Select ${product.productName}`} />
                                            </td>
                                            <td className="prd-td--code">
                                                <Link
                                                    to={`/products/${product.productId}`}
                                                    className="prd-sku-link"
                                                    onClick={e => e.stopPropagation()}
                                                    title={`View details for ${product.productCode}`}
                                                >
                                                    {product.productCode || '—'}
                                                </Link>
                                            </td>
                                            <td className="prd-td--barcode">
                                                {product.barcode
                                                    ? <><BarcodeLineIcon />{product.barcode}</>
                                                    : <span className="prd-no-barcode">—</span>}
                                            </td>
                                            <td className="prd-td--name">
                                                <span className="prd-product-name">{product.productName}</span>
                                                {product.unit && (
                                                    <span className="prd-product-spec">Unit: {product.unit}</span>
                                                )}
                                            </td>
                                            <td>
                                                <span className="prd-category-chip">
                                                    {product.categoryName || '—'}
                                                </span>
                                            </td>
                                            <td className="prd-td--num prd-td--price">
                                                {formatPrice(product.sellingPrice)}
                                            </td>
                                            <td className="prd-td--num">
                                                <span className={`prd-stock-num prd-stock-num--${stockSt.toLowerCase().replace('_', '-')}`}>
                                                    {formatQty(product.stockQuantity)}
                                                </span>
                                            </td>
                                            <td>
                                                <ProductStatusBadge status={product.status} />
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Pagination */}
                {!loading && !error && totalElements > 0 && (
                    <Pagination
                        idPrefix="prd"
                        page={safePage}
                        size={size}
                        totalElements={totalElements}
                        totalPages={totalPages}
                        pageSizeOptions={PAGE_SIZE_OPTIONS}
                        onPageChange={setPage}
                        onSizeChange={(newSize) => { setSize(newSize); setPage(0); }}
                        itemLabel="products"
                    />
                )}
            </div>
        </div>
    );
}

// ─── Stat Card Component ──────────────────────────────────────────────────────

function StatCard({ icon, label, value, sub, color }) {
    return (
        <div className={`prd-stat-card prd-stat-card--${color}`}>
            <div className="prd-stat-icon">{icon}</div>
            <div className="prd-stat-body">
                <span className="prd-stat-label">{label}</span>
                <span className="prd-stat-value">{value}</span>
                {sub && <span className="prd-stat-sub">{sub}</span>}
            </div>
        </div>
    );
}

// ─── Inline SVG Icons ─────────────────────────────────────────────────────────

function SearchIcon({ className }) {
    return (
        <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
    );
}

function BarcodeIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <path d="M3 5v14M7 5v14M11 5v14M17 5v14M21 5v14M13 5v14" />
        </svg>
    );
}

function BarcodeLineIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="14" height="14" style={{ marginRight: 5, opacity: 0.5 }}>
            <path d="M3 5v14M7 5v14M11 5v14M17 5v14M21 5v14M13 5v14" />
        </svg>
    );
}

function ExportIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
            <polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
        </svg>
    );
}

function PlusIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
    );
}

function ResetIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
            <path d="M3 3v5h5" />
        </svg>
    );
}

function SkuIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="22" height="22">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
            <path d="M16 3L12 7 8 3" />
        </svg>
    );
}

function CheckCircleIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="22" height="22">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
        </svg>
    );
}

function AlertIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="22" height="22">
            <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
            <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
    );
}

function BlockIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="22" height="22">
            <circle cx="12" cy="12" r="10" /><line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
        </svg>
    );
}

function ErrorIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="40" height="40" style={{ color: '#e53e3e' }}>
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
    );
}

function BoxIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="48" height="48" style={{ color: '#a0aec0' }}>
            <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" /><line x1="12" y1="22.08" x2="12" y2="12" />
        </svg>
    );
}
