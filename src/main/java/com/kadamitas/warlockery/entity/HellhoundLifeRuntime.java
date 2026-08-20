package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.BiteFacts;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Evidence;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.EvidenceKind;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Intent;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Mode;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.RouteFailure;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.TargetFacts;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The single server-side F09 target/navigation authority. Every observation is loaded and
 * bounded, every claim expires, and one priority ladder cancels lower state before movement.
 * It never invokes the generic tactical or ambient runtimes for Hellhound.
 */
public final class HellhoundLifeRuntime {
    private HellhoundLifeRuntime() {
    }

    /** Structural counters proving actual work, separate from retained-result caps. */
    public static final class Counters {
        long controllerTicks;
        long fullDecisions;
        long evidenceScans;
        long entitiesVisited;
        long maximumRetainedCandidates;
        long chargedBlockReads;
        long maximumBlockReadsPerSearch;
        long navigationRequests;
        long routeFailures;
        long packRefreshes;
        long packCalls;
        long warnings;
        long biteWindups;
        long biteCommits;
        long actionCancellations;
        long feedbackEvents;
        long hazardInterruptions;
        long ownerReconciliations;

        public long controllerTicks() { return controllerTicks; }
        public long fullDecisions() { return fullDecisions; }
        public long evidenceScans() { return evidenceScans; }
        public long entitiesVisited() { return entitiesVisited; }
        public long maximumRetainedCandidates() { return maximumRetainedCandidates; }
        public long chargedBlockReads() { return chargedBlockReads; }
        public long maximumBlockReadsPerSearch() { return maximumBlockReadsPerSearch; }
        public long navigationRequests() { return navigationRequests; }
        public long routeFailures() { return routeFailures; }
        public long packRefreshes() { return packRefreshes; }
        public long packCalls() { return packCalls; }
        public long warnings() { return warnings; }
        public long biteWindups() { return biteWindups; }
        public long biteCommits() { return biteCommits; }
        public long actionCancellations() { return actionCancellations; }
        public long feedbackEvents() { return feedbackEvents; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long ownerReconciliations() { return ownerReconciliations; }
    }

    /** Entry point from {@link HellhoundEntity#tickSpecializedActivity}. */
    public static void tick(final HellhoundEntity hound, final ServerLevel level) {
        final Counters counters = hound.lifeCounters();
        counters.controllerTicks++;
        final long now = level.getGameTime();
        AmbientActivityRuntime.clearExpiredHearth(hound, level);
        HellhoundLifeState state = hound.lifeState();
        state = reconcileFirstTick(hound, level, state, now);

        // The generic HazardEscapeRuntime is the project-sanctioned per-tick hazard authority
        // (the committed F04 LycanPackRuntime pattern). It just issued the escape navigation,
        // so claims are cancelled state-only exactly once on hazard entry and the escape path
        // is never stopped while the hazard persists.
        if (HazardEscapeRuntime.tick(hound, level, ArcaneCreature.CreatureKind.HELLHOUND)) {
            if (state.intent() != Intent.HAZARD_ESCAPE) {
                counters.hazardInterruptions++;
                counters.actionCancellations++;
                hound.setTarget(null);
                state = cancelClaimUnits(state);
            }
            hound.setLifeState(state.withIntent(Intent.HAZARD_ESCAPE));
            return;
        }
        if (!HellhoundLifeRules.due(state.cadence().nextDecisionAt(), now)) {
            hound.setLifeState(state);
            return;
        }
        counters.fullDecisions++;
        state = state.withCadence(bump(state.cadence(), now, CadenceField.DECISION));
        state = expireClaims(state, now);
        state = reconcileOwner(hound, level, state, now, counters);
        state = scanEvidence(hound, level, state, now, counters);
        state = refreshPackRoles(hound, level, state, now, counters);
        state = decide(hound, level, state, now, counters);
        hound.setLifeState(state);
    }

    /** Entry point from {@link HellhoundEntity#hurtServer}; records attributable direct attacks. */
    public static void recordDirectAttack(
        final HellhoundEntity hound,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == hound) {
            return;
        }
        // Attribution freshness contract: an accepted hit records same-tick, so a stale
        // last-hurt timestamp means this call did not come from a live damage event.
        if (hound.getLastHurtByMobTimestamp()
            + HellhoundLifeRules.ATTRIBUTION_FRESHNESS_TICKS < hound.tickCount) {
            return;
        }
        final long now = level.getGameTime();
        HellhoundLifeState state = hound.lifeState();
        if (!eligibleTarget(hound, attacker)) {
            return;
        }
        final Evidence attack = HellhoundLifeRules.createEvidence(
            EvidenceKind.DIRECT_ATTACK,
            Optional.of(attacker.getUUID()),
            Optional.of(dimensionId(level)),
            Optional.of(attacker.blockPosition().asLong()),
            now
        );
        state = state.withEvidence(HellhoundLifeRules.recordEvidence(state.evidence(), attack, now));
        state = state.withChallenger(
            Optional.of(attacker.getUUID()),
            Optional.of(dimensionId(level)),
            HellhoundLifeRules.clampLoadedDeadline(
                HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.SELF_DEFENSE_LEASH_TICKS),
                now, HellhoundLifeRules.SELF_DEFENSE_LEASH_TICKS
            )
        );
        state = broadcastPackCall(hound, level, state, attack, now);
        hound.setLifeState(state);
    }

    /**
     * Entry point from {@link InfernalPactEffects}: the bounded semantic command seam. An owner
     * command never sets a raw target; it is validated through the common eligibility predicate
     * and recorded as owner-threat evidence which the ordinary priority ladder consumes.
     */
    public static void deliverOwnerCommand(
        final HellhoundEntity hound,
        final ServerLevel level,
        final Player owner,
        final LivingEntity commandedTarget
    ) {
        if (hound.getTarget() == owner) {
            hound.setTarget(null);
        }
        final HellhoundLifeState state = hound.lifeState();
        if (!state.bound() || state.ownerId().filter(owner.getUUID()::equals).isEmpty()) {
            return;
        }
        if (commandedTarget == null || commandedTarget == owner
            || !eligibleTarget(hound, commandedTarget)) {
            return;
        }
        final long now = level.getGameTime();
        final Evidence threat = HellhoundLifeRules.createEvidence(
            EvidenceKind.OWNER_THREAT,
            Optional.of(commandedTarget.getUUID()),
            Optional.of(dimensionId(level)),
            Optional.of(commandedTarget.blockPosition().asLong()),
            now
        );
        hound.setLifeState(state.withEvidence(
            HellhoundLifeRules.recordEvidence(state.evidence(), threat, now)
        ));
    }

    /** Common eligibility predicate consumed by canAttack, commands, and every acquisition. */
    public static boolean eligibleTarget(final HellhoundEntity hound, final LivingEntity target) {
        final HellhoundLifeState state = hound.lifeState();
        final Optional<UUID> owner = effectiveOwner(hound);
        final boolean ownerAlly = owner.isPresent()
            && target instanceof HellhoundEntity otherHound
            && effectiveOwner(otherHound).equals(owner);
        final boolean samePack = target instanceof HellhoundEntity otherHound
            && otherHound.lifeState().packId().equals(state.packId());
        // No arbitrary faction: a neutral Hellhound is never an eligible target merely for
        // proximity. Hound-versus-hound combat exists only through the self-defense channel —
        // the recorded challenger, or the fresh attacker being attributed by recordDirectAttack.
        if (target instanceof HellhoundEntity && !samePack && !ownerAlly
            && state.challengerId().filter(target.getUUID()::equals).isEmpty()
            && hound.getLastHurtByMob() != target) {
            return false;
        }
        return HellhoundLifeRules.eligibleTarget(new TargetFacts(
            target.isAlive(),
            !target.isRemoved(),
            target.level() == hound.level(),
            owner.filter(target.getUUID()::equals).isPresent(),
            ownerAlly,
            hound.isAlliedTo(target),
            samePack,
            target instanceof Player player && player.isCreative(),
            target.isSpectator(),
            target.isInvulnerable(),
            target instanceof NamiEntity
        ));
    }

    /** The exact effective Animus authority: only {@code WarlockeryInfernalOwner}, by key. */
    public static Optional<UUID> effectiveOwner(final HellhoundEntity hound) {
        final String stored = WarlockeryEntityData.get(hound).getStringOr(InfernalPactEffects.OWNER_KEY, "");
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(stored));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * Releases target, navigation, pack role/call, warning, destination, heat, and optionally the
     * exact still-owned legacy hearth claim. Used by death, discard, cure, and Peaceful removal.
     */
    public static void releaseAll(
        final HellhoundEntity hound,
        final ServerLevel level,
        final boolean releaseLegacyHearth
    ) {
        hound.setTarget(null);
        hound.getNavigation().stop();
        hound.setLifeState(hound.lifeState().released());
        if (releaseLegacyHearth) {
            AmbientActivityRuntime.releaseExactOwnedLegacyHearth(hound, level);
        }
    }

    /** Deterministic bounded retention: required facts are preseeded before generic candidates. */
    public static List<UUID> retainCandidates(
        final Optional<UUID> ownerThreat,
        final Optional<UUID> directAttacker,
        final Optional<UUID> warningPlayer,
        final Optional<UUID> currentChallenger,
        final List<UUID> generic
    ) {
        final LinkedHashSet<UUID> retained = new LinkedHashSet<>();
        ownerThreat.ifPresent(retained::add);
        directAttacker.ifPresent(retained::add);
        warningPlayer.ifPresent(retained::add);
        currentChallenger.ifPresent(retained::add);
        for (final UUID candidate : generic) {
            if (retained.size() >= HellhoundLifeRules.MAX_RETAINED_CANDIDATES) {
                break;
            }
            retained.add(candidate);
        }
        return retained.stream().limit(HellhoundLifeRules.MAX_RETAINED_CANDIDATES).toList();
    }

    /** Deterministic pack sector offsets around a target, bounded to the 3-5 block ring. */
    public static BlockPos sectorOffset(final PackRole role, final BlockPos target, final BlockPos origin) {
        final int radius = HellhoundLifeRules.SECTOR_MIN_RADIUS
            + Math.floorMod(target.getX() + target.getZ(), HellhoundLifeRules.SECTOR_MAX_RADIUS
                - HellhoundLifeRules.SECTOR_MIN_RADIUS + 1);
        final int dx = Integer.signum(target.getX() - origin.getX());
        final int dz = Integer.signum(target.getZ() - origin.getZ());
        return switch (role) {
            case PRESSURE -> target;
            case LEFT -> target.offset(-dz * radius, 0, dx * radius);
            case RIGHT -> target.offset(dz * radius, 0, -dx * radius);
            case CUTOFF -> target.offset(-dx * radius, 0, -dz * radius);
        };
    }

    private enum CadenceField {
        DECISION, OWNER, EVIDENCE, PACK_REFRESH, PACK_CALL, PATROL, HEAT, EVENT_FEEDBACK,
        AMBIENT_FEEDBACK, NAVIGATION
    }

    private static HellhoundLifeState.Cadence bump(
        final HellhoundLifeState.Cadence cadence,
        final long now,
        final CadenceField field
    ) {
        return new HellhoundLifeState.Cadence(
            field == CadenceField.DECISION
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.DECISION_INTERVAL_TICKS)
                : cadence.nextDecisionAt(),
            field == CadenceField.OWNER
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.OWNER_REFRESH_INTERVAL_TICKS)
                : cadence.nextOwnerRefreshAt(),
            field == CadenceField.EVIDENCE
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.EVIDENCE_SCAN_INTERVAL_TICKS)
                : cadence.nextEvidenceScanAt(),
            field == CadenceField.PACK_REFRESH
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.PACK_REFRESH_INTERVAL_TICKS)
                : cadence.nextPackRefreshAt(),
            field == CadenceField.PACK_CALL
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.PACK_CALL_INTERVAL_TICKS)
                : cadence.nextPackCallAt(),
            field == CadenceField.PATROL
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.PATROL_SEARCH_INTERVAL_TICKS)
                : cadence.nextPatrolSearchAt(),
            field == CadenceField.HEAT
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.HEAT_SEARCH_INTERVAL_TICKS)
                : cadence.nextHeatSearchAt(),
            field == CadenceField.EVENT_FEEDBACK
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.EVENT_FEEDBACK_INTERVAL_TICKS)
                : cadence.nextEventFeedbackAt(),
            field == CadenceField.AMBIENT_FEEDBACK
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.AMBIENT_FEEDBACK_INTERVAL_TICKS)
                : cadence.nextAmbientFeedbackAt(),
            field == CadenceField.NAVIGATION
                ? HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.NAVIGATION_INTERVAL_TICKS)
                : cadence.nextNavigationAt()
        );
    }

    private static HellhoundLifeState reconcileFirstTick(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now
    ) {
        if (state.territoryDimension().isPresent() || state.bound()) {
            return state;
        }
        return state.withTerritory(
            Optional.of(dimensionId(level)),
            Optional.of(hound.blockPosition())
        );
    }

    private static HellhoundLifeState expireClaims(final HellhoundLifeState state, final long now) {
        HellhoundLifeState updated = state.withEvidence(
            HellhoundLifeRules.pruneExpired(state.evidence(), now)
        );
        if (updated.challengerId().isPresent() && HellhoundLifeRules.due(updated.challengerExpiresAt(), now)) {
            updated = updated.withChallenger(Optional.empty(), Optional.empty(), 0L);
        }
        if (updated.packRole().isPresent() && HellhoundLifeRules.due(updated.packRoleExpiresAt(), now)) {
            updated = updated.withPackRole(Optional.empty(), 0L, updated.targetEvidenceEpoch());
        }
        if (updated.destination().isPresent() && HellhoundLifeRules.due(updated.destinationExpiresAt(), now)) {
            updated = updated.withDestination(Optional.empty(), 0L);
        }
        if (updated.heatPoint().isPresent() && HellhoundLifeRules.due(updated.heatPointExpiresAt(), now)) {
            updated = updated.withHeatPoint(Optional.empty(), 0L);
        }
        if (updated.routeRetryAfter() != 0L && HellhoundLifeRules.due(updated.routeRetryAfter(), now)) {
            updated = updated.withRouteFailures(0, Optional.empty(), 0L);
        }
        return updated;
    }

    private static HellhoundLifeState reconcileOwner(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        if (!HellhoundLifeRules.due(state.cadence().nextOwnerRefreshAt(), now)) {
            return state;
        }
        counters.ownerReconciliations++;
        HellhoundLifeState updated = state.withCadence(bump(state.cadence(), now, CadenceField.OWNER));
        final Optional<UUID> owner = effectiveOwner(hound);
        if (owner.isPresent() && updated.mode() != Mode.ANIMUS_BOUND) {
            updated = updated.withMode(Mode.ANIMUS_BOUND, owner);
        } else if (owner.isEmpty() && updated.mode() == Mode.ANIMUS_BOUND) {
            updated = cancelActiveClaims(hound, updated, counters).withMode(Mode.WILD, Optional.empty());
        } else if (owner.isPresent()) {
            updated = updated.withMode(Mode.ANIMUS_BOUND, owner);
        }
        return updated.withOwnerReconciledAt(now);
    }

    private static HellhoundLifeState scanEvidence(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        if (!HellhoundLifeRules.due(state.cadence().nextEvidenceScanAt(), now)) {
            return state;
        }
        counters.evidenceScans++;
        HellhoundLifeState updated = state.withCadence(bump(state.cadence(), now, CadenceField.EVIDENCE));
        final List<LivingEntity> visited = level.getEntitiesOfClass(
            LivingEntity.class,
            hound.getBoundingBox().inflate(HellhoundLifeRules.EVIDENCE_SCAN_RADIUS),
            candidate -> candidate != hound
        );
        counters.entitiesVisited += visited.size();
        final List<UUID> generic = visited.stream()
            .filter(candidate -> candidate instanceof Player)
            .filter(candidate -> eligibleTarget(hound, candidate))
            .sorted(Comparator.<LivingEntity>comparingDouble(hound::distanceToSqr)
                .thenComparing(LivingEntity::getUUID, HellhoundLifeRules.unsignedUuidOrder()))
            .map(LivingEntity::getUUID)
            .toList();
        final List<UUID> retained = retainCandidates(
            updated.evidence().stream()
                .filter(entry -> entry.kind() == EvidenceKind.OWNER_THREAT && entry.valid(now))
                .findFirst().flatMap(Evidence::sourceId),
            updated.evidence().stream()
                .filter(entry -> entry.kind() == EvidenceKind.DIRECT_ATTACK && entry.valid(now))
                .findFirst().flatMap(Evidence::sourceId),
            updated.warningPlayerId(),
            updated.challengerId(),
            generic
        );
        counters.maximumRetainedCandidates = Math.max(counters.maximumRetainedCandidates, retained.size());
        for (final LivingEntity candidate : visited) {
            if (!retained.contains(candidate.getUUID()) || !eligibleTarget(hound, candidate)) {
                continue;
            }
            final boolean sight = hound.getSensing().hasLineOfSight(candidate);
            final boolean scent = scentReaches(hound, level, candidate);
            if (!sight && !scent) {
                continue;
            }
            final Evidence observation = HellhoundLifeRules.createEvidence(
                sight ? EvidenceKind.SIGHT : EvidenceKind.SCENT,
                Optional.of(candidate.getUUID()),
                Optional.of(dimensionId(level)),
                Optional.of(candidate.blockPosition().asLong()),
                now
            );
            updated = updated.withEvidence(
                HellhoundLifeRules.recordEvidence(updated.evidence(), observation, now)
            );
        }
        return updated;
    }

    private static HellhoundLifeState refreshPackRoles(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        if (state.bound() || !HellhoundLifeRules.due(state.cadence().nextPackRefreshAt(), now)) {
            return state;
        }
        counters.packRefreshes++;
        HellhoundLifeState updated = state.withCadence(bump(state.cadence(), now, CadenceField.PACK_REFRESH));
        final List<HellhoundEntity> members = loadedPackMembers(
            hound, level, state.packId(), HellhoundLifeRules.PACK_REFRESH_RADIUS
        );
        counters.entitiesVisited += members.size();
        refreshPackSnapshot(hound, members);
        if (members.size() <= 1 || !hasActionableEvidence(updated, now)) {
            if (updated.packRole().isPresent()) {
                updated = updated.withPackRole(Optional.empty(), 0L, updated.targetEvidenceEpoch());
            }
            return updated;
        }
        final Map<UUID, PackRole> roles = HellhoundLifeRules.deriveRoles(
            members.stream().map(HellhoundEntity::getUUID).toList()
        );
        final PackRole role = roles.get(hound.getUUID());
        if (role == null) {
            return updated.withPackRole(Optional.empty(), 0L, updated.targetEvidenceEpoch());
        }
        return updated.withPackRole(
            Optional.of(role),
            HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.SECTOR_SETUP_TICKS),
            updated.targetEvidenceEpoch() + 1L
        );
    }

    private static HellhoundLifeState broadcastPackCall(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final Evidence source,
        final long now
    ) {
        if (state.bound()
            || !HellhoundLifeRules.shareableWithPack(source.kind())
            || !HellhoundLifeRules.mayBroadcastPackCall(state.cadence().nextPackCallAt(), now)) {
            return state;
        }
        final List<HellhoundEntity> members = loadedPackMembers(
            hound, level, state.packId(), HellhoundLifeRules.PACK_CALL_RADIUS
        );
        refreshPackSnapshot(hound, members);
        for (final HellhoundEntity member : members) {
            if (member == hound || member.lifeState().bound()) {
                continue;
            }
            final Evidence copy = HellhoundLifeRules.packCallCopy(source, now);
            member.setLifeState(member.lifeState().withEvidence(
                HellhoundLifeRules.recordEvidence(member.lifeState().evidence(), copy, now)
            ));
        }
        hound.lifeCounters().packCalls++;
        return state.withCadence(bump(state.cadence(), now, CadenceField.PACK_CALL));
    }

    private static List<HellhoundEntity> loadedPackMembers(
        final HellhoundEntity hound,
        final ServerLevel level,
        final UUID packId,
        final int radius
    ) {
        return level.getEntitiesOfClass(
                HellhoundEntity.class,
                hound.getBoundingBox().inflate(radius),
                member -> member.isAlive() && member.lifeState().packId().equals(packId)
            ).stream()
            .sorted(Comparator.comparing(HellhoundEntity::getUUID, HellhoundLifeRules.unsignedUuidOrder()))
            .limit(HellhoundLifeRules.MAX_PACK_MEMBERS)
            .toList();
    }

    /** Pack facts consumed by tiny decisions come from this cadence-refreshed snapshot only. */
    private static void refreshPackSnapshot(
        final HellhoundEntity hound,
        final List<HellhoundEntity> members
    ) {
        hound.updatePackSnapshot(
            members.size(),
            members.stream()
                .filter(member -> member != hound)
                .min(Comparator.comparingDouble(hound::distanceToSqr))
                .map(LivingEntity::blockPosition)
        );
    }

    private static boolean hasActionableEvidence(final HellhoundLifeState state, final long now) {
        return state.evidence().stream().anyMatch(entry -> entry.valid(now)
            && entry.kind() != EvidenceKind.SCENT);
    }

    private static HellhoundLifeState decide(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        // Retreat hysteresis runs for both modes, and the release facts override the latch
        // facts: while a direct attacker is within three blocks or immediate valid owner
        // defense is required, the hound fights instead of latching or re-latching.
        final float healthFraction = hound.getHealth() / hound.getMaxHealth();
        final boolean attackerClose = updated.challengerId()
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive)
            .filter(attacker -> hound.distanceToSqr(attacker) <= 9.0D)
            .isPresent();
        final boolean ownerDefense = updated.bound() && updated.evidence().stream()
            .anyMatch(entry -> entry.kind() == EvidenceKind.OWNER_THREAT && entry.valid(now));
        final boolean cornered = attackerClose || ownerDefense;
        if (updated.retreatLatched()) {
            if (!HellhoundLifeRules.retreatReleases(healthFraction, attackerClose, ownerDefense)) {
                return retreat(hound, level, updated, now, counters);
            }
            updated = updated.withRetreat(false, 0L);
        }
        final boolean committedWithPack = updated.packRole().isPresent();
        final boolean isolated = committedWithPack && hound.loadedPackCountSnapshot() <= 1;
        if (!cornered && HellhoundLifeRules.retreatLatches(
            healthFraction, updated.routeFailures(), committedWithPack, isolated)) {
            updated = cancelActiveClaims(hound, updated, counters).withRetreat(
                true,
                HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.REGROUP_MAX_TICKS)
            );
            return retreat(hound, level, updated, now, counters);
        }
        // Priority 3: valid Animus owner safety and allegiance replace wild territory duty.
        if (updated.bound()) {
            return decideBound(hound, level, updated, now, counters);
        }
        // Priority 4-7: direct self-defense, then evidence-driven territorial combat.
        final Optional<LivingEntity> target = resolveTarget(hound, level, updated, now);
        if (target.isPresent()) {
            return engage(hound, level, updated, target.orElseThrow(), now, counters);
        }
        // Sniff at a last-known point only when a strong record genuinely lost its refresh:
        // a currently observable source falls through to the warning ladder instead, so a
        // fresh SIGHT record can never shadow the WARN rung.
        final Optional<Evidence> lastKnown = updated.evidence().stream()
            .filter(entry -> entry.valid(now) && entry.packedPosition().isPresent()
                && entry.kind() != EvidenceKind.SCENT)
            .min(HellhoundLifeRules.evidenceOrder())
            .filter(entry -> HellhoundLifeRules.sniffAtLastKnown(
                sourceCurrentlyObservable(hound, level, entry), entry.observedAt(), now));
        if (lastKnown.isPresent()) {
            final BlockPos point = BlockPos.of(lastKnown.orElseThrow().packedPosition().orElseThrow());
            updated = navigate(hound, updated, point, now, counters);
            return updated.withIntent(Intent.SNIFF);
        }
        updated = maybeCancelCombatClaims(hound, updated, counters);
        // Wild warning ladder.
        updated = tickWarning(hound, level, updated, now, counters);
        if (updated.warningPlayerId().isPresent()) {
            return updated.withIntent(Intent.WARN);
        }
        // Return when far from the anchor.
        final HellhoundLifeState settled = updated;
        final Optional<BlockPos> anchor = settled.territoryAnchor()
            .filter(ignored -> settled.territoryDimension()
                .filter(dimensionId(level)::equals).isPresent());
        if (anchor.isPresent()) {
            final double anchorDistance = anchor.orElseThrow().distSqr(hound.blockPosition());
            if (HellhoundLifeRules.leashExceeded(anchorDistance, HellhoundLifeRules.PATROL_RADIUS)) {
                updated = navigate(hound, updated, anchor.orElseThrow(), now, counters);
                return updated.withIntent(Intent.RETURN);
            }
        }
        // Heat rest, then patrol, then quiet idle.
        updated = tickHeat(hound, level, updated, now, counters);
        if (updated.intent() == Intent.HEAT_REST) {
            return updated;
        }
        return tickPatrol(hound, level, updated, anchor, now, counters);
    }

    private static HellhoundLifeState decideBound(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        final Player owner = updated.ownerId().map(level::getPlayerByUUID).orElse(null);
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            // An absent or cross-dimension owner remains authoritative; hold the loaded area.
            hound.getNavigation().stop();
            hound.setTarget(null);
            return updated.withIntent(Intent.OWNER_GUARD);
        }
        // Owner attacker outranks a stale delivered command, but only a fresh attribution
        // (within the forty-tick freshness bound) is accepted.
        final LivingEntity attacker = owner.getLastHurtByMob();
        final boolean freshAttribution = owner.getLastHurtByMobTimestamp()
            + HellhoundLifeRules.ATTRIBUTION_FRESHNESS_TICKS >= owner.tickCount;
        if (attacker != null && freshAttribution && attacker.isAlive() && eligibleTarget(hound, attacker)) {
            final Evidence threat = HellhoundLifeRules.createEvidence(
                EvidenceKind.OWNER_THREAT,
                Optional.of(attacker.getUUID()),
                Optional.of(dimensionId(level)),
                Optional.of(attacker.blockPosition().asLong()),
                now
            );
            updated = updated.withEvidence(
                HellhoundLifeRules.recordEvidence(updated.evidence(), threat, now)
            );
        }
        final Optional<LivingEntity> target = resolveTarget(hound, level, updated, now);
        if (target.isPresent()) {
            final double ownerDistance = hound.distanceToSqr(owner);
            if (!HellhoundLifeRules.leashExceeded(
                ownerDistance, HellhoundLifeRules.OWNER_MAX_FOLLOW_DISTANCE)) {
                return engage(hound, level, updated, target.orElseThrow(), now, counters);
            }
        }
        updated = maybeCancelCombatClaims(hound, updated, counters);
        final double distance = Math.sqrt(hound.distanceToSqr(owner));
        if (HellhoundLifeRules.followOwner(distance)) {
            updated = navigate(hound, updated, owner.blockPosition(), now, counters);
            return updated.withIntent(Intent.OWNER_FOLLOW);
        }
        if (HellhoundLifeRules.ownerPerimeterWatch(distance)) {
            hound.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
        return updated.withIntent(Intent.OWNER_GUARD);
    }

    private static Optional<LivingEntity> resolveTarget(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now
    ) {
        return state.evidence().stream()
            .filter(entry -> entry.valid(now) && entry.sourceId().isPresent()
                && HellhoundLifeRules.engageableEvidence(entry.kind()))
            .sorted(HellhoundLifeRules.evidenceOrder())
            .map(entry -> level.getEntity(entry.sourceId().orElseThrow()))
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(candidate -> eligibleTarget(hound, candidate)
                && (hound.getSensing().hasLineOfSight(candidate)
                    || scentReaches(hound, level, candidate)))
            .findFirst();
    }

    /**
     * Scent is the only wall-penetrating channel: bounded to sixteen blocks, and stopped only by
     * barrier blocks, which are void-sealed and carry no smell. The voxel walk is bounded by the
     * scent radius (at most seventeen block reads) and runs only for candidates already inside
     * the sixteen-block distance gate, so no unbounded spatial work is added.
     */
    private static boolean scentReaches(
        final HellhoundEntity hound,
        final ServerLevel level,
        final LivingEntity candidate
    ) {
        if (hound.distanceToSqr(candidate)
            > (double) HellhoundLifeRules.SCENT_RADIUS * HellhoundLifeRules.SCENT_RADIUS) {
            return false;
        }
        final Vec3 from = hound.getEyePosition();
        final Vec3 to = candidate.getEyePosition();
        final int steps = (int) Math.ceil(from.distanceTo(to));
        for (int step = 0; step <= steps; step++) {
            final Vec3 point = steps == 0 ? from : from.lerp(to, (double) step / steps);
            if (level.getBlockState(BlockPos.containing(point)).is(Blocks.BARRIER)) {
                return false;
            }
        }
        return true;
    }

    /** A record's source is observable while it resolves to a living entity in sense range. */
    private static boolean sourceCurrentlyObservable(
        final HellhoundEntity hound,
        final ServerLevel level,
        final Evidence entry
    ) {
        return entry.sourceId()
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive)
            .filter(candidate -> hound.getSensing().hasLineOfSight(candidate)
                || scentReaches(hound, level, candidate))
            .isPresent();
    }

    private static HellhoundLifeState engage(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final LivingEntity target,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        // Leash enforcement releases the pursuit and begins RETURN.
        final boolean selfDefense = updated.challengerId()
            .filter(target.getUUID()::equals).isPresent();
        final Optional<BlockPos> anchor = updated.bound() ? Optional.empty() : updated.territoryAnchor();
        if (anchor.isPresent() && HellhoundLifeRules.leashExceeded(
            anchor.orElseThrow().distSqr(hound.blockPosition()),
            HellhoundLifeRules.pursuitLeash(selfDefense))) {
            updated = cancelActiveClaims(hound, updated, counters);
            updated = navigate(hound, updated, anchor.orElseThrow(), now, counters);
            return updated.withIntent(Intent.RETURN);
        }
        hound.setTarget(target);
        hound.getLookControl().setLookAt(target, 30.0F, 30.0F);
        final double distanceSquared = hound.distanceToSqr(target);
        // Bite windup already committed: revalidate and complete or cancel, idempotently.
        if (updated.biteWindupStartedAt() > 0L) {
            if (!HellhoundLifeRules.due(updated.biteCommitDeadline(), now)) {
                return updated.withIntent(Intent.BITE_WINDUP);
            }
            final BiteFacts facts = new BiteFacts(
                target.isAlive() && eligibleTarget(hound, target),
                HellhoundLifeRules.withinCommitRange(distanceSquared),
                hound.getSensing().hasLineOfSight(target),
                true,
                HellhoundLifeRules.due(updated.biteRecoveryUntil(), now)
            );
            updated = updated.withBiteWindows(0L, 0L, updated.biteRecoveryUntil());
            if (HellhoundLifeRules.mayCommitBite(facts)) {
                counters.biteCommits++;
                hound.doHurtTarget(level, target);
                updated = updated.withBiteWindows(
                    0L, 0L,
                    HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.BITE_RECOVERY_TICKS)
                );
                final BlockPos away = hound.blockPosition().offset(
                    Integer.signum(hound.blockPosition().getX() - target.blockPosition().getX()) * 3,
                    0,
                    Integer.signum(hound.blockPosition().getZ() - target.blockPosition().getZ()) * 3
                );
                updated = navigate(hound, updated, away, now, counters);
                return updated.withIntent(Intent.REPOSITION);
            }
            counters.actionCancellations++;
            return updated.withIntent(Intent.PRESS);
        }
        // Start a windup only inside range with recovery elapsed.
        if (HellhoundLifeRules.withinCommitRange(distanceSquared)
            && hound.getSensing().hasLineOfSight(target)
            && HellhoundLifeRules.due(updated.biteRecoveryUntil(), now)) {
            counters.biteWindups++;
            updated = updated.withBiteWindows(
                now,
                HellhoundLifeRules.clampLoadedDeadline(
                    HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.BITE_WINDUP_TICKS),
                    now, HellhoundLifeRules.BITE_WINDUP_TICKS
                ),
                updated.biteRecoveryUntil()
            );
            updated = feedback(hound, level, updated, now, counters, false);
            return updated.withIntent(Intent.BITE_WINDUP);
        }
        // Role-preferring approach; sectors validate loaded standability and reachability.
        // Closing on a sensed-but-unseen target is the design's STALK rung.
        final BlockPos approach = updated.packRole()
            .map(role -> sectorOffset(role, target.blockPosition(), hound.blockPosition()))
            .filter(sector -> validDestination(hound, level, sector, counters))
            .orElse(target.blockPosition());
        updated = navigate(hound, updated, approach, now, counters);
        if (updated.packRole().isPresent() && !approach.equals(target.blockPosition())) {
            return updated.withIntent(Intent.PACK_SETUP);
        }
        return updated.withIntent(
            hound.getSensing().hasLineOfSight(target) ? Intent.PRESS : Intent.STALK
        );
    }

    private static HellhoundLifeState tickWarning(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        final Optional<Player> warned = updated.warningPlayerId()
            .map(level::getPlayerByUUID)
            .filter(LivingEntity::isAlive);
        if (updated.warningPlayerId().isPresent()) {
            if (warned.isEmpty() || !eligibleTarget(hound, warned.orElseThrow())) {
                return updated.withWarning(Optional.empty(), 0L, 0L);
            }
            final Player player = warned.orElseThrow();
            hound.getNavigation().stop();
            hound.getLookControl().setLookAt(player, 30.0F, 30.0F);
            if (!HellhoundLifeRules.warningGraceElapsed(updated.warningStartedAt(), now)) {
                return updated;
            }
            final Optional<BlockPos> anchor = updated.territoryAnchor();
            final boolean commits = anchor.isPresent() && HellhoundLifeRules.warningCommits(
                player.isAlive(),
                eligibleTarget(hound, player),
                !player.isRemoved(),
                player.level() == level,
                anchor.orElseThrow().distSqr(player.blockPosition())
            );
            updated = updated.withWarning(Optional.empty(), 0L, 0L);
            if (!commits) {
                return updated;
            }
            final Evidence intrusion = HellhoundLifeRules.createEvidence(
                EvidenceKind.TERRITORY_INTRUSION,
                Optional.of(player.getUUID()),
                Optional.of(dimensionId(level)),
                Optional.of(player.blockPosition().asLong()),
                now
            );
            updated = updated.withEvidence(
                HellhoundLifeRules.recordEvidence(updated.evidence(), intrusion, now)
            );
            return broadcastPackCall(hound, level, updated, intrusion, now);
        }
        // Tiny decisions perform no spatial query: intruders are resolved by UUID from the at
        // most four records the 20-tick evidence scan already produced.
        final Optional<Player> intruder = updated.evidence().stream()
            .filter(entry -> entry.valid(now))
            .flatMap(entry -> entry.sourceId().stream())
            .distinct()
            .map(level::getEntity)
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .filter(candidate -> eligibleTarget(hound, candidate))
            .filter(candidate -> hound.distanceToSqr(candidate)
                <= (double) HellhoundLifeRules.WARNING_TRIGGER_RADIUS
                    * HellhoundLifeRules.WARNING_TRIGGER_RADIUS)
            .min(Comparator.comparingDouble(hound::distanceToSqr));
        if (intruder.isEmpty()) {
            return updated;
        }
        final Player player = intruder.orElseThrow();
        if (!HellhoundLifeRules.warningTriggered(
            !updated.bound(),
            true,
            hound.distanceToSqr(player),
            false,
            updated.retreatLatched(),
            hound.getTarget() != null && hound.getTarget().isAlive()
        )) {
            return updated;
        }
        counters.warnings++;
        updated = feedback(hound, level, updated, now, counters, false);
        return updated.withWarning(
            Optional.of(player.getUUID()),
            now,
            HellhoundLifeRules.clampLoadedDeadline(
                HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.WARNING_GRACE_TICKS),
                now, HellhoundLifeRules.WARNING_GRACE_TICKS
            )
        );
    }

    private static HellhoundLifeState tickHeat(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        if (updated.heatPoint().isPresent()) {
            final BlockPos point = updated.heatPoint().orElseThrow();
            // Revalidation is spatial work and therefore runs only at the 200-tick heat
            // cadence under the same charged-read budget as discovery.
            if (HellhoundLifeRules.due(updated.cadence().nextHeatSearchAt(), now)) {
                updated = updated.withCadence(bump(updated.cadence(), now, CadenceField.HEAT));
                counters.chargedBlockReads++;
                if (!level.isLoaded(point) || !isHeatSourceNear(level, point, counters)) {
                    counters.actionCancellations++;
                    return updated.withHeatPoint(Optional.empty(), 0L).withIntent(Intent.IDLE);
                }
            }
            if (hound.blockPosition().distSqr(point) > 4.0D) {
                updated = navigate(hound, updated, point, now, counters);
            } else {
                hound.getNavigation().stop();
                updated = feedback(hound, level, updated, now, counters, true);
            }
            return updated.withIntent(Intent.HEAT_REST);
        }
        if (!HellhoundLifeRules.due(state.cadence().nextHeatSearchAt(), now)) {
            return updated;
        }
        updated = updated.withCadence(bump(updated.cadence(), now, CadenceField.HEAT));
        final Optional<BlockPos> rest = findHeatRest(hound, level, counters);
        if (rest.isEmpty()) {
            return updated;
        }
        return updated.withHeatPoint(
            rest,
            HellhoundLifeRules.saturatingAdd(now, HellhoundLifeRules.HEAT_POINT_TICKS)
        ).withIntent(Intent.HEAT_REST);
    }

    private static Optional<BlockPos> findHeatRest(
        final HellhoundEntity hound,
        final ServerLevel level,
        final Counters counters
    ) {
        final BlockPos origin = hound.blockPosition();
        long reads = 0L;
        Optional<BlockPos> best = Optional.empty();
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos candidate : BlockPos.betweenClosed(
            origin.offset(-HellhoundLifeRules.HEAT_RADIUS, -2, -HellhoundLifeRules.HEAT_RADIUS),
            origin.offset(HellhoundLifeRules.HEAT_RADIUS, 2, HellhoundLifeRules.HEAT_RADIUS)
        )) {
            if (reads >= HellhoundLifeRules.HEAT_MAX_BLOCK_READS) {
                break;
            }
            if (!level.isLoaded(candidate)) {
                continue;
            }
            reads++;
            if (!isHeatBlock(level, candidate)) {
                continue;
            }
            final Optional<BlockPos> rest = TacticalCombatRuntime
                .standableNear(level, candidate.offset(1, 0, 1))
                .filter(position -> !position.equals(candidate))
                .filter(position -> level.getFluidState(position).isEmpty())
                .filter(position -> level.getWorldBorder().isWithinBounds(position))
                .filter(position -> TacticalCombatRuntime.routeReaches(hound, position));
            if (rest.isPresent()) {
                final double distance = rest.orElseThrow().distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = rest;
                }
            }
        }
        counters.chargedBlockReads += reads;
        counters.maximumBlockReadsPerSearch = Math.max(counters.maximumBlockReadsPerSearch, reads);
        return best;
    }

    private static boolean isHeatBlock(final ServerLevel level, final BlockPos position) {
        final var state = level.getBlockState(position);
        return state.is(Blocks.CAMPFIRE) && state.getOptionalValue(
                net.minecraft.world.level.block.CampfireBlock.LIT).orElse(false)
            || state.is(Blocks.SOUL_CAMPFIRE) && state.getOptionalValue(
                net.minecraft.world.level.block.CampfireBlock.LIT).orElse(false)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || level.getFluidState(position).is(net.minecraft.tags.FluidTags.LAVA);
    }

    /**
     * The rest point is selected diagonally adjacent to its heat block, so a Chebyshev-2 shell
     * (75 cells, under the 128-read heat budget) suffices; reads are charged per search.
     */
    private static boolean isHeatSourceNear(
        final ServerLevel level,
        final BlockPos rest,
        final Counters counters
    ) {
        long reads = 0L;
        boolean found = false;
        for (final BlockPos candidate : BlockPos.betweenClosed(
            rest.offset(-2, -1, -2), rest.offset(2, 1, 2)
        )) {
            reads++;
            if (isHeatBlock(level, candidate)) {
                found = true;
                break;
            }
        }
        counters.chargedBlockReads += reads;
        counters.maximumBlockReadsPerSearch = Math.max(counters.maximumBlockReadsPerSearch, reads);
        return found;
    }

    private static HellhoundLifeState tickPatrol(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final Optional<BlockPos> anchor,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        if (updated.destination().isPresent()) {
            updated = navigate(hound, updated, updated.destination().orElseThrow(), now, counters);
            return updated.withIntent(Intent.PATROL);
        }
        if (anchor.isEmpty() || !HellhoundLifeRules.due(updated.cadence().nextPatrolSearchAt(), now)) {
            return updated.withIntent(Intent.IDLE);
        }
        updated = updated.withCadence(bump(updated.cadence(), now, CadenceField.PATROL));
        final BlockPos center = anchor.orElseThrow();
        final int dwell = HellhoundLifeRules.dwellTicks(now + hound.getUUID().getLeastSignificantBits());
        for (int attempt = 0; attempt < HellhoundLifeRules.MAX_PATROL_POINTS; attempt++) {
            final int angleSeed = HellhoundLifeRules.stableOffset(hound.getUUID(), 8) + attempt * 2;
            final int dx = (int) Math.round(Math.cos(angleSeed * Math.PI / 4.0D)
                * (HellhoundLifeRules.PATROL_RADIUS - 2));
            final int dz = (int) Math.round(Math.sin(angleSeed * Math.PI / 4.0D)
                * (HellhoundLifeRules.PATROL_RADIUS - 2));
            final BlockPos candidate = center.offset(dx, 0, dz);
            if (validDestination(hound, level, candidate, counters)) {
                updated = updated.withDestination(
                    TacticalCombatRuntime.standableNear(level, candidate),
                    HellhoundLifeRules.saturatingAdd(now, dwell)
                );
                updated = navigate(hound, updated, updated.destination().orElse(candidate), now, counters);
                return updated.withIntent(Intent.PATROL);
            }
        }
        return updated.withIntent(Intent.IDLE);
    }

    private static HellhoundLifeState retreat(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        hound.setTarget(null);
        // Seek owner, territory, or the nearest loaded same-pack member under ordinary budgets.
        final HellhoundLifeState settled = updated;
        final Optional<BlockPos> refuge = settled.bound()
            ? settled.ownerId().map(level::getPlayerByUUID)
                .filter(LivingEntity::isAlive)
                .map(LivingEntity::blockPosition)
            : settled.territoryAnchor().or(hound::nearestPackmateSnapshot);
        if (refuge.isPresent()) {
            updated = navigate(hound, updated, refuge.orElseThrow(), now, counters);
        }
        final Intent intent = updated.regroupDeadline() > now && !updated.bound()
            ? Intent.REGROUP
            : Intent.RETREAT;
        if (intent == Intent.RETREAT && updated.regroupDeadline() != 0L
            && HellhoundLifeRules.due(updated.regroupDeadline(), now)) {
            updated = updated.withRetreat(updated.retreatLatched(), 0L);
        }
        return updated.withIntent(intent);
    }

    private static HellhoundLifeState navigate(
        final HellhoundEntity hound,
        final HellhoundLifeState state,
        final BlockPos destination,
        final long now,
        final Counters counters
    ) {
        HellhoundLifeState updated = state;
        if (!HellhoundLifeRules.due(updated.cadence().nextNavigationAt(), now)
            || updated.routeRetryAfter() > now) {
            return updated;
        }
        updated = updated.withCadence(bump(updated.cadence(), now, CadenceField.NAVIGATION));
        counters.navigationRequests++;
        final boolean requested = hound.getNavigation().moveTo(
            destination.getX() + 0.5D,
            destination.getY(),
            destination.getZ() + 0.5D,
            1.0D
        );
        if (requested) {
            if (updated.routeFailures() != 0) {
                updated = updated.withRouteFailures(0, Optional.empty(), 0L);
            }
            return updated;
        }
        counters.routeFailures++;
        final int failures = HellhoundLifeRules.nextRouteFailures(updated.routeFailures());
        updated = updated.withRouteFailures(
            failures,
            Optional.of(RouteFailure.NO_ROUTE),
            HellhoundLifeRules.routeBackoffUntil(failures, now)
        );
        if (failures >= HellhoundLifeRules.MAX_ROUTE_FAILURES) {
            updated = cancelActiveClaims(hound, updated, counters).withRouteFailures(
                failures, Optional.of(RouteFailure.NO_ROUTE),
                HellhoundLifeRules.routeBackoffUntil(failures, now)
            );
        }
        return updated;
    }

    private static boolean validDestination(
        final HellhoundEntity hound,
        final ServerLevel level,
        final BlockPos candidate,
        final Counters counters
    ) {
        counters.chargedBlockReads += 2;
        if (!level.isLoaded(candidate) || !level.getWorldBorder().isWithinBounds(candidate)) {
            return false;
        }
        final Optional<BlockPos> standable = TacticalCombatRuntime.standableNear(level, candidate);
        if (standable.isEmpty()) {
            return false;
        }
        final BlockPos position = standable.orElseThrow();
        final AABB volume = hound.getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(position));
        return level.noCollision(hound, volume) && TacticalCombatRuntime.routeReaches(hound, position);
    }

    private static HellhoundLifeState maybeCancelCombatClaims(
        final HellhoundEntity hound,
        final HellhoundLifeState state,
        final Counters counters
    ) {
        if (hound.getTarget() == null && state.biteWindupStartedAt() == 0L
            && state.packRole().isEmpty()) {
            return state;
        }
        return cancelActiveClaims(hound, state, counters);
    }

    /** Cancellation clears the lower action's destination, sector, and expiry as one unit. */
    private static HellhoundLifeState cancelActiveClaims(
        final HellhoundEntity hound,
        final HellhoundLifeState state,
        final Counters counters
    ) {
        counters.actionCancellations++;
        hound.setTarget(null);
        hound.getNavigation().stop();
        return cancelClaimUnits(state);
    }

    /** The state-only half of cancellation; the hazard branch must not stop navigation. */
    private static HellhoundLifeState cancelClaimUnits(final HellhoundLifeState state) {
        return state.withWarning(Optional.empty(), 0L, 0L)
            .withPackRole(Optional.empty(), 0L, state.targetEvidenceEpoch())
            .withDestination(Optional.empty(), 0L)
            .withBiteWindows(0L, 0L, state.biteRecoveryUntil());
    }

    private static HellhoundLifeState feedback(
        final HellhoundEntity hound,
        final ServerLevel level,
        final HellhoundLifeState state,
        final long now,
        final Counters counters,
        final boolean ambient
    ) {
        final long nextAt = ambient
            ? state.cadence().nextAmbientFeedbackAt()
            : state.cadence().nextEventFeedbackAt();
        if (!HellhoundLifeRules.due(nextAt, now)) {
            return state;
        }
        counters.feedbackEvents++;
        if (ambient) {
            level.sendParticles(
                ParticleTypes.SMALL_FLAME,
                hound.getX(), hound.getY() + hound.getBbHeight() * 0.6D, hound.getZ(),
                3, 0.2D, 0.1D, 0.2D, 0.005D
            );
        } else {
            level.playSound(null, hound.blockPosition(), SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE, 0.8F, 0.7F);
        }
        return state.withCadence(bump(state.cadence(), now,
            ambient ? CadenceField.AMBIENT_FEEDBACK : CadenceField.EVENT_FEEDBACK));
    }

    private static String dimensionId(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
