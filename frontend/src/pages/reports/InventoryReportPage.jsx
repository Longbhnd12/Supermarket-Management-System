import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, PackageCheck, PackageX, RefreshCw, Warehouse } from 'lucide-react';
import Pagination from '../../components/ui/Pagination';
import useCategories from '../../hooks/useCategories';
import reportService from '../../services/reportService';
import './ReportPages.css';

const INITIAL = { categoryId: '', stockStatus: '' };
const EMPTY_PRODUCTS = [];
const number = (value) => Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: 3 });

export default function InventoryReportPage() {
    const [draft, setDraft] = useState(INITIAL);
    const [filters, setFilters] = useState(INITIAL);
    const [report, setReport] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const { categories, categoryError } = useCategories();

    const load = useCallback(async () => {
        setLoading(true); setError(null);
        try {
            const params = {};
            if (filters.categoryId) params.categoryId = filters.categoryId;
            if (filters.stockStatus) params.stockStatus = filters.stockStatus;
            const response = await reportService.getInventory(params);
            if (!response?.success) throw new Error(response?.message || 'Could not generate inventory report.');
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate inventory report.');
        } finally { setLoading(false); }
    }, [filters]);

    useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);
    const submit = (event) => { event.preventDefault(); setPage(0); setFilters(draft); };
    const reset = () => { setDraft(INITIAL); setPage(0); setFilters(INITIAL); };

    const products = report?.products || EMPTY_PRODUCTS;
    const totalPages = Math.ceil(products.length / pageSize);
    const pagedProducts = useMemo(
        () => products.slice(page * pageSize, (page + 1) * pageSize),
        [products, page, pageSize]
    );

    const metrics = report ? [
        { label: 'Products', value: report.totalProducts, note: `${number(report.totalStockUnits)} total stock units`, icon: Boxes, tone: 'blue' },
        { label: 'In stock', value: report.inStockProducts, note: 'Above minimum level', icon: PackageCheck, tone: 'green' },
        { label: 'Low stock', value: report.lowStockProducts, note: 'Needs replenishment', icon: AlertTriangle, tone: 'amber' },
        { label: 'Out of stock', value: report.outOfStockProducts, note: `${report.nearExpiryProducts} near-expiry product(s)`, icon: PackageX, tone: 'red' }
    ] : [];

    return <div className="report-page">
        <header className="report-page-header"><div className="report-title-block"><span className="report-title-icon"><Warehouse size={22} /></span><div>
            <span className="report-eyebrow">Inventory health</span><h1>Inventory Report</h1><p>Review stock levels, safety thresholds and near-expiry risk in one place.</p>
        </div></div><span className="report-period-chip report-period-chip--live"><i /> Live inventory snapshot</span></header>

        <form className="report-toolbar" onSubmit={submit}>
            <div className="report-toolbar-copy"><strong>Filter inventory</strong><span>Narrow the report by category or stock condition.</span></div>
            <label>Category<select value={draft.categoryId} onChange={(event) => setDraft(current => ({ ...current, categoryId: event.target.value }))}><option value="">All categories</option>{categories.map(category => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
            <label>Stock status<select value={draft.stockStatus} onChange={(event) => setDraft(current => ({ ...current, stockStatus: event.target.value }))}><option value="">All stock</option><option value="IN_STOCK">In stock</option><option value="LOW_STOCK">Low stock</option><option value="OUT_OF_STOCK">Out of stock</option></select></label>
            <div className="report-toolbar-actions"><button type="button" className="report-secondary" onClick={reset}>Reset</button><button type="submit" disabled={loading}>{loading && <RefreshCw size={15} className="report-spin" />}Apply filters</button></div>
        </form>
        {(error || categoryError) && <div className="report-error" role="alert">{error || categoryError}</div>}
        {loading ? <div className="report-loading"><RefreshCw size={24} className="report-spin" /><strong>Checking inventory…</strong><span>Calculating stock health across the catalog.</span></div> : report && <>
            <section className="report-grid report-grid--four">{metrics.map(({ label, value, note, icon: Icon, tone }) => <article className={`report-kpi report-kpi--${tone}`} key={label}><span className="report-kpi-icon"><Icon size={19} /></span><div><span>{label}</span><strong>{number(value)}</strong><small>{note}</small></div></article>)}</section>
            <section className="report-table-card"><div className="report-section-heading"><div><span>Catalog snapshot</span><h2>Product stock details</h2></div><strong>{products.length} result{products.length === 1 ? '' : 's'}</strong></div>
                <div className="report-table"><table><thead><tr><th>Product</th><th>Category</th><th>Current stock</th><th>Minimum</th><th>Status</th><th>Expiry risk</th></tr></thead><tbody>
                    {products.length === 0 ? <tr><td colSpan="6"><div className="report-empty"><Warehouse size={30} /><strong>No matching products</strong><span>Clear a filter to broaden the report.</span></div></td></tr> : pagedProducts.map(product => <tr key={product.productId}>
                        <td><div className="report-product-cell"><strong>{product.productName}</strong><span>{product.productCode}</span></div></td><td>{product.categoryName}</td><td className="report-number-cell">{number(product.stockQuantity)}</td><td>{number(product.minimumStockLevel)}</td><td><span className={`report-status report-status--${product.stockStatus.toLowerCase()}`}>{product.stockStatus.replaceAll('_', ' ')}</span></td><td>{product.nearExpiry ? <span className="report-expiry"><AlertTriangle size={13} /> Near expiry</span> : <span className="report-muted">No alert</span>}</td>
                    </tr>)}
                </tbody></table></div>
                {products.length > 0 && <Pagination
                    page={page}
                    size={pageSize}
                    totalElements={products.length}
                    totalPages={totalPages}
                    pageSizeOptions={[5, 10, 20, 50]}
                    onPageChange={setPage}
                    onSizeChange={(size) => { setPageSize(size); setPage(0); }}
                    itemLabel="products"
                    idPrefix="inventory-report"
                />}
            </section>
        </>}
    </div>;
}
