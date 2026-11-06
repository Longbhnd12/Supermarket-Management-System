// Pure state transitions of AuthProvider (no React, so they are unit-tested with node --test).

export const INITIAL_AUTH_STATE = { status: 'loading', user: null, reason: null };

// Result of GET /auth/me -> { status: 'authenticated', user } | { status: 'unauthenticated' } | { status: 'unreachable' }
export function sessionResultFromResponse(response) {
    const user = response?.success ? response.data : null;
    return user ? { status: 'authenticated', user } : { status: 'unauthenticated' };
}

export function sessionResultFromError(error) {
    return error?.response?.status === 401 ? { status: 'unauthenticated' } : { status: 'unreachable' };
}

// New state after a /auth/me check.
// - 401 while logged in -> reason 'expired' (the Login page explains why)
// - backend down / 5xx -> a logged-in user stays logged in; only the very first check shows "unreachable"
export function applySessionResult(prev, result) {
    if (result.status === 'authenticated') {
        return { status: 'authenticated', user: result.user, reason: null };
    }
    if (result.status === 'unauthenticated') {
        if (prev.status === 'unauthenticated') return prev;
        return { status: 'unauthenticated', user: null, reason: prev.status === 'authenticated' ? 'expired' : null };
    }
    return prev.status === 'authenticated' ? prev : { status: 'unreachable', user: null, reason: null };
}

// A request elsewhere in the app got 401.
export function applyUnauthorized(prev) {
    return prev.status === 'authenticated' ? { status: 'unauthenticated', user: null, reason: 'expired' } : prev;
}
