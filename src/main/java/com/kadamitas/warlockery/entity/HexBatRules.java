package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure deterministic rules and constants for the dedicated Hex Bat.
 * Rules receive value objects or primitive facts only; they never read a
 * level, entity collection, path, block, global random, wall clock,
 * capability, or client state.
 */
public final class HexBatRules {
    // Perception.
    public static final int TARGET_SCAN_INTERVAL_TICKS = 20;
    public static final int TARGET_QUERY_RADIUS = 16;
    public static final int MAX_TARGET_VISITS = 16;
    public static final int MAX_RETAINED_TARGETS = 8;
    public static final int MAX_LINE_OF_SIGHT_CLIPS = 8;
    public static final int PROACTIVE_ACQUIRE_RANGE = 12;
    public static final int CHASE_RANGE = 16;
    public static final int UNSEEN_RELEASE_TICKS = 80;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final int OWNER_DEFENSE_RANGE = 12;
    public static final int ROOST_GUARD_RANGE = 4;

    // Exact-species calls.
    public static final int CALL_SCAN_INTERVAL_TICKS = 40;
    public static final int CALL_RADIUS = 12;
    public static final int MAX_PEER_VISITS = 8;
    public static final int MAX_CALL_RECIPIENTS = 3;
    public static final int CALL_DEDUPE_TICKS = 40;
    public static final int CALL_EXPIRY_TICKS = 80;
    public static final int MAX_CALL_HOPS = 1;

    // Schedule and roost.
    public static final long NIGHT_START_TIME = 13_000L;
    public static final long NIGHT_END_TIME = 23_000L;
    public static final int ROOST_SEARCH_INTERVAL_TICKS = 80;
    public static final int MAX_ROOST_CANDIDATES = 48;
    public static final int MAX_ROOST_BLOCK_READS = 128;
    public static final int ROOST_HORIZONTAL_RANGE = 10;
    public static final int ROOST_VERTICAL_RANGE = 6;
    public static final int SORTIE_RANGE = 10;
    public static final int SORTIE_MAX_TICKS = 600;

    // Hazards and withdrawal.
    public static final int HAZARD_SCAN_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_BLOCK_READS = 27;
    public static final float WITHDRAW_HEALTH_FRACTION = 0.20F;
    public static final int WITHDRAW_TICKS = 100;
    public static final int WITHDRAW_ESCAPE_RANGE = 8;

    // Movement.
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int MAX_DESTINATION_CANDIDATES = 24;
    public static final int MAX_DESTINATION_BLOCK_READS = 256;

    // Jinx swoop.
    public static final int SWOOP_WINDUP_TICKS = 10;
    public static final int SWOOP_EXECUTE_TICKS = 40;
    public static final int SWOOP_RECOVERY_TICKS = 60;
    public static final int POST_CONTACT_WITHDRAW_TICKS = 40;
    public static final int JINX_DURATION_TICKS = 200;
    public static final int JINX_AMPLIFIER = 0;

    // Feedback caps.
    public static final int MAX_TELEGRAPH_PARTICLES = 6;
    public static final int MAX_CALL_PARTICLES = 4;
    public static final int MAX_CONTACT_PARTICLES = 8;
    public static final int SOUND_INTERVAL_TICKS = 20;

    // Flight chassis. The deferred ModEntities attribute edit reads this
    // exact value; it matches the existing non-Imp winged convention.
    public static final double FLYING_SPEED = 0.34D;

    // Deadline safety.
    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
        .comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
        .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private HexBatRules() {
    }

    public enum Provenance {
        UNBOUND, MURDEROUS_FLOCK
    }

    public enum Mode {
        SHELTER, SORTIE, INTERCEPT, WITHDRAW, HAZARD
    }

    public enum Action {
        NONE, SWOOP
    }

    public enum DestinationPurpose {
        NONE, ROOST, PATROL, ESCAPE, WITHDRAW
    }

    // ---- schedule ----

    public static boolean isNight(final long dayTime) {
        final long clock = Math.floorMod(dayTime, 24_000L);
        return clock >= NIGHT_START_TIME && clock <= NIGHT_END_TIME;
    }

    // ---- relationships and target legality ----

    /** Facts that make a target absolutely illegal regardless of context. */
    public record AbsoluteFacts(
        boolean self,
        boolean deadOrRemoved,
        boolean invulnerableOrUnattackable,
        boolean isOwner,
        boolean sameOwner,
        boolean exactHexBat,
        boolean creativeOrSpectator,
        boolean otherDimensionOrUnloaded
    ) {
    }

    public static boolean absolutelyExcluded(final AbsoluteFacts facts) {
        return facts.self()
            || facts.deadOrRemoved()
            || facts.invulnerableOrUnattackable()
            || facts.isOwner()
            || facts.sameOwner()
            || facts.exactHexBat()
            || facts.creativeOrSpectator()
            || facts.otherDimensionOrUnloaded();
    }

    /** Facts that block only proactive acquisition, not direct-attacker answers. */
    public record ProactiveFacts(
        boolean noncombatant,
        boolean witchOrOwl,
        boolean otherArcaneCreature,
        boolean beyondProactiveRange,
        boolean noLineOfSight
    ) {
    }

    public static boolean proactivelyExcluded(final ProactiveFacts facts) {
        return facts.noncombatant()
            || facts.witchOrOwl()
            || facts.otherArcaneCreature()
            || facts.beyondProactiveRange()
            || facts.noLineOfSight();
    }

    /** Contextual priority for a legal candidate; lower rank is stronger. */
    public record TargetCandidate(
        UUID id,
        int priorityRank,
        double distanceSqr
    ) {
        public static final int RANK_DIRECT_ATTACKER = 0;
        public static final int RANK_EXPLICIT_FLOCK = 1;
        public static final int RANK_OWNER_ATTACKER = 2;
        public static final int RANK_STABLE_CURRENT = 3;
        public static final int RANK_MARKED = 4;
        public static final int RANK_SURVIVAL_PLAYER = 5;
        public static final int RANK_ORDINARY_HOSTILE = 6;
    }

    public static Comparator<TargetCandidate> targetOrder() {
        return Comparator
            .comparingInt(TargetCandidate::priorityRank)
            .thenComparingDouble(TargetCandidate::distanceSqr)
            .thenComparing(TargetCandidate::id, UNSIGNED_UUID_ORDER);
    }

    /**
     * Deterministic bounded accumulation: priority candidates are preseeded
     * before generic traversal so they cannot be lost beyond the cap.
     */
    public static List<TargetCandidate> retainCandidates(
        final List<TargetCandidate> preseeded,
        final List<TargetCandidate> generic
    ) {
        final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
        final List<TargetCandidate> retained = new ArrayList<>();
        for (final TargetCandidate candidate : preseeded) {
            if (retained.size() >= MAX_RETAINED_TARGETS) break;
            if (seen.add(candidate.id())) retained.add(candidate);
        }
        for (final TargetCandidate candidate : generic) {
            if (retained.size() >= MAX_RETAINED_TARGETS) break;
            if (seen.add(candidate.id())) retained.add(candidate);
        }
        return List.copyOf(retained);
    }

    public static Optional<TargetCandidate> selectTarget(final List<TargetCandidate> retained) {
        return retained.stream().min(targetOrder());
    }

    public record ReleaseFacts(
        boolean targetDeadOrInvalid,
        boolean creativeOrSpectator,
        boolean dimensionChangedOrUnloaded,
        boolean beyondChaseRange,
        boolean unseenTooLong,
        boolean protectedByOwnerRelation,
        boolean withdrawOrHazardPriority
    ) {
    }

    public static boolean shouldRelease(final ReleaseFacts facts) {
        return facts.targetDeadOrInvalid()
            || facts.creativeOrSpectator()
            || facts.dimensionChangedOrUnloaded()
            || facts.beyondChaseRange()
            || facts.unseenTooLong()
            || facts.protectedByOwnerRelation()
            || facts.withdrawOrHazardPriority();
    }

    /**
     * The 80-tick unseen release. A zero baseline means no sighting evidence
     * has been recorded yet, which cannot release on its own.
     */
    public static boolean unseenTooLong(final long lastSeenAt, final long now) {
        return lastSeenAt > 0L && now - lastSeenAt > UNSEEN_RELEASE_TICKS;
    }

    // ---- Murderous Flock ranked accumulator ----

    /**
     * Strict priority: a ROOST invitation is quiet-mode work and may never
     * issue movement into, or overwrite a destination of, a peer holding
     * hazard, withdrawal, or a bound action.
     */
    public static boolean mayAcceptRoostInvite(final Mode mode, final Action action) {
        return (mode == Mode.SHELTER || mode == Mode.SORTIE) && action == Action.NONE;
    }

    /** Ranked once-per-cast flock candidate; lower rank is stronger. */
    public record FlockCandidate(UUID id, int rank, double distanceSqr) {
        public static final int RANK_EXPLICIT_TARGET = 0;
        public static final int RANK_OWNER_ATTACKER = 1;
        public static final int RANK_JINX_MARKED = 2;
        public static final int RANK_SURVIVAL_PLAYER = 3;
        public static final int RANK_ORDINARY_HOSTILE = 4;
    }

    /**
     * Strict flock ranking: explicit target, owner attacker, jinx, survival
     * player, ordinary hostile, then distance, then unsigned UUID. An explicit
     * cast target can never lose to a nearer hostile.
     */
    public static Comparator<FlockCandidate> flockOrder() {
        return Comparator
            .comparingInt(FlockCandidate::rank)
            .thenComparingDouble(FlockCandidate::distanceSqr)
            .thenComparing(FlockCandidate::id, UNSIGNED_UUID_ORDER);
    }

    // ---- calls ----

    public static boolean callCompatible(final Optional<UUID> callerOwner, final Optional<UUID> peerOwner) {
        if (callerOwner.isEmpty() && peerOwner.isEmpty()) return true;
        return callerOwner.isPresent() && peerOwner.isPresent()
            && callerOwner.orElseThrow().equals(peerOwner.orElseThrow());
    }

    public static boolean mayEmitCall(final int hopCount, final long dedupeUntil, final long now) {
        return hopCount < MAX_CALL_HOPS && dedupeUntil <= now;
    }

    public static boolean reportExpired(final long expiresAt, final long now) {
        return expiresAt <= now;
    }

    // ---- roost ----

    public record RoostFacts(
        boolean supportTagged,
        boolean airSafe,
        boolean fluidSafe,
        boolean collisionFree,
        boolean insideWorldBorder,
        boolean loaded,
        boolean withinAnchorEnvelope,
        boolean unoccupied
    ) {
    }

    public static boolean validRoost(final RoostFacts facts) {
        return facts.supportTagged()
            && facts.airSafe()
            && facts.fluidSafe()
            && facts.collisionFree()
            && facts.insideWorldBorder()
            && facts.loaded()
            && facts.withinAnchorEnvelope()
            && facts.unoccupied();
    }

    public static boolean withinAnchorEnvelope(
        final int dx, final int dy, final int dz
    ) {
        return Math.abs(dx) <= ROOST_HORIZONTAL_RANGE
            && Math.abs(dz) <= ROOST_HORIZONTAL_RANGE
            && Math.abs(dy) <= ROOST_VERTICAL_RANGE;
    }

    // ---- roost search coverage ----

    /**
     * Each due search spends 36 candidates on a dense ceiling-first sweep of
     * the near columns (|dx| <= 1, |dz| <= 1, dy +4..+1) so ordinary geometry
     * such as a support one block up and one block over is found immediately,
     * and 12 candidates on one rotating page of the complete anchor envelope
     * so coverage is eventually total. No offset repeats inside one search.
     */
    public static final int ROOST_NEAR_SWEEP_CANDIDATES = 36;
    public static final int ROOST_PAGE_CANDIDATES = 12;

    /** One deterministic candidate offset relative to the anchor. */
    public record RoostOffset(int dx, int dy, int dz) {
    }

    public static int roostEnvelopeSize() {
        final int width = 2 * ROOST_HORIZONTAL_RANGE + 1;
        final int height = 2 * ROOST_VERTICAL_RANGE + 1;
        return width * width * height;
    }

    public static int roostPageCount() {
        return Math.ceilDiv(roostEnvelopeSize(), ROOST_PAGE_CANDIDATES);
    }

    /** Near sweep index 0..35: dy +4 down to +1, then the nine near columns. */
    public static RoostOffset roostNearSweepOffset(final int index) {
        final int dy = 4 - index / 9;
        final int column = index % 9;
        return new RoostOffset(column / 3 - 1, dy, column % 3 - 1);
    }

    public static boolean inRoostNearSweep(final int dx, final int dy, final int dz) {
        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 1 && dy <= 4;
    }

    /** Linear envelope order: dy from +range down, then dx, then dz. */
    public static RoostOffset roostEnvelopeOffset(final int index) {
        final int width = 2 * ROOST_HORIZONTAL_RANGE + 1;
        final int layer = width * width;
        final int dy = ROOST_VERTICAL_RANGE - index / layer;
        final int remainder = index % layer;
        return new RoostOffset(
            remainder / width - ROOST_HORIZONTAL_RANGE,
            dy,
            remainder % width - ROOST_HORIZONTAL_RANGE
        );
    }

    /** Deterministic per-search rotating page: staggered by UUID, wraps fully. */
    public static int roostPageIndex(final long now, final UUID id) {
        final int pages = roostPageCount();
        return (int) Math.floorMod(
            now / ROOST_SEARCH_INTERVAL_TICKS + stableOffset(id, pages), (long) pages
        );
    }

    // ---- movement ----

    public static boolean navigationDue(final long nextNavigationAt, final long now) {
        return nextNavigationAt <= 0L || now >= nextNavigationAt;
    }

    public static boolean scanDue(final long nextScanAt, final long now) {
        return nextScanAt <= 0L || now >= nextScanAt;
    }

    public static int routeFailures(final int failures) {
        return Math.min(MAX_ROUTE_FAILURES, Math.max(0, failures) + 1);
    }

    public static boolean routeBackoffRequired(final int failures) {
        return failures >= MAX_ROUTE_FAILURES;
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return routeBackoffRequired(failures) ? saturatingAdd(now, ROUTE_BACKOFF_TICKS) : 0L;
    }

    // ---- swoop ----

    public record SwoopStartFacts(
        boolean legalVisibleTargetInRange,
        boolean withinEnvelopeOrDirectAttacker,
        boolean noUrgentHazard,
        boolean notWithdrawing,
        boolean cooldownComplete,
        boolean noActiveAction
    ) {
    }

    public static boolean mayBeginSwoop(final SwoopStartFacts facts) {
        return facts.legalVisibleTargetInRange()
            && facts.withinEnvelopeOrDirectAttacker()
            && facts.noUrgentHazard()
            && facts.notWithdrawing()
            && facts.cooldownComplete()
            && facts.noActiveAction();
    }

    public record SwoopCancelFacts(
        boolean targetInvalid,
        boolean lineOfSightOrRangeLost,
        boolean ownerRelationChanged,
        boolean hazardOrWithdrawal,
        boolean unloadedOrDimensionMismatch
    ) {
    }

    public static boolean swoopCancelled(final SwoopCancelFacts facts) {
        return facts.targetInvalid()
            || facts.lineOfSightOrRangeLost()
            || facts.ownerRelationChanged()
            || facts.hazardOrWithdrawal()
            || facts.unloadedOrDimensionMismatch();
    }

    // ---- mode priority ----

    /** Strict mode priority; lower ordinal work may never override higher. */
    public static Mode modePriority(
        final boolean urgentHazard,
        final boolean lowHealth,
        final boolean activeAction,
        final boolean night
    ) {
        if (urgentHazard) return Mode.HAZARD;
        if (lowHealth) return Mode.WITHDRAW;
        if (activeAction) return Mode.INTERCEPT;
        return night ? Mode.SORTIE : Mode.SHELTER;
    }

    public static boolean lowHealth(final float health, final float maxHealth) {
        return maxHealth > 0.0F && health / maxHealth <= WITHDRAW_HEALTH_FRACTION;
    }

    // ---- deterministic helpers ----

    public static int stableOffset(final UUID id, final int modulus) {
        return (int) Math.floorMod(id.getLeastSignificantBits(), (long) modulus);
    }

    public static long clampDeadline(final long deadline, final long now, final long maxHorizonTicks) {
        if (deadline <= 0L) return 0L;
        final long horizon = Math.min(maxHorizonTicks, MAX_FUTURE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(now, horizon));
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return ((base ^ sum) & (addend ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    public static Comparator<UUID> unsignedUuidOrder() {
        return UNSIGNED_UUID_ORDER;
    }
}
