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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthelp.dto.KnowledgeDtos.CreateKnowledgeRequest;
import com.smarthelp.dto.KnowledgeDtos.UpdateKnowledgeRequest;
import com.smarthelp.model.KnowledgeArticle;
import com.smarthelp.service.KnowledgeService;
import com.smarthelp.security.CurrentUserAccess;

import jakarta.validation.Valid;

@RestController
@RequestMapping({ "/api/v1/knowledge", "/api/knowledge" })
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final CurrentUserAccess currentUserAccess;

    public KnowledgeController(KnowledgeService knowledgeService, CurrentUserAccess currentUserAccess) {
        this.knowledgeService = knowledgeService;
        this.currentUserAccess = currentUserAccess;
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@Valid @RequestBody CreateKnowledgeRequest request) {
        currentUserAccess.requireAgent();
        KnowledgeArticle article = knowledgeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/knowledge/" + article.id())).body(article);
    }

    @GetMapping
    public List<KnowledgeArticle> findAll(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        return knowledgeService.findAll(categoryId, query);
    }

    @GetMapping("/{id}")
    public KnowledgeArticle findById(@PathVariable Long id) {
        return knowledgeService.findById(id);
    }

    @PutMapping("/{id}")
    public KnowledgeArticle update(@PathVariable Long id, @Valid @RequestBody UpdateKnowledgeRequest request) {
        currentUserAccess.requireAgent();
        return knowledgeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUserAccess.requireAgent();
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
