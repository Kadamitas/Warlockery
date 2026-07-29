package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class ArcaneCreatureModelTest {
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
        final Map<Archetype, Long> minimumParts = Map.of(
            Archetype.FELINE, 7L,
            Archetype.AVIAN, 7L,
            Archetype.AMPHIBIAN, 6L,
            Archetype.MOUNT, 7L,
            Archetype.CANINE, 7L,
            Archetype.PLANTLING, 7L,
            Archetype.PLANT_BRUTE, 7L,
            Archetype.ARTHROPOD, 10L,
            Archetype.LYCAN, 7L,
            Archetype.BOSS, 7L
        );
        minimumParts.forEach((archetype, minimum) ->
            assertTrue(solidPartCount(ArcaneCreatureModel.createLayer(archetype).bakeRoot()) >= minimum, archetype.name())
        );
    }

    private static long solidPartCount(final ModelPart root) {
        return root.getAllParts().stream().filter(part -> !part.isEmpty()).count();
    }
}
