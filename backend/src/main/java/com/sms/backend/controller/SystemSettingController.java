package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.request.UpdateSystemSettingRequest;
import com.sms.backend.dto.response.SystemSettingResponse;
import com.sms.backend.service.SystemSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system-settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemSettingResponse>>> getAllSettings() {
        List<SystemSettingResponse> settings = systemSettingService.getAllSettings();
        return ResponseEntity.ok(ApiResponse.success("Retrieved system settings successfully", settings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> getSettingById(@PathVariable Long id) {
        SystemSettingResponse setting = systemSettingService.getSettingById(id);
        return ResponseEntity.ok(ApiResponse.success("Retrieved system setting successfully", setting));
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> getSettingByKey(@PathVariable String key) {
        SystemSettingResponse setting = systemSettingService.getSettingByKey(key);
        return ResponseEntity.ok(ApiResponse.success("Retrieved system setting successfully", setting));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> updateSetting(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSystemSettingRequest request
    ) {
        SystemSettingResponse updated = systemSettingService.updateSetting(id, request);
        return ResponseEntity.ok(ApiResponse.success("Updated system setting successfully", updated));
    }
}
