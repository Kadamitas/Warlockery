package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final Path WORLD_GENERATION = Path.of(
        "src/main/java/com/kadamitas/warlockery/fabric/WarlockeryWorldGeneration.java"
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
        final String registration = Files.readString(WORLD_GENERATION);
        assertTrue(registration.contains("BiomeModifications.addSpawn"));
        assertTrue(registration.contains("addSpawn(forests, \"goblin\", 3, 1, 3)"));
        assertTrue(registration.contains("addSpawn(forests, \"hobgoblin\", 5, 1, 3)"));
    }

    @Test
    void goblinAndHobgoblinNaturalSpawnRegistrationsUseTheirOwnExactPredicates() throws IOException {
        final String registry = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModEntities.java"
        ));
        // Both goblinfolk bodies now carry their own village-exclusion predicate, and the generic
        // Monster::checkMonsterSpawnRules loop must clobber neither of them.
        assertTrue(registry.contains("HobgoblinEntity::checkNaturalSpawnRules"));
        assertTrue(registry.contains("GoblinEntity::checkNaturalSpawnRules"));
        assertTrue(registry.contains("!\"hobgoblin\".equals(id)"));
        assertTrue(registry.contains("!\"goblin\".equals(id)"));
    }
}
