package com.smarthelp.model;

import java.time.LocalDateTime;

public record KnowledgeArticle(
        Long id,
        Long categoryId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
