package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LostSoulRules.AnchorCandidate;
import com.kadamitas.warlockery.entity.LostSoulRules.BandAction;
import com.kadamitas.warlockery.entity.LostSoulRules.EpisodeEnd;
import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Lost Soul behavior controller and the sole ordinary navigation writer for
 * this species. Every scan, block read, path request and pulse is counted against the declared
 * hard budgets. Nothing here enumerates a dimension, forces a chunk, iterates entities globally,
 * edits a block or an inventory, writes another entity's persistent state, sets a combat target,
 * teleports, or completes the shade as a reward.
 *
 * <p>The single approved outward mutation is the preserved Night Vision owner aura.</p>
 */
public final class LostSoulRuntime {
    private LostSoulRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long anchorScans;
        long anchorReads;
        long anchorCandidates;
        long blockReads;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long petitionPulses;
        long auraPulses;
        long ownerRecalls;
        long hazardInterruptions;
        long episodesStarted;
        long episodesEnded;

        public long anchorScans() { return anchorScans; }
        public long anchorReads() { return anchorReads; }
        public long anchorCandidates() { return anchorCandidates; }
        public long blockReads() { return blockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long petitionPulses() { return petitionPulses; }
        public long auraPulses() { return auraPulses; }
        public long ownerRecalls() { return ownerRecalls; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long episodesStarted() { return episodesStarted; }
        public long episodesEnded() { return episodesEnded; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay
     * work by one cadence but can never replay a pulse, an aura, a path, or an episode.
     */
    public static final class TransientState {
        boolean reconciled;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int discoveryCooldownTicks;
        int wanderCooldownTicks;
        boolean hazardActive;
        BlockPos destination;

        public void resetForLoad() {
            reconciled = false;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            discoveryCooldownTicks = 0;
            wanderCooldownTicks = 0;
            hazardActive = false;
            destination = null;
        }

        public boolean hazardActive() {
            return hazardActive;
        }
    }

    /**
     * Fixture seam. The one-shot load reconciliation re-seeds the discovery and wander stagger
     * from inside the first {@link #tick}, so a fixture that clears those cadences immediately
     * before that first tick would have them silently overwritten and would observe no decision.
     * Running the real reconciliation up front settles it once, leaving the live path unchanged.
     */
    static void reconcileForFixture(final LostSoulEntity soul, final ServerLevel level) {
        reconcileOnLoad(soul, level);
    }

    public static void tick(final LostSoulEntity soul, final ServerLevel level) {
        reconcileOnLoad(soul, level);
        advanceLoadedTimers(soul);
        if (tickHazard(soul, level)) {
            return;
        }
        if (CreatureBehaviorState.owner(soul).isPresent()) {
            tickBoundAttendance(soul, level);
            return;
        }
        if (soul.lostSoulState().phase() == Phase.BOUND) {
            soul.setLostSoulState(soul.lostSoulState().unbind());
            soul.getNavigation().stop();
            return;
        }
        switch (soul.lostSoulState().phase()) {
            case APPROACH -> tickApproach(soul, level);
            case PETITION -> tickPetition(soul, level);
            case SETTLE -> tickSettle(soul, level);
            case COOLDOWN, WANDER -> tickWander(soul, level);
            case BOUND -> {
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final LostSoulEntity soul, final ServerLevel level) {
        final TransientState scratch = soul.lostSoulTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.discoveryCooldownTicks =
            LostSoulRules.stableOffset(soul.getUUID(), LostSoulRules.DISCOVERY_INTERVAL_TICKS);
        scratch.wanderCooldownTicks =
            LostSoulRules.stableOffset(soul.getUUID(), LostSoulRules.WANDER_INTERVAL_TICKS);
        LostSoulState state = soul.lostSoulState();
        if (CreatureBehaviorState.owner(soul).isPresent() && state.phase() != Phase.BOUND) {
            state = state.bind();
        }
        soul.setLostSoulState(state);
    }

    private static void advanceLoadedTimers(final LostSoulEntity soul) {
        final TransientState scratch = soul.lostSoulTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.discoveryCooldownTicks = Math.max(0, scratch.discoveryCooldownTicks - 1);
        scratch.wanderCooldownTicks = Math.max(0, scratch.wanderCooldownTicks - 1);

        LostSoulState state = soul.lostSoulState();
        final LostSoulState.Cadence cadence = state.cadence();
        state = state.withCadence(new LostSoulState.Cadence(
            LostSoulRules.decrementLoaded(cadence.cooldownTicks()),
            cadence.routeFailures(),
            LostSoulRules.decrementLoaded(cadence.routeRetryTicks())
        ));
        final Phase phase = state.phase();
        if (phase == Phase.APPROACH || phase == Phase.PETITION || phase == Phase.SETTLE) {
            final LostSoulState.Episode episode = state.episode();
            state = state.withEpisode(new LostSoulState.Episode(
                LostSoulRules.decrementLoaded(episode.remainingTicks()),
                phase == Phase.PETITION
                    ? LostSoulRules.decrementLoaded(episode.petitionRemainingTicks())
                    : episode.petitionRemainingTicks(),
                phase == Phase.SETTLE
                    ? LostSoulRules.decrementLoaded(episode.settleRemainingTicks())
                    : episode.settleRemainingTicks(),
                phase == Phase.PETITION
                    ? LostSoulRules.decrementLoaded(episode.pulseRemainingTicks())
                    : episode.pulseRemainingTicks(),
                episode.pulsesEmitted()
            ));
        }
        soul.setLostSoulState(state);
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final LostSoulEntity soul, final ServerLevel level) {
        final TransientState scratch = soul.lostSoulTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !soul.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = LostSoulRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = SpectralEntity.observeHazard(
            soul, level, LostSoulRules.MAX_HAZARD_READS
        );
        soul.lostSoulCounters().blockReads += LostSoulRules.MAX_HAZARD_READS;
        if (!LostSoulRules.hazardPreempts(soul.lostSoulState().phase(), scratch.hazardActive)) {
            return false;
        }
        soul.lostSoulCounters().hazardInterruptions++;
        searchAndRoute(soul, level, soul.blockPosition(),
            LostSoulRules.ESCAPE_SEARCH_HORIZONTAL, LostSoulRules.ESCAPE_SEARCH_VERTICAL,
            LostSoulRules.MAX_SAFE_CANDIDATES, Optional.empty(), true, SpectralEntity.ESCAPE_SPEED);
        return true;
    }

    // ---------------------------------------------------------------- bound attendance

    /**
     * Quiet bound attendance. The shade follows inside a bounded band and supplies the preserved
     * aura. It never copies an owner target, never attacks on the owner's behalf, never teleports
     * to its owner, never collects souls, never consumes corpses, and never despawns as a reward.
     */
    private static void tickBoundAttendance(final LostSoulEntity soul, final ServerLevel level) {
        LostSoulState state = soul.lostSoulState();
        if (state.episode().active() || state.anchor().present()) {
            // An owner acquired outside the interaction hook still ends the episode through the
            // one shared ender, so EpisodeEnd.BOUND is the single statement of that priority.
            endEpisodeIfRequired(soul, level, state);
            state = soul.lostSoulState();
        }
        if (state.phase() != Phase.BOUND) {
            state = state.bind();
            soul.setLostSoulState(state);
        }
        soul.setTarget(null);
        final Optional<LivingEntity> owner = SpectralEntity.resolveOwner(soul, level);
        if (owner.isEmpty()) {
            soul.getNavigation().stop();
            return;
        }
        final LivingEntity attended = owner.orElseThrow();
        final double distanceSquared = soul.distanceToSqr(attended);
        // The recall is evaluated before the attendance range gate so a shade that drifted past
        // the gate is still recovered instead of being silently abandoned.
        if (LostSoulRules.ownerRecallRequired(distanceSquared)) {
            soul.lostSoulCounters().ownerRecalls++;
            soul.getNavigation().stop();
            soul.lostSoulTransient().destination = null;
            soul.teleportTo(attended.getX() + 1.0D, attended.getY(), attended.getZ() + 1.0D);
            return;
        }
        if (!LostSoulRules.ownerAttendanceAllowed(true, attended.isAlive(),
            attended.level() == level, distanceSquared)) {
            soul.getNavigation().stop();
            return;
        }
        if (LostSoulRules.auraDue(soul.tickCount)) {
            soul.lostSoulCounters().auraPulses++;
            SpectralEntity.applyOwnerAura(attended, LostSoulRules.AURA_NIGHT_VISION_TICKS);
        }
        final BandAction action = LostSoulRules.followBand(distanceSquared);
        if (action == BandAction.HOLD) {
            return;
        }
        if (!soul.getNavigation().isDone()) {
            return;
        }
        final Vec3 toOwner = attended.position().subtract(soul.position());
        final Vec3 direction = toOwner.lengthSqr() < 1.0E-4D
            ? new Vec3(1.0D, 0.0D, 0.0D)
            : toOwner.normalize();
        final double band = (LostSoulRules.FOLLOW_BAND_MIN + LostSoulRules.FOLLOW_BAND_MAX) / 2.0D;
        final Vec3 goal = action == BandAction.APPROACH
            ? attended.position().subtract(direction.scale(band))
            : attended.position().subtract(direction.scale(band + 1.0D));
        searchAndRoute(soul, level, BlockPos.containing(goal.x, goal.y + 1.0D, goal.z),
            2, 2, LostSoulRules.MAX_SAFE_CANDIDATES, Optional.empty(), false,
            SpectralEntity.ROUTE_SPEED);
    }

    /**
     * Atomic binding completion, invoked inside the very interaction that wrote the owner UUID.
     * The whole memorial episode, its anchor, its petition feedback and its route accounting are
     * cancelled in one state write, and navigation stops immediately.
     */
    public static void onBindingCommitted(
        final LostSoulEntity soul,
        final ServerLevel level,
        final UUID owner
    ) {
        soul.getNavigation().stop();
        soul.lostSoulTransient().destination = null;
        soul.setTarget(null);
        final LostSoulState state = soul.lostSoulState();
        if (LostSoulRules.bindingPreempts(state.phase(), true)
            && (state.episode().active() || state.anchor().present())) {
            soul.lostSoulCounters().episodesEnded++;
        }
        soul.setLostSoulState(state.bind());
        SpectralEntity.resolveOwner(soul, level)
            .filter(candidate -> candidate.getUUID().equals(owner))
            .ifPresent(candidate -> {
                soul.lostSoulCounters().auraPulses++;
                SpectralEntity.applyOwnerAura(candidate, LostSoulRules.AURA_NIGHT_VISION_TICKS);
            });
    }

    // ---------------------------------------------------------------- memorial episode

    private static void tickWander(final LostSoulEntity soul, final ServerLevel level) {
        LostSoulState state = soul.lostSoulState();
        if (state.phase() == Phase.COOLDOWN && state.cadence().cooldownTicks() <= 0) {
            state = state.withPhase(Phase.WANDER);
            soul.setLostSoulState(state);
        }
        if (LostSoulRules.episodeStartAllowed(false, state.cadence().cooldownTicks(),
            state.anchor().present())
            && soul.lostSoulTransient().discoveryCooldownTicks <= 0) {
            soul.lostSoulTransient().discoveryCooldownTicks = LostSoulRules.DISCOVERY_INTERVAL_TICKS;
            if (discoverAnchor(soul, level)) {
                return;
            }
        }
        if (soul.lostSoulTransient().wanderCooldownTicks <= 0 && soul.getNavigation().isDone()) {
            soul.lostSoulTransient().wanderCooldownTicks = LostSoulRules.WANDER_INTERVAL_TICKS
                + LostSoulRules.stableOffset(soul.getUUID(), 40);
            searchAndRoute(soul, level, soul.blockPosition(),
                LostSoulRules.WANDER_RADIUS_HORIZONTAL, LostSoulRules.WANDER_RADIUS_VERTICAL,
                LostSoulRules.MAX_WANDER_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
    }

    /**
     * One bounded memorial scan over the exact 13 x 5 x 13 envelope. The whole envelope must
     * already be loaded before a single read happens, the read count can never exceed
     * {@link LostSoulRules#MAX_ANCHOR_READS}, and at most sixteen matches are retained before the
     * stable distance-then-position ordering picks exactly one anchor.
     */
    private static boolean discoverAnchor(final LostSoulEntity soul, final ServerLevel level) {
        final Counters counters = soul.lostSoulCounters();
        counters.anchorScans++;
        final BlockPos origin = soul.blockPosition();
        if (!SpectralEntity.envelopeLoaded(level, origin,
            LostSoulRules.ANCHOR_SEARCH_HORIZONTAL, LostSoulRules.ANCHOR_SEARCH_VERTICAL)) {
            return false;
        }
        List<AnchorCandidate> inspected = new ArrayList<>();
        int reads = 0;
        envelope:
        for (int dx = -LostSoulRules.ANCHOR_SEARCH_HORIZONTAL;
             dx <= LostSoulRules.ANCHOR_SEARCH_HORIZONTAL; dx++) {
            for (int dy = -LostSoulRules.ANCHOR_SEARCH_VERTICAL;
                 dy <= LostSoulRules.ANCHOR_SEARCH_VERTICAL; dy++) {
                for (int dz = -LostSoulRules.ANCHOR_SEARCH_HORIZONTAL;
                     dz <= LostSoulRules.ANCHOR_SEARCH_HORIZONTAL; dz++) {
                    if (reads >= LostSoulRules.MAX_ANCHOR_READS) {
                        break envelope;
                    }
                    reads++;
                    counters.anchorReads++;
                    counters.blockReads++;
                    final BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).is(SpectralEntity.SOUL_LIGHTS)) {
                        continue;
                    }
                    counters.anchorCandidates++;
                    inspected.add(new AnchorCandidate(
                        candidate.asLong(), origin.distSqr(candidate)
                    ));
                    // Retention is bounded by re-ranking rather than by refusing later matches,
                    // so a lantern in the far corner of the envelope can still win and the
                    // selected anchor is genuinely the nearest one in the whole box.
                    if (inspected.size() > LostSoulRules.MAX_ANCHOR_CANDIDATES_RETAINED) {
                        inspected = new ArrayList<>(LostSoulRules.rank(inspected));
                    }
                }
            }
        }
        final Optional<AnchorCandidate> selected = LostSoulRules.select(inspected);
        if (selected.isEmpty()) {
            return false;
        }
        counters.episodesStarted++;
        soul.setLostSoulState(soul.lostSoulState()
            .withAnchor(LostSoulState.Anchor.at(
                BlockPos.of(selected.orElseThrow().packedPosition()), SpectralEntity.dimensionOf(level)
            ))
            .withEpisode(LostSoulState.Episode.started())
            .withCadence(new LostSoulState.Cadence(0, 0,
                soul.lostSoulState().cadence().routeRetryTicks()))
            .withPhase(Phase.APPROACH));
        return true;
    }

    private static void tickApproach(final LostSoulEntity soul, final ServerLevel level) {
        final LostSoulState state = soul.lostSoulState();
        if (endEpisodeIfRequired(soul, level, state)) {
            return;
        }
        final BlockPos anchor = state.anchor().position().orElseThrow();
        final double distanceSquared = soul.distanceToSqr(Vec3.atCenterOf(anchor));
        if (LostSoulRules.petitionReached(distanceSquared)) {
            soul.getNavigation().stop();
            soul.setLostSoulState(state
                .withEpisode(new LostSoulState.Episode(
                    state.episode().remainingTicks(),
                    LostSoulRules.PETITION_TICKS,
                    0,
                    LostSoulRules.PETITION_PULSE_INTERVAL_TICKS,
                    // The per-episode pulse count is carried across a band re-entry and across a
                    // reload, so the cap of three is a property of the episode rather than of the
                    // current approach.
                    state.episode().pulsesEmitted()
                ))
                .withPhase(Phase.PETITION));
            return;
        }
        if (soul.getNavigation().isDone()) {
            searchAndRoute(soul, level, anchor.above(1), 2, 2,
                LostSoulRules.MAX_SAFE_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
    }

    private static void tickPetition(final LostSoulEntity soul, final ServerLevel level) {
        LostSoulState state = soul.lostSoulState();
        if (endEpisodeIfRequired(soul, level, state)) {
            return;
        }
        final BlockPos anchor = state.anchor().position().orElseThrow();
        final Vec3 memorial = Vec3.atCenterOf(anchor);
        final BandAction band = LostSoulRules.petitionBand(soul.distanceToSqr(memorial));
        if (band == BandAction.WITHDRAW && soul.getNavigation().isDone()) {
            searchAndRoute(soul, level, anchor.above(1), 2, 1,
                LostSoulRules.MAX_SAFE_CANDIDATES, Optional.of(memorial), true,
                SpectralEntity.ROUTE_SPEED);
        } else if (band != BandAction.WITHDRAW) {
            soul.getNavigation().stop();
        }
        soul.getLookControl().setLookAt(memorial);
        if (LostSoulRules.petitionPulsesRemaining(state.episode().pulsesEmitted()) > 0
            && LostSoulRules.pulseDue(state.episode().pulseRemainingTicks(),
                state.episode().pulsesEmitted(), LostSoulRules.MAX_PETITION_PULSES)) {
            state = state.withEpisode(new LostSoulState.Episode(
                state.episode().remainingTicks(),
                state.episode().petitionRemainingTicks(),
                state.episode().settleRemainingTicks(),
                LostSoulRules.PETITION_PULSE_INTERVAL_TICKS,
                state.episode().pulsesEmitted() + 1
            ));
            soul.lostSoulCounters().petitionPulses++;
            level.playSound(null, soul.getX(), soul.getY(), soul.getZ(),
                SoundEvents.VEX_AMBIENT, soul.getSoundSource(), 0.5F, 0.5F);
            level.sendParticles(ParticleTypes.SOUL, soul.getX(), soul.getY() + 0.6D, soul.getZ(),
                LostSoulRules.MAX_PETITION_PARTICLES, 0.3D, 0.4D, 0.3D, 0.005D);
        }
        if (state.episode().petitionRemainingTicks() <= 0) {
            state = state.withEpisode(new LostSoulState.Episode(
                state.episode().remainingTicks(), 0, LostSoulRules.SETTLE_TICKS, 0,
                state.episode().pulsesEmitted()
            )).withPhase(Phase.SETTLE);
        }
        soul.setLostSoulState(state);
    }

    private static void tickSettle(final LostSoulEntity soul, final ServerLevel level) {
        final LostSoulState state = soul.lostSoulState();
        if (endEpisodeIfRequired(soul, level, state)) {
            return;
        }
        if (state.episode().settleRemainingTicks() <= 0) {
            endEpisode(soul);
            return;
        }
        final BlockPos anchor = state.anchor().position().orElseThrow();
        if (!LostSoulRules.settleReached(soul.distanceToSqr(Vec3.atCenterOf(anchor)))
            && soul.getNavigation().isDone()) {
            searchAndRoute(soul, level, anchor.above(1), 2, 2,
                LostSoulRules.MAX_SAFE_CANDIDATES, Optional.empty(), true,
                SpectralEntity.ROUTE_SPEED);
        }
    }

    private static boolean endEpisodeIfRequired(
        final LostSoulEntity soul,
        final ServerLevel level,
        final LostSoulState state
    ) {
        final Optional<BlockPos> anchor = state.anchor().position();
        final boolean sameDimension = state.anchor().dimension()
            .map(SpectralEntity.dimensionOf(level)::equals)
            .orElse(false);
        final boolean loaded = anchor.map(level::hasChunkAt).orElse(false);
        final boolean stillMemorial = loaded && anchor
            .map(position -> level.getBlockState(position).is(SpectralEntity.SOUL_LIGHTS))
            .orElse(false);
        if (loaded) {
            soul.lostSoulCounters().blockReads++;
        }
        final EpisodeEnd end = LostSoulRules.episodeEnd(new LostSoulRules.AnchorObservation(
            anchor.isPresent(), sameDimension, loaded, stillMemorial,
            state.episode().remainingTicks(), state.cadence().routeFailures(),
            CreatureBehaviorState.owner(soul).isPresent()
        ));
        if (end == EpisodeEnd.NONE) {
            return false;
        }
        endEpisode(soul);
        return true;
    }

    private static void endEpisode(final LostSoulEntity soul) {
        soul.lostSoulCounters().episodesEnded++;
        soul.getNavigation().stop();
        soul.lostSoulTransient().destination = null;
        soul.setLostSoulState(soul.lostSoulState().endEpisode());
    }

    // ---------------------------------------------------------------- movement

    private static boolean searchAndRoute(
        final LostSoulEntity soul,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final double speed
    ) {
        if (!LostSoulRules.pathRequestAllowed(
            soul.lostSoulTransient().pathCooldownTicks,
            soul.lostSoulState().cadence().routeRetryTicks()
        )) {
            return false;
        }
        final Optional<BlockPos> destination = findSafeDestination(
            soul, level, center, horizontalRadius, verticalRadius, candidateBudget,
            awayFrom, avoidHazards
        );
        if (destination.isEmpty()) {
            recordUnroutableSearch(soul);
            return false;
        }
        return requestRoute(soul, destination.orElseThrow(), speed);
    }

    /**
     * A search that qualified no candidate costs the same real work as one that did, so it arms
     * the same path cadence and counts the same route failure. Without this a caller gated only
     * by {@code getNavigation().isDone()} would re-run the whole candidate sweep every tick for
     * as long as the surroundings stayed unusable.
     */
    private static void recordUnroutableSearch(final LostSoulEntity soul) {
        final TransientState scratch = soul.lostSoulTransient();
        scratch.pathCooldownTicks = LostSoulRules.PATH_INTERVAL_TICKS;
        scratch.destination = null;
        final LostSoulState state = soul.lostSoulState();
        final int failures = LostSoulRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new LostSoulRules.RouteResult(false, false, false)
        );
        int retry = state.cadence().routeRetryTicks();
        if (LostSoulRules.routeExhausted(failures)) {
            retry = LostSoulRules.routeBackoffAfter(failures);
            soul.getNavigation().stop();
        }
        soul.setLostSoulState(state.withCadence(new LostSoulState.Cadence(
            state.cadence().cooldownTicks(), failures, retry
        )));
    }

    /**
     * Strict route request. The third consecutive failure stops navigation and starts the
     * backoff; the counter is persisted at its observable maximum so the next decision's
     * {@link LostSoulRules#episodeEnd} can release on it, and only a release or a later success
     * resets it.
     */
    static boolean requestRoute(
        final LostSoulEntity soul,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = soul.lostSoulTransient();
        LostSoulState state = soul.lostSoulState();
        if (!LostSoulRules.pathRequestAllowed(
            scratch.pathCooldownTicks, state.cadence().routeRetryTicks()
        )) {
            return false;
        }
        scratch.pathCooldownTicks = LostSoulRules.PATH_INTERVAL_TICKS;
        soul.lostSoulCounters().navigationRequests++;
        final Path path = soul.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && soul.getNavigation().moveTo(path, speed);
        final int failures = LostSoulRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new LostSoulRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (LostSoulRules.routeExhausted(failures)) {
            retry = LostSoulRules.routeBackoffAfter(failures);
            soul.getNavigation().stop();
            scratch.destination = null;
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        state = state.withCadence(new LostSoulState.Cadence(
            state.cadence().cooldownTicks(), failures, retry
        ));
        soul.setLostSoulState(state);
        return accepted;
    }

    /**
     * Deterministic bounded safe-destination search sharing the 24-candidate and 256-read budget.
     * The origin is always excluded, the complete footprint must already be loaded and inside the
     * world border, and preference is the species lexicographic order.
     */
    static Optional<BlockPos> findSafeDestination(
        final LostSoulEntity soul,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = soul.lostSoulCounters();
        counters.safeSearches++;
        final BlockPos origin = soul.blockPosition();
        final java.util.Comparator<LostSoulRules.SafeCandidate> preference =
            LostSoulRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        LostSoulRules.SafeCandidate bestFacts = null;
        for (final LostSoulRules.SafeSearchOffset offset : LostSoulRules.safeSearchOffsets(
            soul.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + SpectralEntity.READS_PER_SAFE_CANDIDATE > LostSoulRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            // Charged before the filter: a rejected candidate spent these reads too, so the
            // ceiling binds the real cost instead of only the accepted minority.
            reads += SpectralEntity.READS_PER_SAFE_CANDIDATE;
            counters.blockReads += SpectralEntity.READS_PER_SAFE_CANDIDATE;
            final Optional<SpectralEntity.SafeQualification> qualified =
                SpectralEntity.qualifySafeCandidate(soul, level, candidate, avoidHazards);
            if (qualified.isEmpty()) {
                continue;
            }
            final LostSoulRules.SafeCandidate facts = new LostSoulRules.SafeCandidate(
                awayFrom.map(threat -> Vec3.atCenterOf(candidate).distanceToSqr(threat)).orElse(0.0D),
                qualified.orElseThrow().hazardFree(),
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
}
