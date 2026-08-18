package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.BansheeRules.AttackerObservation;
import com.kadamitas.warlockery.entity.BansheeRules.Candidate;
import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import com.kadamitas.warlockery.entity.BansheeRules.ReleaseReason;
import com.kadamitas.warlockery.entity.BansheeRules.StandoffAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Banshee behavior controller. It owns MOVE completely; the entity's look
 * goals own LOOK only. Every scan, read, line-of-sight ray, path request, pulse, and teleport is
 * counted against the declared hard budgets, and no method here enumerates a dimension, forces a
 * chunk, edits a block or inventory, or writes another entity's persistent state. The single
 * approved outward mutations are the two temporary taboo effects on one legal accepted attacker
 * and one validated self-teleport per 120-loaded-tick response window.
 */
public final class BansheeRuntime {
    private static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final double ROUTE_SPEED = 1.0D;
    private static final double ESCAPE_SPEED = 1.2D;

    private BansheeRuntime() {
    }

    /** Structural work counters proving the exact caps. Pass-local, never persisted. */
    public static final class Counters {
        long candidateVisits;
        long lineOfSightChecks;
        long blockReads;
        long navigationRequests;
        long safeSearches;
        long safeCandidateVisits;
        long warningPulsesEmitted;
        long warningPulsesSuppressed;
        long lamentPulsesEmitted;
        long tabooResponses;
        long teleportAttempts;
        long hazardInterruptions;
        long releases;
        long ambientPulses;
        long discoveryScans;

        public long candidateVisits() { return candidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long blockReads() { return blockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long safeSearches() { return safeSearches; }
        public long safeCandidateVisits() { return safeCandidateVisits; }
        public long warningPulsesEmitted() { return warningPulsesEmitted; }
        public long warningPulsesSuppressed() { return warningPulsesSuppressed; }
        public long lamentPulsesEmitted() { return lamentPulsesEmitted; }
        public long tabooResponses() { return tabooResponses; }
        public long teleportAttempts() { return teleportAttempts; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long releases() { return releases; }
        public long ambientPulses() { return ambientPulses; }
        public long discoveryScans() { return discoveryScans; }
    }

    /**
     * Execution scratch that is rebuilt after every load. Nothing here is meaning: losing it can
     * delay work by one cadence but can never replay a pulse, effect, teleport, or path.
     */
    public static final class TransientState {
        boolean reconciled;
        int holdTicks;
        int pathCooldownTicks;
        int hazardCooldownTicks;
        int discoveryCooldownTicks;
        int idleCooldownTicks;
        int ambientCooldownTicks;
        int sightCooldownTicks;
        boolean hazardActive;
        BlockPos destination;
        long warningAdvancedAtGameTime = Long.MIN_VALUE;
        long lamentAdvancedAtGameTime = Long.MIN_VALUE;

        public void resetForLoad() {
            reconciled = false;
            holdTicks = 0;
            pathCooldownTicks = 0;
            hazardCooldownTicks = 0;
            discoveryCooldownTicks = 0;
            idleCooldownTicks = 0;
            ambientCooldownTicks = 0;
            sightCooldownTicks = 0;
            hazardActive = false;
            destination = null;
            warningAdvancedAtGameTime = Long.MIN_VALUE;
            lamentAdvancedAtGameTime = Long.MIN_VALUE;
        }

        public int holdTicks() {
            return holdTicks;
        }

        public boolean hazardActive() {
            return hazardActive;
        }
    }

    public static void tick(final BansheeEntity banshee, final ServerLevel level) {
        reconcileOnLoad(banshee, level);
        advanceLoadedTimers(banshee);
        if (tickHazard(banshee, level)) {
            banshee.syncPresentation(banshee.bansheeState().mode());
            return;
        }
        switch (banshee.bansheeState().mode()) {
            case RECOIL -> tickRecoil(banshee, level);
            case LAMENT -> tickLament(banshee, level);
            case APPROACH, WARNING -> tickWarningEpisode(banshee, level);
            case RECOVERY, VIGIL -> tickVigil(banshee, level);
        }
        banshee.syncPresentation(banshee.bansheeState().mode());
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final BansheeEntity banshee, final ServerLevel level) {
        final TransientState scratch = banshee.bansheeTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.holdTicks = 0;
        scratch.ambientCooldownTicks = BansheeRules.stableOffset(
            banshee.getUUID(), BansheeRules.AMBIENT_INTERVAL_TICKS
        );
        BansheeState state = banshee.bansheeState();
        final String dimension = dimensionOf(level);
        final boolean anchorInvalid = !state.anchor().present()
            || state.anchor().dimension().map(stored -> !stored.equals(dimension)).orElse(true)
            || state.anchor().position()
                .map(anchor -> !level.getWorldBorder().isWithinBounds(anchor)).orElse(true);
        if (anchorInvalid) {
            state = state.withAnchor(new BansheeState.Anchor(
                Optional.of(banshee.blockPosition()), Optional.of(dimension)
            ));
        }
        banshee.setBansheeState(state);
    }

    private static void advanceLoadedTimers(final BansheeEntity banshee) {
        final TransientState scratch = banshee.bansheeTransient();
        scratch.pathCooldownTicks = Math.max(0, scratch.pathCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.discoveryCooldownTicks = Math.max(0, scratch.discoveryCooldownTicks - 1);
        scratch.idleCooldownTicks = Math.max(0, scratch.idleCooldownTicks - 1);
        scratch.ambientCooldownTicks = Math.max(0, scratch.ambientCooldownTicks - 1);
        scratch.sightCooldownTicks = Math.max(0, scratch.sightCooldownTicks - 1);

        BansheeState state = banshee.bansheeState();
        final BansheeState.Cadence cadence = state.cadence();
        state = state.withCadence(new BansheeState.Cadence(
            BansheeRules.decrementLoaded(cadence.tabooCooldownTicks()),
            cadence.routeFailures(),
            BansheeRules.decrementLoaded(cadence.routeRetryTicks()),
            BansheeRules.decrementLoaded(cadence.reacquireTicks()),
            cadence.anchorUnavailableTicks()
        ));
        if (state.subject().present()
            && (state.mode() == Mode.APPROACH || state.mode() == Mode.WARNING)) {
            final BansheeState.Subject subject = state.subject();
            state = state.withSubject(new BansheeState.Subject(
                subject.id(), subject.dimension(), subject.lastSeen(),
                subject.missingTicks(), subject.lostSightTicks(), subject.recoveryTicks(),
                BansheeRules.decrementLoaded(subject.episodeRemainingTicks()),
                BansheeRules.decrementLoaded(subject.pulseRemainingTicks()),
                subject.pulsesEmitted()
            ));
        }
        if (state.death().present() && state.mode() == Mode.LAMENT) {
            final BansheeState.Death death = state.death();
            final int remaining = death.remainingTicks() - 1;
            if (remaining <= 0) {
                banshee.bansheeCounters().releases++;
                state = state.withDeath(BansheeState.Death.none())
                    .withMode(Mode.RECOVERY)
                    .withCadence(new BansheeState.Cadence(
                        state.cadence().tabooCooldownTicks(), 0,
                        state.cadence().routeRetryTicks(),
                        BansheeRules.REACQUIRE_COOLDOWN_TICKS,
                        state.cadence().anchorUnavailableTicks()
                    ));
            } else {
                state = state.withDeath(new BansheeState.Death(
                    death.position(), death.dimension(), remaining,
                    BansheeRules.decrementLoaded(death.pulseRemainingTicks()),
                    death.pulsesEmitted()
                ));
            }
        }
        if (state.attacker().present() && state.mode() == Mode.RECOIL) {
            final BansheeState.Attacker attacker = state.attacker();
            state = state.withAttacker(attacker.remainingTicks() <= 1
                ? BansheeState.Attacker.none()
                : new BansheeState.Attacker(
                    attacker.id(), attacker.dimension(),
                    attacker.remainingTicks() - 1, attacker.teleportAttempted()
                ));
            if (attacker.remainingTicks() <= 1) {
                state = state.withMode(afterRecoilMode(state));
            }
        }
        banshee.setBansheeState(state);
    }

    private static Mode afterRecoilMode(final BansheeState state) {
        if (state.death().present()) {
            return Mode.LAMENT;
        }
        return state.subject().present() ? Mode.APPROACH : Mode.VIGIL;
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final BansheeEntity banshee, final ServerLevel level) {
        final TransientState scratch = banshee.bansheeTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && continueEscape(banshee);
        }
        scratch.hazardCooldownTicks = BansheeRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = observeHazard(banshee, level);
        if (!BansheeRules.hazardPreempts(banshee.bansheeState().mode(), scratch.hazardActive)) {
            return false;
        }
        banshee.bansheeCounters().hazardInterruptions++;
        scratch.holdTicks = 0;
        searchAndRoute(banshee, level, banshee.blockPosition(), 6, 3,
            BansheeRules.MAX_SAFE_CANDIDATES, Optional.empty(), true, ESCAPE_SPEED);
        return true;
    }

    private static boolean continueEscape(final BansheeEntity banshee) {
        return !banshee.getNavigation().isDone();
    }

    private static boolean observeHazard(final BansheeEntity banshee, final ServerLevel level) {
        if (banshee.isOnFire() || banshee.isInLava()) {
            return true;
        }
        if (banshee.isUnderWater() && banshee.getAirSupply() < banshee.getMaxAirSupply()) {
            return true;
        }
        final BlockPos center = banshee.blockPosition();
        if (!footprintLoaded(level, banshee.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= BansheeRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    banshee.bansheeCounters().blockReads++;
                    final BlockState blockState = level.getBlockState(center.offset(dx, dy, dz));
                    if (isHazardBlock(blockState)) {
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

    // ---------------------------------------------------------------- recoil

    private static void tickRecoil(final BansheeEntity banshee, final ServerLevel level) {
        final BansheeState state = banshee.bansheeState();
        final BansheeState.Attacker attacker = state.attacker();
        if (!attacker.present()) {
            banshee.setBansheeState(state.withMode(afterRecoilMode(state)));
            return;
        }
        final Optional<LivingEntity> resolved = resolveLiving(level, attacker.id().orElseThrow());
        final boolean legal = resolved
            .map(living -> BansheeRules.attackerLegal(observeAttacker(banshee, level, living)))
            .orElse(false);
        if (!legal) {
            releaseRecoil(banshee);
            return;
        }
        final LivingEntity threat = resolved.orElseThrow();
        if (banshee.distanceToSqr(threat) >= (double) BansheeRules.RELEASE_RANGE * BansheeRules.RELEASE_RANGE) {
            releaseRecoil(banshee);
            return;
        }
        if (banshee.getNavigation().isDone()) {
            searchAndRoute(banshee, level, banshee.blockPosition(),
                BansheeRules.RECOIL_SEARCH_HORIZONTAL, BansheeRules.RECOIL_SEARCH_VERTICAL,
                BansheeRules.MAX_SAFE_CANDIDATES, Optional.of(threat.position()), false,
                ESCAPE_SPEED);
        }
    }

    private static void releaseRecoil(final BansheeEntity banshee) {
        final BansheeState state = banshee.bansheeState();
        banshee.bansheeCounters().releases++;
        banshee.getNavigation().stop();
        banshee.setBansheeState(
            state.withAttacker(BansheeState.Attacker.none()).withMode(afterRecoilMode(state))
        );
    }

    // ---------------------------------------------------------------- warning episode

    private static void tickWarningEpisode(final BansheeEntity banshee, final ServerLevel level) {
        final TransientState scratch = banshee.bansheeTransient();
        BansheeState state = banshee.bansheeState();
        final BansheeState.Subject subject = state.subject();
        if (!subject.present()) {
            banshee.setBansheeState(state.withMode(Mode.VIGIL));
            return;
        }
        final Optional<ServerPlayer> resolved = resolvePlayer(level, subject.id().orElseThrow());
        final boolean sameDimension = subject.dimension()
            .map(dimensionOf(level)::equals)
            .orElse(false);
        BansheeState.Subject observedSubject = subject;
        float healthFraction = 1.0F;
        double distanceSquared = Double.MAX_VALUE;
        boolean visible = false;
        boolean eligibleGameMode = false;
        boolean invulnerable = false;
        boolean alive = false;
        if (resolved.isPresent() && sameDimension) {
            final ServerPlayer player = resolved.orElseThrow();
            alive = player.isAlive();
            eligibleGameMode = !player.isCreative() && !player.isSpectator();
            invulnerable = player.isInvulnerable();
            healthFraction = BansheeRules.healthFraction(player.getHealth(), player.getMaxHealth());
            distanceSquared = banshee.distanceToSqr(player);
            if (scratch.sightCooldownTicks <= 0) {
                scratch.sightCooldownTicks = BansheeRules.SUBJECT_SIGHT_INTERVAL_TICKS;
                banshee.bansheeCounters().lineOfSightChecks++;
                visible = banshee.getSensing().hasLineOfSight(player);
            } else {
                visible = subject.lostSightTicks() == 0;
            }
            observedSubject = new BansheeState.Subject(
                subject.id(), subject.dimension(), Optional.of(player.blockPosition()),
                0,
                visible ? 0 : subject.lostSightTicks() + 1,
                BansheeRules.recovered(healthFraction) ? subject.recoveryTicks() + 1 : 0,
                subject.episodeRemainingTicks(), subject.pulseRemainingTicks(),
                subject.pulsesEmitted()
            );
            if (BansheeRules.deathReportable(true, true, alive, state.death().present()) ) {
                banshee.bansheeCounters().releases++;
                banshee.getNavigation().stop();
                scratch.holdTicks = 0;
                banshee.setBansheeState(state
                    .withSubject(BansheeState.Subject.none())
                    .withDeath(BansheeState.Death.observed(player.blockPosition(), dimensionOf(level)))
                    .withMode(Mode.LAMENT));
                return;
            }
        } else {
            observedSubject = new BansheeState.Subject(
                subject.id(), subject.dimension(), subject.lastSeen(),
                subject.missingTicks() + 1, subject.lostSightTicks(), subject.recoveryTicks(),
                subject.episodeRemainingTicks(), subject.pulseRemainingTicks(),
                subject.pulsesEmitted()
            );
        }
        state = state.withSubject(observedSubject);
        final ReleaseReason release = BansheeRules.releaseReason(new BansheeRules.SubjectObservation(
            resolved.isPresent() && sameDimension, sameDimension, alive, eligibleGameMode,
            invulnerable, visible, healthFraction, distanceSquared,
            observedSubject.missingTicks(), observedSubject.lostSightTicks(),
            observedSubject.recoveryTicks(), observedSubject.episodeRemainingTicks(),
            state.cadence().routeFailures()
        ));
        if (release != ReleaseReason.NONE) {
            releaseSubject(banshee, state);
            return;
        }
        if (resolved.isEmpty() || !sameDimension) {
            banshee.setBansheeState(state);
            return;
        }
        final ServerPlayer player = resolved.orElseThrow();
        final StandoffAction action = BansheeRules.warningStandoff(distanceSquared);
        final boolean inBand = action == StandoffAction.HOLD;
        scratch.holdTicks = BansheeRules.advanceHold(scratch.holdTicks, inBand, visible);
        state = state.withMode(inBand ? Mode.WARNING : Mode.APPROACH);
        if (!inBand) {
            moveTowardStandoff(banshee, level, player, action);
        }
        if (BansheeRules.pulseDue(
            state.subject().pulseRemainingTicks(),
            state.subject().pulsesEmitted(),
            BansheeRules.MAX_WARNING_PULSES,
            BansheeRules.holdArmed(scratch.holdTicks)
        ) && visible && inBand) {
            state = emitWarningPulse(banshee, level, state);
        }
        banshee.setBansheeState(state);
    }

    private static void moveTowardStandoff(
        final BansheeEntity banshee,
        final ServerLevel level,
        final ServerPlayer player,
        final StandoffAction action
    ) {
        final Vec3 toPlayer = player.position().subtract(banshee.position());
        final Vec3 direction = toPlayer.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D)
            : toPlayer.normalize();
        final double target = (BansheeRules.STANDOFF_MIN + BansheeRules.STANDOFF_MAX) / 2.0D;
        final Vec3 goal = action == StandoffAction.APPROACH
            ? player.position().subtract(direction.scale(target))
            : player.position().subtract(direction.scale(target + 2.0D));
        final BlockPos goalPos = BlockPos.containing(goal.x, goal.y + 1.0D, goal.z);
        searchAndRoute(banshee, level, goalPos, 2, 2, BansheeRules.MAX_SAFE_CANDIDATES,
            Optional.empty(), false, ROUTE_SPEED);
    }

    private static BansheeState emitWarningPulse(
        final BansheeEntity banshee,
        final ServerLevel level,
        final BansheeState input
    ) {
        BansheeState state = input;
        final BansheeState.Subject subject = state.subject();
        if (BansheeRules.warningPulsesRemaining(subject.pulsesEmitted()) <= 0) {
            return state;
        }
        state = state.withSubject(new BansheeState.Subject(
            subject.id(), subject.dimension(), subject.lastSeen(),
            subject.missingTicks(), subject.lostSightTicks(), subject.recoveryTicks(),
            subject.episodeRemainingTicks(),
            BansheeRules.WARNING_PULSE_INTERVAL_TICKS,
            BansheeRules.advanceEmissionCount(subject.pulsesEmitted(), true)
        ));
        banshee.bansheeTransient().warningAdvancedAtGameTime = level.getGameTime();
        if (mayEmitLocally(banshee, level, true)) {
            banshee.bansheeCounters().warningPulsesEmitted++;
            banshee.syncPulse();
            level.playSound(null, banshee.getX(), banshee.getY(), banshee.getZ(),
                SoundEvents.VEX_AMBIENT, banshee.getSoundSource(), 1.0F, 0.7F);
            level.sendParticles(ParticleTypes.SOUL, banshee.getX(), banshee.getY() + 1.0D,
                banshee.getZ(), BansheeRules.MAX_WARNING_PARTICLES, 0.4D, 0.6D, 0.4D, 0.01D);
        } else {
            banshee.bansheeCounters().warningPulsesSuppressed++;
        }
        return state;
    }

    private static void releaseSubject(final BansheeEntity banshee, final BansheeState state) {
        banshee.bansheeCounters().releases++;
        banshee.getNavigation().stop();
        banshee.bansheeTransient().holdTicks = 0;
        banshee.bansheeTransient().destination = null;
        banshee.setBansheeState(state.releaseSubject());
    }

    // ---------------------------------------------------------------- lament

    private static void tickLament(final BansheeEntity banshee, final ServerLevel level) {
        BansheeState state = banshee.bansheeState();
        final BansheeState.Death death = state.death();
        if (!death.present()) {
            banshee.setBansheeState(state.withMode(Mode.RECOVERY));
            return;
        }
        final boolean sameDimension = death.dimension()
            .map(dimensionOf(level)::equals)
            .orElse(false);
        final BlockPos site = death.position().orElseThrow();
        if (!sameDimension || !level.hasChunkAt(site)
            || BansheeRules.routeExhausted(state.cadence().routeFailures())) {
            banshee.bansheeCounters().releases++;
            banshee.getNavigation().stop();
            banshee.setBansheeState(state
                .withDeath(BansheeState.Death.none())
                .withMode(Mode.RECOVERY)
                .withCadence(new BansheeState.Cadence(
                    state.cadence().tabooCooldownTicks(), 0,
                    state.cadence().routeRetryTicks(),
                    BansheeRules.REACQUIRE_COOLDOWN_TICKS,
                    state.cadence().anchorUnavailableTicks()
                )));
            return;
        }
        final double distanceSquared = banshee.distanceToSqr(Vec3.atCenterOf(site));
        final StandoffAction action = BansheeRules.lamentStandoff(distanceSquared);
        if (action != StandoffAction.HOLD && banshee.getNavigation().isDone()) {
            searchAndRoute(banshee, level, site.above(2), 4, 2, BansheeRules.MAX_SAFE_CANDIDATES,
                Optional.empty(), false, ROUTE_SPEED);
        }
        if (BansheeRules.lamentPulsesRemaining(death.pulsesEmitted()) > 0 && BansheeRules.pulseDue(
            death.pulseRemainingTicks(), death.pulsesEmitted(), BansheeRules.MAX_LAMENT_PULSES, true
        )) {
            state = state.withDeath(new BansheeState.Death(
                death.position(), death.dimension(), death.remainingTicks(),
                BansheeRules.LAMENT_PULSE_INTERVAL_TICKS,
                BansheeRules.advanceEmissionCount(death.pulsesEmitted(), true)
            ));
            banshee.bansheeTransient().lamentAdvancedAtGameTime = level.getGameTime();
            if (mayEmitLocally(banshee, level, false)) {
                banshee.bansheeCounters().lamentPulsesEmitted++;
                banshee.syncPulse();
                level.playSound(null, banshee.getX(), banshee.getY(), banshee.getZ(),
                    SoundEvents.VEX_DEATH, banshee.getSoundSource(), 1.0F, 0.6F);
                level.sendParticles(ParticleTypes.SOUL, banshee.getX(), banshee.getY() + 1.0D,
                    banshee.getZ(), BansheeRules.MAX_LAMENT_PARTICLES, 0.4D, 0.6D, 0.4D, 0.01D);
            }
            banshee.setBansheeState(state);
        }
    }

    // ---------------------------------------------------------------- discovery and vigil

    private static void tickVigil(final BansheeEntity banshee, final ServerLevel level) {
        BansheeState state = banshee.bansheeState();
        if (state.mode() == Mode.RECOVERY && state.cadence().reacquireTicks() <= 0) {
            state = state.withMode(Mode.VIGIL);
            banshee.setBansheeState(state);
        }
        if (state.mode() == Mode.VIGIL
            && !state.subject().present() && !state.death().present()
            && state.cadence().reacquireTicks() <= 0
            && banshee.bansheeTransient().discoveryCooldownTicks <= 0) {
            banshee.bansheeTransient().discoveryCooldownTicks = BansheeRules.DISCOVERY_INTERVAL_TICKS;
            discoverSubject(banshee, level);
            state = banshee.bansheeState();
        }
        if (state.mode() == Mode.VIGIL || state.mode() == Mode.RECOVERY) {
            tickAnchor(banshee, level);
            tickAmbientFeedback(banshee, level);
        }
    }

    private static void discoverSubject(final BansheeEntity banshee, final ServerLevel level) {
        final Counters counters = banshee.bansheeCounters();
        counters.discoveryScans++;
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(banshee);
        final List<Candidate> inspected = new ArrayList<>();
        int visited = 0;
        int losChecks = 0;
        final List<ServerPlayer> players = new ArrayList<>();
        ownerId.map(level::getPlayerByUUID)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .ifPresent(players::add);
        for (final ServerPlayer player : level.players()) {
            if (players.size() >= BansheeRules.MAX_CANDIDATES_VISITED) {
                break;
            }
            // Range first: a player beyond the acquire range can never be selected, so admitting
            // it here would spend the bounded candidate budget on join order instead of distance.
            if (banshee.distanceToSqr(player) > BansheeRules.ACQUIRE_RANGE_SQUARED) {
                continue;
            }
            if (!players.contains(player)) {
                players.add(player);
            }
        }
        for (final ServerPlayer player : players) {
            if (visited >= BansheeRules.MAX_CANDIDATES_VISITED) {
                break;
            }
            visited++;
            counters.candidateVisits++;
            final float healthFraction =
                BansheeRules.healthFraction(player.getHealth(), player.getMaxHealth());
            final double distanceSquared = banshee.distanceToSqr(player);
            final boolean preliminaryEligible = player.isAlive()
                && !player.isCreative() && !player.isSpectator() && !player.isInvulnerable()
                && player.level() == level
                && distanceSquared <= BansheeRules.ACQUIRE_RANGE_SQUARED
                && BansheeRules.atRisk(healthFraction);
            if (!preliminaryEligible || inspected.size() >= BansheeRules.MAX_RETAINED_CANDIDATES) {
                continue;
            }
            if (losChecks >= BansheeRules.MAX_LINE_OF_SIGHT_CHECKS) {
                break;
            }
            losChecks++;
            counters.lineOfSightChecks++;
            final boolean visible = banshee.getSensing().hasLineOfSight(player);
            inspected.add(new Candidate(
                player.getUUID(),
                ownerId.map(player.getUUID()::equals).orElse(false),
                healthFraction,
                distanceSquared,
                visible
            ));
        }
        BansheeRules.select(inspected).ifPresent(selected -> {
            final BansheeState current = banshee.bansheeState();
            banshee.setBansheeState(current
                .withSubject(BansheeState.Subject.acquired(selected.id(), dimensionOf(level)))
                .withMode(Mode.APPROACH)
                .withCadence(new BansheeState.Cadence(
                    current.cadence().tabooCooldownTicks(), 0,
                    current.cadence().routeRetryTicks(),
                    current.cadence().reacquireTicks(),
                    current.cadence().anchorUnavailableTicks()
                )));
        });
    }

    private static void tickAnchor(final BansheeEntity banshee, final ServerLevel level) {
        BansheeState state = banshee.bansheeState();
        final BansheeState.Anchor anchor = state.anchor();
        if (!anchor.present()) {
            return;
        }
        final BlockPos anchorPos = anchor.position().orElseThrow();
        if (!level.hasChunkAt(anchorPos)) {
            final int unavailable = state.cadence().anchorUnavailableTicks() + 1;
            if (BansheeRules.reanchorRequired(false, unavailable)) {
                banshee.setBansheeState(state
                    .withAnchor(new BansheeState.Anchor(
                        Optional.of(banshee.blockPosition()), Optional.of(dimensionOf(level))
                    ))
                    .withCadence(withAnchorUnavailable(state.cadence(), 0)));
            } else {
                banshee.setBansheeState(
                    state.withCadence(withAnchorUnavailable(state.cadence(), unavailable))
                );
            }
            return;
        }
        if (state.cadence().anchorUnavailableTicks() != 0) {
            state = state.withCadence(withAnchorUnavailable(state.cadence(), 0));
            banshee.setBansheeState(state);
        }
        final BlockPos current = banshee.blockPosition();
        final int horizontal = Math.max(
            Math.abs(current.getX() - anchorPos.getX()),
            Math.abs(current.getZ() - anchorPos.getZ())
        );
        final int vertical = Math.abs(current.getY() - anchorPos.getY());
        if (BansheeRules.anchorReturnRequired(horizontal, vertical)) {
            if (banshee.getNavigation().isDone()) {
                searchAndRoute(banshee, level, anchorPos.above(2), 2, 2,
                    BansheeRules.MAX_SAFE_CANDIDATES, Optional.empty(), false, ROUTE_SPEED);
            }
            return;
        }
        if (BansheeRules.withinAnchorComfort(horizontal, vertical)
            && banshee.bansheeTransient().idleCooldownTicks <= 0
            && banshee.getNavigation().isDone()) {
            banshee.bansheeTransient().idleCooldownTicks = BansheeRules.IDLE_DESTINATION_INTERVAL_TICKS
                + BansheeRules.stableOffset(banshee.getUUID(), 20);
            searchAndRoute(banshee, level, anchorPos.above(2),
                BansheeRules.IDLE_RADIUS_HORIZONTAL, BansheeRules.IDLE_RADIUS_VERTICAL,
                BansheeRules.MAX_IDLE_CANDIDATES, Optional.empty(), false, ROUTE_SPEED);
        }
    }

    private static BansheeState.Cadence withAnchorUnavailable(
        final BansheeState.Cadence cadence,
        final int unavailableTicks
    ) {
        return new BansheeState.Cadence(
            cadence.tabooCooldownTicks(), cadence.routeFailures(), cadence.routeRetryTicks(),
            cadence.reacquireTicks(), unavailableTicks
        );
    }

    private static void tickAmbientFeedback(final BansheeEntity banshee, final ServerLevel level) {
        final TransientState scratch = banshee.bansheeTransient();
        if (scratch.ambientCooldownTicks > 0) {
            return;
        }
        scratch.ambientCooldownTicks = BansheeRules.AMBIENT_INTERVAL_TICKS;
        banshee.bansheeCounters().ambientPulses++;
        level.playSound(null, banshee.getX(), banshee.getY(), banshee.getZ(),
            SoundEvents.VEX_AMBIENT, banshee.getSoundSource(), 0.4F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, banshee.getX(), banshee.getY() + 1.0D,
            banshee.getZ(), BansheeRules.MAX_AMBIENT_PARTICLES, 0.3D, 0.4D, 0.3D, 0.005D);
    }

    // ---------------------------------------------------------------- accepted damage

    public static void onAcceptedDamage(
        final BansheeEntity banshee,
        final ServerLevel level,
        final DamageSource source
    ) {
        banshee.bansheeTransient().holdTicks = 0;
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        final AttackerObservation observation = observeAttacker(banshee, level, attacker);
        if (!BansheeRules.attackerLegal(observation)) {
            return;
        }
        BansheeState state = banshee.bansheeState();
        if (!BansheeRules.tabooResponseAllowed(true, state.cadence().tabooCooldownTicks())) {
            return;
        }
        final Counters counters = banshee.bansheeCounters();
        counters.tabooResponses++;
        state = state.withCadence(new BansheeState.Cadence(
            BansheeRules.TABOO_COOLDOWN_TICKS,
            state.cadence().routeFailures(), state.cadence().routeRetryTicks(),
            state.cadence().reacquireTicks(), state.cadence().anchorUnavailableTicks()
        ));
        attacker.addEffect(new MobEffectInstance(
            MobEffects.WEAKNESS, BansheeRules.TABOO_EFFECT_TICKS, 0
        ), banshee);
        attacker.addEffect(new MobEffectInstance(
            MobEffects.MINING_FATIGUE, BansheeRules.TABOO_EFFECT_TICKS, 0
        ), banshee);
        state = state.withAttacker(BansheeState.Attacker.recoiling(
            attacker.getUUID(), dimensionOf(level)
        )).withMode(Mode.RECOIL);
        banshee.getNavigation().stop();
        if (BansheeRules.teleportAllowed(true, state.attacker().teleportAttempted())) {
            state = state.withAttacker(new BansheeState.Attacker(
                state.attacker().id(), state.attacker().dimension(),
                state.attacker().remainingTicks(), true
            ));
            banshee.setBansheeState(state);
            attemptVeilRecoilTeleport(banshee, level, attacker);
            return;
        }
        banshee.setBansheeState(state);
    }

    static AttackerObservation observeAttacker(
        final BansheeEntity banshee,
        final ServerLevel level,
        final LivingEntity attacker
    ) {
        final Optional<UUID> ownerId = CreatureBehaviorState.owner(banshee);
        final boolean owner = attacker instanceof Player player
            && ownerId.map(player.getUUID()::equals).orElse(false);
        final boolean sameOwner = attacker instanceof Mob mob
            && ownerId.isPresent()
            && CreatureBehaviorState.owner(mob).equals(ownerId);
        final boolean isPlayer = attacker instanceof Player;
        final boolean eligibleGameMode = attacker instanceof Player player
            && !player.isCreative() && !player.isSpectator();
        return new AttackerObservation(
            true,
            attacker.isAlive(),
            attacker.level() == level,
            attacker == banshee,
            owner,
            sameOwner,
            isPlayer,
            eligibleGameMode,
            attacker.isInvulnerable(),
            attacker instanceof Enemy
        );
    }

    private static void attemptVeilRecoilTeleport(
        final BansheeEntity banshee,
        final ServerLevel level,
        final LivingEntity attacker
    ) {
        banshee.bansheeCounters().teleportAttempts++;
        final Optional<BlockPos> destination = findSafeDestination(
            banshee, level, banshee.blockPosition(), BansheeRules.RECOIL_SEARCH_HORIZONTAL,
            BansheeRules.RECOIL_SEARCH_VERTICAL, BansheeRules.MAX_SAFE_CANDIDATES,
            Optional.of(attacker.position()), false
        );
        if (destination.isEmpty()) {
            return;
        }
        final BlockPos target = destination.orElseThrow();
        level.sendParticles(ParticleTypes.PORTAL, banshee.getX(), banshee.getY() + 0.9D,
            banshee.getZ(), BansheeRules.MAX_RECOIL_PARTICLES, 0.4D, 0.6D, 0.4D, 0.05D);
        level.playSound(null, banshee.getX(), banshee.getY(), banshee.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, banshee.getSoundSource(), 0.8F, 1.2F);
        banshee.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        banshee.getNavigation().stop();
        final BansheeState state = banshee.bansheeState();
        banshee.setBansheeState(state.withCadence(new BansheeState.Cadence(
            state.cadence().tabooCooldownTicks(), 0, state.cadence().routeRetryTicks(),
            state.cadence().reacquireTicks(), state.cadence().anchorUnavailableTicks()
        )));
    }

    // ---------------------------------------------------------------- feedback suppression

    /**
     * Capped local anti-spam gate. At most seven other Banshees are inspected regardless of what
     * they hold (self makes the eighth), a neighbour suppresses only when it independently holds
     * the identical subject or identical observed death AND is itself due on this tick, and the
     * lowest UUID inside that inspected set emits. Local best effort only; never global.
     */
    private static boolean mayEmitLocally(
        final BansheeEntity banshee,
        final ServerLevel level,
        final boolean warning
    ) {
        final BansheeState mine = banshee.bansheeState();
        final long now = level.getGameTime();
        final AABB bounds = banshee.getBoundingBox().inflate(BansheeRules.FEEDBACK_RADIUS);
        final List<UUID> neighbours = new ArrayList<>();
        int inspected = 0;
        for (final BansheeEntity other : level.getEntitiesOfClass(
            BansheeEntity.class, bounds,
            candidate -> candidate != banshee && candidate.isAlive()
        )) {
            if (inspected >= BansheeRules.MAX_FEEDBACK_NEIGHBOURS - 1) {
                break;
            }
            inspected++;
            banshee.bansheeCounters().candidateVisits++;
            final BansheeState theirs = other.bansheeState();
            final boolean sameEvent = warning
                ? BansheeRules.sameWarningEvent(mine.subject().id(), theirs.subject().id())
                : BansheeRules.sameDeathEvent(
                    mine.death().position().map(BlockPos::asLong), mine.death().dimension(),
                    theirs.death().position().map(BlockPos::asLong), theirs.death().dimension()
                );
            if (!sameEvent) {
                continue;
            }
            final boolean due = warning
                ? BansheeRules.neighbourPulseDue(
                    theirs.subject().pulseRemainingTicks(), theirs.subject().pulsesEmitted(),
                    BansheeRules.MAX_WARNING_PULSES,
                    other.bansheeTransient().warningAdvancedAtGameTime == now)
                : BansheeRules.neighbourPulseDue(
                    theirs.death().pulseRemainingTicks(), theirs.death().pulsesEmitted(),
                    BansheeRules.MAX_LAMENT_PULSES,
                    other.bansheeTransient().lamentAdvancedAtGameTime == now);
            if (due) {
                neighbours.add(other.getUUID());
            }
        }
        return BansheeRules.mayEmit(banshee.getUUID(), neighbours);
    }

    // ---------------------------------------------------------------- movement and safety

    /**
     * Cadence-gated search plus route in one step so a safe-destination search is never charged
     * when its result would be discarded by the 20-tick path cadence or an active backoff.
     */
    private static boolean searchAndRoute(
        final BansheeEntity banshee,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final double speed
    ) {
        if (!BansheeRules.pathRequestAllowed(
            banshee.bansheeTransient().pathCooldownTicks,
            banshee.bansheeState().cadence().routeRetryTicks()
        )) {
            return false;
        }
        final Optional<BlockPos> destination = findSafeDestination(
            banshee, level, center, horizontalRadius, verticalRadius, candidateBudget,
            awayFrom, avoidHazards
        );
        return destination.isPresent()
            && requestRoute(banshee, level, destination.orElseThrow(), speed);
    }

    /**
     * Strict route request. The third consecutive failure stops navigation and starts the
     * backoff, but the failure counter is deliberately persisted at its observable maximum so
     * the next decision's {@link BansheeRules#releaseReason} or lament check can release on it;
     * the release path (or a fresh acquisition, or a later success) is what resets the counter.
     */
    static boolean requestRoute(
        final BansheeEntity banshee,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = banshee.bansheeTransient();
        BansheeState state = banshee.bansheeState();
        if (!BansheeRules.pathRequestAllowed(
            scratch.pathCooldownTicks, state.cadence().routeRetryTicks()
        )) {
            return false;
        }
        scratch.pathCooldownTicks = BansheeRules.PATH_INTERVAL_TICKS;
        banshee.bansheeCounters().navigationRequests++;
        final Path path = banshee.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && banshee.getNavigation().moveTo(path, speed);
        final BansheeRules.RouteResult result =
            new BansheeRules.RouteResult(path != null, reachable, accepted);
        final int failures = BansheeRules.routeFailuresAfter(state.cadence().routeFailures(), result);
        int retry = state.cadence().routeRetryTicks();
        if (BansheeRules.routeExhausted(failures)) {
            retry = BansheeRules.routeBackoffAfter(failures);
            banshee.getNavigation().stop();
            scratch.destination = null;
        } else if (accepted) {
            scratch.destination = destination.immutable();
        }
        state = state.withCadence(new BansheeState.Cadence(
            state.cadence().tabooCooldownTicks(),
            failures,
            retry,
            state.cadence().reacquireTicks(),
            state.cadence().anchorUnavailableTicks()
        ));
        banshee.setBansheeState(state);
        return accepted;
    }

    /**
     * Deterministic bounded safe-destination search sharing the 24-candidate/256-read budget.
     * The candidate offsets come from {@link BansheeRules#safeSearchOffsets} and genuinely span
     * the whole horizontal/vertical envelope in every direction, so no side of the envelope is
     * starved by the budget. Preference is the design's lexicographic order via
     * {@link BansheeRules#safeCandidatePreference}: greater separation from the threat, hazard
     * safety, shorter displacement, then the stable packed position. Origin is always excluded,
     * the complete Banshee AABB footprint must already be loaded and inside the world border,
     * and {@code noCollision} must accept the final box.
     */
    static Optional<BlockPos> findSafeDestination(
        final BansheeEntity banshee,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final int candidateBudget,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards
    ) {
        final Counters counters = banshee.bansheeCounters();
        counters.safeSearches++;
        final BlockPos origin = banshee.blockPosition();
        final java.util.Comparator<BansheeRules.SafeCandidate> preference =
            BansheeRules.safeCandidatePreference();
        int reads = 0;
        BlockPos best = null;
        BansheeRules.SafeCandidate bestFacts = null;
        for (final BansheeRules.SafeSearchOffset offset : BansheeRules.safeSearchOffsets(
            banshee.getUUID(), horizontalRadius, verticalRadius, candidateBudget
        )) {
            if (reads + 2 > BansheeRules.MAX_CHARGED_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset.dx(), offset.dy(), offset.dz());
            if (candidate.equals(origin)) {
                continue;
            }
            counters.safeCandidateVisits++;
            final AABB box = banshee.getType().getDimensions()
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
            if (blockState.is(Blocks.LAVA) || fluidState.is(net.minecraft.tags.FluidTags.LAVA)) {
                continue;
            }
            if (!level.noCollision(banshee, box)) {
                continue;
            }
            final BansheeRules.SafeCandidate facts = new BansheeRules.SafeCandidate(
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

    private static Optional<LivingEntity> resolveLiving(final ServerLevel level, final UUID id) {
        final Entity resolved = level.getEntity(id);
        return resolved instanceof LivingEntity living && living.isAlive()
            ? Optional.of(living)
            : Optional.empty();
    }

    private static Optional<ServerPlayer> resolvePlayer(final ServerLevel level, final UUID id) {
        final Player resolved = level.getPlayerByUUID(id);
        return resolved instanceof ServerPlayer player && player.level() == level
            ? Optional.of(player)
            : Optional.empty();
    }

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
