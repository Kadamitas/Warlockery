package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Action;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Candidate;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Hex;
import com.kadamitas.warlockery.entity.HedgeCroneRules.HexContext;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Mode;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Priority;
import com.kadamitas.warlockery.entity.HedgeCroneRules.RelationFacts;
import com.kadamitas.warlockery.entity.HedgeCroneRules.ThreatClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Hedge Crone behavior controller. It owns MOVE and target assignment
 * completely; the entity's look goals own LOOK and its close-defense goal owns nothing. Every
 * scan, block read, line-of-sight ray, path request, and feedback burst is charged against the
 * declared hard budgets. Nothing here forces a chunk, edits a block or inventory, opens a
 * container, evaluates a recipe or ritual, iterates the level globally, or writes another
 * entity's persistent state.
 */
public final class HedgeCroneRuntime {
    private static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final TagKey<Item> RANGED_WEAPONS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("warlockery", "ai/ranged_weapons")
    );
    private static final double ROUTE_SPEED = 1.0D;
    private static final double ESCAPE_SPEED = 1.15D;
    private static final ThreadLocal<Boolean> APPLYING_WARD = ThreadLocal.withInitial(() -> false);

    private HedgeCroneRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long candidateVisits;
        long lineOfSightChecks;
        long blockReads;
        long workstationVisits;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long warningsStarted;
        long escalations;
        long hexesCast;
        long hexesCancelled;
        long wardsPrepared;
        long wardsDischarged;
        long hazardInterruptions;
        long releases;
        long withdrawals;
        long closeDefenseHits;

        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long blockReads() { return blockReads; }
        public long workstationVisits() { return workstationVisits; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long warningsStarted() { return warningsStarted; }
        public long escalations() { return escalations; }
        public long hexesCast() { return hexesCast; }
        public long hexesCancelled() { return hexesCancelled; }
        public long wardsPrepared() { return wardsPrepared; }
        public long wardsDischarged() { return wardsDischarged; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long releases() { return releases; }
        public long withdrawals() { return withdrawals; }
        public long closeDefenseHits() { return closeDefenseHits; }
    }

    /**
     * Execution scratch rebuilt after every load. Losing it can delay work by one cadence but can
     * never replay a hex, ward discharge, particle, or path.
     */
    public static final class TransientState {
        boolean reconciled;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int scanCooldownTicks;
        int safeLoadedTicks;
        boolean hazardActive;
        BlockPos destination;
        boolean projectileAttacker;

        public void resetForLoad() {
            reconciled = false;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            scanCooldownTicks = 0;
            safeLoadedTicks = 0;
            hazardActive = false;
            destination = null;
            projectileAttacker = false;
        }
    }

    // ---------------------------------------------------------------- tick

    public static void tick(final HedgeCroneEntity crone, final ServerLevel level) {
        reconcileOnLoad(crone, level);
        advanceLoadedTimers(crone);
        revalidateThreat(crone, level);

        final boolean hazard = observeHazardWhenDue(crone, level);
        final HedgeCroneState state = crone.croneState();
        final Priority priority = HedgeCroneRules.priority(
            hazard,
            state.action().pending(),
            state.threat().threatClass() == ThreatClass.DIRECT,
            HedgeCroneRules.shouldWithdraw(
                HedgeCroneRules.healthFraction(crone.getHealth(), crone.getMaxHealth())
            ),
            state.threat().threatClass() == ThreatClass.BOUNDARY_ESCALATED,
            state.threat().threatClass() == ThreatClass.BOUNDARY_WARNED,
            HedgeCroneRules.wardPreparationAllowed(
                state.work().wardPrepared(),
                state.work().wardCooldownTicks(),
                !hazard,
                state.threat().present(),
                state.cadence().withdrawalTicks() > 0
            )
        );

        switch (priority) {
            case HAZARD -> escapeHazard(crone, level);
            case ACTION -> advanceAction(crone, level);
            case DIRECT_DEFENSE, ESCALATED_THREAT -> engageThreat(crone, level);
            case WITHDRAW -> withdraw(crone, level);
            case WARNING -> advanceWarning(crone, level);
            case WARD_PREPARATION -> pursueWard(crone, level);
            case ANCHOR_RETURN -> patrolAnchor(crone, level);
        }
        publishPresentation(crone);
    }

    private static void publishPresentation(final HedgeCroneEntity crone) {
        final HedgeCroneState state = crone.croneState();
        crone.syncPresentation(state.mode(), state.work().wardPrepared());
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final HedgeCroneEntity crone, final ServerLevel level) {
        final TransientState scratch = crone.croneTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.scanCooldownTicks =
            HedgeCroneRules.stableOffset(crone.getUUID(), HedgeCroneRules.SCAN_INTERVAL_TICKS);
        HedgeCroneState state = crone.croneState();
        if (!state.anchor().present()) {
            // First loaded server tick soft anchor adoption. It claims no block, structure, land,
            // block entity, or chunk, and it never triggers a chunk load.
            state = state.withAnchor(new HedgeCroneState.Anchor(
                Optional.of(crone.blockPosition()), Optional.of(dimensionOf(level))
            ));
        }
        crone.setCroneState(state);
    }

    private static void advanceLoadedTimers(final HedgeCroneEntity crone) {
        final TransientState scratch = crone.croneTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.scanCooldownTicks = Math.max(0, scratch.scanCooldownTicks - 1);
        scratch.safeLoadedTicks = scratch.hazardActive
            ? 0
            : Math.min(HedgeCroneRules.MAX_DEADLINE_TICKS, scratch.safeLoadedTicks + 1);

        HedgeCroneState state = crone.croneState();
        final HedgeCroneState.Cadence cadence = state.cadence();
        state = state.withCadence(new HedgeCroneState.Cadence(
            HedgeCroneRules.decrementLoaded(cadence.castRecoveryTicks()),
            HedgeCroneRules.decrementLoaded(cadence.withdrawalTicks()),
            cadence.routeFailures(),
            HedgeCroneRules.decrementLoaded(cadence.routeRetryTicks()),
            state.anchor().present()
                ? 0
                : Math.min(HedgeCroneRules.ANCHOR_REPLACE_TICKS, cadence.anchorUnavailableTicks() + 1)
        ));
        final HedgeCroneState.Threat threat = state.threat();
        if (threat.present()) {
            state = state.withThreat(new HedgeCroneState.Threat(
                threat.id(), threat.dimension(), threat.threatClass(),
                HedgeCroneRules.decrementLoaded(threat.remainingTicks()),
                HedgeCroneRules.decrementLoaded(threat.warningRemainingTicks()),
                threat.ticksWithoutSight()
            ));
        }
        final HedgeCroneState.ActionState action = state.action();
        if (action.pending()) {
            state = state.withAction(new HedgeCroneState.ActionState(
                action.action(), action.targetId(), action.dimension(), action.hex(),
                HedgeCroneRules.decrementLoaded(action.windupRemainingTicks())
            ));
        }
        final HedgeCroneState.Work work = state.work();
        state = state.withWork(new HedgeCroneState.Work(
            work.wardPrepared(), work.workstation(), work.dimension(),
            HedgeCroneRules.decrementLoaded(work.wardCooldownTicks()),
            HedgeCroneRules.decrementLoaded(work.workstationSearchTicks())
        ));
        crone.setCroneState(state);
    }

    // ---------------------------------------------------------------- perception

    /**
     * Revalidates the one retained threat and, when due, runs the bounded boundary scan. Both the
     * candidate visit budget and the line-of-sight budget are charged for real.
     */
    private static void revalidateThreat(final HedgeCroneEntity crone, final ServerLevel level) {
        HedgeCroneState state = crone.croneState();
        final HedgeCroneState.Threat threat = state.threat();
        if (threat.present()) {
            final Optional<LivingEntity> resolved = resolveLiving(level, threat.id().orElseThrow());
            final boolean valid = resolved
                .filter(living -> threat.dimension()
                    .map(stored -> stored.equals(dimensionOf(level))).orElse(false))
                .map(living -> legalTarget(crone, living))
                .orElse(false);
            final boolean visible = resolved.map(living -> {
                crone.croneCounters().lineOfSightChecks++;
                return crone.getSensing().hasLineOfSight(living);
            }).orElse(false);
            final double distanceSquared =
                resolved.map(crone::distanceToSqr).orElse(Double.MAX_VALUE);
            final int withoutSight = visible
                ? 0
                : Math.min(HedgeCroneRules.LOST_SIGHT_RELEASE_TICKS, threat.ticksWithoutSight() + 1);
            state = state.withThreat(new HedgeCroneState.Threat(
                threat.id(), threat.dimension(), threat.threatClass(), threat.remainingTicks(),
                threat.warningRemainingTicks(), withoutSight
            ));
            if (HedgeCroneRules.threatReleases(
                threat.threatClass(), valid, distanceSquared, withoutSight, threat.remainingTicks()
            )) {
                crone.croneCounters().releases++;
                if (state.action().pending()) {
                    crone.croneCounters().hexesCancelled++;
                }
                state = state.releaseThreat().cancelAction();
                crone.setTarget(null);
            } else if (HedgeCroneRules.motiveAcquires(threat.threatClass())) {
                resolved.filter(_ -> valid).ifPresent(crone::setTarget);
            }
            crone.setCroneState(state);
            return;
        }
        crone.setTarget(null);
        crone.setCroneState(state);
        scanBoundaryWhenDue(crone, level);
    }

    private static void scanBoundaryWhenDue(final HedgeCroneEntity crone, final ServerLevel level) {
        final TransientState scratch = crone.croneTransient();
        if (scratch.scanCooldownTicks > 0) {
            return;
        }
        scratch.scanCooldownTicks = HedgeCroneRules.SCAN_INTERVAL_TICKS;
        final HedgeCroneState state = crone.croneState();
        final BlockPos anchor = state.anchor().position().orElse(crone.blockPosition());
        final List<Candidate> inspected = new ArrayList<>();
        int visits = 0;
        int sightChecks = 0;
        for (final Player player : level.getEntitiesOfClass(
            Player.class,
            new AABB(crone.blockPosition()).inflate(HedgeCroneRules.PERCEPTION_RADIUS)
        )) {
            if (visits >= HedgeCroneRules.MAX_CANDIDATES_VISITED) {
                break;
            }
            visits++;
            crone.croneCounters().candidateVisits++;
            if (!legalTarget(crone, player)) {
                continue;
            }
            boolean visible = false;
            if (sightChecks < HedgeCroneRules.MAX_LINE_OF_SIGHT_CHECKS) {
                sightChecks++;
                crone.croneCounters().lineOfSightChecks++;
                visible = crone.getSensing().hasLineOfSight(player);
            }
            final double anchorDistanceSquared =
                player.distanceToSqr(Vec3.atCenterOf(anchor));
            if (HedgeCroneRules.boundaryCandidate(true, visible, anchorDistanceSquared)) {
                inspected.add(new Candidate(
                    player.getUUID(), false, false, crone.distanceToSqr(player), true
                ));
            }
        }
        HedgeCroneRules.select(inspected).ifPresent(candidate -> beginWarning(crone, level, candidate));
    }

    private static void beginWarning(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final Candidate candidate
    ) {
        crone.croneCounters().warningsStarted++;
        crone.setCroneState(crone.croneState()
            .withThreat(HedgeCroneState.Threat.warned(candidate.id(), dimensionOf(level)))
            .withMode(Mode.WARNING));
        feedback(crone, level, SoundEvents.EVOKER_PREPARE_ATTACK, ParticleTypes.WITCH,
            HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
    }

    /**
     * Holds the immutable twenty-tick warning and escalates only when the identical UUID is still
     * legal, visible, and inside the boundary at execution.
     */
    private static void advanceWarning(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final HedgeCroneState.Threat threat = state.threat();
        if (threat.warningRemainingTicks() > 0) {
            resolveLiving(level, threat.id().orElseThrow())
                .ifPresent(target -> crone.getLookControl().setLookAt(target));
            return;
        }
        final Optional<LivingEntity> resolved = resolveLiving(level, threat.id().orElseThrow());
        final BlockPos anchor = state.anchor().position().orElse(crone.blockPosition());
        crone.croneCounters().lineOfSightChecks++;
        final boolean visible = resolved.map(crone.getSensing()::hasLineOfSight).orElse(false);
        final boolean escalates = HedgeCroneRules.warningEscalates(
            resolved.isPresent(),
            resolved.map(living -> legalTarget(crone, living)).orElse(false),
            visible,
            resolved.map(living -> living.distanceToSqr(Vec3.atCenterOf(anchor))).orElse(Double.MAX_VALUE)
        );
        if (!escalates) {
            crone.croneCounters().releases++;
            crone.setCroneState(state.releaseThreat());
            crone.setTarget(null);
            return;
        }
        crone.croneCounters().escalations++;
        crone.setCroneState(state.withThreat(
            HedgeCroneState.Threat.escalated(threat.id().orElseThrow(), dimensionOf(level))
        ).withMode(Mode.IDLE));
        resolved.ifPresent(crone::setTarget);
    }

    // ---------------------------------------------------------------- combat

    private static void engageThreat(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final Optional<LivingEntity> resolved =
            resolveLiving(level, state.threat().id().orElseThrow());
        if (resolved.isEmpty()) {
            return;
        }
        final LivingEntity target = resolved.orElseThrow();
        crone.getLookControl().setLookAt(target);
        final double distanceSquared = crone.distanceToSqr(target);
        crone.croneCounters().lineOfSightChecks++;
        final boolean sight = crone.getSensing().hasLineOfSight(target);
        if (state.cadence().castRecoveryTicks() <= 0
            && HedgeCroneRules.castEligible(sight, legalTarget(crone, target), distanceSquared)) {
            beginHex(crone, level, target, distanceSquared);
            return;
        }
        // Outside the 3-14 band the runtime is the only mover: one bounded route request keeps the
        // Crone inside its own casting envelope without any goal ever touching navigation.
        if (distanceSquared > HedgeCroneRules.CAST_MAX_RANGE_SQUARED) {
            requestRoute(crone, level, target.blockPosition(), ROUTE_SPEED);
        }
    }

    private static void beginHex(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final LivingEntity target,
        final double distanceSquared
    ) {
        final HedgeCroneState state = crone.croneState();
        final Hex hex = HedgeCroneRules.selectHex(new HexContext(
            crone.croneTransient().projectileAttacker,
            holdsRangedWeapon(target),
            distanceSquared <= 36.0D,
            state.threat().threatClass() == ThreatClass.DIRECT,
            state.threat().threatClass() == ThreatClass.BOUNDARY_ESCALATED
        ));
        crone.setCroneState(state
            .withAction(HedgeCroneState.ActionState.hex(target.getUUID(), dimensionOf(level), hex))
            .withMode(Mode.CASTING));
        feedback(crone, level, SoundEvents.EVOKER_CAST_SPELL, ParticleTypes.WITCH,
            HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
    }

    /**
     * Completes or cancels the one immutable action. The frozen target UUID is never replaced, the
     * effect is applied only after full revalidation, and cancellation applies nothing at all.
     */
    private static void advanceAction(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final HedgeCroneState.ActionState action = state.action();
        if (action.windupRemainingTicks() > 0) {
            if (action.action() == Action.WARD_PREPARATION) {
                holdPreparation(crone, level);
            } else {
                action.targetId()
                    .flatMap(id -> resolveLiving(level, id))
                    .ifPresent(target -> crone.getLookControl().setLookAt(target));
            }
            return;
        }
        if (action.action() == Action.WARD_PREPARATION) {
            completePreparation(crone, level);
            return;
        }
        completeHex(crone, level);
    }

    private static void completeHex(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final HedgeCroneState.ActionState action = state.action();
        final Optional<LivingEntity> resolved =
            action.targetId().flatMap(id -> resolveLiving(level, id));
        crone.croneCounters().lineOfSightChecks++;
        final boolean eligible = resolved
            .filter(_ -> action.dimension()
                .map(stored -> stored.equals(dimensionOf(level))).orElse(false))
            .filter(_ -> !crone.croneTransient().hazardActive)
            .map(target -> HedgeCroneRules.castEligible(
                crone.getSensing().hasLineOfSight(target),
                legalTarget(crone, target),
                crone.distanceToSqr(target)
            ))
            .orElse(false);
        HedgeCroneState updated = state.cancelAction().withCadence(new HedgeCroneState.Cadence(
            HedgeCroneRules.CAST_RECOVERY_TICKS,
            state.cadence().withdrawalTicks(),
            state.cadence().routeFailures(),
            state.cadence().routeRetryTicks(),
            state.cadence().anchorUnavailableTicks()
        ));
        if (!eligible) {
            crone.croneCounters().hexesCancelled++;
            crone.setCroneState(updated);
            return;
        }
        final Hex hex = action.hex().orElseThrow();
        final boolean accepted = resolved.orElseThrow().addEffect(new MobEffectInstance(
            hexEffect(hex), HedgeCroneRules.hexDurationTicks(hex), HedgeCroneRules.hexAmplifier(hex)
        ), crone);
        if (accepted) {
            crone.croneCounters().hexesCast++;
            feedback(crone, level, SoundEvents.WITCH_THROW, ParticleTypes.WITCH,
                HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
        } else {
            crone.croneCounters().hexesCancelled++;
        }
        crone.setCroneState(updated);
    }

    /** The exact existing four-effect table. Internal action labels, not a historical claim. */
    static Holder<MobEffect> hexEffect(final Hex hex) {
        return switch (hex) {
            case VEIL -> MobEffects.BLINDNESS;
            case BINDING -> MobEffects.SLOWNESS;
            case ENFEEBLE -> MobEffects.WEAKNESS;
            case WITHER -> MobEffects.POISON;
        };
    }

    // ---------------------------------------------------------------- ward

    private static void pursueWard(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        if (!state.work().hasWorkstation()) {
            searchWorkstationWhenDue(crone, level);
            return;
        }
        final BlockPos workstation = state.work().workstation().orElseThrow();
        if (!revalidateWorkstation(crone, level, workstation)) {
            crone.setCroneState(state.cancelAction());
            return;
        }
        if (crone.distanceToSqr(Vec3.atCenterOf(workstation))
            > HedgeCroneRules.WORKSTATION_ARRIVAL_DISTANCE_SQUARED) {
            requestRoute(crone, level, workstation, ROUTE_SPEED);
            return;
        }
        crone.getNavigation().stop();
        crone.setCroneState(state
            .withAction(HedgeCroneState.ActionState.preparation(dimensionOf(level)))
            .withMode(Mode.PREPARING));
        feedback(crone, level, SoundEvents.ENCHANTMENT_TABLE_USE, ParticleTypes.ENCHANT, 4);
    }

    private static void holdPreparation(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final Optional<BlockPos> workstation = state.work().workstation();
        if (workstation.isEmpty()
            || !revalidateWorkstation(crone, level, workstation.orElseThrow())
            || crone.croneTransient().hazardActive
            || state.threat().present()) {
            crone.setCroneState(state.cancelAction());
        }
    }

    /**
     * One boolean, no item consumed or created, no inventory or container opened, no block state
     * changed, no recipe/ritual/progression evaluated, and no workstation reserved against anyone.
     */
    private static void completePreparation(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        final Optional<BlockPos> workstation = state.work().workstation();
        final boolean valid = workstation
            .map(position -> revalidateWorkstation(crone, level, position))
            .orElse(false);
        HedgeCroneState updated = state.cancelAction();
        if (valid) {
            crone.croneCounters().wardsPrepared++;
            updated = updated.withWork(new HedgeCroneState.Work(
                true, Optional.empty(), Optional.empty(),
                HedgeCroneRules.WARD_COOLDOWN_TICKS, HedgeCroneRules.WORKSTATION_INTERVAL_TICKS
            ));
            feedback(crone, level, SoundEvents.ENCHANTMENT_TABLE_USE, ParticleTypes.ENCHANT,
                HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
        }
        crone.setCroneState(updated);
    }

    private static void searchWorkstationWhenDue(final HedgeCroneEntity crone, final ServerLevel level) {
        HedgeCroneState state = crone.croneState();
        if (state.work().workstationSearchTicks() > 0) {
            return;
        }
        state = state.withWork(new HedgeCroneState.Work(
            state.work().wardPrepared(), Optional.empty(), Optional.empty(),
            state.work().wardCooldownTicks(), HedgeCroneRules.WORKSTATION_INTERVAL_TICKS
        ));
        final BlockPos center = state.anchor().position().orElse(crone.blockPosition());
        Optional<BlockPos> found = Optional.empty();
        int reads = 0;
        int visits = 0;
        for (final HedgeCroneRules.SearchOffset offset : HedgeCroneRules.workstationOffsets(
            crone.getUUID(), HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
            HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS, HedgeCroneRules.MAX_WORKSTATION_CANDIDATES
        )) {
            if (visits >= HedgeCroneRules.MAX_WORKSTATION_CANDIDATES
                || reads >= HedgeCroneRules.MAX_WORKSTATION_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (!level.hasChunkAt(candidate)) {
                continue;
            }
            visits++;
            reads++;
            crone.croneCounters().workstationVisits++;
            crone.croneCounters().blockReads++;
            if (AmbientActivityTags.matches(ActivityType.ARCANE_STUDY, level.getBlockState(candidate))) {
                found = Optional.of(candidate.immutable());
                break;
            }
        }
        final HedgeCroneState searched = state;
        crone.setCroneState(found
            .map(position -> searched.withWork(new HedgeCroneState.Work(
                searched.work().wardPrepared(), Optional.of(position), Optional.of(dimensionOf(level)),
                searched.work().wardCooldownTicks(), HedgeCroneRules.WORKSTATION_INTERVAL_TICKS
            )))
            .orElse(searched));
    }

    private static boolean revalidateWorkstation(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final BlockPos workstation
    ) {
        if (!level.hasChunkAt(workstation) || !level.getWorldBorder().isWithinBounds(workstation)) {
            return false;
        }
        crone.croneCounters().blockReads++;
        return AmbientActivityTags.matches(ActivityType.ARCANE_STUDY, level.getBlockState(workstation));
    }

    /**
     * Accepted damage entry point. It records the direct threat, remembers whether the hit arrived
     * as a projectile for contextual hex selection, and discharges a prepared ward exactly once
     * behind a recursion guard that makes ward-on-ward impossible.
     */
    public static void onAcceptedDamage(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final DamageSource source,
        final float acceptedAmount
    ) {
        final LivingEntity attacker =
            source.getEntity() instanceof LivingEntity living ? living : null;
        final boolean legal = attacker != null && legalDirectAttacker(crone, attacker);
        if (legal) {
            crone.croneTransient().projectileAttacker = source.getDirectEntity() instanceof Projectile;
            crone.setCroneState(crone.croneState()
                .withThreat(HedgeCroneState.Threat.direct(attacker.getUUID(), dimensionOf(level))));
        }
        final HedgeCroneState state = crone.croneState();
        if (!HedgeCroneRules.wardDischarges(
            state.work().wardPrepared(), legal, acceptedAmount, APPLYING_WARD.get()
        )) {
            return;
        }
        crone.setCroneState(state.withWork(new HedgeCroneState.Work(
            false, state.work().workstation(), state.work().dimension(),
            state.work().wardCooldownTicks(), state.work().workstationSearchTicks()
        )));
        APPLYING_WARD.set(true);
        try {
            crone.croneCounters().wardsDischarged++;
            attacker.hurtServer(level, crone.damageSources().thorns(crone),
                HedgeCroneRules.wardDamage(acceptedAmount));
        } finally {
            APPLYING_WARD.set(false);
        }
        feedback(crone, level, SoundEvents.THORNS_HIT, ParticleTypes.CRIT,
            HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
    }

    // ---------------------------------------------------------------- withdrawal and patrol

    private static void withdraw(final HedgeCroneEntity crone, final ServerLevel level) {
        HedgeCroneState state = crone.croneState();
        if (state.cadence().withdrawalTicks() <= 0) {
            crone.croneCounters().withdrawals++;
            state = state.cancelAction().withCadence(new HedgeCroneState.Cadence(
                state.cadence().castRecoveryTicks(), HedgeCroneRules.WITHDRAW_TICKS,
                state.cadence().routeFailures(), state.cadence().routeRetryTicks(),
                state.cadence().anchorUnavailableTicks()
            ));
        }
        crone.setCroneState(state.withMode(Mode.WITHDRAWING));
        final Optional<Vec3> away = state.threat().id()
            .flatMap(id -> resolveLiving(level, id))
            .map(LivingEntity::position);
        // No heal, shield, invulnerability, despawn, or teleport is granted by withdrawal.
        findSafeDestination(crone, level, crone.blockPosition(), 6, 2,
            HedgeCroneRules.MAX_SAFE_CANDIDATES, away, true
        ).ifPresent(destination -> requestRoute(crone, level, destination, ESCAPE_SPEED));
    }

    private static void patrolAnchor(final HedgeCroneEntity crone, final ServerLevel level) {
        HedgeCroneState state = crone.croneState();
        if (!state.anchor().present()) {
            if (HedgeCroneRules.mayAdoptReplacementAnchor(
                state.cadence().anchorUnavailableTicks(), !crone.croneTransient().hazardActive
            ) && HedgeCroneRules.mayAdoptAfterDimensionChange(crone.croneTransient().safeLoadedTicks)) {
                crone.setCroneState(state.withAnchor(new HedgeCroneState.Anchor(
                    Optional.of(crone.blockPosition()), Optional.of(dimensionOf(level))
                )).withMode(Mode.IDLE));
            } else {
                crone.setCroneState(state.withMode(Mode.IDLE));
            }
            return;
        }
        final BlockPos anchor = state.anchor().position().orElseThrow();
        final double distanceSquared = crone.distanceToSqr(Vec3.atCenterOf(anchor));
        if (!HedgeCroneRules.anchorReturnRequired(!crone.croneTransient().hazardActive, distanceSquared)) {
            crone.setCroneState(state.withMode(Mode.IDLE));
            return;
        }
        if (!level.hasChunkAt(anchor)) {
            // A missing or unloaded anchor is retained as history but never forces a chunk load.
            crone.setCroneState(state.withMode(Mode.IDLE));
            return;
        }
        crone.setCroneState(state.withMode(Mode.RETURNING));
        requestRoute(crone, level, anchor, ROUTE_SPEED);
    }

    // ---------------------------------------------------------------- hazard

    private static boolean observeHazardWhenDue(final HedgeCroneEntity crone, final ServerLevel level) {
        final TransientState scratch = crone.croneTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive;
        }
        scratch.hazardCooldownTicks = HedgeCroneRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = observeHazard(crone, level);
        return scratch.hazardActive;
    }

    private static void escapeHazard(final HedgeCroneEntity crone, final ServerLevel level) {
        final HedgeCroneState state = crone.croneState();
        if (state.action().pending()) {
            crone.croneCounters().hexesCancelled++;
        }
        crone.croneCounters().hazardInterruptions++;
        crone.setCroneState(state.cancelAction());
        findSafeDestination(crone, level, crone.blockPosition(), 6, 2,
            HedgeCroneRules.MAX_SAFE_CANDIDATES, Optional.empty(), true
        ).ifPresent(destination -> requestRoute(crone, level, destination, ESCAPE_SPEED));
    }

    private static boolean observeHazard(final HedgeCroneEntity crone, final ServerLevel level) {
        if (crone.isOnFire() || crone.isInLava()) {
            return true;
        }
        if (crone.isUnderWater() && crone.getAirSupply() < crone.getMaxAirSupply()) {
            return true;
        }
        if (!footprintLoaded(level, crone.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos center = crone.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= HedgeCroneRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    crone.croneCounters().blockReads++;
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

    /**
     * The single legality gate used by {@code canAttack}, the boundary scan, the cast
     * revalidation, and the ward discharge, so no path can bypass a protected relation.
     */
    public static boolean legalTarget(final HedgeCroneEntity crone, final LivingEntity candidate) {
        return HedgeCroneRules.relationLegal(observe(crone, candidate, false));
    }

    private static boolean legalDirectAttacker(
        final HedgeCroneEntity crone,
        final LivingEntity attacker
    ) {
        return HedgeCroneRules.relationLegal(observe(crone, attacker, true));
    }

    private static RelationFacts observe(
        final HedgeCroneEntity crone,
        final LivingEntity candidate,
        final boolean acceptedDirectAttacker
    ) {
        final CreatureKind kind =
            candidate instanceof ArcaneCreature creature ? creature.creatureKind() : null;
        final boolean player = candidate instanceof Player;
        final boolean ineligible = candidate instanceof Player subject
            && (subject.isCreative() || subject.isSpectator());
        return new RelationFacts(
            true,
            candidate.isAlive() && !candidate.isRemoved(),
            candidate.level() == crone.level(),
            candidate == crone,
            candidate.isInvulnerable() || !candidate.canBeSeenAsEnemy(),
            player && !ineligible,
            ineligible,
            kind == CreatureKind.HEDGE_CRONE,
            candidate instanceof Animal
                || candidate instanceof AbstractVillager
                || candidate instanceof AbstractGolem,
            candidate instanceof Mob other && CreatureBehaviorState.owner(other).isPresent(),
            kind == CreatureKind.CIRCLE_MAGE,
            acceptedDirectAttacker
        );
    }

    private static boolean holdsRangedWeapon(final LivingEntity target) {
        return target.getMainHandItem().is(RANGED_WEAPONS)
            || target.getOffhandItem().is(RANGED_WEAPONS);
    }

    // ---------------------------------------------------------------- movement lease

    /**
     * The only place a Hedge Crone path is ever created. Reach range zero, a strictly reachable
     * path, and an accepted {@code moveTo} are all required; the third consecutive failure clears
     * the destination and starts the hundred-tick backoff.
     */
    static boolean requestRoute(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = crone.croneTransient();
        HedgeCroneState state = crone.croneState();
        if (!HedgeCroneRules.pathRequestAllowed(
            scratch.pathCooldownTicks, state.cadence().routeRetryTicks()
        )) {
            return false;
        }
        if (!level.hasChunkAt(destination)) {
            return false;
        }
        scratch.pathCooldownTicks = HedgeCroneRules.PATH_INTERVAL_TICKS;
        crone.croneCounters().navigationRequests++;
        final Path path = crone.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && crone.getNavigation().moveTo(path, speed);
        final int failures = HedgeCroneRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new HedgeCroneRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (HedgeCroneRules.routeExhausted(failures)) {
            retry = HedgeCroneRules.routeBackoffAfter(failures);
            crone.getNavigation().stop();
            scratch.destination = null;
            state = state.cancelAction();
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        crone.setCroneState(state.withCadence(new HedgeCroneState.Cadence(
            state.cadence().castRecoveryTicks(), state.cadence().withdrawalTicks(),
            HedgeCroneRules.routeExhausted(failures) ? 0 : failures,
            retry, state.cadence().anchorUnavailableTicks()
        )));
        return accepted;
    }

    /**
     * Deterministic bounded safe-destination search sharing the twenty-four candidate and
     * 256-charged-read budget. The complete Crone AABB footprint must already be loaded and inside
     * the world border, {@code noCollision} must accept the box, and the origin is always excluded.
     */
    static Optional<BlockPos> findSafeDestination(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = crone.croneCounters();
        counters.safeSearches++;
        final BlockPos origin = crone.blockPosition();
        final java.util.Comparator<HedgeCroneRules.SafeCandidate> preference =
            HedgeCroneRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        HedgeCroneRules.SafeCandidate bestFacts = null;
        for (final HedgeCroneRules.SearchOffset offset : HedgeCroneRules.safeSearchOffsets(
            crone.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + 2 > HedgeCroneRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            final AABB box = crone.getType().getDimensions()
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
            if (!level.noCollision(crone, box)) {
                continue;
            }
            final HedgeCroneRules.SafeCandidate facts = new HedgeCroneRules.SafeCandidate(
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

    // ---------------------------------------------------------------- helpers

    private static void feedback(
        final HedgeCroneEntity crone,
        final ServerLevel level,
        final net.minecraft.sounds.SoundEvent sound,
        final net.minecraft.core.particles.ParticleOptions particle,
        final int particles
    ) {
        if (sound != null) {
            level.playSound(null, crone.getX(), crone.getY(), crone.getZ(), sound,
                SoundSource.HOSTILE, 0.7F, 1.0F);
        }
        level.sendParticles(particle, crone.getX(), crone.getY() + 1.2D, crone.getZ(),
            Math.min(HedgeCroneRules.MAX_FEEDBACK_PARTICLES, particles), 0.3D, 0.4D, 0.3D, 0.02D);
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
