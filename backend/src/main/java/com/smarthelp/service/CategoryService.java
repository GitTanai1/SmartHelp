package com.smarthelp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthelp.dto.KnowledgeDtos.CreateCategoryRequest;
import com.smarthelp.dto.KnowledgeDtos.UpdateCategoryRequest;
import com.smarthelp.exception.BadRequestException;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.Category;
import com.smarthelp.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category create(CreateCategoryRequest request) {
        categoryRepository.findByName(request.name()).ifPresent(existing -> {
            throw new BadRequestException("A category with this name already exists");
        });
        return categoryRepository.create(request.name());
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " was not found"));
    }

    public boolean existsById(Long id) {
        return categoryRepository.existsById(id);
    }

    public Category update(Long id, UpdateCategoryRequest request) {
        findById(id);
        categoryRepository.findByName(request.name()).ifPresent(existing -> {
            if (!existing.id().equals(id)) {
                throw new BadRequestException("A different category already has this name");
            }
        });
        categoryRepository.update(id, request.name());
        return findById(id);
    }

    public void delete(Long id) {
        findById(id);
        categoryRepository.delete(id);
    }
}
