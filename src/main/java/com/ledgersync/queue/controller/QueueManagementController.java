package com.ledgersync.queue.controller;

import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.queue.dto.InboxMessageResponse;
import com.ledgersync.queue.dto.RedriveResult;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import com.ledgersync.queue.service.DeadLetterRedriveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
@Tag(name = "Queue Management", description = "Monitor durable inbox/outbox status, inspect dead letters, and trigger safe redrive")
public class QueueManagementController {

    private final InboxMessageRepository inboxRepository;
    private final DeadLetterRedriveService redriveService;

    public QueueManagementController(
            InboxMessageRepository inboxRepository,
            DeadLetterRedriveService redriveService) {
        this.inboxRepository = inboxRepository;
        this.redriveService = redriveService;
    }

    @GetMapping("/inbox")
    @Operation(summary = "List Inbox Messages by Status with Pagination")
    public ResponseEntity<Page<InboxMessageResponse>> listInbox(
            @Parameter(description = "Filter by status (PENDING, PROCESSING, COMPLETED, DEAD_LETTER)")
            @RequestParam(name = "status", required = false) InboxStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<InboxMessageEntity> page = status != null
                ? inboxRepository.findByStatus(status, pageable)
                : inboxRepository.findAll(pageable);

        return ResponseEntity.ok(page.map(InboxMessageResponse::fromEntity));
    }

    @GetMapping("/inbox/{id}")
    @Operation(summary = "Get Inbox Message Details")
    public ResponseEntity<InboxMessageResponse> getInboxMessage(@PathVariable("id") String id) {
        InboxMessageEntity msg = inboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inbox message not found: " + id));
        return ResponseEntity.ok(InboxMessageResponse.fromEntity(msg));
    }

    @GetMapping("/dead-letters")
    @Operation(summary = "List Dead-Letter Queue Messages")
    public ResponseEntity<Page<InboxMessageResponse>> listDeadLetters(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InboxMessageEntity> page = inboxRepository.findByStatus(InboxStatus.DEAD_LETTER, pageable);
        return ResponseEntity.ok(page.map(InboxMessageResponse::fromEntity));
    }

    @PostMapping("/redrive/{id}")
    @Operation(summary = "Controlled Redrive of a Dead-Lettered Event")
    public ResponseEntity<RedriveResult> redriveSingle(
            @PathVariable("id") String id,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "system";
        RedriveResult result = redriveService.redriveSingle(id, operator);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/redrive/all")
    @Operation(summary = "Bulk Redrive of Dead-Lettered Events")
    public ResponseEntity<RedriveResult> redriveAll(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "system";
        RedriveResult result = redriveService.redriveAll(limit, operator);
        return ResponseEntity.ok(result);
    }
}
