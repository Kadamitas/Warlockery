package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.block.DreamWeaverMode;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.item.DollKind;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.MagicPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LegacyMagicCatalogTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Map<String, String> THROWABLE_BREWS = Map.ofEntries(
        Map.entry("Brew of Vines", "vines"),
        Map.entry("Brew of Thorns", "thorns"),
        Map.entry("Brew of Webs", "webs"),
        Map.entry("Brew of Ink", "ink"),
        Map.entry("Brew of Sprouting", "sprouting"),
        Map.entry("Brew of Erosion", "erosion"),
        Map.entry("Brew of Raising", "raising"),
        Map.entry("Brew of the Grotesque", "grotesque"),
        Map.entry("Brew of Love", "love"),
        Map.entry("Brew of Frost", "frost"),
        Map.entry("Brew of the Depths", "depths"),
        Map.entry("Brew of Infection", "infection"),
        Map.entry("Brew of Sleeping", "sleeping"),
        Map.entry("Brew of Wasting", "wasting"),
        Map.entry("Brew of Bats", "bats"),
        Map.entry("Brew of Revealing", "revealing"),
        Map.entry("Brew of Substitution", "substitution"),
        Map.entry("Brew of Cursed Leaping", "cursed_leaping"),
        Map.entry("Brew of Frog's Tongue", "frogs_tongue"),
        Map.entry("Brew of Bodega", "bodega")
    );
    private static final Map<String, String> SPECIAL_BREWS = Map.of(
        "Brew of Flowing Spirit", "warlockery_machine/cauldron_flowing_spirit.json",
        "Brew of Hollow Tears", "tags/fluid/hollow_tears.json",
        "Solidifying Brews", "warlockery_machine/kettle_brew_solidify_stone.json",
        "Infused Brew Base", "warlockery_machine/kettle_infusion_base.json",
        "Infused Brew of Soaring", "ritual/infuse_brew_soaring.json",
        "Infused Brew of the Grave", "ritual/infuse_brew_grave.json",
        "Redstone Soup", "warlockery_machine/kettle_redstone_soup.json"
    );
    private static final Map<String, String> BOOKS = Map.ofEntries(
        Map.entry("Witchcraft: Collecting Fumes", "ingredient_book_oven"),
        Map.entry("Witchcraft: Herbology", "ingredient_book_herbology"),
        Map.entry("Witchcraft: Distilling", "ingredient_book_distilling"),
        Map.entry("Witchcraft: Circle Magic", "ingredient_book_circle_magic"),
        Map.entry("Witchcraft: Brews & Infusions", "ingredient_book_infusions"),
        Map.entry("Witchcraft: Symbology", "ingredient_book_wands"),
        Map.entry("Book of Biomes", "ingredient_book_biomes"),
        Map.entry("Book of Biomes (Extended Edition)", "bookbiomes2"),
        Map.entry("Witchcraft: Conjuration & Fetishes", "ingredient_book_burning"),
        Map.entry("Observations of an Immortal", "vampirebook"),
        Map.entry("Torn Page", "ingredient_vbook_page"),
        Map.entry("Witches' Brews", "cauldronbook")
    );
    private static final Map<String, DollKind> DOLLS = Map.of(
        "Earth", DollKind.EARTH_GUARD,
        "Water", DollKind.WATER_GUARD,
        "Fire", DollKind.FIRE_GUARD,
        "Hunger", DollKind.HUNGER_GUARD,
        "Anti-Voodoo", DollKind.HEX_GUARD,
        "Tool", DollKind.TOOL_MENDING,
        "Anti-Death", DollKind.DEATH_GUARD,
        "Poppet Protection", DollKind.DOLL_GUARD,
        "Voodoo", DollKind.HEXING,
        "Vampiric", DollKind.BLOOD_LINK
    );
    private static final Map<String, DreamWeaverMode> DREAM_CATCHERS = Map.of(
        "Swiftness", DreamWeaverMode.FLEET_FOOT,
        "Iron Arm", DreamWeaverMode.IRON_ARM,
        "Satiation", DreamWeaverMode.FASTING,
        "Intensity", DreamWeaverMode.INTENSITY,
        "Nightmares", DreamWeaverMode.NIGHTMARES
    );
    private static final Map<String, MagicPath> SUPERPOWERS = Map.of(
        "Infusion of Light", MagicPath.LIGHT,
        "Infusion of the Overworld", MagicPath.OVERWORLD,
        "Infusion of Otherwhere", MagicPath.OTHERWHERE,
        "Infernal Infusion", MagicPath.INFERNAL
    );

    @Test
    void everyLinkedBrewHasAWorkingModernCounterpart() {
        assertEquals(27, THROWABLE_BREWS.size() + SPECIAL_BREWS.size());
        THROWABLE_BREWS.forEach((page, id) -> {
            final BrewKind brew = BrewKind.find(id).orElseThrow(() -> new AssertionError(page));
            assertFalse(brew.effects().isEmpty() && brew.behaviors().isEmpty(), page);
            assertTrue(Files.exists(DATA.resolve("warlockery_machine/kettle_brew_" + id + ".json")), page);
        });

        final Set<String> solidifying = Set.of(
            "solidify_stone", "solidify_dirt", "solidify_sand", "solidify_sandstone", "solidify_erosion"
        );
        solidifying.forEach(id -> assertTrue(BrewKind.find(id).isPresent(), id));
        SPECIAL_BREWS.forEach((page, relative) -> assertTrue(Files.exists(DATA.resolve(relative)), page));
    }

    @Test
    void everyLinkedBookHasItsOwnManualProfile() {
        assertEquals(12, BOOKS.size());
        assertEquals(Set.copyOf(BOOKS.values()), ManualProfile.ids());
        BOOKS.forEach((page, id) -> assertFalse(ManualProfile.find(id).orElseThrow().sections().isEmpty(), page));
    }

    @Test
    void everyLinkedPoppetMapsToADurableDollAbility() {
        assertEquals(10, DOLLS.size());
        DOLLS.forEach((page, kind) -> assertTrue(kind.definition().durability() > 1, page));
    }

    @Test
    void everyLinkedDreamCatcherHasAWeaverMode() {
        assertEquals(5, DREAM_CATCHERS.size());
        DREAM_CATCHERS.forEach((page, mode) -> assertTrue(DreamWeaverMode.VALUES.contains(mode), page));
    }

    @Test
    void everyLinkedSuperpowerHasAPlayableMagicPath() {
        assertEquals(4, SUPERPOWERS.size());
        SUPERPOWERS.forEach((page, path) -> assertEquals(path, MagicPath.require(path.id()), page));
        assertEquals(
            58,
            THROWABLE_BREWS.size() + SPECIAL_BREWS.size() + BOOKS.size()
                + DOLLS.size() + DREAM_CATCHERS.size() + SUPERPOWERS.size()
        );
    }
}
