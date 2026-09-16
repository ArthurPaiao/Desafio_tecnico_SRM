package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementControllerTest {
    private final SettlementService service = mock(SettlementService.class);
    private MockMvc mvc;
    private final String key = UUID.randomUUID().toString();
    private final String body = """
            {"receivableId":"aebc49b1-1c4a-4f86-bf4e-f0224a9aa009","expectedConditions":{
            "assignorId":"aebc49b1-1c4a-4f86-bf4e-f0224a9aa007","titleCode":"T-001",
            "type":"DUPLICATA_MERCANTIL","dueDate":"2026-12-14","termMonths":3,"paymentCurrency":"BRL",
            "faceValueBrl":"100000.00","effectiveRate":"0.025","presentValueBrl":"92859.94",
            "discountBrl":"7140.06","finalAmount":"92859.94","exchangeRateUsed":null}}
            """;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SettlementController(service)).setControllerAdvice(new ApiErrors()).build();
    }

    @Test
    void missingOrMalformedKeyAndExpectedConditionsReturn400() throws Exception {
        mvc.perform(post("/settlements").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/settlements").header("Idempotency-Key", "bad").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json")
                .content(body.replace("\"92859.94\"", "92859.94"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void firstRequestReturns201AndReplay200WithSameLocationAndBody() throws Exception {
        var view = new SettlementView(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(key), UUID.randomUUID(),
                "Cedente", "T-001", java.time.Instant.parse("2026-09-14T15:00:00Z"), null);
        when(service.settle(any(), any())).thenReturn(new SettlementService.Outcome(view, false), new SettlementService.Outcome(view, true));
        var first = mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/settlements/" + view.id())).andReturn();
        mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().json(first.getResponse().getContentAsString()));
    }

    @Test
    void lockContentionIsSafe409AndDoesNotExposeSql() throws Exception {
        when(service.settle(any(), any())).thenThrow(new CannotAcquireLockException("secret sql"));
        mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void businessConflictsReturn409() throws Exception {
        for (var code : new BusinessException.Code[] {BusinessException.Code.IDEMPOTENCY_CONFLICT,
                BusinessException.Code.ALREADY_SETTLED, BusinessException.Code.OPERATION_IN_PROGRESS}) {
            doThrow(new BusinessException(code, "Conflito")).when(service).settle(any(), any());
            mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content(body))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code.name()));
        }
    }
}
