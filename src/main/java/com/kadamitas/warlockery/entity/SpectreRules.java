package com.kadamitas.warlockery.entity;

import java.util.Objects;

/**
 * Pure F21 Spectre policy. No world, entity, path, level or random state may enter this class.
 *
 * <p>A Spectre is a veil-drawn apparition and an object of dread. Its one species attention is a
 * finite <em>haunting</em>: appoint one visible player as a witness, telegraph its manifestation
 * where that witness can see it coming, drift closer on ordinary bounded spectral flight, deliver
 * Darkness and Weakness exactly once inside the dread window, then fade into recovery.</p>
 *
 * <p>The replaced 1.4 behavior was an uncapped ten-block fear pulse every eighty ticks that hit
 * every attackable player at once, forever, with no warning and no recovery. It scaled with crowd
 * size, could never be escaped and said nothing about the being applying it. The redesign keeps the
 * same two effects and the same source of dread, but makes it an appointed, telegraphed, single
 * delivery against exactly one witness.</p>
 *
 * <p>What it deliberately never does: it never strikes, never deals damage of any kind, never
 * samples or answers a player's motion, never walks, never binds to an owner, never refreshes a
 * dread it already delivered, and never spreads one to a second player. That is what keeps it a
 * different being from the Echo Shade that shares its plan.</p>
 */
public final class SpectreRules {
    /** Total loaded ticks one haunting may occupy from the moment a witness is appointed. */
    public static final int EPISODE_TICKS = 500;
    /** The visible telegraph window. A Spectre is always seen arriving before it is felt. */
    public static final int MANIFEST_TICKS = 100;
    /** Loaded ticks the single dread delivery stays available once the band is reached. */
    public static final int DREAD_TICKS = 60;
    /** Bounded recovery after any haunting, delivered or not. */
    public static final int FADE_TICKS = 80;
    /** Cadence between hauntings, armed by the fade that ends one. */
    public static final int COOLDOWN_TICKS = 400;

    /**
     * A Spectre drifts under a {@code FlyingMoveControl} and a {@code FlyingPathNavigation}, both
     * of which read {@link net.minecraft.world.entity.ai.attributes.Attributes#FLYING_SPEED}. The
     * bare {@code Vex.createAttributes()} supplier does not declare it, so the registration adds it
     * explicitly, exactly as the F15 Hex Bat and F16 Banshee registrations do. The value is the
     * house figure both of those Vex-family apparitions use.
     */
    public static final double FLYING_SPEED = 0.34D;

    /** How often an idle Spectre may run one bounded appointment sweep. */
    public static final int DRIFT_INTERVAL_TICKS = 60;
    /** How often an idle Spectre may reposition inside its own bounded drift envelope. */
    public static final int WANDER_INTERVAL_TICKS = 200;

    public static final int TELEGRAPH_INTERVAL_TICKS = 20;
    public static final int MAX_TELEGRAPHS = 4;
    public static final int MAX_TELEGRAPH_PARTICLES = 10;

    public static final int WITNESS_RANGE = 12;
    public static final double WITNESS_RANGE_SQUARED = (double) WITNESS_RANGE * WITNESS_RANGE;
    /** Past this the witness has left the haunting; it is released rather than pursued. */
    public static final int WITNESS_RELEASE_RANGE = 20;
    public static final double WITNESS_RELEASE_RANGE_SQUARED =
        (double) WITNESS_RELEASE_RANGE * WITNESS_RELEASE_RANGE;

    /** Squared distance inside which the appointed witness may receive the one dread. */
    public static final int DREAD_BAND = 3;
    public static final double DREAD_BAND_SQUARED = (double) DREAD_BAND * DREAD_BAND;
    public static final int MAX_DREADS = 1;

    /** The exact two preserved effects, at their preserved strengths. */
    public static final int DARKNESS_TICKS = 120;
    public static final int WEAKNESS_TICKS = 160;
    public static final int DARKNESS_AMPLIFIER = 0;
    public static final int WEAKNESS_AMPLIFIER = 0;

    public static final int DESTINATION_SEARCH_HORIZONTAL = 2;
    public static final int DESTINATION_SEARCH_VERTICAL = 1;
    public static final int DRIFT_SEARCH_HORIZONTAL = 3;
    public static final int DRIFT_SEARCH_VERTICAL = 2;
    public static final int ESCAPE_SEARCH_HORIZONTAL = 3;
    public static final int ESCAPE_SEARCH_VERTICAL = 2;

    private SpectreRules() {
    }

    /**
     * The complete Spectre phase set. There is deliberately no strike, record, answer, binding or
     * aura phase: a Spectre drifts, manifests, dreads once, and fades.
     */
    public enum Phase {
        DRIFT,
        MANIFEST,
        DREAD,
        FADE
    }

    public enum HauntEnd {
        NONE,
        WITNESS_LOST,
        DIMENSION,
        OUT_OF_RANGE,
        ROUTE_FAILURE,
        EXPIRED
    }

    /** The facts a runtime directly observed about the one appointed witness this decision. */
    public record WitnessObservation(
        boolean present,
        boolean sameDimension,
        boolean loaded,
        boolean eligible,
        double distanceSquared,
        int episodeRemainingTicks,
        int routeFailures
    ) {
    }

    // ---------------------------------------------------------------- episode retention

    /**
     * Exact haunting-end policy. A dimension mismatch wins, then a lost or ineligible witness, then
     * a witness who simply left, then the third route failure, then the loaded-time budget.
     */
    public static HauntEnd hauntEnd(final WitnessObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (!observation.present()) {
            return HauntEnd.WITNESS_LOST;
        }
        if (!observation.sameDimension()) {
            return HauntEnd.DIMENSION;
        }
        if (!observation.loaded() || !observation.eligible()) {
            return HauntEnd.WITNESS_LOST;
        }
        if (observation.distanceSquared() > WITNESS_RELEASE_RANGE_SQUARED) {
            return HauntEnd.OUT_OF_RANGE;
        }
        if (observation.routeFailures() >= ApparitionEpisodeRules.MAX_ROUTE_FAILURES) {
            return HauntEnd.ROUTE_FAILURE;
        }
        if (observation.episodeRemainingTicks() <= 0) {
            return HauntEnd.EXPIRED;
        }
        return HauntEnd.NONE;
    }

    public static boolean hauntStartAllowed(
        final int cooldownRemainingTicks,
        final boolean witnessPresent
    ) {
        return cooldownRemainingTicks <= 0 && !witnessPresent;
    }

    // ---------------------------------------------------------------- telegraph

    /**
     * A telegraph is due only when its interval actually elapsed while loaded and the per-haunting
     * cap has not been reached. A freshly loaded zero interval is never read as due:
     * {@link #resetTelegraphIntervalOnLoad(int)} restores the full interval first, so no reload can
     * replay the manifestation feedback.
     */
    public static boolean telegraphDue(final int remainingIntervalTicks, final int telegraphs) {
        return telegraphs < MAX_TELEGRAPHS && remainingIntervalTicks <= 0;
    }

    public static int resetTelegraphIntervalOnLoad(final int storedRemaining) {
        return storedRemaining <= 0
            ? TELEGRAPH_INTERVAL_TICKS
            : Math.clamp(storedRemaining, 0, TELEGRAPH_INTERVAL_TICKS);
    }

    public static int telegraphsRemaining(final int emitted) {
        return Math.max(0, MAX_TELEGRAPHS - Math.max(0, emitted));
    }

    /**
     * The manifestation graduates into a dread window only once its visible telegraph window has
     * actually elapsed. A Spectre may not skip straight to the effect because the witness happened
     * to walk into the band early.
     */
    public static boolean manifestationGraduates(final int manifestRemainingTicks) {
        return manifestRemainingTicks <= 0;
    }

    // ---------------------------------------------------------------- dread

    /**
     * The complete dread gate: the single delivery is unspent, the witness is inside the band, it
     * is genuinely visible, and the window is open. Nothing else may open it, and there is
     * deliberately no path that reopens it for the same haunting.
     */
    public static boolean dreadAllowed(
        final int dreads,
        final double distanceSquared,
        final boolean visible,
        final int dreadRemainingTicks
    ) {
        return dreads < MAX_DREADS
            && distanceSquared <= DREAD_BAND_SQUARED
            && visible
            && dreadRemainingTicks > 0;
    }

    /** A Spectre never attacks anything, under any circumstances, bound or free, ever. */
    public static boolean canAttack() {
        return false;
    }

    // ---------------------------------------------------------------- priority

    /**
     * Frozen priority for this species: an escapable hazard preempts everything, then the dread
     * delivery, then the manifestation approach, then the fade, then idle drifting. A Spectre has
     * no strike, binding or defence rung at all.
     */
    public static int priority(final Phase phase, final boolean hazard) {
        if (hazard) {
            return 0;
        }
        return switch (phase) {
            case DREAD -> 1;
            case MANIFEST -> 2;
            case FADE -> 3;
            case DRIFT -> 4;
        };
    }

    public static boolean hazardPreempts(final Phase phase, final boolean escapableHazard) {
        return escapableHazard && priority(phase, true) < priority(phase, false);
    }
}
