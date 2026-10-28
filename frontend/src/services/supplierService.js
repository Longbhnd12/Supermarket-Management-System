import API from './api';

/**
 * Supplier API service.
 * Base URL: /api/v1/suppliers
 */
const SupplierService = {
    getAll: (params) => API.get('/suppliers', { params }),          // params: { status }
    getById: (id) => API.get(`/suppliers/${id}`),
    create: (data) => API.post('/suppliers', data),
    update: (id, data) => API.put(`/suppliers/${id}`, data),
    delete: (id) => API.delete(`/suppliers/${id}`),
};

export default SupplierService;
