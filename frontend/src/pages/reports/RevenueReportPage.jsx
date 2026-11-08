import { useCallback, useEffect, useMemo, useState } from 'react';
import { Banknote, CalendarDays, CreditCard, Landmark, RefreshCw, WalletCards } from 'lucide-react';
import Pagination from '../../components/ui/Pagination';
import reportService from '../../services/reportService';
import './ReportPages.css';

const dateValue = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const today = new Date();
const INITIAL = { fromDate: dateValue(new Date(today.getFullYear(), today.getMonth(), 1)), toDate: dateValue(today) };
const EMPTY_DAILY_REVENUE = [];
const currency = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value ?? 0);
const shortDate = (value) => new Date(`${value}T00:00:00`).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });

export default function RevenueReportPage() {
    const [draft, setDraft] = useState(INITIAL);
    const [period, setPeriod] = useState(INITIAL);
    const [report, setReport] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await reportService.getRevenue(period);
            if (!response?.success) throw new Error(response?.message || 'Could not generate revenue report.');
            setReport(response.data);
        } catch (requestError) {
            setReport(null);
            setError(requestError.response?.data?.message || requestError.message || 'Could not generate revenue report.');
        } finally {
            setLoading(false);
        }
    }, [period]);

    useEffect(() => {
        const timer = window.setTimeout(() => void load(), 0);
        return () => window.clearTimeout(timer);
    }, [load]);

    const maxRevenue = useMemo(() => Math.max(...(report?.dailyRevenue || []).map((day) => Number(day.revenue || 0)), 1), [report]);
    const dailyRevenue = report?.dailyRevenue || EMPTY_DAILY_REVENUE;
    const totalPages = Math.ceil(dailyRevenue.length / pageSize);
    const pagedDailyRevenue = useMemo(
        () => dailyRevenue.slice(page * pageSize, (page + 1) * pageSize),
        [dailyRevenue, page, pageSize]
    );

    const submit = (event) => {
        event.preventDefault();
        if (draft.fromDate > draft.toDate) {
            setError('From date cannot be after To date.');
            return;
        }
        setPage(0);
        setPeriod(draft);
    };
    const reset = () => { setDraft(INITIAL); setPage(0); setPeriod(INITIAL); };

    const metrics = report ? [
        { label: 'Total revenue', value: report.totalRevenue, icon: Banknote, tone: 'blue', note: 'All completed payments' },
        { label: 'Cash', value: report.cashRevenue, icon: Landmark, tone: 'green', note: 'Paid at checkout' },
        { label: 'Card', value: report.cardRevenue, icon: CreditCard, tone: 'violet', note: 'Bank and domestic cards' },
        { label: 'E-wallet / other', value: Number(report.eWalletRevenue) + Number(report.otherRevenue), icon: WalletCards, tone: 'amber', note: 'Digital and other methods' }
    ] : [];

    return <div className="report-page">
        <header className="report-page-header">
            <div className="report-title-block"><span className="report-title-icon"><Banknote size={22} /></span><div>
                <span className="report-eyebrow">Financial overview</span>
                <h1>Revenue Report</h1><p>Completed transactions only; COGS and profit are not included.</p>
            </div></div>
            <span className="report-period-chip"><CalendarDays size={16} /> {period.fromDate} — {period.toDate}</span>
        </header>

        <form className="report-toolbar" onSubmit={submit}>
            <div className="report-toolbar-copy"><strong>Reporting period</strong><span>Choose the dates used for every revenue metric below.</span></div>
            <label>From date<input type="date" value={draft.fromDate} onChange={(event) => setDraft(current => ({ ...current, fromDate: event.target.value }))} /></label>
            <label>To date<input type="date" value={draft.toDate} onChange={(event) => setDraft(current => ({ ...current, toDate: event.target.value }))} /></label>
            <div className="report-toolbar-actions"><button type="button" className="report-secondary" onClick={reset}>Reset</button><button type="submit" disabled={loading}>{loading ? <RefreshCw size={15} className="report-spin" /> : null}Generate</button></div>
        </form>

        {error && <div className="report-error" role="alert">{error}<button type="button" onClick={() => void load()}>Try again</button></div>}
        {loading ? <div className="report-loading"><RefreshCw size={24} className="report-spin" /><strong>Calculating revenue…</strong><span>Reading completed transactions for the selected period.</span></div> : report && <>
            <section className="report-grid report-grid--four">
                {metrics.map(({ label, value, icon: Icon, tone, note }) => <article className={`report-kpi report-kpi--${tone}`} key={label}>
                    <span className="report-kpi-icon"><Icon size={19} /></span><div><span>{label}</span><strong>{currency(value)}</strong><small>{note}</small></div>
                </article>)}
            </section>
            <section className="report-panel">
                <div className="report-section-heading"><div><span>Daily breakdown</span><h2>Revenue by day</h2></div><strong>{dailyRevenue.length} day{dailyRevenue.length === 1 ? '' : 's'}</strong></div>
                {dailyRevenue.length === 0 ? <div className="report-empty"><Banknote size={30} /><strong>No completed revenue</strong><span>Try a wider date range or complete a POS transaction first.</span></div> : <div className="report-daily-list">
                    {pagedDailyRevenue.map((day) => <div className="report-daily-row" key={day.date}>
                        <span>{shortDate(day.date)}</span><div><i style={{ width: `${Math.max((Number(day.revenue || 0) / maxRevenue) * 100, 2)}%` }} /></div><strong>{currency(day.revenue)}</strong>
                    </div>)}
                </div>}
                {dailyRevenue.length > 0 && <Pagination
                    page={page}
                    size={pageSize}
                    totalElements={dailyRevenue.length}
                    totalPages={totalPages}
                    pageSizeOptions={[5, 10, 20, 50]}
                    onPageChange={setPage}
                    onSizeChange={(size) => { setPageSize(size); setPage(0); }}
                    itemLabel="days"
                    idPrefix="revenue-report"
                />}
            </section>
        </>}
    </div>;
}
