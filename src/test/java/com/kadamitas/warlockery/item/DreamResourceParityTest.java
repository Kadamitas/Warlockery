package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class DreamResourceParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void icyNeedleReportsFailureAndEverySupportedWakeCondition() {
        assertEquals(
            DreamWakeRules.Diagnostic.NOT_DREAMING,
            DreamWakeRules.diagnose(false, false, false, false)
        );
        assertEquals(DreamWakeRules.Diagnostic.READY, DreamWakeRules.diagnose(true, false, false, false));
        assertEquals(DreamWakeRules.Diagnostic.READY, DreamWakeRules.diagnose(false, true, false, false));
        assertEquals(DreamWakeRules.Diagnostic.READY, DreamWakeRules.diagnose(false, false, true, false));
        assertEquals(DreamWakeRules.Diagnostic.READY, DreamWakeRules.diagnose(false, false, false, true));
        assertTrue(ResourceUtilityItemFactory.ids().contains("ingredient_icy_needle"));
    }

    @Test
    void mellifluousHungerFeedsEveryDocumentedModernConsumer() throws IOException {
        final JsonObject belt = json(DATA.resolve("recipe/bitingbelt.json"));
        assertEquals(
            "warlockery:ingredient_mellifluous_hunger",
            belt.getAsJsonObject("key").get("H").getAsString()
        );
        final JsonObject weaver = json(DATA.resolve("recipe/dreamcatcher.json"));
        assertEquals(
            "warlockery:ingredient_mellifluous_hunger",
            weaver.getAsJsonObject("key").get("H").getAsString()
        );
        final JsonObject wasting = json(DATA.resolve("warlockery_machine/kettle_brew_wasting.json"));
        assertTrue(StreamSupport.stream(wasting.getAsJsonArray("inputs").spliterator(), false)
            .map(element -> element.getAsJsonObject().get("ingredient").getAsString())
            .anyMatch("warlockery:ingredient_mellifluous_hunger"::equals));
        assertFalse(wasting.getAsJsonArray("outputs").isEmpty());
    }

    private static JsonObject json(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
