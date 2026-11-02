package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.category.CategoryRequest;
import com.sms.backend.dto.category.CategoryResponse;
import com.sms.backend.entity.Category;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.CategoryRepository;
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
        // TODO: Implement – create and persist Category from request
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        // TODO: Implement – update Category fields
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        // TODO (SDD): Confirm whether this is soft-delete (status=INACTIVE) or hard-delete
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getCategoryId(),
                c.getCategoryName(),
                c.getDescription(),
                c.getStatus(),
                c.getCreatedAt()
        );
    }
}
