package com.example.baas.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

final class JsonTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    static String read(MvcResult result, String fieldName) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return node.get(fieldName).asText();
    }
}
