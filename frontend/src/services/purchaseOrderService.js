import API from './api';

/**
 * Purchase Order & Purchase Request API service.
 * Base URL: /api/v1/purchase-orders
 */
const PurchaseOrderService = {
    getAll: (params) => API.get('/purchase-orders', { params }), // params: { status }
    getById: (id) => API.get(`/purchase-orders/${id}`),
    getNextNumber: () => API.get('/purchase-orders/next-number'),
    getLowStockSuggestions: () => API.get('/purchase-orders/low-stock-suggestions'),
    create: (data) => API.post('/purchase-orders', data),
    createRequest: (data) => API.post('/purchase-orders/requests', data),
    approve: (id) => API.post(`/purchase-orders/${id}/approve`),
    reject: (id, reason) => API.post(`/purchase-orders/${id}/reject`, null, { params: { reason } }),
    cancel: (id) => API.post(`/purchase-orders/${id}/cancel`),
};

export default PurchaseOrderService;
