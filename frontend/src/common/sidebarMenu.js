import {
    AlertTriangle,
    BarChart3,
    Bell,
    ClipboardList,
    FileCheck,
    FileInput,
    FileText,
    History,
    LayoutDashboard,
    LineChart,
    Package,
    PackageSearch,
    Settings,
    ShieldCheck,
    ShoppingCart,
    Tags,
    TrendingUp,
    Truck,
    UserCog,
    Users,
    Warehouse
} from 'lucide-react';
import { PAGE_ACCESS, canAccess } from '../utils/pageAccess.js';

// Sidebar menu. Each item is shown only if the user can open its page (same rule as the route guard).
// A group with no visible item is hidden.
export const MENU_GROUPS = [
    {
        title: 'OVERVIEW',
        items: [
            { label: 'Dashboard', icon: LayoutDashboard, path: '/', access: PAGE_ACCESS.dashboard }
        ]
    },
    {
        title: 'OPERATIONS',
        items: [
            { label: 'Product Management', icon: Package, path: '/products', access: PAGE_ACCESS.products },
            { label: 'Purchase & Inventory', icon: Warehouse, path: '/inventory', end: true, access: PAGE_ACCESS.inventory },
            { label: 'Purchase Orders', icon: FileText, path: '/purchase-orders', end: true, access: PAGE_ACCESS.purchaseOrders },
            { label: 'Approve Requests', icon: FileCheck, path: '/purchase-orders/pending', end: true, access: PAGE_ACCESS.purchaseRequestApprove },
            { label: 'Stock Receipts', icon: FileInput, path: '/inventory/stock-receipts', access: PAGE_ACCESS.stockReceipts },
            { label: 'Stock History', icon: History, path: '/inventory/stock-history', end: true, access: PAGE_ACCESS.stockHistory },
            { label: 'Stock Alerts', icon: AlertTriangle, path: '/inventory/stock-alerts', end: true, access: PAGE_ACCESS.stockAlerts },
            { label: 'Sales & POS', icon: ShoppingCart, path: '/sales/pos', access: PAGE_ACCESS.pos },
            { label: 'Search invoices', icon: FileText, path: '/invoices', access: PAGE_ACCESS.invoices },
            { label: 'Supplier Management', icon: Truck, path: '/suppliers', access: PAGE_ACCESS.suppliers },
            { label: 'Promotion Management', icon: Tags, path: '/promotions', access: PAGE_ACCESS.promotions }
        ]
    },
    {
        title: 'REPORTS',
        items: [
            { label: 'Sales Report', icon: BarChart3, path: '/reports/sales', access: PAGE_ACCESS.reportSales },
            { label: 'Revenue Report', icon: TrendingUp, path: '/reports/revenue', access: PAGE_ACCESS.reportRevenue },
            { label: 'Product Sales Report', icon: LineChart, path: '/reports/product-sales', access: PAGE_ACCESS.reportProductSales },
            { label: 'Inventory Report', icon: PackageSearch, path: '/reports/inventory', access: PAGE_ACCESS.reportInventory }
        ]
    },
    {
        title: 'ADMINISTRATION',
        items: [
            { label: 'Employee Management', icon: Users, path: '/employees', access: PAGE_ACCESS.employees },
            { label: 'User Management', icon: UserCog, path: '/users', access: PAGE_ACCESS.users },
            { label: 'Roles & Permissions', icon: ShieldCheck, path: '/roles', access: PAGE_ACCESS.roles },
            { label: 'System Settings', icon: Settings, path: '/system-settings', access: PAGE_ACCESS.systemSettings },
            { label: 'System Logs', icon: ClipboardList, path: '/system-logs', access: PAGE_ACCESS.systemLogs }
        ]
    },
    {
        // UC48/UC49: what a Supplier account sees
        title: 'SUPPLIER PORTAL',
        items: [
            { label: 'Purchase Orders', icon: FileText, path: '/supplier/purchase-orders', access: PAGE_ACCESS.supplierPortal },
            { label: 'Notifications', icon: Bell, path: '/notifications', access: { roles: ['SUPPLIER'], anyOf: ['NOTIFICATION_VIEW'] } }
        ]
    }
];

export function menuFor(user, groups = MENU_GROUPS) {
    return groups
        .map((group) => ({ ...group, items: group.items.filter((item) => canAccess(user, item.access)) }))
        .filter((group) => group.items.length > 0);
}
