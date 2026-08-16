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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets/{ticketId}/responses")
public class ResponseController {

    private final ResponseService responseService;

    public ResponseController(ResponseService responseService) {
        this.responseService = responseService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateTicketResponseRequest request) {
        TicketResponse response = responseService.create(ticketId, request);
        return ResponseEntity.created(URI.create("/api/tickets/" + ticketId + "/responses/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<TicketResponse> findByTicketId(@PathVariable Long ticketId) {
        return responseService.findByTicketId(ticketId);
    }
}
