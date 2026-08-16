package com.smarthelp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record CreateKnowledgeRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content) {
    }

    public record UpdateKnowledgeRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content) {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 100) String name) {
    }

    public record UpdateCategoryRequest(
            @NotBlank @Size(max = 100) String name) {
    }
}
