package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        final List<MachineUiLayout> layouts = List.of(
            "alchemical_oven", "distillery", "kettle", "cauldron", "silvervat", "spinningwheel", "brazier"
        ).stream().map(MachineUiLayout::forKind).toList();

        assertEquals(7, layouts.size());
        assertEquals(7, layouts.stream().map(MachineUiLayout::slots).collect(java.util.stream.Collectors.toSet()).size());
        for (final MachineUiLayout layout : layouts) {
            assertEquals(9, layout.slots().size());
            assertTrue(layout.statusY() + 13 < layout.inventoryY() - 17);
            assertTrue(layout.inventoryY() + 75 <= layout.height());
            layout.slots().stream().filter(MachineUiLayout.SlotPosition::visible).forEach(slot -> {
                assertTrue(slot.x() >= 0 && slot.x() + 16 <= layout.width());
                assertTrue(slot.y() >= 24 && slot.y() + 16 < layout.statusY());
            });
        }
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
