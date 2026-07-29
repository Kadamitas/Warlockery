package com.kadamitas.warlockery.brew;

import net.minecraft.world.phys.Vec3;

public final class BrewPhysics {
    private static final double MIN_DISTANCE_SQUARED = 1.0E-6;

    private BrewPhysics() {
    }

    public static Vec3 radialVelocity(
        final Vec3 center,
        final Vec3 subject,
        final double strength,
        final boolean inward
    ) {
        final double safeStrength = Math.clamp(strength, 0.0, 4.0);
        Vec3 direction = inward ? center.subtract(subject) : subject.subtract(center);
        direction = new Vec3(direction.x, 0.0, direction.z);
        if (direction.lengthSqr() < MIN_DISTANCE_SQUARED) {
            return new Vec3(0.0, safeStrength * 0.25, 0.0);
        }
        final Vec3 horizontal = direction.normalize().scale(safeStrength);
        return new Vec3(horizontal.x, Math.min(0.4, safeStrength * 0.2), horizontal.z);
    }
}
