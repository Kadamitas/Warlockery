package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class BrewPhysicsTest {
    private static final double TOLERANCE = 1.0E-9;

    @Test
    void pushMovesAwayFromImpact() {
        final Vec3 velocity = BrewPhysics.radialVelocity(Vec3.ZERO, new Vec3(3.0, 2.0, 0.0), 1.0, false);
        assertEquals(1.0, velocity.x, TOLERANCE);
        assertEquals(0.2, velocity.y, TOLERANCE);
        assertEquals(0.0, velocity.z, TOLERANCE);
    }

    @Test
    void pullMovesTowardImpact() {
        final Vec3 velocity = BrewPhysics.radialVelocity(Vec3.ZERO, new Vec3(3.0, 2.0, 0.0), 1.0, true);
        assertEquals(-1.0, velocity.x, TOLERANCE);
        assertEquals(0.2, velocity.y, TOLERANCE);
    }

    @Test
    void coincidentSubjectReceivesSafeVerticalMotion() {
        assertEquals(new Vec3(0.0, 0.5, 0.0), BrewPhysics.radialVelocity(Vec3.ZERO, Vec3.ZERO, 2.0, false));
    }

    @Test
    void radialStrengthIsBounded() {
        final Vec3 velocity = BrewPhysics.radialVelocity(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 100.0, false);
        assertEquals(4.0, velocity.x, TOLERANCE);
        assertEquals(0.4, velocity.y, TOLERANCE);
    }
}
