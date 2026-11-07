import { useCallback, useEffect, useState } from 'react';
import useCategories from '../../hooks/useCategories';
import reportService from '../../services/reportService';
import './ReportPages.css';

const today = new Date().toISOString().slice(0, 10);
const INITIAL = { fromDate: `${today.slice(0, 8)}01`, toDate: today, categoryId: '', sortBy: 'QUANTITY' };
const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0);

export default function ProductSalesReportPage() {
    const [filter, setFilter] = useState(INITIAL);
    const [report, setReport] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);
    const { categories, categoryError } = useCategories();

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = { fromDate: filter.fromDate, toDate: filter.toDate, sortBy: filter.sortBy };
            if (filter.categoryId) params.categoryId = filter.categoryId;
            const response = await reportService.getProductSales(params);
            if (!response?.success) throw new Error(response?.message);
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate product sales report.');
        } finally {
            setLoading(false);
        }
    }, [filter]);

    useEffect(() => {
        const timer = window.setTimeout(() => void load(), 0);
        return () => window.clearTimeout(timer);
    }, [load]);

    const submit = (event) => {
        event.preventDefault();
        const next = {
            fromDate: event.currentTarget.fromDate.value,
            toDate: event.currentTarget.toDate.value,
            categoryId: event.currentTarget.categoryId.value,
            sortBy: event.currentTarget.sortBy.value
        };
        if (next.fromDate > next.toDate) {
            setError('From date cannot be after To date.');
            return;
        }
        setFilter(next);
    };

    return <div className="report-page">
        <header><h1>Product Sales Report</h1><p>Only sale items belonging to completed transactions are included.</p></header>
        <form className="report-toolbar" onSubmit={submit}>
            <label>From<input name="fromDate" type="date" defaultValue={filter.fromDate} /></label>
            <label>To<input name="toDate" type="date" defaultValue={filter.toDate} /></label>
            <label>Category<select name="categoryId" defaultValue={filter.categoryId}><option value="">All categories</option>{categories.map(category => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
            <label>Sort<select name="sortBy" defaultValue={filter.sortBy}><option value="QUANTITY">Quantity sold</option><option value="REVENUE">Net revenue</option></select></label>
            <button type="submit">Generate report</button>
        </form>
        {(error || categoryError) && <div className="report-error">{error || categoryError}</div>}
        {loading ? <div className="report-loading">Generating report…</div> : report && <>
            <section className="report-grid"><article className="report-kpi"><span>Net sales</span><strong>{money(report.totalNetSales)}</strong></article><article className="report-kpi"><span>Products sold</span><strong>{report.products.length}</strong></article></section>
            <section className="report-table"><table><thead><tr><th>Product</th><th>Category</th><th>Quantity</th><th>Gross</th><th>Discount</th><th>Net sales</th><th>Share</th></tr></thead><tbody>{report.products.length === 0 ? <tr><td className="report-empty" colSpan="7">No completed product sales in this period.</td></tr> : report.products.map(item => <tr key={item.productId}><td>{item.productCode} · {item.productName}</td><td>{item.categoryName}</td><td>{item.quantitySold}</td><td>{money(item.grossSales)}</td><td>{money(item.discount)}</td><td>{money(item.netSales)}</td><td>{item.revenueShare}%</td></tr>)}</tbody></table></section>
        </>}
    </div>;
}
