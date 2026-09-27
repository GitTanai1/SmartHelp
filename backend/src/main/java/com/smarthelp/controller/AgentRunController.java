package com.smarthelp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthelp.model.AgentRun;
import com.smarthelp.model.AgentRunEvent;
import com.smarthelp.security.CurrentUserAccess;
import com.smarthelp.service.AgentRunService;
import com.smarthelp.service.AgentApprovalService;
import com.smarthelp.dto.AiDtos.ApprovalDecisionRequest;
import com.smarthelp.model.AgentApproval;

import jakarta.validation.Valid;

/** Read-only operator API for persisted agent-run history and events. */
@RestController
@RequestMapping({ "/api/v1/tickets/{ticketId}/agent-runs", "/api/tickets/{ticketId}/agent-runs" })
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final CurrentUserAccess currentUserAccess;
    private final AgentApprovalService agentApprovalService;

    public AgentRunController(AgentRunService agentRunService, CurrentUserAccess currentUserAccess,
            AgentApprovalService agentApprovalService) {
        this.agentRunService = agentRunService;
        this.currentUserAccess = currentUserAccess;
        this.agentApprovalService = agentApprovalService;
    }

    @GetMapping
    public List<AgentRun> findByTicket(@PathVariable Long ticketId) {
        currentUserAccess.requireAgent();
        return agentRunService.findByTicketId(ticketId);
    }

    @GetMapping("/{runId}")
    public AgentRun findById(@PathVariable Long ticketId, @PathVariable UUID runId) {
        currentUserAccess.requireAgent();
        AgentRun run = agentRunService.findById(runId);
        if (!run.ticketId().equals(ticketId)) {
            throw new com.smarthelp.exception.ResourceNotFoundException("Agent run " + runId + " was not found for ticket " + ticketId);
        }
        return run;
    }

    @GetMapping("/{runId}/events")
    public List<AgentRunEvent> findEvents(
            @PathVariable Long ticketId,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") long afterEventId) {
        findById(ticketId, runId);
        return agentRunService.findEventsAfter(runId, afterEventId);
    }

    @PostMapping("/{runId}/cancel")
    public AgentRun cancel(@PathVariable Long ticketId, @PathVariable UUID runId) {
        currentUserAccess.requireAgent();
        agentApprovalService.cancel(ticketId, runId);
        return agentRunService.findById(runId);
    }

    @GetMapping("/{runId}/approvals")
    public List<AgentApproval> findApprovals(@PathVariable Long ticketId, @PathVariable UUID runId) {
        findById(ticketId, runId);
        return agentApprovalService.findByRunId(runId);
    }

    @PostMapping("/{runId}/approvals/{approvalId}/approve")
    public AgentApproval approve(@PathVariable Long ticketId, @PathVariable UUID runId, @PathVariable UUID approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        currentUserAccess.requireAgent();
        return agentApprovalService.approve(ticketId, runId, approvalId, request.note());
    }

    @PostMapping("/{runId}/approvals/{approvalId}/reject")
    public AgentApproval reject(@PathVariable Long ticketId, @PathVariable UUID runId, @PathVariable UUID approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        currentUserAccess.requireAgent();
        return agentApprovalService.reject(ticketId, runId, approvalId, request.note());
    }
}
