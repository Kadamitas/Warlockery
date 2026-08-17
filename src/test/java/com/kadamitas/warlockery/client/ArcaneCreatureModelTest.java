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
}
