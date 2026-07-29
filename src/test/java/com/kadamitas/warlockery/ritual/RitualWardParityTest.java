package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class RitualWardParityTest {
    private static final Path RITUALS = Path.of("src", "main", "resources", "data", "warlockery", "ritual");
    private static final Map<RitualWardType, String> IDS = Map.of(
        RitualWardType.IMPRISONMENT, "imprisonment",
        RitualWardType.PROTECTION, "barrier",
        RitualWardType.SANCTITY, "sanctity"
    );

    @TestFactory
    Stream<DynamicContainer> everyWardHasFailureDiagnosticAndSuccessCoverage() {
        return IDS.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
            entry.getKey().id(),
            List.of(
                DynamicTest.dynamicTest("failure", () -> {
                    assertFalse(RitualWardRules.shouldRepel(false, true, true, false));
                    assertFalse(RitualWardRules.shouldRepel(true, true, true, true));
                }),
                DynamicTest.dynamicTest("diagnostic", () -> {
                    final JsonObject ritual = ritual(entry.getValue());
                    assertEquals(entry.getKey().id() + "_ward", ritual.get("action").getAsString());
                    assertTrue(ritual.get("duration").getAsInt() > 0);
                }),
                DynamicTest.dynamicTest("success", () -> {
                    assertTrue(RitualWardRules.shouldRepel(true, true, true, false));
                    assertTrue(RitualWardRules.contains(Vec3.ZERO, 6, new Vec3(5.9, 0.0, 0.0)));
                })
            )
        ));
    }

    @Test
    void boundaryCorrectionPushesTowardTheProvidedCenter() {
        final Vec3 corrected = RitualWardRules.inwardVelocity(
            Vec3.ZERO,
            new Vec3(6.0, 0.0, 0.0),
            new Vec3(1.0, 0.0, 0.0)
        );
        assertTrue(corrected.x < 0.0);
        final Vec3 repelled = RitualWardRules.outwardVelocity(
            Vec3.ZERO,
            new Vec3(6.0, 0.0, 0.0),
            Vec3.ZERO
        );
        assertTrue(repelled.x > 0.0);
    }

    @Test
    void invalidRecoveryCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new PriorIncarnationRuntime.RecoveryReport(-1, 0)
        );
    }

    @Test
    void biomeAndPriorRitesUseDedicatedActions() {
        assertEquals("climate_shift", ritual("climate_change").get("action").getAsString());
        assertEquals("prior_incarnation", ritual("prior_incarnation").get("action").getAsString());
    }

    private static JsonObject ritual(final String id) {
        final Path path = RITUALS.resolve(id + ".json");
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
