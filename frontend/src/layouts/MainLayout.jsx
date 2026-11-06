import { useState } from 'react';
import { Outlet, useOutletContext } from 'react-router-dom';

import Header from '../common/Header';
import Sidebar from '../common/Sidebar';
import Breadcrumb from '../common/Breadcrumb';
import Footer from '../common/Footer';

import './MainLayout.css';

export default function MainLayout() {
    const [collapsed, setCollapsed] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    // { currentUser, refreshUser } from ProtectedRoute, passed on to pages that still use useOutletContext()
    const outletContext = useOutletContext() ?? {};

    return (
        <div className="sms-app-layout">
            <Sidebar
                collapsed={collapsed}
                setCollapsed={setCollapsed}
                mobileOpen={mobileOpen}
                setMobileOpen={setMobileOpen}
            />

            <div className="sms-main-wrapper">
                <Header
                    onMenuClick={() => {
                        setMobileOpen(true);
                    }}
                />

                <main className="sms-main-content">
                    <Breadcrumb />

                    <div className="sms-page-container">
                        <Outlet context={outletContext} />
                    </div>
                </main>

                <Footer />
            </div>
        </div>
    );
}
