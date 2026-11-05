package com.sms.backend.service;

import com.sms.backend.dto.request.UpdateSystemSettingRequest;
import com.sms.backend.dto.response.SystemSettingResponse;

import java.util.List;

public interface SystemSettingService {
    List<SystemSettingResponse> getAllSettings();
    SystemSettingResponse getSettingById(Long id);
    SystemSettingResponse getSettingByKey(String key);
    SystemSettingResponse updateSetting(Long id, UpdateSystemSettingRequest request);
}
