package com.smarthelp.dto;

import java.util.Map;

public final class AiDtos {

    private AiDtos() {
    }

    public record AnalyzeTicketRequest(Long ticketId) {
    }

    public record AiAnalysisResult(
            Long ticketId,
            String category,
            String priority,
            double confidence,
            String generatedResponse,
            boolean sensitive,
            String finalStatus,
            String path) {
    }

    public record WorkflowEvent(
            Long ticketId,
            String node,
            String status,
            Map<String, Object> state,
            String message) {
    }
}
