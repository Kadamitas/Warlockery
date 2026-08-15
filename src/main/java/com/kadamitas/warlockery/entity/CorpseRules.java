package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure F17 Corpse policy. No Level, entity mutation, path, global random,
 * or mutable runtime collection lives here; every value is a frozen design constant.
 */
public final class CorpseRules {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_COHESION = 1_200;
    public static final int COHESION_INTERVAL_TICKS = 20;
    public static final int MAX_DECAY_REMAINDER = 19;
    public static final int SCAVENGE_COHESION_THRESHOLD = 900;
    public static final int FOOD_COHESION_RESTORE = 300;
    public static final float FOOD_DIRECT_HEAL = 2.0F;
    public static final int WAKE_COHESION_FLOOR = 60;
    public static final int GROUND_MEAL_COOLDOWN_TICKS = 4_800;
    public static final int ITEM_SCAN_INTERVAL_TICKS = 100;
    public static final double ACTIVE_SCAN_RADIUS = 6.0D;
    public static final double DORMANT_SCAN_RADIUS = 2.0D;
    public static final int MAX_ITEM_CANDIDATES = 12;
    public static final double FINAL_ARRIVAL_DISTANCE_SQR = 4.0D;
    public static final int ITEM_RELEASE_BACKOFF_TICKS = 60;
    public static final int DIRECT_ATTACKER_TICKS = 120;
    public static final int GRAVE_TIMESTAMP_MAX_AGE = 100;
    public static final int RAISE_TIMESTAMP_MAX_AGE = 80;
    public static final double TARGET_RETENTION_DISTANCE = 24.0D;
    public static final int TARGET_LOS_INTERVAL_TICKS = 10;
    public static final int TARGET_LOST_SIGHT_TICKS = 40;
    public static final int CLUTCH_WINDUP_TICKS = 10;
    public static final int CLUTCH_RECOVERY_TICKS = 40;
    public static final int SLOWNESS_DURATION_TICKS = 40;
    public static final int SLOWNESS_AMPLIFIER = 0;
    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final double FOLLOW_START_DISTANCE = 8.0D;
    public static final double FOLLOW_STOP_DISTANCE = 4.0D;
    public static final double OWNER_ENVELOPE = 32.0D;
    public static final double OWNER_DEFENSE_RANGE = 16.0D;
    public static final int OWNER_RESOLVE_INTERVAL_TICKS = 20;
    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int HAZARD_OBSERVATION_READS = 18;
    public static final int SAFE_CANDIDATES = 16;
    public static final int SAFE_STATE_READS = 128;
    public static final int SAFE_ENTITY_VISITS_PER_CANDIDATE = 8;
    public static final int SAFE_ENTITY_VISITS_PER_SEARCH = 32;
    public static final long GRAVE_DURATION_TICKS = 144_000L;
    public static final double GRAVE_COMMAND_SPEED = 1.1D;
    public static final double COMBAT_SPEED = 1.0D;
    public static final String FOOD_ITEM_ID = "minecraft:rotten_flesh";
    public static final int GRAVE_RAW_BODIES_PER_SCAN = 64;
    /** The exact legacy Zombie lifecycle modifier IDs removed on construction, finalization, and load. */
    public static final Set<String> LEGACY_MODIFIER_IDS = Set.of(
        "minecraft:baby",
        "minecraft:random_spawn_bonus",
        "minecraft:zombie_random_spawn_bonus",
        "minecraft:leader_zombie_bonus",
        "minecraft:reinforcement_caller_charge",
        "minecraft:reinforcement_callee_charge"
    );

    private CorpseRules() {
    }

    /** Per-tick work categories rationed by the transient per-level record. */
    public enum Work {
        EXPENSIVE(16),
        PATH(8),
        ITEM_VISIT(192),
        CHARGED_READ(512),
        SAFE_ENTITY_VISIT(128),
        ITEM_MUTATION(4),
        CLUTCH(8),
        FEEDBACK(8),
        GRAVE_SCAN(2),
        GRAVE_DIRECTIVE(64);

        private final int quota;

        Work(final int quota) {
            this.quota = quota;
        }
    }

    public enum Activity {
        DORMANT, HAZARD, RECOVERY, GRAVE_COMMAND, COMBAT, SCAVENGE, FOLLOW, IDLE
    }

    public enum Hazard {
        LAVA, FIRE, CONTACT, NONE
    }

    public enum Release {
        NONE, MISSING, ILLEGAL, RANGE, LOST_SIGHT
    }

    public enum TargetSource {
        DIRECT_ATTACKER, EXPLICIT, GRAVE_CONTROLLER, RAISE_OWNER
    }

    public static int quota(final Work work) {
        return work.quota;
    }

    public static boolean mayCharge(final int used, final int amount, final Work work) {
        return amount > 0 && used >= 0 && used + amount <= work.quota;
    }

    public static boolean quotaExpired(final long currentServerTick, final long recordedServerTick) {
        return currentServerTick != recordedServerTick;
    }

    public record Decay(int cohesion, int remainder, boolean decremented) {
    }

    public static Decay decay(final int cohesion, final int remainder) {
        if (cohesion <= 0) {
            return new Decay(0, 0, false);
        }
        final int next = remainder + 1;
        if (next > MAX_DECAY_REMAINDER) {
            return new Decay(cohesion - 1, 0, true);
        }
        return new Decay(cohesion, next, false);
    }

    public static boolean dormant(final int cohesion) {
        return cohesion <= 0;
    }

    public static int fed(final int cohesion) {
        return Math.clamp((long) cohesion + FOOD_COHESION_RESTORE, 0, MAX_COHESION);
    }

    public static int woken(final int cohesion) {
        return Math.clamp(Math.max(cohesion, WAKE_COHESION_FLOOR), WAKE_COHESION_FLOOR, MAX_COHESION);
    }

    public static int cooldownTick(final int cooldown) {
        return Math.max(0, cooldown - 1);
    }

    public static int migrateLegacyCooldown(final long oldAbsoluteExpiry, final long currentGameTime) {
        long remaining;
        try {
            remaining = Math.subtractExact(oldAbsoluteExpiry, currentGameTime);
        } catch (final ArithmeticException overflow) {
            remaining = oldAbsoluteExpiry > 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return (int) Math.clamp(remaining, 0L, GROUND_MEAL_COOLDOWN_TICKS);
    }

    public static boolean mayScavenge(final int cohesion, final int cooldown, final boolean higherActivity) {
        return cohesion <= SCAVENGE_COHESION_THRESHOLD && cooldown == 0 && !higherActivity;
    }

    public static double scanRadius(final boolean dormant) {
        return dormant ? DORMANT_SCAN_RADIUS : ACTIVE_SCAN_RADIUS;
    }

    public record ItemCandidate(UUID id, String itemId, int count, double distanceSqr) {
    }

    public record ItemToken(UUID id, String itemId, int count) {
    }

    public static Optional<ItemCandidate> selectItem(final List<ItemCandidate> rawSnapshot) {
        return rawSnapshot.stream()
            .limit(MAX_ITEM_CANDIDATES)
            .filter(candidate -> FOOD_ITEM_ID.equals(candidate.itemId()) && candidate.count() > 0)
            .min(Comparator.comparingDouble(ItemCandidate::distanceSqr)
                .thenComparing(ItemCandidate::id));
    }

    public static boolean tokenValid(
        final ItemToken token,
        final ItemCandidate current,
        final boolean aliveLoadedSameLevel,
        final boolean populationTokenAvailable
    ) {
        return aliveLoadedSameLevel
            && populationTokenAvailable
            && token.id().equals(current.id())
            && token.itemId().equals(current.itemId())
            && token.count() == current.count()
            && current.distanceSqr() <= FINAL_ARRIVAL_DISTANCE_SQR;
    }

    public record OwnerFacts(
        Optional<UUID> raiseOwner,
        Optional<UUID> graveOwner,
        long graveExpiry,
        boolean graveOwnerLoaded,
        boolean graveOwnerHasPath
    ) {
    }

    public static boolean graveKeysUnexpired(final OwnerFacts facts, final long gameTime) {
        return facts.graveOwner().isPresent() && gameTime < facts.graveExpiry();
    }

    public static boolean graveAuthorityActive(final OwnerFacts facts, final long gameTime) {
        return graveKeysUnexpired(facts, gameTime)
            && facts.graveOwnerLoaded()
            && facts.graveOwnerHasPath();
    }

    public static boolean protectedIdentity(final OwnerFacts facts, final UUID candidate) {
        return protectedIdentities(facts).contains(candidate);
    }

    public static Set<UUID> protectedIdentities(final OwnerFacts facts) {
        final java.util.LinkedHashSet<UUID> identities = new java.util.LinkedHashSet<>(2);
        facts.raiseOwner().ifPresent(identities::add);
        facts.graveOwner().ifPresent(identities::add);
        return Set.copyOf(identities);
    }

    public static boolean manualFeedAccepted(
        final OwnerFacts facts,
        final UUID interactingPlayer,
        final long gameTime,
        final boolean healthBelowMax,
        final int cohesion
    ) {
        final boolean improvable = healthBelowMax || cohesion < MAX_COHESION;
        if (!improvable) {
            return false;
        }
        final boolean raise = facts.raiseOwner().map(interactingPlayer::equals).orElse(false);
        final boolean grave = facts.graveOwner().map(interactingPlayer::equals).orElse(false)
            && gameTime < facts.graveExpiry()
            && facts.graveOwnerHasPath();
        return raise || grave;
    }

    public static boolean timestampFresh(final int ownerTick, final int timestamp, final int maxAge) {
        if (timestamp <= 0) {
            return false;
        }
        final int age = ownerTick - timestamp;
        return age >= 0 && age <= maxAge;
    }

    public record TargetLegality(
        boolean baseLegal,
        boolean self,
        boolean dead,
        boolean invulnerable,
        boolean crossLevel,
        boolean protectedOwner,
        boolean corpse,
        boolean creativeOrSpectator,
        boolean garbed
    ) {
        public static TargetLegality of(final boolean baseLegal) {
            return new TargetLegality(baseLegal, false, false, false, false, false, false, false, false);
        }

        public TargetLegality withSelf(final boolean value) {
            return new TargetLegality(baseLegal, value, dead, invulnerable, crossLevel,
                protectedOwner, corpse, creativeOrSpectator, garbed);
        }

        public TargetLegality withDead(final boolean value) {
            return new TargetLegality(baseLegal, self, value, invulnerable, crossLevel,
                protectedOwner, corpse, creativeOrSpectator, garbed);
        }

        public TargetLegality withInvulnerable(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, value, crossLevel,
                protectedOwner, corpse, creativeOrSpectator, garbed);
        }

        public TargetLegality withCrossLevel(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, invulnerable, value,
                protectedOwner, corpse, creativeOrSpectator, garbed);
        }

        public TargetLegality withProtectedOwner(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, invulnerable, crossLevel,
                value, corpse, creativeOrSpectator, garbed);
        }

        public TargetLegality withCorpse(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, invulnerable, crossLevel,
                protectedOwner, value, creativeOrSpectator, garbed);
        }

        public TargetLegality withCreativeOrSpectator(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, invulnerable, crossLevel,
                protectedOwner, corpse, value, garbed);
        }

        public TargetLegality withGarbed(final boolean value) {
            return new TargetLegality(baseLegal, self, dead, invulnerable, crossLevel,
                protectedOwner, corpse, creativeOrSpectator, value);
        }
    }

    public static boolean targetLegal(final TargetLegality legality) {
        return legality.baseLegal()
            && !legality.self()
            && !legality.dead()
            && !legality.invulnerable()
            && !legality.crossLevel()
            && !legality.protectedOwner()
            && !legality.corpse()
            && !legality.creativeOrSpectator()
            && !legality.garbed();
    }

    public static Optional<TargetSource> targetSource(
        final boolean directAttacker,
        final boolean gravePositionCommand,
        final boolean explicitTarget,
        final boolean graveControllerSignal
    ) {
        if (directAttacker) {
            return Optional.of(TargetSource.DIRECT_ATTACKER);
        }
        if (gravePositionCommand) {
            return Optional.empty();
        }
        if (explicitTarget) {
            return Optional.of(TargetSource.EXPLICIT);
        }
        return graveControllerSignal ? Optional.of(TargetSource.GRAVE_CONTROLLER) : Optional.empty();
    }

    public static Optional<TargetSource> targetSourceWithoutExplicit(
        final boolean directAttacker,
        final boolean gravePositionCommand,
        final boolean graveControllerSignal,
        final boolean raiseDefenseSignal
    ) {
        if (directAttacker) {
            return Optional.of(TargetSource.DIRECT_ATTACKER);
        }
        if (gravePositionCommand) {
            return Optional.empty();
        }
        if (graveControllerSignal) {
            return Optional.of(TargetSource.GRAVE_CONTROLLER);
        }
        return raiseDefenseSignal ? Optional.of(TargetSource.RAISE_OWNER) : Optional.empty();
    }

    public static Release retention(
        final boolean loadedAlive,
        final boolean sameLevel,
        final boolean legal,
        final double distance,
        final int lostSightTicks
    ) {
        if (!loadedAlive || !sameLevel) {
            return Release.MISSING;
        }
        if (!legal) {
            return Release.ILLEGAL;
        }
        if (distance > TARGET_RETENTION_DISTANCE) {
            return Release.RANGE;
        }
        if (lostSightTicks > TARGET_LOST_SIGHT_TICKS) {
            return Release.LOST_SIGHT;
        }
        return Release.NONE;
    }

    public static boolean lineOfSightDue(final long lastCheck, final long now) {
        return now - lastCheck >= TARGET_LOS_INTERVAL_TICKS;
    }

    public static boolean effectiveDamage(
        final boolean superAccepted,
        final float healthPlusAbsorptionBefore,
        final float healthPlusAbsorptionAfter
    ) {
        return superAccepted && healthPlusAbsorptionAfter < healthPlusAbsorptionBefore;
    }

    public static boolean clutchComplete(final int windupTicks) {
        return windupTicks >= CLUTCH_WINDUP_TICKS;
    }

    public static boolean applySlowness(
        final boolean hitAccepted,
        final float healthPlusAbsorptionBefore,
        final float healthPlusAbsorptionAfter
    ) {
        return effectiveDamage(hitAccepted, healthPlusAbsorptionBefore, healthPlusAbsorptionAfter);
    }

    public static boolean pathDue(final long lastRequest, final long now) {
        return lastRequest < 0L || now - lastRequest >= PATH_INTERVAL_TICKS;
    }

    public record Route(int failures, long backoffUntil, boolean released) {
        public static Route fresh() {
            return new Route(0, 0L, false);
        }
    }

    public static Route routeFailed(final Route route, final long now) {
        final int failures = Math.min(route.failures() + 1, MAX_ROUTE_FAILURES);
        if (failures >= MAX_ROUTE_FAILURES) {
            return new Route(failures, now + ROUTE_BACKOFF_TICKS, true);
        }
        return new Route(failures, route.backoffUntil(), false);
    }

    public static Route routeSucceeded() {
        return Route.fresh();
    }

    public static boolean routeAllowed(final Route route, final long now) {
        return now >= route.backoffUntil();
    }

    public static boolean followShouldStart(final double distance) {
        return distance > FOLLOW_START_DISTANCE && distance <= OWNER_ENVELOPE;
    }

    public static boolean followShouldStop(final double distance) {
        return distance <= FOLLOW_STOP_DISTANCE;
    }

    /** Hysteresis: an already-following Body keeps closing until the exact 4-block stop. */
    public static boolean followShouldContinue(final double distance) {
        return distance > FOLLOW_STOP_DISTANCE && distance <= OWNER_ENVELOPE;
    }

    /** While Grave control is active, Raise-owner defense is suspended, not erased. */
    public static boolean raiseDefenseAvailable(final boolean graveAuthorityActive) {
        return !graveAuthorityActive;
    }

    public static boolean ownerDefenseInRange(final double distance) {
        return distance <= OWNER_DEFENSE_RANGE;
    }

    public static Hazard hazard(final boolean fire, final boolean lava, final boolean contact) {
        if (lava) {
            return Hazard.LAVA;
        }
        if (fire) {
            return Hazard.FIRE;
        }
        return contact ? Hazard.CONTACT : Hazard.NONE;
    }

    public static boolean hazardObservationDue(final long lastObservation, final long now) {
        return now - lastObservation >= HAZARD_INTERVAL_TICKS;
    }

    public static boolean safeSearchAffordable(
        final int chargedReadsUsed,
        final int entityVisitsUsed,
        final int candidateReads,
        final int candidateEntityVisits
    ) {
        return candidateEntityVisits <= SAFE_ENTITY_VISITS_PER_CANDIDATE
            && chargedReadsUsed + candidateReads <= SAFE_STATE_READS
            && entityVisitsUsed + candidateEntityVisits <= SAFE_ENTITY_VISITS_PER_SEARCH;
    }

    public static int stagger(final UUID body, final int period) {
        if (period <= 1) {
            return 0;
        }
        return Math.floorMod(body.hashCode(), period);
    }
}
