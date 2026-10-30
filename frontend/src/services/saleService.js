import API from './api';

const saleService = {
    getSales: async (params = {}) => {
        const response = await API.get('/sales', { params });
        return response.data;
    }
};

export default saleService;
