package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SystemLogResponse;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SystemLogResponse>>> getLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponse<SystemLogResponse> response = systemLogService.getLogs(
                search,
                action,
                entityType,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDirection
        );
        return ResponseEntity.ok(ApiResponse.success("Retrieved system logs successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemLogResponse>> getLogById(@PathVariable Long id) {
        SystemLogResponse log = systemLogService.getLogById(id);
        return ResponseEntity.ok(ApiResponse.success("Retrieved system log successfully", log));
    }

    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctActions() {
        List<String> actions = systemLogService.getDistinctActions();
        return ResponseEntity.ok(ApiResponse.success("Retrieved distinct actions successfully", actions));
    }

    @GetMapping("/entity-types")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctEntityTypes() {
        List<String> types = systemLogService.getDistinctEntityTypes();
        return ResponseEntity.ok(ApiResponse.success("Retrieved distinct entity types successfully", types));
    }
}
