package com.sms.backend.service;

import com.sms.backend.dto.profile.ProfileResponse;
import com.sms.backend.dto.profile.UpdateProfileRequest;

/**
 * UC44 - View/Update Profile of the logged-in user.
 */
public interface ProfileService {

    ProfileResponse getProfile(String username);

    ProfileResponse updateProfile(String username, UpdateProfileRequest request);
}
