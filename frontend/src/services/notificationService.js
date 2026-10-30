import API from './api';
export const notificationService = {
    getNotifications: async (params={}) => (await API.get('/notifications',{params})).data,
    getUnreadCount: async () => (await API.get('/notifications/unread-count')).data,
    markAsRead: async (id) => (await API.patch(`/notifications/${id}/read`)).data
};
export default notificationService;
