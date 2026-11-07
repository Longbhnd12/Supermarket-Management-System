import API from './api';

/**
 * Category API service.
 * Base URL: /api/v1/categories
 */
const CategoryService = {
    getAll: () => API.get('/categories'),
    getById: (id) => API.get(`/categories/${id}`),
    create: (data) => API.post('/categories', data),
    update: (id, data) => API.put(`/categories/${id}`, data),
    updateStatus: (id, status) => API.patch(`/categories/${id}/status?status=${status}`),
    delete: (id) => API.delete(`/categories/${id}`),
};

export default CategoryService;
