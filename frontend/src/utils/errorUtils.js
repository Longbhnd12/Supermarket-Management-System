export function getErrorMessage(error) {
    if (!error.response) {
        return 'Cannot connect to server';
    }

    return error.response.data?.message
        || 'An unexpected error occurred';
}