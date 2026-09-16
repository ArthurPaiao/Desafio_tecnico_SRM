package com.arthurpaiao.creditengine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.test.web.servlet.ResultActions;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class CreditEngineApplicationIT {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;
    private static final String EDIT = "{\"faceValue\":\"250.00\",\"type\":\"CHEQUE_PRE_DATADO\",\"dueDate\":\"2026-12-15\",\"paymentCurrency\":\"BRL\"}";
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        Clock testClock() { return Clock.fixed(Instant.parse("2026-09-14T15:00:00Z"), ZoneOffset.UTC); }
    }

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        POSTGRES.start();
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        // This datasource belongs exclusively to the disposable Testcontainers database.
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM receivables");
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("UPDATE pricing_config SET base_rate = 0.01, exchange_validity_hours = 24 WHERE id = 1");
    }

    @Test
    void contextLoadsAndSeedIsExposed() throws Exception {
        mvc.perform(get("/assignors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].code").value("CED-001"));
        mvc.perform(get("/openapi.json")).andExpect(status().isOk());
    }

    @Test
    void editInvalidatesConditionsAndCannotChangeSettledTitle() throws Exception {
        var title = createTitle("BRL");
        var oldRequest = settlementRequest(title);
        mvc.perform(put("/receivables/" + title).contentType("application/json").content(EDIT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.faceValue").value("250.00"))
                .andExpect(jsonPath("$.type").value("CHEQUE_PRE_DATADO"));
        settle(UUID.randomUUID(), oldRequest).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONDITIONS_CHANGED"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
        settle(UUID.randomUUID(), settlementRequest(title)).andExpect(status().isCreated());
        mvc.perform(put("/receivables/" + title).contentType("application/json").content(EDIT.replace("250.00", "999.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SETTLED"));
        mvc.perform(get("/receivables/" + title)).andExpect(jsonPath("$.faceValue").value("250.00"))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void editAndSettlementBothRespectTheSameRowLock() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        try (var connection = context.getBean(javax.sql.DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM receivables WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, title); statement.executeQuery().close();
                mvc.perform(put("/receivables/" + title).contentType("application/json").content(EDIT))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"));
                settle(UUID.randomUUID(), request).andExpect(status().isConflict());
            } finally { connection.rollback(); }
        }
        mvc.perform(put("/receivables/" + title).contentType("application/json").content(EDIT)).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void csvPreviewDoesNotPersistAndConfirmationRevalidatesDuplicates() throws Exception {
        String csv = "cedente_codigo;titulo_codigo;tipo;valor_face;vencimento;moeda_pagamento\n"
                + "CED-001;CSV-IT;DUPLICATA_MERCANTIL;1000,00;15/12/2026;USD\n"
                + "UNKNOWN;INVALID;CHEQUE_PRE_DATADO;1,00;15/12/2026;BRL\n";
        var file = new org.springframework.mock.web.MockMultipartFile("file", "titles.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = mvc.perform(multipart("/receivables/import-preview").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].errors").isEmpty())
                .andExpect(jsonPath("$.rows[1].errors[0]").value("cedente_codigo: código desconhecido"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM receivables", Integer.class));
        String body = json.readTree(result).at("/rows/0/request").toString();
        mvc.perform(post("/receivables").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/receivables").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(multipart("/receivables/import-preview").file(file)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].errors[0]").value("Duplicado: título já cadastrado"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM receivables", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void duplicateRegistrationRollsBackAndPreservesOriginal() throws Exception {
        var body = """
                {"assignorId":"aebc49b1-1c4a-4f86-bf4e-f0224a9aa007","titleCode":"IT-001",
                 "type":"DUPLICATA_MERCANTIL","faceValue":"123.45","dueDate":"2026-12-14","paymentCurrency":"USD"}
                """;
        mvc.perform(post("/receivables").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/receivables").contentType("application/json").content(body.replace("123.45", "999.00")))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM receivables", Integer.class));
        assertEquals("123.45", jdbc.queryForObject("SELECT face_value FROM receivables", java.math.BigDecimal.class).toPlainString());
        mvc.perform(get("/receivables?status=PENDING&size=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].faceValue").value("123.45"));
    }

    @Test
    void selectsLatestStartedRateAndIgnoresFutureWithoutPersistingSimulation() throws Exception {
        insertRate("4.00", "2026-09-14T10:00:00Z");
        var selected = insertRate("5.00", "2026-09-14T14:00:00Z");
        insertRate("9.00", "2026-09-14T16:00:00Z");
        mvc.perform(post("/simulations").contentType("application/json").content(simulation("USD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalAmount").value("18571.99"))
                .andExpect(jsonPath("$.exchangeRateId").value(selected.toString()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM receivables", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void expirationBoundaryBlocksUsdButNotBrl() throws Exception {
        insertRate("5.00", "2026-09-13T15:00:00Z");
        insertRate("6.00", "2026-09-14T16:00:00Z");
        mvc.perform(post("/simulations").contentType("application/json").content(simulation("USD")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("EXCHANGE_RATE_EXPIRED"));
        mvc.perform(post("/simulations").contentType("application/json").content(simulation("BRL")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalAmount").value("92859.94"));
    }

    @Test
    void duplicateExchangeInstantWithDifferentOffsetReturns409() throws Exception {
        var body = "{\"pair\":\"USD/BRL\",\"rate\":\"5.1234567890\",\"validFrom\":\"2026-09-14T12:00:00-03:00\"}";
        var result = mvc.perform(post("/exchange-rates").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(get(result.getResponse().getHeader("Location"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("5.1234567890"));
        mvc.perform(post("/exchange-rates").contentType("application/json")
                .content(body.replace("12:00:00-03:00", "15:00:00Z")))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class));
    }

    @Test
    void settlementPersistsCompleteSnapshotAndReplaySurvivesChangedRates() throws Exception {
        insertRate("5.00", "2026-09-14T13:00:00Z");
        var title = createTitle("USD");
        var request = settlementRequest(title);
        var key = UUID.randomUUID();
        var first = settle(key, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculation.finalAmount").value("18571.99"))
                .andExpect(jsonPath("$.calculation.discountBrl").value("7140.06"))
                .andExpect(jsonPath("$.assignorName").value("Empresa Demonstração A"))
                .andExpect(jsonPath("$.settledAt").value("2026-09-14T15:00:00Z")).andReturn();
        assertEquals("SETTLED", jdbc.queryForObject("SELECT status FROM receivables WHERE id = ?", String.class, title));
        assertEquals("0.0250000000", jdbc.queryForObject("SELECT effective_rate FROM settlements", java.math.BigDecimal.class).toPlainString());
        jdbc.update("UPDATE pricing_config SET base_rate = 0.02, exchange_validity_hours = 1 WHERE id = 1");
        settle(key, request).andExpect(status().isOk()).andExpect(content().json(first.getResponse().getContentAsString()));
        mvc.perform(get(first.getResponse().getHeader("Location"))).andExpect(status().isOk())
                .andExpect(content().json(first.getResponse().getContentAsString()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void changedConditionsRequireFreshConfirmationWithNoPartialState() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        jdbc.update("UPDATE pricing_config SET base_rate = 0.02 WHERE id = 1");
        var changed = settle(UUID.randomUUID(), request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITIONS_CHANGED"))
                .andExpect(jsonPath("$.current.expectedConditions.effectiveRate").value("0.0350000000")).andReturn();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM receivables WHERE id = ?", String.class, title));
        var confirmed = json.createObjectNode().put("receivableId", title.toString());
        confirmed.set("expectedConditions", json.readTree(changed.getResponse().getContentAsString()).at("/current/expectedConditions"));
        settle(UUID.randomUUID(), confirmed.toString()).andExpect(status().isCreated());
    }

    @Test
    void sameValueNewQuotationDoesNotRequireConfirmationButAuditsNewId() throws Exception {
        insertRate("5", "2026-09-14T13:00:00Z");
        var title = createTitle("USD");
        var request = settlementRequest(title);
        var latest = insertRate("5", "2026-09-14T14:00:00Z");
        settle(UUID.randomUUID(), request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculation.exchangeRateId").value(latest.toString()));
    }

    @Test
    void reusedKeyDifferentPayloadOrAnotherKeyForSettledTitleConflicts() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        var key = UUID.randomUUID();
        settle(key, request).andExpect(status().isCreated());
        settle(key, request.replace("92859.94", "90000.00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        settle(UUID.randomUUID(), request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SETTLED"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void failureAfterSettlementInsertRollsBackEventKeyAndTitle() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        var key = UUID.randomUUID();
        // Fault injection only in this disposable database, at the second write of the transaction.
        jdbc.execute("""
                CREATE FUNCTION reject_test_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Injected update failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_test_settlement BEFORE UPDATE ON receivables FOR EACH ROW EXECUTE FUNCTION reject_test_settlement()");
        try {
            settle(key, request).andExpect(status().isInternalServerError());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM receivables WHERE id = ?", String.class, title));
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_settlement ON receivables");
            jdbc.execute("DROP FUNCTION reject_test_settlement()");
        }
        settle(key, request).andExpect(status().isCreated());
    }

    @Test
    void heldIntentLockReturns409AndSameIntentCanBeRetriedAfterRelease() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        var key = UUID.randomUUID();
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, key.getMostSignificantBits() ^ key.getLeastSignificantBits());
                statement.execute();
                settle(key, request).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"));
            } finally { connection.rollback(); }
        }
        settle(key, request).andExpect(status().isCreated());
    }

    @Test
    void heldTitleLockRejectsDifferentIntentWithoutWaitingIndefinitely() throws Exception {
        var title = createTitle("BRL");
        var request = settlementRequest(title);
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM receivables WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, title);
                statement.execute();
                settle(UUID.randomUUID(), request).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("OPERATION_IN_PROGRESS"));
            } finally { connection.rollback(); }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    @Test
    void twoConcurrentCallsWithSameKeyCreateExactlyOneSettlement() throws Exception {
        var request = settlementRequest(createTitle("BRL"));
        var key = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return settle(key, request).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            var statuses = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertTrue(statuses.stream().allMatch(status -> status == 201 || status == 200 || status == 409));
        }
        settle(key, request).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    private UUID createTitle(String currency) throws Exception {
        return createTitle(currency, "aebc49b1-1c4a-4f86-bf4e-f0224a9aa007");
    }

    private UUID createTitle(String currency, String assignor) throws Exception {
        var request = json.readTree(simulation(currency)).deepCopy().asObject();
        request.put("assignorId", assignor);
        request.put("titleCode", UUID.randomUUID().toString());
        var result = mvc.perform(post("/receivables").contentType("application/json").content(request.toString()))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asString());
    }

    @Test
    void expiredQuotationPreventsFirstSettlementWithoutPartialWrites() throws Exception {
        insertRate("5", "2026-09-14T13:00:00Z");
        var title = createTitle("USD");
        var request = settlementRequest(title);
        jdbc.update("UPDATE pricing_config SET exchange_validity_hours = 1 WHERE id = 1");
        settle(UUID.randomUUID(), request).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("EXCHANGE_RATE_EXPIRED"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM receivables WHERE id = ?", String.class, title));
    }

    @Test
    void differentKeysRacingOnSameTitleCannotCreateTwoSettlements() throws Exception {
        var request = settlementRequest(createTitle("BRL"));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return settle(UUID.randomUUID(), request).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            var statuses = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settlements", Integer.class));
    }

    private String settlementRequest(UUID title) throws Exception {
        var result = mvc.perform(post("/receivables/" + title + "/simulations")).andExpect(status().isOk()).andReturn();
        JsonNode simulation = json.readTree(result.getResponse().getContentAsString());
        var request = json.createObjectNode().put("receivableId", title.toString());
        request.set("expectedConditions", simulation.path("expectedConditions"));
        return request.toString();
    }

    @Test
    void statementCombinesFiltersAndHonorsInclusiveStartExclusiveEnd() throws Exception {
        insertRate("5", "2026-09-14T14:00:00Z");
        var assignor = "aebc49b1-1c4a-4f86-bf4e-f0224a9aa008";
        settle(UUID.randomUUID(), settlementRequest(createTitle("BRL"))).andExpect(status().isCreated());
        var usd = settle(UUID.randomUUID(), settlementRequest(createTitle("USD", assignor)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/settlements").param("currency", "USD").param("assignorId", assignor)
                        .param("from", "2026-09-14T12:00:00-03:00").param("to", "2026-09-14T15:00:00.000001Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(json.readTree(usd).path("id").asString()));
        mvc.perform(get("/settlements").param("from", "2026-09-14T15:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/settlements").param("to", "2026-09-14T15:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/settlements").param("currency", "BRL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/settlements").param("assignorId", assignor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/settlements").param("assignorId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void statementPagesUseIdToBreakTimestampTies() throws Exception {
        for (int i = 0; i < 3; i++) {
            settle(UUID.randomUUID(), settlementRequest(createTitle("BRL"))).andExpect(status().isCreated());
        }
        var ids = jdbc.queryForList("SELECT id::text FROM settlements ORDER BY settled_at DESC, id DESC", String.class);
        for (int page = 0; page < 3; page++) {
            mvc.perform(get("/settlements").param("page", String.valueOf(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(ids.get(page)))
                    .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
        }
        mvc.perform(get("/settlements").param("page", "3").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void statementKeepsHistoricalSnapshotAfterLiveDataChanges() throws Exception {
        insertRate("5", "2026-09-14T13:00:00Z");
        var response = settle(UUID.randomUUID(), settlementRequest(createTitle("USD")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE pricing_config SET base_rate = 0.02, exchange_validity_hours = 1 WHERE id = 1");
        var assignor = UUID.fromString("aebc49b1-1c4a-4f86-bf4e-f0224a9aa007");
        var original = jdbc.queryForObject("SELECT name FROM assignors WHERE id = ?", String.class, assignor);
        try {
            jdbc.update("UPDATE assignors SET name = 'Nome alterado' WHERE id = ?", assignor);
            var statement = mvc.perform(get("/settlements")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(json.readTree(response), json.readTree(statement).path("content").get(0));
        } finally {
            jdbc.update("UPDATE assignors SET name = ? WHERE id = ?", original, assignor);
        }
    }

    private ResultActions settle(UUID key, String request) throws Exception {
        return mvc.perform(post("/settlements").header("Idempotency-Key", key).contentType("application/json").content(request));
    }

    private UUID insertRate(String value, String validFrom) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO exchange_rates VALUES (?, 'USD/BRL', CAST(? AS numeric), CAST(? AS timestamptz), CURRENT_TIMESTAMP)",
                id, value, validFrom);
        return id;
    }

    private String simulation(String currency) {
        return """
                {"faceValue":"100000.00","type":"DUPLICATA_MERCANTIL","dueDate":"2026-12-14","paymentCurrency":"%s"}
                """.formatted(currency);
    }

}
