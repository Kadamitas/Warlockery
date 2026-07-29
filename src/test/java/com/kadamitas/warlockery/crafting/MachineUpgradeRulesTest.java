package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class MachineUpgradeRulesTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @Test
    void absentUpgradeLeavesNormalSpeedAndYield() {
        assertEquals(MachineUpgradeRules.Upgrade.NONE, MachineUpgradeRules.combine(IntStream.empty()));
    }

    @Test
    void basicAndFilteredFunnelsIncreaseSpeedAndFumeYield() {
        assertEquals(new MachineUpgradeRules.Upgrade(2, 1), MachineUpgradeRules.combine(IntStream.of(1)));
        assertEquals(new MachineUpgradeRules.Upgrade(3, 2), MachineUpgradeRules.combine(IntStream.of(2)));
    }

    @Test
    void upgradesAreCappedAcrossAllAdjacentFaces() {
        assertEquals(new MachineUpgradeRules.Upgrade(5, 4), MachineUpgradeRules.combine(IntStream.of(2, 2, 2, 2, 2, 2)));
    }

    @Test
    void dataPacksCanExtendFunnelsAndFumeOutputs() throws IOException {
        final String basic = Files.readString(DATA.resolve("tags/block/machine_upgrades/fume_funnels.json"));
        final String filtered = Files.readString(DATA.resolve("tags/block/machine_upgrades/filtered_fume_funnels.json"));
        final String fumes = Files.readString(DATA.resolve("tags/item/alchemical_fumes.json"));
        assertTrue(basic.contains("#warlockery:machine_upgrades/filtered_fume_funnels"));
        assertTrue(filtered.contains("warlockery:filteredfumefunnel"));
        assertTrue(fumes.contains("warlockery:ingredient_foul_fume"));
    }
}
