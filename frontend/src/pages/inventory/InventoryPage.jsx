import { useState, useEffect, useCallback, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import InventoryService from '../../services/inventoryService';
import CategoryService from '../../services/categoryService';
import Pagination from '../../components/ui/Pagination';
import './InventoryPage.css';

// ─── Status badge helpers ────────────────────────────────────────────────────

const STATUS_LABELS = {
    IN_STOCK: 'In Stock',
    LOW_STOCK: 'Low Stock',
    OUT_OF_STOCK: 'Out of Stock',
    NEAR_EXPIRY: 'Near Expiry',
};

const STATUS_CLASS = {
    IN_STOCK: 'inv-badge inv-badge--in-stock',
    LOW_STOCK: 'inv-badge inv-badge--low-stock',
    OUT_OF_STOCK: 'inv-badge inv-badge--out-of-stock',
    NEAR_EXPIRY: 'inv-badge inv-badge--near-expiry',
};

function StatusBadge({ status }) {
    return (
        <span className={STATUS_CLASS[status] || 'inv-badge inv-badge--in-stock'}>
            {STATUS_LABELS[status] || status}
        </span>
    );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function InventoryPage() {
    const navigate = useNavigate();

    // Filters
    const [keyword, setKeyword] = useState('');
    const [categoryId, setCategoryId] = useState('');
    const [stockStatus, setStockStatus] = useState('');

    // Pagination
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);

    // Data
    const [inventory, setInventory] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [categories, setCategories] = useState([]);

    // UI state
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [exporting, setExporting] = useState(false);

    // Debounce keyword input
    const debounceTimer = useRef(null);
    const [debouncedKeyword, setDebouncedKeyword] = useState('');

    // Load categories once on mount
    useEffect(() => {
        CategoryService.getAll()
            .then(res => setCategories(res.data?.data || []))
            .catch(() => setCategories([]));
    }, []);

    // Debounce keyword changes
    useEffect(() => {
        clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => {
            setDebouncedKeyword(keyword);
            setPage(0);
        }, 400);
        return () => clearTimeout(debounceTimer.current);
    }, [keyword]);

    // Reset page when filters change
    useEffect(() => {
        setPage(0);
    }, [categoryId, stockStatus]);

    // Fetch inventory data
    const fetchInventory = useCallback(() => {
        setLoading(true);
        setError(null);

        const params = {
            page,
            size,
            ...(debouncedKeyword && { keyword: debouncedKeyword }),
            ...(categoryId && { categoryId }),
            ...(stockStatus && { stockStatus }),
        };

        InventoryService.getInventoryList(params)
            .then(res => {
                const data = res.data?.data;
                if (data) {
                    setInventory(data.content || []);
                    setTotalElements(data.totalElements || 0);
                    setTotalPages(data.totalPages || 0);
                }
            })
            .catch(err => {
                setError(err.response?.data?.message || 'Failed to load inventory. Please try again.');
                setInventory([]);
            })
            .finally(() => setLoading(false));
    }, [page, size, debouncedKeyword, categoryId, stockStatus]);

    useEffect(() => {
        fetchInventory();
    }, [fetchInventory]);

    // Export CSV
    const handleExportCsv = async () => {
        setExporting(true);
        try {
            const params = {
                ...(debouncedKeyword && { keyword: debouncedKeyword }),
                ...(categoryId && { categoryId }),
                ...(stockStatus && { stockStatus }),
            };
            const res = await InventoryService.exportInventoryCsv(params);
            const url = URL.createObjectURL(new Blob([res.data], { type: 'text/csv;charset=utf-8;' }));
            const a = document.createElement('a');
            a.href = url;
            a.download = 'inventory.csv';
            a.click();
            URL.revokeObjectURL(url);
        } catch {
            alert('Export failed. Please try again.');
        } finally {
            setExporting(false);
        }
    };

    const handleFilterReset = () => {
        setKeyword('');
        setDebouncedKeyword('');
        setCategoryId('');
        setStockStatus('');
        setPage(0);
    };

    return (
        <div className="inv-page">
            {/* Page header */}
            <div className="inv-header">
                <div className="inv-header-left">
                    <h1 className="inv-title">Inventory List</h1>
                    <p className="inv-subtitle">
                        {totalElements > 0
                            ? `${totalElements.toLocaleString()} products in inventory`
                            : 'Track stock levels and product availability'}
                    </p>
                </div>
                <div className="inv-header-actions">
                    <button
                        id="inv-export-btn"
                        className="inv-btn inv-btn--secondary"
                        onClick={handleExportCsv}
                        disabled={exporting || loading}
                        title="Export current filter results as CSV"
                    >
                        {exporting ? (
                            <><span className="inv-spinner inv-spinner--sm" /> Exporting…</>
                        ) : (
                            <><DownloadIcon /> Export CSV</>
                        )}
                    </button>
                    {/* UC33 – Adjust Stock */}
                    <button
                        id="inv-adjust-stock-btn"
                        className="inv-btn inv-btn--secondary"
                        onClick={() => navigate('/inventory/adjust')}
                        title="Adjust stock quantities (UC33)"
                    >
                        <svg width="15" height="15" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" style={{ marginRight: '6px' }}>
                            <path fillRule="evenodd" d="M4 2a2 2 0 00-2 2v11a3 3 0 106 0V4a2 2 0 00-2-2H4zm1 14a1 1 0 100-2 1 1 0 000 2zm5-1.757l4.9-4.9a2 2 0 000-2.828L13.485 5.1a2 2 0 00-2.828 0L10 5.757v8.486zM16 18H9.071l6-6H16a2 2 0 012 2v2a2 2 0 01-2 2z" clipRule="evenodd" />
                        </svg>
                        Adjust Stock
                    </button>
                    {/* UC32 – Stock Check: route not yet implemented */}
                    <button
                        id="inv-stock-check-btn"
                        className="inv-btn inv-btn--primary inv-btn--disabled"
                        title="Stock Check (not yet implemented)"
                        disabled
                    >
                        <ClipboardIcon /> Start Stock Check
                    </button>
                </div>
            </div>

            {/* Filter bar */}
            <div className="inv-filter-bar">
                <div className="inv-search-wrap">
                    <SearchIcon className="inv-search-icon" />
                    <input
                        id="inv-search-input"
                        type="text"
                        className="inv-input inv-search-input"
                        placeholder="Search by name, SKU or barcode…"
                        value={keyword}
                        onChange={e => setKeyword(e.target.value)}
                    />
                    {keyword && (
                        <button
                            className="inv-search-clear"
                            onClick={() => setKeyword('')}
                            title="Clear search"
                        >×</button>
                    )}
                </div>

                <select
                    id="inv-category-filter"
                    className="inv-select"
                    value={categoryId}
                    onChange={e => setCategoryId(e.target.value)}
                >
                    <option value="">All Categories</option>
                    {categories.map(cat => (
                        <option key={cat.categoryId} value={cat.categoryId}>
                            {cat.categoryName}
                        </option>
                    ))}
                </select>

                <select
                    id="inv-status-filter"
                    className="inv-select"
                    value={stockStatus}
                    onChange={e => setStockStatus(e.target.value)}
                >
                    <option value="">All Statuses</option>
                    <option value="IN_STOCK">In Stock</option>
                    <option value="LOW_STOCK">Low Stock</option>
                    <option value="OUT_OF_STOCK">Out of Stock</option>
                    <option value="NEAR_EXPIRY">Near Expiry</option>
                </select>

                {(keyword || categoryId || stockStatus) && (
                    <button
                        id="inv-reset-filters-btn"
                        className="inv-btn inv-btn--ghost"
                        onClick={handleFilterReset}
                    >
                        Reset filters
                    </button>
                )}
            </div>

            {/* Summary stats */}
            <div className="inv-stats-row">
                <StatCard label="Total Products" value={totalElements} color="blue" />
                <StatCard
                    label="In Stock"
                    value={inventory.filter(r => r.stockStatus === 'IN_STOCK').length}
                    color="green"
                    note={page === 0 && inventory.length < size ? 'all' : 'this page'}
                />
                <StatCard
                    label="Low Stock"
                    value={inventory.filter(r => r.stockStatus === 'LOW_STOCK').length}
                    color="orange"
                    note={page === 0 && inventory.length < size ? 'all' : 'this page'}
                />
                <StatCard
                    label="Out of Stock"
                    value={inventory.filter(r => r.stockStatus === 'OUT_OF_STOCK').length}
                    color="red"
                    note={page === 0 && inventory.length < size ? 'all' : 'this page'}
                />
            </div>

            {/* Main content */}
            <div className="inv-card">
                {/* Table */}
                {loading ? (
                    <div className="inv-state-box">
                        <span className="inv-spinner" />
                        <p>Loading inventory…</p>
                    </div>
                ) : error ? (
                    <div className="inv-state-box inv-state-box--error">
                        <ErrorIcon />
                        <p>{error}</p>
                        <button className="inv-btn inv-btn--secondary" onClick={fetchInventory}>
                            Retry
                        </button>
                    </div>
                ) : inventory.length === 0 ? (
                    <div className="inv-state-box">
                        <BoxIcon />
                        <p className="inv-empty-title">No products found</p>
                        <p className="inv-empty-sub">
                            {(debouncedKeyword || categoryId || stockStatus)
                                ? 'Try adjusting your search or filters.'
                                : 'No active products in the database.'}
                        </p>
                    </div>
                ) : (
                    <div className="inv-table-wrap">
                        <table className="inv-table" id="inventory-table">
                            <thead>
                                <tr>
                                    <th>SKU</th>
                                    <th>Barcode</th>
                                    <th>Product Name</th>
                                    <th>Category</th>
                                    <th className="inv-th--num">On Hand</th>
                                    <th className="inv-th--num">Safety Min</th>
                                    <th>Status</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {inventory.map(item => (
                                    <tr key={item.productId} className="inv-row">
                                        <td className="inv-td--code">
                                            <Link to={`/products/${item.productId}`} className="inv-sku-link" title="View product details">
                                                {item.productCode}
                                            </Link>
                                        </td>
                                        <td className="inv-td--code">{item.barcode || '—'}</td>
                                        <td className="inv-td--name">{item.productName}</td>
                                        <td>
                                            <span className="inv-category-chip">{item.categoryName}</span>
                                        </td>
                                        <td className="inv-td--num">{formatQty(item.stockQuantity)} {item.unit}</td>
                                        <td className="inv-td--num">{formatQty(item.minimumStockLevel)}</td>
                                        <td><StatusBadge status={item.stockStatus} /></td>
                                        <td>
                                            <div className="inv-row-actions">
                                                {/* UC33 – Adjust Stock */}
                                                <button
                                                    className="inv-action-btn inv-action-btn--primary"
                                                    title={`Adjust stock for ${item.productName}`}
                                                    onClick={() => navigate(`/inventory/adjust?productId=${item.productId}`)}
                                                >
                                                    Adjust
                                                </button>
                                                {/* UC34 – Stock History: route not yet implemented */}
                                                <button
                                                    className="inv-action-btn inv-action-btn--ghost"
                                                    title="Stock History (not yet implemented)"
                                                    disabled
                                                >
                                                    History
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Pagination */}
                {!loading && !error && (
                    <Pagination
                        idPrefix="inv"
                        page={page}
                        size={size}
                        totalElements={totalElements}
                        totalPages={totalPages}
                        pageSizeOptions={[5, 10, 20, 50]}
                        onPageChange={setPage}
                        onSizeChange={(newSize) => {
                            setSize(newSize);
                            setPage(0);
                        }}
                        itemLabel="products"
                    />
                )}
            </div>

            {/* Authorization notice */}
            <p className="inv-auth-note">
                ⚠ JWT/RBAC is not yet implemented. This endpoint is accessible to all
                authenticated users. Role-based restriction (Admin, Manager, Inventory Staff)
                will be enforced once the auth module is complete.
            </p>
        </div>
    );
}

// ─── Small components ─────────────────────────────────────────────────────────

function StatCard({ label, value, color, note }) {
    return (
        <div className={`inv-stat-card inv-stat-card--${color}`}>
            <span className="inv-stat-value">{value}</span>
            <span className="inv-stat-label">{label}</span>
            {note && <span className="inv-stat-note">({note})</span>}
        </div>
    );
}

function formatQty(val) {
    if (val === null || val === undefined) return '—';
    const n = parseFloat(val);
    return Number.isInteger(n) ? n.toString() : n.toFixed(3).replace(/\.?0+$/, '');
}

// ─── Inline SVG icons ─────────────────────────────────────────────────────────

function SearchIcon({ className }) {
    return (
        <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
    );
}
function DownloadIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
            <polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
        </svg>
    );
}
function ClipboardIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2" />
            <rect x="9" y="3" width="6" height="4" rx="1" ry="1" />
        </svg>
    );
}
function ErrorIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="40" height="40" style={{color:'#e53e3e'}}>
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
    );
}
function BoxIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="48" height="48" style={{color:'#a0aec0'}}>
            <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" /><line x1="12" y1="22.08" x2="12" y2="12" />
        </svg>
    );
}
