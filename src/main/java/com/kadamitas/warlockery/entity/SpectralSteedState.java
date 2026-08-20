package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * The durable state one spectral steed owns.
 *
 * <p>Every schedule here is a loaded countdown, never an absolute deadline, because a mount spends
 * long stretches unloaded in a chunk nobody is standing in and must not come back with a schedule
 * that elapsed while it was gone.</p>
 *
 * <p>The canonical constructor reconciles exactly one thing, and it is the identity shape: a rest
 * position and its dimension are one fact, so losing either drops both and the cooldown that
 * belongs to a site nobody holds. It deliberately does not reconcile any timer against its
 * dependents. A balk whose timer reaches zero becomes {@link PhaseTimer.Expired} and stays there
 * until the tick branch that owns ending a balk calls {@code endExpired}, because that branch is
 * what re-arms the gait ceiling and counts the balk as finished.</p>
 */
public record SpectralSteedState(
    int schemaVersion,
    int bond,
    int fatigue,
    Gait gait,
    int gaitHold,
    PhaseTimer<Phase> phase,
    Optional<BlockPos> rest,
    Optional<String> restDimension,
    RouteRequest restRequest,
    int restCursor,
    Cadence hazardScan,
    int fearCooldown,
    int restCooldown,
    int episode,
    int bondThisEpisode,
    Counters counters
) {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_COOLDOWN_TICKS = 20_000;

    /** The phases a steed runs. Each has exactly one tick branch that ends it. */
    public enum Phase {
        /** A startle: the steed is held and accepts no steering until it passes. */
        BALK,
        /** Standing down at a rest site; completing it is the second and last way to earn bond. */
        RESTING
    }

    /**
     * Work actually performed, so every bound an assertion reads has a real increment site.
     *
     * @param restSearches rest searches begun, whether or not they qualified anything
     * @param restBlockReads block reads charged by those searches, rejected candidates included
     * @param restValidationReads block reads charged re-checking a held site, kept separate from
     *     the search so the per-search read bound stays exactly what it claims to bound
     * @param restsCompleted rest phases that ran to their end
     * @param balks startles begun
     * @param gaitChanges band transitions actually applied
     * @param bondGains ticks on which a point of bond was earned
     * @param warningVisits entities a Nightmare warning looked at, qualifying or not
     * @param warningsIssued warnings that reached at least one entity
     */
    public record Counters(
        long restSearches,
        long restBlockReads,
        long restValidationReads,
        long restNavigationStarts,
        long restsCompleted,
        long balks,
        long gaitChanges,
        long bondGains,
        long warningVisits,
        long warningTelegraphs,
        long warningsIssued
    ) {
        public static Counters none() {
            return new Counters(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        Counters plusRestSearch(final int reads) {
            return new Counters(restSearches + 1, restBlockReads + reads, restValidationReads,
                restNavigationStarts, restsCompleted, balks, gaitChanges, bondGains, warningVisits,
                warningTelegraphs, warningsIssued);
        }

        Counters plusRestValidation(final int reads) {
            return new Counters(restSearches, restBlockReads, restValidationReads + reads,
                restNavigationStarts, restsCompleted, balks, gaitChanges, bondGains, warningVisits,
                warningTelegraphs, warningsIssued);
        }

        Counters plusRestNavigationStart() {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts + 1, restsCompleted, balks, gaitChanges, bondGains,
                warningVisits, warningTelegraphs, warningsIssued);
        }

        Counters plusRestCompleted() {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts, restsCompleted + 1, balks, gaitChanges, bondGains,
                warningVisits, warningTelegraphs, warningsIssued);
        }

        Counters plusBalk() {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts, restsCompleted, balks + 1, gaitChanges, bondGains,
                warningVisits, warningTelegraphs, warningsIssued);
        }

        Counters plusGaitChange() {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts, restsCompleted, balks, gaitChanges + 1, bondGains,
                warningVisits, warningTelegraphs, warningsIssued);
        }

        Counters plusBondGain() {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts, restsCompleted, balks, gaitChanges, bondGains + 1,
                warningVisits, warningTelegraphs, warningsIssued);
        }

        Counters plusWarning(final int visits, final int recipients) {
            return new Counters(restSearches, restBlockReads, restValidationReads,
                restNavigationStarts, restsCompleted, balks, gaitChanges, bondGains,
                warningVisits + visits, warningTelegraphs + (recipients > 0 ? 1 : 0),
                warningsIssued + (recipients > 0 ? 1 : 0));
        }
    }

    public SpectralSteedState {
        gait = Objects.requireNonNull(gait, "gait");
        phase = Objects.requireNonNull(phase, "phase");
        rest = Objects.requireNonNull(rest, "rest").map(BlockPos::immutable);
        restDimension = Objects.requireNonNull(restDimension, "restDimension");
        restRequest = Objects.requireNonNull(restRequest, "restRequest");
        hazardScan = Objects.requireNonNull(hazardScan, "hazardScan");
        counters = Objects.requireNonNull(counters, "counters");
        bond = Math.clamp(bond, 0, SpectralSteedRules.MAX_BOND);
        fatigue = Math.clamp(fatigue, 0, SpectralSteedRules.MAX_FATIGUE);
        gaitHold = Ticks.clampRemaining(gaitHold, SpectralSteedRules.GAIT_HOLD_TICKS);
        fearCooldown = Ticks.clampRemaining(fearCooldown, MAX_COOLDOWN_TICKS);
        restCooldown = Ticks.clampRemaining(restCooldown, MAX_COOLDOWN_TICKS);
        episode = Math.max(0, episode);
        restCursor = Math.max(0, restCursor);
        bondThisEpisode = Math.clamp(bondThisEpisode, 0, SpectralSteedRules.MAX_BOND_PER_EPISODE);
        // The one reconciliation, and it is an identity: a site without its dimension is not a
        // site, and a cooldown that belongs to a site nobody holds is not a cooldown.
        if (rest.isEmpty() || restDimension.isEmpty()) {
            rest = Optional.empty();
            restDimension = Optional.empty();
        }
    }

    public static SpectralSteedState empty() {
        return new SpectralSteedState(
            SCHEMA_VERSION, 0, 0, Gait.HALT, 0, PhaseTimer.none(),
            Optional.empty(), Optional.empty(),
            RouteRequest.every(SpectralSteedRules.REST_SEARCH_INTERVAL_TICKS),
            0,
            Cadence.every(SpectralSteedRules.HAZARD_SCAN_INTERVAL_TICKS),
            0, 0, 0, 0, Counters.none()
        );
    }

    public boolean balking() {
        return phase.activePhase().filter(Phase.BALK::equals).isPresent();
    }

    public boolean resting() {
        return phase.activePhase().filter(Phase.RESTING::equals).isPresent();
    }

    /** One loaded tick of every countdown this state owns. Nothing here ends anything. */
    public SpectralSteedState step() {
        return with(bond, fatigue, gait, Ticks.decrementLoaded(gaitHold), phase.step(),
            rest, restDimension, restRequest.step(), restCursor, hazardScan.step(),
            Ticks.decrementLoaded(fearCooldown), Ticks.decrementLoaded(restCooldown),
            episode, bondThisEpisode, counters);
    }

    public SpectralSteedState withBond(final int updated) {
        return with(updated, fatigue, gait, gaitHold, phase, rest, restDimension, restRequest, restCursor,
            hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    public SpectralSteedState withFatigue(final int updated) {
        return with(bond, updated, gait, gaitHold, phase, rest, restDimension, restRequest, restCursor,
            hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    /** Applies a band, arming the hold window and counting the change only when one happened. */
    public SpectralSteedState withGait(final Gait updated) {
        if (updated == gait) {
            return this;
        }
        return with(bond, fatigue, updated, SpectralSteedRules.GAIT_HOLD_TICKS, phase, rest,
            restDimension, restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode,
            bondThisEpisode, counters.plusGaitChange());
    }

    /**
     * Earns one point of bond. Ride-earned bond charges the per-episode accumulator; rest-earned
     * bond does not, because the rest cooldown already limits it and only a ride resets the
     * accumulator.
     */
    public SpectralSteedState withBondGain(final int amount, final boolean chargesEpisode) {
        if (amount <= 0) {
            return this;
        }
        return with(bond + amount, fatigue, gait, gaitHold, phase, rest, restDimension,
            restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode,
            chargesEpisode ? bondThisEpisode + amount : bondThisEpisode, counters.plusBondGain());
    }

    public SpectralSteedState startingBalk(final int ticks) {
        return with(bond, fatigue, gait, gaitHold, PhaseTimer.start(Phase.BALK, ticks), rest,
            restDimension, restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode,
            bondThisEpisode, counters.plusBalk());
    }

    public SpectralSteedState startingRest(final int ticks) {
        return with(bond, fatigue, gait, gaitHold, PhaseTimer.start(Phase.RESTING, ticks), rest,
            restDimension, restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode,
            bondThisEpisode, counters);
    }

    public SpectralSteedState withPhase(final PhaseTimer<Phase> updated) {
        return with(bond, fatigue, gait, gaitHold, updated, rest, restDimension, restRequest, restCursor,
            hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    /** Records that a rest ran to its end, arming the cooldown the ending implies. */
    public SpectralSteedState withRestCompleted() {
        return with(bond, fatigue, gait, gaitHold, PhaseTimer.none(), rest, restDimension,
            restRequest, restCursor, hazardScan, fearCooldown, SpectralSteedRules.REST_COOLDOWN_TICKS,
            episode, bondThisEpisode, counters.plusRestCompleted());
    }

    public SpectralSteedState withRest(
        final Optional<BlockPos> position,
        final Optional<String> dimension
    ) {
        return with(bond, fatigue, gait, gaitHold, phase, position, dimension, restRequest, restCursor,
            hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    public SpectralSteedState withRestRequest(final RouteRequest updated) {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension, updated, restCursor,
            hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    /**
     * Records one rest search: the reads it actually charged, and the cursor the next search must
     * use so the far envelope is reached instead of the same near ring every time.
     */
    public SpectralSteedState withRestSearchCharged(final int reads, final int nextCursor) {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension, restRequest,
            nextCursor, hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode,
            counters.plusRestSearch(reads));
    }

    /**
     * Records the reads one re-check of a held rest site actually charged.
     *
     * <p>It is charged whether the site survived the check or not, because a site that has just
     * been lost cost exactly the same lookups as one that is still good. Recording it only on the
     * surviving branch would be the "budget charged on success" shape that makes a declared bound
     * unable to bind.</p>
     */
    public SpectralSteedState withRestValidationCharged(final int reads) {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension, restRequest,
            restCursor, hazardScan, fearCooldown, restCooldown, episode, bondThisEpisode,
            counters.plusRestValidation(reads));
    }

    public SpectralSteedState withRestNavigationStarted() {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension,
            restRequest.succeeded(), restCursor, hazardScan, fearCooldown, restCooldown, episode,
            bondThisEpisode, counters.plusRestNavigationStart());
    }

    public SpectralSteedState withHazardScan(final Cadence updated) {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension, restRequest, restCursor,
            updated, fearCooldown, restCooldown, episode, bondThisEpisode, counters);
    }

    public SpectralSteedState withWarningIssued(final int visits, final int recipients) {
        return with(bond, fatigue, gait, gaitHold, phase, rest, restDimension, restRequest, restCursor,
            hazardScan, recipients > 0 ? SpectralSteedRules.FEAR_COOLDOWN_TICKS : fearCooldown,
            restCooldown, episode, bondThisEpisode, counters.plusWarning(visits, recipients));
    }

    /**
     * Begins a ride.
     *
     * <p>Per-episode accumulators reset here and the transient band and phase are dropped, but the
     * rest request keeps its consecutive failures and any open backoff window. A steed that has just
     * failed three rest searches in unusable terrain must not get a fresh allowance simply because
     * somebody climbed on and off again.</p>
     */
    public SpectralSteedState episodeStart() {
        return with(bond, fatigue, Gait.HALT, 0, PhaseTimer.none(), rest, restDimension,
            restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode + 1, 0, counters);
    }

    /** Ends a ride, leaving the steed halted and un-phased but keeping every durable fact. */
    public SpectralSteedState episodeEnd() {
        return with(bond, fatigue, Gait.HALT, 0, PhaseTimer.none(), rest, restDimension,
            restRequest, restCursor, hazardScan, fearCooldown, restCooldown, episode, 0, counters);
    }

    public CompoundTag write(final CreatureKind kind) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putString("Kind", kind.name().toLowerCase(Locale.ROOT));
        tag.putInt("Bond", bond);
        tag.putInt("Fatigue", fatigue);
        tag.putString("Gait", gait.name().toLowerCase(Locale.ROOT));
        tag.putInt("GaitHold", gaitHold);
        tag.putInt("FearCooldown", fearCooldown);
        tag.putInt("RestCooldown", restCooldown);
        tag.putInt("Episode", episode);
        tag.putInt("RestFailures", restRequest.consecutiveFailures());
        tag.putInt("RestBackoff", restRequest.backoffRemaining());
        tag.putInt("RestCursor", restCursor);
        rest.ifPresent(position -> tag.putLong("Rest", position.asLong()));
        restDimension.ifPresent(dimension -> tag.putString("RestDimension", dimension));
        return tag;
    }

    /**
     * Reads durable facts and starts every transient one over.
     *
     * <p>A stored kind that is not this steed's kind keeps only bond and fatigue, which are mount
     * maturity rather than species, and drops every kind-shaped fact: the site a Nightmare chose and
     * the cooldown of a warning a Pale Steed cannot issue are not inheritable. Identity itself is
     * never converted; the kind comes from the registered type and this only decides what of the
     * stored data may be believed.</p>
     *
     * <p>Band, hold window, phase, per-episode bond credit and the hazard cadence are all dropped:
     * a steed must not come back mid-canter, mid-balk or mid-rest, and no bond, fatigue or warning
     * may be credited for time that merely elapsed. The rest site, its cooldown and the rest
     * request's failure run and backoff window do survive, because they describe the world rather
     * than the moment.</p>
     */
    public static SpectralSteedState read(final CompoundTag tag, final CreatureKind kind) {
        if (!SpectralSteedRules.isSteed(kind)) {
            throw new IllegalArgumentException("not a spectral steed: " + kind);
        }
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final boolean sameKind =
            kind.name().equalsIgnoreCase(tag.getStringOr("Kind", kind.name()));
        final Optional<BlockPos> rest = tag.getLongOr("Rest", Long.MIN_VALUE) == Long.MIN_VALUE
            ? Optional.empty()
            : Optional.of(BlockPos.of(tag.getLongOr("Rest", 0L)));
        final String storedDimension = tag.getStringOr("RestDimension", "");
        final Optional<String> restDimension =
            storedDimension.isEmpty() || Identifier.tryParse(storedDimension) == null
                ? Optional.empty()
                : Optional.of(storedDimension);
        final int backoff = Ticks.clampRemaining(tag.getIntOr("RestBackoff", 0), MAX_COOLDOWN_TICKS);
        final RouteRequest restRequest = new RouteRequest(
            Cadence.every(SpectralSteedRules.REST_SEARCH_INTERVAL_TICKS),
            Math.max(0, tag.getIntOr("RestFailures", 0)),
            backoff
        );
        return new SpectralSteedState(
            SCHEMA_VERSION,
            tag.getIntOr("Bond", 0),
            tag.getIntOr("Fatigue", 0),
            Gait.HALT,
            0,
            PhaseTimer.none(),
            sameKind ? rest : Optional.empty(),
            sameKind ? restDimension : Optional.empty(),
            sameKind
                ? restRequest
                : RouteRequest.every(SpectralSteedRules.REST_SEARCH_INTERVAL_TICKS),
            sameKind ? Math.max(0, tag.getIntOr("RestCursor", 0)) : 0,
            Cadence.every(SpectralSteedRules.HAZARD_SCAN_INTERVAL_TICKS),
            0,
            sameKind ? tag.getIntOr("RestCooldown", 0) : 0,
            Math.max(0, tag.getIntOr("Episode", 0)),
            0,
            Counters.none()
        );
    }

    private SpectralSteedState with(
        final int newBond,
        final int newFatigue,
        final Gait newGait,
        final int newGaitHold,
        final PhaseTimer<Phase> newPhase,
        final Optional<BlockPos> newRest,
        final Optional<String> newRestDimension,
        final RouteRequest newRestRequest,
        final int newRestCursor,
        final Cadence newHazardScan,
        final int newFearCooldown,
        final int newRestCooldown,
        final int newEpisode,
        final int newBondThisEpisode,
        final Counters newCounters
    ) {
        return new SpectralSteedState(SCHEMA_VERSION, newBond, newFatigue, newGait, newGaitHold,
            newPhase, newRest, newRestDimension, newRestRequest, newRestCursor, newHazardScan,
            newFearCooldown,
            newRestCooldown, newEpisode, newBondThisEpisode, newCounters);
    }
}

