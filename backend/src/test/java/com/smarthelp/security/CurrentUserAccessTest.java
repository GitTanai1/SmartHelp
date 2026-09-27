package com.smarthelp.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.smarthelp.model.Ticket;
import com.smarthelp.model.User;
import com.smarthelp.repository.TicketRepository;
import com.smarthelp.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CurrentUserAccessTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private CurrentUserAccess currentUserAccess;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotAccessAnotherCustomersTicket() {
        authenticate("customer@example.test");
        when(userRepository.findByEmail("customer@example.test")).thenReturn(Optional.of(user(1L, "CUSTOMER")));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket(10L, 2L)));

        assertThatThrownBy(() -> currentUserAccess.requireTicketAccess(10L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void agentCanAccessAnyTicket() {
        authenticate("agent@example.test");
        when(userRepository.findByEmail("agent@example.test")).thenReturn(Optional.of(user(9L, "AGENT")));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket(10L, 2L)));

        currentUserAccess.requireTicketAccess(10L);
    }

    @Test
    void customerCannotSupplyAnotherUserFilter() {
        authenticate("customer@example.test");
        when(userRepository.findByEmail("customer@example.test")).thenReturn(Optional.of(user(1L, "CUSTOMER")));

        assertThatThrownBy(() -> currentUserAccess.effectiveTicketUserFilter(2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(String email) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("email", email)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private User user(Long id, String role) {
        return new User(id, "Name", role.toLowerCase() + "@example.test", role, LocalDateTime.now());
    }

    private Ticket ticket(Long id, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return new Ticket(id, userId, null, "Subject", "Description", "OPEN", "LOW", now, now);
    }
}
