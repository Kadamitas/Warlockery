package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.UUID;

/**
 * Pure F36 policy: station geometry, the ward and its four bearings, the charge state machine, the
 * phase machine, the priority ladder, the ordered charge legality function, strain arithmetic, the
 * socket predicate and the route pacing policy.
 *
 * <p>No {@code Level}, no entity, no path, no random and no growable collection appears here, so
 * every rule below is decidable from numbers a unit test can supply. The runtime owns every world
 * read; this class owns every decision made from one.</p>
 *
 * <p>Every duration is a count of <em>remaining loaded ticks</em>. Nothing in F36 compares a stored
 * stamp against absolute world time, so zero always reads as "ready" and never as "recently fired",
 * and an unloaded gap can neither expire a window nor make one fire late (DC1). The one place a
 * horizon is needed is {@link #boundedCadenceTicks}, which clamps inside
 * {@link Ticks#MAX_FUTURE_HORIZON_TICKS} rather than reaching for a {@code Long.MAX_VALUE} sentinel
 * that state reconciliation would treat as corrupt and reset to now (DC5).</p>
 */
public final class IronboundSentinelRules {

    // ---------------------------------------------------------------- station, ward and tether

    /** Horizontal ward radius measured from the station, never from the Sentinel. */
    public static final double WARD_HORIZONTAL = 12.0D;
    /** Vertical ward radius measured from the station. */
    public static final double WARD_VERTICAL = 5.0D;
    /** Reach eligibility, measured from the Sentinel. Tested separately from ward membership. */
    public static final double REACH = 8.0D;
    /** The Sentinel never paths beyond this distance from its station outside a hazard escape. */
    public static final double TETHER = 8.0D;
    /** A station further than this from the loaded position is treated as corrupt and replaced. */
    public static final double CORRUPT_STATION_DISTANCE = 48.0D;
    /** Squared distance at which the Sentinel counts as standing on its station. */
    public static final double RETURN_ARRIVAL_DISTANCE_SQR = 4.0D;

    // ---------------------------------------------------------------- the bearing

    public static final int BEARINGS = 4;
    public static final int BEARING_ADVANCE_TICKS = 60;

    // ---------------------------------------------------------------- the sweep

    public static final int SWEEP_TICKS = 20;
    public static final int REVALIDATION_TICKS = 10;
    /** Raw living entities the capped quadrant query may visit. */
    public static final int SWEEP_ENTITY_VISITS = 6;
    /** Sight traces one sweep may spend, charged before any of them can reject a candidate. */
    public static final int SWEEP_SIGHT_RAYS = 2;
    /** Identities retained for the current bearing so an unchanged quadrant costs nothing more. */
    public static final int RETAINED_IDENTITIES = 6;

    // ---------------------------------------------------------------- the episode

    public static final double RETENTION_RADIUS = 16.0D;
    public static final int SIGHT_LOSS_RELEASE_TICKS = 40;
    public static final int EPISODE_CAP_TICKS = 400;
    public static final int REPEL_CADENCE_TICKS = 20;
    /** DC3: an attribution older than this on the attacker's own clock cannot mint a reaction. */
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;

    // ---------------------------------------------------------------- strain

    public static final int STRAIN_MAX = 200;
    public static final int STRAIN_ACCRUAL_TICKS = 20;
    public static final int STRAIN_DECAY_TICKS = 40;
    public static final int STRAIN_ROUTE_FAILURE_PENALTY = 2;
    public static final int SEIZE_TICKS = 40;

    // ---------------------------------------------------------------- the charge transitions

    public static final int WAKING_TICKS = 60;
    public static final int STAND_DOWN_TICKS = 60;
    /** The widest transition counter any charge arm can carry, and the record's stored maximum. */
    public static final int MAX_TRANSITION_TICKS = Math.max(WAKING_TICKS, STAND_DOWN_TICKS);

    // ---------------------------------------------------------------- the socket act

    public static final double SOCKET_REACH_SQR = 4.0D;
    /** The front arc: the look vector must point at the interacting player at all. */
    public static final double SOCKET_FRONT_ARC_DOT = 0.0D;

    // ---------------------------------------------------------------- routes and returns

    public static final int PATH_CADENCE_TICKS = 20;
    public static final int RETURN_TIMEOUT_TICKS = 300;
    public static final int ROUTE_FAILURES_BEFORE_BACKOFF = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final double ROUTE_SPEED = 1.0D;

    /** Three consecutive failures, then a flat hundred-tick window that never grows past itself. */
    public static final RouteRequest.RouteBackoff ROUTE_BACKOFF = new RouteRequest.RouteBackoff(
        ROUTE_FAILURES_BEFORE_BACKOFF, ROUTE_BACKOFF_TICKS, ROUTE_BACKOFF_TICKS
    );

    // ---------------------------------------------------------------- persistence

    /**
     * The measured ceiling for one encoded {@link IronboundSentinelState}, not an estimate. The
     * approved design set a 128-byte target; a representative fully populated record encodes to 147
     * bytes under this platform's NBT encoding with the design's own self-describing key names, so
     * the honest measurement is recorded here rather than the target being claimed. The nine values
     * themselves account for roughly a third of that: the remainder is key text, which could be cut
     * below 128 only by renaming the persistence contract's keys to abbreviations, and a
     * self-documenting save file was judged worth nineteen bytes.
     */
    public static final int MAX_STATE_BYTES = 160;
    /** The exact encoded size of a representative populated record, pinned so drift is visible. */
    public static final int REPRESENTATIVE_STATE_BYTES = 147;

    private IronboundSentinelRules() {
    }

    /** The durable animating record. Only {@link #CHARGED} may bar, repel or bind anything. */
    public enum Charge {
        INERT,
        WAKING,
        CHARGED,
        STANDING_DOWN;

        public boolean transitional() {
            return this == WAKING || this == STANDING_DOWN;
        }

        public boolean mayAct() {
            return this == CHARGED;
        }
    }

    /** Exactly one is active per tick. Transient: normalized on load from the durable charge. */
    public enum Phase {
        STILLED,
        VIGIL,
        RETURN,
        BAR,
        REPEL,
        SEIZE,
        UNDOING,
        EVADE;

        public boolean episode() {
            return this == BAR || this == REPEL;
        }
    }

    /**
     * The priority ladder, most urgent first. Declaration order is the ladder, so a band added here
     * cannot be left unranked. Exactly one band owns navigation and effects for a tick.
     */
    public enum Band {
        HAZARD,
        SHUTDOWN,
        SEIZE,
        EPISODE,
        RETURN,
        ROUTINE
    }

    /**
     * Which rung of the ordered charge legality function answered. Only {@link #ELIGIBLE} permits a
     * bar, a repel or a binding; every other value is a refusal naming its own reason, so a test can
     * prove the order rather than only the outcome.
     */
    public enum Legality {
        SELF,
        SIBLING_SENTINEL,
        OWNER,
        CREATIVE_OR_SPECTATOR,
        INVALID,
        OCCUPIED,
        NOT_CHARGED,
        OUTSIDE_WARD,
        OUT_OF_REACH,
        UNSEEN,
        ELIGIBLE;

        public boolean eligible() {
            return this == ELIGIBLE;
        }
    }

    /** What one socket act resolves to. {@link #PASS} is the mandatory default arm. */
    public enum SocketAct {
        SEAT,
        DRAW,
        PASS
    }

    /**
     * The facts about a candidate that the runtime has already gathered. Every field is a plain
     * value: nothing here can be resolved, ticked or navigated, which is what keeps
     * {@link #legality} pure and exhaustively testable.
     *
     * @param self the candidate is the Sentinel itself
     * @param siblingSentinel the candidate is another Ironbound Sentinel
     * @param owned the candidate holds the Sentinel's owner mark
     * @param creativeOrSpectator the candidate is a player who cannot be barred
     * @param valid the candidate is alive, present and not removed
     * @param occupied the candidate is trading, sleeping, raiding, panicking or breeding
     * @param insideWard measured from the station, both radii
     * @param withinReach measured from the Sentinel
     * @param seen an unobstructed sight test succeeded (DC2)
     */
    public record Candidate(
        boolean self,
        boolean siblingSentinel,
        boolean owned,
        boolean creativeOrSpectator,
        boolean valid,
        boolean occupied,
        boolean insideWard,
        boolean withinReach,
        boolean seen
    ) {
    }

    /** A horizontal interposition point, in absolute world coordinates. */
    public record Interposition(double x, double z) {
    }

    /**
     * The one ordered function that decides whom this species may bar, repel or bind. It is
     * evaluated top down and short-circuits at the first rung that answers, it performs no query and
     * resolves no entity, and species is deliberately not a rung: a villager, an iron golem, a
     * familiar, another Warlockery kind and a player are all judged by the same nine tests.
     */
    public static Legality legality(final Charge charge, final Candidate candidate) {
        if (candidate.self()) {
            return Legality.SELF;
        }
        if (candidate.siblingSentinel()) {
            return Legality.SIBLING_SENTINEL;
        }
        if (candidate.owned()) {
            return Legality.OWNER;
        }
        if (candidate.creativeOrSpectator()) {
            return Legality.CREATIVE_OR_SPECTATOR;
        }
        if (!candidate.valid()) {
            return Legality.INVALID;
        }
        if (candidate.occupied()) {
            return Legality.OCCUPIED;
        }
        if (!charge.mayAct()) {
            return Legality.NOT_CHARGED;
        }
        if (!candidate.insideWard()) {
            return Legality.OUTSIDE_WARD;
        }
        if (!candidate.withinReach()) {
            return Legality.OUT_OF_REACH;
        }
        if (!candidate.seen()) {
            return Legality.UNSEEN;
        }
        return Legality.ELIGIBLE;
    }

    // ---------------------------------------------------------------- geometry

    /** Ward membership: horizontal and vertical radii tested separately, both from the station. */
    public static boolean insideWard(
        final double deltaX,
        final double deltaY,
        final double deltaZ
    ) {
        return deltaX * deltaX + deltaZ * deltaZ <= WARD_HORIZONTAL * WARD_HORIZONTAL
            && Math.abs(deltaY) <= WARD_VERTICAL;
    }

    /** Reach eligibility, measured from the Sentinel and never from the station. */
    public static boolean withinReach(final double distanceSqrFromSentinel) {
        return distanceSqrFromSentinel <= REACH * REACH;
    }

    /** A subject beyond this is released rather than pursued. */
    public static boolean withinRetention(final double distanceSqrFromSentinel) {
        return distanceSqrFromSentinel <= RETENTION_RADIUS * RETENTION_RADIUS;
    }

    /** A station this far from the loaded position is unreachable rubbish and is re-stationed. */
    public static boolean stationCorrupt(final double distanceSqrFromStation) {
        return distanceSqrFromStation > CORRUPT_STATION_DISTANCE * CORRUPT_STATION_DISTANCE;
    }

    public static boolean atStation(final double distanceSqrFromStation) {
        return distanceSqrFromStation <= RETURN_ARRIVAL_DISTANCE_SQR;
    }

    /** Never path outside the tether. Hazard escape is the one band exempt from this. */
    public static boolean insideTether(final double distanceSqrFromStation) {
        return distanceSqrFromStation <= TETHER * TETHER;
    }

    /**
     * The sign the quadrant at this bearing occupies on X. Bearings ascend and wrap
     * {@code 0 -> 1 -> 2 -> 3 -> 0} through {@code (+,+), (-,+), (-,-), (+,-)}.
     */
    public static int quadrantSignX(final int bearing) {
        return switch (Math.floorMod(bearing, BEARINGS)) {
            case 0, 3 -> 1;
            case 1, 2 -> -1;
            default -> 1;
        };
    }

    public static int quadrantSignZ(final int bearing) {
        return switch (Math.floorMod(bearing, BEARINGS)) {
            case 0, 1 -> 1;
            case 2, 3 -> -1;
            default -> 1;
        };
    }

    /**
     * The low bound of the quadrant on one axis, as an offset from the station. Deliberately
     * inclusive of zero on both axes: the station's own column belongs to every quadrant, so the
     * four bearings cover the entire ward with no seam down the middle and the position the Sentinel
     * is standing on is evaluated on every bearing rather than on none.
     */
    public static double quadrantLow(final int sign) {
        return sign < 0 ? -WARD_HORIZONTAL : 0.0D;
    }

    public static double quadrantHigh(final int sign) {
        return sign < 0 ? 0.0D : WARD_HORIZONTAL;
    }

    /** The next bearing, ascending and wrapping. */
    public static int nextBearing(final int bearing) {
        return Math.floorMod(bearing + 1, BEARINGS);
    }

    /** The centre of the quadrant at a bearing, for the look-only write on each advance. */
    public static Interposition quadrantCentre(
        final double stationX,
        final double stationZ,
        final int bearing
    ) {
        return new Interposition(
            stationX + quadrantSignX(bearing) * WARD_HORIZONTAL * 0.5D,
            stationZ + quadrantSignZ(bearing) * WARD_HORIZONTAL * 0.5D
        );
    }

    /**
     * The point on the segment from the subject to the station that lies at melee reach from the
     * subject, clamped inside the tether. A Sentinel bars by standing between what it found and what
     * it keeps; it never walks past the subject and never leaves its tether to do it.
     */
    public static Interposition interposition(
        final double subjectX,
        final double subjectZ,
        final double stationX,
        final double stationZ,
        final double meleeReach
    ) {
        final double toStationX = stationX - subjectX;
        final double toStationZ = stationZ - subjectZ;
        final double length = Math.sqrt(toStationX * toStationX + toStationZ * toStationZ);
        if (length < 1.0E-4D) {
            return new Interposition(stationX, stationZ);
        }
        final double step = Math.min(meleeReach, length);
        final double x = subjectX + toStationX / length * step;
        final double z = subjectZ + toStationZ / length * step;
        return clampToTether(x, z, stationX, stationZ);
    }

    /** Pulls a destination back onto the tether circle rather than refusing it outright. */
    public static Interposition clampToTether(
        final double x,
        final double z,
        final double stationX,
        final double stationZ
    ) {
        final double offsetX = x - stationX;
        final double offsetZ = z - stationZ;
        final double distanceSqr = offsetX * offsetX + offsetZ * offsetZ;
        if (distanceSqr <= TETHER * TETHER) {
            return new Interposition(x, z);
        }
        final double scale = TETHER / Math.sqrt(distanceSqr);
        return new Interposition(stationX + offsetX * scale, stationZ + offsetZ * scale);
    }

    // ---------------------------------------------------------------- the state machines

    /**
     * The phase implied by a durable charge, used on load, after every cancellation and whenever a
     * band hands navigation back. It is the only place a phase may be invented without a tick branch
     * deciding it.
     */
    public static Phase phaseFor(final Charge charge) {
        return switch (charge) {
            case INERT, WAKING -> Phase.STILLED;
            case CHARGED -> Phase.VIGIL;
            case STANDING_DOWN -> Phase.UNDOING;
        };
    }

    /** How long the transitional arms run. The settled arms carry no transition at all. */
    public static int transitionTicksFor(final Charge charge) {
        return switch (charge) {
            case WAKING -> WAKING_TICKS;
            case STANDING_DOWN -> STAND_DOWN_TICKS;
            case INERT, CHARGED -> 0;
        };
    }

    /**
     * The charge a transitional arm becomes once its counter has actually been observed at zero by a
     * tick branch. This is a question, not a reconciliation: the state record never calls it, so a
     * counter reaching zero can never end a transition without the branch that also emits its
     * feedback running first.
     */
    public static Charge chargeAfterTransition(final Charge charge) {
        return switch (charge) {
            case WAKING -> Charge.CHARGED;
            case STANDING_DOWN -> Charge.INERT;
            case INERT, CHARGED -> charge;
        };
    }

    /** The band that owns this tick. Exactly one, hazard first, inert last. */
    public static Band band(
        final Charge charge,
        final Phase phase,
        final boolean hazard,
        final boolean seizing,
        final boolean subjectBound,
        final boolean awayFromStation
    ) {
        if (hazard) {
            return Band.HAZARD;
        }
        if (!charge.mayAct()) {
            return Band.SHUTDOWN;
        }
        if (seizing || phase == Phase.SEIZE) {
            return Band.SEIZE;
        }
        if (subjectBound) {
            return Band.EPISODE;
        }
        if (awayFromStation) {
            return Band.RETURN;
        }
        return Band.ROUTINE;
    }

    /** Hazard outranks the shutdown band, because escaping outranks the concept. */
    public static boolean hazardPreemptsShutdown() {
        return Band.HAZARD.ordinal() < Band.SHUTDOWN.ordinal();
    }

    // ---------------------------------------------------------------- strain

    public static int clampStrain(final int strain) {
        return Math.clamp(strain, 0, STRAIN_MAX);
    }

    /** Strain rises only while a bound charge cannot be discharged. Never from taking damage. */
    public static int strainAfterHeldSubject(final int strain) {
        return clampStrain(strain + 1);
    }

    public static int strainAfterRouteFailure(final int strain) {
        return clampStrain(strain + STRAIN_ROUTE_FAILURE_PENALTY);
    }

    /** Strain falls only while the ward is clear and nothing is bound. */
    public static int strainAfterDecay(final int strain) {
        return clampStrain(strain - 1);
    }

    public static boolean seizeDue(final int strain) {
        return strain >= STRAIN_MAX;
    }

    // ---------------------------------------------------------------- the socket act

    /**
     * One deliberate open-handed act, evaluated only on a player interaction and never on a tick.
     * Every precondition must hold; the {@link SocketAct#PASS} arm is mandatory and is what keeps a
     * failed act an ordinary click rather than a swallowed one.
     */
    public static SocketAct socketAct(
        final Charge charge,
        final boolean crouching,
        final boolean mainHandEmpty,
        final boolean offHandEmpty,
        final double distanceSqr,
        final double lookDot,
        final boolean playerIsBoundSubject
    ) {
        if (!crouching || !mainHandEmpty || !offHandEmpty || playerIsBoundSubject) {
            return SocketAct.PASS;
        }
        if (distanceSqr > SOCKET_REACH_SQR || !(lookDot > SOCKET_FRONT_ARC_DOT)) {
            return SocketAct.PASS;
        }
        return switch (charge) {
            case INERT -> SocketAct.SEAT;
            case WAKING, CHARGED -> SocketAct.DRAW;
            case STANDING_DOWN -> SocketAct.PASS;
        };
    }

    // ---------------------------------------------------------------- DC helpers

    /**
     * DC3. An attribution is fresh only inside an inclusive zero-to-forty window on the attributed
     * entity's own clock. A negative age is a clock that has been reset and is never fresh.
     */
    public static boolean attributionFresh(final int ageTicks) {
        return ageTicks >= 0 && ageTicks <= ATTRIBUTION_FRESHNESS_TICKS;
    }

    /**
     * DC5. Any cadence value derived for a far-future check stays inside the loaded horizon. A
     * {@code Long.MAX_VALUE} sentinel would be treated as corrupt by reconciliation and reset to
     * now, which would make every Sentinel in the world fire on the same tick.
     */
    public static long boundedCadenceTicks(final long requested) {
        return Math.clamp(requested, 0L, Ticks.MAX_FUTURE_HORIZON_TICKS);
    }

    /**
     * DC6. A deterministic per-entity phase offset inside a cadence period. The sweep and the
     * bearing advance take deliberately different offsets so one Sentinel never pays for both on the
     * same tick.
     */
    public static int sweepOffset(final UUID identity) {
        return Ticks.stableOffset(identity, SWEEP_TICKS);
    }

    public static int bearingOffset(final UUID identity) {
        return Math.floorMod(Ticks.stableOffset(identity, BEARING_ADVANCE_TICKS) + SWEEP_TICKS / 2,
            BEARING_ADVANCE_TICKS);
    }
}
