package com.smarthelp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.slf4j.MDC;

import com.smarthelp.config.RequestIdFilter;
import com.smarthelp.exception.BadRequestException;
import com.smarthelp.model.AgentRun;
import com.smarthelp.repository.AgentRunRepository;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private AgentRunMetrics agentRunMetrics;

    @InjectMocks
    private AgentRunService agentRunService;

    @Test
    void startPersistsTheCurrentRequestCorrelationId() {
        String requestId = UUID.randomUUID().toString();
        UUID runId = UUID.randomUUID();
        MDC.put(RequestIdFilter.MDC_KEY, requestId);
        try {
            when(agentRunRepository.create(org.mockito.ArgumentMatchers.any(UUID.class),
                    org.mockito.ArgumentMatchers.eq(8L),
                    org.mockito.ArgumentMatchers.eq("local-development"),
                    org.mockito.ArgumentMatchers.eq(requestId)))
                    .thenReturn(run(runId, 8L, "RUNNING"));

            agentRunService.start(8L);

            verify(agentRunRepository).create(org.mockito.ArgumentMatchers.any(UUID.class),
                    org.mockito.ArgumentMatchers.eq(8L),
                    org.mockito.ArgumentMatchers.eq("local-development"),
                    org.mockito.ArgumentMatchers.eq(requestId));
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    @Test
    void cancellationMarksOnlyTheMatchingRunningRunAndRecordsAnEvent() {
        UUID runId = UUID.randomUUID();
        when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run(runId, 8L, "RUNNING")));
        when(agentRunRepository.cancel(runId)).thenReturn(true);

        agentRunService.cancel(8L, runId);

        verify(agentRunRepository).cancel(runId);
        verify(agentRunRepository).appendEvent(
                runId, "WORKFLOW", "CANCELLED",
                "{\"contractVersion\":\"v1\",\"message\":\"Cancelled by an operator\"}");
    }

    @Test
    void cancellationDoesNotClaimSuccessForACompletedRun() {
        UUID runId = UUID.randomUUID();
        when(agentRunRepository.findById(runId)).thenReturn(Optional.of(run(runId, 8L, "COMPLETED")));
        when(agentRunRepository.cancel(runId)).thenReturn(false);

        assertThatThrownBy(() -> agentRunService.cancel(8L, runId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("running");
    }

    @Test
    void actionGuardRejectsACancelledRun() {
        UUID runId = UUID.randomUUID();
        when(agentRunRepository.isRunning(runId, 8L)).thenReturn(false);

        assertThatThrownBy(() -> agentRunService.requireRunning(runId, 8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not executed");
    }

    @Test
    void resumeAllowsOnlyAnInterruptedRunAndRecordsRecovery() {
        UUID runId = UUID.randomUUID();
        AgentRun interrupted = run(runId, 8L, "FAILED");
        AgentRun resumed = run(runId, 8L, "RUNNING");
        when(agentRunRepository.findById(runId)).thenReturn(Optional.of(interrupted), Optional.of(resumed));
        when(agentRunRepository.resumeInterrupted(runId)).thenReturn(true);

        agentRunService.resumeInterrupted(8L, runId);

        verify(agentRunRepository).resumeInterrupted(runId);
        verify(agentRunRepository).appendEvent(runId, "WORKFLOW", "RESUMED",
                "{\"contractVersion\":\"v1\",\"message\":\"Resuming from the last durable checkpoint\"}");
    }

    @Test
    void eventCursorRejectsNegativeValues() {
        assertThatThrownBy(() -> agentRunService.findEventsAfter(UUID.randomUUID(), -1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("zero or greater");
    }

    private AgentRun run(UUID id, Long ticketId, String status) {
        LocalDateTime now = LocalDateTime.now();
        return new AgentRun(id, ticketId, status, "agent@example.com", null, now, now, null, null);
    }
}
