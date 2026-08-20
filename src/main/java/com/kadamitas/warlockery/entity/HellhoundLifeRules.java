package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loader-neutral F09 Hellhound constants and pure decisions: authority, eligibility, evidence,
 * warning, territory, explicit pack membership and roles, scent, heat, bite, retreat/regroup,
 * route failure, cadence, budgets, deterministic ordering, and cleanup.
 */
public final class HellhoundLifeRules {
    public static final int DECISION_INTERVAL_TICKS = 10;
    public static final int OWNER_REFRESH_INTERVAL_TICKS = 20;
    public static final int EVIDENCE_SCAN_INTERVAL_TICKS = 20;
    public static final int PACK_REFRESH_INTERVAL_TICKS = 40;
    public static final int PACK_CALL_INTERVAL_TICKS = 40;
    public static final int PATROL_SEARCH_INTERVAL_TICKS = 100;
    public static final int HEAT_SEARCH_INTERVAL_TICKS = 200;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int EVENT_FEEDBACK_INTERVAL_TICKS = 40;
    public static final int AMBIENT_FEEDBACK_INTERVAL_TICKS = 200;
    public static final int EVIDENCE_SCAN_RADIUS = 16;
    public static final int SCENT_RADIUS = 16;
    public static final int MAX_RETAINED_CANDIDATES = 8;
    public static final int MAX_EVIDENCE_RECORDS = 4;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final int SNIFF_SEARCH_TICKS = 60;
    public static final int PATROL_RADIUS = 12;
    public static final int WARNING_TRIGGER_RADIUS = 12;
    public static final int WARNING_GRACE_TICKS = 20;
    public static final int WARNING_COMMIT_RADIUS = 14;
    public static final int TERRITORY_PURSUIT_LEASH = 24;
    public static final int SELF_DEFENSE_LEASH = 32;
    public static final int SELF_DEFENSE_LEASH_TICKS = 200;
    public static final int MAX_PATROL_POINTS = 4;
    public static final int PATROL_DWELL_MIN_TICKS = 20;
    public static final int PATROL_DWELL_MAX_TICKS = 60;
    public static final int PACK_REFRESH_RADIUS = 20;
    public static final int PACK_CALL_RADIUS = 20;
    public static final int MAX_PACK_MEMBERS = 4;
    public static final int NATURAL_GROUP_MIN = 1;
    public static final int NATURAL_GROUP_MAX = 3;
    public static final int SECTOR_MIN_RADIUS = 3;
    public static final int SECTOR_MAX_RADIUS = 5;
    public static final int SECTOR_SETUP_TICKS = 40;
    public static final int HEAT_RADIUS = 8;
    public static final int HEAT_MAX_BLOCK_READS = 128;
    public static final int HEAT_POINT_TICKS = 400;
    public static final int BITE_WINDUP_TICKS = 8;
    public static final double BITE_COMMIT_RANGE = 2.4D;
    public static final int BITE_RECOVERY_TICKS = 20;
    public static final float RETREAT_LATCH_HEALTH_FRACTION = 0.25F;
    public static final float RETREAT_RELEASE_HEALTH_FRACTION = 0.40F;
    public static final float ISOLATION_HEALTH_FRACTION = 0.60F;
    public static final int REGROUP_MAX_TICKS = 100;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;
    public static final long LOAD_DEADLINE_CLAMP_TICKS = 600L;
    public static final int STRESS_POPULATION = 32;
    public static final int OWNER_PERIMETER_NEAR = 4;
    public static final int OWNER_PERIMETER_FAR = 8;
    public static final int OWNER_FOLLOW_DISTANCE = 10;
    public static final int OWNER_MAX_FOLLOW_DISTANCE = 32;

    private HellhoundLifeRules() {
    }

    /** Semantic mode of one Hellhound. */
    public enum Mode {
        WILD,
        ANIMUS_BOUND
    }

    /** How the durable pack identity was established. */
    public enum PackOrigin {
        NATURAL_GROUP,
        SOLITARY,
        LEGACY_SOLITARY
    }

    /** The sixteen approved semantic intents. */
    public enum Intent {
        IDLE(true),
        PATROL(true),
        HEAT_REST(true),
        WARN(false),
        SNIFF(false),
        STALK(false),
        PACK_SETUP(false),
        PRESS(false),
        BITE_WINDUP(false),
        REPOSITION(false),
        RETREAT(true),
        REGROUP(true),
        RETURN(true),
        OWNER_FOLLOW(true),
        OWNER_GUARD(true),
        HAZARD_ESCAPE(false);

        private final boolean resumesFromDisk;

        Intent(final boolean resumesFromDisk) {
            this.resumesFromDisk = resumesFromDisk;
        }

        public boolean resumesFromDisk() {
            return resumesFromDisk;
        }
    }

    /** Temporary per-epoch pack behavior preference; never a rigid slot or an alpha. */
    public enum PackRole {
        PRESSURE,
        LEFT,
        RIGHT,
        CUTOFF
    }

    /** Classified navigation failure classes. */
    public enum RouteFailure {
        INVALID_TARGET,
        NO_ROUTE,
        STUCK,
        WORLD_CHANGED,
        UNLOADED,
        BUDGET_EXHAUSTED
    }

    /** The six approved observation kinds. */
    public enum EvidenceKind {
        DIRECT_ATTACK,
        OWNER_THREAT,
        TERRITORY_INTRUSION,
        SIGHT,
        PACK_CALL,
        SCENT
    }

    /** One bounded durable observation with a last-known position, never a live location. */
    public record Evidence(
        EvidenceKind kind,
        Optional<UUID> sourceId,
        Optional<String> dimension,
        Optional<Long> packedPosition,
        long observedAt,
        long expiresAt,
        int confidence
    ) {
        public Evidence {
            confidence = Math.clamp(confidence, 0, 100);
        }

        public boolean valid(final long now) {
            return now < expiresAt;
        }
    }

    public static long evidenceLifetimeTicks(final EvidenceKind kind) {
        return switch (kind) {
            case DIRECT_ATTACK, OWNER_THREAT, TERRITORY_INTRUSION -> 200L;
            case SIGHT, PACK_CALL -> 100L;
            case SCENT -> 60L;
        };
    }

    public static int initialConfidence(final EvidenceKind kind) {
        return switch (kind) {
            case DIRECT_ATTACK -> 100;
            case OWNER_THREAT -> 95;
            case TERRITORY_INTRUSION -> 85;
            case SIGHT -> 70;
            case PACK_CALL -> 55;
            case SCENT -> 40;
        };
    }

    /** Lower value means the record outranks weaker generic senses. */
    public static int evidencePriority(final EvidenceKind kind) {
        return kind.ordinal();
    }

    public static Evidence createEvidence(
        final EvidenceKind kind,
        final Optional<UUID> sourceId,
        final Optional<String> dimension,
        final Optional<Long> packedPosition,
        final long now
    ) {
        return new Evidence(
            kind,
            sourceId,
            dimension,
            packedPosition,
            now,
            saturatingAdd(now, evidenceLifetimeTicks(kind)),
            initialConfidence(kind)
        );
    }

    /** Records one observation: refresh replaces the same kind/source record, then truncates. */
    public static List<Evidence> recordEvidence(
        final List<Evidence> ledger,
        final Evidence observation,
        final long now
    ) {
        final List<Evidence> merged = new ArrayList<>(pruneExpired(ledger, now));
        merged.removeIf(entry -> entry.kind() == observation.kind()
            && entry.sourceId().equals(observation.sourceId()));
        merged.add(observation);
        return truncate(merged, now);
    }

    /** Deterministic truncation by priority, freshness, then source UUID and position. */
    public static List<Evidence> truncate(final List<Evidence> ledger, final long now) {
        return pruneExpired(ledger, now).stream()
            .sorted(evidenceOrder())
            .limit(MAX_EVIDENCE_RECORDS)
            .toList();
    }

    public static List<Evidence> pruneExpired(final List<Evidence> ledger, final long now) {
        return ledger.stream().filter(entry -> entry.valid(now)).toList();
    }

    public static Comparator<Evidence> evidenceOrder() {
        return Comparator.comparingInt((Evidence entry) -> evidencePriority(entry.kind()))
            .thenComparing(Comparator.comparingLong(Evidence::observedAt).reversed())
            .thenComparing(entry -> entry.sourceId().map(UUID::toString).orElse(""))
            .thenComparingLong(entry -> entry.packedPosition().orElse(Long.MIN_VALUE));
    }

    /** One-hop copy: never stronger, never longer than the source, at most 100 ticks. */
    public static Evidence packCallCopy(final Evidence source, final long now) {
        return new Evidence(
            EvidenceKind.PACK_CALL,
            source.sourceId(),
            source.dimension(),
            source.packedPosition(),
            now,
            Math.min(source.expiresAt(), saturatingAdd(now, evidenceLifetimeTicks(EvidenceKind.PACK_CALL))),
            Math.min(source.confidence(), initialConfidence(EvidenceKind.PACK_CALL))
        );
    }

    public static boolean shareableWithPack(final EvidenceKind kind) {
        return kind != EvidenceKind.PACK_CALL;
    }

    public static List<PackRole> rolesForSize(final int size) {
        return switch (Math.clamp(size, 1, MAX_PACK_MEMBERS)) {
            case 1 -> List.of(PackRole.PRESSURE);
            case 2 -> List.of(PackRole.PRESSURE, PackRole.CUTOFF);
            case 3 -> List.of(PackRole.PRESSURE, PackRole.LEFT, PackRole.RIGHT);
            default -> List.of(PackRole.PRESSURE, PackRole.LEFT, PackRole.RIGHT, PackRole.CUTOFF);
        };
    }

    /** Roles derive from stable unsigned UUID order without any alpha or encounter-order bias. */
    public static Map<UUID, PackRole> deriveRoles(final List<UUID> loadedMembers) {
        final List<UUID> ordered = loadedMembers.stream()
            .distinct()
            .sorted(unsignedUuidOrder())
            .limit(MAX_PACK_MEMBERS)
            .toList();
        final List<PackRole> roles = rolesForSize(ordered.size());
        final Map<UUID, PackRole> derived = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            derived.put(ordered.get(index), roles.get(index));
        }
        return derived;
    }

    /** Every protected class refuses eligibility before any scoring. */
    public record TargetFacts(
        boolean alive,
        boolean loaded,
        boolean sameDimension,
        boolean owner,
        boolean ownerAlly,
        boolean scoreboardAlly,
        boolean samePack,
        boolean creative,
        boolean spectator,
        boolean invulnerable,
        boolean protectedProgressionParticipant
    ) {
    }

    public static boolean eligibleTarget(final TargetFacts facts) {
        return facts.alive()
            && facts.loaded()
            && facts.sameDimension()
            && !facts.owner()
            && !facts.ownerAlly()
            && !facts.scoreboardAlly()
            && !facts.samePack()
            && !facts.creative()
            && !facts.spectator()
            && !facts.invulnerable()
            && !facts.protectedProgressionParticipant();
    }

    public static boolean warningTriggered(
        final boolean wild,
        final boolean eligible,
        final double distanceSquared,
        final boolean hazard,
        final boolean retreat,
        final boolean combat
    ) {
        return wild
            && eligible
            && distanceSquared <= (double) WARNING_TRIGGER_RADIUS * WARNING_TRIGGER_RADIUS
            && !hazard
            && !retreat
            && !combat;
    }

    public static boolean warningGraceElapsed(final long warnedAt, final long now) {
        return now - warnedAt >= WARNING_GRACE_TICKS;
    }

    public static boolean warningCommits(
        final boolean alive,
        final boolean eligible,
        final boolean loaded,
        final boolean sameDimension,
        final double anchorDistanceSquared
    ) {
        return alive
            && eligible
            && loaded
            && sameDimension
            && anchorDistanceSquared <= (double) WARNING_COMMIT_RADIUS * WARNING_COMMIT_RADIUS;
    }

    public static int pursuitLeash(final boolean selfDefense) {
        return selfDefense ? SELF_DEFENSE_LEASH : TERRITORY_PURSUIT_LEASH;
    }

    public static boolean leashExceeded(final double distanceSquared, final int leash) {
        return distanceSquared > (double) leash * leash;
    }

    public static boolean retreatLatches(
        final float healthFraction,
        final int routeFailures,
        final boolean committedWithPackSupport,
        final boolean isolated
    ) {
        return healthFraction <= RETREAT_LATCH_HEALTH_FRACTION
            || routeFailures >= MAX_ROUTE_FAILURES
            || committedWithPackSupport && isolated && healthFraction <= ISOLATION_HEALTH_FRACTION;
    }

    public static boolean retreatReleases(
        final float healthFraction,
        final boolean directAttackerWithinThreeBlocks,
        final boolean immediateOwnerDefense
    ) {
        return healthFraction >= RETREAT_RELEASE_HEALTH_FRACTION
            || directAttackerWithinThreeBlocks
            || immediateOwnerDefense;
    }

    /** Every bite fact is revalidated at commit time. */
    public record BiteFacts(
        boolean targetValid,
        boolean withinRange,
        boolean lineOfSight,
        boolean windupComplete,
        boolean recoveryElapsed
    ) {
    }

    public static boolean mayCommitBite(final BiteFacts facts) {
        return facts.targetValid()
            && facts.withinRange()
            && facts.lineOfSight()
            && facts.windupComplete()
            && facts.recoveryElapsed();
    }

    public static boolean withinCommitRange(final double distanceSquared) {
        return distanceSquared <= BITE_COMMIT_RANGE * BITE_COMMIT_RANGE;
    }

    public static int nextRouteFailures(final int current) {
        return Math.clamp(current + 1, 0, MAX_ROUTE_FAILURES);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? saturatingAdd(now, ROUTE_BACKOFF_TICKS) : 0L;
    }

    /** Zero and past sentinels always read as due; no timer stores {@code Long.MAX_VALUE}. */
    public static boolean due(final long at, final long now) {
        return at <= now;
    }

    public static long clampDeadline(final long deadline, final long now, final long maxTicks) {
        if (deadline <= 0L) {
            return 0L;
        }
        final long horizon = Math.min(maxTicks, MAX_FUTURE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(now, horizon));
    }

    /** Loaded deadlines additionally clamp to six hundred ticks beyond current game time. */
    public static long clampLoadedDeadline(final long deadline, final long now, final long maxTicks) {
        return clampDeadline(deadline, now, Math.min(maxTicks, LOAD_DEADLINE_CLAMP_TICKS));
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return addend > 0L && sum < base ? Long.MAX_VALUE : sum;
    }

    public static int dwellTicks(final long seed) {
        final long mixed = seed * 0x9E3779B97F4A7C15L;
        final int span = PATROL_DWELL_MAX_TICKS - PATROL_DWELL_MIN_TICKS + 1;
        return PATROL_DWELL_MIN_TICKS + (int) Long.remainderUnsigned(mixed >>> 17, span);
    }

    public static int stableOffset(final UUID id, final int modulus) {
        return Math.floorMod(id.hashCode(), Math.max(1, modulus));
    }

    public static Comparator<UUID> unsignedUuidOrder() {
        return Comparator.comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
            .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);
    }

    public static boolean followOwner(final double distance) {
        return distance > OWNER_FOLLOW_DISTANCE;
    }

    public static boolean ownerPerimeterWatch(final double distance) {
        return distance >= OWNER_PERIMETER_NEAR && distance <= OWNER_PERIMETER_FAR;
    }

    /**
     * Sniffing is only for a lost refresh: a last-known record whose source can no longer be
     * observed, within one bounded sixty-tick window of its final observation. A currently
     * observable source is handled by the warning ladder and engagement instead, so a fresh
     * SIGHT record can never shadow the WARN rung.
     */
    public static boolean sniffAtLastKnown(
        final boolean sourceCurrentlyObservable,
        final long observedAt,
        final long now
    ) {
        return !sourceCurrentlyObservable && now - observedAt <= SNIFF_SEARCH_TICKS;
    }

    /**
     * Only attributed records authorize combat engagement. SIGHT and SCENT feed the warning,
     * sniff, and stalk ladder; combat requires a direct attack, an owner threat, a committed
     * territory intrusion, or a one-hop pack copy of such a record.
     */
    public static boolean engageableEvidence(final EvidenceKind kind) {
        return switch (kind) {
            case DIRECT_ATTACK, OWNER_THREAT, TERRITORY_INTRUSION, PACK_CALL -> true;
            case SIGHT, SCENT -> false;
        };
    }

    public static boolean mayBroadcastPackCall(final long nextAllowedAt, final long now) {
        return due(nextAllowedAt, now);
    }
}
