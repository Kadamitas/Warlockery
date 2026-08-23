package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FabricNativeVampireHudContractTest {
    private static final Path CLIENT_SOURCE = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "client"
    );

    @Test
    void foodHudUsesTheFabricRegistryReplacementWithoutAHudMixin() {
        final String client = read(CLIENT_SOURCE.resolve("WarlockeryClient.java"));
        assertTrue(client.contains("HudElementRegistry.replaceElement("));
        assertTrue(client.contains("VanillaHudElements.FOOD_BAR"));
        assertTrue(client.contains("vanillaFood.extractRenderState(graphics, deltaTracker)"));
        assertFalse(Files.exists(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "mixin", "client", "HudFoodMixin.java"
        )));

        final JsonObject mixins = JsonParser.parseString(read(Path.of(
            "src", "main", "resources", "warlockery.mixins.json"
        ))).getAsJsonObject();
        assertEquals(7, array(mixins, "mixins").size());
        assertEquals(4, array(mixins, "client").size());
        assertTrue(array(mixins, "client").asList().stream()
            .noneMatch(element -> element.getAsString().toLowerCase(java.util.Locale.ROOT).contains("hud")));
    }

    @Test
    void replacementDelegatesUnlessTheLocalVampireOwnsTheFoodLane() {
        final String hud = read(CLIENT_SOURCE.resolve("VampireBloodHud.java"));
        final String model = read(CLIENT_SOURCE.resolve("VampireBloodHudModel.java"));
        assertTrue(hud.contains("ClientSupernaturalState.isVampire()"));
        assertTrue(hud.contains("minecraft.gameMode.canHurtPlayer()"));
        assertTrue(hud.contains("vehicleHealthTakesOver(minecraft.player)"));
        assertTrue(hud.contains("0xFF080A0F"), "the replacement needs an opaque backing");
        assertTrue(hud.contains("0xFFA10D31"), "the replacement needs an opaque fill");
        assertTrue(model.contains("0xFFF6DCE3"), "status text must remain opaque");
    }

    private static JsonArray array(final JsonObject object, final String key) {
        return object.getAsJsonArray(key);
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
