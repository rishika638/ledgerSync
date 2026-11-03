package com.ledgersync.queue.repository;

import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessageEntity, String> {

    @Query(value = """
            SELECT * FROM inbox_messages
            WHERE status = 'PENDING' AND scheduled_at <= :now
            ORDER BY scheduled_at ASC, created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InboxMessageEntity> claimPendingBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Query("""
            SELECT m FROM InboxMessageEntity m
            WHERE m.status = 'PROCESSING' AND m.lockedAt <= :cutoff
            """)
    List<InboxMessageEntity> findAbandonedJobs(@Param("cutoff") Instant cutoff);

    Page<InboxMessageEntity> findByStatus(InboxStatus status, Pageable pageable);

    long countByStatus(InboxStatus status);
}
