package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.CombatRole;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Period;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RelationEvent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Responder;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RouteFailure;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.TargetClass;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.WorkAvailability;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.world.GoblinEnclaveData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side F10 Goblin behavior controller and the sole owner of ordinary Goblin
 * navigation. The body's four executors declare LOOK only, so nothing here ever contends for MOVE.
 *
 * <p>Every scan, charged block read, entity visit, navigation request, claim, transaction, and
 * feedback pulse is counted against the declared hard budgets. No method here enumerates a
 * dimension's entity list, forces or creates a chunk ticket, calls a chunk-loading accessor, holds a
 * mutable block snapshot across ticks, retains a live entity, or writes another family's data.</p>
 *
 * <p>Every world-editing job runs the declared eight-stage transaction inside one server tick:
 * plan, claim, validate, reserve, revalidate, commit, settle, and - on any failure - rollback of the
 * exact prior block states and the exact reserved inventory before the claim is released.</p>
 */
public final class GoblinEnclaveRuntime {
    private static final double WORK_SPEED = 0.85D;
    private static final double URGENT_SPEED = 1.2D;
    private static final double REACH_SQUARED = 9.0D;
    private static final double PICKUP_SQUARED = 4.0D;
    private static final List<BlockPos> HUT_OFFSETS = List.of(
        new BlockPos(7, 0, 0), new BlockPos(-7, 0, 0),
        new BlockPos(0, 0, 7), new BlockPos(0, 0, -7),
        new BlockPos(9, 0, 7), new BlockPos(-9, 0, -7)
    );

    private GoblinEnclaveRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local and never persisted. */
    public static final class Counters {
        long entityVisits;
        long entitiesRetained;
        long memberVisits;
        long looseVisits;
        long chargedBlockReads;
        long plansAttempted;
        long claimsGranted;
        long claimsRejected;
        long navigationRequests;
        long navigationFailures;
        long transactionsCommitted;
        long transactionsRolledBack;
        long editsApplied;
        long editsRestored;
        long actionsCanceled;
        long feedbackPulses;
        long decisions;

        public long entityVisits() { return entityVisits; }
        public long entitiesRetained() { return entitiesRetained; }
        public long memberVisits() { return memberVisits; }
        public long looseVisits() { return looseVisits; }
        public long chargedBlockReads() { return chargedBlockReads; }
        public long plansAttempted() { return plansAttempted; }
        public long claimsGranted() { return claimsGranted; }
        public long claimsRejected() { return claimsRejected; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationFailures() { return navigationFailures; }
        public long transactionsCommitted() { return transactionsCommitted; }
        public long transactionsRolledBack() { return transactionsRolledBack; }
        public long editsApplied() { return editsApplied; }
        public long editsRestored() { return editsRestored; }
        public long actionsCanceled() { return actionsCanceled; }
        public long feedbackPulses() { return feedbackPulses; }
        public long decisions() { return decisions; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by at most one cadence but can never replay a birth, block edit, trade, attack, or transfer.
     */
    public static final class TransientState {
        boolean reconciled;
        int decisionCooldownTicks;
        int perceptionCooldownTicks;
        int memberCooldownTicks;
        int workScanCooldownTicks;
        int siteScanCooldownTicks;
        int navigationCooldownTicks;
        int feedbackCooldownTicks;
        int miningCooldownTicks;
        boolean hazardActive;
        boolean sheltered;
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
            memberCooldownTicks = 0;
            workScanCooldownTicks = 0;
            siteScanCooldownTicks = 0;
            navigationCooldownTicks = 0;
            feedbackCooldownTicks = 0;
            miningCooldownTicks = 0;
            hazardActive = false;
            sheltered = false;
            observedDay = UNSEEDED_DAY;
            work = WorkAvailability.none();
            plan.clear();
            // Not zero: a Goblin that unloads more often than one full rotation would restart the
            // far tail at index 0 every time and never reach the far envelope at all. Unseeded
            // cursors are seeded from the stable identity offset on first use instead.
            java.util.Arrays.fill(scanCursors, UNSEEDED_CURSOR);
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        public Plan plan() {
            return plan;
        }
    }

    /**
     * The single surveyed plan. Scanning happens once per survey cadence and the concrete chosen
     * position is carried forward here; an executor never rescans, it only revalidates the exact
     * position it was handed. This is what makes BUILD_HUT reachable at all: the site survey and the
     * site execution can no longer race each other for the same cooldown.
     */
    public static final class Plan {
        Optional<BlockPos> mine = Optional.empty();
        Optional<BlockPos> log = Optional.empty();
        Optional<BlockPos> deposit = Optional.empty();
        Optional<BlockPos> hutSite = Optional.empty();
        Optional<BlockPos> tunnel = Optional.empty();
        Optional<BlockPos> flower = Optional.empty();
        boolean bed;

        void clear() {
            mine = Optional.empty();
            log = Optional.empty();
            deposit = Optional.empty();
            hutSite = Optional.empty();
            tunnel = Optional.empty();
            flower = Optional.empty();
            bed = false;
        }

        public Optional<BlockPos> hutSite() {
            return hutSite;
        }

        public Optional<BlockPos> mine() {
            return mine;
        }

        public boolean bed() {
            return bed;
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
        MINING, LOG, DEPOSIT, FLOWER, BED
    }

    // ================================================================ entry point

    /** The one live entry point, called from {@code GoblinEntity.customServerAiStep} every tick. */
    public static void tick(final GoblinEntity goblin, final ServerLevel level) {
        if (!GoblinEnclaveRules.isExactGoblin(goblin.creatureKind())
            || goblin.isNoAi() || !goblin.isAlive()) {
            return;
        }
        reconcileOnLoad(goblin, level);
        advanceLoadedTimers(goblin, level);
        if (goblin.isTrading()) {
            holdForTrade(goblin);
            return;
        }
        if (tickHazard(goblin, level)) {
            return;
        }
        decide(goblin, level);
        execute(goblin, level);
        emitFeedback(goblin, level);
    }

    // ================================================================ lifecycle

    private static void reconcileOnLoad(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        // Stagger every cadence by a stable identity offset so a reloaded batch never spikes.
        final int offset = GoblinEnclaveRules.stableOffset(
            goblin.getUUID(), GoblinEnclaveRules.MAX_SCHEDULE_OFFSET_TICKS + 1
        );
        scratch.decisionCooldownTicks = offset % GoblinEnclaveRules.DECISION_INTERVAL_TICKS;
        scratch.perceptionCooldownTicks = offset % GoblinEnclaveRules.PERCEPTION_INTERVAL_TICKS;
        scratch.memberCooldownTicks = offset % GoblinEnclaveRules.MEMBER_INTERVAL_TICKS;
        scratch.observedDay = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);

        GoblinEnclaveState state = goblin.goblinEnclaveState();
        // The persisted anchor key is still intact here, so any lease this Goblin left behind is
        // released from the real record BEFORE the anchor can be invalidated below. Doing it after
        // would strand the lease in a record this Goblin never visits again and nothing ever ages.
        state.anchor().enclaveKey().ifPresent(key ->
            GoblinEnclaveData.get(level).releaseClaimsOf(key, goblin.getUUID())
        );
        // A committed action, claim, target, and material reservation never survive an unload: a
        // transaction cannot span a tick, so it cannot span a save either.
        state = state.releaseAction().withCombat(GoblinEnclaveState.Combat.none());
        final String dimension = dimensionOf(level);
        final boolean anchorInvalid = !state.anchor().present()
            || state.anchor().dimension().map(stored -> !stored.equals(dimension)).orElse(true)
            || state.anchor().position()
                .map(anchor -> !level.getWorldBorder().isWithinBounds(anchor)).orElse(true);
        if (anchorInvalid) {
            state = state.withAnchor(GoblinEnclaveState.Anchor.none());
        }
        if (state.patron().depositPreference()
            .map(position -> !level.getWorldBorder().isWithinBounds(position)).orElse(false)) {
            state = state.withPatron(state.patron().expirePreference());
        }
        goblin.setGoblinEnclaveState(state);
    }

    /**
     * Advances every remaining-tick counter by exactly one loaded tick. Schedule catch-up on load
     * expires deadlines and recomputes the current period; it never replays missed work.
     */
    private static void advanceLoadedTimers(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        scratch.decisionCooldownTicks = Math.max(0, scratch.decisionCooldownTicks - 1);
        scratch.perceptionCooldownTicks = Math.max(0, scratch.perceptionCooldownTicks - 1);
        scratch.memberCooldownTicks = Math.max(0, scratch.memberCooldownTicks - 1);
        scratch.workScanCooldownTicks = Math.max(0, scratch.workScanCooldownTicks - 1);
        scratch.siteScanCooldownTicks = Math.max(0, scratch.siteScanCooldownTicks - 1);
        scratch.navigationCooldownTicks = Math.max(0, scratch.navigationCooldownTicks - 1);
        scratch.feedbackCooldownTicks = Math.max(0, scratch.feedbackCooldownTicks - 1);
        scratch.miningCooldownTicks = Math.max(0, scratch.miningCooldownTicks - 1);

        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        final GoblinEnclaveState.Action action = state.action();
        final GoblinEnclaveState.Cadence cadence = state.cadence();
        GoblinEnclaveState updated = state
            .withAction(new GoblinEnclaveState.Action(
                action.intent(), action.claimId(), action.targetId(), action.destination(),
                action.destinationDimension(), Math.max(0, action.commitRemainingTicks() - 1),
                Math.max(0, action.recoveryRemainingTicks() - 1), action.reservedDirt(),
                action.reservedLogs(), action.reservedPlanks()
            ))
            .withCadence(new GoblinEnclaveState.Cadence(
                cadence.lastFailure(), cadence.routeFailures(),
                Math.max(0, cadence.retryRemainingTicks() - 1), cadence.stuck()
            ))
            .withChildGiftCooldown(Math.max(0, state.childGiftCooldownTicks() - 1));
        final GoblinEnclaveState.Merchant merchant = updated.merchant();
        updated = updated.withMerchant(new GoblinEnclaveState.Merchant(
            merchant.level(), merchant.xp(), merchant.restocksToday(),
            Math.max(0, merchant.restockSpacingTicks() - 1)
        ));
        goblin.setGoblinEnclaveState(updated);
        enclaveKey(goblin).ifPresent(key ->
            GoblinEnclaveData.get(level).advanceLoadedTick(key, level.getGameTime())
        );
        // Lease expiry is a tick transition, never a constructor side effect. The state record must
        // not silently end what this branch owns: if it cleared the claim itself, the action would
        // keep its intent, holdsClaim() would read false, the execute guard would be skipped, and
        // two expired Goblins could mutate the same worksite.
        if (action.commitRemainingTicks() > 0 && updated.action().leaseExpired()) {
            cancelAction(goblin, level, "lease expired");
        }
    }

    // ================================================================ hazard

    /**
     * Immediate entity-only hazard observation runs every tick; it reads the two blocks the body
     * already occupies and nothing else, so it costs no spatial query.
     */
    private static boolean tickHazard(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        final boolean exposed = goblin.isOnFire()
            || goblin.isInLava()
            || goblin.getAirSupply() <= 0
            || goblin.isFreezing();
        scratch.hazardActive = exposed;
        if (!exposed) {
            return false;
        }
        cancelAction(goblin, level, "hazard");
        final Optional<BlockPos> refuge = goblin.goblinEnclaveState().combat().shelter()
            .or(() -> goblin.goblinEnclaveState().anchor().position());
        refuge.ifPresent(position -> requestNavigation(goblin, level, position, URGENT_SPEED));
        return true;
    }

    // ================================================================ decision

    private static void decide(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        if (!GoblinEnclaveRules.isDue(scratch.decisionCooldownTicks)) {
            return;
        }
        scratch.decisionCooldownTicks = GoblinEnclaveRules.DECISION_INTERVAL_TICKS;
        goblin.goblinCounters().decisions++;

        observe(goblin, level);
        reconcileEnclave(goblin, level);
        reconcilePatron(goblin, level);
        reconcileMerchant(goblin, level);

        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        final Period period = GoblinEnclaveRules.period(
            level.getOverworldClockTime(),
            GoblinEnclaveRules.stableOffset(goblin.getUUID(), GoblinEnclaveRules.MAX_SCHEDULE_OFFSET_TICKS + 1)
        );
        final Intent candidate = GoblinEnclaveRules.selectIntent(
            goblin.isBaby(),
            scratch.hazardActive,
            goblin.isAssaultMember(),
            state.combat().role(),
            goblin.isTrading(),
            period,
            scratch.sheltered,
            state.profession(),
            scratch.work
        );
        final Intent current = state.action().intent();
        if (candidate == current) {
            return;
        }
        if (state.action().holdsClaim() && !GoblinEnclaveRules.interrupts(current, candidate)) {
            return;
        }
        cancelAction(goblin, level, "reprioritized");
        commitIntent(goblin, level, candidate);
    }

    /** Grants at most one enclave claim for the newly selected intent, then commits it. */
    private static void commitIntent(
        final GoblinEntity goblin,
        final ServerLevel level,
        final Intent intent
    ) {
        final Optional<Long> key = enclaveKey(goblin);
        Optional<UUID> claim = Optional.empty();
        if (key.isPresent() && intent != Intent.IDLE && intent != Intent.SEEK_SHELTER) {
            claim = GoblinEnclaveData.get(level)
                .claim(key.orElseThrow(), intent, goblin.getUUID(), Optional.empty());
            if (claim.isPresent()) {
                goblin.goblinCounters().claimsGranted++;
            } else {
                goblin.goblinCounters().claimsRejected++;
            }
        }
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        goblin.setGoblinEnclaveState(state.withAction(new GoblinEnclaveState.Action(
            intent, claim, state.action().targetId(), Optional.empty(), Optional.empty(),
            GoblinEnclaveRules.leaseTicks(), 0, 0, 0, 0
        )));
    }

    /** Releases the claim, target, destination, and every material reservation transactionally. */
    private static void cancelAction(
        final GoblinEntity goblin,
        final ServerLevel level,
        final String reason
    ) {
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (state.action().intent() == Intent.IDLE && state.action().claimId().isEmpty()) {
            return;
        }
        enclaveKey(goblin).ifPresent(key -> state.action().claimId()
            .ifPresent(claim -> GoblinEnclaveData.get(level).releaseClaim(key, claim)));
        goblin.setGoblinEnclaveState(state.releaseAction());
        goblin.getNavigation().stop();
        goblin.goblinCounters().actionsCanceled++;
    }

    // ================================================================ observation

    /**
     * One bounded loaded-section perception pass. The current target and bound patron are preseeded
     * so a generic candidate crowd can never starve them out of the retained set.
     */
    private static void observe(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        if (GoblinEnclaveRules.isDue(scratch.perceptionCooldownTicks)) {
            scratch.perceptionCooldownTicks = GoblinEnclaveRules.PERCEPTION_INTERVAL_TICKS;
            acquireTarget(goblin, level);
        }
        scratch.sheltered = !level.canSeeSky(goblin.blockPosition())
            || level.getMaxLocalRawBrightness(goblin.blockPosition())
                <= GoblinEnclaveRules.MAX_NATURAL_SPAWN_LIGHT;
        if (GoblinEnclaveRules.isDue(scratch.workScanCooldownTicks)) {
            scratch.workScanCooldownTicks = GoblinEnclaveRules.WORK_SCAN_INTERVAL_TICKS;
            scratch.work = surveyWork(goblin, level);
        }
    }

    private static void acquireTarget(final GoblinEntity goblin, final ServerLevel level) {
        if (goblin.isBaby()) {
            goblin.setTarget(null);
            return;
        }
        final LivingEntity current = goblin.getTarget();
        if (current != null && current.isAlive() && goblin.canAttack(current)) {
            return;
        }
        goblin.setTarget(null);
        final List<Villager> visited = level.getEntitiesOfClass(
            Villager.class,
            goblin.getBoundingBox().inflate(GoblinEnclaveRules.PERCEPTION_RADIUS, 8.0D,
                GoblinEnclaveRules.PERCEPTION_RADIUS),
            villager -> villager.isAlive()
        );
        goblin.goblinCounters().entityVisits += Math.min(visited.size(), GoblinEnclaveRules.MAX_ENTITY_VISITS);
        visited.stream()
            .limit(GoblinEnclaveRules.MAX_ENTITY_VISITS)
            .filter(villager -> GoblinHostilityRules.isHumanVillager(villager.getType()))
            .filter(goblin::canAttack)
            .limit(GoblinEnclaveRules.MAX_ENTITY_RETAINED)
            .min(Comparator.comparingDouble(goblin::distanceToSqr))
            .ifPresent(villager -> {
                goblin.goblinCounters().entitiesRetained++;
                goblin.setTarget(villager);
            });
    }

    /**
     * One charged work survey per {@link GoblinEnclaveRules#WORK_SCAN_INTERVAL_TICKS}. Every branch
     * is bounded by its own read cap; a survey never reads an unloaded position.
     */
    private static WorkAvailability surveyWork(final GoblinEntity goblin, final ServerLevel level) {
        final Plan plan = goblin.goblinTransient().plan;
        if (goblin.isBaby()) {
            plan.flower = nearestFlower(goblin, level);
            return new WorkAvailability(false, false, false, false, false, false, false,
                plan.flower.isPresent(),
                GoblinEnclaveRules.canDance(sameEnclaveChildren(goblin, level)),
                GoblinEnclaveRules.giftReady(
                    goblin.getMainHandItem().is(net.minecraftforge.common.Tags.Items.FLOWERS),
                    goblin.goblinEnclaveState().childGiftCooldownTicks()
                ));
        }
        final boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        final boolean miner = goblin.goblinProfession() == GoblinProfession.MINER
            && goblin.getMainHandItem().is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS);
        plan.mine = griefing && miner ? nearestMineable(goblin, level) : Optional.empty();
        plan.deposit = carries(goblin, stack ->
            stack.is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            ? nearestDeposit(goblin, level) : Optional.empty();
        plan.log = griefing ? nearestNaturalLog(goblin, level) : Optional.empty();
        plan.bed = unclaimedBed(goblin, level);
        // The site survey owns its own 200-tick cadence and keeps the last valid site across the
        // 100-tick work surveys in between, so the executor always has a site to act on.
        if (griefing && GoblinEnclaveRules.isDue(goblin.goblinTransient().siteScanCooldownTicks)) {
            goblin.goblinTransient().siteScanCooldownTicks = GoblinEnclaveRules.SITE_SCAN_INTERVAL_TICKS;
            plan.hutSite = findHutSite(goblin, level);
            plan.tunnel = findTunnelEntrance(goblin, level);
        }
        return new WorkAvailability(
            plan.mine.isPresent(),
            plan.deposit.isPresent(),
            nearestLooseItem(goblin, level).isPresent(),
            plan.log.isPresent(),
            griefing && plan.hutSite.isPresent() && hutMaterials(goblin, level).isPresent(),
            griefing && plan.tunnel.isPresent(),
            familyReady(goblin, level),
            false, false, false
        );
    }

    // ================================================================ execution

    private static void execute(final GoblinEntity goblin, final ServerLevel level) {
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        // A claim-bearing intent may never execute without a live lease. Checking only when the
        // action still believes it holds one would let a lapsed Goblin keep mutating a worksite.
        if (requiresClaim(state.action().intent()) && !verifyClaim(goblin, level)) {
            cancelAction(goblin, level, "claim lost");
            return;
        }
        switch (state.action().intent()) {
            case SEEK_SHELTER -> executeShelter(goblin, level);
            case PATROL -> executePatrol(goblin, level);
            case GATHER_LOOSE -> executeGatherLoose(goblin, level);
            case GATHER_LOG -> executeGatherLog(goblin, level);
            case MINE -> executeMine(goblin, level);
            case DEPOSIT -> executeDeposit(goblin, level);
            case BUILD_HUT -> executeBuildHut(goblin, level);
            case DIG_TUNNEL -> executeDigTunnel(goblin, level);
            case FAMILY -> executeFamily(goblin, level);
            case CHILD_FLOWER -> executeChildFlower(goblin, level);
            case CHILD_DANCE -> executeChildDance(goblin, level);
            case CHILD_GIFT -> executeChildGift(goblin, level);
            case ALARM_WARD, ALARM_HARRY, ALARM_PRESS, ALARM_RESERVE -> executeAlarm(goblin, level);
            case ASSAULT -> executeAssault(goblin, level);
            case TRADE_HOLD -> holdForTrade(goblin);
            case IDLE -> {
            }
        }
    }

    /** Solitary Goblins hold no enclave, so only enclave-scoped work demands a live lease. */
    private static boolean requiresClaim(final Intent intent) {
        return intent.editsWorld() || intent == Intent.DEPOSIT || intent == Intent.FAMILY;
    }

    private static boolean verifyClaim(final GoblinEntity goblin, final ServerLevel level) {
        final Optional<Long> key = enclaveKey(goblin);
        if (key.isEmpty()) {
            return true;
        }
        final Optional<UUID> claim = goblin.goblinEnclaveState().action().claimId();
        return claim.isPresent()
            && GoblinEnclaveData.get(level).holdsClaim(key.orElseThrow(), claim.orElseThrow());
    }

    private static void executeShelter(final GoblinEntity goblin, final ServerLevel level) {
        if (goblin.goblinTransient().sheltered) {
            goblin.getNavigation().stop();
            return;
        }
        goblin.goblinEnclaveState().anchor().position()
            .or(() -> goblin.goblinEnclaveState().combat().shelter())
            .ifPresent(position -> requestNavigation(goblin, level, position, WORK_SPEED));
    }

    private static void executePatrol(final GoblinEntity goblin, final ServerLevel level) {
        goblin.goblinEnclaveState().anchor().position()
            .ifPresent(anchor -> requestNavigation(goblin, level, anchor, WORK_SPEED));
    }

    private static void executeGatherLoose(final GoblinEntity goblin, final ServerLevel level) {
        final Optional<ItemEntity> loose = nearestLooseItem(goblin, level);
        if (loose.isEmpty()) {
            cancelAction(goblin, level, "loose item lost");
            return;
        }
        final ItemEntity item = loose.orElseThrow();
        if (goblin.distanceToSqr(item) > PICKUP_SQUARED) {
            requestNavigation(goblin, level, item.blockPosition(), WORK_SPEED);
            return;
        }
        InventoryCarrier.pickUpItem(level, goblin, goblin, item);
        cancelAction(goblin, level, "gathered");
    }

    /**
     * One tagged natural log at a time, explicitly gated on {@code mobGriefing}, with the exact
     * prior state restored when the edit record cannot be settled.
     */
    private static void executeGatherLog(final GoblinEntity goblin, final ServerLevel level) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            cancelAction(goblin, level, "mobGriefing disabled");
            return;
        }
        final Optional<BlockPos> plan = goblin.goblinTransient().plan.log;
        if (plan.isEmpty()) {
            cancelAction(goblin, level, "no log");
            return;
        }
        final BlockPos position = plan.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(position)) > REACH_SQUARED) {
            requestNavigation(goblin, level, position, WORK_SPEED);
            return;
        }
        final Optional<Long> key = enclaveKey(goblin);
        if (key.isEmpty()) {
            cancelAction(goblin, level, "solitary");
            return;
        }
        // Revalidate the exact carried position immediately before the first mutation.
        if (!isHarvestableLog(goblin, level, position)) {
            goblin.goblinTransient().plan.log = Optional.empty();
            cancelAction(goblin, level, "log lost");
            return;
        }
        goblin.goblinCounters().plansAttempted++;
        final long enclave = key.orElseThrow();
        if (!GoblinEnclaveData.get(level).recordEdits(enclave, 1)) {
            cancelAction(goblin, level, "edit budget exhausted");
            return;
        }
        final BlockState original = level.getBlockState(position);
        final ItemStack log = new ItemStack(original.getBlock().asItem());
        final InventorySnapshot snapshot = InventorySnapshot.of(goblin);
        if (log.isEmpty() || !level.destroyBlock(position, false, goblin)) {
            rollback(goblin, level, Map.of(), snapshot,
                () -> GoblinEnclaveData.get(level).releaseEdits(enclave, 1));
            return;
        }
        goblin.goblinCounters().editsApplied++;
        final ItemStack remainder = goblin.getInventory().addItem(log);
        if (!remainder.isEmpty()) {
            Block.popResource(level, position, remainder);
        }
        goblin.goblinTransient().plan.log = Optional.empty();
        settle(goblin, level);
    }

    private static void executeMine(final GoblinEntity goblin, final ServerLevel level) {
        final ItemStack tool = goblin.getMainHandItem();
        if (goblin.goblinProfession() != GoblinProfession.MINER
            || !tool.is(WarlockeryTags.Items.HOBGOBLIN_MINING_TOOLS)
            || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            cancelAction(goblin, level, "no mining authority");
            return;
        }
        if (!GoblinEnclaveRules.isDue(goblin.goblinTransient().miningCooldownTicks)) {
            return;
        }
        final Optional<BlockPos> plan = goblin.goblinTransient().plan.mine;
        if (plan.isEmpty()) {
            cancelAction(goblin, level, "no ore");
            return;
        }
        final BlockPos position = plan.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(position)) > REACH_SQUARED) {
            requestNavigation(goblin, level, position, WORK_SPEED);
            return;
        }
        // Revalidate the exact carried position immediately before the first mutation.
        if (!isMineable(goblin, level, position, tool)) {
            goblin.goblinTransient().plan.mine = Optional.empty();
            cancelAction(goblin, level, "ore lost");
            return;
        }
        goblin.goblinCounters().plansAttempted++;
        final Optional<Long> key = enclaveKey(goblin);
        if (key.isPresent() && !GoblinEnclaveData.get(level).recordEdits(key.orElseThrow(), 1)) {
            cancelAction(goblin, level, "edit budget exhausted");
            return;
        }
        final HobgoblinMiningRules.MiningProfile profile = HobgoblinMiningRules.profile(
            tool.is(WarlockeryTags.Items.ENHANCED_HOBGOBLIN_MINING_TOOLS)
        );
        final BlockState original = level.getBlockState(position);
        final List<ItemStack> drops = Block.getDrops(
            original, level, position, level.getBlockEntity(position), goblin, tool
        );
        final InventorySnapshot snapshot = InventorySnapshot.of(goblin);
        if (!level.destroyBlock(position, false, goblin)) {
            rollback(goblin, level, Map.of(), snapshot, () -> key.ifPresent(enclave ->
                GoblinEnclaveData.get(level).releaseEdits(enclave, 1)));
            return;
        }
        goblin.goblinCounters().editsApplied++;
        goblin.swing(InteractionHand.MAIN_HAND);
        original.spawnAfterBreak(level, position, tool, false);
        drops.forEach(stack -> {
            final ItemStack remainder = goblin.getInventory().addItem(stack);
            if (!remainder.isEmpty()) {
                Block.popResource(level, position, remainder);
            }
        });
        goblin.goblinTransient().miningCooldownTicks = profile.cooldownTicks();
        goblin.goblinTransient().plan.mine = Optional.empty();
        settle(goblin, level);
    }

    /** Simulates the insertion before mutating, so a full container can never duplicate a stack. */
    private static void executeDeposit(final GoblinEntity goblin, final ServerLevel level) {
        final Optional<BlockPos> plan = goblin.goblinTransient().plan.deposit;
        if (plan.isEmpty()) {
            cancelAction(goblin, level, "no deposit");
            return;
        }
        final BlockPos position = plan.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(position)) > REACH_SQUARED) {
            requestNavigation(goblin, level, position, WORK_SPEED);
            return;
        }
        final Container container = HopperBlockEntity.getContainerAt(level, position);
        if (container == null) {
            cancelAction(goblin, level, "container lost");
            return;
        }
        goblin.goblinCounters().plansAttempted++;
        final int slot = IntStream.range(0, goblin.getInventory().getContainerSize())
            .filter(index -> goblin.getInventory().getItem(index)
                .is(CreatureBehaviorTags.Items.HOBGOBLIN_COLLECTIBLES))
            .findFirst()
            .orElse(-1);
        if (slot < 0) {
            cancelAction(goblin, level, "nothing to deposit");
            return;
        }
        final ItemStack source = goblin.getInventory().getItem(slot);
        final ItemStack reserved = source.copy();
        final ItemStack remainder = HopperBlockEntity.addItem(
            goblin.getInventory(), container, source.copy(), null
        );
        if (remainder.getCount() == reserved.getCount()) {
            cancelAction(goblin, level, "deposit rejected");
            return;
        }
        goblin.getInventory().setItem(slot, remainder);
        goblin.goblinTransient().plan.deposit = Optional.empty();
        settle(goblin, level);
    }

    /**
     * The complete eight-stage hut transaction. The 32-edit plan is derived without mutation, the
     * site and material lease are reserved, every mutable world guard is revalidated immediately
     * before commit, and any failure restores the exact prior block states and the exact reserved
     * inventory before the claim is released.
     */
    private static void executeBuildHut(final GoblinEntity goblin, final ServerLevel level) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            cancelAction(goblin, level, "mobGriefing disabled");
            return;
        }
        final Optional<Long> key = enclaveKey(goblin);
        // The site was surveyed on the site cadence and carried forward; the executor never rescans,
        // so the survey and the execution can no longer consume the same cooldown against each other.
        final Optional<BlockPos> site = goblin.goblinTransient().plan.hutSite;
        final Optional<HutMaterials> materials = hutMaterials(goblin, level);
        if (key.isEmpty() || site.isEmpty() || materials.isEmpty()) {
            cancelAction(goblin, level, "hut plan invalid");
            return;
        }
        final BlockPos center = site.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(center)) > REACH_SQUARED * 4.0D) {
            requestNavigation(goblin, level, center, WORK_SPEED);
            return;
        }
        goblin.goblinCounters().plansAttempted++;
        final GoblinEnclaveData data = GoblinEnclaveData.get(level);
        final long enclave = key.orElseThrow();
        if (!data.reserveHut(enclave, center)) {
            goblin.goblinTransient().plan.hutSite = Optional.empty();
            cancelAction(goblin, level, "hut cap reached");
            return;
        }
        // The reservation is now live in saved data, so every failure path below must un-reserve it
        // as well as restore the world; otherwise a rolled-back hut permanently burns a hut slot and
        // thirty-two edits of the lifetime budget for a hut that never existed.
        final Runnable unreserve = () -> data.releaseHut(enclave, center);
        final HutMaterials chosen = materials.orElseThrow();
        final InventorySnapshot snapshot = InventorySnapshot.of(goblin);
        reserveMaterials(goblin, GoblinEnclaveRules.HUT_DIRT_COST, GoblinEnclaveRules.HUT_LOG_COST,
            GoblinEnclaveRules.HUT_MIN_PLANKS);
        // Revalidate the mutable footprint immediately before the first mutation.
        if (!clearHutFootprint(goblin, level, center)) {
            goblin.goblinTransient().plan.hutSite = Optional.empty();
            rollback(goblin, level, Map.of(), snapshot, unreserve);
            return;
        }
        final Map<BlockPos, BlockState> journal = new LinkedHashMap<>();
        for (final BlockPlacement placement : hutPlacements(center, chosen)) {
            journal.putIfAbsent(placement.position(), level.getBlockState(placement.position()));
            if (!level.setBlockAndUpdate(placement.position(), placement.state())) {
                rollback(goblin, level, journal, snapshot, unreserve);
                return;
            }
            goblin.goblinCounters().editsApplied++;
        }
        consume(goblin, stack -> stack.is(chosen.dirtItem()), GoblinEnclaveRules.HUT_DIRT_COST);
        consume(goblin, stack -> stack.is(chosen.logItem()), GoblinEnclaveRules.HUT_LOG_COST);
        goblin.swing(InteractionHand.MAIN_HAND);
        goblin.goblinTransient().plan.hutSite = Optional.empty();
        settle(goblin, level);
    }

    private static void executeDigTunnel(final GoblinEntity goblin, final ServerLevel level) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            cancelAction(goblin, level, "mobGriefing disabled");
            return;
        }
        final Optional<Long> key = enclaveKey(goblin);
        final Optional<BlockPos> entrance = goblin.goblinTransient().plan.tunnel;
        if (key.isEmpty() || entrance.isEmpty()) {
            cancelAction(goblin, level, "tunnel plan invalid");
            return;
        }
        final BlockPos start = entrance.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(start)) > REACH_SQUARED * 4.0D) {
            requestNavigation(goblin, level, start, WORK_SPEED);
            return;
        }
        goblin.goblinCounters().plansAttempted++;
        final Direction direction = goblin.getDirection();
        final List<BlockPos> excavation = IntStream.range(0, 5)
            .mapToObj(step -> start.relative(direction, step).below(step / 2))
            .flatMap(base -> java.util.stream.Stream.of(base, base.above()))
            .map(BlockPos::immutable)
            .toList();
        if (excavation.stream().anyMatch(position -> !canExcavate(goblin, level, position))) {
            cancelAction(goblin, level, "tunnel footprint unsafe");
            return;
        }
        final List<BlockPos> solid = excavation.stream()
            .filter(position -> !level.getBlockState(position).isAir())
            .toList();
        if (!GoblinEnclaveRules.canReserveTunnel(0, 0, solid.size())) {
            cancelAction(goblin, level, "tunnel size outside bounds");
            return;
        }
        final GoblinEnclaveData data = GoblinEnclaveData.get(level);
        final long enclave = key.orElseThrow();
        final int edits = solid.size();
        if (!data.reserveTunnel(enclave, start, edits)) {
            goblin.goblinTransient().plan.tunnel = Optional.empty();
            cancelAction(goblin, level, "tunnel cap reached");
            return;
        }
        final Runnable unreserve = () -> data.releaseTunnel(enclave, start, edits);
        final InventorySnapshot snapshot = InventorySnapshot.of(goblin);
        final Map<BlockPos, BlockState> journal = new LinkedHashMap<>();
        for (final BlockPos position : solid) {
            journal.putIfAbsent(position, level.getBlockState(position));
            if (!level.destroyBlock(position, false, goblin)) {
                rollback(goblin, level, journal, snapshot, unreserve);
                return;
            }
            goblin.goblinCounters().editsApplied++;
        }
        goblin.swing(InteractionHand.MAIN_HAND);
        goblin.goblinTransient().plan.tunnel = Optional.empty();
        settle(goblin, level);
    }

    /**
     * Explicit conception. Both parents must be living loaded adult exact Goblins in the same
     * enclave, idle, fed, under the population cap, with one unclaimed bed and exactly one family
     * claim; nothing is inherited from a human Villager willingness system.
     */
    private static void executeFamily(final GoblinEntity goblin, final ServerLevel level) {
        final Optional<Long> key = enclaveKey(goblin);
        final Optional<GoblinEntity> partner = nearestPartner(goblin, level);
        if (key.isEmpty() || partner.isEmpty()) {
            cancelAction(goblin, level, "no partner");
            return;
        }
        final GoblinEntity other = partner.orElseThrow();
        if (goblin.distanceToSqr(other) > REACH_SQUARED) {
            requestNavigation(goblin, level, other.blockPosition(), WORK_SPEED);
            return;
        }
        final GoblinEnclaveData data = GoblinEnclaveData.get(level);
        if (!GoblinEnclaveRules.canConceive(
            true, true, true, data.population(key.orElseThrow()),
            goblin.goblinTransient().plan.bed,
            goblin.goblinEnclaveState().foodPoints(), other.goblinEnclaveState().foodPoints(), true
        )) {
            cancelAction(goblin, level, "family conditions unmet");
            return;
        }
        final net.minecraft.world.entity.AgeableMob child = goblin.getBreedOffspring(level, other);
        if (!(child instanceof GoblinEntity offspring)) {
            cancelAction(goblin, level, "offspring rejected");
            return;
        }
        offspring.setBaby(true);
        offspring.snapTo(goblin.getX(), goblin.getY(), goblin.getZ(), goblin.getYRot(), 0.0F);
        if (!level.addFreshEntity(offspring)) {
            cancelAction(goblin, level, "offspring not added");
            return;
        }
        spendFood(goblin, GoblinEnclaveRules.BREEDING_FOOD_COST);
        spendFood(other, GoblinEnclaveRules.BREEDING_FOOD_COST);
        data.joinEnclave(key.orElseThrow(), offspring.getUUID());
        settle(goblin, level);
    }

    private static void executeChildFlower(final GoblinEntity child, final ServerLevel level) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !child.getMainHandItem().isEmpty()) {
            cancelAction(child, level, "flower unavailable");
            return;
        }
        final Optional<BlockPos> plan = child.goblinTransient().plan.flower;
        if (plan.isEmpty()) {
            cancelAction(child, level, "no flower");
            return;
        }
        final BlockPos position = plan.orElseThrow();
        if (child.distanceToSqr(Vec3.atCenterOf(position)) > PICKUP_SQUARED) {
            requestNavigation(child, level, position, WORK_SPEED);
            return;
        }
        final BlockState state = level.getBlockState(position);
        final ItemStack flower = new ItemStack(state.getBlock().asItem());
        if (flower.isEmpty() || level.getBlockEntity(position) != null) {
            cancelAction(child, level, "flower invalid");
            return;
        }
        final Map<BlockPos, BlockState> journal = Map.of(position.immutable(), state);
        final InventorySnapshot snapshot = InventorySnapshot.of(child);
        if (!level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState())) {
            rollback(child, level, journal, snapshot, () -> {
            });
            return;
        }
        child.goblinCounters().editsApplied++;
        child.setItemSlot(EquipmentSlot.MAINHAND, flower);
        child.swing(InteractionHand.MAIN_HAND);
        child.goblinTransient().plan.flower = Optional.empty();
        settle(child, level);
    }

    private static void executeChildDance(final GoblinEntity child, final ServerLevel level) {
        final List<GoblinEntity> children = sameEnclaveChildList(child, level);
        if (!GoblinEnclaveRules.canDance(children.size())) {
            cancelAction(child, level, "dance dissolved");
            return;
        }
        final Vec3 center = children.stream()
            .map(Entity::position)
            .reduce(Vec3.ZERO, Vec3::add)
            .scale(1.0D / children.size());
        final int index = children.stream()
            .sorted(Comparator.comparing(Entity::getUUID))
            .toList()
            .indexOf(child);
        final double angle = child.tickCount * 0.08D + Math.PI * 2.0D * index / children.size();
        final Vec3 step = center.add(
            Math.cos(angle) * GoblinEnclaveRules.DANCE_RADIUS,
            0.0D,
            Math.sin(angle) * GoblinEnclaveRules.DANCE_RADIUS
        );
        requestNavigation(child, level, BlockPos.containing(step), WORK_SPEED);
    }

    private static void executeChildGift(final GoblinEntity child, final ServerLevel level) {
        final ItemStack flower = child.getMainHandItem();
        if (!GoblinEnclaveRules.giftReady(
            flower.is(net.minecraftforge.common.Tags.Items.FLOWERS),
            child.goblinEnclaveState().childGiftCooldownTicks()
        )) {
            cancelAction(child, level, "gift not ready");
            return;
        }
        final Optional<ServerPlayer> recipient = level.getEntitiesOfClass(
                ServerPlayer.class,
                child.getBoundingBox().inflate(GoblinEnclaveRules.CHILD_GIFT_RADIUS),
                player -> player.isAlive() && !player.isSpectator()
            ).stream()
            .min(Comparator.comparingDouble(child::distanceToSqr));
        if (recipient.isEmpty()) {
            cancelAction(child, level, "no recipient");
            return;
        }
        final ServerPlayer player = recipient.orElseThrow();
        if (child.distanceToSqr(player) > REACH_SQUARED) {
            requestNavigation(child, level, player.blockPosition(), WORK_SPEED);
            return;
        }
        final ItemStack gift = flower.copyWithCount(1);
        if (!player.addItem(gift)) {
            player.drop(gift, false);
        }
        child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        child.setGoblinEnclaveState(child.goblinEnclaveState()
            .withChildGiftCooldown(GoblinEnclaveRules.CHILD_GIFT_COOLDOWN_TICKS));
        enclaveKey(child).ifPresent(key -> GoblinEnclaveData.get(level)
            .recordRelation(key, player.getUUID(), RelationEvent.GIFT_RECEIVED));
        child.swing(InteractionHand.MAIN_HAND);
        settle(child, level);
    }

    /** Bounded body-level defender execution. No projectile, spell, summon, or combat block edit. */
    private static void executeAlarm(final GoblinEntity goblin, final ServerLevel level) {
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        final float fraction = goblin.getHealth() / Math.max(1.0F, goblin.getMaxHealth());
        final LivingEntity target = goblin.getTarget();
        final boolean inReach = target != null && goblin.isWithinMeleeAttackRange(target);
        final boolean retreat = GoblinEnclaveRules.shouldRetreat(
            state.combat().retreating(), fraction, inReach, state.combat().role() != CombatRole.NONE
        );
        if (retreat != state.combat().retreating()) {
            goblin.setGoblinEnclaveState(state.withCombat(new GoblinEnclaveState.Combat(
                state.combat().role(), state.combat().alarmEpoch(), retreat,
                state.combat().shelter(), state.combat().retreat()
            )));
        }
        if (retreat) {
            goblin.setTarget(null);
            state.combat().retreat()
                .or(() -> state.anchor().position())
                .ifPresent(position -> requestNavigation(goblin, level, position, URGENT_SPEED));
            return;
        }
        if (target == null
            || GoblinEnclaveRules.needsRelief(fraction, state.combat().role())) {
            // Relief: a wounded defender releases its lease so a reserve can replace it rather
            // than leaving the role occupied but ineffective.
            releaseRole(goblin, level);
            return;
        }
        switch (state.combat().role()) {
            case PRESS, HARRIER -> requestNavigation(goblin, level, target.blockPosition(), URGENT_SPEED);
            case WARDER, RESERVE -> state.anchor().position()
                .ifPresent(anchor -> requestNavigation(goblin, level, anchor, WORK_SPEED));
            case NONE -> releaseRole(goblin, level);
        }
    }

    private static void executeAssault(final GoblinEntity goblin, final ServerLevel level) {
        final Optional<BlockPos> center = goblin.assaultCenter();
        if (center.isEmpty()) {
            cancelAction(goblin, level, "assault ended");
            return;
        }
        final LivingEntity target = goblin.getTarget();
        if (target != null && target.isAlive()) {
            requestNavigation(goblin, level, target.blockPosition(), URGENT_SPEED);
            return;
        }
        requestNavigation(goblin, level, center.orElseThrow(), WORK_SPEED);
    }

    private static void holdForTrade(final GoblinEntity goblin) {
        goblin.getNavigation().stop();
        goblin.setTarget(null);
    }

    private static void releaseRole(final GoblinEntity goblin, final ServerLevel level) {
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        goblin.setGoblinEnclaveState(state.withCombat(GoblinEnclaveState.Combat.none()));
        cancelAction(goblin, level, "alarm ended");
    }

    // ================================================================ transaction helpers

    private static void settle(final GoblinEntity goblin, final ServerLevel level) {
        goblin.goblinCounters().transactionsCommitted++;
        clearReservation(goblin);
        cancelAction(goblin, level, "settled");
    }

    /**
     * Restores the exact prior block states in reverse application order, restores the exact
     * reserved inventory, un-reserves whatever the transaction already committed into saved data,
     * and only then releases the claim. If any of those three cannot be completed the package is a
     * correctness defect rather than something to silently compensate with drops.
     */
    private static void rollback(
        final GoblinEntity goblin,
        final ServerLevel level,
        final Map<BlockPos, BlockState> journal,
        final InventorySnapshot snapshot,
        final Runnable unreserve
    ) {
        final List<Map.Entry<BlockPos, BlockState>> ordered = new ArrayList<>(journal.entrySet());
        for (int index = ordered.size() - 1; index >= 0; index--) {
            level.setBlockAndUpdate(ordered.get(index).getKey(), ordered.get(index).getValue());
            goblin.goblinCounters().editsRestored++;
        }
        snapshot.restore(goblin);
        unreserve.run();
        clearReservation(goblin);
        goblin.goblinCounters().transactionsRolledBack++;
        cancelAction(goblin, level, "rolled back");
    }

    /**
     * The exact source inventory snapshot taken before any mutation in a transaction. Eight merchant
     * slots is a fixed, tiny cost and it is what makes the rollback contract literally true rather
     * than merely claimed.
     */
    private record InventorySnapshot(List<ItemStack> slots) {
        static InventorySnapshot of(final GoblinEntity goblin) {
            return new InventorySnapshot(IntStream.range(0, goblin.getInventory().getContainerSize())
                .mapToObj(slot -> goblin.getInventory().getItem(slot).copy())
                .toList());
        }

        void restore(final GoblinEntity goblin) {
            for (int slot = 0; slot < slots.size(); slot++) {
                goblin.getInventory().setItem(slot, slots.get(slot).copy());
            }
        }
    }

    /** Records the exact material reservation backing the current transaction in semantic state. */
    private static void reserveMaterials(
        final GoblinEntity goblin,
        final int dirt,
        final int logs,
        final int planks
    ) {
        final GoblinEnclaveState.Action action = goblin.goblinEnclaveState().action();
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState().withAction(
            new GoblinEnclaveState.Action(
                action.intent(), action.claimId(), action.targetId(), action.destination(),
                action.destinationDimension(), action.commitRemainingTicks(),
                action.recoveryRemainingTicks(), dirt, logs, planks
            )
        ));
    }

    private static void clearReservation(final GoblinEntity goblin) {
        final GoblinEnclaveState.Action action = goblin.goblinEnclaveState().action();
        if (action.reservedDirt() == 0 && action.reservedLogs() == 0 && action.reservedPlanks() == 0) {
            return;
        }
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState().withAction(
            new GoblinEnclaveState.Action(
                action.intent(), action.claimId(), action.targetId(), action.destination(),
                action.destinationDimension(), action.commitRemainingTicks(),
                action.recoveryRemainingTicks(), 0, 0, 0
            )
        ));
    }

    // ================================================================ navigation

    /**
     * The only navigation writer. Requests are rate limited to at most one per
     * {@link GoblinEnclaveRules#NAVIGATION_INTERVAL_TICKS}, destinations outside the world border
     * are refused, and three classified failures clear the destination and impose the declared
     * backoff before an expensive retry.
     */
    private static void requestNavigation(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = goblin.goblinTransient();
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (!GoblinEnclaveRules.isDue(state.cadence().retryRemainingTicks())) {
            return;
        }
        if (!GoblinEnclaveRules.isDue(scratch.navigationCooldownTicks)) {
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(destination)) {
            recordRouteFailure(goblin, RouteFailure.UNREACHABLE);
            return;
        }
        scratch.navigationCooldownTicks = GoblinEnclaveRules.NAVIGATION_INTERVAL_TICKS;
        goblin.goblinCounters().navigationRequests++;
        final boolean moving = goblin.getNavigation().moveTo(
            destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, speed
        );
        if (moving) {
            goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
                .withCadence(GoblinEnclaveState.Cadence.none()));
            return;
        }
        recordRouteFailure(goblin, RouteFailure.NO_PATH);
    }

    private static void recordRouteFailure(final GoblinEntity goblin, final RouteFailure failure) {
        goblin.goblinCounters().navigationFailures++;
        final GoblinEnclaveState.Cadence cadence = goblin.goblinEnclaveState().cadence();
        final int failures = GoblinEnclaveRules.nextRouteFailure(cadence.routeFailures(), failure);
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState().withCadence(
            new GoblinEnclaveState.Cadence(
                failure, failures, GoblinEnclaveRules.backoffTicks(failures),
                failure == RouteFailure.STUCK
            )
        ));
    }

    // ================================================================ reconciliation

    private static void reconcileEnclave(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        if (!GoblinEnclaveRules.isDue(scratch.memberCooldownTicks)) {
            return;
        }
        scratch.memberCooldownTicks = GoblinEnclaveRules.MEMBER_INTERVAL_TICKS;
        final GoblinEnclaveData data = GoblinEnclaveData.get(level);
        // The membership heartbeat runs first and unconditionally. Putting it behind the
        // neighbour-count precondition or the assault guard would let a live loaded resident lose
        // its seat, and then its claim, simply because its neighbours wandered out of range or an
        // assault was in progress.
        enclaveKey(goblin).ifPresent(existing -> data.joinEnclave(existing, goblin.getUUID()));
        if (goblin.isAssaultMember()) {
            return;
        }
        final long key = GoblinEnclaveRules.enclaveKey(
            goblin.blockPosition().getX(), goblin.blockPosition().getZ(), CreatureKind.GOBLIN
        );
        final List<GoblinEntity> neighbours = level.getEntitiesOfClass(
            GoblinEntity.class,
            goblin.getBoundingBox().inflate(GoblinEnclaveRules.MEMBER_RADIUS, 12.0D,
                GoblinEnclaveRules.MEMBER_RADIUS),
            candidate -> candidate.isAlive() && !candidate.isAssaultMember()
        );
        goblin.goblinCounters().memberVisits +=
            Math.min(neighbours.size(), GoblinEnclaveRules.MAX_MEMBER_VISITS);
        if (neighbours.size() < 2 || !data.joinEnclave(key, goblin.getUUID())) {
            return;
        }
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (state.anchor().enclaveKey().map(stored -> stored == key).orElse(false)) {
            return;
        }
        goblin.setGoblinEnclaveState(state.withAnchor(GoblinEnclaveState.Anchor.at(
            key, goblin.blockPosition(), dimensionOf(level)
        )));
    }

    /**
     * A patron who logs out, dies, or changes dimension expires only the active work preference.
     * The saved patron UUID deliberately survives, so returning restores finite loaded behavior.
     */
    private static void reconcilePatron(final GoblinEntity goblin, final ServerLevel level) {
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (state.patron().depositPreference().isEmpty()) {
            return;
        }
        final boolean patronPresent = state.patron().id()
            .map(level::getPlayerByUUID)
            .filter(Player::isAlive)
            .isPresent();
        if (!patronPresent) {
            goblin.setGoblinEnclaveState(state.withPatron(state.patron().expirePreference()));
        }
    }

    private static void reconcileMerchant(final GoblinEntity goblin, final ServerLevel level) {
        rollMerchantDay(goblin, level);
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        final boolean needsRestock = goblin.getOffers().stream().anyMatch(offer -> offer.needsRestock());
        if (!GoblinEnclaveRules.canRestock(
            state.merchant().restocksToday(), state.merchant().restockSpacingTicks(),
            needsRestock, safeToTrade(goblin)
        )) {
            return;
        }
        goblin.getOffers().forEach(offer -> offer.resetUses());
        goblin.setGoblinEnclaveState(state.withMerchant(state.merchant().afterRestock()));
    }

    /**
      * The daily restock quota reset that {@code Villager} used to perform for free.
      *
      * <p>The dedicated body is an {@code AbstractVillager}, and only {@code Villager} rolled
      * {@code numberOfRestocksToday} over at the start of each day. Without this branch
      * {@link GoblinEnclaveState.Merchant#onNewDay()} had no production caller at all and a merchant
      * that had restocked twice could never restock again for the rest of the world's life.</p>
      *
      * <p>The day index is seeded from the live clock on load, never zeroed, so a player cannot
      * relog to refresh the quota, and an unloaded merchant that missed several days collapses to
      * exactly one deterministic reset rather than a burst.</p>
      */
    private static void rollMerchantDay(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        final long today = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
        if (scratch.observedDay == UNSEEDED_DAY) {
            scratch.observedDay = today;
            return;
        }
        if (scratch.observedDay == today) {
            return;
        }
        scratch.observedDay = today;
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
            .withMerchant(goblin.goblinEnclaveState().merchant().onNewDay()));
    }

    // ================================================================ feedback

    /**
     * Server-authoritative, range-checked, and rate limited. Feedback never exposes a hidden
     * inventory, unloaded target, relation score, enclave key, claim id, or protected block state.
     */
    private static void emitFeedback(final GoblinEntity goblin, final ServerLevel level) {
        final TransientState scratch = goblin.goblinTransient();
        if (!GoblinEnclaveRules.isDue(scratch.feedbackCooldownTicks)) {
            return;
        }
        scratch.feedbackCooldownTicks = GoblinEnclaveRules.FEEDBACK_INTERVAL_TICKS;
        if (goblin.goblinEnclaveState().action().intent() == Intent.IDLE) {
            return;
        }
        goblin.goblinCounters().feedbackPulses++;
        goblin.playWorkSound();
    }

    // ================================================================ public body hooks

    /** Called from the merchant base before any trade may open or continue. */
    public static boolean safeToTrade(final GoblinEntity goblin) {
        return !goblin.isBaby()
            && !goblin.isAssaultMember()
            && goblin.getTarget() == null
            && !goblin.goblinTransient().hazardActive();
    }

    /** Called from {@code GoblinEntity.canAttack} for every eligibility question. */
    public static boolean canAttack(final GoblinEntity goblin, final LivingEntity target) {
        if (target == null || goblin.isBaby()) {
            return false;
        }
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (state.patron().id().map(patron -> patron.equals(target.getUUID())).orElse(false)) {
            return false;
        }
        // Every goblinfolk body is non-prey, including the dedicated F11 traveler. Naming only the
        // two 1.4-era classes here would quietly make Goblins hostile to exact Hobgoblins the
        // moment the exact species moves to its own dedicated body.
        if (target instanceof GoblinEntity
            || target instanceof HobgoblinEntity
            || target instanceof HobgoblinTravelerEntity) {
            return false;
        }
        final boolean sameOwner = target instanceof Player player
            && state.patron().id().map(patron -> patron.equals(player.getUUID())).orElse(false);
        final TargetClass classification = classify(goblin, target);
        return !sameOwner && GoblinEnclaveRules.canTarget(
            !goblin.isBaby(),
            classification,
            target.isAlive(),
            target.level() == goblin.level(),
            target.level().dimension().equals(goblin.level().dimension()),
            target instanceof Player player && (player.isCreative() || player.isSpectator()),
            target.isInvulnerable()
        );
    }

    private static TargetClass classify(final GoblinEntity goblin, final LivingEntity target) {
        if (GoblinHostilityRules.isHumanVillager(target.getType())) {
            return TargetClass.HUMAN_VILLAGER;
        }
        final LivingEntity attacker = goblin.getLastHurtByMob();
        if (attacker == target
            && GoblinEnclaveRules.isFreshAttribution(goblin.getLastHurtByMobTimestamp() >= 0
                ? goblin.tickCount - goblin.getLastHurtByMobTimestamp()
                : Integer.MAX_VALUE)) {
            return TargetClass.DIRECT_ATTACKER;
        }
        if (target instanceof Player) {
            return TargetClass.PLAYER;
        }
        return TargetClass.OTHER_FAMILY;
    }

    /** Called from {@code GoblinEntity.hurtServer} for every accepted damage event. */
    public static void onAcceptedDamage(
        final GoblinEntity goblin,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker instanceof Player player) {
            enclaveKey(goblin).ifPresent(key -> GoblinEnclaveData.get(level)
                .recordRelation(key, player.getUUID(), RelationEvent.DIRECT_ATTACK));
        }
        if (goblin.canAttack(attacker)) {
            goblin.setTarget(attacker);
            raiseAlarm(goblin, level, attacker);
        }
        // Direct damage closes an open trade so self-defense may begin.
        if (goblin.isTrading()) {
            goblin.setTradingPlayer(null);
        }
    }

    /**
     * One alarm, at most four threat facts, at most four loaded same-enclave adult responders within
     * the declared radius, depth one, and never rebroadcast.
     */
    private static void raiseAlarm(
        final GoblinEntity goblin,
        final ServerLevel level,
        final LivingEntity threat
    ) {
        final Optional<Long> key = enclaveKey(goblin);
        if (key.isEmpty() || !GoblinEnclaveRules.canRelayAlarm(0)) {
            return;
        }
        final GoblinEnclaveData data = GoblinEnclaveData.get(level);
        data.rememberThreat(key.orElseThrow(), threat.getUUID(), 2);
        final List<GoblinEntity> responders = level.getEntitiesOfClass(
                GoblinEntity.class,
                goblin.getBoundingBox().inflate(GoblinEnclaveRules.ALARM_RECRUIT_RADIUS),
                candidate -> candidate.isAlive() && !candidate.isBaby()
                    && enclaveKey(candidate).equals(key)
            ).stream()
            .limit(GoblinEnclaveRules.recruitCap(GoblinEnclaveRules.MAX_DEFENDERS))
            .toList();
        final long epoch = level.getGameTime();
        GoblinEnclaveRules.assignRoles(responders.stream()
            .map(responder -> new Responder(
                responder.getUUID(), responder.goblinProfession(), 2,
                responder.distanceToSqr(threat)
            ))
            .toList()
        ).forEach(assignment -> responders.stream()
            .filter(responder -> responder.getUUID().equals(assignment.id()))
            .findFirst()
            .ifPresent(responder -> {
                final GoblinEnclaveState state = responder.goblinEnclaveState();
                responder.setGoblinEnclaveState(state.withCombat(new GoblinEnclaveState.Combat(
                    assignment.role(), epoch, false,
                    state.combat().shelter().or(() -> state.anchor().position()),
                    state.anchor().position()
                )));
                if (assignment.role() != CombatRole.NONE && responder.canAttack(threat)) {
                    responder.setTarget(threat);
                }
            }));
    }

    /** Called from {@code GoblinEntity.mobInteract} the moment a contract binds a patron. */
    public static void onContractAccepted(final GoblinEntity goblin, final Player player) {
        if (!(goblin.level() instanceof ServerLevel level)) {
            return;
        }
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (state.patron().bound()) {
            return;
        }
        goblin.setGoblinEnclaveState(state.withPatron(GoblinEnclaveState.Patron.bound(player.getUUID())));
        enclaveKey(goblin).ifPresent(key -> GoblinEnclaveData.get(level)
            .recordRelation(key, player.getUUID(), RelationEvent.CONTRACT_ACCEPTED));
        goblin.setTarget(null);
    }

    /** Called from {@code GoblinEntity.mobInteract} once a trading screen has actually opened. */
    public static void onTradeOpened(final GoblinEntity goblin) {
        goblin.getNavigation().stop();
        goblin.setTarget(null);
        if (goblin.level() instanceof ServerLevel level && goblin.getTradingPlayer() != null) {
            final UUID customer = goblin.getTradingPlayer().getUUID();
            enclaveKey(goblin).ifPresent(key -> GoblinEnclaveData.get(level)
                .recordRelation(key, customer, RelationEvent.TRADE_COMPLETED));
        }
    }

    /** Called from {@code GoblinEntity.mobInteract} when a player supplies a tagged mining tool. */
    public static InteractionResult equipMiningTool(
        final GoblinEntity goblin,
        final ServerLevel level,
        final Player player,
        final ItemStack supplied
    ) {
        final ItemStack previous = goblin.getMainHandItem().copy();
        final ItemStack equipped = supplied.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            supplied.shrink(1);
        }
        goblin.equipToolSlot(equipped);
        goblin.goblinTransient().miningCooldownTicks = 0;
        goblin.swing(InteractionHand.MAIN_HAND);
        if (!previous.isEmpty()) {
            goblin.spawnAtLocation(level, previous);
        }
        return InteractionResult.SUCCESS;
    }

    /** Called from {@code GoblinEntity.joinVillageAssault}; suspends every enclave-initiated job. */
    public static void onAssaultJoined(final GoblinEntity goblin) {
        if (!(goblin.level() instanceof ServerLevel level)) {
            return;
        }
        final GoblinEnclaveState state = goblin.goblinEnclaveState();
        if (GoblinEnclaveRules.assaultSuspends(state.action().intent())) {
            cancelAction(goblin, level, "assault marked");
        }
        enclaveKey(goblin).ifPresent(key ->
            GoblinEnclaveData.get(level).releaseClaimsOf(key, goblin.getUUID()));
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
            .withCombat(GoblinEnclaveState.Combat.none()));
    }

    /** Called from {@code GoblinEntity.leaveVillageAssault}; releases target, role, and claims. */
    public static void onAssaultLeft(final GoblinEntity goblin) {
        if (!(goblin.level() instanceof ServerLevel level)) {
            return;
        }
        goblin.setTarget(null);
        enclaveKey(goblin).ifPresent(key ->
            GoblinEnclaveData.get(level).releaseClaimsOf(key, goblin.getUUID()));
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
            .releaseAction().withCombat(GoblinEnclaveState.Combat.none()));
    }

    /**
     * Bounded local population count used by the natural-spawn predicate. It queries one loaded AABB
     * and never touches an unloaded chunk or a dimension-wide entity list.
     */
    public static int countLoadedGoblinsNear(final ServerLevel level, final BlockPos position) {
        return level.getEntitiesOfClass(
            GoblinEntity.class,
            new AABB(position).inflate(GoblinEnclaveRules.LOCAL_SPAWN_CAP_RADIUS),
            GoblinEntity::isAlive
        ).size();
    }

    public static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static Optional<Long> enclaveKey(final GoblinEntity goblin) {
        return goblin.goblinEnclaveState().anchor().enclaveKey();
    }

    // ================================================================ bounded scans

    private static Optional<ItemEntity> nearestLooseItem(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        final List<ItemEntity> visited = level.getEntitiesOfClass(
            ItemEntity.class,
            goblin.getBoundingBox().inflate(GoblinEnclaveRules.LOOSE_ITEM_RADIUS),
            item -> item.isAlive() && goblin.wantsToPickUp(level, item.getItem())
        );
        goblin.goblinCounters().looseVisits += Math.min(visited.size(), GoblinEnclaveRules.MAX_LOOSE_VISITS);
        return visited.stream()
            .limit(GoblinEnclaveRules.MAX_LOOSE_RETAINED)
            .min(Comparator.comparingDouble(goblin::distanceToSqr));
    }

    private static Optional<BlockPos> nearestMineable(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        final ItemStack tool = goblin.getMainHandItem();
        return chargedScan(goblin, level, ScanClass.MINING, 5, 2,
            GoblinEnclaveRules.MAX_MINING_BLOCK_READS,
            position -> isMineable(goblin, level, position, tool));
    }

    /** The single mineable predicate, shared by the survey and by the pre-commit revalidation. */
    private static boolean isMineable(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos position,
        final ItemStack tool
    ) {
        final BlockState state = level.getBlockState(position);
        return GoblinEnclaveRules.canEditBlock(
            true,
            level.getWorldBorder().isWithinBounds(position),
            level.getGameRules().get(GameRules.MOB_GRIEFING),
            state.is(WarlockeryTags.Blocks.HOBGOBLIN_MINEABLES),
            !level.getFluidState(position).isEmpty(),
            level.getBlockEntity(position) != null,
            state.getDestroySpeed(level, position)
        ) && tool.isCorrectToolForDrops(state);
    }

    private static Optional<BlockPos> nearestNaturalLog(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        return chargedScan(goblin, level, ScanClass.LOG, 6, 4,
            GoblinEnclaveRules.MAX_WORK_BLOCK_READS,
            position -> isHarvestableLog(goblin, level, position));
    }

    /** The single log predicate, shared by the survey and by the pre-commit revalidation. */
    private static boolean isHarvestableLog(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos position
    ) {
        final BlockState state = level.getBlockState(position);
        return (state.is(net.minecraftforge.common.Tags.Blocks.NATURAL_LOGS)
            || state.is(BlockTags.LOGS))
            && level.getWorldBorder().isWithinBounds(position)
            && level.getBlockEntity(position) == null
            && level.getFluidState(position).isEmpty()
            && state.getDestroySpeed(level, position) >= 0.0F;
    }

    private static Optional<BlockPos> nearestDeposit(final GoblinEntity goblin, final ServerLevel level) {
        return chargedScan(goblin, level, ScanClass.DEPOSIT, 6, 3,
            GoblinEnclaveRules.MAX_WORK_BLOCK_READS, position ->
                level.getBlockState(position).is(CreatureBehaviorTags.Blocks.HOBGOBLIN_DEPOSIT_CONTAINERS)
                    && HopperBlockEntity.getContainerAt(level, position) != null);
    }

    private static Optional<BlockPos> nearestFlower(final GoblinEntity child, final ServerLevel level) {
        return chargedScan(child, level, ScanClass.FLOWER, 4, 1,
            GoblinEnclaveRules.MAX_CHILD_BLOCK_READS, position ->
                isGatherableFlower(level.getBlockState(position))
                    && level.getBlockEntity(position) == null);
    }

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

    /**
     * The one charged block-scan primitive.
     *
     * <p>Every read cap is far below its own box volume (mining 128 of 605, log 128 of 1,521, deposit
     * 128 of 1,183, bed 256 of 2,601, flower 64 of 243), so a naive raster would spend the entire
     * budget on one corner of the envelope and never reach the entity's own Y level or the opposite
     * quadrant. Instead the envelope is enumerated centre-out and split in two:</p>
     *
     * <ul>
     *   <li>a <em>near anchor</em> of the first {@code readCap / 2} offsets, which always includes
     *       {@code (0,0,0)} and the entity's own level, evaluated on every single scan; and</li>
     *   <li>a <em>rotating page</em> of the remaining budget over the tail, whose per-class cursor
     *       advances by the page size on every scan and wraps, so the whole far envelope including
     *       the opposite quadrant is evaluated within {@code ceil(tail / page)} scans.</li>
     * </ul>
     *
     * <p>Worked case, mining ({@code h=5, v=2}, volume 605, cap 128): anchor 64 offsets covering
     * every cell within radius ~2.4 of the entity including its own level, then a 64-wide page over
     * the 541-offset tail, so the far {@code (+5,+2,+5)} corner is reached within nine scans and the
     * near envelope is never skipped. Bed ({@code h=8, v=4}, volume 2,601, cap 256): anchor 128,
     * page 128 over a 2,473-offset tail, full coverage within twenty scans.</p>
     *
     * <p>Positions outside the world border are skipped without being charged, and every actual
     * charged read is counted.</p>
     */
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

    private static Optional<BlockPos> chargedScan(
        final GoblinEntity goblin,
        final ServerLevel level,
        final ScanClass scanClass,
        final int horizontal,
        final int vertical,
        final int readCap,
        final Predicate<BlockPos> accepts
    ) {
        final BlockPos origin = goblin.blockPosition();
        final List<BlockPos> offsets = envelope(horizontal, vertical);
        final int[] cursors = goblin.goblinTransient().scanCursors;
        final int tail = offsets.size() - anchorSize(offsets.size(), readCap);
        if (cursors[scanClass.ordinal()] == UNSEEDED_CURSOR) {
            cursors[scanClass.ordinal()] = tail == 0
                ? 0
                : GoblinEnclaveRules.stableOffset(goblin.getUUID(), tail);
        }
        final List<BlockPos> hits = new ArrayList<>();
        int reads = 0;
        for (final BlockPos offset : scanWindow(offsets, readCap, cursors[scanClass.ordinal()])) {
            final BlockPos candidate = origin.offset(offset);
            if (!level.getWorldBorder().isWithinBounds(candidate)) {
                continue;
            }
            reads++;
            if (accepts.test(candidate)) {
                hits.add(candidate.immutable());
            }
        }
        if (tail > 0) {
            cursors[scanClass.ordinal()] = Math.floorMod(
                cursors[scanClass.ordinal()] + pageSize(offsets.size(), readCap), tail
            );
        }
        goblin.goblinCounters().chargedBlockReads += reads;
        return hits.stream().min(Comparator.comparingDouble(
            position -> goblin.distanceToSqr(Vec3.atCenterOf(position))
        ));
    }

    // ================================================================ family helpers

    private static Optional<GoblinEntity> nearestPartner(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        final Optional<Long> key = enclaveKey(goblin);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return level.getEntitiesOfClass(
                GoblinEntity.class,
                goblin.getBoundingBox().inflate(GoblinEnclaveRules.MEMBER_RADIUS),
                candidate -> candidate != goblin && candidate.isAlive() && !candidate.isBaby()
                    && !candidate.isTrading() && !candidate.isAssaultMember()
                    && enclaveKey(candidate).equals(key)
                    && candidate.goblinEnclaveState().foodPoints()
                        >= GoblinEnclaveRules.BREEDING_FOOD_COST
            ).stream()
            .min(Comparator.comparing(Entity::getUUID));
    }

    private static boolean familyReady(final GoblinEntity goblin, final ServerLevel level) {
        return goblin.goblinEnclaveState().foodPoints() >= GoblinEnclaveRules.BREEDING_FOOD_COST
            && enclaveKey(goblin)
                .map(key -> GoblinEnclaveData.get(level).population(key) < GoblinEnclaveRules.MAX_MEMBERS)
                .orElse(false)
            && nearestPartner(goblin, level).isPresent()
            && goblin.goblinTransient().plan.bed;
    }

    private static boolean unclaimedBed(final GoblinEntity goblin, final ServerLevel level) {
        return chargedScan(goblin, level, ScanClass.BED, 8, 4,
            GoblinEnclaveRules.MAX_SITE_BLOCK_READS, position -> {
                final BlockState state = level.getBlockState(position);
                return state.getBlock() instanceof BedBlock
                    && state.getValue(BedBlock.PART) == BedPart.HEAD
                    && level.getBlockState(position.above())
                        .getCollisionShape(level, position.above()).isEmpty();
            }).isPresent();
    }

    private static void spendFood(final GoblinEntity goblin, final int amount) {
        goblin.setGoblinEnclaveState(goblin.goblinEnclaveState()
            .withFoodPoints(goblin.goblinEnclaveState().foodPoints() - amount));
    }

    private static int sameEnclaveChildren(final GoblinEntity child, final ServerLevel level) {
        return sameEnclaveChildList(child, level).size();
    }

    private static List<GoblinEntity> sameEnclaveChildList(
        final GoblinEntity child,
        final ServerLevel level
    ) {
        final Optional<Long> key = enclaveKey(child);
        return level.getEntitiesOfClass(
            GoblinEntity.class,
            child.getBoundingBox().inflate(8.0D, 3.0D, 8.0D),
            candidate -> candidate.isAlive() && candidate.isBaby() && enclaveKey(candidate).equals(key)
        ).stream().limit(GoblinEnclaveRules.MAX_DANCE_PARTICIPANTS).toList();
    }

    private static boolean isGatherableFlower(final BlockState state) {
        return (state.is(BlockTags.SMALL_FLOWERS)
            || state.is(net.minecraftforge.common.Tags.Blocks.FLOWERS))
            && !(state.getBlock() instanceof DoublePlantBlock);
    }

    // ================================================================ construction helpers

    /**
     * Site discovery. The cooldown is owned by {@code surveyWork}, never by this method, so the
     * survey and the executor can no longer consume the same cadence against each other.
     */
    private static Optional<BlockPos> findHutSite(final GoblinEntity goblin, final ServerLevel level) {
        return HUT_OFFSETS.stream()
            .map(offset -> surface(goblin, level, goblin.blockPosition().offset(offset)))
            .flatMap(Optional::stream)
            .filter(position -> !humanVillagerNearby(level, position))
            .filter(position -> clearHutFootprint(goblin, level, position))
            .findFirst();
    }

    private static Optional<BlockPos> findTunnelEntrance(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        return List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST).stream()
            .map(direction -> surface(goblin, level, goblin.blockPosition().relative(direction, 8)))
            .flatMap(Optional::stream)
            .map(BlockPos::below)
            .filter(position -> !humanVillagerNearby(level, position))
            .filter(position -> canExcavate(goblin, level, position))
            .findFirst();
    }

    private static boolean clearHutFootprint(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos center
    ) {
        int reads = 0;
        boolean clear = true;
        for (final BlockPos position : BlockPos.betweenClosedStream(
            center.offset(-1, 0, -2), center.offset(1, 2, 1)
        ).map(BlockPos::immutable).toList()) {
            reads++;
            clear &= level.getWorldBorder().isWithinBounds(position)
                && level.getBlockEntity(position) == null
                && level.getFluidState(position).isEmpty()
                && level.getBlockState(position).canBeReplaced();
        }
        for (final BlockPos position : BlockPos.betweenClosedStream(
            center.offset(-1, -1, -2), center.offset(1, -1, 1)
        ).map(BlockPos::immutable).toList()) {
            reads++;
            clear &= level.getFluidState(position).isEmpty()
                && level.getBlockState(position).isFaceSturdy(level, position, Direction.UP);
        }
        // Count the reads actually performed. Clamping to the cap would under-report the real
        // cost, which is precisely the number the budget assertions exist to police.
        goblin.goblinCounters().chargedBlockReads += reads;
        return clear;
    }

    /** The exact retained 32-edit plan: 18 dirt walls, 12 plank roof blocks, and one brown bed. */
    private static List<BlockPlacement> hutPlacements(final BlockPos center, final HutMaterials chosen) {
        final List<BlockPlacement> placements = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            final int height = y;
            BlockPos.betweenClosedStream(center.offset(-1, height, -2), center.offset(1, height, 1))
                .filter(position -> Math.abs(position.getX() - center.getX()) == 1
                    || position.getZ() == center.getZ() - 2
                    || position.getZ() == center.getZ() + 1)
                .filter(position -> !(position.getX() == center.getX()
                    && position.getZ() == center.getZ() + 1))
                .forEach(position -> placements.add(
                    new BlockPlacement(position.immutable(), chosen.dirt().defaultBlockState())
                ));
        }
        BlockPos.betweenClosedStream(center.offset(-1, 2, -2), center.offset(1, 2, 1))
            .forEach(position -> placements.add(
                new BlockPlacement(position.immutable(), chosen.planks().defaultBlockState())
            ));
        final BlockState foot = Blocks.BED.brown().defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.NORTH);
        placements.add(new BlockPlacement(center.immutable(), foot));
        placements.add(new BlockPlacement(center.north().immutable(),
            foot.setValue(BedBlock.PART, BedPart.HEAD)));
        return List.copyOf(placements);
    }

    private static boolean canExcavate(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos position
    ) {
        goblin.goblinCounters().chargedBlockReads++;
        final BlockState state = level.getBlockState(position);
        return level.getWorldBorder().isWithinBounds(position)
            && level.getBlockEntity(position) == null
            && level.getFluidState(position).isEmpty()
            && (state.isAir()
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT)
                || state.is(net.minecraftforge.common.Tags.Blocks.STONES)
                || state.is(net.minecraftforge.common.Tags.Blocks.COBBLESTONES)
                || state.is(net.minecraftforge.common.Tags.Blocks.GRAVELS))
            && state.getDestroySpeed(level, position) >= 0.0F;
    }

    private static Optional<HutMaterials> hutMaterials(
        final GoblinEntity goblin,
        final ServerLevel level
    ) {
        final Optional<ItemStack> dirt = inventoryStacks(goblin)
            .filter(stack -> stack.is(ItemTags.DIRT))
            .filter(stack -> stack.getItem() instanceof net.minecraft.world.item.BlockItem)
            .filter(stack -> count(goblin, candidate -> candidate.is(stack.getItem()))
                >= GoblinEnclaveRules.HUT_DIRT_COST)
            .findFirst();
        final Optional<ItemStack> log = inventoryStacks(goblin)
            .filter(stack -> stack.is(net.minecraftforge.common.Tags.Items.NATURAL_LOGS)
                || stack.is(ItemTags.LOGS))
            .filter(stack -> count(goblin, candidate -> candidate.is(stack.getItem()))
                >= GoblinEnclaveRules.HUT_LOG_COST)
            .findFirst();
        if (dirt.isEmpty() || log.isEmpty()) {
            return Optional.empty();
        }
        final Optional<ItemStack> planks = plankRecipe(level, log.orElseThrow());
        if (planks.isEmpty()
            || !(dirt.orElseThrow().getItem() instanceof net.minecraft.world.item.BlockItem dirtBlock)
            || !(planks.orElseThrow().getItem() instanceof net.minecraft.world.item.BlockItem plankBlock)) {
            return Optional.empty();
        }
        if (!GoblinEnclaveRules.canAffordHut(
            count(goblin, stack -> stack.is(dirt.orElseThrow().getItem())),
            count(goblin, stack -> stack.is(log.orElseThrow().getItem())),
            0
        ) || planks.orElseThrow().getCount() * GoblinEnclaveRules.HUT_LOG_COST
            < GoblinEnclaveRules.HUT_MIN_PLANKS) {
            return Optional.empty();
        }
        return Optional.of(new HutMaterials(
            dirt.orElseThrow().getItem(), log.orElseThrow().getItem(),
            dirtBlock.getBlock(), plankBlock.getBlock()
        ));
    }

    private static Optional<ItemStack> plankRecipe(final ServerLevel level, final ItemStack log) {
        final CraftingInput input = CraftingInput.of(1, 1, List.of(log.copyWithCount(1)));
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty() && result.is(ItemTags.PLANKS));
    }

    private static java.util.stream.Stream<ItemStack> inventoryStacks(final GoblinEntity goblin) {
        return IntStream.range(0, goblin.getInventory().getContainerSize())
            .mapToObj(goblin.getInventory()::getItem)
            .filter(stack -> !stack.isEmpty());
    }

    private static boolean carries(final GoblinEntity goblin, final Predicate<ItemStack> predicate) {
        return inventoryStacks(goblin).anyMatch(predicate);
    }

    private static int count(final GoblinEntity goblin, final Predicate<ItemStack> predicate) {
        return inventoryStacks(goblin).filter(predicate).mapToInt(ItemStack::getCount).sum();
    }

    private static void consume(
        final GoblinEntity goblin,
        final Predicate<ItemStack> predicate,
        final int amount
    ) {
        int remaining = amount;
        for (int slot = 0; slot < goblin.getInventory().getContainerSize() && remaining > 0; slot++) {
            final ItemStack stack = goblin.getInventory().getItem(slot);
            if (!predicate.test(stack)) {
                continue;
            }
            final int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
        }
    }

    private static boolean humanVillagerNearby(final ServerLevel level, final BlockPos position) {
        return !level.getEntitiesOfClass(
            Villager.class,
            new AABB(position).inflate(GoblinEnclaveRules.MEMBER_RADIUS, 8.0D,
                GoblinEnclaveRules.MEMBER_RADIUS),
            villager -> GoblinHostilityRules.isHumanVillager(villager.getType())
        ).isEmpty();
    }

    /**
     * Bounded, non-generating surface probe. The previous implementation called
     * {@code level.getHeight(Heightmap.Types, x, z)}, which resolves through {@code getChunk} and
     * generates the column to FULL when it is absent - exactly the chunk-loading accessor this class
     * promises never to call. This walks nine already-loaded positions around the entity's own Y
     * instead and simply declines the candidate when the column is not loaded.
     */
    private static Optional<BlockPos> surface(
        final GoblinEntity goblin,
        final ServerLevel level,
        final BlockPos position
    ) {
        if (!level.isLoaded(position)) {
            return Optional.empty();
        }
        final int origin = goblin.blockPosition().getY();
        for (int y = origin + 4; y >= origin - 4; y--) {
            final BlockPos candidate = new BlockPos(position.getX(), y, position.getZ());
            if (!level.isLoaded(candidate)) {
                continue;
            }
            goblin.goblinCounters().chargedBlockReads++;
            if (level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)
                && level.getBlockState(candidate).canBeReplaced()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private record HutMaterials(
        net.minecraft.world.item.Item dirtItem,
        net.minecraft.world.item.Item logItem,
        Block dirt,
        Block planks
    ) {
    }

    private record BlockPlacement(BlockPos position, BlockState state) {
    }
}
