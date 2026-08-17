package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HexBatRules.AbsoluteFacts;
import com.kadamitas.warlockery.entity.HexBatRules.Action;
import com.kadamitas.warlockery.entity.HexBatRules.DestinationPurpose;
import com.kadamitas.warlockery.entity.HexBatRules.Mode;
import com.kadamitas.warlockery.entity.HexBatRules.ProactiveFacts;
import com.kadamitas.warlockery.entity.HexBatRules.Provenance;
import com.kadamitas.warlockery.entity.HexBatRules.TargetCandidate;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-only orchestration for the dedicated Hex Bat. This is the single
 * movement and target authority; every expensive family runs on a bounded
 * cadence, every scan is charged against an explicit budget, and structural
 * counters record the exact work performed.
 */
public final class HexBatRuntime {
    private HexBatRuntime() {
    }

    public static final class Counters {
        long targetScans;
        long targetCandidatesVisited;
        long targetCandidatesRetained;
        long losClips;
        long peerVisits;
        long callRecipients;
        long roostSearches;
        long roostCandidates;
        long roostBlockReads;
        long hazardReads;
        long destinationCandidates;
        long destinationBlockReads;
        long navigationRequests;
        long navigationAccepts;
        long navigationFailures;
        long backoffs;
        long actionsBegun;
        long actionsCancelled;
        long actionsTimedOut;
        long contactAttempts;
        long contactsAccepted;
        long callsAttempted;
        long callsAccepted;
        long callsDeduped;
        long effectsApplied;
        long genericRuntimeDispatches;

        public long targetScans() { return targetScans; }
        public long targetCandidatesVisited() { return targetCandidatesVisited; }
        public long targetCandidatesRetained() { return targetCandidatesRetained; }
        public long losClips() { return losClips; }
        public long peerVisits() { return peerVisits; }
        public long callRecipients() { return callRecipients; }
        public long roostSearches() { return roostSearches; }
        public long roostCandidates() { return roostCandidates; }
        public long roostBlockReads() { return roostBlockReads; }
        public long hazardReads() { return hazardReads; }
        public long destinationCandidates() { return destinationCandidates; }
        public long destinationBlockReads() { return destinationBlockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationAccepts() { return navigationAccepts; }
        public long navigationFailures() { return navigationFailures; }
        public long backoffs() { return backoffs; }
        public long actionsBegun() { return actionsBegun; }
        public long actionsCancelled() { return actionsCancelled; }
        public long actionsTimedOut() { return actionsTimedOut; }
        public long contactAttempts() { return contactAttempts; }
        public long contactsAccepted() { return contactsAccepted; }
        public long callsAttempted() { return callsAttempted; }
        public long callsAccepted() { return callsAccepted; }
        public long callsDeduped() { return callsDeduped; }
        public long effectsApplied() { return effectsApplied; }
        public long genericRuntimeDispatches() { return genericRuntimeDispatches; }

        public void reset() {
            targetScans = 0; targetCandidatesVisited = 0; targetCandidatesRetained = 0; losClips = 0;
            peerVisits = 0; callRecipients = 0; roostSearches = 0; roostCandidates = 0; roostBlockReads = 0;
            hazardReads = 0; destinationCandidates = 0; destinationBlockReads = 0; navigationRequests = 0;
            navigationAccepts = 0; navigationFailures = 0; backoffs = 0; actionsBegun = 0;
            actionsCancelled = 0; actionsTimedOut = 0; contactAttempts = 0; contactsAccepted = 0;
            callsAttempted = 0; callsAccepted = 0; callsDeduped = 0; effectsApplied = 0;
            genericRuntimeDispatches = 0;
        }
    }

    // ---- main dispatch ----

    public static void tick(final HexBatEntity bat, final ServerLevel level) {
        if (!bat.isAlive() || bat.isRemoved()) {
            return;
        }
        final long now = level.getGameTime();
        HexBatState state = bat.batState();
        state = reconcileAnchor(bat, level, state);
        state = observeHazards(bat, level, state, now);
        final boolean hazard = state.mode() == Mode.HAZARD;
        final boolean lowHealth = HexBatRules.lowHealth(bat.getHealth(), bat.getMaxHealth());
        if (hazard) {
            bat.setBatState(tickHazard(bat, level, state, now));
            return;
        }
        if (lowHealth || (state.mode() == Mode.WITHDRAW && state.deadlines().withdrawUntil() > now)) {
            bat.setBatState(tickWithdraw(bat, level, state, now, lowHealth));
            return;
        }
        if (state.mode() == Mode.WITHDRAW) {
            // Withdrawal completed: revalidate schedule, never reacquire a stale target.
            state = state.withMode(Mode.SHELTER);
        }
        if (state.action() == Action.SWOOP) {
            bat.setBatState(tickSwoop(bat, level, state, now));
            return;
        }
        state = expireThreat(state, now);
        state = tickSchedule(bat, level, state, now);
        state = tickTargetAcquisition(bat, level, state, now);
        state = tickCalls(bat, level, state, now);
        bat.setBatState(state);
    }

    // ---- anchor and dimension lifecycle ----

    static HexBatState reconcileAnchor(final HexBatEntity bat, final ServerLevel level, final HexBatState input) {
        HexBatState state = input;
        final String dimension = level.dimension().identifier().toString();
        if (state.anchorDimension().isPresent()
            && !state.anchorDimension().orElseThrow().equals(dimension)) {
            bat.getNavigation().stop();
            bat.setSwooping(false);
            bat.setRoosting(false);
            state = state.clearedForDimensionChange();
        }
        if (state.anchor().isEmpty()) {
            state = state.withAnchor(Optional.of(bat.blockPosition()), Optional.of(dimension));
        }
        return state;
    }

    static HexBatState expireThreat(final HexBatState state, final long now) {
        if (state.threatId().isPresent()
            && HexBatRules.reportExpired(state.threatExpiresAt(), now)) {
            return state.withThreat(Optional.empty(), Optional.empty(), 0L, 0);
        }
        return state;
    }

    // ---- hazards ----

    static HexBatState observeHazards(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        if (!HexBatRules.scanDue(state.cadence().nextHazardScanAt(), now)) {
            return state;
        }
        state = state.withCadence(withHazardScan(state.cadence(),
            HexBatRules.saturatingAdd(now, HexBatRules.HAZARD_SCAN_INTERVAL_TICKS)));
        boolean hazard = bat.isInLava() || bat.isOnFire()
            || (bat.isUnderWater() && bat.getAirSupply() < bat.getMaxAirSupply() / 2);
        if (!hazard) {
            final BlockPos origin = bat.blockPosition();
            int reads = 0;
            outer:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (reads >= HexBatRules.MAX_HAZARD_BLOCK_READS) break outer;
                        reads++;
                        bat.batCounters().hazardReads++;
                        final BlockPos pos = origin.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos)) continue;
                        if (contactHazardous(level.getBlockState(pos))) {
                            hazard = true;
                            break outer;
                        }
                    }
                }
            }
        }
        if (hazard) {
            return state.withMode(Mode.HAZARD);
        }
        return state.mode() == Mode.HAZARD ? state.withMode(Mode.SHELTER) : state;
    }

    private static HexBatState tickHazard(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = cancelUnexecutedAction(bat, input, now);
        bat.setRoosting(false);
        // The expensive destination search runs only when the navigation gate
        // could actually consume its result; otherwise it would be discarded.
        if (bat.getNavigation().isInProgress()
            || state.deadlines().routeBackoffUntil() > now
            || !HexBatRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
            return state;
        }
        final Optional<BlockPos> escape = findAerialDestination(
            bat, level, bat.blockPosition(), HexBatRules.WITHDRAW_ESCAPE_RANGE, true
        );
        if (escape.isPresent()) {
            state = state.withDestination(escape, DestinationPurpose.ESCAPE);
            state = requestNavigation(bat, state, escape.orElseThrow(), now, 1.2D);
        }
        return state;
    }

    private static HexBatState tickWithdraw(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input,
        final long now, final boolean lowHealth
    ) {
        HexBatState state = input;
        if (state.mode() != Mode.WITHDRAW && lowHealth) {
            state = cancelUnexecutedAction(bat, state, now);
            bat.setTarget(null);
            state = state.withThreat(Optional.empty(), Optional.empty(), 0L, 0)
                .withMode(Mode.WITHDRAW)
                .withDeadlines(withWithdraw(state.deadlines(),
                    HexBatRules.saturatingAdd(now, HexBatRules.WITHDRAW_TICKS)));
        }
        // The expensive fallback search runs only when the navigation gate
        // could actually consume its result; otherwise it would be discarded.
        if (bat.getNavigation().isInProgress()
            || state.deadlines().routeBackoffUntil() > now
            || !HexBatRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
            return state;
        }
        final HexBatState withdrawSnapshot = state;
        final Optional<BlockPos> refuge = state.roost()
            .filter(pos -> withdrawSnapshot.roostDimension()
                .map(level.dimension().identifier().toString()::equals).orElse(false))
            .or(() -> findAerialDestination(
                bat, level, bat.blockPosition(), HexBatRules.WITHDRAW_ESCAPE_RANGE, true));
        if (refuge.isPresent()) {
            state = state.withDestination(refuge, DestinationPurpose.WITHDRAW);
            state = requestNavigation(bat, state, refuge.orElseThrow(), now, 1.1D);
        }
        return state;
    }

    // ---- schedule, roost, sortie ----

    static HexBatState tickSchedule(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        final boolean night = HexBatRules.isNight(level.getDefaultClockTime());
        final String dimension = level.dimension().identifier().toString();
        if (night) {
            if (bat.isRoosting()) {
                bat.setRoosting(false);
                state = playBoundedSound(bat, level, state, SoundEvents.BAT_TAKEOFF, now);
                state = state.withMode(Mode.SORTIE).withDeadlines(withSortie(state.deadlines(),
                    HexBatRules.saturatingAdd(now, HexBatRules.SORTIE_MAX_TICKS)));
            } else if (state.mode() == Mode.SHELTER) {
                state = state.withMode(Mode.SORTIE).withDeadlines(withSortie(state.deadlines(),
                    HexBatRules.saturatingAdd(now, HexBatRules.SORTIE_MAX_TICKS)));
            }
            if (bat.getTarget() == null) {
                state = tickPatrolOrReturn(bat, level, state, now);
            }
            return state;
        }
        // Day: quiet bats shelter at a valid loaded roost.
        if (state.mode() == Mode.SORTIE) {
            state = state.withMode(Mode.SHELTER);
        }
        if (bat.getTarget() != null) {
            bat.setRoosting(false);
            return state;
        }
        final HexBatState scheduleSnapshot = state;
        final Optional<BlockPos> roost = state.roost()
            .filter(pos -> scheduleSnapshot.roostDimension().map(dimension::equals).orElse(false));
        if (roost.isPresent()) {
            final BlockPos pos = roost.orElseThrow();
            // Support re-validation is a due loaded-only scan on the roost
            // cadence; the stored semantic result is trusted between scans.
            boolean stillValid = true;
            if (HexBatRules.scanDue(state.cadence().nextRoostSearchAt(), now)) {
                state = state.withCadence(withRoostSearch(state.cadence(),
                    HexBatRules.saturatingAdd(now, HexBatRules.ROOST_SEARCH_INTERVAL_TICKS)));
                stillValid = validRoostNow(bat, level, pos, state);
            }
            if (stillValid) {
                if (bat.blockPosition().equals(pos)
                    || bat.position().distanceToSqr(Vec3.atCenterOf(pos)) <= 1.0D) {
                    if (!bat.isRoosting()) {
                        bat.getNavigation().stop();
                        bat.setDeltaMovement(Vec3.ZERO);
                        bat.setRoosting(true);
                    }
                    return state;
                }
                bat.setRoosting(false);
                state = state.withDestination(Optional.of(pos), DestinationPurpose.ROOST);
                return requestNavigation(bat, state, pos, now, 1.0D);
            }
            // Support destroyed, occupied, or unloaded: release without a success cooldown.
            bat.setRoosting(false);
            state = state.withRoost(Optional.empty(), Optional.empty());
        }
        if (HexBatRules.scanDue(state.cadence().nextRoostSearchAt(), now)) {
            state = state.withCadence(withRoostSearch(state.cadence(),
                HexBatRules.saturatingAdd(now, HexBatRules.ROOST_SEARCH_INTERVAL_TICKS)));
            final Optional<BlockPos> candidate = searchRoost(bat, level, state, now);
            if (candidate.isPresent()) {
                state = state.withRoost(candidate, Optional.of(dimension))
                    .withDestination(candidate, DestinationPurpose.ROOST);
                state = requestNavigation(bat, state, candidate.orElseThrow(), now, 1.0D);
            }
        }
        return state;
    }

    private static HexBatState tickPatrolOrReturn(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        final Optional<BlockPos> anchor = state.anchor();
        if (anchor.isEmpty()) return state;
        if (state.deadlines().sortieUntil() > now) {
            if (!bat.getNavigation().isInProgress()
                && HexBatRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
                final Optional<BlockPos> patrol = findAerialDestination(
                    bat, level, anchor.orElseThrow(), HexBatRules.SORTIE_RANGE, true
                );
                if (patrol.isPresent()) {
                    state = state.withDestination(patrol, DestinationPurpose.PATROL);
                    state = requestNavigation(bat, state, patrol.orElseThrow(), now, 1.0D);
                }
            }
            return state;
        }
        final BlockPos home = state.roost().orElse(anchor.orElseThrow());
        if (!bat.blockPosition().closerThan(home, 2.0D)) {
            state = state.withDestination(Optional.of(home), DestinationPurpose.PATROL);
            state = requestNavigation(bat, state, home, now, 1.0D);
        }
        return state;
    }

    // ---- roost search ----

    static boolean validRoostNow(
        final HexBatEntity bat, final ServerLevel level, final BlockPos pos, final HexBatState state
    ) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above())) return false;
        bat.batCounters().roostBlockReads += 2;
        final boolean supportTagged = level.getBlockState(pos.above()).is(WarlockeryTags.Blocks.HEX_BAT_ROOSTS);
        final boolean airSafe = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getFluidState(pos).isEmpty();
        final boolean unoccupied = level.getEntitiesOfClass(
            LivingEntity.class, new AABB(pos), candidate -> candidate != bat
        ).isEmpty();
        final Optional<BlockPos> anchor = state.anchor();
        final boolean withinEnvelope = anchor.map(a -> HexBatRules.withinAnchorEnvelope(
            pos.getX() - a.getX(), pos.getY() - a.getY(), pos.getZ() - a.getZ()
        )).orElse(false);
        return HexBatRules.validRoost(new HexBatRules.RoostFacts(
            supportTagged, airSafe, true, airSafe,
            level.getWorldBorder().isWithinBounds(pos),
            true, withinEnvelope, unoccupied
        ));
    }

    /**
     * Redesigned coverage: each due search spends 36 candidates on a dense
     * ceiling-first sweep of the near columns (which finds ordinary geometry
     * such as a support at anchor offset +1/+1/0 in the very first search)
     * and 12 candidates on one deterministic rotating page of the complete
     * anchor envelope, so every envelope position is eventually inspected.
     * No offset repeats inside one search and the anchor column is never
     * wastefully re-checked ring by ring.
     */
    static Optional<BlockPos> searchRoost(
        final HexBatEntity bat, final ServerLevel level, final HexBatState state, final long now
    ) {
        final Optional<BlockPos> anchorOptional = state.anchor();
        if (anchorOptional.isEmpty()) return Optional.empty();
        final BlockPos anchor = anchorOptional.orElseThrow();
        bat.batCounters().roostSearches++;
        final int[] reads = {0};
        // Phase A: dense near sweep, every search.
        for (int index = 0; index < HexBatRules.ROOST_NEAR_SWEEP_CANDIDATES; index++) {
            if (reads[0] + 2 > HexBatRules.MAX_ROOST_BLOCK_READS) return Optional.empty();
            final HexBatRules.RoostOffset offset = HexBatRules.roostNearSweepOffset(index);
            final Optional<BlockPos> found = probeRoostCandidate(
                bat, level, anchor.offset(offset.dx(), offset.dy(), offset.dz()), reads
            );
            if (found.isPresent()) return found;
        }
        // Phase B: one rotating page over the full envelope for eventual coverage.
        final int page = HexBatRules.roostPageIndex(now, bat.getUUID());
        final int start = page * HexBatRules.ROOST_PAGE_CANDIDATES;
        final int end = Math.min(
            start + HexBatRules.ROOST_PAGE_CANDIDATES, HexBatRules.roostEnvelopeSize()
        );
        for (int index = start; index < end; index++) {
            if (reads[0] + 2 > HexBatRules.MAX_ROOST_BLOCK_READS) return Optional.empty();
            final HexBatRules.RoostOffset offset = HexBatRules.roostEnvelopeOffset(index);
            if (HexBatRules.inRoostNearSweep(offset.dx(), offset.dy(), offset.dz())) continue;
            final Optional<BlockPos> found = probeRoostCandidate(
                bat, level, anchor.offset(offset.dx(), offset.dy(), offset.dz()), reads
            );
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> probeRoostCandidate(
        final HexBatEntity bat, final ServerLevel level, final BlockPos pos, final int[] reads
    ) {
        bat.batCounters().roostCandidates++;
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above())) return Optional.empty();
        reads[0] += 2;
        bat.batCounters().roostBlockReads += 2;
        if (!level.getBlockState(pos.above()).is(WarlockeryTags.Blocks.HEX_BAT_ROOSTS)) return Optional.empty();
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return Optional.empty();
        if (!level.getFluidState(pos).isEmpty()) return Optional.empty();
        if (!level.getWorldBorder().isWithinBounds(pos)) return Optional.empty();
        if (!level.getEntitiesOfClass(
            LivingEntity.class, new AABB(pos), candidate -> candidate != bat
        ).isEmpty()) return Optional.empty();
        return Optional.of(pos.immutable());
    }

    // ---- target acquisition ----

    static HexBatState tickTargetAcquisition(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        if (!HexBatRules.scanDue(state.cadence().nextTargetScanAt(), now)) {
            return state;
        }
        state = state.withCadence(withTargetScan(state.cadence(),
            HexBatRules.saturatingAdd(now, HexBatRules.TARGET_SCAN_INTERVAL_TICKS)));
        bat.batCounters().targetScans++;

        // Revalidate and possibly release the current target first, through
        // the complete pure release contract including the 80-tick unseen rule.
        int losBudget = HexBatRules.MAX_LINE_OF_SIGHT_CLIPS;
        final LivingEntity current = bat.getTarget();
        if (current != null) {
            losBudget--;
            bat.batCounters().losClips++;
            if (bat.getSensing().hasLineOfSight(current)) {
                bat.noteTargetSeen(current.getUUID(), now);
            }
            if (HexBatRules.shouldRelease(new HexBatRules.ReleaseFacts(
                !current.isAlive() || current.isRemoved() || current.isInvulnerable(),
                current instanceof Player player && (player.isCreative() || player.isSpectator()),
                current.level() != bat.level(),
                bat.distanceToSqr(current) > (double) HexBatRules.CHASE_RANGE * HexBatRules.CHASE_RANGE,
                HexBatRules.unseenTooLong(bat.targetLastSeenAt(current.getUUID()), now),
                ownerProtected(bat, current),
                false
            )) || absolutelyExcluded(bat, current)) {
                bat.setTarget(null);
                bat.getNavigation().stop();
            }
        }

        final List<TargetCandidate> preseeded = new ArrayList<>();

        // 1. Direct current attacker within attribution freshness.
        final LivingEntity attacker = bat.getLastHurtByMob();
        if (attacker != null
            && bat.getLastHurtByMobTimestamp() + HexBatRules.ATTRIBUTION_FRESHNESS_TICKS >= bat.tickCount
            && !absolutelyExcluded(bat, attacker)
            && bat.distanceToSqr(attacker) <= (double) HexBatRules.CHASE_RANGE * HexBatRules.CHASE_RANGE) {
            preseeded.add(new TargetCandidate(attacker.getUUID(),
                TargetCandidate.RANK_DIRECT_ATTACKER, bat.distanceToSqr(attacker)));
        }
        // 2. Explicit flock target or accepted report, LOS-validated before response.
        if (state.threatId().isPresent()
            && state.threatDimension().map(level.dimension().identifier().toString()::equals).orElse(false)) {
            final Entity reported = level.getEntity(state.threatId().orElseThrow());
            if (reported instanceof LivingEntity living && !absolutelyExcluded(bat, living)
                && bat.distanceToSqr(living) <= (double) HexBatRules.CHASE_RANGE * HexBatRules.CHASE_RANGE
                && losBudget > 0) {
                losBudget--;
                bat.batCounters().losClips++;
                if (bat.getSensing().hasLineOfSight(living)) {
                    preseeded.add(new TargetCandidate(living.getUUID(),
                        TargetCandidate.RANK_EXPLICIT_FLOCK, bat.distanceToSqr(living)));
                }
            }
        }
        // 3. Owner's current direct attacker while the owner is loaded nearby.
        final Optional<UUID> owner = CreatureBehaviorState.owner(bat);
        if (owner.isPresent()) {
            final Player ownerPlayer = level.getPlayerByUUID(owner.orElseThrow());
            if (ownerPlayer != null && ownerPlayer.isAlive()
                && bat.distanceToSqr(ownerPlayer)
                    <= (double) HexBatRules.OWNER_DEFENSE_RANGE * HexBatRules.OWNER_DEFENSE_RANGE) {
                final LivingEntity ownerAttacker = ownerPlayer.getLastHurtByMob();
                if (ownerAttacker != null
                    && ownerPlayer.getLastHurtByMobTimestamp() + HexBatRules.ATTRIBUTION_FRESHNESS_TICKS
                        >= ownerPlayer.tickCount
                    && !absolutelyExcluded(bat, ownerAttacker)
                    && bat.distanceToSqr(ownerAttacker)
                        <= (double) HexBatRules.OWNER_DEFENSE_RANGE * HexBatRules.OWNER_DEFENSE_RANGE
                    && losBudget > 0) {
                    losBudget--;
                    bat.batCounters().losClips++;
                    if (bat.getSensing().hasLineOfSight(ownerAttacker)) {
                        preseeded.add(new TargetCandidate(ownerAttacker.getUUID(),
                            TargetCandidate.RANK_OWNER_ATTACKER, bat.distanceToSqr(ownerAttacker)));
                    }
                }
            }
        }
        // 4. Stable current target.
        final LivingEntity stable = bat.getTarget();
        if (stable != null && !absolutelyExcluded(bat, stable)) {
            preseeded.add(new TargetCandidate(stable.getUUID(),
                TargetCandidate.RANK_STABLE_CURRENT, bat.distanceToSqr(stable)));
        }

        // Generic bounded traversal.
        final List<TargetCandidate> generic = new ArrayList<>();
        final boolean sortie = state.mode() == Mode.SORTIE;
        final boolean nearRoost = state.roost()
            .map(pos -> bat.blockPosition().closerThan(pos, HexBatRules.ROOST_GUARD_RANGE))
            .orElse(false);
        if (sortie || nearRoost || !preseeded.isEmpty()) {
            final List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                bat.getBoundingBox().inflate(HexBatRules.TARGET_QUERY_RADIUS),
                candidate -> candidate != bat && candidate.isAlive()
            );
            int visited = 0;
            for (final LivingEntity candidate : nearby) {
                if (visited >= HexBatRules.MAX_TARGET_VISITS) break;
                visited++;
                bat.batCounters().targetCandidatesVisited++;
                if (absolutelyExcluded(bat, candidate)) continue;
                final double distanceSqr = bat.distanceToSqr(candidate);
                final boolean marked = candidate.hasEffect(MobEffects.UNLUCK);
                final int rank;
                if (marked) {
                    rank = TargetCandidate.RANK_MARKED;
                } else if (candidate instanceof Player player && !player.isCreative() && !player.isSpectator()
                    && (sortie || nearRoost)) {
                    rank = TargetCandidate.RANK_SURVIVAL_PLAYER;
                } else if (candidate instanceof Enemy && sortie) {
                    rank = TargetCandidate.RANK_ORDINARY_HOSTILE;
                } else {
                    continue;
                }
                boolean lineOfSight = false;
                if (losBudget > 0) {
                    losBudget--;
                    bat.batCounters().losClips++;
                    lineOfSight = bat.getSensing().hasLineOfSight(candidate);
                }
                if (HexBatRules.proactivelyExcluded(proactiveFacts(candidate, distanceSqr, lineOfSight))) {
                    continue;
                }
                generic.add(new TargetCandidate(candidate.getUUID(), rank, distanceSqr));
            }
        }
        final List<TargetCandidate> retained = HexBatRules.retainCandidates(preseeded, generic);
        bat.batCounters().targetCandidatesRetained += retained.size();
        final Optional<TargetCandidate> selected = HexBatRules.selectTarget(retained);
        if (selected.isEmpty()) {
            return state;
        }
        final Entity resolved = level.getEntity(selected.orElseThrow().id());
        if (!(resolved instanceof LivingEntity target) || absolutelyExcluded(bat, target)) {
            return state;
        }
        bat.setTarget(target);
        // Acquisition is the sighting baseline for the 80-tick unseen release.
        bat.noteTargetSeen(target.getUUID(), now);
        return maybeBeginSwoop(bat, level, state, target, now);
    }

    /** True when the target is the owner or shares the bat's owner. */
    static boolean ownerProtected(final HexBatEntity bat, final LivingEntity target) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(bat);
        if (owner.isEmpty()) return false;
        if (owner.orElseThrow().equals(target.getUUID())) return true;
        return CreatureBehaviorState.owner(target)
            .map(owner.orElseThrow()::equals)
            .orElse(false);
    }

    // ---- swoop ----

    static HexBatState maybeBeginSwoop(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input,
        final LivingEntity target, final long now
    ) {
        HexBatState state = input;
        final double distanceSqr = bat.distanceToSqr(target);
        final boolean inRange = distanceSqr
            <= (double) HexBatRules.PROACTIVE_ACQUIRE_RANGE * HexBatRules.PROACTIVE_ACQUIRE_RANGE;
        final boolean directAttacker = bat.getLastHurtByMob() == target;
        // Both the bat and the target must remain inside the anchor envelope.
        final boolean withinEnvelope = state.anchor()
            .map(anchor -> target.blockPosition().closerThan(anchor, HexBatRules.CHASE_RANGE)
                && bat.blockPosition().closerThan(anchor, HexBatRules.CHASE_RANGE))
            .orElse(true);
        bat.batCounters().losClips++;
        final boolean visible = bat.getSensing().hasLineOfSight(target);
        if (visible) {
            bat.noteTargetSeen(target.getUUID(), now);
        }
        if (!HexBatRules.mayBeginSwoop(new HexBatRules.SwoopStartFacts(
            inRange && visible,
            withinEnvelope || directAttacker,
            state.mode() != Mode.HAZARD,
            state.mode() != Mode.WITHDRAW,
            state.deadlines().swoopCooldownUntil() <= now
                && state.deadlines().actionRecoverUntil() <= now,
            state.action() == Action.NONE
        ))) {
            return state;
        }
        bat.batCounters().actionsBegun++;
        bat.setSwooping(true);
        bat.setRoosting(false);
        state = state
            .withMode(Mode.INTERCEPT)
            .withAction(Action.SWOOP, Optional.of(target.getUUID()),
                Optional.of(level.dimension().identifier().toString()))
            .withDeadlines(withActionWindows(state.deadlines(),
                HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_WINDUP_TICKS),
                HexBatRules.saturatingAdd(now,
                    HexBatRules.SWOOP_WINDUP_TICKS + HexBatRules.SWOOP_EXECUTE_TICKS)));
        state = playBoundedSound(bat, level, state, SoundEvents.VEX_CHARGE, now);
        emitParticles(level, bat.position(), ParticleTypes.ENCHANT, HexBatRules.MAX_TELEGRAPH_PARTICLES);
        return state;
    }

    static HexBatState tickSwoop(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        final Optional<UUID> boundId = state.actionTargetId();
        final String dimension = level.dimension().identifier().toString();
        final Entity resolved = boundId.map(level::getEntity).orElse(null);
        final LivingEntity target = resolved instanceof LivingEntity living ? living : null;
        final boolean dimensionMismatch = state.actionTargetDimension()
            .map(bound -> !bound.equals(dimension)).orElse(true);
        final boolean invalid = target == null || absolutelyExcluded(bat, target);
        final boolean outOfEnvelope = target != null
            && bat.distanceToSqr(target) > (double) HexBatRules.CHASE_RANGE * HexBatRules.CHASE_RANGE;
        // One bounded LOS clip per active-action tick maintains the sighting
        // evidence behind the 80-tick unseen release.
        boolean visible = false;
        if (target != null && !invalid) {
            bat.batCounters().losClips++;
            visible = bat.getSensing().hasLineOfSight(target);
            if (visible) {
                bat.noteTargetSeen(target.getUUID(), now);
            }
        }
        final long actionStartedAt = Math.max(1L,
            state.deadlines().actionWindupUntil() - HexBatRules.SWOOP_WINDUP_TICKS);
        final long sightingBaseline = target == null ? 0L
            : Math.max(bat.targetLastSeenAt(target.getUUID()), actionStartedAt);
        if (HexBatRules.swoopCancelled(new HexBatRules.SwoopCancelFacts(
            invalid,
            outOfEnvelope || HexBatRules.unseenTooLong(sightingBaseline, now),
            target != null && ownerProtected(bat, target),
            HexBatRules.lowHealth(bat.getHealth(), bat.getMaxHealth()),
            dimensionMismatch
        ))) {
            bat.batCounters().actionsCancelled++;
            return cancelUnexecutedAction(bat, state, now);
        }
        if (now < state.deadlines().actionWindupUntil()) {
            // Windup: observations may update, but the frozen identity may not.
            return state;
        }
        if (now > state.deadlines().actionExecuteUntil()) {
            bat.batCounters().actionsTimedOut++;
            return cancelUnexecutedAction(bat, state, now);
        }
        // Execution: collision-aware approach through the common gate.
        state = requestNavigation(bat, state, target.blockPosition(), now, 1.2D);
        final double reachSqr = (double) (bat.getBbWidth() + target.getBbWidth() + 0.6F)
            * (bat.getBbWidth() + target.getBbWidth() + 0.6F);
        if (bat.distanceToSqr(target) <= reachSqr && visible) {
            bat.batCounters().contactAttempts++;
            bat.setTarget(target);
            final boolean accepted = bat.doHurtTarget(level, target);
            if (!accepted) {
                // Rejected damage: no effect, call, or success feedback.
                bat.batCounters().actionsCancelled++;
                return cancelUnexecutedAction(bat, state, now);
            }
            // Accepted contact bookkeeping happened in onContactAccepted.
            return bat.batState();
        }
        return state;
    }

    /** Cancel to recovery: no damage, effect, call, or rebind may follow. */
    static HexBatState cancelUnexecutedAction(final HexBatEntity bat, final HexBatState input, final long now) {
        HexBatState state = input;
        if (state.action() == Action.NONE) {
            return state;
        }
        bat.setSwooping(false);
        bat.getNavigation().stop();
        bat.setTarget(null);
        return state
            .withAction(Action.NONE, Optional.empty(), Optional.empty())
            .withMode(state.mode() == Mode.INTERCEPT ? Mode.SHELTER : state.mode())
            .withDestination(Optional.empty(), DestinationPurpose.NONE)
            .withDeadlines(withRecovery(state.deadlines(),
                HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_RECOVERY_TICKS),
                HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_RECOVERY_TICKS)));
    }

    /**
     * Accepted attributed contact from the bound action: applies the
     * Warlockery-original UNLUCK I jinx for exactly 200 ticks, may emit one
     * bounded one-hop THREAT call, and clears the immutable action identity
     * so the hit cannot replay.
     */
    public static void onContactAccepted(final HexBatEntity bat, final ServerLevel level, final Entity target) {
        final HexBatState state = bat.batState();
        final long now = level.getGameTime();
        if (state.action() != Action.SWOOP
            || !(target instanceof LivingEntity living)
            || state.actionTargetId().map(id -> !id.equals(target.getUUID())).orElse(true)
            || now < state.deadlines().actionWindupUntil()
            || now > state.deadlines().actionExecuteUntil()) {
            return;
        }
        living.addEffect(new MobEffectInstance(
            MobEffects.UNLUCK, HexBatRules.JINX_DURATION_TICKS, HexBatRules.JINX_AMPLIFIER
        ), bat);
        bat.batCounters().effectsApplied++;
        bat.batCounters().contactsAccepted++;
        emitParticles(level, living.position(), ParticleTypes.ENCHANT, HexBatRules.MAX_CONTACT_PARTICLES);
        // Rebuild from the CURRENT state, not the entry snapshot: the damage
        // pipeline (for example thorns routed through recordDirectAttack) may
        // have written new evidence while the hit resolved.
        final HexBatState fresh = bat.batState();
        HexBatState next = fresh
            .withAction(Action.NONE, Optional.empty(), Optional.empty())
            .withMode(Mode.WITHDRAW)
            .withDestination(Optional.empty(), DestinationPurpose.NONE)
            .withDeadlines(new HexBatState.Deadlines(
                0L, 0L,
                HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_RECOVERY_TICKS),
                HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_RECOVERY_TICKS),
                HexBatRules.saturatingAdd(now, HexBatRules.POST_CONTACT_WITHDRAW_TICKS),
                fresh.deadlines().routeBackoffUntil(),
                fresh.deadlines().callDedupeUntil(),
                fresh.deadlines().sortieUntil()
            ));
        bat.setSwooping(false);
        bat.setTarget(null);
        bat.getNavigation().stop();
        next = emitThreatCall(bat, level, next, living.getUUID(), now);
        bat.setBatState(next);
    }

    // ---- direct attack evidence ----

    public static void recordDirectAttack(final HexBatEntity bat, final ServerLevel level, final DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)
            || attacker == bat || !attacker.isAlive() || attacker.level() != level
            || absolutelyExcluded(bat, attacker)) {
            return;
        }
        final long now = level.getGameTime();
        bat.setBatState(bat.batState().withThreat(
            Optional.of(attacker.getUUID()),
            Optional.of(level.dimension().identifier().toString()),
            HexBatRules.saturatingAdd(now, HexBatRules.CALL_EXPIRY_TICKS),
            0
        ));
    }

    // ---- exact-species calls ----

    static HexBatState tickCalls(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input, final long now
    ) {
        HexBatState state = input;
        if (!HexBatRules.scanDue(state.cadence().nextPeerScanAt(), now)) {
            return state;
        }
        state = state.withCadence(withPeerScan(state.cadence(),
            HexBatRules.saturatingAdd(now, HexBatRules.CALL_SCAN_INTERVAL_TICKS)));
        // ROOST invitation: a quiet roosting bat invites compatible roostless peers.
        if (bat.isRoosting() && state.roost().isPresent()
            && state.deadlines().callDedupeUntil() <= now) {
            final Optional<UUID> callerOwner = CreatureBehaviorState.owner(bat);
            final List<HexBatEntity> peers = level.getEntitiesOfClass(
                HexBatEntity.class,
                bat.getBoundingBox().inflate(HexBatRules.CALL_RADIUS),
                peer -> peer != bat && peer.isAlive()
            );
            int visited = 0;
            int accepted = 0;
            for (final HexBatEntity peer : peers) {
                if (visited >= HexBatRules.MAX_PEER_VISITS
                    || accepted >= HexBatRules.MAX_CALL_RECIPIENTS) break;
                visited++;
                bat.batCounters().peerVisits++;
                if (!HexBatRules.callCompatible(callerOwner, CreatureBehaviorState.owner(peer))) continue;
                final HexBatState peerState = peer.batState();
                if (peerState.roost().isPresent()) continue;
                // Strict priority: an invitation is quiet-mode work. A peer in
                // hazard, withdrawal, or a bound action keeps its own movement
                // authority untouched and simply refuses the invite.
                if (!HexBatRules.mayAcceptRoostInvite(peerState.mode(), peerState.action())) continue;
                // The quiet invited peer actually consumes the call: it
                // navigates to inspect the invited position; its OWN roost
                // cadence (80-tick floor honored, never forced due) then
                // installs a roost only through independent validation near
                // that support.
                final BlockPos invited = state.roost().orElseThrow();
                HexBatState updated = peerState
                    .withDestination(Optional.of(invited), DestinationPurpose.ROOST);
                updated = requestNavigation(peer, updated, invited, now, 1.0D);
                peer.setBatState(updated);
                accepted++;
                bat.batCounters().callRecipients++;
            }
            bat.batCounters().callsAttempted++;
            if (accepted > 0) {
                bat.batCounters().callsAccepted++;
                state = state.withDeadlines(withCallDedupe(state.deadlines(),
                    HexBatRules.saturatingAdd(now, HexBatRules.CALL_DEDUPE_TICKS)));
                emitParticles(level, bat.position(), ParticleTypes.ENCHANT, HexBatRules.MAX_CALL_PARTICLES);
            }
        }
        return state;
    }

    /** One bounded one-hop THREAT report; received reports never re-emit. */
    static HexBatState emitThreatCall(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input,
        final UUID targetId, final long now
    ) {
        HexBatState state = input;
        if (!HexBatRules.mayEmitCall(state.threatHopCount(), state.deadlines().callDedupeUntil(), now)) {
            bat.batCounters().callsDeduped++;
            return state;
        }
        bat.batCounters().callsAttempted++;
        final Optional<UUID> callerOwner = CreatureBehaviorState.owner(bat);
        final String dimension = level.dimension().identifier().toString();
        final List<HexBatEntity> peers = level.getEntitiesOfClass(
            HexBatEntity.class,
            bat.getBoundingBox().inflate(HexBatRules.CALL_RADIUS),
            peer -> peer != bat && peer.isAlive()
        );
        int visited = 0;
        int accepted = 0;
        for (final HexBatEntity peer : peers) {
            if (visited >= HexBatRules.MAX_PEER_VISITS
                || accepted >= HexBatRules.MAX_CALL_RECIPIENTS) break;
            visited++;
            bat.batCounters().peerVisits++;
            if (!HexBatRules.callCompatible(callerOwner, CreatureBehaviorState.owner(peer))) continue;
            // The receiver stores hop 1 so it can never re-emit the report.
            peer.setBatState(peer.batState().withThreat(
                Optional.of(targetId), Optional.of(dimension),
                HexBatRules.saturatingAdd(now, HexBatRules.CALL_EXPIRY_TICKS),
                HexBatRules.MAX_CALL_HOPS
            ));
            accepted++;
            bat.batCounters().callRecipients++;
        }
        state = state.withDeadlines(withCallDedupe(state.deadlines(),
            HexBatRules.saturatingAdd(now, HexBatRules.CALL_DEDUPE_TICKS)));
        if (accepted > 0) {
            bat.batCounters().callsAccepted++;
            emitParticles(level, bat.position(), ParticleTypes.ENCHANT, HexBatRules.MAX_CALL_PARTICLES);
        }
        return state;
    }

    // ---- navigation gate (single movement authority) ----

    static HexBatState requestNavigation(
        final HexBatEntity bat, final HexBatState input, final BlockPos destination,
        final long now, final double speed
    ) {
        HexBatState state = input;
        if (state.deadlines().routeBackoffUntil() > now
            || !HexBatRules.navigationDue(state.cadence().nextNavigationAt(), now)) {
            return state;
        }
        state = state.withCadence(withNavigation(state.cadence(),
            HexBatRules.saturatingAdd(now, HexBatRules.NAVIGATION_INTERVAL_TICKS)));
        bat.batCounters().navigationRequests++;
        final boolean accepted = bat.getNavigation().moveTo(
            destination.getX() + 0.5D, destination.getY() + 0.2D, destination.getZ() + 0.5D, speed
        );
        if (accepted) {
            bat.batCounters().navigationAccepts++;
            return state;
        }
        bat.batCounters().navigationFailures++;
        final int failures = HexBatRules.routeFailures(state.routeFailures());
        if (HexBatRules.routeBackoffRequired(failures)) {
            bat.batCounters().backoffs++;
            return state
                .withDestination(Optional.empty(), DestinationPurpose.NONE)
                .withRouteFailures(0)
                .withDeadlines(withRouteBackoff(state.deadlines(),
                    HexBatRules.routeBackoffUntil(failures, now)));
        }
        return state.withRouteFailures(failures);
    }

    // ---- aerial destination search ----

    /** Shared contact-hazard classification used by observation and destination search. */
    private static boolean contactHazardous(final net.minecraft.world.level.block.state.BlockState blockState) {
        return blockState.is(net.minecraft.world.level.block.Blocks.CACTUS)
            || blockState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
            || blockState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
            || blockState.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
            || blockState.is(net.minecraft.world.level.block.Blocks.LAVA)
            || blockState.is(net.minecraft.world.level.block.Blocks.FIRE)
            || blockState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE);
    }

    static Optional<BlockPos> findAerialDestination(
        final HexBatEntity bat, final ServerLevel level, final BlockPos origin,
        final int range, final boolean excludeOrigin
    ) {
        final int rotation = HexBatRules.stableOffset(bat.getUUID(), 8);
        int candidates = 0;
        int reads = 0;
        for (int ring = 2; ring <= range && candidates < HexBatRules.MAX_DESTINATION_CANDIDATES; ring += 3) {
            for (int step = 0; step < 8 && candidates < HexBatRules.MAX_DESTINATION_CANDIDATES; step++) {
                final int direction = (step + rotation) % 8;
                for (int dy = 2; dy >= -1 && candidates < HexBatRules.MAX_DESTINATION_CANDIDATES; dy -= 3) {
                    if (reads + 3 > HexBatRules.MAX_DESTINATION_BLOCK_READS) return Optional.empty();
                    candidates++;
                    bat.batCounters().destinationCandidates++;
                    final BlockPos pos = origin.offset(
                        ring * OFFSET_X[direction] / 2, dy, ring * OFFSET_Z[direction] / 2
                    );
                    if (excludeOrigin && pos.equals(bat.blockPosition())) continue;
                    if (!level.hasChunkAt(pos)) continue;
                    reads += 3;
                    bat.batCounters().destinationBlockReads += 3;
                    if (!level.getFluidState(pos).isEmpty()) continue;
                    // A destination must improve the active hazard classification:
                    // a point inside fire or another contact hazard never passes.
                    if (contactHazardous(level.getBlockState(pos))) continue;
                    if (!level.getWorldBorder().isWithinBounds(pos)) continue;
                    final AABB box = bat.getType().getDimensions()
                        .makeBoundingBox(Vec3.atBottomCenterOf(pos));
                    if (!level.noCollision(bat, box)) continue;
                    return Optional.of(pos.immutable());
                }
            }
        }
        return Optional.empty();
    }

    private static final int[] OFFSET_X = {2, 2, 0, -2, -2, -2, 0, 2};
    private static final int[] OFFSET_Z = {0, 2, 2, 2, 0, -2, -2, 0};

    // ---- legality ----

    /** Final absolute exclusion set shared by acquisition and canAttack. */
    public static boolean eligibleTarget(final HexBatEntity bat, final LivingEntity target) {
        if (absolutelyExcluded(bat, target)) {
            return false;
        }
        // During a bound action, only the frozen identity is attackable.
        final HexBatState state = bat.batState();
        if (state.action() == Action.SWOOP) {
            return state.actionTargetId().map(id -> id.equals(target.getUUID())).orElse(false);
        }
        return true;
    }

    static boolean absolutelyExcluded(final HexBatEntity bat, final LivingEntity target) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(bat);
        final Optional<UUID> targetOwner = CreatureBehaviorState.owner(target);
        return HexBatRules.absolutelyExcluded(new AbsoluteFacts(
            target == bat,
            !target.isAlive() || target.isRemoved(),
            target.isInvulnerable(),
            owner.isPresent() && owner.orElseThrow().equals(target.getUUID()),
            owner.isPresent() && targetOwner.isPresent()
                && owner.orElseThrow().equals(targetOwner.orElseThrow()),
            target instanceof HexBatEntity,
            target instanceof Player player && (player.isCreative() || player.isSpectator()),
            target.level() != bat.level()
        ));
    }

    static ProactiveFacts proactiveFacts(
        final LivingEntity candidate, final double distanceSqr, final boolean lineOfSight
    ) {
        final boolean noncombatant = candidate instanceof AbstractVillager
            || candidate instanceof AbstractGolem
            || candidate instanceof Animal
            || (!(candidate instanceof Enemy) && !(candidate instanceof Player));
        final boolean witchOrOwl = candidate instanceof Witch
            || candidate instanceof ArcaneCreature arcane
                && arcane.creatureKind() == ArcaneCreature.CreatureKind.OWL;
        final boolean otherArcane = candidate instanceof ArcaneCreature arcane
            && arcane.creatureKind() != ArcaneCreature.CreatureKind.OWL
            && !(candidate instanceof HexBatEntity);
        return new ProactiveFacts(
            noncombatant,
            witchOrOwl,
            otherArcane,
            distanceSqr > (double) HexBatRules.PROACTIVE_ACQUIRE_RANGE * HexBatRules.PROACTIVE_ACQUIRE_RANGE,
            !lineOfSight
        );
    }

    // ---- Murderous Flock seeding (spawning itself stays in BrewRuntime) ----

    /**
     * Computes the legality-first ranked bounded stable accumulator once per
     * cast. Visits at most 16 living candidates, retains at most 8, ranks
     * explicit target, current owner attacker, current jinx, survival player,
     * ordinary hostile, then distance to the cast center, then UUID, and never
     * includes the owner, same-owner entities, exact Hex Bats,
     * creative/spectator players, villagers, golems, passive animals,
     * neutral Witches, neutral Owls, or unrelated Arcane Creatures. The head
     * of the returned list is the strongest candidate: an explicit cast
     * target can never lose to a nearer hostile.
     */
    public static List<LivingEntity> flockTargets(
        final @Nullable Entity owner,
        final @Nullable LivingEntity directTarget,
        final Vec3 castCenter,
        final List<LivingEntity> nearbyCandidates
    ) {
        record Ranked(LivingEntity entity, HexBatRules.FlockCandidate candidate) {
        }
        final LivingEntity ownerAttacker = owner instanceof LivingEntity livingOwner
            ? livingOwner.getLastHurtByMob()
            : null;
        final List<Ranked> ranked = new ArrayList<>();
        if (directTarget != null && directTarget.isAlive()
            && legalFlockTarget(owner, directTarget)) {
            ranked.add(new Ranked(directTarget, new HexBatRules.FlockCandidate(
                directTarget.getUUID(),
                HexBatRules.FlockCandidate.RANK_EXPLICIT_TARGET,
                directTarget.position().distanceToSqr(castCenter)
            )));
        }
        int visited = 0;
        for (final LivingEntity candidate : nearbyCandidates) {
            if (visited >= HexBatRules.MAX_TARGET_VISITS) break;
            visited++;
            if (candidate == directTarget) continue;
            if (!legalFlockTarget(owner, candidate)) continue;
            final int rank;
            if (candidate == ownerAttacker) {
                rank = HexBatRules.FlockCandidate.RANK_OWNER_ATTACKER;
            } else if (candidate.hasEffect(MobEffects.UNLUCK)) {
                rank = HexBatRules.FlockCandidate.RANK_JINX_MARKED;
            } else if (candidate instanceof Player) {
                rank = HexBatRules.FlockCandidate.RANK_SURVIVAL_PLAYER;
            } else {
                rank = HexBatRules.FlockCandidate.RANK_ORDINARY_HOSTILE;
            }
            ranked.add(new Ranked(candidate, new HexBatRules.FlockCandidate(
                candidate.getUUID(), rank, candidate.position().distanceToSqr(castCenter)
            )));
        }
        ranked.sort(java.util.Comparator.comparing(Ranked::candidate, HexBatRules.flockOrder()));
        return ranked.stream()
            .limit(HexBatRules.MAX_RETAINED_TARGETS)
            .map(Ranked::entity)
            .toList();
    }

    static boolean legalFlockTarget(final @Nullable Entity owner, final LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.isRemoved() || candidate.isInvulnerable()) return false;
        if (candidate == owner) return false;
        if (candidate instanceof HexBatEntity) return false;
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (owner != null && CreatureBehaviorState.isOwnedBy(candidate, owner.getUUID())) return false;
        if (candidate instanceof AbstractVillager || candidate instanceof AbstractGolem
            || candidate instanceof Animal || candidate instanceof Witch) return false;
        if (candidate instanceof ArcaneCreature) return false;
        return candidate instanceof Enemy || candidate instanceof Player;
    }

    /** Seeds provenance, owner, impact anchor, and one legal initial target on a successful flock bat. */
    public static void initializeFlockSpawn(
        final HexBatEntity bat,
        final ServerLevel level,
        final BlockPos impactAnchor,
        final @Nullable Entity owner,
        final Optional<LivingEntity> initialTarget
    ) {
        final long now = level.getGameTime();
        final String dimension = level.dimension().identifier().toString();
        if (owner != null) {
            CreatureBehaviorState.bind(bat, owner.getUUID());
        }
        HexBatState state = bat.batState()
            .withProvenance(Provenance.MURDEROUS_FLOCK)
            .withAnchor(Optional.of(impactAnchor.immutable()), Optional.of(dimension));
        if (initialTarget.isPresent() && legalFlockTarget(owner, initialTarget.orElseThrow())) {
            final LivingEntity target = initialTarget.orElseThrow();
            state = state.withThreat(
                Optional.of(target.getUUID()), Optional.of(dimension),
                HexBatRules.saturatingAdd(now, HexBatRules.CALL_EXPIRY_TICKS), 0
            );
            bat.setTarget(target);
            bat.noteTargetSeen(target.getUUID(), now);
        }
        bat.setBatState(state);
    }

    // ---- bounded feedback ----

    static HexBatState playBoundedSound(
        final HexBatEntity bat, final ServerLevel level, final HexBatState input,
        final net.minecraft.sounds.SoundEvent sound, final long now
    ) {
        if (!HexBatRules.scanDue(input.cadence().nextSoundAt(), now)) {
            return input;
        }
        level.playSound(null, bat.getX(), bat.getY(), bat.getZ(), sound, bat.getSoundSource(), 1.0F, 1.0F);
        return input.withCadence(withSound(input.cadence(),
            HexBatRules.saturatingAdd(now, HexBatRules.SOUND_INTERVAL_TICKS)));
    }

    private static void emitParticles(
        final ServerLevel level, final Vec3 position,
        final net.minecraft.core.particles.SimpleParticleType type, final int count
    ) {
        level.sendParticles(type, position.x(), position.y() + 0.3D, position.z(),
            count, 0.3D, 0.3D, 0.3D, 0.0D);
    }

    // ---- cadence/deadline record helpers ----

    private static HexBatState.Cadence withTargetScan(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(at, cadence.nextPeerScanAt(), cadence.nextRoostSearchAt(),
            cadence.nextHazardScanAt(), cadence.nextNavigationAt(), cadence.nextSoundAt());
    }

    private static HexBatState.Cadence withPeerScan(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(cadence.nextTargetScanAt(), at, cadence.nextRoostSearchAt(),
            cadence.nextHazardScanAt(), cadence.nextNavigationAt(), cadence.nextSoundAt());
    }

    private static HexBatState.Cadence withRoostSearch(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(cadence.nextTargetScanAt(), cadence.nextPeerScanAt(), at,
            cadence.nextHazardScanAt(), cadence.nextNavigationAt(), cadence.nextSoundAt());
    }

    private static HexBatState.Cadence withHazardScan(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(cadence.nextTargetScanAt(), cadence.nextPeerScanAt(),
            cadence.nextRoostSearchAt(), at, cadence.nextNavigationAt(), cadence.nextSoundAt());
    }

    private static HexBatState.Cadence withNavigation(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(cadence.nextTargetScanAt(), cadence.nextPeerScanAt(),
            cadence.nextRoostSearchAt(), cadence.nextHazardScanAt(), at, cadence.nextSoundAt());
    }

    private static HexBatState.Cadence withSound(final HexBatState.Cadence cadence, final long at) {
        return new HexBatState.Cadence(cadence.nextTargetScanAt(), cadence.nextPeerScanAt(),
            cadence.nextRoostSearchAt(), cadence.nextHazardScanAt(), cadence.nextNavigationAt(), at);
    }

    private static HexBatState.Deadlines withActionWindows(
        final HexBatState.Deadlines deadlines, final long windupUntil, final long executeUntil
    ) {
        return new HexBatState.Deadlines(windupUntil, executeUntil, deadlines.actionRecoverUntil(),
            deadlines.swoopCooldownUntil(), deadlines.withdrawUntil(), deadlines.routeBackoffUntil(),
            deadlines.callDedupeUntil(), deadlines.sortieUntil());
    }

    private static HexBatState.Deadlines withRecovery(
        final HexBatState.Deadlines deadlines, final long recoverUntil, final long cooldownUntil
    ) {
        return new HexBatState.Deadlines(0L, 0L, recoverUntil, cooldownUntil,
            deadlines.withdrawUntil(), deadlines.routeBackoffUntil(),
            deadlines.callDedupeUntil(), deadlines.sortieUntil());
    }

    private static HexBatState.Deadlines withWithdraw(final HexBatState.Deadlines deadlines, final long until) {
        return new HexBatState.Deadlines(deadlines.actionWindupUntil(), deadlines.actionExecuteUntil(),
            deadlines.actionRecoverUntil(), deadlines.swoopCooldownUntil(), until,
            deadlines.routeBackoffUntil(), deadlines.callDedupeUntil(), deadlines.sortieUntil());
    }

    private static HexBatState.Deadlines withRouteBackoff(final HexBatState.Deadlines deadlines, final long until) {
        return new HexBatState.Deadlines(deadlines.actionWindupUntil(), deadlines.actionExecuteUntil(),
            deadlines.actionRecoverUntil(), deadlines.swoopCooldownUntil(), deadlines.withdrawUntil(),
            until, deadlines.callDedupeUntil(), deadlines.sortieUntil());
    }

    private static HexBatState.Deadlines withCallDedupe(final HexBatState.Deadlines deadlines, final long until) {
        return new HexBatState.Deadlines(deadlines.actionWindupUntil(), deadlines.actionExecuteUntil(),
            deadlines.actionRecoverUntil(), deadlines.swoopCooldownUntil(), deadlines.withdrawUntil(),
            deadlines.routeBackoffUntil(), until, deadlines.sortieUntil());
    }

    private static HexBatState.Deadlines withSortie(final HexBatState.Deadlines deadlines, final long until) {
        return new HexBatState.Deadlines(deadlines.actionWindupUntil(), deadlines.actionExecuteUntil(),
            deadlines.actionRecoverUntil(), deadlines.swoopCooldownUntil(), deadlines.withdrawUntil(),
            deadlines.routeBackoffUntil(), deadlines.callDedupeUntil(), until);
    }
}
