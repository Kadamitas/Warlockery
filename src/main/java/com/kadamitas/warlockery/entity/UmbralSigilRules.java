package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * The complete pure policy of the Umbral Sigil. No level, entity, path, random source or client
 * fact may enter this class: every input is a scalar or an immutable value, so the whole contract
 * is directly unit testable without a server.
 *
 * <p>The species motive in one sentence: the Sigil picks one visible survival player, snapshots a
 * bounded encounter centre near that player, derives exactly three clockwise air vertices around
 * it, flies to each in order, and if the player is still standing inside the centre when the third
 * vertex is reached it closes the seal with at most one ordinary attributed melee attempt.</p>
 *
 * <p>Deliberately not shared with F19, F20 or F21. The Sigil neither imitates, frightens, throws,
 * binds, petitions nor appoints; the only thing it has in common with its spectral neighbours is
 * that it is a flying hostile, and that is a chassis rather than a motive.</p>
 */
public final class UmbralSigilRules {

    // ---------------------------------------------------------------- chassis

    /**
     * Declared because {@link net.minecraft.world.entity.ai.control.FlyingMoveControl} and
     * {@link net.minecraft.world.entity.ai.navigation.FlyingPathNavigation} both read
     * {@link net.minecraft.world.entity.ai.attributes.Attributes#FLYING_SPEED}, which the bare
     * {@code Vex.createAttributes()} baseline the registry hands every spirit-archetype id does
     * not contain. The exact value the F15 Hex Bat, F16 Banshee and F21 Spectre registrations use.
     */
    public static final double FLYING_SPEED = 0.34D;

    /** Ordinary bounded approach speed. Never a teleport, never a noclip, never a forced chunk. */
    public static final double ROUTE_SPEED = 1.0D;
    /** Hazard withdrawal speed. Faster than an approach, still an ordinary navigation. */
    public static final double ESCAPE_SPEED = 1.2D;

    // ---------------------------------------------------------------- appointment

    /** The furthest a candidate player may be and still be considered, squared. Twelve blocks. */
    public static final double SUBJECT_RANGE_SQUARED = 144.0D;
    /** Players examined by one appointment sweep before it stops, qualified or not. */
    public static final int MAX_PLAYER_CANDIDATES = 8;
    /** Line-of-sight walks one appointment sweep may spend. Sensing caches these per tick. */
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 4;
    /**
     * Charged-read ceiling of one appointment sweep: every examined candidate costs one read before
     * any filter may reject it, and every line-of-sight walk costs one more.
     */
    public static final int MAX_APPOINTMENT_READS =
        MAX_PLAYER_CANDIDATES + MAX_LINE_OF_SIGHT_CHECKS;
    /** How often a dormant Sigil may run one bounded appointment sweep. */
    public static final int SELECT_INTERVAL_TICKS = 40;

    // ---------------------------------------------------------------- geometry

    /** Vertices in one seal. Fixed: a seal is a triangle, never a growing polygon. */
    public static final int SEAL_VERTICES = 3;
    /** Horizontal radius of the seal, in blocks. Clamped and fixed; never derived from a player. */
    public static final int SEAL_RADIUS = 1;
    /** Vertical offset of every vertex from the snapshot centre. A seal is horizontal. */
    public static final int SEAL_LIFT = 0;
    /** How far the snapshot centre may sit from the Sigil, squared. Never further than a subject. */
    public static final double MAX_CENTRE_OFFSET_SQUARED = SUBJECT_RANGE_SQUARED;
    /** How far the snapshot centre may sit above or below the Sigil. */
    public static final int MAX_CENTRE_LIFT = 4;
    /** Distance at which a vertex counts as reached, squared. One block. */
    public static final double VERTEX_REACH_SQUARED = 1.0D;
    /**
     * The bounded encounter centre the subject must still be standing inside, squared. Two blocks.
     * Walking out of it is the whole of the counterplay, and it is checked on every tick of every
     * open phase, so a seal can be broken before it is drawn as well as at the close.
     */
    public static final double CENTRE_HOLD_SQUARED = 4.0D;

    /**
     * The three unit vertices, clockwise when viewed from above with {@code +x} east and
     * {@code +z} south: north, then south-east, then south-west. A fixed table rather than
     * trigonometry, because this is read on a tick branch and because two servers presented with
     * the same centre must derive byte-identical vertices.
     */
    private static final Vec3i[] UNIT_VERTICES = {
        new Vec3i(0, 0, -1),
        new Vec3i(1, 0, 1),
        new Vec3i(-1, 0, 1)
    };

    // ---------------------------------------------------------------- durations

    public static final int INSCRIBE_TICKS = 100;
    public static final int CLOSE_TICKS = 20;
    public static final int STRIKE_TICKS = 20;
    public static final int RECOVER_TICKS = 40;
    /** Armed by every ending, successful or not, so a released Sigil cannot immediately re-seal. */
    public static final int SEAL_COOLDOWN_TICKS = 200;

    // ---------------------------------------------------------------- routing

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    /** Path starts one level may spend on Sigils in one tick, so a crowd cannot contend. */
    public static final int MAX_PATH_STARTS_PER_LEVEL_TICK = 4;

    // ---------------------------------------------------------------- hazards

    public static final int HAZARD_INTERVAL_TICKS = 20;
    /**
     * Exact read ceiling of the 3 x 3 x 3 contact neighbourhood. Equal to the volume of that
     * neighbourhood, so the budget can never truncate the sweep: the Sigil's own block and all
     * eight far corners are evaluated on every single hazard sample.
     */
    public static final int MAX_HAZARD_READS = 27;

    // ---------------------------------------------------------------- strike

    /** At most one ordinary attributed melee attempt per seal. Never two, never an area pulse. */
    public static final int MAX_STRIKES = 1;
    /** Melee reach at which the close may spend its one attempt, squared. */
    public static final double STRIKE_BAND_SQUARED = 9.0D;

    /** Representative encoded-state ceiling asserted by the state suite. */
    public static final int MAX_STATE_BYTES = 512;

    private UmbralSigilRules() {
    }

    /**
     * The seal, phase by phase. {@code DORMANT} is the only phase with no duration, which is why
     * {@link com.kadamitas.warlockery.entity.behavior.PhaseTimer.Idle} is its canonical shape.
     */
    public enum Phase {
        DORMANT,
        INSCRIBE_1,
        INSCRIBE_2,
        INSCRIBE_3,
        CLOSE,
        STRIKE,
        RECOVER
    }

    /** Why a seal ended. Exactly one branch owns each, and every one arms the cadence. */
    public enum SealEnd {
        NONE,
        SUBJECT_LOST,
        SUBJECT_INELIGIBLE,
        LEFT_CENTRE,
        DIMENSION_LOST,
        GEOMETRY_LOST,
        ROUTE_FAILURE
    }

    /** What one tick observed about the appointed subject. Never a live entity. */
    public record SubjectObservation(
        boolean appointed,
        boolean sameDimension,
        boolean resolved,
        boolean eligible,
        boolean geometryHeld,
        double centreOffsetSquared,
        int routeFailures
    ) {
    }

    // ---------------------------------------------------------------- geometry

    /**
     * The bounded snapshot centre for one seal, or none when the subject is outside the declared
     * bound. The vertical component is clamped towards the Sigil so a subject on a distant ledge
     * can never pull a seal far above or below the Sigil's own flight level.
     */
    public static Optional<BlockPos> sealCentre(final BlockPos sigil, final BlockPos subject) {
        if (sigil == null || subject == null) {
            return Optional.empty();
        }
        final double dx = subject.getX() - sigil.getX();
        final double dz = subject.getZ() - sigil.getZ();
        if (dx * dx + dz * dz > MAX_CENTRE_OFFSET_SQUARED) {
            return Optional.empty();
        }
        final int y = Math.clamp(
            subject.getY(), sigil.getY() - MAX_CENTRE_LIFT, sigil.getY() + MAX_CENTRE_LIFT
        );
        return Optional.of(new BlockPos(subject.getX(), y, subject.getZ()));
    }

    /**
     * Vertex {@code index} of the seal around {@code centre}. The index is reduced into range so a
     * corrupted phase can never index outside the fixed table.
     */
    public static BlockPos vertex(final BlockPos centre, final int index) {
        final Vec3i unit = UNIT_VERTICES[Math.floorMod(index, SEAL_VERTICES)];
        return centre.offset(
            unit.getX() * SEAL_RADIUS, SEAL_LIFT, unit.getZ() * SEAL_RADIUS
        ).immutable();
    }

    /**
     * Which vertex a phase is tracing, or {@code -1} for a phase that traces none.
     *
     * <p>This is the mapping that makes the far vertices reachable. A seal that could only ever
     * consult {@code vertex(centre, 0)} would trace one point forever and the other two would never
     * be evaluated, which is the shape that has broken five other families' searches. Here the
     * index is a function of the phase, and {@link #phaseAfterVertex} advances the phase, so
     * reaching vertex 0 is the only way to begin looking at vertex 1 and the only exit from vertex
     * 2 is the close.</p>
     */
    public static int vertexIndex(final Phase phase) {
        return switch (phase) {
            case INSCRIBE_1 -> 0;
            case INSCRIBE_2 -> 1;
            case INSCRIBE_3 -> 2;
            case DORMANT, CLOSE, STRIKE, RECOVER -> -1;
        };
    }

    /** The phase that follows reaching the vertex a tracing phase owns. */
    public static Phase phaseAfterVertex(final Phase phase) {
        return switch (phase) {
            case INSCRIBE_1 -> Phase.INSCRIBE_2;
            case INSCRIBE_2 -> Phase.INSCRIBE_3;
            case INSCRIBE_3 -> Phase.CLOSE;
            case DORMANT, CLOSE, STRIKE, RECOVER -> phase;
        };
    }

    /** The duration a phase runs for. {@code DORMANT} has none, which is why it is idle. */
    public static int phaseTicks(final Phase phase) {
        return switch (phase) {
            case DORMANT -> 0;
            case INSCRIBE_1, INSCRIBE_2, INSCRIBE_3 -> INSCRIBE_TICKS;
            case CLOSE -> CLOSE_TICKS;
            case STRIKE -> STRIKE_TICKS;
            case RECOVER -> RECOVER_TICKS;
        };
    }

    /** Whether a phase is part of an open seal that an ending may cancel. */
    public static boolean sealing(final Phase phase) {
        return switch (phase) {
            case DORMANT, RECOVER -> false;
            case INSCRIBE_1, INSCRIBE_2, INSCRIBE_3, CLOSE, STRIKE -> true;
        };
    }

    public static boolean vertexReached(final double distanceSquared) {
        return distanceSquared <= VERTEX_REACH_SQUARED;
    }

    public static boolean centreHeld(final double centreOffsetSquared) {
        return centreOffsetSquared <= CENTRE_HOLD_SQUARED;
    }

    // ---------------------------------------------------------------- lifecycle policy

    public static boolean sealStartAllowed(final int cooldownTicks, final boolean alreadyAppointed) {
        return cooldownTicks <= 0 && !alreadyAppointed;
    }

    /** A hazard preempts every open seal phase and nothing else. Recovery is already an ending. */
    public static boolean hazardPreempts(final Phase phase, final boolean hazard) {
        return hazard && sealing(phase);
    }

    /**
     * The single ending decision. Deliberately total over the observation rather than a chain of
     * early returns in the runtime, so every reason a seal can break is enumerated in one place and
     * every one of them is reachable from a unit test without a level.
     */
    public static SealEnd sealEnd(final SubjectObservation observed) {
        if (!observed.appointed()) {
            return SealEnd.SUBJECT_LOST;
        }
        if (!observed.sameDimension()) {
            return SealEnd.DIMENSION_LOST;
        }
        if (!observed.resolved()) {
            return SealEnd.SUBJECT_LOST;
        }
        if (!observed.eligible()) {
            return SealEnd.SUBJECT_INELIGIBLE;
        }
        if (!observed.geometryHeld()) {
            return SealEnd.GEOMETRY_LOST;
        }
        if (observed.routeFailures() >= MAX_ROUTE_FAILURES) {
            return SealEnd.ROUTE_FAILURE;
        }
        if (!centreHeld(observed.centreOffsetSquared())) {
            return SealEnd.LEFT_CENTRE;
        }
        return SealEnd.NONE;
    }

    // ---------------------------------------------------------------- strike policy

    /**
     * The complete strike gate. Every clause is required: the latch, the open window, the melee
     * band, real visibility, and the subject still standing inside the centre it was sealed in.
     */
    public static boolean strikeAllowed(
        final int strikes,
        final int strikeRemainingTicks,
        final double distanceSquared,
        final boolean visible,
        final double centreOffsetSquared
    ) {
        return strikes < MAX_STRIKES
            && strikeRemainingTicks > 0
            && visible
            && distanceSquared <= STRIKE_BAND_SQUARED
            && centreHeld(centreOffsetSquared);
    }

    /** The Sigil's own registry attack value, unmodified. It adds nothing and multiplies nothing. */
    public static float strikeDamage(final float attackAttribute) {
        return Math.max(0.0F, attackAttribute);
    }

    // ---------------------------------------------------------------- routing policy

    /**
     * Three consecutive failures, then a flat hundred-tick window. Flat rather than geometric
     * because a seal is released on the third failure anyway, so a growing window would only ever
     * describe the gap between whole episodes.
     */
    public static RouteRequest.RouteBackoff routeBackoff() {
        return new RouteRequest.RouteBackoff(
            MAX_ROUTE_FAILURES, ROUTE_BACKOFF_TICKS, ROUTE_BACKOFF_TICKS
        );
    }

    public static RouteRequest freshRoute() {
        return RouteRequest.every(PATH_INTERVAL_TICKS);
    }

    public static boolean routeExhausted(final int failures) {
        return failures >= MAX_ROUTE_FAILURES;
    }

    /** Whether one more Sigil in this level may start a path on this tick. */
    public static boolean pathStartAllowed(final int startsAlreadySpent) {
        return startsAlreadySpent < MAX_PATH_STARTS_PER_LEVEL_TICK;
    }

    /**
     * A fresh route ledger for the seal that is about to begin, preserving an open backoff.
     *
     * <p>Route failures accumulated while the Sigil was dormant belong to the dormancy, not to the
     * seal that follows. Without this reset a Sigil that failed three times drifting in an enclosed
     * space carries those failures into a fresh seal and {@link #sealEnd} releases it on
     * {@link SealEnd#ROUTE_FAILURE} before it has traced a single vertex. The open backoff window
     * is deliberately carried across, so the reset can never be used to spam path requests.</p>
     */
    public static RouteRequest routeForNewSeal(final RouteRequest carried) {
        return new RouteRequest(
            carried.cadence(), 0, carried.backoffRemaining()
        );
    }
}
