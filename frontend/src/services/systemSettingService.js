import API from './api';

export const systemSettingService = {
    getAllSettings: async () => {
        const response = await API.get('/system-settings');
        return response.data;
    },

    getSettingById: async (id) => {
        const response = await API.get(`/system-settings/${id}`);
        return response.data;
    },

    getSettingByKey: async (key) => {
        const response = await API.get(`/system-settings/key/${key}`);
        return response.data;
    },

    updateSetting: async (id, data) => {
        const response = await API.put(`/system-settings/${id}`, data);
        return response.data;
    }
};

export default systemSettingService;
