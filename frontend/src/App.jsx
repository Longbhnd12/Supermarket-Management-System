import {
    BrowserRouter,
    Routes,
    Route
} from 'react-router-dom';

import MainLayout from './layouts/MainLayout';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import SystemSettingsPage from './pages/system/SystemSettingsPage';
import SystemLogsPage from './pages/system/SystemLogsPage';
import SaleListPage from './pages/sales/SaleListPage';
import SalesReportPage from './pages/reports/SalesReportPage';
import InventoryReportPage from './pages/reports/InventoryReportPage';
import RevenueReportPage from './pages/reports/RevenueReportPage';
import ProductSalesReportPage from './pages/reports/ProductSalesReportPage';
import NotificationsPage from './pages/notifications/NotificationsPage';

import './App.css';

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route element={<MainLayout />}>
                    <Route path="/" element={<HomePage />} />
                    <Route path="/system-settings" element={<SystemSettingsPage />} />
                    <Route path="/system-logs" element={<SystemLogsPage />} />
                    <Route path="/sales" element={<SaleListPage />} />
                    <Route path="/reports/sales" element={<SalesReportPage />} />
                    <Route path="/reports/inventory" element={<InventoryReportPage />} />
                    <Route path="/reports/revenue" element={<RevenueReportPage />} />
                    <Route path="/reports/product-sales" element={<ProductSalesReportPage />} />
                    <Route path="/notifications" element={<NotificationsPage />} />
                    <Route path="*" element={<NotFoundPage />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}
