package com.smarthelp.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import com.smarthelp.dto.AiDtos.AiAnalysisResult;
import com.smarthelp.dto.AiDtos.WorkflowEvent;
import com.smarthelp.config.RequestIdFilter;
import com.smarthelp.exception.BadRequestException;

/**
 * AIService communicates with the Python FastAPI AI service.
 *
 * Responsibilities:
 * - POST /analyze to run a blocking analysis and return the final result,
 * - GET /workflow/{ticketId}/stream to forward SSE events line by line.
 *
 * The AI service owns the LangGraph workflow. Spring Boot owns the tickets,
 * responses, and final ticket-status updates. Spring Boot acts as the API
 * gateway between Angular and the AI service.
 *
 * Spring Boot's built-in RestTemplate / WebClient is intentionally avoided
 * here to keep the dependency list small. Java's built-in HttpClient (Java 11+)
 * is used instead, which also makes the HTTP communication very visible for
 * learning.
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final String aiBaseUrl;
    private final String agentSharedSecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AIService(
            @Value("${smarthelp.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${smarthelp.ai.shared-secret:}") String agentSharedSecret,
            @Value("${smarthelp.ai.require-shared-secret:false}") boolean requireAgentSharedSecret,
            ObjectMapper objectMapper) {
        this.aiBaseUrl = aiBaseUrl.replaceAll("/+$", "");
        this.agentSharedSecret = agentSharedSecret;
        if (requireAgentSharedSecret && agentSharedSecret.isBlank()) {
            throw new IllegalStateException("SMARTHELP_AGENT_SHARED_SECRET is required for this deployment");
        }
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Runs the full AI analysis workflow for a ticket in a blocking request/response
     * call. Returns the structured final result from the Python service.
     *
     * Used by POST /api/tickets/{ticketId}/analyze.
     */
    public AiAnalysisResult analyze(Long ticketId, UUID runId) {
        String url = aiBaseUrl + "/analyze";
        String body = "{\"ticketId\":" + ticketId + "}";
        log.info("Starting AI analysis for ticket id={}", ticketId);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(120));
        addRequestId(requestBuilder);
        requestBuilder.header("X-Agent-Run-ID", runId.toString());
        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("AI service returned {} for ticket id={}", response.statusCode(), ticketId);
                throw new BadRequestException(
                        "AI service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            AiAnalysisResult result = objectMapper.readValue(response.body(), AiAnalysisResult.class);
            if (!"v1".equals(result.contractVersion())) {
                throw new BadRequestException("Unsupported AI analysis contract version");
            }
            if (!ticketId.equals(result.ticketId())) {
                throw new BadRequestException("AI analysis response ticket does not match the requested ticket");
            }
            log.info("AI analysis complete for ticket id={} finalStatus={}", ticketId, result.finalStatus());
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Could not reach AI service at {} for ticket id={}", aiBaseUrl, ticketId, ex);
            throw new BadRequestException(
                    "Could not reach AI service at " + aiBaseUrl + ". Ensure it is running on port 8000.");
        } catch (IOException ex) {
            log.error("Could not reach AI service at {} for ticket id={}", aiBaseUrl, ticketId, ex);
            throw new BadRequestException(
                    "Could not reach AI service at " + aiBaseUrl + ". Ensure it is running on port 8000.");
        }
    }

    /**
     * Streams workflow events from the Python AI service to the provided consumer
     * function. Each event arrives as a raw SSE data line.
     *
     * Used by GET /api/tickets/{ticketId}/workflow.
     *
     * The consumer is called once per "data: ..." line received from the AI service.
     * Spring Boot's SseEmitter, called by AIController, writes each line to the
     * browser as an SSE event.
     *
     * @param ticketId      ticket to analyse
     * @param eventConsumer receives each raw JSON string (without the "data: " prefix)
     * @throws IOException when the stream cannot be established or read
     */
    public void streamWorkflowEvents(Long ticketId, UUID runId, Consumer<String> eventConsumer) throws IOException {
        String url = aiBaseUrl + "/workflow/" + ticketId + "/stream";
        log.info("Opening AI workflow stream for ticket id={}", ticketId);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofMinutes(5));
        addRequestId(requestBuilder);
        requestBuilder.header("X-Agent-Run-ID", runId.toString());
        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                throw new IOException("AI service returned HTTP " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).strip();
                        if (!data.isEmpty()) {
                            WorkflowEvent event = objectMapper.readValue(data, WorkflowEvent.class);
                            if (!"v1".equals(event.contractVersion())) {
                                throw new IOException("Unsupported AI workflow event contract version");
                            }
                            if (!ticketId.equals(event.ticketId())) {
                                throw new IOException("AI workflow event ticket does not match the requested ticket");
                            }
                            if (event.runId() != null && !runId.toString().equals(event.runId())) {
                                throw new IOException("AI workflow event run does not match the requested run");
                            }
                            eventConsumer.accept(objectMapper.writeValueAsString(event));
                        }
                    }
                }
            }
            log.info("AI workflow stream completed for ticket id={}", ticketId);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Workflow stream interrupted", ex);
        }
    }

    private void addRequestId(HttpRequest.Builder requestBuilder) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            requestBuilder.header(RequestIdFilter.HEADER_NAME, requestId);
        }
        if (!agentSharedSecret.isBlank()) {
            requestBuilder.header("X-SmartHelp-Agent-Token", agentSharedSecret);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            requestBuilder.header("Authorization", "Bearer " + jwt.getToken().getTokenValue());
        }
    }
}
