package com.smarthelp.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgentRunEvent(
        Long id,
        UUID runId,
        String node,
        String status,
        String payload,
        LocalDateTime occurredAt) {
}
