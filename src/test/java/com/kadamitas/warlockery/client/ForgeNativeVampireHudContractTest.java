package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ForgeNativeVampireHudContractTest {
    private static final Path CLIENT_SOURCE = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "client"
    );

    @Test
    void bloodHudUsesTheSupportedForgeLayerAndNoClientMixin() {
        assertFalse(Files.exists(CLIENT_SOURCE.resolve("mixin/HudFoodMixin.java")));
        assertFalse(Files.exists(Path.of("src", "main", "resources", "warlockery.client.mixins.json")));

        final String client = read(CLIENT_SOURCE.resolve("WarlockeryClient.java"));
        assertTrue(client.contains("addAbove("));
        assertTrue(client.contains("ForgeLayeredDraw.HOTBAR_AND_DECOS"));
        assertTrue(client.contains("ForgeLayeredDraw.HEALTH_BAR"));
        assertTrue(client.contains("VampireBloodHud.LAYER"));
        assertTrue(client.contains("VampireBloodHud::extract"));

        final String build = read(Path.of("build.gradle"));
        assertFalse(build.contains("warlockery.client.mixins.json"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
