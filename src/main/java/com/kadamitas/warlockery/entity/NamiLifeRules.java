package com.kadamitas.warlockery.entity;

import java.util.Objects;

public final class NamiLifeRules {
    public static final int DECISION_INTERVAL_TICKS = 40;
    public static final int DISCOVERY_INTERVAL_TICKS = 200;
    public static final int NAVIGATION_INTERVAL_TICKS = 40;
    public static final int MAX_BLOCK_STATES_EXAMINED = 256;
    public static final int MAX_SOCIAL_CANDIDATES = 16;
    public static final int SOCIAL_RADIUS = 8;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_FAILURE_COOLDOWN_TICKS = 100;
    public static final int ACTIVITY_COMMITMENT_TICKS = 100;
    public static final int VISITOR_MEMORY_TICKS = 12_000;
    public static final int GREETING_COOLDOWN_TICKS = 200;
    public static final int AGGRESSOR_MEMORY_TICKS = 1_200;
    public static final int WARD_CHARGE_TICKS = 20;
    public static final int WARD_COOLDOWN_TICKS = 80;
    public static final float WITHDRAW_HEALTH_FRACTION = 0.30F;

    private NamiLifeRules() {
    }

    public static Activity scheduledActivity(final long dayTime) {
        final long time = Math.floorMod(dayTime, 24_000L);
        if (time < 3_000L) {
            return Activity.APOTHECARY;
        }
        if (time < 9_000L) {
            return Activity.HERB_WALK;
        }
        if (time < 13_000L) {
            return Activity.SOCIAL_VISIT;
        }
        return Activity.SHELTER;
    }

    public static Activity chooseActivity(final ActivityContext context) {
        Objects.requireNonNull(context, "context");
        if (context.hazard() || context.lowHealth()) {
            return Activity.WITHDRAW;
        }
        if (context.actionableThreat()) {
            return Activity.WARD;
        }
        if (context.spouseRoutineActive()) {
            return Activity.SPOUSE_ROUTINE;
        }
        if (context.severeWeather()) {
            return Activity.SHELTER;
        }
        if (context.targetValid() && context.currentActivity() != Activity.IDLE
            && context.now() <= context.commitUntil()) {
            return context.currentActivity();
        }
        return scheduledActivity(context.dayTime());
    }

    public static Defense chooseDefense(final DefenseContext context) {
        Objects.requireNonNull(context, "context");
        if (context.lowHealth()) {
            return Defense.WITHDRAW;
        }
        if (context.playerAggressor()) {
            return Defense.WARN;
        }
        if (context.hasWardTarget() && (!context.actionableMonster() || context.spouseTarget() || context.stale())) {
            return Defense.RELEASE;
        }
        if (context.actionableMonster()) {
            return Defense.WARD;
        }
        return Defense.NONE;
    }

    public static boolean shouldDecide(final long tick, final int entityId) {
        return Math.floorMod(tick + entityId, DECISION_INTERVAL_TICKS) == 0L;
    }

    public static boolean shouldDiscover(final long now, final long nextDiscoveryAt) {
        return now >= nextDiscoveryAt;
    }

    public static boolean shouldPollSpouseRoutine(final long tick, final boolean actionStored) {
        return actionStored || Math.floorMod(tick, SpouseAmbientRules.DECISION_INTERVAL_TICKS) == 0L;
    }

    public static boolean mayRequestNavigation(
        final long now,
        final long lastRequestAt,
        final int failures,
        final long retryAfter
    ) {
        if (failures < 0) {
            throw new IllegalArgumentException("Route failures must be nonnegative");
        }
        return now - lastRequestAt >= NAVIGATION_INTERVAL_TICKS
            && (failures < MAX_ROUTE_FAILURES || now >= retryAfter);
    }

    public static long retryAfterFailure(final long now, final int failures) {
        if (failures < 0) {
            throw new IllegalArgumentException("Route failures must be nonnegative");
        }
        return failures >= MAX_ROUTE_FAILURES
            ? Math.addExact(now, ROUTE_FAILURE_COOLDOWN_TICKS)
            : now;
    }

    public enum Activity {
        IDLE,
        APOTHECARY,
        HERB_WALK,
        SOCIAL_VISIT,
        SHELTER,
        SPOUSE_ROUTINE,
        WARD,
        WITHDRAW
    }

    public enum Defense {
        NONE,
        WARN,
        WARD,
        WITHDRAW,
        RELEASE
    }

    public enum Interruption {
        RESUME,
        REPLAN,
        ABORT,
        COMPLETE
    }

    public record ActivityContext(
        long dayTime,
        long now,
        Activity currentActivity,
        long commitUntil,
        boolean targetValid,
        boolean hazard,
        boolean lowHealth,
        boolean actionableThreat,
        boolean spouseRoutineActive,
        boolean severeWeather
    ) {
        public ActivityContext {
            Objects.requireNonNull(currentActivity, "currentActivity");
        }
    }

    public record DefenseContext(
        boolean lowHealth,
        boolean hasWardTarget,
        boolean playerAggressor,
        boolean actionableMonster,
        boolean stale,
        boolean spouseTarget
    ) {
    }
}
