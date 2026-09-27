package com.smarthelp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthelp.model.AgentApproval;
import com.smarthelp.model.AgentRun;
import com.smarthelp.repository.AgentApprovalRepository;
import com.smarthelp.repository.AgentRunRepository;

@ExtendWith(MockitoExtension.class)
class AgentApprovalServiceTest {

    @Mock
    private AgentApprovalRepository approvalRepository;

    @Mock
    private AgentRunRepository runRepository;

    @Mock
    private AiResolutionService aiResolutionService;

    @Mock
    private AgentRunMetrics agentRunMetrics;

    @InjectMocks
    private AgentApprovalService approvalService;

    @Test
    void proposalMovesAnActiveRunToWaitingForApproval() {
        UUID runId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        AgentApproval approval = approval(approvalId, runId, "PENDING");
        when(runRepository.isRunning(runId, 4L)).thenReturn(true);
        when(approvalRepository.createOrFindResolution(runId, 4L, "Draft response", "MEDIUM"))
                .thenReturn(approval);

        AgentApproval result = approvalService.requestResolution(runId, 4L, "Draft response", "MEDIUM");

        assertThat(result.id()).isEqualTo(approvalId);
        verify(runRepository).waitForApproval(runId);
        verify(runRepository).appendEvent(eq(runId), eq("APPROVAL"), eq("PENDING"), any());
    }

    @Test
    void approvedProposalResumesRunBeforeExecutingIdempotentResolution() {
        UUID runId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        AgentApproval pending = approval(approvalId, runId, "PENDING");
        AgentApproval approved = approval(approvalId, runId, "APPROVED");
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(pending), Optional.of(approved));
        when(approvalRepository.approve(eq(approvalId), any(), any())).thenReturn(true);
        when(runRepository.resumeFromApproval(runId)).thenReturn(true);

        AgentApproval result = approvalService.approve(4L, runId, approvalId, "Reviewed");

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(aiResolutionService).resolve(eq(4L), eq("approval:" + approvalId), any(), eq(runId));
        verify(runRepository).complete(runId);
    }

    private AgentApproval approval(UUID id, UUID runId, String status) {
        LocalDateTime now = LocalDateTime.now();
        return new AgentApproval(id, runId, 4L, "TICKET_RESOLUTION", "Draft response", "MEDIUM", status,
                now, "PENDING".equals(status) ? null : now, null, null);
    }
}
