package com.smarthelp.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smarthelp.dto.KnowledgeDtos.CreateKnowledgeRequest;
import com.smarthelp.dto.KnowledgeDtos.UpdateKnowledgeRequest;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.KnowledgeArticle;
import com.smarthelp.repository.CategoryRepository;
import com.smarthelp.repository.KnowledgeRepository;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final CategoryRepository categoryRepository;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, CategoryRepository categoryRepository) {
        this.knowledgeRepository = knowledgeRepository;
        this.categoryRepository = categoryRepository;
    }

    public KnowledgeArticle create(CreateKnowledgeRequest request) {
        requireCategory(request.categoryId());
        KnowledgeArticle article = knowledgeRepository.create(request.categoryId(), request.title(), request.content());
        log.info("Created knowledge article id={} categoryId={}", article.id(), article.categoryId());
        return article;
    }

    public List<KnowledgeArticle> findAll(Long categoryId, String query) {
        return knowledgeRepository.findAll(categoryId, query);
    }

    public KnowledgeArticle findById(Long id) {
        return knowledgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article " + id + " was not found"));
    }

    public KnowledgeArticle update(Long id, UpdateKnowledgeRequest request) {
        findById(id);
        requireCategory(request.categoryId());
        knowledgeRepository.update(id, request.categoryId(), request.title(), request.content());
        KnowledgeArticle updated = findById(id);
        log.info("Updated knowledge article id={} categoryId={}", updated.id(), updated.categoryId());
        return updated;
    }

    public void delete(Long id) {
        findById(id);
        knowledgeRepository.delete(id);
        log.info("Deleted knowledge article id={}", id);
    }

    private void requireCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category " + categoryId + " was not found");
        }
    }
}
