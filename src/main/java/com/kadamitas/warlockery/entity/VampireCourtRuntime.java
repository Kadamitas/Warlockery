package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import com.kadamitas.warlockery.entity.VampireCourtRules.Intent;
import com.kadamitas.warlockery.entity.VampireCourtRules.ReportOutcome;
import com.kadamitas.warlockery.entity.VampireCourtRules.VictimReport;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

public final class VampireCourtRuntime {
    private static final long TARGET_LEASE_TICKS = 200L;
    private static final long SHELTER_LEASE_TICKS = 200L;
    private static final long ATTACKER_MEMORY_TICKS = 200L;
    private static final int MAX_LEVEL_CLAIMS = 256;
    private static final Map<ServerLevel, Map<ClaimKey, Claim>> CLAIMS = new WeakHashMap<>();

    private VampireCourtRuntime() {
    }

    public static void tick(final VampireCourtEntity member, final ServerLevel level) {
        final long dayTime = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        final boolean night = dayTime >= 13_000L && dayTime <= 23_000L;
        final boolean exposedDay = !night && level.canSeeSky(member.blockPosition());
        tickForObservation(member, level, exposedDay, night);
    }

    public static void tickForObservation(
        final VampireCourtEntity member,
        final ServerLevel level,
        final boolean exposedDay,
        final boolean night
    ) {
        final long now = level.getGameTime();
        final VampireCourtState stored = member.courtState();
        final boolean wasWavering = stored.intent() == Intent.WAVERING;
        final long waveringUntil = stored.waveringUntil();
        VampireCourtState state = stored.reconcileAfterLoad(now);
        state = revalidateAttacker(member, level, state, now);
        member.setCourtState(state);
        state = revalidateTarget(member, level, state, now);
        final boolean urgent = exposedDay && !member.hasEffect(MobEffects.FIRE_RESISTANCE)
            || member.isOnFire() || member.isInLava();
        if (member.creatureKind() == CreatureKind.BLOOD_THRALL && state.masterId().isPresent()) {
            final Entity rawMaster = level.getEntity(state.masterId().orElseThrow());
            if (!(rawMaster instanceof VampireCourtEntity master) || !validMaster(member, master)) {
                member.setTarget(null);
                member.getNavigation().stop();
                member.setCourtState(state.loseMaster(now));
                member.courtCounters().cancellations++;
                return;
            }
            if (master.courtState().recentAttacker().isPresent()) {
                final Entity attacker = level.getEntity(master.courtState().recentAttacker().orElseThrow());
                if (attacker instanceof LivingEntity living && eligibleDirectAttacker(member, living, now)
                    && member.distanceToSqr(living) <= VampireCourtRules.ENTITY_SCAN_RADIUS
                        * VampireCourtRules.ENTITY_SCAN_RADIUS) {
                    state = state.rememberAttacker(living.getUUID(), VampireCourtRules.saturatingAdd(
                        now, ATTACKER_MEMORY_TICKS
                    ));
                }
            }
        }
        member.setCourtState(state);
        state = revalidateShelterClaim(member, level, state, now);
        final boolean assaultMember = VillageAssaultRuntime.isAssaultRaider(member);
        if (wasWavering && !urgent) {
            final Intent wavering = VampireCourtRules.afterWavering(now, waveringUntil, assaultMember);
            member.setTarget(null);
            member.getNavigation().stop();
            member.setCourtState(state.withIntent(
                wavering,
                wavering == Intent.WAVERING ? waveringUntil : 0L
            ));
            return;
        }
        if (state.intent() == Intent.RETREAT && assaultMember && state.masterId().isEmpty()) {
            VillageAssaultRuntime.beginMemberRetreat(level, member);
            return;
        }
        if (!VampireCourtRules.decisionDue(state.nextDecisionAt(), now, urgent, state.intent())) return;
        final boolean directThreat = state.recentAttacker().isPresent();
        final boolean assaultLeader = state.assaultRole() == AssaultRole.PREDATOR_LEADER;
        final boolean hasMaster = state.masterId().isPresent();
        final Intent next = urgent ? Intent.SEEK_SHELTER : directThreat ? Intent.INTERCEPT : assaultMember
            ? (assaultLeader ? Intent.ASSAULT_LEAD
                : Intent.THRESHOLD_GUARD)
            : VampireCourtRules.chooseIntent(
                member.creatureKind(), night ? 18_000L : 6_000L, urgent, directThreat,
                false, state.pressure(), hasMaster
            );
        state = releaseClaimsForTransition(member, level, state, next);
        state = state.withIntent(next, VampireCourtRules.saturatingAdd(now, VampireCourtRules.DECISION_INTERVAL_TICKS));
        member.courtCounters().decisions++;

        if (next == Intent.SEEK_SHELTER && now >= state.nextShelterScanAt()) {
            state = findShelter(member, level, state, now);
        } else if ((next == Intent.STALK || next == Intent.WATCH) && now >= state.nextEntityScanAt()) {
            state = observePrey(member, level, state, now, next == Intent.STALK);
        }
        if (next == Intent.INTERCEPT && state.recentAttacker().isPresent()) {
            final Entity attacker = level.getEntity(state.recentAttacker().orElseThrow());
            if (attacker instanceof LivingEntity living && eligibleTarget(member, living)) {
                member.setTarget(living);
                state = state.withTarget(living.getUUID(), VampireCourtRules.saturatingAdd(now, TARGET_LEASE_TICKS));
                state = navigate(
                    member, state, living.getX(), living.getY(), living.getZ(), 1.15D, now
                );
            }
        }
        if (next == Intent.ASSAULT_LEAD) {
            state = approachAssignedTarget(member, state, now);
        }
        member.setCourtState(state.withCadence(
            VampireCourtRules.saturatingAdd(now, VampireCourtRules.DECISION_INTERVAL_TICKS),
            state.nextEntityScanAt(), state.nextShelterScanAt(), state.nextFeedbackAt(),
            state.lastNavigationAt()
        ));
    }

    private static VampireCourtState approachAssignedTarget(
        final VampireCourtEntity member,
        VampireCourtState state,
        final long now
    ) {
        if (state.targetId().isEmpty()) return state;
        final LivingEntity target = assignedTarget(member);
        if (target == null || !eligibleTarget(member, target)) {
            member.setTarget(null);
            member.getNavigation().stop();
            return state.withTarget(null, 0L).withIntent(Intent.RECOVER,
                VampireCourtRules.saturatingAdd(now, VampireCourtRules.DECISION_INTERVAL_TICKS));
        }
        if (member.getTarget() != target) member.setTarget(target);
        if (member.isWithinMeleeAttackRange(target) && member.getSensing().hasLineOfSight(target)) {
            if (!member.getNavigation().isDone()) member.getNavigation().stop();
            return state;
        }
        // Minecraft's default reach range of one lets a path finish on the adjacent node, which
        // can still sit just outside the current AABB-based melee band. Assault feeding must close
        // the final node instead of treating that near miss as three route failures.
        return navigate(member, state, target.getX(), target.getY(), target.getZ(), 0, 1.15D, now);
    }

    public static void acceptAssaultObjective(
        final VampireCourtEntity leader,
        final LivingEntity target,
        final long now
    ) {
        final long claimUntil = VampireCourtRules.claimExpiry(
            now, VampireCourtRules.MAX_CLAIM_LEASE_TICKS
        );
        final VampireCourtState assigned = leader.courtState().withTarget(
            target.getUUID(), claimUntil
        ).withIntent(Intent.ASSAULT_LEAD, claimUntil);
        leader.setCourtState(assigned);
        leader.setTarget(target);
        leader.setCourtState(approachAssignedTarget(leader, assigned, now));
    }

    private static LivingEntity assignedTarget(final VampireCourtEntity member) {
        if (!(member.level() instanceof ServerLevel level)) return null;
        final LivingEntity current = member.getTarget();
        if (current != null && member.courtState().targetId().filter(current.getUUID()::equals).isPresent()) {
            return current;
        }
        return member.courtState().targetId().map(level::getEntity)
            .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
    }

    private static VampireCourtState revalidateAttacker(
        final VampireCourtEntity member,
        final ServerLevel level,
        final VampireCourtState state,
        final long now
    ) {
        if (state.recentAttacker().isEmpty()) return state;
        final Entity raw = level.getEntity(state.recentAttacker().orElseThrow());
        if (!(raw instanceof LivingEntity living) || state.attackerExpiresAt() <= now
            || !eligibleDirectAttacker(member, living, now)
            || member.distanceToSqr(living) > VampireCourtRules.ENTITY_SCAN_RADIUS
                * VampireCourtRules.ENTITY_SCAN_RADIUS) {
            return state.rememberAttacker(null, 0L);
        }
        return state;
    }

    private static VampireCourtState revalidateTarget(
        final VampireCourtEntity member,
        final ServerLevel level,
        final VampireCourtState state,
        final long now
    ) {
        if (state.targetId().isEmpty()) {
            if (member.getTarget() != null) {
                member.setTarget(null);
                member.getNavigation().stop();
                member.courtCounters().cancellations++;
            }
            return state;
        }
        final LivingEntity target = assignedTarget(member);
        final boolean ordinaryPreyClaim = target instanceof ServerPlayer
            && (state.intent() == Intent.STALK || state.intent() == Intent.WATCH || state.intent() == Intent.FEED);
        final boolean retained = target != null && state.targetExpiresAt() > now
            && !target.isRemoved() && target.isAlive() && target.level() == level
            && level.hasChunkAt(target.blockPosition())
            && member.distanceToSqr(target) <= VampireCourtRules.ENTITY_RETAIN_RADIUS
                * VampireCourtRules.ENTITY_RETAIN_RADIUS
            && eligibleTarget(member, target)
            && (!ordinaryPreyClaim
                || ownedClaim(level, ClaimKey.prey(target.getUUID()), member.getUUID(), now))
            && (state.intent() != Intent.FEED || member.getSensing().hasLineOfSight(target));
        if (retained) return state;
        state.targetId().ifPresent(id -> releaseClaim(level, ClaimKey.prey(id), member.getUUID()));
        member.setTarget(null);
        member.getNavigation().stop();
        member.courtCounters().cancellations++;
        final Intent released = member.creatureKind() == CreatureKind.VAMPIRE
            ? Intent.RECOVER : state.masterId().isPresent() ? Intent.THRESHOLD_GUARD : Intent.UNBOUND;
        return state.withTarget(null, 0L).withIntent(
            released, VampireCourtRules.saturatingAdd(now, VampireCourtRules.DECISION_INTERVAL_TICKS)
        );
    }

    private static VampireCourtState observePrey(
        final VampireCourtEntity vampire,
        final ServerLevel level,
        VampireCourtState state,
        final long now,
        final boolean mayNavigate
    ) {
        final AABB bounds = vampire.getBoundingBox().inflate(VampireCourtRules.ENTITY_SCAN_RADIUS);
        final var players = level.getEntitiesOfClass(ServerPlayer.class, bounds, player -> eligibleTarget(vampire, player))
            .stream().sorted(Comparator.comparingDouble(vampire::distanceToSqr))
            .limit(VampireCourtRules.MAX_CANDIDATES).toList();
        vampire.courtCounters().entityScans++;
        vampire.courtCounters().candidateAppraisals += players.size();
        state = state.withCadence(
            state.nextDecisionAt(), VampireCourtRules.saturatingAdd(now, VampireCourtRules.ENTITY_SCAN_INTERVAL_TICKS),
            state.nextShelterScanAt(), state.nextFeedbackAt(), state.lastNavigationAt()
        );
        if (players.isEmpty()) {
            vampire.setTarget(null);
            return state.withTarget(null, 0L);
        }
        final ServerPlayer prey = players.stream()
            .filter(player -> acquireClaim(level, ClaimKey.prey(player.getUUID()), vampire.getUUID(), now))
            .findFirst().orElse(null);
        if (prey == null) {
            vampire.setTarget(null);
            return state.withTarget(null, 0L);
        }
        state = state.withTarget(prey.getUUID(), VampireCourtRules.saturatingAdd(now, TARGET_LEASE_TICKS));
        if (mayNavigate) {
            vampire.setTarget(prey);
            state = navigate(vampire, state, prey.getX(), prey.getY(), prey.getZ(), 1.15D, now);
        }
        return state;
    }

    private static VampireCourtState findShelter(
        final VampireCourtEntity member,
        final ServerLevel level,
        VampireCourtState state,
        final long now
    ) {
        member.courtCounters().shelterScans++;
        int inspected = 0;
        BlockPos selected = null;
        final BlockPos origin = member.blockPosition();
        search:
        for (int radius = 1; radius <= 8; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    for (int y = -2; y <= 2; y++) {
                        if (++inspected > VampireCourtRules.MAX_SHELTER_BLOCKS) break search;
                        final BlockPos candidate = origin.offset(x, y, z);
                        if (!level.hasChunkAt(candidate)) continue;
                        if (!level.canSeeSky(candidate) && level.isEmptyBlock(candidate)
                            && level.isEmptyBlock(candidate.above())
                            && level.getBlockState(candidate.below()).blocksMotion()
                            && tryClaimShelter(member, level, candidate, now)) {
                            selected = candidate;
                            break search;
                        }
                    }
                }
            }
        }
        member.courtCounters().shelterBlocks += inspected;
        state = state.withCadence(
            state.nextDecisionAt(), state.nextEntityScanAt(),
            VampireCourtRules.saturatingAdd(now, VampireCourtRules.SHELTER_SCAN_INTERVAL_TICKS),
            state.nextFeedbackAt(), state.lastNavigationAt()
        );
        if (selected == null) return state;
        state = state.withShelter(level.dimension().identifier().toString(), selected,
            VampireCourtRules.saturatingAdd(now, SHELTER_LEASE_TICKS));
        return navigate(member, state, selected.getX() + 0.5D, selected.getY(), selected.getZ() + 0.5D, 1.2D, now);
    }

    private static VampireCourtState navigate(
        final VampireCourtEntity member,
        VampireCourtState state,
        final double x,
        final double y,
        final double z,
        final double speed,
        final long now
    ) {
        return navigate(member, state, x, y, z, 1, speed, now);
    }

    private static VampireCourtState navigate(
        final VampireCourtEntity member,
        VampireCourtState state,
        final double x,
        final double y,
        final double z,
        final int reachRange,
        final double speed,
        final long now
    ) {
        if (now < state.retryAfter() || !VampireCourtRules.navigationDue(state.lastNavigationAt(), now)) return state;
        member.courtCounters().navigationRequests++;
        final boolean accepted = member.getNavigation().moveTo(x, y, z, reachRange, speed);
        state = state.withCadence(
            state.nextDecisionAt(), state.nextEntityScanAt(), state.nextShelterScanAt(),
            state.nextFeedbackAt(), now
        );
        final VampireCourtState routed = state.recordRouteResult(accepted, now);
        if (!accepted && routed.retryAfter() > now) {
            state.targetId().ifPresent(id -> releaseClaim(
                (ServerLevel) member.level(), ClaimKey.prey(id), member.getUUID()
            ));
            state.shelter().ifPresent(position -> releaseClaim(
                (ServerLevel) member.level(), ClaimKey.shelter(position), member.getUUID()
            ));
        }
        return routed;
    }

    public static boolean eligibleTarget(final VampireCourtEntity member, final LivingEntity target) {
        if (!baseAdmission(member, target)) return false;
        final boolean directAggressor = member.courtState().recentAttacker()
            .filter(target.getUUID()::equals).isPresent()
            && member.courtState().attackerExpiresAt() > member.level().getGameTime();
        if (target instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
            && VillageAssaultRuntime.isAssignedVampireObjective(member, target)) {
            return true;
        }
        return courtRelationAllows(member, target, directAggressor);
    }

    private static boolean baseAdmission(final VampireCourtEntity member, final LivingEntity target) {
        return target != null && target != member && target.isAlive() && target.level() == member.level()
            && !target.isSpectator()
            && (!(target instanceof ServerPlayer player) || !player.isCreative())
            && target.canBeSeenAsEnemy()
            && member.courtBaseMayAttack(target);
    }

    private static boolean courtRelationAllows(
        final VampireCourtEntity member,
        final LivingEntity target,
        final boolean directAggressor
    ) {
        if (target instanceof NamiEntity || target instanceof NaamahEntity || target instanceof Villager
            || target instanceof IronGolem || target instanceof Turtle) {
            return directAggressor;
        }
        if (target instanceof ArcaneCreature arcane
            && (arcane.creatureKind() == CreatureKind.VAMPIRE
                || arcane.creatureKind() == CreatureKind.BLOOD_THRALL
                || arcane.creatureKind() == CreatureKind.NAAMAH)) {
            return directAggressor;
        }
        if (target instanceof ServerPlayer player) {
            final boolean vampirePlayer = SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE;
            return VampireCourtRules.eligibleOrdinaryPrey(
                true, vampirePlayer, false, false, false, directAggressor
            );
        }
        return directAggressor;
    }

    private static boolean eligibleDirectAttacker(
        final VampireCourtEntity member,
        final LivingEntity target,
        final long now
    ) {
        return baseAdmission(member, target) && courtRelationAllows(member, target, true);
    }

    public static boolean meleeExecutorMayRun(final VampireCourtEntity member) {
        if (!(member.level() instanceof ServerLevel level)) return false;
        final long now = level.getGameTime();
        VampireCourtState state = revalidateAttacker(member, level, member.courtState().reconcileAfterLoad(now), now);
        member.setCourtState(state);
        state = revalidateTarget(member, level, state, now);
        member.setCourtState(state);
        final LivingEntity target = member.getTarget();
        if (target == null || !eligibleTarget(member, target)) return false;
        final Intent intent = member.courtState().intent();
        return intent == Intent.STALK || intent == Intent.FEED || intent == Intent.INTERCEPT
            || intent == Intent.ASSAULT_LEAD;
    }

    public static void rememberAttacker(
        final VampireCourtEntity member,
        final Entity attacker,
        final long now
    ) {
        if (!(attacker instanceof LivingEntity living)
            || !baseAdmission(member, living)
            || !courtRelationAllows(member, living, true)) return;
        member.setCourtState(member.courtState().rememberAttacker(
            attacker.getUUID(), VampireCourtRules.saturatingAdd(now, ATTACKER_MEMORY_TICKS)
        ));
    }

    public static void afterSuccessfulAttack(
        final VampireCourtEntity member,
        final Entity target,
        final long now
    ) {
        if (member.creatureKind() != CreatureKind.VAMPIRE || !(target instanceof LivingEntity living)) return;
        if (member.level() instanceof ServerLevel level) {
            releaseClaim(level, ClaimKey.prey(living.getUUID()), member.getUUID());
        }
        final BlockPos position = living.blockPosition();
        member.setCourtState(member.courtState().afterOrdinaryFeed(now).rememberVictim(
            new VictimReport(living.getUUID(), position.getX(), position.getY(), position.getZ(),
                now, ReportOutcome.FED, 10), now
        ));
    }

    public static void afterAssaultFeed(final VampireCourtEntity leader, final long now) {
        if (leader.creatureKind() == CreatureKind.VAMPIRE
            && leader.courtState().assaultRole() == AssaultRole.PREDATOR_LEADER) {
            leader.setCourtState(leader.courtState().afterAssaultFeed(now));
        }
    }

    public static void markAssaultLeader(final VampireCourtEntity leader) {
        if (leader.creatureKind() == CreatureKind.VAMPIRE) {
            leader.setCourtState(leader.courtState().withAssaultRole(AssaultRole.PREDATOR_LEADER));
        }
    }

    public static void bindAssaultMember(
        final VampireCourtEntity thrall,
        final VampireCourtEntity leader
    ) {
        if (thrall.creatureKind() != CreatureKind.BLOOD_THRALL || !validMaster(thrall, leader)) return;
        thrall.setCourtState(thrall.courtState().withMaster(leader.getUUID(), AssaultRole.BOUND_GUARD));
    }

    private static boolean validMaster(
        final VampireCourtEntity thrall,
        final VampireCourtEntity leader
    ) {
        return leader.isAlive() && leader.creatureKind() == CreatureKind.VAMPIRE
            && leader.level() == thrall.level()
            && sameCourt(thrall, leader);
    }

    private static boolean sameCourt(final VampireCourtEntity first, final VampireCourtEntity second) {
        if (!VillageAssaultRuntime.isAssaultRaider(first) && !VillageAssaultRuntime.isAssaultRaider(second)) {
            return true;
        }
        return VillageAssaultRuntime.isAssaultRaider(first) && VillageAssaultRuntime.isAssaultRaider(second)
            && WarlockeryEntityData.get(first).getLongOr(VillageAssaultRuntime.ASSAULT_CENTER, Long.MIN_VALUE)
                == WarlockeryEntityData.get(second).getLongOr(VillageAssaultRuntime.ASSAULT_CENTER, Long.MAX_VALUE)
            && WarlockeryEntityData.get(first).getIntOr(VillageAssaultRuntime.ASSAULT_WAVE, -1)
                == WarlockeryEntityData.get(second).getIntOr(VillageAssaultRuntime.ASSAULT_WAVE, -2);
    }

    public static final class Counters {
        private long decisions;
        private long entityScans;
        private long candidateAppraisals;
        private long shelterScans;
        private long shelterBlocks;
        private long navigationRequests;
        private long cancellations;

        public long decisions() { return decisions; }
        public long entityScans() { return entityScans; }
        public long candidateAppraisals() { return candidateAppraisals; }
        public long shelterScans() { return shelterScans; }
        public long shelterBlocks() { return shelterBlocks; }
        public long navigationRequests() { return navigationRequests; }
        public long cancellations() { return cancellations; }
    }

    private static VampireCourtState revalidateShelterClaim(
        final VampireCourtEntity member,
        final ServerLevel level,
        final VampireCourtState state,
        final long now
    ) {
        if (state.shelter().isEmpty()) return state;
        final BlockPos shelter = state.shelter().orElseThrow();
        final boolean valid = state.shelterExpiresAt() > now
            && state.shelterDimension().filter(level.dimension().identifier().toString()::equals).isPresent()
            && level.hasChunkAt(shelter) && !level.canSeeSky(shelter)
            && ownedClaim(level, ClaimKey.shelter(shelter), member.getUUID(), now);
        if (valid) return state;
        releaseClaim(level, ClaimKey.shelter(shelter), member.getUUID());
        member.getNavigation().stop();
        return state.withoutShelter();
    }

    private static VampireCourtState releaseClaimsForTransition(
        final VampireCourtEntity member,
        final ServerLevel level,
        VampireCourtState state,
        final Intent next
    ) {
        final boolean keepsPrey = next == Intent.STALK || next == Intent.WATCH || next == Intent.FEED
            || next == Intent.ASSAULT_LEAD || next == Intent.INTERCEPT;
        if (!keepsPrey && state.targetId().isPresent()) {
            state.targetId().ifPresent(id -> releaseClaim(level, ClaimKey.prey(id), member.getUUID()));
            member.setTarget(null);
            state = state.withTarget(null, 0L);
        }
        final boolean keepsShelter = next == Intent.SEEK_SHELTER || next == Intent.ROOST;
        if (!keepsShelter && state.shelter().isPresent()) {
            state.shelter().ifPresent(position -> releaseClaim(
                level, ClaimKey.shelter(position), member.getUUID()
            ));
            state = state.withoutShelter();
        }
        return state;
    }

    private static boolean acquireClaim(
        final ServerLevel level,
        final ClaimKey key,
        final UUID owner,
        final long now
    ) {
        final Map<ClaimKey, Claim> claims = CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>());
        claims.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now
            || level.getEntity(entry.getValue().owner()) == null);
        final Claim existing = claims.get(key);
        if (existing != null && !existing.owner().equals(owner)) return false;
        if (existing == null && claims.size() >= MAX_LEVEL_CLAIMS) return false;
        claims.put(key, new Claim(owner, VampireCourtRules.claimExpiry(now, (int) TARGET_LEASE_TICKS)));
        return true;
    }

    static boolean tryClaimShelter(
        final VampireCourtEntity member,
        final ServerLevel level,
        final BlockPos shelter,
        final long now
    ) {
        return acquireClaim(level, ClaimKey.shelter(shelter), member.getUUID(), now);
    }

    static boolean tryClaimPrey(
        final VampireCourtEntity member,
        final ServerLevel level,
        final UUID prey,
        final long now
    ) {
        return acquireClaim(level, ClaimKey.prey(prey), member.getUUID(), now);
    }

    private static boolean ownedClaim(
        final ServerLevel level,
        final ClaimKey key,
        final UUID owner,
        final long now
    ) {
        final Map<ClaimKey, Claim> claims = CLAIMS.get(level);
        if (claims == null) return false;
        final Claim claim = claims.get(key);
        return claim != null && claim.expiresAt() > now && claim.owner().equals(owner);
    }

    private static void releaseClaim(final ServerLevel level, final ClaimKey key, final UUID owner) {
        final Map<ClaimKey, Claim> claims = CLAIMS.get(level);
        if (claims == null) return;
        final Claim claim = claims.get(key);
        if (claim != null && claim.owner().equals(owner)) claims.remove(key);
        if (claims.isEmpty()) CLAIMS.remove(level);
    }

    private record ClaimKey(String kind, String value) {
        private static ClaimKey prey(final UUID id) {
            return new ClaimKey("prey", id.toString());
        }

        private static ClaimKey shelter(final BlockPos position) {
            return new ClaimKey("shelter", Long.toString(position.asLong()));
        }
    }

    private record Claim(UUID owner, long expiresAt) {
    }
}
