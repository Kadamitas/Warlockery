package com.kadamitas.warlockery.ritual;

import net.minecraft.world.phys.Vec3;

public final class RitualWardRules {
    private RitualWardRules() {
    }

    public static boolean contains(final Vec3 center, final int radius, final Vec3 position) {
        return radius > 0 && center.distanceToSqr(position) <= (double) radius * radius;
    }

    public static Vec3 inwardVelocity(final Vec3 center, final Vec3 position, final Vec3 velocity) {
        final Vec3 inward = center.subtract(position);
        if (inward.lengthSqr() < 0.0001) {
            return new Vec3(0.0, Math.min(velocity.y, 0.0), 0.0);
        }
        final Vec3 correction = inward.normalize().scale(0.45);
        return velocity.scale(0.2).add(correction.x, Math.min(correction.y, 0.15), correction.z);
    }

    public static Vec3 outwardVelocity(final Vec3 center, final Vec3 position, final Vec3 velocity) {
        final Vec3 outward = position.subtract(center);
        if (outward.lengthSqr() < 0.0001) {
            return new Vec3(0.0, Math.max(velocity.y, 0.0), 0.45);
        }
        final Vec3 correction = outward.normalize().scale(0.45);
        return velocity.scale(0.2).add(correction.x, Math.max(correction.y, -0.15), correction.z);
    }

    public static boolean shouldRepel(
        final boolean wardActive,
        final boolean living,
        final boolean hostileOrTagged,
        final boolean immune
    ) {
        return wardActive && living && hostileOrTagged && !immune;
    }
}
