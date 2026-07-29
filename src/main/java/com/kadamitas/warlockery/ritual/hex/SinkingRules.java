package com.kadamitas.warlockery.ritual.hex;

import net.minecraft.world.phys.Vec3;

public final class SinkingRules {
    public static final double HORIZONTAL_MULTIPLIER = 0.65;
    public static final double MAXIMUM_VERTICAL_VELOCITY = -0.08;

    private SinkingRules() {
    }

    public static boolean shouldSink(final double taggedFluidHeight) {
        return taggedFluidHeight > 0.0;
    }

    public static Vec3 burden(final Vec3 movement) {
        return new Vec3(
            movement.x * HORIZONTAL_MULTIPLIER,
            Math.min(movement.y, MAXIMUM_VERTICAL_VELOCITY),
            movement.z * HORIZONTAL_MULTIPLIER
        );
    }
}
