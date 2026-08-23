package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FabricNativeDedicatedCreatureRendererTest {
    @Test
    void subclassOwnsTheNarrowLayerBridgeWithoutAMixinOrAccessor() throws Exception {
        final String renderer = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderer.java"
        ));
        final String registrations = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
        ));

        assertTrue(renderer.contains("addPresentationLayer(final RenderLayer<S, M> layer)"));
        assertTrue(renderer.contains("addLayer(layer)"));
        assertEqualsTwo(registrations, ".addPresentationLayer(new NativeVillagerClothingLayer<>(");
        assertFalse(renderer.toLowerCase(java.util.Locale.ROOT).contains("mixin"));
        assertFalse(registrations.toLowerCase(java.util.Locale.ROOT).contains("mixin"));
    }

    private static void assertEqualsTwo(final String source, final String needle) {
        assertTrue(source.indexOf(needle) >= 0);
        assertTrue(source.indexOf(needle, source.indexOf(needle) + needle.length()) >= 0);
        assertTrue(source.indexOf(needle,
            source.indexOf(needle, source.indexOf(needle) + needle.length()) + needle.length()) < 0);
    }
}
