package com.smarthelp.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthelp.dto.KnowledgeDtos.CreateCategoryRequest;
import com.smarthelp.dto.KnowledgeDtos.UpdateCategoryRequest;
import com.smarthelp.model.Category;
import com.smarthelp.service.CategoryService;
import com.smarthelp.security.CurrentUserAccess;

import jakarta.validation.Valid;

@RestController
@RequestMapping({ "/api/v1/categories", "/api/categories" })
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserAccess currentUserAccess;

    public CategoryController(CategoryService categoryService, CurrentUserAccess currentUserAccess) {
        this.categoryService = categoryService;
        this.currentUserAccess = currentUserAccess;
    }

    @PostMapping
    public ResponseEntity<Category> create(@Valid @RequestBody CreateCategoryRequest request) {
        currentUserAccess.requireAgent();
        Category category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id())).body(category);
    }

    @GetMapping
    public List<Category> findAll() {
        return categoryService.findAll();
    }

    @GetMapping("/{id}")
    public Category findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        currentUserAccess.requireAgent();
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUserAccess.requireAgent();
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
