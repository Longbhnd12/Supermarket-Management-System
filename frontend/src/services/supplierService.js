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
    updateStatus: (id, status) => API.patch(`/suppliers/${id}/status?status=${status}`, { status }),
    delete: (id) => API.delete(`/suppliers/${id}`),
};

export default SupplierService;
