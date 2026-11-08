export function getErrorMessage(error) {
    if (!error.response) {
        return 'Cannot connect to server';
    }

    const message = error.response.data?.message;
    if (message === 'Sale is locked by a pending, successful or unresolved payment' ||
        message === 'A sale with a pending, successful or unresolved payment cannot be cancelled') {
        return 'Không thể hủy: Đơn hàng đang có giao dịch thanh toán chờ xử lý hoặc cần đối soát. Vui lòng kiểm tra lại thanh toán.';
    }

    return message || 'An unexpected error occurred';
}

// 403 FORBIDDEN -> MSG19 (SRS 5.3), otherwise the backend message.
// A 403 CSRF_INVALID is not a permission problem, so its own message is shown.
export function getAccessAwareErrorMessage(error) {
    if (error?.response?.status === 403 && error.response.data?.errorCode !== 'CSRF_INVALID') {
        return 'You do not have permission to perform this action.';
    }
    return getErrorMessage(error);
}
