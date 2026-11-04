import API from './api';

const authService = {
    register: async (details) => (await API.post('/auth/register', details)).data,
    login: async (credentials) => (await API.post('/auth/login', credentials)).data,
    getMe: async () => (await API.get('/auth/me')).data,
    logout: async () => (await API.post('/auth/logout')).data
};

export default authService;
