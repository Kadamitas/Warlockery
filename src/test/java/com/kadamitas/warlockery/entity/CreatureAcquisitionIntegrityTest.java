package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualValidator;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CreatureAcquisitionIntegrityTest {
    @Test void creatureAcquisitionResourcesDecodeWithoutSpawnEggIngredients() throws Exception {
        for (String id : List.of("bind_death", "summon_circle_mage", "summon_cat_familiar", "summon_storm_simian",
                "summon_forgewarden", "summon_stonebroker", "summon_lost_soul", "summon_parasytic_louse",
                "summon_poltergeist", "summon_thorned_pursuer")) {
            final var path = Path.of("src/main/resources/data/warlockery/ritual", id + ".json");
            final var definition = RitualDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(path))).getOrThrow();
            assertTrue(RitualValidator.isStructurallyValid(definition), id);
            assertFalse(definition.requirements().ingredients().isEmpty(), id);
            assertTrue(definition.requirements().ingredients().stream()
                .noneMatch(ingredient -> ingredient.ingredient().contains("spawn_egg")), id);
        }
    }
}
