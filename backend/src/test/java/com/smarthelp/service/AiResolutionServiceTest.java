package com.smarthelp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthelp.dto.AiDtos.ResolveTicketRequest;
import com.smarthelp.dto.AiDtos.EscalateTicketRequest;
import com.smarthelp.dto.ResponseDtos.CreateTicketResponseRequest;
import com.smarthelp.dto.TicketDtos.UpdateTicketRequest;
import com.smarthelp.model.Ticket;
import com.smarthelp.repository.AgentActionRepository;
import com.smarthelp.repository.AuditEventRepository;

@ExtendWith(MockitoExtension.class)
class AiResolutionServiceTest {

    @Mock
    private ResponseService responseService;

    @Mock
    private TicketService ticketService;

    @Mock
    private AgentActionRepository agentActionRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AgentRunService agentRunService;

    @InjectMocks
    private AiResolutionService aiResolutionService;

    @Test
    void resolvePostsAiResponseAndResolvesWithRequestedPriority() {
        Ticket current = ticket("OPEN", "LOW");
        Ticket resolved = ticket("RESOLVED", "HIGH");
        when(ticketService.findById(1L)).thenReturn(current);
        when(ticketService.update(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(resolved);
        when(agentActionRepository.tryStart(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(true);

        Ticket result = aiResolutionService.resolve(
                1L, "run-1:resolve", new ResolveTicketRequest("Resolved from policy.", "HIGH"), null);

        ArgumentCaptor<CreateTicketResponseRequest> response = ArgumentCaptor.forClass(CreateTicketResponseRequest.class);
        ArgumentCaptor<UpdateTicketRequest> update = ArgumentCaptor.forClass(UpdateTicketRequest.class);
        verify(responseService).create(org.mockito.ArgumentMatchers.eq(1L), response.capture());
        verify(ticketService).update(org.mockito.ArgumentMatchers.eq(1L), update.capture());
        verify(agentActionRepository).markCompleted("run-1:resolve");
        verify(auditEventRepository).append(
                org.mockito.ArgumentMatchers.eq("TICKET_RESOLVED_BY_AI"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("run-1:resolve"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("status=RESOLVED"));
        assertThat(response.getValue().senderType()).isEqualTo("AI");
        assertThat(update.getValue().status()).isEqualTo("RESOLVED");
        assertThat(update.getValue().priority()).isEqualTo("HIGH");
        assertThat(result.status()).isEqualTo("RESOLVED");
    }

    @Test
    void escalatePostsAiNoteAndEscalatesTicket() {
        Ticket current = ticket("OPEN", "MEDIUM");
        Ticket escalated = ticket("ESCALATED", "MEDIUM");
        when(ticketService.findById(1L)).thenReturn(current);
        when(ticketService.update(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(escalated);
        when(agentActionRepository.tryStart(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(true);

        Ticket result = aiResolutionService.escalate(
                1L, "run-2:escalate", new EscalateTicketRequest("Needs review."), null);

        ArgumentCaptor<CreateTicketResponseRequest> response = ArgumentCaptor.forClass(CreateTicketResponseRequest.class);
        ArgumentCaptor<UpdateTicketRequest> update = ArgumentCaptor.forClass(UpdateTicketRequest.class);
        verify(responseService).create(org.mockito.ArgumentMatchers.eq(1L), response.capture());
        verify(ticketService).update(org.mockito.ArgumentMatchers.eq(1L), update.capture());
        assertThat(response.getValue().message()).contains("Needs review.");
        assertThat(update.getValue().status()).isEqualTo("ESCALATED");
        assertThat(result.status()).isEqualTo("ESCALATED");
        verify(auditEventRepository).append(
                org.mockito.ArgumentMatchers.eq("TICKET_ESCALATED_BY_AI"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("run-2:escalate"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("status=ESCALATED"));
    }

    private Ticket ticket(String status, String priority) {
        LocalDateTime now = LocalDateTime.now();
        return new Ticket(1L, 2L, 3L, "Subject", "Description", status, priority, now, now);
    }
}
