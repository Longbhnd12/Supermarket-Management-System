import API from './api';

/**
 * Stock Receipt API service.
 * UC37 — Stock Receipt List
 * Base URL: /api/v1/stock-receipts
 */
const StockReceiptService = {
    /**
     * UC37 — Returns a paginated, filtered list of stock receipts.
     * @param {Object} params - {
     *   keyword, productSku, supplierId, status,
     *   dateFrom (yyyy-MM-dd), dateTo (yyyy-MM-dd),
     *   page, size
     * }
     */
    getList: (params) => API.get('/stock-receipts', { params }),

    /**
     * Returns a single stock receipt with detail lines.
     * Planned for UC37 Details page.
     * @param {number} id
     */
    getById: (id) => API.get(`/stock-receipts/${id}`),

    /**
     * UC36 — Creates a new stock receipt.
     * @param {Object} data - {
     *   receiptNumber, supplierId, purchaseOrderId, status,
     *   items: [{ productId, quantity, unitCost, batchNumber, expirationDate }]
     * }
     */
    create: (data) => API.post('/stock-receipts', data),

    /**
     * UC36/UC37 — Confirms a draft stock receipt.
     * @param {number} id
     */
    confirm: (id) => API.post(`/stock-receipts/${id}/confirm`),

    /**
     * UC36 — Generates the next sequential stock receipt number.
     */
    getNextNumber: () => API.get('/stock-receipts/next-number'),
};

export default StockReceiptService;
