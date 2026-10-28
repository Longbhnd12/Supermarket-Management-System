import API from './api';

/**
 * Purchase Order API service.
 * Base URL: /api/v1/purchase-orders
 */
const PurchaseOrderService = {
    getAll: (params) => API.get('/purchase-orders', { params }),    // params: { status }
    getById: (id) => API.get(`/purchase-orders/${id}`),
    create: (data) => API.post('/purchase-orders', data),
    approve: (id) => API.post(`/purchase-orders/${id}/approve`),
    reject: (id, reason) => API.post(`/purchase-orders/${id}/reject`, null, { params: { reason } }),
    cancel: (id) => API.post(`/purchase-orders/${id}/cancel`),
};

export default PurchaseOrderService;
