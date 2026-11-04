import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import authService from '../services/authService';

export default function ProtectedRoute() {
    const [state, setState] = useState({ loading: true, user: null });

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

    if (state.loading) return <div className="auth-loading">Checking your session…</div>;
    // The logged-in user is shared with MainLayout -> Header (account menu, UC02)
    return state.user ? <Outlet context={{ currentUser: state.user }} /> : <Navigate to="/login" replace />;
}
