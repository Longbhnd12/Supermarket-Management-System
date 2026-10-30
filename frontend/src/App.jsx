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
                    <Route path="*" element={<NotFoundPage />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}
