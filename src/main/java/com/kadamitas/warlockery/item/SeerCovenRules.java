package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Optional;
import java.util.UUID;
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

    /**
     * Whether a Circle Mage counts towards the coven of the player who started the rite.
     *
     * <p>Being bound to somebody is not enough. A Mage sworn to another player standing near the circle used
     * to satisfy the coven requirement, so a rite could be cast on a coven its caster had never gathered.
     * Attribution is now exact: the Mage answers to this caster or it does not count. A caster who cannot be
     * resolved has no coven, because a coven cannot be attributed to nobody.</p>
     */
    public static boolean countsForCaster(
        final @Nullable CreatureKind kind,
        final Optional<UUID> owner,
        final Optional<UUID> caster
    ) {
        return isCircleMageParticipant(kind, owner.isPresent()) && owner.equals(caster);
    }

    /**
     * The most Mages one caster's coven may contribute. Applied where the coven is counted rather than
     * trusted from registration, because a save written before the cap can legitimately hold more rows for a
     * single owner and the recall path already reads its roster capped the same way.
     */
    public static int cappedCoven(final int present) {
        return Math.clamp(present, 0, CovenRosterData.MAX_PER_OWNER);
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
