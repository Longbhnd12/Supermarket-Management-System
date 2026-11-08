package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.promotion.PromotionRequest;
import com.sms.backend.dto.promotion.PromotionResponse;
import com.sms.backend.entity.*;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PromotionType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.*;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SystemLogRepository systemLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SaleItemRepository saleItemRepository;

    @Override
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PromotionResponse> getActivePromotions() {
        // Step 14 & 15: findActivePromotions(currentTime) where status = 'ACTIVE' and currentTime BETWEEN start_at AND end_at
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findActivePromotions(now)
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
        // Step 14: Validate discountValue (> 0, <= 100 if %) & endAt > startAt
        validatePromotionRequest(request);

        boolean hasCategory = request.categoryId() != null;
        boolean hasSingleProduct = request.productId() != null;
        boolean hasMultipleProducts = request.productIds() != null && !request.productIds().isEmpty();
        boolean hasProduct = hasSingleProduct || hasMultipleProducts;

        if (!hasCategory && !hasProduct) {
            throw new BadRequestException("Vui lòng chọn phạm vi áp dụng: Sản phẩm cụ thể hoặc Danh mục cụ thể");
        }
        if (hasCategory && hasProduct) {
            throw new BadRequestException("Khuyến mãi không thể áp dụng đồng thời cho cả sản phẩm và danh mục");
        }

        User creator = currentUserProvider.currentUser()
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
        if (creator == null) {
            throw new BadRequestException("Không tìm thấy thông tin tài khoản người tạo khuyến mãi");
        }

        // Category Scope
        if (hasCategory) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.categoryId()));

            Promotion p = new Promotion();
            p.setPromotionName(request.promotionName().trim());
            p.setPromotionType(request.promotionType());
            p.setDiscountValue(request.discountValue());
            p.setCategory(category);
            p.setProduct(null);
            p.setStartAt(request.startAt());
            p.setEndAt(request.endAt());
            p.setStatus(GeneralStatus.ACTIVE);
            p.setCreatedBy(creator);

            Promotion saved = promotionRepository.save(p);
            logAction("PROMOTION_CREATED", saved.getPromotionId(),
                    "Tạo mới chương trình khuyến mãi: " + saved.getPromotionName() + " cho danh mục: " + category.getCategoryName());
            return toResponse(saved);
        }

        // Product Scope (supports single product or multiple products)
        List<Long> targetProductIds = hasMultipleProducts
                ? request.productIds()
                : List.of(request.productId());

        Promotion primarySaved = null;
        for (Long pId : targetProductIds) {
            Product product = productRepository.findById(pId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + pId));

            Promotion p = new Promotion();
            p.setPromotionName(request.promotionName().trim());
            p.setPromotionType(request.promotionType());
            p.setDiscountValue(request.discountValue());
            p.setCategory(null);
            p.setProduct(product);
            p.setStartAt(request.startAt());
            p.setEndAt(request.endAt());
            p.setStatus(GeneralStatus.ACTIVE);
            p.setCreatedBy(creator);

            Promotion saved = promotionRepository.save(p);
            if (primarySaved == null) {
                primarySaved = saved;
            }
            logAction("PROMOTION_CREATED", saved.getPromotionId(),
                    "Tạo mới chương trình khuyến mãi: " + saved.getPromotionName() + " cho sản phẩm: " + product.getProductName());
        }

        return toResponse(primarySaved);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        Promotion p = findById(id);
        validatePromotionRequest(request);

        p.setPromotionName(request.promotionName().trim());
        p.setPromotionType(request.promotionType());
        p.setDiscountValue(request.discountValue());
        p.setStartAt(request.startAt());
        p.setEndAt(request.endAt());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục: " + request.categoryId()));
            p.setCategory(category);
            p.setProduct(null);
        } else if (request.productId() != null) {
            Product product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + request.productId()));
            p.setProduct(product);
            p.setCategory(null);
        }

        Promotion saved = promotionRepository.save(p);
        logAction("PROMOTION_UPDATED", saved.getPromotionId(),
                "Cập nhật chương trình khuyến mãi: " + saved.getPromotionName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PromotionResponse updateStatus(Long id, GeneralStatus status) {
        Promotion p = findById(id);
        GeneralStatus oldStatus = p.getStatus();
        p.setStatus(status);
        Promotion saved = promotionRepository.save(p);
        logAction("STATUS_CHANGED", id, "Đổi trạng thái khuyến mãi " + p.getPromotionName() + " từ " + oldStatus + " sang " + status);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion p = findById(id);

        // Sequence Diagram Step 114: existsByPromotionId(id) (if deleting)
        if (saleItemRepository.existsByPromotion_PromotionId(id)) {
            throw new BadRequestException("Chương trình khuyến mãi đã được áp dụng trong hóa đơn bán hàng. Vui lòng sử dụng tính năng Tạm dừng (Deactivate) để bảo toàn dữ liệu kiểm toán tài chính (SRS 3.5.7).");
        }

        // Unused promotion: can be permanently deleted
        promotionRepository.delete(p);
        logAction("PROMOTION_DELETED", id, "Xóa vĩnh viễn chương trình khuyến mãi chưa sử dụng: " + p.getPromotionName());
    }

    // --- helpers ---

    private void validatePromotionRequest(PromotionRequest request) {
        if (request.discountValue() == null || request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Giá trị giảm giá phải lớn hơn 0");
        }
        if (request.promotionType() == PromotionType.PERCENTAGE
                && request.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Phần trăm giảm giá không được vượt quá 100%");
        }
        if (request.startAt() == null || request.endAt() == null) {
            throw new BadRequestException("Thời gian bắt đầu và kết thúc là bắt buộc");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        LocalDateTime now = LocalDateTime.now();
        if (request.endAt().isBefore(now)) {
            throw new BadRequestException("Thời gian kết thúc khuyến mãi không được ở quá khứ");
        }
        if (request.startAt().isBefore(now.minusHours(24))) {
            throw new BadRequestException("Thời gian bắt đầu khuyến mãi không được trước ngày hôm nay");
        }
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID: " + id));
    }

    private void logAction(String action, Long entityId, String description) {
        try {
            SystemLog log = new SystemLog();
            log.setUser(currentUserProvider.currentUser().orElse(null));
            log.setAction(action);
            log.setEntityType("Promotion");
            log.setEntityId(entityId);
            log.setDescription(description);
            log.setIpAddress(currentUserProvider.clientIp());
            log.setCreatedAt(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception ignored) {
            // Logging failure must not abort core business transaction
        }
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
                p.getCreatedBy() != null ? p.getCreatedBy().getUserId() : null,
                p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null
        );
    }
}
