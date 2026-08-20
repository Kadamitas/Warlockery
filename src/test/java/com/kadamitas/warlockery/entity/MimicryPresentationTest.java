package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.MimicryPresentation.Stance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The negative half of the Glass Doppelganger signature invariant. The allow-list is asserted
 * positively by its own shape and negatively against the runtime source, because a whole-struct
 * copy that silently inherits a field added later is exactly the failure this family exists to
 * avoid.
 */
final class MimicryPresentationTest {

    private static final Path RUNTIME =
        Path.of("src/main/java/com/kadamitas/warlockery/entity/MimicryRuntime.java");
    private static final Path GAME_TESTS =
        Path.of("src/main/java/com/kadamitas/warlockery/entity/MimicryGameTests.java");

    private static String runtimeSource() throws IOException {
        return Files.readString(RUNTIME);
    }

    @Test
    void theAllowListHasExactlyTwoCopiedMembers() {
        assertEquals(2, MimicryPresentation.class.getRecordComponents().length);
        assertEquals(
            List.of("presentedName", "stance"),
            List.of(
                MimicryPresentation.class.getRecordComponents()[0].getName(),
                MimicryPresentation.class.getRecordComponents()[1].getName()
            )
        );
        assertEquals(3, Stance.values().length);
    }

    @Test
    void theFallbackIsNeverDerivedFromAnyEntity() {
        assertTrue(MimicryPresentation.fallback().presentedName().isEmpty());
        assertEquals(Stance.STILL, MimicryPresentation.fallback().stance());
    }

    @Test
    void stanceIsDerivedFromExactlyTwoPublicPoseFactsAndNoDirection() {
        assertEquals(Stance.CROUCHING, MimicryPresentation.stanceOf(true, 0.0D));
        assertEquals(Stance.CROUCHING, MimicryPresentation.stanceOf(true, 5.0D));
        assertEquals(Stance.STILL, MimicryPresentation.stanceOf(false, 0.0D));
        assertEquals(Stance.STILL,
            MimicryPresentation.stanceOf(false, MimicryPresentation.WALKING_SPEED_THRESHOLD));
        assertEquals(Stance.WALKING,
            MimicryPresentation.stanceOf(false, MimicryPresentation.WALKING_SPEED_THRESHOLD + 0.001D));
        assertEquals(Stance.STILL, MimicryPresentation.stanceOf(false, Double.NaN));
    }

    /**
     * The direct negative. No mimic runtime path reads or writes another entity's equipment,
     * inventory, item components, enchantments, NBT, persistent data, attributes, experience,
     * hunger, advancements, statistics, gamemode or ender chest, and none of them copies health.
     */
    @Test
    void theRuntimeNeverReadsOrWritesAnyForbiddenSurfaceOfAnotherEntity() throws IOException {
        final String source = runtimeSource();
        final List<String> forbidden = List.of(
            "getItemBySlot", "setItemSlot", "getInventory", "getEnderChestInventory",
            "getPersistentData", "getAttribute", "getAttributeValue", "getEnchantment",
            "getAdvancements", "getStats", "setHealth", "getFoodData", "gameMode",
            "saveWithoutId", "getExperienceReward", "addTag", "setAbsorptionAmount"
        );
        for (final String token : forbidden) {
            assertFalse(source.contains(token),
                "MimicryRuntime must never touch " + token + ": the copied surface is the allow-list");
        }
    }

    /**
     * The one permitted foreign mutation in either family, stated positively so its scope cannot
     * quietly grow: exactly one Slowness application and one guarded removal, and nothing else.
     */
    @Test
    void theOnlyForeignMutationIsTheOneSlownessPairAndItIsGuarded() throws IOException {
        final String source = runtimeSource();
        assertEquals(1, count(source, "addEffect("),
            "exactly one effect application may exist anywhere in the runtime");
        assertEquals(1, count(source, "removeEffect("),
            "exactly one effect removal may exist anywhere in the runtime");
        assertTrue(source.contains("instance.getAmplifier() != MimicryRules.WEAVER_SNARE_AMPLIFIER"),
            "the removal must prove the instance is the one the weaver applied");
        assertTrue(source.contains("instance.getDuration() > MimicryRules.WEAVER_SNARE_DURATION_TICKS"),
            "the removal must refuse a longer instance belonging to another source");
        assertTrue(source.contains("snareRemovalGuardMisses++"),
            "a failed guard must be counted rather than silently ignored");
    }

    @Test
    void noMimicEverExplodesConvertsTeleportsOrEditsABlock() throws IOException {
        final String source = runtimeSource();
        for (final String token : List.of(
            "explode(", "Explosion", "randomTeleport", "teleportTo", "setBlock", "destroyBlock",
            "convertTo", "getChunk(", "addFreshEntity", "discard()"
        )) {
            assertFalse(source.contains(token), "MimicryRuntime must never call " + token);
        }
    }

    @Test
    void exactlyOneDamageCallSiteExistsAndItIsInTheConfrontationBand() throws IOException {
        final String source = runtimeSource();
        assertEquals(1, count(source, "doHurtTarget("),
            "the three Illusion Copies deal no damage, so exactly one call site may exist");
        final int callSite = source.indexOf("doHurtTarget(");
        final int confront = source.indexOf("private static void confront(");
        assertTrue(confront >= 0 && callSite > confront,
            "the single damage call must live inside the reactive confrontation band");
    }

    @Test
    void noFarFutureSentinelExistsAnywhereInTheRuntimeOrTheRules() throws IOException {
        assertFalse(runtimeSource().contains("Long.MAX_VALUE"));
        assertFalse(
            Files.readString(Path.of("src/main/java/com/kadamitas/warlockery/entity/MimicryRules.java"))
                .contains("Long.MAX_VALUE")
        );
    }

    @Test
    void liveFixturesDisconnectMockPlayersOnSuccessAndFailure() throws IOException {
        final String source = Files.readString(GAME_TESTS);
        assertTrue(source.contains("GameTestMockPlayers.autoDisconnect(helper, player)"),
            "every connected mock player must register a failure-safe disconnect");
        assertTrue(source.contains("helper.addCleanup(passed -> close())"),
            "the fixture scope must register cleanup before it builds or schedules work");
        assertTrue(source.contains("entities.forEach(GameTestMockPlayers::release)"),
            "ordinary close must disconnect players rather than merely discarding them");
        assertFalse(source.contains("entities.forEach(Entity::discard)"),
            "discard leaves mock players in the server player list and poisons later bounded scans");
    }

    private static int count(final String source, final String token) {
        int total = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            total++;
            index = source.indexOf(token, index + token.length());
        }
        return total;
    }
}

