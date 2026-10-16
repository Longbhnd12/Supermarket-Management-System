// SalePromotionCalculator - applies promotion discounts to order line items
package com.sms.backend.service;

import com.sms.backend.entity.Promotion;
import com.sms.backend.entity.SaleItem;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PromotionType;
import com.sms.backend.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Resolves one best promotion per line, never stacking promotions, and calculating fixed discount per unit multiplied by line quantity. */
@Component
@RequiredArgsConstructor
public class SalePromotionCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final PromotionRepository promotions;
    private final Clock clock;

    public void apply(List<SaleItem> items) {
        if (items.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now(clock);
        List<Promotion> candidates = promotions.findEffective(GeneralStatus.ACTIVE, now);
        for (SaleItem item : items) {
            Promotion best = null;
            BigDecimal bestDiscount = new BigDecimal("0.00");
            for (Promotion promotion : candidates) {
                if (!eligible(promotion, item, now)) continue;
                BigDecimal value = promotion.getDiscountValue();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
                BigDecimal discount = promotion.getPromotionType() == PromotionType.PERCENTAGE
                        ? item.getSubtotal().multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                        : value.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
                discount = discount.min(item.getSubtotal());
                // Equal discounts have no business priority; use ID only for repeatable persistence.
                if (discount.signum() > 0 && (discount.compareTo(bestDiscount) > 0
                        || discount.compareTo(bestDiscount) == 0 && best != null
                        && promotion.getPromotionId() < best.getPromotionId())) {
                    best = promotion;
                    bestDiscount = discount;
                }
            }
            item.setPromotion(best);
            item.setDiscountAmount(bestDiscount);
        }
    }

    private boolean eligible(Promotion promotion, SaleItem item, LocalDateTime now) {
        if (promotion.getPromotionId() == null || promotion.getStatus() != GeneralStatus.ACTIVE
                || promotion.getStartAt() == null || promotion.getEndAt() == null
                || now.isBefore(promotion.getStartAt()) || now.isAfter(promotion.getEndAt())
                || promotion.getDiscountValue() == null || promotion.getDiscountValue().signum() <= 0
                || promotion.getPromotionType() == null) return false;
        if (promotion.getPromotionType() == PromotionType.PERCENTAGE
                && promotion.getDiscountValue().compareTo(HUNDRED) > 0) return false;
        boolean productScope = promotion.getProduct() != null;
        boolean categoryScope = promotion.getCategory() != null;
        if (productScope == categoryScope) return false;
        return productScope
                ? Objects.equals(promotion.getProduct().getProductId(), item.getProduct().getProductId())
                : item.getProduct().getCategory() != null && Objects.equals(
                        promotion.getCategory().getCategoryId(), item.getProduct().getCategory().getCategoryId());
    }
}
