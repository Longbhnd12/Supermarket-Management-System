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
import SalesReportPage from './pages/reports/SalesReportPage';
import InventoryReportPage from './pages/reports/InventoryReportPage';
import RevenueReportPage from './pages/reports/RevenueReportPage';
import ProductSalesReportPage from './pages/reports/ProductSalesReportPage';
import NotificationsPage from './pages/notifications/NotificationsPage';

// Product
import ProductListPage from './pages/product/ProductListPage';
import ProductDetailPage from './pages/product/ProductDetailPage';

// Sales
import SaleListPage from './pages/sales/SaleListPage';
import POSPage from './pages/sales/POSPage';

// Purchase Orders
import PurchaseOrderListPage from './pages/purchaseOrder/PurchaseOrderListPage';
import PurchaseOrderDetailPage from './pages/purchaseOrder/PurchaseOrderDetailPage';

// Inventory
import InventoryPage from './pages/inventory/InventoryPage';
import StockReceiptListPage from './pages/inventory/StockReceiptListPage';
import StockReceiptDetailPage from './pages/inventory/StockReceiptDetailPage';
import CreateStockReceiptPage from './pages/inventory/CreateStockReceiptPage';

// Supplier
import SupplierListPage from './pages/supplier/SupplierListPage';

// Promotion
import PromotionListPage from './pages/promotion/PromotionListPage';

// Employee
import EmployeeListPage from './pages/employee/EmployeeListPage';

// User
import UserListPage from './pages/user/UserListPage';

import './App.css';

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route element={<MainLayout />}>
                    <Route path="/" element={<HomePage />} />

                    {/* Product Management */}
                    <Route path="/products" element={<ProductListPage />} />
                    <Route path="/products/:id" element={<ProductDetailPage />} />

                    {/* Sales & POS */}
                    <Route path="/sales" element={<SaleListPage />} />
                    <Route path="/sales/pos" element={<POSPage />} />

                    {/* Purchase Orders */}
                    <Route path="/purchase-orders" element={<PurchaseOrderListPage />} />
                    <Route path="/purchase-orders/:id" element={<PurchaseOrderDetailPage />} />

                    {/* Inventory */}
                    <Route path="/inventory" element={<InventoryPage />} />
                    <Route path="/inventory/stock-receipts" element={<StockReceiptListPage />} />
                    <Route path="/inventory/stock-receipts/create" element={<CreateStockReceiptPage />} />
                    <Route path="/inventory/stock-receipts/:id" element={<StockReceiptDetailPage />} />

                    {/* Suppliers */}
                    <Route path="/suppliers" element={<SupplierListPage />} />

                    {/* Promotions */}
                    <Route path="/promotions" element={<PromotionListPage />} />

                    {/* Administration */}
                    <Route path="/employees" element={<EmployeeListPage />} />
                    <Route path="/users" element={<UserListPage />} />

                    <Route path="/system-settings" element={<SystemSettingsPage />} />
                    <Route path="/system-logs" element={<SystemLogsPage />} />
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
