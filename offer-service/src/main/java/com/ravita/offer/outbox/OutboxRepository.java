package com.ravita.offer.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Próximas linhas pendentes, em ordem de criação, já travadas para esta transação.
     * SKIP LOCKED pula as linhas que outra réplica do offer-service está publicando,
     * então duas réplicas nunca publicam a mesma linha ao mesmo tempo.
     */
    @Query(value = """
            SELECT * FROM outbox_events
             WHERE status = 'PENDING'
             ORDER BY created_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextPending(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateId(String aggregateId);
}
