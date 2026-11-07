package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.category.CategoryRequest;
import com.sms.backend.dto.category.CategoryResponse;
import com.sms.backend.entity.Category;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.service.CategoryService;
import com.sms.backend.enums.GeneralStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String categoryName = request.categoryName().trim();
        if (categoryRepository.existsByCategoryName(categoryName)) {
            throw new BadRequestException("Tên danh mục đã tồn tại: " + categoryName);
        }

        Category category = new Category();
        category.setCategoryName(categoryName);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        category.setStatus(request.status() != null ? request.status() : GeneralStatus.ACTIVE);
        category.setCreatedAt(LocalDateTime.now());

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findById(id);

        String categoryName = request.categoryName().trim();
        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(categoryName, id)) {
            throw new BadRequestException("Tên danh mục đã tồn tại: " + categoryName);
        }

        category.setCategoryName(categoryName);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.status() != null) {
            category.setStatus(request.status());
        }

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(Long id, GeneralStatus status) {
        Category category = findById(id);
        category.setStatus(status);
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findById(id);
        // Soft delete (SRS / SDD requirement: set status to INACTIVE)
        category.setStatus(GeneralStatus.INACTIVE);
        categoryRepository.save(category);
    }

    // --- helpers ---

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category c) {
        long productCount = productRepository.countByCategory_CategoryId(c.getCategoryId());
        return new CategoryResponse(
                c.getCategoryId(),
                c.getCategoryName(),
                c.getDescription(),
                c.getStatus(),
                c.getCreatedAt(),
                productCount
        );
    }
}
