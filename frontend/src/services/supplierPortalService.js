import API from './api';

/**
 * Supplier portal API (UC48 - View Purchase Orders, UC49 - Respond to Purchase Order).
 * Base URL: /api/v1/supplier-portal/purchase-orders
 */
const SupplierPortalService = {
    // params: { keyword, status, fromDate, toDate, page, size }
    getOrders: (params) => API.get('/supplier-portal/purchase-orders', { params }),
    getOrder: (id) => API.get(`/supplier-portal/purchase-orders/${id}`),
    // data: { action: 'ACCEPT' | 'REJECT' | 'PROPOSE_CHANGES', reason, proposedDeliveryDate, items: [{ orderDetailId, proposedQuantity, proposedUnitPrice }] }
    respond: (id, data) => API.post(`/supplier-portal/purchase-orders/${id}/respond`, data),
};

export default SupplierPortalService;
