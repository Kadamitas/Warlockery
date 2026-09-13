package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.kadamitas.warlockery.entity.MimicryPresentation.Stance;
import org.junit.jupiter.api.Test;

final class MimicryPresentationTest {
    @Test
    void theFallbackIsNeverDerivedFromAnyEntity() {
        assertTrue(MimicryPresentation.fallback().presentedName().isEmpty());
        assertEquals(Stance.STILL, MimicryPresentation.fallback().stance());
    }

    @Test
    void stanceIsDerivedFromExactlyTwoPublicPoseFactsAndNoDirection() {
        assertEquals(Stance.CROUCHING, MimicryPresentation.stanceOf(true, 0.0D));
        assertEquals(Stance.CROUCHING, MimicryPresentation.stanceOf(true, 5.0D));
        assertEquals(Stance.STILL, MimicryPresentation.stanceOf(false, 0.0D));
        assertEquals(Stance.STILL,
            MimicryPresentation.stanceOf(false, MimicryPresentation.WALKING_SPEED_THRESHOLD));
        assertEquals(Stance.WALKING,
            MimicryPresentation.stanceOf(false, MimicryPresentation.WALKING_SPEED_THRESHOLD + 0.001D));
        assertEquals(Stance.STILL, MimicryPresentation.stanceOf(false, Double.NaN));
    }
}
