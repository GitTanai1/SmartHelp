package com.smarthelp.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smarthelp.dto.TicketDtos.CreateTicketRequest;
import com.smarthelp.dto.TicketDtos.TicketDetail;
import com.smarthelp.dto.TicketDtos.TicketSummary;
import com.smarthelp.dto.TicketDtos.UpdateTicketRequest;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.Ticket;
import com.smarthelp.repository.CategoryRepository;
import com.smarthelp.repository.ResponseRepository;
import com.smarthelp.repository.TicketRepository;
import com.smarthelp.repository.UserRepository;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ResponseRepository responseRepository;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ResponseRepository responseRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.responseRepository = responseRepository;
    }

    public Ticket create(CreateTicketRequest request) {
        requireUser(request.userId());
        requireCategoryIfPresent(request.categoryId());
        Ticket ticket = ticketRepository.create(
                request.userId(),
                request.categoryId(),
                request.subject(),
                request.description(),
                request.priority());
        log.info("Created ticket id={} userId={} priority={}", ticket.id(), ticket.userId(), ticket.priority());
        return ticket;
    }

    public List<TicketSummary> findAll(String status, Long categoryId, Long userId, String priority) {
        return ticketRepository.findAll(status, categoryId, userId, priority);
    }

    public Ticket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + id + " was not found"));
    }

    public TicketDetail findDetailById(Long id) {
        TicketSummary ticket = ticketRepository.findSummaryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + id + " was not found"));
        log.info("Retrieved ticket detail id={}", id);
        return new TicketDetail(ticket, responseRepository.findByTicketId(id));
    }

    public Ticket update(Long id, UpdateTicketRequest request) {
        findById(id);
        requireCategoryIfPresent(request.categoryId());
        ticketRepository.update(
                id,
                request.categoryId(),
                request.subject(),
                request.description(),
                request.status(),
                request.priority());
        Ticket updated = findById(id);
        log.info("Updated ticket id={} status={} priority={}", updated.id(), updated.status(), updated.priority());
        return updated;
    }

    public Ticket updateStatus(Long id, String status) {
        findById(id);
        ticketRepository.updateStatus(id, status);
        Ticket updated = findById(id);
        log.info("Updated ticket status id={} status={}", id, status);
        return updated;
    }

    public void delete(Long id) {
        findById(id);
        ticketRepository.delete(id);
        log.info("Deleted ticket id={}", id);
    }

    public boolean existsById(Long id) {
        return ticketRepository.existsById(id);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User " + userId + " was not found");
        }
    }

    private void requireCategoryIfPresent(Long categoryId) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category " + categoryId + " was not found");
        }
    }
}
