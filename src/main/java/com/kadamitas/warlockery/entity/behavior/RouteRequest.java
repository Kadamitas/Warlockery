package com.kadamitas.warlockery.entity.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Route request pacing with failure driven backoff.
 *
 * <p>Eleven committed families carry this in two dialects. Four store countdowns and spell it
 * {@code pathRequestAllowed}, {@code routeFailuresAfter}, {@code routeExhausted},
 * {@code routeBackoffAfter}; seven store absolute deadlines and spell it {@code routeFailures} plus
 * {@code routeBackoffUntil}. The arithmetic is the same in both.</p>
 *
 * <p>The defect worth naming is the one case all of them handle differently and most handle wrongly:
 * a search that qualifies no candidate at all. It costs the same real work as a search that
 * succeeds, so it must arm the request cadence and count as a failure. LostSoul and Spirit have a
 * {@code recordUnroutableSearch} for exactly this, duplicated down to the javadoc. Banshee has no
 * such method, so a Banshee standing in unusable terrain re-runs its whole sweep every single tick.
 * {@link #attempt} makes that shape unwritable: the empty branch of the search lives inside the
 * primitive, so there is no place for a caller to return early without arming.</p>
 *
 * <p>All three fields are loaded tick countdowns, never absolute world time, so an unloaded entity
 * cannot come back with a stale schedule. A family that stores deadlines instead can still use
 * {@link RouteBackoff#windowAfter} on its own and keep its own storage.</p>
 *
 * @param cadence how often a request may be made
 * @param consecutiveFailures failures since the last accepted route
 * @param backoffRemaining ticks left before requests resume
 */
public record RouteRequest(Cadence cadence, int consecutiveFailures, int backoffRemaining) {

    public RouteRequest {
        if (cadence == null) {
            throw new IllegalArgumentException("a route request needs a cadence");
        }
        consecutiveFailures = Math.max(0, consecutiveFailures);
        backoffRemaining = Math.max(0, backoffRemaining);
    }

    /** What became of one route request. Everything but {@link #ACCEPTED} counts as a failure. */
    public enum Outcome {
        /** The navigator took the path. */
        ACCEPTED,
        /** The search qualified nothing. The case the families got wrong. */
        NO_CANDIDATE,
        /** A destination was chosen but no path could be built to it. */
        NO_PATH,
        /** A path was built but does not actually reach the destination. */
        UNREACHABLE,
        /** A path was built and reaches, but the navigator declined it. */
        REFUSED;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    /** How long to wait after a run of failures. Geometric growth, clamped. */
    public record RouteBackoff(int failuresBeforeBackoff, int baseTicks, int maxTicks) {

        public RouteBackoff {
            if (failuresBeforeBackoff < 1 || baseTicks < 1 || maxTicks < baseTicks) {
                throw new IllegalArgumentException(
                    "a backoff policy needs at least one failure before it engages, a positive base"
                        + " and a maximum no smaller than the base");
            }
        }

        /** Whether this many consecutive failures is enough to stop requesting for a while. */
        public boolean engagedAt(final int failures) {
            return failures >= failuresBeforeBackoff;
        }

        /** The backoff window after this many consecutive failures, zero while not engaged. */
        public int windowAfter(final int failures) {
            if (!engagedAt(failures)) {
                return 0;
            }
            final int doublings = Math.min(failures - failuresBeforeBackoff, 30);
            final long window = (long) baseTicks << doublings;
            return (int) Math.min(window, maxTicks);
        }
    }

    /** The request as it stands after an attempt, and whatever the attempt produced. */
    public record Attempt<T>(RouteRequest request, Optional<T> destination, Outcome outcome) {}

    public static RouteRequest every(final int period) {
        return new RouteRequest(Cadence.every(period), 0, 0);
    }

    /** Whether a request may be made now: the cadence is due and no backoff is running. */
    public boolean mayRequest() {
        return cadence.due() && backoffRemaining == 0;
    }

    /** Advances one loaded tick. */
    public RouteRequest step() {
        return new RouteRequest(cadence.step(), consecutiveFailures,
            Ticks.decrementLoaded(backoffRemaining));
    }

    /** Records an accepted route: cadence armed, failure run cleared, backoff dropped. */
    public RouteRequest succeeded() {
        return new RouteRequest(cadence.arm(), 0, 0);
    }

    /**
     * Records a failed route. The cadence is armed whatever the reason, which is the property the
     * hand-rolled versions lost when they armed inside the success branch.
     */
    public RouteRequest failed(final RouteBackoff policy) {
        final int failures = consecutiveFailures + 1;
        return new RouteRequest(cadence.arm(), failures, policy.windowAfter(failures));
    }

    /**
     * Runs one full request: search for a destination, then route to it, recording whichever
     * outcome occurred.
     *
     * <p>A search that qualifies nothing takes the {@link Outcome#NO_CANDIDATE} path and arms the
     * cadence exactly as a routing failure would, because the caller has no branch of its own
     * between the two.</p>
     */
    public <T> Attempt<T> attempt(
        final RouteBackoff policy,
        final Supplier<Optional<T>> search,
        final Function<T, Outcome> route
    ) {
        if (!mayRequest()) {
            return new Attempt<>(step(), Optional.empty(), Outcome.NO_CANDIDATE);
        }
        final Optional<T> destination = search.get();
        if (destination.isEmpty()) {
            return new Attempt<>(failed(policy), Optional.empty(), Outcome.NO_CANDIDATE);
        }
        final Outcome outcome = route.apply(destination.get());
        return new Attempt<>(outcome.accepted() ? succeeded() : failed(policy),
            outcome.accepted() ? destination : Optional.empty(), outcome);
    }

    /** The outcome implied by the three booleans the countdown dialect families already compute. */
    public static Outcome outcomeOf(
        final boolean pathCreated,
        final boolean reachable,
        final boolean accepted
    ) {
        if (!pathCreated) {
            return Outcome.NO_PATH;
        }
        if (!reachable) {
            return Outcome.UNREACHABLE;
        }
        return accepted ? Outcome.ACCEPTED : Outcome.REFUSED;
    }
}
