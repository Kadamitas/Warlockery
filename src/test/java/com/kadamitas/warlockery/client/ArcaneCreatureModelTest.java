package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class ArcaneCreatureModelTest {
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
