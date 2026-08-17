package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F13 Hedge Crone policy. No {@code Level}, {@code Entity}, {@code Path}, container, mutable
 * registry, or random source may enter this class: every input is a scalar or an immutable record,
 * so the whole boundary/ward/hex contract is unit-testable without a server.
 *
 * <p>Deliberately separate from {@link CircleMageRules}. The two practitioners share Minecraft
 * primitives and common safety shapes but never a motive, a priority order, or a controller.</p>
 */
public final class HedgeCroneRules {
    /** Immutable warning hold before a boundary candidate may escalate. */
    public static final int WARNING_TICKS = 20;
    public static final int HEX_WINDUP_TICKS = 20;
    public static final int CAST_RECOVERY_TICKS = 60;

    public static final int BOUNDARY_RADIUS = 12;
    public static final int THREAT_RELEASE_RADIUS = 18;
    public static final int PERCEPTION_RADIUS = 16;
    public static final double BOUNDARY_RADIUS_SQUARED = (double) BOUNDARY_RADIUS * BOUNDARY_RADIUS;
    public static final double THREAT_RELEASE_RADIUS_SQUARED =
        (double) THREAT_RELEASE_RADIUS * THREAT_RELEASE_RADIUS;

    public static final int THREAT_TICKS = 200;
    public static final int LOST_SIGHT_RELEASE_TICKS = 60;

    public static final int SCAN_INTERVAL_TICKS = 20;
    public static final int MAX_CANDIDATES_VISITED = 16;
    public static final int MAX_RETAINED_CANDIDATES = 8;
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 8;

    public static final int WORKSTATION_INTERVAL_TICKS = 100;
    public static final int MAX_WORKSTATION_CANDIDATES = 64;
    public static final int MAX_WORKSTATION_READS = 128;
    public static final int WORKSTATION_HORIZONTAL_RADIUS = 8;
    public static final int WORKSTATION_VERTICAL_RADIUS = 2;
    /** Arrival distance for the workstation preparation hold. */
    public static final double WORKSTATION_ARRIVAL_DISTANCE_SQUARED = 6.25D;

    public static final int PREPARATION_TICKS = 60;
    public static final int WARD_COOLDOWN_TICKS = 1_200;
    public static final float WARD_BASE_DAMAGE = 2.0F;
    public static final float WARD_DAMAGE_SCALE = 0.25F;
    public static final float WARD_MAX_DAMAGE = 6.0F;

    public static final int ANCHOR_RETURN_RADIUS = 16;
    public static final double ANCHOR_RETURN_RADIUS_SQUARED =
        (double) ANCHOR_RETURN_RADIUS * ANCHOR_RETURN_RADIUS;
    public static final int ANCHOR_ADOPT_DELAY_TICKS = 100;
    public static final int ANCHOR_REPLACE_TICKS = 1_200;

    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_READS = 27;
    public static final int MAX_SAFE_CANDIDATES = 24;
    public static final int MAX_CHARGED_READS = 256;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final float WITHDRAW_HEALTH_FRACTION = 0.25F;
    public static final int WITHDRAW_TICKS = 100;

    public static final int CAST_MIN_RANGE = 3;
    public static final int CAST_MAX_RANGE = 14;
    public static final double CAST_MIN_RANGE_SQUARED = (double) CAST_MIN_RANGE * CAST_MIN_RANGE;
    public static final double CAST_MAX_RANGE_SQUARED = (double) CAST_MAX_RANGE * CAST_MAX_RANGE;

    public static final int MAX_FEEDBACK_PARTICLES = 8;

    /**
     * Bounded future sentinel. A persisted deadline further away than this is treated as corrupt
     * and clamped instead of surviving as an effectively permanent block; a zero remaining value
     * always reads as due, so no sentinel may ever be {@code Long.MAX_VALUE}.
     */
    public static final int MAX_DEADLINE_TICKS = 20_000;

    /** Representative encoded-state ceiling asserted by the state tests and the live save fixture. */
    public static final int MAX_STATE_BYTES = 768;

    private HedgeCroneRules() {
    }

    public enum Mode {
        IDLE,
        WARNING,
        CASTING,
        PREPARING,
        WITHDRAWING,
        RETURNING
    }

    public enum Action {
        NONE,
        HEX,
        WARD_PREPARATION
    }

    public enum ThreatClass {
        NONE,
        BOUNDARY_WARNED,
        BOUNDARY_ESCALATED,
        DIRECT
    }

    /** Internal action labels only. They are not a historical or folkloric claim. */
    public enum Hex {
        VEIL,
        BINDING,
        ENFEEBLE,
        WITHER
    }

    public enum Priority {
        HAZARD,
        ACTION,
        DIRECT_DEFENSE,
        WITHDRAW,
        ESCALATED_THREAT,
        WARNING,
        WARD_PREPARATION,
        ANCHOR_RETURN
    }

    /** Directly observed relationship facts about one already-inspected candidate. */
    public record RelationFacts(
        boolean living,
        boolean alive,
        boolean sameDimension,
        boolean self,
        boolean invulnerableOrUnattackable,
        boolean survivalPlayer,
        boolean creativeOrSpectatorPlayer,
        boolean hedgeCrone,
        boolean protectedPassive,
        boolean ownedOrFamiliar,
        boolean circleMage,
        boolean acceptedDirectAttacker
    ) {
    }

    /** One bounded, already-inspected candidate. Never a live entity reference. */
    public record Candidate(
        UUID id,
        boolean directAttacker,
        boolean alreadyWarned,
        double distanceSquared,
        boolean visible
    ) {
        public Candidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** The observed context that selects one contextual hex. */
    public record HexContext(
        boolean attackedWithProjectile,
        boolean holdsTaggedRangedWeapon,
        boolean withinSixBlocks,
        boolean currentDirectAttacker,
        boolean escalatedBoundaryIntruder
    ) {
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    /** One deterministic candidate offset inside a bounded search envelope. */
    public record SearchOffset(int dx, int dy, int dz) {
    }

    /** The lexicographic facts of one evaluated safe candidate. */
    public record SafeCandidate(
        double separationSquared,
        boolean hazardFree,
        double displacementSquared,
        long packedPosition
    ) {
    }

    // ---------------------------------------------------------------- relationship legality

    /**
     * Absolute exclusions first, then the Circle Mage species preference. A Circle Mage becomes
     * legal only as an accepted direct attacker; it is never proactive prey.
     */
    public static boolean relationLegal(final RelationFacts facts) {
        Objects.requireNonNull(facts, "facts");
        if (!facts.living() || !facts.alive() || !facts.sameDimension()) {
            return false;
        }
        if (facts.self() || facts.invulnerableOrUnattackable() || facts.creativeOrSpectatorPlayer()) {
            return false;
        }
        if (facts.hedgeCrone() || facts.protectedPassive() || facts.ownedOrFamiliar()) {
            return false;
        }
        if (facts.circleMage()) {
            return facts.acceptedDirectAttacker();
        }
        return true;
    }

    /** Only accepted direct self-defense or a completed boundary escalation acquires a target. */
    public static boolean motiveAcquires(final ThreatClass threatClass) {
        return threatClass == ThreatClass.DIRECT || threatClass == ThreatClass.BOUNDARY_ESCALATED;
    }

    // ---------------------------------------------------------------- boundary warning

    public static boolean boundaryCandidate(
        final boolean survivalPlayer,
        final boolean visible,
        final double distanceSquaredFromAnchor
    ) {
        return survivalPlayer && visible && distanceSquaredFromAnchor <= BOUNDARY_RADIUS_SQUARED;
    }

    /**
     * Escalation requires the identical UUID in the identical dimension, still legal, still
     * visible, and still inside the boundary at execution. Anything else cancels and can never
     * rebind to a replacement entity.
     */
    public static boolean warningEscalates(
        final boolean sameIdentity,
        final boolean stillLegal,
        final boolean visible,
        final double distanceSquaredFromAnchor
    ) {
        return sameIdentity && stillLegal && visible
            && distanceSquaredFromAnchor <= BOUNDARY_RADIUS_SQUARED;
    }

    /**
     * An escalated boundary threat releases immediately outside eighteen blocks or after sixty
     * consecutive ticks without valid loaded line of sight. A direct attacker releases only on
     * expiry or invalidity, so a fleeing attacker is not forgotten by distance alone.
     */
    public static boolean threatReleases(
        final ThreatClass threatClass,
        final boolean valid,
        final double distanceSquared,
        final int consecutiveTicksWithoutSight,
        final int remainingThreatTicks
    ) {
        if (threatClass == ThreatClass.NONE) {
            return true;
        }
        if (!valid || remainingThreatTicks <= 0) {
            return true;
        }
        if (threatClass != ThreatClass.BOUNDARY_ESCALATED) {
            return false;
        }
        return distanceSquared > THREAT_RELEASE_RADIUS_SQUARED
            || consecutiveTicksWithoutSight >= LOST_SIGHT_RELEASE_TICKS;
    }

    /**
     * Deterministic ranking over candidates that were actually inspected. A valid direct attacker
     * and the currently warned candidate are preseeded ahead of generic traversal order, then
     * shorter distance, then UUID as the final tie-break, so a crowd larger than the visit budget
     * can never bury the real threat.
     */
    public static List<Candidate> rank(final List<Candidate> inspected) {
        Objects.requireNonNull(inspected, "inspected");
        // Preseeding is what makes a crowd larger than the visit budget safe: the direct attacker
        // and the currently warned candidate are admitted regardless of where generic traversal
        // order happened to place them, and only the generic remainder is truncated.
        final List<Candidate> ordered = new ArrayList<>(inspected.stream()
            .filter(candidate -> candidate.directAttacker() || candidate.alreadyWarned())
            .limit(MAX_RETAINED_CANDIDATES)
            .toList());
        inspected.stream()
            .filter(candidate -> !candidate.directAttacker() && !candidate.alreadyWarned())
            .limit(MAX_CANDIDATES_VISITED)
            .forEach(ordered::add);
        ordered.sort(Comparator
            .comparingInt((Candidate candidate) -> candidate.directAttacker() ? 0 : 1)
            .thenComparingInt(candidate -> candidate.alreadyWarned() ? 0 : 1)
            .thenComparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<Candidate> select(final List<Candidate> inspected) {
        return rank(inspected).stream().filter(Candidate::visible).findFirst();
    }

    // ---------------------------------------------------------------- contextual hex

    /** The exact existing four-effect table, chosen by observed context in strict priority. */
    public static Hex selectHex(final HexContext context) {
        Objects.requireNonNull(context, "context");
        if (context.attackedWithProjectile() || context.holdsTaggedRangedWeapon()) {
            return Hex.VEIL;
        }
        if (context.withinSixBlocks()) {
            return Hex.BINDING;
        }
        if (context.currentDirectAttacker()) {
            return Hex.ENFEEBLE;
        }
        return Hex.WITHER;
    }

    public static int hexDurationTicks(final Hex hex) {
        return switch (hex) {
            case VEIL -> 80;
            case BINDING, ENFEEBLE -> 160;
            case WITHER -> 120;
        };
    }

    public static int hexAmplifier(final Hex hex) {
        return switch (hex) {
            case VEIL, WITHER -> 0;
            case BINDING, ENFEEBLE -> 1;
        };
    }

    public static boolean castEligible(
        final boolean lineOfSight,
        final boolean relationLegal,
        final double distanceSquared
    ) {
        return lineOfSight && relationLegal
            && distanceSquared >= CAST_MIN_RANGE_SQUARED
            && distanceSquared <= CAST_MAX_RANGE_SQUARED;
    }

    /** During any immutable action the frozen target UUID may never be replaced. */
    public static boolean mayRetarget(
        final Action current,
        final UUID frozenTarget,
        final UUID candidate
    ) {
        if (current == Action.NONE) {
            return true;
        }
        return frozenTarget != null && frozenTarget.equals(candidate);
    }

    // ---------------------------------------------------------------- ward

    public static boolean wardPreparationAllowed(
        final boolean wardPrepared,
        final int cooldownRemainingTicks,
        final boolean safeAndCalm,
        final boolean threatPresent,
        final boolean withdrawing
    ) {
        return !wardPrepared && cooldownRemainingTicks <= 0 && safeAndCalm
            && !threatPresent && !withdrawing;
    }

    /** The exact existing thorns formula {@code min(6, 2 + amount * 0.25)}. */
    public static float wardDamage(final float acceptedIncomingDamage) {
        if (!Float.isFinite(acceptedIncomingDamage) || acceptedIncomingDamage <= 0.0F) {
            return WARD_BASE_DAMAGE;
        }
        return Math.min(
            WARD_MAX_DAMAGE,
            WARD_BASE_DAMAGE + acceptedIncomingDamage * WARD_DAMAGE_SCALE
        );
    }

    public static boolean wardDischarges(
        final boolean wardPrepared,
        final boolean legalLivingSource,
        final float acceptedIncomingDamage,
        final boolean recursionGuardActive
    ) {
        return wardPrepared && legalLivingSource && !recursionGuardActive
            && Float.isFinite(acceptedIncomingDamage) && acceptedIncomingDamage > 0.0F;
    }

    // ---------------------------------------------------------------- health and priority

    public static float healthFraction(final float health, final float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
            return 1.0F;
        }
        return Math.clamp(health / maxHealth, 0.0F, 1.0F);
    }

    public static boolean shouldWithdraw(final float healthFraction) {
        return healthFraction <= WITHDRAW_HEALTH_FRACTION;
    }

    /**
     * The frozen strict order. No lower branch may write navigation, target, state, particles, or
     * effects once a higher branch has claimed the tick.
     */
    public static Priority priority(
        final boolean urgentHazard,
        final boolean immutableActionPending,
        final boolean directDefense,
        final boolean lowHealth,
        final boolean escalatedThreat,
        final boolean boundaryWarning,
        final boolean wardPreparation
    ) {
        if (urgentHazard) {
            return Priority.HAZARD;
        }
        if (immutableActionPending) {
            return Priority.ACTION;
        }
        if (directDefense) {
            return Priority.DIRECT_DEFENSE;
        }
        if (lowHealth) {
            return Priority.WITHDRAW;
        }
        if (escalatedThreat) {
            return Priority.ESCALATED_THREAT;
        }
        if (boundaryWarning) {
            return Priority.WARNING;
        }
        return wardPreparation ? Priority.WARD_PREPARATION : Priority.ANCHOR_RETURN;
    }

    // ---------------------------------------------------------------- movement lease

    public static boolean pathRequestAllowed(
        final int remainingPathTicks,
        final int backoffRemainingTicks
    ) {
        return remainingPathTicks <= 0 && backoffRemainingTicks <= 0;
    }

    public static int routeFailuresAfter(final int failures, final RouteResult result) {
        return result.success() ? 0 : Math.clamp(failures + 1, 0, MAX_ROUTE_FAILURES);
    }

    public static boolean routeExhausted(final int failures) {
        return failures >= MAX_ROUTE_FAILURES;
    }

    public static int routeBackoffAfter(final int failures) {
        return routeExhausted(failures) ? ROUTE_BACKOFF_TICKS : 0;
    }

    // ---------------------------------------------------------------- anchor

    public static boolean anchorReturnRequired(final boolean calm, final double distanceSquared) {
        return calm && distanceSquared > ANCHOR_RETURN_RADIUS_SQUARED;
    }

    public static boolean mayAdoptReplacementAnchor(
        final int idleTicksWithoutUsableAnchor,
        final boolean safeHere
    ) {
        return safeHere && idleTicksWithoutUsableAnchor >= ANCHOR_REPLACE_TICKS;
    }

    public static boolean mayAdoptAfterDimensionChange(final int safeLoadedTicks) {
        return safeLoadedTicks >= ANCHOR_ADOPT_DELAY_TICKS;
    }

    // ---------------------------------------------------------------- durations

    public static int clampRemaining(final int stored, final int maximum) {
        if (stored < 0) {
            return 0;
        }
        return Math.min(stored, Math.max(0, maximum));
    }

    public static int decrementLoaded(final int remaining) {
        return Math.max(0, remaining - 1);
    }

    /** Staggers per-entity cadence deterministically without ever using absolute world time. */
    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(
            id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span
        );
    }

    // ---------------------------------------------------------------- bounded search envelopes

    private static final int[][] COMPASS =
        {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    /**
     * Deterministic rotating workstation offset table inside the horizontal radius and the exact
     * two-block vertical delta, so a workstation one block above or below the Crone's feet is
     * genuinely reachable within the sixty-four candidate budget.
     */
    public static List<SearchOffset> workstationOffsets(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget
    ) {
        return sweepEnvelope(id, horizontalRadius, verticalRadius, budget, MAX_WORKSTATION_CANDIDATES);
    }

    /**
     * Deterministic safe-destination envelope sharing the twenty-four candidate budget. The
     * vertical layers are genuinely reachable, so hazard escape can route up or down a block.
     */
    public static List<SearchOffset> safeSearchOffsets(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget
    ) {
        return sweepEnvelope(id, horizontalRadius, verticalRadius, budget, MAX_SAFE_CANDIDATES);
    }

    /**
     * The one bounded search envelope both F13 practitioners use. It is a pure geometric
     * primitive with no motive content at all, so sharing it couples no behavior: the Crone and
     * the Mage still own separate rules, state, runtimes, target policies, and movement schedules.
     * It lives here rather than being copied because the duplicated copy was the direct cause of
     * an unreachable-envelope defect.
     *
     * <p>Enumeration is a three-digit odometer whose digits advance at deliberately different
     * rates, so the complete envelope is genuinely reachable inside the candidate budget rather
     * than exhausting it on the inner rings at one height:</p>
     *
     * <ul>
     *   <li>the ring advances every single step, so every ring up to the horizontal radius is
     *       reached within {@code rings} candidates;</li>
     *   <li>the vertical layer advances by {@code index + index / rings}, which stays coprime with
     *       the layer count even when the ring count is not, so every layer in the whole vertical
     *       delta is reached within a few candidates;</li>
     *   <li>the compass direction advances every step plus one extra turn per completed
     *       ring-and-layer block, which keeps every emitted triple distinct once the ring and
     *       layer pair wraps.</li>
     * </ul>
     *
     * <p>The origin is never produced, duplicates are removed so no candidate is wasted, and the
     * per-entity UUID rotation only rotates the compass so repeated searches sweep coverage.</p>
     *
     * <p><strong>Completeness is a property of each real caller's configuration, not of the
     * enumeration in general.</strong> A budget smaller than the cell count cannot visit every
     * cell: at radius three with delta one the envelope has seventy-two cells and a twenty-five
     * candidate budget reaches thirty-six of them. What is guaranteed for every configuration is
     * that the emitted candidates are distinct, origin free, inside the envelope, and that the
     * budget is filled whenever the envelope is large enough to fill it. What is asserted per
     * caller, for every rotation, is that each ring, each vertical layer, and all eight directions
     * are actually reached. Hand traced at rotation zero for the three real callers:</p>
     *
     * <ul>
     *   <li>Crone and Mage safe destination (radius 6, delta 2, budget 24): first candidates
     *       {@code (1,0,0) (0,1,2) (-3,-1,0) (0,2,-4) (5,-2,5) (-6,0,6)}; all 24 distinct, rings
     *       one through six, layers -2 through +2, all eight directions.</li>
     *   <li>Crone and Mage workstation (radius 8, delta 2, budget 64): first candidates
     *       {@code (1,0,0) (0,1,2) (-3,-1,0) (0,2,-4) (5,-2,5) (-6,0,6) (-7,1,-7) (8,-1,-8)};
     *       all 64 distinct, rings one through eight, layers -2 through +2.</li>
     *   <li>Mage owner safe step (radius 3, delta 1, budget 25): first candidates
     *       {@code (1,0,0) (0,1,2) (-3,-1,0) (0,1,-1) (2,-1,2)}; all 25 distinct, rings one
     *       through three, layers -1 through +1.</li>
     * </ul>
     */
    static List<SearchOffset> sweepEnvelope(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget,
        final int hardCap
    ) {
        final int rings = Math.max(1, horizontalRadius);
        final int[] layers = verticalLayers(Math.max(0, verticalRadius));
        final int cap = Math.clamp(budget, 0, hardCap);
        final int rotation = stableOffset(id, COMPASS.length);
        final int block = rings * layers.length;
        final int total = block * COMPASS.length;
        final LinkedHashSet<SearchOffset> offsets = new LinkedHashSet<>();
        for (int index = 0; index < total && offsets.size() < cap; index++) {
            final int ring = 1 + index % rings;
            final int dy = layers[(index + index / rings) % layers.length];
            final int[] direction =
                COMPASS[(index + index / block + rotation) % COMPASS.length];
            final SearchOffset offset =
                new SearchOffset(direction[0] * ring, dy, direction[1] * ring);
            if (offset.dx() != 0 || offset.dy() != 0 || offset.dz() != 0) {
                offsets.add(offset);
            }
        }
        return List.copyOf(offsets);
    }

    private static int[] verticalLayers(final int vertical) {
        if (vertical <= 0) {
            return new int[] {0};
        }
        final int[] layers = new int[vertical * 2 + 1];
        layers[0] = 0;
        for (int step = 1; step <= vertical; step++) {
            layers[step * 2 - 1] = step;
            layers[step * 2] = -step;
        }
        return layers;
    }

    /**
     * The design's lexicographic preference: greater separation from the threat first, then hazard
     * safety, then shorter displacement, then the stable packed position. Deliberately not a
     * weighted score.
     */
    public static Comparator<SafeCandidate> safeCandidatePreference() {
        return Comparator.comparingDouble(SafeCandidate::separationSquared).reversed()
            .thenComparingInt(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(SafeCandidate::displacementSquared)
            .thenComparingLong(SafeCandidate::packedPosition);
    }
}
