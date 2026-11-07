import { useCallback, useEffect, useState } from 'react';
import useCategories from '../../hooks/useCategories';
import reportService from '../../services/reportService';
import './ReportPages.css';

export default function InventoryReportPage() {
    const [filters, setFilters] = useState({ categoryId: '', stockStatus: '' });
    const [report, setReport] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const { categories, categoryError } = useCategories();

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {};
            if (filters.categoryId) params.categoryId = filters.categoryId;
            if (filters.stockStatus) params.stockStatus = filters.stockStatus;
            const response = await reportService.getInventory(params);
            if (!response?.success) throw new Error(response?.message);
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate inventory report.');
        } finally {
            setLoading(false);
        }
    }, [filters]);

    useEffect(() => {
        const timer = window.setTimeout(() => void load(), 0);
        return () => window.clearTimeout(timer);
    }, [load]);

    const submit = (event) => {
        event.preventDefault();
        setFilters({ categoryId: event.currentTarget.categoryId.value, stockStatus: event.currentTarget.stockStatus.value });
    };

    return <div className="report-page">
        <header><h1>Inventory Report</h1><p>Current stock is calculated from Product stock levels; near-expiry uses available receipt batches.</p></header>
        <form className="report-toolbar" onSubmit={submit}>
            <label>Category<select name="categoryId" defaultValue={filters.categoryId}><option value="">All categories</option>{categories.map(category => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
            <label>Stock status<select name="stockStatus" defaultValue={filters.stockStatus}><option value="">All stock</option><option value="IN_STOCK">In stock</option><option value="LOW_STOCK">Low stock</option><option value="OUT_OF_STOCK">Out of stock</option></select></label>
            <button type="submit">Generate report</button>
        </form>
        {(error || categoryError) && <div className="report-error">{error || categoryError}</div>}
        {loading ? <div className="report-loading">Generating report…</div> : report && <>
            <section className="report-grid">{[['Products', report.totalProducts], ['In stock', report.inStockProducts], ['Low stock', report.lowStockProducts], ['Out of stock', report.outOfStockProducts]].map(([label, value]) => <article className="report-kpi" key={label}><span>{label}</span><strong>{value}</strong></article>)}</section>
            <section className="report-table"><table><thead><tr><th>Product</th><th>Category</th><th>Current stock</th><th>Minimum</th><th>Status</th><th>Near expiry</th></tr></thead><tbody>{report.products.length === 0 ? <tr><td className="report-empty" colSpan="6">No products match the selected filters.</td></tr> : report.products.map(product => <tr key={product.productId}><td>{product.productCode} · {product.productName}</td><td>{product.categoryName}</td><td>{product.stockQuantity}</td><td>{product.minimumStockLevel}</td><td><span className={`report-status report-status--${product.stockStatus.toLowerCase()}`}>{product.stockStatus.replaceAll('_', ' ')}</span></td><td>{product.nearExpiry ? 'Yes' : '—'}</td></tr>)}</tbody></table></section>
        </>}
    </div>;
}
