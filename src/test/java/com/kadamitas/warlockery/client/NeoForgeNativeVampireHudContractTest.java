package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NeoForgeNativeVampireHudContractTest {
    private static final Path CLIENT_SOURCE = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "client"
    );

    @Test
    void bloodHudUsesTheSupportedNeoForgeLayerAndNoClientMixin() {
        assertFalse(Files.exists(CLIENT_SOURCE.resolve("mixin/HudFoodMixin.java")));
        assertFalse(Files.exists(Path.of("src", "main", "resources", "warlockery.client.mixins.json")));

        final String client = read(CLIENT_SOURCE.resolve("WarlockeryClient.java"));
        assertTrue(client.contains("registerAbove("));
        assertTrue(client.contains("VanillaGuiLayers.FOOD_LEVEL"));
        assertTrue(client.contains("VampireBloodHud.LAYER"));
        assertTrue(client.contains("VampireBloodHud::extract"));
        assertTrue(client.contains("MovementInputUpdateEvent"));
        assertTrue(client.contains("ClientSupernaturalState.clear()"));
        assertTrue(client.contains("SupernaturalStatusOverlay.clear()"));

        final String build = read(Path.of("build.gradle")).toLowerCase(java.util.Locale.ROOT);
        assertFalse(build.contains("mixin"));
        assertFalse(build.contains("accesstransformer"));
        assertFalse(build.contains("coremod"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
