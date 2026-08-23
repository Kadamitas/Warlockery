package com.kadamitas.warlockery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class ArcaneCreatureModelTest {
    @Test
    void mandrakeAndDreamrootUseDedicatedNestedArticulation() {
        final ModelPart mandrake = modelFor("mandrake");
        final ModelPart mandrakeCrown = requiredChild(mandrake, "crown");
        assertFalse(requiredChild(mandrakeCrown, "mandrake_leaf_north").isEmpty());
        assertFalse(requiredChild(mandrakeCrown, "mandrake_leaf_west").isEmpty());
        assertFalse(requiredChild(requiredChild(mandrake, "right_arm"), "right_arm_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(mandrake, "left_arm"), "left_arm_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(mandrake, "right_hind_leg"), "right_root_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(mandrake, "left_hind_leg"), "left_root_distal").isEmpty());

        final ModelPart dreamroot = modelFor("dreamroot");
        final ModelPart bloom = requiredChild(dreamroot, "crown");
        final ModelPart innerPetals = requiredChild(bloom, "petal_tier_inner");
        final ModelPart middlePetals = requiredChild(innerPetals, "petal_tier_middle");
        assertFalse(innerPetals.isEmpty());
        assertFalse(middlePetals.isEmpty());
        assertFalse(requiredChild(middlePetals, "petal_tier_outer").isEmpty());
        assertFalse(requiredChild(requiredChild(dreamroot, "right_hind_leg"), "right_tassel_distal").isEmpty());
        assertFalse(requiredChild(requiredChild(dreamroot, "left_hind_leg"), "left_tassel_distal").isEmpty());
    }

    @Test
    void mandrakeAndDreamrootProfilesDeclareExpandedAtlases() {
        for (final String entityId : java.util.List.of("mandrake", "dreamroot")) {
            final CreatureModelProfile profile = CreatureModelProfile.forEntity(
                entityId,
                com.kadamitas.warlockery.entity.CreatureVisualProfile.forKind(
                    com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind.valueOf(entityId.toUpperCase())
                )
            );
            assertEquals(128, recordInt(profile, "textureWidth"), entityId + " atlas width");
            assertEquals(64, recordInt(profile, "textureHeight"), entityId + " atlas height");
            assertDoesNotThrow(() -> ArcaneCreatureModel.createLayer(profile).bakeRoot(), entityId + " UV footprint");
        }
    }

    @Test
    void restingPlantRootsStayAtOrAboveTheGroundPlane() {
        final float dreamrootMaxY = maxTransformedY(modelFor("dreamroot"));
        final float mandrakeMaxY = maxTransformedY(modelFor("mandrake"));
        assertTrue(dreamrootMaxY <= 24.0F,
            "Dreamroot resting geometry must not descend below Y=24, found " + dreamrootMaxY);
        assertTrue(mandrakeMaxY <= 24.6F,
            "Mandrake toe rounding must remain within 0.6 pixels of Y=24, found " + mandrakeMaxY);
    }

    @Test
    void dreamrootBodyCuboidUsesOnlyOpaqueAtlasPixelsOnEveryFace() throws Exception {
        final ModelPart root = modelFor("dreamroot");
        final BufferedImage texture = ImageIO.read(Path.of(
            "src/main/resources/assets/warlockery/textures/entity/dreamroot.png"
        ).toFile());
        final List<ModelPart.Polygon> bodyPolygons = new ArrayList<>();
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (path.endsWith("body") && cubeIndex == 1) {
                bodyPolygons.addAll(List.of(cube.polygons));
            }
        });

        assertEquals(6, bodyPolygons.size(), "Dreamroot bulbous body cube must expose all six faces");
        for (final ModelPart.Polygon polygon : bodyPolygons) {
            final int minU = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                .mapToDouble(vertex -> vertex.u() * texture.getWidth()).min().orElseThrow());
            final int maxU = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                .mapToDouble(vertex -> vertex.u() * texture.getWidth()).max().orElseThrow());
            final int minV = (int) Math.floor(java.util.Arrays.stream(polygon.vertices())
                .mapToDouble(vertex -> vertex.v() * texture.getHeight()).min().orElseThrow());
            final int maxV = (int) Math.ceil(java.util.Arrays.stream(polygon.vertices())
                .mapToDouble(vertex -> vertex.v() * texture.getHeight()).max().orElseThrow());
            for (int v = minV; v < maxV; v++) {
                for (int u = minU; u < maxU; u++) {
                    assertEquals(255, texture.getRGB(u, v) >>> 24,
                        "transparent Dreamroot body texel at (" + u + ", " + v + ") for face " + polygon.normal());
                }
            }
        }
    }

    @Test
    void plantJointAnimationCarriesNestedLeavesAndTassels() {
        for (final String entityId : java.util.List.of("mandrake", "dreamroot")) {
            final ArcaneCreatureModel model = ArcaneCreatureModel.create(profileFor(entityId));
            final ModelPart root = model.root();
            final float restingArm = root.getChild("right_arm").xRot;
            final float restingCrown = root.getChild("crown").zRot;
            final TexturedCreatureRenderers.ArcaneState state = new TexturedCreatureRenderers.ArcaneState();
            state.walkAnimationPos = 0.0F;
            state.walkAnimationSpeed = 1.0F;
            state.ageInTicks = 20.0F;
            model.setupAnim(state);
            assertNotEquals(restingArm, root.getChild("right_arm").xRot, entityId + " proximal limb motion");
            assertNotEquals(restingCrown, root.getChild("crown").zRot, entityId + " crown lag");
        }
    }

    @Test
    void hexBatRoostAndSwoopPosesComeOnlyFromSynchronizedFacts() {
        final var neutral = ArcaneCreatureModel.hexBatPose(
            com.kadamitas.warlockery.client.CreatureModelProfile.Variant.HEX_BAT, false, false
        );
        assertFalse(neutral.overrides(), "ordinary flight keeps the existing flap animation");
        final var roost = ArcaneCreatureModel.hexBatPose(
            com.kadamitas.warlockery.client.CreatureModelProfile.Variant.HEX_BAT, true, false
        );
        assertTrue(roost.overrides());
        assertTrue(roost.bodyXRot() > 3.0F, "the roost pose hangs the body upside down");
        assertTrue(roost.wingFoldZRot() > 0.0F, "the roost pose folds the wings");
        final var swoop = ArcaneCreatureModel.hexBatPose(
            com.kadamitas.warlockery.client.CreatureModelProfile.Variant.HEX_BAT, false, true
        );
        assertTrue(swoop.overrides());
        assertTrue(swoop.bodyXRot() > 0.0F && swoop.bodyXRot() < 1.5F,
            "the swoop pose pitches the body forward without flipping it");
        assertTrue(swoop.wingFoldZRot() < 0.0F, "the swoop pose sweeps and narrows the wings");
    }

    @Test
    void nonHexAvianVariantsNeverReceiveAHexBatPose() {
        for (final var variant : com.kadamitas.warlockery.client.CreatureModelProfile.Variant.values()) {
            if (variant == com.kadamitas.warlockery.client.CreatureModelProfile.Variant.HEX_BAT) continue;
            assertFalse(ArcaneCreatureModel.hexBatPose(variant, true, false).overrides(),
                variant + " must not roost like a Hex Bat");
            assertFalse(ArcaneCreatureModel.hexBatPose(variant, false, true).overrides(),
                variant + " must not swoop like a Hex Bat");
        }
    }

    @Test
    void hedgeCroneAndCircleMagePosesComeOnlyFromSynchronizedFacts() {
        final var croneVariant = CreatureModelProfile.Variant.HEDGE_CRONE;
        final var mageVariant = CreatureModelProfile.Variant.CIRCLE_MAGE;

        assertFalse(ArcaneCreatureModel.hedgeCronePose(
            croneVariant, com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.IDLE, false
        ).overrides(), "a calm unwarded Crone keeps the existing animation exactly");
        assertFalse(ArcaneCreatureModel.hedgeCronePose(croneVariant, null, false).overrides(),
            "an absent synchronized fact never poses anything");

        final var warning = ArcaneCreatureModel.hedgeCronePose(
            croneVariant, com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.WARNING, false);
        assertTrue(warning.overrides());
        assertTrue(warning.rightArmXRot() < 0.0F, "the warning raises the staff arm");

        final var preparing = ArcaneCreatureModel.hedgeCronePose(
            croneVariant, com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.PREPARING, false);
        assertTrue(preparing.bodyXRot() > 0.0F, "preparation lowers the Crone toward the workstation");

        final var casting = ArcaneCreatureModel.hedgeCronePose(
            croneVariant, com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.CASTING, false);
        assertTrue(casting.rightArmXRot() < preparing.rightArmXRot(),
            "the cast uses a deliberate extended staff arm pose");

        assertTrue(ArcaneCreatureModel.hedgeCronePose(
            croneVariant, com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.IDLE, true
        ).leftArmZRot() < 0.0F, "a prepared ward is visible on the off hand");

        assertFalse(ArcaneCreatureModel.circleMagePose(
            mageVariant, com.kadamitas.warlockery.entity.CircleMageRules.Mode.IDLE, false
        ).overrides());
        final var studying = ArcaneCreatureModel.circleMagePose(
            mageVariant, com.kadamitas.warlockery.entity.CircleMageRules.Mode.STUDYING, false);
        assertTrue(studying.leftArmXRot() < 0.0F && studying.headXRot() > 0.0F,
            "study presents the book-facing rehearsal pose");
        final var bolt = ArcaneCreatureModel.circleMagePose(
            mageVariant, com.kadamitas.warlockery.entity.CircleMageRules.Mode.DEFENDING, false);
        assertTrue(bolt.rightArmXRot() < studying.rightArmXRot(),
            "the bolt cast is a distinct forward staff pose");
        assertTrue(ArcaneCreatureModel.circleMagePose(
            mageVariant, com.kadamitas.warlockery.entity.CircleMageRules.Mode.IDLE, true
        ).leftArmZRot() < 0.0F, "a prepared focus is visible on the off hand");

        assertTrue(warning.rightArmXRot() != bolt.rightArmXRot()
                || warning.headXRot() != bolt.headXRot(),
            "the two practitioners never read as the same mob");
    }

    @Test
    void noOtherVariantEverReceivesAnF13PractitionerPose() {
        for (final var variant : CreatureModelProfile.Variant.values()) {
            if (variant != CreatureModelProfile.Variant.HEDGE_CRONE) {
                for (final var mode : com.kadamitas.warlockery.entity.HedgeCroneRules.Mode.values()) {
                    assertFalse(ArcaneCreatureModel.hedgeCronePose(variant, mode, true).overrides(),
                        variant + " must not pose like a Hedge Crone");
                }
            }
            if (variant != CreatureModelProfile.Variant.CIRCLE_MAGE) {
                for (final var mode : com.kadamitas.warlockery.entity.CircleMageRules.Mode.values()) {
                    assertFalse(ArcaneCreatureModel.circleMagePose(variant, mode, true).overrides(),
                        variant + " must not pose like a Circle Mage");
                }
            }
        }
    }

    @Test
    void everyArchetypeBakesAHeadBodyAndMultipleSolidParts() {
        for (final Archetype archetype : Archetype.values()) {
            final ModelPart root = ArcaneCreatureModel.createLayer(archetype).bakeRoot();
            assertFalse(root.getChild("head").isEmpty(), archetype + " head");
            assertFalse(root.getChild("body").isEmpty(), archetype + " body");
            assertTrue(solidPartCount(root) >= 5, archetype + " solid part count");
        }
    }

    @Test
    void restoredFamiliesHaveEnoughGeometryForRecognizableSilhouettes() {
        final Map<Archetype, Long> minimumParts = Map.ofEntries(
            Map.entry(Archetype.FELINE, 7L),
            Map.entry(Archetype.AVIAN, 7L),
            Map.entry(Archetype.AMPHIBIAN, 6L),
            Map.entry(Archetype.MOUNT, 7L),
            Map.entry(Archetype.CANINE, 7L),
            Map.entry(Archetype.PLANTLING, 7L),
            Map.entry(Archetype.PLANT_BRUTE, 7L),
            Map.entry(Archetype.ARTHROPOD, 10L),
            Map.entry(Archetype.LYCAN, 7L),
            Map.entry(Archetype.BOSS, 7L),
            Map.entry(Archetype.IMP, 9L),
            Map.entry(Archetype.SIMIAN, 9L)
        );
        minimumParts.forEach((archetype, minimum) ->
            assertTrue(solidPartCount(ArcaneCreatureModel.createLayer(archetype).bakeRoot()) >= minimum, archetype.name())
        );
    }

    private static long solidPartCount(final ModelPart root) {
        return root.getAllParts().stream().filter(part -> !part.isEmpty()).count();
    }

    private static float maxTransformedY(final ModelPart root) {
        final float[] maximum = {Float.NEGATIVE_INFINITY};
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    final Vector3f transformed = pose.pose().transformPosition(
                        vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                    );
                    maximum[0] = Math.max(maximum[0], transformed.y() * 16.0F);
                }
            }
        });
        return maximum[0];
    }

    private static ModelPart modelFor(final String entityId) {
        return ArcaneCreatureModel.createLayer(profileFor(entityId)).bakeRoot();
    }

    private static CreatureModelProfile profileFor(final String entityId) {
        return CreatureModelProfile.forEntity(
            entityId,
            com.kadamitas.warlockery.entity.CreatureVisualProfile.forKind(
                com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind.valueOf(entityId.toUpperCase())
            )
        );
    }

    private static ModelPart requiredChild(final ModelPart parent, final String name) {
        return assertDoesNotThrow(() -> parent.getChild(name), "missing semantic child " + name);
    }

    private static int recordInt(final CreatureModelProfile profile, final String componentName) {
        return java.util.Arrays.stream(CreatureModelProfile.class.getRecordComponents())
            .filter(component -> component.getName().equals(componentName))
            .findFirst()
            .map(component -> assertDoesNotThrow(() -> (Integer) component.getAccessor().invoke(profile)))
            .orElse(-1);
    }

    @Test
    void bansheeGeometryKeepsItsHoodVeilAndClaws() {
        final ModelPart root = ArcaneCreatureModel.createLayer(bansheeProfile()).bakeRoot();
        assertFalse(root.getChild("hood_top").isEmpty());
        assertFalse(root.getChild("hair_veil").isEmpty());
        assertFalse(root.getChild("right_banshee_claw").isEmpty());
        assertFalse(root.getChild("left_banshee_claw").isEmpty());
    }

    @Test
    void bansheePresentationPosesAreExactAndPoseOnly() {
        final ArcaneCreatureModel model = ArcaneCreatureModel.create(bansheeProfile());
        final Pose baseline = pose(model, null, 0, 1.0F);
        final Pose vigil = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.VIGIL, 0, 1.0F);
        org.junit.jupiter.api.Assertions.assertEquals(baseline, vigil,
            "vigil and recovery keep the ordinary spirit hover");
        org.junit.jupiter.api.Assertions.assertEquals(baseline,
            pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.RECOVERY, 0, 1.0F));

        final Pose approach = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.APPROACH, 0, 1.0F);
        assertExact(baseline.bodyX() + ArcaneCreatureModel.BANSHEE_APPROACH_LEAN, approach.bodyX());
        assertExact(baseline.headX() - ArcaneCreatureModel.BANSHEE_APPROACH_LEAN * 0.5F, approach.headX());

        final Pose warningHold = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.WARNING, 0, 1.0F);
        assertExact(baseline.rightArmZ() + ArcaneCreatureModel.BANSHEE_WARNING_ARM_DRAW, warningHold.rightArmZ());
        assertExact(baseline.leftArmZ() - ArcaneCreatureModel.BANSHEE_WARNING_ARM_DRAW, warningHold.leftArmZ());
        final Pose warningPulse = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.WARNING, 1, 1.0F);
        assertExact(
            baseline.rightArmZ() + ArcaneCreatureModel.BANSHEE_WARNING_ARM_DRAW
                - ArcaneCreatureModel.BANSHEE_WARNING_PULSE_FLARE,
            warningPulse.rightArmZ()
        );

        final Pose lament = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.LAMENT, 0, 1.0F);
        assertExact(baseline.headX() + ArcaneCreatureModel.BANSHEE_LAMENT_HEAD_DROP, lament.headX());
        assertExact(baseline.bodyX() + ArcaneCreatureModel.BANSHEE_LAMENT_SHOULDER_DROP, lament.bodyX());
        assertExact(baseline.rightWingZ() * 0.5F, lament.rightWingZ());

        final Pose recoil = pose(model, com.kadamitas.warlockery.entity.BansheeRules.Mode.RECOIL, 0, 1.0F);
        assertExact(baseline.bodyX() + ArcaneCreatureModel.BANSHEE_RECOIL_COMPRESSION, recoil.bodyX());
        assertExact(
            baseline.rightWingZ()
                - net.minecraft.util.Mth.sin(1.0F * 1.3F) * ArcaneCreatureModel.BANSHEE_RECOIL_WING_BEAT,
            recoil.rightWingZ()
        );
    }

    @Test
    void nonBansheePosesAreIsolatedFromThePresentationChannel() {
        final ArcaneCreatureModel spectre = ArcaneCreatureModel.create(CreatureModelProfile.forEntity(
            "spectre",
            com.kadamitas.warlockery.entity.CreatureVisualProfile
                .forKind(com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind.SPECTRE)
        ));
        final Pose plain = pose(spectre, null, 0, 1.0F);
        final Pose withStaleFacts = pose(spectre, null, 7, 1.0F);
        org.junit.jupiter.api.Assertions.assertEquals(plain, withStaleFacts,
            "a non-Banshee render state never consumes the Banshee presentation channel");
    }

    private static CreatureModelProfile bansheeProfile() {
        return CreatureModelProfile.forEntity(
            "banshee",
            com.kadamitas.warlockery.entity.CreatureVisualProfile
                .forKind(com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind.BANSHEE)
        );
    }

    private static Pose pose(
        final ArcaneCreatureModel model,
        final com.kadamitas.warlockery.entity.BansheeRules.Mode activity,
        final int pulseSequence,
        final float ageInTicks
    ) {
        final TexturedCreatureRenderers.ArcaneState state = new TexturedCreatureRenderers.ArcaneState();
        state.bansheeActivity = activity;
        state.bansheePulseSequence = pulseSequence;
        state.ageInTicks = ageInTicks;
        state.walkAnimationPos = 0.0F;
        state.walkAnimationSpeed = 0.0F;
        model.setupAnim(state);
        final ModelPart root = model.root();
        return new Pose(
            root.getChild("head").xRot,
            root.getChild("body").xRot,
            root.getChild("right_arm").zRot,
            root.getChild("left_arm").zRot,
            root.getChild("right_wing").zRot,
            root.getChild("left_wing").zRot
        );
    }

    private static void assertExact(final float expected, final float actual) {
        assertTrue(Math.abs(expected - actual) < 1.0E-6F, expected + " != " + actual);
    }

    private record Pose(
        float headX,
        float bodyX,
        float rightArmZ,
        float leftArmZ,
        float rightWingZ,
        float leftWingZ
    ) {
    }
}
