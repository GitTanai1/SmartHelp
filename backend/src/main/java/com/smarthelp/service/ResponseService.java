package com.smarthelp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthelp.dto.ResponseDtos.CreateTicketResponseRequest;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.TicketResponse;
import com.smarthelp.repository.ResponseRepository;
import com.smarthelp.repository.TicketRepository;

@Service
public class ResponseService {

    private final ResponseRepository responseRepository;
    private final TicketRepository ticketRepository;

    public ResponseService(ResponseRepository responseRepository, TicketRepository ticketRepository) {
        this.responseRepository = responseRepository;
        this.ticketRepository = ticketRepository;
    }

    public TicketResponse create(Long ticketId, CreateTicketResponseRequest request) {
        requireTicket(ticketId);
        return responseRepository.create(ticketId, request.message(), request.senderType());
    }

    public List<TicketResponse> findByTicketId(Long ticketId) {
        requireTicket(ticketId);
        return responseRepository.findByTicketId(ticketId);
    }

    private void requireTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " was not found");
        }
    }
}
