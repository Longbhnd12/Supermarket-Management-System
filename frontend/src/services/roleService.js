import API from './api';

/**
 * Role API service (UC06 - Manage Roles & Permissions).
 * Base URL: /api/v1/roles
 */
const RoleService = {
    getAll: () => API.get('/roles'),
    getPermissionCatalog: () => API.get('/roles/permissions'),
    updatePermissions: (id, permissions) => API.put(`/roles/${id}/permissions`, { permissions }),
    resetToDefault: (id) => API.post(`/roles/${id}/reset`),
};

export default RoleService;
