package com.supportflow.api.ticket;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(CAST(:requesterId AS text) || ':' || :idempotencyKey, 0))) acquired", nativeQuery = true)
    int lockIdempotencyKey(@Param("requesterId") UUID requesterId,
                           @Param("idempotencyKey") String idempotencyKey);

    Optional<Ticket> findByRequester_IdAndIdempotencyKey(UUID requesterId, String idempotencyKey);
}
