package com.supportflow.api.ticket;

import com.supportflow.api.user.User;
import com.supportflow.api.user.UserRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._~-]{1,64}");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, Authentication authentication, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw invalidInput();
        }
        String title = normalizeAndValidate(request == null ? null : request.title(), MAX_TITLE_LENGTH);
        String description = normalizeAndValidate(request == null ? null : request.description(), MAX_DESCRIPTION_LENGTH);
        if (request.priority() == null) {
            throw invalidInput();
        }

        User requester = resolveRequester(authentication);
        ticketRepository.lockIdempotencyKey(requester.getId(), idempotencyKey);
        return ticketRepository.findByRequester_IdAndIdempotencyKey(requester.getId(), idempotencyKey)
                .map(ticket -> replay(ticket, title, description, request.priority()))
                .orElseGet(() -> TicketResponse.from(ticketRepository.saveAndFlush(
                        new Ticket(requester, title, description, request.priority(), idempotencyKey))));
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(UUID id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw unauthorized();
        }
        return ticketRepository.findByIdAndRequester_Email(id, authentication.getName())
                .map(TicketResponse::from)
                .orElseThrow(TicketService::ticketNotFound);
    }

    private static TicketResponse replay(Ticket ticket, String title, String description, TicketPriority priority) {
        if (!ticket.getTitle().equals(title) || !ticket.getDescription().equals(description)
                || ticket.getPriority() != priority) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
        }
        return TicketResponse.from(ticket);
    }

    private User resolveRequester(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw unauthorized();
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(TicketService::unauthorized);
    }

    private static String normalizeAndValidate(String value, int maximumLength) {
        if (value == null) {
            throw invalidInput();
        }
        String normalized = stripEdges(value);
        int codePointLength = normalized.codePointCount(0, normalized.length());
        if (normalized.indexOf('\0') >= 0 || codePointLength == 0 || codePointLength > maximumLength) {
            throw invalidInput();
        }
        return normalized;
    }

    private static String stripEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (start < end && isWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static ResponseStatusException invalidInput() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation failed");
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    private static ResponseStatusException ticketNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
    }
}
