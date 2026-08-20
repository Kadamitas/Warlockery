package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WerewolfHunterRules {
    public static final int MAX_EVIDENCE_RECORDS = 4;
    public static final long EVENT_QUARRY_TICKS = 6_000L;
    public static final long DIRECT_ATTACK_TICKS = 600L;
    public static final long WITNESSED_ATTACK_TICKS = 400L;
    public static final long LAST_KNOWN_TICKS = 400L;
    public static final int WITNESS_RADIUS = 24;
    public static final int WITNESS_FRESHNESS_TICKS = 40;

    public static final int DEFAULT_SILVER_BOLTS = 24;
    public static final int MAX_SILVER_BOLTS = 32;
    public static final int LOW_RESERVE_BOLTS = 6;

    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int OBSERVATION_INTERVAL_TICKS = 40;
    public static final int SCHEDULE_INTERVAL_TICKS = 100;
    public static final int FEEDBACK_INTERVAL_TICKS = 200;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;

    public static final int WARN_MINIMUM_TICKS = 20;
    public static final int ENGAGE_TICKS = 400;
    public static final int LOST_SIGHT_TICKS = 100;
    public static final int SEARCH_TICKS = 400;
    public static final int SEARCH_RADIUS = 12;
    public static final int MAX_SEARCH_WAYPOINTS = 4;
    public static final int RETREAT_TICKS = 120;
    public static final float RETREAT_HEALTH_FRACTION = 0.30F;

    public static final int PREFERRED_RANGE_MIN = 10;
    public static final int PREFERRED_RANGE_MAX = 14;
    public static final int LANE_HORIZONTAL_RADIUS = 8;
    public static final int LANE_VERTICAL_RADIUS = 4;
    public static final int MAX_LANE_BLOCK_READS = 128;
    public static final int MAX_SPAWN_BLOCK_READS = 256;

    public static final int OBSERVATION_RADIUS = 24;
    public static final int MAX_RETAINED_CANDIDATES = 16;
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 4;

    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final int MAX_HUNT_RECORDS = 8;
    public static final int HUNT_DEDUP_RADIUS = 128;
    public static final long HUNT_RECORD_TICKS = 6_000L;
    public static final int HUNT_CLEANUP_INTERVAL_TICKS = 200;
    public static final int HUNT_PARTICIPANT_CONSTRUCTIONS = 2;

    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
        .comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
        .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private WerewolfHunterRules() {
    }

    public enum EvidenceType {
        EVENT_QUARRY, DIRECT_ATTACK, WITNESSED_ATTACK, LAST_KNOWN
    }

    public enum Confidence {
        CLUE, PROBABLE, CONFIRMED
    }

    public enum Intent {
        IDLE, PATROL, INVESTIGATE, WARN, ENGAGE, REPOSITION, RETREAT, RESUPPLY, RETURN
    }

    public enum HuntStage {
        RESERVED, PREPARING, ACTIVE, CLEANUP
    }

    public enum HuntFailure {
        NONE, CONSTRUCTION_FAILED, PARTICIPANT_MISSING, EXPIRED, DIMENSION_MISMATCH, PEACEFUL
    }

    public record Evidence(
        EvidenceType type,
        Confidence confidence,
        Optional<UUID> sourceId,
        Optional<UUID> targetId,
        Optional<Long> packedPosition,
        Optional<String> dimension,
        long observedAt,
        long expiresAt,
        boolean consumed
    ) {
        public Evidence {
            if (packedPosition.isPresent() && dimension.isEmpty()) {
                packedPosition = Optional.empty();
            }
        }

        public UUID stableKey() {
            return targetId.or(this::sourceId)
                .orElseGet(() -> new UUID(observedAt, type.ordinal()));
        }

        public boolean valid(final long now) {
            return !consumed && expiresAt > now;
        }
    }

    public static long evidenceLifetimeTicks(final EvidenceType type) {
        return switch (type) {
            case EVENT_QUARRY -> EVENT_QUARRY_TICKS;
            case DIRECT_ATTACK -> DIRECT_ATTACK_TICKS;
            case WITNESSED_ATTACK -> WITNESSED_ATTACK_TICKS;
            case LAST_KNOWN -> LAST_KNOWN_TICKS;
        };
    }

    public static Confidence initialConfidence(final EvidenceType type) {
        return type == EvidenceType.LAST_KNOWN ? Confidence.PROBABLE : Confidence.CONFIRMED;
    }

    public static Evidence createEvidence(
        final EvidenceType type,
        final Optional<UUID> sourceId,
        final Optional<UUID> targetId,
        final long now
    ) {
        return createEvidence(type, sourceId, targetId, Optional.empty(), Optional.empty(), now);
    }

    public static Evidence createEvidence(
        final EvidenceType type,
        final Optional<UUID> sourceId,
        final Optional<UUID> targetId,
        final Optional<Long> packedPosition,
        final Optional<String> dimension,
        final long now
    ) {
        return new Evidence(
            type, initialConfidence(type), sourceId, targetId, packedPosition, dimension,
            now, saturatingAdd(now, evidenceLifetimeTicks(type)), false
        );
    }

    public static List<Evidence> recordEvidence(
        final List<Evidence> ledger,
        final Evidence incoming,
        final long now
    ) {
        final List<Evidence> entries = new ArrayList<>(ledger);
        entries.removeIf(entry -> entry.type() == incoming.type()
            && entry.stableKey().equals(incoming.stableKey()));
        if (entries.size() >= MAX_EVIDENCE_RECORDS) {
            final Evidence weakest = entries.stream()
                .min(evictionOrder(now))
                .orElseThrow();
            final boolean weakestValid = weakest.valid(now);
            if (weakestValid && incoming.confidence().ordinal() < weakest.confidence().ordinal()) {
                return List.copyOf(entries);
            }
            entries.remove(weakest);
        }
        entries.add(incoming);
        return List.copyOf(entries);
    }

    private static Comparator<Evidence> evictionOrder(final long now) {
        return Comparator
            .comparing((Evidence entry) -> entry.valid(now))
            .thenComparingInt(entry -> entry.confidence().ordinal())
            .thenComparingLong(Evidence::observedAt)
            .thenComparing(Comparator.comparing(Evidence::stableKey, UNSIGNED_UUID_ORDER).reversed());
    }

    public static List<Evidence> pruneEvidence(final List<Evidence> ledger, final long now) {
        return ledger.stream().filter(entry -> entry.valid(now)).toList();
    }

    public record QuarryCandidate(UUID id, double distanceSqr) {
    }

    public static Optional<UUID> selectQuarry(
        final Optional<UUID> liveEventQuarry,
        final Optional<UUID> validDirectAttacker,
        final Optional<UUID> stableCurrentQuarry,
        final List<QuarryCandidate> confirmedWitnessedAttackers
    ) {
        if (liveEventQuarry.isPresent()) return liveEventQuarry;
        if (validDirectAttacker.isPresent()) return validDirectAttacker;
        if (stableCurrentQuarry.isPresent()) return stableCurrentQuarry;
        return confirmedWitnessedAttackers.stream()
            .min(Comparator.comparingDouble(QuarryCandidate::distanceSqr)
                .thenComparing(QuarryCandidate::id, UNSIGNED_UUID_ORDER))
            .map(QuarryCandidate::id);
    }

    public record TargetFacts(
        boolean liveEventQuarry,
        boolean validDirectAttacker,
        boolean witnessedActiveAttacker,
        boolean engineOrTeamProtected
    ) {
    }

    public static boolean identityAloneQualifies() {
        return false;
    }

    public static boolean eligibleQuarry(final TargetFacts facts) {
        return (facts.liveEventQuarry() || facts.validDirectAttacker() || facts.witnessedActiveAttacker())
            && !facts.engineOrTeamProtected();
    }

    public record ProtectedFacts(
        boolean villager,
        boolean golem,
        boolean sameKindHunter,
        boolean nonAggressivePlayer,
        boolean completeHunterArmorPlayer
    ) {
    }

    public static boolean protectedCorridorActor(final ProtectedFacts facts) {
        return facts.villager() || facts.golem() || facts.sameKindHunter()
            || facts.nonAggressivePlayer() || facts.completeHunterArmorPlayer();
    }

    public static boolean crossfireCancelsShot(final int protectedActorsInCorridor) {
        return protectedActorsInCorridor > 0;
    }

    public static boolean armorGrantsCommandAuthority() {
        return false;
    }

    public static boolean mayCommitRanged(final int silverBolts) {
        return silverBolts > 0;
    }

    public static boolean lowReserve(final int silverBolts) {
        return silverBolts <= LOW_RESERVE_BOLTS;
    }

    public static int acceptedResupply(final int offered, final int reserve) {
        if (offered <= 0 || reserve >= MAX_SILVER_BOLTS) return 0;
        return Math.min(offered, MAX_SILVER_BOLTS - Math.max(0, reserve));
    }

    public static boolean resupplyRefused(
        final boolean charging,
        final boolean engaging,
        final boolean retreatingFromDonor,
        final boolean directEvidenceAgainstDonor
    ) {
        return charging || engaging || retreatingFromDonor || directEvidenceAgainstDonor;
    }

    public record ShotFacts(
        boolean crossbowHeld,
        boolean compatibleSilverBolt,
        boolean quarryLivingAndLoaded,
        boolean sameDimension,
        boolean authorizedByEvidence,
        boolean lineOfSight,
        boolean corridorClear,
        boolean withinRange,
        boolean generationMatches
    ) {
    }

    public static boolean mayFire(final ShotFacts facts) {
        return facts.crossbowHeld()
            && facts.compatibleSilverBolt()
            && facts.quarryLivingAndLoaded()
            && facts.sameDimension()
            && facts.authorizedByEvidence()
            && facts.lineOfSight()
            && facts.corridorClear()
            && facts.withinRange()
            && facts.generationMatches();
    }

    public static boolean withinPreferredRange(final double distance) {
        return distance >= PREFERRED_RANGE_MIN && distance <= PREFERRED_RANGE_MAX;
    }

    public record LaneCandidate(
        boolean clearAttributedLine,
        boolean withinDistanceBand,
        int coverScore,
        int routeCostClass,
        double distanceFromProtectedSqr,
        long stablePositionOrder
    ) {
    }

    public static Comparator<LaneCandidate> laneOrder() {
        return Comparator
            .comparing((LaneCandidate lane) -> !lane.clearAttributedLine())
            .thenComparing(lane -> !lane.withinDistanceBand())
            .thenComparing(Comparator.comparingInt(LaneCandidate::coverScore).reversed())
            .thenComparingInt(LaneCandidate::routeCostClass)
            .thenComparing(Comparator.comparingDouble(LaneCandidate::distanceFromProtectedSqr).reversed())
            .thenComparingLong(LaneCandidate::stablePositionOrder);
    }

    public static Intent scheduleIntent(
        final long dayTime,
        final boolean confirmedQuarry,
        final boolean liveEventRecord,
        final boolean lowAmmo,
        final boolean lowHealth,
        final boolean anchorValid
    ) {
        if (confirmedQuarry) return Intent.WARN;
        if (lowHealth) return Intent.RETREAT;
        if (lowAmmo) return anchorValid ? Intent.RESUPPLY : Intent.IDLE;
        if (liveEventRecord) return Intent.INVESTIGATE;
        return anchorValid ? Intent.PATROL : Intent.IDLE;
    }

    public static boolean retreatRequired(
        final float healthFraction,
        final int silverBolts,
        final int routeFailures,
        final boolean crossfireUnclearable,
        final boolean evidenceExpired,
        final boolean higherMovementAuthority
    ) {
        return healthFraction <= RETREAT_HEALTH_FRACTION
            || silverBolts <= 0
            || routeFailures >= MAX_ROUTE_FAILURES
            || crossfireUnclearable
            || evidenceExpired
            || higherMovementAuthority;
    }

    public static int routeFailures(final int failures) {
        return Math.min(MAX_ROUTE_FAILURES, failures + 1);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? saturatingAdd(now, ROUTE_BACKOFF_TICKS) : 0L;
    }

    public static boolean navigationDue(final long nextNavigationAt, final long now) {
        return nextNavigationAt <= 0L || now >= nextNavigationAt;
    }

    public static boolean decisionDue(final long nextDecisionAt, final long now) {
        return nextDecisionAt <= 0L || now >= nextDecisionAt;
    }

    public static boolean warningDue(final long lastWarnAt, final long now) {
        return lastWarnAt <= 0L || now - lastWarnAt >= FEEDBACK_INTERVAL_TICKS;
    }

    public static boolean warnWaitElapsed(final long warnedAt, final long now, final boolean underDirectCloseAttack) {
        return underDirectCloseAttack || (warnedAt > 0L && now - warnedAt >= WARN_MINIMUM_TICKS);
    }

    public static long clampDeadline(final long deadline, final long now, final long maxHorizonTicks) {
        if (deadline <= 0L) return 0L;
        final long horizon = Math.min(maxHorizonTicks, MAX_FUTURE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(now, horizon));
    }

    public static boolean mayReserveHunt(final int recordCount, final boolean localAreaOccupied) {
        return recordCount < MAX_HUNT_RECORDS && !localAreaOccupied;
    }

    public static boolean withinDedupRadius(final double distanceSqr) {
        return distanceSqr <= (double) HUNT_DEDUP_RADIUS * HUNT_DEDUP_RADIUS;
    }

    public static boolean huntRecordExpired(final long expiresAt, final long now) {
        return expiresAt <= now;
    }

    public static boolean cleanupDue(final long lastCleanupAt, final long now) {
        return lastCleanupAt <= 0L || now - lastCleanupAt >= HUNT_CLEANUP_INTERVAL_TICKS;
    }

    public static boolean stageAllowsActivation(final HuntStage stage) {
        return stage == HuntStage.RESERVED || stage == HuntStage.PREPARING;
    }

    public static int stableOffset(final UUID id, final int modulus) {
        return (int) Math.floorMod(id.getLeastSignificantBits(), (long) modulus);
    }

    public static Comparator<UUID> unsignedUuidOrder() {
        return UNSIGNED_UUID_ORDER;
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return ((base ^ sum) & (addend ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }
}
