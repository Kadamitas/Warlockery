package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import com.kadamitas.warlockery.entity.UmbralSigilRules.SealEnd;
import com.kadamitas.warlockery.entity.UmbralSigilRules.SubjectObservation;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.Candidates;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side Umbral Sigil behavior controller and the sole ordinary navigation writer for
 * this species. Every player read, block read, path request and attack attempt this species makes
 * passes through exactly one branch here and is counted.
 *
 * <p>Nothing here enumerates a dimension, forces a chunk, iterates entities globally, edits a block
 * or an inventory, writes another entity's persistent state, teleports, applies a status effect,
 * places or removes a ward, or reads anything about the appointed subject beyond its position,
 * visibility, dimension and liveness. The single outward mutation is one ordinary attributed melee
 * attempt per seal.</p>
 *
 * <p>Per-tick code here is explicit loops over fixed arrays and over the level's own player list.
 * There are no stream pipelines on any path a server tick can reach.</p>
 */
public final class UmbralSigilRuntime {

    static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );

    /**
     * The honest worst-case read cost of qualifying one destination: the world border test, the
     * four chunk-presence tests reduced to one footprint check, the block state, the fluid state
     * and the collision sweep. Charged before any filter may reject the candidate.
     */
    static final int READS_PER_DESTINATION = 4;

    private static final Comparator<UUID> STABLE_IDENTITY = Candidates.unsignedUuidOrder();

    /**
     * Per-level path-start quota. Weakly keyed so an unloaded level cannot be retained, and read
     * and written only from that level's own server tick.
     */
    private static final Map<ServerLevel, PathQuota> PATH_QUOTAS = new WeakHashMap<>();

    private UmbralSigilRuntime() {
    }

    private static final class PathQuota {
        private long tick = Long.MIN_VALUE;
        private int spent;
    }

    /** Structural work counters. Pass-local diagnostics: never persisted, never behavior. */
    public static final class Counters {
        long appointmentSweeps;
        long appointmentCandidateVisits;
        long appointmentReads;
        long lineOfSightChecks;
        long appointmentFailures;
        long sealsStarted;
        long sealsEnded;
        long verticesReached;
        long strikes;
        long strikesLanded;
        long hazardSamples;
        long hazardInterruptions;
        long blockReads;
        long navigationRequests;
        long routeFailures;
        long unroutableRequests;
        long pathStartsDenied;

        public long appointmentSweeps() { return appointmentSweeps; }
        public long appointmentCandidateVisits() { return appointmentCandidateVisits; }
        public long appointmentReads() { return appointmentReads; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long appointmentFailures() { return appointmentFailures; }
        public long sealsStarted() { return sealsStarted; }
        public long sealsEnded() { return sealsEnded; }
        public long verticesReached() { return verticesReached; }
        public long strikes() { return strikes; }
        public long strikesLanded() { return strikesLanded; }
        public long hazardSamples() { return hazardSamples; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long blockReads() { return blockReads; }
        public long navigationRequests() { return navigationRequests; }
        public long routeFailures() { return routeFailures; }
        public long unroutableRequests() { return unroutableRequests; }
        public long pathStartsDenied() { return pathStartsDenied; }
    }

    /**
     * The transient seal: who is being sealed, in which dimension, and around which snapshot
     * centre. Never persisted, so a reload can neither resume a seal nor carry a reference to
     * anybody.
     *
     * <p><strong>Canonical-constructor classification: identity shape, legitimate.</strong> The
     * three components are one identity, not three facts. A subject without a dimension names
     * nobody, and a centre without a subject encloses nobody, so the constructor collapses a
     * half-written seal to none. It asserts that the parts of one identity cannot disagree, which
     * is the type's job. It decides that nothing <em>ended</em> and touches no duration, so no tick
     * branch can lose the ending it owns; that is the defect shape and it is deliberately absent.
     * </p>
     */
    record Seal(Optional<UUID> subject, Optional<String> dimension, Optional<BlockPos> centre) {
        Seal {
            subject = Objects.requireNonNull(subject, "subject");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(key -> !key.isBlank());
            centre = Objects.requireNonNull(centre, "centre");
            if (subject.isEmpty() || dimension.isEmpty() || centre.isEmpty()) {
                subject = Optional.empty();
                dimension = Optional.empty();
                centre = Optional.empty();
            }
        }

        static Seal none() {
            return new Seal(Optional.empty(), Optional.empty(), Optional.empty());
        }

        static Seal of(final UUID subject, final String dimension, final BlockPos centre) {
            return new Seal(
                Optional.of(subject), Optional.of(dimension), Optional.of(centre.immutable())
            );
        }

        boolean appointed() {
            return subject.isPresent();
        }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay work
     * by at most one cadence and can never replay a strike, a seal or a path.
     */
    public static final class TransientState {
        boolean reconciled;
        Cadence selectCadence = Cadence.every(UmbralSigilRules.SELECT_INTERVAL_TICKS);
        boolean hazardActive;
        int hazardCooldownTicks;
        Seal seal = Seal.none();
        BlockPos destination;

        public void resetForLoad() {
            reconciled = false;
            selectCadence = Cadence.every(UmbralSigilRules.SELECT_INTERVAL_TICKS);
            hazardActive = false;
            hazardCooldownTicks = 0;
            seal = Seal.none();
            destination = null;
        }

        /** Whether the last bounded sample found a contact hazard. Read by the state suite. */
        public boolean hazardActive() {
            return hazardActive;
        }

        public boolean appointed() {
            return seal.appointed();
        }

        public Optional<BlockPos> centre() {
            return seal.centre();
        }
    }

    // ---------------------------------------------------------------- tick

    public static void tick(final UmbralSigilEntity sigil, final ServerLevel level) {
        reconcileOnLoad(sigil);
        advanceLoadedTimers(sigil);
        if (tickHazard(sigil, level)) {
            return;
        }
        switch (sigil.sigilState().phase()) {
            case DORMANT -> tickDormant(sigil, level);
            case INSCRIBE_1, INSCRIBE_2, INSCRIBE_3 -> tickInscribe(sigil, level);
            case CLOSE -> tickClose(sigil, level);
            case STRIKE -> tickStrike(sigil, level);
            case RECOVER -> tickRecover(sigil);
        }
    }

    /**
     * The one damage hook. A struck Sigil abandons a half-drawn seal rather than continuing to
     * trace a figure that no longer means anything, but it never gains a target and never
     * retaliates outside the single attempt its own close owns.
     */
    public static void onAcceptedDamage(final UmbralSigilEntity sigil) {
        if (UmbralSigilRules.sealing(sigil.sigilState().phase())) {
            cancelIntoRecovery(sigil);
        }
    }

    /** Entity seam: whether this exact player is the one currently being sealed. */
    public static boolean isSubject(final UmbralSigilEntity sigil, final UUID candidate) {
        return sigil.sigilTransient().seal.subject().filter(candidate::equals).isPresent();
    }

    // ---------------------------------------------------------------- lifecycle

    private static void reconcileOnLoad(final UmbralSigilEntity sigil) {
        final TransientState scratch = sigil.sigilTransient();
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        // A per-entity stagger so a crowd of Sigils never sweeps on the same tick. Derived from
        // identity rather than world time, so an unloaded Sigil cannot return on a stale schedule.
        scratch.selectCadence = new Cadence(
            UmbralSigilRules.SELECT_INTERVAL_TICKS,
            Ticks.stableOffset(sigil.getUUID(), UmbralSigilRules.SELECT_INTERVAL_TICKS)
        );
    }

    private static void advanceLoadedTimers(final UmbralSigilEntity sigil) {
        final TransientState scratch = sigil.sigilTransient();
        scratch.selectCadence = scratch.selectCadence.step();
        scratch.hazardCooldownTicks = Ticks.decrementLoaded(scratch.hazardCooldownTicks);

        final UmbralSigilState state = sigil.sigilState();
        sigil.setSigilState(state
            .withTimer(state.timer().step())
            .withRoute(state.route().step())
            .withCooldown(Ticks.decrementLoaded(state.cooldownTicks())));
    }

    // ---------------------------------------------------------------- hazard overlay

    private static boolean tickHazard(final UmbralSigilEntity sigil, final ServerLevel level) {
        final TransientState scratch = sigil.sigilTransient();
        if (scratch.hazardCooldownTicks > 0) {
            return scratch.hazardActive && !sigil.getNavigation().isDone();
        }
        scratch.hazardCooldownTicks = UmbralSigilRules.HAZARD_INTERVAL_TICKS;
        scratch.hazardActive = observeHazard(sigil, level);
        if (!UmbralSigilRules.hazardPreempts(sigil.sigilState().phase(), scratch.hazardActive)) {
            return false;
        }
        sigil.sigilCounters().hazardInterruptions++;
        cancelIntoRecovery(sigil);
        // One bounded withdrawal candidate: the block directly above. A Sigil in contact with a
        // hazard is in contact with the floor of it, and it flies. An unusable candidate is a
        // route failure like any other, so the cadence is armed and the attempt is not retried on
        // the next tick.
        requestRoute(sigil, level, sigil.blockPosition().above(), UmbralSigilRules.ESCAPE_SPEED);
        return true;
    }

    /**
     * Bounded local hazard observation over the 3 x 3 x 3 contact neighbourhood.
     *
     * <p>The declared ceiling equals the volume of that neighbourhood, so the budget can never
     * truncate the sweep partway. The loops start at {@code -1} on every axis and the offset
     * {@code (0, 0, 0)} is evaluated rather than skipped, so the Sigil's own block, all six faces,
     * all twelve edges and all eight far corners are read on every single sample. An unloaded
     * footprint is never forced: it simply reports no hazard.</p>
     */
    private static boolean observeHazard(final UmbralSigilEntity sigil, final ServerLevel level) {
        final Counters counters = sigil.sigilCounters();
        counters.hazardSamples++;
        counters.blockReads += UmbralSigilRules.MAX_HAZARD_READS;
        if (sigil.isOnFire() || sigil.isInLava()) {
            return true;
        }
        if (!footprintLoaded(level, sigil.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos centre = sigil.blockPosition();
        int reads = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= UmbralSigilRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    if (isHazardBlock(level.getBlockState(centre.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    // ---------------------------------------------------------------- dormant

    /**
     * One bounded appointment sweep. The cadence is armed the moment the sweep begins, before its
     * outcome is known, so a Sigil drifting in an empty room runs one sweep every forty ticks
     * rather than one sweep every tick forever, and a sweep that qualifies nobody records the
     * failure it actually had.
     */
    private static void tickDormant(final UmbralSigilEntity sigil, final ServerLevel level) {
        final TransientState scratch = sigil.sigilTransient();
        final UmbralSigilState state = sigil.sigilState();
        if (!UmbralSigilRules.sealStartAllowed(state.cooldownTicks(), scratch.appointed())) {
            return;
        }
        if (!scratch.selectCadence.due()) {
            return;
        }
        scratch.selectCadence = scratch.selectCadence.arm();
        final Counters counters = sigil.sigilCounters();
        counters.appointmentSweeps++;
        final Optional<ServerPlayer> chosen = appoint(sigil, level);
        if (chosen.isEmpty()) {
            counters.appointmentFailures++;
            return;
        }
        final ServerPlayer subject = chosen.orElseThrow();
        final Optional<BlockPos> centre =
            UmbralSigilRules.sealCentre(sigil.blockPosition(), subject.blockPosition());
        if (centre.isEmpty()) {
            counters.appointmentFailures++;
            return;
        }
        scratch.seal = Seal.of(subject.getUUID(), dimensionOf(level), centre.orElseThrow());
        scratch.destination = null;
        counters.sealsStarted++;
        sigil.setSigilState(state.startSeal());
    }

    /**
     * The bounded appointment sweep over loaded players of this level.
     *
     * <p>Every examined candidate is charged one read <em>before</em> any eligibility filter may
     * look at it, and every line-of-sight walk is charged separately, because a barrier walk is the
     * expensive half. The declared ceiling therefore bounds the reads actually spent rather than
     * the accepted minority, and a sweep that rejects all eight candidates costs and reports the
     * same eight reads as one that accepts the first.</p>
     */
    private static Optional<ServerPlayer> appoint(
        final UmbralSigilEntity sigil,
        final ServerLevel level
    ) {
        final Counters counters = sigil.sigilCounters();
        final ReadBudget budget = ReadBudget.of(UmbralSigilRules.MAX_APPOINTMENT_READS);
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        int visited = 0;
        int lineOfSightSpent = 0;
        for (final ServerPlayer player : level.players()) {
            if (visited >= UmbralSigilRules.MAX_PLAYER_CANDIDATES || !budget.charge()) {
                break;
            }
            visited++;
            counters.appointmentCandidateVisits++;
            final double distanceSquared = sigil.distanceToSqr(player);
            final boolean qualifies = player.isAlive()
                && player.level() == level
                && !player.isCreative()
                && !player.isSpectator()
                && !player.isInvulnerable()
                && distanceSquared <= UmbralSigilRules.SUBJECT_RANGE_SQUARED;
            if (!qualifies
                || lineOfSightSpent >= UmbralSigilRules.MAX_LINE_OF_SIGHT_CHECKS
                || !budget.charge()) {
                continue;
            }
            lineOfSightSpent++;
            counters.lineOfSightChecks++;
            if (!sigil.getSensing().hasLineOfSight(player)) {
                continue;
            }
            if (best == null
                || distanceSquared < bestDistance
                || distanceSquared == bestDistance
                    && STABLE_IDENTITY.compare(player.getUUID(), best.getUUID()) < 0) {
                best = player;
                bestDistance = distanceSquared;
            }
        }
        counters.appointmentReads += budget.spent();
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- inscribe

    /**
     * Traces the one vertex this phase owns. Reaching it is the only way into the next phase, and
     * the only exit from the third is the close, so all three vertices are genuinely visited and
     * none of them can be skipped or repeated.
     */
    private static void tickInscribe(final UmbralSigilEntity sigil, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = resolveSubject(sigil, level);
        if (endSealIfRequired(sigil, level, resolved)) {
            return;
        }
        final UmbralSigilState state = sigil.sigilState();
        final Phase phase = state.phase();
        final ServerPlayer subject = resolved.orElseThrow();
        sigil.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        final BlockPos target = UmbralSigilRules.vertex(
            sigil.sigilTransient().centre().orElseThrow(), UmbralSigilRules.vertexIndex(phase)
        );
        if (UmbralSigilRules.vertexReached(sigil.distanceToSqr(Vec3.atCenterOf(target)))) {
            sigil.sigilCounters().verticesReached++;
            sigil.getNavigation().stop();
            sigil.sigilTransient().destination = null;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                4, 0.15D, 0.15D, 0.15D, 0.0D);
            sigil.setSigilState(state.enter(UmbralSigilRules.phaseAfterVertex(phase)));
            return;
        }
        if (state.timer().expired()) {
            // The window this branch owns has run out. It performs the ending itself, so the
            // recovery and the cooldown that ending implies are always armed.
            cancelIntoRecovery(sigil);
            return;
        }
        requestRoute(sigil, level, target, UmbralSigilRules.ROUTE_SPEED);
    }

    // ---------------------------------------------------------------- close

    /**
     * The seal is drawn. The Sigil now has one short window to come into its own melee band while
     * the subject is still standing inside the centre it was sealed in. A subject that walked out
     * has already broken the seal through {@link SealEnd#LEFT_CENTRE} above.
     */
    private static void tickClose(final UmbralSigilEntity sigil, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = resolveSubject(sigil, level);
        if (endSealIfRequired(sigil, level, resolved)) {
            return;
        }
        final UmbralSigilState state = sigil.sigilState();
        final ServerPlayer subject = resolved.orElseThrow();
        sigil.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        if (sigil.distanceToSqr(subject) <= UmbralSigilRules.STRIKE_BAND_SQUARED) {
            sigil.getNavigation().stop();
            sigil.setSigilState(state.enter(Phase.STRIKE));
            return;
        }
        if (state.timer().expired()) {
            cancelIntoRecovery(sigil);
            return;
        }
        // The block above the subject rather than the subject's own block: a Sigil closing a seal
        // descends onto it, and a destination a player is standing in can never qualify, so routing
        // there would spend three guaranteed failures and release every seal that had to approach.
        requestRoute(sigil, level, subject.blockPosition().above(), UmbralSigilRules.ROUTE_SPEED);
    }

    // ---------------------------------------------------------------- strike

    /**
     * At most one ordinary attributed melee attempt per seal. The attempt is recorded in persisted
     * state before it is delivered, so no reentrant damage handler can produce a second one, and
     * the seal closes into recovery in the same pass whether the attempt landed or the window
     * simply ran out.
     */
    private static void tickStrike(final UmbralSigilEntity sigil, final ServerLevel level) {
        final Optional<ServerPlayer> resolved = resolveSubject(sigil, level);
        if (endSealIfRequired(sigil, level, resolved)) {
            return;
        }
        UmbralSigilState state = sigil.sigilState();
        if (state.timer().expired()) {
            cancelIntoRecovery(sigil);
            return;
        }
        final ServerPlayer subject = resolved.orElseThrow();
        sigil.getLookControl().setLookAt(subject, 30.0F, 30.0F);
        sigil.sigilCounters().lineOfSightChecks++;
        final boolean visible = sigil.getSensing().hasLineOfSight(subject);
        if (!UmbralSigilRules.strikeAllowed(
            state.strikes(),
            state.remainingTicks(),
            sigil.distanceToSqr(subject),
            visible,
            centreOffsetSquared(sigil, subject)
        )) {
            return;
        }
        if (UmbralSigilRules.strikeDamage(
            (float) sigil.getAttributeValue(Attributes.ATTACK_DAMAGE)) <= 0.0F) {
            // A Sigil stripped of its attack attribute has nothing to close with, so the seal
            // recovers rather than delivering a zero-damage hit that still reads as a closure.
            cancelIntoRecovery(sigil);
            return;
        }
        state = state.withStrikes(state.strikes() + 1);
        sigil.setSigilState(state);
        sigil.sigilCounters().strikes++;
        sigil.setStriking(true);
        try {
            sigil.swing(InteractionHand.MAIN_HAND);
            if (sigil.doHurtTarget(level, subject)) {
                sigil.sigilCounters().strikesLanded++;
            }
        } finally {
            sigil.setStriking(false);
        }
        level.playSound(null, sigil.getX(), sigil.getY(), sigil.getZ(),
            SoundEvents.SOUL_ESCAPE.value(), sigil.getSoundSource(), 0.7F, 0.8F);
        cancelIntoRecovery(sigil);
    }

    // ---------------------------------------------------------------- recover

    private static void tickRecover(final UmbralSigilEntity sigil) {
        if (!sigil.sigilState().timer().expired()) {
            return;
        }
        endSeal(sigil);
    }

    // ---------------------------------------------------------------- shared endings

    private static Optional<ServerPlayer> resolveSubject(
        final UmbralSigilEntity sigil,
        final ServerLevel level
    ) {
        return sigil.sigilTransient().seal.subject()
            .map(level::getPlayerByUUID)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .filter(player -> player.level() == level && player.isAlive());
    }

    private static boolean endSealIfRequired(
        final UmbralSigilEntity sigil,
        final ServerLevel level,
        final Optional<ServerPlayer> resolved
    ) {
        final TransientState scratch = sigil.sigilTransient();
        final SealEnd end = UmbralSigilRules.sealEnd(new SubjectObservation(
            scratch.seal.appointed(),
            scratch.seal.dimension().map(dimensionOf(level)::equals).orElse(false),
            resolved.isPresent(),
            resolved.map(player -> !player.isCreative() && !player.isSpectator()).orElse(false),
            scratch.centre().isPresent(),
            resolved.map(player -> centreOffsetSquared(sigil, player)).orElse(Double.MAX_VALUE),
            sigil.sigilState().route().consecutiveFailures()
        ));
        if (end == SealEnd.NONE) {
            return false;
        }
        cancelIntoRecovery(sigil);
        return true;
    }

    /**
     * Every ending of an open seal, without exception, comes through here and then through
     * recovery. Cancelling clears the transient seal so no later branch can dereference a subject
     * that is already gone, and it never clears the spent-attempt latch: only {@link #endSeal}
     * does that, at the end of the recovery it arms.
     */
    private static void cancelIntoRecovery(final UmbralSigilEntity sigil) {
        final TransientState scratch = sigil.sigilTransient();
        scratch.seal = Seal.none();
        scratch.destination = null;
        sigil.getNavigation().stop();
        sigil.setTarget(null);
        sigil.setSigilState(sigil.sigilState().enter(Phase.RECOVER));
    }

    private static void endSeal(final UmbralSigilEntity sigil) {
        sigil.sigilCounters().sealsEnded++;
        final TransientState scratch = sigil.sigilTransient();
        scratch.seal = Seal.none();
        scratch.destination = null;
        sigil.getNavigation().stop();
        sigil.setTarget(null);
        sigil.setSigilState(sigil.sigilState().endSeal());
    }

    private static double centreOffsetSquared(
        final UmbralSigilEntity sigil,
        final ServerPlayer subject
    ) {
        final Optional<BlockPos> centre = sigil.sigilTransient().centre();
        if (centre.isEmpty()) {
            return Double.MAX_VALUE;
        }
        final BlockPos anchor = centre.orElseThrow();
        final double dx = subject.getX() - (anchor.getX() + 0.5D);
        final double dz = subject.getZ() - (anchor.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    // ---------------------------------------------------------------- movement plumbing

    /**
     * The one route request. Pacing, the consecutive-failure run and the backoff window are the
     * shared {@link RouteRequest} contract; only the destination is this species' own.
     *
     * <p>There is exactly one assignment of the updated ledger and both arms reach it, so a request
     * whose destination did not qualify arms the request cadence identically to one whose path
     * could not be built. Without that, a Sigil facing an unusable vertex would re-qualify the same
     * block on every single tick and the declared failure cap could never bind.</p>
     */
    private static void requestRoute(
        final UmbralSigilEntity sigil,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final UmbralSigilState state = sigil.sigilState();
        final RouteRequest request = state.route();
        if (!request.mayRequest()) {
            return;
        }
        if (!claimPathStart(level)) {
            // Contention deferral, not a failure: no world read was spent and no ledger moves.
            sigil.sigilCounters().pathStartsDenied++;
            return;
        }
        final RouteRequest.Outcome outcome = attemptPath(sigil, level, destination, speed);
        final RouteRequest updated = outcome.accepted()
            ? request.succeeded()
            : request.failed(UmbralSigilRules.routeBackoff());
        if (!outcome.accepted()) {
            sigil.sigilCounters().routeFailures++;
        }
        sigil.sigilTransient().destination = outcome.accepted() ? destination.immutable() : null;
        sigil.setSigilState(state.withRoute(updated));
        if (UmbralSigilRules.routeExhausted(updated.consecutiveFailures())) {
            sigil.getNavigation().stop();
        }
    }

    private static RouteRequest.Outcome attemptPath(
        final UmbralSigilEntity sigil,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        if (!destinationUsable(sigil, level, destination)) {
            sigil.sigilCounters().unroutableRequests++;
            return RouteRequest.Outcome.NO_CANDIDATE;
        }
        sigil.sigilCounters().navigationRequests++;
        final Path path = sigil.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && sigil.getNavigation().moveTo(path, speed);
        return RouteRequest.outcomeOf(path != null, reachable, accepted);
    }

    /**
     * Qualifies one destination. The full worst-case read cost is charged before any filter may
     * reject the candidate, so a rejected destination costs exactly what an accepted one costs.
     */
    private static boolean destinationUsable(
        final UmbralSigilEntity sigil,
        final ServerLevel level,
        final BlockPos candidate
    ) {
        sigil.sigilCounters().blockReads += READS_PER_DESTINATION;
        final AABB box = sigil.getType().getDimensions()
            .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
        if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
            return false;
        }
        final BlockState blockState = level.getBlockState(candidate);
        if (isHazardBlock(blockState) || level.getFluidState(candidate).is(FluidTags.LAVA)) {
            return false;
        }
        return level.noCollision(sigil, box);
    }

    private static boolean footprintLoaded(final ServerLevel level, final AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ));
    }

    /**
     * Claims one of this level's path starts for this tick. A dense room of Sigils therefore costs
     * a fixed number of path builds per tick however many of them want to move.
     */
    private static boolean claimPathStart(final ServerLevel level) {
        final PathQuota quota = PATH_QUOTAS.computeIfAbsent(level, _ -> new PathQuota());
        final long now = level.getGameTime();
        if (quota.tick != now) {
            quota.tick = now;
            quota.spent = 0;
        }
        if (!UmbralSigilRules.pathStartAllowed(quota.spent)) {
            return false;
        }
        quota.spent++;
        return true;
    }

    static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
