package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoblinHostilityRulesTest {
    private static final Path FOREST_SPAWNS = Path.of(
        "src/main/resources/data/warlockery/forge/biome_modifier/forest_creatures.json"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyOrdinaryGoblinsRaidVillagers() {
        assertTrue(GoblinHostilityRules.raidsVillagers(CreatureKind.GOBLIN));
        assertFalse(GoblinHostilityRules.raidsVillagers(CreatureKind.HOBGOBLIN));
        assertFalse(GoblinHostilityRules.raidsVillagers(CreatureKind.STONEBROKER));
        assertFalse(GoblinHostilityRules.raidsVillagers(CreatureKind.FORGEWARDEN));
        assertTrue(GoblinHostilityRules.canTarget(CreatureKind.GOBLIN, EntityTypes.VILLAGER));
        assertFalse(GoblinHostilityRules.canTarget(CreatureKind.GOBLIN, EntityTypes.WANDERING_TRADER));
        assertFalse(GoblinHostilityRules.canTarget(CreatureKind.HOBGOBLIN, EntityTypes.VILLAGER));
        assertTrue(GoblinHostilityRules.isHumanVillager(EntityTypes.VILLAGER));
        assertFalse(GoblinHostilityRules.isHumanVillager(EntityTypes.WANDERING_TRADER));
    }

    @Test
    void goblinsAndTravellingHobgoblinsBothSpawnNaturally() throws IOException {
        final JsonObject modifier = JsonParser.parseString(Files.readString(FOREST_SPAWNS)).getAsJsonObject();
        final var spawners = modifier.getAsJsonArray("spawners");
        assertTrue(spawners.asList().stream()
            .anyMatch(entry -> entry.getAsJsonObject().get("type").getAsString().equals("warlockery:goblin")));
        assertTrue(spawners.asList().stream()
            .anyMatch(entry -> entry.getAsJsonObject().get("type").getAsString().equals("warlockery:hobgoblin")));
    }

    @Test
    void hobgoblinNaturalSpawnRegistrationUsesTheVillageExclusionPredicate() throws IOException {
        final String registry = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModEntities.java"
        ));
        assertTrue(registry.contains("HobgoblinEntity::checkNaturalSpawnRules"));
        assertTrue(registry.contains("filter(id -> !\"hobgoblin\".equals(id))"));
    }
}
