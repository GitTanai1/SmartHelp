package com.smarthelp.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthelp.dto.TicketDtos.CreateTicketRequest;
import com.smarthelp.dto.TicketDtos.TicketDetail;
import com.smarthelp.dto.TicketDtos.TicketSummary;
import com.smarthelp.dto.TicketDtos.UpdateTicketRequest;
import com.smarthelp.exception.BadRequestException;
import com.smarthelp.model.Ticket;
import com.smarthelp.service.TicketService;
import com.smarthelp.security.CurrentUserAccess;

import jakarta.validation.Valid;

@RestController
@RequestMapping({ "/api/v1/tickets", "/api/tickets" })
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUserAccess currentUserAccess;

    public TicketController(TicketService ticketService, CurrentUserAccess currentUserAccess) {
        this.ticketService = ticketService;
        this.currentUserAccess = currentUserAccess;
    }

    @PostMapping
    public ResponseEntity<Ticket> create(@Valid @RequestBody CreateTicketRequest request) {
        currentUserAccess.requireTicketCreationFor(request.userId());
        Ticket ticket = ticketService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.id())).body(ticket);
    }

    @GetMapping
    public List<TicketSummary> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new BadRequestException("limit must be between 1 and 100 and offset must be zero or greater");
        }
        return ticketService.findAll(status, categoryId, currentUserAccess.effectiveTicketUserFilter(userId), priority,
                limit, offset);
    }

    @GetMapping("/{id}")
    public TicketDetail findById(@PathVariable Long id) {
        currentUserAccess.requireTicketAccess(id);
        return ticketService.findDetailById(id);
    }

    @PutMapping("/{id}")
    public Ticket update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        currentUserAccess.requireAgent();
        return ticketService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUserAccess.requireAgent();
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
