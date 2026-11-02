import API from './api';

/**
 * Product API service.
 * Base URL: /api/v1/products
 */
const ProductService = {
    getAll: (params) => API.get('/products', { params }),           // params: { status, categoryId }
    getById: (id) => API.get(`/products/${id}`),
    getByCode: (productCode) => API.get(`/products/code/${productCode}`),
    getByBarcode: (barcode) => API.get(`/products/barcode/${barcode}`),
    create: (data) => API.post('/products', data),
    update: (id, data) => API.put(`/products/${id}`, data),
    delete: (id) => API.delete(`/products/${id}`),
};

export default ProductService;
