package com.smarthelp.model;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        Long ticketId,
        String message,
        String senderType,
        LocalDateTime createdAt) {
}
