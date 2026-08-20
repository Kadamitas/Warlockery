package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.behavior.PriorityLadder;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.Objects;

/**
 * Every pure decision the two spectral steeds make.
 *
 * <p>Nothing here touches a level, an entity, a navigator or the clock, so the whole decision
 * surface is directly testable. The runtime supplies facts and applies the answer.</p>
 *
 * <p>Ownership and the mount speed floor deliberately stay in {@link SpectralMountRules}. That class
 * is the frozen acquisition contract shared with {@code CreatureBehaviorRuntime.interactMount} and
 * with {@code ArcaneMob}'s passenger seam; this family layers gait, fatigue, rest and fear on top of
 * it rather than restating it, so nothing there becomes dead.</p>
 */
public final class SpectralSteedRules {

    /** Bond is a mount maturity scale, never an ownership claim. Ownership stays the owner UUID. */
    public static final int MAX_BOND = 1_000;
    public static final int MAX_FATIGUE = 1_000;

    /** At most one point of bond per server tick, from riding or from a completed rest. */
    public static final int MAX_BOND_PER_TICK = 1;
    /** Per-ride accumulator cap, so one very long episode cannot mature a steed on its own. */
    public static final int MAX_BOND_PER_EPISODE = 200;

    /** Bond at which a Pale Steed is trusted with its top band. */
    public static final int PALE_SPRINT_BOND = 600;
    /** Bond at which a bonded Nightmare will issue its warning at all. */
    public static final int NIGHTMARE_WARNING_BOND = 200;

    /** Normal gait changes move at most one band per this many ticks. */
    public static final int GAIT_HOLD_TICKS = 10;

    public static final int EXHAUSTED_FATIGUE = 800;
    public static final int REST_SEEK_FATIGUE = 400;

    public static final int REST_HORIZONTAL_RADIUS = 2;
    public static final int REST_VERTICAL_RADIUS = 1;
    /** Reads charged per rest search, spent before any candidate can be rejected. */
    public static final int MAX_REST_BLOCK_READS = 48;
    /**
     * Queries charged per re-check of a held rest site, and its exact worst case: world-border
     * admission, four loaded-footprint corners, three stance block states, block and entity
     * collision checks, then one landmark query per horizontal direction. A held site is re-checked
     * on every tick the steed is walking to it, so none of these may be taken for free.
     */
    public static final int MAX_REST_VALIDATION_READS = 14;
    /** Positions a single rest search may evaluate. */
    public static final int MAX_REST_CANDIDATES = 16;
    public static final int REST_SEARCH_INTERVAL_TICKS = 20;
    public static final int REST_SETTLE_TICKS = 100;
    public static final int REST_COOLDOWN_TICKS = 400;
    /** Distance within which the steed counts as arrived at its rest site. */
    public static final double REST_ARRIVAL_DISTANCE_SQUARED = 4.0;

    public static final int HAZARD_SCAN_INTERVAL_TICKS = 5;

    public static final int PALE_BALK_TICKS = 30;
    public static final int NIGHTMARE_BALK_TICKS = 15;
    public static final int MIN_BALK_TICKS = 6;

    public static final double FEAR_RADIUS = 6.0;
    /** Entities a warning may visit, charged whether or not they qualify. */
    public static final int MAX_FEAR_VISITS = 8;
    /** Entities a warning may actually reach. */
    public static final int MAX_FEAR_RECIPIENTS = 4;
    public static final int FEAR_COOLDOWN_TICKS = 200;
    public static final int FEAR_EFFECT_TICKS = 60;

    /** Three fruitless rest searches buy a hundred ticks of quiet, doubling to four hundred. */
    public static final RouteRequest.RouteBackoff REST_BACKOFF =
        new RouteRequest.RouteBackoff(3, 100, 400);

    private static final Gait[] GAITS = Gait.values();
    private static final PriorityLadder<Concern> LADDER = PriorityLadder.ofEnum(Concern.class);

    private SpectralSteedRules() {
    }

    /** The five bands, slowest first. Ordinal order is the band order and is relied upon. */
    public enum Gait {
        HALT,
        WALK,
        TROT,
        CANTER,
        SPRINT
    }

    /**
     * What the steed is doing this tick, most urgent first.
     *
     * <p>Declaration order is the priority order {@link #ladder()} reports and
     * {@link #chooseConcern} implements without allocating.</p>
     */
    public enum Concern {
        HAZARD,
        BALK,
        CARRY,
        REST,
        IDLE
    }

    /** The steeds this family owns, asked through the existing shared predicate. */
    public static boolean isSteed(final CreatureKind kind) {
        return SpectralMountRules.isMount(kind);
    }

    /** The ranking, for tests and for anything that wants the ordering without the tick. */
    public static PriorityLadder<Concern> ladder() {
        return LADDER;
    }

    /**
     * The winning concern, as a branch chain rather than a ladder call.
     *
     * <p>{@code PriorityLadder.select} copies the rung list, sorts it and opens a stream on every
     * call. A mount decides this twenty times a second while ridden, so the ladder is kept for the
     * ordering statement and for the equivalence test, and the tick uses this.</p>
     */
    public static Concern chooseConcern(
        final boolean hazard,
        final boolean balking,
        final boolean carrying,
        final boolean seekingRest
    ) {
        if (hazard) {
            return Concern.HAZARD;
        }
        if (balking) {
            return Concern.BALK;
        }
        if (carrying) {
            return Concern.CARRY;
        }
        if (seekingRest) {
            return Concern.REST;
        }
        return Concern.IDLE;
    }

    /**
     * How one bounded rest search ended.
     *
     * <p>The distinction the two empty cases carry is the whole point. A search that walked its
     * entire window and qualified nothing has learned something about the world: there is nowhere
     * to rest here, and backing off is right. A search that ran out of reads has learned nothing at
     * all except that looking is expensive here, which is exactly the situation a dense hay field
     * produces, and charging that as evidence of absence pushes a steed standing in the best rest
     * terrain in the world into a backoff window.</p>
     */
    public enum RestSearchOutcome {
        /** A site qualified and the search stopped there. */
        FOUND,
        /** The whole window was evaluated and nothing in it qualified. Absence. */
        NOTHING_QUALIFIED,
        /** The read allowance ran out before the window was evaluated. Cost, not absence. */
        BUDGET_EXHAUSTED
    }

    /**
     * The rest request as it stands after one search.
     *
     * <p>All three arms arm the cadence, because in all three a real sweep ran and re-running it on
     * the next tick is the defect {@link com.kadamitas.warlockery.entity.behavior.Cadence} exists to
     * prevent. Only the arms differ in what they claim to have learned: a find clears the failure
     * run, an empty window advances it, and an exhausted allowance leaves it exactly where it was.
     * Exhaustion therefore paces the steed without ever reaching the backoff threshold on its own,
     * while {@code advanceCursor} moves the window on so the next look falls somewhere else.</p>
     */
    public static RouteRequest afterRestSearch(
        final RouteRequest request,
        final RestSearchOutcome outcome
    ) {
        Objects.requireNonNull(request, "request");
        return switch (outcome) {
            case FOUND -> request.succeeded();
            case NOTHING_QUALIFIED -> request.failed(REST_BACKOFF);
            case BUDGET_EXHAUSTED -> new RouteRequest(
                request.cadence().arm(), request.consecutiveFailures(), request.backoffRemaining()
            );
        };
    }

    /** Whether an unridden steed should go looking for somewhere to stand down. */
    public static boolean seeksRest(
        final int fatigue,
        final int restCooldown,
        final boolean carrying
    ) {
        return !carrying && restCooldown <= 0 && fatigue >= REST_SEEK_FATIGUE;
    }

    /** The highest band this steed may currently use. */
    public static Gait ceilingGait(final CreatureKind kind, final int bond, final int fatigue) {
        requireSteed(kind);
        if (fatigue >= EXHAUSTED_FATIGUE) {
            return Gait.TROT;
        }
        return switch (kind) {
            case NIGHTMARE -> Gait.SPRINT;
            case PALE_STEED -> bond >= PALE_SPRINT_BOND ? Gait.SPRINT : Gait.CANTER;
            default -> throw new IllegalArgumentException("not a spectral steed: " + kind);
        };
    }

    /**
     * The band the rider is asking for. Backward input never asks for more than a walk, which is why
     * a reversing mount cannot canter into whatever is behind it.
     */
    public static Gait desiredGait(final CreatureKind kind, final float forward, final float sideways) {
        requireSteed(kind);
        if (forward <= 0.0F && Math.abs(sideways) < 0.05F) {
            return forward < 0.0F ? Gait.WALK : Gait.HALT;
        }
        final float demand = Math.max(Math.abs(forward), Math.abs(sideways) * 0.5F);
        final Gait ceiling = kind == CreatureKind.NIGHTMARE ? Gait.SPRINT : Gait.CANTER;
        if (demand >= 0.95F) {
            return ceiling;
        }
        if (demand >= 0.6F) {
            return Gait.CANTER;
        }
        if (demand >= 0.3F) {
            return Gait.TROT;
        }
        return Gait.WALK;
    }

    /**
     * The next band. Upshifts wait out the hold window and move exactly one band; downshifts never
     * wait, because the reasons to slow down are hazards, startle and exhaustion. An urgent request
     * drops straight to the asked band rather than one step at a time.
     */
    public static Gait nextGait(
        final Gait current,
        final Gait desired,
        final int holdRemaining,
        final boolean urgent
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(desired, "desired");
        if (desired == current) {
            return current;
        }
        if (desired.ordinal() < current.ordinal()) {
            return urgent ? desired : GAITS[current.ordinal() - 1];
        }
        return holdRemaining > 0 ? current : GAITS[current.ordinal() + 1];
    }

    /** A band never exceeds its ceiling, whoever asked for it. */
    public static Gait capped(final Gait gait, final Gait ceiling) {
        return gait.ordinal() > ceiling.ordinal() ? ceiling : gait;
    }

    /** The rider-facing speed multiplier of a band. */
    public static float gaitSpeedFactor(final Gait gait) {
        return switch (gait) {
            case HALT -> 0.0F;
            case WALK -> 0.45F;
            case TROT -> 0.70F;
            case CANTER -> 1.00F;
            case SPRINT -> 1.25F;
        };
    }

    /**
     * How fatigue moves in one tick at a band. A balking or halted steed recovers, and the Nightmare
     * pays more for its top band than the Pale Steed pays for its.
     */
    public static int fatigueDelta(final CreatureKind kind, final Gait gait) {
        requireSteed(kind);
        return switch (gait) {
            case HALT -> -4;
            case WALK -> -2;
            case TROT -> 0;
            case CANTER -> 2;
            case SPRINT -> kind == CreatureKind.NIGHTMARE ? 7 : 4;
        };
    }

    /** Resting recovers faster than merely standing, and is the only item-free full recovery. */
    public static int restFatigueDelta() {
        return -8;
    }

    /**
     * Bond earned this tick. Riding the legal owner or completing a rest earns one point; nothing
     * else does, and never more than one, so proximity, reload and elapsed offline time earn none.
     *
     * <p>Only the riding half is charged against the per-episode accumulator. A completed rest is
     * already rate limited by its own four hundred tick cooldown, and charging it against a
     * accumulator that only a mount resets would stop a steed nobody ever rides from maturing at
     * all.</p>
     */
    public static int bondGain(
        final boolean carryingOwner,
        final boolean completedRest,
        final int earnedThisEpisode
    ) {
        if (completedRest) {
            return MAX_BOND_PER_TICK;
        }
        if (!carryingOwner || earnedThisEpisode >= MAX_BOND_PER_EPISODE) {
            return 0;
        }
        return MAX_BOND_PER_TICK;
    }

    /** How long a startle holds the steed. Higher bond shortens it but never removes it. */
    public static int balkTicks(final CreatureKind kind, final int bond) {
        requireSteed(kind);
        final int base = kind == CreatureKind.NIGHTMARE ? NIGHTMARE_BALK_TICKS : PALE_BALK_TICKS;
        final int relief = base * Math.clamp(bond, 0, MAX_BOND) / (2 * MAX_BOND);
        return Math.max(MIN_BALK_TICKS, base - relief);
    }

    /** A balking steed accepts no steering at all; the rider is never thrown for it. */
    public static double riddenInputScale(final boolean balking) {
        return balking ? 0.0 : 1.0;
    }

    /**
     * Whether a bonded Nightmare may issue its one telegraphed warning. A Pale Steed never can: it
     * answers fear by balking and re-heading, which is the whole difference between the two.
     */
    public static boolean warningWarranted(
        final CreatureKind kind,
        final boolean threatened,
        final int bond,
        final int fearCooldown
    ) {
        requireSteed(kind);
        return kind == CreatureKind.NIGHTMARE
            && threatened
            && fearCooldown <= 0
            && bond >= NIGHTMARE_WARNING_BOND;
    }

    /**
     * Whether one visited entity may receive the warning. Every exclusion is stated here so the
     * runtime cannot quietly widen the set; the owner, allies of the same owner, any Warlockery
     * creature, and players who cannot be fought are all out.
     */
    public static boolean warningReaches(
        final boolean alive,
        final boolean taggedHostile,
        final boolean isOwner,
        final boolean sharesOwner,
        final boolean arcaneCreature,
        final boolean unfightablePlayer
    ) {
        return alive && taggedHostile && !isOwner && !sharesOwner && !arcaneCreature
            && !unfightablePlayer;
    }

    private static void requireSteed(final CreatureKind kind) {
        if (!isSteed(kind)) {
            throw new IllegalArgumentException("not a spectral steed: " + kind);
        }
    }
}
