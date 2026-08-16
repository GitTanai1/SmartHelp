package com.smarthelp.controller;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.smarthelp.dto.AiDtos.AiAnalysisResult;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.service.AIService;
import com.smarthelp.service.TicketService;

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
@RequestMapping("/api/tickets")
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    private final AIService aiService;
    private final TicketService ticketService;

    // A small thread pool for running SSE streams asynchronously.
    // Each /workflow request uses one thread for its duration.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AIController(AIService aiService, TicketService ticketService) {
        this.aiService = aiService;
        this.ticketService = ticketService;
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
        requireTicket(ticketId);
        AiAnalysisResult result = aiService.analyze(ticketId);
        return ResponseEntity.ok(result);
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
    public SseEmitter streamWorkflow(@PathVariable Long ticketId) {
        requireTicket(ticketId);

        // Timeout: 5 minutes is generous for a complete workflow run.
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executor.submit(() -> {
            try {
                aiService.streamWorkflowEvents(ticketId, (jsonData) -> {
                    try {
                        emitter.send(
                                SseEmitter.event()
                                        .data(jsonData, MediaType.APPLICATION_JSON));
                    } catch (IOException sendEx) {
                        // Client disconnected — stop streaming
                        emitter.completeWithError(sendEx);
                    }
                });
                emitter.complete();
            } catch (IOException streamEx) {
                log.error("Workflow stream failed for ticket id={}", ticketId, streamEx);
                try {
                    // Send a terminal error event so Angular can show a meaningful message
                    String errorJson = "{\"node\":\"WORKFLOW\",\"status\":\"FAILED\",\"message\":\""
                            + streamEx.getMessage().replace("\"", "'") + "\"}";
                    emitter.send(SseEmitter.event().data(errorJson, MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    // Best effort — if we can't send the error, just complete
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private void requireTicket(Long ticketId) {
        if (!ticketService.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " was not found");
        }
    }
}
