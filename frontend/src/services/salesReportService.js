import API from './api';

const salesReportService = {
    getSalesReport: async (params = {}) => {
        const response = await API.get('/reports/sales', { params });
        return response.data;
    }
};

export default salesReportService;
