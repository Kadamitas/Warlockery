package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CircleMageRules.Action;
import com.kadamitas.warlockery.entity.CircleMageRules.Candidate;
import com.kadamitas.warlockery.entity.CircleMageRules.Mode;
import com.kadamitas.warlockery.entity.CircleMageRules.Priority;
import com.kadamitas.warlockery.entity.CircleMageRules.RecruitmentResult;
import com.kadamitas.warlockery.entity.CircleMageRules.RelationFacts;
import com.kadamitas.warlockery.entity.CircleMageRules.TargetSource;
import com.kadamitas.warlockery.item.CovenRosterData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Circle Mage behavior controller. It owns MOVE and target assignment
 * completely; the entity's look goals own LOOK and its emergency goal owns nothing. Every scan,
 * block read, line-of-sight ray, path request, and feedback burst is charged against the declared
 * hard budgets. Nothing here forces a chunk, edits a block or inventory, opens a container,
 * iterates a level globally, propagates a report recursively, or retains an unbounded collection.
 */
public final class CircleMageRuntime {
    private static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final double ROUTE_SPEED = 1.05D;
    private static final double ESCAPE_SPEED = 1.2D;

    private CircleMageRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long candidateVisits;
        long lineOfSightChecks;
        long blockReads;
        long workstationVisits;
        long peerVisits;
        long reportsEmitted;
        long reportsAccepted;
        long ownerLookups;
        long auraApplications;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long safeSteps;
        long boltsCast;
        long boltsCancelled;
        long focusPrepared;
        long focusConsumed;
        long sessionsJoined;
        long sessionsReleased;
        long hazardInterruptions;
        long withdrawals;
        long recallReconciliations;
        long reportsExpired;
        long rosterRegistrations;
        long emergencyHits;

        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long blockReads() { return blockReads; }
        public long workstationVisits() { return workstationVisits; }
        public long peerVisits() { return peerVisits; }
        public long reportsEmitted() { return reportsEmitted; }
        public long reportsAccepted() { return reportsAccepted; }
        public long ownerLookups() { return ownerLookups; }
        public long auraApplications() { return auraApplications; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long safeSteps() { return safeSteps; }
        public long boltsCast() { return boltsCast; }
        public long boltsCancelled() { return boltsCancelled; }
        public long focusPrepared() { return focusPrepared; }
        public long focusConsumed() { return focusConsumed; }
        public long sessionsJoined() { return sessionsJoined; }
        public long sessionsReleased() { return sessionsReleased; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long withdrawals() { return withdrawals; }
        public long recallReconciliations() { return recallReconciliations; }
        public long reportsExpired() { return reportsExpired; }
        public long rosterRegistrations() { return rosterRegistrations; }
        public long emergencyHits() { return emergencyHits; }
    }

    /** Execution scratch rebuilt after every load. It can never replay a bolt, focus, or path. */
    public static final class TransientState {
        boolean reconciled;
        boolean recallPending;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int targetScanCooldownTicks;
        boolean hazardActive;
        boolean receivedAsReport;
        BlockPos destination;

        public void resetForLoad() {
            reconciled = false;
            recallPending = false;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            targetScanCooldownTicks = 0;
            hazardActive = false;
            receivedAsReport = false;
            destination = null;
        }
    }

    // ---------------------------------------------------------------- tick

    public static void tick(final CircleMageEntity mage, final ServerLevel level) {
        reconcileOnLoad(mage, level);
        advanceLoadedTimers(mage);
        endExpiredPhases(mage);
        revalidateThreat(mage, level);

        final boolean hazard = observeHazardWhenDue(mage, level);
        final CircleMageState state = mage.mageState();
        final Optional<LivingEntity> owner = resolveOwnerWhenDue(mage, level);
        final boolean ownerFollow = owner
            .map(living -> !CircleMageRules.withinFormation(mage.distanceToSqr(living)))
            .orElse(false);

        final Priority priority = CircleMageRules.priority(
            mage.mageTransient().recallPending,
            hazard,
            state.action().pending(),
            state.threat().source() == TargetSource.DIRECT
                || state.threat().source() == TargetSource.OWNER,
            CircleMageRules.shouldWithdraw(
                CircleMageRules.healthFraction(mage.getHealth(), mage.getMaxHealth())
            ),
            ownerFollow,
            state.threat().source() == TargetSource.PEER_REPORT,
            state.session().present() || state.action().action() == Action.STUDY,
            CircleMageRules.studySearchAllowed(
                state.study().searchCooldownTicks(),
                state.study().studyCooldownTicks(),
                !state.threat().present(),
                mage.getTarget() == null,
                !hazard,
                ownerFollow
            )
        );

        switch (priority) {
            case RECALL -> finishRecall(mage);
            case HAZARD -> escapeHazard(mage, level);
            case ACTION -> advanceAction(mage, level);
            case DEFENSE, PEER_DEFENSE -> engageThreat(mage, level);
            case WITHDRAW -> withdraw(mage, level);
            case OWNER_FOLLOW -> followOwner(mage, level, owner);
            case ACTIVE_STUDY -> advanceSession(mage, level);
            case STUDY_PROPOSAL -> proposeStudy(mage, level);
            case ANCHOR_RETURN -> holdAnchor(mage, level);
        }
        // The aura is an owner effect on its own twenty-tick cadence, never a movement branch, so
        // it deliberately runs after the single claimed priority without contesting it.
        applyOwnerAura(mage, level, owner);
        publishPresentation(mage);
    }

    private static void publishPresentation(final CircleMageEntity mage) {
        final CircleMageState state = mage.mageState();
        mage.syncPresentation(state.mode(), state.study().focusPrepared());
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final CircleMageEntity mage, final ServerLevel level) {
        final TransientState scratch = mage.mageTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.targetScanCooldownTicks =
            CircleMageRules.stableOffset(mage.getUUID(), CircleMageRules.TARGET_SCAN_INTERVAL_TICKS);
        CircleMageState state = mage.mageState();
        if (!state.anchor().present()) {
            state = state.withAnchor(new CircleMageState.Anchor(
                Optional.of(mage.blockPosition()), Optional.of(dimensionOf(level))
            ));
        }
        mage.setMageState(state);
        // One-time idempotent roster reconciliation for an already bound loaded Mage. It never
        // loads a chunk or dimension and never displaces a legitimate member.
        registerWithRoster(mage, level);
    }

    private static void registerWithRoster(final CircleMageEntity mage, final ServerLevel level) {
        CreatureBehaviorState.owner(mage).ifPresent(owner -> {
            final CovenRosterData roster = CovenRosterData.get(level);
            // A roster row for a different owner is a real conflict, not a stale duplicate: the
            // durable entity owner tag wins, but the conflict is never silently reassigned by a
            // reconciliation pass. Only an explicit recruitment may move a Mage between owners.
            if (roster.ownerOf(mage.getUUID()).filter(stored -> !stored.equals(owner)).isPresent()) {
                return;
            }
            mage.mageCounters().rosterRegistrations++;
            roster.register(owner, mage.getUUID());
        });
    }

    /** A different owner cannot replace or steal the existing owner and consumes nothing. */
    public static InteractionResult rejectConflictingOwner(
        final CircleMageEntity mage,
        final ServerLevel level,
        final Player player
    ) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
            "message.warlockery.creature.bound_elsewhere", mage.getDisplayName()));
        level.playSound(null, mage.getX(), mage.getY(), mage.getZ(),
            SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
        return InteractionResult.FAIL;
    }

    private static void advanceLoadedTimers(final CircleMageEntity mage) {
        final TransientState scratch = mage.mageTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.targetScanCooldownTicks = Math.max(0, scratch.targetScanCooldownTicks - 1);

        CircleMageState state = mage.mageState();
        final CircleMageState.Cadence cadence = state.cadence();
        state = state.withCadence(new CircleMageState.Cadence(
            CircleMageRules.decrementLoaded(cadence.castRecoveryTicks()),
            CircleMageRules.decrementLoaded(cadence.withdrawalTicks()),
            CircleMageRules.decrementLoaded(cadence.auraTicks()),
            CircleMageRules.decrementLoaded(cadence.ownerCheckTicks()),
            CircleMageRules.decrementLoaded(cadence.peerScanTicks()),
            CircleMageRules.decrementLoaded(cadence.reportCooldownTicks()),
            CircleMageRules.decrementLoaded(cadence.safeStepTicks()),
            cadence.routeFailures(),
            CircleMageRules.decrementLoaded(cadence.routeRetryTicks())
        ));
        final CircleMageState.Threat threat = state.threat();
        if (threat.present()) {
            state = state.withThreat(new CircleMageState.Threat(
                threat.id(), threat.dimension(), threat.source(),
                CircleMageRules.decrementLoaded(threat.remainingTicks())
            ));
        }
        final CircleMageState.ActionState action = state.action();
        if (action.pending()) {
            state = state.withAction(new CircleMageState.ActionState(
                action.action(), action.targetId(), action.dimension(), action.focusReserved(),
                CircleMageRules.decrementLoaded(action.windupRemainingTicks())
            ));
        }
        final CircleMageState.Session session = state.session();
        if (session.present()) {
            state = state.withSession(new CircleMageState.Session(
                session.coordinator(), session.dimension(), session.epoch(), session.slot(),
                CircleMageRules.decrementLoaded(session.remainingTicks())
            ));
        }
        final CircleMageState.Study study = state.study();
        state = state.withStudy(new CircleMageState.Study(
            study.focusPrepared(), study.workstation(), study.dimension(),
            CircleMageRules.decrementLoaded(study.studyCooldownTicks()),
            CircleMageRules.decrementLoaded(study.searchCooldownTicks())
        ));
        mage.setMageState(state);
    }

    /**
     * The single place a timed-out phase ends. The state records no longer end phases inside their
     * canonical constructors, so every expiry is observable exactly once, here, where it can be
     * counted and where its dependent transient facts are cleared in the same step.
     */
    private static void endExpiredPhases(final CircleMageEntity mage) {
        CircleMageState state = mage.mageState();
        if (state.threat().present() && state.threat().remainingTicks() <= 0) {
            mage.mageCounters().reportsExpired++;
            // Clearing the one-hop marker with the threat is what keeps a later direct or owner
            // threat relayable; leaving it set muted the Mage for the rest of its loaded life.
            mage.mageTransient().receivedAsReport = false;
            state = state.withThreat(CircleMageState.Threat.none()).withMode(Mode.IDLE);
            mage.setTarget(null);
        }
        if (state.session().present() && state.session().remainingTicks() <= 0) {
            mage.mageCounters().sessionsReleased++;
            state = state.withSession(CircleMageState.Session.none());
        }
        mage.setMageState(state);
    }

    // ---------------------------------------------------------------- perception

    private static void revalidateThreat(final CircleMageEntity mage, final ServerLevel level) {
        CircleMageState state = mage.mageState();
        final CircleMageState.Threat threat = state.threat();
        if (threat.present()) {
            final Optional<LivingEntity> resolved = resolveLiving(level, threat.id().orElseThrow());
            final boolean valid = resolved
                .filter(_ -> threat.dimension()
                    .map(stored -> stored.equals(dimensionOf(level))).orElse(false))
                .map(living -> legalTarget(mage, living))
                .orElse(false);
            if (!valid) {
                if (state.action().pending()) {
                    mage.mageCounters().boltsCancelled++;
                }
                mage.setMageState(state.withThreat(CircleMageState.Threat.none())
                    .withAction(CircleMageState.ActionState.none()));
                mage.setTarget(null);
                mage.mageTransient().receivedAsReport = false;
                return;
            }
            resolved.ifPresent(mage::setTarget);
            mage.setMageState(state.withMode(Mode.DEFENDING));
            emitPeerReportWhenDue(mage, level);
            return;
        }
        mage.setTarget(null);
        mage.mageTransient().receivedAsReport = false;
        // The mode is no longer reconciled by the record constructor, so the branch that observes
        // "no threat" has to leave DEFENDING itself. Without this a completed bolt kept presenting
        // a defending pose indefinitely.
        if (mage.mageState().mode() == Mode.DEFENDING) {
            mage.setMageState(mage.mageState().withMode(Mode.IDLE));
        }
        acquireThreatWhenDue(mage, level);
    }

    /**
     * The one bounded target scan. Only three facts may propose a target, in the exact motive
     * order direct attacker, owner's attacker, then one validated same-owner peer report, and each
     * proposal still has to pass the full legality gate. A bound Mage never inherits its owner's
     * arbitrary target and an unbound Mage never proactively targets a survival player.
     *
     * <p>The scan runs at most every twenty ticks and is bounded by the motive count, not by a
     * crowd traversal: only the Mage's own fresh attacker and its owner's fresh attacker can be
     * proposed, so at most two candidates are ever inspected and at most two line-of-sight rays
     * are cast. There is deliberately no entity-box sweep here. A previous revision queried a
     * thirty-two-cube box every twenty ticks and then discarded every result, which cost a real
     * query for zero behavior and double counted the preseeded candidates.</p>
     */
    private static void acquireThreatWhenDue(final CircleMageEntity mage, final ServerLevel level) {
        final TransientState scratch = mage.mageTransient();
        if (scratch.targetScanCooldownTicks > 0) {
            return;
        }
        scratch.targetScanCooldownTicks = CircleMageRules.TARGET_SCAN_INTERVAL_TICKS;

        final List<Candidate> inspected = new ArrayList<>();
        // The Mage's attacker and its owner's attacker are frequently the same entity; the direct
        // motive is proposed first and wins, so the second proposal must not duplicate it.
        final java.util.Set<UUID> proposed = new java.util.LinkedHashSet<>();

        // Preseed: the Mage's own fresh accepted attacker, then the owner's fresh attacker.
        final LivingEntity direct = freshAttacker(mage);
        if (direct != null) {
            propose(mage, inspected, proposed, direct, TargetSource.DIRECT);
        }
        final Optional<LivingEntity> owner = resolveOwner(mage, level);
        owner.map(CircleMageRuntime::freshAttacker)
            .ifPresent(attacker -> propose(mage, inspected, proposed, attacker, TargetSource.OWNER));

        CircleMageRules.select(inspected)
            .ifPresent(candidate -> mage.setMageState(mage.mageState()
                .withThreat(CircleMageState.Threat.of(
                    candidate.id(), dimensionOf(level), candidate.source()))
                .cancelSessionOnly()));
    }

    private static void propose(
        final CircleMageEntity mage,
        final List<Candidate> inspected,
        final java.util.Set<UUID> proposed,
        final LivingEntity candidate,
        final TargetSource source
    ) {
        if (candidate == null || !proposed.add(candidate.getUUID())) {
            return;
        }
        mage.mageCounters().candidateVisits++;
        final double distanceSquared = mage.distanceToSqr(candidate);
        if (!legalTarget(mage, candidate)
            || distanceSquared > CircleMageRules.TARGET_RADIUS_SQUARED) {
            return;
        }
        mage.mageCounters().lineOfSightChecks++;
        final boolean visible = mage.getSensing().hasLineOfSight(candidate);
        inspected.add(new Candidate(candidate.getUUID(), source, distanceSquared, visible));
    }

    /** Vanilla keeps the last-hurt-by relation fresh for forty ticks. */
    private static LivingEntity freshAttacker(final LivingEntity subject) {
        final LivingEntity attacker = subject.getLastHurtByMob();
        if (attacker == null || subject.getLastHurtByMobTimestamp() <= 0) {
            return null;
        }
        return subject.tickCount - subject.getLastHurtByMobTimestamp()
            > OWNER_ATTACKER_FRESHNESS_TICKS ? null : attacker;
    }

    static final int OWNER_ATTACKER_FRESHNESS_TICKS = 40;

    // ---------------------------------------------------------------- owner, formation, aura

    private static Optional<LivingEntity> resolveOwnerWhenDue(
        final CircleMageEntity mage,
        final ServerLevel level
    ) {
        CircleMageState state = mage.mageState();
        if (state.cadence().ownerCheckTicks() > 0) {
            return resolveOwner(mage, level);
        }
        mage.setMageState(state.withCadence(new CircleMageState.Cadence(
            state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
            state.cadence().auraTicks(), CircleMageRules.OWNER_CHECK_INTERVAL_TICKS,
            state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
            state.cadence().safeStepTicks(), state.cadence().routeFailures(),
            state.cadence().routeRetryTicks()
        )));
        mage.mageCounters().ownerLookups++;
        return resolveOwner(mage, level);
    }

    /** Direct current-level UUID lookup only: absence is safe and no dimension is enumerated. */
    private static Optional<LivingEntity> resolveOwner(
        final CircleMageEntity mage,
        final ServerLevel level
    ) {
        return CreatureBehaviorState.owner(mage)
            .map(level::getPlayerByUUID)
            .filter(player -> player != null && player.isAlive() && player.level() == level)
            .map(LivingEntity.class::cast);
    }

    private static void followOwner(
        final CircleMageEntity mage,
        final ServerLevel level,
        final Optional<LivingEntity> owner
    ) {
        if (owner.isEmpty()) {
            mage.setMageState(mage.mageState().withMode(Mode.IDLE));
            return;
        }
        final LivingEntity target = owner.orElseThrow();
        mage.setMageState(mage.mageState().withMode(Mode.FOLLOWING));
        final double distanceSquared = mage.distanceToSqr(target);
        final CircleMageState state = mage.mageState();
        if (CircleMageRules.safeStepAllowed(distanceSquared, state.cadence().safeStepTicks())) {
            mage.setMageState(state.withCadence(new CircleMageState.Cadence(
                state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
                state.cadence().auraTicks(), state.cadence().ownerCheckTicks(),
                state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
                CircleMageRules.SAFE_STEP_INTERVAL_TICKS, state.cadence().routeFailures(),
                state.cadence().routeRetryTicks()
            )));
            if (safeOwnerStep(mage, level, target)) {
                return;
            }
        }
        final int slot = formationSlot(mage, level, target);
        final double angle = Math.PI * 2.0D * slot / CircleMageRules.MAX_COVEN_MAGES;
        final BlockPos station = BlockPos.containing(
            target.getX() + Math.cos(angle) * 2.5D,
            target.getY(),
            target.getZ() + Math.sin(angle) * 2.5D
        );
        requestRoute(mage, level, station, ROUTE_SPEED);
    }

    private static int formationSlot(
        final CircleMageEntity mage,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        return CircleMageRules.formationSlot(mage.getUUID(), sameOwnerPeers(mage, level, owner));
    }

    /**
     * Visits at most eight loaded same-owner Mages inside the sixteen-block query box. This is the
     * only peer query and it never retains an entity reference beyond the pass.
     */
    private static List<UUID> sameOwnerPeers(
        final CircleMageEntity mage,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(mage);
        if (ownerId.isEmpty()) {
            return List.of();
        }
        final List<UUID> peers = new ArrayList<>();
        for (final CircleMageEntity peer : BoundedEntityQuery.collect(
            level,
            CircleMageEntity.class,
            owner.getBoundingBox().inflate(CircleMageRules.FORMATION_QUERY_RADIUS),
            other -> other != mage && other.isAlive()
                && CreatureBehaviorState.owner(other).equals(ownerId),
            CircleMageRules.MAX_FORMATION_PEERS_VISITED
        )) {
            mage.mageCounters().peerVisits++;
            peers.add(peer.getUUID());
        }
        return List.copyOf(peers);
    }

    /**
     * The exact existing Regeneration I for sixty ticks on a twenty-tick refresh, applied by the
     * lowest-UUID eligible provider inside the inspected set. No stacking amplifier, health
     * transfer, invulnerability, particle flood, cross-dimension aura, or new effect.
     */
    private static void applyOwnerAura(
        final CircleMageEntity mage,
        final ServerLevel level,
        final Optional<LivingEntity> owner
    ) {
        CircleMageState state = mage.mageState();
        if (state.cadence().auraTicks() > 0 || owner.isEmpty()) {
            return;
        }
        mage.setMageState(state.withCadence(new CircleMageState.Cadence(
            state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
            CircleMageRules.AURA_INTERVAL_TICKS, state.cadence().ownerCheckTicks(),
            state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
            state.cadence().safeStepTicks(), state.cadence().routeFailures(),
            state.cadence().routeRetryTicks()
        )));
        final LivingEntity target = owner.orElseThrow();
        if (!CircleMageRules.auraEligible(true, target.isAlive(), target.level() == level,
            mage.distanceToSqr(target))) {
            return;
        }
        if (!CircleMageRules.auraProvider(mage.getUUID(), sameOwnerPeers(mage, level, target))) {
            return;
        }
        mage.mageCounters().auraApplications++;
        target.addEffect(new MobEffectInstance(
            MobEffects.REGENERATION, CircleMageRules.AURA_DURATION_TICKS, 0, true, false
        ));
    }

    // ---------------------------------------------------------------- peer report

    private static void emitPeerReportWhenDue(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        if (!CircleMageRules.mayEmitReport(
            state.threat().source(), state.cadence().reportCooldownTicks(),
            mage.mageTransient().receivedAsReport
        )) {
            return;
        }
        mage.setMageState(state.withCadence(new CircleMageState.Cadence(
            state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
            state.cadence().auraTicks(), state.cadence().ownerCheckTicks(),
            state.cadence().peerScanTicks(), CircleMageRules.PEER_SCAN_INTERVAL_TICKS,
            state.cadence().safeStepTicks(), state.cadence().routeFailures(),
            state.cadence().routeRetryTicks()
        )));
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(mage);
        if (ownerId.isEmpty()) {
            return;
        }
        final List<Candidate> inspected = new ArrayList<>();
        final List<CircleMageEntity> loaded = new ArrayList<>();
        for (final CircleMageEntity peer : BoundedEntityQuery.collect(
            level,
            CircleMageEntity.class,
            mage.getBoundingBox().inflate(CircleMageRules.PEER_RADIUS),
            other -> other != mage && other.isAlive()
                && CreatureBehaviorState.owner(other).equals(ownerId),
            CircleMageRules.MAX_PEERS_VISITED
        )) {
            mage.mageCounters().peerVisits++;
            loaded.add(peer);
            inspected.add(new Candidate(
                peer.getUUID(), TargetSource.NONE, mage.distanceToSqr(peer), true
            ));
        }
        final List<UUID> recipients = CircleMageRules.reportRecipients(inspected);
        if (recipients.isEmpty()) {
            return;
        }
        final UUID target = state.threat().id().orElseThrow();
        loaded.stream()
            .filter(peer -> recipients.contains(peer.getUUID()))
            .forEach(peer -> receiveReport(peer, level, target));
        mage.mageCounters().reportsEmitted++;
        level.playSound(null, mage.getX(), mage.getY(), mage.getZ(),
            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 0.6F, 1.2F);
    }

    /** One hop. The receiver revalidates independently and can never emit another report. */
    private static void receiveReport(
        final CircleMageEntity receiver,
        final ServerLevel level,
        final UUID targetId
    ) {
        if (receiver.mageState().threat().present()) {
            return;
        }
        final Optional<LivingEntity> resolved = resolveLiving(level, targetId);
        if (!CircleMageRules.reportAcceptable(
            true,
            resolved.map(living -> legalTarget(receiver, living)).orElse(false),
            resolved.map(living -> living.level() == level).orElse(false),
            CircleMageRules.REPORT_EXPIRY_TICKS
        )) {
            return;
        }
        receiver.mageTransient().receivedAsReport = true;
        receiver.mageCounters().reportsAccepted++;
        receiver.setMageState(receiver.mageState().withThreat(CircleMageState.Threat.of(
            targetId, dimensionOf(level), TargetSource.PEER_REPORT
        )).cancelSessionOnly());
    }

    // ---------------------------------------------------------------- bolt

    private static void engageThreat(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final Optional<LivingEntity> resolved = resolveLiving(level, state.threat().id().orElseThrow());
        if (resolved.isEmpty()) {
            return;
        }
        final LivingEntity target = resolved.orElseThrow();
        mage.getLookControl().setLookAt(target);
        final double distanceSquared = mage.distanceToSqr(target);
        mage.mageCounters().lineOfSightChecks++;
        final boolean sight = mage.getSensing().hasLineOfSight(target);
        if (state.cadence().castRecoveryTicks() <= 0
            && CircleMageRules.boltEligible(sight, legalTarget(mage, target), distanceSquared)) {
            mage.setMageState(state
                .withAction(CircleMageState.ActionState.bolt(
                    target.getUUID(), dimensionOf(level), state.study().focusPrepared()
                ))
                .withMode(Mode.DEFENDING));
            level.playSound(null, mage.getX(), mage.getY(), mage.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.7F, 1.3F);
            return;
        }
        if (distanceSquared > CircleMageRules.BOLT_MAX_RANGE_SQUARED) {
            requestRoute(mage, level, target.blockPosition(), ROUTE_SPEED);
        }
    }

    private static void advanceAction(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final CircleMageState.ActionState action = state.action();
        if (action.windupRemainingTicks() > 0) {
            if (action.action() == Action.STUDY) {
                holdRehearsal(mage, level);
            } else {
                action.targetId()
                    .flatMap(id -> resolveLiving(level, id))
                    .ifPresent(target -> mage.getLookControl().setLookAt(target));
            }
            return;
        }
        if (action.action() == Action.STUDY) {
            completeRehearsal(mage, level);
            return;
        }
        completeBolt(mage, level);
    }

    /**
     * One attributed indirect-magic hit, five normally and seven with a reserved focus. The focus
     * is consumed only after {@code hurtServer} accepted the focused hit; a rejected hit consumes
     * nothing and produces no success feedback. No projectile entity, area effect, new damage type,
     * status effect, homing, through-wall cast, or recursive peer cast is added.
     */
    private static void completeBolt(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final CircleMageState.ActionState action = state.action();
        final Optional<LivingEntity> resolved = action.targetId().flatMap(id -> resolveLiving(level, id));
        mage.mageCounters().lineOfSightChecks++;
        final boolean eligible = resolved
            .filter(_ -> action.dimension()
                .map(stored -> stored.equals(dimensionOf(level))).orElse(false))
            .filter(_ -> !mage.mageTransient().hazardActive)
            .map(target -> CircleMageRules.boltEligible(
                mage.getSensing().hasLineOfSight(target),
                legalTarget(mage, target),
                mage.distanceToSqr(target)
            ))
            .orElse(false);
        CircleMageState updated = state
            .withAction(CircleMageState.ActionState.none())
            .withCadence(new CircleMageState.Cadence(
                CircleMageRules.BOLT_RECOVERY_TICKS, state.cadence().withdrawalTicks(),
                state.cadence().auraTicks(), state.cadence().ownerCheckTicks(),
                state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
                state.cadence().safeStepTicks(), state.cadence().routeFailures(),
                state.cadence().routeRetryTicks()
            ));
        if (!eligible) {
            mage.mageCounters().boltsCancelled++;
            mage.setMageState(updated);
            return;
        }
        final boolean focusReserved = action.focusReserved() && state.study().focusPrepared();
        final boolean accepted = resolved.orElseThrow().hurtServer(
            level,
            level.damageSources().indirectMagic(mage, mage),
            CircleMageRules.boltDamage(focusReserved)
        );
        if (accepted) {
            mage.mageCounters().boltsCast++;
            level.sendParticles(ParticleTypes.ENCHANT, mage.getX(), mage.getY() + 1.2D, mage.getZ(),
                CircleMageRules.MAX_FEEDBACK_PARTICLES, 0.3D, 0.4D, 0.3D, 0.02D);
        } else {
            mage.mageCounters().boltsCancelled++;
        }
        if (CircleMageRules.consumesFocus(focusReserved, accepted)) {
            mage.mageCounters().focusConsumed++;
            updated = updated.withStudy(new CircleMageState.Study(
                false, updated.study().workstation(), updated.study().dimension(),
                updated.study().studyCooldownTicks(), updated.study().searchCooldownTicks()
            ));
        }
        mage.setMageState(updated);
    }

    // ---------------------------------------------------------------- study and conclave

    private static void proposeStudy(final CircleMageEntity mage, final ServerLevel level) {
        CircleMageState state = mage.mageState();
        if (!state.study().hasWorkstation()) {
            searchWorkstation(mage, level);
            return;
        }
        final BlockPos workstation = state.study().workstation().orElseThrow();
        if (!revalidateWorkstation(mage, level, workstation)) {
            mage.setMageState(state.cancelLiveWork());
            return;
        }
        if (mage.distanceToSqr(Vec3.atCenterOf(workstation))
            > CircleMageRules.WORKSTATION_ARRIVAL_DISTANCE_SQUARED) {
            requestRoute(mage, level, workstation, ROUTE_SPEED);
            return;
        }
        mage.getNavigation().stop();
        state = joinOrOpenSession(mage, level, workstation);
        mage.setMageState(state
            .withAction(CircleMageState.ActionState.study(dimensionOf(level)))
            .withMode(Mode.STUDYING));
    }

    /**
     * A bounded temporary conclave. Eligible peers are loaded, alive, same owner, exact Circle
     * Mage, targetless, safe, and near the site; at most eight are inspected and at most two
     * accepted, so the total session size is at most three. Lowest UUID coordinates that one
     * session only: it is ordering, not rank, authority, or succession.
     */
    private static CircleMageState joinOrOpenSession(
        final CircleMageEntity mage,
        final ServerLevel level,
        final BlockPos workstation
    ) {
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(mage);
        if (ownerId.isEmpty()) {
            // A solo or unbound Mage simply rehearses alone; no session is opened at all.
            return mage.mageState();
        }
        final List<UUID> eligible = new ArrayList<>();
        int visited = 0;
        for (final CircleMageEntity peer : BoundedEntityQuery.collect(
            level,
            CircleMageEntity.class,
            new AABB(workstation).inflate(CircleMageRules.CONCLAVE_RADIUS),
            other -> other != mage && other.isAlive(),
            CircleMageRules.MAX_PEERS_VISITED
        )) {
            visited++;
            mage.mageCounters().peerVisits++;
            if (CircleMageRules.conclaveAdmits(
                CreatureBehaviorState.owner(peer).equals(ownerId),
                peer.creatureKind() == CreatureKind.CIRCLE_MAGE,
                peer.getTarget() == null,
                !peer.mageTransient().hazardActive,
                peer.mageState().session().present(),
                peer.distanceToSqr(Vec3.atCenterOf(workstation))
            )) {
                eligible.add(peer.getUUID());
            }
        }
        final List<UUID> accepted = CircleMageRules.acceptPeers(eligible);
        if (accepted.isEmpty()) {
            return mage.mageState();
        }
        final UUID coordinator = CircleMageRules.coordinator(mage.getUUID(), accepted);
        mage.mageCounters().sessionsJoined++;
        return mage.mageState().withSession(CircleMageState.Session.joined(
            coordinator,
            dimensionOf(level),
            level.getGameTime(),
            CircleMageRules.sessionSlot(mage.getUUID(), coordinator, accepted)
        ));
    }

    private static void advanceSession(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        if (!state.session().present()) {
            return;
        }
        final Optional<BlockPos> workstation = state.study().workstation();
        if (CircleMageRules.sessionReleased(
            state.session().coordinator()
                .map(id -> level.getEntity(id) instanceof CircleMageEntity).orElse(false),
            workstation.map(position -> revalidateWorkstation(mage, level, position)).orElse(false),
            !mage.mageTransient().hazardActive,
            mage.getTarget() == null
        )) {
            mage.mageCounters().sessionsReleased++;
            mage.setMageState(state.withSession(CircleMageState.Session.none()));
        }
    }

    private static void holdRehearsal(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final Optional<BlockPos> workstation = state.study().workstation();
        if (workstation.isEmpty()
            || !revalidateWorkstation(mage, level, workstation.orElseThrow())
            || mage.mageTransient().hazardActive
            || state.threat().present()) {
            mage.setMageState(state.cancelLiveWork());
            return;
        }
        if (state.action().windupRemainingTicks() % CircleMageRules.STUDY_PARTICLE_INTERVAL_TICKS == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, mage.getX(), mage.getY() + 1.2D, mage.getZ(),
                CircleMageRules.MAX_STUDY_PARTICLES, 0.25D, 0.35D, 0.25D, 0.01D);
        }
    }

    /**
     * Focus is a game resource in entity state only. It is not an item, spell knowledge, recipe,
     * advancement, ritual result, or persistent curriculum, and each participant receives its own
     * focus independently, so a no-show never blocks anyone.
     */
    private static void completeRehearsal(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final boolean valid = state.study().workstation()
            .map(position -> revalidateWorkstation(mage, level, position))
            .orElse(false);
        CircleMageState updated = state
            .withAction(CircleMageState.ActionState.none())
            .withSession(CircleMageState.Session.none())
            .withMode(Mode.IDLE);
        if (valid) {
            mage.mageCounters().focusPrepared++;
            updated = updated.withStudy(new CircleMageState.Study(
                true, Optional.empty(), Optional.empty(),
                CircleMageRules.STUDY_COOLDOWN_TICKS, CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS
            ));
            level.sendParticles(ParticleTypes.ENCHANT, mage.getX(), mage.getY() + 1.2D, mage.getZ(),
                CircleMageRules.MAX_FEEDBACK_PARTICLES, 0.3D, 0.4D, 0.3D, 0.02D);
        } else {
            updated = updated.withStudy(new CircleMageState.Study(
                state.study().focusPrepared(), Optional.empty(), Optional.empty(),
                state.study().studyCooldownTicks(), CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS
            ));
        }
        mage.setMageState(updated);
    }

    private static void searchWorkstation(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        final BlockPos center = mage.blockPosition();
        Optional<BlockPos> found = Optional.empty();
        int visits = 0;
        int reads = 0;
        for (final CircleMageRules.SearchOffset offset : CircleMageRules.workstationOffsets(
            mage.getUUID(), CircleMageRules.WORKSTATION_HORIZONTAL_RADIUS,
            CircleMageRules.WORKSTATION_VERTICAL_RADIUS, CircleMageRules.MAX_WORKSTATION_CANDIDATES
        )) {
            if (visits >= CircleMageRules.MAX_WORKSTATION_CANDIDATES
                || reads >= CircleMageRules.MAX_WORKSTATION_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (!level.hasChunkAt(candidate)) {
                continue;
            }
            visits++;
            reads++;
            mage.mageCounters().workstationVisits++;
            mage.mageCounters().blockReads++;
            if (AmbientActivityTags.matches(ActivityType.ARCANE_STUDY, level.getBlockState(candidate))) {
                found = Optional.of(candidate.immutable());
                break;
            }
        }
        // Only a failed search burns the 120-tick cadence. A successful search must leave the
        // cadence alone so the Mage can walk to the workstation it just found on the next tick
        // instead of standing still for two seconds first.
        mage.setMageState(found
            .map(position -> state.withStudy(new CircleMageState.Study(
                state.study().focusPrepared(), Optional.of(position), Optional.of(dimensionOf(level)),
                state.study().studyCooldownTicks(), state.study().searchCooldownTicks()
            )))
            .orElseGet(() -> state.withStudy(new CircleMageState.Study(
                state.study().focusPrepared(), Optional.empty(), Optional.empty(),
                state.study().studyCooldownTicks(), CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS
            ))));
    }

    private static boolean revalidateWorkstation(
        final CircleMageEntity mage,
        final ServerLevel level,
        final BlockPos workstation
    ) {
        if (!level.hasChunkAt(workstation) || !level.getWorldBorder().isWithinBounds(workstation)) {
            return false;
        }
        mage.mageCounters().blockReads++;
        return AmbientActivityTags.matches(ActivityType.ARCANE_STUDY, level.getBlockState(workstation));
    }

    // ---------------------------------------------------------------- recruitment surface

    /**
     * Pure decision over directly observed facts. Called from the entity's interaction surface
     * before the shared binding path so a same-owner repeat and a conflicting owner never reach it.
     */
    public static RecruitmentResult recruitmentDecision(
        final CircleMageEntity mage,
        final ServerLevel level,
        final Player player,
        final InteractionHand hand
    ) {
        final CreatureBehaviorProfile profile =
            CreatureBehaviorProfile.find(CreatureKind.CIRCLE_MAGE).orElseThrow();
        final var held = player.getItemInHand(hand);
        return CircleMageRules.recruitmentDecision(
            CreatureBehaviorState.owner(mage),
            player.getUUID(),
            profile.offering().stream().anyMatch(held::is),
            true,
            CovenRosterData.get(level).count(player.getUUID())
        );
    }

    /** Idempotent same-owner success: no offering is consumed and no roster slot is spent. */
    public static InteractionResult acknowledgeExistingBinding(
        final CircleMageEntity mage,
        final ServerLevel level,
        final Player player
    ) {
        CovenRosterData.get(level).register(player.getUUID(), mage.getUUID());
        mage.mageCounters().rosterRegistrations++;
        level.playSound(null, mage.getX(), mage.getY(), mage.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /** Called after a successful new admission so the roster and presentation stay consistent. */
    public static void onRecruited(final CircleMageEntity mage, final ServerLevel level) {
        registerWithRoster(mage, level);
        mage.setMageState(mage.mageState().cancelLiveWork());
        mage.setTarget(null);
    }

    // ---------------------------------------------------------------- Seer recall

    /**
     * Explicit Seer recall reconciliation. It cancels bolt, study, conclave, report, and
     * destination state and adopts the called circle as a soft anchor after the runtime's own safe
     * placement. It changes no Seer result: positions, feedback, and participant counting are
     * decided entirely by {@code SeerCovenRuntime}.
     */
    public static void onSeerRecall(
        final CircleMageEntity mage,
        final ServerLevel level,
        final BlockPos circleCenter
    ) {
        mage.mageTransient().recallPending = true;
        mage.mageTransient().destination = null;
        mage.mageCounters().recallReconciliations++;
        mage.setMageState(mage.mageState()
            .cancelLiveWork()
            .withAnchor(new CircleMageState.Anchor(
                Optional.of(circleCenter.immutable()), Optional.of(dimensionOf(level))
            )));
        mage.setTarget(null);
        mage.getNavigation().stop();
    }

    private static void finishRecall(final CircleMageEntity mage) {
        mage.mageTransient().recallPending = false;
        mage.setMageState(mage.mageState().withMode(Mode.IDLE));
    }

    // ---------------------------------------------------------------- damage, hazard, withdrawal

    public static void onAcceptedDamage(
        final CircleMageEntity mage,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || !legalTarget(mage, attacker)) {
            return;
        }
        mage.mageTransient().receivedAsReport = false;
        mage.setMageState(mage.mageState()
            .withThreat(CircleMageState.Threat.of(
                attacker.getUUID(), dimensionOf(level), TargetSource.DIRECT
            ))
            .cancelSessionOnly());
    }

    private static boolean observeHazardWhenDue(final CircleMageEntity mage, final ServerLevel level) {
        final TransientState scratch = mage.mageTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive;
        }
        scratch.hazardCooldownTicks = CircleMageRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = observeHazard(mage, level);
        return scratch.hazardActive;
    }

    private static void escapeHazard(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        if (state.action().pending()) {
            mage.mageCounters().boltsCancelled++;
        }
        mage.mageCounters().hazardInterruptions++;
        mage.setMageState(state.cancelLiveWork());
        findSafeDestination(mage, level, mage.blockPosition(), 6, 2,
            CircleMageRules.MAX_SAFE_CANDIDATES, Optional.empty(), true
        ).ifPresent(destination -> requestRoute(mage, level, destination, ESCAPE_SPEED));
    }

    private static void withdraw(final CircleMageEntity mage, final ServerLevel level) {
        CircleMageState state = mage.mageState();
        if (state.cadence().withdrawalTicks() <= 0) {
            mage.mageCounters().withdrawals++;
            state = state.cancelLiveWork().withCadence(new CircleMageState.Cadence(
                state.cadence().castRecoveryTicks(), CircleMageRules.WITHDRAW_TICKS,
                state.cadence().auraTicks(), state.cadence().ownerCheckTicks(),
                state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
                state.cadence().safeStepTicks(), state.cadence().routeFailures(),
                state.cadence().routeRetryTicks()
            ));
        }
        mage.setMageState(state.withMode(Mode.WITHDRAWING));
        final Optional<Vec3> away = state.threat().id()
            .flatMap(id -> resolveLiving(level, id))
            .map(LivingEntity::position);
        // No self-heal, shield, invulnerability, teleport, or despawn is granted by withdrawal.
        findSafeDestination(mage, level, mage.blockPosition(), 6, 2,
            CircleMageRules.MAX_SAFE_CANDIDATES, away, true
        ).ifPresent(destination -> requestRoute(mage, level, destination, ESCAPE_SPEED));
    }

    private static void holdAnchor(final CircleMageEntity mage, final ServerLevel level) {
        final CircleMageState state = mage.mageState();
        mage.setMageState(state.withMode(Mode.IDLE));
        final Optional<BlockPos> anchor = state.anchor().position();
        if (anchor.isEmpty() || !level.hasChunkAt(anchor.orElseThrow())) {
            return;
        }
        if (mage.distanceToSqr(Vec3.atCenterOf(anchor.orElseThrow()))
            > CircleMageRules.FORMATION_RADIUS_SQUARED) {
            requestRoute(mage, level, anchor.orElseThrow(), ROUTE_SPEED);
        }
    }

    private static boolean observeHazard(final CircleMageEntity mage, final ServerLevel level) {
        if (mage.isOnFire() || mage.isInLava()) {
            return true;
        }
        if (mage.isUnderWater() && mage.getAirSupply() < mage.getMaxAirSupply()) {
            return true;
        }
        if (!footprintLoaded(level, mage.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos center = mage.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= CircleMageRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    mage.mageCounters().blockReads++;
                    if (isHazardBlock(level.getBlockState(center.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    // ---------------------------------------------------------------- relationship

    public static boolean legalTarget(final CircleMageEntity mage, final LivingEntity candidate) {
        return CircleMageRules.relationLegal(observe(mage, candidate));
    }

    private static RelationFacts observe(final CircleMageEntity mage, final LivingEntity candidate) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(mage);
        final CreatureKind kind =
            candidate instanceof ArcaneCreature creature ? creature.creatureKind() : null;
        final boolean ineligible = candidate instanceof Player subject
            && (subject.isCreative() || subject.isSpectator());
        return new RelationFacts(
            true,
            candidate.isAlive() && !candidate.isRemoved(),
            candidate.level() == mage.level(),
            candidate == mage,
            candidate.isInvulnerable() || !candidate.canBeSeenAsEnemy(),
            owner.filter(candidate.getUUID()::equals).isPresent(),
            candidate instanceof Mob other && owner.isPresent()
                && CreatureBehaviorState.owner(other).equals(owner),
            kind == CreatureKind.CIRCLE_MAGE,
            ineligible,
            candidate instanceof Animal
                || candidate instanceof AbstractVillager
                || candidate instanceof AbstractGolem,
            candidate instanceof Mob other && CreatureBehaviorState.owner(other).isPresent()
                && !CreatureBehaviorState.owner(other).equals(owner),
            candidate.canBeSeenAsEnemy() && candidate.attackable()
        );
    }

    // ---------------------------------------------------------------- movement lease

    /** The only place a Circle Mage path is ever created. */
    static boolean requestRoute(
        final CircleMageEntity mage,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = mage.mageTransient();
        CircleMageState state = mage.mageState();
        if (!CircleMageRules.pathRequestAllowed(
            scratch.pathCooldownTicks, state.cadence().routeRetryTicks()
        )) {
            return false;
        }
        if (!level.hasChunkAt(destination)) {
            return false;
        }
        scratch.pathCooldownTicks = CircleMageRules.PATH_INTERVAL_TICKS;
        mage.mageCounters().navigationRequests++;
        final Path path = mage.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && mage.getNavigation().moveTo(path, speed);
        final int failures = CircleMageRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new CircleMageRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (CircleMageRules.routeExhausted(failures)) {
            retry = CircleMageRules.routeBackoffAfter(failures);
            mage.getNavigation().stop();
            scratch.destination = null;
            state = state.cancelLiveWork();
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        mage.setMageState(state.withCadence(new CircleMageState.Cadence(
            state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
            state.cadence().auraTicks(), state.cadence().ownerCheckTicks(),
            state.cadence().peerScanTicks(), state.cadence().reportCooldownTicks(),
            state.cadence().safeStepTicks(),
            CircleMageRules.routeExhausted(failures) ? 0 : failures,
            retry
        )));
        return accepted;
    }

    /**
     * The owner-relative safe step used only beyond thirty-two blocks. The entire actual Mage
     * destination AABB must already be loaded, inside the world border, collision free, and fluid
     * safe; failure falls back to bounded routing and never force-loads a chunk.
     */
    private static boolean safeOwnerStep(
        final CircleMageEntity mage,
        final ServerLevel level,
        final LivingEntity owner
    ) {
        final BlockPos center = owner.blockPosition();
        int reads = 0;
        for (final CircleMageRules.SearchOffset offset :
            CircleMageRules.safeStepOffsets(mage.getUUID(), 3)) {
            if (reads + 2 > CircleMageRules.MAX_SAFE_STEP_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            final AABB box = mage.getType().getDimensions()
                .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
            if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
                continue;
            }
            reads += 2;
            mage.mageCounters().blockReads += 2;
            if (isHazardBlock(level.getBlockState(candidate))
                || !level.getFluidState(candidate).isEmpty()
                || !level.noCollision(mage, box)) {
                continue;
            }
            mage.mageCounters().safeSteps++;
            mage.getNavigation().stop();
            mage.snapTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            return true;
        }
        return false;
    }

    static Optional<BlockPos> findSafeDestination(
        final CircleMageEntity mage,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = mage.mageCounters();
        counters.safeSearches++;
        final BlockPos origin = mage.blockPosition();
        final java.util.Comparator<CircleMageRules.SafeCandidate> preference =
            CircleMageRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        CircleMageRules.SafeCandidate bestFacts = null;
        for (final CircleMageRules.SearchOffset offset : CircleMageRules.safeSearchOffsets(
            mage.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + 2 > CircleMageRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            final AABB box = mage.getType().getDimensions()
                .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
            if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
                continue;
            }
            reads += 2;
            counters.blockReads += 2;
            final BlockState blockState = level.getBlockState(candidate);
            final var fluidState = level.getFluidState(candidate);
            final boolean hazardous = isHazardBlock(blockState) || !fluidState.isEmpty();
            if (avoidHazards && hazardous) {
                continue;
            }
            if (blockState.is(Blocks.LAVA) || fluidState.is(FluidTags.LAVA)) {
                continue;
            }
            if (!level.noCollision(mage, box)) {
                continue;
            }
            final CircleMageRules.SafeCandidate facts = new CircleMageRules.SafeCandidate(
                awayFrom.map(threat -> Vec3.atCenterOf(candidate).distanceToSqr(threat)).orElse(0.0D),
                !hazardous,
                candidate.distSqr(origin),
                candidate.asLong()
            );
            if (bestFacts == null || preference.compare(facts, bestFacts) < 0) {
                bestFacts = facts;
                best = candidate.immutable();
            }
        }
        return Optional.ofNullable(best);
    }

    static boolean footprintLoaded(final ServerLevel level, final AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ));
    }

    private static Optional<LivingEntity> resolveLiving(final ServerLevel level, final UUID id) {
        final Entity resolved = level.getEntity(id);
        return resolved instanceof LivingEntity living && living.isAlive()
            ? Optional.of(living)
            : Optional.empty();
    }

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
