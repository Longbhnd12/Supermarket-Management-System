package com.sms.backend.service;

import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalePromotionCalculatorTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private PromotionRepository repository;
    private SalePromotionCalculator calculator;
    private SaleItem item;

    @BeforeEach void setup() {
        repository = mock(PromotionRepository.class);
        calculator = new SalePromotionCalculator(repository, clock);
        Category category = new Category(); category.setCategoryId(8L);
        Product product = new Product(); product.setProductId(2L); product.setCategory(category);
        item = new SaleItem(); item.setProduct(product); item.setQuantity(new BigDecimal("3"));
        item.setUnitPrice(new BigDecimal("100000.00")); item.setSubtotal(new BigDecimal("300000.00"));
    }

    private Promotion promotion(long id, PromotionType type, String value) {
        Promotion p = new Promotion(); p.setPromotionId(id); p.setProduct(item.getProduct());
        p.setPromotionType(type); p.setDiscountValue(new BigDecimal(value));
        p.setStartAt(now.minusDays(1)); p.setEndAt(now.plusDays(1));
        return p;
    }

    private void apply(Promotion... candidates) {
        when(repository.findEffective(GeneralStatus.ACTIVE, now)).thenReturn(List.of(candidates));
        calculator.apply(List.of(item));
    }

    @Test void choosesBestAcrossProductAndCategoryWithoutStacking() {
        Promotion fixed = promotion(1, PromotionType.FIXED_AMOUNT, "20000");
        Promotion percentage = promotion(2, PromotionType.PERCENTAGE, "10");
        percentage.setProduct(null); percentage.setCategory(item.getProduct().getCategory());
        apply(fixed, percentage);
        assertSame(percentage, item.getPromotion());
        assertEquals(new BigDecimal("30000.00"), item.getDiscountAmount());
        calculator.apply(List.of(item));
        assertEquals(new BigDecimal("30000.00"), item.getDiscountAmount());
        verify(repository, times(2)).findEffective(GeneralStatus.ACTIVE, now);
    }

    @Test void fixedDiscountAppliesOnceForWholeLineAndIsCapped() {
        Promotion fixed = promotion(1, PromotionType.FIXED_AMOUNT, "20000");
        apply(fixed);
        assertEquals(new BigDecimal("20000.00"), item.getDiscountAmount());
        item.setQuantity(new BigDecimal("0.125")); item.setSubtotal(new BigDecimal("12500.00"));
        calculator.apply(List.of(item));
        assertEquals(new BigDecimal("12500.00"), item.getDiscountAmount());
    }

    @Test void roundsPercentageHalfUpAndAllowsInclusiveTimeBounds() {
        item.setSubtotal(new BigDecimal("0.05"));
        Promotion p = promotion(1, PromotionType.PERCENTAGE, "10");
        p.setStartAt(now); p.setEndAt(now);
        apply(p);
        assertEquals(new BigDecimal("0.01"), item.getDiscountAmount());
        assertSame(p, item.getPromotion());
    }

    @Test void equalDiscountUsesStableIdRegardlessOfCandidateOrder() {
        Promotion a = promotion(9, PromotionType.PERCENTAGE, "10");
        Promotion b = promotion(3, PromotionType.FIXED_AMOUNT, "30000");
        apply(a, b); assertSame(b, item.getPromotion());
        apply(b, a); assertSame(b, item.getPromotion());
        assertEquals(new BigDecimal("30000.00"), item.getDiscountAmount());
    }

    @Test void invalidScopesDatesStatusAndValuesAreIgnoredAndStaleDiscountIsCleared() {
        List<Promotion> invalid = new ArrayList<>();
        for (int i = 0; i < 12; i++) invalid.add(promotion(i + 1, PromotionType.PERCENTAGE, "10"));
        invalid.get(0).setStatus(GeneralStatus.INACTIVE);
        invalid.get(1).setStartAt(now.plusNanos(1));
        invalid.get(2).setEndAt(now.minusNanos(1));
        invalid.get(3).setProduct(null);
        invalid.get(4).setCategory(item.getProduct().getCategory());
        Product other = new Product(); other.setProductId(99L); invalid.get(5).setProduct(other);
        invalid.get(6).setDiscountValue(BigDecimal.ZERO);
        invalid.get(7).setDiscountValue(new BigDecimal("-1"));
        invalid.get(8).setDiscountValue(new BigDecimal("100.01"));
        invalid.get(9).setDiscountValue(null);
        invalid.get(10).setPromotionType(null);
        invalid.get(11).setStartAt(null);
        item.setPromotion(invalid.getFirst()); item.setDiscountAmount(new BigDecimal("999.00"));
        apply(invalid.toArray(Promotion[]::new));
        assertNull(item.getPromotion()); assertEquals(new BigDecimal("0.00"), item.getDiscountAmount());
    }

    @Test void categoryMustMatchAndOneHundredPercentCanReduceLineToZero() {
        Promotion wrong = promotion(1, PromotionType.PERCENTAGE, "50"); wrong.setProduct(null);
        Category category = new Category(); category.setCategoryId(99L); wrong.setCategory(category);
        apply(wrong); assertEquals(new BigDecimal("0.00"), item.getDiscountAmount());
        Promotion full = promotion(2, PromotionType.PERCENTAGE, "100");
        apply(wrong, full); assertEquals(item.getSubtotal(), item.getDiscountAmount());
        item.setSubtotal(new BigDecimal("0.00")); calculator.apply(List.of(item));
        assertNull(item.getPromotion()); assertEquals(new BigDecimal("0.00"), item.getDiscountAmount());
    }

    @Test void emptySaleDoesNotQueryPromotions() {
        calculator.apply(List.of()); verifyNoInteractions(repository);
    }
}
