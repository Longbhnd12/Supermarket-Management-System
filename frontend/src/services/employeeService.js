import API from './api';

/**
 * Employee API service.
 * Base URL: /api/v1/employees
 */
const EmployeeService = {
    getAll: (params) => API.get('/employees', { params }),          // params: { status }
    getById: (id) => API.get(`/employees/${id}`),
    create: (data) => API.post('/employees', data),
    update: (id, data) => API.put(`/employees/${id}`, data),
    updateStatus: (id, status) => API.patch(`/employees/${id}/status`, { status }),
    delete: (id) => API.delete(`/employees/${id}`),
};

export default EmployeeService;
