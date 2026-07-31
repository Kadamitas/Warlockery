package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class CreatureConceptImplementationTest {
    private static final Path ENTITY_TEXTURES = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Path CONCEPTS = Path.of("docs/art-source/creature-concepts");
    private static final Path ENTITY_LOOT = Path.of("src/main/resources/data/warlockery/loot_table/entities");
    private static final Set<String> CONCEPT_SHEETS = Set.of(
        "familiars-and-vermin.png",
        "illusions-and-anomalies.png",
        "infernal-and-bosses.png",
        "goblin-clans.png",
        "lycans-and-mounts.png",
        "nami_naamah_turnaround.png",
        "occult-humanoids.png",
        "spectral-entities.png",
        "verdant-creatures.png"
    );

    @Test
    void everyRegisteredCreatureHasAnExactModelProfile() throws IOException {
        final Set<String> profileIds = Arrays.stream(CreatureModelProfile.Variant.values())
            .map(CreatureModelProfile.Variant::id)
            .collect(Collectors.toUnmodifiableSet());
        final Set<String> registeredIds = registeredCreatureIds();
        assertEquals(registeredIds, profileIds);
        registeredIds.forEach(id -> {
            final CreatureVisualProfile visual = new CreatureVisualProfile(
                0.8F,
                1.8F,
                CreatureVisualProfile.Archetype.HUMANOID
            );
            final CreatureModelProfile profile = CreatureModelProfile.forEntity(id, visual);
            final ModelPart root = ArcaneCreatureModel.createLayer(profile).bakeRoot();
            assertFalse(root.getChild("head").isEmpty(), id + " head");
            assertFalse(root.getChild("body").isEmpty(), id + " body");
            assertTrue(root.getAllParts().stream().filter(part -> !part.isEmpty()).count() >= 7, id + " geometry");
        });
    }

    @Test
    void everyCreatureTextureIsPixelSizedAndOriginal() throws Exception {
        final Set<String> registeredIds = registeredCreatureIds();
        final Set<String> hashes = registeredIds.stream()
            .map(id -> ENTITY_TEXTURES.resolve(id + ".png"))
            .peek(path -> assertTrue(Files.isRegularFile(path), path.toString()))
            .map(CreatureConceptImplementationTest::inspectTexture)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(registeredIds.size(), hashes.size());
    }

    @Test
    void everyGeneratedConceptSheetIsSavedWithProductionResolution() throws IOException {
        try (Stream<Path> files = Files.list(CONCEPTS)) {
            assertEquals(CONCEPT_SHEETS, files
                .filter(path -> path.getFileName().toString().endsWith(".png"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toUnmodifiableSet()));
        }
        for (final String fileName : CONCEPT_SHEETS) {
            final BufferedImage image = ImageIO.read(CONCEPTS.resolve(fileName).toFile());
            assertTrue(image.getWidth() >= 1024, fileName + " width");
            assertTrue(image.getHeight() >= 768, fileName + " height");
        }
    }

    @Test
    void customCreatureRenderingDoesNotReuseVanillaMobModels() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/TexturedCreatureRenderers.java"
        ));
        assertFalse(source.contains("VexRenderer"));
        assertFalse(source.contains("VillagerRenderer"));
        assertFalse(source.contains("PillagerRenderer"));
        assertFalse(source.contains("IronGolemRenderer"));
        assertTrue(source.contains("Nami extends SkinnedHumanoid<NamiEntity>"));
        assertTrue(source.contains("Naamah extends SkinnedHumanoid<NaamahEntity>"));
        assertTrue(source.contains("super(context, \"nami\""));
        assertTrue(source.contains("super(context, \"naamah\""));
    }

    @Test
    void namiAndNaamahUseDedicatedMobRenderStates() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/TexturedCreatureRenderers.java"
        ));
        assertFalse(source.contains("AvatarRenderState"));
        assertFalse(source.contains("PlayerModel"));
        assertTrue(source.contains("HumanoidRenderState"));
        assertTrue(source.contains("HumanoidModel"));
        assertTrue(source.contains("context.bakeLayer(ModelLayers.PLAYER_SLIM)"));
    }

    @Test
    void namiAndNaamahUseCompleteCustomHumanoidSkins() throws IOException {
        assertCompleteHumanoidSkin("nami");
        assertCompleteHumanoidSkin("naamah");
        final BufferedImage nami = ImageIO.read(ENTITY_TEXTURES.resolve("nami.png").toFile());
        final BufferedImage naamah = ImageIO.read(ENTITY_TEXTURES.resolve("naamah.png").toFile());
        assertNotEquals(faceSignature(nami), faceSignature(naamah));
        assertNotEquals(nami.getRGB(10, 12), naamah.getRGB(10, 12));
    }

    @Test
    void namiAndNaamahHaveDedicatedStableGeneratorPaths() throws IOException {
        final String source = Files.readString(Path.of("tools/GenerateOriginalAssets.java"));
        assertTrue(source.contains("return namiSkin();"));
        assertTrue(source.contains("return naamahSkin();"));
        assertTrue(source.contains("--nami-naamah"));
        assertTrue(source.contains("ImageIO.write(namiSkin()"));
        assertTrue(source.contains("ImageIO.write(naamahSkin()"));
    }

    private static void assertCompleteHumanoidSkin(final String entityId) throws IOException {
        final BufferedImage skin = ImageIO.read(ENTITY_TEXTURES.resolve(entityId + ".png").toFile());
        assertEquals(64, skin.getWidth());
        assertEquals(64, skin.getHeight());
        assertTrue(skin.getColorModel().hasAlpha());
        assertEquals(4, skin.getColorModel().getNumComponents());
        assertBinaryAlpha(skin, entityId);
        assertOpaqueCuboid(skin, entityId, "head", 0, 0, 8, 8, 8);
        assertOpaqueCuboid(skin, entityId, "body", 16, 16, 8, 12, 4);
        assertOpaqueCuboid(skin, entityId, "right arm", 40, 16, 3, 12, 4);
        assertOpaqueCuboid(skin, entityId, "right leg", 0, 16, 4, 12, 4);
        assertOpaqueCuboid(skin, entityId, "left arm", 32, 48, 3, 12, 4);
        assertOpaqueCuboid(skin, entityId, "left leg", 16, 48, 4, 12, 4);
        assertOverlayTransparency(skin, entityId);
        assertTransparent(skin, entityId, 0, 0);
        assertTransparent(skin, entityId, 24, 0);
        assertTransparent(skin, entityId, 32, 0);
        assertTransparent(skin, entityId, 56, 0);
        assertTransparent(skin, entityId, 63, 31);
        assertTransparent(skin, entityId, 63, 47);
        assertTransparent(skin, entityId, 62, 63);
        assertOpaque(skin, entityId, "face", 10, 11);
        assertOpaque(skin, entityId, "torso", 22, 24);
        assertOpaque(skin, entityId, "right arm", 45, 22);
        assertOpaque(skin, entityId, "right leg", 5, 22);
        assertOpaque(skin, entityId, "left arm", 37, 54);
        assertOpaque(skin, entityId, "left leg", 21, 54);
        assertOpaque(skin, entityId, "jacket overlay", 20, 36);
        assertFalse(skin.getRGB(10, 11) == 0xFFB87A5B, entityId + " must not contain Steve's face palette");
        assertEquals(skin.getRGB(9, 11), skin.getRGB(13, 11), entityId + " paired eye line");
        assertTrue(colorDistance(skin.getRGB(10, 12), skin.getRGB(11, 12)) > 70, entityId + " readable irises");
    }

    private static void assertOpaqueCuboid(
        final BufferedImage skin,
        final String entityId,
        final String region,
        final int u,
        final int v,
        final int width,
        final int height,
        final int depth
    ) {
        assertOpaqueRectangle(skin, entityId, region + " top", u + depth, v, width, depth);
        assertOpaqueRectangle(skin, entityId, region + " bottom", u + depth + width, v, width, depth);
        assertOpaqueRectangle(skin, entityId, region + " right", u, v + depth, depth, height);
        assertOpaqueRectangle(skin, entityId, region + " front", u + depth, v + depth, width, height);
        assertOpaqueRectangle(skin, entityId, region + " left", u + depth + width, v + depth, depth, height);
        assertOpaqueRectangle(skin, entityId, region + " back", u + depth * 2 + width, v + depth, width, height);
    }

    private static void assertOpaqueRectangle(
        final BufferedImage skin,
        final String entityId,
        final String region,
        final int x,
        final int y,
        final int width,
        final int height
    ) {
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                assertOpaque(skin, entityId, region, px, py);
            }
        }
    }

    private static void assertOverlayTransparency(final BufferedImage skin, final String entityId) {
        int opaque = 0;
        int transparent = 0;
        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                if (isBaseSkinPixel(x, y)) {
                    continue;
                }
                if ((skin.getRGB(x, y) >>> 24) == 0) {
                    transparent++;
                } else {
                    opaque++;
                }
            }
        }
        assertTrue(opaque > 200, entityId + " overlays must contain garment and hair pixels");
        assertTrue(transparent > 500, entityId + " overlays must preserve transparent pixels");
    }

    private static boolean isBaseSkinPixel(final int x, final int y) {
        return inCuboid(x, y, 0, 0, 8, 8, 8)
            || inCuboid(x, y, 16, 16, 8, 12, 4)
            || inCuboid(x, y, 40, 16, 3, 12, 4)
            || inCuboid(x, y, 0, 16, 4, 12, 4)
            || inCuboid(x, y, 32, 48, 3, 12, 4)
            || inCuboid(x, y, 16, 48, 4, 12, 4);
    }

    private static boolean inCuboid(
        final int x,
        final int y,
        final int u,
        final int v,
        final int width,
        final int height,
        final int depth
    ) {
        return inRectangle(x, y, u + depth, v, width, depth)
            || inRectangle(x, y, u + depth + width, v, width, depth)
            || inRectangle(x, y, u, v + depth, depth, height)
            || inRectangle(x, y, u + depth, v + depth, width, height)
            || inRectangle(x, y, u + depth + width, v + depth, depth, height)
            || inRectangle(x, y, u + depth * 2 + width, v + depth, width, height);
    }

    private static boolean inRectangle(
        final int x,
        final int y,
        final int left,
        final int top,
        final int width,
        final int height
    ) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static void assertBinaryAlpha(final BufferedImage skin, final String entityId) {
        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                final int alpha = skin.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, entityId + " alpha at " + x + "," + y);
            }
        }
    }

    private static void assertTransparent(
        final BufferedImage skin,
        final String entityId,
        final int x,
        final int y
    ) {
        assertEquals(0, skin.getRGB(x, y) >>> 24, entityId + " unused UV at " + x + "," + y);
    }

    private static int colorDistance(final int first, final int second) {
        final int red = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        final int green = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        final int blue = (first & 0xFF) - (second & 0xFF);
        return Math.abs(red) + Math.abs(green) + Math.abs(blue);
    }

    private static String faceSignature(final BufferedImage skin) {
        final StringBuilder signature = new StringBuilder();
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                signature.append(Integer.toHexString(skin.getRGB(x, y)));
            }
        }
        return signature.toString();
    }

    private static void assertOpaque(
        final BufferedImage skin,
        final String entityId,
        final String region,
        final int x,
        final int y
    ) {
        assertTrue((skin.getRGB(x, y) >>> 24) != 0, entityId + " " + region + " must be painted");
    }

    @Test
    void impAndStormSimianHaveArticulatedSilhouettes() {
        final ModelPart imp = modelFor("imp");
        assertFalse(imp.getChild("right_wing").getChild("right_wing_finger").isEmpty());
        assertFalse(imp.getChild("left_wing").getChild("left_wing_finger").isEmpty());
        final ModelPart simian = modelFor("storm_simian");
        assertFalse(simian.getChild("right_arm").getChild("right_hand").isEmpty());
        assertFalse(simian.getChild("left_arm").getChild("left_hand").isEmpty());
        assertFalse(simian.getChild("right_wing").getChild("right_primary_feathers").isEmpty());
        assertFalse(simian.getChild("left_wing").getChild("left_primary_feathers").isEmpty());
    }

    private static String inspectTexture(final Path path) {
        try {
            final BufferedImage image = ImageIO.read(path.toFile());
            assertEquals(64, image.getWidth(), path + " width");
            assertEquals(64, image.getHeight(), path + " height");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (final IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static Set<String> registeredCreatureIds() throws IOException {
        try (Stream<Path> files = Files.list(ENTITY_LOOT)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static ModelPart modelFor(final String id) {
        final CreatureVisualProfile visual = new CreatureVisualProfile(
            0.8F,
            1.8F,
            CreatureVisualProfile.Archetype.HUMANOID
        );
        return ArcaneCreatureModel.createLayer(CreatureModelProfile.forEntity(id, visual)).bakeRoot();
    }
}
