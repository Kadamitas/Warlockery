package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.WolfMouthItemPresentation.DisplayContext;
import com.kadamitas.warlockery.transformation.WolfMouthItemPresentation.Hand;
import com.kadamitas.warlockery.transformation.WolfMouthItemPresentation.Mode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WolfMouthItemPresentationTest {
    @Test
    void onlyFourLeggedWerewolvesReplaceVanillaHandPresentation() {
        for (final SupernaturalForm form : SupernaturalForm.values()) {
            for (final WerewolfShape shape : WerewolfShape.values()) {
                final WolfMouthItemPresentation presentation = WolfMouthItemPresentation.resolve(
                    form,
                    shape,
                    false,
                    false
                );
                final boolean expectedMouth = form == SupernaturalForm.WEREWOLF && shape == WerewolfShape.WOLF;
                assertEquals(expectedMouth, presentation.presentsAtMouth(), form + " " + shape);
                assertEquals(!expectedMouth, presentation.usesVanillaHandPresentation(), form + " " + shape);
                assertEquals(expectedMouth ? Mode.MOUTH : Mode.VANILLA, presentation.mode(), form + " " + shape);
            }
        }
    }

    @Test
    void mainHandTakesPrecedenceAndIsTheSuppressedVanillaState() {
        final WolfMouthItemPresentation presentation = WolfMouthItemPresentation.resolve(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            false,
            false
        );

        assertEquals(Optional.of(Hand.MAIN), presentation.mouthHand());
        assertEquals(Optional.of(Hand.MAIN), presentation.suppressedVanillaHand());
    }

    @Test
    void offhandIsUsedWhenTheMainHandIsEmpty() {
        final WolfMouthItemPresentation presentation = WolfMouthItemPresentation.resolve(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            true,
            false
        );

        assertEquals(Mode.MOUTH, presentation.mode());
        assertEquals(Optional.of(Hand.OFFHAND), presentation.mouthHand());
        assertEquals(Optional.of(Hand.OFFHAND), presentation.suppressedVanillaHand());
    }

    @Test
    void emptyWolfHandsProduceNoMouthOrSuppression() {
        final WolfMouthItemPresentation presentation = WolfMouthItemPresentation.resolve(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            true,
            true
        );

        assertEquals(Mode.NONE, presentation.mode());
        assertFalse(presentation.presentsAtMouth());
        assertFalse(presentation.usesVanillaHandPresentation());
        assertTrue(presentation.mouthHand().isEmpty());
        assertTrue(presentation.suppressedVanillaHand().isEmpty());
    }

    @Test
    void mouthPoseIsExactFiniteAndSidewaysAtTheMuzzle() {
        final WolfMouthItemPresentation.MouthPose pose = WolfMouthItemPresentation.resolve(
            SupernaturalForm.WEREWOLF,
            WerewolfShape.WOLF,
            false,
            true
        ).pose();

        assertEquals(0.0F, pose.translateX());
        assertEquals(0.27F, pose.translateY());
        assertEquals(-0.5F, pose.translateZ());
        assertEquals(90.0F, pose.rotateXDegrees());
        assertEquals(0.0F, pose.rotateYDegrees());
        assertEquals(90.0F, pose.rotateZDegrees());
        assertEquals(0.75F, pose.scale());
        assertEquals(DisplayContext.GROUND, pose.displayContext());
        assertTrue(Float.isFinite(pose.translateX()));
        assertTrue(Float.isFinite(pose.translateY()));
        assertTrue(Float.isFinite(pose.translateZ()));
        assertTrue(Float.isFinite(pose.rotateXDegrees()));
        assertTrue(Float.isFinite(pose.rotateYDegrees()));
        assertTrue(Float.isFinite(pose.rotateZDegrees()));
        assertTrue(Float.isFinite(pose.scale()));
    }
}
