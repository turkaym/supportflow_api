package com.supportflow.api.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supportflow.api.auth.CustomUserDetailsService;
import com.supportflow.api.auth.JwtService;
import com.supportflow.api.user.Role;
import com.supportflow.api.user.User;
import com.supportflow.api.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "app.security.jwt.secret=test-secret-for-ticket-controller-1234567890",
        "app.security.jwt.expiration=3600000"
})
@AutoConfigureMockMvc
class TicketControllerTest {

    private static final String NO_BREAK_SPACE = "\u00A0";
    private static final String IDEMPOTENCY_KEY = "ticket-retry_1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private TicketRepository ticketRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void authenticatedCreationReturns201AndOnlySafeNormalizedFields() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-23T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-07-23T12:00:01Z");
        when(ticketService.create(any(), any(), any())).thenReturn(new TicketResponse(
                id,
                "Printer failure",
                "Cannot print invoices.",
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                createdAt,
                updatedAt
        ));

        String body = mockMvc.perform(post("/api/tickets")
                        .with(user("person@example.com").authorities(() -> "ROLE_USER"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Printer failure  ",
                                  "description": "  Cannot print invoices.  ",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Printer failure"))
                .andExpect(jsonPath("$.description").value("Cannot print invoices."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt.toString()))
                .andExpect(jsonPath("$.requester").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("person@example.com", "credentials", "authentication");
        verify(ticketService).create(
                argThat(request -> request.title().equals("  Printer failure  ")
                        && request.description().equals("  Cannot print invoices.  ")
                        && request.priority() == TicketPriority.HIGH),
                argThat(authentication -> authentication.getName().equals("person@example.com")),
                argThat(IDEMPOTENCY_KEY::equals)
        );
    }

    @Test
    void authenticatedNoBreakSpaceIsNormalizedIn201Response() throws Exception {
        TicketService realService = new TicketService(ticketRepository, userRepository);
        when(userRepository.findByEmail("person@example.com"))
                .thenReturn(Optional.of(new User("person@example.com", "password-hash", Role.USER)));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.initializeCreation();
            return ticket;
        });
        when(ticketService.create(any(), any(), any())).thenAnswer(invocation ->
                realService.create(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        mockMvc.perform(post("/api/tickets")
                        .with(user("person@example.com").authorities(() -> "ROLE_USER"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%sPrinter failure%s",
                                  "description": "%sCannot print invoices.%s",
                                  "priority": "HIGH"
                                }
                                """.formatted(NO_BREAK_SPACE, NO_BREAK_SPACE, NO_BREAK_SPACE, NO_BREAK_SPACE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Printer failure"))
                .andExpect(jsonPath("$.description").value("Cannot print invoices."));

        verify(ticketService).create(
                argThat(request -> request.title().equals(NO_BREAK_SPACE + "Printer failure" + NO_BREAK_SPACE)
                        && request.description().equals(NO_BREAK_SPACE + "Cannot print invoices." + NO_BREAK_SPACE)),
                any(Authentication.class),
                argThat(IDEMPOTENCY_KEY::equals)
        );
    }

    @Test
    void ignoresClientControlledFields() throws Exception {
        UUID serverId = UUID.randomUUID();
        when(ticketService.create(any(), any(), any())).thenReturn(new TicketResponse(
                serverId,
                "Title",
                "Description",
                TicketStatus.OPEN,
                TicketPriority.LOW,
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z")
        ));

        mockMvc.perform(post("/api/tickets")
                        .with(user("person@example.com"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "description": "Description",
                                  "priority": "LOW",
                                  "id": "00000000-0000-0000-0000-000000000000",
                                  "requester": "another@example.com",
                                  "status": "CLOSED",
                                  "createdAt": "2000-01-01T00:00:00Z",
                                  "updatedAt": "2000-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(serverId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requester").doesNotExist());
    }

    @Test
    void rejectsNullAndBlankRequiredInputBeforeCallingService() throws Exception {
        for (String content : Set.of(
                "{\"description\":\"Description\",\"priority\":\"LOW\"}",
                "{\"title\":\"   \",\"description\":\"Description\",\"priority\":\"LOW\"}",
                "{\"title\":\"Title\",\"description\":\"   \",\"priority\":\"LOW\"}",
                "{\"title\":\"Title\",\"description\":\"Description\"}"
        )) {
            mockMvc.perform(post("/api/tickets")
                            .with(user("person@example.com"))
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(ticketService);
    }

    @Test
    void noBreakSpaceOnlyInputReturns400WithoutRequesterLookupOrSave() throws Exception {
        TicketService realService = new TicketService(ticketRepository, userRepository);
        when(ticketService.create(any(), any(), any())).thenAnswer(invocation ->
                realService.create(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        for (String content : Set.of(
                "{\"title\":\"%s\",\"description\":\"Description\",\"priority\":\"LOW\"}".formatted(NO_BREAK_SPACE),
                "{\"title\":\"Title\",\"description\":\"%s\",\"priority\":\"LOW\"}".formatted(NO_BREAK_SPACE)
        )) {
            mockMvc.perform(post("/api/tickets")
                            .with(user("person@example.com"))
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(userRepository, ticketRepository);
    }

    @Test
    void nullCharacterInputReturns400WithoutRequesterOrRepositoryInteractions() throws Exception {
        TicketService realService = new TicketService(ticketRepository, userRepository);
        when(ticketService.create(any(), any(), any())).thenAnswer(invocation ->
                realService.create(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        for (String content : Set.of(
                "{\"title\":\"Title\\u0000suffix\",\"description\":\"Description\",\"priority\":\"LOW\"}",
                "{\"title\":\"Title\",\"description\":\"Description\\u0000suffix\",\"priority\":\"LOW\"}"
        )) {
            mockMvc.perform(post("/api/tickets")
                            .with(user("person@example.com"))
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(userRepository, ticketRepository);
    }

    @Test
    void rejectsUnknownPriorityBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .with(user("person@example.com"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Description","priority":"URGENT"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void unauthenticatedCreationReturnsGeneric401WithoutCallingService() throws Exception {
        String responseBody = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Description","priority":"LOW"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody.toLowerCase()).doesNotContain("principal", "account", "person@example.com");
        verifyNoInteractions(ticketService);
    }

    @Test
    void stalePrincipalReturnsGeneric401WithoutIdentityLeakage() throws Exception {
        when(ticketService.create(any(), any(Authentication.class), any())).thenThrow(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Account does not exist for principal person@example.com"
        ));

        String responseBody = mockMvc.perform(post("/api/tickets")
                        .with(user("person@example.com"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","description":"Description","priority":"LOW"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody.toLowerCase()).doesNotContain(
                "person@example.com", "account", "principal", "password", "token", "credential"
        );
    }

    @Test
    void missingOrMalformedIdempotencyKeyReturns400BeforePersistence() throws Exception {
        TicketService realService = new TicketService(ticketRepository, userRepository);
        when(ticketService.create(any(), any(), any())).thenAnswer(invocation ->
                realService.create(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        for (String key : new String[]{null, "bad key"}) {
            var request = post("/api/tickets").with(user("person@example.com"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Title\",\"description\":\"Description\",\"priority\":\"LOW\"}");
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(userRepository, ticketRepository);
    }

    @Test
    void ownerRetrievalReturnsOnlySevenSafeFieldsAndIgnoresIdempotencyHeader() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-23T12:00:00Z");
        TicketResponse response = new TicketResponse(id, "Title", "Description", TicketStatus.OPEN,
                TicketPriority.HIGH, createdAt, createdAt);
        when(ticketService.getById(any(), any())).thenReturn(response);

        String withoutHeader = mockMvc.perform(get("/api/tickets/{id}", id)
                        .with(user("person@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(7)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Title"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()))
                .andReturn().getResponse().getContentAsString();
        String withHeader = mockMvc.perform(get("/api/tickets/{id}", id)
                        .with(user("person@example.com"))
                        .header("Idempotency-Key", "ignored-on-retrieval"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(withHeader).isEqualTo(withoutHeader);
        verify(ticketService, times(2)).getById(
                argThat(id::equals), argThat(auth -> auth.getName().equals("person@example.com")));
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void absentAndForeignTicketsReturnIdenticalGeneric404() throws Exception {
        UUID absentId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();
        when(ticketService.getById(any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        String absent = mockMvc.perform(get("/api/tickets/{id}", absentId).with(user("person@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"))
                .andReturn().getResponse().getContentAsString();
        String foreign = mockMvc.perform(get("/api/tickets/{id}", foreignId).with(user("person@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"))
                .andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(absent);
    }

    @Test
    void unauthenticatedRetrievalReturnsGeneric401WithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void malformedUuidReturnsSafe400WithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets/not-a-uuid").with(user("person@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/tickets/not-a-uuid"));

        verifyNoInteractions(ticketService);
    }
}
