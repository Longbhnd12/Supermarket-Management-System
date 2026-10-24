import { Link, useLocation } from 'react-router-dom';
import { ChevronRight, Home } from 'lucide-react';

const routeLabels = {
    dashboard: 'Dashboard',
    products: 'Product Management',
    inventory: 'Inventory Management',
    'adjust-stock': 'Adjust Stock',
    'stock-check': 'Stock Check',
    'stock-history': 'Stock History',
    sales: 'Sales Management',
    pos: 'POS',
    suppliers: 'Supplier Management',
    'purchase-orders': 'Purchase Orders',
    'create-request': 'Create Purchase Request',
    employees: 'Employee Management',
    users: 'User Management',
    reports: 'Reports',
    settings: 'System Settings'
};

export default function Breadcrumb() {
    const location = useLocation();

    const segments = location.pathname
        .split('/')
        .filter(Boolean);

    return (
        <nav
            className="sms-breadcrumb"
            aria-label="Breadcrumb"
        >
            <Link to="/" className="sms-breadcrumb-home">
                <Home size={16} />
                <span>Home</span>
            </Link>

            {segments.map((segment, index) => {
                const path = '/' + segments
                    .slice(0, index + 1)
                    .join('/');

                const label =
                    routeLabels[segment] ||
                    segment.replace(/-/g, ' ');

                const isLast = index === segments.length - 1;

                return (
                    <div
                        className="sms-breadcrumb-part"
                        key={path}
                    >
                        <ChevronRight size={15} />

                        {isLast ? (
                            <span className="sms-breadcrumb-current">
                                {label}
                            </span>
                        ) : (
                            <Link to={path}>
                                {label}
                            </Link>
                        )}
                    </div>
                );
            })}
        </nav>
    );
}