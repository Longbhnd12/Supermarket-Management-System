import API from './api';

export default {
    search: async (params, signal) => (await API.get('/invoices', { params, signal })).data
};
