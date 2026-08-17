package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.PoltergeistRules.BandAction;
import com.kadamitas.warlockery.entity.PoltergeistRules.EpisodeEnd;
import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import com.kadamitas.warlockery.entity.PoltergeistRules.PropCandidate;
import com.kadamitas.warlockery.entity.PoltergeistRules.ScanOffset;
import com.kadamitas.warlockery.entity.PoltergeistRules.TargetCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Poltergeist behavior controller and the sole ordinary navigation and
 * disturbance writer for this species. Every scan, charged block read, candidate visit, path
 * request, pulse, lift, velocity write and hit is counted against the declared hard budgets.
 *
 * <p>Nothing here enumerates a dimension, forces a chunk, iterates entities globally, edits or
 * replaces a block, opens a container or an inventory, mutates an item stack, changes a pickup
 * delay or a thrower, writes another entity's persistent state, sets a combat target, or binds an
 * owner. The only outward mutations are one Levitation window, one Slow Falling recovery, one
 * velocity write on one already-loose item, one bell ring that leaves the block state untouched,
 * and the preserved damage blink.</p>
 */
public final class PoltergeistRuntime {
    private static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    /**
     * The honest worst-case read cost of one {@link #qualifySafeCandidate} call: the world-border
     * test, four chunk-presence tests, the block state, the fluid state and the collision sweep.
     * Callers charge this before the candidate can be filtered, so a rejected candidate costs
     * exactly what it actually spent and the charged-read ceiling genuinely bounds the search.
     */
    private static final int READS_PER_SAFE_CANDIDATE = 8;
    private static final double ROUTE_SPEED = 1.0D;
    private static final double ESCAPE_SPEED = 1.2D;
    private static final int UNSEEDED_CURSOR = Integer.MIN_VALUE;

    private PoltergeistRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long targetScans;
        long emptyTargetScans;
        long propScans;
        long emptyPropScans;
        long candidateVisits;
        long bellScans;
        long emptyBellScans;
        long bellReads;
        long blockReads;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long rattlePulses;
        long bellRings;
        long lifts;
        long velocityWrites;
        long throwHits;
        long recoveries;
        long blinks;
        long damageReactions;
        long hazardInterruptions;
        long episodesStarted;
        long episodesEnded;

        public long targetScans() { return targetScans; }
        public long emptyTargetScans() { return emptyTargetScans; }
        public long propScans() { return propScans; }
        public long emptyPropScans() { return emptyPropScans; }
        public long candidateVisits() { return candidateVisits; }
        public long bellScans() { return bellScans; }
        public long emptyBellScans() { return emptyBellScans; }
        public long bellReads() { return bellReads; }
        public long blockReads() { return blockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long rattlePulses() { return rattlePulses; }
        public long bellRings() { return bellRings; }
        public long lifts() { return lifts; }
        public long velocityWrites() { return velocityWrites; }
        public long throwHits() { return throwHits; }
        public long recoveries() { return recoveries; }
        public long blinks() { return blinks; }
        public long damageReactions() { return damageReactions; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long episodesStarted() { return episodesStarted; }
        public long episodesEnded() { return episodesEnded; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by one cadence but can never replay a pulse, a lift, a velocity write, a hit, or an episode.
     * The marked target and the chosen prop live here precisely so no saved reference can be
     * resurrected into a delayed throw.
     */
    public static final class TransientState {
        boolean reconciled;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int discoveryCooldownTicks;
        int idleCooldownTicks;
        int bellScanCooldownTicks;
        boolean hazardActive;
        UUID targetId;
        UUID propId;
        int bellScanCursor = UNSEEDED_CURSOR;
        BlockPos destination;

        public void resetForLoad() {
            reconciled = false;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            discoveryCooldownTicks = 0;
            idleCooldownTicks = 0;
            bellScanCooldownTicks = 0;
            hazardActive = false;
            targetId = null;
            propId = null;
            bellScanCursor = UNSEEDED_CURSOR;
            destination = null;
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        public Optional<UUID> markedTarget() {
            return Optional.ofNullable(targetId);
        }

        public Optional<UUID> markedProp() {
            return Optional.ofNullable(propId);
        }
    }

    public static void tick(final PoltergeistEntity poltergeist, final ServerLevel level) {
        reconcileOnLoad(poltergeist, level);
        advanceLoadedTimers(poltergeist);
        if (tickHazard(poltergeist, level)) {
            return;
        }
        switch (poltergeist.poltergeistState().phase()) {
            case LURK -> tickLurk(poltergeist, level);
            case RATTLE -> tickRattle(poltergeist, level);
            case MARK -> tickMark(poltergeist, level);
            case LIFT -> tickLift(poltergeist, level);
            case THROW -> tickThrow(poltergeist, level);
            case RECOVER -> tickRecover(poltergeist, level);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.discoveryCooldownTicks = PoltergeistRules.stableOffset(
            poltergeist.getUUID(), PoltergeistRules.DISCOVERY_INTERVAL_TICKS
        );
        scratch.idleCooldownTicks = PoltergeistRules.stableOffset(
            poltergeist.getUUID(), PoltergeistRules.IDLE_INTERVAL_TICKS
        );
        poltergeist.getNavigation().stop();
        poltergeist.setTarget(null);
    }

    private static void advanceLoadedTimers(final PoltergeistEntity poltergeist) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.discoveryCooldownTicks = Math.max(0, scratch.discoveryCooldownTicks - 1);
        scratch.idleCooldownTicks = Math.max(0, scratch.idleCooldownTicks - 1);
        scratch.bellScanCooldownTicks = Math.max(0, scratch.bellScanCooldownTicks - 1);

        PoltergeistState state = poltergeist.poltergeistState();
        final PoltergeistState.Cadence cadence = state.cadence();
        state = state.withCadence(new PoltergeistState.Cadence(
            PoltergeistRules.decrementLoaded(cadence.cooldownTicks()),
            cadence.routeFailures(),
            PoltergeistRules.decrementLoaded(cadence.routeRetryTicks())
        ));
        if (state.phase() != Phase.LURK) {
            PoltergeistState.Episode episode = state.episode();
            episode = episode
                .withRemaining(PoltergeistRules.decrementLoaded(episode.remainingTicks()))
                .withPhaseRemaining(
                    PoltergeistRules.decrementLoaded(episode.phaseRemainingTicks())
                );
            if (state.phase() == Phase.RATTLE) {
                episode = episode.withPulse(
                    PoltergeistRules.decrementLoaded(episode.pulseRemainingTicks()),
                    episode.pulsesEmitted()
                );
            }
            state = state.withEpisode(episode);
        }
        poltergeist.setPoltergeistState(state);
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !poltergeist.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = PoltergeistRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = observeHazard(poltergeist, level);
        poltergeist.poltergeistCounters().blockReads += PoltergeistRules.MAX_HAZARD_READS;
        if (!PoltergeistRules.hazardPreempts(
            poltergeist.poltergeistState().phase(), scratch.hazardActive
        )) {
            return false;
        }
        poltergeist.poltergeistCounters().hazardInterruptions++;
        // Only a live attack phase is cancelled. Cancelling from LURK would open a phantom recovery
        // that later armed a cooldown no episode ever earned, and cancelling from RECOVER would
        // restart the very window that is already closing the episode.
        final Phase interrupted = poltergeist.poltergeistState().phase();
        if (interrupted != Phase.LURK && interrupted != Phase.RECOVER) {
            endEpisode(poltergeist);
        }
        // The escape is exempt from the episode path quota. Hazard escape is the top priority rung,
        // so an episode that had already spent its quota must not be able to strand a burning
        // disturbance in the fire that cancelled it.
        searchAndRoute(poltergeist, level, poltergeist.blockPosition(),
            PoltergeistRules.ESCAPE_SEARCH_HORIZONTAL, PoltergeistRules.ESCAPE_SEARCH_VERTICAL,
            PoltergeistRules.MAX_SAFE_CANDIDATES, Optional.empty(), true, ESCAPE_SPEED, false);
        return true;
    }

    // ---------------------------------------------------------------- damage reaction

    /**
     * The damage-reaction rung, invoked from the very call that accepted the damage. The preserved
     * PHASED blink still displaces the disturbance, and any struck attack phase is abandoned into
     * the recovery that lands the lifted target safely, so a cancelled episode can never leave a
     * delayed lift or throw behind.
     */
    public static void onAcceptedDamage(
        final PoltergeistEntity poltergeist,
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
        final Counters counters = poltergeist.poltergeistCounters();
        if (PoltergeistRules.damageReactionPreempts(poltergeist.poltergeistState().phase())) {
            counters.damageReactions++;
            endEpisode(poltergeist);
        }
        if (!PoltergeistRules.blinkOnDamage(amount) || source == null) {
            return;
        }
        counters.blinks++;
        poltergeist.randomTeleport(
            poltergeist.getX() + level.getRandom().nextIntBetweenInclusive(
                -PoltergeistRules.BLINK_HORIZONTAL, PoltergeistRules.BLINK_HORIZONTAL),
            poltergeist.getY() + level.getRandom().nextIntBetweenInclusive(
                -PoltergeistRules.BLINK_VERTICAL, PoltergeistRules.BLINK_VERTICAL),
            poltergeist.getZ() + level.getRandom().nextIntBetweenInclusive(
                -PoltergeistRules.BLINK_HORIZONTAL, PoltergeistRules.BLINK_HORIZONTAL),
            true
        );
    }

    // ---------------------------------------------------------------- idle

    private static void tickLurk(final PoltergeistEntity poltergeist, final ServerLevel level) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        final PoltergeistState state = poltergeist.poltergeistState();
        if (PoltergeistRules.episodeStartAllowed(
                state.cadence().cooldownTicks(), state.episode().active())
            && scratch.discoveryCooldownTicks <= 0) {
            // The cadence is armed before the scan runs, so a scan that qualifies nothing costs the
            // same one attempt as a scan that did and can never retry every tick forever.
            scratch.discoveryCooldownTicks = PoltergeistRules.DISCOVERY_INTERVAL_TICKS;
            if (startEpisode(poltergeist, level)) {
                return;
            }
        }
        if (scratch.idleCooldownTicks <= 0 && poltergeist.getNavigation().isDone()) {
            scratch.idleCooldownTicks = PoltergeistRules.IDLE_INTERVAL_TICKS
                + PoltergeistRules.stableOffset(poltergeist.getUUID(), 40);
            searchAndRoute(poltergeist, level, poltergeist.blockPosition(),
                PoltergeistRules.IDLE_RADIUS_HORIZONTAL, PoltergeistRules.IDLE_RADIUS_VERTICAL,
                PoltergeistRules.MAX_IDLE_CANDIDATES, Optional.empty(), true, ROUTE_SPEED, false);
        }
    }

    /**
     * Opens exactly one disturbance episode. One loaded living attackable player is marked, and at
     * most one loaded loose item is chosen as the prop. A scan that qualifies no target records the
     * failure and opens nothing: the prop scan never runs without a target, so an empty room costs
     * one player query per discovery cadence and nothing else.
     */
    private static boolean startEpisode(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final Optional<UUID> target = selectTarget(poltergeist, level);
        if (target.isEmpty()) {
            return false;
        }
        final TransientState scratch = poltergeist.poltergeistTransient();
        scratch.targetId = target.orElseThrow();
        scratch.propId = selectProp(poltergeist, level).orElse(null);
        poltergeist.poltergeistCounters().episodesStarted++;
        poltergeist.setPoltergeistState(poltergeist.poltergeistState()
            .withEpisode(PoltergeistState.Episode.started())
            .withCadence(new PoltergeistState.Cadence(
                0, 0, poltergeist.poltergeistState().cadence().routeRetryTicks()
            ))
            .withPhase(Phase.RATTLE));
        return true;
    }

    /**
     * One bounded player scan over the preserved eight-block envelope.
     *
     * <p>Every player the query returns is visited and charged before any filter can reject it, so
     * the counted cost is the real cost rather than the accepted minority. Retention is bounded by
     * re-ranking rather than by refusing later matches, so a player in the far corner of the
     * envelope can still win and the marked target is genuinely the nearest eligible one in the
     * whole box rather than the first one the level happened to iterate.</p>
     */
    private static Optional<UUID> selectTarget(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final Counters counters = poltergeist.poltergeistCounters();
        counters.targetScans++;
        List<TargetCandidate> inspected = new ArrayList<>();
        for (final Player player : level.getEntitiesOfClass(
            Player.class, poltergeist.getBoundingBox().inflate(PoltergeistRules.TARGET_SEARCH_RANGE)
        )) {
            counters.candidateVisits++;
            final double distanceSquared = poltergeist.distanceToSqr(player);
            if (!eligibleTarget(player, level, distanceSquared)) {
                continue;
            }
            inspected.add(new TargetCandidate(player.getUUID(), distanceSquared));
            if (inspected.size() > PoltergeistRules.MAX_RETAINED_CANDIDATES) {
                inspected = new ArrayList<>(PoltergeistRules.rankTargets(inspected));
            }
        }
        final Optional<TargetCandidate> selected = PoltergeistRules.selectTarget(inspected);
        if (selected.isEmpty()) {
            counters.emptyTargetScans++;
        }
        return selected.map(TargetCandidate::id);
    }

    private static boolean eligibleTarget(
        final Player player,
        final ServerLevel level,
        final double distanceSquared
    ) {
        return player.isAlive()
            && !player.isSpectator()
            && !player.isCreative()
            && !player.isInvulnerable()
            && player.level() == level
            && distanceSquared <= PoltergeistRules.TARGET_SEARCH_RANGE_SQUARED;
    }

    /**
     * One bounded loose-item scan over the preserved six-block envelope, using the same
     * charge-before-filter and re-ranking retention contract as the target scan. Nothing about the
     * chosen item is persisted, reserved, or made unpickable: a player may always take the prop.
     */
    private static Optional<UUID> selectProp(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final Counters counters = poltergeist.poltergeistCounters();
        counters.propScans++;
        List<PropCandidate> inspected = new ArrayList<>();
        for (final ItemEntity item : level.getEntitiesOfClass(
            ItemEntity.class, poltergeist.getBoundingBox().inflate(PoltergeistRules.PROP_SEARCH_RANGE)
        )) {
            counters.candidateVisits++;
            final double distanceSquared = poltergeist.distanceToSqr(item);
            if (!eligibleProp(item, level, distanceSquared)) {
                continue;
            }
            inspected.add(new PropCandidate(item.getUUID(), distanceSquared));
            if (inspected.size() > PoltergeistRules.MAX_RETAINED_CANDIDATES) {
                inspected = new ArrayList<>(PoltergeistRules.rankProps(inspected));
            }
        }
        final Optional<PropCandidate> selected = PoltergeistRules.selectProp(inspected);
        if (selected.isEmpty()) {
            counters.emptyPropScans++;
        }
        return selected.map(PropCandidate::id);
    }

    private static boolean eligibleProp(
        final ItemEntity item,
        final ServerLevel level,
        final double distanceSquared
    ) {
        return item.isAlive()
            && !item.getItem().isEmpty()
            && item.level() == level
            && distanceSquared <= PoltergeistRules.PROP_SEARCH_RANGE_SQUARED;
    }

    // ---------------------------------------------------------------- disturbance episode

    private static void tickRattle(final PoltergeistEntity poltergeist, final ServerLevel level) {
        if (endEpisodeIfRequired(poltergeist, level)) {
            return;
        }
        PoltergeistState state = poltergeist.poltergeistState();
        final LivingEntity target = resolveTarget(poltergeist, level).orElseThrow();
        poltergeist.getNavigation().stop();
        poltergeist.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (PoltergeistRules.rattlePulsesRemaining(state.episode().pulsesEmitted()) > 0
            && PoltergeistRules.pulseDue(
                state.episode().pulseRemainingTicks(), state.episode().pulsesEmitted())) {
            state = state.withEpisode(state.episode().withPulse(
                PoltergeistRules.RATTLE_PULSE_INTERVAL_TICKS, state.episode().pulsesEmitted() + 1
            ));
            poltergeist.poltergeistCounters().rattlePulses++;
            level.playSound(null, poltergeist.getX(), poltergeist.getY(), poltergeist.getZ(),
                SoundEvents.VEX_CHARGE, poltergeist.getSoundSource(), 0.6F, 0.6F);
            level.sendParticles(ParticleTypes.SOUL, poltergeist.getX(),
                poltergeist.getY() + 0.6D, poltergeist.getZ(),
                PoltergeistRules.MAX_RATTLE_PARTICLES, 0.3D, 0.4D, 0.3D, 0.005D);
        }
        poltergeist.setPoltergeistState(state);
        maybeRingBell(poltergeist, level);
        state = poltergeist.poltergeistState();
        if (state.episode().phaseRemainingTicks() <= 0) {
            poltergeist.setPoltergeistState(state
                .withEpisode(state.episode().withPhaseRemaining(PoltergeistRules.MARK_TICKS))
                .withPhase(Phase.MARK));
        }
    }

    private static void tickMark(final PoltergeistEntity poltergeist, final ServerLevel level) {
        if (endEpisodeIfRequired(poltergeist, level)) {
            return;
        }
        final PoltergeistState state = poltergeist.poltergeistState();
        final LivingEntity target = resolveTarget(poltergeist, level).orElseThrow();
        poltergeist.getLookControl().setLookAt(target, 30.0F, 30.0F);
        final double distanceSquared = poltergeist.distanceToSqr(target);
        // The mark band is strictly inside the lift reach, so holding the band is on its own the
        // proof that the lift is legal here; re-testing the reach would be a branch that can never
        // be false. The reach is re-tested in the lift window, where the target has had forty ticks
        // to walk away from it.
        if (PoltergeistRules.markBand(distanceSquared) == BandAction.HOLD) {
            poltergeist.getNavigation().stop();
            poltergeist.setPoltergeistState(state
                .withEpisode(state.episode().withPhaseRemaining(PoltergeistRules.LIFT_TICKS))
                .withPhase(Phase.LIFT));
            return;
        }
        if (state.episode().phaseRemainingTicks() <= 0) {
            // The approach window is finite. A disturbance that never reached its band recovers
            // instead of levitating a target it never actually closed on.
            endEpisode(poltergeist);
            return;
        }
        if (!poltergeist.getNavigation().isDone()) {
            return;
        }
        final Vec3 toTarget = target.position().subtract(poltergeist.position());
        final Vec3 direction = toTarget.lengthSqr() < 1.0E-4D
            ? new Vec3(1.0D, 0.0D, 0.0D)
            : toTarget.normalize();
        final double band =
            (PoltergeistRules.MARK_BAND_MIN + PoltergeistRules.MARK_BAND_MAX) / 2.0D;
        final Vec3 goal = target.position().subtract(direction.scale(band));
        searchAndRoute(poltergeist, level, BlockPos.containing(goal.x, goal.y + 1.0D, goal.z),
            2, 2, PoltergeistRules.MAX_SAFE_CANDIDATES, Optional.empty(), false, ROUTE_SPEED, true);
    }

    private static void tickLift(final PoltergeistEntity poltergeist, final ServerLevel level) {
        if (endEpisodeIfRequired(poltergeist, level)) {
            return;
        }
        PoltergeistState state = poltergeist.poltergeistState();
        final LivingEntity target = resolveTarget(poltergeist, level).orElseThrow();
        poltergeist.getNavigation().stop();
        poltergeist.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (state.episode().lifts() < PoltergeistRules.MAX_LIFTS
            && PoltergeistRules.liftAllowed(poltergeist.distanceToSqr(target))) {
            target.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION,
                PoltergeistRules.LEVITATION_TICKS,
                PoltergeistRules.LEVITATION_AMPLIFIER
            ));
            poltergeist.poltergeistCounters().lifts++;
            state = state.withEpisode(state.episode().withLifts(state.episode().lifts() + 1));
            poltergeist.setPoltergeistState(state);
        }
        if (state.episode().phaseRemainingTicks() > 0) {
            return;
        }
        // The prop is revalidated here and never rescanned: a prop that was picked up or removed
        // finishes the episode lift-only instead of opening a second search.
        if (resolveProp(poltergeist, level).isPresent()) {
            poltergeist.setPoltergeistState(state
                .withEpisode(state.episode().withPhaseRemaining(PoltergeistRules.THROW_TICKS))
                .withPhase(Phase.THROW));
            return;
        }
        endEpisode(poltergeist);
    }

    private static void tickThrow(final PoltergeistEntity poltergeist, final ServerLevel level) {
        if (endEpisodeIfRequired(poltergeist, level)) {
            return;
        }
        PoltergeistState state = poltergeist.poltergeistState();
        final LivingEntity target = resolveTarget(poltergeist, level).orElseThrow();
        final Optional<ItemEntity> prop = resolveProp(poltergeist, level);
        if (prop.isEmpty()) {
            endEpisode(poltergeist);
            return;
        }
        final ItemEntity thrown = prop.orElseThrow();
        poltergeist.getNavigation().stop();
        poltergeist.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (state.episode().velocityWrites() < PoltergeistRules.MAX_VELOCITY_WRITES) {
            final Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(thrown.position());
            final Vec3 direction = toTarget.lengthSqr() < 1.0E-4D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : toTarget.normalize();
            // Velocity only. The stack, its count, its age, its pickup delay, its owner and its
            // thrower are never written, so the prop stays exactly the item the player dropped.
            thrown.setDeltaMovement(direction
                .scale(PoltergeistRules.THROW_HORIZONTAL_SPEED)
                .add(0.0D, PoltergeistRules.THROW_VERTICAL_SPEED, 0.0D));
            thrown.hurtMarked = true;
            poltergeist.poltergeistCounters().velocityWrites++;
            state = state.withEpisode(
                state.episode().withVelocityWrites(state.episode().velocityWrites() + 1)
            );
            poltergeist.setPoltergeistState(state);
            level.playSound(null, poltergeist.getX(), poltergeist.getY(), poltergeist.getZ(),
                SoundEvents.WIND_CHARGE_THROW, poltergeist.getSoundSource(), 0.6F, 0.8F);
        }
        if (PoltergeistRules.throwHitAllowed(
            state.episode().hits(), thrown.distanceToSqr(target)
        )) {
            // Separately attributed: the prop is the direct entity and the disturbance is only the
            // cause, so this hit is never confused with an ordinary Poltergeist melee attack.
            target.hurtServer(level,
                poltergeist.damageSources().thrown(thrown, poltergeist),
                PoltergeistRules.THROW_HIT_DAMAGE);
            poltergeist.poltergeistCounters().throwHits++;
            state = state.withEpisode(state.episode().withHits(state.episode().hits() + 1));
            poltergeist.setPoltergeistState(state);
        }
        if (poltergeist.poltergeistState().episode().phaseRemainingTicks() <= 0) {
            endEpisode(poltergeist);
        }
    }

    /**
     * The single episode exit. The lifted target receives one bounded Slow Falling recovery while it
     * is still valid, navigation stops, the transient target and prop claims are dropped and the
     * long cooldown is armed. Nothing else in the package arms that cooldown, so an episode that
     * skipped this branch would be observable as a Poltergeist that never rests.
     */
    private static void tickRecover(final PoltergeistEntity poltergeist, final ServerLevel level) {
        PoltergeistState state = poltergeist.poltergeistState();
        poltergeist.getNavigation().stop();
        poltergeist.poltergeistTransient().destination = null;
        if (state.episode().recoveries() < PoltergeistRules.MAX_RECOVERIES
            && state.episode().lifts() > 0) {
            resolveTarget(poltergeist, level).ifPresent(recovered -> recovered.addEffect(
                new MobEffectInstance(
                    MobEffects.SLOW_FALLING,
                    PoltergeistRules.SLOW_FALLING_TICKS,
                    PoltergeistRules.SLOW_FALLING_AMPLIFIER
                )
            ));
            poltergeist.poltergeistCounters().recoveries++;
            state = state.withEpisode(
                state.episode().withRecoveries(state.episode().recoveries() + 1)
            );
            poltergeist.setPoltergeistState(state);
        }
        if (state.episode().phaseRemainingTicks() > 0) {
            return;
        }
        final TransientState scratch = poltergeist.poltergeistTransient();
        scratch.targetId = null;
        scratch.propId = null;
        poltergeist.poltergeistCounters().episodesEnded++;
        poltergeist.setPoltergeistState(state.finishEpisode());
    }

    /**
     * Exact end policy for the four attack phases. It is deliberately never consulted from the
     * recovery, because the recovery is what every end transitions into: consulting it there would
     * re-enter the recovery forever and the cooldown would never be armed.
     */
    private static boolean endEpisodeIfRequired(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final PoltergeistState state = poltergeist.poltergeistState();
        final Optional<ServerPlayer> marked = resolveMarkedPlayer(poltergeist, level);
        final EpisodeEnd end = PoltergeistRules.episodeEnd(new PoltergeistRules.TargetObservation(
            poltergeist.poltergeistTransient().markedTarget().isPresent(),
            // A player who is still connected but walked into another dimension is genuinely
            // observable, so the dimension release is a reachable branch rather than a synonym for
            // a lost target: only the level-wide lookup would have collapsed the two.
            marked.map(player -> player.level() == level).orElse(true),
            marked.filter(ServerPlayer::isAlive)
                .filter(player -> player.level() == level)
                .isPresent(),
            state.episode().remainingTicks(),
            state.cadence().routeFailures(),
            state.episode().pathRequests()
        ));
        if (end == EpisodeEnd.NONE) {
            return false;
        }
        endEpisode(poltergeist);
        return true;
    }

    private static void endEpisode(final PoltergeistEntity poltergeist) {
        poltergeist.getNavigation().stop();
        poltergeist.poltergeistTransient().destination = null;
        poltergeist.setPoltergeistState(poltergeist.poltergeistState().enterRecovery());
    }

    /** The marked player wherever it currently is, including another dimension. */
    private static Optional<ServerPlayer> resolveMarkedPlayer(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        return poltergeist.poltergeistTransient().markedTarget()
            .map(id -> level.getServer().getPlayerList().getPlayer(id));
    }

    private static Optional<LivingEntity> resolveTarget(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        return resolveMarkedPlayer(poltergeist, level)
            .filter(ServerPlayer::isAlive)
            .filter(candidate -> candidate.level() == level)
            .map(LivingEntity.class::cast);
    }

    private static Optional<ItemEntity> resolveProp(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        return poltergeist.poltergeistTransient().markedProp()
            .map(level::getEntity)
            .filter(ItemEntity.class::isInstance)
            .map(ItemEntity.class::cast)
            .filter(Entity::isAlive)
            .filter(candidate -> !candidate.getItem().isEmpty());
    }

    // ---------------------------------------------------------------- bell rattling

    /**
     * At most one already-loaded nearby bell rings once per episode. The bell's block state is never
     * written: the preserved {@link BellBlock#attemptToRing} call only starts the block entity's own
     * ringing animation, exactly as the generic ambient routine that used to own this did.
     */
    private static void maybeRingBell(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final PoltergeistState state = poltergeist.poltergeistState();
        if (state.episode().bellRings() >= PoltergeistRules.MAX_BELL_RINGS
            || poltergeist.poltergeistTransient().bellScanCooldownTicks > 0) {
            return;
        }
        // Armed before the scan: a scan that finds no bell costs one attempt per cadence instead of
        // one attempt per tick for the whole rattle window.
        poltergeist.poltergeistTransient().bellScanCooldownTicks =
            PoltergeistRules.BELL_SCAN_INTERVAL_TICKS;
        final Optional<BlockPos> bell = scanForBell(poltergeist, level);
        if (bell.isEmpty()) {
            poltergeist.poltergeistCounters().emptyBellScans++;
            return;
        }
        final BlockPos position = bell.orElseThrow();
        final double distanceSquared = poltergeist.distanceToSqr(Vec3.atCenterOf(position));
        if (!PoltergeistRules.bellRingAllowed(state.episode().bellRings(), distanceSquared)) {
            poltergeist.poltergeistCounters().emptyBellScans++;
            return;
        }
        final BlockState blockState = level.getBlockState(position);
        if (!(blockState.getBlock() instanceof BellBlock ringable)) {
            poltergeist.poltergeistCounters().emptyBellScans++;
            return;
        }
        ringable.attemptToRing(poltergeist, level, position, Direction.UP);
        level.sendParticles(ParticleTypes.SOUL, position.getX() + 0.5D, position.getY() + 1.0D,
            position.getZ() + 0.5D, PoltergeistRules.MAX_RATTLE_PARTICLES, 0.35D, 0.35D, 0.35D, 0.01D);
        poltergeist.poltergeistCounters().bellRings++;
        poltergeist.setPoltergeistState(state.withEpisode(
            state.episode().withBellRings(state.episode().bellRings() + 1)
        ));
    }

    /**
     * One charged bell scan. The whole envelope must already be loaded before a single read happens,
     * the read count can never exceed {@link PoltergeistRules#MAX_BELL_READS}, and the fixed near
     * anchor plus rotating far page make the complete envelope reachable across successive scans
     * rather than burning the whole budget on the innermost ring.
     */
    private static Optional<BlockPos> scanForBell(
        final PoltergeistEntity poltergeist,
        final ServerLevel level
    ) {
        final Counters counters = poltergeist.poltergeistCounters();
        counters.bellScans++;
        final BlockPos origin = poltergeist.blockPosition();
        if (!envelopeLoaded(level, origin, PoltergeistRules.BELL_SEARCH_HORIZONTAL,
            PoltergeistRules.BELL_SEARCH_VERTICAL)) {
            return Optional.empty();
        }
        final List<ScanOffset> offsets = PoltergeistRules.envelope(
            PoltergeistRules.BELL_SEARCH_HORIZONTAL, PoltergeistRules.BELL_SEARCH_VERTICAL
        );
        final TransientState scratch = poltergeist.poltergeistTransient();
        final int tail = offsets.size()
            - PoltergeistRules.anchorSize(offsets.size(), PoltergeistRules.MAX_BELL_READS);
        if (scratch.bellScanCursor == UNSEEDED_CURSOR) {
            scratch.bellScanCursor = tail <= 0
                ? 0
                : PoltergeistRules.stableOffset(poltergeist.getUUID(), tail);
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final ScanOffset offset : PoltergeistRules.scanWindow(
            offsets, PoltergeistRules.MAX_BELL_READS, scratch.bellScanCursor
        )) {
            final BlockPos candidate = origin.offset(offset.dx(), offset.dy(), offset.dz());
            if (!level.getWorldBorder().isWithinBounds(candidate)) {
                continue;
            }
            // Charged before the filter: a candidate that turns out not to be a bell spent this
            // read too, so the ceiling binds the real cost instead of only the accepted minority.
            counters.bellReads++;
            counters.blockReads++;
            if (!(level.getBlockState(candidate).getBlock() instanceof BellBlock)) {
                continue;
            }
            final double distanceSquared = poltergeist.distanceToSqr(Vec3.atCenterOf(candidate));
            if (best == null || distanceSquared < bestDistance
                || (distanceSquared == bestDistance && candidate.asLong() < best.asLong())) {
                best = candidate.immutable();
                bestDistance = distanceSquared;
            }
        }
        scratch.bellScanCursor = PoltergeistRules.advanceCursor(
            offsets.size(), PoltergeistRules.MAX_BELL_READS, scratch.bellScanCursor
        );
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- movement

    private static boolean searchAndRoute(
        final PoltergeistEntity poltergeist,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final double speed,
        final boolean chargeEpisodeQuota
    ) {
        final PoltergeistState state = poltergeist.poltergeistState();
        if (!PoltergeistRules.pathRequestAllowed(
            poltergeist.poltergeistTransient().pathCooldownTicks,
            state.cadence().routeRetryTicks(),
            chargeEpisodeQuota && state.episode().active() ? state.episode().pathRequests() : 0
        )) {
            return false;
        }
        final Optional<BlockPos> destination = findSafeDestination(
            poltergeist, level, center, horizontalRadius, verticalRadius, candidateBudget,
            awayFrom, avoidHazards
        );
        if (destination.isEmpty()) {
            recordUnroutableSearch(poltergeist);
            return false;
        }
        return requestRoute(poltergeist, destination.orElseThrow(), speed, chargeEpisodeQuota);
    }

    /**
     * A search that qualified no candidate costs the same real work as one that did, so it arms the
     * same path cadence and counts the same route failure. Without this a caller gated only by
     * {@code getNavigation().isDone()} would re-run the whole candidate sweep every tick for as long
     * as the surroundings stayed unusable.
     */
    private static void recordUnroutableSearch(final PoltergeistEntity poltergeist) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        scratch.pathCooldownTicks = PoltergeistRules.PATH_INTERVAL_TICKS;
        scratch.destination = null;
        final PoltergeistState state = poltergeist.poltergeistState();
        final int failures = PoltergeistRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new PoltergeistRules.RouteResult(false, false, false)
        );
        int retry = state.cadence().routeRetryTicks();
        if (PoltergeistRules.routeExhausted(failures)) {
            retry = PoltergeistRules.routeBackoffAfter(failures);
            poltergeist.getNavigation().stop();
        }
        poltergeist.setPoltergeistState(state.withCadence(new PoltergeistState.Cadence(
            state.cadence().cooldownTicks(), failures, retry
        )));
    }

    /**
     * Strict route request. The third consecutive failure stops navigation and starts the backoff;
     * the counter is persisted at its observable maximum so the next decision's
     * {@link PoltergeistRules#episodeEnd} can release on it, and only a release or a later success
     * resets it. Every accepted or rejected request also spends one unit of the episode path quota.
     */
    static boolean requestRoute(
        final PoltergeistEntity poltergeist,
        final BlockPos destination,
        final double speed,
        final boolean chargeEpisodeQuota
    ) {
        final TransientState scratch = poltergeist.poltergeistTransient();
        PoltergeistState state = poltergeist.poltergeistState();
        if (!PoltergeistRules.pathRequestAllowed(
            scratch.pathCooldownTicks,
            state.cadence().routeRetryTicks(),
            chargeEpisodeQuota && state.episode().active() ? state.episode().pathRequests() : 0
        )) {
            return false;
        }
        scratch.pathCooldownTicks = PoltergeistRules.PATH_INTERVAL_TICKS;
        poltergeist.poltergeistCounters().navigationRequests++;
        final Path path = poltergeist.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && poltergeist.getNavigation().moveTo(path, speed);
        final int failures = PoltergeistRules.routeFailuresAfter(
            state.cadence().routeFailures(),
            new PoltergeistRules.RouteResult(path != null, reachable, accepted)
        );
        int retry = state.cadence().routeRetryTicks();
        if (PoltergeistRules.routeExhausted(failures)) {
            retry = PoltergeistRules.routeBackoffAfter(failures);
            poltergeist.getNavigation().stop();
            scratch.destination = null;
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        state = state.withCadence(new PoltergeistState.Cadence(
            state.cadence().cooldownTicks(), failures, retry
        ));
        if (chargeEpisodeQuota && state.episode().active()) {
            state = state.withEpisode(
                state.episode().withPathRequests(state.episode().pathRequests() + 1)
            );
        }
        poltergeist.setPoltergeistState(state);
        return accepted;
    }

    /**
     * Deterministic bounded safe-destination search sharing the 24-candidate and 256-read budget.
     * The origin is always excluded, the complete footprint must already be loaded and inside the
     * world border, and preference is the species lexicographic order.
     */
    static Optional<BlockPos> findSafeDestination(
        final PoltergeistEntity poltergeist,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = poltergeist.poltergeistCounters();
        counters.safeSearches++;
        final BlockPos origin = poltergeist.blockPosition();
        final Comparator<PoltergeistRules.SafeCandidate> preference =
            PoltergeistRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        PoltergeistRules.SafeCandidate bestFacts = null;
        for (final PoltergeistRules.SafeSearchOffset offset : PoltergeistRules.safeSearchOffsets(
            poltergeist.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + READS_PER_SAFE_CANDIDATE > PoltergeistRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            // Charged before the filter: a rejected candidate spent these reads too.
            reads += READS_PER_SAFE_CANDIDATE;
            counters.blockReads += READS_PER_SAFE_CANDIDATE;
            final Optional<SafeQualification> qualified =
                qualifySafeCandidate(poltergeist, level, candidate, avoidHazards);
            if (qualified.isEmpty()) {
                continue;
            }
            final PoltergeistRules.SafeCandidate facts = new PoltergeistRules.SafeCandidate(
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

    // ---------------------------------------------------------------- world observation

    /** What one qualified candidate turned out to be, so no caller has to read the position twice. */
    record SafeQualification(boolean hazardFree) {
    }

    static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    static boolean footprintLoaded(final ServerLevel level, final AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ));
    }

    /**
     * Bounded local hazard observation over the 3 x 3 x 3 neighbourhood. Reads stop at the declared
     * ceiling and an unloaded footprint is never forced: it simply reports no hazard.
     */
    static boolean observeHazard(final PoltergeistEntity poltergeist, final ServerLevel level) {
        if (poltergeist.isOnFire() || poltergeist.isInLava()) {
            return true;
        }
        if (poltergeist.isUnderWater()
            && poltergeist.getAirSupply() < poltergeist.getMaxAirSupply()) {
            return true;
        }
        if (!footprintLoaded(level, poltergeist.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos center = poltergeist.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= PoltergeistRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    if (isHazardBlock(level.getBlockState(center.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Shared candidate qualification for a bounded safe destination. Only positions whose entire
     * entity footprint is already loaded, inside the world border, collision free, and not lava
     * qualify. Nothing here decides preference: the species rules do.
     */
    static Optional<SafeQualification> qualifySafeCandidate(
        final PoltergeistEntity poltergeist,
        final ServerLevel level,
        final BlockPos candidate,
        final boolean avoidHazards
    ) {
        final AABB box = poltergeist.getType().getDimensions()
            .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
        if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
            return Optional.empty();
        }
        final BlockState blockState = level.getBlockState(candidate);
        final var fluidState = level.getFluidState(candidate);
        final boolean hazardous = isHazardBlock(blockState) || !fluidState.isEmpty();
        if (avoidHazards && hazardous) {
            return Optional.empty();
        }
        if (blockState.is(Blocks.LAVA) || fluidState.is(FluidTags.LAVA)) {
            return Optional.empty();
        }
        return level.noCollision(poltergeist, box)
            ? Optional.of(new SafeQualification(!hazardous))
            : Optional.empty();
    }

    /**
     * Bounded envelope check. The complete 2r+1 by 2v+1 by 2r+1 box must already be loaded before
     * any read happens, so a scan never forces a chunk and never crosses into an unloaded neighbour.
     */
    static boolean envelopeLoaded(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius
    ) {
        return level.hasChunkAt(center.offset(-horizontalRadius, 0, -horizontalRadius))
            && level.hasChunkAt(center.offset(horizontalRadius, 0, -horizontalRadius))
            && level.hasChunkAt(center.offset(-horizontalRadius, 0, horizontalRadius))
            && level.hasChunkAt(center.offset(horizontalRadius, 0, horizontalRadius))
            && verticalRadius >= 0;
    }
}
