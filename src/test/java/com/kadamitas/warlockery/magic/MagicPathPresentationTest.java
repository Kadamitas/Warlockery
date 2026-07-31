package com.kadamitas.warlockery.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MagicPathPresentationTest {
    private static final Path ENGLISH = Path.of(
        "src/main/resources/assets/warlockery/lang/en_us.json"
    );

    @Test
    void everyArcaneFocusDiagnosticResolvesToPlayerFacingProse() throws IOException {
        final JsonObject translations = JsonParser.parseString(Files.readString(ENGLISH)).getAsJsonObject();
        for (MagicPathRules.Diagnostic diagnostic : MagicPathRules.Diagnostic.values()) {
            final String key = diagnostic.messageKey();
            assertTrue(translations.has(key), key);
            final String prose = translations.get(key).getAsString();
            assertFalse(prose.isBlank(), key);
            assertFalse(prose.equals(key), key);
        }
        assertTrue(translations.get(MagicPathRules.Diagnostic.NOT_ATTUNED.messageKey())
            .getAsString().contains("Arcane Focus"));
    }
}
