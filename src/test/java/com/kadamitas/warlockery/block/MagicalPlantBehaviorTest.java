package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.MagicalPlantBlockFactory.Behavior;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class MagicalPlantBehaviorTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @Test
    void factoryDispatchIsExact() {
        assertEquals(Set.of(
            "embermoss", "glintweed", "leapinglily", "bramble",
            "bloodrose", "crittersnare", "grassper", "spanishmoss"
        ),
            MagicalPlantBlockFactory.supportedIds());
        assertFalse(MagicalPlantBlockFactory.supports("generic_flower"));
        assertTrue(MagicalPlantBlockFactory.behaviorOf("bloodrose").isPresent());
    }

    @TestFactory
    Stream<DynamicContainer> everyPlantHasFailureCompatibilityAndSuccessCoverage() {
        return Stream.of(
            suite("ember_moss", this::emberMossRejectsSafeCrossings, this::emberMossUsesExtensionsAndShears,
                this::emberMossIgnitesUnsafeCrossings),
            suite("glint_weed", this::glintWeedRejectsUnsafeSpread, this::glintWeedUsesStrongLightAndGroundTag,
                this::glintWeedAcceptsSafeSpread),
            suite("leaping_lily", this::leapingLilyRejectsIneligibleTicks, this::leapingLilyUsesItsDedicatedBehavior,
                this::leapingLilyBoostsEligibleCrossings),
            suite("ender_bramble", this::enderBrambleRejectsUnsafeTargets, this::enderBrambleUsesImmunityAndGroundTags,
                this::enderBrambleAcceptsSafeTargets)
        );
    }

    private void emberMossRejectsSafeCrossings() {
        assertFalse(MagicalPlantRules.shouldIgnite(true, true, false, false));
        assertFalse(MagicalPlantRules.shouldIgnite(true, false, true, false));
        assertFalse(MagicalPlantRules.shouldIgnite(true, false, false, true));
        assertFalse(MagicalPlantRules.shouldIgnite(false, false, false, false));
        assertFalse(MagicalPlantRules.canSpreadEmberMoss(true, false, true, true, 1));
        assertFalse(MagicalPlantRules.canSpreadEmberMoss(true, true, true, true,
            MagicalPlantRules.MAX_NEARBY_SPREADERS));
    }

    private void emberMossUsesExtensionsAndShears() {
        assertEquals(Behavior.EMBER_MOSS, MagicalPlantBlockFactory.behaviorOf("embermoss").orElseThrow());
        assertTrue(readTag("entity_type/ember_moss_immune").contains("#warlockery:demons"));
        assertTrue(readTag("block/ember_moss_spreadable_ground").contains("#minecraft:dirt"));
        assertTrue(read("loot_table/blocks/embermoss.json").contains("minecraft:shears"));
        assertTrue(Behavior.EMBER_MOSS.randomlyTicks());
    }

    private void emberMossIgnitesUnsafeCrossings() {
        assertTrue(MagicalPlantRules.shouldIgnite(true, false, false, false));
        assertTrue(MagicalPlantRules.canSpreadEmberMoss(true, true, true, true, 1));
    }

    private void glintWeedRejectsUnsafeSpread() {
        assertFalse(MagicalPlantRules.canSpreadGlintWeed(true, false, true, true, 15, 1));
        assertFalse(MagicalPlantRules.canSpreadGlintWeed(true, true, true, true, 7, 1));
        assertFalse(MagicalPlantRules.canSpreadGlintWeed(true, true, true, true, 15,
            MagicalPlantRules.MAX_NEARBY_SPREADERS));
    }

    private void glintWeedUsesStrongLightAndGroundTag() {
        assertEquals(Behavior.GLINT_WEED, MagicalPlantBlockFactory.behaviorOf("glintweed").orElseThrow());
        assertEquals(15, MagicalPlantBlockFactory.lightLevel("glintweed"));
        assertTrue(Behavior.GLINT_WEED.randomlyTicks());
        assertTrue(readTag("block/glint_weed_spreadable_ground").contains("#minecraft:dirt"));
    }

    private void glintWeedAcceptsSafeSpread() {
        assertTrue(MagicalPlantRules.canSpreadGlintWeed(true, true, true, true, 15, 1));
    }

    private void leapingLilyRejectsIneligibleTicks() {
        assertFalse(MagicalPlantRules.shouldBoost(false, 20));
        assertFalse(MagicalPlantRules.shouldBoost(true, 21));
    }

    private void leapingLilyUsesItsDedicatedBehavior() {
        assertEquals(Behavior.LEAPING_LILY, MagicalPlantBlockFactory.behaviorOf("leapinglily").orElseThrow());
        assertFalse(Behavior.LEAPING_LILY.randomlyTicks());
    }

    private void leapingLilyBoostsEligibleCrossings() {
        assertTrue(MagicalPlantRules.shouldBoost(true, 20));
        assertTrue(MagicalPlantRules.shouldBoost(true, 0));
    }

    private void enderBrambleRejectsUnsafeTargets() {
        assertFalse(MagicalPlantRules.canTeleport(true, false, true, true, true, true, true, true));
        assertFalse(MagicalPlantRules.canTeleport(false, true, true, true, true, true, true, true));
        assertFalse(MagicalPlantRules.canTeleport(false, false, false, true, true, true, true, true));
        assertFalse(MagicalPlantRules.canTeleport(false, false, true, true, true, true, false, true));
        assertFalse(MagicalPlantRules.canTeleport(false, false, true, true, true, true, true, false));
    }

    private void enderBrambleUsesImmunityAndGroundTags() {
        assertEquals(Behavior.ENDER_BRAMBLE, MagicalPlantBlockFactory.behaviorOf("bramble").orElseThrow());
        assertTrue(readTag("entity_type/ender_bramble_immune").contains("minecraft:enderman"));
        assertTrue(readTag("block/ender_bramble_teleport_ground").contains("#minecraft:base_stone_overworld"));
    }

    private void enderBrambleAcceptsSafeTargets() {
        assertTrue(MagicalPlantRules.canTeleport(false, false, true, true, true, true, true, true));
    }

    private static DynamicContainer suite(
        final String name,
        final Runnable failure,
        final Runnable compatibility,
        final Runnable success
    ) {
        return DynamicContainer.dynamicContainer(name, Stream.of(
            DynamicTest.dynamicTest("failure", failure::run),
            DynamicTest.dynamicTest("compatibility", compatibility::run),
            DynamicTest.dynamicTest("success", success::run)
        ));
    }

    private static String readTag(final String relative) {
        final String json = read("tags/" + relative + ".json");
        JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values");
        return json;
    }

    private static String read(final String relative) {
        try {
            return Files.readString(DATA.resolve(relative));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
