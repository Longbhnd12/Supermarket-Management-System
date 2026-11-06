import axios from 'axios';

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

// CSRF_INVALID: the 403 response already carries a fresh XSRF-TOKEN cookie and the backend has not
// run the action, so the request is safely sent once more (e.g. the cookie was cleared or expired).
API.interceptors.response.use(undefined, (error) => {
    const { config, response } = error;
    if (config && !config.csrfRetried && response?.status === 403 && response.data?.errorCode === 'CSRF_INVALID') {
        return API({ ...config, csrfRetried: true });
    }
    return Promise.reject(error);
});

export default API;
