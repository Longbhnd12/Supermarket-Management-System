// paymentService - frontend API client for cash and VNPay payment initiation
import API from './api';

export default {
    methods: () => API.get('/payments/methods'),
    overview: (saleId) => API.get(`/payments/sales/${saleId}`),
    byReference: (ref, signal) => API.get(`/payments/reference/${encodeURIComponent(ref)}`, { signal }),
    create: (saleId, data) => API.post(`/payments/sales/${saleId}`, data),
    cashChange: (saleId, data, signal) => API.post(`/payments/sales/${saleId}/cash-change`, data, { signal }),
    reconcile: (id) => API.post(`/payments/${id}/reconcile`),
    confirmCancellation: (parameters, signal) => API.post('/payments/vnpay/confirm-cancellation', parameters, { signal }),
};
