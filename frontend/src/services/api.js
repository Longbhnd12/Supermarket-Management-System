import axios from 'axios';
import { emitAuthEvent } from './authEvents';

// Authentication = JWT in an HttpOnly cookie (set by the backend, never readable by JavaScript).
// - withCredentials: the browser sends the auth cookie with every request.
// - CSRF (double-submit cookie): the backend sets the XSRF-TOKEN cookie; Axios copies it into the
//   X-XSRF-TOKEN header of every request. withXSRFToken is needed because the API runs on another port.
const API = axios.create({
    baseURL: 'http://localhost:8080/api/v1',
    timeout: 10000,
    withCredentials: true,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
    withXSRFToken: true
});

// A 401 from these endpoints does not go through the 'unauthorized' event:
// - login, forgot/reset password, logout: 401 is a normal answer (wrong password...)
// - /auth/me: AuthProvider reads its answer itself (an event here would cancel that check)
const NO_SESSION_EVENT_PATHS = ['/auth/me', '/auth/login', '/auth/logout', '/auth/forgot-password', '/auth/reset-password'];

function isNoSessionEventRequest(config) {
    const url = (config?.url || '').split('?')[0];
    return NO_SESSION_EVENT_PATHS.some((path) => url === path || url.endsWith(path));
}

// Central error handling. Pages still receive the error (to show it in their own form), but:
//   401           -> AuthProvider logs the user out and the Login page says "session expired"
//   403           -> toast MSG19 "You do not have permission..." (the user stays logged in)
//   403 CSRF      -> the 403 already carries a fresh XSRF-TOKEN cookie and the backend did not run
//                    the action, so the request is sent once more
//   5xx / network -> toast with the error, the user stays logged in
// A request can turn off the toast with: API.get(url, { skipGlobalError: true })
API.interceptors.response.use(undefined, (error) => {
    const { config, response } = error;

    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
        return Promise.reject(error);
    }

    if (config && !config.csrfRetried && response?.status === 403 && response.data?.errorCode === 'CSRF_INVALID') {
        return API({ ...config, csrfRetried: true });
    }

    const showToast = !config?.skipGlobalError;
    if (!response) {
        if (showToast) emitAuthEvent({ type: 'network-error', message: 'Cannot connect to server. Please check your connection and try again.' });
    } else if (response.status === 401) {
        if (!isNoSessionEventRequest(config)) emitAuthEvent({ type: 'unauthorized' });
    } else if (response.status === 403) {
        if (showToast) emitAuthEvent({ type: 'forbidden', message: 'You do not have permission to perform this action.' });
    } else if (response.status >= 500) {
        if (showToast) emitAuthEvent({ type: 'server-error', message: response.data?.message || 'An unexpected error occurred. Please try again.' });
    }
    return Promise.reject(error);
});

export default API;
