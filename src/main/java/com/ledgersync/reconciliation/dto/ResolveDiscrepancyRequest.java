package com.ledgersync.reconciliation.dto;

import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResolveDiscrepancyRequest(
        @NotNull(message = "Target status must be provided")
        DiscrepancyStatus status,

        @NotBlank(message = "Resolution notes explaining manual investigation must be provided")
        String resolutionNotes
) {
}
