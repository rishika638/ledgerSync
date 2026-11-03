package com.ledgersync.security;

import com.ledgersync.common.security.ApiKeyAuthenticationFilter;
import com.ledgersync.common.security.SecurityConfig;
import com.ledgersync.payment.controller.PaymentController;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.payment.repository.RefundRepository;
import com.ledgersync.reconciliation.controller.ReconciliationController;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import com.ledgersync.reconciliation.service.DiscrepancyResolutionService;
import com.ledgersync.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PaymentController.class, ReconciliationController.class})
@Import({SecurityConfig.class, ApiKeyAuthenticationFilter.class})
@TestPropertySource(properties = {
        "ledgersync.security.enabled=true",
        "ledgersync.security.header-name=X-API-Key",
        "ledgersync.security.api-keys.ingestion=test-ingest-key",
        "ledgersync.security.api-keys.operator=test-operator-key",
        "ledgersync.security.api-keys.admin=test-admin-key"
})
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentTransactionRepository paymentRepository;

    @MockBean
    private RefundRepository refundRepository;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private DiscrepancyResolutionService resolutionService;

    @MockBean
    private ReconciliationRunRepository runRepository;

    @MockBean
    private DiscrepancyRepository discrepancyRepository;

    @Test
    @DisplayName("Protected endpoint without API key returns 403 Forbidden")
    void testAccessWithoutApiKeyForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Operator API key grants access to read payments")
    void testOperatorKeyAllowedOnPayments() throws Exception {

        when(paymentRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/payments")
                        .header("X-API-Key", "test-operator-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Operator API key is forbidden from admin-only discrepancy resolution")
    void testOperatorForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliation/discrepancies/dsc_1/resolve")
                        .header("X-API-Key", "test-operator-key")
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin API key allowed on discrepancy resolution")
    void testAdminAllowedOnAdminEndpoint() throws Exception {

        DiscrepancyEntity entity = mock(DiscrepancyEntity.class);
        ReconciliationRunEntity run = mock(ReconciliationRunEntity.class);

        when(entity.getId()).thenReturn("dsc_1");
        when(entity.getRun()).thenReturn(run);
        when(run.getId()).thenReturn("run_1");

        when(resolutionService.resolve(
                eq("dsc_1"),
                eq(DiscrepancyStatus.RESOLVED),
                eq("test resolution"),
                anyString()
        )).thenReturn(entity);

        mockMvc.perform(post("/api/v1/reconciliation/discrepancies/dsc_1/resolve")
                        .header("X-API-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"test resolution\"}"))
                .andExpect(status().isOk());
    }
}
