package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import com.kadamitas.warlockery.entity.VampireCourtEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

final class VampireModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/VampireModel.java"
    );
    private static final Path MASCULINE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/vampire_masculine.png"
    );
    private static final Path FEMININE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/vampire_feminine.png"
    );

    @Test
    void ownsDistinctMasculineAndFeminineAbyssalCourtSilhouettes() throws Exception {
        final ModelPart root = VampireModel.createBodyLayer().bakeRoot();
        final ModelPart body = requiredChild(root, "body");
        assertFalse(requiredChild(body, "pearl_brooch").isEmpty());
        for (final String part : java.util.List.of(
            "head", "right_arm", "left_arm", "right_leg", "left_leg"
        )) {
            assertFalse(requiredChild(root, part).isEmpty(), part);
        }
        final ModelPart masculine = requiredChild(root, "masculine_variant");
        final ModelPart feminine = requiredChild(root, "feminine_variant");
        for (final String part : java.util.List.of("short_hair", "coat_collar", "coat_tail")) {
            assertFalse(requiredChild(masculine, part).isEmpty(), part);
        }
        for (final String part : java.util.List.of(
            "long_hair_cap", "back_hair", "right_hair_lock", "left_hair_lock", "dress_skirt"
        )) {
            assertFalse(requiredChild(feminine, part).isEmpty(), part);
        }
        CreatureModelTestSupport.assertUvsWithin(
            root, VampireModel.TEXTURE_WIDTH, VampireModel.TEXTURE_HEIGHT
        );
    }

    @Test
    void feminineOceanicVariantIsVisiblyDistinctWhileBothCourtFormsRemainLean() {
        final CreatureModelTestSupport.Bounds masculine = boundsForVariant(VampireModel.Variant.MASCULINE);
        final CreatureModelTestSupport.Bounds feminine = boundsForVariant(VampireModel.Variant.FEMININE);
        final float masculineHeight = masculine.maxY() - masculine.minY();
        final float feminineHeight = feminine.maxY() - feminine.minY();
        final float masculineWidth = masculine.maxX() - masculine.minX();
        final float feminineWidth = feminine.maxX() - feminine.minX();
        final float masculineDepth = masculine.maxZ() - masculine.minZ();
        final float feminineDepth = feminine.maxZ() - feminine.minZ();
        assertTrue(masculineWidth / masculineHeight >= 0.42F
                && masculineWidth / masculineHeight <= 0.60F,
            "masculine pressure-jacket variant must remain tall and lean");
        assertTrue(feminineWidth / feminineHeight >= 0.45F
                && feminineWidth / feminineHeight <= 0.55F,
            "feminine court variant must retain the shared tall, lean chassis");
        assertEquals(masculineWidth, feminineWidth, 0.001F,
            "variant clothing must not change the shared arm span");
        assertTrue(feminineDepth >= masculineDepth * 1.08F,
            "back hair and side locks must distinguish the feminine profile");
        assertTrue(feminineDepth / feminineHeight >= 0.27F
                && feminineDepth / feminineHeight <= 0.31F,
            "the feminine profile must remain lean while retaining its authored hair depth");
    }

    @Test
    void uuidSelectionIsDeterministicStableAndReachesBothVariants() {
        final UUID masculineId = new UUID(0L, 0L);
        final UUID feminineId = new UUID(0L, 1L);
        assertEquals(VampireModel.Variant.MASCULINE, VampireModel.variantFor(masculineId));
        assertEquals(VampireModel.Variant.FEMININE, VampireModel.variantFor(feminineId));
        assertSame(VampireModel.variantFor(masculineId), VampireModel.variantFor(masculineId));
        assertSame(VampireModel.variantFor(feminineId), VampireModel.variantFor(feminineId));
    }

    @Test
    void variantControlsGeometryVisibilityAndDedicatedTextureLocation() {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final VampireModel.State state = new VampireModel.State();
        state.variant = VampireModel.Variant.MASCULINE;
        model.setupAnim(state);
        final String masculineGeometry = geometrySnapshot(
            requiredChild(model.root(), "masculine_variant")
        );
        assertTrue(requiredChild(model.root(), "masculine_variant").visible);
        assertFalse(requiredChild(model.root(), "feminine_variant").visible);
        state.variant = VampireModel.Variant.FEMININE;
        model.setupAnim(state);
        assertFalse(requiredChild(model.root(), "masculine_variant").visible);
        assertTrue(requiredChild(model.root(), "feminine_variant").visible);
        final String feminineGeometry = geometrySnapshot(
            requiredChild(model.root(), "feminine_variant")
        );
        assertNotEquals(masculineGeometry, feminineGeometry);
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
    void bothVariantAtlasesHaveIndependentRecordedHashes() throws Exception {
        assertEquals(VampireModel.TEXTURE_WIDTH, ImageIO.read(MASCULINE.toFile()).getWidth());
        assertEquals(VampireModel.TEXTURE_HEIGHT, ImageIO.read(MASCULINE.toFile()).getHeight());
        assertEquals(VampireModel.TEXTURE_WIDTH, ImageIO.read(FEMININE.toFile()).getWidth());
        assertEquals(VampireModel.TEXTURE_HEIGHT, ImageIO.read(FEMININE.toFile()).getHeight());
        final String masculineHash = sha256(MASCULINE);
        final String feminineHash = sha256(FEMININE);
        assertNotEquals(masculineHash, feminineHash);
        assertEquals("e46c54ca36e95e4c39bf24ea0dc3b10905cb35302616d1b7b51b66463a580be0",
            masculineHash);
        assertEquals("f86fe37d8f003592dac1686af816128f3e3325d4cb7a5d7d843974813a8e6b40",
            feminineHash);
        final ModelPart root = VampireModel.createBodyLayer().bakeRoot();
        CreatureModelTestSupport.assertOpaqueUvs(
            root,
            ImageIO.read(MASCULINE.toFile()),
            cube -> !cube.path().contains("/feminine_variant/")
        );
        CreatureModelTestSupport.assertOpaqueUvs(
            root,
            ImageIO.read(FEMININE.toFile()),
            cube -> !cube.path().contains("/masculine_variant/")
        );
    }

    @Test
    void sourceContainsNoPlayerModelSharedRigOrThrallReuse() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : java.util.List.of(
            "PlayerModel", "HumanoidModel<", "ArcaneCreatureModel", "CreatureModelProfile",
            "AnimationHelper", "GeometryHelper", "ModelHelper", "BloodThrallModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void exposesConcreteUuidVariantExtractor() {
        assertDoesNotThrow(() -> VampireModel.class.getDeclaredMethod(
            "extractRenderState", VampireCourtEntity.class, VampireModel.State.class, float.class
        ));
    }

    @Test
    void writesTwoRowMasculineAndFeminineSoftwareContactSheet() throws Exception {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 320, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(17, 34, 43));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        final VampireModel.Variant[] variants = {
            VampireModel.Variant.MASCULINE, VampireModel.Variant.FEMININE
        };
        for (int row = 0; row < variants.length; row++) {
            for (int index = 0; index < turns.length; index++) {
                final VampireModel.State state = new VampireModel.State();
                state.variant = variants[row];
                model.setupAnim(state);
                applySoftwareVariantVisibility(model, variants[row]);
                model.root().yRot = turns[index];
                graphics.drawImage(softwareSnapshot(
                    model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
                ), index * 128, row * 160, null);
            }
            final VampireModel.State action = new VampireModel.State();
            action.variant = variants[row];
            action.activity = VampireModel.Activity.ASSAULT_LEAD;
            action.ageInTicks = 33.0F;
            model.setupAnim(action);
            applySoftwareVariantVisibility(model, variants[row]);
            graphics.drawImage(softwareSnapshot(
                model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
            ), 640, row * 160, null);
        }
        graphics.dispose();
        assertNotEquals(
            imageSnapshot(sheet.getSubimage(0, 0, 768, 160)),
            imageSnapshot(sheet.getSubimage(0, 160, 768, 160)),
            "contact-sheet rows must preserve the selected Vampire silhouette"
        );
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/vampire-software-contact-sheet.png"
        );
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }

    private static void applySoftwareVariantVisibility(
        final VampireModel model,
        final VampireModel.Variant variant
    ) {
        final ModelPart hidden = requiredChild(
            model.root(),
            variant == VampireModel.Variant.MASCULINE ? "feminine_variant" : "masculine_variant"
        );
        hidden.xScale = 0.0F;
        hidden.yScale = 0.0F;
        hidden.zScale = 0.0F;
    }

    private static CreatureModelTestSupport.Bounds boundsForVariant(final VampireModel.Variant variant) {
        final VampireModel model = new VampireModel(VampireModel.createBodyLayer().bakeRoot());
        final VampireModel.State state = new VampireModel.State();
        state.variant = variant;
        model.setupAnim(state);
        applySoftwareVariantVisibility(model, variant);
        return CreatureModelTestSupport.bounds(model.root());
    }

    private static String sha256(final Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
