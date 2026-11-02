import API from './api';

export const systemLogService = {
    getLogs: async (params = {}) => {
        const response = await API.get('/system-logs', { params });
        return response.data;
    },

    getLogById: async (id) => {
        const response = await API.get(`/system-logs/${id}`);
        return response.data;
    },

    getActions: async () => {
        const response = await API.get('/system-logs/actions');
        return response.data;
    },

    getEntityTypes: async () => {
        const response = await API.get('/system-logs/entity-types');
        return response.data;
    }
};

export default systemLogService;
