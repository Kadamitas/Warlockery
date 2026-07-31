package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.menu.MachineUiLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MachineAndVillagerReleaseTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");

    @Test
    void machineFamiliesUseCompleteAndDistinctLayouts() {
        final MachineUiLayout oven = MachineUiLayout.forKind("alchemical_oven");
        final MachineUiLayout wheel = MachineUiLayout.forKind("spinningwheel");
        final MachineUiLayout kettle = MachineUiLayout.forKind("kettle");

        assertEquals(9, oven.slots().size());
        assertEquals(9, wheel.slots().size());
        assertEquals(9, kettle.slots().size());
        assertNotEquals(oven.slots(), wheel.slots());
        assertNotEquals(wheel.slots(), kettle.slots());
    }

    @Test
    void warlockProfessionHasFiveTradeLevelsAndExclusiveCropTending() throws IOException {
        for (int level = 1; level <= 5; level++) {
            final Path tradeSet = DATA.resolve(Path.of("warlockery", "trade_set", "warlock", "level_" + level + ".json"));
            assertTrue(Files.exists(tradeSet));
            assertTrue(Files.readString(tradeSet).contains("warlockery:warlock/" + level));
        }
        assertFalse(Files.exists(DATA.resolve(Path.of(
            "minecraft", "tags", "item", "villager_plantable_seeds.json"
        ))));
        final String farming = Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "world", "WarlockVillagerFarming.java"
        ));
        assertTrue(farming.contains("profession().is(ModVillagers.WARLOCK_KEY)"));
        assertTrue(farming.contains("instanceof WarlockeryCropBlock"));
    }

    @Test
    void glyphsHaveNeitherItemsNorLootTables() throws IOException {
        final Path loot = DATA.resolve(Path.of("warlockery", "loot_table", "blocks"));
        final List<String> glyphs = List.of(
            "circle", "circleglyphgolden", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
        );

        assertTrue(glyphs.stream().noneMatch(id -> Files.exists(loot.resolve(id + ".json"))));
        final String itemRegistry = Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "registry", "ModItems.java"
        ));
        assertTrue(itemRegistry.contains("!ConnectedGlyphBlock.supports(id)"));
        assertFalse(itemRegistry.contains("register(\"circleglyphritual\""));
    }
}
