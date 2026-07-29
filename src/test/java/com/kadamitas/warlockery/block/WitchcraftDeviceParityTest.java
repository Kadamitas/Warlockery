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
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class WitchcraftDeviceParityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @TestFactory
    Stream<DynamicContainer> everyFetishModeHasFailureDiagnosticAndSuccessCoverage() {
        return FetishMode.VALUES.stream().map(mode -> DynamicContainer.dynamicContainer(
            "fetish_" + mode.getSerializedName(),
            List.of(
                DynamicTest.dynamicTest("failure", () -> {
                    assertFalse(FetishRules.shouldAffect(false, true, false));
                    assertFalse(FetishRules.shouldAffect(true, false, false));
                    assertFalse(FetishRules.shouldAffect(true, true, true));
                }),
                DynamicTest.dynamicTest("diagnostic", () -> {
                    assertEquals(
                        FetishRules.Diagnostic.WRONG_FOCUS,
                        FetishRules.diagnostic(false, mode, false, false, false)
                    );
                    assertEquals(
                        FetishRules.Diagnostic.READY,
                        FetishRules.diagnostic(true, mode, false, false, true)
                    );
                }),
                DynamicTest.dynamicTest("success", () -> {
                    assertTrue(FetishRules.shouldAffect(true, true, false));
                    assertNotEquals(mode, mode.next());
                })
            )
        ));
    }

    @TestFactory
    Stream<DynamicContainer> everyDreamWeaverModeHasFailureDiagnosticAndSuccessCoverage() {
        return DreamWeaverMode.VALUES.stream().map(mode -> DynamicContainer.dynamicContainer(
            "dream_weaver_" + mode.getSerializedName(),
            List.of(
                DynamicTest.dynamicTest("failure", () -> {
                    assertFalse(DreamWeaverRules.canReward(false, 100, false, true));
                    assertFalse(DreamWeaverRules.canReward(true, 99, false, true));
                    assertFalse(DreamWeaverRules.canReward(true, 100, true, true));
                    assertFalse(DreamWeaverRules.canReward(true, 100, false, false));
                }),
                DynamicTest.dynamicTest("diagnostic", () -> {
                    final DreamWeaverRules.WakeReward reward = DreamWeaverRules.reward(mode, true);
                    assertFalse(reward.effect().isBlank());
                    assertTrue(json("assets/warlockery/blockstates/dreamcatcher.json").has("multipart"));
                }),
                DynamicTest.dynamicTest("success", () -> {
                    assertTrue(DreamWeaverRules.canReward(true, 100, false, true));
                    assertNotEquals(mode, mode.next());
                    final DreamWeaverRules.WakeReward reward = DreamWeaverRules.reward(mode, false);
                    assertEquals(mode == DreamWeaverMode.NIGHTMARES, reward.spawnNightmare());
                })
            )
        ));
    }

    @Test
    void invalidWakeRewardsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new DreamWeaverRules.WakeReward(-1, 0.0F, "speed", false, false)
        );
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
