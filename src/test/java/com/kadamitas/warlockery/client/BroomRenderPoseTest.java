package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BroomRenderPoseTest {
    @Test
    void followsRiderYawAndPitchInsteadOfFacingTheCamera() {
        final BroomRenderPose pose = BroomRenderPose.resolve(45.0F, -30.0F, 0.0F, 0.5F, false, 0.0F);

        assertEquals(135.0F, pose.yawDegrees());
        assertEquals(-23.4F, pose.pitchDegrees(), 0.001F);
        assertEquals(1.55F, pose.scale());
    }

    @Test
    void banksIntoRiderTurnsAndClampsAbruptRotation() {
        final BroomRenderPose left = BroomRenderPose.resolve(0.0F, 0.0F, -12.0F, 0.0F, false, 0.0F);
        final BroomRenderPose right = BroomRenderPose.resolve(0.0F, 0.0F, 12.0F, 0.0F, false, 0.0F);
        final BroomRenderPose abrupt = BroomRenderPose.resolve(0.0F, 0.0F, 120.0F, 0.0F, false, 0.0F);

        assertTrue(left.rollDegrees() > 0.0F);
        assertTrue(right.rollDegrees() < 0.0F);
        assertEquals(-20.0F, abrupt.rollDegrees());
    }

    @Test
    void glideAnimationIsCalmerThanPoweredFlight() {
        final BroomRenderPose powered = BroomRenderPose.resolve(0.0F, 40.0F, 14.0F, 0.85F, false, 8.0F);
        final BroomRenderPose gliding = BroomRenderPose.resolve(0.0F, 40.0F, 14.0F, 0.85F, true, 8.0F);

        assertTrue(Math.abs(gliding.pitchDegrees()) < Math.abs(powered.pitchDegrees()));
        assertTrue(Math.abs(gliding.rollDegrees()) < Math.abs(powered.rollDegrees()));
        assertTrue(Math.abs(gliding.bob()) < 0.025F);
    }
}
