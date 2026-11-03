package com.ledgersync.queue.repository;

import com.ledgersync.queue.model.OutboxMessageEntity;
import com.ledgersync.queue.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {

    List<OutboxMessageEntity> findByStatus(OutboxStatus status, Pageable pageable);
}
