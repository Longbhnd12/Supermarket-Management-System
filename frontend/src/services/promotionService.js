import API from './api';

/**
 * Promotion API service.
 * Base URL: /api/v1/promotions
 */
const PromotionService = {
    getAll: (params) => API.get('/promotions', { params }),         // params: { status }
    getActive: () => API.get('/promotions/active'),
    getById: (id) => API.get(`/promotions/${id}`),
    create: (data) => API.post('/promotions', data),
    update: (id, data) => API.put(`/promotions/${id}`, data),
    updateStatus: (id, status) => API.patch(`/promotions/${id}/status`, { status }),
    delete: (id) => API.delete(`/promotions/${id}`),
};

export default PromotionService;
