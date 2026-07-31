package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SeerCovenRules {
    private static final int MAGES_PER_RING = 12;

    private SeerCovenRules() {
    }

    public static boolean isCircleMageParticipant(final @Nullable CreatureKind kind, final boolean bound) {
        return bound && kind == CreatureKind.CIRCLE_MAGE;
    }

    public static String feedbackKey(final int calledMages) {
        return calledMages > 0
            ? "message.warlockery.seer_stone.coven_called"
            : "message.warlockery.seer_stone.no_coven";
    }

    public static Vec3 gatheringPosition(final BlockPos center, final int index, final int total) {
        if (index < 0 || index >= total) {
            throw new IllegalArgumentException("Mage index must be within the gathering");
        }
        final int ring = index / MAGES_PER_RING;
        final int firstInRing = ring * MAGES_PER_RING;
        final int ringSize = Math.min(MAGES_PER_RING, total - firstInRing);
        final int positionInRing = index - firstInRing;
        final double angle = Math.PI * 2.0 * positionInRing / ringSize;
        final double radius = 3.0 + ring * 1.5;
        return Vec3.atCenterOf(center).add(
            Math.cos(angle) * radius,
            1.0,
            Math.sin(angle) * radius
        );
    }
}
