package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EntParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void ordinaryDamageIsCappedWhileAxesAndMobsExploitTheWeakness() {
        assertEquals(7.0F, EntRules.incomingDamage(7.0F, false, false));
        assertEquals(15.0F, EntRules.incomingDamage(80.0F, false, false));
        assertEquals(30.0F, EntRules.incomingDamage(10.0F, true, false));
        assertEquals(24.0F, EntRules.incomingDamage(8.0F, false, true));
        assertEquals(0.0F, EntRules.incomingDamage(Float.POSITIVE_INFINITY, true, false));
    }

    @Test
    void everyNeighboringLogAddsOnePercentagePointToTheEncounterChance() {
        assertEquals(0.0D, EntRules.logBreakSpawnChance(0));
        assertEquals(0.08D, EntRules.logBreakSpawnChance(8));
        assertEquals(1.0D, EntRules.logBreakSpawnChance(120));
        assertTrue(EntRules.shouldSpawn(8, 0.079D));
        assertFalse(EntRules.shouldSpawn(8, 0.08D));
        assertFalse(EntRules.shouldSpawn(-1, 0.0D));
    }

    @Test
    void logBreakSpawnOffsetsStayWithinTheDocumentedRanges() {
        assertEquals(-8, EntRules.horizontalOffset(0, false));
        assertEquals(16, EntRules.horizontalOffset(8, true));
        assertEquals(0, EntRules.verticalOffset(0));
        assertEquals(6, EntRules.verticalOffset(6));
        assertThrows(IllegalArgumentException.class, () -> EntRules.horizontalOffset(9, true));
        assertThrows(IllegalArgumentException.class, () -> EntRules.verticalOffset(7));
    }

    @Test
    void growthPulseRunsOncePerConfiguredInterval() {
        final int entityId = 37;
        final long pulses = java.util.stream.IntStream.range(0, EntRules.FERTILIZE_INTERVAL_TICKS * 3)
            .filter(tick -> EntRules.shouldFertilizeGround(tick, entityId))
            .count();
        assertEquals(3L, pulses);
    }

    @Test
    void lootAndLogTagsExposeTheCompleteEntAcquisitionPath() throws IOException {
        final String loot = Files.readString(DATA.resolve("loot_table/entities/ent.json"));
        assertTrue(loot.contains("warlockery:ingredient_heartwood_splinter"));
        assertTrue(loot.contains("warlockery:alder_sapling"));
        assertTrue(loot.contains("warlockery:hawthorn_sapling"));
        assertTrue(loot.contains("warlockery:rowan_sapling"));
        assertFalse(loot.contains("warlockery:hex_sapling"));
        assertFalse(loot.contains("minecraft:killed_by_player"));

        final String logs = Files.readString(DATA.resolve("tags/block/ent_spawning_logs.json"));
        assertTrue(logs.contains("#warlockery:alder_logs"));
        assertTrue(logs.contains("#warlockery:hawthorn_logs"));
        assertTrue(logs.contains("#warlockery:rowan_logs"));
    }
}
