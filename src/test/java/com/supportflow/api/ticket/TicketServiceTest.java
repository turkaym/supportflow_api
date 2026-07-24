package com.supportflow.api.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.supportflow.api.user.Role;
import com.supportflow.api.user.User;
import com.supportflow.api.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String REQUESTER_EMAIL = "person@example.com";
    private static final String IDEMPOTENCY_KEY = "ticket-retry_1";

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    private TicketService ticketService;
    private User requester;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository);
        requester = new User(REQUESTER_EMAIL, "password-hash", Role.USER);
        ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());
        authentication = UsernamePasswordAuthenticationToken.authenticated(
                REQUESTER_EMAIL,
                "credentials",
                List.of()
        );
    }

    @Test
    void createsOneOpenTicketOwnedByRequesterWithNormalizedSafeResponse() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        persistTicketOnSave();

        TicketResponse response = ticketService.create(
                new CreateTicketRequest("  Printer failure  ", "  Cannot print invoices.  ", TicketPriority.HIGH),
                authentication, IDEMPOTENCY_KEY
        );

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveAndFlush(captor.capture());
        Ticket saved = captor.getValue();
        assertThat(saved.getRequester()).isSameAs(requester);
        assertThat(saved.getTitle()).isEqualTo("Printer failure");
        assertThat(saved.getDescription()).isEqualTo("Cannot print invoices.");
        assertThat(saved.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Printer failure");
        assertThat(response.description()).isEqualTo("Cannot print invoices.");
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void acceptsTrimmedInclusiveTextBoundaries() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        persistTicketOnSave();
        String maxTitle = "t".repeat(200);
        String maxDescription = "d".repeat(5000);

        TicketResponse minimum = ticketService.create(
                new CreateTicketRequest(" t ", " d ", TicketPriority.LOW),
                authentication, "minimum"
        );
        TicketResponse maximum = ticketService.create(
                new CreateTicketRequest(" " + maxTitle + " ", " " + maxDescription + " ", TicketPriority.MEDIUM),
                authentication, "maximum"
        );

        assertThat(minimum.title()).isEqualTo("t");
        assertThat(minimum.description()).isEqualTo("d");
        assertThat(maximum.title()).isEqualTo(maxTitle);
        assertThat(maximum.description()).isEqualTo(maxDescription);
        verify(ticketRepository, times(2)).saveAndFlush(any(Ticket.class));
    }

    @Test
    void stripsUnicodeWhitespaceFromPersistedAndReturnedText() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        persistTicketOnSave();

        TicketResponse response = ticketService.create(
                new CreateTicketRequest("\u2003Printer failure\u2003", "\u2003Cannot print invoices.\u2003", TicketPriority.HIGH),
                authentication, IDEMPOTENCY_KEY
        );

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Printer failure");
        assertThat(captor.getValue().getDescription()).isEqualTo("Cannot print invoices.");
        assertThat(response.title()).isEqualTo("Printer failure");
        assertThat(response.description()).isEqualTo("Cannot print invoices.");
    }

    @Test
    void stripsNoBreakSpacesFromPersistedAndReturnedText() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        persistTicketOnSave();

        TicketResponse response = ticketService.create(
                new CreateTicketRequest("\u00A0Printer\u00A0failure\u00A0", "\u00A0Cannot print invoices.\u00A0", TicketPriority.HIGH),
                authentication, IDEMPOTENCY_KEY
        );

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Printer\u00A0failure");
        assertThat(captor.getValue().getDescription()).isEqualTo("Cannot print invoices.");
        assertThat(response.title()).isEqualTo("Printer\u00A0failure");
        assertThat(response.description()).isEqualTo("Cannot print invoices.");
    }

    @Test
    void acceptsTwoHundredSupplementaryUnicodeCodePoints() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        persistTicketOnSave();
        String title = "\uD83D\uDE80".repeat(200);

        TicketResponse response = ticketService.create(
                new CreateTicketRequest(title, "Description", TicketPriority.MEDIUM),
                authentication, IDEMPOTENCY_KEY
        );

        assertThat(response.title()).isEqualTo(title);
        verify(ticketRepository).saveAndFlush(any(Ticket.class));
    }

    @Test
    void rejectsBlankTextBeforeRequesterLookupOrSave() {
        assertInvalidWithoutSideEffects(new CreateTicketRequest("   ", "description", TicketPriority.LOW));
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title", " \t ", TicketPriority.LOW));
    }

    @Test
    void rejectsUnicodeWhitespaceOnlyTextBeforeRequesterLookupOrSave() {
        assertInvalidWithoutSideEffects(new CreateTicketRequest("\u2003", "description", TicketPriority.LOW));
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title", "\u2003", TicketPriority.LOW));
        assertInvalidWithoutSideEffects(new CreateTicketRequest("\u00A0", "description", TicketPriority.LOW));
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title", "\u00A0", TicketPriority.LOW));
    }

    @Test
    void rejectsNullCharactersBeforeRequesterOrRepositoryInteractions() {
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title\0suffix", "description", TicketPriority.LOW));
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title", "description\0suffix", TicketPriority.LOW));
    }

    @Test
    void rejectsTwoHundredOneSupplementaryUnicodeCodePointsBeforeRequesterLookupOrSave() {
        String title = "\uD83D\uDE80".repeat(201);

        assertInvalidWithoutSideEffects(new CreateTicketRequest(title, "description", TicketPriority.LOW));
    }

    @Test
    void rejectsTrimmedTextOverInclusiveLimitsBeforeRequesterLookupOrSave() {
        assertInvalidWithoutSideEffects(new CreateTicketRequest(
                " " + "t".repeat(201) + " ",
                "description",
                TicketPriority.LOW
        ));
        assertInvalidWithoutSideEffects(new CreateTicketRequest(
                "title",
                " " + "d".repeat(5001) + " ",
                TicketPriority.LOW
        ));
    }

    @Test
    void rejectsMissingPriorityWithoutSideEffects() {
        assertInvalidWithoutSideEffects(new CreateTicketRequest("title", "description", null));
    }

    @Test
    void rejectsAbsentAuthenticationWithoutRequesterLookupOrSave() {
        assertThatThrownBy(() -> ticketService.create(validRequest(), null, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason()).isEqualTo("Unauthorized");
                });

        verifyNoInteractions(userRepository, ticketRepository);
    }

    @Test
    void rejectsStalePrincipalWithoutSaving() {
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(validRequest(), authentication, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason()).isEqualTo("Unauthorized");
                });

        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidKeysBeforePersistence() {
        for (String key : new String[]{null, "", "bad key", "a".repeat(65), "é"}) {
            assertThatThrownBy(() -> ticketService.create(validRequest(), authentication, key))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(userRepository, ticketRepository);
    }

    @Test
    void replaysNormalizedPayloadAndRejectsConflictingReuse() {
        Ticket existing = new Ticket(requester, "Title", "Description", TicketPriority.MEDIUM, IDEMPOTENCY_KEY);
        existing.initializeCreation();
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        when(ticketRepository.findByRequester_IdAndIdempotencyKey(requester.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        assertThat(ticketService.create(new CreateTicketRequest("\u00A0Title\u00A0", "\u00A0Description\u00A0", TicketPriority.MEDIUM),
                authentication, IDEMPOTENCY_KEY).id()).isEqualTo(existing.getId());
        assertThatThrownBy(() -> ticketService.create(
                new CreateTicketRequest("Changed", "Description", TicketPriority.MEDIUM), authentication, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(ticketRepository, times(2)).lockIdempotencyKey(requester.getId(), IDEMPOTENCY_KEY);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyIsIndependentAcrossRequesters() {
        User other = new User("other@example.com", "password-hash", Role.USER);
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        when(userRepository.findByEmail(REQUESTER_EMAIL)).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));
        persistTicketOnSave();

        TicketResponse first = ticketService.create(validRequest(), authentication, IDEMPOTENCY_KEY);
        TicketResponse second = ticketService.create(validRequest(), UsernamePasswordAuthenticationToken.authenticated(
                "other@example.com", "credentials", List.of()), IDEMPOTENCY_KEY);
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    private void assertInvalidWithoutSideEffects(CreateTicketRequest request) {
        assertThatThrownBy(() -> ticketService.create(request, authentication, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(userRepository, ticketRepository);
    }

    private CreateTicketRequest validRequest() {
        return new CreateTicketRequest("Title", "Description", TicketPriority.MEDIUM);
    }

    private void persistTicketOnSave() {
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.initializeCreation();
            return ticket;
        });
    }
}
