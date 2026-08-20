package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.WerewolfHunterRules.Evidence;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.EvidenceType;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Intent;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.LaneCandidate;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.QuarryCandidate;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.ShotFacts;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WerewolfHunterRuntime {
    private WerewolfHunterRuntime() {
    }

    public static final class Counters {
        long observationScans;
        long candidateVisits;
        long lineOfSightChecks;
        long blockReads;
        long navigationRequests;
        long laneSearches;
        long warnings;
        long shotCancellations;
        long hazardInterruptions;
        long releases;

        public long observationScans() { return observationScans; }
        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long blockReads() { return blockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long laneSearches() { return laneSearches; }
        public long warnings() { return warnings; }
        public long shotCancellations() { return shotCancellations; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long releases() { return releases; }
    }

    public static void tick(final WerewolfHunterEntity hunter, final ServerLevel level) {
        final long now = level.getGameTime();
        WerewolfHunterState state = hunter.hunterState();
        if (hazardActive(hunter)) {
            hunter.hunterCounters().hazardInterruptions++;
            hunter.setHunterState(clearAttackClaims(hunter, state).withIntent(Intent.RETREAT));
            return;
        }
        if (!WerewolfHunterRules.decisionDue(state.cadence().nextDecisionAt(), now)) {
            return;
        }
        state = state.withCadence(new WerewolfHunterState.Cadence(
            WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.DECISION_INTERVAL_TICKS),
            state.cadence().nextObservationAt(),
            state.cadence().nextScheduleAt(),
            state.cadence().nextFeedbackAt(),
            state.cadence().nextNavigationAt()
        ));
        state = state.withEvidence(WerewolfHunterRules.pruneEvidence(state.evidence(), now));
        if (state.cadence().nextObservationAt() <= 0L || now >= state.cadence().nextObservationAt()) {
            state = state.withCadence(new WerewolfHunterState.Cadence(
                state.cadence().nextDecisionAt(),
                WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.OBSERVATION_INTERVAL_TICKS),
                state.cadence().nextScheduleAt(),
                state.cadence().nextFeedbackAt(),
                state.cadence().nextNavigationAt()
            ));
            state = observeWitnessedAttacks(hunter, level, state, now);
        }
        state = reconcileQuarry(hunter, level, state, now);
        hunter.setHunterState(state);
    }

    static boolean hazardActive(final WerewolfHunterEntity hunter) {
        return hunter.isOnFire() || hunter.isInLava();
    }

    static WerewolfHunterState observeWitnessedAttacks(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final WerewolfHunterState input,
        final long now
    ) {
        WerewolfHunterState state = input;
        hunter.hunterCounters().observationScans++;
        final AABB bounds = hunter.getBoundingBox().inflate(WerewolfHunterRules.OBSERVATION_RADIUS);
        final List<LivingEntity> victims = BoundedEntityQuery.collect(
            level,
            LivingEntity.class, bounds,
            candidate -> candidate != hunter && isProtectedActor(hunter, candidate),
            WerewolfHunterRules.MAX_RETAINED_CANDIDATES
        );
        final double witnessRangeSqr =
            (double) WerewolfHunterRules.WITNESS_RADIUS * WerewolfHunterRules.WITNESS_RADIUS;
        final List<UUID> genericAttackers = new ArrayList<>();
        final List<LivingEntity> observedVictims = new ArrayList<>();
        final List<LivingEntity> observedAttackers = new ArrayList<>();
        int visited = 0;
        for (final LivingEntity victim : victims) {
            visited++;
            hunter.hunterCounters().candidateVisits++;
            final LivingEntity attacker = victim.getLastHurtByMob();
            final boolean staleAttribution = victim.getLastHurtByMobTimestamp()
                + WerewolfHunterRules.WITNESS_FRESHNESS_TICKS < victim.tickCount;
            if (attacker == null || staleAttribution || attacker == hunter || !attacker.isAlive()
                || attacker.level() != level
                || isProtectedActor(hunter, attacker)
                || hunter.distanceToSqr(attacker) > witnessRangeSqr
                || victim.distanceToSqr(attacker) > witnessRangeSqr) {
                continue;
            }
            genericAttackers.add(attacker.getUUID());
            observedVictims.add(victim);
            observedAttackers.add(attacker);
        }
        final List<UUID> retained = retainCandidates(
            evidenceTarget(state, EvidenceType.EVENT_QUARRY, now),
            evidenceSource(state, EvidenceType.DIRECT_ATTACK, now),
            state.quarryId(),
            genericAttackers
        );
        int losBudget = WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS;
        for (int index = 0; index < observedAttackers.size(); index++) {
            final LivingEntity attacker = observedAttackers.get(index);
            if (!retained.contains(attacker.getUUID())) continue;
            if (losBudget < 2) break;
            losBudget -= 2;
            hunter.hunterCounters().lineOfSightChecks += 2;
            if (!hunter.getSensing().hasLineOfSight(observedVictims.get(index))
                || !hunter.getSensing().hasLineOfSight(attacker)) {
                continue;
            }
            state = state.withEvidence(WerewolfHunterRules.recordEvidence(
                state.evidence(),
                WerewolfHunterRules.createEvidence(
                    EvidenceType.WITNESSED_ATTACK,
                    Optional.of(attacker.getUUID()),
                    Optional.empty(),
                    Optional.of(attacker.blockPosition().asLong()),
                    Optional.of(level.dimension().identifier().toString()),
                    now
                ),
                now
            ));
        }
        return state;
    }

    static WerewolfHunterState reconcileQuarry(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final WerewolfHunterState input,
        final long now
    ) {
        WerewolfHunterState state = input;
        final int bolts = hunter.silverBoltCount();
        if (state.deadlines().actionBackoffUntil() > now) {
            return releaseQuarry(hunter, state, Intent.RETURN);
        }
        if (!WerewolfHunterRules.mayCommitRanged(bolts)) {
            hunter.hunterCounters().releases++;
            return releaseQuarry(hunter, state, Intent.RESUPPLY);
        }
        if (WerewolfHunterRules.retreatRequired(
            hunter.getHealth() / hunter.getMaxHealth(), bolts, state.routeFailures(),
            false, false, false
        )) {
            state = releaseQuarry(hunter, state, Intent.RETREAT);
            return state.withDeadlines(new WerewolfHunterState.Deadlines(
                state.deadlines().warnedAt(), state.deadlines().engageUntil(),
                state.deadlines().lostSightUntil(), state.deadlines().searchUntil(),
                WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.RETREAT_TICKS),
                state.deadlines().actionBackoffUntil()
            ));
        }
        final WerewolfHunterState snapshot = state;
        final Optional<UUID> selected = WerewolfHunterRules.selectQuarry(
            evidenceTarget(snapshot, EvidenceType.EVENT_QUARRY, now),
            evidenceSource(snapshot, EvidenceType.DIRECT_ATTACK, now),
            snapshot.quarryId().filter(id -> hasValidSupport(snapshot, id, now)),
            witnessedCandidates(hunter, snapshot, now)
        );
        if (selected.isEmpty()) {
            if (state.quarryId().isPresent()) {
                hunter.hunterCounters().releases++;
                state = releaseQuarry(hunter, state, Intent.RETURN);
            }
            state = investigate(hunter, level, state, now);
            return state.intent() == Intent.INVESTIGATE ? state : navigateHome(hunter, state, now);
        }
        if (state.quarryId().isPresent() && !state.quarryId().equals(selected)) {
            state = state.withDeadlines(new WerewolfHunterState.Deadlines(
                0L, 0L, state.deadlines().lostSightUntil(), state.deadlines().searchUntil(),
                state.deadlines().retreatUntil(), state.deadlines().actionBackoffUntil()
            ));
        }
        final Entity resolved = level.getEntity(selected.orElseThrow());
        if (!(resolved instanceof LivingEntity quarry) || !quarry.isAlive()
            || quarry.level() != level) {
            hunter.hunterCounters().releases++;
            state = recordLostQuarry(hunter, level, state, selected.orElseThrow(), now);
            state = releaseQuarry(hunter, state, Intent.INVESTIGATE);
            return investigate(hunter, level, state, now);
        }
        hunter.rememberQuarrySeen(quarry.blockPosition());
        state = state.withQuarry(selected);
        if (state.deadlines().warnedAt() <= 0L) {
            hunter.hunterCounters().warnings++;
            hunter.setTarget(null);
            level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(),
                SoundEvents.PILLAGER_AMBIENT, hunter.getSoundSource(), 1.0F, 0.8F);
            return state.withIntent(Intent.WARN).withDeadlines(new WerewolfHunterState.Deadlines(
                Math.max(1L, now), state.deadlines().engageUntil(), state.deadlines().lostSightUntil(),
                state.deadlines().searchUntil(), state.deadlines().retreatUntil(),
                state.deadlines().actionBackoffUntil()
            ));
        }
        if (!WerewolfHunterRules.warnWaitElapsed(
            state.deadlines().warnedAt(), now, hunter.distanceToSqr(quarry) <= 9.0D
        )) {
            return state.withIntent(Intent.WARN);
        }
        final List<LivingEntity> blocking = protectedInCorridor(hunter, level, quarry);
        if (WerewolfHunterRules.crossfireCancelsShot(blocking.size())) {
            hunter.hunterCounters().shotCancellations++;
            hunter.setTarget(null);
            hunter.stopUsingItem();
            hunter.setChargingCrossbow(false);
            state = secureLane(hunter, level, state, quarry, blocking, now);
            return state.withIntent(Intent.REPOSITION);
        }
        hunter.setTarget(quarry);
        final long engageUntil = state.deadlines().engageUntil() > now
            ? state.deadlines().engageUntil()
            : WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.ENGAGE_TICKS);
        return state.withIntent(Intent.ENGAGE).withDeadlines(new WerewolfHunterState.Deadlines(
            state.deadlines().warnedAt(), engageUntil, state.deadlines().lostSightUntil(),
            state.deadlines().searchUntil(), state.deadlines().retreatUntil(),
            state.deadlines().actionBackoffUntil()
        ));
    }

    private static WerewolfHunterState investigate(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final WerewolfHunterState input,
        final long now
    ) {
        WerewolfHunterState state = input;
        final String dimension = level.dimension().identifier().toString();
        final Optional<Evidence> clue = state.evidence().stream()
            .filter(entry -> entry.type() == EvidenceType.LAST_KNOWN && entry.valid(now))
            .filter(entry -> entry.packedPosition().isPresent()
                && entry.dimension().map(dimension::equals).orElse(false))
            .findFirst();
        if (clue.isEmpty() || state.deadlines().searchUntil() <= now) {
            if (state.anchors().search().isPresent()) {
                state = state.withAnchors(new WerewolfHunterState.Anchors(
                    state.anchors().settlement(), state.anchors().event(),
                    state.anchors().lane(), Optional.empty(), state.anchors().returnPoint()
                ));
            }
            return state;
        }
        final BlockPos locus = BlockPos.of(clue.orElseThrow().packedPosition().orElseThrow());
        state = state.withAnchors(new WerewolfHunterState.Anchors(
            state.anchors().settlement(), state.anchors().event(),
            state.anchors().lane(), Optional.of(locus), state.anchors().returnPoint()
        ));
        if (!WerewolfHunterRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
            return state.withIntent(Intent.INVESTIGATE);
        }
        final List<BlockPos> waypoints = searchWaypoints(locus, hunter.getUUID());
        final BlockPos waypoint = waypoints.get((int) Math.floorMod(
            now / WerewolfHunterRules.NAVIGATION_INTERVAL_TICKS, (long) waypoints.size()
        ));
        hunter.hunterCounters().navigationRequests++;
        final boolean accepted = hunter.getNavigation().moveTo(
            waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, 1.0D
        );
        state = state.withCadence(new WerewolfHunterState.Cadence(
            state.cadence().nextDecisionAt(), state.cadence().nextObservationAt(),
            state.cadence().nextScheduleAt(), state.cadence().nextFeedbackAt(),
            WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.NAVIGATION_INTERVAL_TICKS)
        ));
        return accepted
            ? state.withIntent(Intent.INVESTIGATE)
            : recordRouteFailure(hunter, state, now).withIntent(Intent.INVESTIGATE);
    }

    private static WerewolfHunterState secureLane(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final WerewolfHunterState input,
        final LivingEntity quarry,
        final List<LivingEntity> blocking,
        final long now
    ) {
        WerewolfHunterState state = input;
        hunter.hunterCounters().laneSearches++;
        final int[] readBudget = {WerewolfHunterRules.MAX_LANE_BLOCK_READS};
        final List<LaneCandidate> candidates = new ArrayList<>();
        final List<BlockPos> positions = new ArrayList<>();
        final BlockPos base = hunter.blockPosition();
        outer:
        for (int dx = -WerewolfHunterRules.LANE_HORIZONTAL_RADIUS;
             dx <= WerewolfHunterRules.LANE_HORIZONTAL_RADIUS; dx += 4) {
            for (int dz = -WerewolfHunterRules.LANE_HORIZONTAL_RADIUS;
                 dz <= WerewolfHunterRules.LANE_HORIZONTAL_RADIUS; dz += 4) {
                final BlockPos candidate = base.offset(dx, 0, dz);
                if (!level.hasChunkAt(candidate)) continue;
                if (readBudget[0] < 3) break outer;
                if (!standable(level, candidate, hunter.hunterCounters(), readBudget)) continue;
                final double quarryDistance = Math.sqrt(candidate.distSqr(quarry.blockPosition()));
                final double protectedDistanceSqr = blocking.stream()
                    .mapToDouble(actor -> actor.blockPosition().distSqr(candidate))
                    .min().orElse(Double.MAX_VALUE);
                candidates.add(new LaneCandidate(
                    true,
                    WerewolfHunterRules.withinPreferredRange(quarryDistance),
                    0,
                    (Math.abs(dx) + Math.abs(dz)) / 8,
                    protectedDistanceSqr,
                    candidate.asLong()
                ));
                positions.add(candidate);
            }
        }
        final Optional<LaneCandidate> best = candidates.stream().min(WerewolfHunterRules.laneOrder());
        if (best.isEmpty()) {
            return state;
        }
        final BlockPos lane = positions.get(candidates.indexOf(best.orElseThrow()));
        state = state.withAnchors(new WerewolfHunterState.Anchors(
            state.anchors().settlement(), state.anchors().event(),
            Optional.of(lane), state.anchors().search(), state.anchors().returnPoint()
        ));
        if (WerewolfHunterRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
            hunter.hunterCounters().navigationRequests++;
            hunter.getNavigation().moveTo(lane.getX() + 0.5D, lane.getY(), lane.getZ() + 0.5D, 1.0D);
            state = state.withCadence(new WerewolfHunterState.Cadence(
                state.cadence().nextDecisionAt(), state.cadence().nextObservationAt(),
                state.cadence().nextScheduleAt(), state.cadence().nextFeedbackAt(),
                WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.NAVIGATION_INTERVAL_TICKS)
            ));
        }
        return state;
    }

    private static boolean standable(
        final ServerLevel level,
        final BlockPos pos,
        final Counters counters,
        final int[] readBudget
    ) {
        readBudget[0] -= 3;
        counters.blockReads += 3;
        return level.getBlockState(pos.below()).blocksMotion()
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static WerewolfHunterState navigateHome(
        final WerewolfHunterEntity hunter,
        final WerewolfHunterState input,
        final long now
    ) {
        WerewolfHunterState state = input;
        final Optional<BlockPos> home = input.anchors().returnPoint()
            .or(() -> input.anchors().settlement())
            .or(() -> input.anchors().event());
        if (home.isEmpty()
            || !WerewolfHunterRules.navigationDue(state.cadence().nextNavigationAt(), now)
            || state.deadlines().actionBackoffUntil() > now) {
            return state;
        }
        final BlockPos anchor = home.orElseThrow();
        if (hunter.blockPosition().closerThan(anchor, 3.0D)) {
            return state.withIntent(Intent.IDLE);
        }
        hunter.hunterCounters().navigationRequests++;
        final boolean accepted = hunter.getNavigation().moveTo(
            anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.0D
        );
        state = state.withCadence(new WerewolfHunterState.Cadence(
            state.cadence().nextDecisionAt(), state.cadence().nextObservationAt(),
            state.cadence().nextScheduleAt(), state.cadence().nextFeedbackAt(),
            WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.NAVIGATION_INTERVAL_TICKS)
        ));
        return accepted ? state.withIntent(Intent.RETURN) : recordRouteFailure(hunter, state, now);
    }

    public static WerewolfHunterState recordRouteFailure(
        final WerewolfHunterEntity hunter,
        final WerewolfHunterState input,
        final long now
    ) {
        final int failures = WerewolfHunterRules.routeFailures(input.routeFailures());
        WerewolfHunterState state = input.withRouteFailures(failures);
        if (failures >= WerewolfHunterRules.MAX_ROUTE_FAILURES) {
            state = clearAttackClaims(hunter, state).nextGeneration();
            state = state.withDeadlines(new WerewolfHunterState.Deadlines(
                state.deadlines().warnedAt(), state.deadlines().engageUntil(),
                state.deadlines().lostSightUntil(), state.deadlines().searchUntil(),
                state.deadlines().retreatUntil(),
                WerewolfHunterRules.routeBackoffUntil(failures, now)
            )).withRouteFailures(0);
        }
        return state;
    }

    public static WerewolfHunterState clearAttackClaims(
        final WerewolfHunterEntity hunter,
        final WerewolfHunterState state
    ) {
        hunter.setTarget(null);
        hunter.getNavigation().stop();
        hunter.stopUsingItem();
        hunter.setChargingCrossbow(false);
        return state.withQuarry(Optional.empty())
            .withAnchors(state.anchors().withoutTransientClaims())
            .withDeadlines(new WerewolfHunterState.Deadlines(
                0L, 0L, state.deadlines().lostSightUntil(), state.deadlines().searchUntil(),
                state.deadlines().retreatUntil(), state.deadlines().actionBackoffUntil()
            ));
    }

    private static WerewolfHunterState releaseQuarry(
        final WerewolfHunterEntity hunter,
        final WerewolfHunterState state,
        final Intent intent
    ) {
        return clearAttackClaims(hunter, state).withIntent(intent);
    }

    private static WerewolfHunterState recordLostQuarry(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final WerewolfHunterState state,
        final UUID quarryId,
        final long now
    ) {
        final Optional<BlockPos> locus = hunter.lastQuarrySeen();
        return state.withEvidence(WerewolfHunterRules.recordEvidence(
            state.evidence(),
            WerewolfHunterRules.createEvidence(
                EvidenceType.LAST_KNOWN, Optional.empty(), Optional.of(quarryId),
                locus.map(BlockPos::asLong),
                locus.map(pos -> level.dimension().identifier().toString()),
                now
            ),
            now
        )).withDeadlines(new WerewolfHunterState.Deadlines(
            state.deadlines().warnedAt(), state.deadlines().engageUntil(),
            state.deadlines().lostSightUntil(),
            WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.SEARCH_TICKS),
            state.deadlines().retreatUntil(), state.deadlines().actionBackoffUntil()
        ));
    }

    static Optional<UUID> evidenceTarget(final WerewolfHunterState state, final EvidenceType type, final long now) {
        return state.evidence().stream()
            .filter(entry -> entry.type() == type && entry.valid(now))
            .max(java.util.Comparator.comparingLong(Evidence::observedAt))
            .flatMap(Evidence::targetId);
    }

    static Optional<UUID> evidenceSource(final WerewolfHunterState state, final EvidenceType type, final long now) {
        return state.evidence().stream()
            .filter(entry -> entry.type() == type && entry.valid(now))
            .max(java.util.Comparator.comparingLong(Evidence::observedAt))
            .flatMap(Evidence::sourceId);
    }

    private static boolean hasValidSupport(final WerewolfHunterState state, final UUID id, final long now) {
        return state.evidence().stream().anyMatch(entry -> entry.valid(now)
            && entry.type() != EvidenceType.LAST_KNOWN
            && (entry.targetId().map(id::equals).orElse(false)
                || entry.sourceId().map(id::equals).orElse(false)));
    }

    private static List<QuarryCandidate> witnessedCandidates(
        final WerewolfHunterEntity hunter,
        final WerewolfHunterState state,
        final long now
    ) {
        final List<QuarryCandidate> candidates = new ArrayList<>();
        for (final Evidence entry : state.evidence()) {
            if (entry.type() != EvidenceType.WITNESSED_ATTACK || !entry.valid(now)) continue;
            entry.sourceId().ifPresent(id -> {
                final Entity resolved = hunter.level().getEntity(id);
                if (resolved instanceof LivingEntity living && living.isAlive()) {
                    candidates.add(new QuarryCandidate(id, hunter.distanceToSqr(living)));
                }
            });
        }
        return candidates;
    }

    public static List<UUID> retainCandidates(
        final Optional<UUID> eventQuarry,
        final Optional<UUID> directAttacker,
        final Optional<UUID> currentQuarry,
        final List<UUID> genericCandidates
    ) {
        final LinkedHashSet<UUID> retained = new LinkedHashSet<>();
        eventQuarry.ifPresent(retained::add);
        directAttacker.ifPresent(retained::add);
        currentQuarry.ifPresent(retained::add);
        for (final UUID candidate : genericCandidates) {
            if (retained.size() >= WerewolfHunterRules.MAX_RETAINED_CANDIDATES) break;
            retained.add(candidate);
        }
        return List.copyOf(retained);
    }

    public static boolean mayFireNow(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final LivingEntity target
    ) {
        final ItemStack crossbow = hunter.getMainHandItem();
        final ChargedProjectiles charged = crossbow.getOrDefault(
            DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY
        );
        hunter.hunterCounters().lineOfSightChecks++;
        final ShotFacts facts = new ShotFacts(
            crossbow.getItem() instanceof CrossbowItem,
            !charged.isEmpty(),
            target != null && target.isAlive(),
            target != null && target.level() == level,
            target != null && eligibleTarget(hunter, target),
            target != null && hunter.getSensing().hasLineOfSight(target),
            target != null && protectedInCorridor(hunter, level, target).isEmpty(),
            target != null && hunter.distanceToSqr(target) <= 32.0D * 32.0D,
            true
        );
        return WerewolfHunterRules.mayFire(facts);
    }

    static List<LivingEntity> protectedInCorridor(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final LivingEntity quarry
    ) {
        final AABB corridor = hunter.getBoundingBox().minmax(quarry.getBoundingBox()).inflate(1.0D);
        final List<LivingEntity> nearby = BoundedEntityQuery.collect(
            level,
            LivingEntity.class, corridor,
            candidate -> candidate != hunter && candidate != quarry && isProtectedActor(hunter, candidate),
            WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS
        );
        int checks = 0;
        final List<LivingEntity> blocking = new ArrayList<>();
        final Vec3 origin = hunter.getEyePosition();
        final Vec3 aim = quarry.getEyePosition().subtract(origin);
        for (final LivingEntity candidate : nearby) {
            checks++;
            hunter.hunterCounters().lineOfSightChecks++;
            hunter.hunterCounters().candidateVisits++;
            if (nearCorridorSegment(origin, aim, candidate.getEyePosition(), 1.5D)) {
                blocking.add(candidate);
            }
        }
        return blocking;
    }

    public static boolean nearCorridorSegment(
        final Vec3 origin,
        final Vec3 direction,
        final Vec3 point,
        final double radius
    ) {
        final double lengthSqr = direction.lengthSqr();
        if (lengthSqr < 1.0E-6D) return false;
        final Vec3 toPoint = point.subtract(origin);
        final double along = Math.clamp(toPoint.dot(direction) / lengthSqr, 0.0D, 1.0D);
        return toPoint.subtract(direction.scale(along)).lengthSqr() <= radius * radius;
    }

    static boolean isProtectedActor(final WerewolfHunterEntity hunter, final LivingEntity candidate) {
        return WerewolfHunterRules.protectedCorridorActor(new WerewolfHunterRules.ProtectedFacts(
            candidate instanceof AbstractVillager,
            candidate instanceof IronGolem,
            candidate instanceof WerewolfHunterEntity,
            candidate instanceof Player player && player.getLastHurtMob() != hunter
                && !EquipmentSetEffects.wearsCompleteHunterSet(player),
            candidate instanceof Player player && EquipmentSetEffects.wearsCompleteHunterSet(player)
        ));
    }

    public static void recordDirectAttack(
        final WerewolfHunterEntity hunter,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == hunter
            || !attacker.isAlive()) {
            return;
        }
        final long now = level.getGameTime();
        hunter.setHunterState(hunter.hunterState().withEvidence(WerewolfHunterRules.recordEvidence(
            hunter.hunterState().evidence(),
            WerewolfHunterRules.createEvidence(
                EvidenceType.DIRECT_ATTACK, Optional.of(attacker.getUUID()), Optional.empty(),
                Optional.of(attacker.blockPosition().asLong()),
                Optional.of(level.dimension().identifier().toString()),
                now
            ),
            now
        )));
    }

    public static void recordWitnessedAttack(
        final WerewolfHunterEntity hunter,
        final UUID attackerId,
        final long now
    ) {
        hunter.setHunterState(hunter.hunterState().withEvidence(WerewolfHunterRules.recordEvidence(
            hunter.hunterState().evidence(),
            WerewolfHunterRules.createEvidence(
                EvidenceType.WITNESSED_ATTACK, Optional.of(attackerId), Optional.empty(), now
            ),
            now
        )));
    }

    public static void assignHuntEvent(
        final WerewolfHunterEntity hunter,
        final UUID huntId,
        final UUID quarryId,
        final BlockPos anchor,
        final long now
    ) {
        final WerewolfHunterState state = hunter.hunterState();
        final String dimension = hunter.level().dimension().identifier().toString();
        hunter.setHunterState(state
            .withHunt(Optional.of(huntId),
                WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.EVENT_QUARRY_TICKS))
            .withAnchors(new WerewolfHunterState.Anchors(
                state.anchors().settlement(), Optional.of(anchor), state.anchors().lane(),
                state.anchors().search(), Optional.of(anchor)
            ))
            .withEvidence(WerewolfHunterRules.recordEvidence(
                state.evidence(),
                WerewolfHunterRules.createEvidence(
                    EvidenceType.EVENT_QUARRY, Optional.empty(), Optional.of(quarryId),
                    Optional.of(anchor.asLong()), Optional.of(dimension), now
                ),
                now
            )));
    }

    public static List<BlockPos> searchWaypoints(final BlockPos locus, final UUID hunterId) {
        final int offset = WerewolfHunterRules.stableOffset(hunterId, 4);
        final List<BlockPos> waypoints = new ArrayList<>();
        for (int index = 0; index < WerewolfHunterRules.MAX_SEARCH_WAYPOINTS; index++) {
            final int direction = (index + offset) % 4;
            final int reach = 4 + 4 * (index % 2);
            waypoints.add(switch (direction) {
                case 0 -> locus.offset(reach, 0, 0);
                case 1 -> locus.offset(-reach, 0, 0);
                case 2 -> locus.offset(0, 0, reach);
                default -> locus.offset(0, 0, -reach);
            });
        }
        return List.copyOf(waypoints);
    }

    public static boolean eligibleTarget(final WerewolfHunterEntity hunter, final LivingEntity target) {
        return hunter.hunterState().quarryId()
            .map(id -> id.equals(target.getUUID()))
            .orElse(false);
    }
}
