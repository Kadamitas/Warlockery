package com.kadamitas.warlockery.entity;

import java.util.Objects;
import java.util.UUID;

public final class LycanVillagerRules {
    public static final double MAX_HEALTH = 20.0;
    public static final double MOVEMENT_SPEED = 0.5;
    public static final double FOLLOW_RANGE = 16.0;
    public static final double ATTACK_DAMAGE = 6.0;
    public static final int FAMILIARITY_CAP = 4;
    public static final int MAX_FAMILIARITY = 8;
    public static final int FAMILIARITY_GAIN_TICKS = 200;
    public static final int FAMILIARITY_DECAY_TICKS = 72_000;
    public static final int DECISION_CADENCE_TICKS = 20;
    public static final int NEARBY_OBSERVATION_TICKS = 100;
    public static final int LUNAR_OBSERVATION_TICKS = 200;
    public static final int FEEDBACK_CADENCE_TICKS = 200;
    public static final int NAVIGATION_CADENCE_TICKS = 20;
    public static final int WATCH_TICKS = 200;
    public static final int WARNING_TICKS = 20;
    public static final int PURSUIT_TICKS = 200;
    public static final int WITHDRAW_TICKS = 100;
    public static final int TRADE_FAMILIARITY_POINTS = 2;
    public static final int TRADE_FAMILIARITY_COOLDOWN_TICKS = 1_200;
    public static final int TRADE_COOLDOWN_CAP = 16;
    public static final int EVIDENCE_FRESHNESS_TICKS = 40;
    public static final int HOUSEHOLD_THRESHOLD = 6;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_RETRY_TICKS = 100;
    public static final double WITHDRAW_HEALTH_FRACTION = 0.35;

    private LycanVillagerRules() {}

    public enum Intent { ROUTINE, BOUNDARY_WATCH, MOON_WATCH, GREETING, RESERVE, WARNING, INTERCEPT, DEFEND, WITHDRAW, RETURN }
    public enum RelationshipSource { RESIDENT, PLAYER }

    public record WatchInputs(boolean night, boolean fullMoon, boolean skyVisible, boolean safe,
                              boolean raidOrHide, boolean panic, boolean trading, boolean sleeping,
                              boolean breeding, boolean hasAnchor) {}

    public static boolean mustCancelSentinel(final boolean alive, final boolean trading, final boolean sleeping,
                                             final boolean baby, final boolean raidOrHide, final boolean breeding) {
        return !alive || trading || sleeping || baby || raidOrHide || breeding;
    }

    public static boolean panicOverridesIntent(final Intent intent) {
        return intent == Intent.BOUNDARY_WATCH || intent == Intent.MOON_WATCH
            || intent == Intent.GREETING || intent == Intent.RESERVE;
    }

    public static boolean releasesAggressor(final boolean aggressorInvalid, final boolean lineOfSightBroken,
                                            final boolean warningPhase, final boolean evidenceStale,
                                            final boolean pursuitExpired, final boolean targetTooFar) {
        return aggressorInvalid || lineOfSightBroken || warningPhase && evidenceStale
            || pursuitExpired || targetTooFar;
    }

    public static Intent watchIntent(final WatchInputs input) {
        Objects.requireNonNull(input, "input");
        if (!input.night || !input.safe || !input.hasAnchor || input.raidOrHide || input.panic
            || input.trading || input.sleeping || input.breeding) return Intent.ROUTINE;
        return input.fullMoon && input.skyVisible ? Intent.MOON_WATCH : Intent.BOUNDARY_WATCH;
    }

    public static boolean canTransition(final Intent from, final Intent to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to == Intent.ROUTINE || to == Intent.WITHDRAW) return true;
        return switch (from) {
            case ROUTINE -> to == Intent.BOUNDARY_WATCH || to == Intent.MOON_WATCH || to == Intent.GREETING
                || to == Intent.RESERVE || to == Intent.WARNING;
            case BOUNDARY_WATCH, MOON_WATCH, GREETING, RESERVE -> to == Intent.WARNING;
            case WARNING -> to == Intent.INTERCEPT;
            case INTERCEPT -> to == Intent.DEFEND;
            case DEFEND -> false;
            case WITHDRAW -> to == Intent.RETURN;
            case RETURN -> false;
        };
    }

    public static int stagger(final UUID id, final int cadence) {
        Objects.requireNonNull(id, "id");
        if (cadence <= 0) throw new IllegalArgumentException("cadence must be positive");
        return Math.floorMod(id.hashCode(), cadence);
    }
}
