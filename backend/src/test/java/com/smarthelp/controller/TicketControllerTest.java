package com.smarthelp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smarthelp.dto.TicketDtos.CreateTicketRequest;
import com.smarthelp.dto.TicketDtos.TicketDetail;
import com.smarthelp.dto.TicketDtos.TicketSummary;
import com.smarthelp.exception.GlobalExceptionHandler;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.service.TicketService;

class TicketControllerTest {

    private final TicketService ticketService = org.mockito.Mockito.mock(TicketService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void createTicketReturnsCreated() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(ticketService.create(any(CreateTicketRequest.class)))
                .thenReturn(new com.smarthelp.model.Ticket(10L, 1L, 2L, "Subject", "Description", "OPEN", "LOW", now,
                        now));

        mockMvc.perform(post("/api/tickets")
                .contentType("application/json")
                .content("""
                        {"userId":1,"categoryId":2,"subject":"Subject","description":"Description","priority":"LOW"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void invalidTicketReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tickets")
                .contentType("application/json")
                .content("""
                        {"userId":1,"subject":"","description":"","priority":"BAD"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTicketReturnsNotFound() throws Exception {
        when(ticketService.findDetailById(99L)).thenThrow(new ResourceNotFoundException("Ticket 99 was not found"));

        mockMvc.perform(get("/api/tickets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket 99 was not found"));
    }

    @Test
    void listTicketsReturnsJsonArray() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        TicketSummary summary = new TicketSummary(1L, 1L, "Asha", 2L, "Billing", "Subject", "Description", "OPEN",
                "LOW", now, now);
        when(ticketService.findAll(eq("OPEN"), eq(null), eq(null), eq(null))).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/tickets?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subject").value("Subject"));
    }

    @Test
    void getTicketReturnsDetail() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        TicketSummary summary = new TicketSummary(1L, 1L, "Asha", 2L, "Billing", "Subject", "Description", "OPEN",
                "LOW", now, now);
        when(ticketService.findDetailById(1L)).thenReturn(new TicketDetail(summary, List.of()));

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.subject").value("Subject"));
    }
}
