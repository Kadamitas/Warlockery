package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.bounds;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.solidPartCount;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CircleMageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

/** The Circle Mage is a player-proportioned robed scholar with a modeled wizard hat. */
final class CircleMageModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/CircleMageModel.java"
    );
    private static final Path RENDERERS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/circle_mage.png"
    );
    private static final Path OCCULT_GENERATOR = Path.of(
        "tools/creature_models/generate_occult_humanoids.ps1"
    );
    private static final Path SKIN_GENERATOR = Path.of(
        "tools/creature_models/generate_circle_mage_skin.py"
    );
    /** Skin outer layers are legitimately transparent where the skin has no second layer. */
    private static final Set<String> OUTER_LAYERS = Set.of(
        "hood", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants"
    );
    private static final float EPSILON = 0.001F;

    @Test
    void ownsPlayerProportionedHumanoidPartsOnAStandardSkinLayout() {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        // Left 64x64 is the standard skin layout; the right half carries the modeled hat islands.
        assertEquals(128, CircleMageModel.TEXTURE_WIDTH);
        assertEquals(64, CircleMageModel.TEXTURE_HEIGHT);

        assertBoxSize(requiredChild(root, "head"), 8.0F, 8.0F, 8.0F, "head");
        assertBoxSize(requiredChild(root, "body"), 8.0F, 12.0F, 4.0F, "body");
        assertBoxSize(requiredChild(root, "right_arm"), 4.0F, 12.0F, 4.0F, "right_arm");
        assertBoxSize(requiredChild(root, "left_arm"), 4.0F, 12.0F, 4.0F, "left_arm");
        assertBoxSize(requiredChild(root, "right_leg"), 4.0F, 12.0F, 4.0F, "right_leg");
        assertBoxSize(requiredChild(root, "left_leg"), 4.0F, 12.0F, 4.0F, "left_leg");

        final CreatureModelTestSupport.Bounds legs = bounds(requiredChild(root, "left_leg"));
        assertEquals(24.25F, legs.maxY(), EPSILON, "feet (with the pants overlay) stand on the ground");
        final CreatureModelTestSupport.Bounds head = bounds(requiredChild(root, "head"));
        assertTrue(head.minY() < -20.0F, "the hat must rise well above the 8-pixel head: " + head.minY());
        CreatureModelTestSupport.assertUvsWithin(
            root, CircleMageModel.TEXTURE_WIDTH, CircleMageModel.TEXTURE_HEIGHT
        );
    }

    @Test
    void wearsEverySkinOuterLayerSoTheAtlasRendersAsASkinEditorShowsIt() {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        assertBoxSize(requiredChild(requiredChild(root, "head"), "hood"), 9.0F, 9.0F, 9.0F, "hood");
        assertBoxSize(requiredChild(requiredChild(root, "body"), "jacket"), 8.5F, 12.5F, 4.5F, "jacket");
        assertBoxSize(requiredChild(requiredChild(root, "right_arm"), "right_sleeve"),
            4.5F, 12.5F, 4.5F, "right_sleeve");
        assertBoxSize(requiredChild(requiredChild(root, "left_arm"), "left_sleeve"),
            4.5F, 12.5F, 4.5F, "left_sleeve");
        assertBoxSize(requiredChild(requiredChild(root, "right_leg"), "right_pants"),
            4.5F, 12.5F, 4.5F, "right_pants");
        assertBoxSize(requiredChild(requiredChild(root, "left_leg"), "left_pants"),
            4.5F, 12.5F, 4.5F, "left_pants");
    }

    @Test
    void wearsAModeledWideBrimHatThatTapersToADroopingTip() {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        final ModelPart brim = requiredChild(head, "hat_brim");
        final ModelPart cone1 = requiredChild(brim, "hat_cone_1");
        final ModelPart cone2 = requiredChild(cone1, "hat_cone_2");
        final ModelPart cone3 = requiredChild(cone2, "hat_cone_3");
        final ModelPart tip = requiredChild(cone3, "hat_tip");
        for (final ModelPart part : List.of(brim, cone1, cone2, cone3, tip)) {
            assertFalse(part.isEmpty());
        }
        final CreatureModelTestSupport.Bounds brimBounds = bounds(brim);
        assertTrue(brimBounds.maxX() - brimBounds.minX() >= 11.0F, "brim is wider than the head");
        final float cone1Width = width(cone1);
        final float cone2Width = width(cone2);
        final float cone3Width = width(cone3);
        assertTrue(cone1Width > cone2Width && cone2Width > cone3Width,
            "cone boxes must shrink upward: " + cone1Width + " > " + cone2Width + " > " + cone3Width);
        assertTrue(cone2.y < 0.0F && cone3.y < 0.0F && tip.y < 0.0F,
            "each cone box stacks above its parent so the tip caps the cone");
        assertTrue(tip.zRot > cone3.zRot && cone3.zRot > 0.0F, "the point droops sideways");
        assertTrue(solidPartCount(root) >= 17);
    }

    @Test
    void atlasIsAComposedSkinWithOpaqueBaseLayersAndAReadableFace() throws Exception {
        final ModelPart root = CircleMageModel.createBodyLayer().bakeRoot();
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(CircleMageModel.TEXTURE_WIDTH, texture.getWidth());
        assertEquals(CircleMageModel.TEXTURE_HEIGHT, texture.getHeight());
        // Base body islands and the modeled hat must be fully painted; outer layers may be cut out.
        CreatureModelTestSupport.assertOpaqueUvs(
            root, texture, cube -> OUTER_LAYERS.stream().noneMatch(cube.path()::endsWith)
        );
        // The face island (8..16, 8..16) must carry authored detail, not a flat fill.
        final Set<Integer> faceColors = new HashSet<>();
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                faceColors.add(texture.getRGB(x, y));
            }
        }
        assertTrue(faceColors.size() >= 3, "the face needs skin, eyes, and shading: " + faceColors.size());
    }

    @Test
    void presentationChangesTheOwnedGeometry() {
        final CircleMageModel model = new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot());
        final CircleMageModel.State state = new CircleMageModel.State();
        state.ageInTicks = 18.0F;
        model.setupAnim(state);
        final String neutral = geometrySnapshot(model.root());

        state.focusPrepared = true;
        model.setupAnim(state);
        final String bookOut = geometrySnapshot(model.root());
        assertNotEquals(neutral, bookOut, "the prepared book lifts the left arm");

        state.activity = CircleMageModel.Activity.STUDYING;
        model.setupAnim(state);
        final String studying = geometrySnapshot(model.root());
        assertNotEquals(bookOut, studying);

        state.activity = CircleMageModel.Activity.DEFENDING;
        state.ageInTicks = 27.0F;
        model.setupAnim(state);
        final String defending = geometrySnapshot(model.root());
        assertNotEquals(studying, defending);

        state.activity = CircleMageModel.Activity.WITHDRAWING;
        model.setupAnim(state);
        assertNotEquals(defending, geometrySnapshot(model.root()));

        state.activity = CircleMageModel.Activity.IDLE;
        state.focusPrepared = false;
        state.walkAnimationSpeed = 1.0F;
        state.walkAnimationPos = 3.0F;
        model.setupAnim(state);
        assertNotEquals(neutral, geometrySnapshot(model.root()), "walking swings the limbs");
    }

    @Test
    void translatesToEitherHandForTheHeldBook() {
        final CircleMageModel model = new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot());
        assertTrue(model instanceof ArmedModel<?>);
        final CircleMageModel.State state = new CircleMageModel.State();
        state.activity = CircleMageModel.Activity.STUDYING;
        model.setupAnim(state);

        final PoseStack left = new PoseStack();
        final String identity = matrixSnapshot(left);
        model.translateToHand(state, HumanoidArm.LEFT, left);
        assertNotEquals(identity, matrixSnapshot(left));

        final PoseStack right = new PoseStack();
        model.translateToHand(state, HumanoidArm.RIGHT, right);
        assertNotEquals(matrixSnapshot(left), matrixSnapshot(right));
    }

    @Test
    void rendererPutsTheCircleMagicBookInTheLeftHand() throws Exception {
        final String renderers = Files.readString(RENDERERS);
        assertTrue(renderers.contains("class CircleMageRenderer"));
        assertTrue(renderers.contains("\"ingredient_book_circle_magic\""));
        assertTrue(renderers.contains("ItemDisplayContext.THIRD_PERSON_LEFT_HAND"));
        assertTrue(renderers.contains("state.leftHandItemStack = book"));
    }

    @Test
    void sourceContainsNoSharedWarlockeryRigOrHelper() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "AnimationHelper", "GeometryHelper",
            "ModelHelper", "HedgeCroneModel", "VampireModel", "HumanoidModel<",
            "generic_book", "held_staff", "broom", "cape"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("extends EntityModel<"));
    }

    @Test
    void exposesConcreteSynchronizedPresentationExtractor() {
        assertDoesNotThrow(() -> CircleMageModel.class.getDeclaredMethod(
            "extractRenderState", CircleMageEntity.class, CircleMageModel.State.class, float.class
        ));
    }

    @Test
    void packageGeneratorOwnsOnlyTheOccultAtlases() throws Exception {
        final String generator = Files.readString(OCCULT_GENERATOR);
        for (final String id : List.of(
            "circle_mage", "hedge_crone", "blood_thrall", "corpse", "werewolf_hunter",
            "lycan_villager"
        )) {
            assertTrue(generator.contains(id), id);
        }
        for (final String forbidden : List.of(
            "banshee", "vampire_masculine.png", "vampire_feminine.png", "werewolf.png",
            "imp.png", "goblin.png", "mandrake.png", "circle_mage.png"
        )) {
            assertFalse(generator.contains(forbidden), forbidden);
        }
    }

    @Test
    void dedicatedSkinGeneratorComposesTheAtlasFromTheOwnersReferences() throws Exception {
        assertTrue(Files.isRegularFile(SKIN_GENERATOR));
        final String generator = Files.readString(SKIN_GENERATOR);
        assertTrue(generator.contains("circle_mage.png"));
        assertTrue(generator.contains("from PIL import Image"));
        assertFalse(generator.contains("import random"));
        for (final String reference : List.of(
            "docs/art-source/skin-references/circle_mage_reference_1.png", "docs/art-source/skin-references/circle_mage_reference_2.png", "docs/art-source/skin-references/circle_mage_reference_3.png"
        )) {
            assertTrue(generator.contains(reference), reference);
            assertFalse(Files.exists(Path.of("src/main/resources/assets/warlockery/textures/entity", reference)),
                reference + " must stay out of the shipped assets");
        }
        for (final String foreign : List.of(
            "hedge_crone", "blood_thrall", "corpse.png", "werewolf_hunter", "lycan_villager", "nami"
        )) {
            assertFalse(generator.contains(foreign), foreign);
        }
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final CircleMageModel model = new CircleMageModel(CircleMageModel.createBodyLayer().bakeRoot());
        final CircleMageModel.State action = new CircleMageModel.State();
        action.activity = CircleMageModel.Activity.DEFENDING;
        action.focusPrepared = true;
        action.ageInTicks = 27.0F;
        writeContactSheet(
            model,
            action,
            Path.of("build/reports/visual-audit/creatures/circle_mage-software-contact-sheet.png")
        );
    }

    private static void assertBoxSize(
        final ModelPart part,
        final float width,
        final float height,
        final float depth,
        final String name
    ) {
        assertFalse(part.isEmpty(), name);
        final float[] size = ownCubeSize(part);
        assertEquals(width, size[0], EPSILON, name + " width");
        assertEquals(height, size[1], EPSILON, name + " height");
        assertEquals(depth, size[2], EPSILON, name + " depth");
    }

    private static float width(final ModelPart part) {
        return ownCubeSize(part)[0];
    }

    /** Untransformed size in pixels of the part's own cubes, ignoring every child. */
    private static float[] ownCubeSize(final ModelPart part) {
        final float[] extent = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        final String[] ownPath = {null};
        part.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (ownPath[0] == null) {
                ownPath[0] = path;
            }
            if (!path.equals(ownPath[0])) {
                return;
            }
            for (final ModelPart.Polygon polygon : cube.polygons) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    extent[0] = Math.min(extent[0], vertex.worldX() * 16.0F);
                    extent[1] = Math.min(extent[1], vertex.worldY() * 16.0F);
                    extent[2] = Math.min(extent[2], vertex.worldZ() * 16.0F);
                    extent[3] = Math.max(extent[3], vertex.worldX() * 16.0F);
                    extent[4] = Math.max(extent[4], vertex.worldY() * 16.0F);
                    extent[5] = Math.max(extent[5], vertex.worldZ() * 16.0F);
                }
            }
        });
        return new float[] {extent[3] - extent[0], extent[4] - extent[1], extent[5] - extent[2]};
    }

    private static void writeContactSheet(
        final CircleMageModel model,
        final CircleMageModel.State action,
        final Path output
    ) throws Exception {
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(31, 38, 45));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new CircleMageModel.State());
            model.root().yRot = turns[index];
            graphics.drawImage(softwareSnapshot(
                model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
            ), index * 128, 0, null);
        }
        model.setupAnim(action);
        graphics.drawImage(softwareSnapshot(
            model.root(), CreatureModelTestSupport.Projection.FRONT, 128, 5
        ), 640, 0, null);
        graphics.dispose();
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
