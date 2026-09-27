package com.smarthelp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.MDC;

import com.smarthelp.dto.AiDtos.ResolveTicketRequest;
import com.smarthelp.dto.AiDtos.EscalateTicketRequest;
import com.smarthelp.dto.ResponseDtos.CreateTicketResponseRequest;
import com.smarthelp.dto.TicketDtos.UpdateTicketRequest;
import com.smarthelp.model.Ticket;
import com.smarthelp.repository.AgentActionRepository;
import com.smarthelp.repository.AuditEventRepository;
import com.smarthelp.config.RequestIdFilter;

/** Owns the atomic business operation performed when the AI resolves a ticket. */
@Service
public class AiResolutionService {

    private final ResponseService responseService;
    private final TicketService ticketService;
    private final AgentActionRepository agentActionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AgentRunService agentRunService;

    public AiResolutionService(
            ResponseService responseService,
            TicketService ticketService,
            AgentActionRepository agentActionRepository,
            AuditEventRepository auditEventRepository,
            AgentRunService agentRunService) {
        this.responseService = responseService;
        this.ticketService = ticketService;
        this.agentActionRepository = agentActionRepository;
        this.auditEventRepository = auditEventRepository;
        this.agentRunService = agentRunService;
    }

    @Transactional
    public Ticket resolve(Long ticketId, String idempotencyKey, ResolveTicketRequest request, UUID agentRunId) {
        requireActiveRun(agentRunId, ticketId);
        Ticket current = ticketService.findById(ticketId);
        String requestHash = hash(ticketId + ":" + request.message() + ":" + request.priority());
        if (!claim(idempotencyKey, "TICKET_RESOLUTION", requestHash, ticketId)) {
            return ticketService.findById(ticketId);
        }
        responseService.create(ticketId, new CreateTicketResponseRequest(request.message(), "AI"));
        String priority = request.priority() == null || request.priority().isBlank()
                ? current.priority()
                : request.priority();
        Ticket resolved = ticketService.update(ticketId, new UpdateTicketRequest(
                current.categoryId(), current.subject(), current.description(), "RESOLVED", priority));
        agentActionRepository.markCompleted(idempotencyKey);
        audit("TICKET_RESOLVED_BY_AI", ticketId, idempotencyKey, "status=RESOLVED");
        return resolved;
    }

    @Transactional
    public Ticket escalate(Long ticketId, String idempotencyKey, EscalateTicketRequest request, UUID agentRunId) {
        requireActiveRun(agentRunId, ticketId);
        Ticket current = ticketService.findById(ticketId);
        String requestHash = hash(ticketId + ":" + request.reason());
        if (!claim(idempotencyKey, "TICKET_ESCALATION", requestHash, ticketId)) {
            return ticketService.findById(ticketId);
        }
        responseService.create(ticketId, new CreateTicketResponseRequest(
                "AI workflow escalated this ticket for human review. Reason: " + request.reason(), "AI"));
        Ticket escalated = ticketService.update(ticketId, new UpdateTicketRequest(
                current.categoryId(), current.subject(), current.description(), "ESCALATED", current.priority()));
        agentActionRepository.markCompleted(idempotencyKey);
        audit("TICKET_ESCALATED_BY_AI", ticketId, idempotencyKey, "status=ESCALATED");
        return escalated;
    }

    private boolean claim(String idempotencyKey, String actionType, String requestHash, Long ticketId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 128 characters");
        }
        if (agentActionRepository.tryStart(idempotencyKey, actionType, requestHash, ticketId)) {
            return true;
        }
        AgentActionRepository.ActionExecution existing = agentActionRepository.findByKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key was not persisted"));
        if (!existing.actionType().equals(actionType)
                || !existing.requestHash().equals(requestHash)
                || !existing.ticketId().equals(ticketId)) {
            throw new IllegalArgumentException("Idempotency key was already used for a different action");
        }
        return false;
    }

    private void requireActiveRun(UUID agentRunId, Long ticketId) {
        if (agentRunId != null) {
            agentRunService.requireRunning(agentRunId, ticketId);
        }
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void audit(String eventType, Long ticketId, String actionKey, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "local-development" : authentication.getName();
        auditEventRepository.append(
                eventType, actor, ticketId, actionKey, MDC.get(RequestIdFilter.MDC_KEY), details);
    }
}
