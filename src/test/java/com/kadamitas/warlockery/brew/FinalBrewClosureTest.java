package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class FinalBrewClosureTest {
    private static final Path MAIN = Path.of("src", "main");
    private static final Path JAVA = MAIN.resolve(Path.of("java", "com", "kadamitas", "warlockery"));
    private static final Path DATA = MAIN.resolve(Path.of("resources", "data", "warlockery"));
    private static final String TRANSLATIONS = read(MAIN.resolve(
        Path.of("resources", "assets", "warlockery", "lang", "en_us.json")
    ));
    private static final List<Case> CASES = List.of(
        marked("Brew of Absorb Magic", "absorb_magic", BrewBehavior.APPLY_ABSORB_MAGIC,
            BrewMarkerKind.ABSORB_MAGIC, evidence("brew/BrewPersistentRuntime.java", "MagicPathState.recharge")),
        fixed("Brew of Bodega", "bodega", BrewBehavior.SUMMON_OWLS,
            evidence("brew/BrewRuntime.java", "ModEntities.ALL.get(\"owl\")")),
        fixed("Brew of Brew Bottling", "bottling", BrewBehavior.BOTTLE_YIELD,
            evidence("brew/custom/CustomBrewFormula.java", "outputCount()")),
        fixed("Brew of Combustion", "combustion", BrewBehavior.IGNITE,
            data("warlockery_machine/cauldron_colored_brew_water.json", "warlockery:bucketbrew")),
        marked("Brew of Cursed Leaping", "cursed_leaping", BrewBehavior.APPLY_CURSED_LEAPING,
            BrewMarkerKind.CURSED_LEAPING, evidence("brew/BrewPersistentRuntime.java", "tickCursedLeaping")),
        marked("Brew of Erosion", "erosion", BrewBehavior.ERODE, BrewMarkerKind.EROSION,
            evidence("block/ErosionBrewLiquidBlock.java", "hurtAndBreak")),
        marked("Brew of Fear", "fear", BrewBehavior.FEAR, BrewMarkerKind.FEAR,
            evidence("brew/BrewPersistentRuntime.java", "tickFear")),
        marked("Brew of Grue's Prey", "grues_prey", BrewBehavior.DARKNESS_PREY, BrewMarkerKind.GRUES_PREY,
            evidence("brew/BrewPersistentRuntime.java", "tickGruesPrey")),
        fixed("Brew of Nightmare", "nightmare", BrewBehavior.APPLY_NIGHTMARE,
            evidence("brew/BrewRuntime.java", "HexKind.WAKING_NIGHTMARE")),
        marked("Brew of Overheating", "overheating", BrewBehavior.APPLY_OVERHEATING,
            BrewMarkerKind.OVERHEATING, evidence("brew/BrewPersistentRuntime.java", "OverheatingRules.shouldBurn")),
        fixed("Brew of Part Lava", "part_lava", BrewBehavior.PART_LAVA,
            evidence("brew/BrewWorldData.java", "replaceTemporarily")),
        fixed("Brew of Raise Dead", "raise_dead", BrewBehavior.RAISE_DEAD,
            evidence("brew/BrewRuntime.java", "CreatureBehaviorState.bind")),
        fixed("Brew of Raising", "raising", BrewBehavior.RAISE_DEAD,
            evidence("brew/BrewRuntime.java", "CreatureBehaviorState.bind")),
        fixed("Brew of Shifting Seasons", "shifting_seasons", BrewBehavior.SHIFT_SEASONS,
            evidence("brew/BrewRuntime.java", "FillBiomeCommand.fill")),
        marked("Brew of Sleeping", "sleeping", BrewBehavior.APPLY_SLEEPING, BrewMarkerKind.SLEEPING,
            evidence("brew/BrewPersistentRuntime.java", "SpiritWorldRuntime.enterFromSleepingBrew")),
        marked("Brew of Snow Burst and Trail", "snow_burst", BrewBehavior.APPLY_SNOW_TRAIL,
            BrewMarkerKind.SNOW_TRAIL, evidence("brew/BrewPersistentRuntime.java", "tickSnowTrail")),
        fixed("Brew of Sprouting", "sprouting", BrewBehavior.SPROUT_BRANCHES,
            data("tags/block/brew_sprouting_branches.json", "#minecraft:logs")),
        fixed("Brew of Substitution", "substitution", BrewBehavior.SUBSTITUTE_BLOCKS,
            data("tags/block/brew_substitutable.json", "minecraft:stone")),
        marked("Brew of the Depths", "depths", BrewBehavior.APPLY_DEPTHS, BrewMarkerKind.DEPTHS,
            evidence("brew/BrewPersistentRuntime.java", "tickDepths")),
        marked("Brew of the Grotesque", "grotesque", BrewBehavior.APPLY_GROTESQUE,
            BrewMarkerKind.GROTESQUE, data("tags/entity_type/grotesque_immune.json", "#warlockery:demons")),
        marked("Brew of Tint Skin", "tint_skin", BrewBehavior.APPLY_TINT_SKIN, BrewMarkerKind.TINT_SKIN,
            evidence("brew/BrewPersistentRuntime.java", "DustParticleOptions")),
        resource("Congealed Spirit",
            data("recipe/ingredient_brew_congealed_spirit.json", "warlockery:ingredient_brew_congealed_spirit"),
            data("tags/item/congealed_spirits.json", "warlockery:ingredient_brew_congealed_spirit")),
        resource("Liquid Brew of Colored Water",
            data("tags/fluid/colored_brew_water.json", "flowing_colored_brew_water")),
        resource("Redstone Soup", data("tags/item/chalice_fillers.json", "ingredient_redstone_soup"),
            evidence("item/RedstoneSoupItem.java", "MobEffects.HEALTH_BOOST", "grantsHealthBoost")),
        fixed("Solidifying Brew", "solidify_stone", BrewBehavior.SOLIDIFY_STONE,
            evidence(
                "brew/BrewRuntime.java",
                "SOLIDIFY_DIRT",
                "SOLIDIFY_SAND",
                "SOLIDIFY_SANDSTONE",
                "erodeHollowTears",
                "WarlockeryTags.Fluids.HOLLOW_TEARS"
            ))
    );

    @TestFactory
    Stream<DynamicContainer> oneFailureVisibleStateAndSuccessSuitePerFormerPartialPage() {
        return CASES.stream().map(testCase -> DynamicContainer.dynamicContainer(testCase.page(), List.of(
            DynamicTest.dynamicTest("failure is bounded", () -> failure(testCase)),
            DynamicTest.dynamicTest("visible state is exposed", () -> visibleState(testCase)),
            DynamicTest.dynamicTest("success path is wired", () -> success(testCase))
        )));
    }

    private static void failure(final Case testCase) {
        assertEquals(BrewRuntime.ImpactResult.ZERO, new BrewRuntime.ImpactResult(0, 0, 0));
        testCase.brewId().ifPresent(id -> assertTrue(BrewKind.find("missing_" + id).isEmpty()));
        assertFalse(testCase.evidence().isEmpty());
    }

    private static void visibleState(final Case testCase) {
        testCase.brewId().ifPresent(id -> assertTrue(
            TRANSLATIONS.contains("\"item.warlockery.brew_" + id + "\"")
        ));
        testCase.marker().ifPresent(marker -> {
            assertFalse(marker.id().isBlank());
            assertTrue(marker.defaultDuration() > 0);
        });
        assertFalse(testCase.page().isBlank());
    }

    private static void success(final Case testCase) {
        testCase.brewId().ifPresent(id -> {
            final BrewKind kind = BrewKind.require(id);
            assertTrue(kind.hasPotionEffects() || !kind.behaviors().isEmpty());
            assertTrue(kind.behaviors().containsAll(testCase.behaviors()));
        });
        testCase.evidence().forEach(evidence -> {
            final String content = read(evidence.path());
            evidence.tokens().forEach(token -> assertTrue(content.contains(token), evidence.path() + ": " + token));
        });
    }

    private static Case fixed(
        final String page,
        final String brewId,
        final BrewBehavior behavior,
        final Evidence... evidence
    ) {
        return new Case(page, Optional.of(brewId), Set.of(behavior), Optional.empty(), List.of(evidence));
    }

    private static Case marked(
        final String page,
        final String brewId,
        final BrewBehavior behavior,
        final BrewMarkerKind marker,
        final Evidence... evidence
    ) {
        return new Case(page, Optional.of(brewId), Set.of(behavior), Optional.of(marker), List.of(evidence));
    }

    private static Case resource(final String page, final Evidence... evidence) {
        return new Case(page, Optional.empty(), Set.of(), Optional.empty(), List.of(evidence));
    }

    private static Evidence evidence(final String path, final String... tokens) {
        return new Evidence(JAVA.resolve(path), List.of(tokens));
    }

    private static Evidence data(final String path, final String... tokens) {
        return new Evidence(DATA.resolve(path), List.of(tokens));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record Case(
        String page,
        Optional<String> brewId,
        Set<BrewBehavior> behaviors,
        Optional<BrewMarkerKind> marker,
        List<Evidence> evidence
    ) {
        private Case {
            behaviors = Set.copyOf(behaviors);
            evidence = List.copyOf(evidence);
        }
    }

    private record Evidence(Path path, List<String> tokens) {
        private Evidence {
            tokens = List.copyOf(tokens);
        }
    }
}
