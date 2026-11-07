import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
    AlertTriangle,
    ArrowRight,
    BarChart3,
    Boxes,
    ClipboardCheck,
    History,
    LayoutDashboard,
    LineChart,
    RefreshCw,
    Settings,
    ShieldCheck,
    ShoppingCart,
    TrendingUp,
    UserCog,
    Warehouse
} from 'lucide-react';
import { useAuth } from '../context/authContext';
import dashboardService from '../services/dashboardService';
import { PAGE_ACCESS, canAccess } from '../utils/pageAccess';
import './HomePage.css';

const ROLE_CONTENT = {
    ADMIN: {
        label: 'Administrator overview',
        title: 'System-wide performance',
        description: 'Monitor store performance, inventory health, and the administration areas that need attention.',
        actions: [
            { label: 'Manage users', description: 'Accounts and access', path: '/users', icon: UserCog, access: PAGE_ACCESS.users },
            { label: 'System settings', description: 'Operational parameters', path: '/system-settings', icon: Settings, access: PAGE_ACCESS.systemSettings },
            { label: 'Audit logs', description: 'Review system activity', path: '/system-logs', icon: History, access: PAGE_ACCESS.systemLogs },
            { label: 'Revenue report', description: 'Analyze store revenue', path: '/reports/revenue', icon: TrendingUp, access: PAGE_ACCESS.reportRevenue }
        ]
    },
    MANAGER: {
        label: 'Management overview',
        title: 'Store performance at a glance',
        description: 'Track sales, review inventory risk, and move pending operational work forward.',
        actions: [
            { label: 'Approve requests', description: 'Review pending orders', path: '/purchase-orders/pending', icon: ClipboardCheck, access: PAGE_ACCESS.purchaseRequestApprove },
            { label: 'Sales monitoring', description: 'Inspect store sales', path: '/sales', icon: BarChart3, access: PAGE_ACCESS.salesMonitoring },
            { label: 'Revenue report', description: 'Review revenue trends', path: '/reports/revenue', icon: TrendingUp, access: PAGE_ACCESS.reportRevenue },
            { label: 'Inventory report', description: 'Check stock health', path: '/reports/inventory', icon: Warehouse, access: PAGE_ACCESS.reportInventory }
        ]
    }
};

const formatCurrency = (value) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;
const formatNumber = (value) => Number(value || 0).toLocaleString('vi-VN');

function MetricCard({ icon: Icon, label, value, note, tone = 'blue' }) {
    return (
        <article className={`dashboard-metric dashboard-metric--${tone}`}>
            <span className="dashboard-metric-icon"><Icon size={20} /></span>
            <div>
                <span>{label}</span>
                <strong>{value}</strong>
                <small>{note}</small>
            </div>
        </article>
    );
}

function EmptyState({ children }) {
    return <p className="dashboard-empty">{children}</p>;
}

export default function HomePage() {
    const { currentUser } = useAuth();
    const [dashboard, setDashboard] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);

    const loadDashboard = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const response = await dashboardService.getDashboard();
            if (!response?.success) throw new Error(response?.message || 'Could not load dashboard.');
            setDashboard(response.data);
        } catch (requestError) {
            setError(requestError.response?.data?.message || requestError.message || 'Could not load dashboard.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => void loadDashboard(), 0);
        return () => window.clearTimeout(timer);
    }, [loadDashboard]);

    const role = currentUser?.role || 'ADMIN';
    const content = ROLE_CONTENT[role] || ROLE_CONTENT.ADMIN;
    const availableActions = content.actions.filter((action) => canAccess(currentUser, action.access));
    const displayName = currentUser?.fullName || currentUser?.username || 'there';
    const alerts = dashboard?.urgentRestock || [];
    const canViewSales = canAccess(currentUser, PAGE_ACCESS.reportSales);
    const canViewRevenue = canAccess(currentUser, PAGE_ACCESS.reportRevenue);
    const canViewInventory = canAccess(currentUser, PAGE_ACCESS.reportInventory);
    const canViewProductSales = canAccess(currentUser, PAGE_ACCESS.reportProductSales);
    const canOpenStockAlerts = canAccess(currentUser, PAGE_ACCESS.stockAlerts);
    const canOpenProductReport = canAccess(currentUser, PAGE_ACCESS.reportProductSales);

    const metrics = useMemo(() => {
        if (!dashboard) return [];
        const visibleMetrics = [];
        if (canViewSales) visibleMetrics.push(
            { icon: ShoppingCart, label: 'Sales today', value: formatCurrency(dashboard.todaySales), note: 'Completed sales today', tone: 'blue' }
        );
        if (canViewRevenue) visibleMetrics.push(
            { icon: TrendingUp, label: 'Month-to-date revenue', value: formatCurrency(dashboard.monthToDateGrossRevenue), note: 'Gross revenue this month', tone: 'green' }
        );
        if (canViewInventory) visibleMetrics.push(
            { icon: Boxes, label: 'Active SKUs', value: formatNumber(dashboard.activeCatalogSkus), note: 'Products available in catalog', tone: 'violet' },
            { icon: ShieldCheck, label: 'In-stock health', value: `${Number(dashboard.inStockHealth || 0).toFixed(1)}%`, note: `${alerts.length} item${alerts.length === 1 ? '' : 's'} need attention`, tone: alerts.length ? 'amber' : 'green' }
        );
        return visibleMetrics;
    }, [alerts.length, canViewInventory, canViewRevenue, canViewSales, dashboard]);

    const maxTrend = Math.max(...(dashboard?.salesTrend || []).map((point) => Number(point.sales || 0)), 1);

    if (loading && !dashboard) {
        return (
            <div className="dashboard-loading">
                <RefreshCw size={28} className="dashboard-spinner" />
                <strong>Preparing your dashboard…</strong>
                <span>Loading the latest store activity.</span>
            </div>
        );
    }

    return (
        <div className="role-dashboard">
            <header className={`dashboard-hero dashboard-hero--${role.toLowerCase()}`}>
                <div>
                    <span className="dashboard-eyebrow"><LayoutDashboard size={15} /> {content.label}</span>
                    <h1>{content.title}</h1>
                    <p>Welcome back, {displayName}. {content.description}</p>
                </div>
                <button type="button" onClick={() => void loadDashboard()} disabled={loading}>
                    <RefreshCw size={16} className={loading ? 'dashboard-spinner' : ''} />
                    Refresh data
                </button>
            </header>

            {error && (
                <div className="dashboard-error" role="alert">
                    <AlertTriangle size={18} />
                    <span>{error}</span>
                    <button type="button" onClick={() => void loadDashboard()}>Try again</button>
                </div>
            )}

            {metrics.length > 0 && (
                <section className="dashboard-metrics" aria-label="Store metrics">
                    {metrics.map((metric) => <MetricCard key={metric.label} {...metric} />)}
                </section>
            )}

            <section className="dashboard-section">
                <div className="dashboard-section-heading">
                    <div>
                        <span>Shortcuts</span>
                        <h2>Quick actions</h2>
                    </div>
                    <p>Common tasks for your role</p>
                </div>
                <div className="dashboard-actions">
                    {availableActions.map(({ icon: Icon, label, description, path, primary }) => (
                        <Link key={path} to={path} className={primary ? 'dashboard-action dashboard-action--primary' : 'dashboard-action'}>
                            <span><Icon size={20} /></span>
                            <div><strong>{label}</strong><small>{description}</small></div>
                            <ArrowRight size={17} />
                        </Link>
                    ))}
                </div>
            </section>

            <div className="dashboard-content-grid">
                {canViewSales && <section className="dashboard-card dashboard-trend-card">
                        <div className="dashboard-card-heading">
                            <div><span>Last 7 days</span><h2>Sales trend</h2></div>
                            <LineChart size={20} />
                        </div>
                        {(dashboard?.salesTrend || []).length === 0 ? <EmptyState>No completed sales in the last seven days.</EmptyState> : (
                            <div className="dashboard-trend">
                                {dashboard.salesTrend.map((point) => (
                                    <div className="dashboard-trend-column" key={point.date} title={`${point.date}: ${formatCurrency(point.sales)}`}>
                                        <strong>{formatCurrency(point.sales)}</strong>
                                        <div><i style={{ height: `${Math.max((Number(point.sales || 0) / maxTrend) * 100, 5)}%` }} /></div>
                                        <span>{new Date(`${point.date}T00:00:00`).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                </section>}

                {canViewInventory && <section className="dashboard-card">
                    <div className="dashboard-card-heading">
                        <div><span>Inventory risk</span><h2>Needs attention</h2></div>
                        <AlertTriangle size={20} />
                    </div>
                    {alerts.length === 0 ? <EmptyState>Inventory is healthy. No urgent stock alerts.</EmptyState> : (
                        <div className="dashboard-alert-list">
                            {alerts.map((item) => (
                                <div key={item.productId} className="dashboard-alert-item">
                                    <span className={`dashboard-stock-dot dashboard-stock-dot--${item.status.toLowerCase()}`} />
                                    <div><strong>{item.productName}</strong><small>Minimum: {formatNumber(item.minimumStockLevel)}</small></div>
                                    <span>{formatNumber(item.currentStock)}</span>
                                </div>
                            ))}
                        </div>
                    )}
                    {canOpenStockAlerts && <Link className="dashboard-card-link" to="/inventory/stock-alerts">View all stock alerts <ArrowRight size={15} /></Link>}
                </section>}

                {canViewProductSales && <section className="dashboard-card dashboard-card--wide">
                        <div className="dashboard-card-heading">
                            <div><span>This month</span><h2>Top-selling products</h2></div>
                            <BarChart3 size={20} />
                        </div>
                        {(dashboard?.topProducts || []).length === 0 ? <EmptyState>No product sales recorded this month.</EmptyState> : (
                            <div className="dashboard-top-products">
                                {dashboard.topProducts.map((product, index) => (
                                    <div key={product.productId}>
                                        <span className="dashboard-rank">{String(index + 1).padStart(2, '0')}</span>
                                        <div><strong>{product.productName}</strong><small>{formatNumber(product.quantitySold)} units sold</small></div>
                                        <strong>{formatCurrency(product.netSales)}</strong>
                                    </div>
                                ))}
                            </div>
                        )}
                        {canOpenProductReport && <Link className="dashboard-card-link" to="/reports/product-sales">Open product sales report <ArrowRight size={15} /></Link>}
                </section>}
            </div>
        </div>
    );
}
