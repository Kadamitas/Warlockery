package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The vampire court is a plain player-skin rig wearing two authored 64x64 court skins. */
final class VampireModelTest {
    private static final Path MASCULINE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/vampire_masculine.png"
    );
    private static final Path FEMININE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/vampire_feminine.png"
    );
    private static final List<String> BASE_PARTS = List.of(
        "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"
    );

    @Test
    void usesTheStandardPlayerSkinRigWithEveryOuterLayer() {
        final ModelPart root = VampireModel.createBodyLayer().bakeRoot();
        for (final String part : BASE_PARTS) {
            assertFalse(requiredChild(root, part).isEmpty(), part);
        }
        assertFalse(requiredChild(requiredChild(root, "head"), "hat").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "body"), "jacket").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_arm"), "right_sleeve").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_arm"), "left_sleeve").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_leg"), "right_pants").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_leg"), "left_pants").isEmpty());
        assertEquals(64, VampireModel.TEXTURE_WIDTH);
        assertEquals(64, VampireModel.TEXTURE_HEIGHT);
        CreatureModelTestSupport.assertUvsWithin(root, VampireModel.TEXTURE_WIDTH, VampireModel.TEXTURE_HEIGHT);
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        assertTrue(height >= 31.5F && height <= 33.5F, "player height: " + height);
        assertTrue((bounds.maxX() - bounds.minX()) / height <= 0.6F, "player-width silhouette");
    }

    @Test
    void uuidSelectionIsDeterministicStableAndReachesBothVariants() {
        final UUID masculineId = new UUID(7L, 2L);
        final UUID feminineId = new UUID(7L, 3L);
        assertEquals(VampireModel.Variant.MASCULINE, VampireModel.variantFor(masculineId));
        assertEquals(VampireModel.Variant.FEMININE, VampireModel.variantFor(feminineId));
        assertSame(VampireModel.variantFor(masculineId), VampireModel.variantFor(masculineId));
        assertSame(VampireModel.variantFor(feminineId), VampireModel.variantFor(feminineId));
    }

    @Test
    void variantSelectsTheDedicatedSkinWhileGeometryStaysShared() {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final VampireModel.State state = new VampireModel.State();
        state.variant = VampireModel.Variant.MASCULINE;
        model.setupAnim(state);
        final String masculine = geometrySnapshot(model.root());
        state.variant = VampireModel.Variant.FEMININE;
        model.setupAnim(state);
        assertEquals(masculine, geometrySnapshot(model.root()), "the skin, not the rig, distinguishes the court");
        assertEquals(
            Identifier.fromNamespaceAndPath("warlockery", "textures/entity/vampire_masculine.png"),
            VampireModel.textureFor(VampireModel.Variant.MASCULINE)
        );
        assertEquals(
            Identifier.fromNamespaceAndPath("warlockery", "textures/entity/vampire_feminine.png"),
            VampireModel.textureFor(VampireModel.Variant.FEMININE)
        );
    }

    @Test
    void activitiesChangeTheOwnedGeometry() {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final VampireModel.State state = new VampireModel.State();
        model.setupAnim(state);
        final String roosting = geometrySnapshot(model.root());
        state.activity = VampireModel.Activity.FEEDING;
        model.setupAnim(state);
        final String feeding = geometrySnapshot(model.root());
        assertNotEquals(roosting, feeding);
        state.activity = VampireModel.Activity.ASSAULT_LEAD;
        model.setupAnim(state);
        assertNotEquals(feeding, geometrySnapshot(model.root()));
    }

    @Test
    void bothCourtSkinsAreAuthored64x64AtlasesWithOpaqueBaseLayers() throws Exception {
        for (final Path atlas : List.of(MASCULINE, FEMININE)) {
            assertEquals(64, ImageIO.read(atlas.toFile()).getWidth(), atlas.toString());
            assertEquals(64, ImageIO.read(atlas.toFile()).getHeight(), atlas.toString());
        }
        assertNotEquals(sha256(MASCULINE), sha256(FEMININE));
        assertEquals("cbc3e2c71412914d029bc3b499b9212981fbb6e9614eb7d5961fc10b45731987", sha256(MASCULINE));
        assertEquals("1a1b3a9e37c879d4ce17e180c716e1a9469729caf4cf2bfe6ad61835c73c1642", sha256(FEMININE));
        final ModelPart root = VampireModel.createBodyLayer().bakeRoot();
        for (final Path atlas : List.of(MASCULINE, FEMININE)) {
            // skin outer layers are legitimately transparent; the base layer must be fully painted
            CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(atlas.toFile()), cube -> BASE_PARTS.stream()
                .anyMatch(part -> cube.path().endsWith("/" + part)));
        }
    }

    private static String sha256(final Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
