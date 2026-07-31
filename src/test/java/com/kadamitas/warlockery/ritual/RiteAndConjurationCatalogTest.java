package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class RiteAndConjurationCatalogTest {
    private static final Path RITUAL_DIRECTORY = Path.of(
        "src", "main", "resources", "data", "warlockery", "ritual"
    );
    private static final Path MACHINE_DIRECTORY = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );
    private static final Map<String, RitualDefinition> RITUALS = JsonFixtureLoader.load(
        RITUAL_DIRECTORY,
        RitualDefinition.CODEC
    ).stream().collect(Collectors.toUnmodifiableMap(JsonFixtureLoader.Fixture::id, JsonFixtureLoader.Fixture::value));
    private static final Map<String, List<String>> RITE_PAGES = Map.ofEntries(
        Map.entry("Rite of Binding", List.of("bind_circle", "bind_waystone", "bind_familiar", "bind_fetish", "bind_trent", "bind_spectral", "bind_statue_player")),
        Map.entry("Rite of Charging", List.of("charge_attuned_stone", "recharge_infusion")),
        Map.entry("Rite of Glyphic Transformation", List.of("glyph_to_ritual", "glyph_to_infernal", "glyph_to_the_veil")),
        Map.entry("Rite of Transposition", List.of("teleport_waystone", "teleport_entity", "transpose_ore")),
        Map.entry("Rite of Manifestation", List.of("manifestation")),
        Map.entry("Rite of Beastial Call", List.of("call_beasts")),
        Map.entry("Rite of Protection", List.of("barrier", "barrier_large", "barrier_portable")),
        Map.entry("Rite of Imprisonment", List.of("imprisonment")),
        Map.entry("Rite of Sanctity", List.of("sanctity")),
        Map.entry("Rite of Icy Expansion", List.of("ice_shell")),
        Map.entry("Rite of Broken Earth", List.of("part_earth")),
        Map.entry("Rite of Moving Earth", List.of("raise_earth_small", "raise_earth", "raise_earth_large")),
        Map.entry("Rite of Earth's Wrath", List.of("volcano")),
        Map.entry("Rite of Sky's Wrath", List.of("storm", "storm_large", "storm_portable")),
        Map.entry("Rite of Total Eclipse", List.of("eclipse", "eclipse_portable")),
        Map.entry("Rite of Fertility", List.of("fertility", "fertility_portable")),
        Map.entry("Rite of Nature's Power", List.of("natures_power")),
        Map.entry("Rite of the Forest", List.of("forestation")),
        Map.entry("Rite of Shifting Seasons", List.of("climate_change")),
        Map.entry("Rite of Broiling", List.of("cook_food")),
        Map.entry("Rite of Banishing", List.of("banish_demon", "banish_demon_portable")),
        Map.entry("Rite of Summoning", List.of("summon_demon", "summon_imp", "summon_wither", "summon_reflection", "summon_witch", "summon_familiar")),
        Map.entry("Rite of Infusion", List.of("infusion_light", "infusion_earth", "infusion_ender", "infusion_hell", "infuse_broom", "infuse_mirror")),
        Map.entry("Rite of Necromancy", List.of("necrostone", "spectral_stone")),
        Map.entry("Rite of Prior Incarnation", List.of("prior_incarnation")),
        Map.entry("Rite of Remove Curse", List.of("cure_misfortune", "cure_insanity", "cure_sinking", "cure_overheating", "cure_nightmare")),
        Map.entry("Curse of Blindness", List.of("blindness")),
        Map.entry("Curse of Blight", List.of("blight")),
        Map.entry("Curse of Misfortune", List.of("hex_misfortune")),
        Map.entry("Curse of Insanity", List.of("hex_insanity")),
        Map.entry("Curse of Sinking", List.of("hex_sinking")),
        Map.entry("Curse of Overheating", List.of("hex_overheating")),
        Map.entry("Curse of Waking Nightmare", List.of("hex_nightmare")),
        Map.entry("Curse of Corrupt Poppet", List.of("corrupt_doll")),
        Map.entry("Curse of Raining Toads", List.of("rain_of_toads")),
        Map.entry("Curse of the Wolf", List.of("hex_wolf")),
        Map.entry("Curse of Hell on Earth", List.of("hell_on_earth"))
    );
    private static final Map<String, String> CONJURATION_PAGES = Map.of(
        "Graveyard Mist", "brazier_graveyard_mist",
        "Summon Spectre", "brazier_summon_spectre",
        "Summon Banshee", "brazier_summon_banshee",
        "Deathly Veil", "brazier_deathly_veil",
        "Anguish of the Dead", "brazier_anguish_of_the_dead",
        "Fortification of the Corpse", "brazier_fortification_of_the_corpse",
        "Drain Growth", "brazier_drain_growth"
    );

    @Test
    void archivedHomepageContainsEveryRiteAndConjurationLinkExactlyOnce() {
        assertEquals(37, RITE_PAGES.size());
        assertEquals(7, CONJURATION_PAGES.size());
        assertEquals(44, Stream.concat(RITE_PAGES.keySet().stream(), CONJURATION_PAGES.keySet().stream())
            .distinct()
            .count());
    }

    @TestFactory
    Stream<DynamicContainer> everyArchivedRitePageHasConcreteDatapackCoverage() {
        return RITE_PAGES.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
            entry.getKey(),
            entry.getValue().stream().map(id -> DynamicTest.dynamicTest(id, () -> {
                assertNotNull(RITUALS.get(id));
                assertTrue(Files.isRegularFile(RITUAL_DIRECTORY.resolve(id + ".json")));
            })).toList()
        ));
    }

    @TestFactory
    Stream<DynamicTest> everyArchivedConjurationPageHasABrazierRecipe() {
        return CONJURATION_PAGES.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
            entry.getKey(),
            () -> assertTrue(Files.isRegularFile(MACHINE_DIRECTORY.resolve(entry.getValue() + ".json")))
        ));
    }
}
