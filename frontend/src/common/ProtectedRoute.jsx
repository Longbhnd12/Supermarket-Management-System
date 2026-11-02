import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import authService from '../services/authService';

export default function ProtectedRoute() {
    const [state, setState] = useState({ loading: true, authenticated: false });

    useEffect(() => {
        let active = true;
        authService.getMe()
            .then((response) => active && setState({ loading: false, authenticated: response?.success === true }))
            .catch(() => active && setState({ loading: false, authenticated: false }));
        return () => { active = false; };
    }, []);

    if (state.loading) return <div className="auth-loading">Checking your session…</div>;
    return state.authenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
