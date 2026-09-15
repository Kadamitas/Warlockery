package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.matrixSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import com.kadamitas.warlockery.entity.LycanVillagerEntity;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The lycan villager is the vanilla villager body rig carrying a werewolf head; the fur lives in
 * the atlas. These tests pin the vanilla body part tree (so native profession clothing keeps
 * lining up), the wolf head, the bare-skull clothing meshes, and the atlas contracts.
 */
final class LycanVillagerModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/LycanVillagerModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/lycan_villager.png"
    );
    private static final Path GENERATOR = Path.of(
        "tools/creature_models/generate_lycan_villager_fur.py"
    );

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void usesTheVanillaVillagerRigWithOnlyWolfEarsAdded() throws Exception {
        assertEquals(128, LycanVillagerModel.TEXTURE_WIDTH);
        assertEquals(64, LycanVillagerModel.CLOTHING_TEXTURE_WIDTH);
        assertEquals(64, LycanVillagerModel.TEXTURE_HEIGHT);
        final ModelPart root = LycanVillagerModel.createBodyLayer().bakeRoot();

        // Vanilla villager part tree, verbatim names.
        final ModelPart head = requiredChild(root, "head");
        final ModelPart hat = requiredChild(head, "hat");
        final ModelPart hatRim = requiredChild(hat, "hat_rim");
        final ModelPart muzzle = requiredChild(head, "muzzle");
        final ModelPart lowerJaw = requiredChild(head, "lower_jaw");
        final ModelPart browWedge = requiredChild(head, "brow_wedge");
        final ModelPart body = requiredChild(root, "body");
        final ModelPart jacket = requiredChild(body, "jacket");
        final ModelPart arms = requiredChild(root, "arms");
        final ModelPart rightLeg = requiredChild(root, "right_leg");
        final ModelPart leftLeg = requiredChild(root, "left_leg");
        for (final ModelPart part : new ModelPart[] {head, hat, hatRim, muzzle, lowerJaw, browWedge, body, jacket, arms, rightLeg, leftLeg}) {
            assertFalse(part.isEmpty());
        }

        // Vanilla box extents (so vanilla villager.png-layout clothing textures keep lining up).
        // Werewolf skull fills the vanilla head slot; muzzle and jaw project ahead of it.
        assertBox(head, -4.0F, -9.0F, -4.0F, 4.0F, -1.0F, 4.0F);
        assertBox(hat, -4.51F, -10.51F, -4.51F, 4.51F, 0.51F, 4.51F);
        assertBox(muzzle, -2.5F, -1.5F, -3.0F, 2.5F, 1.5F, 0.0F);
        assertEquals(-4.0F, muzzle.y, 1.0E-6F);
        assertEquals(-4.0F, muzzle.z, 1.0E-6F);
        assertTrue(lowerJaw.y > muzzle.y, "jaw hangs below the muzzle");
        assertTrue(browWedge.y < muzzle.y, "brow wedge sits above the muzzle");
        assertBox(body, -4.0F, 0.0F, -3.0F, 4.0F, 12.0F, 3.0F);
        assertBox(jacket, -4.5F, -0.5F, -3.5F, 4.5F, 20.5F, 3.5F);
        assertBox(rightLeg, -2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F);
        assertBox(leftLeg, -2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F);
        assertEquals(-2.0F, rightLeg.x, 1.0E-6F);
        assertEquals(2.0F, leftLeg.x, 1.0E-6F);
        assertEquals(12.0F, rightLeg.y, 1.0E-6F);
        assertEquals(3, ownCubeCount(arms), "two sleeves and the folded forearm block");
        assertEquals(-0.75F, arms.xRot, 1.0E-6F);
        assertEquals(3.0F, arms.y, 1.0E-6F);
        assertEquals(1, ownCubeCount(hatRim));
        assertEquals(-Mth.HALF_PI, hatRim.xRot, 1.0E-6F);

        // The only addition: a pair of wolf ears pinned to the crown.
        final ModelPart ears = requiredChild(head, "ears");
        assertTrue(ears.isEmpty(), "ear pivot carries no geometry of its own");
        final ModelPart rightEar = requiredChild(ears, "right_ear");
        final ModelPart leftEar = requiredChild(ears, "left_ear");
        assertFalse(rightEar.isEmpty());
        assertFalse(leftEar.isEmpty());
        assertTrue(rightEar.x < 0.0F && leftEar.x > 0.0F, "ears sit either side of the crown");
        assertEquals(-rightEar.zRot, leftEar.zRot, 1.0E-6F, "ears splay symmetrically");
        final CreatureModelTestSupport.Bounds earBounds = CreatureModelTestSupport.bounds(rightEar);
        assertTrue(earBounds.maxY() - earBounds.minY() <= 5.0F, "ears stay short");
        assertTrue(earBounds.maxX() - earBounds.minX() <= 3.0F);

        // Nothing else from the retired wolf-anatomy body rig survives (the head is wolf by design).
        for (final String retired : java.util.List.of(
            "nose", "tail", "shoulder_ruff", "tapered_wolf_waist", "right_wolf_forearm",
            "left_wolf_forearm", "right_wolf_claws", "left_wolf_claws", "right_heavy_calf"
        )) {
            for (final ModelPart part : root.getAllParts()) {
                assertFalse(hasChild(part, retired), retired);
            }
        }
        assertEquals(15, root.getAllParts().size(),
            "root, head, hat, hat_rim, muzzle, lower_jaw, brow_wedge, ears, right_ear, left_ear, body, jacket, arms, legs");
    }

    @Test
    void keepsAVillagerSilhouette() {
        final ModelPart root = LycanVillagerModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.40F && frontAspect <= 0.52F,
            "vanilla villager front proportion (hat brim is the widest extent): " + frontAspect);
        assertTrue(sideAspect >= 0.40F && sideAspect <= 0.52F,
            "vanilla villager side proportion: " + sideAspect);
        assertTrue(height >= 34.0F && height <= 38.0F,
            "hat brim, head, body and legs stack to the vanilla villager height: " + height);
    }

    @Test
    void noHatLayerRemovesOnlyHatGeometry() {
        final ModelPart full = LycanVillagerModel.createBodyLayer().bakeRoot();
        final ModelPart noHat = LycanVillagerModel.createBodyLayerNoHat().bakeRoot();
        final ModelPart fullHat = requiredChild(requiredChild(full, "head"), "hat");
        final ModelPart noHatHat = requiredChild(requiredChild(noHat, "head"), "hat");
        assertFalse(fullHat.isEmpty());
        assertFalse(requiredChild(fullHat, "hat_rim").isEmpty());
        assertTrue(noHatHat.isEmpty());
        assertTrue(requiredChild(noHatHat, "hat_rim").isEmpty());

        final ModelPart noHatHead = requiredChild(noHat, "head");
        assertFalse(noHatHead.isEmpty());
        assertFalse(requiredChild(noHatHead, "muzzle").isEmpty());
        final ModelPart noHatEars = requiredChild(noHatHead, "ears");
        assertFalse(requiredChild(noHatEars, "right_ear").isEmpty());
        assertFalse(requiredChild(noHatEars, "left_ear").isEmpty());
        assertFalse(requiredChild(noHat, "body").isEmpty());
        assertFalse(requiredChild(requiredChild(noHat, "body"), "jacket").isEmpty());
        assertFalse(requiredChild(noHat, "arms").isEmpty());
        assertFalse(requiredChild(noHat, "right_leg").isEmpty());
        assertFalse(requiredChild(noHat, "left_leg").isEmpty());
        assertEquals(full.getAllParts().size(), noHat.getAllParts().size());
    }

    @Test
    void clothingLayersKeepTheVanillaBodyAndHatButLeaveTheSkullBare() {
        final ModelPart clothing = LycanVillagerModel.createClothingLayer().bakeRoot();
        final ModelPart head = requiredChild(clothing, "head");
        assertTrue(head.isEmpty(), "biome skin must not paint a villager face over the wolf skull");
        for (final String wolf : java.util.List.of("muzzle", "lower_jaw", "brow_wedge")) {
            assertTrue(requiredChild(head, wolf).isEmpty(), wolf);
        }
        final ModelPart ears = requiredChild(head, "ears");
        assertTrue(requiredChild(ears, "right_ear").isEmpty());
        assertTrue(requiredChild(ears, "left_ear").isEmpty());
        assertFalse(requiredChild(head, "hat").isEmpty(), "profession hats still render");
        assertFalse(requiredChild(clothing, "body").isEmpty());
        assertFalse(requiredChild(clothing, "arms").isEmpty());
        assertFalse(requiredChild(clothing, "right_leg").isEmpty());
        assertTrue(requiredChild(requiredChild(LycanVillagerModel.createClothingLayerNoHat().bakeRoot(), "head"), "hat").isEmpty());
        assertEquals(LycanVillagerModel.createBodyLayer().bakeRoot().getAllParts().size(), clothing.getAllParts().size());
        // Clothing meshes sample the vanilla 64x64 sheets, so every clothing UV must stay inside them.
        CreatureModelTestSupport.assertUvsWithin(clothing, 64, 64);
    }

    @Test
    void furAtlasCoversEveryVanillaIslandAndTheEarPocket() throws Exception {
        final ModelPart root = LycanVillagerModel.createBodyLayer().bakeRoot();
        CreatureModelTestSupport.assertUvsWithin(root, 128, 64);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(64, texture.getHeight());
        // Every island the base render samples is opaque. The hat rim plane is the one exception:
        // vanilla profession textures own the brim through the clothing layer, and opaque paint
        // there would render a solid 16x16 plane straight through the face.
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> !cube.path().contains("hat_rim"));
        for (int v = 48; v < 64; v++) {
            for (int u = 30; u < 64; u++) {
                assertEquals(0, texture.getRGB(u, v) >>> 24, "hat rim island must stay clear at " + u + "," + v);
            }
        }
        // Ear islands live in the pocket no vanilla villager island touches.
        for (int v = 38; v < 43; v++) {
            for (int u = 28; u < 40; u++) {
                assertEquals(255, texture.getRGB(u, v) >>> 24, "ear island opaque at " + u + "," + v);
            }
        }
        // Amber eyes on the face island and pink inner ears.
        assertTrue(isAmber(texture.getRGB(9, 11)) && isAmber(texture.getRGB(14, 11)), "amber eyes");
        // Muzzle island carries the black nose leather.
        assertTrue(((texture.getRGB(69, 3) >>> 16) & 0xFF) < 40, "nose leather on the muzzle front");
        assertTrue(isPink(texture.getRGB(29, 40)) && isPink(texture.getRGB(36, 40)), "pink inner ears");
        // Face and hands are fur, not villager skin: low saturation grey-brown.
        for (final int[] sample : new int[][] {{8, 9}, {15, 16}, {2, 12}, {20, 14}, {28, 12}, {45, 32}, {57, 31}}) {
            assertTrue(isFur(texture.getRGB(sample[0], sample[1])), "fur at " + sample[0] + "," + sample[1]);
        }
        // Robe islands stay an earthy cloth tone for vanilla clothing to sit over.
        for (final int[] sample : new int[][] {{18, 30}, {38, 30}, {2, 48}, {22, 58}, {46, 27}, {2, 30}}) {
            assertTrue(isCloth(texture.getRGB(sample[0], sample[1])), "cloth at " + sample[0] + "," + sample[1]);
        }
    }

    @Test
    void dedicatedPythonGeneratorOwnsTheAtlas() throws Exception {
        assertTrue(Files.exists(GENERATOR));
        final String generator = Files.readString(GENERATOR);
        assertTrue(generator.contains("lycan_villager.png"));
        assertTrue(generator.contains("hash_noise"), "deterministic hash noise, no random module");
        assertFalse(generator.contains("import random"));
        final String occult = Files.readString(Path.of("tools/creature_models/generate_occult_humanoids.ps1"));
        assertFalse(occult.contains("'lycan_villager.png'"), "the occult PowerShell generator no longer paints the atlas");
    }

    @Test
    void nativeVillagerStateRetainsDynamicTypeProfessionAndLevel() {
        final VillagerData cartographer = new VillagerData(
            BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.DESERT),
            BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.CARTOGRAPHER),
            4
        );
        final LycanVillagerModel.State state = new LycanVillagerModel.State();
        state.villagerData = cartographer;
        assertInstanceOf(VillagerRenderState.class, state);
        assertSame(cartographer, state.getVillagerData());
        assertEquals(VillagerProfession.CARTOGRAPHER, state.getVillagerData().profession().unwrapKey().orElseThrow());
        assertEquals(VillagerType.DESERT, state.getVillagerData().type().unwrapKey().orElseThrow());
        assertEquals(4, state.getVillagerData().level());
    }

    @Test
    void implementsNativeVillagerLikeArmSeamWithoutAPlayerOrWerewolfRig() throws Exception {
        final LycanVillagerModel model = new LycanVillagerModel(
            LycanVillagerModel.createBodyLayer().bakeRoot()
        );
        assertInstanceOf(VillagerLikeModel.class, model);
        final PoseStack translated = new PoseStack();
        model.translateToArms(new LycanVillagerModel.State(), translated);
        assertNotEquals(matrixSnapshot(new PoseStack()), matrixSnapshot(translated));

        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends VillagerRenderState"));
        assertTrue(source.contains("implements VillagerLikeModel<LycanVillagerModel.State>"));
        assertTrue(source.contains("createBodyLayerNoHat"));
        assertTrue(source.contains("translateToArms"));
        for (final String forbidden : java.util.List.of(
            "WerewolfModel", "PlayerModel", "HumanoidModel<", "ArcaneCreatureModel",
            "CreatureModelProfile", "AnimationHelper", "GeometryHelper", "ModelHelper",
            "indigo", "staticProfession", "fixedProfession"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(Files.exists(Path.of(
            "src/main/resources/assets/warlockery/textures/entity/lycan_villager_cartographer.png"
        )));
        assertTrue(Files.exists(TEXTURE));
    }

    @Test
    void exposesConcreteVillagerDataExtractorForNativeClothingLayers() {
        assertDoesNotThrow(() -> LycanVillagerModel.class.getDeclaredMethod(
            "extractRenderState", LycanVillagerEntity.class, LycanVillagerModel.State.class, float.class
        ));
        final String source = assertDoesNotThrow(() -> Files.readString(SOURCE));
        assertTrue(source.contains("entity.getVillagerData()"));
        assertTrue(source.contains("state.villagerData"));
    }

    @Test
    void activityPosesMoveHeadArmsAndEarsWithoutATail() {
        final LycanVillagerModel model = new LycanVillagerModel(
            LycanVillagerModel.createBodyLayer().bakeRoot()
        );
        final ModelPart head = requiredChild(model.root(), "head");
        final ModelPart arms = requiredChild(model.root(), "arms");
        final ModelPart ears = requiredChild(head, "ears");

        model.setupAnim(new LycanVillagerModel.State());
        final float restingHead = head.xRot;
        final float restingArms = arms.xRot;

        final LycanVillagerModel.State moon = new LycanVillagerModel.State();
        moon.activity = LycanVillagerModel.Activity.MOON_WATCH;
        model.setupAnim(moon);
        assertTrue(head.xRot < restingHead, "moon watch lifts the head");

        final LycanVillagerModel.State warning = new LycanVillagerModel.State();
        warning.activity = LycanVillagerModel.Activity.WARNING;
        model.setupAnim(warning);
        assertTrue(arms.xRot < restingArms, "warning raises the folded arms");
        assertNotEquals(0.0F, ears.xRot, "warning pins the ears");

        final LycanVillagerModel.State withdraw = new LycanVillagerModel.State();
        withdraw.activity = LycanVillagerModel.Activity.WITHDRAWING;
        model.setupAnim(withdraw);
        assertTrue(requiredChild(model.root(), "body").xRot > 0.0F, "withdrawing leans the body");

        model.setupAnim(new LycanVillagerModel.State());
        assertEquals(restingArms, arms.xRot, 1.0E-6F, "poses reset between frames");
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final LycanVillagerModel model = new LycanVillagerModel(
            LycanVillagerModel.createBodyLayer().bakeRoot()
        );
        final LycanVillagerModel.State action = new LycanVillagerModel.State();
        action.activity = LycanVillagerModel.Activity.MOON_WATCH;
        action.ageInTicks = 42.0F;
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/lycan_villager-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(768, 160, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(35, 39, 36));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        final float[] turns = {0.0F, Mth.HALF_PI, Mth.PI, -Mth.HALF_PI, -0.72F};
        for (int index = 0; index < turns.length; index++) {
            model.setupAnim(new LycanVillagerModel.State());
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

    private static boolean hasChild(final ModelPart part, final String name) {
        try {
            part.getChild(name);
            return true;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    /** Cubes owned directly by the part (visit path ""), excluding any child parts. */
    private static java.util.List<CreatureModelTestSupport.CubeVisit> ownCubes(final ModelPart part) {
        return CreatureModelTestSupport.cubes(part).stream()
            .filter(visit -> visit.path().isEmpty())
            .toList();
    }

    private static int ownCubeCount(final ModelPart part) {
        return ownCubes(part).size();
    }

    /** Asserts the part owns exactly one box with the given local (pre-pose) pixel extents. */
    private static void assertBox(
        final ModelPart part,
        final float minX, final float minY, final float minZ,
        final float maxX, final float maxY, final float maxZ
    ) {
        final java.util.List<CreatureModelTestSupport.CubeVisit> cubes = ownCubes(part);
        assertEquals(1, cubes.size(), "single vanilla box");
        final float[] extents = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        for (final ModelPart.Polygon polygon : cubes.getFirst().polygons()) {
            for (final ModelPart.Vertex vertex : polygon.vertices()) {
                extents[0] = Math.min(extents[0], vertex.worldX() * 16.0F);
                extents[1] = Math.min(extents[1], vertex.worldY() * 16.0F);
                extents[2] = Math.min(extents[2], vertex.worldZ() * 16.0F);
                extents[3] = Math.max(extents[3], vertex.worldX() * 16.0F);
                extents[4] = Math.max(extents[4], vertex.worldY() * 16.0F);
                extents[5] = Math.max(extents[5], vertex.worldZ() * 16.0F);
            }
        }
        assertEquals(minX, extents[0], 1.0E-3F, "minX");
        assertEquals(minY, extents[1], 1.0E-3F, "minY");
        assertEquals(minZ, extents[2], 1.0E-3F, "minZ");
        assertEquals(maxX, extents[3], 1.0E-3F, "maxX");
        assertEquals(maxY, extents[4], 1.0E-3F, "maxY");
        assertEquals(maxZ, extents[5], 1.0E-3F, "maxZ");
    }

    private static boolean isAmber(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        return red > 180 && green > 120 && green < red && blue < 90;
    }

    private static boolean isPink(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        return red > green + 30 && red > blue + 30 && blue >= green - 8;
    }

    private static boolean isFur(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        final int maximum = Math.max(red, Math.max(green, blue));
        final int minimum = Math.min(red, Math.min(green, blue));
        return maximum - minimum <= 24 && maximum < 200 && red >= green && green >= blue;
    }

    private static boolean isCloth(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        return red > green && green > blue && red - blue >= 30;
    }
}
