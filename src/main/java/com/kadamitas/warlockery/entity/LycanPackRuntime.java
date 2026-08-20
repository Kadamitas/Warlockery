package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import com.kadamitas.warlockery.entity.LycanPackRules.CarrionFacts;
import com.kadamitas.warlockery.entity.LycanPackRules.CoordinatorCandidate;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntPhase;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntRole;
import com.kadamitas.warlockery.entity.LycanPackRules.PreyFacts;
import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.world.VillageAssaultData;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class LycanPackRuntime {
    private static final String WOLF_TRAP_MARKER = "WarlockeryWolfTrap";
    private static final long TRANSIENT_ATTACKER_TICKS = 200L;
    private static final long TARGET_LEASE_TICKS = 200L;
    private static final Map<Mob, Boolean> MOON_CACHE = new WeakHashMap<>();

    private LycanPackRuntime() {
    }

    public static boolean exactWerewolf(final WerewolfEntity mob) {
        return mob.variant() == Variant.WEREWOLF;
    }

    public static void tick(final WerewolfEntity mob, final ServerLevel level) {
        final long dayTime = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        tickForObservation(mob, level, dayTime, sampleMoonOnCadence(mob, level));
    }

    private static boolean sampleMoonOnCadence(final WerewolfEntity mob, final ServerLevel level) {
        final long now = level.getGameTime();
        final LycanPackState state = mob.packState();
        if (now >= state.cadence().nextMoonSampleAt()) {
            MOON_CACHE.put(mob, level.environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_PHASE, mob.position()) == MoonPhase.FULL_MOON);
            mob.setPackState(state.withCadence(new LycanPackState.Cadence(
                state.cadence().nextDecisionAt(), state.cadence().nextPerceptionAt(),
                state.cadence().nextPlanAt(), state.cadence().nextFeedbackAt(),
                state.cadence().lastNavigationAt(),
                LycanPackRules.saturatingAdd(now, LycanPackRules.MOON_SAMPLE_INTERVAL_TICKS)
                    - LycanPackRules.stableOffset(mob.getUUID(), LycanPackRules.MOON_SAMPLE_INTERVAL_TICKS) / 4L,
                state.cadence().routeFailures(), state.cadence().retryAfter()
            )));
        }
        return MOON_CACHE.getOrDefault(mob, false);
    }

    public static void tickForObservation(
        final WerewolfEntity mob,
        final ServerLevel level,
        final long dayTime,
        final boolean fullMoon
    ) {
        final long now = level.getGameTime();
        if (!mob.isAlive() || mob.isRemoved() || mob.isPassenger()) return;
        LycanPackState state = mob.packState().reconcile(now);
        mob.setPackState(state);
        if (mob.getPersistentData().contains(WOLF_TRAP_MARKER)) return;
        if (VillageAssaultRuntime.isAssaultRaider(mob)) return;
        if (HazardEscapeRuntime.tick(mob, level, CreatureKind.WEREWOLF)) {
            mob.packCounters().hazardInterruptions++;
            if (state.action().kind() != ActionKind.NONE) {
                mob.setPackState(state.cancelAction("hazard", now));
            }
            return;
        }
        state = revalidateTarget(mob, level, mob.packState(), now);
        mob.setPackState(state);
        if (now < state.cadence().nextDecisionAt()) return;
        mob.packCounters().decisions++;
        state = withNextDecision(state, now);
        final LivingEntity aggressor = admittedAggressor(mob, level, now);
        if (aggressor != null) {
            state = respondToAggressor(mob, level, state, aggressor, now);
            mob.setPackState(state);
            return;
        }
        final float healthFraction = mob.getHealth() / mob.getMaxHealth();
        if (LycanPackRules.forcedRetreat(
            state.needs().fear(), healthFraction, false, state.cadence().routeFailures()
        )) {
            state = retreat(mob, level, state, now);
            mob.setPackState(state);
            return;
        }
        if (state.action().kind() != ActionKind.NONE) {
            state = continueAction(mob, level, state, now);
            mob.setPackState(state);
            return;
        }
        state = mob.variant() == Variant.WEREWOLF
            ? tickWerewolfAutonomy(mob, level, state, dayTime, fullMoon, now)
            : tickFeralAutonomy(mob, level, state, dayTime, now);
        state = emitFeedback(mob, level, state, now);
        mob.setPackState(state);
    }

    private static LycanPackState withNextDecision(final LycanPackState state, final long now) {
        return state.withCadence(new LycanPackState.Cadence(
            LycanPackRules.saturatingAdd(now, LycanPackRules.DECISION_INTERVAL_TICKS),
            state.cadence().nextPerceptionAt(), state.cadence().nextPlanAt(),
            state.cadence().nextFeedbackAt(), state.cadence().lastNavigationAt(),
            state.cadence().nextMoonSampleAt(), state.cadence().routeFailures(),
            state.cadence().retryAfter()
        ));
    }

    private static LycanPackState withPerceptionAt(final LycanPackState state, final long at) {
        return state.withCadence(new LycanPackState.Cadence(
            state.cadence().nextDecisionAt(), at, state.cadence().nextPlanAt(),
            state.cadence().nextFeedbackAt(), state.cadence().lastNavigationAt(),
            state.cadence().nextMoonSampleAt(), state.cadence().routeFailures(),
            state.cadence().retryAfter()
        ));
    }

    private static LycanPackState withPlanAt(final LycanPackState state, final long at) {
        return state.withCadence(new LycanPackState.Cadence(
            state.cadence().nextDecisionAt(), state.cadence().nextPerceptionAt(), at,
            state.cadence().nextFeedbackAt(), state.cadence().lastNavigationAt(),
            state.cadence().nextMoonSampleAt(), state.cadence().routeFailures(),
            state.cadence().retryAfter()
        ));
    }

    private static LycanPackState withFeedbackAt(final LycanPackState state, final long at) {
        return state.withCadence(new LycanPackState.Cadence(
            state.cadence().nextDecisionAt(), state.cadence().nextPerceptionAt(),
            state.cadence().nextPlanAt(), at, state.cadence().lastNavigationAt(),
            state.cadence().nextMoonSampleAt(), state.cadence().routeFailures(),
            state.cadence().retryAfter()
        ));
    }

    private static LycanPackState revalidateTarget(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        final LivingEntity target = mob.getTarget();
        if (target != null && !eligibleTarget(mob, target)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.packCounters().cancellations++;
            if (state.action().kind() != ActionKind.NONE) {
                return state.cancelAction("target_invalid",
                    LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_ABORT_RECOVERY_TICKS));
            }
        }
        return state;
    }

    private static @Nullable LivingEntity admittedAggressor(
        final WerewolfEntity mob,
        final ServerLevel level,
        final long now
    ) {
        final UUID transientId = mob.transientAttackerId(now);
        if (transientId != null && level.getEntity(transientId) instanceof LivingEntity living
            && living.isAlive() && living.level() == level
            && mob.distanceToSqr(living) <= (double) (LycanPackRules.ALERT_RADIUS * LycanPackRules.ALERT_RADIUS)) {
            return living;
        }
        for (final LycanPackRules.PlayerRelation relation : mob.packState().relationships()) {
            if (relation.expiresAt() <= now) continue;
            if (level.getEntity(relation.playerId()) instanceof ServerPlayer player
                && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.level() == level
                && mob.distanceToSqr(player) <= (double) (LycanPackRules.ALERT_RADIUS * LycanPackRules.ALERT_RADIUS)) {
                return player;
            }
        }
        return null;
    }

    private static LycanPackState respondToAggressor(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final LivingEntity aggressor,
        final long now
    ) {
        if (mob.variant() == Variant.FERAL_LYCAN) {
            final boolean cornered = mob.distanceToSqr(aggressor) <= 9.0D;
            if (!cornered || state.needs().fear() >= LycanPackRules.PANIC_FEAR) {
                return retreat(mob, level, state, now);
            }
        }
        if (eligibleTarget(mob, aggressor)) {
            if (mob.getTarget() != aggressor) mob.setTarget(aggressor);
            state = navigate(mob, state, aggressor.getX(), aggressor.getY(), aggressor.getZ(), 1.2D, now);
        }
        return state;
    }

    private static LycanPackState retreat(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long now
    ) {
        if (mob.getTarget() != null) {
            mob.setTarget(null);
            mob.packCounters().cancellations++;
        }
        if (state.action().kind() != ActionKind.NONE) {
            state = state.cancelAction("retreat",
                LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_ABORT_RECOVERY_TICKS));
        }
        final Optional<BlockPos> refuge = state.refuge().position()
            .filter(position -> level.hasChunkAt(position));
        if (refuge.isPresent()) {
            return navigate(mob, state, refuge.orElseThrow().getX() + 0.5D,
                refuge.orElseThrow().getY(), refuge.orElseThrow().getZ() + 0.5D, 1.25D, now);
        }
        final Vec3 threatOrigin = mob.getLastHurtByMob() != null
            ? mob.getLastHurtByMob().position() : mob.position().add(1.0D, 0.0D, 0.0D);
        final Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, threatOrigin);
        if (away != null) {
            return navigate(mob, state, away.x, away.y, away.z, 1.25D, now);
        }
        return state;
    }

    private static LycanPackState continueAction(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long now
    ) {
        return switch (state.action().kind()) {
            case POUNCE -> continuePounce(mob, level, state, now);
            case CONSUME_CARRION -> continueForage(mob, level, state, now);
            case HARRY, RETREAT, NONE -> now >= state.action().recoveryUntil()
                ? state.cancelAction("action_complete", now) : state;
        };
    }

    private static LycanPackState continuePounce(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        final LivingEntity target = mob.getTarget();
        if (now < state.action().windupUntil()) {
            if (target != null) mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return state;
        }
        if (target == null || !eligibleTarget(mob, target) || !LycanPackRules.mayPounce(
            mob.distanceTo(target), mob.getSensing().hasLineOfSight(target),
            standable(level, target.blockPosition()), false, 0L, now
        )) {
            mob.packCounters().cancellations++;
            return state.cancelAction("pounce_invalid",
                LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_ABORT_RECOVERY_TICKS));
        }
        final Vec3 launch = target.position().subtract(mob.position());
        final Vec3 impulse = new Vec3(launch.x, 0.0D, launch.z).normalize().scale(0.85D).add(0.0D, 0.42D, 0.0D);
        mob.setDeltaMovement(impulse);
        mob.packCounters().pounces++;
        return state.beginAction(ActionKind.HARRY, now, now,
            LycanPackRules.saturatingAdd(now, LycanPackRules.HARRY_TICKS));
    }

    private static LycanPackState tickWerewolfAutonomy(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long dayTime,
        final boolean fullMoon,
        final long now
    ) {
        if (state.hunt().episodeId().isPresent()) {
            return planHunt(mob, level, state, dayTime, fullMoon, now);
        }
        if (mob.getTarget() != null) {
            final LivingEntity target = mob.getTarget();
            return navigate(mob, state, target.getX(), target.getY(), target.getZ(), 1.2D, now);
        }
        if (LycanPackRules.prefersRecovery(state.needs().hunger())) return state;
        if (!LycanPackRules.mayWatchPrey(state.needs().hunger())) return state;
        if (now < state.cadence().nextPerceptionAt()) return state;
        state = withPerceptionAt(state, LycanPackRules.saturatingAdd(
            now, LycanPackRules.perceptionIntervalTicks(Variant.WEREWOLF)
        ));
        final Optional<LivingEntity> prey = perceiveNearestPrey(mob, level,
            LycanPackRules.perceptionRadius(Variant.WEREWOLF));
        if (prey.isEmpty()) return state;
        if (!LycanPackRules.nightHuntingEligible(dayTime)
            || !LycanPackRules.maySoloHunt(state.needs().hunger(), fullMoon)) {
            return state;
        }
        if (now >= state.cadence().nextPlanAt()) {
            state = withPlanAt(state, LycanPackRules.saturatingAdd(now, LycanPackRules.PLAN_INTERVAL_TICKS));
            final List<WerewolfEntity> recruits = recruitHuntMembers(mob, level, fullMoon);
            if (recruits.size() >= LycanPackRules.minimumRecruitmentQuorum(fullMoon)) {
                formHunt(level, recruits, prey.orElseThrow(), now);
                return mob.packState();
            }
        }
        mob.setTarget(prey.orElseThrow());
        return navigate(mob, state, prey.orElseThrow().getX(), prey.orElseThrow().getY(),
            prey.orElseThrow().getZ(), 1.2D, now);
    }

    private static List<WerewolfEntity> recruitHuntMembers(
        final WerewolfEntity recruiter,
        final ServerLevel level,
        final boolean fullMoon
    ) {
        final List<WerewolfEntity> inspected = new ArrayList<>();
        final AABB bounds = recruiter.getBoundingBox().inflate(LycanPackRules.recruitmentRadius(fullMoon));
        level.getEntities().get(EntityTypeTest.forClass(WerewolfEntity.class), bounds, candidate -> {
            inspected.add(candidate);
            recruiter.packCounters().recruitmentInspections++;
            return inspected.size() >= LycanPackRules.MAX_RECRUITMENT_CANDIDATES
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        final List<WerewolfEntity> accepted = inspected.stream()
            .filter(candidate -> candidate.isAlive() && exactWerewolf(candidate))
            .filter(candidate -> !VillageAssaultRuntime.isAssaultRaider(candidate))
            .filter(candidate -> !candidate.getPersistentData().contains(WOLF_TRAP_MARKER))
            .filter(candidate -> candidate == recruiter || candidate.packState().hunt().episodeId().isEmpty())
            .sorted(Comparator.comparing(Entity::getUUID, LycanPackRules.unsignedUuidOrder()))
            .limit(LycanPackRules.MAX_HUNT_MEMBERS)
            .toList();
        return accepted.contains(recruiter) ? accepted : List.of();
    }

    public static boolean formHunt(
        final ServerLevel level,
        final List<WerewolfEntity> members,
        final LivingEntity target,
        final long now
    ) {
        final List<WerewolfEntity> sorted = members.stream()
            .filter(member -> exactWerewolf(member) && member.isAlive() && member.level() == level)
            .sorted(Comparator.comparing(Entity::getUUID, LycanPackRules.unsignedUuidOrder()))
            .limit(LycanPackRules.MAX_HUNT_MEMBERS)
            .toList();
        if (sorted.isEmpty() || !eligibleTarget(sorted.get(0), target)) return false;
        final UUID episodeId = UUID.randomUUID();
        final List<UUID> memberIds = sorted.stream().map(Entity::getUUID).toList();
        final Map<UUID, HuntRole> roles = LycanPackRules.assignRoles(memberIds);
        final UUID coordinatorId = LycanPackRules.selectCoordinator(sorted.stream()
            .map(member -> coordinatorCandidate(member, false))
            .toList()).map(CoordinatorCandidate::memberId).orElse(memberIds.get(0));
        for (final WerewolfEntity member : sorted) {
            member.setPackState(member.packState().withHunt(new LycanPackState.Hunt(
                Optional.of(episodeId), Optional.of(coordinatorId), memberIds,
                Optional.ofNullable(roles.get(member.getUUID())), Optional.of(HuntPhase.RALLY),
                Optional.of(target.getUUID()), Optional.of(target.blockPosition()),
                LycanPackRules.saturatingAdd(now, LycanPackRules.HUNT_EPISODE_TICKS),
                LycanPackRules.saturatingAdd(now, LycanPackRules.phaseDeadlineTicks(HuntPhase.RALLY)),
                0, Optional.empty()
            )));
            member.packCounters().membershipChanges++;
            if (eligibleTarget(member, target)) member.setTarget(target);
        }
        return true;
    }

    private static CoordinatorCandidate coordinatorCandidate(final WerewolfEntity member, final boolean lease) {
        return new CoordinatorCandidate(
            member.getUUID(), lease,
            member.getPersistentData().getBooleanOr(VillageAssaultRuntime.ASSAULT_LEADER, false),
            member.isAlive() && !member.isBaby(),
            member.getHealth() / member.getMaxHealth(),
            member.packState().needs().hunger()
        );
    }

    public static LycanPackState planHunt(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long dayTime,
        final boolean fullMoon,
        final long now
    ) {
        if (now < state.cadence().nextPlanAt()) {
            return continueHuntMovement(mob, level, state, now);
        }
        state = withPlanAt(state, LycanPackRules.saturatingAdd(now, LycanPackRules.PLAN_INTERVAL_TICKS));
        final LycanPackState.Hunt hunt = state.hunt();
        final List<WerewolfEntity> loadedMembers = hunt.memberIds().stream()
            .map(level::getEntity)
            .filter(WerewolfEntity.class::isInstance)
            .map(WerewolfEntity.class::cast)
            .filter(member -> member.isAlive() && member.level() == level && exactWerewolf(member))
            .toList();
        final LivingEntity target = hunt.targetId()
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .orElse(null);
        final HuntPhase phase = hunt.phase().orElse(HuntPhase.RECOVER);
        final boolean coordinatorMissing = hunt.coordinatorId()
            .map(level::getEntity)
            .filter(WerewolfEntity.class::isInstance)
            .map(WerewolfEntity.class::cast)
            .filter(coordinator -> coordinator.isAlive() && coordinator.level() == level)
            .isEmpty();
        UUID coordinatorId = hunt.coordinatorId().orElse(null);
        if (coordinatorMissing) {
            coordinatorId = LycanPackRules.selectCoordinator(loadedMembers.stream()
                .map(member -> coordinatorCandidate(member, false))
                .toList()).map(CoordinatorCandidate::memberId).orElse(null);
            mob.packCounters().membershipChanges++;
        }
        final boolean abort = LycanPackRules.shouldAbortHunt(new LycanPackRules.HuntAbortFacts(
            !LycanPackRules.nightHuntingEligible(dayTime) && !VillageAssaultRuntime.isAssaultRaider(mob),
            target == null || !eligibleTarget(mob, target),
            coordinatorId == null,
            loadedMembers.size() < LycanPackRules.minimumRecruitmentQuorum(fullMoon)
                && loadedMembers.size() > 1,
            false,
            mob.isOnFire() || mob.isInLava(),
            mob.getHealth() / mob.getMaxHealth(),
            state.cadence().routeFailures(),
            now >= hunt.episodeExpiresAt(),
            hunt.targetChanges()
        ));
        if (abort || loadedMembers.size() <= 1 && phase == HuntPhase.RECOVER) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.packCounters().cancellations++;
            return state.withHunt(LycanPackState.Hunt.none());
        }
        HuntPhase nextPhase = phase;
        long phaseExpiresAt = hunt.phaseExpiresAt();
        if (now >= hunt.phaseExpiresAt()) {
            final HuntPhase[] order = HuntPhase.values();
            if (phase == HuntPhase.RECOVER) {
                mob.setTarget(null);
                return state.withHunt(LycanPackState.Hunt.none());
            }
            nextPhase = order[phase.ordinal() + 1];
            phaseExpiresAt = LycanPackRules.saturatingAdd(now, LycanPackRules.phaseDeadlineTicks(nextPhase));
        }
        state = state.withHunt(new LycanPackState.Hunt(
            hunt.episodeId(), Optional.ofNullable(coordinatorId), hunt.memberIds(),
            hunt.role(), Optional.of(nextPhase), hunt.targetId(),
            target != null ? Optional.of(target.blockPosition()) : hunt.targetPosition(),
            hunt.episodeExpiresAt(), phaseExpiresAt, hunt.targetChanges(), hunt.returnIntent()
        ));
        if (target != null && eligibleTarget(mob, target) && mob.getTarget() != target
            && (nextPhase == HuntPhase.PRESSURE || nextPhase == HuntPhase.STRIKE)) {
            mob.setTarget(target);
        }
        return continueHuntMovement(mob, level, state, now);
    }

    private static LycanPackState continueHuntMovement(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        final LycanPackState.Hunt hunt = state.hunt();
        if (hunt.episodeId().isEmpty() || hunt.targetPosition().isEmpty()) return state;
        final HuntPhase phase = hunt.phase().orElse(HuntPhase.RECOVER);
        final BlockPos targetPosition = hunt.targetPosition().orElseThrow();
        return switch (phase) {
            case RALLY, TRAIL, PRESSURE, STRIKE -> navigate(mob, state,
                targetPosition.getX() + 0.5D, targetPosition.getY(), targetPosition.getZ() + 0.5D,
                phase == HuntPhase.STRIKE ? 1.28D : 1.15D, now);
            case FAN_OUT -> {
                final int sector = LycanPackRules.approachSectorDegrees(
                    hunt.episodeId().orElseThrow().getLeastSignificantBits(),
                    hunt.role().orElse(HuntRole.PRESSURE)
                );
                final double radians = Math.toRadians(sector);
                yield navigate(mob, state,
                    targetPosition.getX() + 0.5D + Math.cos(radians) * 6.0D,
                    targetPosition.getY(),
                    targetPosition.getZ() + 0.5D + Math.sin(radians) * 6.0D,
                    1.15D, now);
            }
            case DISENGAGE, RECOVER -> {
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
                yield state;
            }
        };
    }

    private static LycanPackState tickFeralAutonomy(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long dayTime,
        final long now
    ) {
        if (now >= state.cadence().nextPlanAt()) {
            state = withPlanAt(state, LycanPackRules.saturatingAdd(
                now, LycanPackRules.FAMILIARITY_OBSERVATION_INTERVAL_TICKS
            ));
            state = observeNearbyFerals(mob, level, state, now);
        }
        if (state.refuge().position().isEmpty() && now >= state.refuge().nextSearchAt()) {
            state = searchRefuge(mob, level, state, now);
        }
        if (mob.variant() == Variant.FERAL_LYCAN && state.refuge().position().isPresent()) {
            state = guardRefuge(mob, level, state, now);
        }
        if (LycanPackRules.mayForage(state.needs().hunger(), false,
            mob.transientAttackerId(now) != null, state.action().kind() != ActionKind.NONE,
            state.needs().forageCooldownUntil(), now)) {
            state = beginForage(mob, level, state, now);
            if (state.action().kind() == ActionKind.CONSUME_CARRION) return state;
        }
        if (!LycanPackRules.mayStalk(state.needs().hunger(), state.needs().fear(), false)) return state;
        if (mob.getTarget() == null) {
            if (now < state.cadence().nextPerceptionAt()) return state;
            state = withPerceptionAt(state, LycanPackRules.saturatingAdd(
                now, LycanPackRules.perceptionIntervalTicks(Variant.FERAL_LYCAN)
            ));
            final Optional<LivingEntity> prey = perceiveNearestPrey(mob, level, LycanPackRules.STALK_RANGE);
            if (prey.isEmpty()) return state;
            mob.setTarget(prey.orElseThrow());
        }
        final LivingEntity target = mob.getTarget();
        if (target == null) return state;
        final double distance = mob.distanceTo(target);
        if (LycanPackRules.mayPounce(distance, mob.getSensing().hasLineOfSight(target),
            standable(level, target.blockPosition()), false, pounceCooldownUntil(state), now)) {
            return state.beginAction(ActionKind.POUNCE,
                LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_TELEGRAPH_TICKS),
                LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_TELEGRAPH_TICKS + 4L),
                LycanPackRules.saturatingAdd(now,
                    LycanPackRules.POUNCE_TELEGRAPH_TICKS + LycanPackRules.POUNCE_COOLDOWN_TICKS));
        }
        return navigate(mob, state, target.getX(), target.getY(), target.getZ(), 1.15D, now);
    }

    private static long pounceCooldownUntil(final LycanPackState state) {
        return state.action().recoveryUntil();
    }

    private static LycanPackState observeNearbyFerals(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long now
    ) {
        final List<WerewolfEntity> inspected = new ArrayList<>();
        final AABB bounds = mob.getBoundingBox().inflate(LycanPackRules.FAMILIARITY_RADIUS);
        level.getEntities().get(EntityTypeTest.forClass(WerewolfEntity.class), bounds, candidate -> {
            inspected.add(candidate);
            return inspected.size() >= LycanPackRules.MAX_RAW_CARRION_VISITS
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        for (final WerewolfEntity other : inspected) {
            if (other == mob || other.variant() != Variant.FERAL_LYCAN || !other.isAlive()) continue;
            state = recordFamiliarityObservation(mob, state, other.getUUID(), now);
        }
        return state;
    }

    public static LycanPackState recordFamiliarityObservation(
        final WerewolfEntity mob,
        final LycanPackState state,
        final UUID otherId,
        final long now
    ) {
        final LycanPackState.Cohort cohort = state.cohort();
        final List<LycanPackState.Familiarity> entries = new ArrayList<>(cohort.familiarity());
        final Optional<LycanPackState.Familiarity> existing = entries.stream()
            .filter(entry -> entry.otherId().equals(otherId))
            .findFirst();
        if (existing.isPresent()) {
            if (now - existing.orElseThrow().lastObservedAt()
                < LycanPackRules.FAMILIARITY_OBSERVATION_INTERVAL_TICKS) {
                return state;
            }
            entries.remove(existing.orElseThrow());
            entries.add(new LycanPackState.Familiarity(
                otherId,
                LycanPackRules.familiarityAfterObservation(existing.orElseThrow().points()),
                now
            ));
        } else if (entries.size() < LycanPackRules.MAX_FAMILIARITY_ENTRIES) {
            entries.add(new LycanPackState.Familiarity(otherId, 1, now));
        } else {
            return state;
        }
        final List<UUID> bonded = entries.stream()
            .filter(entry -> LycanPackRules.bonded(entry.points()))
            .map(LycanPackState.Familiarity::otherId)
            .sorted(LycanPackRules.unsignedUuidOrder())
            .limit(LycanPackRules.MAX_COHORT_MEMBERS - 1)
            .toList();
        return state.withCohort(new LycanPackState.Cohort(
            entries,
            bonded.isEmpty() ? Optional.empty()
                : Optional.of(cohort.cohortId().orElseGet(UUID::randomUUID)),
            bonded,
            bonded.isEmpty() ? 0L : LycanPackRules.saturatingAdd(now, LycanPackRules.COHORT_EXPIRY_TICKS),
            cohort.warningExpiresAt(),
            cohort.lastWarnAt()
        ));
    }

    private static LycanPackState guardRefuge(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        final BlockPos refuge = state.refuge().position().orElseThrow();
        if (!level.hasChunkAt(refuge)) return state.withoutRefuge();
        if (state.refuge().defenseExpiresAt() > now) return state;
        final List<LivingEntity> intruders = new ArrayList<>();
        level.getEntities().get(EntityTypeTest.forClass(LivingEntity.class),
            new AABB(refuge).inflate(LycanPackRules.TERRITORY_DEFENSE_RADIUS), candidate -> {
                if (candidate instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()
                    || candidate instanceof Villager) {
                    intruders.add(candidate);
                }
                return intruders.size() >= 2
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            });
        if (intruders.isEmpty()) return state;
        if (state.cohort().warningExpiresAt() > now) return state;
        if (!LycanPackRules.warningDue(state.cohort().lastWarnAt(), now)) return state;
        mob.packCounters().alerts++;
        final LycanPackState.Cohort cohort = state.cohort();
        return state.withCohort(new LycanPackState.Cohort(
            cohort.familiarity(), cohort.cohortId(), cohort.bondedIds(), cohort.cohortExpiresAt(),
            LycanPackRules.saturatingAdd(now, LycanPackRules.WARNING_EXPIRY_TICKS), now
        ));
    }

    private static LycanPackState searchRefuge(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long now
    ) {
        state = state.withRefugeSearchAt(LycanPackRules.saturatingAdd(
            now, LycanPackRules.REFUGE_SEARCH_INTERVAL_TICKS
        ));
        final BlockPos origin = mob.blockPosition();
        int inspected = 0;
        for (int x = -LycanPackRules.REFUGE_HORIZONTAL_RADIUS; x <= LycanPackRules.REFUGE_HORIZONTAL_RADIUS; x++) {
            for (int z = -LycanPackRules.REFUGE_HORIZONTAL_RADIUS; z <= LycanPackRules.REFUGE_HORIZONTAL_RADIUS; z++) {
                for (int y = -LycanPackRules.REFUGE_VERTICAL_RADIUS; y <= LycanPackRules.REFUGE_VERTICAL_RADIUS; y++) {
                    if (++inspected > LycanPackRules.MAX_REFUGE_BLOCK_INSPECTIONS) {
                        mob.packCounters().refugeBlockInspections += inspected - 1;
                        return state;
                    }
                    final BlockPos candidate = origin.offset(x, y, z);
                    if (!level.hasChunkAt(candidate)) continue;
                    if (!level.canSeeSky(candidate) && standable(level, candidate)) {
                        mob.packCounters().refugeBlockInspections += inspected;
                        return state.withRefuge(candidate,
                            LycanPackRules.saturatingAdd(now, LycanPackRules.REFUGE_EXPIRY_TICKS),
                            LycanPackRules.saturatingAdd(now, LycanPackRules.REFUGE_SEARCH_INTERVAL_TICKS));
                    }
                }
            }
        }
        mob.packCounters().refugeBlockInspections += inspected;
        return state;
    }

    private static LycanPackState beginForage(
        final WerewolfEntity mob,
        final ServerLevel level,
        LycanPackState state,
        final long now
    ) {
        final List<ItemEntity> visited = new ArrayList<>();
        level.getEntities().get(EntityTypeTest.forClass(ItemEntity.class),
            mob.getBoundingBox().inflate(LycanPackRules.CARRION_SCAN_RADIUS), candidate -> {
                visited.add(candidate);
                mob.packCounters().carrionVisits++;
                return visited.size() >= LycanPackRules.MAX_RAW_CARRION_VISITS
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            });
        final Optional<ItemEntity> retained = visited.stream()
            .filter(item -> LycanPackRules.eligibleCarrion(carrionFacts(mob, item)))
            .min(Comparator.<ItemEntity>comparingDouble(mob::distanceToSqr)
                .thenComparing(Entity::getUUID, LycanPackRules.unsignedUuidOrder()));
        if (retained.isEmpty()) return state;
        mob.setTransientCarrionId(retained.orElseThrow().getUUID());
        return state.beginAction(ActionKind.CONSUME_CARRION, now, now,
            LycanPackRules.saturatingAdd(now, LycanPackRules.FORAGE_COOLDOWN_TICKS));
    }

    private static LycanPackState continueForage(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        final UUID itemId = mob.transientCarrionId();
        final Entity raw = itemId == null ? null : level.getEntity(itemId);
        if (!(raw instanceof ItemEntity item) || !LycanPackRules.eligibleCarrion(carrionFacts(mob, item))) {
            mob.setTransientCarrionId(null);
            mob.packCounters().cancellations++;
            return state.cancelAction("carrion_invalid",
                LycanPackRules.saturatingAdd(now, LycanPackRules.POUNCE_ABORT_RECOVERY_TICKS));
        }
        if (mob.distanceTo(item) > LycanPackRules.CARRION_EAT_DISTANCE) {
            return navigate(mob, state, item.getX(), item.getY(), item.getZ(), 1.1D, now);
        }
        final ItemStack stack = item.getItem().copy();
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
        mob.setTransientCarrionId(null);
        mob.packCounters().carrionConsumed++;
        return state
            .withNeeds(LycanPackRules.hungerAfterCarrion(state.needs().hunger()), state.needs().fear(), now)
            .withForageCooldownUntil(LycanPackRules.saturatingAdd(now, LycanPackRules.FORAGE_COOLDOWN_TICKS))
            .cancelAction("carrion_consumed", now);
    }

    private static CarrionFacts carrionFacts(final WerewolfEntity mob, final ItemEntity item) {
        return new CarrionFacts(
            BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
            item.isAlive() && !item.isRemoved(),
            item.getItem().isEmpty(),
            item.getAge(),
            item.hasPickUpDelay(),
            item.getOwner() != null,
            item.hasCustomName(),
            item.level() == mob.level()
                && mob.distanceToSqr(item) <= (double) (LycanPackRules.CARRION_SCAN_RADIUS
                    * LycanPackRules.CARRION_SCAN_RADIUS)
        );
    }

    private static Optional<LivingEntity> perceiveNearestPrey(
        final WerewolfEntity mob,
        final ServerLevel level,
        final int radius
    ) {
        mob.packCounters().perceptionScans++;
        final List<LivingEntity> visited = new ArrayList<>();
        level.getEntities().get(EntityTypeTest.forClass(LivingEntity.class),
            mob.getBoundingBox().inflate(radius), candidate -> {
                visited.add(candidate);
                mob.packCounters().candidateAppraisals++;
                return visited.size() >= LycanPackRules.MAX_SCAN_RESULTS
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            });
        final List<LivingEntity> retained = visited.stream()
            .filter(candidate -> LycanPackRules.eligibleLivingPrey(preyFacts(mob, candidate, radius)))
            .sorted(Comparator.<LivingEntity>comparingDouble(mob::distanceToSqr)
                .thenComparing(Entity::getUUID, LycanPackRules.unsignedUuidOrder()))
            .limit(LycanPackRules.MAX_RETAINED_CANDIDATES)
            .toList();
        int losChecks = 0;
        for (final LivingEntity candidate : retained) {
            if (losChecks >= LycanPackRules.MAX_LINE_OF_SIGHT_CHECKS) break;
            losChecks++;
            mob.packCounters().lineOfSightChecks++;
            if (mob.getSensing().hasLineOfSight(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static PreyFacts preyFacts(final WerewolfEntity mob, final LivingEntity candidate, final int radius) {
        return new PreyFacts(
            BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()).toString(),
            !candidate.isBaby(),
            candidate instanceof TamableAnimal tamable && tamable.isTame()
                || candidate instanceof TraceableEntity traceable && traceable.getOwner() != null,
            candidate.hasCustomName(),
            candidate instanceof Leashable leashable && leashable.isLeashed(),
            candidate.isPassenger() || candidate.isVehicle(),
            candidate.isAlive() && !candidate.isRemoved(),
            candidate.level() == mob.level(),
            mob.distanceToSqr(candidate) <= (double) (radius * radius),
            VillageAssaultRuntime.isAssaultRaider(candidate)
        );
    }

    private static LycanPackState navigate(
        final WerewolfEntity mob,
        LycanPackState state,
        final double x,
        final double y,
        final double z,
        final double speed,
        final long now
    ) {
        if (now < state.cadence().retryAfter()
            || !LycanPackRules.navigationDue(state.cadence().lastNavigationAt(), now)) {
            return state;
        }
        mob.packCounters().navigationRequests++;
        final boolean accepted = mob.getNavigation().moveTo(x, y, z, speed);
        if (!accepted) mob.packCounters().navigationFailures++;
        state = state.withCadence(new LycanPackState.Cadence(
            state.cadence().nextDecisionAt(), state.cadence().nextPerceptionAt(),
            state.cadence().nextPlanAt(), state.cadence().nextFeedbackAt(), now,
            state.cadence().nextMoonSampleAt(), state.cadence().routeFailures(),
            state.cadence().retryAfter()
        ));
        return state.recordRouteResult(accepted, now);
    }

    private static LycanPackState emitFeedback(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        if (!LycanPackRules.feedbackDue(state.cadence().nextFeedbackAt(), now)) return state;
        if (mob.getTarget() == null && state.action().kind() == ActionKind.NONE) return state;
        mob.packCounters().feedbackBursts++;
        level.sendParticles(
            mob.variant() == Variant.WEREWOLF ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE,
            mob.getX(), mob.getY() + 1.2D, mob.getZ(),
            LycanPackRules.MAX_FEEDBACK_PARTICLES, 0.3D, 0.2D, 0.3D, 0.01D
        );
        return withFeedbackAt(state, LycanPackRules.saturatingAdd(now, LycanPackRules.FEEDBACK_INTERVAL_TICKS));
    }

    public static boolean meleeExecutorMayRun(final WerewolfEntity mob) {
        if (!(mob.level() instanceof ServerLevel)) return false;
        final LivingEntity target = mob.getTarget();
        return target != null && eligibleTarget(mob, target)
            && mob.packState().action().kind() != ActionKind.RETREAT;
    }

    public static boolean eligibleTarget(final WerewolfEntity mob, final LivingEntity target) {
        if (target == null || target == mob || !target.isAlive() || target.isRemoved()
            || target.level() != mob.level()) {
            return false;
        }
        if (target instanceof Player player
            && (player.isCreative() || player.isSpectator() || player.isInvulnerable())) {
            return false;
        }
        if (!target.canBeSeenAsEnemy() || !mob.lycanBaseMayAttack(target)) return false;
        final long now = mob.level().getGameTime();
        if (target instanceof Villager villager && isAssaultDesignatedVictim(mob, villager)) return true;
        if (isAdmittedAggressorTarget(mob, target, now)) return true;
        if (exactWerewolf(mob) && target instanceof ServerPlayer player
            && hasLiveRelation(mob.packState(), player.getUUID(), now)) {
            return true;
        }
        if (exactWerewolf(mob) && isSilverThreat(target)) return true;
        if (target instanceof WerewolfEntity || target instanceof LycanVillagerEntity) return false;
        if (target instanceof Player player
            && SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF) {
            return false;
        }
        final int radius = LycanPackRules.perceptionRadius(mob.variant());
        return LycanPackRules.eligibleLivingPrey(preyFacts(mob, target, radius));
    }

    private static boolean isAdmittedAggressorTarget(
        final WerewolfEntity mob,
        final LivingEntity target,
        final long now
    ) {
        final UUID transientId = mob.transientAttackerId(now);
        if (transientId != null && transientId.equals(target.getUUID())) return true;
        return target instanceof ServerPlayer player
            && hasLiveRelation(mob.packState(), player.getUUID(), now)
            && exactWerewolf(mob);
    }

    private static boolean hasLiveRelation(final LycanPackState state, final UUID playerId, final long now) {
        return state.relationships().stream()
            .anyMatch(relation -> relation.playerId().equals(playerId) && relation.expiresAt() > now);
    }

    public static boolean isSilverThreat(final LivingEntity target) {
        if (target instanceof WerewolfHunterEntity) return true;
        return target instanceof Pillager pillager
            && (pillager.getOffhandItem().is(WarlockeryTags.Items.SILVER_PROJECTILES)
                || pillager.getMainHandItem().is(WarlockeryTags.Items.SILVER_PROJECTILES));
    }

    public static boolean isAssaultDesignatedVictim(final WerewolfEntity mob, final Villager villager) {
        if (!exactWerewolf(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        if (!VillageAssaultRuntime.isAssaultRaider(mob)) return false;
        if (!mob.getPersistentData().getStringOr(VillageAssaultRuntime.ASSAULT_KIND, "")
            .equals(AssaultKind.WEREWOLF.serializedName())) {
            return false;
        }
        final long center = mob.getPersistentData().getLongOr(VillageAssaultRuntime.ASSAULT_CENTER, Long.MIN_VALUE);
        return villager.isAlive()
            && VillageAssaultData.get(level).active()
                .filter(state -> state.center().asLong() == center)
                .filter(state -> !state.raidersRetreating())
                .isPresent();
    }

    public static void coordinateAssaultPressure(
        final ServerLevel level,
        final WerewolfEntity werewolf,
        final @Nullable Villager victim,
        final BlockPos center
    ) {
        final long now = level.getGameTime();
        LycanPackState state = werewolf.packState();
        if (victim != null && isAssaultDesignatedVictim(werewolf, victim)) {
            if (werewolf.getTarget() != victim) werewolf.setTarget(victim);
            state = navigate(werewolf, state, victim.getX(), victim.getY(), victim.getZ(), 1.2D, now);
            werewolf.setPackState(state);
            return;
        }
        if (werewolf.getTarget() instanceof Villager) {
            werewolf.setTarget(null);
        }
        if (werewolf.distanceToSqr(Vec3.atCenterOf(center)) > 16.0D) {
            state = navigate(werewolf, state,
                center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 1.05D, now);
        }
        werewolf.setPackState(state);
    }

    public static void afterHurt(
        final WerewolfEntity mob,
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
        final long now = level.getGameTime();
        LycanPackState state = mob.packState();
        final boolean entityCaused = source.getEntity() != null || source.getDirectEntity() != null;
        if (entityCaused) {
            final int fear = isSilverSource(source)
                ? LycanPackRules.fearAfterSilverOrGuardDamage(state.needs().fear())
                : LycanPackRules.fearAfterOrdinaryDamage(state.needs().fear());
            state = state.withNeeds(state.needs().hunger(), fear, now);
        }
        final ServerPlayer attributed = resolveAttributedPlayer(source);
        if (attributed != null && LycanPackRules.acceptsAttribution(
            true, !attributed.isCreative() && !attributed.isSpectator(), attributed.isAlive()
        )) {
            state = state.withRelationships(
                LycanPackRules.recordAttributedHit(state.relationships(), attributed.getUUID(), now)
            );
            mob.packCounters().relationshipWrites++;
        } else if (source.getEntity() instanceof LivingEntity living && living != mob) {
            mob.rememberTransientAttacker(living.getUUID(),
                LycanPackRules.saturatingAdd(now, TRANSIENT_ATTACKER_TICKS));
        }
        if (mob.variant() == Variant.FERAL_LYCAN) {
            if (source.getEntity() instanceof WerewolfEntity other
                && other.variant() == Variant.FERAL_LYCAN) {
                state = withReducedFamiliarity(state, other.getUUID(), now);
            }
            state = warnBondedMembers(mob, level, state, now);
        }
        mob.setPackState(state);
    }

    private static LycanPackState withReducedFamiliarity(
        final LycanPackState state,
        final UUID otherId,
        final long now
    ) {
        final LycanPackState.Cohort cohort = state.cohort();
        final List<LycanPackState.Familiarity> entries = new ArrayList<>();
        for (final LycanPackState.Familiarity entry : cohort.familiarity()) {
            entries.add(entry.otherId().equals(otherId)
                ? new LycanPackState.Familiarity(
                    otherId, LycanPackRules.familiarityAfterFriendlyDamage(entry.points()), now)
                : entry);
        }
        final List<UUID> bonded = entries.stream()
            .filter(entry -> LycanPackRules.bonded(entry.points()))
            .map(LycanPackState.Familiarity::otherId)
            .sorted(LycanPackRules.unsignedUuidOrder())
            .limit(LycanPackRules.MAX_COHORT_MEMBERS - 1)
            .toList();
        return state.withCohort(new LycanPackState.Cohort(
            entries, bonded.isEmpty() ? Optional.empty() : cohort.cohortId(), bonded,
            cohort.cohortExpiresAt(), cohort.warningExpiresAt(), cohort.lastWarnAt()
        ));
    }

    public static LycanPackState warnBondedMembers(
        final WerewolfEntity mob,
        final ServerLevel level,
        final LycanPackState state,
        final long now
    ) {
        if (!LycanPackRules.warningDue(state.cohort().lastWarnAt(), now)) return state;
        int recipients = 0;
        for (final UUID bondedId : state.cohort().bondedIds()) {
            if (recipients >= LycanPackRules.MAX_WARNING_RECIPIENTS) break;
            if (level.getEntity(bondedId) instanceof WerewolfEntity bonded
                && bonded.variant() == Variant.FERAL_LYCAN && bonded.isAlive()
                && mob.distanceToSqr(bonded) <= (double) (LycanPackRules.WARNING_RADIUS
                    * LycanPackRules.WARNING_RADIUS)) {
                final LycanPackState.Cohort other = bonded.packState().cohort();
                bonded.setPackState(bonded.packState().withCohort(new LycanPackState.Cohort(
                    other.familiarity(), other.cohortId(), other.bondedIds(), other.cohortExpiresAt(),
                    LycanPackRules.saturatingAdd(now, LycanPackRules.WARNING_EXPIRY_TICKS),
                    other.lastWarnAt()
                )));
                recipients++;
                mob.packCounters().alerts++;
            }
        }
        if (recipients == 0) return state;
        final LycanPackState.Cohort cohort = state.cohort();
        return state.withCohort(new LycanPackState.Cohort(
            cohort.familiarity(), cohort.cohortId(), cohort.bondedIds(), cohort.cohortExpiresAt(),
            cohort.warningExpiresAt(), now
        ));
    }

    public static void afterSuccessfulAttack(final WerewolfEntity mob, final Entity target, final long now) {
        final LycanPackState state = mob.packState();
        if (state.action().kind() == ActionKind.POUNCE) {
            mob.setPackState(state.beginAction(ActionKind.HARRY, now, now,
                LycanPackRules.saturatingAdd(now, LycanPackRules.HARRY_TICKS)));
        }
    }

    public static void afterKill(final WerewolfEntity mob, final ServerLevel level, final LivingEntity killed) {
        final long now = level.getGameTime();
        final LycanPackState state = mob.packState();
        if (VillageAssaultRuntime.isAssaultRaider(mob)) return;
        if (!LycanPackRules.isOrdinaryPreyType(
            BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString()
        )) {
            return;
        }
        mob.setPackState(state.withNeeds(
            LycanPackRules.hungerAfterKill(mob.variant(), state.needs().hunger()),
            state.needs().fear(), now
        ));
        if (mob.getTarget() == killed) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    public static ServerPlayer resolveAttributedPlayer(final DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer causing) return causing;
        if (source.getDirectEntity() instanceof ServerPlayer direct) return direct;
        if (source.getDirectEntity() instanceof TraceableEntity traceable
            && traceable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (source.getEntity() instanceof TraceableEntity traceable
            && traceable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    private static boolean isSilverSource(final DamageSource source) {
        final ItemStack projectile = source.getDirectEntity() instanceof AbstractArrow arrow
            ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
        final ItemStack weapon = source.getWeaponItem();
        return projectile.is(WarlockeryTags.Items.SILVER_PROJECTILES)
            || weapon != null && weapon.is(WarlockeryTags.Items.SILVER_WEAPONS)
            || VillageAssaultRuntime.isSilverGuard(source.getEntity());
    }

    private static boolean standable(final ServerLevel level, final BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty()
            && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
            && !level.getBlockState(position.below()).getCollisionShape(level, position.below()).isEmpty();
    }

    public static final class Counters {
        long decisions;
        long perceptionScans;
        long candidateAppraisals;
        long lineOfSightChecks;
        long navigationRequests;
        long navigationFailures;
        long cancellations;
        long relationshipWrites;
        long recruitmentInspections;
        long membershipChanges;
        long alerts;
        long refugeBlockInspections;
        long carrionVisits;
        long carrionConsumed;
        long feedbackBursts;
        long hazardInterruptions;
        long pounces;

        public long decisions() { return decisions; }
        public long perceptionScans() { return perceptionScans; }
        public long candidateAppraisals() { return candidateAppraisals; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationFailures() { return navigationFailures; }
        public long cancellations() { return cancellations; }
        public long relationshipWrites() { return relationshipWrites; }
        public long recruitmentInspections() { return recruitmentInspections; }
        public long membershipChanges() { return membershipChanges; }
        public long alerts() { return alerts; }
        public long refugeBlockInspections() { return refugeBlockInspections; }
        public long carrionVisits() { return carrionVisits; }
        public long carrionConsumed() { return carrionConsumed; }
        public long feedbackBursts() { return feedbackBursts; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long pounces() { return pounces; }
    }
}
