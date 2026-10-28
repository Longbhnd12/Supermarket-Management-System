import API from './api';

/**
 * Inventory API service.
 * Base URL: /api/v1/inventory
 */
const InventoryService = {
    // Stock Receipts
    getAllReceipts: (params) => API.get('/inventory/receipts', { params }),  // params: { status }
    getReceiptById: (id) => API.get(`/inventory/receipts/${id}`),
    confirmReceipt: (id) => API.post(`/inventory/receipts/${id}/confirm`),

    // Stock Movements
    getMovements: (params) => API.get('/inventory/movements', { params }),  // params: { productId, movementType, referenceType, referenceId }

    // Stock Checks
    getAllChecks: (params) => API.get('/inventory/checks', { params }),     // params: { productId }
    getCheckById: (id) => API.get(`/inventory/checks/${id}`),
};

export default InventoryService;
