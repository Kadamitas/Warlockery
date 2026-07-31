package com.kadamitas.warlockery.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class AdvancedMutationRulesTest {
    @TestFactory
    Stream<DynamicContainer> oneSuitePerAdvancedMutation() {
        return Stream.of(
            DynamicContainer.dynamicContainer("toad", List.of(
                DynamicTest.dynamicTest("failure", this::incompleteToadFails),
                DynamicTest.dynamicTest("diagnostic", this::toadListsEveryMissingCondition),
                DynamicTest.dynamicTest("success", this::completeToadCreatesOnePerSnare)
            )),
            DynamicContainer.dynamicContainer("owl", List.of(
                DynamicTest.dynamicTest("failure", this::incompleteOwlFails),
                DynamicTest.dynamicTest("diagnostic", this::owlListsEveryMissingCondition),
                DynamicTest.dynamicTest("success", this::completeOwlCreatesOnePerSnare)
            )),
            DynamicContainer.dynamicContainer("minedrake", List.of(
                DynamicTest.dynamicTest("failure", this::incompleteMinedrakeFails),
                DynamicTest.dynamicTest("diagnostic", this::minedrakeListsEveryMissingCondition),
                DynamicTest.dynamicTest("success", this::completeMinedrakeTransformsFourCrops)
            ))
        );
    }

    @Test
    void patternUsesFourCardinalAndFourDiagonalSlots() {
        final BlockPos center = new BlockPos(10, 64, -3);
        assertEquals(4, AdvancedMutationLayout.cardinalRays(center).size());
        assertEquals(4, AdvancedMutationLayout.diagonalRays(center).size());
        assertTrue(AdvancedMutationLayout.cardinalRays(center).stream()
            .allMatch(ray -> ray.size() == AdvancedMutationLayout.MAX_DISTANCE));
        assertTrue(AdvancedMutationLayout.diagonalRays(center).stream()
            .flatMap(List::stream)
            .allMatch(position -> position.getY() == center.getY()
                && Math.abs(position.getX() - center.getX()) == Math.abs(position.getZ() - center.getZ())));
    }

    @Test
    void invalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AdvancedMutationSnapshot(
            true, true, -1, 4, 3, 1, 0, 0, 1, 0, 0
        ));
    }

    @Test
    void sprigPreservesLegacyBlockConversions() {
        assertEquals(
            MutatingSprigRules.Transformation.MYCELIUM,
            MutatingSprigRules.transformation(true, false, false, false)
        );
        assertEquals(
            MutatingSprigRules.Transformation.DIRT,
            MutatingSprigRules.transformation(false, true, false, false)
        );
        assertEquals(
            MutatingSprigRules.Transformation.CLAY,
            MutatingSprigRules.transformation(true, false, false, true)
        );
        assertEquals(
            MutatingSprigRules.Transformation.DIRT,
            MutatingSprigRules.transformation(false, false, true, true)
        );
    }

    private void incompleteToadFails() {
        assertFalse(AdvancedMutationRules.assess(AdvancedMutationKind.TOAD, toadSnapshot(1, 2, 0, 0)).complete());
    }

    private void toadListsEveryMissingCondition() {
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(
            AdvancedMutationKind.TOAD,
            toadSnapshot(1, 2, 0, 0)
        );
        assertTrue(assessment.diagnostic().contains("slime-filled Critter Snares (1/4)"));
        assertTrue(assessment.diagnostic().contains("diagonal Grasspers (2/4)"));
        assertTrue(assessment.diagnostic().contains("charged Attuned Stone"));
        assertTrue(assessment.diagnostic().contains("cat or ocelot host"));
        assertEquals(AdvancedMutationKind.TOAD, AdvancedMutationRules.select(toadSnapshot(1, 2, 0, 1)).kind());
    }

    private void completeToadCreatesOnePerSnare() {
        final AdvancedMutationSnapshot snapshot = toadSnapshot(5, 4, 1, 1);
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(AdvancedMutationKind.TOAD, snapshot);
        assertTrue(assessment.complete());
        assertEquals("\u2713 Toad mutation is ready", assessment.diagnostic());
        assertEquals(5, snapshot.slimeSnares());
    }

    private void incompleteOwlFails() {
        assertFalse(AdvancedMutationRules.assess(AdvancedMutationKind.OWL, owlSnapshot(2, 3, 0, 0)).complete());
    }

    private void owlListsEveryMissingCondition() {
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(
            AdvancedMutationKind.OWL,
            owlSnapshot(2, 3, 0, 0)
        );
        assertTrue(assessment.diagnostic().contains("bat-filled Critter Snares (2/4)"));
        assertTrue(assessment.diagnostic().contains("diagonal Grasspers (3/4)"));
        assertTrue(assessment.diagnostic().contains("charged Attuned Stone"));
        assertTrue(assessment.diagnostic().contains("wolf host"));
        assertEquals(AdvancedMutationKind.OWL, AdvancedMutationRules.select(owlSnapshot(4, 4, 1, 1)).kind());
    }

    private void completeOwlCreatesOnePerSnare() {
        final AdvancedMutationSnapshot snapshot = owlSnapshot(4, 4, 1, 1);
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(AdvancedMutationKind.OWL, snapshot);
        assertTrue(assessment.complete());
        assertEquals("\u2713 Owl mutation is ready", assessment.diagnostic());
        assertEquals(AdvancedMutationRules.REQUIRED_BAT_SNARES, snapshot.batSnares());
    }

    private void incompleteMinedrakeFails() {
        assertFalse(AdvancedMutationRules.assess(
            AdvancedMutationKind.MINEDRAKE,
            minedrakeSnapshot(3, 3, 0, 1, 0)
        ).complete());
    }

    private void minedrakeListsEveryMissingCondition() {
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(
            AdvancedMutationKind.MINEDRAKE,
            minedrakeSnapshot(3, 3, 0, 1, 0)
        );
        assertTrue(assessment.diagnostic().contains("mature cardinal Mandrakes (3/4)"));
        assertTrue(assessment.diagnostic().contains("diagonal Grasspers (3/4)"));
        assertTrue(assessment.diagnostic().contains("Focused Will"));
        assertTrue(assessment.diagnostic().contains("living Mandrake host"));
        assertEquals(
            AdvancedMutationKind.MINEDRAKE,
            AdvancedMutationRules.select(minedrakeSnapshot(4, 4, 1, 1, 1)).kind()
        );
    }

    private void completeMinedrakeTransformsFourCrops() {
        final AdvancedMutationSnapshot snapshot = minedrakeSnapshot(4, 4, 1, 1, 1);
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.assess(
            AdvancedMutationKind.MINEDRAKE,
            snapshot
        );
        assertTrue(assessment.complete());
        assertEquals("\u2713 Dreamroot mutation is ready", assessment.diagnostic());
        assertEquals(AdvancedMutationRules.REQUIRED_MANDRAKE_CROPS, snapshot.matureCardinalMandrakes());
    }

    private static AdvancedMutationSnapshot toadSnapshot(
        final int snares,
        final int grasspers,
        final int chargedStones,
        final int hosts
    ) {
        return new AdvancedMutationSnapshot(
            true,
            true,
            snares,
            grasspers,
            3,
            chargedStones,
            0,
            0,
            hosts,
            0,
            0
        );
    }

    private static AdvancedMutationSnapshot minedrakeSnapshot(
        final int crops,
        final int grasspers,
        final int focusedWill,
        final int creepers,
        final int livingMandrakes
    ) {
        return new AdvancedMutationSnapshot(
            true,
            true,
            0,
            grasspers,
            2,
            1,
            focusedWill,
            crops,
            0,
            creepers,
            livingMandrakes
        );
    }

    private static AdvancedMutationSnapshot owlSnapshot(
        final int snares,
        final int grasspers,
        final int chargedStones,
        final int hosts
    ) {
        return new AdvancedMutationSnapshot(
            true,
            true,
            0,
            grasspers,
            3,
            chargedStones,
            0,
            0,
            0,
            0,
            0,
            snares,
            hosts
        );
    }
}
