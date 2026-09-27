package com.smarthelp.dto;

import java.util.Map;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AiDtos {

    private AiDtos() {
    }

    public record AnalyzeTicketRequest(Long ticketId) {
    }

    public record ResolveTicketRequest(
            @NotBlank @Size(max = 10000) String message,
            @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "must be LOW, MEDIUM, or HIGH") String priority) {
    }

    public record EscalateTicketRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record RequestResolutionApproval(
            @NotBlank @Size(max = 10000) String message,
            @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "must be LOW, MEDIUM, or HIGH") String priority) {
    }

    public record ApprovalDecisionRequest(@Size(max = 1000) String note) {
    }

    public record AiAnalysisResult(
            String contractVersion,
            Long ticketId,
            String category,
            String priority,
            double confidence,
            String generatedResponse,
            boolean sensitive,
            String finalStatus,
            List<Evidence> evidence,
            String path) {
    }

    public record Evidence(Long articleId, String title, Long categoryId) {
    }

    public record WorkflowEvent(
            String contractVersion,
            Long ticketId,
            String runId,
            String node,
            String status,
            Map<String, Object> state,
            String message) {
    }
}
