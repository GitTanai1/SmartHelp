package com.smarthelp.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Java-owned record of a high-risk agent action awaiting an operator decision. */
public record AgentApproval(
        UUID id,
        UUID runId,
        Long ticketId,
        String actionType,
        String proposedMessage,
        String proposedPriority,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String decidedBy,
        String decisionNote) {
}
