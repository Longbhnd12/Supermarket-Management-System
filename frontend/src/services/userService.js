import API from './api';

/**
 * User API service.
 * Base URL: /api/v1/users
 * TODO (SDD): Add auth endpoints (login, logout, password reset) once auth design is confirmed.
 */
const UserService = {
    getAll: (params) => API.get('/users', { params }),              // params: { status }
    getById: (id) => API.get(`/users/${id}`),
    create: (data) => API.post('/users', data),
    updateStatus: (id, status) => API.patch(`/users/${id}/status`, null, { params: { status } }),
    delete: (id) => API.delete(`/users/${id}`),
};

export default UserService;
