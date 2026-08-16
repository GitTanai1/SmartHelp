package com.smarthelp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ResponseDtos {

    private ResponseDtos() {
    }

    public record CreateTicketResponseRequest(
            @NotBlank String message,
            @NotBlank @Pattern(regexp = "AI|AGENT") String senderType) {
    }
}
