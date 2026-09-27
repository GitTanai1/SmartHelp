package com.smarthelp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthelp.dto.AiDtos.ResolveTicketRequest;
import com.smarthelp.exception.BadRequestException;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.AgentApproval;
import com.smarthelp.repository.AgentApprovalRepository;
import com.smarthelp.repository.AgentRunRepository;

/** Owns approval state; only this service can turn a proposed resolution into a business write. */
@Service
public class AgentApprovalService {

    private final AgentApprovalRepository approvalRepository;
    private final AgentRunRepository runRepository;
    private final AiResolutionService aiResolutionService;
    private final AgentRunMetrics agentRunMetrics;

    public AgentApprovalService(AgentApprovalRepository approvalRepository, AgentRunRepository runRepository,
            AiResolutionService aiResolutionService, AgentRunMetrics agentRunMetrics) {
        this.approvalRepository = approvalRepository;
        this.runRepository = runRepository;
        this.aiResolutionService = aiResolutionService;
        this.agentRunMetrics = agentRunMetrics;
    }

    @Transactional
    public AgentApproval requestResolution(UUID runId, Long ticketId, String message, String priority) {
        if (!runRepository.isRunning(runId, ticketId)) {
            throw new BadRequestException("A resolution approval can only be requested by an active agent run");
        }
        AgentApproval approval = approvalRepository.createOrFindResolution(runId, ticketId, message, priority);
        if ("PENDING".equals(approval.status())) {
            runRepository.waitForApproval(runId);
            runRepository.appendEvent(runId, "APPROVAL", "PENDING",
                    "{\"contractVersion\":\"v1\",\"approvalId\":\"" + approval.id()
                            + "\",\"actionType\":\"TICKET_RESOLUTION\"}");
            agentRunMetrics.approvalRequested();
        }
        return approval;
    }

    public List<AgentApproval> findByRunId(UUID runId) {
        return approvalRepository.findByRunId(runId);
    }

    @Transactional
    public void cancel(Long ticketId, UUID runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent run " + runId + " was not found"));
        if (!run.ticketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Agent run " + runId + " was not found for ticket " + ticketId);
        }
        if (!runRepository.cancel(runId)) {
            throw new BadRequestException("Only a running or approval-waiting agent run can be cancelled");
        }
        approvalRepository.rejectPendingForRun(runId, actor(), "Agent run cancelled before approval");
        runRepository.appendEvent(runId, "WORKFLOW", "CANCELLED",
                "{\"contractVersion\":\"v1\",\"message\":\"Cancelled by an operator\"}");
        agentRunMetrics.finished("CANCELLED", run.createdAt());
    }

    @Transactional
    public AgentApproval approve(Long ticketId, UUID runId, UUID approvalId, String note) {
        AgentApproval approval = requirePending(ticketId, runId, approvalId);
        if (!approvalRepository.approve(approvalId, actor(), note)) {
            throw new BadRequestException("Approval was already decided");
        }
        if (!runRepository.resumeFromApproval(runId)) {
            throw new BadRequestException("The agent run is not waiting for approval");
        }
        aiResolutionService.resolve(ticketId, "approval:" + approvalId,
                new ResolveTicketRequest(approval.proposedMessage(), approval.proposedPriority()), runId);
        runRepository.complete(runId);
        agentRunMetrics.finished("COMPLETED", approval.requestedAt());
        agentRunMetrics.approvalDecided("APPROVED");
        runRepository.appendEvent(runId, "APPROVAL", "APPROVED",
                "{\"contractVersion\":\"v1\",\"approvalId\":\"" + approvalId + "\"}");
        return approvalRepository.findById(approvalId).orElseThrow();
    }

    @Transactional
    public AgentApproval reject(Long ticketId, UUID runId, UUID approvalId, String note) {
        AgentApproval approval = requirePending(ticketId, runId, approvalId);
        if (!approvalRepository.reject(approvalId, actor(), note)) {
            throw new BadRequestException("Approval was already decided");
        }
        if (!runRepository.cancel(runId)) {
            throw new BadRequestException("The agent run is no longer awaiting approval");
        }
        runRepository.appendEvent(runId, "APPROVAL", "REJECTED",
                "{\"contractVersion\":\"v1\",\"approvalId\":\"" + approvalId + "\"}");
        agentRunMetrics.approvalDecided("REJECTED");
        return approvalRepository.findById(approvalId).orElseThrow();
    }

    private AgentApproval requirePending(Long ticketId, UUID runId, UUID approvalId) {
        AgentApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent approval " + approvalId + " was not found"));
        if (!approval.ticketId().equals(ticketId) || !approval.runId().equals(runId)) {
            throw new ResourceNotFoundException("Agent approval " + approvalId + " was not found for this run");
        }
        if (!"PENDING".equals(approval.status())) {
            throw new BadRequestException("Approval was already decided");
        }
        return approval;
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "local-development" : authentication.getName();
    }
}
