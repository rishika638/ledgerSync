package com.ledgersync.queue.dto;

import java.util.List;

public record RedriveResult(
        int redrivenCount,
        List<String> redrivenMessageIds,
        String message
) {
}
