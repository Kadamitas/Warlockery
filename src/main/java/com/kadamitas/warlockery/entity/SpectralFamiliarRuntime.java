package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Decision;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Facts;
import com.kadamitas.warlockery.entity.SpectralFamiliarRules.Phase;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.util.DataParsing;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one tick authority for a spectral familiar.
 *
 * <p>Exactly one decision is executed per tick and exactly one writer touches the move control and
 * the target per tick. The generic profiled companion layer, the generic tactical layer and the
 * generic ambient layer are all reached and declined by {@link SpectralFamiliarEntity}, so the four
 * writers that competed at 1.4 are one writer here.</p>
 *
 * <p>Movement is the move control, never the path navigation, and that is forced rather than
 * stylistic. A {@code Vex} keeps {@code GroundPathNavigation} and flies with {@code setNoGravity},
 * and {@code GroundPathNavigation.canUpdatePath()} is {@code onGround() || isInLiquid() ||
 * isPassenger()} - false for a permanently airborne body on every tick - so
 * {@code PathNavigation.followThePath()} never runs and a requested path never advances past its
 * first node. Steering the move control directly is what actually moves this chassis.</p>
 *
 * <p>No stream, no lambda capture and no boxing appears in anything reachable from {@link #tick}.
 * The survey's candidate list is the one allocation, it is bounded by the candidate cap, and it is
 * built only on the surveys that actually run.</p>
 */
public final class SpectralFamiliarRuntime {

    private SpectralFamiliarRuntime() {
    }

    /**
     * Live observation counters.
     *
     * <p>Every counter here has a real increment site in this file or in {@link
     * SpectralFamiliarEntity}, and {@code SpectralFamiliarContractTest} enforces that structurally
     * rather than by hand. There was a fifteenth, {@code worldEdits}, whose zero was asserted ten
     * times across this family's and F23's fixtures and which was incremented nowhere at all: an
     * assertion on a counter that cannot move is an assertion that cannot fail. It is gone, and the
     * claim it pretended to prove - that a spectral familiar never places, breaks or marks a block
     * in any phase - is proven instead by comparing the arena's real block states before and
     * after.</p>
     */
    public static final class Counters {
        long decisions;
        long surveys;
        long surveyBlockReads;
        long surveyCandidatesInspected;
        long episodesOpened;
        long episodesCompleted;
        long episodesAbandoned;
        long signalsEmitted;
        long defenceLeases;
        long meleeOpportunities;
        long driftRequests;
        long recalls;
        long auraPulses;
        long genericLayersDeclined;

        public long decisions() { return decisions; }
        public long surveys() { return surveys; }
        public long surveyBlockReads() { return surveyBlockReads; }
        public long surveyCandidatesInspected() { return surveyCandidatesInspected; }
        public long episodesOpened() { return episodesOpened; }
        public long episodesCompleted() { return episodesCompleted; }
        public long episodesAbandoned() { return episodesAbandoned; }
        public long signalsEmitted() { return signalsEmitted; }
        public long defenceLeases() { return defenceLeases; }
        public long meleeOpportunities() { return meleeOpportunities; }
        public long driftRequests() { return driftRequests; }
        public long recalls() { return recalls; }
        public long auraPulses() { return auraPulses; }

        /** Generic profiled, tactical and ambient layers reached and declined. */
        public long genericLayersDeclined() { return genericLayersDeclined; }
    }

    // ---- the one tick ----

    public static void tick(final SpectralFamiliarEntity body, final ServerLevel level) {
        final long now = level.getGameTime();
        final Optional<LivingEntity> owner = resolveOwner(body, level);
        final double ownerDistanceSquared = owner.isPresent()
            ? body.distanceToSqr(owner.orElseThrow())
            : Double.MAX_VALUE;
        final Optional<String> currentSample = sampleIdentity(body);

        advanceEpisode(body, level, now, owner.isPresent(), ownerDistanceSquared, currentSample);
        pulseOwnerAura(body, owner, now);

        final boolean defending = holdOrAcquireDefence(body, level, owner, now);
        final Facts facts = observe(body, owner, ownerDistanceSquared, currentSample,
            defending, now);
        final Decision decision = SpectralFamiliarRules.decide(facts);
        body.recordSpectralDecision(decision);
        body.spectralCounters().decisions++;
        execute(decision, body, level, owner, ownerDistanceSquared, currentSample, now);
    }

    // ---- the single exit that acts on an expiry report ----

    /**
     * The one place that turns an elapsed deadline into a transition.
     *
     * <p>{@link SpectralFamiliarState}'s constructor deliberately never does this, because ending an
     * episode is not just clearing a phase: it releases the frozen guide identity, arms the guide
     * cooldown and stamps the survey epoch. All three are paid here or nowhere.</p>
     */
    private static void advanceEpisode(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final long now,
        final boolean ownerLoaded,
        final double ownerDistanceSquared,
        final Optional<String> currentSample
    ) {
        SpectralFamiliarState state = body.spectralState();

        if (state.defenceElapsed(now)) {
            // Releasing the lease arms the window. Without that, a familiar standing beside a
            // permanently aggressive attacker retakes a lease on the very next tick, which is a
            // chain rather than one bounded intercept.
            state = state.withDefence(Optional.empty(), 0L,
                SpectralFamiliarRules.saturatingAdd(now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS));
            body.setSpectralState(state);
            body.setTarget(null);
        }

        if (!state.episodeRunning()) {
            return;
        }
        if (SpectralFamiliarRules.episodeInvalidated(
                ownerLoaded, ownerDistanceSquared, state.episodeSample(), currentSample)
            || !inGuideDimension(level, state)) {
            endEpisode(body, now, false);
            return;
        }

        switch (state.phase()) {
            case APPROACH -> {
                if (arrivedAtGuidePoint(body, state)) {
                    if (guideBlockStillQualifies(body, level, state)) {
                        body.setSpectralState(state.withPhase(Phase.SIGNAL,
                            SpectralFamiliarRules.saturatingAdd(
                                now, SpectralFamiliarRules.phaseDuration(Phase.SIGNAL))));
                    } else {
                        // The ore was mined, replaced or unloaded under the familiar mid-approach.
                        // That is an invalidation, not a discovery, and it never signals.
                        endEpisode(body, now, false);
                    }
                } else if (state.phaseElapsed(now)) {
                    endEpisode(body, now, false);
                }
            }
            case SIGNAL -> {
                if (state.phaseElapsed(now)) {
                    body.setSpectralState(state.withPhase(Phase.RETURN,
                        SpectralFamiliarRules.saturatingAdd(
                            now, SpectralFamiliarRules.phaseDuration(Phase.RETURN))));
                }
            }
            case RETURN -> {
                if (SpectralFamiliarRules.insideTetherBand(ownerLoaded, ownerDistanceSquared)
                    || state.phaseElapsed(now)) {
                    endEpisode(body, now, state.signalSpent());
                }
            }
            case DORMANT -> {
                // Unreachable: guarded by episodeRunning above. Present so that adding a phase is a
                // compile error rather than a silent no-op.
            }
        }
    }

    /**
     * Ends the episode and pays, in one place, everything ending it implies: the frozen guide
     * identity is released, the phase goes dormant, the guide cooldown is armed and the epoch is
     * stamped.
     *
     * <p>An episode that ends without having signalled is a fruitless attempt and is recorded as
     * one, against the same consecutive-failure count a fruitless survey feeds. That is the only
     * failure this chassis can actually have - it is steered rather than pathed, so no individual
     * movement request can be refused - and three in a row earn the backoff window. A completed
     * episode clears the count.</p>
     */
    private static void endEpisode(
        final SpectralFamiliarEntity body,
        final long now,
        final boolean completed
    ) {
        SpectralFamiliarState next = body.spectralState().withEpisodeEnded(now,
            SpectralFamiliarRules.saturatingAdd(now, SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS));
        final var outcome = SpectralFamiliarRules.recordSurvey(
            now, completed, next.survey().consecutiveFailures());
        final int backoff = SpectralFamiliarRules.backoffTicks(outcome.consecutiveFailures());
        next = next.withSurvey(
            outcome,
            backoff > 0
                ? SpectralFamiliarRules.saturatingAdd(now, backoff)
                : next.surveyBackoffUntil()
        );
        body.setSpectralState(next);
        if (completed) {
            body.spectralCounters().episodesCompleted++;
        } else {
            body.spectralCounters().episodesAbandoned++;
        }
    }

    // ---- perception ----

    private static Facts observe(
        final SpectralFamiliarEntity body,
        final Optional<LivingEntity> owner,
        final double ownerDistanceSquared,
        final Optional<String> currentSample,
        final boolean defending,
        final long now
    ) {
        final SpectralFamiliarState state = body.spectralState();
        return new Facts(
            owner.isPresent(),
            ownerDistanceSquared,
            defending,
            state.phase(),
            currentSample.isPresent(),
            state.surveyDue(now),
            state.guideReady(now),
            state.surveyBackedOff(now)
        );
    }

    private static Optional<LivingEntity> resolveOwner(
        final SpectralFamiliarEntity body,
        final ServerLevel level
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(body);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        final Entity resolved = level.getEntity(owner.orElseThrow());
        return resolved instanceof LivingEntity living && living.isAlive()
            ? Optional.of(living)
            : Optional.empty();
    }

    /** The frozen sample contract. The identity lives in the existing behaviour-state key. */
    private static Optional<String> sampleIdentity(final SpectralFamiliarEntity body) {
        final Optional<Identifier> sample = CreatureBehaviorState.sampleBlock(body);
        return sample.isPresent() ? Optional.of(sample.orElseThrow().toString()) : Optional.empty();
    }

    /**
     * The frozen owner aura. Effect, duration, amplifier, ambient and visibility flags and the
     * twenty-tick period are byte identical to {@code CreatureBehaviorRuntime.applyOwnerAura}'s
     * {@code case FAMILIAR}; this family took over the pulse because it took over the tick, not
     * because it wanted to change it.
     */
    private static void pulseOwnerAura(
        final SpectralFamiliarEntity body,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        if (owner.isEmpty()
            || body.tickCount % AnimalFamiliarRules.AURA_PULSE_INTERVAL_TICKS != 0) {
            return;
        }
        owner.orElseThrow().addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 0, true, false));
        body.spectralCounters().auraPulses++;
    }

    // ---- the one bounded defensive lease ----

    private static boolean holdOrAcquireDefence(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        final SpectralFamiliarState held = body.spectralState();
        if (held.defenceTargetId().isPresent()) {
            return targetStillValid(level, held.defenceTargetId());
        }
        if (owner.isEmpty()) {
            return false;
        }
        final LivingEntity attacker = owner.orElseThrow().getLastHurtByMob();
        return attacker != null && acquireDefence(body, attacker, now);
    }

    /**
     * The single acquisition. Both entry points funnel here: the owner's attributed attacker each
     * tick, and a direct attributed attacker of the familiar itself through {@link
     * #onAcceptedDamage}.
     */
    private static boolean acquireDefence(
        final SpectralFamiliarEntity body,
        final LivingEntity candidate,
        final long now
    ) {
        final SpectralFamiliarState state = body.spectralState();
        if (state.defenceTargetId().isPresent() || !state.defenceReady(now)) {
            return state.defenceTargetId().isPresent();
        }
        final boolean invulnerable = candidate instanceof Player player
            && (player.isCreative() || player.isSpectator());
        if (!SpectralFamiliarRules.mayDefendAgainst(
            CreatureBehaviorState.owner(body),
            candidate.getUUID(),
            CreatureBehaviorState.owner(candidate),
            candidate.isAlive(),
            candidate == body,
            invulnerable,
            true
        )) {
            return false;
        }
        body.setSpectralState(state.withDefence(
            Optional.of(candidate.getUUID()),
            SpectralFamiliarRules.saturatingAdd(now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS),
            state.defenceCooldownUntil()
        ));
        body.spectralCounters().defenceLeases++;
        return true;
    }

    private static boolean targetStillValid(
        final ServerLevel level,
        final Optional<UUID> target
    ) {
        if (target.isEmpty()) {
            return false;
        }
        return level.getEntity(target.orElseThrow()) instanceof LivingEntity living
            && living.isAlive();
    }

    // ---- the one bounded survey ----

    /**
     * One bounded survey of the loaded blocks for the sampled ore.
     *
     * <p>The envelope is a {@link com.kadamitas.warlockery.entity.behavior.ScanEnvelope}, so the
     * fixed near anchor is evaluated on every survey while the rotating page walks the far tail. A
     * naive raster over the same 847 offsets with a 64-read cap would spend every read inside the
     * bottom {@code dy = -3} layer, which is 121 offsets wide, and would never once evaluate the
     * familiar's own level. Every position is charged before it is looked at, so a survey through
     * solid stone costs exactly the cap rather than nothing, and the cadence is re-armed and the
     * failure recorded whether or not anything qualified.</p>
     *
     * <p>Two bounds, and they bound different things. The <em>read budget</em> bounds the traversal:
     * one charge per position, paid before the block is looked at, and the loop stops when it is
     * spent. The <em>candidate cap</em> bounds only the per-candidate description work that follows
     * a match. Letting the cap bound the traversal instead is the innermost-ring defect wearing this
     * family's clothes, and it was live here until it was measured: with every loaded position made
     * a candidate, the cap filled from the near anchor on every single survey and no offset past the
     * twelfth was ever inspected, however faithfully the cursor rotated.</p>
     */
    private static void survey(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final Optional<String> currentSample,
        final long now
    ) {
        final var envelope = SpectralFamiliarRules.SURVEY_ENVELOPE;
        if (body.surveyCursor() == SpectralFamiliarEntity.UNSEEDED_CURSOR) {
            body.setSurveyCursor(
                envelope.seedCursor(body.getUUID(), SpectralFamiliarRules.SURVEY_READ_CAP));
        }
        final Optional<Block> sampled = resolveSampledBlock(currentSample);
        final ReadBudget budget = ReadBudget.of(SpectralFamiliarRules.SURVEY_READ_CAP);
        final List<BlockPos> window =
            envelope.window(SpectralFamiliarRules.SURVEY_READ_CAP, body.surveyCursor());
        final BlockPos origin = body.blockPosition();
        final List<AnimalFamiliarRules.HomeCandidate> candidates =
            new ArrayList<>(SpectralFamiliarRules.SURVEY_CANDIDATE_CAP);

        for (int index = 0; index < window.size(); index++) {
            if (!budget.charge()) {
                break;
            }
            body.spectralCounters().surveyBlockReads++;
            final BlockPos position = origin.offset(window.get(index));
            if (!level.isLoaded(position)) {
                // Never load a chunk to answer a survey. The 1.4 guidance streamed 10,625 positions
                // through Level.getBlockState, which does load.
                continue;
            }
            final BlockState here = level.getBlockState(position);
            if (sampled.isEmpty() || !here.is(sampled.orElseThrow())) {
                // Charged and read, and only then rejected. The READ BUDGET is what bounds the
                // search, and it has already been paid for this position whatever the block turns
                // out to be.
                continue;
            }
            if (candidates.size() >= SpectralFamiliarRules.SURVEY_CANDIDATE_CAP) {
                // The candidate cap bounds the per-candidate description work - the distance, the
                // habitat tag and the reachability read - and nothing else. It must never bound the
                // traversal itself: collecting the first twelve LOADED positions as candidates,
                // rather than the first twelve MATCHING ones, spent the cap inside the fixed near
                // anchor on every survey, so the twelve blocks the familiar was already touching
                // were the only blocks it could ever discover and the whole 847-offset envelope was
                // charged, counted and then never inspected. Live proof of the old shape: an ore
                // three squared away, inside the anchor that every survey reads, opened no episode.
                continue;
            }
            candidates.add(SpectralFamiliarRules.guideCandidate(
                position.asLong(),
                body.distanceToSqr(Vec3.atCenterOf(position)),
                // matchesSample: established above, which is why this position is a candidate at all
                true,
                here.is(CreatureBehaviorTags.Blocks.SPECTRAL_ORES),
                guidePointReachable(level, position, budget)
            ));
        }
        body.setSurveyCursor(
            envelope.advanceCursor(SpectralFamiliarRules.SURVEY_READ_CAP, body.surveyCursor()));

        final var selection = SpectralFamiliarRules.selectGuideBlock(candidates);
        body.spectralCounters().surveys++;
        body.spectralCounters().surveyCandidatesInspected += selection.inspected();

        final boolean qualified = selection.home().isPresent();
        final SpectralFamiliarState state = body.spectralState();
        final var outcome = SpectralFamiliarRules.recordSurvey(
            now, qualified, state.survey().consecutiveFailures());
        final int backoff = SpectralFamiliarRules.backoffTicks(outcome.consecutiveFailures());
        SpectralFamiliarState next = state.withSurvey(
            outcome,
            backoff > 0
                ? SpectralFamiliarRules.saturatingAdd(now, backoff)
                : state.surveyBackoffUntil()
        );
        if (qualified) {
            next = next.withEpisode(
                Phase.APPROACH,
                SpectralFamiliarRules.saturatingAdd(
                    now, SpectralFamiliarRules.phaseDuration(Phase.APPROACH)),
                currentSample,
                Optional.of(BlockPos.of(selection.home().orElseThrow())),
                Optional.of(level.dimension().identifier().toString())
            );
            body.spectralCounters().episodesOpened++;
        }
        body.setSpectralState(next);
    }

    private static Optional<Block> resolveSampledBlock(final Optional<String> sample) {
        if (sample.isEmpty()) {
            return Optional.empty();
        }
        final Optional<Identifier> id = DataParsing.identifier(sample.orElseThrow());
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.get(id.orElseThrow()).map(holder -> holder.value());
    }

    /**
     * Whether the position directly above the candidate is a safe, visible place to hover.
     *
     * <p>Charged against the same budget as the candidate itself, because it is a real block read.
     * If the budget is already spent the answer is no rather than an uncharged read.</p>
     */
    private static boolean guidePointReachable(
        final ServerLevel level,
        final BlockPos ore,
        final ReadBudget budget
    ) {
        final BlockPos above = ore.above();
        if (!budget.charge() || !level.isLoaded(above)) {
            return false;
        }
        final BlockState state = level.getBlockState(above);
        return state.isAir() || state.getCollisionShape(level, above).isEmpty();
    }

    private static Vec3 guidePoint(final SpectralFamiliarState state) {
        final BlockPos ore = state.guideBlock().orElseThrow();
        return new Vec3(ore.getX() + 0.5D, ore.getY() + 1.0D, ore.getZ() + 0.5D);
    }

    private static boolean arrivedAtGuidePoint(
        final SpectralFamiliarEntity body,
        final SpectralFamiliarState state
    ) {
        return state.guideBlock().isPresent()
            && body.distanceToSqr(guidePoint(state))
                <= SpectralFamiliarRules.ARRIVAL_DISTANCE_SQUARED;
    }

    private static boolean inGuideDimension(
        final ServerLevel level,
        final SpectralFamiliarState state
    ) {
        return state.guideDimension()
            .filter(level.dimension().identifier().toString()::equals)
            .isPresent();
    }

    /** One block read, on arrival only, so a mined ore never produces a discovery. */
    private static boolean guideBlockStillQualifies(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final SpectralFamiliarState state
    ) {
        final BlockPos ore = state.guideBlock().orElseThrow();
        if (!level.isLoaded(ore)) {
            return false;
        }
        final Optional<Block> sampled = resolveSampledBlock(state.episodeSample());
        final BlockState here = level.getBlockState(ore);
        body.spectralCounters().surveyBlockReads++;
        return sampled.isPresent()
            && here.is(sampled.orElseThrow())
            && here.is(CreatureBehaviorTags.Blocks.SPECTRAL_ORES);
    }

    // ---- execution: exactly one writer ----

    private static void execute(
        final Decision decision,
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final Optional<LivingEntity> owner,
        final double ownerDistanceSquared,
        final Optional<String> currentSample,
        final long now
    ) {
        switch (decision.action()) {
            case IDLE -> {
                // Deliberately nothing. An unbound or invalid familiar writes no movement, takes no
                // target and grants no aura.
            }
            case HOVER -> hover(body, owner, now);
            case DEFEND_OWNER -> defend(body, level, now);
            case TETHER_RETURN, RETURN_TO_OWNER ->
                returnToOwner(body, owner, ownerDistanceSquared, now);
            case SURVEY -> survey(body, level, currentSample, now);
            case APPROACH_GUIDE -> approach(body, now);
            case SIGNAL_FIND -> signal(body);
        }
    }

    /**
     * Ethereal station keeping. This body has no gravity and no footing, so holding position is a
     * gentle drift to a point above the owner rather than a stroll goal or a rest pose.
     */
    private static void hover(
        final SpectralFamiliarEntity body,
        final Optional<LivingEntity> owner,
        final long now
    ) {
        if (owner.isEmpty()) {
            return;
        }
        final LivingEntity target = owner.orElseThrow();
        drift(body, new Vec3(
            target.getX(),
            target.getY() + SpectralFamiliarRules.HOVER_HEIGHT,
            target.getZ()
        ), now);
    }

    private static void defend(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final long now
    ) {
        final Optional<UUID> held = body.spectralState().defenceTargetId();
        if (held.isEmpty()) {
            return;
        }
        if (!(level.getEntity(held.orElseThrow()) instanceof LivingEntity attacker)
            || !attacker.isAlive()) {
            return;
        }
        body.setTarget(attacker);
        drift(body, attacker.getEyePosition(), now);
        if (!body.getBoundingBox().intersects(attacker.getBoundingBox())) {
            return;
        }
        // The one ordinary attack opportunity this lease buys, and completion releasing the target
        // in the same breath. Counting the intersection without releasing was a real defect that
        // only live execution could find: the lease is a hundred ticks long, so a familiar that
        // reached its attacker and stayed inside its bounding box earned an opportunity on every
        // one of those ticks. One intercept means one opportunity, so completing it ends the lease
        // and arms the window, exactly as an expiring lease does.
        body.spectralCounters().meleeOpportunities++;
        body.setSpectralState(body.spectralState().withDefence(
            Optional.empty(),
            0L,
            SpectralFamiliarRules.saturatingAdd(now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS)
        ));
        body.setTarget(null);
    }

    private static void returnToOwner(
        final SpectralFamiliarEntity body,
        final Optional<LivingEntity> owner,
        final double ownerDistanceSquared,
        final long now
    ) {
        if (owner.isEmpty()) {
            return;
        }
        final LivingEntity target = owner.orElseThrow();
        if (SpectralFamiliarRules.recallRequired(true, ownerDistanceSquared)) {
            // The frozen 1.4 emergency recall, at its existing distance and with no new cadence.
            body.teleportTo(target.getX() + 1.0, target.getY(), target.getZ() + 1.0);
            body.spectralCounters().recalls++;
            return;
        }
        drift(body, new Vec3(
            target.getX(),
            target.getY() + SpectralFamiliarRules.HOVER_HEIGHT,
            target.getZ()
        ), now);
    }

    private static void approach(final SpectralFamiliarEntity body, final long now) {
        final SpectralFamiliarState state = body.spectralState();
        if (state.guideBlock().isEmpty()) {
            return;
        }
        drift(body, guidePoint(state), now);
    }

    /**
     * The single signal of an episode.
     *
     * <p>It is one self-applied glow, byte identical to the 1.4 guidance's. It exposes no
     * coordinates, creates no waypoint or outline, marks, mines, replaces or inventories nothing,
     * and it cannot repeat: {@code signalSpent} is durable, a reload clears the whole episode rather
     * than resuming it, and the phase's exit is the only thing that clears the flag.</p>
     */
    private static void signal(final SpectralFamiliarEntity body) {
        final SpectralFamiliarState state = body.spectralState();
        if (state.signalSpent()) {
            return;
        }
        body.addEffect(new MobEffectInstance(
            MobEffects.GLOWING, SpectralFamiliarRules.SIGNAL_GLOW_TICKS, 0, true, false));
        body.setSpectralState(state.withSignalSpent());
        body.spectralCounters().signalsEmitted++;
    }

    /**
     * The one movement writer. Paced, failure counted and backed off exactly like a route request,
     * because it is one: the difference is that this chassis is steered rather than pathed.
     */
    private static void drift(
        final SpectralFamiliarEntity body,
        final Vec3 destination,
        final long now
    ) {
        final SpectralFamiliarState state = body.spectralState();
        if (!SpectralFamiliarRules.mayDrift(now, state.nextDriftAt())) {
            return;
        }
        body.spectralCounters().driftRequests++;
        body.getMoveControl().setWantedPosition(
            destination.x, destination.y, destination.z, SpectralFamiliarRules.DRIFT_SPEED);
        body.setSpectralState(state.withDrift(
            SpectralFamiliarRules.saturatingAdd(now, SpectralFamiliarRules.DRIFT_INTERVAL_TICKS)));
    }

    // ---- seams the body calls ----

    /**
     * Target legality. A familiar never attacks its owner or a creature bound to its owner, and
     * without a lease it attacks nothing at all: this body carries no target-selector goal, so the
     * only target it can hold is one this runtime leased it.
     */
    public static boolean canAttack(
        final SpectralFamiliarEntity body,
        final LivingEntity target
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(body);
        if (owner.isEmpty()) {
            return true;
        }
        if (owner.orElseThrow().equals(target.getUUID())) {
            return false;
        }
        return !CreatureBehaviorState.owner(target).equals(owner);
    }

    /** A direct attributed attacker of the familiar itself opens the same one defensive lease. */
    public static void onAcceptedDamage(
        final SpectralFamiliarEntity body,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            // Unattributed irritation never becomes a target. Frozen rule, not a new one.
            return;
        }
        acquireDefence(body, attacker, level.getGameTime());
    }
}

