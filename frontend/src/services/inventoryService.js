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

    // UC33 - Adjust Stock
    /**
     * Submits a stock adjustment request.
     * @param {Object} data - { productId, adjustmentType, adjustmentQuantity, reason, stockCheckId, batchAllocations }
     */
    adjustStock: (data) => API.post('/inventory/adjust', data),

    /**
     * Retrieves confirmed inventory batches for a product (UC33).
     * @param {number|string} productId
     */
    getProductBatches: (productId) => API.get('/inventory/products/' + productId + '/batches'),

    /**
     * Retrieves confirmed grouped physical batches for a product (UC33).
     * @param {number|string} productId
     */
    getGroupedProductBatches: (productId) => API.get('/inventory/products/' + productId + '/batches/grouped'),

    // Stock Receipts
    getAllReceipts: (params) => API.get('/inventory/receipts', { params }),
    getReceiptById: (id) => API.get('/inventory/receipts/' + id),
    confirmReceipt: (id) => API.post('/inventory/receipts/' + id + '/confirm'),

    // UC34 - Stock History
    /**
     * Retrieves paginated stock movement history with optional search, product, type, and date range filters.
     * @param {Object} params - { productId, keyword, movementType, startDate, endDate, page, size }
     */
    getStockHistory: (params) => API.get('/inventory/history', { params }),

    // UC35 - Stock Alerts
    /**
     * Retrieves stock alert summary counts and paginated alert items.
     * @param {Object} params - { keyword, categoryId, alertType, page, size }
     */
    getStockAlerts: (params) => API.get('/inventory/alerts', { params }),

    // Stock Movements
    getMovements: (params) => API.get('/inventory/movements', { params }),

    // Stock Checks
    getAllChecks: (params) => API.get('/inventory/checks', { params }),
    getCheckById: (id) => API.get('/inventory/checks/' + id),

    // UC32 - Stock Check & Stock Difference
    getNextStockCheckSession: () => API.get('/inventory/checks/next-session'),
    performStockCheck: (data) => API.post('/inventory/checks', data),
    getStockCheckSession: (checkNumber) => API.get('/inventory/checks/session/' + encodeURIComponent(checkNumber)),
    getAllStockCheckSessions: (params) => API.get('/inventory/checks/sessions', { params }),
    updateStockCheckCount: (id, data) => API.put('/inventory/checks/' + id, data),
};

export default InventoryService;