package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DedicatedCreatureRendererTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderer.java"
    );
    private static final Path COMMON_TOOLS = Path.of("tools/creature_models/common.ps1");

    @Test
    void rendererContractIsTypedAndGeometryFree() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("ModelFactory"));
        assertTrue(source.contains("StateFactory"));
        assertTrue(source.contains("TextureSelector"));
        assertTrue(source.contains("StateExtractor"));
        assertTrue(source.contains("TintSelector"));
        assertTrue(source.contains("ScaleTransform"));
        assertTrue(source.contains("ShadowRadiusSelector"));
        assertTrue(source.contains("createWithItemLayer"));
        for (final String forbidden : List.of(
            "ModelPart", "CubeListBuilder", "PartPose", "LayerDefinition", "MeshDefinition",
            "CreatureModelProfile", "ArcaneCreatureModel", "mandrake", "dreamroot", "\"ent\"", "\"imp\"",
            "\"head\"", "\"body\"", "textures/entity/"
        )) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains(forbidden.toLowerCase(java.util.Locale.ROOT)),
                forbidden);
        }
    }

    @Test
    void commonAtlasToolsContainOnlyGenericPixelPrimitives() throws Exception {
        final String source = Files.readString(COMMON_TOOLS);
        for (final String function : List.of(
            "New-PixelAtlas", "Test-AtlasRectangle", "Set-AtlasPixel",
            "Set-AtlasRectangle", "Copy-AtlasRectangle", "Save-PixelAtlas"
        )) {
            assertTrue(source.contains("function " + function), function);
        }
        for (final String forbidden : List.of(
            "mandrake", "dreamroot", "\"ent\"", "\"imp\"", "species", "palette", "bone", "pivot"
        )) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains(forbidden), forbidden);
        }
    }
}
