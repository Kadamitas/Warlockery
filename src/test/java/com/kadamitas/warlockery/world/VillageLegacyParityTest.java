package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageLegacyParityTest {
    @Test
    void guardsRequireHeroicVillageStandingAndLeatherTunic() {
        assertTrue(VillageGuardRules.canCommission(true, true, true, true));
        assertFalse(VillageGuardRules.canCommission(false, true, true, true));
        assertFalse(VillageGuardRules.canCommission(true, false, true, true));
        assertFalse(VillageGuardRules.canCommission(true, true, false, true));
        assertFalse(VillageGuardRules.canCommission(true, true, true, false));
    }

    @Test
    void hobgoblinHutsStayOutsideVillagesAndRemainSmallCamps() {
        assertTrue(HobgoblinCampRules.canFound(false, false, true, 32));
        assertFalse(HobgoblinCampRules.canFound(true, false, true, 32));
        assertFalse(HobgoblinCampRules.canFound(false, true, true, 32));
        assertEquals(2, HobgoblinCampRules.residents(0));
        assertEquals(4, HobgoblinCampRules.residents(2));
    }

    @Test
    void villageEnrichmentBuildsApothecaryAndKeepFeatures() throws IOException {
        final String runtime = Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "world", "CreatureWorldIntegration.java"
        ));
        assertTrue(runtime.contains("buildApothecary"));
        assertTrue(runtime.contains("distilleryidle"));
        assertTrue(runtime.contains("ModVillagers.WARLOCK_KEY"));
        assertTrue(runtime.contains("buildTownKeep"));
        assertTrue(runtime.contains("Items.GOLDEN_CHESTPLATE"));
    }
}
