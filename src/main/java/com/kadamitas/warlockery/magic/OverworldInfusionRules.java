package com.kadamitas.warlockery.magic;

import net.minecraft.world.phys.Vec3;

public final class OverworldInfusionRules {
    private OverworldInfusionRules() {
    }

    public static int transmutationDropCopies() {
        return 2;
    }

    public static Vec3 launchedBlockVelocity(final Vec3 lookDirection) {
        final Vec3 direction = lookDirection.lengthSqr() == 0.0
            ? new Vec3(0.0, 0.0, 1.0)
            : lookDirection.normalize();
        return direction.scale(1.35).add(0.0, 0.45, 0.0);
    }
}
