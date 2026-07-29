package com.kadamitas.warlockery.item;

import net.minecraft.world.phys.Vec3;

public final class GazeGeometry {
    private GazeGeometry() {
    }

    public static boolean faces(final Vec3 lookDirection, final Vec3 offsetToTarget, final double minimumDot) {
        if (lookDirection.lengthSqr() < 1.0E-8 || offsetToTarget.lengthSqr() < 1.0E-8) {
            return false;
        }
        return lookDirection.normalize().dot(offsetToTarget.normalize()) >= Math.clamp(minimumDot, -1.0, 1.0);
    }
}
