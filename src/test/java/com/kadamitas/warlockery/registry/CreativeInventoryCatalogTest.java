package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CreativeInventoryCatalogTest {
    @Test
    void internalWorldStateItemsStayOutOfTheCreativeTab() {
        List.of(
            "abyssal_portal", "alchemical_oven_lit", "barrier", "biomenote", "brew", "brewgas",
            "brewliquid", "disease", "distilleryburning", "force", "hollowtears", "ingredient", "light",
            "mirrorblock2", "placeditem", "potion", "spiritflowing", "wallgen"
        ).forEach(id -> assertFalse(CreativeInventoryCatalog.isVisible(id), id));
        List.of("altar", "spiritportal", "mirrorblock", "ingredient_broom", "werewolf_spawn_egg")
            .forEach(id -> assertTrue(CreativeInventoryCatalog.isVisible(id), id));
    }

    @Test
    void contentIsGroupedByPlayerPurpose() {
        assertEquals(CreativeInventoryCatalog.Section.GETTING_STARTED,
            CreativeInventoryCatalog.section("ingredient_book_herbology"));
        assertEquals(CreativeInventoryCatalog.Section.RITUALS,
            CreativeInventoryCatalog.section("broken_hexes_statue"));
        assertEquals(CreativeInventoryCatalog.Section.MACHINES, CreativeInventoryCatalog.section("altar"));
        assertEquals(CreativeInventoryCatalog.Section.DOLLS, CreativeInventoryCatalog.section("fire_guard_doll"));
        assertEquals(CreativeInventoryCatalog.Section.BREWS,
            CreativeInventoryCatalog.section("brew_murderous_flock"));
        assertEquals(CreativeInventoryCatalog.Section.EQUIPMENT,
            CreativeInventoryCatalog.section("delvealloypickaxe"));
        assertEquals(CreativeInventoryCatalog.Section.NATURE, CreativeInventoryCatalog.section("alder_sapling"));
        assertEquals(CreativeInventoryCatalog.Section.CREATURES,
            CreativeInventoryCatalog.section("werewolf_spawn_egg"));
    }

    @Test
    void sortingIsStableBySectionThenIdentifier() {
        assertEquals(
            List.of("cauldronbook", "statuegoddess", "altar", "brew_murderous_flock", "werewolf_spawn_egg"),
            CreativeInventoryCatalog.sortedIds(List.of(
                "werewolf_spawn_egg", "brew_murderous_flock", "altar", "statuegoddess", "cauldronbook", "light"
            ))
        );
    }
}
