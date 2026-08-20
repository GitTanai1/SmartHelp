package com.smarthelp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthelp.dto.KnowledgeDtos.CreateKnowledgeRequest;
import com.smarthelp.dto.KnowledgeDtos.UpdateKnowledgeRequest;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.KnowledgeArticle;
import com.smarthelp.repository.CategoryRepository;
import com.smarthelp.repository.KnowledgeRepository;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    KnowledgeRepository knowledgeRepository;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    KnowledgeService knowledgeService;

    @Test
    void createArticleRequiresCategory() {
        KnowledgeArticle article = article(5L, "Title");
        when(categoryRepository.existsById(2L)).thenReturn(true);
        when(knowledgeRepository.create(2L, "Title", "Content")).thenReturn(article);

        KnowledgeArticle result = knowledgeService.create(new CreateKnowledgeRequest(2L, "Title", "Content"));

        assertThat(result.id()).isEqualTo(5L);
    }

    @Test
    void findArticleReturnsExistingArticle() {
        when(knowledgeRepository.findById(5L)).thenReturn(Optional.of(article(5L, "Title")));

        KnowledgeArticle result = knowledgeService.findById(5L);

        assertThat(result.title()).isEqualTo("Title");
    }

    @Test
    void updateArticleRequiresExistingArticleAndCategory() {
        when(knowledgeRepository.findById(5L))
                .thenReturn(Optional.of(article(5L, "Old")))
                .thenReturn(Optional.of(article(5L, "New")));
        when(categoryRepository.existsById(2L)).thenReturn(true);

        KnowledgeArticle result = knowledgeService.update(5L, new UpdateKnowledgeRequest(2L, "New", "New content"));

        assertThat(result.title()).isEqualTo("New");
        verify(knowledgeRepository).update(5L, 2L, "New", "New content");
    }

    @Test
    void deleteArticleRequiresExistingArticle() {
        when(knowledgeRepository.findById(5L)).thenReturn(Optional.of(article(5L, "Title")));

        knowledgeService.delete(5L);

        verify(knowledgeRepository).delete(5L);
    }

    @Test
    void missingArticleThrowsNotFound() {
        when(knowledgeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Knowledge article 99");
    }

    private KnowledgeArticle article(Long id, String title) {
        LocalDateTime now = LocalDateTime.now();
        return new KnowledgeArticle(id, 2L, title, "Content", now, now);
    }
}
