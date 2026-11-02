import API from './api';

/**
 * Sale API service.
 * Base URL: /api/v1/sales
 */
const SaleService = {
    getAll: (params) => API.get('/sales', { params }),              // params: { status }
    getById: (id) => API.get(`/sales/${id}`),
    getBySaleNumber: (saleNumber) => API.get(`/sales/number/${saleNumber}`),
    create: (data) => API.post('/sales', data),                   // { cashierId }
    searchProducts: (params) => API.get('/sales/products', { params }), // { barcode } or { keyword }
    addItem: (saleId, itemData) => API.post(`/sales/${saleId}/items`, itemData),
    updateItemQuantity: (saleId, saleItemId, quantity) =>
        API.patch(`/sales/${saleId}/items/${saleItemId}`, { quantity }),
    removeItem: (saleId, saleItemId) => API.delete(`/sales/${saleId}/items/${saleItemId}`),
    calculateTotal: (saleId) => API.post(`/sales/${saleId}/calculate`),
    invoice: (saleId) => API.get(`/sales/${saleId}/invoice`),
    invoicePdf: (saleId) => API.get(`/sales/${saleId}/invoice/pdf`, { responseType: 'blob' }),
    complete: (saleId) => API.post(`/sales/${saleId}/complete`),
    cancel: (saleId) => API.post(`/sales/${saleId}/cancel`),
};

export default SaleService;
