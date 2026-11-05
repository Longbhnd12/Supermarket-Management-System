import API from './api';

/**
 * Purchase Order & Purchase Request API service.
 * Base URL: /api/v1/purchase-orders
 */
const PurchaseOrderService = {
    getAll: (params) => API.get('/purchase-orders', { params }), // params: { keyword, supplierId, status, startDate, endDate, page, size }
    getStatusCounts: (params) => API.get('/purchase-orders/counts', { params }),
    getPending: (params) => API.get('/purchase-orders/pending', { params }),
    getById: (id) => API.get(`/purchase-orders/${id}`),
    getNextNumber: () => API.get('/purchase-orders/next-number'),
    getLowStockSuggestions: () => API.get('/purchase-orders/low-stock-suggestions'),
    create: (data) => API.post('/purchase-orders', data),
    createRequest: (data) => API.post('/purchase-orders/requests', data),
    approve: (id, data) => API.post(`/purchase-orders/${id}/approve`, data),
    reject: (id, data) => {
        if (typeof data === 'string') {
            return API.post(`/purchase-orders/${id}/reject`, { reason: data });
        }
        return API.post(`/purchase-orders/${id}/reject`, data);
    },
    cancel: (id) => API.post(`/purchase-orders/${id}/cancel`),
};

export default PurchaseOrderService;
