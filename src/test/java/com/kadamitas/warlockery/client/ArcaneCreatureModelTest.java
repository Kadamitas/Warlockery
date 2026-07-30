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
