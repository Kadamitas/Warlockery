package com.kadamitas.warlockery.transformation;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class WerewolfPreyDriveRules {
    public static final int CHECK_INTERVAL_TICKS = 40;
    public static final int COOLDOWN_TICKS = 180 * 20;
    public static final int TIMEOUT_TICKS = 10 * 20;
    public static final int TRIGGER_BOUND = 100;
    public static final double RANGE = 24.0;
    public static final double PURSUIT_ACCELERATION = 0.08;
    public static final double MAX_PURSUIT_SPEED = 0.38;

    private WerewolfPreyDriveRules() {
    }

    public static boolean shapeEligible(final WerewolfShape shape) {
        return shape == WerewolfShape.WOLF || shape == WerewolfShape.WOLFMAN;
    }

    public static boolean triggered(final int boundedRoll) {
        return boundedRoll == 0;
    }

    public static boolean eligible(final Candidate candidate) {
        return candidate.taggedPrey()
            && candidate.alive()
            && !candidate.baby()
            && !candidate.protectedIdentity()
            && !candidate.arcaneOrFamiliar()
            && !candidate.riding()
            && !candidate.invalidWorld()
            && !candidate.protectedAssaultTarget()
            && !candidate.outOfRange()
            && !candidate.noLineOfSight();
    }

    public static Optional<Target> select(final Collection<Target> targets) {
        return targets.stream().min(Comparator.comparingDouble(Target::distanceSquared).thenComparing(Target::id));
    }

    public static PursuitMotion pursuitMotion(
        final double currentX,
        final double currentZ,
        final double targetX,
        final double targetZ,
        final double vertical
    ) {
        final double distance = Math.hypot(targetX, targetZ);
        if (distance < 1.0E-6) {
            return new PursuitMotion(0.0, 0.0, vertical);
        }
        double x = currentX + targetX / distance * PURSUIT_ACCELERATION;
        double z = currentZ + targetZ / distance * PURSUIT_ACCELERATION;
        final double speed = Math.hypot(x, z);
        if (speed > MAX_PURSUIT_SPEED) {
            x = x / speed * MAX_PURSUIT_SPEED;
            z = z / speed * MAX_PURSUIT_SPEED;
        }
        return new PursuitMotion(x, z, vertical);
    }

    public static boolean cancelsEpisode(final PlayerCondition condition) {
        return condition.passenger()
            || condition.flying()
            || condition.fallFlying()
            || condition.inWater()
            || condition.drowning()
            || condition.inLavaOrFire()
            || condition.inPowderSnow()
            || condition.freezing();
    }

    public record Candidate(
        boolean taggedPrey,
        boolean alive,
        boolean baby,
        boolean protectedIdentity,
        boolean arcaneOrFamiliar,
        boolean riding,
        boolean invalidWorld,
        boolean protectedAssaultTarget,
        boolean outOfRange,
        boolean noLineOfSight
    ) {
    }

    public record Target(UUID id, double distanceSquared) {
    }

    public record PursuitMotion(double x, double z, double vertical) {
    }

    public record PlayerCondition(
        boolean passenger,
        boolean flying,
        boolean fallFlying,
        boolean inWater,
        boolean drowning,
        boolean inLavaOrFire,
        boolean inPowderSnow,
        boolean freezing
    ) {
    }
}
