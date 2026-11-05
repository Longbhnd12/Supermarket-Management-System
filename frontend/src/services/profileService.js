import API from './api';

/**
 * UC44 - View/Update Profile of the logged-in user.
 * Base URL: /api/v1/profile
 */
const ProfileService = {
    get: () => API.get('/profile'),
    update: (data) => API.put('/profile', data),
};

export default ProfileService;
