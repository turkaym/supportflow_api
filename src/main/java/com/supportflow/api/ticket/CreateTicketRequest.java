package com.supportflow.api.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTicketRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull TicketPriority priority
) {
}
