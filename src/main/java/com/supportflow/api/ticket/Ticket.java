package com.supportflow.api.ticket;

import com.supportflow.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tickets", uniqueConstraints =
        @UniqueConstraint(name = "uk_tickets_requester_idempotency", columnNames = {"requester_id", "idempotency_key"}))
public class Ticket {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TicketPriority priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ticket() {
    }

    public Ticket(User requester, String title, String description, TicketPriority priority, String idempotencyKey) {
        this.requester = Objects.requireNonNull(requester);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.title = Objects.requireNonNull(title);
        this.description = Objects.requireNonNull(description);
        this.priority = Objects.requireNonNull(priority);
        this.status = TicketStatus.OPEN;
    }

    @PrePersist
    void initializeCreation() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
