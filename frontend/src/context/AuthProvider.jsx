import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import authService from '../services/authService';
import { broadcastAuthChange, onAuthEvent, onOtherTabAuthChange } from '../services/authEvents';
import { getHomePathByRole } from '../utils/authRedirect';
import { hasAnyPermission, hasPermission, hasRole } from '../utils/permissions';
import { AuthContext } from './authContext';
import {
    INITIAL_AUTH_STATE,
    applySessionResult,
    applyUnauthorized,
    sessionResultFromError,
    sessionResultFromResponse
} from './authState';

// Login state of the whole app (one place instead of every page calling /auth/me).
//
// status
//   'loading'          first check of the session cookie (GET /auth/me)
//   'authenticated'    currentUser is set
//   'unauthenticated'  no valid session -> ProtectedRoute sends the user to /login
//   'unreachable'      the first check failed because the backend is down or returned 5xx.
//                      The user is NOT logged out: ProtectedRoute shows a "Try again" screen.
// reason (why the user is unauthenticated, used for the message on the Login page)
//   'expired'   a request got 401 while logged in
//   'logout'    the user clicked Logout in this tab
//   'other-tab' the user logged out in another tab
const REVALIDATE_AFTER_MS = 15_000; // when the tab gets focus again

export default function AuthProvider({ children }) {
    const [state, setState] = useState(INITIAL_AUTH_STATE);
    // Every login/logout/refresh gets a number: an older /auth/me answer that arrives late is ignored.
    const requestSeq = useRef(0);
    const lastCheckAt = useRef(0);
    const currentUserId = useRef(null);

    useEffect(() => {
        currentUserId.current = state.user?.userId ?? null;
    }, [state.user]);

    // GET /auth/me. Resolves to { seq, result }; the caller applies it with applyResult.
    const checkSession = useCallback(() => {
        const seq = ++requestSeq.current;
        return authService.getMe()
            .then(sessionResultFromResponse, sessionResultFromError)
            .then((result) => ({ seq, result }));
    }, []);

    // Ignore answers that arrived after a newer login/logout/refresh.
    const applyResult = useCallback(({ seq, result }) => {
        if (seq !== requestSeq.current) return null;
        lastCheckAt.current = Date.now();
        setState((prev) => applySessionResult(prev, result));
        return result.user ?? null;
    }, []);

    // Returns the user, or null when not logged in.
    const loadMe = useCallback(() => checkSession().then(applyResult), [checkSession, applyResult]);

    const clear = useCallback((reason = null) => {
        requestSeq.current += 1;
        setState({ status: 'unauthenticated', user: null, reason });
    }, []);

    // UC01: POST /auth/login sets the HttpOnly cookie, then /auth/me returns the user with permissions.
    const login = useCallback(async (credentials) => {
        const response = await authService.login(credentials);
        if (!response?.success) throw new Error(response?.message || 'Login failed.');
        const seq = ++requestSeq.current;
        let user = response.data;
        try {
            const me = await authService.getMe();
            if (me?.success) user = me.data;
        } catch {
            // keep the login response; permissions are loaded on the next refresh
        }
        if (seq === requestSeq.current) {
            lastCheckAt.current = Date.now();
            setState({ status: 'authenticated', user, reason: null });
        }
        broadcastAuthChange('login');
        return user;
    }, []);

    // UC02: the backend revokes the token and clears the cookie. Even if that request fails
    // (already expired, server down) this tab and the other tabs are logged out.
    const logout = useCallback(async () => {
        try {
            await authService.logout();
        } catch {
            // ignore: the local state is cleared anyway
        }
        clear('logout');
        broadcastAuthChange('logout');
    }, [clear]);

    // UC44: after a profile change the header (in every tab) shows the new name and email.
    const refreshUser = useCallback(async () => {
        const user = await loadMe();
        broadcastAuthChange('refresh');
        return user;
    }, [loadMe]);

    const retry = useCallback(() => {
        setState(INITIAL_AUTH_STATE);
        return loadMe();
    }, [loadMe]);

    // First check of the session cookie
    useEffect(() => {
        checkSession().then(applyResult);
    }, [checkSession, applyResult]);

    // 401 from any request (api.js) -> logged out, the Login page says the session expired.
    useEffect(() => onAuthEvent((event) => {
        if (event.type !== 'unauthorized') return;
        requestSeq.current += 1;
        setState(applyUnauthorized);
    }), []);

    // Other tabs of the same browser share the cookie, so they must follow login/logout here.
    useEffect(() => onOtherTabAuthChange(async (type) => {
        if (type === 'logout') {
            clear('other-tab');
            return;
        }
        if (type === 'login' || type === 'refresh') {
            const before = currentUserId.current;
            const user = await loadMe();
            // Another account logged in from another tab: reload so no page keeps the old user's data
            if (type === 'login' && before && user && user.userId !== before) {
                window.location.assign(getHomePathByRole(user.role));
            }
        }
    }), [clear, loadMe]);

    // Coming back to the tab: check that the session is still valid (expired, account locked...).
    useEffect(() => {
        const revalidate = () => {
            if (document.visibilityState !== 'visible' || !currentUserId.current) return;
            if (Date.now() - lastCheckAt.current < REVALIDATE_AFTER_MS) return;
            loadMe();
        };
        document.addEventListener('visibilitychange', revalidate);
        window.addEventListener('focus', revalidate);
        return () => {
            document.removeEventListener('visibilitychange', revalidate);
            window.removeEventListener('focus', revalidate);
        };
    }, [loadMe]);

    const value = useMemo(() => {
        const user = state.user;
        return {
            status: state.status,
            reason: state.reason,
            currentUser: user,
            isLoading: state.status === 'loading',
            isAuthenticated: state.status === 'authenticated',
            permissions: user?.permissions ?? [],
            hasPermission: (permission) => hasPermission(user, permission),
            hasAnyPermission: (permissions) => hasAnyPermission(user, permissions),
            hasRole: (...roles) => hasRole(user, ...roles),
            login,
            logout,
            refreshUser,
            retry,
            clear
        };
    }, [state, login, logout, refreshUser, retry, clear]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
