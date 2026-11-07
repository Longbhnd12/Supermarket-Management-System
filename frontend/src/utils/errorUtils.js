export function getErrorMessage(error) {
    if (!error.response) {
        return 'Cannot connect to server';
    }

    return error.response.data?.message
        || 'An unexpected error occurred';
}
// 403 FORBIDDEN -> MSG19 (SRS 5.3), otherwise the backend message.
// A 403 CSRF_INVALID is not a permission problem, so its own message is shown.
export function getAccessAwareErrorMessage(error) {
    if (error?.response?.status === 403 && error.response.data?.errorCode !== 'CSRF_INVALID') {
        return 'You do not have permission to perform this action.';
    }
    return getErrorMessage(error);
}
