import { useCallback, useEffect, useMemo, useState } from 'react';
import { MoreVertical, Package, Pencil, Plus, Search } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Pagination from '../../components/ui/Pagination';
import CategoryService from '../../services/categoryService';
import ProductService from '../../services/productService';
import { useAuth } from '../../context/authContext';
import { hasPermission } from '../../utils/permissions';
import './ProductListPage.css';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function numberValue(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

function formatPrice(value) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0
    }).format(numberValue(value));
}

function formatQuantity(value) {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 }).format(numberValue(value));
}

function getStockStatus(product) {
    if (product.status === 'INACTIVE') return 'INACTIVE';
    const stock = numberValue(product.stockQuantity);
    const minimum = numberValue(product.minimumStockLevel);
    if (stock <= 0) return 'OUT_OF_STOCK';
    if (minimum > 0 && stock <= minimum) return 'LOW_STOCK';
    return 'IN_STOCK';
}

const STATUS_META = {
    IN_STOCK: { label: 'In Stock', className: 'prd-stock-badge--in' },
    LOW_STOCK: { label: 'Low Stock', className: 'prd-stock-badge--low' },
    OUT_OF_STOCK: { label: 'Out of Stock', className: 'prd-stock-badge--out' },
    INACTIVE: { label: 'Inactive', className: 'prd-stock-badge--inactive' }
};

function StockBadge({ product }) {
    const meta = STATUS_META[getStockStatus(product)] || STATUS_META.INACTIVE;
    return <span className={`prd-stock-badge ${meta.className}`}><i />{meta.label}</span>;
}

function StatCard({ value, label, tone }) {
    return <article className={`prd-kpi prd-kpi--${tone}`}>
        <strong>{value.toLocaleString('en-US')}</strong>
        <span>{label}</span>
    </article>;
}

export default function ProductListPage() {
    const navigate = useNavigate();
    const { currentUser } = useAuth();
    const [products, setProducts] = useState([]);
    const [categories, setCategories] = useState([]);
    const [searchInput, setSearchInput] = useState('');
    const [search, setSearch] = useState('');
    const [categoryId, setCategoryId] = useState('');
    const [stockStatus, setStockStatus] = useState('');
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(10);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const canCreate = hasPermission(currentUser, 'PRODUCT_MANAGE');

    const loadProducts = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await ProductService.getAll();
            setProducts(response.data?.data || []);
        } catch (requestError) {
            setProducts([]);
            setError(requestError.response?.data?.message || 'Could not load products. Please try again.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => void loadProducts(), 0);
        return () => window.clearTimeout(timer);
    }, [loadProducts]);

    useEffect(() => {
        let active = true;
        CategoryService.getAll()
            .then(response => { if (active) setCategories(response.data?.data || []); })
            .catch(() => { if (active) setCategories([]); });
        return () => { active = false; };
    }, []);

    const stats = useMemo(() => ({
        total: products.length,
        inStock: products.filter(product => getStockStatus(product) === 'IN_STOCK').length,
        lowStock: products.filter(product => getStockStatus(product) === 'LOW_STOCK').length,
        outOfStock: products.filter(product => getStockStatus(product) === 'OUT_OF_STOCK').length
    }), [products]);

    const filteredProducts = useMemo(() => {
        const keyword = search.trim().toLocaleLowerCase('en-US');
        return products.filter(product => {
            const matchesKeyword = !keyword || [product.productName, product.productCode, product.barcode]
                .some(value => String(value || '').toLocaleLowerCase('en-US').includes(keyword));
            const matchesCategory = !categoryId || String(product.categoryId) === categoryId;
            const matchesStatus = !stockStatus || getStockStatus(product) === stockStatus;
            return matchesKeyword && matchesCategory && matchesStatus;
        });
    }, [categoryId, products, search, stockStatus]);

    const totalElements = filteredProducts.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));
    const safePage = Math.min(page, totalPages - 1);
    const visibleProducts = filteredProducts.slice(safePage * size, safePage * size + size);

    const submitSearch = (event) => {
        event.preventDefault();
        setSearch(searchInput);
        setPage(0);
    };

    return <div className="prd-page">
        <header className="prd-page-header">
            <div>
                <h1>Product Management</h1>
                <p>Manage catalog, pricing and stock visibility</p>
            </div>
            {canCreate && <button className="prd-add-button" type="button" onClick={() => navigate('/products/create')}>
                <Plus size={19} /> Add Product
            </button>}
        </header>

        <section className="prd-kpi-grid" aria-label="Product summary">
            <StatCard value={stats.total} label="Total Products" tone="blue" />
            <StatCard value={stats.inStock} label="In Stock" tone="green" />
            <StatCard value={stats.lowStock} label="Low Stock" tone="amber" />
            <StatCard value={stats.outOfStock} label="Out of Stock" tone="red" />
        </section>

        <form className="prd-filter-panel" onSubmit={submitSearch}>
            <label className="prd-search-field">
                <Search size={18} />
                <input value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="Search by product name, SKU or barcode" />
            </label>
            <select aria-label="Category" value={categoryId} onChange={event => { setCategoryId(event.target.value); setPage(0); }}>
                <option value="">All Categories</option>
                {categories.map(category => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}
            </select>
            <select aria-label="Stock status" value={stockStatus} onChange={event => { setStockStatus(event.target.value); setPage(0); }}>
                <option value="">All Statuses</option>
                <option value="IN_STOCK">In Stock</option>
                <option value="LOW_STOCK">Low Stock</option>
                <option value="OUT_OF_STOCK">Out of Stock</option>
                <option value="INACTIVE">Inactive</option>
            </select>
            <button type="submit"><Search size={17} /> Search</button>
        </form>

        {error && <div className="prd-error" role="alert">{error}<button type="button" onClick={() => void loadProducts()}>Try again</button></div>}

        <section className="prd-table-card">
            <div className="prd-table-scroll">
                <table className="prd-table">
                    <thead><tr><th>Product</th><th>Category</th><th>Barcode</th><th>Unit</th><th className="numeric">Selling Price</th><th className="numeric">Stock</th><th className="numeric">Minimum</th><th>Status</th><th aria-label="Actions" /></tr></thead>
                    <tbody>
                        {loading ? <tr><td className="prd-empty" colSpan="9">Loading products…</td></tr> : visibleProducts.length === 0 ? <tr><td className="prd-empty" colSpan="9"><Package size={28} /><strong>No products found</strong><span>Try changing your search or filters.</span></td></tr> : visibleProducts.map(product => <tr key={product.productId} onClick={() => navigate(`/products/${product.productId}`)}>
                            <td><div className="prd-product-cell"><span className="prd-product-icon"><Package size={18} /></span><div><strong>{product.productName}</strong><small>SKU: {product.productCode || '—'}</small></div></div></td>
                            <td>{product.categoryName || '—'}</td>
                            <td className="prd-code">{product.barcode || '—'}</td>
                            <td>{product.unit || '—'}</td>
                            <td className="numeric prd-price">{formatPrice(product.sellingPrice)}</td>
                            <td className="numeric">{formatQuantity(product.stockQuantity)}</td>
                            <td className="numeric">{formatQuantity(product.minimumStockLevel)}</td>
                            <td><StockBadge product={product} /></td>
                            <td><div className="prd-row-actions"><button type="button" title="View product" onClick={event => { event.stopPropagation(); navigate(`/products/${product.productId}`); }}><Pencil size={16} /></button><button type="button" title="More actions" onClick={event => event.stopPropagation()}><MoreVertical size={17} /></button></div></td>
                        </tr>)}
                    </tbody>
                </table>
            </div>
            <Pagination page={safePage} size={size} totalElements={totalElements} totalPages={totalPages} pageSizeOptions={PAGE_SIZE_OPTIONS} onPageChange={setPage} onSizeChange={newSize => { setSize(newSize); setPage(0); }} itemLabel="products" idPrefix="product" disabled={loading} />
        </section>
    </div>;
}
