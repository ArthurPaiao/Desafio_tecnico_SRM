package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CreditControllerTest {
    private final AssignorRepository assignors = mock(AssignorRepository.class);
    private final PricingConfigRepository configs = mock(PricingConfigRepository.class);
    private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
    private final ReceivableRepository receivables = mock(ReceivableRepository.class);
    private MockMvc mvc;
    private static final String SIMULATION = """
            {"faceValue":"100000.00","type":"DUPLICATA_MERCANTIL","dueDate":"2026-12-14","paymentCurrency":"BRL"}
            """;
    private static final String RECEIVABLE = """
            {"assignorId":"aebc49b1-1c4a-4f86-bf4e-f0224a9aa007","titleCode":"T-001",
             "faceValue":"100000.00","type":"DUPLICATA_MERCANTIL","dueDate":"2026-12-14","paymentCurrency":"BRL"}
            """;

    @BeforeEach
    void setup() {
        var config = mock(PricingConfig.class);
        when(configs.findById(1)).thenReturn(Optional.of(config));
        when(config.getBaseRate()).thenReturn(new BigDecimal("0.01"));
        when(config.getMaxTermMonths()).thenReturn(120);
        when(config.getExchangeValidityHours()).thenReturn(24);
        var service = new CreditService(assignors, configs, rates, receivables,
                Clock.fixed(Instant.parse("2026-09-14T15:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new CreditController(service)).setControllerAdvice(new ApiErrors()).build();
    }

    @Test
    void simulationReturnsExactDecimalStringsAndNoWrites() throws Exception {
        mvc.perform(post("/simulations").contentType("application/json").content(SIMULATION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalAmount").value("92859.94"))
                .andExpect(jsonPath("$.finalAmount").isString()).andExpect(jsonPath("$.termMonths").value(3))
                .andExpect(jsonPath("$.referenceDate").value("2026-09-14"));
        verifyNoInteractions(receivables, rates);
    }

    @Test
    void editRejectsInvalidPayloadBeforeAccessingDatabase() throws Exception {
        for (String body : new String[]{"{}", "{\"faceValue\":100,\"type\":\"CHEQUE_PRE_DATADO\",\"dueDate\":\"2026-12-14\",\"paymentCurrency\":\"BRL\"}",
                "{\"faceValue\":\"1e2\",\"type\":\"CHEQUE_PRE_DATADO\",\"dueDate\":\"2026-12-14\",\"paymentCurrency\":\"BRL\"}"}) {
            mvc.perform(put("/receivables/" + java.util.UUID.randomUUID()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(receivables);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.001", "NaN", "1e5", "100000000000000000.00", ""})
    void rejectsInvalidDecimalInput(String value) throws Exception {
        mvc.perform(post("/simulations").contentType("application/json").content(SIMULATION.replace("100000.00", value)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    void numericJsonIsRejectedInsteadOfCoercedToMoneyText() throws Exception {
        mvc.perform(post("/simulations").contentType("application/json")
                .content(SIMULATION.replace("\"100000.00\"", "100000.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAndMalformedFieldsAreSafe400Errors() throws Exception {
        mvc.perform(post("/simulations").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        for (var body : new String[] {"{", SIMULATION.replace("2026-12-14", "2026-02-30"),
                SIMULATION.replace("BRL", "EUR"), SIMULATION.replace("2026-12-14", "2026-09-13")}) {
            mvc.perform(post("/simulations").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    @Test
    void registrationReturns201AndRetrievableLocation() throws Exception {
        when(assignors.existsById(any())).thenReturn(true);
        when(receivables.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = mvc.perform(post("/receivables").contentType("application/json").content(RECEIVABLE))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        var captor = org.mockito.ArgumentCaptor.forClass(Receivable.class);
        verify(receivables).saveAndFlush(captor.capture());
        when(receivables.findById(captor.getValue().getId())).thenReturn(Optional.of(captor.getValue()));
        mvc.perform(get(response.getResponse().getHeader("Location")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titleCode").value("T-001"));
    }

    @Test
    void duplicateReturns409WithoutDatabaseDetails() throws Exception {
        when(assignors.existsById(any())).thenReturn(true);
        when(receivables.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret SQL",
                new SQLException("secret constraint", "23505")));
        mvc.perform(post("/receivables").contentType("application/json").content(RECEIVABLE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void missingAssignorReturns404() throws Exception {
        mvc.perform(post("/receivables").contentType("application/json").content(RECEIVABLE))
                .andExpect(status().isNotFound());
        verifyNoInteractions(receivables);
    }

    @Test
    void missingAndExpiredRateReturn422() throws Exception {
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        mvc.perform(post("/simulations").contentType("application/json").content(SIMULATION.replace("BRL", "USD")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("EXCHANGE_RATE_UNAVAILABLE"));
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc(anyString(), any()))
                .thenReturn(Optional.of(new ExchangeRate(java.util.UUID.randomUUID(), "USD/BRL", BigDecimal.ONE,
                        Instant.parse("2026-09-13T15:00:00Z"), Instant.parse("2026-09-13T15:00:00Z"))));
        mvc.perform(post("/simulations").contentType("application/json").content(SIMULATION.replace("BRL", "USD")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("EXCHANGE_RATE_EXPIRED"));
    }

    @Test
    void exchangeRegistrationRequiresSupportedPairPositiveRateAndOffset() throws Exception {
        for (var body : new String[] {
                "{\"pair\":\"EUR/BRL\",\"rate\":\"5\",\"validFrom\":\"2026-09-14T15:00:00Z\"}",
                "{\"pair\":\"USD/BRL\",\"rate\":\"0\",\"validFrom\":\"2026-09-14T15:00:00Z\"}",
                "{\"pair\":\"USD/BRL\",\"rate\":\"5\",\"validFrom\":\"2026-09-14T15:00:00\"}"}) {
            mvc.perform(post("/exchange-rates").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(rates);
    }

    @Test
    void badParametersAndHttpMethodsKeepCorrectStatuses() throws Exception {
        mvc.perform(get("/receivables?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/receivables?status=UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/receivables/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/simulations")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/simulations").contentType("text/plain").content(SIMULATION))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void technicalErrorDoesNotLeakInternalMessage() throws Exception {
        when(configs.findById(1)).thenThrow(new IllegalStateException("secret database address"));
        mvc.perform(post("/simulations").contentType("application/json").content(SIMULATION))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
