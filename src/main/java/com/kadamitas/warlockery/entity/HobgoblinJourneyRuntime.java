package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractEnd;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.DefensiveResponse;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Period;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RelationFact;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RouteFailure;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.TargetClass;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.WorkAvailability;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.world.HobgoblinJourneyData;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side F11 Hobgoblin behavior controller and the sole owner of ordinary Hobgoblin
 * navigation and of {@code Mob.target}. The body's four executors declare LOOK only, so nothing here
 * ever contends for MOVE, and the traveler has no proactive target selector at all.
 *
 * <p>Every scan, charged block read, entity visit, navigation request, claim, transaction, and
 * feedback pulse is counted against the declared hard budgets. No method here enumerates a
 * dimension's entity list, forces or creates a chunk ticket, calls a chunk-loading accessor, holds a
 * mutable block snapshot across ticks, retains a live entity, or writes another family's data.</p>
 *
 * <p>Village exclusion is a policy surface, not a late flee goal: spawn, route waypoint, camp anchor,
 * work target, and trade continuation all consult the same predicate, and a traveler already inside
 * village space chooses {@code VILLAGE_EXIT} before any other non-emergency intent.</p>
 */
public final class HobgoblinJourneyRuntime {
    private static final double WORK_SPEED = 0.85D;
    private static final double TRAVEL_SPEED = 1.0D;
    private static final double URGENT_SPEED = 1.25D;
    private static final double REACH_SQUARED = 9.0D;
    private static final double PICKUP_SQUARED = 4.0D;
    /** The bounded outward exit ring, sampled centre-out and never randomised per tick. */
    private static final List<BlockPos> EXIT_OFFSETS = List.of(
        new BlockPos(16, 0, 0), new BlockPos(-16, 0, 0),
        new BlockPos(0, 0, 16), new BlockPos(0, 0, -16),
        new BlockPos(12, 0, 12), new BlockPos(-12, 0, 12),
        new BlockPos(12, 0, -12), new BlockPos(-12, 0, -12),
        new BlockPos(22, 0, 0), new BlockPos(-22, 0, 0),
        new BlockPos(0, 0, 22), new BlockPos(0, 0, -22),
        new BlockPos(16, 0, 16), new BlockPos(-16, 0, 16),
        new BlockPos(16, 0, -16), new BlockPos(-16, 0, -16)
    );
    /** The bounded travel-leg ring. Every leg stays inside the declared local route length. */
    private static final List<BlockPos> TRAVEL_OFFSETS = List.of(
        new BlockPos(24, 0, 0), new BlockPos(-24, 0, 0),
        new BlockPos(0, 0, 24), new BlockPos(0, 0, -24),
        new BlockPos(18, 0, 18), new BlockPos(-18, 0, -18)
    );
    /** The recognizable 5x4x5 windbreak envelope; every offset stays inside the declared cap. */
    private static final List<BlockPos> CAMP_OFFSETS = campEnvelope();

    private HobgoblinJourneyRuntime() {
    }

    // ================================================================ counters

    /** Structural work counters proving the exact caps. Pass-local and never persisted. */
    public static final class Counters {
        long entityVisits;
        long entitiesRetained;
        long memberVisits;
        long looseVisits;
        long chargedBlockReads;
        long decisions;
        long navigationRequests;
        long navigationFailures;
        long claimsGranted;
        long claimsRejected;
        long transactionsCommitted;
        long transactionsRolledBack;
        long campEditsCommitted;
        long campEditsRemoved;
        long villageExitSearches;
        long relationFacts;
        long contractsAccepted;
        long contractsEnded;
        long feedbackPulses;

        public long entityVisits() { return entityVisits; }
        public long entitiesRetained() { return entitiesRetained; }
        public long memberVisits() { return memberVisits; }
        public long looseVisits() { return looseVisits; }
        public long chargedBlockReads() { return chargedBlockReads; }
        public long decisions() { return decisions; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationFailures() { return navigationFailures; }
        public long claimsGranted() { return claimsGranted; }
        public long claimsRejected() { return claimsRejected; }
        public long transactionsCommitted() { return transactionsCommitted; }
        public long transactionsRolledBack() { return transactionsRolledBack; }
        public long campEditsCommitted() { return campEditsCommitted; }
        public long campEditsRemoved() { return campEditsRemoved; }
        public long villageExitSearches() { return villageExitSearches; }
        public long relationFacts() { return relationFacts; }
        public long contractsAccepted() { return contractsAccepted; }
        public long contractsEnded() { return contractsEnded; }
        public long feedbackPulses() { return feedbackPulses; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by at most one cadence but can never replay a birth, block edit, trade, attack, or transfer.
     */
    public static final class TransientState {
        boolean reconciled;
        int decisionCooldownTicks;
        int perceptionCooldownTicks;
        int groupCooldownTicks;
        int workScanCooldownTicks;
        int villageCooldownTicks;
        int campProposalCooldownTicks;
        int navigationCooldownTicks;
        int feedbackCooldownTicks;
        int miningCooldownTicks;
        int relationCooldownTicks;
        int ambientCooldownTicks;
        boolean hazardActive;
        boolean insideExcludedSpace;
        boolean eventResident;
        /**
         * The overworld day this body last observed. Seeded from the live clock on load rather than
         * zeroed, so relogging can never hand a merchant a fresh restock quota.
         */
        long observedDay = UNSEEDED_DAY;
        WorkAvailability work = WorkAvailability.none();
        final Plan plan = new Plan();
        final int[] scanCursors = unseededCursors();

        public void resetForLoad() {
            reconciled = false;
            decisionCooldownTicks = 0;
            perceptionCooldownTicks = 0;
            groupCooldownTicks = 0;
            workScanCooldownTicks = 0;
            villageCooldownTicks = 0;
            campProposalCooldownTicks = 0;
            navigationCooldownTicks = 0;
            feedbackCooldownTicks = 0;
            miningCooldownTicks = 0;
            relationCooldownTicks = 0;
            ambientCooldownTicks = 0;
            hazardActive = false;
            insideExcludedSpace = false;
            eventResident = false;
            observedDay = UNSEEDED_DAY;
            work = WorkAvailability.none();
            plan.clear();
            // Not zero: a traveler that unloads more often than one full rotation would restart the
            // far tail at index 0 every time and never reach the far envelope at all. Unseeded
            // cursors are seeded from the stable identity offset on first use instead.
            java.util.Arrays.fill(scanCursors, UNSEEDED_CURSOR);
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        public boolean insideExcludedSpace() {
            return insideExcludedSpace;
        }

        public boolean eventResident() {
            return eventResident;
        }

        public Plan plan() {
            return plan;
        }

        public WorkAvailability work() {
            return work;
        }
    }

    /**
     * The single surveyed plan. Scanning happens once per survey cadence and the concrete chosen
     * position is carried forward here; an executor never rescans, it only revalidates the exact
     * position it was handed. This is what makes the camp and the work modes reachable at all: the
     * survey and the executor can no longer race each other for the same cooldown.
     */
    public static final class Plan {
        Optional<BlockPos> mine = Optional.empty();
        Optional<BlockPos> deposit = Optional.empty();
        Optional<UUID> looseItem = Optional.empty();
        Optional<BlockPos> campAnchor = Optional.empty();
        Optional<BlockPos> exit = Optional.empty();
        Optional<BlockPos> flower = Optional.empty();
        Optional<BlockPos> travelLeg = Optional.empty();

        void clear() {
            mine = Optional.empty();
            deposit = Optional.empty();
            looseItem = Optional.empty();
            campAnchor = Optional.empty();
            exit = Optional.empty();
            flower = Optional.empty();
            travelLeg = Optional.empty();
        }

        public Optional<BlockPos> mine() {
            return mine;
        }

        public Optional<BlockPos> deposit() {
            return deposit;
        }

        public Optional<BlockPos> campAnchor() {
            return campAnchor;
        }

        public Optional<BlockPos> exit() {
            return exit;
        }

        public Optional<BlockPos> flower() {
            return flower;
        }
    }

    private static final int UNSEEDED_CURSOR = -1;
    private static final long UNSEEDED_DAY = Long.MIN_VALUE;
    private static final long TICKS_PER_DAY = 24_000L;

    private static int[] unseededCursors() {
        final int[] cursors = new int[ScanClass.values().length];
        java.util.Arrays.fill(cursors, UNSEEDED_CURSOR);
        return cursors;
    }

    /** One rotating scan cursor per job class, so no class can starve another out of its budget. */
    private enum ScanClass {
        MINING, DEPOSIT, CAMP, FLOWER
    }

    // ================================================================ entry point

    /** The one live entry point, called from {@code HobgoblinEntity.customServerAiStep}. */
    public static void tick(final HobgoblinEntity traveler, final ServerLevel level) {
        if (!HobgoblinJourneyRules.isExactHobgoblin(traveler.creatureKind())
            || traveler.isNoAi() || !traveler.isAlive()) {
            return;
        }
        reconcileOnLoad(traveler, level);
        advanceLoadedTimers(traveler, level);
        observeHazard(traveler);
        decide(traveler, level);
        execute(traveler, level);
        emitFeedback(traveler, level);
    }

    // ================================================================ lifecycle

    private static void reconcileOnLoad(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        // Stagger every cadence by a stable identity offset so a reloaded batch never spikes.
        final int offset = HobgoblinJourneyRules.stableOffset(
            traveler.getUUID(), HobgoblinJourneyRules.MAX_SCHEDULE_OFFSET_TICKS + 1
        );
        scratch.decisionCooldownTicks = offset % HobgoblinJourneyRules.DECISION_INTERVAL_TICKS;
        scratch.perceptionCooldownTicks = offset % HobgoblinJourneyRules.PERCEPTION_INTERVAL_TICKS;
        scratch.groupCooldownTicks = offset % HobgoblinJourneyRules.GROUP_INTERVAL_TICKS;
        scratch.workScanCooldownTicks = offset % HobgoblinJourneyRules.WORK_SCAN_INTERVAL_TICKS;
        scratch.villageCooldownTicks = offset % HobgoblinJourneyRules.VILLAGE_INTERVAL_TICKS;
        scratch.observedDay = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);

        // The persisted claim is released from the real record before any invalidation can run.
        // Doing it later would strand the lease in a record this traveler never visits again.
        HobgoblinJourneyData.get(level).releaseClaimsOf(traveler.getUUID());

        HobgoblinJourneyState state = traveler.journeyState().releaseJob();
        final String dimension = dimensionOf(level);
        final boolean waypointInvalid = state.caravan().waypoint()
            .map(position -> !level.getWorldBorder().isWithinBounds(position))
            .orElse(false)
            || state.caravan().dimension().map(stored -> !stored.equals(dimension)).orElse(false);
        if (waypointInvalid) {
            state = state.withCaravan(state.caravan().clearWaypoint());
        }
        if (state.contract().target()
            .map(position -> !level.getWorldBorder().isWithinBounds(position)).orElse(false)) {
            state = state.withContract(new HobgoblinJourneyState.Contract(
                state.contract().contractor(), state.contract().kind(), Optional.empty(),
                state.contract().remainingTicks(), state.contract().completedUnits(),
                state.contract().end()
            ));
        }
        traveler.setJourneyState(state);
    }

    /**
     * Advances every remaining-tick counter by exactly one loaded tick, then runs the transitions
     * that those counters reaching zero imply. The state records deliberately do not end their own
     * phases: this is the single exit, and it is also the branch that arms the cooldown, releases
     * the real claim, clears the anchor, and emits the completion feedback.
     */
    private static void advanceLoadedTimers(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        scratch.decisionCooldownTicks = Math.max(0, scratch.decisionCooldownTicks - 1);
        scratch.perceptionCooldownTicks = Math.max(0, scratch.perceptionCooldownTicks - 1);
        scratch.groupCooldownTicks = Math.max(0, scratch.groupCooldownTicks - 1);
        scratch.workScanCooldownTicks = Math.max(0, scratch.workScanCooldownTicks - 1);
        scratch.villageCooldownTicks = Math.max(0, scratch.villageCooldownTicks - 1);
        scratch.campProposalCooldownTicks = Math.max(0, scratch.campProposalCooldownTicks - 1);
        scratch.navigationCooldownTicks = Math.max(0, scratch.navigationCooldownTicks - 1);
        scratch.feedbackCooldownTicks = Math.max(0, scratch.feedbackCooldownTicks - 1);
        scratch.miningCooldownTicks = Math.max(0, scratch.miningCooldownTicks - 1);
        scratch.relationCooldownTicks = Math.max(0, scratch.relationCooldownTicks - 1);
        scratch.ambientCooldownTicks = Math.max(0, scratch.ambientCooldownTicks - 1);

        final HobgoblinJourneyState before = traveler.journeyState();
        final HobgoblinJourneyState.Contract contract = before.contract();
        final HobgoblinJourneyState.Caravan caravan = before.caravan();
        final HobgoblinJourneyState.Job job = before.job();
        final HobgoblinJourneyState.Combat combat = before.combat();
        final HobgoblinJourneyState.Cadence cadence = before.cadence();

        HobgoblinJourneyState updated = before
            .withContract(new HobgoblinJourneyState.Contract(
                contract.contractor(), contract.kind(), contract.target(),
                Math.max(0, contract.remainingTicks() - 1), contract.completedUnits(), contract.end()
            ))
            .withCaravan(new HobgoblinJourneyState.Caravan(
                caravan.key(), caravan.leader(), caravan.waypoint(), caravan.dimension(),
                Math.max(0, caravan.routeRemainingTicks() - 1),
                Math.max(0, caravan.regroupRemainingTicks() - 1)
            ))
            .withJob(new HobgoblinJourneyState.Job(
                job.claimId(), job.target(), job.dimension(),
                Math.max(0, job.commitRemainingTicks() - 1)
            ))
            .withCombat(new HobgoblinJourneyState.Combat(
                combat.aggressor(), Math.max(0, combat.aggressorRemainingTicks() - 1),
                combat.retreating()
            ))
            .withCadence(new HobgoblinJourneyState.Cadence(
                cadence.lastFailure(), cadence.routeFailures(),
                Math.max(0, cadence.retryRemainingTicks() - 1), cadence.stuck(), cadence.blockedExits()
            ))
            .withChildGiftCooldown(Math.max(0, before.childGiftCooldownTicks() - 1))
            .withBirthCooldown(Math.max(0, before.birthCooldownTicks() - 1));
        final HobgoblinJourneyState.Merchant merchant = updated.merchant();
        updated = updated.withMerchant(new HobgoblinJourneyState.Merchant(
            merchant.level(), merchant.xp(), merchant.restocksToday(),
            Math.max(0, merchant.restockSpacingTicks() - 1)
        ));
        // Live relationship facts age with the traveler and are dropped by this branch, never by a
        // record constructor, so the score is always a function of exactly the facts still alive.
        final List<HobgoblinJourneyState.Relation> aged = updated.relations().stream()
            .map(relation -> new HobgoblinJourneyState.Relation(
                relation.id(), relation.kind(), Math.max(0, relation.remainingTicks() - 1)
            ))
            .filter(HobgoblinJourneyState.Relation::live)
            .toList();
        updated = updated.withRelations(aged);
        traveler.setJourneyState(updated);
        HobgoblinJourneyData.get(level).advanceLoadedTick(level.getGameTime());

        // ---- transitions this branch, and only this branch, owns.
        if (job.commitRemainingTicks() > 0 && traveler.journeyState().job().leaseExpired()) {
            cancelJob(traveler, level, "lease expired");
        }
        if (combat.aggressorRemainingTicks() > 0 && traveler.journeyState().combat().aggressorLapsed()) {
            clearAggressor(traveler);
        }
        if (contract.remainingTicks() > 0 && traveler.journeyState().contract().expired()) {
            endContract(traveler, level, ContractEnd.EXPIRED);
        }
        if (caravan.regroupRemainingTicks() > 0
            && caravan.key().isPresent()
            && HobgoblinJourneyRules.regroupAbandoned(
                traveler.journeyState().caravan().regroupRemainingTicks())) {
            leaveCaravan(traveler, level);
        }
    }

    /** Clears the lapsed aggressor and, with it, the target and the retreat flag it justified. */
    private static void clearAggressor(final HobgoblinEntity traveler) {
        traveler.setJourneyState(traveler.journeyState().withCombat(
            HobgoblinJourneyState.Combat.none()
        ));
        traveler.setTarget(null);
    }

    /** Ends the agreement, releases the job it authorized, and records the completion fact. */
    private static void endContract(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final ContractEnd reason
    ) {
        final HobgoblinJourneyState state = traveler.journeyState();
        if (state.contract().contractor().isEmpty()) {
            return;
        }
        cancelJob(traveler, level, "contract ended");
        HobgoblinJourneyState updated = traveler.journeyState()
            .withContract(state.contract().ended(reason));
        if (reason == ContractEnd.COMPLETED) {
            updated = updated.withRelation(
                state.contract().contractor().orElseThrow(), RelationFact.WORK_COMPLETED
            );
            traveler.journeyCounters().relationFacts++;
        }
        traveler.setJourneyState(updated);
        traveler.journeyCounters().contractsEnded++;
    }

    /** Leaves the caravan safely. A stranded member is never teleported and never deleted. */
    private static void leaveCaravan(final HobgoblinEntity traveler, final ServerLevel level) {
        traveler.journeyState().caravan().key().ifPresent(key ->
            HobgoblinJourneyData.get(level).leaveCaravan(key, traveler.getUUID()));
        traveler.setJourneyState(traveler.journeyState()
            .withCaravan(HobgoblinJourneyState.Caravan.none()));
    }

    // ================================================================ hazard

    /**
     * Immediate entity-only hazard observation runs every tick; it reads the two blocks the body
     * already occupies and nothing else, so it costs no spatial query.
     */
    private static void observeHazard(final HobgoblinEntity traveler) {
        traveler.journeyTransient().hazardActive = traveler.isOnFire()
            || traveler.isInLava()
            || traveler.getAirSupply() <= 0
            || traveler.isFreezing();
    }

    // ================================================================ decision

    private static void decide(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.decisionCooldownTicks)) {
            return;
        }
        final HobgoblinJourneyState before = traveler.journeyState();
        scratch.decisionCooldownTicks = HobgoblinJourneyRules.decisionInterval(before.mode());
        traveler.journeyCounters().decisions++;

        observeVillage(traveler, level);
        reconcileCaravan(traveler, level);
        reconcileCamp(traveler, level);
        reconcileContract(traveler, level);
        reconcileMerchant(traveler, level);
        surveyWork(traveler, level);

        final HobgoblinJourneyState state = traveler.journeyState();
        final Period period = HobgoblinJourneyRules.period(
            level.getOverworldClockTime(),
            HobgoblinJourneyRules.stableOffset(
                traveler.getUUID(), HobgoblinJourneyRules.MAX_SCHEDULE_OFFSET_TICKS + 1
            )
        );
        final CampPhase campPhase = campPhaseOf(traveler, level);
        final Mode candidate = HobgoblinJourneyRules.selectMode(
            traveler.isBaby(),
            scratch.hazardActive,
            defensiveResponse(traveler, level),
            scratch.insideExcludedSpace,
            state.job().holdsClaim() && state.mode() == Mode.WORK_COMMIT,
            traveler.isTrading(),
            campPhase,
            campEventHeld(traveler, level),
            state.caravan().regroupRemainingTicks() > 0,
            period,
            state.contract().active(),
            scratch.work
        );
        final Mode current = state.mode();
        if (candidate == current) {
            return;
        }
        if (state.job().holdsClaim() && !HobgoblinJourneyRules.interrupts(current, candidate)) {
            return;
        }
        cancelJob(traveler, level, "reprioritized");
        commitMode(traveler, level, candidate);
    }

    /** Grants at most one work claim for the newly selected mode, then commits it. */
    private static void commitMode(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final Mode mode
    ) {
        Optional<UUID> claim = Optional.empty();
        Optional<BlockPos> target = Optional.empty();
        if (mode.requiresClaim()) {
            target = workTarget(traveler);
            claim = HobgoblinJourneyData.get(level).claim(mode.name(), traveler.getUUID(), target);
            if (claim.isPresent()) {
                traveler.journeyCounters().claimsGranted++;
            } else {
                traveler.journeyCounters().claimsRejected++;
                // A refused claim must still arm the cadence: without this the traveler would ask
                // for the same contended worksite on every single decision, forever.
                traveler.journeyTransient().workScanCooldownTicks =
                    HobgoblinJourneyRules.WORK_SCAN_INTERVAL_TICKS;
                traveler.setJourneyState(traveler.journeyState().withMode(Mode.TRAVEL));
                return;
            }
        }
        traveler.setJourneyState(traveler.journeyState()
            .withMode(mode)
            .withJob(new HobgoblinJourneyState.Job(
                claim, target, target.map(unused -> dimensionOf(level)),
                claim.isPresent() ? HobgoblinJourneyRules.leaseTicks() : 0
            )));
    }

    /** Releases the claim, the target, and the mode transactionally. */
    private static void cancelJob(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final String reason
    ) {
        final HobgoblinJourneyState state = traveler.journeyState();
        if (state.mode() == Mode.IDLE && state.job().claimId().isEmpty()) {
            return;
        }
        state.job().claimId().ifPresent(claim -> HobgoblinJourneyData.get(level).releaseClaim(claim));
        traveler.setJourneyState(state.releaseJob());
        traveler.getNavigation().stop();
    }

    private static Optional<BlockPos> workTarget(final HobgoblinEntity traveler) {
        final Plan plan = traveler.journeyTransient().plan;
        return plan.mine.or(() -> plan.deposit).or(() -> plan.campAnchor);
    }

    // ================================================================ observation

    /**
     * The one village-boundary observation. It is charged, bounded, and never forces a chunk: an
     * unloaded probe simply declines the candidate.
     */
    private static void observeVillage(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.villageCooldownTicks)) {
            return;
        }
        scratch.villageCooldownTicks = HobgoblinJourneyRules.VILLAGE_INTERVAL_TICKS;
        final BlockPos origin = traveler.blockPosition();
        scratch.insideExcludedSpace = HobgoblinJourneyRules.villageExcluded(
            level.isVillage(origin),
            humanVillagerNearby(traveler, level),
            false
        );
        if (!scratch.insideExcludedSpace) {
            scratch.plan.exit = Optional.empty();
            traveler.setJourneyState(traveler.journeyState()
                .withCadence(traveler.journeyState().cadence().withBlockedExits(0)));
            return;
        }
        traveler.journeyCounters().villageExitSearches++;
        scratch.plan.exit = findExit(traveler, level, origin);
        if (scratch.plan.exit.isEmpty()) {
            // A search that qualifies nothing still arms its cadence and records the failure, so a
            // walled-in traveler waits instead of re-scanning the same blocked ring every tick.
            recordExitFailure(traveler);
        }
    }

    /**
     * Samples at most sixteen loaded outward candidates between twelve and twenty-four blocks and
     * retains at most four. Every probe is charged before any filter can reject it.
     */
    private static Optional<BlockPos> findExit(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos origin
    ) {
        final List<BlockPos> retained = new ArrayList<>();
        int reads = 0;
        for (final BlockPos offset : EXIT_OFFSETS) {
            if (retained.size() >= HobgoblinJourneyRules.MAX_RETAINED_EXITS
                || reads >= HobgoblinJourneyRules.MAX_VILLAGE_BLOCK_READS) {
                break;
            }
            final BlockPos flat = origin.offset(offset);
            // Every probe is charged first: four reads for the ground, the body space, the head
            // space, and the boundary question, whether or not the candidate survives.
            reads += 4;
            if (!level.isLoaded(flat) || !level.getWorldBorder().isWithinBounds(flat)) {
                continue;
            }
            final BlockPos candidate = groundedAt(level, flat);
            final double distance = Math.sqrt(candidate.distSqr(origin));
            final boolean excluded = HobgoblinJourneyRules.villageExcluded(
                level.isVillage(candidate), false, false
            );
            if (HobgoblinJourneyRules.exitCandidateAccepted(distance, excluded, standable(level, candidate))) {
                retained.add(candidate);
            }
        }
        traveler.journeyCounters().chargedBlockReads += reads;
        return retained.stream()
            .min(Comparator.comparingDouble(position -> traveler.distanceToSqr(Vec3.atCenterOf(position))));
    }

    private static void recordExitFailure(final HobgoblinEntity traveler) {
        final HobgoblinJourneyState.Cadence cadence = traveler.journeyState().cadence();
        final int blocked = Math.min(
            HobgoblinJourneyRules.MAX_BLOCKED_EXITS, cadence.blockedExits() + 1
        );
        final int retry = HobgoblinJourneyRules.exitBlocked(blocked)
            ? HobgoblinJourneyRules.BLOCKED_EXIT_BACKOFF_TICKS
            : cadence.retryRemainingTicks();
        traveler.setJourneyState(traveler.journeyState().withCadence(
            new HobgoblinJourneyState.Cadence(
                cadence.lastFailure(), cadence.routeFailures(), retry, cadence.stuck(), blocked
            )
        ));
    }

    private static boolean humanVillagerNearby(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final List<Villager> visited = BoundedEntityQuery.collect(
            level,
            Villager.class,
            traveler.getBoundingBox().inflate(
                HobgoblinJourneyRules.VILLAGER_SIGNAL_RADIUS, 6.0D,
                HobgoblinJourneyRules.VILLAGER_SIGNAL_RADIUS
            ),
            Villager::isAlive,
            HobgoblinJourneyRules.MAX_ENTITY_VISITS
        );
        traveler.journeyCounters().entityVisits += visited.size();
        return visited.stream()
            .anyMatch(villager -> GoblinHostilityRules.isHumanVillager(villager.getType()));
    }

    /**
     * One charged work survey per cadence. Every branch is bounded by its own read cap and a survey
     * never reads an unloaded position.
     */
    private static void surveyWork(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.workScanCooldownTicks)) {
            return;
        }
        scratch.workScanCooldownTicks = HobgoblinJourneyRules.WORK_SCAN_INTERVAL_TICKS;
        final Plan plan = scratch.plan;
        if (traveler.isBaby()) {
            plan.mine = Optional.empty();
            plan.deposit = Optional.empty();
            plan.looseItem = Optional.empty();
            plan.campAnchor = Optional.empty();
            plan.flower = traveler.getMainHandItem().isEmpty()
                ? nearestFlower(traveler, level) : Optional.empty();
            scratch.work = new WorkAvailability(false, false, false, false, false,
                plan.flower.isPresent(),
                HobgoblinJourneyRules.canDance(sameCaravanChildren(traveler, level)),
                HobgoblinJourneyRules.giftReady(
                    traveler.getMainHandItem().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.FLOWERS),
                    traveler.journeyState().childGiftCooldownTicks()
                ));
            return;
        }
        final boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        final boolean miner = traveler.goblinProfession() == GoblinProfession.MINER
            && traveler.getMainHandItem().is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)
            && traveler.journeyState().contract().active()
            && HobgoblinJourneyRules.isDue(scratch.miningCooldownTicks);
        plan.mine = griefing && miner ? nearestMineable(traveler, level) : Optional.empty();
        plan.deposit = carries(traveler, stack ->
            stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            ? nearestDeposit(traveler, level) : Optional.empty();
        final Optional<ItemEntity> loose = nearestLooseItem(traveler, level);
        plan.looseItem = loose.map(Entity::getUUID);
        // The camp survey owns its own 400-tick cadence and keeps the last valid anchor across the
        // work surveys in between, so a committed camp proposal always has an anchor to act on.
        if (HobgoblinJourneyRules.isDue(scratch.campProposalCooldownTicks)) {
            scratch.campProposalCooldownTicks = HobgoblinJourneyRules.CAMP_PROPOSAL_INTERVAL_TICKS;
            plan.campAnchor = griefing && !scratch.insideExcludedSpace
                ? findCampAnchor(traveler, level) : Optional.empty();
        }
        scratch.work = new WorkAvailability(
            plan.mine.isPresent(),
            plan.deposit.isPresent(),
            plan.looseItem.isPresent(),
            plan.campAnchor.isPresent(),
            campMaterialsCarried(traveler),
            false, false, false
        );
    }

    // ================================================================ reconciliation

    /**
     * The caravan heartbeat runs first and unconditionally for any traveler that already holds a
     * key. Putting it behind the neighbour count or the regroup guard would let a live loaded member
     * lose its seat, and then its claim, simply because its companions wandered out of range.
     */
    private static void reconcileCaravan(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.groupCooldownTicks)) {
            return;
        }
        scratch.groupCooldownTicks = HobgoblinJourneyRules.GROUP_INTERVAL_TICKS;
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        traveler.journeyState().caravan().key()
            .ifPresent(existing -> data.joinCaravan(existing, traveler.getUUID()));
        if (traveler.isBaby()) {
            return;
        }
        final List<HobgoblinEntity> neighbours = level.getEntitiesOfClass(
            HobgoblinEntity.class,
            traveler.getBoundingBox().inflate(
                HobgoblinJourneyRules.MEMBER_RADIUS, 12.0D, HobgoblinJourneyRules.MEMBER_RADIUS
            ),
            candidate -> candidate.isAlive()
        );
        traveler.journeyCounters().memberVisits +=
            Math.min(neighbours.size(), HobgoblinJourneyRules.MAX_MEMBER_VISITS);
        final long key = HobgoblinJourneyRules.caravanKey(
            traveler.blockPosition().getX(), traveler.blockPosition().getZ()
        );
        HobgoblinJourneyState state = traveler.journeyState();
        if (state.caravan().key().isEmpty() && neighbours.size() >= 2 && data.joinCaravan(key, traveler.getUUID())) {
            state = state.withCaravan(state.caravan().withKey(key));
            traveler.setJourneyState(state);
        }
        final Optional<Long> active = traveler.journeyState().caravan().key();
        if (active.isEmpty()) {
            return;
        }
        final List<UUID> adults = neighbours.stream()
            .filter(candidate -> !candidate.isBaby())
            .filter(candidate -> candidate.journeyState().caravan().key()
                .map(member -> member.equals(active.orElseThrow())).orElse(false))
            .limit(HobgoblinJourneyRules.MAX_MEMBER_RETAINED)
            .map(Entity::getUUID)
            .toList();
        final Optional<UUID> leader = data.electLeader(active.orElseThrow(), adults);
        state = traveler.journeyState().withCaravan(traveler.journeyState().caravan().withLeader(leader));
        // Leader publishes one shared semantic waypoint; every member paths to it on its own cadence.
        if (leader.map(traveler.getUUID()::equals).orElse(false)) {
            if (HobgoblinJourneyRules.isDue(state.caravan().routeRemainingTicks())) {
                final Optional<BlockPos> leg = findTravelLeg(traveler, level);
                leg.ifPresent(position -> data.setWaypoint(active.orElseThrow(), position));
            }
        }
        final Optional<BlockPos> shared = data.waypoint(active.orElseThrow());
        if (shared.isPresent()) {
            state = state.withCaravan(state.caravan().withWaypoint(
                shared.orElseThrow(), dimensionOf(level), HobgoblinJourneyRules.CAMP_PROPOSAL_INTERVAL_TICKS
            ));
        }
        final Optional<BlockPos> leaderPosition = leaderPosition(traveler, neighbours, leader);
        if (leaderPosition.isPresent()) {
            final double distance = Math.sqrt(traveler.distanceToSqr(Vec3.atCenterOf(leaderPosition.orElseThrow())));
            if (HobgoblinJourneyRules.shouldRegroup(distance)
                && HobgoblinJourneyRules.isDue(state.caravan().regroupRemainingTicks())) {
                state = state.withCaravan(state.caravan()
                    .withRegroup(HobgoblinJourneyRules.REGROUP_DEADLINE_TICKS));
            } else if (HobgoblinJourneyRules.regroupSatisfied(distance)) {
                state = state.withCaravan(state.caravan().withRegroup(0));
            }
        }
        traveler.setJourneyState(state);
    }

    private static Optional<BlockPos> leaderPosition(
        final HobgoblinEntity traveler,
        final List<HobgoblinEntity> neighbours,
        final Optional<UUID> leader
    ) {
        if (leader.isEmpty() || leader.map(traveler.getUUID()::equals).orElse(false)) {
            return Optional.empty();
        }
        return neighbours.stream()
            .filter(candidate -> candidate.getUUID().equals(leader.orElseThrow()))
            .findFirst()
            .map(Entity::blockPosition);
    }

    /**
     * Camp reconciliation. Phase advancement is a tick decision, never a record side effect, so the
     * branch that ends a phase is always the branch that also releases what that phase reserved.
     */
    private static void reconcileCamp(final HobgoblinEntity traveler, final ServerLevel level) {
        final HobgoblinJourneyState state = traveler.journeyState();
        final Optional<Long> campKey = state.camp().key();
        if (campKey.isEmpty()) {
            return;
        }
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        final HobgoblinJourneyData.CampRecord record = data.camp(campKey.orElseThrow());
        if (!record.present()) {
            traveler.journeyTransient().eventResident = false;
            traveler.setJourneyState(state.withCamp(HobgoblinJourneyState.Camp.none()));
            return;
        }
        // F03/F04 stay authoritative: F11 only reads their public raider marker and refuses to tear
        // its own camp down while a live matching event is standing on it.
        final boolean eventActive = record.anchor()
            .map(anchor -> assaultRaiderNear(level, anchor))
            .orElse(false);
        if (eventActive) {
            data.holdCampForEvent(campKey.orElseThrow());
        }
        traveler.journeyTransient().eventResident = eventActive || record.eventHeld();
        final boolean caravanPresent = state.caravan().key()
            .map(key -> data.population(key) > 0).orElse(false);
        final boolean expired = HobgoblinJourneyRules.campExpired(
            record.expiryRemainingTicks(), caravanPresent, eventActive, record.eventHoldRemainingTicks()
        );
        final CampPhase next = HobgoblinJourneyRules.nextCampPhase(
            record.phase(),
            !expired,
            record.journal().size() >= requiredCampEdits(),
            traveler.journeyTransient().hazardActive,
            expired,
            record.journal().isEmpty()
        );
        if (next != record.phase()) {
            data.setCampPhase(campKey.orElseThrow(), next);
        }
        if (next == CampPhase.RELEASE) {
            data.closeCamp(campKey.orElseThrow());
            traveler.setJourneyState(traveler.journeyState().withCamp(HobgoblinJourneyState.Camp.none()));
            return;
        }
        traveler.setJourneyState(traveler.journeyState()
            .withCamp(HobgoblinJourneyState.Camp.at(campKey.orElseThrow(), next)));
    }

    private static void reconcileContract(final HobgoblinEntity traveler, final ServerLevel level) {
        final HobgoblinJourneyState state = traveler.journeyState();
        if (state.contract().contractor().isEmpty()) {
            return;
        }
        final ContractEnd outcome = HobgoblinJourneyRules.contractOutcome(
            state.contract().contractor().map(level::getPlayerByUUID).filter(Player::isAlive).isPresent(),
            state.contract().remainingTicks(),
            state.contract().completedUnits(),
            false,
            state.combat().aggressor()
                .map(aggressor -> state.contract().contractor()
                    .map(aggressor::equals).orElse(false))
                .orElse(false)
        );
        if (outcome != ContractEnd.ACTIVE) {
            endContract(traveler, level, outcome);
        }
    }

    private static void reconcileMerchant(final HobgoblinEntity traveler, final ServerLevel level) {
        rollMerchantDay(traveler, level);
        final HobgoblinJourneyState state = traveler.journeyState();
        final boolean needsRestock = traveler.getOffers().stream().anyMatch(offer -> offer.needsRestock());
        if (!HobgoblinJourneyRules.canRestock(
            state.merchant().restocksToday(), state.merchant().restockSpacingTicks(),
            needsRestock, safeToTrade(traveler)
        )) {
            return;
        }
        traveler.getOffers().forEach(offer -> offer.resetUses());
        traveler.setJourneyState(state.withMerchant(state.merchant().afterRestock()));
    }

    /**
      * The daily restock quota reset that {@code Villager} used to perform for free.
      *
      * <p>The dedicated body is an {@code AbstractVillager}, and only {@code Villager} rolled its
      * restock counter over at the start of each day. Without this branch
      * {@link HobgoblinJourneyState.Merchant#onNewDay()} would have no production caller at all and
      * a traveler that had restocked twice could never restock again.</p>
      *
      * <p>The day index is seeded from the live clock on load, never zeroed, so a player cannot
      * relog to refresh the quota, and a traveler that missed several days while unloaded collapses
      * to exactly one deterministic reset rather than a burst.</p>
      */
    private static void rollMerchantDay(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        final long today = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
        if (scratch.observedDay == UNSEEDED_DAY) {
            scratch.observedDay = today;
            return;
        }
        if (scratch.observedDay == today) {
            return;
        }
        scratch.observedDay = today;
        traveler.setJourneyState(traveler.journeyState()
            .withMerchant(traveler.journeyState().merchant().onNewDay()));
    }

    // ================================================================ defence

    /**
     * The one defensive answer. Without a live remembered direct aggressor there is no answer at
     * all: a Hobgoblin never picks a fight, and escape always outranks the strike below the declared
     * health fraction.
     */
    private static DefensiveResponse defensiveResponse(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final HobgoblinJourneyState state = traveler.journeyState();
        if (!state.combat().remembersAggressor()) {
            return DefensiveResponse.NONE;
        }
        final Optional<LivingEntity> aggressor = resolveAggressor(traveler, level);
        final boolean reachable = aggressor
            .map(target -> !HobgoblinJourneyRules.shouldDisengage(
                Math.sqrt(traveler.distanceToSqr(target))))
            .orElse(false);
        return HobgoblinJourneyRules.defensiveResponse(
            !traveler.isBaby(),
            true,
            reachable,
            dependentNearby(traveler, level),
            traveler.getHealth() / Math.max(1.0F, traveler.getMaxHealth())
        );
    }

    private static Optional<LivingEntity> resolveAggressor(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        return traveler.journeyState().combat().aggressor().flatMap(id -> {
            final Entity found = level.getEntity(id);
            return found instanceof LivingEntity living && living.isAlive()
                ? Optional.of(living) : Optional.empty();
        });
    }

    /** A child or an already-retreating caravan member inside the rescue radius. */
    private static boolean dependentNearby(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final Optional<Long> key = traveler.journeyState().caravan().key();
        final List<HobgoblinEntity> visited = BoundedEntityQuery.collect(
            level,
            HobgoblinEntity.class,
            traveler.getBoundingBox().inflate(
                HobgoblinJourneyRules.DEFEND_RESCUE_RADIUS, 6.0D,
                HobgoblinJourneyRules.DEFEND_RESCUE_RADIUS
            ),
            candidate -> candidate != traveler && candidate.isAlive(),
            HobgoblinJourneyRules.MAX_ENTITY_VISITS
        );
        traveler.journeyCounters().entityVisits += visited.size();
        return visited.stream()
            .filter(candidate -> candidate.isBaby() || candidate.journeyState().combat().retreating())
            .anyMatch(candidate -> key.isEmpty()
                || candidate.journeyState().caravan().key()
                    .map(member -> member.equals(key.orElseThrow())).orElse(false));
    }

    // ================================================================ execution

    private static void execute(final HobgoblinEntity traveler, final ServerLevel level) {
        final HobgoblinJourneyState state = traveler.journeyState();
        // A claim-bearing mode may never execute without a live lease. Checking only when the job
        // still believes it holds one would let a lapsed traveler keep mutating a worksite.
        if (state.mode().requiresClaim() && !verifyClaim(traveler, level)) {
            cancelJob(traveler, level, "claim lost");
            return;
        }
        switch (state.mode()) {
            case IDLE -> traveler.getNavigation().stop();
            case TRADE_WAIT -> holdForTrade(traveler);
            case VILLAGE_EXIT -> executeVillageExit(traveler, level);
            case TRAVEL -> executeTravel(traveler, level);
            case REGROUP -> executeRegroup(traveler, level);
            case CAMP_PROPOSE -> executeCampPropose(traveler, level);
            case CAMP_BUILD -> executeCampBuild(traveler, level);
            case CAMP_REST -> executeCampRest(traveler, level);
            case CAMP_TEARDOWN -> executeCampTeardown(traveler, level);
            case WORK_APPROACH -> executeWorkApproach(traveler, level);
            case WORK_COMMIT -> executeWorkCommit(traveler, level);
            case DEFEND -> executeDefend(traveler, level);
            case FLEE -> executeFlee(traveler, level);
            case CHILD_PLAY -> executeChildPlay(traveler, level);
        }
    }

    private static boolean verifyClaim(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<UUID> claim = traveler.journeyState().job().claimId();
        return claim.isPresent() && HobgoblinJourneyData.get(level).holdsClaim(claim.orElseThrow());
    }

    private static void holdForTrade(final HobgoblinEntity traveler) {
        traveler.getNavigation().stop();
        traveler.setTarget(null);
    }

    /**
     * Trading never suspends the boundary rule: if the traveler is inside village space the customer
     * interaction is closed cleanly first, and only then does it route outward.
     */
    private static void executeVillageExit(final HobgoblinEntity traveler, final ServerLevel level) {
        if (traveler.isTrading()) {
            traveler.setTradingPlayer(null);
        }
        final HobgoblinJourneyState.Cadence cadence = traveler.journeyState().cadence();
        if (HobgoblinJourneyRules.exitBlocked(cadence.blockedExits())
            && !HobgoblinJourneyRules.isDue(cadence.retryRemainingTicks())) {
            traveler.getNavigation().stop();
            return;
        }
        final Optional<BlockPos> exit = traveler.journeyTransient().plan.exit;
        if (exit.isEmpty()) {
            recordExitFailure(traveler);
            return;
        }
        requestNavigation(traveler, level, exit.orElseThrow(), URGENT_SPEED);
    }

    private static void executeTravel(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<BlockPos> waypoint = traveler.journeyState().caravan().waypoint()
            .or(() -> traveler.journeyTransient().plan.travelLeg);
        if (waypoint.isEmpty()) {
            final Optional<BlockPos> leg = findTravelLeg(traveler, level);
            traveler.journeyTransient().plan.travelLeg = leg;
            leg.ifPresent(position -> requestNavigation(traveler, level, position, TRAVEL_SPEED));
            return;
        }
        final BlockPos destination = waypoint.orElseThrow();
        if (traveler.distanceToSqr(Vec3.atCenterOf(destination)) <= REACH_SQUARED) {
            traveler.journeyTransient().plan.travelLeg = Optional.empty();
            traveler.setJourneyState(traveler.journeyState()
                .withCaravan(traveler.journeyState().caravan().clearWaypoint()));
            return;
        }
        requestNavigation(traveler, level, destination, TRAVEL_SPEED);
    }

    private static void executeRegroup(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<Long> key = traveler.journeyState().caravan().key();
        if (key.isEmpty()) {
            traveler.setJourneyState(traveler.journeyState().withMode(Mode.TRAVEL));
            return;
        }
        final Optional<BlockPos> rally = HobgoblinJourneyData.get(level).waypoint(key.orElseThrow());
        rally.ifPresent(position -> requestNavigation(traveler, level, position, TRAVEL_SPEED));
    }

    /**
     * Proposal reserves the record and the exact materials before a single block is touched. A
     * refused proposal simply arms the cadence again; it never leaves a half-owned camp behind.
     */
    private static void executeCampPropose(final HobgoblinEntity traveler, final ServerLevel level) {
        final HobgoblinJourneyState state = traveler.journeyState();
        final Optional<Long> caravanKey = state.caravan().key();
        final Optional<BlockPos> anchor = traveler.journeyTransient().plan.campAnchor;
        if (caravanKey.isEmpty() || anchor.isEmpty()) {
            cancelJob(traveler, level, "camp plan invalid");
            return;
        }
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        final long key = caravanKey.orElseThrow();
        final BlockPos site = anchor.orElseThrow();
        if (traveler.distanceToSqr(Vec3.atCenterOf(site)) > REACH_SQUARED) {
            requestNavigation(traveler, level, site, WORK_SPEED);
            return;
        }
        final boolean eligible = HobgoblinJourneyRules.campEligible(
            data.population(key),
            !traveler.isBaby(),
            !traveler.journeyTransient().hazardActive,
            traveler.journeyTransient().insideExcludedSpace,
            campFootprintLoaded(level, site),
            campFootprintClear(traveler, level, site),
            level.getWorldBorder().isWithinBounds(site),
            campMaterialsCarried(traveler),
            data.caravanHasCamp(key),
            data.campCount()
        );
        if (!eligible) {
            cancelJob(traveler, level, "camp ineligible");
            return;
        }
        final long campKey = HobgoblinJourneyRules.campKey(key);
        if (!data.openCamp(campKey, key, site,
            HobgoblinJourneyRules.CAMP_DIRT_COST, HobgoblinJourneyRules.CAMP_LOG_COST)) {
            cancelJob(traveler, level, "camp record refused");
            return;
        }
        data.setCampPhase(campKey, CampPhase.COMMIT);
        traveler.setJourneyState(traveler.journeyState()
            .withCamp(HobgoblinJourneyState.Camp.at(campKey, CampPhase.COMMIT)));
    }

    /**
     * Places at most four journaled blocks per tick, recording {@code {position, placed}} before
     * every mutation and revalidating the exact position immediately before it. A camp only ever
     * claims genuinely empty space, so teardown is the exact reverse of the placement.
     */
    private static void executeCampBuild(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<Long> campKey = traveler.journeyState().camp().key();
        if (campKey.isEmpty()) {
            cancelJob(traveler, level, "no camp");
            return;
        }
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        final HobgoblinJourneyData.CampRecord record = data.camp(campKey.orElseThrow());
        final Optional<BlockPos> anchor = record.anchor();
        if (anchor.isEmpty()
            || !HobgoblinJourneyRules.campMayPlaceBlocks(level.getGameRules().get(GameRules.MOB_GRIEFING))) {
            // The data-only camp is the declared safe fallback: the record survives, the world does
            // not change, and the caravan still gets a rest point.
            data.setCampPhase(campKey.orElseThrow(), CampPhase.ACTIVE);
            return;
        }
        final BlockPos origin = anchor.orElseThrow();
        if (traveler.distanceToSqr(Vec3.atCenterOf(origin)) > REACH_SQUARED) {
            requestNavigation(traveler, level, origin, WORK_SPEED);
            return;
        }
        final List<BlockPos> journaled = record.journal().stream()
            .map(HobgoblinJourneyData.CampEdit::position)
            .toList();
        int placed = 0;
        for (final BlockPos offset : CAMP_OFFSETS) {
            if (placed >= HobgoblinJourneyRules.campEditsThisTick(
                HobgoblinJourneyRules.CAMP_MAX_EDITS - journaled.size())) {
                break;
            }
            final BlockPos position = origin.offset(offset);
            if (journaled.contains(position)) {
                continue;
            }
            traveler.journeyCounters().chargedBlockReads += 3;
            final Block material = campMaterial(offset);
            if (!HobgoblinJourneyRules.canPlaceCampBlock(
                level.isLoaded(position),
                level.getWorldBorder().isWithinBounds(position),
                true,
                level.getBlockState(position).isAir(),
                level.getBlockEntity(position) != null,
                !level.getFluidState(position).isEmpty()
            )) {
                continue;
            }
            // Journal first: a crash between the record and the placement leaves an owned entry
            // whose state simply will not match at teardown, never an unowned block.
            if (!data.recordCampEdit(campKey.orElseThrow(), position,
                blockId(material))) {
                break;
            }
            if (!level.setBlockAndUpdate(position, material.defaultBlockState())) {
                data.removeCampEdit(campKey.orElseThrow(), position);
                traveler.journeyCounters().transactionsRolledBack++;
                break;
            }
            traveler.journeyCounters().campEditsCommitted++;
            placed++;
        }
        if (placed > 0) {
            consume(traveler, stack -> stack.is(ItemTags.DIRT), placed);
            traveler.swing(InteractionHand.MAIN_HAND);
            traveler.journeyCounters().transactionsCommitted++;
        }
    }

    private static void executeCampRest(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<BlockPos> anchor = traveler.journeyState().camp().key()
            .flatMap(key -> HobgoblinJourneyData.get(level).camp(key).anchor());
        if (anchor.isEmpty()) {
            traveler.setJourneyState(traveler.journeyState().withMode(Mode.TRAVEL));
            return;
        }
        final BlockPos position = anchor.orElseThrow();
        if (traveler.distanceToSqr(Vec3.atCenterOf(position)) > REACH_SQUARED) {
            requestNavigation(traveler, level, position, WORK_SPEED);
            return;
        }
        traveler.getNavigation().stop();
        attemptConception(traveler, level);
    }

    /**
     * Family life happens at a safe camp, never at a human POI. This is the only production route
     * that can create a Hobgoblin from two parents, and it is deliberately the only caller of
     * {@code getBreedOffspring}: two willing same-kind adults, accepted food, group headroom, no
     * hazard, no event, and an expired birth cooldown.
     */
    private static void attemptConception(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.ambientCooldownTicks)) {
            return;
        }
        scratch.ambientCooldownTicks = HobgoblinJourneyRules.AMBIENT_INTERVAL_TICKS;
        final HobgoblinJourneyState state = traveler.journeyState();
        final Optional<Long> key = state.caravan().key();
        if (key.isEmpty() || traveler.isBaby()) {
            return;
        }
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        final Optional<HobgoblinEntity> partner = nearestPartner(traveler, level, key.orElseThrow());
        if (!HobgoblinJourneyRules.canConceive(
            data.population(key.orElseThrow()),
            partner.isPresent(),
            !scratch.hazardActive && !scratch.eventResident && !state.combat().remembersAggressor(),
            carries(traveler, stack -> stack.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.FOODS)),
            state.birthCooldownTicks()
        )) {
            return;
        }
        final HobgoblinEntity other = partner.orElseThrow();
        if (!(traveler.getBreedOffspring(level, other) instanceof HobgoblinEntity child)) {
            return;
        }
        child.setBaby(true);
        child.snapTo(traveler.getX(), traveler.getY(), traveler.getZ(), traveler.getYRot(), 0.0F);
        if (!level.addFreshEntity(child)) {
            return;
        }
        consume(traveler, stack -> stack.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.FOODS), 1);
        data.joinCaravan(key.orElseThrow(), child.getUUID());
        // One birth arms a long cooldown on both parents, so a camp cannot become a nursery.
        traveler.setJourneyState(traveler.journeyState()
            .withBirthCooldown(HobgoblinJourneyRules.BIRTH_COOLDOWN_TICKS));
        other.setJourneyState(other.journeyState()
            .withBirthCooldown(HobgoblinJourneyRules.BIRTH_COOLDOWN_TICKS));
        traveler.playWorkSound();
    }

    private static Optional<HobgoblinEntity> nearestPartner(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final long caravanKey
    ) {
        final List<HobgoblinEntity> visited = BoundedEntityQuery.collect(
            level,
            HobgoblinEntity.class,
            traveler.getBoundingBox().inflate(8.0D, 4.0D, 8.0D),
            candidate -> candidate != traveler && candidate.isAlive() && !candidate.isBaby(),
            HobgoblinJourneyRules.MAX_MEMBER_VISITS
        );
        traveler.journeyCounters().memberVisits += visited.size();
        return visited.stream()
            .limit(HobgoblinJourneyRules.MAX_MEMBER_RETAINED)
            .filter(candidate -> candidate.journeyState().caravan().key()
                .map(key -> key == caravanKey).orElse(false))
            .filter(candidate -> HobgoblinJourneyRules.isDue(
                candidate.journeyState().birthCooldownTicks()))
            .min(Comparator.comparing(Entity::getUUID, HobgoblinJourneyRules.unsignedUuidOrder()));
    }

    /**
     * Removes at most four journaled edits per tick, and only where the current state still exactly
     * equals the state this camp placed. A player-modified or already-broken position is released
     * from ownership without any drop and without overwriting the later edit.
     */
    private static void executeCampTeardown(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<Long> campKey = traveler.journeyState().camp().key();
        if (campKey.isEmpty()) {
            traveler.setJourneyState(traveler.journeyState().withMode(Mode.TRAVEL));
            return;
        }
        final HobgoblinJourneyData data = HobgoblinJourneyData.get(level);
        final long key = campKey.orElseThrow();
        final List<HobgoblinJourneyData.CampEdit> journal = data.campJournal(key);
        if (journal.isEmpty()) {
            data.setCampPhase(key, CampPhase.RELEASE);
            return;
        }
        final int budget = HobgoblinJourneyRules.campTeardownThisTick(journal.size());
        int removed = 0;
        for (final HobgoblinJourneyData.CampEdit edit : journal) {
            if (removed >= budget) {
                break;
            }
            final BlockPos position = edit.position();
            traveler.journeyCounters().chargedBlockReads += 2;
            if (!level.isLoaded(position)) {
                continue;
            }
            final BlockState current = level.getBlockState(position);
            if (blockId(current.getBlock()).equals(edit.placedBlockId())) {
                level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                traveler.journeyCounters().campEditsRemoved++;
            }
            // Matched or not, ownership of this position is released exactly once.
            data.removeCampEdit(key, position);
            removed++;
        }
        if (removed > 0) {
            traveler.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void executeWorkApproach(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<BlockPos> target = traveler.journeyState().job().target();
        final Optional<UUID> loose = traveler.journeyTransient().plan.looseItem;
        if (target.isPresent()) {
            final BlockPos position = target.orElseThrow();
            if (traveler.distanceToSqr(Vec3.atCenterOf(position)) <= REACH_SQUARED) {
                traveler.setJourneyState(traveler.journeyState().withMode(Mode.WORK_COMMIT));
                return;
            }
            requestNavigation(traveler, level, position, WORK_SPEED);
            return;
        }
        if (loose.isEmpty()) {
            cancelJob(traveler, level, "no work target");
            return;
        }
        final Optional<ItemEntity> item = resolveLooseItem(level, loose.orElseThrow());
        if (item.isEmpty()) {
            cancelJob(traveler, level, "loose item lost");
            return;
        }
        if (traveler.distanceToSqr(item.orElseThrow()) <= PICKUP_SQUARED) {
            traveler.setJourneyState(traveler.journeyState().withMode(Mode.WORK_COMMIT));
            return;
        }
        requestNavigation(traveler, level, item.orElseThrow().blockPosition(), WORK_SPEED);
    }

    /**
     * Exactly one atomic step per work pulse: one block broken, one stack deposited, or one item
     * picked up. Every guard is revalidated immediately before the mutation.
     */
    private static void executeWorkCommit(final HobgoblinEntity traveler, final ServerLevel level) {
        final Plan plan = traveler.journeyTransient().plan;
        final Optional<BlockPos> target = traveler.journeyState().job().target();
        if (target.isPresent() && plan.mine.filter(target.orElseThrow()::equals).isPresent()) {
            commitMining(traveler, level, target.orElseThrow());
            return;
        }
        if (target.isPresent() && plan.deposit.filter(target.orElseThrow()::equals).isPresent()) {
            commitDeposit(traveler, level, target.orElseThrow());
            return;
        }
        final Optional<ItemEntity> item = plan.looseItem.flatMap(id -> resolveLooseItem(level, id));
        if (item.isEmpty()) {
            cancelJob(traveler, level, "work target lost");
            return;
        }
        InventoryCarrier.pickUpItem(level, traveler, traveler, item.orElseThrow());
        traveler.journeyCounters().transactionsCommitted++;
        recordContractUnit(traveler, level);
        cancelJob(traveler, level, "gathered");
    }

    private static void commitMining(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos position
    ) {
        final ItemStack tool = traveler.getMainHandItem();
        if (!isMineable(traveler, level, position, tool)) {
            cancelJob(traveler, level, "mining target invalid");
            return;
        }
        final HobgoblinMiningRules.MiningProfile profile = HobgoblinMiningRules.profile(
            tool.is(WarlockeryTags.Items.ENHANCED_HOBGOBLIN_MINING_TOOLS)
        );
        final BlockState state = level.getBlockState(position);
        final List<ItemStack> drops = Block.getDrops(
            state, level, position, level.getBlockEntity(position), traveler, tool
        );
        if (!level.destroyBlock(position, false, traveler)) {
            traveler.journeyCounters().transactionsRolledBack++;
            cancelJob(traveler, level, "mining refused");
            return;
        }
        traveler.swing(InteractionHand.MAIN_HAND);
        state.spawnAfterBreak(level, position, tool, false);
        drops.forEach(stack -> Block.popResource(level, position, stack));
        if (HobgoblinMiningRules.findsGoblinite(profile, traveler.getRandom().nextFloat())) {
            Block.popResource(level, position, new ItemStack(
                com.kadamitas.warlockery.registry.ModItems.ALL.get("ingredient_delvealloydust").get()
            ));
        }
        traveler.journeyTransient().miningCooldownTicks = profile.cooldownTicks();
        traveler.journeyCounters().transactionsCommitted++;
        recordContractUnit(traveler, level);
        cancelJob(traveler, level, "mined");
    }

    /**
     * Simulates the insertion into a copy before mutating anything, so a full or removed container
     * can never duplicate or destroy a stack.
     */
    private static void commitDeposit(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos position
    ) {
        traveler.journeyCounters().chargedBlockReads += 2;
        final Container container = HopperBlockEntity.getContainerAt(level, position);
        if (container == null
            || !level.getBlockState(position).is(CreatureBehaviorTags.Blocks.HOBGOBLIN_DEPOSIT_CONTAINERS)) {
            cancelJob(traveler, level, "deposit invalid");
            return;
        }
        final int slot = IntStream.range(0, traveler.getInventory().getContainerSize())
            .filter(index -> traveler.getInventory().getItem(index)
                .is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            .findFirst()
            .orElse(-1);
        if (slot < 0) {
            cancelJob(traveler, level, "no cargo");
            return;
        }
        final ItemStack source = traveler.getInventory().getItem(slot);
        final ItemStack remainder = HopperBlockEntity.addItem(
            traveler.getInventory(), container, source.copy(), null
        );
        if (remainder.getCount() == source.getCount()) {
            // Nothing settled: the cargo is retained rather than dropped, and the claim is released.
            traveler.journeyCounters().transactionsRolledBack++;
            cancelJob(traveler, level, "deposit full");
            return;
        }
        traveler.getInventory().setItem(slot, remainder);
        traveler.journeyCounters().transactionsCommitted++;
        recordContractUnit(traveler, level);
        cancelJob(traveler, level, "deposited");
    }

    /** One completed unit ends the agreement once the declared cap is reached. */
    private static void recordContractUnit(final HobgoblinEntity traveler, final ServerLevel level) {
        final HobgoblinJourneyState state = traveler.journeyState();
        if (!state.contract().active()) {
            return;
        }
        traveler.setJourneyState(state.withContract(state.contract().withUnit()));
        if (traveler.journeyState().contract().unitsExhausted()) {
            endContract(traveler, level, ContractEnd.COMPLETED);
        }
    }

    /**
     * Approach only within a short local range and never past the disengage radius. The strike
     * itself is committed by the body's attack-only goal, which revalidates the target twice.
     */
    private static void executeDefend(final HobgoblinEntity traveler, final ServerLevel level) {
        final Optional<LivingEntity> aggressor = resolveAggressor(traveler, level);
        if (aggressor.isEmpty()) {
            traveler.setTarget(null);
            traveler.setJourneyState(traveler.journeyState().withMode(Mode.TRAVEL));
            return;
        }
        final LivingEntity target = aggressor.orElseThrow();
        if (HobgoblinJourneyRules.shouldDisengage(Math.sqrt(traveler.distanceToSqr(target)))) {
            traveler.setTarget(null);
            traveler.getNavigation().stop();
            return;
        }
        traveler.setTarget(target);
        traveler.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (traveler.distanceToSqr(target) > PICKUP_SQUARED) {
            requestNavigation(traveler, level, target.blockPosition(), WORK_SPEED);
        }
    }

    /** Escape routes away from the aggressor and away from village space, never through it. */
    private static void executeFlee(final HobgoblinEntity traveler, final ServerLevel level) {
        traveler.setTarget(null);
        traveler.setJourneyState(traveler.journeyState()
            .withCombat(traveler.journeyState().combat().withRetreating(true)));
        final Optional<LivingEntity> aggressor = resolveAggressor(traveler, level);
        final Vec3 away = aggressor
            .map(target -> traveler.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D))
            .filter(vector -> vector.lengthSqr() > 1.0E-4D)
            .map(Vec3::normalize)
            .orElseGet(() -> Vec3.directionFromRotation(0.0F, traveler.getYRot()));
        final BlockPos flat = BlockPos.containing(traveler.position().add(away.scale(12.0D)));
        traveler.journeyCounters().chargedBlockReads += 3;
        if (!level.isLoaded(flat) || !level.getWorldBorder().isWithinBounds(flat)) {
            traveler.getNavigation().stop();
            return;
        }
        final BlockPos refuge = groundedAt(level, flat);
        if (HobgoblinJourneyRules.villageExcluded(level.isVillage(refuge), false, false)) {
            traveler.getNavigation().stop();
            return;
        }
        requestNavigation(traveler, level, refuge, URGENT_SPEED);
    }

    /**
     * Children gather one tagged flower, dance with at most three same-caravan children, or gift a
     * neutral or friendly player on a long cooldown. Every pickup is claimed by {@code mobGriefing}
     * and by an exact single-position revalidation.
     */
    private static void executeChildPlay(final HobgoblinEntity child, final ServerLevel level) {
        final WorkAvailability work = child.journeyTransient().work;
        if (work.childGift() && offerFlowerToPlayer(child, level)) {
            return;
        }
        if (work.childFlower()) {
            gatherFlower(child, level);
            return;
        }
        if (work.childDance()) {
            danceWithChildren(child, level);
        }
    }

    private static boolean offerFlowerToPlayer(final HobgoblinEntity child, final ServerLevel level) {
        final Optional<ServerPlayer> recipient = level.players().stream()
            .filter(candidate -> candidate.isAlive() && !candidate.isSpectator())
            .filter(candidate -> child.distanceToSqr(candidate) <= 64.0D)
            .filter(candidate -> child.journeyState().relationScore(candidate.getUUID()) >= 0)
            .min(Comparator.comparingDouble(child::distanceToSqr));
        if (recipient.isEmpty()) {
            return false;
        }
        final ServerPlayer player = recipient.orElseThrow();
        if (child.distanceToSqr(player) > REACH_SQUARED) {
            requestNavigation(child, level, player.blockPosition(), WORK_SPEED);
            return true;
        }
        final ItemStack gift = child.getMainHandItem().copyWithCount(1);
        if (gift.isEmpty()) {
            return false;
        }
        if (!player.addItem(gift)) {
            player.drop(gift, false);
        }
        child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        child.setJourneyState(child.journeyState()
            .withChildGiftCooldown(HobgoblinJourneyRules.CHILD_GIFT_COOLDOWN_TICKS));
        child.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static void gatherFlower(final HobgoblinEntity child, final ServerLevel level) {
        final Optional<BlockPos> target = child.journeyTransient().plan.flower;
        if (target.isEmpty()) {
            return;
        }
        final BlockPos position = target.orElseThrow();
        if (child.distanceToSqr(Vec3.atCenterOf(position)) > PICKUP_SQUARED) {
            requestNavigation(child, level, position, WORK_SPEED);
            return;
        }
        child.journeyCounters().chargedBlockReads += 2;
        final BlockState state = level.getBlockState(position);
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)
            || !isGatherableFlower(state)
            || level.getBlockEntity(position) != null) {
            child.journeyTransient().plan.flower = Optional.empty();
            return;
        }
        final ItemStack flower = new ItemStack(state.getBlock().asItem());
        if (flower.isEmpty() || !level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState())) {
            child.journeyTransient().plan.flower = Optional.empty();
            return;
        }
        child.setItemSlot(EquipmentSlot.MAINHAND, flower);
        child.swing(InteractionHand.MAIN_HAND);
        child.journeyTransient().plan.flower = Optional.empty();
    }

    private static void danceWithChildren(final HobgoblinEntity child, final ServerLevel level) {
        final List<HobgoblinEntity> children = sameCaravanChildList(child, level);
        if (children.size() < HobgoblinJourneyRules.MIN_DANCE_CHILDREN) {
            return;
        }
        final Vec3 centre = children.stream()
            .map(Entity::position)
            .reduce(Vec3.ZERO, Vec3::add)
            .scale(1.0D / children.size());
        final int index = children.stream()
            .sorted(Comparator.comparing(Entity::getUUID, HobgoblinJourneyRules.unsignedUuidOrder()))
            .toList()
            .indexOf(child);
        final double angle = level.getGameTime() * 0.08D + Math.PI * 2.0D * index / children.size();
        final BlockPos step = BlockPos.containing(
            centre.add(Math.cos(angle) * 2.25D, 0.0D, Math.sin(angle) * 2.25D)
        );
        requestNavigation(child, level, step, WORK_SPEED);
    }

    // ================================================================ navigation

    /**
     * The only navigation writer. Requests are rate limited to at most one per
     * {@link HobgoblinJourneyRules#NAVIGATION_INTERVAL_TICKS}, every destination must be loaded,
     * inside the world border, and within the declared local route length, and three classified
     * failures impose the declared backoff before an expensive retry.
     */
    private static void requestNavigation(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = traveler.journeyTransient();
        final HobgoblinJourneyState state = traveler.journeyState();
        if (!HobgoblinJourneyRules.isDue(state.cadence().retryRemainingTicks())
            || !HobgoblinJourneyRules.isDue(scratch.navigationCooldownTicks)) {
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(destination)
            || !HobgoblinJourneyRules.withinLocalRoute(
                Math.sqrt(traveler.distanceToSqr(Vec3.atCenterOf(destination))))) {
            recordRouteFailure(traveler, RouteFailure.UNREACHABLE);
            return;
        }
        scratch.navigationCooldownTicks = HobgoblinJourneyRules.NAVIGATION_INTERVAL_TICKS;
        traveler.journeyCounters().navigationRequests++;
        final boolean moving = traveler.getNavigation().moveTo(
            destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, speed
        );
        if (moving) {
            traveler.setJourneyState(traveler.journeyState().withCadence(
                new HobgoblinJourneyState.Cadence(
                    RouteFailure.NONE, 0, 0, false, traveler.journeyState().cadence().blockedExits()
                )
            ));
            return;
        }
        recordRouteFailure(traveler, RouteFailure.NO_PATH);
    }

    private static void recordRouteFailure(
        final HobgoblinEntity traveler,
        final RouteFailure failure
    ) {
        traveler.journeyCounters().navigationFailures++;
        final HobgoblinJourneyState.Cadence cadence = traveler.journeyState().cadence();
        final int failures = HobgoblinJourneyRules.nextRouteFailure(cadence.routeFailures(), failure);
        traveler.setJourneyState(traveler.journeyState().withCadence(
            new HobgoblinJourneyState.Cadence(
                failure, failures, HobgoblinJourneyRules.backoffTicks(failures),
                failure == RouteFailure.STUCK, cadence.blockedExits()
            )
        ));
    }

    // ================================================================ feedback

    /**
     * Server-authoritative, range-checked, and rate limited. Feedback never exposes a hidden
     * inventory, unloaded target, relation score, caravan key, claim id, or protected block state.
     */
    private static void emitFeedback(final HobgoblinEntity traveler, final ServerLevel level) {
        final TransientState scratch = traveler.journeyTransient();
        if (!HobgoblinJourneyRules.isDue(scratch.feedbackCooldownTicks)) {
            return;
        }
        scratch.feedbackCooldownTicks = HobgoblinJourneyRules.FEEDBACK_INTERVAL_TICKS;
        final Mode mode = traveler.journeyState().mode();
        if (mode == Mode.IDLE || mode == Mode.TRADE_WAIT) {
            return;
        }
        traveler.journeyCounters().feedbackPulses++;
        traveler.playWorkSound();
    }

    // ================================================================ public body hooks

    /** Called from the merchant base before any trade may open or continue. */
    public static boolean safeToTrade(final HobgoblinEntity traveler) {
        return !traveler.isBaby()
            && traveler.getTarget() == null
            && !traveler.journeyTransient().hazardActive()
            && !traveler.journeyTransient().insideExcludedSpace();
    }

    /** Called from {@code HobgoblinEntity.canAttack} for every eligibility question. */
    public static boolean canAttack(final HobgoblinEntity traveler, final LivingEntity target) {
        if (target == null || traveler.isBaby()) {
            return false;
        }
        return HobgoblinJourneyRules.canTarget(
            !traveler.isBaby(),
            classify(traveler, target),
            target.isAlive(),
            target.level().dimension().equals(traveler.level().dimension()),
            target instanceof Player player && (player.isCreative() || player.isSpectator()),
            target.isInvulnerable()
        );
    }

    /**
     * The complete non-prey list. Everything that is not the one remembered direct aggressor is
     * protected, which is the mechanical form of "Hobgoblins have no proactive target selector".
     */
    private static TargetClass classify(
        final HobgoblinEntity traveler,
        final LivingEntity target
    ) {
        if (target instanceof HobgoblinEntity
            || target instanceof GoblinEntity
            || GoblinHostilityRules.isHumanVillager(target.getType())) {
            return TargetClass.PROTECTED;
        }
        final HobgoblinJourneyState state = traveler.journeyState();
        if (state.contract().contractor().map(target.getUUID()::equals).orElse(false)) {
            return TargetClass.PROTECTED;
        }
        return state.combat().remembersAggressor()
            && state.combat().aggressor().map(target.getUUID()::equals).orElse(false)
            ? TargetClass.DIRECT_AGGRESSOR
            : TargetClass.PROTECTED;
    }

    /** Called from {@code hurtServer}: exactly one direct aggressor is remembered, briefly. */
    public static void onAcceptedDamage(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == traveler) {
            return;
        }
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        HobgoblinJourneyState state = traveler.journeyState()
            .withCombat(HobgoblinJourneyState.Combat.aggressor(attacker.getUUID()));
        if (attacker instanceof Player player) {
            state = state.withRelation(player.getUUID(), RelationFact.ATTACK);
            traveler.journeyCounters().relationFacts++;
            if (state.contract().contractor().map(player.getUUID()::equals).orElse(false)) {
                traveler.setJourneyState(state);
                endContract(traveler, level, ContractEnd.BETRAYED);
                raiseAlarm(traveler, level, attacker.getUUID());
                return;
            }
        }
        traveler.setJourneyState(state);
        raiseAlarm(traveler, level, attacker.getUUID());
    }

    /**
     * Depth one, at most four members, one direct aggressor. An alerted member never rebroadcasts,
     * so a caravan can never chain-alert a neighbouring caravan.
     */
    private static void raiseAlarm(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final UUID aggressor
    ) {
        final Optional<Long> key = traveler.journeyState().caravan().key();
        if (key.isEmpty()) {
            return;
        }
        final List<HobgoblinEntity> visited = level.getEntitiesOfClass(
            HobgoblinEntity.class,
            traveler.getBoundingBox().inflate(
                HobgoblinJourneyRules.MEMBER_RADIUS, 8.0D, HobgoblinJourneyRules.MEMBER_RADIUS
            ),
            candidate -> candidate != traveler && candidate.isAlive() && !candidate.isBaby()
        );
        traveler.journeyCounters().memberVisits +=
            Math.min(visited.size(), HobgoblinJourneyRules.MAX_MEMBER_VISITS);
        visited.stream()
            .filter(candidate -> candidate.journeyState().caravan().key()
                .map(member -> member.equals(key.orElseThrow())).orElse(false))
            .limit(HobgoblinJourneyRules.alarmRecipients(HobgoblinJourneyRules.MAX_ALARM_MEMBERS))
            .forEach(candidate -> candidate.setJourneyState(candidate.journeyState()
                .withCombat(HobgoblinJourneyState.Combat.aggressor(aggressor))));
    }

    /** Called from {@code mobInteract} the moment the shared contract binding accepts. */
    public static void onContractAccepted(final HobgoblinEntity traveler, final Player player) {
        if (!(traveler.level() instanceof ServerLevel level)) {
            return;
        }
        final HobgoblinJourneyState state = traveler.journeyState();
        if (!HobgoblinJourneyRules.canAcceptContract(
            !traveler.isBaby(),
            state.combat().remembersAggressor(),
            traveler.isTrading(),
            traveler.journeyTransient().eventResident(),
            traveler.journeyTransient().insideExcludedSpace(),
            state.contract().active()
        )) {
            return;
        }
        traveler.setJourneyState(state.withContract(HobgoblinJourneyState.Contract.accepted(
            player.getUUID(),
            HobgoblinJourneyRules.preferredWork(traveler.goblinProfession()),
            Optional.empty()
        )));
        traveler.journeyCounters().contractsAccepted++;
        HobgoblinJourneyData.get(level).releaseClaimsOf(traveler.getUUID());
    }

    /**
      * Called from {@code mobInteract} once a customer screen actually opened. A positive
      * impression buys a small, vanilla-safe discount through the ordinary special-price field; no
      * item identity or count required by ritual compatibility is ever changed.
      */
    public static void onTradeOpened(final HobgoblinEntity traveler, final Player player) {
        traveler.getNavigation().stop();
        traveler.setTarget(null);
        final int discount = HobgoblinJourneyRules.priceImprovement(
            traveler.journeyState().relationScore(player.getUUID())
        );
        traveler.getOffers().forEach(offer -> {
            offer.resetSpecialPriceDiff();
            if (discount > 0) {
                offer.addToSpecialPriceDiff(-discount);
            }
        });
        traveler.setJourneyState(traveler.journeyState()
            .withMode(Mode.TRADE_WAIT)
            .withRelation(player.getUUID(), RelationFact.FAIR_TRADE));
        traveler.journeyCounters().relationFacts++;
    }

    /**
     * Accepting food is hospitality, not payment: it records one bounded positive fact and never
     * creates ownership, a follow mode, or a contract.
     */
    public static boolean offerHospitality(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final Player player,
        final ItemStack supplied
    ) {
        if (traveler.isBaby()
            || supplied.isEmpty()
            || !supplied.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.FOODS)
            || !HobgoblinJourneyRules.isDue(traveler.journeyTransient().relationCooldownTicks)) {
            return false;
        }
        traveler.journeyTransient().relationCooldownTicks = HobgoblinJourneyRules.RELATION_INTERVAL_TICKS;
        if (!player.hasInfiniteMaterials()) {
            supplied.shrink(1);
        }
        traveler.setJourneyState(traveler.journeyState()
            .withRelation(player.getUUID(), RelationFact.ACCEPTED_FOOD));
        traveler.journeyCounters().relationFacts++;
        traveler.playWorkSound();
        return true;
    }

    /** Called from {@code mobInteract}: any player may hand a Miner an accepted tool. */
    public static InteractionResult equipMiningTool(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final Player player,
        final ItemStack supplied
    ) {
        final ItemStack previous = traveler.getMainHandItem().copy();
        final ItemStack equipped = supplied.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            supplied.shrink(1);
        }
        traveler.equipToolSlot(equipped);
        traveler.journeyTransient().miningCooldownTicks = 0;
        traveler.swing(InteractionHand.MAIN_HAND);
        if (!previous.isEmpty()) {
            traveler.spawnAtLocation(level, previous);
        }
        traveler.setJourneyState(traveler.journeyState()
            .withRelation(player.getUUID(), RelationFact.AID));
        traveler.journeyCounters().relationFacts++;
        return InteractionResult.SUCCESS;
    }

    /** Called from {@code checkNaturalSpawnRules}; uses already-loaded sections only. */
    public static int countLoadedTravelersNear(final ServerLevel level, final BlockPos position) {
        return level.getEntitiesOfClass(
            HobgoblinEntity.class,
            new AABB(position).inflate(HobgoblinJourneyRules.LOCAL_SPAWN_CAP_RADIUS)
        ).size();
    }

    public static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /**
     * The complete external-event adapter surface: the anchor of this traveler's active camp, if it
     * has one. F03/F04 own event creation, membership, attackers, quotas, rewards, and cleanup.
     */
    public static Optional<BlockPos> activeCampAnchor(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        return traveler.journeyState().camp().key()
            .map(key -> HobgoblinJourneyData.get(level).camp(key))
            .filter(HobgoblinJourneyData.CampRecord::present)
            .flatMap(HobgoblinJourneyData.CampRecord::anchor);
    }

    // ================================================================ surveys

    private static Optional<ItemEntity> nearestLooseItem(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final List<ItemEntity> visited = BoundedEntityQuery.collect(
            level,
            ItemEntity.class,
            traveler.getBoundingBox().inflate(
                HobgoblinJourneyRules.LOOSE_ITEM_RADIUS, 4.0D, HobgoblinJourneyRules.LOOSE_ITEM_RADIUS
            ),
            item -> item.isAlive() && traveler.wantsToPickUp(level, item.getItem()),
            HobgoblinJourneyRules.MAX_LOOSE_VISITS
        );
        traveler.journeyCounters().looseVisits += visited.size();
        return visited.stream()
            .limit(HobgoblinJourneyRules.MAX_LOOSE_RETAINED)
            .min(Comparator.comparingDouble(traveler::distanceToSqr));
    }

    private static Optional<ItemEntity> resolveLooseItem(final ServerLevel level, final UUID id) {
        final Entity found = level.getEntity(id);
        return found instanceof ItemEntity item && item.isAlive() ? Optional.of(item) : Optional.empty();
    }

    private static Optional<BlockPos> nearestMineable(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final ItemStack tool = traveler.getMainHandItem();
        return chargedScan(traveler, level, ScanClass.MINING, 5, 2,
            HobgoblinJourneyRules.MAX_MINING_BLOCK_READS,
            position -> isMineable(traveler, level, position, tool));
    }

    /** The single mineable predicate, shared by the survey and by the pre-commit revalidation. */
    private static boolean isMineable(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos position,
        final ItemStack tool
    ) {
        final BlockState state = level.getBlockState(position);
        return HobgoblinJourneyRules.canEditBlock(
            level.isLoaded(position),
            level.getWorldBorder().isWithinBounds(position),
            level.getGameRules().get(GameRules.MOB_GRIEFING),
            state.is(WarlockeryTags.Blocks.HOBGOBLIN_MINEABLES),
            !level.getFluidState(position).isEmpty(),
            level.getBlockEntity(position) != null,
            state.getDestroySpeed(level, position)
        )
            && tool.isCorrectToolForDrops(state)
            && !HobgoblinJourneyRules.villageExcluded(level.isVillage(position), false, false);
    }

    private static Optional<BlockPos> nearestDeposit(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        return chargedScan(traveler, level, ScanClass.DEPOSIT, 6, 3,
            HobgoblinJourneyRules.MAX_WORK_BLOCK_READS, position ->
                level.getBlockState(position).is(CreatureBehaviorTags.Blocks.HOBGOBLIN_DEPOSIT_CONTAINERS)
                    && HopperBlockEntity.getContainerAt(level, position) != null);
    }

    private static Optional<BlockPos> nearestFlower(
        final HobgoblinEntity child,
        final ServerLevel level
    ) {
        return chargedScan(child, level, ScanClass.FLOWER, 4, 1,
            HobgoblinJourneyRules.MAX_CHILD_BLOCK_READS, position ->
                isGatherableFlower(level.getBlockState(position))
                    && level.getBlockEntity(position) == null);
    }

    private static Optional<BlockPos> findCampAnchor(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        return chargedScan(traveler, level, ScanClass.CAMP, 6, 2,
            HobgoblinJourneyRules.MAX_CAMP_BLOCK_READS, position ->
                standable(level, position)
                    && !HobgoblinJourneyRules.villageExcluded(level.isVillage(position), false, false)
                    && !HobgoblinJourneyData.get(level).siteClaimed(position));
    }

    /** One bounded outward travel leg. A long journey is a sequence of these, never a global path. */
    private static Optional<BlockPos> findTravelLeg(
        final HobgoblinEntity traveler,
        final ServerLevel level
    ) {
        final BlockPos origin = traveler.blockPosition();
        final int start = HobgoblinJourneyRules.stableOffset(traveler.getUUID(), TRAVEL_OFFSETS.size());
        for (int index = 0; index < TRAVEL_OFFSETS.size(); index++) {
            final BlockPos flat = origin.offset(
                TRAVEL_OFFSETS.get((start + index) % TRAVEL_OFFSETS.size())
            );
            traveler.journeyCounters().chargedBlockReads += 3;
            if (!level.isLoaded(flat) || !level.getWorldBorder().isWithinBounds(flat)) {
                continue;
            }
            final BlockPos candidate = groundedAt(level, flat);
            if (standable(level, candidate)
                && !HobgoblinJourneyRules.villageExcluded(level.isVillage(candidate), false, false)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // ================================================================ charged scanning

    /**
     * Precomputed centre-out offset envelopes, one immutable list per box shape. Sorted by squared
     * distance from the entity, then deterministically by y, x, z, so identical facts always produce
     * an identical evaluation order on every server.
     */
    private static final Map<Long, List<BlockPos>> ENVELOPES = new java.util.concurrent.ConcurrentHashMap<>();

    static List<BlockPos> envelope(final int horizontal, final int vertical) {
        return ENVELOPES.computeIfAbsent((long) horizontal << 32 | vertical, _ -> {
            final List<BlockPos> offsets = new ArrayList<>();
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dx = -horizontal; dx <= horizontal; dx++) {
                    for (int dz = -horizontal; dz <= horizontal; dz++) {
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
            offsets.sort(Comparator
                .comparingInt((BlockPos offset) -> offset.getX() * offset.getX()
                    + offset.getY() * offset.getY() + offset.getZ() * offset.getZ())
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
            return List.copyOf(offsets);
        });
    }

    /** The number of near-envelope offsets evaluated on every single scan of a given budget. */
    static int anchorSize(final int envelopeSize, final int readCap) {
        return Math.min(Math.max(0, readCap) / 2, envelopeSize);
    }

    /** The rotating page size, that is the budget left over for the far tail after the anchor. */
    static int pageSize(final int envelopeSize, final int readCap) {
        final int anchor = anchorSize(envelopeSize, readCap);
        return Math.min(Math.max(0, readCap - anchor), envelopeSize - anchor);
    }

    /**
     * The exact offsets one scan evaluates: the fixed near anchor followed by one rotating page over
     * the far tail. Pure and world free, so the coverage contract is directly unit testable.
     */
    static List<BlockPos> scanWindow(
        final List<BlockPos> offsets,
        final int readCap,
        final int cursor
    ) {
        final int anchor = anchorSize(offsets.size(), readCap);
        final int tail = offsets.size() - anchor;
        final int page = pageSize(offsets.size(), readCap);
        final int start = tail == 0 ? 0 : Math.floorMod(cursor, tail);
        final List<BlockPos> window = new ArrayList<>(offsets.subList(0, anchor));
        for (int index = 0; index < page; index++) {
            window.add(offsets.get(anchor + (start + index) % tail));
        }
        return List.copyOf(window);
    }

    /**
     * The one charged block-scan primitive, and the reason a Hobgoblin search actually leaves its
     * innermost ring.
     *
     * <p>Every read cap is far below its own box volume (mining 128 of 605, deposit 128 of 1,183,
     * camp 128 of 845, flower 64 of 243), so a naive raster would spend the entire budget on one
     * corner of the envelope and never reach the traveler's own Y level or the opposite quadrant.
     * Instead the envelope is enumerated centre-out and split into a fixed near anchor of
     * {@code readCap / 2} offsets evaluated on every scan, plus a rotating page of the remaining
     * budget over the tail, whose per-class cursor advances by the page size on every scan and
     * wraps. The whole far envelope is therefore evaluated within {@code ceil(tail / page)} scans and
     * the near envelope is never skipped.</p>
     *
     * <p>Every candidate the window names is charged before any filter can reject it, including the
     * world-border and loaded-chunk rejections, so a rejected candidate can never be free.</p>
     */
    private static Optional<BlockPos> chargedScan(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final ScanClass scanClass,
        final int horizontal,
        final int vertical,
        final int readCap,
        final Predicate<BlockPos> accepts
    ) {
        final BlockPos origin = traveler.blockPosition();
        final List<BlockPos> offsets = envelope(horizontal, vertical);
        final int[] cursors = traveler.journeyTransient().scanCursors;
        final int tail = offsets.size() - anchorSize(offsets.size(), readCap);
        if (cursors[scanClass.ordinal()] == UNSEEDED_CURSOR) {
            cursors[scanClass.ordinal()] = tail == 0
                ? 0
                : HobgoblinJourneyRules.stableOffset(traveler.getUUID(), tail);
        }
        final List<BlockPos> hits = new ArrayList<>();
        int reads = 0;
        for (final BlockPos offset : scanWindow(offsets, readCap, cursors[scanClass.ordinal()])) {
            final BlockPos candidate = origin.offset(offset);
            reads++;
            if (!level.isLoaded(candidate) || !level.getWorldBorder().isWithinBounds(candidate)) {
                continue;
            }
            if (accepts.test(candidate)) {
                hits.add(candidate.immutable());
            }
        }
        if (tail > 0) {
            cursors[scanClass.ordinal()] = Math.floorMod(
                cursors[scanClass.ordinal()] + pageSize(offsets.size(), readCap), tail
            );
        }
        traveler.journeyCounters().chargedBlockReads += reads;
        return hits.stream().min(Comparator.comparingDouble(
            position -> traveler.distanceToSqr(Vec3.atCenterOf(position))
        ));
    }

    // ================================================================ helpers

    private static CampPhase campPhaseOf(final HobgoblinEntity traveler, final ServerLevel level) {
        return traveler.journeyState().camp().key()
            .map(key -> HobgoblinJourneyData.get(level).camp(key).phase())
            .orElse(CampPhase.NONE);
    }

    private static boolean campEventHeld(final HobgoblinEntity traveler, final ServerLevel level) {
        return traveler.journeyState().camp().key()
            .map(key -> HobgoblinJourneyData.get(level).camp(key).eventHeld())
            .orElse(false);
    }

    private static boolean assaultRaiderNear(final ServerLevel level, final BlockPos anchor) {
        return level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(anchor).inflate(32.0D, 16.0D, 32.0D),
            VillageAssaultRuntime::isAssaultRaider
        ).stream().findAny().isPresent();
    }

    private static int requiredCampEdits() {
        return CAMP_OFFSETS.size();
    }

    /** The recognizable windbreak: a low dirt wall ring one block high around the anchor. */
    private static List<BlockPos> campEnvelope() {
        final List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL;
             dx <= HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL; dx++) {
            for (int dz = -HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL;
                 dz <= HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL; dz++) {
                final boolean edge = Math.abs(dx) == HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL
                    || Math.abs(dz) == HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL;
                // The south-centre gap is the doorway; a camp never seals its own residents in.
                final boolean doorway = dx == 0 && dz == HobgoblinJourneyRules.CAMP_FOOTPRINT_HORIZONTAL;
                if (edge && !doorway) {
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static Block campMaterial(final BlockPos offset) {
        return offset.getY() == 0 ? Blocks.DIRT : Blocks.OAK_PLANKS;
    }

    private static String blockId(final Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static boolean campFootprintLoaded(final ServerLevel level, final BlockPos anchor) {
        return CAMP_OFFSETS.stream().allMatch(offset -> level.isLoaded(anchor.offset(offset)));
    }

    private static boolean campFootprintClear(
        final HobgoblinEntity traveler,
        final ServerLevel level,
        final BlockPos anchor
    ) {
        traveler.journeyCounters().chargedBlockReads += CAMP_OFFSETS.size() * 2L;
        return CAMP_OFFSETS.stream().allMatch(offset -> {
            final BlockPos position = anchor.offset(offset);
            return level.getBlockState(position).isAir() && level.getBlockEntity(position) == null;
        });
    }

    private static boolean campMaterialsCarried(final HobgoblinEntity traveler) {
        return count(traveler, stack -> stack.is(ItemTags.DIRT)) >= HobgoblinJourneyRules.CAMP_DIRT_COST;
    }

    private static boolean standable(final ServerLevel level, final BlockPos position) {
        return level.getBlockState(position.below()).blocksMotion()
            && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
            && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
            && level.getFluidState(position).isEmpty();
    }

    /**
     * Probes downward and upward through already-loaded positions around the traveler's own Y. It
     * never calls a chunk-loading accessor, so a column that is not loaded simply declines.
     */
    private static BlockPos groundedAt(final ServerLevel level, final BlockPos flat) {
        for (int delta = 0; delta <= 4; delta++) {
            final BlockPos below = flat.below(delta);
            if (level.isLoaded(below) && standable(level, below)) {
                return below;
            }
            final BlockPos above = flat.above(delta);
            if (level.isLoaded(above) && standable(level, above)) {
                return above;
            }
        }
        return flat;
    }

    private static boolean isGatherableFlower(final BlockState state) {
        return (state.is(BlockTags.SMALL_FLOWERS)
            || state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.FLOWERS))
            && !(state.getBlock() instanceof DoublePlantBlock);
    }

    private static int sameCaravanChildren(
        final HobgoblinEntity child,
        final ServerLevel level
    ) {
        return sameCaravanChildList(child, level).size();
    }

    private static List<HobgoblinEntity> sameCaravanChildList(
        final HobgoblinEntity child,
        final ServerLevel level
    ) {
        final Optional<Long> key = child.journeyState().caravan().key();
        final List<HobgoblinEntity> visited = BoundedEntityQuery.collect(
            level,
            HobgoblinEntity.class,
            child.getBoundingBox().inflate(8.0D, 3.0D, 8.0D),
            candidate -> candidate.isAlive() && candidate.isBaby(),
            HobgoblinJourneyRules.MAX_ENTITY_VISITS
        );
        child.journeyCounters().entityVisits += visited.size();
        return visited.stream()
            .filter(candidate -> key.isEmpty()
                || candidate.journeyState().caravan().key()
                    .map(member -> member.equals(key.orElseThrow())).orElse(false))
            .limit(HobgoblinJourneyRules.MAX_DANCE_CHILDREN)
            .toList();
    }

    private static boolean carries(
        final HobgoblinEntity traveler,
        final Predicate<ItemStack> predicate
    ) {
        return count(traveler, predicate) > 0;
    }

    private static int count(
        final HobgoblinEntity traveler,
        final Predicate<ItemStack> predicate
    ) {
        return IntStream.range(0, traveler.getInventory().getContainerSize())
            .mapToObj(traveler.getInventory()::getItem)
            .filter(predicate)
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    private static void consume(
        final HobgoblinEntity traveler,
        final Predicate<ItemStack> predicate,
        final int amount
    ) {
        int remaining = amount;
        for (int slot = 0; slot < traveler.getInventory().getContainerSize() && remaining > 0; slot++) {
            final ItemStack stack = traveler.getInventory().getItem(slot);
            if (!predicate.test(stack)) {
                continue;
            }
            final int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
        }
    }
}
