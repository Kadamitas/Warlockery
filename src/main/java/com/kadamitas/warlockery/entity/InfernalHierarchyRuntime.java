package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.InfernalHierarchyRules.AuthorityClass;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.MemberCandidate;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.OrderKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public final class InfernalHierarchyRuntime {
    private InfernalHierarchyRuntime() {
    }

    public static final class Counters {
        long observationScans;
        long candidateVisits;
        long lineOfSightChecks;
        long blockReads;
        long anchorCandidateVisits;
        // Transient pass-to-pass follow distance for rejected-episode detection. Never persisted.
        double navEpisodeDistance = Double.MAX_VALUE;
        // Transient fear-episode facts. A save during the ten-tick fear telegraph re-telegraphs on
        // reload, which is the safe direction, so neither fact is persisted.
        long fearTelegraphAt;
        long lastFearPulseAt;
        long fearTelegraphs;
        long fearPulses;
        long squadProvocations;
        long navigationRequests;
        long groupRefreshes;
        long orderIssues;
        long orderCancellations;
        long truceRefreshes;
        long moraleEvents;
        long phaseTransitions;
        long summonConstructions;
        long summonCleanups;
        long releases;

        public long observationScans() { return observationScans; }
        public long fearTelegraphs() { return fearTelegraphs; }
        public long fearPulses() { return fearPulses; }
        public long squadProvocations() { return squadProvocations; }
        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long blockReads() { return blockReads; }
        public long anchorCandidateVisits() { return anchorCandidateVisits; }
        public long navigationRequests() { return navigationRequests; }
        public long groupRefreshes() { return groupRefreshes; }
        public long orderIssues() { return orderIssues; }
        public long orderCancellations() { return orderCancellations; }
        public long truceRefreshes() { return truceRefreshes; }
        public long moraleEvents() { return moraleEvents; }
        public long phaseTransitions() { return phaseTransitions; }
        public long summonConstructions() { return summonConstructions; }
        public long summonCleanups() { return summonCleanups; }
        public long releases() { return releases; }
    }

    /**
     * Bounded pass-local facts produced by the one observation query and consumed by rank intent
     * selection. Nothing here is persisted, so no observation result can outlive its decision.
     */
    record Observation(
        boolean intruderInTerritory,
        boolean ownerUnderAttack,
        boolean attackerWithinCloseRange,
        boolean hostileWithinFearRadius,
        // Pass-local resolved actors for the same pass's target acquisition, nullable, never persisted.
        LivingEntity intruder,
        LivingEntity ownerAttacker
    ) {
        static Observation none() {
            return new Observation(false, false, false, false, null, null);
        }
    }

    record ObservationPass(InfernalHierarchyState state, Observation observation) {
    }

    public static void tick(final InfernalHierarchyEntity entity, final ServerLevel level) {
        final long now = level.getGameTime();
        InfernalHierarchyState state = entity.hierarchyState();
        if (!InfernalHierarchyRules.due(state.cadence().nextDecisionAt(), now)) {
            return;
        }
        state = state.withCadence(new InfernalHierarchyState.Cadence(
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.DECISION_INTERVAL_TICKS),
            state.cadence().nextObservationAt(),
            state.cadence().nextGroupRefreshAt(),
            state.cadence().nextAnchorSearchAt(),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        state = reconcileAuthority(entity, state);
        state = recoverMorale(state, now);
        state = expireClaims(entity, state, now);
        state = tickSummonLifecycle(entity, level, state, now);
        if (!entity.isAlive()) {
            entity.setHierarchyState(state);
            return;
        }
        Observation observation = Observation.none();
        boolean observed = false;
        if (InfernalHierarchyRules.due(state.cadence().nextObservationAt(), now)) {
            observed = true;
            state = state.withCadence(new InfernalHierarchyState.Cadence(
                state.cadence().nextDecisionAt(),
                InfernalHierarchyRules.saturatingAdd(now, observationIntervalTicks(entity.hierarchyRank())),
                state.cadence().nextGroupRefreshAt(),
                state.cadence().nextAnchorSearchAt(),
                state.cadence().nextNavigationAt(),
                state.cadence().nextFeedbackAt()
            ));
            final ObservationPass pass = observe(entity, level, state, now);
            state = pass.state();
            observation = pass.observation();
        }
        state = reconcileLeader(entity, level, state, now);
        final Intent previousIntent = state.intent();
        // Group orders must use this tick's leader facts, not an intent retained from the previous
        // decision. Keep this prediction non-mutating: the real selection below must still happen
        // exactly once so WARN and other staged transitions cannot advance twice in one tick.
        final Intent commandIntent = decideIntent(
            entity, level, state, observation, observed, false, now
        ).intent();
        boolean commandIssued = false;
        if (entity.hierarchyRank() != Rank.DEMON
            && InfernalHierarchyRules.due(state.cadence().nextGroupRefreshAt(), now)) {
            state = state.withCadence(new InfernalHierarchyState.Cadence(
                state.cadence().nextDecisionAt(),
                state.cadence().nextObservationAt(),
                InfernalHierarchyRules.saturatingAdd(now, groupRefreshIntervalTicks(entity.hierarchyRank())),
                state.cadence().nextAnchorSearchAt(),
                state.cadence().nextNavigationAt(),
                state.cadence().nextFeedbackAt()
            ));
            state = refreshGroup(entity, level, state, now, commandIntent);
            commandIssued = state.roster().stream().anyMatch(member -> member.valid(now));
        }
        state = maybeClaimDemonPost(entity, level, state, now);
        state = maybeClaimArchfiendAnchor(entity, level, state, now);
        state = maybeClaimDeepAnchor(entity, level, state, now);
        state = advancePhase(entity, level, state, now);
        state = decideIntent(entity, level, state, observation, observed, commandIssued, now);
        acquireCombatTarget(entity, level, state, observation, now);
        state = applyIntent(entity, level, state, now);
        state = tickNavigation(entity, level, state, now);
        entity.setHierarchyState(state);
        if (entity.hierarchyRank() == Rank.EMBERHORN_ARCHFIEND
            && entity.hierarchyState().intent() == Intent.EMBER_FRONT) {
            advanceEmberFront(entity, level, previousIntent == Intent.EMBER_FRONT);
        }
    }

    /**
     * Rank intent selection. Hazard outranks combat, combat outranks routine, and morale drives the
     * retreat hysteresis exactly as the approved design requires.
     */
    static InfernalHierarchyState decideIntent(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final Observation observation,
        final boolean observed,
        final boolean commandIssued,
        final long now
    ) {
        // A standing warning persists until the next observation revalidates the territory. Intruder
        // facts only exist on observation passes, so without this a live warning would flap back to
        // routine every intermediate decision and pressure could never follow the warning window.
        final boolean intruderStanding = observation.intruderInTerritory()
            || (!observed && state.intent() == Intent.WARN);
        final float maxHealth = Math.max(1.0F, entity.getMaxHealth());
        final float healthFraction = entity.getHealth() / maxHealth;
        final boolean hazard = entity.isUnderWater()
            && entity.getAirSupply() < entity.getMaxAirSupply();
        final boolean truceActive = truceHeld(level, state, now);
        final boolean aggressorValid = state.aggressorId().isPresent()
            && state.aggressorExpiresAt() > now;
        final boolean anchorHeld = state.anchorPos().isPresent() && state.anchorExpiresAt() > now;
        final boolean anchorSearchDue = InfernalHierarchyRules.due(
            state.cadence().nextAnchorSearchAt(), now
        );
        final Optional<InfernalHierarchyState.Order> order = state.order().filter(row -> row.valid(now));
        final int validMembers = (int) state.roster().stream().filter(row -> row.valid(now)).count();
        final boolean threatPresent = aggressorValid
            || loadedChallenger(level, state, now).isPresent();
        final Intent selected = switch (entity.hierarchyRank()) {
            case DEMON -> InfernalHierarchyRules.selectDemonIntent(
                new InfernalHierarchyRules.DemonIntentFacts(
                    hazard,
                    healthFraction <= InfernalHierarchyRules.RETREAT_HEALTH_FRACTION,
                    InfernalHierarchyRules.moraleRetreatRequired(state.morale(), healthFraction),
                    InfernalHierarchyRules.mayReenterPressure(
                        state.morale(),
                        observation.attackerWithinCloseRange(),
                        observation.ownerUnderAttack()
                    ),
                    // A route backoff and a carried withdraw or dissolve instruction both unmoor the
                    // Demon into the bounded return, which is the designed regroup behavior for a
                    // dissolved or withdrawing group.
                    !InfernalHierarchyRules.due(state.actionBackoffUntil(), now)
                        || order.filter(row -> switch (row.kind()) {
                            case WITHDRAW, WITHDRAW_TO_ANCHOR, DISSOLVE -> true;
                            default -> false;
                        }).isPresent(),
                    truceActive,
                    InfernalHierarchyRules.acceptsPlayerAuthority(Rank.DEMON)
                        && state.authorityId().isPresent()
                        && (state.authorityClass() == AuthorityClass.DIRECT_PACT
                            || state.authorityClass() == AuthorityClass.ANIMUS),
                    observation.ownerUnderAttack(),
                    ownerBeyondFollowRange(entity, level, state),
                    order.filter(row -> switch (row.kind()) {
                        case HOLD_POST, HOLD_COURT, SCREEN, SCREEN_REGENT -> true;
                        default -> false;
                    }).isPresent(),
                    aggressorValid || order.filter(row -> row.kind() == OrderKind.FOCUS_CHALLENGER).isPresent(),
                    intruderStanding,
                    // The warning stands until the next observation revalidates the territory, which is
                    // at least the designed twenty ticks. Only that revalidating pass can authorize
                    // pressure, and a close direct attacker skips the warning outright.
                    !(observed && state.intent() == Intent.WARN)
                        && !observation.attackerWithinCloseRange(),
                    anchorHeld,
                    AmbientActivityRules.isDay(level.getDefaultClockTime())
                )
            );
            case EMBERHORN_ARCHFIEND -> InfernalHierarchyRules.selectArchfiendIntent(
                new InfernalHierarchyRules.ArchfiendIntentFacts(
                    hazard,
                    healthFraction <= 0.25F,
                    InfernalHierarchyRules.rallyRewardDue(state.lastRallyAt(), now) && validMembers > 0,
                    order.filter(row -> row.kind() == OrderKind.WITHDRAW
                        || row.kind() == OrderKind.WITHDRAW_TO_ANCHOR).isPresent(),
                    state.roster().size() > validMembers,
                    loadedChallenger(level, state, now).isPresent() && !truceActive,
                    InfernalHierarchyRules.due(state.actionBackoffUntil(), now) && !truceActive,
                    intruderStanding,
                    validMembers < InfernalHierarchyRules.memberCap(Rank.EMBERHORN_ARCHFIEND),
                    anchorHeld,
                    anchorSearchDue
                )
            );
            case ABYSSAL_REGENT -> InfernalHierarchyRules.selectRegentIntent(
                new InfernalHierarchyRules.RegentIntentFacts(
                    hazard,
                    state.phaseState() == PhaseState.TELEGRAPH,
                    state.phaseState() == PhaseState.COMMIT,
                    state.phaseState() == PhaseState.RECOVERY,
                    level.getDifficulty() == Difficulty.PEACEFUL && validMembers > 0,
                    displaceReady(entity),
                    observation.hostileWithinFearRadius(),
                    commandIssued,
                    threatPresent && validMembers > 0,
                    threatPresent && validMembers == 0,
                    anchorHeld,
                    anchorSearchDue
                )
            );
        };
        return selected == state.intent() ? state : state.withIntent(selected);
    }

    static Optional<LivingEntity> loadedChallenger(
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        if (state.challengerId().isEmpty() || state.challengerExpiresAt() <= now) {
            return Optional.empty();
        }
        final Entity challenger = level.getEntity(state.challengerId().orElseThrow());
        return challenger instanceof LivingEntity living && living.isAlive() && living.level() == level
            ? Optional.of(living)
            : Optional.empty();
    }

    private static boolean displaceReady(final InfernalHierarchyEntity entity) {
        final LivingEntity struck = entity.getLastHurtMob();
        return struck != null
            && struck.isAlive()
            && entity.getLastHurtMobTimestamp() + InfernalHierarchyRules.ATTRIBUTION_FRESHNESS_TICKS
                >= entity.tickCount;
    }

    private static boolean ownerBeyondFollowRange(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState state
    ) {
        if (state.authorityId().isEmpty()) return false;
        final Entity owner = level.getEntity(state.authorityId().orElseThrow());
        if (!(owner instanceof Player player) || player.level() != level) {
            return false;
        }
        final double range = InfernalHierarchyRules.DEMON_FOLLOW_RADIUS;
        return entity.distanceToSqr(player) > range * range;
    }

    /**
     * The designed cancellation contract. Retreat, return, withdrawal, dissolution, truce, and hazard
     * clear target and navigation before a safe point is chosen. No other intent writes movement, so
     * one movement authority remains.
     */
    static InfernalHierarchyState applyIntent(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        if (state.intent() == Intent.RALLY) {
            return pulseRally(entity, level, state, now);
        }
        if (!InfernalHierarchyRules.cancelsExecution(state.intent())) {
            return state;
        }
        if (entity.getTarget() != null) {
            entity.setTarget(null);
        }
        if (!entity.getNavigation().isDone()) {
            entity.getNavigation().stop();
        }
        if (state.intent() == Intent.DISSOLVE && entity.hierarchyRank() == Rank.ABYSSAL_REGENT) {
            return dissolveCourt(entity, level, state, now);
        }
        return state;
    }

    /**
     * A live dissolving Regent issues the DISSOLVE instruction to every valid loaded member and
     * releases its roster in the same decision, so the dissolution arm executes in real play rather
     * than existing only as a selectable label.
     */
    private static InfernalHierarchyState dissolveCourt(
        final InfernalHierarchyEntity regent,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        final long expiresAt = InfernalHierarchyRules.saturatingAdd(
            now, InfernalHierarchyRules.REGENT_ORDER_TICKS
        );
        for (final InfernalHierarchyState.Member row : state.roster()) {
            if (!row.valid(now)) continue;
            if (!(level.getEntity(row.id()) instanceof InfernalHierarchyEntity member)
                || !member.isAlive()
                || member.level() != level
                || member.hierarchyState().leaderId().filter(regent.getUUID()::equals).isEmpty()) {
                continue;
            }
            if (!InfernalHierarchyRules.mayIssueOrder(
                Rank.ABYSSAL_REGENT, member.hierarchyRank(), OrderKind.DISSOLVE
            )) {
                continue;
            }
            regent.hierarchyCounters().orderIssues++;
            member.setHierarchyState(releaseLeader(member, member.hierarchyState(), true)
                .withOrder(Optional.of(new InfernalHierarchyState.Order(
                    OrderKind.DISSOLVE, Optional.empty(), expiresAt,
                    member.hierarchyState().orderEpoch(), Rank.ABYSSAL_REGENT
                ))));
        }
        return state.withRoster(List.of(), state.orderEpoch() + 1L);
    }

    /**
     * The live target priority contract. Direct player-owner attacker, valid higher-rank focus order,
     * provoked stable challenger, direct aggressor, then territorial intruder, exactly as approved.
     * Every candidate passes through the entity's own {@code canAttack}, which is the single hierarchy
     * predicate, so Archfiend restraint, truce, court allegiance, and protected classes all hold on the
     * acquisition path too. The authored acquisition objective is never cleared here: when no candidate
     * qualifies the current target claim is left untouched.
     */
    static void acquireCombatTarget(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final Observation observation,
        final long now
    ) {
        if (!InfernalHierarchyRules.engagesTarget(state.intent())) {
            return;
        }
        final List<LivingEntity> candidates = new ArrayList<>();
        if (observation.ownerAttacker() != null) {
            candidates.add(observation.ownerAttacker());
        }
        state.order()
            .filter(row -> row.valid(now) && row.kind() == OrderKind.FOCUS_CHALLENGER)
            .flatMap(InfernalHierarchyState.Order::targetId)
            .map(level::getEntity)
            .filter(resolved -> resolved instanceof LivingEntity living
                && living.isAlive() && living.level() == level)
            .ifPresent(resolved -> candidates.add((LivingEntity) resolved));
        loadedChallenger(level, state, now).ifPresent(candidates::add);
        if (state.aggressorId().isPresent() && state.aggressorExpiresAt() > now) {
            final Entity aggressor = level.getEntity(state.aggressorId().orElseThrow());
            if (aggressor instanceof LivingEntity living && living.isAlive() && living.level() == level) {
                candidates.add(living);
            }
        }
        if (observation.intruder() != null) {
            candidates.add(observation.intruder());
        }
        for (final LivingEntity candidate : candidates) {
            if (candidate == entity || !candidate.isAlive() || !entity.canAttack(candidate)) {
                continue;
            }
            if (entity.getTargetUnchecked() != candidate) {
                entity.setTarget(candidate);
            }
            return;
        }
    }

    /**
     * The one live navigation writer outside the engine's own melee pursuit. A pact-bound Demon beyond
     * its follow radius requests one path per navigation interval, and a request that ends without
     * closing the distance is a rejected navigation episode: it charges the route-failure ladder, so
     * three rejections produce the designed one-hundred-tick backoff and a bounded return.
     */
    static InfernalHierarchyState tickNavigation(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        if (input.intent() == Intent.RETREAT || input.intent() == Intent.RETURN
            || input.intent() == Intent.WITHDRAW) {
            return tickWithdrawNavigation(entity, level, input, now);
        }
        if (input.intent() != Intent.PACT_FOLLOW) {
            return input;
        }
        if (!InfernalHierarchyRules.due(input.cadence().nextNavigationAt(), now)
            || !InfernalHierarchyRules.due(input.actionBackoffUntil(), now)) {
            return input;
        }
        final Entity owner = input.authorityId().map(level::getEntity).orElse(null);
        if (!(owner instanceof Player player) || player.level() != level || !player.isAlive()) {
            return input;
        }
        InfernalHierarchyState state = input.withCadence(new InfernalHierarchyState.Cadence(
            input.cadence().nextDecisionAt(),
            input.cadence().nextObservationAt(),
            input.cadence().nextGroupRefreshAt(),
            input.cadence().nextAnchorSearchAt(),
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.NAVIGATION_INTERVAL_TICKS),
            input.cadence().nextFeedbackAt()
        ));
        final Counters counters = entity.hierarchyCounters();
        final double distance = entity.distanceTo(player);
        // A follow episode that ends, stalls, or makes no meaningful progress over one full
        // navigation interval is a rejected navigation episode in the design's sense.
        final boolean previousEpisodeRejected = counters.navigationRequests > 0
            && (entity.getNavigation().isDone()
                || entity.getNavigation().isStuck()
                || distance >= counters.navEpisodeDistance - 0.75D);
        counters.navEpisodeDistance = distance;
        if (previousEpisodeRejected) {
            state = recordRouteFailure(entity, state, now);
            if (!InfernalHierarchyRules.due(state.actionBackoffUntil(), now)) {
                counters.navEpisodeDistance = Double.MAX_VALUE;
                return state;
            }
        }
        counters.navigationRequests++;
        if (!entity.getNavigation().moveTo(player, 1.0D)) {
            state = recordRouteFailure(entity, state, now);
        }
        return state;
    }

    /**
     * A retreat, return, or withdrawal is a movement toward a real point rather than standing in
     * place: the held anchor when one exists and is loaded, otherwise the live leader. One bounded
     * path request per navigation interval; a refused request charges the route-failure ladder. With
     * neither point available the rank holds its ground, which is the safe pre-existing behavior for
     * a fully unmoored body.
     */
    static InfernalHierarchyState tickWithdrawNavigation(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        if (!InfernalHierarchyRules.due(input.cadence().nextNavigationAt(), now)
            || !InfernalHierarchyRules.due(input.actionBackoffUntil(), now)) {
            return input;
        }
        net.minecraft.world.phys.Vec3 point = null;
        if (input.anchorPos().isPresent() && input.anchorExpiresAt() > now) {
            final BlockPos anchor = BlockPos.of(input.anchorPos().orElseThrow());
            if (level.hasChunkAt(anchor)) {
                point = net.minecraft.world.phys.Vec3.atBottomCenterOf(anchor);
            }
        }
        if (point == null && input.leaderId().isPresent() && input.membershipLeaseUntil() > now) {
            if (level.getEntity(input.leaderId().orElseThrow()) instanceof InfernalHierarchyEntity leader
                && leader.isAlive() && leader.level() == level) {
                point = leader.position();
            }
        }
        if (point == null || entity.position().distanceToSqr(point) <= 4.0D) {
            return input;
        }
        final InfernalHierarchyState state = input.withCadence(new InfernalHierarchyState.Cadence(
            input.cadence().nextDecisionAt(),
            input.cadence().nextObservationAt(),
            input.cadence().nextGroupRefreshAt(),
            input.cadence().nextAnchorSearchAt(),
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.NAVIGATION_INTERVAL_TICKS),
            input.cadence().nextFeedbackAt()
        ));
        entity.hierarchyCounters().navigationRequests++;
        if (!entity.getNavigation().moveTo(point.x(), point.y(), point.z(), 1.0D)) {
            return recordRouteFailure(entity, state, now);
        }
        return state;
    }

    /**
     * The Demon warm post claim. An unbound, unordered Demon may claim one already loaded warm post
     * within eight blocks: a lit campfire or a magical cauldron qualifies and raw fire never does. The
     * claim is what makes POST_WATCH, APPRAISE, WARN, and PRESS reachable in real play. Every candidate
     * charges its read before any block state is inspected, under the approved sixty-four read budget.
     */
    static InfernalHierarchyState maybeClaimDemonPost(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        if (entity.hierarchyRank() != Rank.DEMON) {
            return input;
        }
        InfernalHierarchyState state = input;
        final boolean bound = state.authorityClass() == AuthorityClass.DIRECT_PACT
            || state.authorityClass() == AuthorityClass.ANIMUS;
        final boolean ordered = state.order().filter(row -> row.valid(now)).isPresent();
        if (bound || ordered) {
            return state.anchorPos().isPresent() ? state.withAnchor(Optional.empty(), 0L) : state;
        }
        if (state.anchorPos().isPresent() && state.anchorExpiresAt() > now) {
            final BlockPos post = BlockPos.of(state.anchorPos().orElseThrow());
            if (level.hasChunkAt(post)) {
                entity.hierarchyCounters().blockReads++;
                if (warmPost(level, post)) {
                    return state.withAnchor(state.anchorPos(), InfernalHierarchyRules.saturatingAdd(
                        now, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS
                    ));
                }
            }
            state = state.withAnchor(Optional.empty(), 0L);
        }
        if (!InfernalHierarchyRules.due(state.cadence().nextAnchorSearchAt(), now)) {
            return state;
        }
        state = state.withCadence(new InfernalHierarchyState.Cadence(
            state.cadence().nextDecisionAt(),
            state.cadence().nextObservationAt(),
            state.cadence().nextGroupRefreshAt(),
            InfernalHierarchyRules.saturatingAdd(
                now, InfernalHierarchyRules.DEMON_ANCHOR_SEARCH_INTERVAL_TICKS
            ),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        int reads = 0;
        for (final BlockPos candidate : BlockPos.withinManhattan(
            entity.blockPosition(),
            InfernalHierarchyRules.DEMON_ANCHOR_RADIUS,
            2,
            InfernalHierarchyRules.DEMON_ANCHOR_RADIUS
        )) {
            if (reads >= InfernalHierarchyRules.DEMON_ANCHOR_BLOCK_READS) {
                break;
            }
            if (!level.hasChunkAt(candidate)) {
                continue;
            }
            reads++;
            entity.hierarchyCounters().blockReads++;
            if (warmPost(level, candidate)) {
                return state.withAnchor(
                    Optional.of(candidate.asLong()),
                    InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS)
                );
            }
        }
        return state;
    }

    private static boolean warmPost(final ServerLevel level, final BlockPos position) {
        final net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(position);
        if (blockState.is(CreatureBehaviorTags.Blocks.MAGICAL_CAULDRONS)) {
            return true;
        }
        return blockState.is(BlockTags.CAMPFIRES)
            && blockState.getOptionalValue(
                net.minecraft.world.level.block.CampfireBlock.LIT
            ).orElse(false);
    }

    /**
     * A rally pulse raises squad morale once per two hundred ticks and never loops while retreating.
     */
    static InfernalHierarchyState pulseRally(
        final InfernalHierarchyEntity leader,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        if (!InfernalHierarchyRules.rallyRewardDue(state.lastRallyAt(), now)) {
            return state;
        }
        for (final InfernalHierarchyState.Member member : state.roster()) {
            if (!member.valid(now)) continue;
            final Entity resolved = level.getEntity(member.id());
            if (!(resolved instanceof InfernalHierarchyEntity ally) || !ally.isAlive()) continue;
            final InfernalHierarchyState allyState = ally.hierarchyState();
            if (!InfernalHierarchyRules.rallyRewardDue(allyState.lastRallyAt(), now)) continue;
            ally.setHierarchyState(applyMoraleEvent(
                ally, allyState, InfernalHierarchyRules.MORALE_RALLY_REWARD, now
            ).withMoraleEvents(allyState.lastAllyLossAt(), now));
        }
        return state.withMoraleEvents(state.lastAllyLossAt(), now);
    }

    /**
     * One same-court ally death observed within sixteen blocks costs one hundred twenty morale, no more
     * than once per forty ticks. The event is raised at the ally's death rather than sampled later, so a
     * short death animation can never hide it from the bounded observation cadence.
     */
    public static void recordAllyLoss(final InfernalHierarchyEntity fallen, final ServerLevel level) {
        final long now = level.getGameTime();
        final Optional<UUID> court = fallen.hierarchyState().leaderId();
        final List<InfernalHierarchyEntity> witnesses = level.getEntitiesOfClass(
            InfernalHierarchyEntity.class,
            fallen.getBoundingBox().inflate(InfernalHierarchyRules.MORALE_ALLY_LOSS_RADIUS),
            candidate -> candidate != fallen && candidate.isAlive()
        );
        int visited = 0;
        for (final InfernalHierarchyEntity witness : witnesses) {
            if (visited >= InfernalHierarchyRules.REGENT_RETAINED_CANDIDATES) break;
            visited++;
            final InfernalHierarchyState state = witness.hierarchyState();
            final boolean sameCourt = state.leaderId().filter(fallen.getUUID()::equals).isPresent()
                || court.filter(witness.getUUID()::equals).isPresent()
                || (court.isPresent() && court.equals(state.leaderId()));
            if (!sameCourt) continue;
            if (!InfernalHierarchyRules.allyLossPenaltyDue(state.lastAllyLossAt(), now)) continue;
            witness.setHierarchyState(applyMoraleEvent(
                witness, state, -InfernalHierarchyRules.MORALE_ALLY_LOSS_PENALTY, now
            ).withMoraleEvents(now, state.lastRallyAt()));
        }
    }

    /**
     * The Regent leader-loss command contract. On a Regent's death one rostered, loaded Archfiend
     * within twenty-four blocks is selected deterministically as the withdrawal captain and every
     * loaded member carries the court's WITHDRAW_TO_ANCHOR instruction; without such an Archfiend
     * every loaded member receives DISSOLVE immediately. The membership lease is left in place so the
     * members' own next decision still applies the leader-loss morale event and bounded regroup, with
     * the parting instruction surviving that release. An Archfiend's death issues nothing: its
     * standalone squad releases through the ordinary lease reconciliation, as approved. Members
     * resolve through the direct UUID index only, so nothing is force loaded.
     */
    public static void dissolveCommandOnLeaderDeath(
        final InfernalHierarchyEntity fallen,
        final ServerLevel level
    ) {
        if (fallen.hierarchyRank() != Rank.ABYSSAL_REGENT) {
            return;
        }
        final long now = level.getGameTime();
        final InfernalHierarchyState state = fallen.hierarchyState();
        final List<InfernalHierarchyEntity> members = new ArrayList<>();
        for (final InfernalHierarchyState.Member row : state.roster()) {
            if (!row.valid(now)) continue;
            if (level.getEntity(row.id()) instanceof InfernalHierarchyEntity member
                && member.isAlive()
                && member.level() == level
                && member.hierarchyState().leaderId().filter(fallen.getUUID()::equals).isPresent()) {
                members.add(member);
            }
        }
        if (members.isEmpty()) {
            return;
        }
        final double captainRange = (double) InfernalHierarchyRules.WITHDRAWAL_CAPTAIN_RADIUS
            * InfernalHierarchyRules.WITHDRAWAL_CAPTAIN_RADIUS;
        final Optional<InfernalHierarchyEntity> captain = members.stream()
            .filter(member -> member.hierarchyRank() == Rank.EMBERHORN_ARCHFIEND)
            .filter(member -> fallen.distanceToSqr(member) <= captainRange)
            .min(java.util.Comparator.comparing(Entity::getUUID, InfernalHierarchyRules.unsignedUuidOrder()));
        final OrderKind kind = captain.isPresent() ? OrderKind.WITHDRAW_TO_ANCHOR : OrderKind.DISSOLVE;
        final long expiresAt = InfernalHierarchyRules.saturatingAdd(
            now, InfernalHierarchyRules.ARCHFIEND_ORDER_TICKS
        );
        for (final InfernalHierarchyEntity member : members) {
            if (!InfernalHierarchyRules.mayIssueOrder(Rank.ABYSSAL_REGENT, member.hierarchyRank(), kind)) {
                continue;
            }
            final InfernalHierarchyState memberState = member.hierarchyState();
            fallen.hierarchyCounters().orderIssues++;
            member.setTarget(null);
            member.getNavigation().stop();
            member.setHierarchyState(memberState.withOrder(Optional.of(new InfernalHierarchyState.Order(
                kind, Optional.empty(), expiresAt, memberState.orderEpoch(), Rank.ABYSSAL_REGENT
            ))));
        }
    }

    static InfernalHierarchyState maybeClaimArchfiendAnchor(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        if (entity.hierarchyRank() != Rank.EMBERHORN_ARCHFIEND) {
            return input;
        }
        InfernalHierarchyState state = input;
        if (state.anchorPos().isPresent() && state.anchorExpiresAt() > now) {
            final BlockPos anchor = BlockPos.of(state.anchorPos().orElseThrow());
            if (level.hasChunkAt(anchor)) {
                entity.hierarchyCounters().blockReads++;
                if (level.getBlockState(anchor).is(CreatureBehaviorTags.Blocks.MAGICAL_CAULDRONS)) {
                    return state.withAnchor(state.anchorPos(), InfernalHierarchyRules.saturatingAdd(
                        now, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS
                    ));
                }
            }
            state = state.withAnchor(Optional.empty(), 0L);
        }
        if (!InfernalHierarchyRules.due(state.cadence().nextAnchorSearchAt(), now)) {
            return state;
        }
        state = state.withCadence(new InfernalHierarchyState.Cadence(
            state.cadence().nextDecisionAt(),
            state.cadence().nextObservationAt(),
            state.cadence().nextGroupRefreshAt(),
            InfernalHierarchyRules.saturatingAdd(
                now, InfernalHierarchyRules.ARCHFIEND_ANCHOR_SEARCH_INTERVAL_TICKS
            ),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        int reads = 0;
        for (final BlockPos candidate : BlockPos.withinManhattan(
            entity.blockPosition(),
            InfernalHierarchyRules.ARCHFIEND_ANCHOR_RADIUS,
            2,
            InfernalHierarchyRules.ARCHFIEND_ANCHOR_RADIUS
        )) {
            if (reads >= InfernalHierarchyRules.ARCHFIEND_ANCHOR_BLOCK_READS) {
                break;
            }
            if (!level.hasChunkAt(candidate)) {
                continue;
            }
            reads++;
            entity.hierarchyCounters().blockReads++;
            if (level.getBlockState(candidate).is(CreatureBehaviorTags.Blocks.MAGICAL_CAULDRONS)) {
                return state.withAnchor(
                    Optional.of(candidate.asLong()),
                    InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS)
                );
            }
        }
        return state;
    }

    /**
     * The Regent deep anchor. A loaded, world border valid, collision safe stand point with hidden sky
     * and either adjacent water or low local light. Every candidate charges its actual reads and the
     * search stops at the approved read budget, so a cell edge can never force a chunk load.
     */
    static InfernalHierarchyState maybeClaimDeepAnchor(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        if (entity.hierarchyRank() != Rank.ABYSSAL_REGENT) {
            return input;
        }
        InfernalHierarchyState state = input;
        if (state.anchorPos().isPresent() && state.anchorExpiresAt() > now) {
            return state;
        }
        if (!InfernalHierarchyRules.due(state.cadence().nextAnchorSearchAt(), now)) {
            return state;
        }
        state = state.withCadence(new InfernalHierarchyState.Cadence(
            state.cadence().nextDecisionAt(),
            state.cadence().nextObservationAt(),
            state.cadence().nextGroupRefreshAt(),
            InfernalHierarchyRules.saturatingAdd(
                now, InfernalHierarchyRules.REGENT_ANCHOR_SEARCH_INTERVAL_TICKS
            ),
            state.cadence().nextNavigationAt(),
            state.cadence().nextFeedbackAt()
        ));
        int reads = 0;
        for (final BlockPos candidate : BlockPos.withinManhattan(
            entity.blockPosition(),
            InfernalHierarchyRules.REGENT_ANCHOR_RADIUS,
            4,
            InfernalHierarchyRules.REGENT_ANCHOR_RADIUS
        )) {
            if (reads >= InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS) {
                break;
            }
            if (!level.hasChunkAt(candidate)) {
                // The loaded-chunk guard reads no state; everything after it charges the budget.
                continue;
            }
            entity.hierarchyCounters().anchorCandidateVisits++;
            // The world-border and sky filters are actual reads, so they are charged before they run.
            // An open-sky Regent therefore burns its budget in at most one hundred twenty eight
            // positions rather than walking the entire uncharged candidate volume.
            reads++;
            entity.hierarchyCounters().blockReads++;
            if (!level.getWorldBorder().isWithinBounds(candidate) || level.canSeeSky(candidate)) {
                continue;
            }
            if (reads + 3 > InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS) {
                break;
            }
            reads += 3;
            entity.hierarchyCounters().blockReads += 3;
            if (!level.getBlockState(candidate.below()).blocksMotion()
                || !level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                || !level.getBlockState(candidate.above())
                    .getCollisionShape(level, candidate.above()).isEmpty()) {
                continue;
            }
            // The brightness sample and each fluid neighbour probe are actual reads, so they are
            // charged against the same budget before they run, exactly like the earlier filters.
            if (reads + 1 > InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS) {
                break;
            }
            reads++;
            entity.hierarchyCounters().blockReads++;
            if (level.getMaxLocalRawBrightness(candidate) > 7) {
                boolean waterAdjacent = false;
                boolean budgetExhausted = false;
                for (final net.minecraft.core.Direction direction
                    : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    final BlockPos neighbour = candidate.relative(direction);
                    if (!level.hasChunkAt(neighbour)) continue;
                    if (reads + 1 > InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS) {
                        budgetExhausted = true;
                        break;
                    }
                    reads++;
                    entity.hierarchyCounters().blockReads++;
                    if (level.getFluidState(neighbour).is(net.minecraft.tags.FluidTags.WATER)) {
                        waterAdjacent = true;
                        break;
                    }
                }
                if (budgetExhausted) {
                    break;
                }
                if (!waterAdjacent) {
                    continue;
                }
            }
            return state.withAnchor(
                Optional.of(candidate.asLong()),
                InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.ANCHOR_CLAIM_TICKS)
            );
        }
        return state;
    }

    /**
     * Public entry retained for direct characterization. The live tick path reaches the front through
     * {@link #advanceEmberFront}, which is what makes the Archfiend telegraph, damage, and fire occur in
     * real play rather than only under a direct call.
     */
    public static boolean attemptEmberFront(final InfernalHierarchyEntity archfiend, final ServerLevel level) {
        return advanceEmberFront(
            archfiend, level, archfiend.hierarchyState().intent() == Intent.EMBER_FRONT
        );
    }

    static boolean advanceEmberFront(
        final InfernalHierarchyEntity archfiend,
        final ServerLevel level,
        final boolean telegraphArmed
    ) {
        if (archfiend.hierarchyRank() != Rank.EMBERHORN_ARCHFIEND) {
            return false;
        }
        final long now = level.getGameTime();
        final InfernalHierarchyState state = archfiend.hierarchyState();
        if (state.challengerId().isEmpty() || state.challengerExpiresAt() <= now) {
            return false;
        }
        final Entity challenger = level.getEntity(state.challengerId().orElseThrow());
        if (!(challenger instanceof LivingEntity living) || !living.isAlive() || living.level() != level) {
            return false;
        }
        if (truceHeldWith(state, living, now)) {
            return false;
        }
        if (!InfernalHierarchyRules.due(state.actionBackoffUntil(), now)) {
            return false;
        }
        if (!telegraphArmed) {
            archfiend.getLookControl().setLookAt(living);
            // Player-visible windup through existing assets only: flame particles and a blaze cue.
            level.sendParticles(ParticleTypes.FLAME,
                archfiend.getX(), archfiend.getEyeY(), archfiend.getZ(), 16, 0.5D, 0.5D, 0.5D, 0.02D);
            level.playSound(null, archfiend.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 1.0F, 0.7F);
            archfiend.setHierarchyState(state.withIntent(Intent.EMBER_FRONT).withRouteFailures(
                state.routeFailures(),
                InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.EMBER_FRONT_TELEGRAPH_TICKS)
            ));
            return false;
        }
        final net.minecraft.world.phys.Vec3 toward = living.position()
            .subtract(archfiend.position()).normalize();
        final net.minecraft.world.phys.AABB front = archfiend.getBoundingBox()
            .expandTowards(toward.scale(InfernalHierarchyRules.EMBER_FRONT_RANGE))
            .inflate(1.0D);
        final List<LivingEntity> ordered = level.getEntitiesOfClass(
                LivingEntity.class, front, candidate -> candidate != archfiend && candidate.isAlive()
            ).stream()
            .sorted(java.util.Comparator
                .<LivingEntity>comparingDouble(archfiend::distanceToSqr)
                .thenComparing(Entity::getUUID, InfernalHierarchyRules.unsignedUuidOrder()))
            .toList();
        int visited = 0;
        for (final LivingEntity target : ordered) {
            if (visited >= InfernalHierarchyRules.EMBER_FRONT_TARGET_CAP) {
                break;
            }
            visited++;
            archfiend.hierarchyCounters().candidateVisits++;
            if (!archfiend.canAttack(target)) {
                continue;
            }
            if (target.hurtServer(
                level,
                level.damageSources().indirectMagic(archfiend, archfiend),
                InfernalHierarchyRules.EMBER_FRONT_DAMAGE
            )) {
                target.igniteForSeconds(InfernalHierarchyRules.EMBER_FRONT_FIRE_SECONDS);
            }
        }
        archfiend.setHierarchyState(archfiend.hierarchyState().withIntent(Intent.FOCUS).withRouteFailures(
            archfiend.hierarchyState().routeFailures(),
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.EMBER_FRONT_SPACING_TICKS)
        ));
        return true;
    }

    public static int observationIntervalTicks(final Rank rank) {
        return switch (rank) {
            case DEMON -> InfernalHierarchyRules.DEMON_OBSERVATION_INTERVAL_TICKS;
            case EMBERHORN_ARCHFIEND -> InfernalHierarchyRules.ARCHFIEND_OBSERVATION_INTERVAL_TICKS;
            case ABYSSAL_REGENT -> InfernalHierarchyRules.REGENT_OBSERVATION_INTERVAL_TICKS;
        };
    }

    public static int observationRadius(final Rank rank) {
        return switch (rank) {
            case DEMON -> InfernalHierarchyRules.DEMON_OBSERVATION_RADIUS;
            case EMBERHORN_ARCHFIEND -> InfernalHierarchyRules.ARCHFIEND_OBSERVATION_RADIUS;
            case ABYSSAL_REGENT -> InfernalHierarchyRules.REGENT_OBSERVATION_RADIUS;
        };
    }

    public static int retainedCandidateCap(final Rank rank) {
        return switch (rank) {
            case DEMON -> InfernalHierarchyRules.DEMON_RETAINED_CANDIDATES;
            case EMBERHORN_ARCHFIEND -> InfernalHierarchyRules.ARCHFIEND_RETAINED_CANDIDATES;
            case ABYSSAL_REGENT -> InfernalHierarchyRules.REGENT_RETAINED_CANDIDATES;
        };
    }

    public static int groupRefreshIntervalTicks(final Rank rank) {
        return rank == Rank.ABYSSAL_REGENT
            ? InfernalHierarchyRules.REGENT_GROUP_REFRESH_TICKS
            : InfernalHierarchyRules.ARCHFIEND_GROUP_REFRESH_TICKS;
    }

    static InfernalHierarchyState reconcileAuthority(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState state
    ) {
        if (!InfernalHierarchyRules.acceptsPlayerAuthority(entity.hierarchyRank())) {
            return state.withAuthority(
                state.order().isPresent()
                    ? state.order().orElseThrow().issuerRank() == Rank.ABYSSAL_REGENT
                        ? AuthorityClass.REGENT_ORDER
                        : AuthorityClass.ARCHFIEND_ORDER
                    : AuthorityClass.AUTONOMY,
                Optional.empty()
            );
        }
        final Optional<UUID> direct = directPactOwner(entity);
        final Optional<UUID> animus = animusOwner(entity);
        final Optional<UUID> effective = InfernalHierarchyRules.effectiveOwner(direct, animus);
        if (effective.isPresent()) {
            return state.withAuthority(
                direct.isPresent() ? AuthorityClass.DIRECT_PACT : AuthorityClass.ANIMUS,
                effective
            );
        }
        if (state.order().isPresent()) {
            return state.withAuthority(
                state.order().orElseThrow().issuerRank() == Rank.ABYSSAL_REGENT
                    ? AuthorityClass.REGENT_ORDER
                    : AuthorityClass.ARCHFIEND_ORDER,
                Optional.empty()
            );
        }
        if (state.aggressorId().isPresent()) {
            return state.withAuthority(AuthorityClass.SELF_DEFENSE, state.aggressorId());
        }
        return state.withAuthority(AuthorityClass.AUTONOMY, Optional.empty());
    }

    public static Optional<UUID> directPactOwner(final Entity entity) {
        return CreatureBehaviorState.owner(entity);
    }

    public static Optional<UUID> animusOwner(final Entity entity) {
        final String stored = entity.getPersistentData().getStringOr(InfernalPactEffects.OWNER_KEY, "");
        if (stored.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(stored));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    static InfernalHierarchyState recoverMorale(final InfernalHierarchyState state, final long now) {
        final int recovered = InfernalHierarchyRules.recoveredMorale(
            state.morale(), state.lastMoraleRecoveryAt(), now
        );
        return recovered == state.morale() ? state : state.withMorale(recovered, now);
    }

    static InfernalHierarchyState expireClaims(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        if (state.aggressorId().isPresent() && state.aggressorExpiresAt() <= now) {
            state = state.withAggressor(Optional.empty(), 0L);
        }
        if (state.challengerId().isPresent() && state.challengerExpiresAt() <= now) {
            state = state.withChallenger(Optional.empty(), 0L);
        }
        if (state.trucePlayerId().isPresent() && state.truceExpiresAt() <= now) {
            state = state.withTruce(Optional.empty(), 0L, 0L, state.truceBreachUntil());
        }
        if (state.order().isPresent() && !state.order().orElseThrow().valid(now)) {
            entity.hierarchyCounters().orderCancellations++;
            entity.setTarget(null);
            state = state.withOrder(Optional.empty());
        }
        return state;
    }

    /**
     * One bounded loaded observation. The current truce player, challenger, aggressor, order target, and
     * current roster members are preseeded before generic candidates, and generic candidates are ordered
     * by distance then unsigned UUID, so engine iteration order can never decide truce refresh, stable
     * challenger retention, or order execution.
     */
    static ObservationPass observe(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        entity.hierarchyCounters().observationScans++;
        final int radius = observationRadius(entity.hierarchyRank());
        final int cap = retainedCandidateCap(entity.hierarchyRank());
        final java.util.LinkedHashMap<UUID, LivingEntity> loaded = new java.util.LinkedHashMap<>();
        final List<UUID> generic = new ArrayList<>();
        level.getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(radius),
                candidate -> candidate != entity && candidate.isAlive()
            ).stream()
            .sorted(java.util.Comparator
                .<LivingEntity>comparingDouble(entity::distanceToSqr)
                .thenComparing(Entity::getUUID, InfernalHierarchyRules.unsignedUuidOrder()))
            .forEach(candidate -> {
                if (loaded.putIfAbsent(candidate.getUUID(), candidate) == null) {
                    generic.add(candidate.getUUID());
                }
            });
        final List<UUID> required = new ArrayList<>();
        state.trucePlayerId().ifPresent(required::add);
        state.challengerId().ifPresent(required::add);
        state.aggressorId().ifPresent(required::add);
        state.authorityId().ifPresent(required::add);
        state.order().flatMap(InfernalHierarchyState.Order::targetId).ifPresent(required::add);
        state.roster().stream()
            .filter(member -> member.valid(now))
            .map(InfernalHierarchyState.Member::id)
            .forEach(required::add);
        for (final UUID identity : required) {
            if (loaded.containsKey(identity)) continue;
            // The naturally loaded UUID index never loads a chunk and never scans the dimension.
            final Entity resolved = level.getEntity(identity);
            if (resolved instanceof LivingEntity living && living.isAlive() && living.level() == level) {
                loaded.put(identity, living);
            }
        }
        final List<UUID> retained = InfernalHierarchyRules.retainObservation(required, generic, cap);
        final double truceRangeSqr =
            (double) InfernalHierarchyRules.TRUCE_RANGE * InfernalHierarchyRules.TRUCE_RANGE;
        final double territoryRangeSqr = (double) InfernalHierarchyRules.DEMON_TERRITORY_RADIUS
            * InfernalHierarchyRules.DEMON_TERRITORY_RADIUS;
        final double fearRangeSqr =
            (double) InfernalHierarchyRules.FEAR_PULSE_RADIUS * InfernalHierarchyRules.FEAR_PULSE_RADIUS;
        final double closeRangeSqr =
            (double) InfernalHierarchyRules.CLOSE_ATTACKER_RANGE * InfernalHierarchyRules.CLOSE_ATTACKER_RANGE;
        final boolean anchorHeld = state.anchorPos().isPresent() && state.anchorExpiresAt() > now;
        int lineOfSightBudget = InfernalHierarchyRules.lineOfSightBudget(entity.hierarchyRank());
        boolean intruder = false;
        boolean ownerUnderAttack = false;
        boolean attackerClose = false;
        boolean hostileWithinFear = false;
        LivingEntity intruderActor = null;
        LivingEntity ownerAttacker = null;
        for (final UUID identity : retained) {
            final LivingEntity candidate = loaded.get(identity);
            if (candidate == null) continue;
            entity.hierarchyCounters().candidateVisits++;
            final double distanceSqr = entity.distanceToSqr(candidate);
            if (state.aggressorId().filter(identity::equals).isPresent()
                && state.aggressorExpiresAt() > now
                && distanceSqr <= closeRangeSqr) {
                attackerClose = true;
            }
            if (!(candidate instanceof Player player)) {
                continue;
            }
            // Every infernal rank can hold the single truce. The Archfiend aura's beneficial side and
            // the Regent phase filter both branch on truce state, so the truce must be reachable for
            // them too, not only for the Demon.
            if (distanceSqr <= truceRangeSqr) {
                state = maybeRefreshTruce(entity, state, player, now);
            }
            if (state.authorityId().filter(identity::equals).isPresent()) {
                final LivingEntity attacker = player.getLastHurtByMob();
                if (attacker != null
                    && attacker != entity
                    && attacker.isAlive()
                    && player.getLastHurtByMobTimestamp()
                        + InfernalHierarchyRules.ATTRIBUTION_FRESHNESS_TICKS >= player.tickCount) {
                    ownerUnderAttack = true;
                    if (ownerAttacker == null) {
                        ownerAttacker = attacker;
                    }
                }
                continue;
            }
            if (!hostileCandidate(entity, state, player, now)) {
                continue;
            }
            if (entity.hierarchyRank() == Rank.ABYSSAL_REGENT
                && distanceSqr <= fearRangeSqr
                && lineOfSightBudget > 0) {
                lineOfSightBudget--;
                entity.hierarchyCounters().lineOfSightChecks++;
                hostileWithinFear = hostileWithinFear || entity.getSensing().hasLineOfSight(player);
                continue;
            }
            if (anchorHeld
                && entity.hierarchyRank() != Rank.ABYSSAL_REGENT
                && postDistanceSqr(state, player) <= territoryRangeSqr
                && lineOfSightBudget > 0) {
                lineOfSightBudget--;
                entity.hierarchyCounters().lineOfSightChecks++;
                if (!intruder && entity.getSensing().hasLineOfSight(player)) {
                    intruder = true;
                    intruderActor = player;
                }
            }
        }
        return new ObservationPass(
            state,
            new Observation(intruder, ownerUnderAttack, attackerClose, hostileWithinFear,
                intruderActor, ownerAttacker)
        );
    }

    private static boolean hostileCandidate(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState state,
        final Player player,
        final long now
    ) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (truceHeldWith(state, player, now)) {
            return false;
        }
        return state.authorityId().filter(player.getUUID()::equals).isEmpty()
            && entity.canAttack(player);
    }

    private static double postDistanceSqr(final InfernalHierarchyState state, final Player player) {
        if (state.anchorPos().isEmpty()) {
            return Double.MAX_VALUE;
        }
        final BlockPos post = BlockPos.of(state.anchorPos().orElseThrow());
        return player.distanceToSqr(post.getX() + 0.5D, post.getY() + 0.5D, post.getZ() + 0.5D);
    }

    /**
     * A stable carriage predicate for truce eligibility. The legacy pacification predicate flickers on
     * three of every four ticks by design, which is exactly the flicker the stable truce replaces, so
     * eligibility here depends only on actually carrying the infernal Silver Tongue charm.
     */
    static boolean carriesInfernalCharm(final Player player) {
        final net.minecraft.world.item.Item charm = ModItems.ALL.get("silver_tongue_charm").get();
        return player.getInventory().contains(stack -> stack.getItem() == charm);
    }

    /**
     * The one stable truce carriage predicate, shared by intent selection, the ember front guard,
     * the hostile filter, the cauldron aura, and target eligibility, all through
     * {@link InfernalHierarchyRules#truceValid}. Formation and refresh are range gated by the
     * observation pass; carriage between passes requires the persisted expiry and a live loaded
     * truce player, and range is a formation fact rather than a carriage fact.
     */
    static boolean truceHeld(final ServerLevel level, final InfernalHierarchyState state, final long now) {
        if (state.trucePlayerId().isEmpty()) {
            return false;
        }
        final Entity resolved = level.getEntity(state.trucePlayerId().orElseThrow());
        final boolean playerLoaded = resolved instanceof Player player
            && player.isAlive() && player.level() == level;
        return InfernalHierarchyRules.truceValid(state.truceExpiresAt(), playerLoaded, true, now);
    }

    static boolean truceHeldWith(
        final InfernalHierarchyState state,
        final LivingEntity actor,
        final long now
    ) {
        return state.trucePlayerId().filter(actor.getUUID()::equals).isPresent()
            && InfernalHierarchyRules.truceValid(state.truceExpiresAt(), actor.isAlive(), true, now);
    }

    static InfernalHierarchyState maybeRefreshTruce(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState state,
        final Player player,
        final long now
    ) {
        final boolean existing = state.trucePlayerId().filter(player.getUUID()::equals).isPresent();
        if (!existing && state.trucePlayerId().isPresent()) {
            return state;
        }
        if (!InfernalHierarchyRules.truceRefreshDue(state.truceRefreshedAt(), now)) {
            return state;
        }
        final boolean aggressor = state.aggressorId().filter(player.getUUID()::equals).isPresent();
        if (!InfernalHierarchyRules.mayFormTruce(
            carriesInfernalCharm(player), aggressor, state.truceBreachUntil(), now
        )) {
            return state;
        }
        entity.hierarchyCounters().truceRefreshes++;
        if (entity.getTarget() == player) {
            entity.setTarget(null);
        }
        return state.withTruce(
            Optional.of(player.getUUID()),
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.TRUCE_TICKS),
            now,
            state.truceBreachUntil()
        );
    }

    static InfernalHierarchyState reconcileLeader(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        if (state.leaderId().isEmpty()) {
            return state;
        }
        if (state.membershipLeaseUntil() <= now) {
            return releaseLeader(entity, state, false);
        }
        final Entity leader = level.getEntity(state.leaderId().orElseThrow());
        if (!(leader instanceof InfernalHierarchyEntity living) || !living.isAlive() || living.level() != level) {
            entity.hierarchyCounters().orderCancellations++;
            // A dead leader's parting withdraw or dissolve instruction is the one order that
            // survives the release, which is what carries the withdrawal-captain and dissolution
            // contract into the members' own live decisions.
            final Optional<InfernalHierarchyState.Order> parting = state.order()
                .filter(row -> row.valid(now))
                .filter(row -> switch (row.kind()) {
                    case WITHDRAW, WITHDRAW_TO_ANCHOR, DISSOLVE -> true;
                    default -> false;
                });
            state = releaseLeader(entity, state, true);
            state = state.withOrder(parting);
            state = applyMoraleEvent(entity, state,
                -InfernalHierarchyRules.MORALE_LEADER_LOSS_PENALTY, now);
            // Bounded unmoored regroup. The member never resolves or force loads the missing issuer.
            return state.withRouteFailures(
                state.routeFailures(),
                InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.ROUTE_BACKOFF_TICKS)
            ).withIntent(Intent.RETURN);
        }
        return state;
    }

    static InfernalHierarchyState releaseLeader(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState state,
        final boolean cancelExecution
    ) {
        if (cancelExecution) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }
        entity.hierarchyCounters().releases++;
        return state.withLeader(Optional.empty(), Optional.empty(), 0L)
            .withOrder(Optional.empty());
    }

    static InfernalHierarchyState refreshGroup(
        final InfernalHierarchyEntity leader,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now,
        final Intent commandIntent
    ) {
        InfernalHierarchyState state = input;
        leader.hierarchyCounters().groupRefreshes++;
        final Rank leaderRank = leader.hierarchyRank();
        final int range = leaderRank == Rank.ABYSSAL_REGENT
            ? InfernalHierarchyRules.COURT_RANGE
            : InfernalHierarchyRules.SQUAD_RANGE;
        final int genericCap = leaderRank == Rank.ABYSSAL_REGENT
            ? InfernalHierarchyRules.REGENT_GENERIC_CANDIDATE_CAP
            : InfernalHierarchyRules.ARCHFIEND_GENERIC_CANDIDATE_CAP;
        final List<UUID> currentMembers = state.roster().stream()
            .filter(member -> member.valid(now))
            .map(InfernalHierarchyState.Member::id)
            .toList();
        final List<InfernalHierarchyEntity> loaded = level.getEntitiesOfClass(
            InfernalHierarchyEntity.class,
            leader.getBoundingBox().inflate(range),
            candidate -> candidate != leader && candidate.isAlive()
        );
        final List<MemberCandidate> candidates = new ArrayList<>();
        final java.util.LinkedHashMap<UUID, InfernalHierarchyEntity> resolved = new java.util.LinkedHashMap<>();
        int visited = 0;
        for (final InfernalHierarchyEntity candidate : loaded) {
            final boolean current = currentMembers.contains(candidate.getUUID());
            if (!current && visited >= genericCap) continue;
            if (!current) visited++;
            leader.hierarchyCounters().candidateVisits++;
            final InfernalHierarchyState memberState = candidate.hierarchyState();
            final boolean otherwiseLeased = memberState.leaderId()
                .filter(id -> !id.equals(leader.getUUID()))
                .isPresent()
                && memberState.membershipLeaseUntil() > now;
            candidates.add(new MemberCandidate(
                candidate.getUUID(),
                candidate.hierarchyRank(),
                current,
                leader.distanceToSqr(candidate),
                InfernalHierarchyRules.effectiveOwner(
                    directPactOwner(candidate), animusOwner(candidate)
                ).isPresent(),
                otherwiseLeased,
                candidate.level() == level,
                true
            ));
            resolved.put(candidate.getUUID(), candidate);
        }
        final List<MemberCandidate> retained = InfernalHierarchyRules.retainRoster(leaderRank, candidates);
        final long leaseUntil = InfernalHierarchyRules.saturatingAdd(
            now, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS
        );
        final long epoch = state.orderEpoch() + 1L;
        // The live command doctrine. An engaged challenger draws a focus order carrying its exact
        // target, a threatened but unengaged leader screens, a withdrawing leader withdraws its
        // group, and only a quiet leader holds. This is what makes the focus target slot, the Demon
        // press-through-focus arm, and the withdraw arm reachable in real play.
        final Optional<UUID> focusTarget = loadedChallenger(level, state, now).map(Entity::getUUID);
        final boolean threatened = state.aggressorId().isPresent() && state.aggressorExpiresAt() > now;
        final boolean withdrawing = InfernalHierarchyRules.cancelsExecution(commandIntent);
        final OrderKind kind;
        Optional<UUID> orderTarget = Optional.empty();
        if (withdrawing) {
            kind = leaderRank == Rank.ABYSSAL_REGENT
                ? OrderKind.WITHDRAW_TO_ANCHOR
                : OrderKind.WITHDRAW;
        } else if (focusTarget.isPresent()) {
            kind = OrderKind.FOCUS_CHALLENGER;
            orderTarget = focusTarget;
        } else if (threatened) {
            kind = leaderRank == Rank.ABYSSAL_REGENT ? OrderKind.SCREEN_REGENT : OrderKind.SCREEN;
        } else {
            kind = leaderRank == Rank.ABYSSAL_REGENT ? OrderKind.HOLD_COURT : OrderKind.HOLD_POST;
        }
        final List<InfernalHierarchyState.Member> roster = new ArrayList<>();
        for (final MemberCandidate candidate : retained) {
            roster.add(new InfernalHierarchyState.Member(candidate.id(), candidate.rank(), leaseUntil));
            final InfernalHierarchyEntity member = resolved.get(candidate.id());
            if (member == null) continue;
            if (!InfernalHierarchyRules.mayIssueOrder(leaderRank, candidate.rank(), kind)) continue;
            leader.hierarchyCounters().orderIssues++;
            member.setHierarchyState(member.hierarchyState()
                .withLeader(Optional.of(leader.getUUID()), Optional.of(leaderRank), leaseUntil)
                .withOrder(Optional.of(new InfernalHierarchyState.Order(
                    kind,
                    orderTarget,
                    InfernalHierarchyRules.saturatingAdd(
                        now, InfernalHierarchyRules.orderLifetimeTicks(leaderRank)
                    ),
                    epoch,
                    leaderRank
                ))));
        }
        for (final UUID former : currentMembers) {
            if (retained.stream().anyMatch(candidate -> candidate.id().equals(former))) continue;
            final InfernalHierarchyEntity member = resolved.get(former);
            if (member != null && member.hierarchyState().leaderId()
                .filter(leader.getUUID()::equals).isPresent()) {
                member.setHierarchyState(releaseLeader(member, member.hierarchyState(), true));
            }
        }
        return state.withRoster(roster, epoch);
    }

    static InfernalHierarchyState tickSummonLifecycle(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        if (state.summonerId().isPresent()) {
            final Entity summoner = level.getEntity(state.summonerId().orElseThrow());
            final boolean expired = state.summonExpiresAt() > 0L && state.summonExpiresAt() <= now;
            final boolean orphaned = !(summoner instanceof InfernalHierarchyEntity living)
                || !living.isAlive()
                || level.getDifficulty() == Difficulty.PEACEFUL;
            if (expired || orphaned) {
                entity.hierarchyCounters().summonCleanups++;
                emitSummonCollapse(entity, level, true);
                entity.discard();
                return state;
            }
            // The last hundred ticks of a temporary body's life are a visible collapse window at the
            // bounded decision cadence, so the discard never looks like an unexplained vanish.
            if (state.summonExpiresAt() > 0L
                && state.summonExpiresAt() - now <= InfernalHierarchyRules.SUMMON_COLLAPSE_TICKS) {
                emitSummonCollapse(entity, level, false);
            }
        }
        if (entity.hierarchyRank() == Rank.ABYSSAL_REGENT && !state.summons().isEmpty()) {
            final boolean expired = state.summonExpiresAt() <= now;
            final List<UUID> live = new ArrayList<>();
            for (final UUID id : state.summons()) {
                final Entity summon = level.getEntity(id);
                if (summon instanceof InfernalHierarchyEntity living && living.isAlive()) {
                    if (expired) {
                        entity.hierarchyCounters().summonCleanups++;
                        emitSummonCollapse(living, level, true);
                        living.discard();
                    } else {
                        live.add(id);
                    }
                }
            }
            if (expired || live.size() != state.summons().size()) {
                state = state.withSummons(expired ? List.of() : live, state.summonExpiresAt());
            }
        }
        return state;
    }

    /**
     * Existing-asset collapse feedback for a temporary summon: smoke while the collapse window runs
     * and a final burst with the enderman cue at the discard itself.
     */
    private static void emitSummonCollapse(
        final InfernalHierarchyEntity summon,
        final ServerLevel level,
        final boolean finalBurst
    ) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
            summon.getX(), summon.getY() + 1.0D, summon.getZ(),
            finalBurst ? 16 : 4, 0.3D, 0.5D, 0.3D, 0.01D);
        if (finalBurst) {
            level.playSound(null, summon.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.8F, 0.7F);
        }
    }

    static InfernalHierarchyState advancePhase(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        if (entity.hierarchyRank() != Rank.ABYSSAL_REGENT) {
            return state;
        }
        switch (state.phaseState()) {
            case TELEGRAPH -> {
                // The telegraph is a real warning window. No phase effect exists until the commit.
                if (InfernalHierarchyRules.due(state.phaseDeadline(), now)) {
                    entity.hierarchyCounters().phaseTransitions++;
                    state = state.withPhase(PhaseState.COMMIT, true, now);
                    state = commitPhase(entity, level, state, now);
                    state = attemptReinforcementTransaction(entity, level, state, now);
                }
            }
            case COMMIT -> {
                if (InfernalHierarchyRules.due(state.phaseDeadline(), now)) {
                    entity.hierarchyCounters().phaseTransitions++;
                    state = state.withPhase(PhaseState.RECOVERY, true, InfernalHierarchyRules.saturatingAdd(
                        now, InfernalHierarchyRules.PHASE_RECOVERY_TICKS
                    ));
                }
            }
            case RECOVERY -> {
                if (InfernalHierarchyRules.due(state.phaseDeadline(), now)) {
                    entity.hierarchyCounters().phaseTransitions++;
                    state = state.withPhase(PhaseState.DONE, true, 0L);
                }
            }
            default -> {
            }
        }
        return state;
    }

    /**
     * The exact commit effects, applied once. Recipients are the nearest deterministic relationship
     * valid hostile players inside the approved radius, capped by the approved player cap, with a
     * stable unsigned UUID tiebreak so two players at the same distance always resolve identically.
     */
    static InfernalHierarchyState commitPhase(
        final InfernalHierarchyEntity regent,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        regent.addEffect(new MobEffectInstance(
            MobEffects.RESISTANCE, InfernalHierarchyRules.PHASE_EFFECT_TICKS, 1, true, true
        ));
        regent.addEffect(new MobEffectInstance(
            MobEffects.STRENGTH, InfernalHierarchyRules.PHASE_EFFECT_TICKS, 1, true, true
        ));
        for (final Player recipient : phaseRecipients(regent, level, state, now)) {
            recipient.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS, InfernalHierarchyRules.PHASE_EFFECT_TICKS, 2
            ));
            recipient.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, InfernalHierarchyRules.PHASE_EFFECT_TICKS, 0
            ));
            recipient.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, InfernalHierarchyRules.PHASE_EFFECT_TICKS, 1
            ));
        }
        return state;
    }

    static List<Player> phaseRecipients(
        final InfernalHierarchyEntity regent,
        final ServerLevel level,
        final InfernalHierarchyState state,
        final long now
    ) {
        final java.util.LinkedHashMap<UUID, Player> loaded = new java.util.LinkedHashMap<>();
        final List<InfernalHierarchyRules.RankedActor> candidates = new ArrayList<>();
        for (final Player candidate : level.getEntitiesOfClass(
            Player.class,
            regent.getBoundingBox().inflate(InfernalHierarchyRules.PHASE_PLAYER_RADIUS),
            Player::isAlive
        )) {
            if (!hostileCandidate(regent, state, candidate, now)) continue;
            loaded.putIfAbsent(candidate.getUUID(), candidate);
            candidates.add(new InfernalHierarchyRules.RankedActor(
                candidate.getUUID(), regent.distanceToSqr(candidate)
            ));
        }
        final List<Player> recipients = new ArrayList<>();
        for (final UUID identity : InfernalHierarchyRules.retainNearest(
            candidates, InfernalHierarchyRules.PHASE_PLAYER_CAP
        )) {
            final Player recipient = loaded.get(identity);
            if (recipient == null) continue;
            regent.hierarchyCounters().candidateVisits++;
            recipients.add(recipient);
        }
        return List.copyOf(recipients);
    }

    static InfernalHierarchyState attemptReinforcementTransaction(
        final InfernalHierarchyEntity regent,
        final ServerLevel level,
        final InfernalHierarchyState input,
        final long now
    ) {
        InfernalHierarchyState state = input;
        final int courtSize = (int) state.roster().stream().filter(member -> member.valid(now)).count();
        final List<BlockPos> safe = new ArrayList<>();
        int reads = 0;
        for (final int[] offset : new int[][]{{2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}}) {
            if (reads + 3 > 256) break;
            final BlockPos candidate = regent.blockPosition().offset(offset[0], offset[1], offset[2]);
            if (!level.hasChunkAt(candidate)) continue;
            reads += 3;
            regent.hierarchyCounters().blockReads += 3;
            if (level.getBlockState(candidate.below()).blocksMotion()
                && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                && level.getWorldBorder().isWithinBounds(candidate)) {
                safe.add(candidate);
            }
            if (safe.size() >= InfernalHierarchyRules.PHASE_SUMMON_CAP) break;
        }
        if (!InfernalHierarchyRules.summonTransactionAllowed(
            courtSize,
            level.getDifficulty() == Difficulty.PEACEFUL,
            safe.size(),
            !state.summons().isEmpty()
        )) {
            return state;
        }
        final List<InfernalHierarchyEntity> constructed = new ArrayList<>();
        for (int index = 0; index < InfernalHierarchyRules.PHASE_SUMMON_CAP; index++) {
            final Entity created = ModEntities.ALL.get("demon").get()
                .create(level, EntitySpawnReason.MOB_SUMMONED);
            if (!(created instanceof InfernalHierarchyEntity demon)) {
                constructed.forEach(Entity::discard);
                return state;
            }
            final BlockPos position = safe.get(index);
            demon.snapTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            constructed.add(demon);
        }
        final long expiresAt = InfernalHierarchyRules.saturatingAdd(
            now, InfernalHierarchyRules.SUMMON_LIFE_TICKS
        );
        for (final InfernalHierarchyEntity demon : constructed) {
            if (!level.addFreshEntity(demon)) {
                constructed.forEach(Entity::discard);
                return state;
            }
            demon.setHierarchyState(demon.hierarchyState()
                .withSummons(List.of(), 0L)
                .withSummoner(Optional.of(regent.getUUID()), expiresAt)
                .withLeader(Optional.of(regent.getUUID()), Optional.of(Rank.ABYSSAL_REGENT),
                    InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS)));
            regent.hierarchyCounters().summonConstructions++;
        }
        return state.withSummons(
            constructed.stream().map(Entity::getUUID).toList(),
            expiresAt
        );
    }

    /**
     * The cauldron duality under the approved bounds. The primary office anchor plus at most three
     * further loaded magical cauldrons inside eight blocks contribute, every read is charged and guarded
     * by a loaded chunk test, and recipients are relationship filtered. The relocated legacy body scanned
     * a seventeen by seven by seventeen volume of two thousand and twenty three unguarded positions.
     */
    public static void tickCauldronAura(final Mob creature, final ServerLevel level) {
        if (!(creature instanceof InfernalHierarchyEntity archfiend)) {
            return;
        }
        final long now = level.getGameTime();
        final InfernalHierarchyState state = archfiend.hierarchyState();
        final BlockPos primary = state.anchorPos().isPresent() && state.anchorExpiresAt() > now
            ? BlockPos.of(state.anchorPos().orElseThrow())
            : archfiend.blockPosition();
        int contributors = 0;
        int reads = 0;
        for (final BlockPos candidate : BlockPos.withinManhattan(
            primary,
            InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_RADIUS,
            InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_RADIUS,
            InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_RADIUS
        )) {
            if (reads >= InfernalHierarchyRules.CAULDRON_SCAN_BLOCK_READS
                || contributors >= InfernalHierarchyRules.CAULDRON_CONTRIBUTOR_CAP) {
                break;
            }
            if (!level.hasChunkAt(candidate)) {
                continue;
            }
            reads++;
            archfiend.hierarchyCounters().blockReads++;
            if (level.getBlockState(candidate).is(CreatureBehaviorTags.Blocks.MAGICAL_CAULDRONS)) {
                contributors++;
            }
        }
        if (CreatureBehaviorRules.cauldronRangeBonus(contributors) == 0) {
            return;
        }
        final int reach = InfernalHierarchyRules.cauldronReach(contributors);
        final int amplifier = Math.min(1, contributors - 1);
        for (final Player player : level.getEntitiesOfClass(
            Player.class, archfiend.getBoundingBox().inflate(reach), Player::isAlive
        )) {
            final boolean truced = truceHeldWith(state, player, now);
            if (truced) {
                player.addEffect(new MobEffectInstance(
                    MobEffects.LUCK, InfernalHierarchyRules.CAULDRON_LUCK_TICKS, amplifier
                ));
                continue;
            }
            final boolean challenger = state.challengerId().filter(player.getUUID()::equals).isPresent()
                && state.challengerExpiresAt() > now;
            if (challenger || (hostileCandidate(archfiend, state, player, now)
                && Math.floorMod(archfiend.tickCount + player.getId(), 400) == 0)) {
                player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    InfernalHierarchyRules.CAULDRON_WEAKNESS_TICKS,
                    InfernalHierarchyRules.CAULDRON_WEAKNESS_AMPLIFIER
                ));
            }
            // Neutral bystanders receive neither side of the duality.
        }
    }

    /**
     * The half health phase entry. It latches the transition and opens the telegraph. It applies no
     * effect, because the approved order is telegraph, then commit, then recovery.
     */
    public static void tickAbyssalTorment(final Mob creature, final ServerLevel level) {
        if (!(creature instanceof InfernalHierarchyEntity regent)) {
            return;
        }
        final InfernalHierarchyState state = regent.hierarchyState();
        final boolean phaseActive = state.phaseState() == PhaseState.TELEGRAPH
            || state.phaseState() == PhaseState.COMMIT
            || state.phaseState() == PhaseState.RECOVERY;
        if (!phaseActive) {
            pulseFear(regent, level);
        }
        final boolean phaseTriggered = regent.getPersistentData()
            .getBooleanOr(InfernalHierarchyEntity.LEGACY_PHASE_KEY, false);
        if (!AbyssalRegentRules.beginsTormentPhase(regent.getHealth(), phaseTriggered)) {
            return;
        }
        regent.getPersistentData().putBoolean(InfernalHierarchyEntity.LEGACY_PHASE_KEY, true);
        regent.getNavigation().stop();
        // Player-visible half-health telegraph through existing assets only.
        level.sendParticles(ParticleTypes.SOUL,
            regent.getX(), regent.getEyeY(), regent.getZ(), 24, 0.6D, 0.8D, 0.6D, 0.02D);
        level.playSound(null, regent.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
            SoundSource.HOSTILE, 1.2F, 0.6F);
        regent.hierarchyCounters().phaseTransitions++;
        regent.setHierarchyState(state.withPhase(
            PhaseState.TELEGRAPH,
            true,
            InfernalHierarchyRules.saturatingAdd(
                level.getGameTime(), InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS
            )
        ).withIntent(Intent.PHASE_TELEGRAPH));
    }

    /**
     * The regular fear pulse. A pulse that begins a new combat episode is preceded by at least ten
     * ticks of existing feedback, soul particles and the evoker cue, before any effect applies;
     * inside a running episode the pulse continues at its ordinary cadence with no repeated
     * transition feedback. A passive cadence call with nobody in radius neither telegraphs nor
     * pulses, so cadence refresh can never spam the transition feedback.
     */
    private static void pulseFear(final InfernalHierarchyEntity regent, final ServerLevel level) {
        final long now = level.getGameTime();
        final InfernalHierarchyState state = regent.hierarchyState();
        final java.util.LinkedHashMap<UUID, Player> loaded = new java.util.LinkedHashMap<>();
        final List<InfernalHierarchyRules.RankedActor> candidates = new ArrayList<>();
        for (final Player candidate : level.getEntitiesOfClass(
            Player.class,
            regent.getBoundingBox().inflate(InfernalHierarchyRules.FEAR_PULSE_RADIUS),
            Player::isAlive
        )) {
            if (!hostileCandidate(regent, state, candidate, now)) continue;
            loaded.putIfAbsent(candidate.getUUID(), candidate);
            candidates.add(new InfernalHierarchyRules.RankedActor(
                candidate.getUUID(), regent.distanceToSqr(candidate)
            ));
        }
        final Counters counters = regent.hierarchyCounters();
        if (candidates.isEmpty()) {
            counters.fearTelegraphAt = 0L;
            return;
        }
        final boolean newEpisode = counters.lastFearPulseAt <= 0L
            || now - counters.lastFearPulseAt > 2L * InfernalHierarchyRules.FEAR_PULSE_INTERVAL_TICKS;
        if (newEpisode && counters.fearTelegraphAt <= 0L) {
            counters.fearTelegraphAt = now;
            counters.fearTelegraphs++;
            level.sendParticles(ParticleTypes.SOUL,
                regent.getX(), regent.getEyeY(), regent.getZ(), 12, 0.5D, 0.6D, 0.5D, 0.01D);
            level.playSound(null, regent.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
                SoundSource.HOSTILE, 0.9F, 0.8F);
            return;
        }
        if (counters.fearTelegraphAt > 0L
            && now - counters.fearTelegraphAt < InfernalHierarchyRules.FEAR_TELEGRAPH_TICKS) {
            return;
        }
        counters.fearTelegraphAt = 0L;
        counters.lastFearPulseAt = now;
        counters.fearPulses++;
        for (final UUID identity : InfernalHierarchyRules.retainNearest(
            candidates, InfernalHierarchyRules.FEAR_PULSE_CANDIDATE_CAP
        )) {
            final Player player = loaded.get(identity);
            if (player == null) continue;
            regent.hierarchyCounters().candidateVisits++;
            player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, InfernalHierarchyRules.FEAR_PULSE_EFFECT_TICKS, 0
            ));
            player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, InfernalHierarchyRules.FEAR_PULSE_EFFECT_TICKS, 0
            ));
        }
    }

    public static boolean restraintAllows(final InfernalHierarchyEntity entity, final LivingEntity target) {
        if (entity.hierarchyRank() != Rank.EMBERHORN_ARCHFIEND) {
            return true;
        }
        final long now = entity.level().getGameTime();
        final InfernalHierarchyState state = entity.hierarchyState();
        final UUID id = target.getUUID();
        return (state.challengerId().filter(id::equals).isPresent() && state.challengerExpiresAt() > now)
            || (state.aggressorId().filter(id::equals).isPresent() && state.aggressorExpiresAt() > now)
            || state.order().flatMap(InfernalHierarchyState.Order::targetId)
                .filter(id::equals).isPresent();
    }

    public static boolean eligibleTarget(final InfernalHierarchyEntity entity, final LivingEntity target) {
        final long now = entity.level().getGameTime();
        final InfernalHierarchyState state = entity.hierarchyState();
        if (target instanceof AbstractVillager || target instanceof IronGolem || target instanceof Turtle) {
            return false;
        }
        if (truceHeldWith(state, target, now)) {
            return false;
        }
        if (target instanceof InfernalHierarchyEntity other) {
            final boolean sameCourt = state.leaderId().filter(other.getUUID()::equals).isPresent()
                || other.hierarchyState().leaderId().filter(entity.getUUID()::equals).isPresent()
                || (state.leaderId().isPresent()
                    && state.leaderId().equals(other.hierarchyState().leaderId()));
            if (sameCourt) {
                return false;
            }
            return state.aggressorId().filter(other.getUUID()::equals).isPresent()
                || state.order().flatMap(InfernalHierarchyState.Order::targetId)
                    .filter(other.getUUID()::equals).isPresent();
        }
        return true;
    }

    public static void recordDirectAttack(
        final InfernalHierarchyEntity entity,
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == entity || !attacker.isAlive()) {
            return;
        }
        final long now = level.getGameTime();
        InfernalHierarchyState state = entity.hierarchyState();
        state = state.withAggressor(
            Optional.of(attacker.getUUID()),
            InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.AGGRESSOR_TICKS)
        );
        if (entity.hierarchyRank() != Rank.DEMON) {
            if (state.challengerId().isEmpty() || state.challengerExpiresAt() <= now) {
                state = state.withChallenger(
                    Optional.of(attacker.getUUID()),
                    InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.PROVOCATION_TICKS)
                );
            }
        }
        if (state.trucePlayerId().filter(attacker.getUUID()::equals).isPresent()) {
            state = state.withTruce(
                Optional.empty(), 0L, 0L, InfernalHierarchyRules.truceBreachUntil(now)
            );
        }
        state = applyMoraleEvent(entity, state,
            -InfernalHierarchyRules.damageMoralePenalty(amount, entity.getMaxHealth()), now);
        entity.setHierarchyState(state);
        propagateSquadProvocation(entity, level, attacker, now);
    }

    /**
     * The designed squad provocation. A direct attack on a valid squad member within sixteen blocks
     * of its leader is valid provocation for that leader. The propagation is raised at the accepted
     * hit itself, so it is inside the forty-tick attribution freshness window by construction, and a
     * still-fresh claim for the same attacker is never rewritten faster than that window. The leader
     * resolves through the level's direct UUID index, which loads no chunk and scans no dimension,
     * and one charged line of sight check gates the write so a hidden attacker cannot provoke.
     */
    private static void propagateSquadProvocation(
        final InfernalHierarchyEntity victim,
        final ServerLevel level,
        final LivingEntity attacker,
        final long now
    ) {
        final InfernalHierarchyState victimState = victim.hierarchyState();
        if (victimState.leaderId().isEmpty() || victimState.membershipLeaseUntil() <= now) {
            return;
        }
        final Entity resolved = level.getEntity(victimState.leaderId().orElseThrow());
        if (!(resolved instanceof InfernalHierarchyEntity leader)
            || !leader.isAlive()
            || leader.level() != level
            || leader.hierarchyRank() == Rank.DEMON
            || attacker == leader) {
            return;
        }
        final double radius = InfernalHierarchyRules.SQUAD_PROVOCATION_RADIUS;
        if (leader.distanceToSqr(victim) > radius * radius) {
            return;
        }
        InfernalHierarchyState state = leader.hierarchyState();
        final UUID attackerId = attacker.getUUID();
        final long freshUntil = InfernalHierarchyRules.saturatingAdd(
            now, InfernalHierarchyRules.AGGRESSOR_TICKS
        );
        if (state.aggressorId().filter(attackerId::equals).isPresent()
            && state.aggressorExpiresAt()
                >= freshUntil - InfernalHierarchyRules.ATTRIBUTION_FRESHNESS_TICKS) {
            return;
        }
        leader.hierarchyCounters().lineOfSightChecks++;
        if (!leader.getSensing().hasLineOfSight(attacker)) {
            return;
        }
        leader.hierarchyCounters().squadProvocations++;
        state = state.withAggressor(Optional.of(attackerId), freshUntil);
        if (state.challengerId().isEmpty() || state.challengerExpiresAt() <= now) {
            state = state.withChallenger(
                Optional.of(attackerId),
                InfernalHierarchyRules.saturatingAdd(now, InfernalHierarchyRules.PROVOCATION_TICKS)
            );
        }
        if (state.trucePlayerId().filter(attackerId::equals).isPresent()) {
            state = state.withTruce(
                Optional.empty(), 0L, 0L, InfernalHierarchyRules.truceBreachUntil(now)
            );
        }
        leader.setHierarchyState(state);
    }

    public static void recordSuccessfulMelee(final InfernalHierarchyEntity entity, final ServerLevel level) {
        entity.setHierarchyState(applyMoraleEvent(
            entity, entity.hierarchyState(),
            InfernalHierarchyRules.MORALE_MELEE_REWARD, level.getGameTime()
        ));
    }

    static InfernalHierarchyState applyMoraleEvent(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState state,
        final int delta,
        final long now
    ) {
        entity.hierarchyCounters().moraleEvents++;
        return state.withMorale(InfernalHierarchyRules.clampMorale(state.morale() + delta), now);
    }

    public static InfernalHierarchyState recordRouteFailure(
        final InfernalHierarchyEntity entity,
        final InfernalHierarchyState input,
        final long now
    ) {
        final int failures = InfernalHierarchyRules.routeFailures(input.routeFailures());
        InfernalHierarchyState state = applyMoraleEvent(
            entity, input, -InfernalHierarchyRules.MORALE_ROUTE_FAILURE_PENALTY, now
        );
        if (failures >= InfernalHierarchyRules.MAX_ROUTE_FAILURES) {
            entity.setTarget(null);
            entity.getNavigation().stop();
            return state.withRouteFailures(0, InfernalHierarchyRules.routeBackoffUntil(failures, now))
                .withIntent(Intent.RETURN);
        }
        return state.withRouteFailures(failures, state.actionBackoffUntil());
    }
}
