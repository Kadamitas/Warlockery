package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.AmbientMode;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Candidate;
import com.kadamitas.warlockery.entity.NaamahCourtRules.CandidateType;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Server-only, bounded executor for Naamah's court encounter. */
public final class NaamahCourtRuntime {
    private static final long CHALLENGER_MEMORY_TICKS = 200L;
    private static final long ATTACKER_MEMORY_TICKS = 200L;
    private static final long DESTINATION_MEMORY_TICKS = 60L;
    private static final double COLLISION_EPSILON = 1.0E-7D;
    private static final TagKey<net.minecraft.world.level.block.Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );

    private static final class BlockReadBudget {
        private final Map<Long, BlockState> states = new HashMap<>(NaamahCourtRules.MAX_DESTINATION_BLOCKS);
        private int chargedReads;

        Optional<BlockState> read(final ServerLevel level, final BlockPos position) {
            final BlockState known = states.get(position.asLong());
            if (known != null) {
                return Optional.of(known);
            }
            if (chargedReads >= NaamahCourtRules.MAX_DESTINATION_BLOCKS || !level.isLoaded(position)) {
                return Optional.empty();
            }
            final BlockState state = level.getBlockState(position);
            states.put(position.asLong(), state);
            chargedReads++;
            return Optional.of(state);
        }

        boolean reserve(final int maximumInternalReads) {
            if (maximumInternalReads < 0
                || chargedReads + maximumInternalReads > NaamahCourtRules.MAX_DESTINATION_BLOCKS) {
                return false;
            }
            chargedReads += maximumInternalReads;
            return true;
        }

        int used() {
            return chargedReads;
        }
    }

    private record HazardObservation(boolean active, boolean requiresShade, Optional<Hazard> hazard) {
    }

    public static final class Counters {
        private int maximumCandidatesRetained;
        private int maximumBlockStatesPerSearch;
        private long candidateScans;
        private long destinationSearches;
        private long navigationRequests;
        private long localHazardScans;
        private long localHazardBlockStateReads;
        private int maximumLocalHazardBlockStatesPerScan;
        private int maximumEntitiesVisitedPerCandidateScan;
        private int maximumEntitiesVisitedPerWave;
        long mendedPulses;
        long bindsApplied;
        long surgesCalled;
        long surgeVictims;

        public long mendedPulses() {
            return mendedPulses;
        }

        public long bindsApplied() {
            return bindsApplied;
        }

        public long surgesCalled() {
            return surgesCalled;
        }

        public long surgeVictims() {
            return surgeVictims;
        }

        public int maximumCandidatesRetained() {
            return maximumCandidatesRetained;
        }

        public int maximumBlockStatesPerSearch() {
            return maximumBlockStatesPerSearch;
        }

        public long candidateScans() {
            return candidateScans;
        }

        public long destinationSearches() {
            return destinationSearches;
        }

        public long navigationRequests() {
            return navigationRequests;
        }

        public long localHazardScans() {
            return localHazardScans;
        }

        public long localHazardBlockStateReads() {
            return localHazardBlockStateReads;
        }

        public int maximumLocalHazardBlockStatesPerScan() {
            return maximumLocalHazardBlockStatesPerScan;
        }

        public int maximumEntitiesVisitedPerCandidateScan() {
            return maximumEntitiesVisitedPerCandidateScan;
        }

        public int maximumEntitiesVisitedPerWave() {
            return maximumEntitiesVisitedPerWave;
        }

        private void candidates(final int count, final int visited) {
            candidateScans++;
            maximumCandidatesRetained = Math.max(maximumCandidatesRetained, count);
            maximumEntitiesVisitedPerCandidateScan = Math.max(maximumEntitiesVisitedPerCandidateScan, visited);
        }

        private void wave(final int count, final int visited) {
            maximumCandidatesRetained = Math.max(maximumCandidatesRetained, count);
            maximumEntitiesVisitedPerWave = Math.max(maximumEntitiesVisitedPerWave, visited);
        }

        private void blocks(final int count) {
            destinationSearches++;
            maximumBlockStatesPerSearch = Math.max(maximumBlockStatesPerSearch, count);
        }

        private void localHazard(final int count) {
            localHazardScans++;
            localHazardBlockStateReads += count;
            maximumLocalHazardBlockStatesPerScan = Math.max(maximumLocalHazardBlockStatesPerScan, count);
        }

        private void navigation() {
            navigationRequests++;
        }

    }

    private NaamahCourtRuntime() {
    }

    public static void rememberAttacker(final NaamahEntity naamah, final Entity attacker, final long now) {
        if (attacker instanceof LivingEntity living && !protectedTarget(naamah, living)
            && NaamahCourtRules.mayAttack(living.getUUID(), naamah.courtState().audienceConcluded(),
                naamah.courtState().concludedOwner())) {
            naamah.setCourtState(naamah.courtState().rememberAttacker(attacker.getUUID(), now + ATTACKER_MEMORY_TICKS));
        }
    }

    public static void tick(final NaamahEntity naamah, final ServerLevel level) {
        if (!naamah.isAlive()) {
            return;
        }
        final long now = level.getGameTime();
        naamah.setAirSupply(naamah.getMaxAirSupply());

        NaamahCourtState state = naamah.courtState().reconcile(now);
        if (state.anchor().isEmpty()) {
            state = state.withAnchor(level.dimension().identifier().toString(), naamah.blockPosition())
                .withSchedule(
                    NaamahCourtRules.staggeredDeadline(now, naamah.getId(), NaamahCourtRules.DECISION_INTERVAL_TICKS),
                    NaamahCourtRules.staggeredDeadline(now, naamah.getId(), NaamahCourtRules.CANDIDATE_SCAN_INTERVAL_TICKS),
                    NaamahCourtRules.staggeredDeadline(now, naamah.getId(), NaamahCourtRules.EXPENSIVE_SCAN_INTERVAL_TICKS),
                    NaamahCourtRules.staggeredDeadline(now, naamah.getId(), 200), now
                );
        }
        state = state.latchPhase(naamah.getHealth(), naamah.getMaxHealth());
        naamah.setCourtState(state);

        releaseInvalidTarget(naamah, now);
        final HazardObservation immediateHazard = observeImmediateHazard(naamah, level);
        final boolean localScanDue = now >= naamah.courtState().nextShadeScanAt();
        final BlockReadBudget hazardBudget = new BlockReadBudget();
        if (localScanDue) {
            final Optional<Hazard> observed = scanLocalHazard(naamah, level, hazardBudget);
            naamah.courtCounters().localHazard(hazardBudget.used());
            naamah.setCourtState(naamah.courtState().withLocalHazard(observed));
        }
        final HazardObservation hazard = combineHazards(immediateHazard, naamah.courtState().localHazard());
        if (hazard.active()) {
            if (naamah.courtState().action() != Action.NONE) {
                naamah.setCourtState(naamah.courtState().cancelAction(now));
            }
            if (localScanDue && now >= naamah.courtState().retryAfter()) {
                findHazardDestination(naamah, level, now, hazard, hazardBudget);
            } else if (localScanDue) {
                scheduleNextLocalHazardScan(naamah, now);
            }
            navigateToRememberedDestination(naamah, level, now);
            naamah.updateCourtBossBar();
            return;
        }
        if (localScanDue) {
            scheduleNextLocalHazardScan(naamah, now);
        }
        tickRegeneration(naamah, level, now);
        if (executeTelegraphedAction(naamah, level, now)) {
            naamah.updateCourtBossBar();
            return;
        }
        if (naamah.courtState().action() != Action.NONE) {
            naamah.updateCourtBossBar();
            return;
        }

        state = naamah.courtState();
        if (now >= state.nextCandidateScanAt()) {
            scanCandidates(naamah, level, now);
        }
        decideAction(naamah, level, now);
        approachChallenger(naamah, now);
        returnToCourtAnchor(naamah, level, now);
        ambientCourtFeedback(naamah, level, now);
        naamah.updateCourtBossBar();
    }

    public static boolean eligibleTarget(final NaamahEntity naamah, final LivingEntity target) {
        if (protectedTarget(naamah, target)) {
            return false;
        }
        if (target instanceof NamiEntity || target instanceof AbstractVillager
            || target instanceof AbstractGolem || target instanceof Turtle || target instanceof NaamahEntity) {
            return false;
        }
        if (target instanceof ArcaneCreature creature) {
            final CreatureKind kind = creature.creatureKind();
            if (kind == CreatureKind.NAAMAH || kind == CreatureKind.VAMPIRE || kind == CreatureKind.BLOOD_THRALL) {
                return false;
            }
        }
        if (!NaamahCourtRules.mayAttack(target.getUUID(), naamah.courtState().audienceConcluded(),
            naamah.courtState().concludedOwner())) {
            return false;
        }
        return target instanceof Player || naamah.courtState().recentAttacker().filter(target.getUUID()::equals).isPresent()
            && naamah.courtState().attackerExpiresAt() > naamah.level().getGameTime();
    }

    private static boolean protectedTarget(final NaamahEntity naamah, final LivingEntity target) {
        if (target == naamah || !target.isAlive() || target.isSpectator()
            || target instanceof Player player && player.isCreative()
            || !target.canBeSeenAsEnemy()) return true;
        if (target instanceof NamiEntity || target instanceof AbstractVillager
            || target instanceof AbstractGolem || target instanceof Turtle || target instanceof NaamahEntity) return true;
        if (target instanceof ArcaneCreature creature) {
            final CreatureKind kind = creature.creatureKind();
            return kind == CreatureKind.NAAMAH || kind == CreatureKind.VAMPIRE || kind == CreatureKind.BLOOD_THRALL;
        }
        return false;
    }

    public static boolean challengerApproachMayRun(final NaamahEntity naamah) {
        return naamah.courtState().action() == Action.NONE
            && naamah.level().getGameTime() >= naamah.courtState().recoverUntil()
            && naamah.getTarget() != null
            && eligibleTarget(naamah, naamah.getTarget());
    }

    public static boolean meleeExecutorMayRun(final NaamahEntity naamah) {
        final LivingEntity target = naamah.getTarget();
        return target != null && challengerApproachMayRun(naamah)
            && naamah.isWithinMeleeAttackRange(target)
            && naamah.getSensing().hasLineOfSight(target);
    }

    private static void releaseInvalidTarget(final NaamahEntity naamah, final long now) {
        final LivingEntity target = naamah.getTarget();
        if (target != null && (!eligibleTarget(naamah, target)
            || target.level() != naamah.level()
            || target.distanceToSqr(naamah) > NaamahCourtRules.CANDIDATE_RADIUS * NaamahCourtRules.CANDIDATE_RADIUS)) {
            naamah.setTarget(null);
            naamah.getNavigation().stop();
            naamah.setCourtState(naamah.courtState().releaseChallenger(now));
        }
    }

    private static void scanCandidates(final NaamahEntity naamah, final ServerLevel level, final long now) {
        final NaamahCourtRules.CandidateAccumulator retained = new NaamahCourtRules.CandidateAccumulator();
        final double radiusSquared = NaamahCourtRules.CANDIDATE_RADIUS * NaamahCourtRules.CANDIDATE_RADIUS;
        final Optional<UUID> trialOwner = activeTrialOwner(naamah, level);
        final Optional<UUID> recentAttacker = naamah.courtState().recentAttacker()
            .filter(id -> naamah.courtState().attackerExpiresAt() > now);
        final Optional<UUID> currentChallenger = naamah.courtState().challenger()
            .filter(id -> naamah.courtState().challengerExpiresAt() > now);
        trialOwner.ifPresent(id -> preseedCandidate(naamah, level, retained, id, trialOwner, recentAttacker,
            currentChallenger, radiusSquared));
        recentAttacker.ifPresent(id -> preseedCandidate(naamah, level, retained, id, trialOwner, recentAttacker,
            currentChallenger, radiusSquared));
        currentChallenger.ifPresent(id -> preseedCandidate(naamah, level, retained, id, trialOwner, recentAttacker,
            currentChallenger, radiusSquared));

        final int[] visited = {0};
        final AABB bounds = naamah.getBoundingBox().inflate(NaamahCourtRules.CANDIDATE_RADIUS);
        level.getEntities().get(EntityTypeTest.forClass(LivingEntity.class), bounds, entity -> {
            visited[0]++;
            if (entity != naamah && entity.distanceToSqr(naamah) <= radiusSquared && eligibleTarget(naamah, entity)) {
                retained.accept(candidate(naamah, entity, trialOwner, recentAttacker, currentChallenger));
            }
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        final List<Candidate> candidates = retained.snapshot();
        naamah.courtCounters().candidates(candidates.size(), visited[0]);

        final LivingEntity challenger = NaamahCourtRules.chooseChallenger(candidates)
            .map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
            .filter(entity -> entity.distanceToSqr(naamah) <= radiusSquared && eligibleTarget(naamah, entity))
            .orElse(null);
        if (challenger != null) {
            naamah.setTarget(challenger);
            naamah.setCourtState(naamah.courtState().withChallenger(challenger.getUUID(), now + CHALLENGER_MEMORY_TICKS));
        } else if (naamah.getTarget() == null) {
            naamah.setCourtState(naamah.courtState().releaseChallenger(now));
        }
        final NaamahCourtState state = naamah.courtState();
        naamah.setCourtState(state.withSchedule(state.nextDecisionAt(), now + NaamahCourtRules.CANDIDATE_SCAN_INTERVAL_TICKS,
            state.nextShadeScanAt(), state.nextAmbientFeedbackAt(), state.lastNavigationAt()));
    }

    private static void preseedCandidate(
        final NaamahEntity naamah,
        final ServerLevel level,
        final NaamahCourtRules.CandidateAccumulator retained,
        final UUID id,
        final Optional<UUID> trialOwner,
        final Optional<UUID> recentAttacker,
        final Optional<UUID> currentChallenger,
        final double radiusSquared
    ) {
        final Entity entity = level.getEntity(id);
        if (entity instanceof LivingEntity living && living.distanceToSqr(naamah) <= radiusSquared
            && eligibleTarget(naamah, living)) {
            retained.accept(candidate(naamah, living, trialOwner, recentAttacker, currentChallenger));
        }
    }

    private static Candidate candidate(
        final NaamahEntity naamah,
        final LivingEntity entity,
        final Optional<UUID> trialOwner,
        final Optional<UUID> recentAttacker,
        final Optional<UUID> currentChallenger
    ) {
        return new Candidate(
            entity.getUUID(), candidateType(entity), recentAttacker.filter(entity.getUUID()::equals).isPresent(),
            trialOwner.filter(entity.getUUID()::equals).isPresent(),
            currentChallenger.filter(entity.getUUID()::equals).isPresent(), entity.distanceToSqr(naamah)
        );
    }

    private static Optional<UUID> activeTrialOwner(final NaamahEntity naamah, final ServerLevel level) {
        final String text = naamah.getPersistentData().getStringOr(SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER, "");
        try {
            final UUID id = UUID.fromString(text);
            final ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            return player != null && player.level() == level && player.isAlive()
                && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
                && SupernaturalProgression.level(player, SupernaturalProgression.Path.VAMPIRE) == 6
                    ? Optional.of(id) : Optional.empty();
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static CandidateType candidateType(final LivingEntity entity) {
        if (entity instanceof Player) return CandidateType.PLAYER;
        if (entity instanceof NamiEntity) return CandidateType.NAMI;
        if (entity instanceof AbstractVillager) return CandidateType.VILLAGER;
        if (entity instanceof AbstractGolem) return CandidateType.GOLEM;
        if (entity instanceof Turtle) return CandidateType.TURTLE;
        if (entity instanceof NaamahEntity) return CandidateType.NAAMAH;
        if (entity instanceof ArcaneCreature creature) {
            if (creature.creatureKind() == CreatureKind.VAMPIRE) return CandidateType.VAMPIRE;
            if (creature.creatureKind() == CreatureKind.BLOOD_THRALL) return CandidateType.BLOOD_THRALL;
        }
        return CandidateType.OTHER;
    }

    private static boolean executeTelegraphedAction(final NaamahEntity naamah, final ServerLevel level, final long now) {
        final NaamahCourtState state = naamah.courtState();
        if (state.action() == Action.NONE || now < state.actionExecuteAt()) {
            return false;
        }
        final String dimension = level.dimension().identifier().toString();
        final Optional<UUID> boundTarget = state.actionTarget();
        final LivingEntity currentTarget = naamah.getTarget();
        final boolean replaced = currentTarget != null
            && boundTarget.filter(currentTarget.getUUID()::equals).isEmpty();
        final LivingEntity target = state.actionDimension().filter(dimension::equals)
            .flatMap(ignored -> boundTarget)
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .orElse(null);
        final boolean valid = !replaced && target != null
            && state.challenger().filter(target.getUUID()::equals).isPresent()
            && state.challengerExpiresAt() > now
            && eligibleTarget(naamah, target)
            && target.level() == level
            && target.distanceToSqr(naamah)
                <= NaamahCourtRules.CANDIDATE_RADIUS * NaamahCourtRules.CANDIDATE_RADIUS;
        if (!valid) {
            naamah.setCourtState(state.cancelAction(now));
            naamah.setTarget(null);
            naamah.getNavigation().stop();
            return true;
        }
        if (naamah.getTarget() != target) {
            naamah.setTarget(target);
        }

        level.sendParticles(ParticleTypes.ENCHANT, naamah.getX(), naamah.getY() + 1.0D, naamah.getZ(),
            24, 1.2D, 0.8D, 1.2D, 0.05D);
        level.playSound(null, naamah.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
            SoundSource.HOSTILE, 1.0F, state.action() == Action.COURT_WAVE ? 0.7F : 1.1F);

        if (state.action() == Action.COURT_WAVE) {
            executeCourtWave(naamah, level, now);
        } else if (state.action() == Action.TENTACLE_BIND) {
            executeTentacleBind(naamah, level, target);
        } else if (state.action() == Action.DROWNING_SURGE) {
            executeDrowningSurge(naamah, level, target, now);
        } else if (state.action() == Action.DREAM_APPROACH) {
            if (!NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)) {
                naamah.setCourtState(state.cancelAction(now));
                naamah.getNavigation().stop();
                return true;
            }
            if (!requestNavigation(naamah, target, 1.05D, now)) {
                final NaamahCourtState failedRoute = naamah.courtState();
                if (now < failedRoute.retryAfter()) {
                    naamah.setCourtState(failedRoute.cancelAction(now));
                    return true;
                }
                final Optional<BlockPos> fallback = localStepToward(
                    naamah, level, target.blockPosition(), false, Optional.empty(), new BlockReadBudget()
                );
                if (fallback.isEmpty()) {
                    naamah.setCourtState(failedRoute.cancelAction(now));
                    return true;
                }
                final BlockPos destination = fallback.orElseThrow();
                naamah.teleportTo(
                    destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D
                );
                naamah.setCourtState(naamah.courtState().recordRouteResult(true, now));
            }
        } else {
            final Optional<BlockPos> safe = state.destination()
                .filter(destination -> NaamahCourtRules.withinLocalStep(naamah.blockPosition(), destination))
                .filter(destination -> safeDestination(naamah, level, destination));
            if (safe.isEmpty()) {
                naamah.setCourtState(state.cancelAction(now));
                naamah.getNavigation().stop();
                return true;
            }
            final BlockPos destination = safe.orElseThrow();
            naamah.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
            naamah.setCourtState(naamah.courtState().recordRouteResult(true, now));
        }
        if (state.action() != Action.DREAM_APPROACH) naamah.getNavigation().stop();
        naamah.setCourtState(naamah.courtState().finishAction());
        return true;
    }

    /**
     * Holds one challenger where they stand. The tentacles take the challenger the action was
     * telegraphed against and nobody else, so this never becomes a second area attack.
     */
    private static void executeTentacleBind(
        final NaamahEntity naamah,
        final ServerLevel level,
        final LivingEntity target
    ) {
        naamah.courtCounters().bindsApplied++;
        target.addEffect(new MobEffectInstance(
            MobEffects.SLOWNESS,
            NaamahCourtRules.BIND_DURATION_TICKS,
            NaamahCourtRules.BIND_SLOWNESS_AMPLIFIER,
            false,
            true
        ));
        target.hurtServer(level, level.damageSources().indirectMagic(naamah, naamah),
            NaamahCourtRules.BIND_DAMAGE);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
            target.getX(), target.getY(), target.getZ(), 32, 0.4D, 0.1D, 0.4D, 0.08D);
        level.playSound(null, target.blockPosition(), SoundEvents.DROWNED_HURT,
            SoundSource.HOSTILE, 0.9F, 0.6F);
    }

    /**
     * Breaks a column of water over the challenger's ground. Unlike the bind this deliberately
     * catches everything standing with them, but it is still bounded by the same candidate cap the
     * wave uses, and it never touches anyone her court refuses to strike.
     */
    private static void executeDrowningSurge(
        final NaamahEntity naamah,
        final ServerLevel level,
        final LivingEntity target,
        final long now
    ) {
        naamah.courtCounters().surgesCalled++;
        final AABB area = new AABB(target.blockPosition()).inflate(NaamahCourtRules.SURGE_RADIUS);
        int struck = 0;
        for (final LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (struck >= NaamahCourtRules.MAX_CANDIDATES) {
                break;
            }
            if (caught == naamah || !caught.isAlive() || !eligibleTarget(naamah, caught)) {
                continue;
            }
            struck++;
            naamah.courtCounters().surgeVictims++;
            caught.hurtServer(level, level.damageSources().indirectMagic(naamah, naamah),
                NaamahCourtRules.SURGE_DAMAGE);
        }
        level.sendParticles(ParticleTypes.FALLING_WATER,
            target.getX(), target.getY() + 3.0D, target.getZ(), 60, 2.0D, 0.5D, 2.0D, 0.1D);
        level.playSound(null, target.blockPosition(), SoundEvents.CONDUIT_DEACTIVATE,
            SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    private static void executeCourtWave(
        final NaamahEntity naamah,
        final ServerLevel level,
        final long now
    ) {
        final List<LivingEntity> victims = new ArrayList<>(NaamahCourtRules.MAX_CANDIDATES);
        final Set<UUID> inspected = new HashSet<>(NaamahCourtRules.MAX_CANDIDATES);
        final double radiusSquared = NaamahCourtRules.WAVE_RADIUS * NaamahCourtRules.WAVE_RADIUS;
        final Optional<UUID> trialOwner = activeTrialOwner(naamah, level);
        final Optional<UUID> recentAttacker = naamah.courtState().recentAttacker()
            .filter(id -> naamah.courtState().attackerExpiresAt() > now);
        final Optional<UUID> currentChallenger = naamah.courtState().challenger()
            .filter(id -> naamah.courtState().challengerExpiresAt() > now);
        inspectWaveCandidate(naamah, naamah.getTarget(), radiusSquared, inspected, victims);
        trialOwner.map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
            .ifPresent(entity -> inspectWaveCandidate(naamah, entity, radiusSquared, inspected, victims));
        recentAttacker.map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
            .ifPresent(entity -> inspectWaveCandidate(naamah, entity, radiusSquared, inspected, victims));
        currentChallenger.map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
            .ifPresent(entity -> inspectWaveCandidate(naamah, entity, radiusSquared, inspected, victims));

        final AABB bounds = naamah.getBoundingBox().inflate(NaamahCourtRules.WAVE_RADIUS);
        if (inspected.size() < NaamahCourtRules.MAX_CANDIDATES) {
            level.getEntities().get(EntityTypeTest.forClass(LivingEntity.class), bounds, entity ->
                inspectWaveCandidate(naamah, entity, radiusSquared, inspected, victims)
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE
            );
        }
        naamah.courtCounters().wave(victims.size(), inspected.size());
        for (final LivingEntity victim : victims) {
            if (!eligibleTarget(naamah, victim) || victim.distanceToSqr(naamah) > radiusSquared) continue;
            final var source = level.damageSources().indirectMagic(naamah, naamah);
            if (victim.hurtServer(level, source, NaamahCourtRules.WAVE_DAMAGE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
                final Vec3 delta = victim.position().subtract(naamah.position());
                victim.knockback(0.45D, -delta.x, -delta.z, source, NaamahCourtRules.WAVE_DAMAGE);
            }
        }
    }

    private static boolean inspectWaveCandidate(
        final NaamahEntity naamah,
        final LivingEntity entity,
        final double radiusSquared,
        final Set<UUID> inspected,
        final List<LivingEntity> victims
    ) {
        if (entity == null || entity == naamah || inspected.contains(entity.getUUID())) {
            return inspected.size() >= NaamahCourtRules.MAX_CANDIDATES;
        }
        if (inspected.size() >= NaamahCourtRules.MAX_CANDIDATES) return true;
        inspected.add(entity.getUUID());
        if (entity.distanceToSqr(naamah) <= radiusSquared && eligibleTarget(naamah, entity)) {
            victims.add(entity);
        }
        return inspected.size() >= NaamahCourtRules.MAX_CANDIDATES;
    }

    /**
     * Mends her while she holds her challenger in sight, and shuts that off when the sight breaks.
     *
     * <p>The gaze is the whole counterplay, so it is read from live line of sight every second
     * rather than from anything she remembers: a challenger who steps behind a monument pillar has
     * stopped her healing at the instant they broke it, not at the next time she happened to look.
     * The suppression outlives the break so that stepping back out does not immediately resume
     * it.</p>
     */
    private static void tickRegeneration(
        final NaamahEntity naamah,
        final ServerLevel level,
        final long now
    ) {
        final LivingEntity challenger = naamah.getTarget();
        final boolean holdsGaze = challenger != null
            && challenger.isAlive()
            && challenger.level() == level
            // Direct, not through Sensing. This runs once a regeneration interval rather than
            // every tick, so the cache buys nothing here, and Sensing only refreshes on the AI
            // step: a Naamah whose AI is suspended would otherwise answer from a stale reading.
            && naamah.hasLineOfSight(challenger);
        if (!holdsGaze) {
            if (challenger != null) {
                naamah.suppressRegenerationUntil(now + NaamahCourtRules.GAZE_BREAK_SUPPRESSION_TICKS);
            }
            return;
        }
        if (now < naamah.nextRegenerationAt()) {
            return;
        }
        naamah.setNextRegenerationAt(now + NaamahCourtRules.REGENERATION_INTERVAL_TICKS);
        if (NaamahCourtRules.mayRegenerate(
            naamah.isAlive(), naamah.getHealth(), naamah.getMaxHealth(),
            true, now, naamah.regenerationSuppressedUntil()
        )) {
            naamah.heal(NaamahCourtRules.REGENERATION_PER_INTERVAL);
            naamah.courtCounters().mendedPulses++;
        }
    }

    private static void decideAction(final NaamahEntity naamah, final ServerLevel level, final long now) {
        NaamahCourtState state = naamah.courtState();
        if (now < state.nextDecisionAt()) {
            return;
        }
        state = state.withSchedule(now + NaamahCourtRules.DECISION_INTERVAL_TICKS,
            state.nextCandidateScanAt(), state.nextShadeScanAt(), state.nextAmbientFeedbackAt(),
            state.lastNavigationAt());
        naamah.setCourtState(state);
        if (state.action() != Action.NONE || now < state.recoverUntil() || now < state.retryAfter()
            || naamah.getTarget() == null || !eligibleTarget(naamah, naamah.getTarget())) {
            return;
        }
        final Action action = NaamahCourtRules.automaticAction(
            state.phase(), Math.floorDiv(now, NaamahCourtRules.DECISION_INTERVAL_TICKS)
        );
        if (action == Action.NONE) return;
        NaamahCourtState next = state;
        if (action == Action.VEIL_STEP) {
            final Optional<BlockPos> destination = localStepToward(
                naamah, level, naamah.getTarget().blockPosition(), false, Optional.empty(), new BlockReadBudget()
            );
            if (destination.isEmpty()) {
                naamah.setCourtState(next.recordRouteResult(false, now));
                return;
            } else {
                next = next.withDestination(destination.orElseThrow(), now + DESTINATION_MEMORY_TICKS);
            }
        }
        final LivingEntity target = naamah.getTarget();
        next = next.beginAction(action, now, target.getUUID(), level.dimension().identifier().toString());
        level.sendParticles(ParticleTypes.WITCH, naamah.getX(), naamah.getY() + 1.0D, naamah.getZ(),
            16, 0.7D, 0.9D, 0.7D, 0.02D);
        level.playSound(null, naamah.blockPosition(), SoundEvents.EVOKER_PREPARE_WOLOLO,
            SoundSource.HOSTILE, 0.9F, action == Action.COURT_WAVE ? 0.65F : 1.0F);
        naamah.setCourtState(next);
        naamah.getNavigation().stop();
    }

    private static HazardObservation observeImmediateHazard(
        final NaamahEntity naamah,
        final ServerLevel level
    ) {
        final long dayTime = level.getOverworldClockTime() % 24_000L;
        // Wet is safe: the same rule the rest of the sunlight-weak creatures follow. Submerged in
        // a monument she is out of the sun entirely, which is what makes the drowned court livable.
        final boolean exposedSun = (dayTime < 13_000L || dayTime > 23_000L)
            && level.canSeeSky(naamah.blockPosition()) && !naamah.hasEffect(MobEffects.FIRE_RESISTANCE)
            && !naamah.isInWaterOrRain();
        if (naamah.isInLava()) {
            return new HazardObservation(true, exposedSun, Optional.of(Hazard.LAVA));
        }
        if (naamah.isOnFire()) {
            return new HazardObservation(true, exposedSun, Optional.of(Hazard.FIRE));
        }
        return new HazardObservation(exposedSun, exposedSun, Optional.empty());
    }

    private static Optional<Hazard> scanLocalHazard(
        final NaamahEntity naamah,
        final ServerLevel level,
        final BlockReadBudget budget
    ) {
        Hazard observed = null;
        final BlockPos origin = naamah.blockPosition();
        for (int y = -1; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    final Optional<BlockState> read = budget.read(level, origin.offset(x, y, z));
                    if (read.isEmpty()) continue;
                    final BlockState state = read.orElseThrow();
                    if (state.getFluidState().is(FluidTags.LAVA)) {
                        observed = Hazard.LAVA;
                    } else if (observed != Hazard.LAVA && isFireHazard(state)) {
                        observed = Hazard.FIRE;
                    } else if (observed == null && state.is(CONTACT_HAZARDS)) {
                        observed = Hazard.CONTACT;
                    }
                }
            }
        }
        return Optional.ofNullable(observed);
    }

    private static HazardObservation combineHazards(
        final HazardObservation immediate,
        final Optional<Hazard> localHazard
    ) {
        return new HazardObservation(
            immediate.active() || localHazard.isPresent(), immediate.requiresShade(),
            immediate.hazard().or(() -> localHazard)
        );
    }

    private static void scheduleNextLocalHazardScan(final NaamahEntity naamah, final long now) {
        final NaamahCourtState state = naamah.courtState();
        naamah.setCourtState(state.withSchedule(
            state.nextDecisionAt(), state.nextCandidateScanAt(),
            now + NaamahCourtRules.EXPENSIVE_SCAN_INTERVAL_TICKS,
            state.nextAmbientFeedbackAt(), state.lastNavigationAt()
        ));
    }

    private static void findHazardDestination(
        final NaamahEntity naamah,
        final ServerLevel level,
        final long now,
        final HazardObservation hazard,
        final BlockReadBudget budget
    ) {
        final Optional<BlockPos> destination = localStepToward(
            naamah, level, naamah.blockPosition(), hazard.requiresShade(), hazard.hazard(), budget
        );
        NaamahCourtState state = naamah.courtState();
        if (destination.isPresent()) {
            state = state.withDestination(destination.orElseThrow(), now + DESTINATION_MEMORY_TICKS);
        } else {
            state = state.recordRouteResult(false, now);
        }
        naamah.setCourtState(state.withSchedule(state.nextDecisionAt(), state.nextCandidateScanAt(),
            now + NaamahCourtRules.EXPENSIVE_SCAN_INTERVAL_TICKS, state.nextAmbientFeedbackAt(), state.lastNavigationAt()));
    }

    private static Optional<BlockPos> localStepToward(
        final NaamahEntity naamah,
        final ServerLevel level,
        final BlockPos focus,
        final boolean requireShade,
        final Optional<Hazard> observedHazard,
        final BlockReadBudget budget
    ) {
        final BlockPos origin = naamah.blockPosition();
        final List<BlockPos> candidates = new ArrayList<>(201);
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                final BlockPos candidate = origin.offset(dx, 0, dz);
                if (NaamahCourtRules.withinLocalStep(origin, candidate)) candidates.add(candidate.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble((BlockPos position) -> position.distSqr(focus))
            .thenComparing(position -> position));
        BlockPos selected = null;
        for (final BlockPos candidate : candidates) {
            if (candidate.equals(origin)) continue;
            if (destinationSafe(naamah, level, candidate, requireShade, observedHazard, budget)) {
                selected = candidate;
                break;
            }
            if (budget.used() >= NaamahCourtRules.MAX_DESTINATION_BLOCKS) break;
        }
        naamah.courtCounters().blocks(budget.used());
        return Optional.ofNullable(selected);
    }

    static boolean safeDestination(final NaamahEntity naamah, final ServerLevel level, final BlockPos position) {
        return destinationSafe(naamah, level, position, false, Optional.empty(), new BlockReadBudget());
    }

    private static boolean destinationSafe(
        final NaamahEntity naamah,
        final ServerLevel level,
        final BlockPos position,
        final boolean requireShade,
        final Optional<Hazard> observedHazard,
        final BlockReadBudget budget
    ) {
        final Vec3 destination = new Vec3(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        final AABB bounds = naamah.getDimensions(naamah.getPose()).makeBoundingBox(destination);
        if (!completeFootprintLoaded(level, position) || !level.getWorldBorder().isWithinBounds(bounds)) {
            return false;
        }
        if (requireShade && level.canSeeSky(position.above())) {
            return false;
        }
        if (!level.noEntityCollision(naamah, bounds)) {
            return false;
        }
        // noEntityCollision only reports hard collision shapes, which in practice means boats and
        // shulkers: ordinary mobs and players have none, so it would happily land Naamah inside a
        // living body. A Veil Step is a teleport with no travel, so an occupied destination has to
        // be rejected outright rather than resolved by the usual push-apart on the next tick.
        if (!level.getEntities(naamah, bounds, occupant -> occupant instanceof LivingEntity
            && occupant.isAlive()).isEmpty()) {
            return false;
        }
        final BlockState floor = budget.read(level, position.below()).orElse(null);
        if (floor == null || !floor.blocksMotion() || !floor.getFluidState().isEmpty()) {
            return false;
        }
        for (int y = -1; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    final BlockState state = budget.read(level, position.offset(x, y, z)).orElse(null);
                    if (state == null || unsafeDestinationState(state, observedHazard)) {
                        return false;
                    }
                }
            }
        }
        if (!budget.reserve(maximumCollisionBlockReads(bounds))) {
            return false;
        }
        return level.noCollision(naamah, bounds);
    }

    private static boolean completeFootprintLoaded(final ServerLevel level, final BlockPos position) {
        for (int y = -2; y <= 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.isLoaded(position.offset(x, y, z))) return false;
                }
            }
        }
        return true;
    }

    private static int maximumCollisionBlockReads(final AABB bounds) {
        final int minX = (int)Math.floor(bounds.minX - COLLISION_EPSILON) - 1;
        final int maxX = (int)Math.floor(bounds.maxX + COLLISION_EPSILON) + 1;
        final int minY = (int)Math.floor(bounds.minY - COLLISION_EPSILON) - 1;
        final int maxY = (int)Math.floor(bounds.maxY + COLLISION_EPSILON) + 1;
        final int minZ = (int)Math.floor(bounds.minZ - COLLISION_EPSILON) - 1;
        final int maxZ = (int)Math.floor(bounds.maxZ + COLLISION_EPSILON) + 1;
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    private static boolean unsafeDestinationState(final BlockState state, final Optional<Hazard> observedHazard) {
        if (state.getFluidState().is(FluidTags.LAVA) || isFireHazard(state) || state.is(CONTACT_HAZARDS)) {
            return true;
        }
        return observedHazard.filter(hazard -> hazard == Hazard.DROWNING)
            .isPresent() && state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean isFireHazard(final BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.MAGMA_BLOCK);
    }

    private static void navigateToRememberedDestination(final NaamahEntity naamah, final ServerLevel level, final long now) {
        final NaamahCourtState state = naamah.courtState();
        if (state.action() != Action.NONE || now < state.retryAfter()
            || !NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)) {
            return;
        }
        final Optional<BlockPos> destination = state.destination().filter(position -> state.destinationExpiresAt() > now);
        if (destination.isEmpty()) return;
        requestNavigation(naamah, level, destination.orElseThrow(), 1.05D, now);
    }

    private static boolean requestNavigation(
        final NaamahEntity naamah,
        final ServerLevel level,
        final BlockPos position,
        final double speed,
        final long now
    ) {
        final NaamahCourtState state = naamah.courtState();
        if (!NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)) return false;
        naamah.courtCounters().navigation();
        final Path path = safeDestination(naamah, level, position)
            ? naamah.getNavigation().createPath(position, 0) : null;
        final boolean accepted = path != null && path.canReach() && naamah.getNavigation().moveTo(path, speed);
        if (!accepted) naamah.getNavigation().stop();
        final NaamahCourtState routed = state.recordRouteResult(accepted, now);
        naamah.setCourtState(routed.withSchedule(
            state.nextDecisionAt(), state.nextCandidateScanAt(), state.nextShadeScanAt(),
            state.nextAmbientFeedbackAt(), now));
        return accepted;
    }

    private static boolean requestNavigation(
        final NaamahEntity naamah,
        final LivingEntity target,
        final double speed,
        final long now
    ) {
        final NaamahCourtState state = naamah.courtState();
        if (!NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)) return false;
        naamah.courtCounters().navigation();
        final Path path = naamah.getNavigation().createPath(target, 0);
        final boolean accepted = path != null && path.canReach()
            && naamah.getNavigation().moveTo(path, speed);
        if (!accepted) naamah.getNavigation().stop();
        final NaamahCourtState routed = state.recordRouteResult(accepted, now);
        naamah.setCourtState(routed.withSchedule(
            state.nextDecisionAt(), state.nextCandidateScanAt(), state.nextShadeScanAt(),
            state.nextAmbientFeedbackAt(), now));
        return accepted;
    }

    private static void approachChallenger(final NaamahEntity naamah, final long now) {
        final NaamahCourtState state = naamah.courtState();
        final LivingEntity target = naamah.getTarget();
        if (target == null || !challengerApproachMayRun(naamah) || now < state.retryAfter()) return;
        if (naamah.isWithinMeleeAttackRange(target)
            && naamah.getSensing().hasLineOfSight(target)) {
            if (!naamah.getNavigation().isDone()) naamah.getNavigation().stop();
            return;
        }
        if (NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)) {
            requestNavigation(naamah, target, 1.05D, now);
        }
    }

    private static void returnToCourtAnchor(final NaamahEntity naamah, final ServerLevel level, final long now) {
        final NaamahCourtState state = naamah.courtState();
        if (naamah.getTarget() != null || state.action() != Action.NONE
            || now < state.retryAfter()
            || !NaamahCourtRules.navigationDue(state.lastNavigationAt(), now)
            || state.anchorDimension().filter(level.dimension().identifier().toString()::equals).isEmpty()) return;
        state.anchor().filter(level::isLoaded).filter(anchor -> anchor.distSqr(naamah.blockPosition()) > 64.0D)
            .ifPresent(anchor -> {
                requestNavigation(naamah, level, anchor, 0.9D, now);
            });
    }

    private static void ambientCourtFeedback(final NaamahEntity naamah, final ServerLevel level, final long now) {
        final NaamahCourtState state = naamah.courtState();
        if (now < state.nextAmbientFeedbackAt()) return;
        final long dayTime = level.getOverworldClockTime() % 24_000L;
        final boolean daylight = dayTime < 13_000L || dayTime > 23_000L;
        final AmbientMode mode = NaamahCourtRules.ambientMode(
            daylight, !level.canSeeSky(naamah.blockPosition()), naamah.isInWater()
        );
        if (mode != AmbientMode.VEILED_REST) {
            level.sendParticles(naamah.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.ENCHANT,
                naamah.getX(), naamah.getY() + 1.0D, naamah.getZ(), 6, 0.5D, 0.6D, 0.5D, 0.01D);
            level.playSound(null, naamah.blockPosition(), SoundEvents.WITCH_AMBIENT,
                SoundSource.HOSTILE, 0.35F, mode == AmbientMode.SEA_BORNE_COMPOSURE ? 0.8F : 1.2F);
        }
        naamah.setCourtState(state.withSchedule(state.nextDecisionAt(), state.nextCandidateScanAt(),
            state.nextShadeScanAt(), now + NaamahCourtRules.ambientFeedbackInterval(mode), state.lastNavigationAt()));
    }
}
