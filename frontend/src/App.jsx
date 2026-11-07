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
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ProtectedRoute from './common/ProtectedRoute';
import AppNotice from './common/AppNotice';
import AuthProvider from './context/AuthProvider';
import RequirePermission from './common/RequirePermission';
import { PAGE_ACCESS } from './utils/pageAccess';
import InvoiceListPage from './pages/sales/InvoiceListPage';

// Product
import ProductListPage from './pages/product/ProductListPage';
import ProductDetailPage from './pages/product/ProductDetailPage';
import ProductFormPage from './pages/product/ProductFormPage';
import PriceManagementPage from './pages/product/PriceManagementPage';

// Category
import CategoryListPage from './pages/category/CategoryListPage';

// Sales
import SaleListPage from './pages/sales/SaleListPage';
import POSPage from './pages/sales/POSPage';
import InvoicePage from './pages/sales/InvoicePage';
import PaymentReturnPage from './pages/sales/PaymentReturnPage';

// Purchase Orders
import PurchaseOrderListPage from './pages/purchaseOrder/PurchaseOrderListPage';
import PurchaseOrderDetailPage from './pages/purchaseOrder/PurchaseOrderDetailPage';
import CreatePurchaseRequestPage from './pages/purchaseOrder/CreatePurchaseRequestPage';
import PendingPurchaseRequestsPage from './pages/purchaseOrder/PendingPurchaseRequestsPage';

// Inventory
import InventoryPage from './pages/inventory/InventoryPage';
import AdjustStockPage from './pages/inventory/AdjustStockPage';
import StockCheckPage from './pages/inventory/StockCheckPage';
import StockDifferencePage from './pages/inventory/StockDifferencePage';
import StockHistoryPage from './pages/inventory/StockHistoryPage';
import StockAlertsPage from './pages/inventory/StockAlertsPage';
import StockReceiptListPage from './pages/inventory/StockReceiptListPage';
import StockReceiptDetailPage from './pages/inventory/StockReceiptDetailPage';
import CreateStockReceiptPage from './pages/inventory/CreateStockReceiptPage';

// Supplier
import SupplierListPage from './pages/supplier/SupplierListPage';
import SupplierDetailPage from './pages/supplier/SupplierDetailPage';
import SupplierFormPage from './pages/supplier/SupplierFormPage';

// Promotion
import PromotionListPage from './pages/promotion/PromotionListPage';

// Employee
import EmployeeListPage from './pages/employee/EmployeeListPage';
import EmployeeDetailPage from './pages/employee/EmployeeDetailPage';
import EmployeeFormPage from './pages/employee/EmployeeFormPage';

// User
import UserListPage from './pages/user/UserListPage';
import UserFormPage from './pages/user/UserFormPage';
import RolePermissionsPage from './pages/user/RolePermissionsPage';
import ProfilePage from './pages/account/ProfilePage';
import ChangePasswordPage from './pages/account/ChangePasswordPage';
import SupplierOrderListPage from './pages/supplierPortal/SupplierOrderListPage';
import SupplierOrderDetailPage from './pages/supplierPortal/SupplierOrderDetailPage';

import './App.css';

export default function App() {
    return (
        <BrowserRouter>
            {/* Login state + central 401/403 handling for every page (useAuth()) */}
            <AuthProvider>
            <AppNotice />
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                <Route element={<ProtectedRoute />}>
                <Route element={<MainLayout />}>
                    {/* Each group: only for the roles with the permission (utils/pageAccess.js).
                        Without it the Access Denied page is shown. */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.dashboard} />}>
                        <Route path="/" element={<HomePage />} />
                    </Route>

                    {/* Product Management */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.products} />}>
                        <Route path="/products" element={<ProductListPage />} />
                        <Route path="/products/:id" element={<ProductDetailPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.productForm} />}>
                        <Route path="/products/create" element={<ProductFormPage />} />
                        <Route path="/products/:id/edit" element={<ProductFormPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.priceManage} />}>
                        <Route path="/products/:id/price" element={<PriceManagementPage />} />
                    </Route>

                    {/* Category Management (UC11) */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.categories} />}>
                        <Route path="/categories" element={<CategoryListPage />} />
                    </Route>

                    {/* Sales & POS */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.salesMonitoring} />}>
                        <Route path="/sales" element={<SaleListPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.pos} />}>
                        <Route path="/sales/pos" element={<POSPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.invoices} />}>
                        <Route path="/invoices" element={<InvoiceListPage />} />
                        <Route path="/sales/:id/invoice" element={<InvoicePage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.paymentReturn} />}>
                        <Route path="/payments/vnpay/return" element={<PaymentReturnPage />} />
                    </Route>

                    {/* Purchase Orders */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.purchaseOrders} />}>
                        <Route path="/purchase-orders" element={<PurchaseOrderListPage />} />
                        <Route path="/purchase-orders/:id" element={<PurchaseOrderDetailPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.purchaseRequestCreate} />}>
                        <Route path="/purchase-orders/create" element={<CreatePurchaseRequestPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.purchaseRequestApprove} />}>
                        <Route path="/purchase-orders/pending" element={<PendingPurchaseRequestsPage />} />
                    </Route>

                    {/* Inventory */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.inventory} />}>
                        <Route path="/inventory" element={<InventoryPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockCheck} />}>
                        <Route path="/inventory/stock-check" element={<StockCheckPage />} />
                        <Route path="/inventory/stock-checks" element={<StockCheckPage />} />
                        <Route path="/inventory/stock-difference" element={<StockDifferencePage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockAdjust} />}>
                        <Route path="/inventory/adjust" element={<AdjustStockPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockHistory} />}>
                        <Route path="/inventory/stock-history" element={<StockHistoryPage />} />
                        <Route path="/inventory/history" element={<StockHistoryPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockAlerts} />}>
                        <Route path="/inventory/stock-alerts" element={<StockAlertsPage />} />
                        <Route path="/inventory/alerts" element={<StockAlertsPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockReceipts} />}>
                        <Route path="/inventory/stock-receipts" element={<StockReceiptListPage />} />
                        <Route path="/inventory/stock-receipts/:id" element={<StockReceiptDetailPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.stockReceiptForm} />}>
                        <Route path="/inventory/stock-receipts/create" element={<CreateStockReceiptPage />} />
                        <Route path="/inventory/stock-receipts/:id/edit" element={<CreateStockReceiptPage />} />
                    </Route>

                    {/* Suppliers, promotions */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.suppliers} />}>
                        <Route path="/suppliers" element={<SupplierListPage />} />
                        <Route path="/suppliers/:id" element={<SupplierDetailPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.supplierForm} />}>
                        <Route path="/suppliers/create" element={<SupplierFormPage />} />
                        <Route path="/suppliers/:id/edit" element={<SupplierFormPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.promotions} />}>
                        <Route path="/promotions" element={<PromotionListPage />} />
                    </Route>

                    {/* Administration */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.employeeForm} />}>
                        <Route path="/employees/create" element={<EmployeeFormPage />} />
                        <Route path="/employees/:id/edit" element={<EmployeeFormPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.employees} />}>
                        <Route path="/employees" element={<EmployeeListPage />} />
                        <Route path="/employees/:id" element={<EmployeeDetailPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.users} />}>
                        <Route path="/users" element={<UserListPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.userForm} />}>
                        <Route path="/users/new" element={<UserFormPage />} />
                        <Route path="/users/:id/edit" element={<UserFormPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.roles} />}>
                        <Route path="/roles" element={<RolePermissionsPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.systemSettings} />}>
                        <Route path="/system-settings" element={<SystemSettingsPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.systemLogs} />}>
                        <Route path="/system-logs" element={<SystemLogsPage />} />
                    </Route>

                    {/* Reports */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.reportSales} />}>
                        <Route path="/reports/sales" element={<SalesReportPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.reportInventory} />}>
                        <Route path="/reports/inventory" element={<InventoryReportPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.reportRevenue} />}>
                        <Route path="/reports/revenue" element={<RevenueReportPage />} />
                    </Route>
                    <Route element={<RequirePermission access={PAGE_ACCESS.reportProductSales} />}>
                        <Route path="/reports/product-sales" element={<ProductSalesReportPage />} />
                    </Route>

                    {/* Supplier portal (UC48, UC49) */}
                    <Route element={<RequirePermission access={PAGE_ACCESS.supplierPortal} />}>
                        <Route path="/supplier/purchase-orders" element={<SupplierOrderListPage />} />
                        <Route path="/supplier/purchase-orders/:id" element={<SupplierOrderDetailPage />} />
                    </Route>

                    <Route element={<RequirePermission access={PAGE_ACCESS.notifications} />}>
                        <Route path="/notifications" element={<NotificationsPage />} />
                    </Route>

                    {/* Account (UC44, UC03): every logged-in account */}
                    <Route path="/profile" element={<ProfilePage />} />
                    <Route path="/change-password" element={<ChangePasswordPage />} />

                    <Route path="*" element={<NotFoundPage />} />
                </Route>
                </Route>
            </Routes>
            </AuthProvider>
        </BrowserRouter>
    );
}
