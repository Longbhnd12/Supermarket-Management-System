import { NavLink } from 'react-router-dom';

import {
    LayoutDashboard,
    Package,
    Warehouse,
    ShoppingCart,
    Truck,
    Tags,
    Users,
    UserCog,
    BarChart3,
    Settings,
    ClipboardList,
    Store,
    PanelLeftClose,
    PanelLeftOpen,
    FileInput,
    X
} from 'lucide-react';

const menuGroups = [
    {
        title: 'OVERVIEW',
        items: [
            {
                label: 'Dashboard',
                icon: LayoutDashboard,
                path: '/'
            }
        ]
    },
    {
        title: 'OPERATIONS',
        items: [
            {
                label: 'Product Management',
                icon: Package,
                path: '/products'
            },
            {
                label: 'Purchase & Inventory',
                icon: Warehouse,
                path: '/inventory',
                end: true
            },
            {
                label: 'Stock Receipts',
                icon: FileInput,
                path: '/inventory/stock-receipts'
            },
            {
                label: 'Sales & POS',
                icon: ShoppingCart,
                path: '/sales'
            },
            {
                label: 'Supplier Management',
                icon: Truck,
                path: '/suppliers'
            },
            {
                label: 'Promotion Management',
                icon: Tags,
                path: '/promotions'
            }
        ]
    },
    {
        title: 'ADMINISTRATION',
        items: [
            {
                label: 'Employee Management',
                icon: Users,
                path: '/employees'
            },
            {
                label: 'User Management',
                icon: UserCog,
                path: '/users'
            },
            {
                label: 'Reports',
                icon: BarChart3,
                path: '/reports/sales'
            },
            {
                label: 'System Settings',
                icon: Settings,
                path: '/system-settings'
            },
            {
                label: 'System Logs',
                icon: ClipboardList,
                path: '/system-logs'
            }
        ]
    }
];

export default function Sidebar({
    collapsed,
    setCollapsed,
    mobileOpen,
    setMobileOpen
}) {
    return (
        <>
            {mobileOpen && (
                <div
                    className="sms-sidebar-overlay"
                    onClick={() => setMobileOpen(false)}
                />
            )}

            <aside
                className={[
                    'sms-sidebar',
                    collapsed ? 'sms-sidebar--collapsed' : '',
                    mobileOpen ? 'sms-sidebar--mobile-open' : ''
                ].filter(Boolean).join(' ')}
            >
                <div className="sms-sidebar-brand">
                    <div className="sms-brand-logo">
                        <Store size={23} />
                    </div>

                    {!collapsed && (
                        <div className="sms-brand-text">
                            <strong>RETAIL SMS</strong>
                            <span>Store Operations</span>
                        </div>
                    )}

                    <button
                        type="button"
                        className="sms-sidebar-toggle"
                        onClick={() => setCollapsed(!collapsed)}
                        aria-label="Toggle sidebar"
                    >
                        {collapsed
                            ? <PanelLeftOpen size={19} />
                            : <PanelLeftClose size={19} />}
                    </button>

                    <button
                        type="button"
                        className="sms-mobile-close"
                        onClick={() => setMobileOpen(false)}
                        aria-label="Close menu"
                    >
                        <X size={20} />
                    </button>
                </div>

                <nav className="sms-sidebar-nav">
                    {menuGroups.map((group) => (
                        <div
                            className="sms-menu-group"
                            key={group.title}
                        >
                            {!collapsed && (
                                <div className="sms-menu-title">
                                    {group.title}
                                </div>
                            )}

                            {group.items.map((item) => {
                                const Icon = item.icon;

                                if (!item.path) {
                                    return (
                                        <div
                                            key={item.label}
                                            className="sms-menu-item sms-menu-item--disabled"
                                            title={`${item.label} - Coming soon`}
                                            aria-disabled="true"
                                        >
                                            <Icon size={19} />
                                            {!collapsed && (
                                                <span>{item.label}</span>
                                            )}
                                        </div>
                                    );
                                }

                                return (
                                    <NavLink
                                        key={item.label}
                                        to={item.path}
                                        end={item.end ?? item.path === '/'}
                                        title={item.label}
                                        onClick={() => setMobileOpen(false)}
                                        className={({ isActive }) =>
                                            `sms-menu-item ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        <Icon size={19} />

                                        {!collapsed && (
                                            <span>{item.label}</span>
                                        )}
                                    </NavLink>
                                );
                            })}
                        </div>
                    ))}
                </nav>

                <div className="sms-sidebar-bottom">
                    <div className="sms-system-indicator">
                        <span className="sms-status-dot" />

                        {!collapsed && (
                            <div>
                                <strong>SMS Workspace</strong>
                                <small>Development Mode</small>
                            </div>
                        )}
                    </div>
                </div>
            </aside>
        </>
    );
}
