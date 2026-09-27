package com.smarthelp.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.smarthelp.dto.AiDtos.AiAnalysisResult;
import com.smarthelp.dto.AiDtos.ResolveTicketRequest;
import com.smarthelp.dto.AiDtos.EscalateTicketRequest;
import com.smarthelp.dto.AiDtos.RequestResolutionApproval;
import com.smarthelp.config.RequestIdFilter;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.service.AIService;
import com.smarthelp.service.AiResolutionService;
import com.smarthelp.service.TicketService;
import com.smarthelp.service.AgentRunService;
import com.smarthelp.service.AgentApprovalService;
import com.smarthelp.model.Ticket;
import com.smarthelp.security.CurrentUserAccess;

import jakarta.validation.Valid;

/**
 * AIController exposes two endpoints for AI workflow interaction:
 *
 *   POST /api/tickets/{ticketId}/analyze
 *     Runs the full LangGraph workflow in a blocking request and returns the
 *     final structured result. Useful for API testing and for the Ticket Detail
 *     "Analyze" button that shows the final outcome without streaming.
 *
 *   GET  /api/tickets/{ticketId}/workflow
 *     Opens a Server-Sent Events stream. Spring Boot starts the workflow on
 *     the Python AI service and forwards each node event to the browser as
 *     it arrives. Angular uses EventSource to receive these events and update
 *     the workflow graph in real time.
 *
 * Responsibility boundary:
 *   - AIController owns the HTTP entry points and SSE infrastructure.
 *   - AIService owns the Java HttpClient calls to the Python service.
 *   - Python FastAPI / LangGraph owns the actual AI execution.
 *   - TicketService / ResponseService own persistence of the final result.
 */
@RestController
@RequestMapping({ "/api/v1/tickets", "/api/tickets" })
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    private final AIService aiService;
    private final TicketService ticketService;
    private final AiResolutionService aiResolutionService;
    private final TaskExecutor agentWorkflowExecutor;
    private final CurrentUserAccess currentUserAccess;
    private final AgentRunService agentRunService;
    private final AgentApprovalService agentApprovalService;

    public AIController(
            AIService aiService,
            TicketService ticketService,
            AiResolutionService aiResolutionService,
            @Qualifier("agentWorkflowExecutor") TaskExecutor agentWorkflowExecutor,
            CurrentUserAccess currentUserAccess,
            AgentRunService agentRunService,
            AgentApprovalService agentApprovalService) {
        this.aiService = aiService;
        this.ticketService = ticketService;
        this.aiResolutionService = aiResolutionService;
        this.agentWorkflowExecutor = agentWorkflowExecutor;
        this.currentUserAccess = currentUserAccess;
        this.agentRunService = agentRunService;
        this.agentApprovalService = agentApprovalService;
    }

    /**
     * Blocking AI analysis endpoint.
     *
     * Validates the ticket exists, calls AIService.analyze(), and returns
     * the structured result. If the AI service is unavailable, GlobalExceptionHandler
     * converts the BadRequestException into a 400 response.
     */
    @PostMapping("/{ticketId}/analyze")
    public ResponseEntity<AiAnalysisResult> analyze(@PathVariable Long ticketId) {
        currentUserAccess.requireAgent();
        requireTicket(ticketId);
        com.smarthelp.model.AgentRun run = agentRunService.start(ticketId);
        try {
            AiAnalysisResult result = aiService.analyze(ticketId, run.id());
            if (agentRunService.isRunning(run.id(), ticketId)) {
                agentRunService.complete(run.id());
            }
            return ResponseEntity.ok(result);
        } catch (RuntimeException analysisFailure) {
            agentRunService.fail(run.id(), analysisFailure.getMessage());
            throw analysisFailure;
        }
    }

    /** Atomically persist the AI response and the resulting ticket status. */
    @PostMapping("/{ticketId}/ai-resolution")
    public ResponseEntity<Ticket> resolve(
            @PathVariable Long ticketId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Agent-Run-ID", required = false) UUID agentRunId,
            @Valid @RequestBody ResolveTicketRequest request) {
        currentUserAccess.requireAgent();
        return ResponseEntity.ok(aiResolutionService.resolve(ticketId, idempotencyKey, request, agentRunId));
    }

    /** Atomically post an escalation note and change the ticket status. */
    @PostMapping("/{ticketId}/ai-escalation")
    public ResponseEntity<Ticket> escalate(
            @PathVariable Long ticketId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Agent-Run-ID", required = false) UUID agentRunId,
            @Valid @RequestBody EscalateTicketRequest request) {
        currentUserAccess.requireAgent();
        return ResponseEntity.ok(aiResolutionService.escalate(ticketId, idempotencyKey, request, agentRunId));
    }

    /** The agent can propose, but only a later operator approval can resolve. */
    @PostMapping("/{ticketId}/ai-resolution-approval")
    public ResponseEntity<com.smarthelp.model.AgentApproval> requestResolutionApproval(
            @PathVariable Long ticketId,
            @RequestHeader("X-Agent-Run-ID") UUID agentRunId,
            @Valid @RequestBody RequestResolutionApproval request) {
        currentUserAccess.requireAgent();
        return ResponseEntity.ok(agentApprovalService.requestResolution(
                agentRunId, ticketId, request.message(), request.priority()));
    }

    /**
     * SSE workflow streaming endpoint.
     *
     * Returns a text/event-stream response. The browser keeps the connection
     * open and Angular's EventSource receives each JSON event as it is emitted
     * by the Python LangGraph workflow.
     *
     * Flow:
     *   Angular EventSource connects to GET /api/tickets/{id}/workflow
     *     -> AIController creates SseEmitter
     *     -> executor submits background task
     *     -> AIService.streamWorkflowEvents() opens HTTP stream to Python
     *     -> each "data: ..." line from Python is forwarded to SseEmitter
     *     -> SseEmitter sends it to Angular as a server-sent event
     *     -> Angular updates the workflow graph
     *     -> stream ends, SseEmitter completes, EventSource closes
     */
    @GetMapping(value = "/{ticketId}/workflow", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWorkflow(
            @PathVariable Long ticketId,
            @RequestParam(value = "runId", required = false) UUID runId) {
        currentUserAccess.requireAgent();
        requireTicket(ticketId);
        com.smarthelp.model.AgentRun run = runId == null
                ? agentRunService.start(ticketId)
                : agentRunService.resumeInterrupted(ticketId, runId);

        // Timeout: 5 minutes is generous for a complete workflow run.
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        Runnable workflow = () -> {
            if (requestId != null) {
                MDC.put(RequestIdFilter.MDC_KEY, requestId);
            }
            try {
                aiService.streamWorkflowEvents(ticketId, run.id(), (jsonData) -> {
                    boolean waitingForApproval = agentRunService.isWaitingForApproval(run.id(), ticketId);
                    if (!agentRunService.isRunning(run.id(), ticketId) && !waitingForApproval) {
                        throw new AgentRunCancelledException();
                    }
                    agentRunService.appendEvent(run.id(), jsonData);
                    try {
                        emitter.send(
                                SseEmitter.event()
                                        .data(jsonData, MediaType.APPLICATION_JSON));
                    } catch (IOException sendEx) {
                        // Client disconnected — stop streaming
                        emitter.completeWithError(sendEx);
                    }
                    // Forward the terminal node before closing an approval-waiting stream.
                    if (waitingForApproval) {
                        throw new AgentRunWaitingApprovalException();
                    }
                });
                agentRunService.complete(run.id());
                emitter.complete();
            } catch (AgentRunCancelledException cancelled) {
                log.info("Agent workflow cancelled for ticket id={} run id={}", ticketId, run.id());
                emitter.complete();
            } catch (AgentRunWaitingApprovalException waiting) {
                log.info("Agent workflow awaiting approval for ticket id={} run id={}", ticketId, run.id());
                emitter.complete();
            } catch (Exception streamEx) {
                log.error("Workflow stream failed for ticket id={}", ticketId, streamEx);
                agentRunService.fail(run.id(), streamEx.getMessage());
                try {
                    // Keep Java-originated terminal events on the same validated v1
                    // shape as Python events, instead of emitting an ad-hoc JSON blob.
                    String errorJson = new tools.jackson.databind.ObjectMapper().writeValueAsString(
                            terminalFailureEvent(ticketId, run.id(), streamEx.getMessage()));
                    emitter.send(SseEmitter.event().data(errorJson, MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    // Best effort — if we can't send the error, just complete
                }
                emitter.complete();
            } finally {
                MDC.remove(RequestIdFilter.MDC_KEY);
            }
        };
        try {
            agentWorkflowExecutor.execute(workflow);
        } catch (RejectedExecutionException rejected) {
            log.warn("Agent workflow capacity exhausted for ticket id={}", ticketId);
            agentRunService.fail(run.id(), "Agent workflow capacity is temporarily exhausted");
            emitter.completeWithError(new IllegalStateException("Agent workflow capacity is temporarily exhausted"));
        }

        return emitter;
    }

    private void requireTicket(Long ticketId) {
        if (!ticketService.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " was not found");
        }
    }

    static com.smarthelp.dto.AiDtos.WorkflowEvent terminalFailureEvent(Long ticketId, UUID runId, String message) {
        String safeMessage = message == null || message.isBlank()
                ? "Workflow stream failed"
                : message.substring(0, Math.min(message.length(), 1_000));
        Map<String, Object> state = Map.of(
                "category", "unknown",
                "priority", "MEDIUM",
                "confidence", 0.0,
                "sensitive", false,
                "knowledgeCount", 0,
                "evidence", List.of(),
                "generatedResponse", "",
                "path", List.of("WORKFLOW"));
        return new com.smarthelp.dto.AiDtos.WorkflowEvent(
                "v1", ticketId, runId.toString(), "WORKFLOW", "FAILED", state, safeMessage);
    }

    private static final class AgentRunCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class AgentRunWaitingApprovalException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
