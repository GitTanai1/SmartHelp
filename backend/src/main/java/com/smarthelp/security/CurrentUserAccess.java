package com.smarthelp.security;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.Ticket;
import com.smarthelp.model.User;
import com.smarthelp.repository.TicketRepository;
import com.smarthelp.repository.UserRepository;

/** Server-side ownership and role checks based on a verified OIDC email claim. */
@Component
public class CurrentUserAccess {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    public CurrentUserAccess(UserRepository userRepository, TicketRepository ticketRepository) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
    }

    public void requireTicketAccess(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " was not found"));
        currentUser().ifPresent(user -> {
            if ("CUSTOMER".equals(user.role()) && !user.id().equals(ticket.userId())) {
                deny("You cannot access another customer's ticket");
            }
        });
    }

    public Long effectiveTicketUserFilter(Long requestedUserId) {
        return currentUser().map(user -> {
            if ("CUSTOMER".equals(user.role())) {
                if (requestedUserId != null && !requestedUserId.equals(user.id())) {
                    deny("You cannot filter tickets for another user");
                }
                return user.id();
            }
            return requestedUserId;
        }).orElse(requestedUserId);
    }

    public void requireTicketCreationFor(Long userId) {
        currentUser().ifPresent(user -> {
            if ("CUSTOMER".equals(user.role()) && !user.id().equals(userId)) {
                deny("You cannot create a ticket for another user");
            }
        });
    }

    public void requireAgent() {
        currentUser().ifPresent(user -> {
            if (!"AGENT".equals(user.role())) {
                deny("This operation requires the AGENT role");
            }
        });
    }

    public void requireUserAccess(Long userId) {
        currentUser().ifPresent(user -> {
            if (!"AGENT".equals(user.role()) && !user.id().equals(userId)) {
                deny("You cannot access another user");
            }
        });
    }

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            // Local development has no OIDC principal. Production filter-chain
            // configuration requires a JWT before reaching these checks.
            return Optional.empty();
        }
        String email = jwt.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            deny("Verified email claim is required");
        }
        return userRepository.findByEmail(email)
                .or(() -> {
                    deny("No SmartHelp user is mapped to this identity");
                    return Optional.empty();
                });
    }

    private void deny(String message) {
        throw new AccessDeniedException(message);
    }
}
