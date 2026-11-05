import API from './api';

const reportService = {
    getInventory: async (params = {}) => (await API.get('/reports/inventory', { params })).data,
    getRevenue: async (params = {}) => (await API.get('/reports/revenue', { params })).data,
    getProductSales: async (params = {}) => (await API.get('/reports/product-sales', { params })).data
};

export default reportService;
