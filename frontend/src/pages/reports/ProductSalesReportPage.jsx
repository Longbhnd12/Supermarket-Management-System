import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDownUp, Boxes, CalendarDays, PackageSearch, RefreshCw, TrendingUp } from 'lucide-react';
import Pagination from '../../components/ui/Pagination';
import useCategories from '../../hooks/useCategories';
import reportService from '../../services/reportService';
import './ReportPages.css';

const dateValue = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const today = new Date();
const INITIAL = { fromDate: dateValue(new Date(today.getFullYear(), today.getMonth(), 1)), toDate: dateValue(today), categoryId: '', sortBy: 'QUANTITY' };
const EMPTY_PRODUCTS = [];
const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0);
const number = (value) => Number(value || 0).toLocaleString('vi-VN');

export default function ProductSalesReportPage() {
    const [draft, setDraft] = useState(INITIAL);
    const [filter, setFilter] = useState(INITIAL);
    const [report, setReport] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const { categories, categoryError } = useCategories();

    const load = useCallback(async () => {
        setLoading(true); setError(null);
        try {
            const params = { fromDate: filter.fromDate, toDate: filter.toDate, sortBy: filter.sortBy };
            if (filter.categoryId) params.categoryId = filter.categoryId;
            const response = await reportService.getProductSales(params);
            if (!response?.success) throw new Error(response?.message || 'Could not generate product sales report.');
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate product sales report.');
        } finally { setLoading(false); }
    }, [filter]);

    useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);
    const totalUnits = useMemo(() => (report?.products || []).reduce((sum, item) => sum + Number(item.quantitySold || 0), 0), [report]);
    const products = report?.products || EMPTY_PRODUCTS;
    const totalPages = Math.ceil(products.length / pageSize);
    const pagedProducts = useMemo(
        () => products.slice(page * pageSize, (page + 1) * pageSize),
        [products, page, pageSize]
    );

    const submit = (event) => {
        event.preventDefault();
        if (draft.fromDate > draft.toDate) { setError('From date cannot be after To date.'); return; }
        setPage(0);
        setFilter(draft);
    };

    return <div className="report-page">
        <header className="report-page-header"><div className="report-title-block"><span className="report-title-icon"><TrendingUp size={22} /></span><div>
            <span className="report-eyebrow">Product performance</span><h1>Product Sales Report</h1><p>Compare completed product sales, discounts and revenue contribution.</p>
        </div></div><span className="report-period-chip"><CalendarDays size={16} /> {filter.fromDate} — {filter.toDate}</span></header>

        <form className="report-toolbar report-toolbar--wide" onSubmit={submit}>
            <label>From<input type="date" value={draft.fromDate} onChange={(event) => setDraft(current => ({ ...current, fromDate: event.target.value }))} /></label>
            <label>To<input type="date" value={draft.toDate} onChange={(event) => setDraft(current => ({ ...current, toDate: event.target.value }))} /></label>
            <label>Category<select value={draft.categoryId} onChange={(event) => setDraft(current => ({ ...current, categoryId: event.target.value }))}><option value="">All categories</option>{categories.map(category => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
            <label>Sort by<select value={draft.sortBy} onChange={(event) => setDraft(current => ({ ...current, sortBy: event.target.value }))}><option value="QUANTITY">Quantity sold</option><option value="REVENUE">Net revenue</option></select></label>
            <button type="submit" disabled={loading}>{loading && <RefreshCw size={15} className="report-spin" />}Generate</button>
        </form>
        {(error || categoryError) && <div className="report-error" role="alert">{error || categoryError}</div>}
        {loading ? <div className="report-loading"><RefreshCw size={24} className="report-spin" /><strong>Ranking products…</strong><span>Aggregating completed sale items.</span></div> : report && <>
            <section className="report-grid report-grid--three">
                <article className="report-kpi report-kpi--blue"><span className="report-kpi-icon"><TrendingUp size={19} /></span><div><span>Net sales</span><strong>{money(report.totalNetSales)}</strong><small>After product discounts</small></div></article>
                <article className="report-kpi report-kpi--violet"><span className="report-kpi-icon"><PackageSearch size={19} /></span><div><span>Products sold</span><strong>{number(products.length)}</strong><small>Distinct products in result</small></div></article>
                <article className="report-kpi report-kpi--green"><span className="report-kpi-icon"><Boxes size={19} /></span><div><span>Units sold</span><strong>{number(totalUnits)}</strong><small>Total completed quantity</small></div></article>
            </section>
            <section className="report-table-card"><div className="report-section-heading"><div><span>Performance ranking</span><h2>Product results</h2></div><strong><ArrowDownUp size={14} /> {filter.sortBy === 'REVENUE' ? 'Net revenue' : 'Quantity sold'}</strong></div>
                <div className="report-table"><table><thead><tr><th>Product</th><th>Category</th><th>Quantity</th><th>Gross</th><th>Discount</th><th>Net sales</th><th>Share</th></tr></thead><tbody>
                    {products.length === 0 ? <tr><td colSpan="7"><div className="report-empty"><PackageSearch size={30} /><strong>No completed product sales</strong><span>Adjust the date range or category filter.</span></div></td></tr> : pagedProducts.map(item => <tr key={item.productId}>
                        <td><div className="report-product-cell"><strong>{item.productName}</strong><span>{item.productCode}</span></div></td><td>{item.categoryName}</td><td className="report-number-cell">{number(item.quantitySold)}</td><td>{money(item.grossSales)}</td><td className="report-discount">−{money(item.discount)}</td><td className="report-money-cell">{money(item.netSales)}</td><td><div className="report-share"><span>{item.revenueShare}%</span><i><b style={{ width: `${Math.min(Number(item.revenueShare || 0), 100)}%` }} /></i></div></td>
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
                    idPrefix="product-sales-report"
                />}
            </section>
        </>}
    </div>;
}
