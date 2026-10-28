package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.promotion.PromotionRequest;
import com.sms.backend.dto.promotion.PromotionResponse;
import com.sms.backend.entity.Promotion;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.PromotionRepository;
import com.sms.backend.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;

    @Override
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PromotionResponse> getPromotionsByStatus(GeneralStatus status) {
        return promotionRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PromotionResponse getPromotionById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        // TODO: Implement – validate date range, resolve category/product,
        //       set createdBy from auth context
        // TODO (SDD): Confirm overlap detection rules
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        // TODO: Implement – update Promotion fields
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        // TODO (SDD): Confirm deletion policy (soft vs hard)
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
    }

    private PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(
                p.getPromotionId(),
                p.getPromotionName(),
                p.getPromotionType(),
                p.getDiscountValue(),
                p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                p.getCategory() != null ? p.getCategory().getCategoryName() : null,
                p.getProduct() != null ? p.getProduct().getProductId() : null,
                p.getProduct() != null ? p.getProduct().getProductName() : null,
                p.getStartAt(),
                p.getEndAt(),
                p.getStatus(),
                p.getCreatedBy().getUserId(),
                p.getCreatedBy().getUsername()
        );
    }
}
