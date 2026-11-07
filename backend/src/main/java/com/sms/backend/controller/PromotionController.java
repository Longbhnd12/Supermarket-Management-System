package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.promotion.PromotionRequest;
import com.sms.backend.dto.promotion.PromotionResponse;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAllPromotions(
            @RequestParam(required = false) GeneralStatus status) {
        List<PromotionResponse> result = status != null
                ? promotionService.getPromotionsByStatus(status)
                : promotionService.getAllPromotions();
        return ResponseEntity.ok(ApiResponse.success("Promotions retrieved", result));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getActivePromotions() {
        return ResponseEntity.ok(ApiResponse.success("Active promotions retrieved", promotionService.getActivePromotions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> getPromotionById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Promotion retrieved", promotionService.getPromotionById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody PromotionRequest request) {
        PromotionResponse response = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Promotion created", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Promotion updated", promotionService.updatePromotion(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotionStatus(
            @PathVariable Long id,
            @RequestParam(required = false) GeneralStatus status,
            @RequestBody(required = false) Map<String, Object> body) {
        GeneralStatus targetStatus = status;
        if (targetStatus == null && body != null && body.containsKey("status")) {
            targetStatus = GeneralStatus.valueOf(body.get("status").toString().toUpperCase());
        }
        if (targetStatus == null) {
            throw new BadRequestException("Status is required (ACTIVE or INACTIVE)");
        }
        return ResponseEntity.ok(ApiResponse.success("Promotion status updated", promotionService.updateStatus(id, targetStatus)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }
}
