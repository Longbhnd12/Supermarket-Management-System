import { useEffect, useState } from 'react';
import dashboardService from '../services/dashboardService';

export default function HomePage() {

    const [dashboard, setDashboard] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        const loadDashboard = async () => {
            try {
                const response = await dashboardService.getDashboard();
                if (!response?.success) throw new Error(response?.message);
                setDashboard(response.data);
            } catch (error) {
                console.error(error);
                setError('Could not load dashboard.');
            }
        };

        loadDashboard();
    }, []);

    return (
        <div>
            <h1>Dashboard</h1>
            {error && <p>{error}</p>}
            {!dashboard ? <p>Loading dashboard…</p> : <div className="report-grid">
                <article className="report-kpi"><span>Sales today</span><strong>{Number(dashboard.todaySales || 0).toLocaleString('vi-VN')} ₫</strong></article>
                <article className="report-kpi"><span>MTD gross revenue</span><strong>{Number(dashboard.monthToDateGrossRevenue || 0).toLocaleString('vi-VN')} ₫</strong></article>
                <article className="report-kpi"><span>Active SKUs</span><strong>{dashboard.activeCatalogSkus}</strong></article>
                <article className="report-kpi"><span>In-stock health</span><strong>{dashboard.inStockHealth}%</strong></article>
            </div>}
        </div>
    );
}
