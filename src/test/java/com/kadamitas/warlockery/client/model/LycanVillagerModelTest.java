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

final class LycanVillagerModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/LycanVillagerModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/lycan_villager.png"
    );

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ownsWolfAnatomyUnderVillagerCompatibleClothingGeometry() throws Exception {
        assertEquals(64, LycanVillagerModel.TEXTURE_WIDTH);
        assertEquals(64, LycanVillagerModel.TEXTURE_HEIGHT);
        final ModelPart root = LycanVillagerModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "head");
        assertFalse(requiredChild(head, "muzzle").isEmpty());
        assertFalse(requiredChild(requiredChild(head, "muzzle"), "lower_wedge_muzzle").isEmpty());
        final ModelPart ears = requiredChild(head, "ears");
        assertTrue(ears.isEmpty(), "ear pivot must not duplicate the canonical ear geometry");
        final ModelPart rightEar = requiredChild(ears, "right_ear");
        final ModelPart leftEar = requiredChild(ears, "left_ear");
        assertFalse(rightEar.isEmpty());
        assertFalse(leftEar.isEmpty());
        assertTrue(rightEar.y != leftEar.y || Math.abs(rightEar.zRot) != Math.abs(leftEar.zRot),
            "short ears must retain an uneven lycan cadence");
        assertFalse(requiredChild(head, "hat").isEmpty());
        assertFalse(requiredChild(head, "hat_rim").isEmpty());
        final ModelPart body = requiredChild(root, "body");
        assertFalse(requiredChild(body, "jacket").isEmpty());
        final ModelPart shoulderRuff = requiredChild(body, "shoulder_ruff");
        final ModelPart wolfWaist = requiredChild(body, "tapered_wolf_waist");
        assertFalse(shoulderRuff.isEmpty());
        assertFalse(wolfWaist.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(shoulderRuff).maxX()
                - CreatureModelTestSupport.bounds(shoulderRuff).minX() >= 11.0F);
        assertTrue(CreatureModelTestSupport.bounds(wolfWaist).maxX()
                - CreatureModelTestSupport.bounds(wolfWaist).minX() <= 8.0F);
        final ModelPart arms = requiredChild(root, "arms");
        final ModelPart rightForearm = requiredChild(arms, "right_wolf_forearm");
        final ModelPart leftForearm = requiredChild(arms, "left_wolf_forearm");
        assertFalse(rightForearm.isEmpty());
        assertFalse(leftForearm.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rightForearm).maxY()
                - CreatureModelTestSupport.bounds(rightForearm).minY() >= 9.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftForearm).maxY()
                - CreatureModelTestSupport.bounds(leftForearm).minY() >= 9.0F);
        assertFalse(requiredChild(arms, "right_wolf_claws").isEmpty());
        assertFalse(requiredChild(arms, "left_wolf_claws").isEmpty());
        final ModelPart rightLeg = requiredChild(root, "right_leg");
        final ModelPart leftLeg = requiredChild(root, "left_leg");
        final ModelPart rightCalf = requiredChild(rightLeg, "right_heavy_calf");
        final ModelPart leftCalf = requiredChild(leftLeg, "left_heavy_calf");
        final ModelPart rightFoot = requiredChild(rightCalf, "right_digitigrade_foot");
        final ModelPart leftFoot = requiredChild(leftCalf, "left_digitigrade_foot");
        assertFalse(rightFoot.isEmpty());
        assertFalse(leftFoot.isEmpty());
        assertTrue(CreatureModelTestSupport.bounds(rightCalf).maxX()
                - CreatureModelTestSupport.bounds(rightCalf).minX() >= 5.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftCalf).maxX()
                - CreatureModelTestSupport.bounds(leftCalf).minX() >= 5.0F);
        assertTrue(CreatureModelTestSupport.bounds(rightFoot).maxZ()
                - CreatureModelTestSupport.bounds(rightFoot).minZ() >= 6.0F);
        assertTrue(CreatureModelTestSupport.bounds(leftFoot).maxZ()
                - CreatureModelTestSupport.bounds(leftFoot).minZ() >= 6.0F);
        assertFalse(requiredChild(root, "tail").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, 64, 64);
        assertEquals(64, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(64, ImageIO.read(TEXTURE.toFile()).getHeight());
        CreatureModelTestSupport.assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
    }

    @Test
    void conceptShapeHasBroadWolfBandsAroundAnUntouchedNativeVillagerClothingSeam() {
        final ModelPart root = LycanVillagerModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float height = bounds.maxY() - bounds.minY();
        final float frontAspect = (bounds.maxX() - bounds.minX()) / height;
        final float sideAspect = (bounds.maxZ() - bounds.minZ()) / height;
        assertTrue(frontAspect >= 0.52F && frontAspect <= 0.66F,
            "ruff, chest, long forearms, calves, and feet must build the large lycan front: " + frontAspect);
        assertTrue(sideAspect >= 0.46F && sideAspect <= 0.60F,
            "wedge muzzle, digitigrade feet, clothing, and tail need the concept profile: " + sideAspect);
        final ModelPart body = requiredChild(root, "body");
        assertFalse(requiredChild(body, "jacket").isEmpty(),
            "native profession clothing must retain the vanilla jacket seam");
    }

    @Test
    void noHatLayerPreservesAnatomyAndRemovesOnlyHatGeometry() {
        final ModelPart full = LycanVillagerModel.createBodyLayer().bakeRoot();
        final ModelPart noHat = LycanVillagerModel.createBodyLayerNoHat().bakeRoot();
        assertFalse(requiredChild(requiredChild(full, "head"), "hat").isEmpty());
        assertTrue(requiredChild(requiredChild(noHat, "head"), "hat").isEmpty());
        assertFalse(requiredChild(requiredChild(noHat, "head"), "muzzle").isEmpty());
        final ModelPart noHatEars = requiredChild(requiredChild(noHat, "head"), "ears");
        assertFalse(requiredChild(noHatEars, "right_ear").isEmpty());
        assertFalse(requiredChild(noHatEars, "left_ear").isEmpty());
        assertFalse(requiredChild(noHat, "tail").isEmpty());
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
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
            768, 160, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
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
}
