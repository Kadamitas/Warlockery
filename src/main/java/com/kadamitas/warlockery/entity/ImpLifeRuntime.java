package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.kadamitas.warlockery.entity.ImpLifeRules.Authority;
import com.kadamitas.warlockery.entity.ImpLifeRules.Duty;
import com.kadamitas.warlockery.entity.ImpLifeRules.InfernalOrder;
import com.kadamitas.warlockery.entity.ImpLifeRules.Observation;
import com.kadamitas.warlockery.entity.ImpLifeRules.ObservationType;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderFacts;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.util.DataParsing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ImpLifeRuntime {
    public static final int ORDER_INTAKE_RADIUS = 32;
    private static final double ARRIVAL_DISTANCE = 2.5;
    private static final double NAVIGATION_SPEED = 1.0;

    private ImpLifeRuntime() {
    }

    public static final class Counters {
        long blockReads;
        long lineOfSightChecks;
        long observationScans;
        long curiosityScans;
        long navigationRequests;
        long laneSearches;
        long scoutLegsCompleted;
        long reportsDelivered;
        long ordersAccepted;
        long ordersCleared;
        long hazardInterruptions;
        long windupCancellations;
        long shotsFired;
        long meleeAttempts;
        long releases;

        public long blockReads() {
            return blockReads;
        }

        public long lineOfSightChecks() {
            return lineOfSightChecks;
        }

        public long observationScans() {
            return observationScans;
        }

        public long curiosityScans() {
            return curiosityScans;
        }

        public long navigationRequests() {
            return navigationRequests;
        }

        public long laneSearches() {
            return laneSearches;
        }

        public long scoutLegsCompleted() {
            return scoutLegsCompleted;
        }

        public long reportsDelivered() {
            return reportsDelivered;
        }

        public long ordersAccepted() {
            return ordersAccepted;
        }

        public long ordersCleared() {
            return ordersCleared;
        }

        public long hazardInterruptions() {
            return hazardInterruptions;
        }

        public long windupCancellations() {
            return windupCancellations;
        }

        public long shotsFired() {
            return shotsFired;
        }

        public long meleeAttempts() {
            return meleeAttempts;
        }

        public long releases() {
            return releases;
        }
    }

    public enum FollowBand {
        ORBIT, PATH, RECOVER
    }

    public enum DestinationCheck {
        OK, UNLOADED, OUTSIDE_BORDER, COLLISION, HAZARD, NO_SUPPORT, BUDGET_EXHAUSTED
    }

    public static FollowBand followBand(final double distance) {
        if (distance > ImpLifeRules.FOLLOW_PATH_DISTANCE) {
            return FollowBand.RECOVER;
        }
        return distance >= ImpLifeRules.FOLLOW_ORBIT_DISTANCE ? FollowBand.PATH : FollowBand.ORBIT;
    }

    public static boolean watchWithinEnvelope(final double distance) {
        return distance >= ImpLifeRules.WATCH_ENVELOPE_MIN && distance <= ImpLifeRules.WATCH_ENVELOPE_MAX;
    }

    public static int nextScoutLeg(final int leg) {
        return Math.min(ImpLifeRules.SCOUT_LEGS, leg + 1);
    }

    public static List<BlockPos> curiositySampleOffsets(final UUID impId) {
        final List<BlockPos> pattern = new ArrayList<>();
        for (final int radius : new int[] {3, 6, 8}) {
            for (int direction = 0; direction < 8; direction++) {
                final double angle = 2.0 * Math.PI * direction / 8.0;
                for (final int dy : new int[] {-4, -2, 0, 2}) {
                    pattern.add(new BlockPos(
                        (int) Math.round(Math.cos(angle) * radius),
                        dy,
                        (int) Math.round(Math.sin(angle) * radius)
                    ));
                }
            }
        }
        final int rotation = ImpLifeRules.stableOffset(impId, pattern.size());
        final List<BlockPos> rotated = new ArrayList<>(pattern.size());
        for (int index = 0; index < pattern.size(); index++) {
            rotated.add(pattern.get((index + rotation) % pattern.size()));
        }
        return List.copyOf(rotated.subList(0, Math.min(rotated.size(), ImpLifeRules.CURIOSITY_READ_BUDGET)));
    }

    public static List<BlockPos> laneOffsets(final UUID impId) {
        final int side = (impId.getLeastSignificantBits() & 1L) == 0L ? 1 : -1;
        return List.of(
            new BlockPos(10 * side, 2, 0),
            new BlockPos(-10 * side, 2, 0),
            new BlockPos(0, 2, 10 * side),
            new BlockPos(0, 2, -10 * side),
            new BlockPos(8 * side, 4, 8),
            new BlockPos(-8 * side, 4, -8)
        );
    }

    public static List<BlockPos> followRecoveryOffsets(final UUID impId) {
        final int side = (impId.getLeastSignificantBits() & 1L) == 0L ? 1 : -1;
        return List.of(
            new BlockPos(side, 1, 1),
            new BlockPos(-side, 1, -1),
            new BlockPos(2 * side, 1, 0),
            new BlockPos(0, 1, 2 * side),
            new BlockPos(-2 * side, 2, 0),
            new BlockPos(0, 2, -2 * side),
            new BlockPos(3 * side, 0, 3),
            new BlockPos(-3 * side, 0, -3)
        );
    }

    public static void tick(final ImpEntity imp, final ServerLevel level) {
        final long now = level.getGameTime();
        ImpLifeState state = imp.lifeState();
        if (!imp.isAlive() || imp.isNoAi() || imp.isPassenger()) {
            if (state.action() != Action.NONE) {
                imp.getNavigation().stop();
                imp.setLifeState(state.withAction(Action.NONE).withDestination(Optional.empty()));
            }
            return;
        }
        if (HazardEscapeRuntime.tick(imp, level, CreatureKind.IMP)) {
            imp.lifeCounters().hazardInterruptions++;
            if (state.action() != Action.HAZARD_ESCAPE) {
                imp.setTarget(null);
                imp.setLifeState(state.withAction(Action.HAZARD_ESCAPE)
                    .withDestination(Optional.empty())
                    .withDeadlines(clearWindup(state.deadlines())));
            }
            return;
        }
        if (state.action() == Action.HAZARD_ESCAPE) {
            state = state.withAction(Action.NONE);
        }
        state = tickOwnerPresence(imp, level, state, now);
        state = tickWindupMotor(imp, level, state, now);
        if (state.action() == Action.RANGED_WINDUP) {
            imp.setLifeState(state);
            return;
        }
        if (!ImpLifeRules.due(state.cadence().nextDecisionAt(), now)) {
            imp.setLifeState(state);
            return;
        }
        final boolean combat = imp.getTarget() != null && imp.getTarget().isAlive();
        state = state.withCadence(new ImpLifeState.Cadence(
            ImpLifeRules.saturatingAdd(now, ImpLifeRules.decisionInterval(combat)),
            state.cadence().nextOwnerAt(),
            state.cadence().nextDiscoveryAt(),
            state.cadence().nextCuriosityAt(),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        state = decide(imp, level, state, now);
        imp.setLifeState(state);
    }

    static ImpLifeState decide(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = expireOrder(imp, level, input, now);
        state = combatBand(imp, level, state, now);
        if (combatClaims(state.action())) {
            return state;
        }
        state = dutyBand(imp, level, state, now);
        if (dutyClaims(state.action())) {
            return state;
        }
        if (state.order().filter(order -> order.valid(now)).isEmpty()
            && CreatureBehaviorState.owner(imp).isEmpty()) {
            imp.setLifeState(state);
            if (offerInfernalHierarchyOrder(imp, level, now)) {
                state = imp.lifeState();
            }
        }
        state = orderBand(imp, level, state, now);
        if (state.action() == Action.NPC_ORDER) {
            return state;
        }
        state = committedBand(imp, level, state, now);
        if (state.action() == Action.INSPECT || state.action() == Action.PERCH) {
            return state;
        }
        return autonomousBand(imp, level, state, now);
    }

    private static boolean combatClaims(final Action action) {
        return action == Action.RANGED_WINDUP || action == Action.CLOSE_ESCAPE || action == Action.DISENGAGE;
    }

    private static boolean dutyClaims(final Action action) {
        return action == Action.FOLLOW || action == Action.WATCH
            || action == Action.SCOUT_OUT || action == Action.SCOUT_RETURN;
    }

    static ImpLifeState tickOwnerPresence(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        if (!ImpLifeRules.due(input.cadence().nextOwnerAt(), now)) {
            return input;
        }
        ImpLifeState state = input.withCadence(new ImpLifeState.Cadence(
            input.cadence().nextDecisionAt(),
            ImpLifeRules.saturatingAdd(now, ImpLifeRules.OWNER_RESOLUTION_TICKS),
            input.cadence().nextDiscoveryAt(),
            input.cadence().nextCuriosityAt(),
            input.cadence().nextNavigationAt(),
            input.cadence().nextFeedbackAt()
        ));
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(imp);
        if (ownerId.isEmpty()) {
            return state;
        }
        final LivingEntity owner = resolveOwner(level, ownerId.orElseThrow());
        if (owner == null) {
            return state;
        }
        owner.addEffect(new MobEffectInstance(
            MobEffects.FIRE_RESISTANCE, ImpLifeRules.OWNER_AURA_DURATION_TICKS, 0, true, false));
        final LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && attacker != imp
            && owner.getLastHurtByMobTimestamp() + ImpLifeRules.OWNER_THREAT_FRESHNESS_TICKS >= owner.tickCount) {
            state = state.withThreat(Optional.of(new ImpLifeState.Threat(
                attacker.getUUID(),
                ImpLifeRules.saturatingAdd(now, ImpLifeRules.THREAT_EXPIRY_TICKS)
            )));
            imp.lifeCounters().lineOfSightChecks++;
            if (imp.getTarget() == null && imp.getSensing().hasLineOfSight(attacker)
                && imp.canAttack(attacker)) {
                imp.setTarget(attacker);
            }
        }
        return state;
    }

    static ImpLifeState tickWindupMotor(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        if (input.action() != Action.RANGED_WINDUP) {
            return input;
        }
        final LivingEntity target = imp.getTarget();
        final long startedAt = input.deadlines().windupStartedAt();
        imp.lifeCounters().lineOfSightChecks++;
        if (target == null || !target.isAlive() || target.level() != level
            || imp.distanceTo(target) > ImpLifeRules.TARGET_MAX_RANGE
            || !mayAttack(imp, target)
            || imp.isInWater()
            || !imp.getSensing().hasLineOfSight(target)) {
            imp.lifeCounters().windupCancellations++;
            return input.withAction(Action.NONE).withDeadlines(clearWindup(input.deadlines()));
        }
        imp.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!ImpLifeRules.windupComplete(startedAt, now)) {
            if ((now - startedAt) % 2L == 0L) {
                level.sendParticles(ParticleTypes.FLAME,
                    imp.getX(), imp.getEyeY(), imp.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
            }
            return input;
        }
        imp.performRangedAttack(target, 1.0F);
        imp.lifeCounters().shotsFired++;
        return input.withAction(Action.NONE).withDeadlines(new ImpLifeState.Deadlines(
            input.deadlines().recoveryUntil(),
            input.deadlines().meleeRecoveryUntil(),
            input.deadlines().curiosityBackoffUntil(),
            0L,
            now
        ));
    }

    static ImpLifeState combatBand(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(imp);
        LivingEntity target = imp.getTarget();
        if (target != null && (!target.isAlive() || target.level() != level
            || imp.distanceTo(target) > ImpLifeRules.TARGET_MAX_RANGE)) {
            releaseTarget(imp);
            target = null;
        }
        if (target != null && ownerId.isPresent() && !mayAttack(imp, target)) {
            final Authority authority = effectiveAuthority(imp, now);
            if (authority == Authority.SAME_PLAYER_DUAL) {
                state = state.withThreat(Optional.of(new ImpLifeState.Threat(
                    target.getUUID(),
                    ImpLifeRules.saturatingAdd(now, ImpLifeRules.THREAT_EXPIRY_TICKS)
                )));
            } else {
                releaseTarget(imp);
                target = null;
            }
        }
        if (target == null && ownerId.isEmpty()) {
            state = discoverUnboundTarget(imp, level, state, now);
            target = imp.getTarget();
        }
        final float healthFraction = imp.getHealth() / imp.getMaxHealth();
        if (target == null) {
            if (combatClaims(state.action())) {
                state = state.withAction(Action.NONE).withDestination(Optional.empty())
                    .withDeadlines(clearWindup(state.deadlines()));
            } else if (state.action() == Action.NONE && state.destination().isPresent()) {
                state = state.withDestination(Optional.empty());
            }
            if (state.retreatLatched() && healthFraction >= ImpLifeRules.RETREAT_RELEASE_FRACTION) {
                state = state.withRetreatLatched(false);
            }
            return state;
        }
        if (state.retreatLatched() || ImpLifeRules.retreatLatches(healthFraction, state.routeFailures())) {
            if (ImpLifeRules.retreatReleases(healthFraction, true, !state.retreatLatched())) {
                state = state.withRetreatLatched(false);
            } else {
                state = state.withRetreatLatched(true);
                releaseTarget(imp);
                return navigateRetreat(imp, level, state.withAction(Action.DISENGAGE), now);
            }
        }
        final double distance = imp.distanceTo(target);
        if (state.action() == Action.CLOSE_ESCAPE) {
            state = state.withAction(Action.DISENGAGE);
        }
        if (ImpLifeRules.tooClose(distance)) {
            final boolean disengaged = navigateAwayFrom(imp, level, target, now, state);
            if (!disengaged && ImpLifeRules.mayCloseEscape(
                distance, true, false, state.deadlines().meleeRecoveryUntil(), now)) {
                imp.lifeCounters().meleeAttempts++;
                imp.doHurtTarget(level, target);
                state = state.withDeadlines(new ImpLifeState.Deadlines(
                    state.deadlines().recoveryUntil(),
                    ImpLifeRules.saturatingAdd(now, ImpLifeRules.MELEE_RECOVERY_TICKS),
                    state.deadlines().curiosityBackoffUntil(),
                    0L,
                    state.deadlines().lastShotAt()
                ));
                return bumpNavigation(state, now).withAction(Action.CLOSE_ESCAPE);
            }
            return bumpNavigation(state, now).withAction(Action.DISENGAGE);
        }
        if (!ImpLifeRules.withinPreferredBand(distance)) {
            state = navigateLane(imp, level, state, target, now);
        }
        imp.lifeCounters().lineOfSightChecks++;
        if (ImpLifeRules.shotReady(state.deadlines().lastShotAt(), now)
            && !ImpLifeRules.tooFar(distance)
            && imp.getSensing().hasLineOfSight(target)) {
            return state.withAction(Action.RANGED_WINDUP)
                .withActionWindow(now, ImpLifeRules.saturatingAdd(now, ImpLifeRules.WINDUP_TICKS + 5L))
                .withDeadlines(new ImpLifeState.Deadlines(
                    state.deadlines().recoveryUntil(),
                    state.deadlines().meleeRecoveryUntil(),
                    state.deadlines().curiosityBackoffUntil(),
                    now,
                    state.deadlines().lastShotAt()
                ));
        }
        return state;
    }

    private static void releaseTarget(final ImpEntity imp) {
        if (imp.getTarget() != null) {
            imp.lifeCounters().releases++;
            imp.setTarget(null);
        }
    }

    static ImpLifeState discoverUnboundTarget(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        if (!ImpLifeRules.due(input.cadence().nextDiscoveryAt(), now)) {
            return input;
        }
        final ImpLifeState state = input.withCadence(new ImpLifeState.Cadence(
            input.cadence().nextDecisionAt(),
            input.cadence().nextOwnerAt(),
            ImpLifeRules.saturatingAdd(now, ImpLifeRules.TARGET_DISCOVERY_TICKS),
            input.cadence().nextCuriosityAt(),
            input.cadence().nextNavigationAt(),
            input.cadence().nextFeedbackAt()
        ));
        imp.lifeCounters().observationScans++;
        final List<Player> candidates = level.getEntitiesOfClass(
            Player.class,
            imp.getBoundingBox().inflate(ImpLifeRules.TARGET_DISCOVERY_RADIUS),
            player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
        );
        candidates.stream()
            .filter(player -> imp.distanceTo(player) <= ImpLifeRules.TARGET_DISCOVERY_RADIUS)
            .sorted(Comparator.<Player>comparingDouble(imp::distanceToSqr)
                .thenComparing(Entity::getUUID, Comparator.comparing(UUID::toString)))
            .limit(ImpLifeRules.TARGET_RETENTION)
            .findFirst()
            .ifPresent(player -> {
                imp.lifeCounters().lineOfSightChecks++;
                if (imp.getSensing().hasLineOfSight(player) && imp.canAttack(player)) {
                    imp.setTarget(player);
                }
            });
        return state;
    }

    static ImpLifeState dutyBand(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(imp);
        if (ownerId.isEmpty()) {
            if (dutyClaims(input.action())) {
                return input.withAction(Action.NONE).withDestination(Optional.empty());
            }
            return input;
        }
        if (input.action() == Action.SCOUT_OUT) {
            return scoutOut(imp, level, input, now);
        }
        if (input.action() == Action.SCOUT_RETURN) {
            return scoutReturn(imp, level, input, now);
        }
        final LivingEntity owner = resolveOwner(level, ownerId.orElseThrow());
        final Duty duty = input.steadyDuty().orElse(ImpLifeRules.defaultDuty());
        return duty == Duty.WATCH
            ? watch(imp, level, input, now)
            : follow(imp, level, input, owner, now);
    }

    private static ImpLifeState follow(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final LivingEntity owner,
        final long now
    ) {
        ImpLifeState state = input;
        if (owner == null) {
            return state.action() == Action.FOLLOW ? state.withAction(Action.NONE) : state;
        }
        final double distance = imp.distanceTo(owner);
        switch (followBand(distance)) {
            case ORBIT -> {
                if (state.action() == Action.FOLLOW) {
                    state = state.withAction(Action.NONE);
                }
            }
            case PATH -> {
                if (navigationReady(state, now)) {
                    imp.lifeCounters().navigationRequests++;
                    imp.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), NAVIGATION_SPEED);
                    state = bumpNavigation(state, now);
                }
                state = state.withAction(Action.FOLLOW);
            }
            case RECOVER -> {
                final Optional<BlockPos> recovery = followRecoveryPoint(imp, level, owner);
                if (recovery.isPresent()) {
                    final BlockPos point = recovery.orElseThrow();
                    imp.teleportTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5);
                    imp.getNavigation().stop();
                    state = state.withAction(Action.NONE);
                } else if (navigationReady(state, now)) {
                    imp.lifeCounters().navigationRequests++;
                    final boolean accepted = navigateTo(
                        imp, owner.getX(), owner.getY(), owner.getZ());
                    state = bumpNavigation(state, now);
                    if (!accepted) {
                        state = recordRouteFailure(imp, state, now);
                    }
                    state = state.withAction(Action.FOLLOW);
                }
            }
        }
        return state;
    }

    static Optional<BlockPos> followRecoveryPoint(
        final ImpEntity imp,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        final int[] readBudget = {ImpLifeRules.WAYPOINT_READ_BUDGET};
        final BlockPos base = owner.blockPosition();
        for (final BlockPos offset : followRecoveryOffsets(imp.getUUID())) {
            final BlockPos candidate = base.offset(offset);
            if (validateDestination(imp, level, candidate, readBudget) == DestinationCheck.OK) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static ImpLifeState watch(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final Optional<ImpLifeState.Anchor> anchor = state.anchor()
            .filter(row -> row.dimension().equals(level.dimension().identifier().toString()));
        if (anchor.isEmpty()) {
            return state.action() == Action.WATCH ? state.withAction(Action.NONE) : state;
        }
        final BlockPos point = anchor.orElseThrow().position();
        if (!level.hasChunkAt(point)) {
            return state.action() == Action.WATCH ? state.withAction(Action.NONE) : state;
        }
        final double distance = Math.sqrt(imp.blockPosition().distSqr(point));
        if (watchWithinEnvelope(distance)) {
            return state.action() == Action.WATCH ? state.withAction(Action.NONE) : state;
        }
        if (distance > ImpLifeRules.WATCH_ENVELOPE_MAX) {
            if (navigationReady(state, now)) {
                imp.lifeCounters().navigationRequests++;
                final boolean accepted = navigateTo(
                    imp, point.getX() + 0.5, point.getY() + 2.0, point.getZ() + 0.5);
                state = bumpNavigation(state, now);
                if (!accepted) {
                    state = recordRouteFailure(imp, state, now);
                }
            }
            return state.withAction(Action.WATCH);
        }
        if (navigationReady(state, now)) {
            Vec3 outward = imp.position().subtract(Vec3.atCenterOf(point)).multiply(1.0, 0.0, 1.0);
            if (outward.lengthSqr() < 1.0E-4) {
                outward = awayVector(imp);
            }
            final Vec3 envelope = Vec3.atCenterOf(point)
                .add(outward.normalize().scale(ImpLifeRules.WATCH_ENVELOPE_MIN + 1.0))
                .add(0.0, 2.0, 0.0);
            imp.lifeCounters().navigationRequests++;
            navigateTo(imp, envelope.x, envelope.y, envelope.z);
            state = bumpNavigation(state, now);
        }
        return state.withAction(Action.WATCH);
    }

    private static ImpLifeState scoutOut(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final Optional<ImpLifeState.Anchor> anchor = state.anchor();
        if (anchor.isEmpty()) {
            return state.withAction(Action.NONE).withDestination(Optional.empty());
        }
        if (now - state.actionStartedAt() >= ImpLifeRules.SCOUT_OUTBOUND_TICKS
            || ImpLifeRules.scoutTimedOut(state.actionStartedAt(), now)
            || ImpLifeRules.scoutBudgetExhausted(imp.scoutChargedReads())
            || state.scoutLeg() >= ImpLifeRules.SCOUT_LEGS) {
            return beginScoutReturn(state, anchor.orElseThrow(), now);
        }
        final BlockPos anchorPos = anchor.orElseThrow().position();
        if (state.destination().isEmpty()) {
            final int[] readBudget = {Math.min(
                ImpLifeRules.WAYPOINT_READ_BUDGET,
                ImpLifeRules.SCOUT_TOTAL_READ_BUDGET - imp.scoutChargedReads()
            )};
            final int before = readBudget[0];
            Optional<BlockPos> chosen = Optional.empty();
            for (final BlockPos waypoint : ImpLifeRules.legWaypoints(anchorPos, state.scoutLeg(), imp.getUUID())) {
                if (validateDestination(imp, level, waypoint, readBudget) == DestinationCheck.OK) {
                    chosen = Optional.of(waypoint);
                    break;
                }
            }
            imp.chargeScoutReads(before - readBudget[0]);
            if (chosen.isEmpty()) {
                state = recordRouteFailure(imp, state, now);
                return advanceScoutLeg(imp, level, state, anchorPos, now);
            }
            state = state.withDestination(Optional.of(new ImpLifeState.Anchor(
                anchor.orElseThrow().dimension(), chosen.orElseThrow())));
        }
        final BlockPos destination = state.destination().orElseThrow().position();
        if (imp.blockPosition().distSqr(destination) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {
            return advanceScoutLeg(imp, level, state, anchorPos, now);
        }
        if (navigationReady(state, now)) {
            imp.lifeCounters().navigationRequests++;
            final boolean accepted = navigateTo(
                imp, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
            state = bumpNavigation(state, now);
            if (!accepted) {
                state = recordRouteFailure(imp, state, now).withDestination(Optional.empty());
            }
        }
        return state;
    }

    private static ImpLifeState advanceScoutLeg(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final BlockPos anchorPos,
        final long now
    ) {
        ImpLifeState state = observeScoutLeg(imp, level, input, now);
        imp.lifeCounters().scoutLegsCompleted++;
        state = state.withScout(nextScoutLeg(state.scoutLeg()), state.reportDelivered())
            .withDestination(Optional.empty());
        if (state.scoutLeg() >= ImpLifeRules.SCOUT_LEGS) {
            return beginScoutReturn(state, state.anchor().orElseThrow(), now);
        }
        return state;
    }

    private static ImpLifeState beginScoutReturn(
        final ImpLifeState state,
        final ImpLifeState.Anchor anchor,
        final long now
    ) {
        return state.withAction(Action.SCOUT_RETURN)
            .withDestination(Optional.of(anchor))
            .withScout(ImpLifeRules.SCOUT_LEGS, state.reportDelivered())
            .withActionWindow(now, ImpLifeRules.saturatingAdd(now, ImpLifeRules.SCOUT_RETURN_TICKS));
    }

    static ImpLifeState observeScoutLeg(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        imp.lifeCounters().observationScans++;
        final List<LivingEntity> hostiles = level.getEntitiesOfClass(
            LivingEntity.class,
            imp.getBoundingBox().inflate(ImpLifeRules.SCOUT_OBSERVATION_RADIUS),
            candidate -> candidate != imp && candidate.isAlive()
                && candidate instanceof net.minecraft.world.entity.monster.Monster
                && !(candidate instanceof ArcaneCreature)
        );
        int lineOfSightBudget = ImpLifeRules.SCOUT_LINE_OF_SIGHT_CHECKS;
        int retained = 0;
        for (final LivingEntity hostile : hostiles) {
            if (retained >= ImpLifeRules.SCOUT_RETAINED_OBSERVATIONS || lineOfSightBudget <= 0) {
                break;
            }
            lineOfSightBudget--;
            imp.lifeCounters().lineOfSightChecks++;
            if (!imp.getSensing().hasLineOfSight(hostile)) {
                continue;
            }
            retained++;
            state = state.withObservations(ImpLifeRules.recordObservation(
                state.observations(),
                new Observation(
                    ObservationType.HOSTILE,
                    hostile.blockPosition().asLong(),
                    Optional.of(hostile.getUUID()),
                    now,
                    now,
                    1_000,
                    ImpLifeRules.saturatingAdd(now, ImpLifeRules.OBSERVATION_EXPIRY_TICKS)
                ),
                now
            ));
        }
        return state;
    }

    private static ImpLifeState scoutReturn(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final Optional<ImpLifeState.Anchor> anchor = state.anchor();
        if (anchor.isEmpty()) {
            return state.withAction(Action.NONE).withDestination(Optional.empty());
        }
        if (now >= state.actionTimeoutAt()) {
            imp.getNavigation().stop();
            return state.withAction(Action.NONE)
                .withDestination(Optional.empty())
                .withDuties(Optional.of(state.priorDuty().orElse(ImpLifeRules.defaultDuty())),
                    Optional.empty())
                .withScout(0, state.reportDelivered());
        }
        final BlockPos point = anchor.orElseThrow().position();
        final double distance = Math.sqrt(imp.blockPosition().distSqr(point));
        final LivingEntity owner = CreatureBehaviorState.owner(imp)
            .map(id -> resolveOwner(level, id))
            .orElse(null);
        final boolean nearOwner = owner != null
            && imp.distanceTo(owner) <= ImpLifeRules.SCOUT_RETURN_COMPLETION_DISTANCE;
        if (distance <= ImpLifeRules.SCOUT_RETURN_COMPLETION_DISTANCE || nearOwner) {
            if (ImpLifeRules.mayDeliverReport(state.reportDelivered(), owner != null, true)) {
                deliverReport(imp, level, state, now);
                imp.lifeCounters().reportsDelivered++;
                state = state.withScout(state.scoutLeg(), true);
            }
            if (state.reportDelivered()) {
                return state.withAction(Action.NONE)
                    .withDestination(Optional.empty())
                    .withDuties(Optional.of(state.priorDuty().orElse(ImpLifeRules.defaultDuty())),
                        Optional.empty())
                    .withScout(0, true);
            }
            return state;
        }
        if (state.deadlines().recoveryUntil() > now) {
            return state;
        }
        if (navigationReady(state, now)) {
            imp.lifeCounters().navigationRequests++;
            final boolean accepted = navigateTo(
                imp, point.getX() + 0.5, point.getY() + 1.0, point.getZ() + 0.5);
            state = bumpNavigation(state, now);
            if (!accepted) {
                state = recordRouteFailure(imp, state, now);
            }
        }
        return state;
    }

    private static void deliverReport(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState state,
        final long now
    ) {
        final int hostiles = ImpLifeRules.reportedHostileCount(state.observations(), now);
        level.playSound(null, imp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.NEUTRAL, 0.4F, hostiles > 0 ? 0.8F : 1.25F);
        level.sendParticles(
            hostiles > 0 ? ParticleTypes.FLAME : ParticleTypes.ENCHANT,
            imp.getX(), imp.getEyeY() + 0.25, imp.getZ(),
            Math.max(1, hostiles), 0.2, 0.2, 0.2, 0.0
        );
    }

    static ImpLifeState orderBand(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final Optional<InfernalOrder> order = state.order();
        if (order.isEmpty() || CreatureBehaviorState.owner(imp).isPresent()) {
            return state;
        }
        final InfernalOrder active = order.orElseThrow();
        final Entity issuer = level.getEntity(active.issuerId());
        final boolean issuerInvalid = !(issuer instanceof LivingEntity living)
            || !living.isAlive() || living.level() != level;
        if (!active.valid(now) || ImpLifeRules.orderCleared(
            issuerInvalid, false, false, false, state.routeFailures())) {
            imp.lifeCounters().ordersCleared++;
            return state.withOrder(Optional.empty())
                .withAction(state.action() == Action.NPC_ORDER ? Action.NONE : state.action())
                .withDestination(state.action() == Action.NPC_ORDER ? Optional.empty() : state.destination());
        }
        switch (active.action()) {
            case HARASS -> active.targetId()
                .map(level::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(candidate -> candidate.isAlive() && imp.canAttack(candidate))
                .ifPresent(imp::setTarget);
            case SCOUT, REPORT, WATCH -> {
                final LivingEntity living = (LivingEntity) issuer;
                if (imp.distanceTo(living) > ImpLifeRules.WATCH_ENVELOPE_MAX && navigationReady(state, now)) {
                    imp.lifeCounters().navigationRequests++;
                    imp.getNavigation().moveTo(living.getX(), living.getY() + 1.0, living.getZ(), NAVIGATION_SPEED);
                    state = bumpNavigation(state, now);
                }
            }
        }
        return state.withAction(Action.NPC_ORDER);
    }

    static boolean offerOrder(
        final ImpEntity imp,
        final ServerLevel level,
        final InfernalOrder order,
        final int issuerTotalSubordinates,
        final int issuerImpSubordinates,
        final boolean delegatedByAnImp
    ) {
        final long now = level.getGameTime();
        final Entity issuer = level.getEntity(order.issuerId());
        final boolean issuerValid = issuer instanceof LivingEntity living
            && living.isAlive() && living.level() == level;
        final ImpLifeState state = imp.lifeState();
        final OrderFacts facts = new OrderFacts(
            imp.isAlive(),
            level.hasChunkAt(imp.blockPosition()),
            CreatureBehaviorState.owner(imp).isPresent(),
            issuerValid,
            issuerValid && imp.distanceTo((LivingEntity) issuer) <= ORDER_INTAKE_RADIUS,
            delegatedByAnImp,
            state.order().filter(existing -> existing.valid(now)
                && existing.epoch() >= order.epoch()).isPresent(),
            issuerTotalSubordinates,
            issuerImpSubordinates
        );
        if (!ImpLifeRules.acceptsOrder(order.rank(), facts)) {
            return false;
        }
        imp.lifeCounters().ordersAccepted++;
        imp.setLifeState(state.withOrder(Optional.of(new InfernalOrder(
            order.issuerId(),
            order.rank(),
            order.groupId(),
            order.epoch(),
            order.action(),
            order.targetId(),
            now,
            ImpLifeRules.orderExpiry(now, order.expiresAt())
        ))));
        return true;
    }

    /**
     * F07 -> F08 order-intake binding. F07's approved design keeps the hierarchy's own writes away
     * from Imps ("F07 performs no write to ... Imp ... state"; Imps are ineligible roster members)
     * and defers inbound eligibility to "their own approved plans". F08's approved design supplies
     * that plan: an alive, loaded, player-unbound Imp inside a valid Archfiend or Regent's bounded
     * group query accepts a one-hop, non-delegable order - Archfiend squad cap four total
     * subordinates with at most two Imp slots, Regent court cap seven total with at most two Imp
     * slots, expiry clamped to at most 600 ticks, and only SCOUT/REPORT/WATCH/HARASS verbs.
     *
     * <p>The translation reads only F07's public surface and mirrors the leader's live command
     * doctrine (the same precedence F07 uses when ordering its roster: withdraw, focus challenger,
     * screen, hold). A leader focused on a live challenger yields HARASS carrying that exact target;
     * F08's order band re-filters it through the relationship matrix before any attack. A screening
     * or quietly holding leader yields WATCH, keeping the Imp inside the issuer's envelope. A
     * withdrawing or dissolving leader takes no new Imp subordinates. Total-subordinate pressure is
     * the leader's valid F07 roster plus already-ordered Imps, so the shared four/seven pools hold
     * across both families. Everything funnels through {@link #offerOrder}, so alive/loaded/unbound,
     * dimension, range, delegation, epoch, cap, and expiry checks stay in the one intake seam.
     */
    static boolean offerInfernalHierarchyOrder(
        final ImpEntity imp,
        final ServerLevel level,
        final long now
    ) {
        final List<InfernalHierarchyEntity> leaders = level.getEntitiesOfClass(
            InfernalHierarchyEntity.class,
            imp.getBoundingBox().inflate(ORDER_INTAKE_RADIUS),
            candidate -> candidate.isAlive()
                && candidate.hierarchyRank() != InfernalHierarchyRules.Rank.DEMON
        );
        leaders.sort(Comparator.comparingDouble(
            (InfernalHierarchyEntity candidate) -> imp.distanceToSqr(candidate)
        ));
        for (final InfernalHierarchyEntity leader : leaders) {
            final Optional<InfernalOrder> translated = translateLeaderDoctrine(leader, level, now);
            if (translated.isEmpty()) {
                continue;
            }
            final int impSubordinates = orderedImpCount(level, leader, now, imp);
            final int totalSubordinates = impSubordinates + (int) leader.hierarchyState().roster()
                .stream()
                .filter(member -> member.valid(now))
                .count();
            if (offerOrder(imp, level, translated.orElseThrow(),
                totalSubordinates, impSubordinates, false)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<InfernalOrder> translateLeaderDoctrine(
        final InfernalHierarchyEntity leader,
        final ServerLevel level,
        final long now
    ) {
        final ImpLifeRules.OrderRank rank = switch (leader.hierarchyRank()) {
            case EMBERHORN_ARCHFIEND -> ImpLifeRules.OrderRank.ARCHFIEND;
            case ABYSSAL_REGENT -> ImpLifeRules.OrderRank.REGENT;
            case DEMON -> null;
        };
        if (rank == null) {
            return Optional.empty();
        }
        final InfernalHierarchyState leaderState = leader.hierarchyState();
        if (InfernalHierarchyRules.cancelsExecution(leaderState.intent())) {
            return Optional.empty();
        }
        final Optional<UUID> challenger = leaderState.challengerId()
            .filter(id -> leaderState.challengerExpiresAt() > now)
            .filter(id -> level.getEntity(id) instanceof LivingEntity living && living.isAlive());
        return Optional.of(new InfernalOrder(
            leader.getUUID(),
            rank,
            leader.getUUID(),
            leaderState.orderEpoch(),
            challenger.isPresent()
                ? ImpLifeRules.OrderAction.HARASS
                : ImpLifeRules.OrderAction.WATCH,
            challenger,
            now,
            ImpLifeRules.saturatingAdd(
                now, InfernalHierarchyRules.orderLifetimeTicks(leader.hierarchyRank())
            )
        ));
    }

    private static int orderedImpCount(
        final ServerLevel level,
        final InfernalHierarchyEntity leader,
        final long now,
        final ImpEntity offering
    ) {
        return level.getEntitiesOfClass(
            ImpEntity.class,
            leader.getBoundingBox().inflate(ORDER_INTAKE_RADIUS),
            candidate -> candidate != offering && candidate.isAlive()
                && candidate.lifeState().order()
                    .filter(order -> order.valid(now)
                        && order.issuerId().equals(leader.getUUID()))
                    .isPresent()
        ).size();
    }

    private static ImpLifeState expireOrder(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        if (input.order().filter(order -> !order.valid(now)).isPresent()) {
            imp.lifeCounters().ordersCleared++;
            return input.withOrder(Optional.empty())
                .withAction(input.action() == Action.NPC_ORDER ? Action.NONE : input.action())
                .withDestination(input.action() == Action.NPC_ORDER
                    ? Optional.empty() : input.destination());
        }
        return input;
    }

    private static ImpLifeState committedBand(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        if (input.action() != Action.INSPECT && input.action() != Action.PERCH) {
            return input;
        }
        if (input.action() == Action.PERCH) {
            if (now >= input.actionTimeoutAt()) {
                return input.withAction(Action.NONE);
            }
            return input;
        }
        ImpLifeState state = input;
        final Optional<ImpLifeState.Anchor> destination = state.destination();
        if (destination.isPresent()) {
            final BlockPos stimulus = destination.orElseThrow().position();
            if (now >= state.actionTimeoutAt()) {
                imp.getNavigation().stop();
                return cancelInspect(state, now);
            }
            if (!level.hasChunkAt(stimulus) || !stillStimulating(imp, level, stimulus)) {
                return cancelInspect(state, now);
            }
            if (imp.progressStalled(now, stimulus)) {
                state = recordRouteFailure(imp, state, now);
                if (state.routeFailures() == 0) {
                    return cancelInspect(state, now);
                }
            }
            final double distance = Math.sqrt(imp.blockPosition().distSqr(stimulus));
            if (distance <= ImpLifeRules.INSPECT_APPROACH_MAX) {
                imp.getNavigation().stop();
                imp.getLookControl().setLookAt(Vec3.atCenterOf(stimulus));
                level.playSound(null, imp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.NEUTRAL, 0.4F, 1.25F);
                level.sendParticles(ParticleTypes.ENCHANT,
                    imp.getX(), imp.getEyeY() + 0.5, imp.getZ(),
                    ImpLifeRules.INSPECT_PARTICLE_CAP, 0.3, 0.3, 0.3, 0.0);
                final int dwell = ImpLifeRules.INSPECT_MIN_TICKS + ImpLifeRules.stableOffset(
                    imp.getUUID(), ImpLifeRules.INSPECT_MAX_TICKS - ImpLifeRules.INSPECT_MIN_TICKS + 1);
                final int[] perchBudget = {ImpLifeRules.WAYPOINT_READ_BUDGET};
                final boolean perched = validatePerch(
                    imp, level, imp.blockPosition(), perchBudget) == DestinationCheck.OK;
                return state.withAction(perched ? Action.PERCH : Action.INSPECT)
                    .withDestination(Optional.empty())
                    .withActionWindow(now, ImpLifeRules.saturatingAdd(now, dwell));
            }
            if (navigationReady(state, now)) {
                imp.lifeCounters().navigationRequests++;
                final boolean accepted = navigateTo(
                    imp, stimulus.getX() + 0.5, stimulus.getY() + 1.0, stimulus.getZ() + 0.5);
                state = bumpNavigation(state, now);
                if (!accepted) {
                    state = recordRouteFailure(imp, state, now);
                    if (state.routeFailures() == 0) {
                        return cancelInspect(state, now);
                    }
                }
            }
            return state;
        }
        if (now >= state.actionTimeoutAt()) {
            return state.withAction(Action.NONE);
        }
        return state;
    }

    private static ImpLifeState cancelInspect(final ImpLifeState state, final long now) {
        return state.withAction(Action.NONE)
            .withDestination(Optional.empty())
            .withDeadlines(new ImpLifeState.Deadlines(
                state.deadlines().recoveryUntil(),
                state.deadlines().meleeRecoveryUntil(),
                ImpLifeRules.saturatingAdd(now, ImpLifeRules.CURIOSITY_BACKOFF_TICKS),
                state.deadlines().windupStartedAt(),
                state.deadlines().lastShotAt()
            ));
    }

    private static boolean stillStimulating(final ImpEntity imp, final ServerLevel level, final BlockPos pos) {
        imp.lifeCounters().blockReads++;
        final BlockState state = level.getBlockState(pos);
        return state.is(AmbientActivityTags.SHINY_STORAGE_BLOCKS)
            || state.is(BlockTags.CAMPFIRES)
            || state.is(BlockTags.FIRE);
    }

    static ImpLifeState autonomousBand(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        final boolean combatEvidence = imp.getTarget() != null
            || state.threat().filter(threat -> threat.valid(now)).isPresent();
        if (!ImpLifeRules.curiosityAllowed(
            false,
            combatEvidence,
            dutyClaims(state.action()),
            state.order().filter(order -> order.valid(now)).isPresent(),
            state.deadlines().curiosityBackoffUntil(),
            state.cadence().nextCuriosityAt(),
            now
        )) {
            return state;
        }
        state = state.withCadence(new ImpLifeState.Cadence(
            state.cadence().nextDecisionAt(),
            state.cadence().nextOwnerAt(),
            state.cadence().nextDiscoveryAt(),
            ImpLifeRules.saturatingAdd(now, ImpLifeRules.CURIOSITY_INTERVAL_TICKS),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        state = curiosityScan(imp, level, state, now);
        final Optional<Observation> chosen = state.observations().stream()
            .filter(row -> row.valid(now) && row.type() != ObservationType.HOSTILE)
            .min(Comparator
                .comparingDouble((Observation row) ->
                    imp.blockPosition().distSqr(BlockPos.of(row.packedPosition())))
                .thenComparingLong(Observation::packedPosition));
        if (chosen.isEmpty()) {
            return state;
        }
        final BlockPos stimulus = BlockPos.of(chosen.orElseThrow().packedPosition());
        return state.withAction(Action.INSPECT)
            .withDestination(Optional.of(new ImpLifeState.Anchor(
                level.dimension().identifier().toString(), stimulus)))
            .withActionWindow(now, ImpLifeRules.saturatingAdd(now, 400L));
    }

    static ImpLifeState curiosityScan(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        imp.lifeCounters().curiosityScans++;
        int readBudget = ImpLifeRules.CURIOSITY_READ_BUDGET;
        final BlockPos origin = imp.blockPosition();
        for (final BlockPos offset : curiositySampleOffsets(imp.getUUID())) {
            if (readBudget <= 0) {
                break;
            }
            final BlockPos pos = origin.offset(offset);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            readBudget--;
            imp.lifeCounters().blockReads++;
            final BlockState blockState = level.getBlockState(pos);
            final Optional<ObservationType> type = stimulusType(blockState);
            if (type.isEmpty()) {
                continue;
            }
            state = state.withObservations(ImpLifeRules.recordObservation(
                state.observations(),
                new Observation(
                    type.orElseThrow(),
                    pos.asLong(),
                    Optional.empty(),
                    now,
                    now,
                    800,
                    ImpLifeRules.saturatingAdd(now, ImpLifeRules.OBSERVATION_EXPIRY_TICKS)
                ),
                now
            ));
        }
        return state;
    }

    private static Optional<ObservationType> stimulusType(final BlockState state) {
        if (state.is(AmbientActivityTags.SHINY_STORAGE_BLOCKS)) {
            return Optional.of(ObservationType.SHINY);
        }
        if (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE)) {
            return Optional.of(ObservationType.HEAT);
        }
        return Optional.empty();
    }

    static DestinationCheck validateDestination(
        final ImpEntity imp,
        final ServerLevel level,
        final BlockPos pos,
        final int[] readBudget
    ) {
        if (!ImpLifeRules.validWorldPosition(pos)) {
            return DestinationCheck.OUTSIDE_BORDER;
        }
        for (final BlockPos corner : List.of(
            pos.offset(-1, 0, -1), pos.offset(1, 0, -1), pos.offset(-1, 0, 1), pos.offset(1, 0, 1))) {
            if (!level.hasChunkAt(corner)) {
                return DestinationCheck.UNLOADED;
            }
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return DestinationCheck.OUTSIDE_BORDER;
        }
        if (readBudget[0] < 3) {
            return DestinationCheck.BUDGET_EXHAUSTED;
        }
        readBudget[0] -= 3;
        imp.lifeCounters().blockReads += 3;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return DestinationCheck.HAZARD;
        }
        final Vec3 offset = Vec3.atBottomCenterOf(pos).subtract(imp.position());
        if (!level.noCollision(imp, imp.getBoundingBox().move(offset))) {
            return DestinationCheck.COLLISION;
        }
        return DestinationCheck.OK;
    }

    static DestinationCheck validatePerch(
        final ImpEntity imp,
        final ServerLevel level,
        final BlockPos pos,
        final int[] readBudget
    ) {
        final DestinationCheck base = validateDestination(imp, level, pos, readBudget);
        if (base != DestinationCheck.OK) {
            return base;
        }
        if (readBudget[0] < 1) {
            return DestinationCheck.BUDGET_EXHAUSTED;
        }
        readBudget[0]--;
        imp.lifeCounters().blockReads++;
        return level.getBlockState(pos.below()).isFaceSturdy(
            level, pos.below(), net.minecraft.core.Direction.UP)
            ? DestinationCheck.OK
            : DestinationCheck.NO_SUPPORT;
    }

    static ImpLifeState recordRouteFailure(final ImpEntity imp, final ImpLifeState input, final long now) {
        final int failures = ImpLifeRules.nextRouteFailures(input.routeFailures());
        ImpLifeState state = input.withRouteFailures(failures);
        if (failures >= ImpLifeRules.MAX_ROUTE_FAILURES) {
            imp.getNavigation().stop();
            state = state.withDestination(Optional.empty())
                .nextEpoch()
                .withDeadlines(new ImpLifeState.Deadlines(
                    ImpLifeRules.routeBackoffUntil(failures, now),
                    state.deadlines().meleeRecoveryUntil(),
                    state.deadlines().curiosityBackoffUntil(),
                    state.deadlines().windupStartedAt(),
                    state.deadlines().lastShotAt()
                ))
                .withRouteFailures(0);
        }
        return state;
    }

    private static ImpLifeState navigateRetreat(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final long now
    ) {
        ImpLifeState state = input;
        if (!navigationReady(state, now)) {
            return state;
        }
        final Optional<BlockPos> preferred = state.anchor()
            .filter(anchor -> anchor.dimension().equals(level.dimension().identifier().toString()))
            .map(ImpLifeState.Anchor::position)
            .filter(level::hasChunkAt);
        imp.lifeCounters().navigationRequests++;
        if (preferred.isPresent()) {
            final BlockPos point = preferred.orElseThrow();
            imp.getNavigation().moveTo(point.getX() + 0.5, point.getY() + 1.0, point.getZ() + 0.5,
                NAVIGATION_SPEED);
        } else {
            final Vec3 away = imp.position().add(awayVector(imp).scale(8.0));
            imp.getNavigation().moveTo(away.x, away.y + 2.0, away.z, NAVIGATION_SPEED);
        }
        return bumpNavigation(state, now);
    }

    private static boolean navigateAwayFrom(
        final ImpEntity imp,
        final ServerLevel level,
        final LivingEntity threat,
        final long now,
        final ImpLifeState state
    ) {
        if (!navigationReady(state, now)) {
            return false;
        }
        Vec3 away = imp.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = awayVector(imp);
        }
        final Vec3 destination = imp.position().add(away.normalize().scale(8.0)).add(0.0, 2.0, 0.0);
        imp.lifeCounters().navigationRequests++;
        return imp.getNavigation().moveTo(destination.x, destination.y, destination.z, NAVIGATION_SPEED);
    }

    private static Vec3 awayVector(final ImpEntity imp) {
        return new Vec3((imp.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0 : -1.0, 0.0, 1.0);
    }

    private static ImpLifeState navigateLane(
        final ImpEntity imp,
        final ServerLevel level,
        final ImpLifeState input,
        final LivingEntity target,
        final long now
    ) {
        ImpLifeState state = input;
        if (!navigationReady(state, now) || state.deadlines().recoveryUntil() > now) {
            return state;
        }
        final String dimension = level.dimension().identifier().toString();
        final long laneHeldUntil = state.actionTimeoutAt();
        final Optional<BlockPos> heldLane = state.destination()
            .filter(anchor -> anchor.dimension().equals(dimension))
            .filter(anchor -> now < laneHeldUntil)
            .map(ImpLifeState.Anchor::position);
        if (heldLane.isPresent()) {
            final BlockPos lane = heldLane.orElseThrow();
            final int[] revalidation = {ImpLifeRules.LANE_READ_BUDGET};
            if (validateDestination(imp, level, lane, revalidation) == DestinationCheck.OK) {
                imp.lifeCounters().navigationRequests++;
                final boolean accepted = navigateTo(
                    imp, lane.getX() + 0.5, lane.getY(), lane.getZ() + 0.5);
                state = bumpNavigation(state, now);
                if (!accepted) {
                    state = recordRouteFailure(imp, state, now).withDestination(Optional.empty());
                }
                return state;
            }
            state = state.withDestination(Optional.empty());
        }
        imp.lifeCounters().laneSearches++;
        final int[] readBudget = {ImpLifeRules.LANE_READ_BUDGET};
        int lineOfSightBudget = ImpLifeRules.LANE_LINE_OF_SIGHT_CHECKS;
        final BlockPos base = target.blockPosition();
        for (final BlockPos offset : laneOffsets(imp.getUUID())) {
            final BlockPos candidate = base.offset(offset);
            if (validateDestination(imp, level, candidate, readBudget) != DestinationCheck.OK) {
                continue;
            }
            if (lineOfSightBudget > 0) {
                lineOfSightBudget--;
                imp.lifeCounters().lineOfSightChecks++;
            }
            imp.lifeCounters().navigationRequests++;
            final boolean accepted = navigateTo(
                imp, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            state = bumpNavigation(state, now);
            if (!accepted) {
                state = recordRouteFailure(imp, state, now);
                return state;
            }
            return state
                .withDestination(Optional.of(new ImpLifeState.Anchor(dimension, candidate)))
                .withActionWindow(now, ImpLifeRules.saturatingAdd(now, ImpLifeRules.LANE_HOLD_TICKS));
        }
        return recordRouteFailure(imp, bumpNavigation(state, now), now);
    }

    private static boolean navigationReady(final ImpLifeState state, final long now) {
        return ImpLifeRules.due(state.cadence().nextNavigationAt(), now);
    }

    private static boolean navigateTo(
        final ImpEntity imp,
        final double x,
        final double y,
        final double z
    ) {
        final boolean accepted = imp.getNavigation().moveTo(x, y, z, NAVIGATION_SPEED);
        final var path = imp.getNavigation().getPath();
        if (!accepted || path == null || !path.canReach()) {
            imp.getNavigation().stop();
            return false;
        }
        return true;
    }

    private static ImpLifeState bumpNavigation(final ImpLifeState state, final long now) {
        return state.withCadence(new ImpLifeState.Cadence(
            state.cadence().nextDecisionAt(),
            state.cadence().nextOwnerAt(),
            state.cadence().nextDiscoveryAt(),
            state.cadence().nextCuriosityAt(),
            ImpLifeRules.saturatingAdd(now, ImpLifeRules.NAVIGATION_INTERVAL_TICKS),
            state.cadence().nextFeedbackAt()
        ));
    }

    static InteractionResult interactDutyCommand(
        final ImpEntity imp,
        final ServerLevel level,
        final Player player
    ) {
        if (!CreatureBehaviorState.isOwnedBy(imp, player.getUUID())) {
            return InteractionResult.PASS;
        }
        final long now = level.getGameTime();
        final ImpLifeState state = imp.lifeState();
        final String dimension = level.dimension().identifier().toString();
        if (player.isShiftKeyDown()) {
            final BlockPos anchorPos = player.blockPosition();
            final boolean anchorValid = level.hasChunkAt(anchorPos)
                && level.getWorldBorder().isWithinBounds(anchorPos)
                && ImpLifeRules.validWorldPosition(anchorPos);
            if (!ImpLifeRules.mayBeginScout(
                player.isAlive(), player.level() == level, anchorValid)) {
                return InteractionResult.FAIL;
            }
            imp.resetScoutReads();
            imp.setLifeState(state
                .withDuties(state.steadyDuty(),
                    Optional.of(state.steadyDuty().orElse(ImpLifeRules.defaultDuty())))
                .withAction(Action.SCOUT_OUT)
                .withAnchor(Optional.of(new ImpLifeState.Anchor(dimension, anchorPos)))
                .withDestination(Optional.empty())
                .withScout(0, false)
                .withActionWindow(now, ImpLifeRules.saturatingAdd(now, ImpLifeRules.SCOUT_TOTAL_TICKS)));
            level.playSound(null, imp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 0.4F, 1.45F);
            return InteractionResult.SUCCESS;
        }
        final Duty toggled = ImpLifeRules.toggledDuty(state.steadyDuty());
        if (toggled == Duty.WATCH) {
            final BlockPos anchorPos = imp.blockPosition();
            final int[] readBudget = {ImpLifeRules.WAYPOINT_READ_BUDGET};
            if (validateDestination(imp, level, anchorPos, readBudget) == DestinationCheck.UNLOADED) {
                return InteractionResult.FAIL;
            }
            imp.setLifeState(state
                .withDuties(Optional.of(Duty.WATCH), Optional.empty())
                .withAnchor(Optional.of(new ImpLifeState.Anchor(dimension, anchorPos)))
                .withAction(Action.NONE)
                .withDestination(Optional.empty()));
            level.playSound(null, imp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 0.4F, 0.8F);
            level.sendParticles(ParticleTypes.SMOKE,
                imp.getX(), imp.getEyeY(), imp.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        } else {
            imp.setLifeState(state
                .withDuties(Optional.of(Duty.FOLLOW), Optional.empty())
                .withAnchor(Optional.empty())
                .withAction(Action.NONE)
                .withDestination(Optional.empty()));
            level.playSound(null, imp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 0.4F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    static boolean mayAttack(final ImpEntity imp, final LivingEntity target) {
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (!target.isAlive() || target.level() != imp.level()) {
            return false;
        }
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(imp);
        if (ownerId.isEmpty()) {
            return true;
        }
        final long now = imp.level().getGameTime();
        if (isFreshDirectAttacker(imp, target)) {
            return true;
        }
        final ImpLifeState state = imp.lifeState();
        if (state.threat().filter(threat -> threat.valid(now)
            && threat.id().equals(target.getUUID())).isPresent()) {
            return true;
        }
        return state.order().filter(order -> order.valid(now)
            && order.targetId().filter(target.getUUID()::equals).isPresent()).isPresent();
    }

    static boolean isFreshDirectAttacker(final ImpEntity imp, final LivingEntity target) {
        return imp.getLastHurtByMob() == target
            && imp.getLastHurtByMobTimestamp() + ImpLifeRules.ATTRIBUTION_FRESHNESS_TICKS >= imp.tickCount;
    }

    static boolean ignoresBoundDamage(final ImpEntity imp, final DamageSource source) {
        if (CreatureBehaviorState.owner(imp).isEmpty()) {
            return false;
        }
        if (FamiliarBondRules.ignoresEnvironmentalDamage(source)) {
            return true;
        }
        return source.getEntity() == null
            && source.getDirectEntity() == null
            && source.is(DamageTypes.GENERIC);
    }

    static Authority effectiveAuthority(final ImpEntity imp, final long now) {
        final Optional<UUID> creatureOwner = CreatureBehaviorState.owner(imp);
        final Optional<UUID> infernalOwner = DataParsing.uuid(
            WarlockeryEntityData.get(imp).getStringOr(InfernalPactEffects.OWNER_KEY, ""));
        final Optional<InfernalOrder> order = imp.lifeState().order()
            .filter(row -> row.valid(now));
        return ImpLifeRules.effectiveAuthority(
            creatureOwner,
            infernalOwner,
            order.filter(row -> row.rank() == ImpLifeRules.OrderRank.REGENT).isPresent(),
            order.filter(row -> row.rank() == ImpLifeRules.OrderRank.ARCHFIEND).isPresent()
        );
    }

    private static ImpLifeState.Deadlines clearWindup(final ImpLifeState.Deadlines deadlines) {
        return new ImpLifeState.Deadlines(
            deadlines.recoveryUntil(),
            deadlines.meleeRecoveryUntil(),
            deadlines.curiosityBackoffUntil(),
            0L,
            deadlines.lastShotAt()
        );
    }

    private static LivingEntity resolveOwner(final ServerLevel level, final UUID ownerId) {
        return level.getEntity(ownerId) instanceof LivingEntity living
            && living.isAlive() && living.level() == level
            ? living
            : null;
    }
}
