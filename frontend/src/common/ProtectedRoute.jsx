import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import authService from '../services/authService';

export default function ProtectedRoute() {
    const [state, setState] = useState({ loading: true, user: null });
    const location = useLocation();

    const loadUser = useCallback(() => authService.getMe()
        .then((response) => setState({ loading: false, user: response?.success ? response.data : null }))
        .catch(() => setState({ loading: false, user: null })), []);

    useEffect(() => {
        let active = true;
        authService.getMe()
            .then((response) => active && setState({
                loading: false,
                user: response?.success ? response.data : null
            }))
            .catch(() => active && setState({ loading: false, user: null }));
        return () => { active = false; };
    }, []);

    // Shared with MainLayout -> Header (UC02) and the pages (UC44 refreshes it after a profile change)
    const context = useMemo(() => ({ currentUser: state.user, refreshUser: loadUser }), [state.user, loadUser]);

    if (state.loading) return <div className="auth-loading">Checking your session…</div>;
    // UC48: the home page of a Supplier is the Supplier portal, not the staff dashboard
    if (state.user?.role === 'SUPPLIER' && location.pathname === '/') {
        return <Navigate to="/supplier/purchase-orders" replace />;
    }
    return state.user ? <Outlet context={context} /> : <Navigate to="/login" replace />;
}
