export function getErrorMessage(error) {
    if (!error.response) {
        return 'Cannot connect to server';
    }

    return error.response.data?.message
        || 'An unexpected error occurred';
}
// 403 -> MSG19 (SRS 5.3), otherwise the backend message
export function getAccessAwareErrorMessage(error) {
    if (error?.response?.status === 403) {
        return 'You do not have permission to perform this action.';
    }
    return getErrorMessage(error);
}
