import API from './api';
export const dashboardService = { getDashboard: async () => (await API.get('/dashboard')).data };
export default dashboardService;
