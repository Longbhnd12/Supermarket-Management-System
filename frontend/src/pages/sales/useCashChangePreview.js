import { useEffect, useState } from 'react';
import PaymentService from '../../services/paymentService';
import { getErrorMessage } from '../../utils/errorUtils';

export default function useCashChangePreview({ saleId, total, received, enabled, revision }) {
    const [state, setState] = useState(null);
    const valid = /^\d{1,13}(?:\.\d{1,2})?$/.test(received);
    const key = JSON.stringify([saleId, total, received, enabled, revision]);
    if (state?.key !== key) {
        // Invalidate even when input goes A → B → A before B's debounced request starts.
        setState({ key, result: null, error: '', loading: true });
    }
    useEffect(() => {
        if (!enabled || !valid) return;
        let active = true;
        const controller = new AbortController();
        const timer = setTimeout(async () => {
            try {
                const { data } = await PaymentService.cashChange(saleId, { receivedAmount: received, expectedTotal: total }, controller.signal);
                if (active) setState({ key, result: data.data, error: '', loading: false });
            } catch (err) {
                if (active && !controller.signal.aborted) setState({ key, result: null, error: getErrorMessage(err), loading: false });
            }
        }, 300);
        return () => { active = false; clearTimeout(timer); controller.abort(); };
    }, [enabled, valid, key, saleId, total, received]);
    // Never expose an old result during the render before effect cleanup/debounce runs.
    if (!enabled) return { result: null, status: 'inactive', error: '' };
    if (!received) return { result: null, status: 'empty', error: '' };
    if (!valid) return { result: null, status: 'invalid', error: 'Nhập số tiền không âm, tối đa 13 chữ số nguyên và 2 số thập phân.' };
    if (state?.key !== key || state.loading) return { result: null, status: 'loading', error: '' };
    return { result: state.result, status: state.error ? 'error' : 'ready', error: state.error };
}
