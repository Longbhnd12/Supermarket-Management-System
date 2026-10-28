import API from './api';

/**
 * Sale API service.
 * Base URL: /api/v1/sales
 */
const SaleService = {
    getAll: (params) => API.get('/sales', { params }),              // params: { status }
    getById: (id) => API.get(`/sales/${id}`),
    getBySaleNumber: (saleNumber) => API.get(`/sales/number/${saleNumber}`),
    create: () => API.post('/sales'),
    addItem: (saleId, itemData) => API.post(`/sales/${saleId}/items`, itemData),
    removeItem: (saleId, saleItemId) => API.delete(`/sales/${saleId}/items/${saleItemId}`),
    complete: (saleId) => API.post(`/sales/${saleId}/complete`),
    cancel: (saleId) => API.post(`/sales/${saleId}/cancel`),
};

export default SaleService;
