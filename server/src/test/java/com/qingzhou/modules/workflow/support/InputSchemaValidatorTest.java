package com.qingzhou.modules.workflow.support;

import com.qingzhou.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSchemaValidatorTest {

    @Test
    void emptySchemaSkips() {
        assertDoesNotThrow(() -> InputSchemaValidator.validate(null, Map.of()));
        assertDoesNotThrow(() -> InputSchemaValidator.validate("{}", Map.of()));
    }

    @Test
    void requiredFieldMustPresent() {
        String schema = "{\"type\":\"object\",\"required\":[\"ping\"],\"properties\":{\"ping\":{\"type\":\"string\"}}}";
        BizException ex = assertThrows(BizException.class, () -> InputSchemaValidator.validate(schema, Map.of()));
        assertTrue(ex.getMessage().contains("ping"));
        assertDoesNotThrow(() -> InputSchemaValidator.validate(schema, Map.of("ping", "ok")));
    }

    @Test
    void integerTypeChecked() {
        String schema = "{\"type\":\"object\",\"required\":[\"n\"],\"properties\":{\"n\":{\"type\":\"integer\"}}}";
        assertThrows(BizException.class, () -> InputSchemaValidator.validate(schema, Map.of("n", "abc")));
        assertDoesNotThrow(() -> InputSchemaValidator.validate(schema, Map.of("n", 3)));
        assertDoesNotThrow(() -> InputSchemaValidator.validate(schema, Map.of("n", "7")));
    }
}
