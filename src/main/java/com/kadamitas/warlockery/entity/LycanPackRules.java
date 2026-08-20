package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LycanPackRules {
    public static final int MAX_HUNGER = 1_000;
    public static final int DEFAULT_WEREWOLF_HUNGER = 300;
    public static final int DEFAULT_FERAL_HUNGER = 350;
    public static final int DEFAULT_FEAR = 0;
    public static final int HUNGER_RISE_INTERVAL_TICKS = 40;
    public static final int FEAR_DECAY_INTERVAL_TICKS = 20;
    public static final long MAX_ELAPSED_RECONCILE_TICKS = 24_000L;
    public static final int WEREWOLF_KILL_REDUCTION = 450;
    public static final int FERAL_KILL_REDUCTION = 500;
    public static final int CARRION_REDUCTION = 250;
    public static final int WATCH_HUNGER = 500;
    public static final int HUNT_HUNGER = 700;
    public static final int RECOVERY_HUNGER = 250;
    public static final int STALK_HUNGER = 650;
    public static final int ORDINARY_DAMAGE_FEAR = 120;
    public static final int SILVER_OR_GUARD_FEAR = 300;
    public static final int HUNTER_SIGHT_FEAR = 80;
    public static final int SIGHT_FEAR_INTERVAL_TICKS = 100;
    public static final int RETREAT_FEAR = 650;
    public static final int PANIC_FEAR = 850;
    public static final float RETREAT_HEALTH_FRACTION = 0.35F;
    public static final float HUNT_ABORT_HEALTH_FRACTION = 0.20F;
    public static final long DUSK_START = 12_000L;
    public static final long NIGHT_START = 13_000L;
    public static final long NIGHT_END = 23_000L;
    public static final int MOON_SAMPLE_INTERVAL_TICKS = 200;
    public static final long THREAT_TICKS = 2_400L;
    public static final long GRIEVANCE_TICKS = 12_000L;
    public static final int MAX_RELATIONSHIP_ENTRIES = 4;
    public static final int MAX_TRAIL_ENTRIES = 4;
    public static final int TRAIL_EXPIRY_TICKS = 2_400;
    public static final int MAX_HUNT_MEMBERS = 6;
    public static final int MAX_RECRUITMENT_CANDIDATES = 16;
    public static final int HUNT_EPISODE_TICKS = 2_400;
    public static final int MAX_TARGET_CHANGES = 2;
    public static final int MAX_FAMILIARITY_ENTRIES = 3;
    public static final int MAX_COHORT_MEMBERS = 3;
    public static final int COHORT_EXPIRY_TICKS = 2_400;
    public static final int FAMILIARITY_OBSERVATION_INTERVAL_TICKS = 100;
    public static final int FAMILIARITY_BOND_POINTS = 6;
    public static final int FAMILIARITY_RADIUS = 12;
    public static final int WARNING_EXPIRY_TICKS = 200;
    public static final int MAX_WARNING_RECIPIENTS = 2;
    public static final int WARNING_RADIUS = 16;
    public static final int REFUGE_SEARCH_INTERVAL_TICKS = 200;
    public static final int REFUGE_HORIZONTAL_RADIUS = 8;
    public static final int REFUGE_VERTICAL_RADIUS = 4;
    public static final int MAX_REFUGE_BLOCK_INSPECTIONS = 256;
    public static final int REFUGE_EXPIRY_TICKS = 12_000;
    public static final int TERRITORY_WARNING_RADIUS = 12;
    public static final int TERRITORY_DEFENSE_RADIUS = 4;
    public static final int TERRITORY_INTRUSION_TICKS = 100;
    public static final double POUNCE_MIN_DISTANCE = 3.0D;
    public static final double POUNCE_MAX_DISTANCE = 6.0D;
    public static final int POUNCE_TELEGRAPH_TICKS = 10;
    public static final int POUNCE_COOLDOWN_TICKS = 80;
    public static final int POUNCE_ABORT_RECOVERY_TICKS = 20;
    public static final int HARRY_TICKS = 80;
    public static final int MAX_RETREAT_CANDIDATES = 8;
    public static final int STALK_RANGE = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int DECISION_INTERVAL_TICKS = 10;
    public static final int PLAN_INTERVAL_TICKS = 40;
    public static final int WEREWOLF_PERCEPTION_INTERVAL_TICKS = 80;
    public static final int FERAL_PERCEPTION_INTERVAL_TICKS = 60;
    public static final int WEREWOLF_PERCEPTION_RADIUS = 24;
    public static final int FERAL_PERCEPTION_RADIUS = 20;
    public static final int MAX_SCAN_RESULTS = 32;
    public static final int MAX_RETAINED_CANDIDATES = 16;
    public static final int MAX_LINE_OF_SIGHT_CHECKS = 8;
    public static final int MAX_ALERT_RECIPIENTS = 5;
    public static final int ALERT_RADIUS = 24;
    public static final int ALERT_INTERVAL_TICKS = 100;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int MAX_FEEDBACK_PARTICLES = 8;
    public static final int FORAGE_COOLDOWN_TICKS = 400;
    public static final int CARRION_SCAN_INTERVAL_TICKS = 100;
    public static final int CARRION_SCAN_RADIUS = 6;
    public static final int MAX_RAW_CARRION_VISITS = 12;
    public static final int MIN_CARRION_AGE_TICKS = 200;
    public static final double CARRION_EAT_DISTANCE = 1.5D;
    public static final int MAX_RAW_ARMING_VISITS = 32;
    public static final int MAX_RETAINED_ARMING = 16;

    private static final Set<String> ORDINARY_PREY_TYPES = Set.of(
        "minecraft:cow", "minecraft:pig", "minecraft:sheep",
        "minecraft:chicken", "minecraft:rabbit", "minecraft:goat"
    );
    private static final Set<String> CARRION_ITEMS = Set.of(
        "minecraft:rotten_flesh", "minecraft:beef", "minecraft:porkchop",
        "minecraft:mutton", "minecraft:chicken", "minecraft:rabbit"
    );
    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
        .comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
        .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private LycanPackRules() {
    }

    public enum Variant {
        WEREWOLF, FERAL_LYCAN
    }

    public enum Relation {
        THREAT, GRIEVANCE
    }

    public enum HuntRole {
        ROUTE_SETTER, PRESSURE, FLANK_LEFT, FLANK_RIGHT, INTERCEPT, REAR_GUARD
    }

    public enum HuntPhase {
        RALLY, TRAIL, FAN_OUT, PRESSURE, STRIKE, DISENGAGE, RECOVER
    }

    public enum ActionKind {
        NONE, POUNCE, HARRY, CONSUME_CARRION, RETREAT
    }

    public enum TrailClass {
        PREY, THREAT, BONDED_WARNING, EVENT
    }

    public record PreyFacts(
        String entityTypeId,
        boolean adult,
        boolean tamedOrOwned,
        boolean customNamed,
        boolean leashed,
        boolean vehicleOrPassenger,
        boolean aliveAndLoaded,
        boolean sameDimension,
        boolean withinRange,
        boolean eventProtected
    ) {
    }

    public record CarrionFacts(
        String itemId,
        boolean aliveEntity,
        boolean emptyStack,
        int ageTicks,
        boolean pickupDelayed,
        boolean resolvedOwner,
        boolean customNamed,
        boolean withinRange
    ) {
    }

    public record PlayerRelation(UUID playerId, Relation relation, int confidence, long observedAt, long expiresAt) {
    }

    public record TrailObservation(UUID key, int confidence, long observedAt, long expiresAt) {
    }

    public record CoordinatorCandidate(
        UUID memberId,
        boolean validLease,
        boolean eventLeader,
        boolean pathCapableAdult,
        float healthFraction,
        int hunger
    ) {
    }

    public record HuntAbortFacts(
        boolean dawnOutsideEvent,
        boolean targetInvalid,
        boolean coordinatorUnavailable,
        boolean quorumLost,
        boolean dimensionMismatch,
        boolean hazard,
        float healthFraction,
        int routeFailures,
        boolean deadlineExceeded,
        int targetChanges
    ) {
    }

    public static int defaultHunger(final Variant variant) {
        return variant == Variant.WEREWOLF ? DEFAULT_WEREWOLF_HUNGER : DEFAULT_FERAL_HUNGER;
    }

    public static int reconcileHunger(final int hunger, final long lastAt, final long now) {
        final long elapsed = Math.clamp(now - lastAt, 0L, MAX_ELAPSED_RECONCILE_TICKS);
        return Math.clamp(hunger + (int) (elapsed / HUNGER_RISE_INTERVAL_TICKS), 0, MAX_HUNGER);
    }

    public static int reconcileFear(final int fear, final long lastAt, final long now) {
        final long elapsed = Math.clamp(now - lastAt, 0L, MAX_ELAPSED_RECONCILE_TICKS);
        return Math.clamp(fear - (int) (elapsed / FEAR_DECAY_INTERVAL_TICKS), 0, MAX_HUNGER);
    }

    public static int hungerAfterKill(final Variant variant, final int hunger) {
        final int reduction = variant == Variant.WEREWOLF ? WEREWOLF_KILL_REDUCTION : FERAL_KILL_REDUCTION;
        return Math.max(0, hunger - reduction);
    }

    public static int hungerAfterCarrion(final int hunger) {
        return Math.max(0, hunger - CARRION_REDUCTION);
    }

    public static boolean assaultObjectiveFeedsHunger() {
        return false;
    }

    public static boolean mayWatchPrey(final int hunger) {
        return hunger >= WATCH_HUNGER;
    }

    public static boolean mayStartHuntEpisode(final int hunger) {
        return hunger >= HUNT_HUNGER;
    }

    public static boolean prefersRecovery(final int hunger) {
        return hunger <= RECOVERY_HUNGER;
    }

    public static int fearAfterOrdinaryDamage(final int fear) {
        return Math.min(MAX_HUNGER, fear + ORDINARY_DAMAGE_FEAR);
    }

    public static int fearAfterSilverOrGuardDamage(final int fear) {
        return Math.min(MAX_HUNGER, fear + SILVER_OR_GUARD_FEAR);
    }

    public static int fearAfterHunterSight(final int fear) {
        return Math.min(MAX_HUNGER, fear + HUNTER_SIGHT_FEAR);
    }

    public static boolean sightFearDue(final long lastSightFearAt, final long now) {
        return now - lastSightFearAt >= SIGHT_FEAR_INTERVAL_TICKS;
    }

    public static boolean forcedRetreat(
        final int fear,
        final float healthFraction,
        final boolean outnumberedDirectFight,
        final int routeFailures
    ) {
        return fear >= RETREAT_FEAR
            || healthFraction <= RETREAT_HEALTH_FRACTION
            || outnumberedDirectFight
            || routeFailures >= MAX_ROUTE_FAILURES;
    }

    public static boolean mayStalk(final int hunger, final int fear, final boolean hazard) {
        return hunger >= STALK_HUNGER && fear < PANIC_FEAR && !hazard;
    }

    public static boolean nightHuntingEligible(final long dayTime) {
        return dayTime >= NIGHT_START && dayTime <= NIGHT_END;
    }

    public static boolean duskStarted(final long dayTime) {
        return dayTime >= DUSK_START;
    }

    public static int minimumRecruitmentQuorum(final boolean fullMoon) {
        return fullMoon ? 2 : 3;
    }

    public static int recruitmentRadius(final boolean fullMoon) {
        return fullMoon ? 24 : 20;
    }

    public static boolean maySoloHunt(final int hunger, final boolean fullMoon) {
        return hunger >= HUNT_HUNGER;
    }

    public static int longRangeConfidence(
        final int baseConfidence,
        final boolean rainingOrThundering,
        final boolean openSky
    ) {
        int confidence = baseConfidence;
        if (rainingOrThundering) confidence -= confidence / 4;
        if (!openSky) confidence -= confidence / 4;
        return Math.max(0, confidence);
    }

    public static boolean isOrdinaryPreyType(final String entityTypeId) {
        return ORDINARY_PREY_TYPES.contains(entityTypeId);
    }

    public static boolean eligibleLivingPrey(final PreyFacts facts) {
        return isOrdinaryPreyType(facts.entityTypeId())
            && facts.adult()
            && !facts.tamedOrOwned()
            && !facts.customNamed()
            && !facts.leashed()
            && !facts.vehicleOrPassenger()
            && facts.aliveAndLoaded()
            && facts.sameDimension()
            && facts.withinRange()
            && !facts.eventProtected();
    }

    public static boolean isCarrionItem(final String itemId) {
        return CARRION_ITEMS.contains(itemId);
    }

    public static boolean eligibleCarrion(final CarrionFacts facts) {
        return isCarrionItem(facts.itemId())
            && facts.aliveEntity()
            && !facts.emptyStack()
            && facts.ageTicks() >= MIN_CARRION_AGE_TICKS
            && !facts.pickupDelayed()
            && !facts.resolvedOwner()
            && !facts.customNamed()
            && facts.withinRange();
    }

    public static boolean mayForage(
        final int hunger,
        final boolean hazard,
        final boolean directThreat,
        final boolean higherPriorityAction,
        final long forageCooldownUntil,
        final long now
    ) {
        return hunger >= STALK_HUNGER
            && !hazard
            && !directThreat
            && !higherPriorityAction
            && now >= forageCooldownUntil;
    }

    public static boolean candidatePreferred(
        final double firstDistanceSqr,
        final UUID firstId,
        final double secondDistanceSqr,
        final UUID secondId
    ) {
        if (firstDistanceSqr != secondDistanceSqr) return firstDistanceSqr < secondDistanceSqr;
        return UNSIGNED_UUID_ORDER.compare(firstId, secondId) < 0;
    }

    public static Comparator<UUID> unsignedUuidOrder() {
        return UNSIGNED_UUID_ORDER;
    }

    public static boolean acceptsAttribution(
        final boolean resolvedToPlayer,
        final boolean survivalOrAdventure,
        final boolean attackerAlive
    ) {
        return resolvedToPlayer && survivalOrAdventure && attackerAlive;
    }

    public static List<PlayerRelation> recordAttributedHit(
        final List<PlayerRelation> ledger,
        final UUID playerId,
        final long now
    ) {
        final List<PlayerRelation> entries = new ArrayList<>(ledger);
        final Optional<PlayerRelation> existing = entries.stream()
            .filter(entry -> entry.playerId().equals(playerId))
            .findFirst();
        if (existing.isPresent()) {
            entries.remove(existing.orElseThrow());
            final boolean live = existing.orElseThrow().expiresAt() > now;
            final Relation relation = live ? Relation.GRIEVANCE : Relation.THREAT;
            entries.add(new PlayerRelation(
                playerId, relation, relation == Relation.GRIEVANCE ? 2 : 1, now,
                now + (relation == Relation.GRIEVANCE ? GRIEVANCE_TICKS : THREAT_TICKS)
            ));
        } else {
            entries.add(new PlayerRelation(playerId, Relation.THREAT, 1, now, now + THREAT_TICKS));
        }
        while (entries.size() > MAX_RELATIONSHIP_ENTRIES) {
            entries.remove(entries.stream()
                .min(Comparator
                    .comparing((PlayerRelation entry) -> entry.expiresAt() > now)
                    .thenComparingInt(PlayerRelation::confidence)
                    .thenComparingLong(PlayerRelation::observedAt)
                    .thenComparing(PlayerRelation::playerId, UNSIGNED_UUID_ORDER))
                .orElseThrow());
        }
        return List.copyOf(entries);
    }

    public static List<PlayerRelation> pruneRelations(final List<PlayerRelation> ledger, final long now) {
        return ledger.stream().filter(entry -> entry.expiresAt() > now).toList();
    }

    public static Optional<CoordinatorCandidate> selectCoordinator(final List<CoordinatorCandidate> candidates) {
        return candidates.stream()
            .filter(CoordinatorCandidate::pathCapableAdult)
            .min(Comparator
                .comparing((CoordinatorCandidate candidate) -> !candidate.validLease())
                .thenComparing(candidate -> !candidate.eventLeader())
                .thenComparing(Comparator.comparingDouble(CoordinatorCandidate::healthFraction).reversed())
                .thenComparing(Comparator.comparingInt(CoordinatorCandidate::hunger).reversed())
                .thenComparing(CoordinatorCandidate::memberId, UNSIGNED_UUID_ORDER));
    }

    public static boolean coordinatorGrantsNoStatBonus() {
        return true;
    }

    public static Map<UUID, HuntRole> assignRoles(final List<UUID> sortedMembers) {
        final Map<UUID, HuntRole> roles = new LinkedHashMap<>();
        final HuntRole[] order = HuntRole.values();
        final int assigned = Math.min(sortedMembers.size(), Math.min(order.length, MAX_HUNT_MEMBERS));
        for (int index = 0; index < assigned; index++) {
            roles.put(sortedMembers.get(index), order[index]);
        }
        return roles;
    }

    public static int approachSectorDegrees(final long episodeId, final HuntRole role) {
        final int sectors = HuntRole.values().length;
        final int base = (int) Math.floorMod(episodeId, sectors);
        return Math.floorMod(base + role.ordinal(), sectors) * (360 / sectors);
    }

    public static int phaseDeadlineTicks(final HuntPhase phase) {
        return switch (phase) {
            case RALLY, PRESSURE, RECOVER -> 200;
            case TRAIL -> 400;
            case FAN_OUT, DISENGAGE -> 160;
            case STRIKE -> 240;
        };
    }

    public static boolean shouldAbortHunt(final HuntAbortFacts facts) {
        return facts.dawnOutsideEvent()
            || facts.targetInvalid()
            || facts.coordinatorUnavailable()
            || facts.quorumLost()
            || facts.dimensionMismatch()
            || facts.hazard()
            || facts.healthFraction() <= HUNT_ABORT_HEALTH_FRACTION
            || facts.routeFailures() >= MAX_ROUTE_FAILURES
            || facts.deadlineExceeded()
            || facts.targetChanges() >= MAX_TARGET_CHANGES + 1;
    }

    public static int familiarityAfterObservation(final int points) {
        return Math.min(FAMILIARITY_BOND_POINTS, points + 1);
    }

    public static int familiarityAfterFriendlyDamage(final int points) {
        return Math.max(0, points - 1);
    }

    public static boolean bonded(final int points) {
        return points >= FAMILIARITY_BOND_POINTS;
    }

    public static boolean warningDue(final long lastWarnAt, final long now) {
        return lastWarnAt <= 0L || now - lastWarnAt >= ALERT_INTERVAL_TICKS;
    }

    public static boolean cohortHasLeader() {
        return false;
    }

    public static List<TrailObservation> recordTrail(
        final List<TrailObservation> trails,
        final TrailObservation incoming,
        final long now
    ) {
        final List<TrailObservation> entries = new ArrayList<>(trails);
        entries.removeIf(entry -> entry.key().equals(incoming.key()));
        entries.add(incoming);
        while (entries.size() > MAX_TRAIL_ENTRIES) {
            entries.remove(entries.stream()
                .min(Comparator
                    .comparing((TrailObservation entry) -> entry.expiresAt() > now)
                    .thenComparingInt(TrailObservation::confidence)
                    .thenComparingLong(TrailObservation::observedAt)
                    .thenComparing(TrailObservation::key, UNSIGNED_UUID_ORDER))
                .orElseThrow());
        }
        return List.copyOf(entries);
    }

    public static List<TrailObservation> pruneTrails(final List<TrailObservation> trails, final long now) {
        return trails.stream().filter(entry -> entry.expiresAt() > now).toList();
    }

    public static int decayedTrailConfidence(final int confidence, final long observedAt, final long now) {
        final long elapsed = Math.clamp(now - observedAt, 0L, MAX_ELAPSED_RECONCILE_TICKS);
        return Math.max(0, confidence - (int) (elapsed * confidence / TRAIL_EXPIRY_TICKS));
    }

    public static boolean refugeValid(
        final boolean covered,
        final boolean standable,
        final boolean loaded,
        final long expiresAt,
        final long now,
        final int routeFailures
    ) {
        return covered && standable && loaded && expiresAt > now && routeFailures < MAX_ROUTE_FAILURES;
    }

    public static boolean mayPounce(
        final double distance,
        final boolean lineOfSight,
        final boolean standableLanding,
        final boolean obstructed,
        final long cooldownUntil,
        final long now
    ) {
        return distance >= POUNCE_MIN_DISTANCE && distance <= POUNCE_MAX_DISTANCE
            && lineOfSight && standableLanding && !obstructed && now >= cooldownUntil;
    }

    public static int routeFailures(final int failures) {
        return Math.min(MAX_ROUTE_FAILURES, failures + 1);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? now + ROUTE_BACKOFF_TICKS : 0L;
    }

    public static boolean navigationDue(final long lastNavigationAt, final long now) {
        return now - lastNavigationAt >= NAVIGATION_INTERVAL_TICKS;
    }

    public static boolean claimExpired(final long expiresAt, final long now) {
        return expiresAt <= now;
    }

    public static boolean feedbackDue(final long nextFeedbackAt, final long now) {
        return now >= nextFeedbackAt;
    }

    public static int perceptionIntervalTicks(final Variant variant) {
        return variant == Variant.WEREWOLF
            ? WEREWOLF_PERCEPTION_INTERVAL_TICKS
            : FERAL_PERCEPTION_INTERVAL_TICKS;
    }

    public static int perceptionRadius(final Variant variant) {
        return variant == Variant.WEREWOLF ? WEREWOLF_PERCEPTION_RADIUS : FERAL_PERCEPTION_RADIUS;
    }

    public static int stableOffset(final UUID id, final int modulus) {
        return (int) Math.floorMod(id.getLeastSignificantBits(), (long) modulus);
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return ((base ^ sum) & (addend ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }
}
