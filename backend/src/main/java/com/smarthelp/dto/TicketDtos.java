package com.smarthelp.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.smarthelp.model.TicketResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotNull Long userId,
            Long categoryId,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank String description,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String priority) {
    }

    public record UpdateTicketRequest(
            Long categoryId,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank String description,
            @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|ESCALATED|CLOSED") String status,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String priority) {
    }

    public record TicketSummary(
            Long id,
            Long userId,
            String userName,
            Long categoryId,
            String categoryName,
            String subject,
            String description,
            String status,
            String priority,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record TicketDetail(
            TicketSummary ticket,
            List<TicketResponse> responses) {
    }
}
