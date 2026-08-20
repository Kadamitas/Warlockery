package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Poltergeist semantics. Exactly one disturbance episode may exist and
 * it owns exactly one lift, one velocity write, one hit, one bell ring and one recovery. Every
 * duration is a remaining loaded-active tick count, never an absolute world deadline, so unloading
 * pauses meaning instead of expiring it. No path, live entity reference, item stack, collection or
 * client fact is ever stored: the marked target and the chosen prop are transient by design and are
 * reacquired rather than replayed.
 */
public record PoltergeistState(
    int schemaVersion,
    Phase phase,
    Episode episode,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public PoltergeistState {
        phase = Objects.requireNonNull(phase, "phase");
        episode = Objects.requireNonNull(episode, "episode");
        cadence = Objects.requireNonNull(cadence, "cadence");
    }

    /**
     * The one disturbance episode. This canonical constructor performs range clamping only: every
     * field is bounded independently and no field is ever derived from another.
     *
     * <p>It deliberately does not carry the timer-shaped reconcile
     * ({@code if (remainingTicks <= 0) zero the dependents}). That shape decides that something
     * ended, which is a tick branch's job here: the recovery branch is the single episode exit and
     * it is what arms the long cooldown, clears the transient claims and lands the lifted target.
     * A constructor that zeroed the sub-counters the moment the loaded-time budget ran out would
     * also erase the spent-lift and spent-hit evidence that the surviving recovery still has to
     * observe, so a reloaded or expired episode could grant a second lift.</p>
     *
     * <p>There is deliberately no identity-shaped coupled invariant either
     * ({@code if (identity absent) zero the dependents}), because this record holds no identity to
     * couple to: the marked target and the claimed prop are transient by design and live in
     * {@link PoltergeistRuntime.TransientState}, so no two persisted fields here can disagree.</p>
     */
    public record Episode(
        int remainingTicks,
        int phaseRemainingTicks,
        int pulseRemainingTicks,
        int pulsesEmitted,
        int bellRings,
        int lifts,
        int velocityWrites,
        int hits,
        int recoveries,
        int pathRequests
    ) {
        public Episode {
            remainingTicks = PoltergeistRules.clampRemaining(
                remainingTicks, PoltergeistRules.EPISODE_TICKS
            );
            phaseRemainingTicks = PoltergeistRules.clampRemaining(
                phaseRemainingTicks, PoltergeistRules.MAX_PHASE_TICKS
            );
            pulseRemainingTicks = PoltergeistRules.clampRemaining(
                pulseRemainingTicks, PoltergeistRules.RATTLE_PULSE_INTERVAL_TICKS
            );
            pulsesEmitted = Math.clamp(pulsesEmitted, 0, PoltergeistRules.MAX_RATTLE_PULSES);
            bellRings = Math.clamp(bellRings, 0, PoltergeistRules.MAX_BELL_RINGS);
            lifts = Math.clamp(lifts, 0, PoltergeistRules.MAX_LIFTS);
            velocityWrites = Math.clamp(velocityWrites, 0, PoltergeistRules.MAX_VELOCITY_WRITES);
            hits = Math.clamp(hits, 0, PoltergeistRules.MAX_THROW_HITS);
            recoveries = Math.clamp(recoveries, 0, PoltergeistRules.MAX_RECOVERIES);
            pathRequests = Math.clamp(pathRequests, 0, PoltergeistRules.MAX_EPISODE_PATH_REQUESTS);
        }

        public static Episode none() {
            return new Episode(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        /** A fresh episode enters the rattle telegraph with its first pulse already due. */
        public static Episode started() {
            return new Episode(PoltergeistRules.EPISODE_TICKS, PoltergeistRules.RATTLE_TICKS,
                0, 0, 0, 0, 0, 0, 0, 0);
        }

        public boolean active() {
            return remainingTicks > 0;
        }

        public Episode withRemaining(final int updated) {
            return new Episode(updated, phaseRemainingTicks, pulseRemainingTicks, pulsesEmitted,
                bellRings, lifts, velocityWrites, hits, recoveries, pathRequests);
        }

        public Episode withPhaseRemaining(final int updated) {
            return new Episode(remainingTicks, updated, pulseRemainingTicks, pulsesEmitted,
                bellRings, lifts, velocityWrites, hits, recoveries, pathRequests);
        }

        public Episode withPulse(final int remainingIntervalTicks, final int emitted) {
            return new Episode(remainingTicks, phaseRemainingTicks, remainingIntervalTicks, emitted,
                bellRings, lifts, velocityWrites, hits, recoveries, pathRequests);
        }

        public Episode withBellRings(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, updated, lifts, velocityWrites, hits, recoveries, pathRequests);
        }

        public Episode withLifts(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, bellRings, updated, velocityWrites, hits, recoveries, pathRequests);
        }

        public Episode withVelocityWrites(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, bellRings, lifts, updated, hits, recoveries, pathRequests);
        }

        public Episode withHits(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, bellRings, lifts, velocityWrites, updated, recoveries, pathRequests);
        }

        public Episode withRecoveries(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, bellRings, lifts, velocityWrites, hits, updated, pathRequests);
        }

        public Episode withPathRequests(final int updated) {
            return new Episode(remainingTicks, phaseRemainingTicks, pulseRemainingTicks,
                pulsesEmitted, bellRings, lifts, velocityWrites, hits, recoveries, updated);
        }
    }

    public record Cadence(
        int cooldownTicks,
        int routeFailures,
        int routeRetryTicks
    ) {
        public Cadence {
            cooldownTicks = PoltergeistRules.clampRemaining(
                cooldownTicks, PoltergeistRules.COOLDOWN_TICKS
            );
            routeFailures = Math.clamp(routeFailures, 0, PoltergeistRules.MAX_ROUTE_FAILURES);
            routeRetryTicks = PoltergeistRules.clampRemaining(
                routeRetryTicks, PoltergeistRules.ROUTE_BACKOFF_TICKS
            );
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0);
        }
    }

    public static PoltergeistState empty() {
        return new PoltergeistState(SCHEMA_VERSION, Phase.LURK, Episode.none(), Cadence.none());
    }

    /**
     * The canonical constructor deliberately performs no phase reconciliation. Every phase here is
     * ended by exactly one tick branch in {@link PoltergeistRuntime}, and a constructor that
     * silently rewrote a phase when a timer reached zero would race those branches: an expired
     * throw window would leave THROW before {@code tickThrow} could observe the expiry, so the
     * recovery would never be entered, the lifted target would never receive its safe landing and
     * the long cooldown would never be armed. Tick dispatch is the single exit.
     */
    public PoltergeistState withPhase(final Phase updated) {
        return new PoltergeistState(schemaVersion, updated, episode, cadence);
    }

    public PoltergeistState withEpisode(final Episode updated) {
        return new PoltergeistState(schemaVersion, phase, updated, cadence);
    }

    public PoltergeistState withCadence(final Cadence updated) {
        return new PoltergeistState(schemaVersion, phase, episode, updated);
    }

    /** Enters the recovery that closes an episode. The long cooldown belongs to the recovery exit. */
    public PoltergeistState enterRecovery() {
        return withEpisode(episode.withPhaseRemaining(PoltergeistRules.RECOVER_TICKS))
            .withPhase(Phase.RECOVER);
    }

    /**
     * The single episode exit, owned by the recovery tick branch. It drops every episode fact, arms
     * the long cooldown and clears the route accounting in one state write, so no half-finished
     * disturbance is ever observable.
     */
    public PoltergeistState finishEpisode() {
        return new PoltergeistState(schemaVersion, Phase.LURK, Episode.none(),
            new Cadence(PoltergeistRules.COOLDOWN_TICKS, 0, cadence.routeRetryTicks()));
    }

    /**
     * Compact fixed-cardinality encoding. Representative populated states must encode below
     * {@link PoltergeistRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        tag.putInt("Episode", episode.remainingTicks());
        tag.putInt("PhaseTicks", episode.phaseRemainingTicks());
        tag.putInt("Pulse", episode.pulseRemainingTicks());
        tag.putInt("PulseCount", episode.pulsesEmitted());
        tag.putInt("Bells", episode.bellRings());
        tag.putInt("Lifts", episode.lifts());
        tag.putInt("Throws", episode.velocityWrites());
        tag.putInt("Hits", episode.hits());
        tag.putInt("Recoveries", episode.recoveries());
        tag.putInt("Paths", episode.pathRequests());
        tag.putInt("Cooldown", cadence.cooldownTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to a safe lurk. Every remaining duration
     * is clamped without consulting elapsed world time, and any saved attack phase resumes as the
     * recovery that closes it, so a reload can never replay a lift, a velocity write, a hit, or a
     * bell ring. The spent-work counters survive the reload precisely so the resumed recovery
     * cannot grant a second one.
     */
    public static PoltergeistState read(final CompoundTag tag) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        final Phase resumed = PoltergeistRules.phaseAfterLoad(stored);
        final int storedPhaseTicks = tag.getIntOr("PhaseTicks", 0);
        final Episode episode = new Episode(
            tag.getIntOr("Episode", 0),
            resumed == stored ? storedPhaseTicks : PoltergeistRules.phaseWindowTicks(resumed),
            tag.getIntOr("Pulse", 0),
            tag.getIntOr("PulseCount", 0),
            tag.getIntOr("Bells", 0),
            tag.getIntOr("Lifts", 0),
            tag.getIntOr("Throws", 0),
            tag.getIntOr("Hits", 0),
            tag.getIntOr("Recoveries", 0),
            tag.getIntOr("Paths", 0)
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("Cooldown", 0),
            tag.getIntOr("RouteFail", 0),
            tag.getIntOr("RouteRetry", 0)
        );
        return new PoltergeistState(SCHEMA_VERSION, resumed, episode, cadence);
    }

    private static Phase parsePhase(final String value) {
        for (final Phase candidate : Phase.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Phase.LURK;
    }
}
