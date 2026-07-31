package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArcaneFluidParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery", "tags");

    @Test
    void hollowTearsLeavesUnclassifiedEntitiesUntouched() {
        assertEquals(ArcaneFluidRules.Outcome.NONE, ArcaneFluidRules.hollowTearsOutcome(false, true, true));
        assertEquals(ArcaneFluidRules.Outcome.NONE, ArcaneFluidRules.hollowTearsOutcome(true, false, false));
    }

    @Test
    void beneficiariesTakePriorityOverVictims() {
        assertEquals(ArcaneFluidRules.Outcome.BENEFIT, ArcaneFluidRules.hollowTearsOutcome(true, true, true));
        assertEquals(ArcaneFluidRules.Outcome.HARM, ArcaneFluidRules.hollowTearsOutcome(true, false, true));
    }

    @Test
    void flowingSpiritHealsMortalsAndWeakensNightmaresUndeadAndDemons() {
        assertEquals(ArcaneFluidRules.Outcome.NONE, ArcaneFluidRules.flowingSpiritOutcome(false, true, true));
        assertEquals(ArcaneFluidRules.Outcome.BENEFIT, ArcaneFluidRules.flowingSpiritOutcome(true, false, false));
        assertEquals(ArcaneFluidRules.Outcome.HARM, ArcaneFluidRules.flowingSpiritOutcome(true, true, false));
        assertEquals(ArcaneFluidRules.Outcome.HARM, ArcaneFluidRules.flowingSpiritOutcome(true, false, true));
    }

    @Test
    void fluidAndCreatureFamiliesAreDataPackExtensible() throws IOException {
        assertTrue(values("fluid/hollow_tears.json") >= 2);
        assertTrue(values("entity_type/hollow_tears_beneficiaries.json") >= 2);
        assertTrue(values("entity_type/hollow_tears_victims.json") >= 1);
    }

    private static int values(final String relative) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(relative)))
            .getAsJsonObject()
            .getAsJsonArray("values")
            .size();
    }
}
