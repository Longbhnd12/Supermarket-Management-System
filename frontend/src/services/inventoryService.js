import API from './api';

/**
 * Inventory API service.
 * Base URL: /api/v1/inventory
 */
const InventoryService = {
    // UC31 - Inventory List
    /**
     * Returns a paginated inventory list.
     * @param {Object} params - { keyword, categoryId, stockStatus, page, size }
     */
    getInventoryList: (params) => API.get('/inventory', { params }),

    /**
     * Downloads the full filtered inventory as a CSV file (returns blob).
     * @param {Object} params - { keyword, categoryId, stockStatus }
     */
    exportInventoryCsv: (params) =>
        API.get('/inventory/export', { params, responseType: 'blob' }),

    // Stock Receipts
    getAllReceipts: (params) => API.get('/inventory/receipts', { params }),
    getReceiptById: (id) => API.get('/inventory/receipts/' + id),
    confirmReceipt: (id) => API.post('/inventory/receipts/' + id + '/confirm'),

    // Stock Movements
    getMovements: (params) => API.get('/inventory/movements', { params }),

    // Stock Checks
    getAllChecks: (params) => API.get('/inventory/checks', { params }),
    getCheckById: (id) => API.get('/inventory/checks/' + id),
};

export default InventoryService;