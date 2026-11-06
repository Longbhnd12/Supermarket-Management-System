import { useMemo } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/authContext';
import { LOGIN_NOTICES } from '../utils/authRedirect';
import '../pages/auth/AuthPage.css';
import './AppNotice.css';

export default function ProtectedRoute() {
    const { status, reason, currentUser, refreshUser, retry } = useAuth();
    const location = useLocation();

    // Kept for pages that still read useOutletContext(); new code should use useAuth().
    const context = useMemo(() => ({ currentUser, refreshUser }), [currentUser, refreshUser]);

    if (status === 'loading') return <div className="auth-loading">Checking your session…</div>;

    // Backend down / 5xx on the first check: do not send the user to Login, let them try again.
    if (status === 'unreachable') {
        return (
            <div className="auth-loading">
                <div className="auth-unreachable" role="alert">
                    <strong>Cannot connect to server</strong>
                    <p>Your session was not checked. Please make sure the server is running and try again.</p>
                    <button type="button" onClick={retry}>Try again</button>
                </div>
            </div>
        );
    }

    if (status !== 'authenticated') {
        // Session expired: come back to this page after logging in again.
        const state = reason === 'expired'
            ? { notice: LOGIN_NOTICES.expired, noticeTone: 'warning', from: location.pathname + location.search }
            : (LOGIN_NOTICES[reason] ? { notice: LOGIN_NOTICES[reason], noticeTone: 'warning' } : undefined);
        return <Navigate to="/login" replace state={state} />;
    }

    // UC48: the home page of a Supplier is the Supplier portal, not the staff dashboard
    if (currentUser?.role === 'SUPPLIER' && location.pathname === '/') {
        return <Navigate to="/supplier/purchase-orders" replace />;
    }
    return <Outlet context={context} />;
}
