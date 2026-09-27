package com.smarthelp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import com.smarthelp.exception.BadRequestException;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.AgentRun;
import com.smarthelp.model.AgentRunEvent;
import com.smarthelp.repository.AgentRunRepository;
import com.smarthelp.config.RequestIdFilter;

@Service
public class AgentRunService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunMetrics agentRunMetrics;

    public AgentRunService(AgentRunRepository agentRunRepository, AgentRunMetrics agentRunMetrics) {
        this.agentRunRepository = agentRunRepository;
        this.agentRunMetrics = agentRunMetrics;
    }

    public AgentRun start(Long ticketId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String requestedBy = authentication == null ? "local-development" : authentication.getName();
        AgentRun run = agentRunRepository.create(
                UUID.randomUUID(), ticketId, requestedBy, MDC.get(RequestIdFilter.MDC_KEY));
        agentRunMetrics.started();
        return run;
    }

    public void appendEvent(UUID runId, String payload) {
        agentRunRepository.appendEvent(runId, "WORKFLOW_EVENT", "RECEIVED", payload);
    }

    public void complete(UUID runId) {
        AgentRun run = findById(runId);
        agentRunRepository.complete(runId);
        agentRunMetrics.finished("COMPLETED", run.createdAt());
    }

    public void fail(UUID runId, String message) {
        AgentRun run = findById(runId);
        agentRunRepository.fail(runId, message == null ? "Unknown workflow error" : message);
        agentRunMetrics.finished("FAILED", run.createdAt());
    }

    public void cancel(Long ticketId, UUID runId) {
        AgentRun run = findById(runId);
        if (!run.ticketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Agent run " + runId + " was not found for ticket " + ticketId);
        }
        if (!agentRunRepository.cancel(runId)) {
            throw new BadRequestException("Only a running agent run can be cancelled");
        }
        agentRunRepository.appendEvent(runId, "WORKFLOW", "CANCELLED",
                "{\"contractVersion\":\"v1\",\"message\":\"Cancelled by an operator\"}");
        agentRunMetrics.finished("CANCELLED", run.createdAt());
    }

    public boolean isRunning(UUID runId, Long ticketId) {
        return agentRunRepository.isRunning(runId, ticketId);
    }

    public boolean isWaitingForApproval(UUID runId, Long ticketId) {
        return agentRunRepository.isWaitingForApproval(runId, ticketId);
    }

    /** Resume only an interrupted active run; terminal and approval-waiting runs are immutable. */
    public AgentRun resumeInterrupted(Long ticketId, UUID runId) {
        AgentRun run = findById(runId);
        if (!run.ticketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Agent run " + runId + " was not found for ticket " + ticketId);
        }
        if (!agentRunRepository.resumeInterrupted(runId)) {
            throw new BadRequestException("Only a running or interrupted agent run can be resumed");
        }
        agentRunRepository.appendEvent(runId, "WORKFLOW", "RESUMED",
                "{\"contractVersion\":\"v1\",\"message\":\"Resuming from the last durable checkpoint\"}");
        return findById(runId);
    }

    public void requireRunning(UUID runId, Long ticketId) {
        if (!isRunning(runId, ticketId)) {
            throw new BadRequestException("The agent run is no longer active; its action was not executed");
        }
    }

    public List<AgentRun> findByTicketId(Long ticketId) {
        return agentRunRepository.findByTicketId(ticketId);
    }

    public AgentRun findById(UUID runId) {
        return agentRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent run " + runId + " was not found"));
    }

    public List<AgentRunEvent> findEvents(UUID runId) {
        findById(runId);
        return agentRunRepository.findEvents(runId);
    }

    public List<AgentRunEvent> findEventsAfter(UUID runId, long afterEventId) {
        if (afterEventId < 0) {
            throw new BadRequestException("afterEventId must be zero or greater");
        }
        findById(runId);
        return agentRunRepository.findEventsAfter(runId, afterEventId);
    }
}
