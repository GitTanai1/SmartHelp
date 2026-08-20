package com.smarthelp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthelp.dto.TicketDtos.CreateTicketRequest;
import com.smarthelp.dto.TicketDtos.UpdateTicketRequest;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.Ticket;
import com.smarthelp.repository.CategoryRepository;
import com.smarthelp.repository.ResponseRepository;
import com.smarthelp.repository.TicketRepository;
import com.smarthelp.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketRepository ticketRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    ResponseRepository responseRepository;

    @InjectMocks
    TicketService ticketService;

    @Test
    void createTicketChecksUserAndCategory() {
        Ticket saved = ticket(10L, "OPEN", "MEDIUM");
        when(userRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.existsById(2L)).thenReturn(true);
        when(ticketRepository.create(1L, 2L, "Subject", "Description", "MEDIUM")).thenReturn(saved);

        Ticket result = ticketService.create(new CreateTicketRequest(1L, 2L, "Subject", "Description", "MEDIUM"));

        assertThat(result.id()).isEqualTo(10L);
        verify(ticketRepository).create(1L, 2L, "Subject", "Description", "MEDIUM");
    }

    @Test
    void findTicketReturnsExistingTicket() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket(10L, "OPEN", "LOW")));

        Ticket result = ticketService.findById(10L);

        assertThat(result.subject()).isEqualTo("Subject");
    }

    @Test
    void updateTicketChecksCategoryAndReturnsUpdatedTicket() {
        when(ticketRepository.findById(10L))
                .thenReturn(Optional.of(ticket(10L, "OPEN", "LOW")))
                .thenReturn(Optional.of(ticket(10L, "IN_PROGRESS", "HIGH")));
        when(categoryRepository.existsById(2L)).thenReturn(true);

        Ticket result = ticketService.update(
                10L,
                new UpdateTicketRequest(2L, "Updated", "Updated description", "IN_PROGRESS", "HIGH"));

        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        verify(ticketRepository).update(10L, 2L, "Updated", "Updated description", "IN_PROGRESS", "HIGH");
    }

    @Test
    void deleteTicketRequiresExistingTicket() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket(10L, "OPEN", "LOW")));

        ticketService.delete(10L);

        verify(ticketRepository).delete(10L);
    }

    @Test
    void missingTicketThrowsNotFound() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ticket 99");
    }

    private Ticket ticket(Long id, String status, String priority) {
        LocalDateTime now = LocalDateTime.now();
        return new Ticket(id, 1L, 2L, "Subject", "Description", status, priority, now, now);
    }
}
