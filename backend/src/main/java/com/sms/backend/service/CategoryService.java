package com.sms.backend.service;

import com.sms.backend.dto.category.CategoryRequest;
import com.sms.backend.dto.category.CategoryResponse;

import java.util.List;

/**
 * Service interface for Category operations.
 * TODO (SDD): Confirm whether deactivation is soft-delete (status=INACTIVE) or hard-delete.
 */
public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    void deleteCategory(Long id);
}
