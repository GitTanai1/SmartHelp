package com.smarthelp.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import com.smarthelp.exception.BadRequestException;
import com.smarthelp.model.AgentRun;
import com.smarthelp.security.CurrentUserAccess;
import com.smarthelp.service.AIService;
import com.smarthelp.service.AgentApprovalService;
import com.smarthelp.service.AgentRunService;
import com.smarthelp.service.AiResolutionService;
import com.smarthelp.service.TicketService;

@ExtendWith(MockitoExtension.class)
class AIControllerTest {

    @Mock private AIService aiService;
    @Mock private TicketService ticketService;
    @Mock private AiResolutionService aiResolutionService;
    @Mock private TaskExecutor agentWorkflowExecutor;
    @Mock private CurrentUserAccess currentUserAccess;
    @Mock private AgentRunService agentRunService;
    @Mock private AgentApprovalService agentApprovalService;

    @InjectMocks private AIController controller;

    @Test
    void terminalFailureEventUsesTheVersionedWorkflowContractShape() {
        UUID runId = UUID.randomUUID();
        var event = AIController.terminalFailureEvent(9L, runId, "agent upstream failed");

        assertThat(event.contractVersion()).isEqualTo("v1");
        assertThat(event.ticketId()).isEqualTo(9L);
        assertThat(event.runId()).isEqualTo(runId.toString());
        assertThat(event.node()).isEqualTo("WORKFLOW");
        assertThat(event.status()).isEqualTo("FAILED");
        assertThat(event.state()).containsEntry("confidence", 0.0)
                .containsEntry("knowledgeCount", 0)
                .containsKey("evidence");
    }

    @Test
    void blockingAnalysisMarksItsRunFailedWhenPythonFails() {
        UUID runId = UUID.randomUUID();
        AgentRun run = new AgentRun(
                runId, 9L, "RUNNING", "agent", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(ticketService.existsById(9L)).thenReturn(true);
        when(agentRunService.start(9L)).thenReturn(run);
        when(aiService.analyze(9L, runId)).thenThrow(new BadRequestException("AI service unavailable"));

        assertThatThrownBy(() -> controller.analyze(9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("AI service unavailable");

        verify(agentRunService).fail(runId, "AI service unavailable");
    }
}
