package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WitchcraftDeviceParityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void fetishAffectsOnlyActiveEligibleUnprotectedTargets() {
        assertFalse(FetishRules.shouldAffect(false, true, false));
        assertFalse(FetishRules.shouldAffect(true, false, false));
        assertFalse(FetishRules.shouldAffect(true, true, true));
        assertTrue(FetishRules.shouldAffect(true, true, false));
    }

    @Test
    void dreamRewardsRequireFullSleepAndAnEligibleSleeper() {
        assertFalse(DreamWeaverRules.canReward(false, 100, false, true));
        assertFalse(DreamWeaverRules.canReward(true, 99, false, true));
        assertFalse(DreamWeaverRules.canReward(true, 100, true, true));
        assertFalse(DreamWeaverRules.canReward(true, 100, false, false));
        assertTrue(DreamWeaverRules.canReward(true, 100, false, true));
    }

    @Test
    void invalidWakeRewardsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new DreamWeaverRules.WakeReward(-1, 0.0F, "speed", false, false)
        );
    }

    @Test
    void ironArmUsesHasteAndNearbyNightmareWeaversCorruptItToMiningFatigue() {
        assertEquals("haste", DreamWeaverRules.reward(DreamWeaverMode.IRON_ARM, false, 0).effect());
        assertEquals("mining_fatigue", DreamWeaverRules.reward(DreamWeaverMode.IRON_ARM, false, 1).effect());
        assertEquals("haste", DreamWeaverRules.reward(DreamWeaverMode.IRON_ARM, true, 1).effect());
    }

    @Test
    void nightmareWeaverProtectsDreamEntryButStillHasDocumentedWakingCosts() {
        assertEquals("weakness", DreamWeaverRules.reward(DreamWeaverMode.NIGHTMARES, false, 1).effect());
        assertEquals("blindness", DreamWeaverRules.reward(DreamWeaverMode.NIGHTMARES, false, 2).effect());
        assertFalse(DreamWeaverRules.reward(DreamWeaverMode.NIGHTMARES, false, 2).spawnNightmare());
        assertThrows(IllegalArgumentException.class, () ->
            DreamWeaverRules.reward(DreamWeaverMode.NIGHTMARES, false, -1)
        );
    }

    @Test
    void intensityBoostsHelpfulWeaversAndNightmaresAddTheirSecondaryWeakness() {
        final DreamWeaverRules.WakeReward fleet = DreamWeaverRules.reward(
            DreamWeaverMode.FLEET_FOOT,
            false,
            0,
            1
        );
        assertEquals(2, fleet.effects().getFirst().amplifier());
        assertEquals(1_800, fleet.effects().getFirst().duration());
        final DreamWeaverRules.WakeReward corruptedFleet = DreamWeaverRules.reward(
            DreamWeaverMode.FLEET_FOOT,
            false,
            1,
            1
        );
        assertEquals(List.of("slowness", "weakness"), corruptedFleet.effects().stream()
            .map(DreamWeaverRules.EffectReward::id)
            .toList());
        assertEquals(12, DreamWeaverRules.reward(DreamWeaverMode.FASTING, false, 0, 1).nutrition());
    }

    @Test
    void devicesExposeExtensionTagsAndGenericStateModels() {
        assertTrue(json("data/warlockery/tags/item/configuration_foci.json").has("values"));
        assertTrue(json("data/warlockery/tags/block/dream_protective_plants.json").has("values"));
        assertTrue(json("data/warlockery/tags/fluid/dream_protective_fluids.json").has("values"));
        assertTrue(json("data/warlockery/tags/entity_type/fetish_immune.json").has("values"));
        assertTrue(json("assets/warlockery/blockstates/scarecrow.json").has("multipart"));
    }

    private static JsonObject json(final String relative) {
        final Path path = RESOURCES.resolve(relative);
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
