import { useCallback, useEffect, useMemo, useState } from 'react';
import { BarChart3, CalendarDays, ReceiptText, RefreshCw, RotateCcw, TrendingUp } from 'lucide-react';
import Pagination from '../../components/ui/Pagination';
import salesReportService from '../../services/salesReportService';
import './SalesReportPage.css';

const toDateInput = (date) => date.toISOString().slice(0, 10);
const today = new Date();
const initialPeriod = {
    fromDate: toDateInput(new Date(today.getFullYear(), today.getMonth(), 1)),
    toDate: toDateInput(today)
};
const EMPTY_DAILY_SALES = [];

const formatCurrency = (value) => new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', maximumFractionDigits: 0
}).format(value ?? 0);

export default function SalesReportPage() {
    const [draftPeriod, setDraftPeriod] = useState(initialPeriod);
    const [period, setPeriod] = useState(initialPeriod);
    const [report, setReport] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);

    const loadReport = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await salesReportService.getSalesReport(period);
            if (!response?.success) throw new Error(response?.message || 'Could not generate sales report.');
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate sales report.');
        } finally {
            setLoading(false);
        }
    }, [period]);

    useEffect(() => {
        const timer = window.setTimeout(() => { void loadReport(); }, 0);
        return () => window.clearTimeout(timer);
    }, [loadReport]);

    const maxDailySales = useMemo(() => Math.max(...(report?.dailySales || []).map((day) => Number(day.netSales)), 1), [report]);
    const dailySales = report?.dailySales || EMPTY_DAILY_SALES;
    const totalPages = Math.ceil(dailySales.length / pageSize);
    const pagedDailySales = useMemo(
        () => dailySales.slice(page * pageSize, (page + 1) * pageSize),
        [dailySales, page, pageSize]
    );

    const submitPeriod = (event) => {
        event.preventDefault();
        if (draftPeriod.fromDate > draftPeriod.toDate) {
            setError('From date cannot be after To date.');
            return;
        }
        setPage(0);
        setPeriod(draftPeriod);
    };

    const resetPeriod = () => {
        setDraftPeriod(initialPeriod);
        setPage(0);
        setPeriod(initialPeriod);
    };

    const kpis = report ? [
        ['Net sales', formatCurrency(report.netSales), 'Completed sales after discounts'],
        ['Gross sales', formatCurrency(report.grossSales), 'Subtotal from completed transactions'],
        ['Total discount', formatCurrency(report.totalDiscount), 'Discounts applied to completed sales'],
        ['Items sold', Number(report.totalItemsSold || 0).toLocaleString('vi-VN'), 'Quantity from completed sales']
    ] : [];

    return (
        <div className="sales-report-page">
            <header className="report-header">
                <div className="sales-report-title"><span><TrendingUp size={22} /></span><div><small>Sales performance</small><h1>Sales Report</h1><p>Financial metrics include completed transactions only.</p></div></div>
                <div className="report-period-label"><CalendarDays size={18} /> {report ? `${report.fromDate} to ${report.toDate}` : 'Select reporting period'}</div>
            </header>

            <form className="report-filter" onSubmit={submitPeriod}>
                <label>From date<input type="date" value={draftPeriod.fromDate} onChange={(event) => setDraftPeriod(current => ({ ...current, fromDate: event.target.value }))} /></label>
                <label>To date<input type="date" value={draftPeriod.toDate} onChange={(event) => setDraftPeriod(current => ({ ...current, toDate: event.target.value }))} /></label>
                <div className="report-filter-actions">
                    <button type="button" onClick={resetPeriod}><RotateCcw size={16} /> Reset</button>
                    <button type="submit" className="report-primary">Generate report</button>
                </div>
            </form>

            {error && <div role="alert" className="report-error">{error}</div>}

            {loading ? <div className="report-loading"><RefreshCw size={24} className="sales-report-spin" /><strong>Generating sales report…</strong><span>Aggregating transactions for the selected period.</span></div> : report && <>
                <section className="report-kpis">
                    {kpis.map(([label, value, description]) => <article key={label}>
                        <span>{label}</span><strong>{value}</strong><small>{description}</small>
                    </article>)}
                </section>

                <section className="report-summary-grid">
                    <article className="report-card">
                        <h2><ReceiptText size={19} /> Transaction summary</h2>
                        <dl>
                            <div><dt>Total transactions</dt><dd>{report.totalTransactions}</dd></div>
                            <div><dt>Completed</dt><dd className="report-success">{report.completedTransactions}</dd></div>
                            <div><dt>Cancelled</dt><dd className="report-danger">{report.cancelledTransactions}</dd></div>
                        </dl>
                    </article>
                    <article className="report-card report-chart-card">
                        <h2><BarChart3 size={19} /> Daily net sales</h2>
                        {report.dailySales.length === 0 ? <p className="report-empty">No completed sales in this period.</p> : (
                            <div className="report-bars">
                                {pagedDailySales.map((day) => <div className="report-bar-row" key={day.date}>
                                    <span>{day.date}</span>
                                    <div><i style={{ width: `${Math.max((Number(day.netSales) / maxDailySales) * 100, 2)}%` }} /></div>
                                    <strong>{formatCurrency(day.netSales)}</strong>
                                </div>)}
                            </div>
                        )}
                        {dailySales.length > 0 && <Pagination
                            page={page}
                            size={pageSize}
                            totalElements={dailySales.length}
                            totalPages={totalPages}
                            pageSizeOptions={[5, 10, 20, 50]}
                            onPageChange={setPage}
                            onSizeChange={(size) => { setPageSize(size); setPage(0); }}
                            itemLabel="days"
                            idPrefix="sales-report"
                        />}
                    </article>
                </section>
            </>}
        </div>
    );
}
