import API from './api';

const saleService = {
    // The list endpoint returns paginated summaries; POS needs full item details.
    getDrafts: async () => {
        const summaries = [];
        let page = 0;
        let totalPages;
        do {
            const { data } = await API.get('/sales', { params: { status: 'DRAFT', page, size: 100 } });
            summaries.push(...data.data.content);
            totalPages = data.data.totalPages;
            page += 1;
        } while (page < totalPages);
        return Promise.all(summaries.map(async ({ saleId }) =>
            (await API.get(`/sales/${saleId}`)).data.data));
    },
    getSales: async (params = {}) => (await API.get('/sales', { params })).data,
    getById: (id) => API.get(`/sales/${id}`),
    getBySaleNumber: (saleNumber) => API.get(`/sales/number/${saleNumber}`),
    create: (data) => API.post('/sales', data),                   // { cashierId }
    searchProducts: (params) => API.get('/sales/products', { params }), // { barcode } or { keyword }
    addItem: (saleId, itemData) => API.post(`/sales/${saleId}/items`, itemData),
    updateItemQuantity: (saleId, saleItemId, quantity) =>
        API.patch(`/sales/${saleId}/items/${saleItemId}`, { quantity }),
    removeItem: (saleId, saleItemId) => API.delete(`/sales/${saleId}/items/${saleItemId}`),
    calculateTotal: (saleId) => API.post(`/sales/${saleId}/calculate`),
    invoice: (saleId) => API.get(`/sales/${saleId}/invoice`),
    invoicePdf: (saleId) => API.get(`/sales/${saleId}/invoice/pdf`, { responseType: 'blob' }),
    complete: (saleId) => API.post(`/sales/${saleId}/complete`),
    cancel: (saleId) => API.post(`/sales/${saleId}/cancel`)
};

export default saleService;
