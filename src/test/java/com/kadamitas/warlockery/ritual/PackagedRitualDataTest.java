package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Packaged data validation; native acceptance verifies casting and actual outcomes. */
final class PackagedRitualDataTest {
    @Test
    void packagedRitualsHaveValidDefinitionsAndNamedActions() {
        final var fixtures = JsonFixtureLoader.load(
            Path.of("src/main/resources/data/warlockery/ritual"), RitualDefinition.CODEC);
        assertFalse(fixtures.isEmpty(), "The packaged ritual directory must not be empty");
        for (final var fixture : fixtures) {
            final var definition = fixture.value();
            assertTrue(RitualValidator.isStructurallyValid(definition), fixture.id());
            assertNotNull(RitualAction.require(definition.action()).outcome(), fixture.id());
            assertFalse(definition.title().isBlank(), fixture.id());
            assertFalse(definition.description().isBlank(), fixture.id());
        }
    }
}
