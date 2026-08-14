package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.AABB;

public final class LycanVillagerRuntime {
    private static final int MAX_CANDIDATES = 12;
    private static final int LEVEL_OBSERVATIONS_PER_TICK = 4;
    private static final int LEVEL_PATHS_PER_TICK = 4;
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static final Map<LycanVillagerEntity, Map<UUID, Residence>> RESIDENCE = new WeakHashMap<>();
    private static final Map<LycanVillagerEntity, Evidence> EVIDENCE = new WeakHashMap<>();

    private LycanVillagerRuntime() { }

    public static boolean mayProtectResident(final int familiarity, final boolean directAttack,
                                               final boolean residentLoaded, final boolean attackerLoaded) {
        return familiarity >= LycanVillagerRules.HOUSEHOLD_THRESHOLD && directAttack && residentLoaded && attackerLoaded;
    }

    public static boolean mustWithdraw(final float health, final float maximumHealth,
                                       final boolean routeFailuresExhausted, final boolean protectedResidentSafe,
                                       final boolean targetTooFar, final boolean pursuitExpired) {
        return maximumHealth > 0.0F && health / maximumHealth <= LycanVillagerRules.WITHDRAW_HEALTH_FRACTION
            || routeFailuresExhausted || protectedResidentSafe || targetTooFar || pursuitExpired;
    }

    public static boolean hazardHasPriority(final boolean hazardActive, final LycanVillagerRules.Intent intent) {
        return hazardActive && intent != LycanVillagerRules.Intent.ROUTINE;
    }

    public static boolean admitsTarget(final LycanVillagerEntity lycan, final LivingEntity target) {
        return target != lycan && target.isAlive() && !target.isRemoved() && target.level() == lycan.level()
            && lycan.canAttack(target) && !(target instanceof ServerPlayer player
                && (player.isCreative() || player.isSpectator() || progressionAuthority(player, lycan)));
    }

    static boolean progressionAuthority(final ServerPlayer player, final LivingEntity target) {
        // These are the authoritative target-side prerequisites consumed by
        // SupernaturalProgressionRuntime.tryCreateVampire. Posture alone is not an action.
        return target.getPersistentData().getBooleanOr("WarlockeryCreationTargetDrained", false)
            && player.getStringUUID().equals(target.getPersistentData().getStringOr("WarlockeryMesmerizedBy", ""));
    }

    public static void tick(final LycanVillagerEntity lycan, final ServerLevel level) {
        final long now = level.getGameTime();
        LycanVillagerState state = lycan.sentinelState();
        final boolean hazard = HazardEscapeRuntime.currentHazard(lycan, level).isPresent();
        if (hazard) {
            lycan.setTarget(null);
            lycan.getNavigation().stop();
            lycan.setSentinelState(state.cancel(now));
            RESIDENCE.remove(lycan);
            EVIDENCE.remove(lycan);
            HazardEscapeRuntime.tick(lycan, level, lycan.creatureKind());
            return;
        }
        final var brain = lycan.getBrain();
        final boolean raidOrHide = brain.isActive(Activity.HIDE) || brain.isActive(Activity.RAID);
        final boolean panic = brain.isActive(Activity.PANIC);
        final boolean breeding = brain.hasMemoryValue(MemoryModuleType.BREED_TARGET);
        if (LycanVillagerRules.mustCancelSentinel(lycan.isAlive(), lycan.isTrading(), lycan.isSleeping(),
            lycan.isBaby(), raidOrHide, breeding)) {
            cancel(lycan);
            return;
        }
        if (panic && LycanVillagerRules.panicOverridesIntent(state.intent())) {
            cancel(lycan);
            return;
        }
        state = anchorFromBrain(lycan, state);
        if (now >= state.nextNearbyObservationAt() && admitObservation(level, lycan, now)) {
            state = observe(lycan, level, state, now);
        }
        final LivingEntity aggressor = resolve(level, state.recentAggressor().orElse(null));
        final LivingEntity resident = resolve(level, state.protectedResident().orElse(null));
        Evidence evidence = EVIDENCE.get(lycan);
        if (evidence != null && evidence.resident() && aggressor != null && resident instanceof Villager villager
            && villager.getLastHurtByMob() == aggressor
            && villager.getLastHurtByMobTimestamp() + LycanVillagerRules.EVIDENCE_FRESHNESS_TICKS >= villager.tickCount) {
            evidence = new Evidence(now, true);
            EVIDENCE.put(lycan, evidence);
        }
        if (state.recentAggressor().isPresent() && LycanVillagerRules.releasesAggressor(
            aggressor == null || !admitsTarget(lycan, aggressor) || aggressor.isInvulnerable(),
            aggressor != null && !lycan.getSensing().hasLineOfSight(aggressor),
            state.intent() == LycanVillagerRules.Intent.WARNING,
            evidence == null || now - evidence.observedAt() > LycanVillagerRules.EVIDENCE_FRESHNESS_TICKS,
            now > state.pursuitExpiry(),
            aggressor != null && lycan.distanceToSqr(aggressor) > 400.0D)) {
            cancel(lycan);
            return;
        } else if (aggressor != null && mustWithdraw(lycan.getHealth(), lycan.getMaxHealth(),
            state.routeFailures() >= LycanVillagerRules.MAX_ROUTE_FAILURES, false,
            lycan.distanceToSqr(aggressor) > 400.0D, now > state.pursuitExpiry())) {
            state = state.withIntent(LycanVillagerRules.Intent.WITHDRAW, now);
            lycan.setTarget(null);
            state = applyNavigation(state, navigateHome(lycan, level, state, now), now);
        } else if (state.intent() == LycanVillagerRules.Intent.WARNING) {
            if (aggressor != null) lycan.getLookControl().setLookAt(aggressor, 30.0F, 30.0F);
            if (now >= state.warningDeadline()) state = state.withIntent(LycanVillagerRules.Intent.INTERCEPT, now);
        } else if (state.intent() == LycanVillagerRules.Intent.INTERCEPT) {
            lycan.setTarget(aggressor);
            final NavigationResult result = navigate(lycan, level, aggressor.blockPosition(), now);
            if (result == NavigationResult.SUCCESS) state = state.routeSucceeded(now).withIntent(LycanVillagerRules.Intent.DEFEND, now);
            else if (result == NavigationResult.FAILED) state = state.routeFailed(now);
        } else if (state.intent() == LycanVillagerRules.Intent.DEFEND) {
            lycan.setTarget(aggressor);
            if (aggressor != null && lycan.distanceToSqr(aggressor) > 4.0D) {
                state = applyNavigation(state, navigate(lycan, level, aggressor.blockPosition(), now), now);
            }
            if (resident != null && resident.getLastHurtByMob() != aggressor) state = state.withIntent(LycanVillagerRules.Intent.WITHDRAW, now);
        } else if (state.intent() == LycanVillagerRules.Intent.WITHDRAW) {
            lycan.setTarget(null);
            state = applyNavigation(state, navigateHome(lycan, level, state, now), now);
            if (now >= state.withdrawalExpiry()) state = state.withIntent(LycanVillagerRules.Intent.RETURN, now);
        } else if (state.intent() == LycanVillagerRules.Intent.RETURN) {
            cancel(lycan);
            return;
        } else if ((state.intent() == LycanVillagerRules.Intent.GREETING
            || state.intent() == LycanVillagerRules.Intent.RESERVE) && now >= state.nextDecisionAt()) {
            cancel(lycan);
            return;
        } else if ((state.intent() == LycanVillagerRules.Intent.BOUNDARY_WATCH
            || state.intent() == LycanVillagerRules.Intent.MOON_WATCH)
            && now >= state.nextDecisionAt() + LycanVillagerRules.WATCH_TICKS - LycanVillagerRules.DECISION_CADENCE_TICKS) {
            cancel(lycan);
            return;
        } else if (now >= state.nextLunarObservationAt()
            && state.intent() != LycanVillagerRules.Intent.BOUNDARY_WATCH
            && state.intent() != LycanVillagerRules.Intent.MOON_WATCH) {
            final long day = Math.floorMod(level.getOverworldClockTime(), 24_000L);
            final boolean night = day >= 13_000L && day <= 23_000L;
            final boolean fullMoon = level.environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_PHASE, lycan.position()) == MoonPhase.FULL_MOON;
            final boolean sky = level.canSeeSky(lycan.blockPosition()) && !level.isRainingAt(lycan.blockPosition());
            final boolean working = brain.isActive(Activity.WORK) || brain.isActive(Activity.MEET);
            final LycanVillagerRules.Intent watch = LycanVillagerRules.watchIntent(new LycanVillagerRules.WatchInputs(
                night, fullMoon, sky, !working, raidOrHide, panic, lycan.isTrading(), lycan.isSleeping(),
                breeding, state.anchor().isPresent()));
            if (watch != LycanVillagerRules.Intent.ROUTINE) state = state.withIntent(watch, now);
            state = state.withCadence(now + LycanVillagerRules.DECISION_CADENCE_TICKS,
                state.nextNearbyObservationAt(), now + LycanVillagerRules.LUNAR_OBSERVATION_TICKS);
        } else if ((state.intent() == LycanVillagerRules.Intent.BOUNDARY_WATCH
            || state.intent() == LycanVillagerRules.Intent.MOON_WATCH) && now >= state.nextDecisionAt()) {
            state = applyNavigation(state, navigateHome(lycan, level, state, now), now);
            watchPosture(lycan, level, state);
        }
        lycan.setSentinelState(state);
    }

    private static LycanVillagerState observe(final LycanVillagerEntity lycan, final ServerLevel level,
                                               LycanVillagerState state, final long now) {
        final var nearby = level.getEntitiesOfClass(Villager.class, lycan.getBoundingBox().inflate(16.0D),
                entity -> entity != lycan && entity.isAlive())
            .stream().sorted(Comparator.comparingDouble((Villager value) -> lycan.distanceToSqr(value))
                .thenComparing(Villager::getUUID))
            .limit(MAX_CANDIDATES).toList();
        final java.util.Set<UUID> qualifying = new java.util.HashSet<>();
        for (final Villager resident : nearby) {
            if (lycan.distanceToSqr(resident) <= 64.0D && sharesVillageContext(lycan, resident)) {
                qualifying.add(resident.getUUID());
                final Map<UUID, Residence> ledger = RESIDENCE.computeIfAbsent(lycan, ignored -> new java.util.HashMap<>());
                final Residence old = ledger.getOrDefault(resident.getUUID(), new Residence(0, now));
                final Residence next = new Residence(Math.min(200, old.qualifyingTicks() + 100), now);
                if (next.qualifyingTicks() >= LycanVillagerRules.FAMILIARITY_GAIN_TICKS) {
                    state = state.observe(resident.getUUID(), LycanVillagerRules.RelationshipSource.RESIDENT, 1, now);
                    ledger.put(resident.getUUID(), new Residence(0, now));
                } else ledger.put(resident.getUUID(), next);
            }
            final LivingEntity attacker = resident.getLastHurtByMob();
            if (attacker != null && resident.getLastHurtByMobTimestamp() + LycanVillagerRules.EVIDENCE_FRESHNESS_TICKS >= resident.tickCount
                && mayProtectResident(state.points(resident.getUUID()), true, true, attacker.isAlive())
                && admitsTarget(lycan, attacker)) {
                EVIDENCE.put(lycan, new Evidence(now, true));
                beginWarning(lycan, level, attacker);
                return state.withCombat(attacker.getUUID(), resident.getUUID(), LycanVillagerRules.Intent.WARNING,
                    now + LycanVillagerRules.WARNING_TICKS, now + LycanVillagerRules.PURSUIT_TICKS)
                    .withCadence(state.nextDecisionAt(), now + LycanVillagerRules.NEARBY_OBSERVATION_TICKS,
                        state.nextLunarObservationAt());
            }
        }
        RESIDENCE.computeIfAbsent(lycan, ignored -> new java.util.HashMap<>()).keySet().retainAll(qualifying);
        // Social pressure is deliberately resolved from the same bounded observation pass.
        // Known players receive a brief greeting; unfamiliar armed outsiders cause reserve.
        final var players = level.getEntitiesOfClass(ServerPlayer.class, lycan.getBoundingBox().inflate(16.0D),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
            .stream().sorted(Comparator.comparingDouble((ServerPlayer value) -> lycan.distanceToSqr(value))
                .thenComparing(ServerPlayer::getUUID)).limit(MAX_CANDIDATES).toList();
        if (state.intent() == LycanVillagerRules.Intent.ROUTINE && !players.isEmpty()) {
            final ServerPlayer visitor = players.getFirst();
            if (state.points(visitor.getUUID()) >= LycanVillagerRules.TRADE_FAMILIARITY_POINTS) {
                state = state.withIntent(LycanVillagerRules.Intent.GREETING, now);
                lycan.getLookControl().setLookAt(visitor, 20.0F, 20.0F);
                level.broadcastEntityEvent(lycan, (byte) 14);
            } else if (!visitor.getMainHandItem().isEmpty() || !visitor.getOffhandItem().isEmpty()) {
                state = state.withIntent(LycanVillagerRules.Intent.RESERVE, now);
                lycan.getLookControl().setLookAt(visitor, 30.0F, 30.0F);
                state = applyNavigation(state, navigateHome(lycan, level, state, now), now);
            }
        }
        return state.decay(now).withCadence(state.nextDecisionAt(),
            now + LycanVillagerRules.NEARBY_OBSERVATION_TICKS, state.nextLunarObservationAt());
    }

    private static boolean sharesVillageContext(final Villager left, final Villager right) {
        return sameMemory(left, right, MemoryModuleType.HOME) || sameMemory(left, right, MemoryModuleType.MEETING_POINT);
    }

    private static <T> boolean sameMemory(final Villager left, final Villager right, final MemoryModuleType<T> type) {
        final var a = left.getBrain().getMemory(type);
        final var b = right.getBrain().getMemory(type);
        return a.isPresent() && a.equals(b);
    }

    private static LycanVillagerState anchorFromBrain(final LycanVillagerEntity lycan, final LycanVillagerState state) {
        final var anchor = lycan.getBrain().getMemory(MemoryModuleType.HOME)
            .or(() -> lycan.getBrain().getMemory(MemoryModuleType.MEETING_POINT))
            .filter(global -> global.dimension() == lycan.level().dimension())
            .filter(global -> lycan.level().isLoaded(global.pos()));
        if (anchor.isEmpty() && state.anchor().isPresent() && state.intent() != LycanVillagerRules.Intent.ROUTINE) {
            cancel(lycan);
            return lycan.sentinelState();
        }
        return anchor.map(global -> state.withAnchor(global.dimension().identifier().toString(), global.pos().asLong()))
            .orElse(state);
    }

    private static NavigationResult navigateHome(final LycanVillagerEntity lycan, final ServerLevel level,
                                     final LycanVillagerState state, final long now) {
        return state.anchor().filter(anchor -> anchor.dimension().equals(level.dimension().identifier().toString()))
            .map(anchor -> BlockPos.of(anchor.packedPosition())).filter(level::isLoaded)
            .map(pos -> navigate(lycan, level, pos, now)).orElse(NavigationResult.DEFERRED);
    }

    private static LycanVillagerState applyNavigation(final LycanVillagerState state,
                                                       final NavigationResult result, final long now) {
        return result == NavigationResult.SUCCESS ? state.routeSucceeded(now)
            : result == NavigationResult.FAILED ? state.routeFailed(now) : state;
    }

    private static NavigationResult navigate(final LycanVillagerEntity lycan, final ServerLevel level,
                                    final BlockPos position, final long now) {
        if (now - lycan.sentinelState().lastNavigationAt() < LycanVillagerRules.NAVIGATION_CADENCE_TICKS
            || now < lycan.sentinelState().retryAfter() || !level.isLoaded(position)
            || !admitPath(level, lycan, now)) return NavigationResult.DEFERRED;
        claimMovement(lycan);
        final var path = lycan.getNavigation().createPath(position, 0);
        return path != null && path.canReach() && lycan.getNavigation().moveTo(path, 1.0D)
            ? NavigationResult.SUCCESS : NavigationResult.FAILED;
    }

    private static void claimMovement(final LycanVillagerEntity lycan) {
        lycan.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        lycan.getBrain().eraseMemory(MemoryModuleType.PATH);
        lycan.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        lycan.getNavigation().stop();
    }

    private static LivingEntity resolve(final ServerLevel level, final java.util.UUID id) {
        if (id == null) return null;
        return level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public static void cancel(final LycanVillagerEntity lycan) {
        lycan.setTarget(null);
        lycan.getNavigation().stop();
        if (lycan.sentinelState() != null) lycan.setSentinelState(lycan.sentinelState().cancel(lycan.level().getGameTime()));
        RESIDENCE.remove(lycan);
        EVIDENCE.remove(lycan);
    }

    private static boolean admitObservation(final ServerLevel level, final LycanVillagerEntity entity, final long tick) {
        return budget(level, tick).admit(entity.getUUID(), true);
    }

    private static boolean admitPath(final ServerLevel level, final LycanVillagerEntity entity, final long tick) {
        return budget(level, tick).admit(entity.getUUID(), false);
    }

    private static Budget budget(final ServerLevel level, final long tick) {
        final Budget value = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (value.tick != tick) value.begin(tick);
        return value;
    }

    private static final class Budget {
        long tick = Long.MIN_VALUE;
        final java.util.NavigableSet<UUID> known = new java.util.TreeSet<>();
        final java.util.Set<UUID> observationWinners = new java.util.HashSet<>();
        final java.util.Set<UUID> pathWinners = new java.util.HashSet<>();
        UUID observationCursor;
        UUID pathCursor;
        void begin(final long now) {
            tick = now;
            observationCursor = select(known, observationCursor, LEVEL_OBSERVATIONS_PER_TICK, observationWinners);
            pathCursor = select(known, pathCursor, LEVEL_PATHS_PER_TICK, pathWinners);
            if (known.size() > 64) {
                while (known.size() > 64) known.pollFirst();
            }
        }
        boolean admit(final UUID id, final boolean observation) {
            known.add(id);
            return (observation ? observationWinners : pathWinners).remove(id);
        }
        private static UUID select(final java.util.NavigableSet<UUID> ids, final UUID cursor, final int limit,
                                   final java.util.Set<UUID> winners) {
            winners.clear();
            if (ids.isEmpty()) return cursor;
            UUID value = cursor == null ? ids.first() : ids.higher(cursor);
            if (value == null) value = ids.first();
            UUID last = value;
            for (int count = 0; count < Math.min(limit, ids.size()); count++) {
                winners.add(value); last = value; value = ids.higher(value); if (value == null) value = ids.first();
            }
            return last;
        }
    }
    static void rememberDirectEvidence(final LycanVillagerEntity lycan, final long now) {
        EVIDENCE.put(lycan, new Evidence(now, false));
    }
    static void beginWarning(final LycanVillagerEntity lycan, final ServerLevel level, final LivingEntity threat) {
        claimMovement(lycan);
        lycan.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        level.broadcastEntityEvent(lycan, (byte) 13);
    }
    private static void watchPosture(final LycanVillagerEntity lycan, final ServerLevel level,
                                     final LycanVillagerState state) {
        if (state.intent() == LycanVillagerRules.Intent.MOON_WATCH) {
            lycan.getLookControl().setLookAt(lycan.getX(), lycan.getEyeY() + 8.0D, lycan.getZ());
            return;
        }
        state.anchor().filter(anchor -> anchor.dimension().equals(level.dimension().identifier().toString()))
            .map(anchor -> BlockPos.of(anchor.packedPosition()))
            .ifPresent(pos -> {
                final double dx = lycan.getX() - (pos.getX() + 0.5D);
                final double dz = lycan.getZ() - (pos.getZ() + 0.5D);
                final double norm = Math.max(1.0E-3D, Math.sqrt(dx * dx + dz * dz));
                lycan.getLookControl().setLookAt(lycan.getX() + dx / norm * 4.0D, lycan.getEyeY(),
                    lycan.getZ() + dz / norm * 4.0D);
            });
    }
    private record Residence(int qualifyingTicks, long lastObservedAt) { }
    private record Evidence(long observedAt, boolean resident) { }
    private enum NavigationResult { DEFERRED, SUCCESS, FAILED }
}
