package com.kadamitas.warlockery.entity;

public final class SpouseAmbientRules {
    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int KISS_ROLL_BOUND = 900;
    public static final int COOK_ROLL_BOUND = 1_200;
    public static final long KISS_COOLDOWN_TICKS = 12_000L;
    public static final long COOK_COOLDOWN_TICKS = 24_000L;
    public static final long ROUTINE_TIMEOUT_TICKS = 1_200L;

    public enum Routine {
        NONE,
        KISS,
        COOK
    }

    private SpouseAmbientRules() {
    }

    public static Routine choose(
        final Context context,
        final int kissRoll,
        final int cookRoll
    ) {
        if (kissRoll < 0 || cookRoll < 0) {
            throw new IllegalArgumentException("Ambient routine rolls must be nonnegative");
        }
        if (!context.available()) {
            return Routine.NONE;
        }
        if (context.hasCookWork() && context.gameTime() >= context.cookReadyAt() && cookRoll == 0) {
            return Routine.COOK;
        }
        if (context.gameTime() >= context.kissReadyAt() && kissRoll == 0) {
            return Routine.KISS;
        }
        return Routine.NONE;
    }

    public static long nextReadyAt(final Routine routine, final long gameTime) {
        return Math.addExact(gameTime, switch (routine) {
            case KISS -> KISS_COOLDOWN_TICKS;
            case COOK -> COOK_COOLDOWN_TICKS;
            case NONE -> 0L;
        });
    }

    public record Context(
        boolean married,
        boolean sameDimension,
        boolean peaceful,
        boolean safe,
        boolean adult,
        boolean emptyHand,
        boolean hasCookWork,
        long gameTime,
        long kissReadyAt,
        long cookReadyAt
    ) {
        public boolean available() {
            return married && sameDimension && peaceful && safe && adult && emptyHand;
        }
    }
}
