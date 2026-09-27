package com.smarthelp.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthelp.dto.ResponseDtos.CreateTicketResponseRequest;
import com.smarthelp.model.TicketResponse;
import com.smarthelp.service.ResponseService;
import com.smarthelp.security.CurrentUserAccess;

import jakarta.validation.Valid;

@RestController
@RequestMapping({ "/api/v1/tickets/{ticketId}/responses", "/api/tickets/{ticketId}/responses" })
public class ResponseController {

    private final ResponseService responseService;
    private final CurrentUserAccess currentUserAccess;

    public ResponseController(ResponseService responseService, CurrentUserAccess currentUserAccess) {
        this.responseService = responseService;
        this.currentUserAccess = currentUserAccess;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateTicketResponseRequest request) {
        currentUserAccess.requireAgent();
        TicketResponse response = responseService.create(ticketId, request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticketId + "/responses/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<TicketResponse> findByTicketId(@PathVariable Long ticketId) {
        currentUserAccess.requireTicketAccess(ticketId);
        return responseService.findByTicketId(ticketId);
    }
}
