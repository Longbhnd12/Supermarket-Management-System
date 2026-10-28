package com.sms.backend.service;

import com.sms.backend.dto.promotion.PromotionRequest;
import com.sms.backend.dto.promotion.PromotionResponse;
import com.sms.backend.enums.GeneralStatus;

import java.util.List;

/**
 * Service interface for Promotion operations.
 * TODO (SDD): Confirm overlap/conflict detection rules for promotions on the same product/category.
 */
public interface PromotionService {

    List<PromotionResponse> getAllPromotions();

    List<PromotionResponse> getPromotionsByStatus(GeneralStatus status);

    PromotionResponse getPromotionById(Long id);

    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(Long id, PromotionRequest request);

    void deletePromotion(Long id);
}
