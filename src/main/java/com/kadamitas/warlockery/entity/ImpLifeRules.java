package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ImpLifeRules {
    public static final int COMBAT_DECISION_TICKS = 10;
    public static final int IDLE_DECISION_TICKS = 20;
    public static final int OWNER_RESOLUTION_TICKS = 20;
    public static final int TARGET_DISCOVERY_TICKS = 40;
    public static final int TARGET_DISCOVERY_RADIUS = 24;
    public static final int TARGET_RETENTION = 12;
    public static final int TARGET_MAX_RANGE = 32;
    public static final int THREAT_EXPIRY_TICKS = 200;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final int OWNER_THREAT_FRESHNESS_TICKS = 200;

    public static final int CURIOSITY_INTERVAL_TICKS = 200;
    public static final int CURIOSITY_HORIZONTAL_RADIUS = 8;
    public static final int CURIOSITY_VERTICAL_RADIUS = 4;
    public static final int CURIOSITY_READ_BUDGET = 96;
    public static final int CURIOSITY_BACKOFF_TICKS = 600;
    public static final int MAX_OBSERVATIONS = 4;
    public static final int OBSERVATION_DEDUP_RADIUS = 2;
    public static final int OBSERVATION_EXPIRY_TICKS = 1_200;
    public static final int INSPECT_MIN_TICKS = 40;
    public static final int INSPECT_MAX_TICKS = 80;
    public static final double INSPECT_APPROACH_MAX = 4.0;
    public static final int INSPECT_PARTICLE_CAP = 5;

    public static final int SCOUT_LEGS = 3;
    public static final int SCOUT_OUTBOUND_TICKS = 400;
    public static final int SCOUT_RETURN_TICKS = 200;
    public static final int SCOUT_TOTAL_TICKS = 600;
    public static final int SCOUT_MAX_VERTICAL_OFFSET = 6;
    public static final int SCOUT_TOTAL_READ_BUDGET = 192;
    public static final int SCOUT_OBSERVATION_RADIUS = 12;
    public static final int SCOUT_RETAINED_OBSERVATIONS = 4;
    public static final int SCOUT_LINE_OF_SIGHT_CHECKS = 4;
    public static final double SCOUT_RETURN_COMPLETION_DISTANCE = 4.0;

    public static final double TOO_CLOSE_RANGE = 5.0;
    public static final double PREFERRED_RANGE_MIN = 8.0;
    public static final double PREFERRED_RANGE_MAX = 12.0;
    public static final double TOO_FAR_RANGE = 16.0;
    public static final int LANE_CANDIDATES = 6;
    public static final int LANE_READ_BUDGET = 64;
    public static final int LANE_LINE_OF_SIGHT_CHECKS = 3;
    public static final int LANE_HOLD_TICKS = 40;
    public static final int WINDUP_TICKS = 10;
    public static final int SHOT_INTERVAL_TICKS = 30;
    public static final double CLOSE_ESCAPE_RANGE = 3.0;
    public static final int MELEE_RECOVERY_TICKS = 40;
    public static final float RETREAT_LATCH_FRACTION = 0.25F;
    public static final float RETREAT_RELEASE_FRACTION = 0.45F;

    public static final int WAYPOINT_CANDIDATES = 8;
    public static final int WAYPOINT_READ_BUDGET = 64;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int STUCK_WINDOW_TICKS = 60;
    public static final double STUCK_MIN_PROGRESS = 0.5;

    public static final double FOLLOW_ORBIT_DISTANCE = 8.0;
    public static final double FOLLOW_PATH_DISTANCE = 24.0;
    public static final int FOLLOW_RECOVERY_RADIUS = 4;
    public static final double WATCH_ENVELOPE_MIN = 3.0;
    public static final double WATCH_ENVELOPE_MAX = 6.0;
    public static final int OWNER_AURA_INTERVAL_TICKS = 20;
    public static final int OWNER_AURA_DURATION_TICKS = 60;

    public static final int ORDER_MAX_TICKS = 600;
    private static final int ARCHFIEND_SUBORDINATE_CAP = 4;
    private static final int REGENT_SUBORDINATE_CAP = 7;
    private static final int ORDER_IMP_SLOT_CAP = 2;

    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;

    private ImpLifeRules() {
    }

    public enum Duty {
        FOLLOW, WATCH
    }

    public enum Action {
        NONE, FOLLOW, WATCH, SCOUT_OUT, SCOUT_RETURN, INSPECT, PERCH,
        RANGED_WINDUP, CLOSE_ESCAPE, DISENGAGE, NPC_ORDER, HAZARD_ESCAPE
    }

    public enum ObservationType {
        HOSTILE, HEAT, SHINY
    }

    public enum OrderRank {
        ARCHFIEND, REGENT
    }

    public enum OrderAction {
        SCOUT, REPORT, WATCH, HARASS
    }

    public enum Authority {
        CREATURE_OWNER, SAME_PLAYER_DUAL, INFERNAL_ONLY, CONFLICTED,
        REGENT_ORDER, ARCHFIEND_ORDER, AUTONOMOUS
    }

    public record Observation(
        ObservationType type,
        long packedPosition,
        Optional<UUID> subjectId,
        long firstObservedAt,
        long lastObservedAt,
        int confidence,
        long expiresAt
    ) {
        public Observation {
            confidence = Math.clamp(confidence, 0, 1_000);
        }

        public boolean valid(final long now) {
            return expiresAt > now;
        }
    }

    public record InfernalOrder(
        UUID issuerId,
        OrderRank rank,
        UUID groupId,
        long epoch,
        OrderAction action,
        Optional<UUID> targetId,
        long createdAt,
        long expiresAt
    ) {
        public boolean valid(final long now) {
            return expiresAt > now;
        }
    }

    public record OrderFacts(
        boolean impAlive,
        boolean impLoaded,
        boolean boundToPlayer,
        boolean sameDimension,
        boolean withinIssuerRange,
        boolean delegatedByAnImp,
        boolean higherOrderActive,
        int issuerTotalSubordinates,
        int issuerImpSubordinates
    ) {
    }

    public record TargetFacts(
        boolean alive,
        boolean sameDimension,
        boolean loaded,
        boolean withinRange,
        boolean creativeOrSpectator,
        boolean owner,
        boolean sameAuthorityAlly,
        boolean acceptedGroupAlly,
        boolean protectedIdentity,
        boolean recentDirectAttacker,
        boolean recentOwnerAttacker,
        boolean commandedTarget
    ) {
    }

    public static Authority effectiveAuthority(
        final Optional<UUID> creatureOwner,
        final Optional<UUID> infernalOwner,
        final boolean validRegentOrder,
        final boolean validArchfiendOrder
    ) {
        if (creatureOwner.isPresent() && infernalOwner.isPresent()) {
            return creatureOwner.equals(infernalOwner) ? Authority.SAME_PLAYER_DUAL : Authority.CONFLICTED;
        }
        if (creatureOwner.isPresent()) {
            return Authority.CREATURE_OWNER;
        }
        if (infernalOwner.isPresent()) {
            return Authority.INFERNAL_ONLY;
        }
        if (validRegentOrder) {
            return Authority.REGENT_ORDER;
        }
        if (validArchfiendOrder) {
            return Authority.ARCHFIEND_ORDER;
        }
        return Authority.AUTONOMOUS;
    }

    public static boolean commandAuthorityHolds(final Authority authority) {
        return authority == Authority.CREATURE_OWNER || authority == Authority.SAME_PLAYER_DUAL;
    }

    public static boolean infernalCommandRefused(final Authority authority) {
        return authority == Authority.CONFLICTED;
    }

    public static boolean infernalSacrificeAuthorized(final Authority authority) {
        return authority == Authority.INFERNAL_ONLY || authority == Authority.SAME_PLAYER_DUAL;
    }

    public static Duty defaultDuty() {
        return Duty.FOLLOW;
    }

    public static Duty toggledDuty(final Optional<Duty> current) {
        return current.orElse(defaultDuty()) == Duty.FOLLOW ? Duty.WATCH : Duty.FOLLOW;
    }

    public static boolean mayBeginScout(
        final boolean ownerValid,
        final boolean ownerLoadedSameDimension,
        final boolean anchorValid
    ) {
        return ownerValid && ownerLoadedSameDimension && anchorValid;
    }

    public static int legRadius(final int leg) {
        return switch (Math.clamp(leg, 0, SCOUT_LEGS - 1)) {
            case 0 -> 8;
            case 1 -> 12;
            default -> 16;
        };
    }

    public static List<BlockPos> legWaypoints(final BlockPos anchor, final int leg, final UUID impId) {
        final int radius = legRadius(leg);
        final int rotation = stableOffset(impId, WAYPOINT_CANDIDATES);
        final List<BlockPos> waypoints = new ArrayList<>(WAYPOINT_CANDIDATES);
        for (int index = 0; index < WAYPOINT_CANDIDATES; index++) {
            final double angle = 2.0 * Math.PI * ((index + rotation) % WAYPOINT_CANDIDATES)
                / WAYPOINT_CANDIDATES;
            final int vertical = Math.min(SCOUT_MAX_VERTICAL_OFFSET, (index % 3) * 2);
            waypoints.add(anchor.offset(
                (int) Math.round(Math.cos(angle) * radius),
                vertical,
                (int) Math.round(Math.sin(angle) * radius)
            ));
        }
        return List.copyOf(waypoints);
    }

    public static boolean scoutTimedOut(final long startedAt, final long now) {
        return now - startedAt >= SCOUT_TOTAL_TICKS;
    }

    public static boolean scoutBudgetExhausted(final int chargedReads) {
        return chargedReads >= SCOUT_TOTAL_READ_BUDGET;
    }

    public static boolean mayDeliverReport(
        final boolean alreadyDelivered,
        final boolean ownerAliveSameDimension,
        final boolean withinCompletionDistance
    ) {
        return !alreadyDelivered && ownerAliveSameDimension && withinCompletionDistance;
    }

    public static int reportedHostileCount(final List<Observation> observations, final long now) {
        return (int) observations.stream()
            .filter(row -> row.type() == ObservationType.HOSTILE && row.valid(now))
            .limit(SCOUT_RETAINED_OBSERVATIONS)
            .count();
    }

    public static List<Observation> recordObservation(
        final List<Observation> ledger,
        final Observation incoming,
        final long now
    ) {
        final List<Observation> rows = new ArrayList<>();
        Observation merged = incoming;
        for (final Observation row : ledger) {
            if (!row.valid(now)) {
                continue;
            }
            if (row.type() == incoming.type() && withinDedupRadius(row.packedPosition(), incoming.packedPosition())) {
                merged = new Observation(
                    row.type(),
                    row.packedPosition(),
                    row.subjectId().or(incoming::subjectId),
                    Math.min(row.firstObservedAt(), incoming.firstObservedAt()),
                    Math.max(row.lastObservedAt(), incoming.lastObservedAt()),
                    Math.max(row.confidence(), incoming.confidence()),
                    Math.max(row.expiresAt(), incoming.expiresAt())
                );
                continue;
            }
            rows.add(row);
        }
        rows.add(merged);
        rows.sort(observationRetentionOrder());
        return List.copyOf(rows.subList(0, Math.min(rows.size(), MAX_OBSERVATIONS)));
    }

    public static Comparator<Observation> observationRetentionOrder() {
        return Comparator
            .comparingInt((Observation row) -> row.type().ordinal())
            .thenComparing(Comparator.comparingLong(Observation::lastObservedAt).reversed())
            .thenComparingLong(Observation::packedPosition);
    }

    private static boolean withinDedupRadius(final long first, final long second) {
        final BlockPos a = BlockPos.of(first);
        final BlockPos b = BlockPos.of(second);
        return a.distSqr(b) <= (double) OBSERVATION_DEDUP_RADIUS * OBSERVATION_DEDUP_RADIUS;
    }

    public static int subordinateCap(final OrderRank rank) {
        return rank == OrderRank.ARCHFIEND ? ARCHFIEND_SUBORDINATE_CAP : REGENT_SUBORDINATE_CAP;
    }

    public static int impSlotCap(final OrderRank rank) {
        return ORDER_IMP_SLOT_CAP;
    }

    public static boolean acceptsOrder(final OrderRank rank, final OrderFacts facts) {
        return facts.impAlive()
            && facts.impLoaded()
            && !facts.boundToPlayer()
            && facts.sameDimension()
            && facts.withinIssuerRange()
            && !facts.delegatedByAnImp()
            && !facts.higherOrderActive()
            && facts.issuerTotalSubordinates() < subordinateCap(rank)
            && facts.issuerImpSubordinates() < impSlotCap(rank);
    }

    public static long orderExpiry(final long now, final long requestedExpiry) {
        final long clamped = Math.min(requestedExpiry, saturatingAdd(now, ORDER_MAX_TICKS));
        return Math.max(clamped, now);
    }

    public static boolean orderCleared(
        final boolean issuerMissingOrDead,
        final boolean issuerDimensionChanged,
        final boolean rankOrGroupMismatch,
        final boolean targetInvalid,
        final int routeFailures
    ) {
        return issuerMissingOrDead
            || issuerDimensionChanged
            || rankOrGroupMismatch
            || targetInvalid
            || routeFailures >= MAX_ROUTE_FAILURES;
    }

    public static boolean boundMayEngage(final TargetFacts facts) {
        return baseEligible(facts)
            && (facts.recentDirectAttacker() || facts.recentOwnerAttacker() || facts.commandedTarget());
    }

    public static boolean unboundMayAcquire(final TargetFacts facts, final double distance) {
        return baseEligible(facts)
            && !facts.protectedIdentity()
            && distance <= TARGET_DISCOVERY_RADIUS;
    }

    private static boolean baseEligible(final TargetFacts facts) {
        return facts.alive()
            && facts.sameDimension()
            && facts.loaded()
            && facts.withinRange()
            && !facts.creativeOrSpectator()
            && !facts.owner()
            && !facts.sameAuthorityAlly()
            && !facts.acceptedGroupAlly();
    }

    public static boolean attributionFresh(final long attributedAt, final long now) {
        return now - attributedAt <= ATTRIBUTION_FRESHNESS_TICKS;
    }

    public static boolean ownerThreatFresh(final long attributedAt, final long now) {
        return now - attributedAt <= OWNER_THREAT_FRESHNESS_TICKS;
    }

    public static boolean tooClose(final double distance) {
        return distance < TOO_CLOSE_RANGE;
    }

    public static boolean withinPreferredBand(final double distance) {
        return distance >= PREFERRED_RANGE_MIN && distance <= PREFERRED_RANGE_MAX;
    }

    public static boolean tooFar(final double distance) {
        return distance > TOO_FAR_RANGE;
    }

    public static boolean windupComplete(final long startedAt, final long now) {
        return startedAt > 0L && now - startedAt >= WINDUP_TICKS;
    }

    public static boolean shotReady(final long lastShotAt, final long now) {
        return lastShotAt <= 0L || now - lastShotAt >= SHOT_INTERVAL_TICKS;
    }

    public static boolean mayCloseEscape(
        final double distance,
        final boolean failedDisengage,
        final boolean hazardActive,
        final long meleeRecoveryUntil,
        final long now
    ) {
        return distance <= CLOSE_ESCAPE_RANGE
            && failedDisengage
            && !hazardActive
            && meleeRecoveryUntil <= now;
    }

    public static boolean retreatLatches(final float healthFraction, final int routeFailures) {
        return healthFraction <= RETREAT_LATCH_FRACTION || routeFailures >= MAX_ROUTE_FAILURES;
    }

    public static boolean retreatReleases(
        final float healthFraction,
        final boolean targetValid,
        final boolean safeLane
    ) {
        return healthFraction >= RETREAT_RELEASE_FRACTION && targetValid && safeLane;
    }

    public static int nextRouteFailures(final int failures) {
        return Math.min(MAX_ROUTE_FAILURES, Math.max(0, failures) + 1);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? saturatingAdd(now, ROUTE_BACKOFF_TICKS) : 0L;
    }

    public static boolean curiosityAllowed(
        final boolean hazardActive,
        final boolean combatEvidence,
        final boolean dutyMovementActive,
        final boolean orderActive,
        final long backoffUntil,
        final long nextCuriosityAt,
        final long now
    ) {
        return !hazardActive
            && !combatEvidence
            && !dutyMovementActive
            && !orderActive
            && backoffUntil <= now
            && due(nextCuriosityAt, now);
    }

    public static boolean due(final long sentinel, final long now) {
        return sentinel <= 0L || now >= sentinel;
    }

    public static int stableOffset(final UUID id, final int modulus) {
        return (int) Math.floorMod(id.getLeastSignificantBits(), (long) modulus);
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return ((base ^ sum) & (addend ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    public static long clampDeadline(final long deadline, final long now, final long maxHorizonTicks) {
        if (deadline <= 0L) {
            return 0L;
        }
        final long horizon = Math.min(maxHorizonTicks, MAX_FUTURE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(now, horizon));
    }

    public static int decisionInterval(final boolean combat) {
        return combat ? COMBAT_DECISION_TICKS : IDLE_DECISION_TICKS;
    }

    public static boolean ignoresWhileBound(
        final boolean bound,
        final boolean fire,
        final boolean fall,
        final boolean drowning,
        final boolean unattributedEnvironmental
    ) {
        return bound && (fire || fall || drowning || unattributedEnvironmental);
    }

    public static boolean familiarDamageTransfers() {
        return false;
    }

    public static long nextEpoch(final long epoch) {
        return saturatingAdd(Math.max(0L, epoch), 1L);
    }

    public static boolean actionResumableAfterLoad(final Action action) {
        return switch (action) {
            case NONE, FOLLOW, WATCH, SCOUT_OUT, SCOUT_RETURN -> true;
            case INSPECT, PERCH, RANGED_WINDUP, CLOSE_ESCAPE, DISENGAGE, NPC_ORDER, HAZARD_ESCAPE -> false;
        };
    }

    public static boolean validWorldPosition(final BlockPos position) {
        return Math.abs(position.getX()) <= 30_000_000
            && Math.abs(position.getZ()) <= 30_000_000
            && position.getY() >= -2_032
            && position.getY() <= 2_031;
    }
}
