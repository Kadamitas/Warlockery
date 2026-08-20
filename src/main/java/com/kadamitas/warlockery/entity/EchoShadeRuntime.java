package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRuntime.RouteOutcome;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRuntime.SweepCursor;
import com.kadamitas.warlockery.entity.EchoShadeRules.EchoEnd;
import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Echo Shade behavior controller and the sole ordinary navigation writer for
 * this species. Every scan, block read, path request and strike goes through the shared
 * {@link ApparitionEpisodeRuntime} budget primitives and is counted.
 *
 * <p>Nothing here enumerates a dimension, forces a chunk, iterates entities globally, edits a
 * block or an inventory, writes another entity's persistent state, teleports, applies a status
 * effect, or reads anything about the marked player beyond its position, visibility and liveness.
 * The single outward mutation is one ordinary attributed melee attempt per echo.</p>
 */
public final class EchoShadeRuntime {
    private EchoShadeRuntime() {
    }

    /** Species work counters. The shared budget counters live on the apparition counters instead. */
    public static final class Counters {
        long strikes;
        long answersComputed;
        long unanswerableRecords;

        public long strikes() { return strikes; }
        public long answersComputed() { return answersComputed; }
        public long unanswerableRecords() { return unanswerableRecords; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by one cadence but can never replay a strike, an echo, or a path.
     */
    public static final class TransientState {
        boolean reconciled;
        int watchCooldownTicks;
        boolean hazardActive;
        int hazardCooldownTicks;
        BlockPos destination;
        BlockPos answer;
        Vec3 lastMarkPosition;
        final SweepCursor sweepCursor = new SweepCursor();

        public void resetForLoad() {
            reconciled = false;
            watchCooldownTicks = 0;
            hazardActive = false;
            hazardCooldownTicks = 0;
            destination = null;
            answer = null;
            lastMarkPosition = null;
            sweepCursor.reset();
        }

        public boolean hazardActive() {
            return hazardActive;
        }
    }

    public static void tick(final EchoShadeEntity shade, final ServerLevel level) {
        reconcileOnLoad(shade);
        advanceLoadedTimers(shade);
        if (tickHazard(shade, level)) {
            return;
        }
        switch (shade.echoShadeState().phase()) {
            case WATCH -> tickWatch(shade, level);
            case RECORD -> tickRecord(shade, level);
            case ANSWER -> tickAnswer(shade, level);
            case STRIKE -> tickStrike(shade, level);
            case RECOVER -> tickRecover(shade);
        }
    }

    /**
     * The one damage hook. An attacked shade abandons a half-finished echo instead of walking to an
     * answer that no longer means anything, but it never gains a target and never holds a grudge.
     */
    public static void onAcceptedDamage(final EchoShadeEntity shade) {
        final Phase phase = shade.echoShadeState().phase();
        if (phase == Phase.RECORD || phase == Phase.ANSWER) {
            endEcho(shade);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final EchoShadeEntity shade) {
        final TransientState scratch = shade.echoShadeTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        scratch.watchCooldownTicks = ApparitionEpisodeRules.stableOffset(
            shade.getUUID(), EchoShadeRules.WATCH_INTERVAL_TICKS
        );
    }

    private static void advanceLoadedTimers(final EchoShadeEntity shade) {
        final TransientState scratch = shade.echoShadeTransient();
        scratch.watchCooldownTicks = Math.max(0, scratch.watchCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);

        EchoShadeState state = shade.echoShadeState();
        final RouteLedger route = state.route();
        state = state
            .withRoute(new RouteLedger(
                ApparitionEpisodeRules.decrementLoaded(route.pathCooldownTicks()),
                route.routeFailures(),
                ApparitionEpisodeRules.decrementLoaded(route.routeRetryTicks())
            ))
            .withCooldown(ApparitionEpisodeRules.decrementLoaded(state.cooldownTicks()));
        final Phase phase = state.phase();
        if (phase != Phase.WATCH) {
            final EchoShadeState.Echo echo = state.echo();
            state = state.withEcho(new EchoShadeState.Echo(
                ApparitionEpisodeRules.decrementLoaded(echo.remainingTicks()),
                phase == Phase.RECORD
                    ? ApparitionEpisodeRules.decrementLoaded(echo.recordRemainingTicks())
                    : echo.recordRemainingTicks(),
                phase == Phase.RECORD
                    ? ApparitionEpisodeRules.decrementLoaded(echo.sampleRemainingTicks())
                    : echo.sampleRemainingTicks(),
                phase == Phase.ANSWER
                    ? ApparitionEpisodeRules.decrementLoaded(echo.answerRemainingTicks())
                    : echo.answerRemainingTicks(),
                phase == Phase.STRIKE
                    ? ApparitionEpisodeRules.decrementLoaded(echo.strikeRemainingTicks())
                    : echo.strikeRemainingTicks(),
                phase == Phase.RECOVER
                    ? ApparitionEpisodeRules.decrementLoaded(echo.recoverRemainingTicks())
                    : echo.recoverRemainingTicks(),
                echo.recordedMillisX(),
                echo.recordedMillisZ(),
                echo.samples(),
                echo.strikes()
            ));
        }
        shade.setEchoShadeState(state);
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final EchoShadeEntity shade, final ServerLevel level) {
        final TransientState scratch = shade.echoShadeTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !shade.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = ApparitionEpisodeRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive =
            ApparitionEpisodeRuntime.observeHazard(shade, level, shade.apparitionCounters());
        if (!EchoShadeRules.hazardPreempts(shade.echoShadeState().phase(), scratch.hazardActive)) {
            return false;
        }
        shade.apparitionCounters().hazardInterruptions++;
        applyRoute(shade, ApparitionEpisodeRuntime.sweepAndRoute(
            shade, level, shade.blockPosition(),
            EchoShadeRules.ESCAPE_SEARCH_HORIZONTAL, EchoShadeRules.ESCAPE_SEARCH_VERTICAL,
            scratch.sweepCursor, shade.echoShadeState().route(), Optional.empty(), true,
            ApparitionEpisodeRules.ESCAPE_SPEED, shade.apparitionCounters()
        ));
        return true;
    }

    // ---------------------------------------------------------------- watch

    /**
     * One bounded appointment sweep. A sweep that qualifies nobody still arms the watch cadence and
     * records the failure, so an Echo Shade in an empty room runs one sweep every forty ticks
     * rather than one sweep every tick forever.
     */
    private static void tickWatch(final EchoShadeEntity shade, final ServerLevel level) {
        final EchoShadeState state = shade.echoShadeState();
        if (!EchoShadeRules.echoStartAllowed(state.cooldownTicks(), state.mark().present())) {
            return;
        }
        final TransientState scratch = shade.echoShadeTransient();
        if (scratch.watchCooldownTicks > 0) {
            return;
        }
        scratch.watchCooldownTicks = EchoShadeRules.WATCH_INTERVAL_TICKS;
        final Optional<ApparitionEpisodeRules.PlayerCandidate> selected =
            ApparitionEpisodeRules.appoint(ApparitionEpisodeRuntime.observePlayers(
                shade, level, EchoShadeRules.MARK_RANGE_SQUARED,
                (_, _) -> true, shade.apparitionCounters()
            ));
        if (selected.isEmpty()) {
            shade.apparitionCounters().appointmentFailures++;
            return;
        }
        shade.apparitionCounters().episodesStarted++;
        scratch.answer = null;
        scratch.lastMarkPosition = null;
        shade.setEchoShadeState(state
            .withMark(EchoShadeState.Mark.of(
                selected.orElseThrow().id(), ApparitionEpisodeRuntime.dimensionOf(level)
            ))
            .withEcho(EchoShadeState.Echo.started())
            // Route failures accumulated while idly watching belong to the watching, not to the
            // echo that follows. Without this reset a Shade that failed to route in an enclosed
            // space carries three stale failures into a fresh echo, and endEchoIfRequired releases
            // it on ROUTE_FAILURE before it ever records a gesture. An open backoff is preserved,
            // so a new echo still cannot spam path requests.
            .withRoute(new RouteLedger(0, 0, state.route().routeRetryTicks()))
            .withPhase(Phase.RECORD));
    }

    // ---------------------------------------------------------------- record

    /**
     * The whole of an Echo Shade's perception: the mark's horizontal displacement between samples,
     * clamped per sample and per window. Nothing else about the player is read, and the shade holds
     * still while it watches, because a mirror does not walk alongside what it reflects.
     */
    private static void tickRecord(final EchoShadeEntity shade, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observedMark(shade, level);
        if (endEchoIfRequired(shade, level, resolved)) {
            return;
        }
        final ServerPlayer mark = resolved.orElseThrow();
        final TransientState scratch = shade.echoShadeTransient();
        shade.getNavigation().stop();
        shade.getLookControl().setLookAt(mark, 30.0F, 30.0F);
        EchoShadeState state = shade.echoShadeState();
        final EchoShadeState.Echo echo = state.echo();
        if (EchoShadeRules.sampleDue(echo.sampleRemainingTicks(), echo.samples())) {
            final Vec3 previous = scratch.lastMarkPosition;
            scratch.lastMarkPosition = mark.position();
            state = previous == null
                ? state.withEcho(echo.withSampled(
                    echo.recordedMillisX(), echo.recordedMillisZ(), echo.samples() + 1))
                : state.withEcho(echo.withSampled(
                    EchoShadeRules.accumulate(echo.recordedMillisX(),
                        EchoShadeRules.sampleMillis(mark.getX() - previous.x)),
                    EchoShadeRules.accumulate(echo.recordedMillisZ(),
                        EchoShadeRules.sampleMillis(mark.getZ() - previous.z)),
                    echo.samples() + 1));
            shade.setEchoShadeState(state);
        }
        if (state.echo().recordRemainingTicks() > 0) {
            return;
        }
        if (!EchoShadeRules.answerable(
            state.echo().recordedMillisX(), state.echo().recordedMillisZ())) {
            // A motionless mark leaves nothing to answer. The shade releases rather than inventing
            // a gesture, and the release arms the ordinary cadence like any other ending.
            shade.echoShadeCounters().unanswerableRecords++;
            endEcho(shade);
            return;
        }
        shade.echoShadeCounters().answersComputed++;
        scratch.answer = answerPosition(shade, state);
        level.sendParticles(ParticleTypes.SMOKE, shade.getX(), shade.getY() + 0.8D, shade.getZ(),
            EchoShadeRules.MAX_ANSWER_PARTICLES, 0.25D, 0.35D, 0.25D, 0.0D);
        shade.setEchoShadeState(state
            .withEcho(state.echo().withAnswer(EchoShadeRules.ANSWER_TICKS))
            .withPhase(Phase.ANSWER));
    }

    /**
     * The single bounded answer position: the recorded horizontal gesture, negated and scaled into
     * the offset range, applied to the mark's last sampled block. It always lands inside the
     * declared offset box, so no recorded motion can produce an unbounded or remote destination.
     */
    private static BlockPos answerPosition(final EchoShadeEntity shade, final EchoShadeState state) {
        final Vec3 sampled = shade.echoShadeTransient().lastMarkPosition;
        final BlockPos origin =
            sampled == null ? shade.blockPosition() : BlockPos.containing(sampled);
        return origin.offset(
            EchoShadeRules.answerOffset(state.echo().recordedMillisX()),
            0,
            EchoShadeRules.answerOffset(state.echo().recordedMillisZ())
        ).immutable();
    }

    // ---------------------------------------------------------------- answer

    private static void tickAnswer(final EchoShadeEntity shade, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observedMark(shade, level);
        if (endEchoIfRequired(shade, level, resolved)) {
            return;
        }
        final EchoShadeState state = shade.echoShadeState();
        if (state.echo().answerRemainingTicks() <= 0) {
            // The window this branch owns has closed. It ends the echo itself, arming the cadence.
            endEcho(shade);
            return;
        }
        final ServerPlayer mark = resolved.orElseThrow();
        shade.getLookControl().setLookAt(mark, 30.0F, 30.0F);
        final BlockPos answer = shade.echoShadeTransient().answer;
        if (answer == null) {
            endEcho(shade);
            return;
        }
        final boolean standingOnTheAnswer =
            EchoShadeRules.answerReached(shade.distanceToSqr(Vec3.atCenterOf(answer)));
        final boolean withinReach =
            shade.distanceToSqr(mark) <= EchoShadeRules.STRIKE_BAND_SQUARED;
        if (standingOnTheAnswer || withinReach) {
            shade.getNavigation().stop();
            shade.setEchoShadeState(state
                .withEcho(state.echo().withStrike(EchoShadeRules.STRIKE_TICKS))
                .withPhase(Phase.STRIKE));
            return;
        }
        if (shade.getNavigation().isDone()) {
            applyRoute(shade, ApparitionEpisodeRuntime.sweepAndRoute(
                shade, level, answer,
                EchoShadeRules.DESTINATION_SEARCH_HORIZONTAL,
                EchoShadeRules.DESTINATION_SEARCH_VERTICAL,
                shade.echoShadeTransient().sweepCursor, state.route(), Optional.empty(), true,
                ApparitionEpisodeRules.ROUTE_SPEED, shade.apparitionCounters()
            ));
        }
    }

    // ---------------------------------------------------------------- strike

    /**
     * At most one ordinary attributed melee attempt per echo. The attempt is recorded before it is
     * delivered so no reentrant damage handler can produce a second one, and the echo closes into
     * recovery in the same pass whether the attempt landed or the window simply ran out.
     */
    private static void tickStrike(final EchoShadeEntity shade, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = observedMark(shade, level);
        if (endEchoIfRequired(shade, level, resolved)) {
            return;
        }
        final ServerPlayer mark = resolved.orElseThrow();
        EchoShadeState state = shade.echoShadeState();
        if (state.echo().strikeRemainingTicks() <= 0) {
            // The window this branch owns has closed. It must arm the recovery itself: no sibling
            // branch and no state constructor is permitted to end a strike window.
            enterRecovery(shade);
            return;
        }
        shade.getLookControl().setLookAt(mark, 30.0F, 30.0F);
        shade.apparitionCounters().lineOfSightChecks++;
        final boolean visible = shade.getSensing().hasLineOfSight(mark);
        if (!EchoShadeRules.strikeAllowed(state.echo().strikes(), shade.distanceToSqr(mark),
            visible, state.echo().strikeRemainingTicks())) {
            return;
        }
        if (EchoShadeRules.strikeDamage(
            (float) shade.getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0.0F) {
            // A shade stripped of its attack attribute has nothing to contribute, so the echo
            // recovers rather than delivering a zero-damage hit that still reads as an answer.
            enterRecovery(shade);
            return;
        }
        state = state.withEcho(state.echo().withStrikes(state.echo().strikes() + 1));
        shade.setEchoShadeState(state);
        shade.echoShadeCounters().strikes++;
        shade.setStriking(true);
        try {
            shade.swing(InteractionHand.MAIN_HAND);
            shade.doHurtTarget(level, mark);
        } finally {
            shade.setStriking(false);
        }
        level.playSound(null, shade.getX(), shade.getY(), shade.getZ(),
            SoundEvents.GLASS_BREAK, shade.getSoundSource(), 0.6F, 1.6F);
        enterRecovery(shade);
    }

    private static void enterRecovery(final EchoShadeEntity shade) {
        shade.getNavigation().stop();
        shade.setTarget(null);
        shade.echoShadeTransient().destination = null;
        final EchoShadeState state = shade.echoShadeState();
        shade.setEchoShadeState(state
            .withEcho(state.echo().withRecover(EchoShadeRules.RECOVER_TICKS))
            .withPhase(Phase.RECOVER));
    }

    // ---------------------------------------------------------------- recover

    private static void tickRecover(final EchoShadeEntity shade) {
        if (shade.echoShadeState().echo().recoverRemainingTicks() > 0) {
            return;
        }
        endEcho(shade);
    }

    // ---------------------------------------------------------------- shared endings

    private static Optional<ServerPlayer> observedMark(
        final EchoShadeEntity shade,
        final ServerLevel level
    ) {
        return shade.echoShadeState().mark().id()
            .flatMap(id -> ApparitionEpisodeRuntime.resolvePlayer(level, id));
    }

    private static boolean endEchoIfRequired(
        final EchoShadeEntity shade,
        final ServerLevel level,
        final Optional<ServerPlayer> resolved
    ) {
        final EchoShadeState state = shade.echoShadeState();
        final boolean sameDimension = state.mark().dimension()
            .map(ApparitionEpisodeRuntime.dimensionOf(level)::equals)
            .orElse(false);
        final EchoEnd end = EchoShadeRules.echoEnd(new EchoShadeRules.MarkObservation(
            state.mark().present(),
            sameDimension,
            resolved.isPresent(),
            resolved.map(player -> !player.isCreative() && !player.isSpectator()).orElse(false),
            resolved.map(shade::distanceToSqr).orElse(Double.MAX_VALUE),
            state.echo().remainingTicks(),
            state.route().routeFailures()
        ));
        if (end == EchoEnd.NONE) {
            return false;
        }
        endEcho(shade);
        return true;
    }

    private static void endEcho(final EchoShadeEntity shade) {
        shade.apparitionCounters().episodesEnded++;
        shade.getNavigation().stop();
        shade.setTarget(null);
        final TransientState scratch = shade.echoShadeTransient();
        scratch.destination = null;
        scratch.answer = null;
        scratch.lastMarkPosition = null;
        shade.setEchoShadeState(shade.echoShadeState().endEcho());
    }

    // ---------------------------------------------------------------- movement plumbing

    /** The only place the shared route outcome is written back into this species' own state. */
    private static void applyRoute(final EchoShadeEntity shade, final RouteOutcome outcome) {
        shade.echoShadeTransient().destination = outcome.destination().orElse(null);
        shade.setEchoShadeState(shade.echoShadeState().withRoute(outcome.ledger()));
    }

    /** Entity seam: whether this exact player is the one currently marked. */
    static boolean isMark(final EchoShadeEntity shade, final UUID candidate) {
        return shade.echoShadeState().mark().id().filter(candidate::equals).isPresent();
    }
}
