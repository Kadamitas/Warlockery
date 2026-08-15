package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.EldritchWatcherRules.ActionType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.CandidateFacts;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.EvidenceType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import com.kadamitas.warlockery.entity.EldritchWatcherState.Cadence;
import com.kadamitas.warlockery.entity.EldritchWatcherState.Site;
import com.kadamitas.warlockery.entity.EldritchWatcherState.TimedSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EldritchWatcherRuntime {
    public static final TagKey<net.minecraft.world.level.block.Block> FOCUS_BLOCKS = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath("warlockery", "ai/eldritch_watcher_focus")
    );
    private static final TagKey<net.minecraft.world.level.block.Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );

    private EldritchWatcherRuntime() {
    }

    public static final class Counters {
        long perceptionScans;
        long entityVisits;
        long lineOfSightClips;
        long focusBlockReads;
        long hazardBlockReads;
        long safetyBlockReads;
        long movementCommands;
        long warningVisits;
        long warningRecipients;
        long executedActions;
        long actionCancellations;
        long releases;
        long hazardInterruptions;
        long lureAccepts;

        public long perceptionScans() { return perceptionScans; }
        public long entityVisits() { return entityVisits; }
        public long lineOfSightClips() { return lineOfSightClips; }
        public long focusBlockReads() { return focusBlockReads; }
        public long hazardBlockReads() { return hazardBlockReads; }
        public long safetyBlockReads() { return safetyBlockReads; }
        public long movementCommands() { return movementCommands; }
        public long warningVisits() { return warningVisits; }
        public long warningRecipients() { return warningRecipients; }
        public long executedActions() { return executedActions; }
        public long actionCancellations() { return actionCancellations; }
        public long releases() { return releases; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long lureAccepts() { return lureAccepts; }
    }

    public static void tick(final EldritchWatcherEntity watcher, final ServerLevel level) {
        final long now = level.getGameTime();
        final String dimension = level.dimension().identifier().toString();
        EldritchWatcherState state = watcher.watcherState();
        state = reconcileAnchor(watcher, state, dimension);

        boolean urgentHazard = contactHazard(watcher);
        if (EldritchWatcherRules.due(state.cadence().nextHazardScanAt(), now)) {
            state = state.withCadence(cadenceWithHazard(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.HAZARD_SCAN_INTERVAL_TICKS)));
            urgentHazard = urgentHazard || scannedLocalHazard(watcher, level);
        }
        if (urgentHazard) {
            watcher.watcherCounters().hazardInterruptions++;
            state = cancelUnexecutedAction(watcher, state, now);
            state = escapeHazard(watcher, level, state, now);
            watcher.setWatcherState(state.withMode(Mode.EXPOSED_WITHDRAWAL));
            return;
        }

        if (EldritchWatcherRules.withdrawRequired(watcher.getHealth() / watcher.getMaxHealth())
            && state.withdrawUntil() <= now && state.mode() != Mode.EXPOSED_WITHDRAWAL) {
            state = cancelUnexecutedAction(watcher, state, now)
                .withWithdrawUntil(EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.WITHDRAW_TICKS));
        }
        if (state.withdrawUntil() > now) {
            state = state.withMode(Mode.EXPOSED_WITHDRAWAL);
            state = moveToward(watcher, level, state, vigilPoint(watcher, state), now);
            watcher.setWatcherState(state);
            return;
        }

        state = revalidateSubject(watcher, level, state, dimension, now);
        state = executeDueAction(watcher, level, state, dimension, now);

        if (EldritchWatcherRules.due(state.cadence().nextPerceptionAt(), now)) {
            state = state.withCadence(cadenceWithPerception(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.PERCEPTION_INTERVAL_TICKS)));
            state = perceive(watcher, level, state, dimension, now);
        }

        final boolean lureUsable = state.lure().map(site -> site.valid(now)).orElse(false)
            && !EldritchWatcherRules.lureOutranked(
                false,
                state.action() != ActionType.NONE,
                state.threatId().isPresent() && state.threatExpiresAt() > now
            );

        final boolean escalated = escalatedSubject(state, now);
        final Mode mode = EldritchWatcherRules.selectMode(
            false,
            false,
            escalated,
            lureUsable,
            state.subjectId().isPresent() && state.evidenceExpiresAt() > now,
            state.focus().map(site -> site.valid(now)).orElse(false),
            nearAnchor(watcher, state)
        );
        state = state.withMode(mode);

        if (mode == Mode.INTERCEPTING && state.action() == ActionType.NONE) {
            state = maybeStartRevelation(watcher, level, state, dimension, now);
        }
        if (mode == Mode.QUIET_VIGIL && EldritchWatcherRules.due(state.cadence().nextFocusScanAt(), now)) {
            state = state.withCadence(cadenceWithFocus(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.FOCUS_SCAN_INTERVAL_TICKS)));
            state = scanForFocus(watcher, level, state, dimension, now);
        }

        state = moveForMode(watcher, level, state, now);
        state = emitAmbientFeedback(watcher, level, state, now);
        watcher.setWatcherState(state);
    }

    static EldritchWatcherState reconcileAnchor(
        final EldritchWatcherEntity watcher,
        final EldritchWatcherState state,
        final String dimension
    ) {
        if (state.anchor().isEmpty()) {
            return state.withAnchor(Optional.of(new Site(dimension, watcher.blockPosition())));
        }
        return state;
    }

    private static BlockPos anchorPoint(final EldritchWatcherEntity watcher, final EldritchWatcherState state) {
        final String dimension = watcher.level().dimension().identifier().toString();
        return state.anchor()
            .filter(site -> site.dimension().equals(dimension))
            .map(Site::position)
            .orElse(watcher.blockPosition());
    }

    private static boolean nearAnchor(final EldritchWatcherEntity watcher, final EldritchWatcherState state) {
        final BlockPos anchor = anchorPoint(watcher, state);
        return watcher.blockPosition().distSqr(anchor)
            <= (double) EldritchWatcherRules.ANCHOR_HOLD_RADIUS * EldritchWatcherRules.ANCHOR_HOLD_RADIUS;
    }

    private static Vec3 vigilPoint(final EldritchWatcherEntity watcher, final EldritchWatcherState state) {
        final BlockPos anchor = anchorPoint(watcher, state);
        return new Vec3(anchor.getX() + 0.5D, anchor.getY() + 3.5D, anchor.getZ() + 0.5D);
    }

    static boolean contactHazard(final EldritchWatcherEntity watcher) {
        return watcher.isInLava() || watcher.isOnFire()
            || (watcher.isUnderWater() && watcher.getAirSupply() < watcher.getMaxAirSupply() / 2);
    }

    static boolean scannedLocalHazard(final EldritchWatcherEntity watcher, final ServerLevel level) {
        final BlockPos base = watcher.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= EldritchWatcherRules.MAX_HAZARD_BLOCK_READS) {
                        return false;
                    }
                    final BlockPos pos = base.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    reads++;
                    watcher.watcherCounters().hazardBlockReads++;
                    if (hazardousState(level.getBlockState(pos))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hazardousState(final BlockState blockState) {
        return blockState.is(Blocks.FIRE) || blockState.is(Blocks.SOUL_FIRE)
            || blockState.is(Blocks.LAVA) || blockState.is(Blocks.MAGMA_BLOCK)
            || blockState.is(CONTACT_HAZARDS)
            || blockState.getFluidState().is(FluidTags.LAVA);
    }

    static EldritchWatcherState escapeHazard(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.retryAfter() > now
            || !EldritchWatcherRules.due(state.cadence().nextMovementAt(), now)) {
            return state;
        }
        final Optional<BlockPos> safe = findSafeDestination(watcher, level, state);
        if (safe.isEmpty()) {
            return recordRouteFailure(watcher, state, now);
        }
        return commitDestination(watcher, state, safe.orElseThrow(), now);
    }

    static Optional<BlockPos> findSafeDestination(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState state
    ) {
        final BlockPos base = watcher.blockPosition();
        final BlockPos anchor = anchorPoint(watcher, state);
        int readBudget = EldritchWatcherRules.MAX_SAFETY_BLOCK_READS;
        int candidates = 0;
        for (final long[] offset : EldritchWatcherRules.destinationOffsets(watcher.getUUID())) {
            if (candidates >= EldritchWatcherRules.MAX_DESTINATION_CANDIDATES || readBudget < 4) {
                break;
            }
            candidates++;
            final BlockPos candidate = base.offset((int) offset[0], (int) offset[1], (int) offset[2]);
            if (!level.hasChunkAt(candidate)
                || !level.getWorldBorder().isWithinBounds(candidate)
                || candidate.distSqr(anchor) > (double) EldritchWatcherRules.ANCHOR_CHASE_RADIUS
                    * EldritchWatcherRules.ANCHOR_CHASE_RADIUS) {
                continue;
            }
            readBudget -= 4;
            watcher.watcherCounters().safetyBlockReads += 4;
            if (hazardousState(level.getBlockState(candidate))
                || hazardousState(level.getBlockState(candidate.above()))) {
                continue;
            }
            final AABB footprint = watcher.getDimensions(watcher.getPose())
                .makeBoundingBox(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            if (level.noCollision(watcher, footprint)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static EldritchWatcherState commitDestination(
        final EldritchWatcherEntity watcher,
        final EldritchWatcherState state,
        final BlockPos destination,
        final long now
    ) {
        watcher.watcherCounters().movementCommands++;
        watcher.getMoveControl().setWantedPosition(
            destination.getX() + 0.5D, destination.getY() + 0.5D, destination.getZ() + 0.5D, 1.0D
        );
        return state
            .withDestination(Optional.of(new TimedSite(
                watcher.level().dimension().identifier().toString(),
                destination,
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.DESTINATION_EXPIRY_TICKS)
            )))
            .withRouteFailures(0, state.retryAfter())
            .withCadence(cadenceWithMovement(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.MOVEMENT_INTERVAL_TICKS)));
    }

    static EldritchWatcherState recordRouteFailure(
        final EldritchWatcherEntity watcher,
        final EldritchWatcherState state,
        final long now
    ) {
        final int failures = EldritchWatcherRules.routeFailures(state.routeFailures());
        final long backoff = EldritchWatcherRules.routeBackoffUntil(failures, now);
        return state
            .withDestination(Optional.empty())
            .withRouteFailures(backoff > 0L ? 0 : failures, Math.max(state.retryAfter(), backoff))
            .withCadence(cadenceWithMovement(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.MOVEMENT_INTERVAL_TICKS)));
    }

    static EldritchWatcherState revalidateSubject(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final String dimension,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.threatId().isPresent() && state.threatExpiresAt() <= now) {
            state = state.withThreat(Optional.empty(), 0L, state.warningDedupeUntil());
        }
        if (state.subjectId().isEmpty()) {
            return state;
        }
        if (state.evidenceExpiresAt() <= now) {
            watcher.watcherCounters().releases++;
            return state.releasedEncounter();
        }
        final Entity resolved = level.getEntity(state.subjectId().orElseThrow());
        if (!(resolved instanceof LivingEntity subject) || !subject.isAlive()
            || subject.level() != level || protectedActor(watcher, subject)) {
            watcher.watcherCounters().releases++;
            return cancelUnexecutedAction(watcher, state, now).releasedEncounter();
        }
        final BlockPos anchor = anchorPoint(watcher, state);
        if (!EldritchWatcherRules.withinChaseEnvelope(
            watcher.blockPosition().distSqr(anchor), subject.blockPosition().distSqr(anchor)
        )) {
            watcher.watcherCounters().releases++;
            return cancelUnexecutedAction(watcher, state, now)
                .releasedEncounter()
                .withLastSeen(Optional.of(new TimedSite(
                    dimension, subject.blockPosition(),
                    EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.LAST_SEEN_TICKS)
                )));
        }
        return state;
    }

    static EldritchWatcherState executeDueAction(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final String dimension,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.action() != ActionType.REVELATION || state.actionExecuteAt() <= 0L
            || now < state.actionExecuteAt()) {
            return state;
        }
        final Optional<UUID> targetId = state.actionTargetId();
        final Entity resolved = targetId.map(level::getEntity).orElse(null);
        final boolean valid = resolved instanceof LivingEntity target
            && target.isAlive()
            && target.level() == level
            && state.actionDimension().map(dimension::equals).orElse(false)
            && !protectedActor(watcher, target)
            && EldritchWatcherRules.withinAttackRange(watcher.distanceToSqr(target))
            && EldritchWatcherRules.withinChaseEnvelope(
                watcher.blockPosition().distSqr(anchorPoint(watcher, state)),
                target.blockPosition().distSqr(anchorPoint(watcher, state)))
            && hasLineOfSight(watcher, target);
        if (!valid) {
            watcher.watcherCounters().actionCancellations++;
            level.sendParticles(ParticleTypes.SOUL,
                watcher.getX(), watcher.getY() + 0.6D, watcher.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.01D);
            return finishAction(state, now);
        }
        final LivingEntity target = (LivingEntity) resolved;
        final boolean mutualGaze = mutualGaze(watcher, target);
        final DamageSource source = level.damageSources().indirectMagic(watcher, watcher);
        final boolean accepted = target.hurtServer(level, source, EldritchWatcherRules.REVELATION_DAMAGE);
        watcher.watcherCounters().executedActions++;
        level.playSound(null, watcher.getX(), watcher.getY(), watcher.getZ(),
            SoundEvents.VEX_CHARGE, watcher.getSoundSource(), 1.0F, 0.6F);
        sendEyeLineParticles(level, watcher, target);
        if (accepted) {
            target.addEffect(new MobEffectInstance(
                MobEffects.GLOWING, EldritchWatcherRules.GLOWING_TICKS, 0
            ));
            if (mutualGaze) {
                target.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, EldritchWatcherRules.DARKNESS_TICKS, 0
                ));
            }
        }
        return finishAction(state, now);
    }

    private static EldritchWatcherState finishAction(final EldritchWatcherState state, final long now) {
        return state.withAction(
            ActionType.NONE, Optional.empty(), Optional.empty(), 0L,
            EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.REVELATION_RECOVERY_TICKS)
        );
    }

    private static void sendEyeLineParticles(
        final ServerLevel level,
        final EldritchWatcherEntity watcher,
        final LivingEntity target
    ) {
        final Vec3 origin = watcher.getEyePosition();
        final Vec3 line = target.getEyePosition().subtract(origin);
        for (int step = 0; step < 8; step++) {
            final Vec3 point = origin.add(line.scale(step / 8.0D));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    static EldritchWatcherState cancelUnexecutedAction(
        final EldritchWatcherEntity watcher,
        final EldritchWatcherState state,
        final long now
    ) {
        if (state.action() == ActionType.NONE) {
            return state;
        }
        watcher.watcherCounters().actionCancellations++;
        return finishAction(state, now);
    }

    static EldritchWatcherState perceive(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final String dimension,
        final long now
    ) {
        EldritchWatcherState state = input;
        watcher.watcherCounters().perceptionScans++;
        final AABB bounds = watcher.getBoundingBox().inflate(EldritchWatcherRules.PERCEPTION_RADIUS);
        final List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class, bounds,
            candidate -> candidate != watcher && candidate.isAlive()
        );
        nearby.sort(Comparator
            .comparingDouble((LivingEntity candidate) -> watcher.distanceToSqr(candidate))
            .thenComparing(Entity::getUUID));
        final BlockPos anchor = anchorPoint(watcher, state);
        final BlockPos thresholdCenter = state.focus()
            .filter(site -> site.valid(now))
            .map(TimedSite::position)
            .orElse(anchor);
        final List<LivingEntity> visitedEntities = new ArrayList<>();
        for (final LivingEntity candidate : nearby) {
            if (visitedEntities.size() >= EldritchWatcherRules.MAX_ENTITIES_VISITED) {
                break;
            }
            watcher.watcherCounters().entityVisits++;
            visitedEntities.add(candidate);
        }
        final int[] losBudget = {EldritchWatcherRules.MAX_LINE_OF_SIGHT_CLIPS};
        state = observeOwnerGuardHarm(watcher, level, state, visitedEntities, anchor, losBudget, now);
        final List<CandidateFacts> facts = new ArrayList<>();
        final List<LivingEntity> candidates = new ArrayList<>();
        for (final LivingEntity candidate : visitedEntities) {
            if (protectedActor(watcher, candidate)
                || candidate instanceof EldritchWatcherEntity
                || (candidate instanceof Player player && (player.isCreative() || player.isSpectator()))) {
                continue;
            }
            if (facts.size() >= EldritchWatcherRules.MAX_RETAINED_CANDIDATES || losBudget[0] <= 0) {
                break;
            }
            losBudget[0]--;
            watcher.watcherCounters().lineOfSightClips++;
            final boolean lineOfSight = hasLineOfSight(watcher, candidate);
            final boolean gaze = lineOfSight && gazeTowardWatcher(watcher, candidate);
            final boolean breach = lineOfSight
                && EldritchWatcherRules.thresholdBreach(candidate.blockPosition().distSqr(thresholdCenter));
            facts.add(new CandidateFacts(
                state.threatId().map(candidate.getUUID()::equals).orElse(false)
                    && state.evidenceType().map(type -> type == EvidenceType.DIRECT_HARM).orElse(false),
                state.threatId().map(candidate.getUUID()::equals).orElse(false),
                state.subjectId().map(candidate.getUUID()::equals).orElse(false),
                breach,
                gaze,
                watcher.distanceToSqr(candidate),
                candidate.getUUID()
            ));
            candidates.add(candidate);
        }
        final Optional<CandidateFacts> best = facts.stream().min(EldritchWatcherRules.candidateOrder());
        if (best.isEmpty()) {
            return state;
        }
        final CandidateFacts selected = best.orElseThrow();
        final LivingEntity subject = candidates.get(facts.indexOf(selected));
        final boolean sameSubject = state.subjectId().map(selected.id()::equals).orElse(false);
        final EvidenceType observed;
        if (selected.directThreat() || selected.validReport() && hasLineOfSight(watcher, subject)) {
            observed = selected.directThreat() ? EvidenceType.DIRECT_HARM : EvidenceType.REPORTED_HARM;
        } else if (selected.reciprocalGaze()) {
            observed = EvidenceType.RECIPROCAL_GAZE;
        } else if (selected.thresholdBreach()) {
            observed = EvidenceType.THRESHOLD_BREACH;
        } else if (hasLineOfSight(watcher, subject)) {
            observed = EvidenceType.SEEN;
        } else {
            return state;
        }
        final boolean currentDirect = state.evidenceType()
            .map(type -> type == EvidenceType.DIRECT_HARM).orElse(false)
            && state.evidenceExpiresAt() > now;
        if (currentDirect && observed != EvidenceType.DIRECT_HARM) {
            return state;
        }
        final int previousSamples = sameSubject
            && state.evidenceType().map(observed::equals).orElse(false)
            ? state.attentionSamples() : 0;
        final int samples = observed == EvidenceType.RECIPROCAL_GAZE
            || observed == EvidenceType.THRESHOLD_BREACH
            ? Math.min(EldritchWatcherRules.ESCALATION_SAMPLES, previousSamples + 1)
            : previousSamples;
        state = state.withSubject(
            Optional.of(selected.id()),
            Optional.of(observed),
            EldritchWatcherRules.saturatingAdd(now,
                EldritchWatcherRules.evidenceLifetimeTicks(observed)),
            samples
        );
        return state.withLastSeen(Optional.of(new TimedSite(
            dimension, subject.blockPosition(),
            EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.LAST_SEEN_TICKS)
        )));
    }

    static EldritchWatcherState observeOwnerGuardHarm(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState state,
        final List<LivingEntity> visitedEntities,
        final BlockPos anchor,
        final int[] losBudget,
        final long now
    ) {
        final Optional<UUID> ownerId = watcher.warlockeryOwner();
        if (ownerId.isEmpty()) {
            return state;
        }
        for (final LivingEntity candidate : visitedEntities) {
            if (!ownerId.orElseThrow().equals(candidate.getUUID())) {
                continue;
            }
            final LivingEntity attacker = candidate.getLastHurtByMob();
            final boolean fresh = attacker != null
                && candidate.getLastHurtByMobTimestamp()
                    + EldritchWatcherRules.ATTRIBUTION_FRESHNESS_TICKS >= candidate.tickCount;
            final boolean attackerValid = attacker != null && attacker != watcher
                && attacker.isAlive() && attacker.level() == level
                && !protectedActor(watcher, attacker);
            final boolean ownerNearAnchor = candidate.blockPosition().distSqr(anchor)
                <= (double) EldritchWatcherRules.OWNER_GUARD_RADIUS
                    * EldritchWatcherRules.OWNER_GUARD_RADIUS;
            if (!fresh || !attackerValid || !ownerNearAnchor || losBudget[0] <= 0) {
                return state;
            }
            losBudget[0]--;
            watcher.watcherCounters().lineOfSightClips++;
            final boolean sight = hasLineOfSight(watcher, attacker);
            if (!EldritchWatcherRules.ownerGuardEvidence(
                true, fresh, attackerValid, ownerNearAnchor, sight
            )) {
                return state;
            }
            return state
                .withThreat(Optional.of(attacker.getUUID()),
                    EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS),
                    state.warningDedupeUntil())
                .withSubject(Optional.of(attacker.getUUID()),
                    Optional.of(EvidenceType.DIRECT_HARM),
                    EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS),
                    EldritchWatcherRules.ESCALATION_SAMPLES);
        }
        return state;
    }

    private static boolean escalatedSubject(final EldritchWatcherState state, final long now) {
        if (state.subjectId().isEmpty() || state.evidenceExpiresAt() <= now
            || state.evidenceType().isEmpty()) {
            return false;
        }
        final EvidenceType type = state.evidenceType().orElseThrow();
        return EldritchWatcherRules.escalationReady(
            type,
            state.attentionSamples(),
            type == EvidenceType.REPORTED_HARM
        );
    }

    static EldritchWatcherState maybeStartRevelation(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState state,
        final String dimension,
        final long now
    ) {
        if (state.actionRecoverUntil() > now || state.retryAfter() > now
            || state.subjectId().isEmpty()) {
            return state;
        }
        final Entity resolved = level.getEntity(state.subjectId().orElseThrow());
        if (!(resolved instanceof LivingEntity target)) {
            return state;
        }
        final BlockPos anchor = anchorPoint(watcher, state);
        final EldritchWatcherRules.RevelationFacts facts = new EldritchWatcherRules.RevelationFacts(
            true,
            target.isAlive(),
            target.level() == level,
            !protectedActor(watcher, target),
            EldritchWatcherRules.withinAttackRange(watcher.distanceToSqr(target)),
            EldritchWatcherRules.withinChaseEnvelope(
                watcher.blockPosition().distSqr(anchor), target.blockPosition().distSqr(anchor)),
            hasLineOfSight(watcher, target),
            false,
            state.withdrawUntil() > now,
            state.actionRecoverUntil() > now || state.retryAfter() > now
        );
        if (!EldritchWatcherRules.mayStartRevelation(facts)) {
            return state;
        }
        level.playSound(null, watcher.getX(), watcher.getY(), watcher.getZ(),
            SoundEvents.VEX_AMBIENT, watcher.getSoundSource(), 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            watcher.getX(), watcher.getY() + 0.8D, watcher.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.02D);
        return state.withAction(
            ActionType.REVELATION,
            Optional.of(target.getUUID()),
            Optional.of(dimension),
            EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.REVELATION_WINDUP_TICKS),
            state.actionRecoverUntil()
        );
    }

    static EldritchWatcherState scanForFocus(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final String dimension,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.focus().map(site -> site.valid(now)).orElse(false)) {
            return state;
        }
        final BlockPos base = watcher.blockPosition();
        final long scanPhase = now / EldritchWatcherRules.FOCUS_SCAN_INTERVAL_TICKS
            + EldritchWatcherRules.stableOffset(watcher.getUUID(), 8);
        final int[] layers = EldritchWatcherRules.focusLayers(scanPhase);
        final int span = EldritchWatcherRules.FOCUS_HORIZONTAL_RADIUS + 1;
        final int layerSize = span * span;
        int reads = 0;
        for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
            final int dy = layers[layerIndex];
            final int start = layerIndex == 0
                ? 0
                : EldritchWatcherRules.focusLayerStart(scanPhase, layerSize);
            for (int step = 0; step < layerSize; step++) {
                if (reads >= EldritchWatcherRules.MAX_FOCUS_BLOCK_READS) {
                    return state;
                }
                final int index = (start + step) % layerSize;
                final int dx = (index / span) * 2 - EldritchWatcherRules.FOCUS_HORIZONTAL_RADIUS;
                final int dz = (index % span) * 2 - EldritchWatcherRules.FOCUS_HORIZONTAL_RADIUS;
                final BlockPos pos = base.offset(dx, dy, dz);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                reads++;
                watcher.watcherCounters().focusBlockReads++;
                if (level.getBlockState(pos).is(FOCUS_BLOCKS)) {
                    return state.withFocus(Optional.of(new TimedSite(
                        dimension, pos,
                        EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.FOCUS_RETENTION_TICKS)
                    )));
                }
            }
        }
        return state;
    }

    static EldritchWatcherState moveForMode(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.mode() == Mode.FOCUS_INSPECTION) {
            final Optional<TimedSite> focus = state.focus().filter(site -> site.valid(now));
            if (focus.isEmpty() || !level.hasChunkAt(focus.orElseThrow().position())
                || !level.getBlockState(focus.orElseThrow().position()).is(FOCUS_BLOCKS)) {
                return state.withFocus(Optional.empty()).withMode(Mode.QUIET_VIGIL);
            }
        }
        final Vec3 desired = switch (state.mode()) {
            case QUIET_VIGIL, RETURNING, EXPOSED_WITHDRAWAL -> vigilPoint(watcher, state);
            case FOCUS_INSPECTION -> state.focus().map(site ->
                new Vec3(site.position().getX() + 0.5D, site.position().getY() + 3.0D,
                    site.position().getZ() + 0.5D)).orElse(vigilPoint(watcher, state));
            case EXTERNAL_LURE -> state.lure().map(site ->
                new Vec3(site.position().getX() + 0.5D, site.position().getY() + 2.0D,
                    site.position().getZ() + 0.5D)).orElse(vigilPoint(watcher, state));
            case OBSERVING, INTERCEPTING -> observationPoint(watcher, level, state);
        };
        return moveToward(watcher, level, state, desired, now);
    }

    private static Vec3 observationPoint(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState state
    ) {
        final Entity subject = state.subjectId().map(level::getEntity).orElse(null);
        if (subject == null) {
            return vigilPoint(watcher, state);
        }
        final double preferred = state.mode() == Mode.INTERCEPTING ? 10.0D : 12.0D;
        final Vec3 away = watcher.position().subtract(subject.position());
        final Vec3 direction = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        return subject.position().add(direction.scale(preferred)).add(0.0D, 2.0D, 0.0D);
    }

    static EldritchWatcherState moveToward(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState input,
        final Vec3 desired,
        final long now
    ) {
        EldritchWatcherState state = input;
        if (state.retryAfter() > now
            || !EldritchWatcherRules.due(state.cadence().nextMovementAt(), now)) {
            return state;
        }
        if (watcher.position().distanceToSqr(desired) <= 2.25D) {
            return state.withCadence(cadenceWithMovement(state.cadence(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.MOVEMENT_INTERVAL_TICKS)));
        }
        final BlockPos base = watcher.blockPosition();
        final BlockPos anchor = anchorPoint(watcher, state);
        int readBudget = EldritchWatcherRules.MAX_SAFETY_BLOCK_READS;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int candidates = 0;
        for (final long[] offset : EldritchWatcherRules.destinationOffsets(watcher.getUUID())) {
            if (candidates >= EldritchWatcherRules.MAX_DESTINATION_CANDIDATES || readBudget < 4) {
                break;
            }
            candidates++;
            final BlockPos candidate = base.offset((int) offset[0], (int) offset[1], (int) offset[2]);
            if (!level.hasChunkAt(candidate)
                || !level.getWorldBorder().isWithinBounds(candidate)
                || candidate.distSqr(anchor) > (double) EldritchWatcherRules.ANCHOR_CHASE_RADIUS
                    * EldritchWatcherRules.ANCHOR_CHASE_RADIUS) {
                continue;
            }
            readBudget -= 4;
            watcher.watcherCounters().safetyBlockReads += 4;
            if (hazardousState(level.getBlockState(candidate))
                || hazardousState(level.getBlockState(candidate.above()))) {
                continue;
            }
            final AABB footprint = watcher.getDimensions(watcher.getPose())
                .makeBoundingBox(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            if (!level.noCollision(watcher, footprint)) {
                continue;
            }
            final double distance = desired.distanceToSqr(
                candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D
            );
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) {
            return recordRouteFailure(watcher, state, now);
        }
        return commitDestination(watcher, state, best, now);
    }

    private static EldritchWatcherState emitAmbientFeedback(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final EldritchWatcherState state,
        final long now
    ) {
        if (!EldritchWatcherRules.due(state.cadence().nextFeedbackAt(), now)) {
            return state;
        }
        if (state.mode() == Mode.FOCUS_INSPECTION) {
            state.focus().ifPresent(site -> level.sendParticles(
                ParticleTypes.ENCHANT,
                site.position().getX() + 0.5D, site.position().getY() + 1.2D, site.position().getZ() + 0.5D,
                4, 0.3D, 0.3D, 0.3D, 0.02D
            ));
        }
        return state.withCadence(cadenceWithFeedback(state.cadence(),
            EldritchWatcherRules.saturatingAdd(now, 100L)));
    }

    public static boolean acceptExternalLure(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final BlockPos lurePosition
    ) {
        final long now = level.getGameTime();
        final EldritchWatcherState state = watcher.watcherState();
        if (watcher.level() != level
            || watcher.blockPosition().distSqr(lurePosition)
                > (double) EldritchWatcherRules.LURE_RADIUS * EldritchWatcherRules.LURE_RADIUS
            || EldritchWatcherRules.lureOutranked(
                watcher.isOnFire() || watcher.isInLava(),
                state.action() != ActionType.NONE,
                state.threatId().isPresent() && state.threatExpiresAt() > now)) {
            return false;
        }
        watcher.watcherCounters().lureAccepts++;
        watcher.setWatcherState(state.withLure(Optional.of(new TimedSite(
            level.dimension().identifier().toString(),
            lurePosition,
            EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.LURE_TICKS)
        ))));
        return true;
    }

    public static void recordDirectHarm(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == watcher
            || !attacker.isAlive() || protectedActor(watcher, attacker)) {
            return;
        }
        final long now = level.getGameTime();
        EldritchWatcherState state = watcher.watcherState();
        state = state
            .withThreat(Optional.of(attacker.getUUID()),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS),
                state.warningDedupeUntil())
            .withSubject(Optional.of(attacker.getUUID()), Optional.of(EvidenceType.DIRECT_HARM),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS),
                EldritchWatcherRules.ESCALATION_SAMPLES);
        if (!EldritchWatcherRules.warningDeduped(state.warningDedupeUntil(), now)) {
            state = state.withThreat(state.threatId(), state.threatExpiresAt(),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.WARNING_DEDUPE_TICKS));
            emitOneHopWarning(watcher, level, attacker, now);
        }
        watcher.setWatcherState(state);
    }

    static void emitOneHopWarning(
        final EldritchWatcherEntity watcher,
        final ServerLevel level,
        final LivingEntity attacker,
        final long now
    ) {
        final Optional<UUID> senderOwner = watcher.warlockeryOwner();
        final List<EldritchWatcherEntity> peers = level.getEntitiesOfClass(
            EldritchWatcherEntity.class,
            watcher.getBoundingBox().inflate(EldritchWatcherRules.WARNING_RADIUS),
            peer -> peer != watcher && peer.isAlive()
        );
        peers.sort(Comparator
            .comparing((EldritchWatcherEntity peer) ->
                !EldritchWatcherRules.warningCompatible(senderOwner, peer.warlockeryOwner()))
            .thenComparingDouble(watcher::distanceToSqr)
            .thenComparing(Entity::getUUID));
        int visited = 0;
        int recipients = 0;
        for (final EldritchWatcherEntity peer : peers) {
            if (visited >= EldritchWatcherRules.MAX_WARNING_VISITS
                || recipients >= EldritchWatcherRules.MAX_WARNING_RECIPIENTS) {
                break;
            }
            visited++;
            watcher.watcherCounters().warningVisits++;
            if (!EldritchWatcherRules.warningCompatible(senderOwner, peer.warlockeryOwner())) {
                continue;
            }
            recipients++;
            watcher.watcherCounters().warningRecipients++;
            final EldritchWatcherState peerState = peer.watcherState();
            peer.setWatcherState(peerState.withThreat(
                Optional.of(attacker.getUUID()),
                EldritchWatcherRules.saturatingAdd(now, EldritchWatcherRules.REPORTED_HARM_TICKS),
                peerState.warningDedupeUntil()
            ));
        }
    }

    public static boolean eligibleTarget(final EldritchWatcherEntity watcher, final LivingEntity target) {
        if (protectedActor(watcher, target)) {
            return false;
        }
        final EldritchWatcherState state = watcherStateFor(watcher);
        final long now = watcher.level().getGameTime();
        final boolean actionTarget = state.actionTargetId()
            .map(target.getUUID()::equals).orElse(false);
        final boolean escalated = state.subjectId().map(target.getUUID()::equals).orElse(false)
            && escalatedSubject(state, now);
        return actionTarget || escalated;
    }

    private static EldritchWatcherState watcherStateFor(final EldritchWatcherEntity watcher) {
        return watcher.watcherState();
    }

    static boolean protectedActor(final EldritchWatcherEntity watcher, final LivingEntity candidate) {
        final UUID id = candidate.getUUID();
        return watcher.warlockeryOwner().map(id::equals).orElse(false)
            || watcher.vanillaOwner().map(id::equals).orElse(false);
    }

    private static boolean hasLineOfSight(final EldritchWatcherEntity watcher, final LivingEntity target) {
        return watcher.getSensing().hasLineOfSight(target);
    }

    static boolean gazeTowardWatcher(final EldritchWatcherEntity watcher, final LivingEntity candidate) {
        final Vec3 toWatcher = watcher.getEyePosition().subtract(candidate.getEyePosition());
        if (toWatcher.lengthSqr() < 1.0E-6D) {
            return true;
        }
        final double dot = candidate.getViewVector(1.0F).normalize().dot(toWatcher.normalize());
        return EldritchWatcherRules.reciprocalGaze(dot, true);
    }

    static boolean mutualGaze(final EldritchWatcherEntity watcher, final LivingEntity target) {
        return hasLineOfSight(watcher, target) && gazeTowardWatcher(watcher, target);
    }

    private static Cadence cadenceWithPerception(final Cadence cadence, final long next) {
        return new Cadence(next, cadence.nextFocusScanAt(), cadence.nextHazardScanAt(),
            cadence.nextMovementAt(), cadence.nextFeedbackAt());
    }

    private static Cadence cadenceWithFocus(final Cadence cadence, final long next) {
        return new Cadence(cadence.nextPerceptionAt(), next, cadence.nextHazardScanAt(),
            cadence.nextMovementAt(), cadence.nextFeedbackAt());
    }

    private static Cadence cadenceWithHazard(final Cadence cadence, final long next) {
        return new Cadence(cadence.nextPerceptionAt(), cadence.nextFocusScanAt(), next,
            cadence.nextMovementAt(), cadence.nextFeedbackAt());
    }

    private static Cadence cadenceWithMovement(final Cadence cadence, final long next) {
        return new Cadence(cadence.nextPerceptionAt(), cadence.nextFocusScanAt(),
            cadence.nextHazardScanAt(), next, cadence.nextFeedbackAt());
    }

    private static Cadence cadenceWithFeedback(final Cadence cadence, final long next) {
        return new Cadence(cadence.nextPerceptionAt(), cadence.nextFocusScanAt(),
            cadence.nextHazardScanAt(), cadence.nextMovementAt(), next);
    }
}
