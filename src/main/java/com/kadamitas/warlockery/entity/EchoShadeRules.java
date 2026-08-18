package com.kadamitas.warlockery.entity;

import java.util.Objects;

/**
 * Pure F21 Echo Shade policy. No world, entity, path, level or random state may enter this class.
 *
 * <p>An Echo Shade is the mirror's hostile answer, released by {@code summon_reflection} from a
 * mirror and a refined evil. It is not a dead soul and it has no memory of a person. Its one
 * species attention is a finite <em>echo</em>: mark one visible player, sample how that player is
 * moving for a short window, walk to the single bounded offset that answers the sampled motion,
 * make at most one ordinary attributed melee attempt from there, then recover.</p>
 *
 * <p>What it deliberately never does: it never reads or copies inventory, armor, effects,
 * attributes, identity, speech, input, NBT or powers from the player it marks; it never applies a
 * status effect; it never warns; it never flies; it never petitions at a memorial; it never binds
 * to an owner; and it never appoints more than one mark. Sampling a horizontal displacement is the
 * whole of its perception, and one melee attempt is the whole of its payload. That is what keeps it
 * a different being from the Spectre that shares its plan, and from the Lost Soul and Spirit
 * next door.</p>
 */
public final class EchoShadeRules {
    /** Total loaded ticks one echo may occupy from the moment a mark is taken. */
    public static final int EPISODE_TICKS = 400;
    /** The motion sampling window. Short: an Echo Shade answers a gesture, not a journey. */
    public static final int RECORD_TICKS = 40;
    /** Loaded ticks the shade may spend walking to the answer offset before it gives up. */
    public static final int ANSWER_TICKS = 120;
    /** Loaded ticks the single melee attempt may remain available once the offset is reached. */
    public static final int STRIKE_TICKS = 40;
    /** Bounded recovery after any echo, successful or not. */
    public static final int RECOVER_TICKS = 60;
    /** Cadence between echoes, armed by the recovery that ends one. */
    public static final int COOLDOWN_TICKS = 300;

    /** How often an idle shade may run one bounded appointment sweep. */
    public static final int WATCH_INTERVAL_TICKS = 40;
    /** How often a marked shade samples the mark's horizontal displacement. */
    public static final int SAMPLE_INTERVAL_TICKS = 5;
    public static final int MAX_SAMPLES = 8;

    public static final int MARK_RANGE = 12;
    public static final double MARK_RANGE_SQUARED = (double) MARK_RANGE * MARK_RANGE;
    /** Past this the mark has simply left; the echo is released rather than chased. */
    public static final int MARK_RELEASE_RANGE = 20;
    public static final double MARK_RELEASE_RANGE_SQUARED =
        (double) MARK_RELEASE_RANGE * MARK_RELEASE_RANGE;

    /** Horizontal reach of one answer offset. The mirror answers close, never across a valley. */
    public static final int MAX_ANSWER_OFFSET = 3;
    /** Millis of horizontal displacement one sample may contribute, per axis. */
    public static final int MAX_SAMPLE_MILLIS = 4_000;
    /** Millis of accumulated horizontal displacement the whole record window may retain, per axis. */
    public static final int MAX_RECORDED_MILLIS = 16_000;
    /** Recorded displacement below this is no gesture at all, so no echo is worth answering. */
    public static final int MIN_ANSWERABLE_MILLIS = 250;

    /** Squared distance at which the shade is standing on its answer and may attempt the strike. */
    public static final int ANSWER_BAND = 2;
    public static final double ANSWER_BAND_SQUARED = (double) ANSWER_BAND * ANSWER_BAND;
    /** Squared melee reach of the single attempt. */
    public static final int STRIKE_BAND = 2;
    public static final double STRIKE_BAND_SQUARED = (double) STRIKE_BAND * STRIKE_BAND;
    public static final int MAX_STRIKES = 1;

    public static final int DESTINATION_SEARCH_HORIZONTAL = 2;
    public static final int DESTINATION_SEARCH_VERTICAL = 1;
    public static final int ESCAPE_SEARCH_HORIZONTAL = 3;
    public static final int ESCAPE_SEARCH_VERTICAL = 2;

    public static final int MAX_ANSWER_PARTICLES = 6;

    private EchoShadeRules() {
    }

    /**
     * The complete Echo Shade phase set. There is deliberately no warning, no aura, no binding and
     * no flight phase: an Echo Shade watches, records, answers, strikes once, and recovers.
     */
    public enum Phase {
        WATCH,
        RECORD,
        ANSWER,
        STRIKE,
        RECOVER
    }

    public enum EchoEnd {
        NONE,
        MARK_LOST,
        DIMENSION,
        OUT_OF_RANGE,
        ROUTE_FAILURE,
        EXPIRED
    }

    /** The facts a runtime directly observed about the one current mark this decision. */
    public record MarkObservation(
        boolean present,
        boolean sameDimension,
        boolean loaded,
        boolean eligible,
        double distanceSquared,
        int episodeRemainingTicks,
        int routeFailures
    ) {
    }

    /** One sampled horizontal displacement, already reduced to clamped integer millis per axis. */
    public record MotionSample(int millisX, int millisZ) {
        public MotionSample {
            millisX = Math.clamp(millisX, -MAX_SAMPLE_MILLIS, MAX_SAMPLE_MILLIS);
            millisZ = Math.clamp(millisZ, -MAX_SAMPLE_MILLIS, MAX_SAMPLE_MILLIS);
        }
    }

    // ---------------------------------------------------------------- episode retention

    /**
     * Exact echo-end policy. A dimension mismatch wins, then a lost or ineligible mark, then a mark
     * that simply walked away, then the third route failure, then the loaded-time budget.
     */
    public static EchoEnd echoEnd(final MarkObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (!observation.present()) {
            return EchoEnd.MARK_LOST;
        }
        if (!observation.sameDimension()) {
            return EchoEnd.DIMENSION;
        }
        if (!observation.loaded() || !observation.eligible()) {
            return EchoEnd.MARK_LOST;
        }
        if (observation.distanceSquared() > MARK_RELEASE_RANGE_SQUARED) {
            return EchoEnd.OUT_OF_RANGE;
        }
        if (observation.routeFailures() >= ApparitionEpisodeRules.MAX_ROUTE_FAILURES) {
            return EchoEnd.ROUTE_FAILURE;
        }
        if (observation.episodeRemainingTicks() <= 0) {
            return EchoEnd.EXPIRED;
        }
        return EchoEnd.NONE;
    }

    public static boolean echoStartAllowed(final int cooldownRemainingTicks, final boolean markPresent) {
        return cooldownRemainingTicks <= 0 && !markPresent;
    }

    // ---------------------------------------------------------------- motion sampling

    /** One sample of an axis displacement in blocks, reduced to clamped integer millis. */
    public static int sampleMillis(final double displacement) {
        if (!Double.isFinite(displacement)) {
            return 0;
        }
        return Math.clamp(Math.round(displacement * 1_000.0D),
            -MAX_SAMPLE_MILLIS, MAX_SAMPLE_MILLIS);
    }

    public static boolean sampleDue(final int remainingIntervalTicks, final int samples) {
        return samples < MAX_SAMPLES && remainingIntervalTicks <= 0;
    }

    public static int accumulate(final int recordedMillis, final int sampleMillis) {
        return Math.clamp((long) recordedMillis + sampleMillis,
            -MAX_RECORDED_MILLIS, MAX_RECORDED_MILLIS);
    }

    /**
     * Whether the recorded gesture is large enough to be worth answering at all. A shade that
     * marked a motionless player records nothing and must release rather than invent a motion.
     */
    public static boolean answerable(final int recordedMillisX, final int recordedMillisZ) {
        return Math.abs(recordedMillisX) >= MIN_ANSWERABLE_MILLIS
            || Math.abs(recordedMillisZ) >= MIN_ANSWERABLE_MILLIS;
    }

    /**
     * The mirror answer for one axis, in whole blocks.
     *
     * <p>The recorded displacement is negated and scaled into the bounded offset range, so the
     * shade steps to where the reflection of that motion puts it rather than following the player.
     * A player who walked east is answered from the west. The result is always inside
     * {@code [-MAX_ANSWER_OFFSET, MAX_ANSWER_OFFSET]} regardless of how far the player actually
     * travelled, so no recorded gesture can produce an unbounded destination.</p>
     */
    public static int answerOffset(final int recordedMillis) {
        if (recordedMillis == 0) {
            return 0;
        }
        final int magnitude = Math.min(MAX_ANSWER_OFFSET,
            Math.max(1, Math.abs(recordedMillis) * MAX_ANSWER_OFFSET / MAX_RECORDED_MILLIS));
        return recordedMillis > 0 ? -magnitude : magnitude;
    }

    // ---------------------------------------------------------------- bands

    public static boolean answerReached(final double distanceSquared) {
        return distanceSquared <= ANSWER_BAND_SQUARED;
    }

    /**
     * The complete strike gate: the single attempt is spent, the mark is inside melee reach, it is
     * genuinely visible, and the window has not closed. Nothing else may open it.
     */
    public static boolean strikeAllowed(
        final int strikes,
        final double distanceSquared,
        final boolean visible,
        final int strikeRemainingTicks
    ) {
        return strikes < MAX_STRIKES
            && distanceSquared <= STRIKE_BAND_SQUARED
            && visible
            && strikeRemainingTicks > 0;
    }

    public static float strikeDamage(final float attackDamageAttribute) {
        return Math.max(0.0F, attackDamageAttribute);
    }

    /** An Echo Shade only ever attacks the one mark of an open strike window. */
    public static boolean canAttack(final boolean striking, final boolean isMark) {
        return striking && isMark;
    }

    // ---------------------------------------------------------------- priority

    /**
     * Frozen priority for this species: an escapable hazard preempts everything, then the strike,
     * then the answer walk, then recording, then recovery, then idle watching. An Echo Shade has no
     * binding, defence or aura rung at all.
     */
    public static int priority(final Phase phase, final boolean hazard) {
        if (hazard) {
            return 0;
        }
        return switch (phase) {
            case STRIKE -> 1;
            case ANSWER -> 2;
            case RECORD -> 3;
            case RECOVER -> 4;
            case WATCH -> 5;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard && priority(phase, true) < priority(phase, false);
    }
}
