package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRuntime.RouteOutcome;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRuntime.SweepCursor;
import com.kadamitas.warlockery.entity.SpectreRules.HauntEnd;
import com.kadamitas.warlockery.entity.SpectreRules.Phase;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The only server-side Spectre behavior controller and the sole ordinary navigation writer for this
 * species. Every scan, block read and path request goes through the shared
 * {@link ApparitionEpisodeRuntime} budget primitives and is counted.
 *
 * <p>Nothing here enumerates a dimension, forces a chunk, iterates entities globally, edits a block
 * or an inventory, writes another entity's persistent state, teleports, sets a combat target or
 * deals damage of any kind. The single outward mutation is the preserved Darkness and Weakness
 * pair, applied exactly once per haunting to exactly one appointed witness.</p>
 */
public final class SpectreRuntime {
    private SpectreRuntime() {
    }

    /** Species work counters. The shared budget counters live on the apparition counters instead. */
    public static final class Counters {
        long telegraphs;
        long dreads;
        long dreadWindowsOpened;

        public long telegraphs() { return telegraphs; }
        public long dreads() { return dreads; }
        public long dreadWindowsOpened() { return dreadWindowsOpened; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by one cadence but can never replay a dread, a telegraph, a haunting, or a path.
     */
    public static final class TransientState {
        boolean reconciled;
        int driftCooldownTicks;
        int wanderCooldownTicks;
        boolean hazardActive;
        int hazardCooldownTicks;
        BlockPos destination;
        final SweepCursor sweepCursor = new SweepCursor();

        public void resetForLoad() {
            reconciled = false;
            driftCooldownTicks = 0;
            wanderCooldownTicks = 0;
            hazardActive = false;
            hazardCooldownTicks = 0;
            destination = null;
            sweepCursor.reset();
        }

        public boolean hazardActive() {
            return hazardActive;
        }
    }

    public static void tick(final SpectreEntity spectre, final ServerLevel level) {
        reconcileOnLoad(spectre);
        advanceLoadedTimers(spectre);
        if (tickHazard(spectre, level)) {
            return;
        }
        switch (spectre.spectreState().phase()) {
            case DRIFT -> tickDrift(spectre, level);
            case MANIFEST -> tickManifest(spectre, level);
            case DREAD -> tickDread(spectre, level);
            case FADE -> tickFade(spectre);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final SpectreEntity spectre) {
        final TransientState scratch = spectre.spectreTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.driftCooldownTicks = ApparitionEpisodeRules.stableOffset(
            spectre.getUUID(), SpectreRules.DRIFT_INTERVAL_TICKS
        );
        scratch.wanderCooldownTicks = ApparitionEpisodeRules.stableOffset(
            spectre.getUUID(), SpectreRules.WANDER_INTERVAL_TICKS
        );
    }

    private static void advanceLoadedTimers(final SpectreEntity spectre) {
        final TransientState scratch = spectre.spectreTransient();
        scratch.driftCooldownTicks = Math.max(0, scratch.driftCooldownTicks - 1);
        scratch.wanderCooldownTicks = Math.max(0, scratch.wanderCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);

        SpectreState state = spectre.spectreState();
        final RouteLedger route = state.route();
        state = state
            .withRoute(new RouteLedger(
                ApparitionEpisodeRules.decrementLoaded(route.pathCooldownTicks()),
                route.routeFailures(),
                ApparitionEpisodeRules.decrementLoaded(route.routeRetryTicks())
            ))
            .withCooldown(ApparitionEpisodeRules.decrementLoaded(state.cooldownTicks()));
        final Phase phase = state.phase();
        if (phase != Phase.DRIFT) {
            final SpectreState.Haunt haunt = state.haunt();
            state = state.withHaunt(new SpectreState.Haunt(
                ApparitionEpisodeRules.decrementLoaded(haunt.remainingTicks()),
                phase == Phase.MANIFEST
                    ? ApparitionEpisodeRules.decrementLoaded(haunt.manifestRemainingTicks())
                    : haunt.manifestRemainingTicks(),
                phase == Phase.MANIFEST
                    ? ApparitionEpisodeRules.decrementLoaded(haunt.telegraphRemainingTicks())
                    : haunt.telegraphRemainingTicks(),
                phase == Phase.DREAD
                    ? ApparitionEpisodeRules.decrementLoaded(haunt.dreadRemainingTicks())
                    : haunt.dreadRemainingTicks(),
                phase == Phase.FADE
                    ? ApparitionEpisodeRules.decrementLoaded(haunt.fadeRemainingTicks())
                    : haunt.fadeRemainingTicks(),
                haunt.telegraphs(),
                haunt.dreads()
            ));
        }
        spectre.setSpectreState(state);
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final SpectreEntity spectre, final ServerLevel level) {
        final TransientState scratch = spectre.spectreTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !spectre.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = ApparitionEpisodeRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive =
            ApparitionEpisodeRuntime.observeHazard(spectre, level, spectre.apparitionCounters());
        if (!SpectreRules.hazardPreempts(spectre.spectreState().phase(), scratch.hazardActive)) {
            return false;
        }
        spectre.apparitionCounters().hazardInterruptions++;
        applyRoute(spectre, ApparitionEpisodeRuntime.sweepAndRoute(
            spectre, level, spectre.blockPosition(),
            SpectreRules.ESCAPE_SEARCH_HORIZONTAL, SpectreRules.ESCAPE_SEARCH_VERTICAL,
            scratch.sweepCursor, spectre.spectreState().route(), Optional.empty(), true,
            ApparitionEpisodeRules.ESCAPE_SPEED, spectre.apparitionCounters()
        ));
        return true;
    }

    // ---------------------------------------------------------------- drift

    /**
     * Idle drifting plus one bounded appointment sweep. A sweep that qualifies nobody still arms
     * the drift cadence and records the failure, so a Spectre in an empty room runs one sweep every
     * sixty ticks rather than one sweep every tick forever.
     */
    private static void tickDrift(final SpectreEntity spectre, final ServerLevel level) {
        final SpectreState state = spectre.spectreState();
        final TransientState scratch = spectre.spectreTransient();
        if (SpectreRules.hauntStartAllowed(state.cooldownTicks(), state.witness().present())
            && scratch.driftCooldownTicks <= 0) {
            scratch.driftCooldownTicks = SpectreRules.DRIFT_INTERVAL_TICKS;
            if (appointWitness(spectre, level)) {
                return;
            }
        }
        if (scratch.wanderCooldownTicks <= 0 && spectre.getNavigation().isDone()) {
            scratch.wanderCooldownTicks = SpectreRules.WANDER_INTERVAL_TICKS
                + ApparitionEpisodeRules.stableOffset(spectre.getUUID(), 40);
            applyRoute(spectre, ApparitionEpisodeRuntime.sweepAndRoute(
                spectre, level, spectre.blockPosition(),
                SpectreRules.DRIFT_SEARCH_HORIZONTAL, SpectreRules.DRIFT_SEARCH_VERTICAL,
                scratch.sweepCursor, spectre.spectreState().route(), Optional.empty(), true,
                ApparitionEpisodeRules.ROUTE_SPEED, spectre.apparitionCounters()
            ));
        }
    }

    private static boolean appointWitness(final SpectreEntity spectre, final ServerLevel level) {
        final Optional<ApparitionEpisodeRules.PlayerCandidate> selected =
            ApparitionEpisodeRules.appoint(ApparitionEpisodeRuntime.observePlayers(
                spectre, level, SpectreRules.WITNESS_RANGE_SQUARED,
                (_, _) -> true, spectre.apparitionCounters()
            ));
        if (selected.isEmpty()) {
            spectre.apparitionCounters().appointmentFailures++;
            return false;
        }
        spectre.apparitionCounters().episodesStarted++;
        final SpectreState state = spectre.spectreState();
        spectre.setSpectreState(state
            .withWitness(SpectreState.Witness.of(
                selected.orElseThrow().id(), ApparitionEpisodeRuntime.dimensionOf(level)
            ))
            .withHaunt(SpectreState.Haunt.started())
            // Route failures accumulated while idly drifting belong to the drifting, not to the
            // haunting that follows. Without this reset a Spectre that failed to route in an
            // enclosed space carries three stale failures into a fresh haunting, and
            // endHauntIfRequired releases it on ROUTE_FAILURE before it ever telegraphs. An open
            // backoff is preserved, so a new haunting still cannot spam path requests.
            .withRoute(new RouteLedger(0, 0, state.route().routeRetryTicks()))
            .withPhase(Phase.MANIFEST));
        return true;
    }

    // ---------------------------------------------------------------- manifest

    /**
     * The visible, finite telegraph. It applies no effect and no damage: it exists so the appointed
     * witness and every onlooker can see the apparition drawing itself out of the veil before any
     * dread is permitted. The approach itself is ordinary bounded spectral flight.
     */
    private static void tickManifest(final SpectreEntity spectre, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observedWitness(spectre, level);
        if (endHauntIfRequired(spectre, level, resolved)) {
            return;
        }
        final ServerPlayer witness = resolved.orElseThrow();
        SpectreState state = spectre.spectreState();
        spectre.getLookControl().setLookAt(witness, 30.0F, 30.0F);
        if (SpectreRules.telegraphsRemaining(state.haunt().telegraphs()) > 0
            && SpectreRules.telegraphDue(
                state.haunt().telegraphRemainingTicks(), state.haunt().telegraphs())) {
            state = state.withHaunt(state.haunt().withTelegraph(
                SpectreRules.TELEGRAPH_INTERVAL_TICKS, state.haunt().telegraphs() + 1
            ));
            spectre.setSpectreState(state);
            spectre.spectreCounters().telegraphs++;
            level.playSound(null, spectre.getX(), spectre.getY(), spectre.getZ(),
                SoundEvents.VEX_CHARGE, spectre.getSoundSource(), 0.7F, 0.6F);
            level.sendParticles(ParticleTypes.SOUL, spectre.getX(), spectre.getY() + 0.7D,
                spectre.getZ(), SpectreRules.MAX_TELEGRAPH_PARTICLES, 0.35D, 0.45D, 0.35D, 0.01D);
        }
        if (SpectreRules.manifestationGraduates(state.haunt().manifestRemainingTicks())) {
            // The window this branch owns has elapsed. It opens the dread window itself: no
            // sibling branch and no state constructor may graduate a manifestation.
            spectre.spectreCounters().dreadWindowsOpened++;
            spectre.getNavigation().stop();
            spectre.setSpectreState(state
                .withHaunt(state.haunt().withDread(SpectreRules.DREAD_TICKS))
                .withPhase(Phase.DREAD));
            return;
        }
        if (spectre.distanceToSqr(witness) > SpectreRules.DREAD_BAND_SQUARED
            && spectre.getNavigation().isDone()) {
            applyRoute(spectre, ApparitionEpisodeRuntime.sweepAndRoute(
                spectre, level, witness.blockPosition(),
                SpectreRules.DESTINATION_SEARCH_HORIZONTAL,
                SpectreRules.DESTINATION_SEARCH_VERTICAL,
                spectre.spectreTransient().sweepCursor, state.route(), Optional.empty(), true,
                ApparitionEpisodeRules.ROUTE_SPEED, spectre.apparitionCounters()
            ));
        }
    }

    // ---------------------------------------------------------------- dread

    /**
     * Exactly one Darkness and Weakness delivery per haunting, against exactly the one appointed
     * witness. The delivery is recorded before the effects are applied so no reentrant handler can
     * produce a second one, and the haunting closes into the fade in the same pass whether the
     * dread landed or the window simply ran out. There is deliberately no refresh path: a witness
     * who stays in the band receives nothing further.
     */
    private static void tickDread(final SpectreEntity spectre, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observedWitness(spectre, level);
        if (endHauntIfRequired(spectre, level, resolved)) {
            return;
        }
        final ServerPlayer witness = resolved.orElseThrow();
        SpectreState state = spectre.spectreState();
        if (state.haunt().dreadRemainingTicks() <= 0) {
            // The window this branch owns has closed. It must arm the fade itself: no sibling
            // branch and no state constructor is permitted to end a dread window.
            enterFade(spectre);
            return;
        }
        spectre.getLookControl().setLookAt(witness, 30.0F, 30.0F);
        spectre.apparitionCounters().lineOfSightChecks++;
        final boolean visible = spectre.getSensing().hasLineOfSight(witness);
        if (!SpectreRules.dreadAllowed(state.haunt().dreads(), spectre.distanceToSqr(witness),
            visible, state.haunt().dreadRemainingTicks())) {
            if (spectre.distanceToSqr(witness) > SpectreRules.DREAD_BAND_SQUARED
                && spectre.getNavigation().isDone()) {
                applyRoute(spectre, ApparitionEpisodeRuntime.sweepAndRoute(
                    spectre, level, witness.blockPosition(),
                    SpectreRules.DESTINATION_SEARCH_HORIZONTAL,
                    SpectreRules.DESTINATION_SEARCH_VERTICAL,
                    spectre.spectreTransient().sweepCursor, state.route(), Optional.empty(), true,
                    ApparitionEpisodeRules.ROUTE_SPEED, spectre.apparitionCounters()
                ));
            }
            return;
        }
        state = state.withHaunt(state.haunt().withDreads(state.haunt().dreads() + 1));
        spectre.setSpectreState(state);
        spectre.spectreCounters().dreads++;
        witness.addEffect(new MobEffectInstance(
            MobEffects.DARKNESS, SpectreRules.DARKNESS_TICKS, SpectreRules.DARKNESS_AMPLIFIER
        ));
        witness.addEffect(new MobEffectInstance(
            MobEffects.WEAKNESS, SpectreRules.WEAKNESS_TICKS, SpectreRules.WEAKNESS_AMPLIFIER
        ));
        level.playSound(null, spectre.getX(), spectre.getY(), spectre.getZ(),
            SoundEvents.VEX_AMBIENT, spectre.getSoundSource(), 0.8F, 0.5F);
        enterFade(spectre);
    }

    private static void enterFade(final SpectreEntity spectre) {
        spectre.getNavigation().stop();
        spectre.spectreTransient().destination = null;
        final SpectreState state = spectre.spectreState();
        spectre.setSpectreState(state
            .withHaunt(state.haunt().withFade(SpectreRules.FADE_TICKS))
            .withPhase(Phase.FADE));
    }

    // ---------------------------------------------------------------- fade

    private static void tickFade(final SpectreEntity spectre) {
        if (spectre.spectreState().haunt().fadeRemainingTicks() > 0) {
            return;
        }
        endHaunt(spectre);
    }

    // ---------------------------------------------------------------- shared endings

    private static Optional<ServerPlayer> observedWitness(
        final SpectreEntity spectre,
        final ServerLevel level
    ) {
        return spectre.spectreState().witness().id()
            .flatMap(id -> ApparitionEpisodeRuntime.resolvePlayer(level, id));
    }

    private static boolean endHauntIfRequired(
        final SpectreEntity spectre,
        final ServerLevel level,
        final Optional<ServerPlayer> resolved
    ) {
        final SpectreState state = spectre.spectreState();
        final boolean sameDimension = state.witness().dimension()
            .map(ApparitionEpisodeRuntime.dimensionOf(level)::equals)
            .orElse(false);
        final HauntEnd end = SpectreRules.hauntEnd(new SpectreRules.WitnessObservation(
            state.witness().present(),
            sameDimension,
            resolved.isPresent(),
            resolved.map(player -> !player.isCreative() && !player.isSpectator()).orElse(false),
            resolved.map(spectre::distanceToSqr).orElse(Double.MAX_VALUE),
            state.haunt().remainingTicks(),
            state.route().routeFailures()
        ));
        if (end == HauntEnd.NONE) {
            return false;
        }
        endHaunt(spectre);
        return true;
    }

    private static void endHaunt(final SpectreEntity spectre) {
        spectre.apparitionCounters().episodesEnded++;
        spectre.getNavigation().stop();
        spectre.setTarget(null);
        spectre.spectreTransient().destination = null;
        spectre.setSpectreState(spectre.spectreState().endHaunt());
    }

    // ---------------------------------------------------------------- movement plumbing

    /** The only place the shared route outcome is written back into this species' own state. */
    private static void applyRoute(final SpectreEntity spectre, final RouteOutcome outcome) {
        spectre.spectreTransient().destination = outcome.destination().orElse(null);
        spectre.setSpectreState(spectre.spectreState().withRoute(outcome.ledger()));
    }
}
