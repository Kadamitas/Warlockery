package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.PriorityLadder;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.UUID;

/**
 * The pure Storm Simian kernel: every decision the species makes, expressed over immutable facts
 * with no world access, no entity reference and no mutation.
 *
 * <p>The species is a bounded canopy scout and storm observer, not a flying familiar. It selects a
 * supported grip inside a small local envelope, reads the weather it is already standing in, raises
 * one local troop alarm when something legal hits it, and inspects loose objects without ever taking
 * them. It never creates or redirects lightning, never changes weather, never writes a block, never
 * picks an item up, never carries a rider and never borrows the Owl courier, Steed mount, familiar
 * sit or Imp contract systems.</p>
 *
 * <h2>Which shared primitives this family adopted, and which it declined</h2>
 *
 * <ul>
 *   <li>{@link ScanEnvelope} and {@code ReadBudget} are adopted for the grip search. The canopy
 *       sweep is exactly the shape they were extracted from: a read cap far smaller than the box
 *       volume, where a raster walk would spend the whole budget on the innermost ring.</li>
 *   <li>{@link RouteRequest} is adopted for navigation pacing. Both movement writers this family
 *       has, the canopy reposition and the curiosity approach, share one ledger, so the twenty tick
 *       cadence and the three failure backoff bind across both rather than per behaviour.</li>
 *   <li>{@code PhaseTimer} is adopted for the alarm, inspection and observation windows.</li>
 *   <li>{@link PriorityLadder} is adopted as the single statement of the concern order, and is read
 *       live through {@link #preempts} whenever a running window meets a more urgent concern.
 *       {@link PriorityLadder#select} itself is deliberately <em>not</em> called from the tick: it
 *       copies and sorts a list per call, and this runs twenty times a second across every loaded
 *       simian, so {@link #select} is an explicit chain and
 *       {@code StormSimianRulesTest} proves the chain agrees with the ladder on every reachable
 *       fact combination.</li>
 *   <li>{@code Candidates} is adopted for troop and curiosity retention ordering.</li>
 *   <li>{@code Cadence} is used only through {@link RouteRequest}; the alarm, curiosity and
 *       observation cooldowns are plain persisted countdowns because they are cooldowns after work
 *       rather than periodic triggers, and converting between the two dialects would be a behaviour
 *       change rather than an extraction.</li>
 * </ul>
 */
public final class StormSimianRules {

    /** Most urgent first. {@link #CONCERN_LADDER} takes this declaration order as its ranking. */
    public enum Concern {
        /** Dead, sleeping, riding or otherwise not a live decision maker. */
        INVALID,
        /** The shared hazard escape owns the tick; the arbiter writes nothing. */
        HAZARD,
        /** A legal combat target exists; the shared tactical runtime owns movement. */
        COMBAT,
        /** A legal direct attacker is remembered and the alarm cooldown has elapsed. */
        ALARM,
        /** A bound owner is beyond the tether; the frozen companion follow owns movement. */
        OWNER_TETHER,
        /** No usable grip is held, so a supported canopy position is searched for. */
        CANOPY,
        /** Local weather is read and the visible charge is updated. */
        STORM_WATCH,
        /** A loose object is approached and inspected, never taken. */
        CURIOSITY,
        /** Nothing applies; generic wandering keeps whatever it already did. */
        IDLE
    }

    /** What the observation epoch read from the level it is standing in. Never written back. */
    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    /** The ordering, stated once. Read live by {@link #preempts}. */
    public static final PriorityLadder<Concern> CONCERN_LADDER =
        PriorityLadder.ofEnum(Concern.class);

    // ---------------------------------------------------------------- canopy grip

    /** Half extent of the canopy envelope on x and z. */
    public static final int GRIP_HORIZONTAL_RADIUS = 3;
    /** Half extent of the canopy envelope on y. */
    public static final int GRIP_VERTICAL_RADIUS = 2;
    /** Offsets one canopy sweep may visit, which is the approved sixteen candidate cap. */
    public static final int GRIP_CANDIDATE_CAP = 16;
    /** Charged block reads one canopy sweep may spend, which is the approved sixty four cap. */
    public static final int GRIP_READ_CAP = 64;
    /**
     * The honest cost of one candidate: the loaded test, the body state, the head state and the
     * support state. Charged before any of the four values can reject the candidate, so a rejected
     * candidate costs exactly what it spent.
     */
    public static final int READS_PER_GRIP_CANDIDATE = 4;
    /** Loaded ticks a chosen grip is held before the canopy concern may search again. */
    public static final int GRIP_HOLD_TICKS = 300;

    // ---------------------------------------------------------------- troop alarm

    public static final double ALARM_RADIUS = 12.0;
    public static final int TROOP_CANDIDATE_CAP = 8;
    public static final int ALARM_RECIPIENT_CAP = 4;
    /** Line of sight traces one alarm may spend, charged before the trace may reject anyone. */
    public static final int ALARM_LINE_OF_SIGHT_CAP = 4;
    public static final int ALARM_COOLDOWN_TICKS = 200;
    public static final int ALARM_WINDOW_TICKS = 20;
    /** How long a recipient stays alert. Awareness only: no target, no aggression, no relay. */
    public static final int AWARENESS_TICKS = 100;
    /** How long a remembered direct attacker may still arm an alarm. */
    public static final int ATTACKER_MEMORY_TICKS = 60;

    // ---------------------------------------------------------------- curiosity

    public static final double CURIOSITY_RADIUS = 8.0;
    public static final int CURIOSITY_CANDIDATE_CAP = 8;
    public static final int CURIOSITY_INSPECT_CAP = 4;
    public static final int CURIOSITY_COOLDOWN_TICKS = 160;
    public static final int INSPECT_WINDOW_TICKS = 40;

    // ---------------------------------------------------------------- storm observation

    public static final int OBSERVATION_COOLDOWN_TICKS = 120;
    public static final int OBSERVE_WINDOW_TICKS = 20;
    public static final int MAX_CHARGE = 100;
    public static final int THUNDER_CHARGE_GAIN = 12;
    public static final int RAIN_CHARGE_GAIN = 5;
    public static final int CLEAR_CHARGE_DECAY = 3;

    // ---------------------------------------------------------------- charged gust

    /** Charge one legal ranged attack may consume, at most once per attack. */
    public static final int CHARGED_GUST_COST = 40;
    public static final float BASE_GUST_POWER = 1.0F;
    /** The bounded potency ceiling. It scales the one owned wind charge and nothing else. */
    public static final float CHARGED_GUST_POWER = 1.25F;

    // ---------------------------------------------------------------- routing

    public static final int ROUTE_PERIOD_TICKS = 20;
    public static final int ROUTE_FAILURES_BEFORE_BACKOFF = 3;
    public static final int ROUTE_BACKOFF_BASE_TICKS = 100;
    public static final int ROUTE_BACKOFF_MAX_TICKS = 400;
    public static final double CANOPY_SPEED = 1.0;
    public static final double CURIOSITY_SPEED = 1.1;

    /** The shared backoff policy for both movement writers. */
    public static final RouteRequest.RouteBackoff ROUTE_BACKOFF = new RouteRequest.RouteBackoff(
        ROUTE_FAILURES_BEFORE_BACKOFF, ROUTE_BACKOFF_BASE_TICKS, ROUTE_BACKOFF_MAX_TICKS
    );

    /** Beyond this the frozen companion follow owns movement, exactly as it does today. */
    public static final double OWNER_TETHER_DISTANCE_SQUARED =
        CreatureBehaviorRules.OWNER_FOLLOW_DISTANCE_SQUARED;

    /** Persisted state must stay small enough that a crowded chunk save is not dominated by it. */
    public static final int MAX_STATE_BYTES = 512;

    private StormSimianRules() {
    }

    /** The shared canopy envelope. Identical shapes share one immutable instance. */
    public static ScanEnvelope gripEnvelope() {
        return ScanEnvelope.of(GRIP_HORIZONTAL_RADIUS, GRIP_VERTICAL_RADIUS);
    }

    /** The windows one simian needs before its canopy sweep has covered the whole envelope. */
    public static int gripScansToCover() {
        return gripEnvelope().scansToCover(GRIP_CANDIDATE_CAP);
    }

    /** A per simian starting page, so a troop does not all scan the same offsets on one tick. */
    public static int seedGripCursor(final UUID identity) {
        return gripEnvelope().seedCursor(identity, GRIP_CANDIDATE_CAP);
    }

    // ---------------------------------------------------------------- arbitration

    /** Everything the arbiter is allowed to know, already reduced to booleans by the runtime. */
    public record Facts(
        boolean operational,
        boolean hazard,
        boolean combat,
        boolean alarmDue,
        boolean ownerBeyondTether,
        boolean gripDue,
        boolean observationDue,
        boolean curiosityDue
    ) {
    }

    /**
     * The one winner for this tick.
     *
     * <p>An explicit chain rather than {@link PriorityLadder#select}, because this runs on every
     * loaded simian twenty times a second and the ladder's select copies and sorts its rung list per
     * call. The ladder remains the specification: the rules test asserts this chain returns exactly
     * {@code CONCERN_LADDER.mostUrgent(applicable)} for all two hundred and fifty six fact
     * combinations, so the two can never drift.</p>
     */
    public static Concern select(final Facts facts) {
        if (!facts.operational()) {
            return Concern.INVALID;
        }
        if (facts.hazard()) {
            return Concern.HAZARD;
        }
        if (facts.combat()) {
            return Concern.COMBAT;
        }
        if (facts.alarmDue()) {
            return Concern.ALARM;
        }
        if (facts.ownerBeyondTether()) {
            return Concern.OWNER_TETHER;
        }
        if (facts.gripDue()) {
            return Concern.CANOPY;
        }
        if (facts.observationDue()) {
            return Concern.STORM_WATCH;
        }
        if (facts.curiosityDue()) {
            return Concern.CURIOSITY;
        }
        return Concern.IDLE;
    }

    /**
     * Whether a newly selected concern outranks the one that opened the running window, in which
     * case the runtime cancels that window instead of letting it finish. A map lookup per call, so
     * it is safe in the tick that {@link PriorityLadder#select} is not.
     */
    public static boolean preempts(final Concern candidate, final Concern running) {
        return CONCERN_LADDER.outranks(candidate, running);
    }

    /** Only these two concerns are permitted to write navigation. */
    public static boolean writesNavigation(final Concern concern) {
        return switch (concern) {
            case CANOPY, CURIOSITY -> true;
            case INVALID, HAZARD, COMBAT, ALARM, OWNER_TETHER, STORM_WATCH, IDLE -> false;
        };
    }

    /** The window a concern opens, or zero for a concern that opens none. */
    public static int windowTicks(final Concern concern) {
        return switch (concern) {
            case ALARM -> ALARM_WINDOW_TICKS;
            case CURIOSITY -> INSPECT_WINDOW_TICKS;
            case STORM_WATCH -> OBSERVE_WINDOW_TICKS;
            case INVALID, HAZARD, COMBAT, OWNER_TETHER, CANOPY, IDLE -> 0;
        };
    }

    // ---------------------------------------------------------------- canopy legality

    /**
     * A grip the simian may actually occupy: it is loaded, its body and head boxes are clear, and
     * the block under it can hold weight. Every input was charged before it was read.
     */
    public static boolean gripAcceptable(
        final boolean loaded,
        final boolean bodyClear,
        final boolean headClear,
        final boolean supported
    ) {
        return loaded && bodyClear && headClear && supported;
    }

    // ---------------------------------------------------------------- storm observation

    /** The weather as read, never as wished for. */
    public static Weather weatherOf(final boolean raining, final boolean thundering) {
        if (thundering) {
            return Weather.THUNDER;
        }
        return raining ? Weather.RAIN : Weather.CLEAR;
    }

    /**
     * The charge after exactly one completed observation epoch. Clear weather bleeds charge away,
     * so a simian that walked out of a storm does not stay charged forever. There is deliberately
     * no elapsed time term: an unloaded simian gains nothing, so no offline or reload catch up can
     * exist.
     */
    public static int chargeAfterObservation(final int charge, final Weather weather) {
        final int delta = switch (weather) {
            case THUNDER -> THUNDER_CHARGE_GAIN;
            case RAIN -> RAIN_CHARGE_GAIN;
            case CLEAR -> -CLEAR_CHARGE_DECAY;
        };
        return Math.clamp(charge + delta, 0, MAX_CHARGE);
    }

    /** Whether an already legal ranged attack may spend charge on presentation and potency. */
    public static boolean chargedGustReady(final int charge) {
        return charge >= CHARGED_GUST_COST;
    }

    /** Charge is consumed at most once per attack, and only when it was actually ready. */
    public static int chargeAfterGust(final int charge) {
        return chargedGustReady(charge) ? charge - CHARGED_GUST_COST : charge;
    }

    /** The bounded potency of the one owned wind charge. No area, no fire, no extra target. */
    public static float gustPower(final int charge) {
        return chargedGustReady(charge) ? CHARGED_GUST_POWER : BASE_GUST_POWER;
    }

    // ---------------------------------------------------------------- small arithmetic

    /** Delegates to the shared helper so this family cannot drift from the other twelve. */
    public static int clampRemaining(final int stored, final int maximum) {
        return Ticks.clampRemaining(stored, maximum);
    }

    public static int decrementLoaded(final int remaining) {
        return Ticks.decrementLoaded(remaining);
    }

    public static int stableOffset(final UUID identity, final int span) {
        return Ticks.stableOffset(identity, span);
    }
}
