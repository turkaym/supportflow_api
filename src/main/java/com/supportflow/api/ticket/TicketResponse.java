package com.supportflow.api.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
