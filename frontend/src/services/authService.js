import API from './api';

const authService = {
    register: async (details) => (await API.post('/auth/register', details)).data,
    login: async (credentials) => (await API.post('/auth/login', credentials)).data,
    getMe: async () => (await API.get('/auth/me')).data,
    logout: async () => (await API.post('/auth/logout')).data,
    // UC03 - Change Password
    changePassword: async (data) => (await API.post('/auth/change-password', data)).data
};

export default authService;
