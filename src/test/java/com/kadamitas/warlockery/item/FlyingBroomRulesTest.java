package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.BrewFactory;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class FlyingBroomRulesTest {
    @Test
    void missingRiderOrStoredBroomStopsPropulsion() {
        assertFalse(FlyingBroomRules.decide(false, true, false, false).active());
        assertFalse(FlyingBroomRules.decide(true, false, false, false).active());
    }

    @Test
    void soaringImprovesTorqueWithoutIncreasingThrustOrTopSpeed() {
        final FlyingBroomRules.FlightDecision normal = FlyingBroomRules.decide(true, true, false, false);
        final FlyingBroomRules.FlightDecision soaring = FlyingBroomRules.decide(true, true, false, true);
        assertTrue(soaring.torque() > normal.torque());
        assertEquals(normal.thrust(), soaring.thrust());
        assertEquals(normal.maxSpeed(), soaring.maxSpeed());
    }

    @Test
    void pressingForwardPropelsAlongVehicleYaw() {
        final Vec3 movement = FlyingBroomRules.nextVelocity(
            Vec3.ZERO,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            FlyingBroomRules.decide(true, true, false, false)
        );
        assertTrue(movement.z > 0.0D);
        assertEquals(0.0D, movement.y);
    }

    @Test
    void cameraPitchProvidesHappyGhastStyleClimbAndDive() {
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(true, true, false, false);
        final FlyingBroomRules.ControlInput forward = new FlyingBroomRules.ControlInput(0.0D, 1.0D, false);
        final Vec3 climbing = FlyingBroomRules.nextVelocity(Vec3.ZERO, 0.0F, -35.0F, forward, decision);
        final Vec3 diving = FlyingBroomRules.nextVelocity(Vec3.ZERO, 0.0F, 35.0F, forward, decision);
        assertTrue(climbing.y > 0.0D);
        assertTrue(diving.y < 0.0D);
        assertEquals(climbing.horizontalDistance(), diving.horizontalDistance(), 1.0E-9D);
    }

    @Test
    void jumpProvidesLiftWithoutChangingHorizontalControls() {
        final Vec3 movement = FlyingBroomRules.nextVelocity(
            Vec3.ZERO,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 0.0D, true),
            FlyingBroomRules.decide(true, true, false, false)
        );
        assertTrue(movement.y > 0.0D);
    }

    @Test
    void wasdProvidesForwardReverseAndLateralRocketControls() {
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(true, true, false, false);
        final Vec3 forward = FlyingBroomRules.nextVelocity(
            Vec3.ZERO, 0.0F, new FlyingBroomRules.ControlInput(0.0D, 1.0D, false), decision
        );
        final Vec3 reverse = FlyingBroomRules.nextVelocity(
            Vec3.ZERO, 0.0F, new FlyingBroomRules.ControlInput(0.0D, -1.0D, false), decision
        );
        final Vec3 left = FlyingBroomRules.nextVelocity(
            Vec3.ZERO, 0.0F, new FlyingBroomRules.ControlInput(-1.0D, 0.0D, false), decision
        );
        final Vec3 right = FlyingBroomRules.nextVelocity(
            Vec3.ZERO, 0.0F, new FlyingBroomRules.ControlInput(1.0D, 0.0D, false), decision
        );
        assertTrue(forward.z > 0.0D);
        assertTrue(reverse.z < 0.0D);
        assertEquals(forward.length() * 0.5D, reverse.length(), 1.0E-9D);
        assertTrue(left.x > 0.0D);
        assertTrue(right.x < 0.0D);
    }

    @Test
    void forwardThrottleReachesRocketSpeedQuickly() {
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(true, true, false, false);
        final FlyingBroomRules.ControlInput throttle = new FlyingBroomRules.ControlInput(0.0D, 1.0D, false);
        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 3; tick++) {
            velocity = FlyingBroomRules.nextVelocity(velocity, 0.0F, throttle, decision);
        }
        assertTrue(velocity.horizontalDistance() > 0.45D);
    }

    @Test
    void poweredBroomHoversWhenNoControlIsPressed() {
        final Vec3 movement = FlyingBroomRules.nextVelocity(
            Vec3.ZERO,
            0.0F,
            FlyingBroomRules.ControlInput.IDLE,
            FlyingBroomRules.decide(true, true, false, false)
        );
        assertEquals(Vec3.ZERO, movement);
    }

    @Test
    void baseHeadingUsesHappyGhastTurningWhileSoaringSuppliesExtraTorque() {
        final float normalYaw = FlyingBroomRules.nextYaw(0.0F, 90.0F, FlyingBroomRules.NORMAL_TORQUE);
        final float soaringYaw = FlyingBroomRules.nextYaw(0.0F, 90.0F, FlyingBroomRules.SOARING_TORQUE);
        assertEquals(7.2F, normalYaw, 0.001F);
        assertTrue(soaringYaw > normalYaw * 2.0F);
        assertTrue(soaringYaw < 90.0F);
    }

    @Test
    void soaringTurnsExistingMomentumMoreSharply() {
        final Vec3 current = new Vec3(0.55D, 0.0D, 0.0D);
        final Vec3 normal = FlyingBroomRules.nextVelocity(
            current,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            FlyingBroomRules.decide(true, true, false, false)
        );
        final Vec3 soaring = FlyingBroomRules.nextVelocity(
            current,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            FlyingBroomRules.decide(true, true, false, true)
        );
        assertTrue(soaring.z > normal.z);
        assertTrue(soaring.x < normal.x);
    }

    @Test
    void glideAddsNoThrustAndCapsDescentWithoutHovering() {
        final FlyingBroomRules.FlightDecision glide = FlyingBroomRules.decide(true, true, true, false);
        final Vec3 stationary = FlyingBroomRules.nextVelocity(
            Vec3.ZERO, 0.0F, FlyingBroomRules.ControlInput.IDLE, glide
        );
        final Vec3 falling = FlyingBroomRules.nextVelocity(
            new Vec3(0.45D, -2.0D, 0.0D),
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            glide
        );
        assertEquals(0.0D, glide.thrust());
        assertEquals(0.0D, stationary.horizontalDistanceSqr());
        assertTrue(stationary.y <= 0.0D);
        assertEquals(FlyingBroomRules.GLIDE_DESCENT, falling.y);
    }

    @Test
    void everyFlightModeClampsRunawayVelocity() {
        final Vec3 runaway = new Vec3(8.0D, 4.0D, -6.0D);
        final Vec3 powered = FlyingBroomRules.nextVelocity(
            runaway,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            FlyingBroomRules.decide(true, true, false, false)
        );
        final Vec3 glide = FlyingBroomRules.nextVelocity(
            runaway,
            0.0F,
            new FlyingBroomRules.ControlInput(0.0D, 1.0D, false),
            FlyingBroomRules.decide(true, true, true, true)
        );
        assertTrue(powered.length() <= FlyingBroomRules.MAX_SPEED + 1.0E-9D);
        assertTrue(glide.length() <= FlyingBroomRules.MAX_SPEED + 1.0E-9D);
    }

    @Test
    void glideUsesItsConfiguredSpeedLimit() {
        final FlyingBroomRules.FlightDecision glide = new FlyingBroomRules.FlightDecision(
            true, true, FlyingBroomRules.NORMAL_TORQUE, 0.0D, 0.4D
        );
        final Vec3 velocity = FlyingBroomRules.nextVelocity(
            new Vec3(3.0D, -1.0D, 0.0D),
            0.0F,
            FlyingBroomRules.ControlInput.IDLE,
            glide
        );
        assertTrue(velocity.length() <= 0.4D + 1.0E-9D);
    }

    @Test
    void collisionResolutionDropsOnlyBlockedMomentum() {
        final Vec3 resolved = FlyingBroomRules.retainUnblockedVelocity(
            new Vec3(0.8D, 0.2D, -0.4D),
            new Vec3(0.0D, 0.2D, -0.4D)
        );
        assertEquals(0.0D, resolved.x);
        assertEquals(0.2D, resolved.y);
        assertEquals(-0.4D, resolved.z);
    }

    @Test
    void staleOrTimeReversedControlsFailClosed() {
        assertTrue(FlyingBroomRules.controlsAreFresh(100L, 95L));
        assertFalse(FlyingBroomRules.controlsAreFresh(106L, 95L));
        assertFalse(FlyingBroomRules.controlsAreFresh(94L, 95L));
        assertFalse(FlyingBroomRules.controlsAreFresh(100L, Long.MIN_VALUE));
    }

    @Test
    void infusedSoaringBrewProvidesTorqueInsteadOfSlowFalling() {
        final var kind = BrewFactory.legacyKind("ingredient_brew_soaring").orElseThrow();
        assertTrue(kind.effects().stream().anyMatch(effect -> "warlockery:soaring".equals(effect.effect())));
        assertFalse(kind.effects().stream().anyMatch(effect -> "minecraft:slow_falling".equals(effect.effect())));
        assertEquals(20 * 60 * 120, kind.effects().getFirst().duration());
    }

    @Test
    void invalidMovementProfileIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new FlyingBroomRules.FlightDecision(true, false, 1.1D, 0.0D, 0.8D)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new FlyingBroomRules.FlightDecision(true, false, 0.1D, -0.1D, 0.8D)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new FlyingBroomRules.FlightDecision(true, false, Double.NaN, 0.1D, 0.8D)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new FlyingBroomRules.ControlInput(Double.NaN, 0.0D, false)
        );
    }
}
