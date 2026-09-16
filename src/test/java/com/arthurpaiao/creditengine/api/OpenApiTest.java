package com.arthurpaiao.creditengine.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class OpenApiTest {
    @Test
    void documentParsesAndAllLocalSchemaReferencesResolve() throws Exception {
        try (var stream = getClass().getResourceAsStream("/static/openapi.json")) {
            assertNotNull(stream);
            var document = JsonMapper.builder().build().readTree(stream);
            assertEquals("3.1.0", document.path("openapi").asString());
            assertEquals(10, document.path("paths").size());
            assertTrue(document.path("paths").path("/receivables/{id}").path("put").path("responses").has("409"));
            assertTrue(document.path("paths").path("/settlements").path("post").path("responses").has("409"));
            var statement = document.path("paths").path("/settlements").path("get");
            assertEquals(6, statement.path("parameters").size());
            assertEquals("#/components/schemas/SettlementPage", statement.at("/responses/200/content/application~1json/schema/$ref").asString());
            assertEquals("string", document.at("/components/schemas/Simulate/properties/faceValue/type").asString());
            checkReferences(document, document);
        }
    }

    private void checkReferences(JsonNode node, JsonNode document) {
        if (node.has("$ref")) {
            var reference = node.path("$ref").asString();
            assertTrue(reference.startsWith("#/"));
            assertFalse(document.at(reference.substring(1)).isMissingNode(), reference);
        }
        for (var child : node) checkReferences(child, document);
    }
}
