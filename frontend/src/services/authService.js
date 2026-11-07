import API from './api';

const authService = {
    login: async (credentials) => (await API.post('/auth/login', credentials)).data,
    // No toast: AuthProvider shows its own "Cannot connect" screen and keeps the user logged in
    getMe: async () => (await API.get('/auth/me', { skipGlobalError: true })).data,
    logout: async () => (await API.post('/auth/logout')).data,
    // UC03 - Change Password
    changePassword: async (data) => (await API.post('/auth/change-password', data)).data,
    // UC45 - Forgot Password
    forgotPassword: async (data) => (await API.post('/auth/forgot-password', data)).data,
    resetPassword: async (data) => (await API.post('/auth/reset-password', data)).data
};

export default authService;
