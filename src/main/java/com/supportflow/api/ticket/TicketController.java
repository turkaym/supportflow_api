package com.supportflow.api.ticket;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request, Authentication authentication,
                                 @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ticketService.create(request, authentication, idempotencyKey);
    }

    @GetMapping("/{id}")
    public TicketResponse getById(@PathVariable UUID id, Authentication authentication) {
        return ticketService.getById(id, authentication);
    }
}
