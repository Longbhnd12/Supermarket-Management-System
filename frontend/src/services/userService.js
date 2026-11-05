import API from './api';

/**
 * User API service (UC04 - View Users, UC05 - Manage Users).
 * Base URL: /api/v1/users
 */
const UserService = {
    // Plain list, used by the POS screen. params: { status }
    getAll: (params) => API.get('/users', { params }),
    // UC04. params: { search, roleId, status, page, size }
    search: (params) => API.get('/users/search', { params }),
    // UC05 form dropdowns. userId: the user being edited (keeps its own employee/supplier in the list)
    getOptions: (userId) => API.get('/users/options', { params: userId ? { userId } : {} }),
    getById: (id) => API.get(`/users/${id}`),
    create: (data) => API.post('/users', data),
    update: (id, data) => API.put(`/users/${id}`, data),
    updateStatus: (id, status) => API.patch(`/users/${id}/status`, null, { params: { status } }),
    delete: (id) => API.delete(`/users/${id}`),
};

export default UserService;
