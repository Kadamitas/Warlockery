package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RouteRequestTest {

    private static final RouteRequest.RouteBackoff POLICY =
        new RouteRequest.RouteBackoff(2, 20, 200);

    @Test
    void anAcceptedRouteClearsTheFailureRunAndTheBackoff() {
        final RouteRequest failed = RouteRequest.every(10).failed(POLICY).failed(POLICY);
        assertEquals(2, failed.consecutiveFailures());
        assertTrue(failed.backoffRemaining() > 0);
        final RouteRequest recovered = failed.succeeded();
        assertEquals(0, recovered.consecutiveFailures());
        assertEquals(0, recovered.backoffRemaining());
    }

    @Test
    void backoffGrowsGeometricallyAndClampsAtTheMaximum() {
        assertEquals(0, POLICY.windowAfter(0));
        assertEquals(0, POLICY.windowAfter(1));
        assertEquals(20, POLICY.windowAfter(2));
        assertEquals(40, POLICY.windowAfter(3));
        assertEquals(80, POLICY.windowAfter(4));
        assertEquals(160, POLICY.windowAfter(5));
        assertEquals(200, POLICY.windowAfter(6));
        assertEquals(200, POLICY.windowAfter(400), "growth cannot overflow past the maximum");
    }

    @Test
    void requestsAreBlockedWhileBackoffRuns() {
        RouteRequest request = RouteRequest.every(1).failed(POLICY).failed(POLICY);
        assertFalse(request.mayRequest());
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(request.mayRequest(), "still backing off at tick " + tick);
            request = request.step();
        }
        assertTrue(request.mayRequest());
    }

    @Test
    void theCadencePacesRequestsWhenNothingHasFailed() {
        RouteRequest request = RouteRequest.every(5).succeeded();
        assertFalse(request.mayRequest());
        for (int tick = 0; tick < 5; tick++) {
            request = request.step();
        }
        assertTrue(request.mayRequest());
    }

    @Test
    void theThreeBooleanDialectMapsOntoTheOutcomes() {
        assertEquals(RouteRequest.Outcome.NO_PATH, RouteRequest.outcomeOf(false, false, false));
        assertEquals(RouteRequest.Outcome.UNREACHABLE, RouteRequest.outcomeOf(true, false, false));
        assertEquals(RouteRequest.Outcome.REFUSED, RouteRequest.outcomeOf(true, true, false));
        assertEquals(RouteRequest.Outcome.ACCEPTED, RouteRequest.outcomeOf(true, true, true));
    }

    @Test
    void anAttemptThatIsNotDueRunsNoSearchAtAll() {
        final AtomicInteger searches = new AtomicInteger();
        final RouteRequest request = RouteRequest.every(10).succeeded();
        final RouteRequest.Attempt<String> attempt = request.attempt(POLICY, () -> {
            searches.incrementAndGet();
            return Optional.of("somewhere");
        }, _ -> RouteRequest.Outcome.ACCEPTED);
        assertEquals(0, searches.get(), "an undue request costs no search");
        assertEquals(0, attempt.request().consecutiveFailures(), "and counts as no failure");
    }

    /**
     * The historical defect, and the one the brief says every family got wrong.
     *
     * <p>LostSoulRuntime and SpiritRuntime carry a {@code recordUnroutableSearch} for the case where
     * the sweep qualifies nothing, duplicated down to its javadoc. BansheeRuntime.searchAndRoute has
     * no such method: it simply returns false when the destination is empty, leaving the cadence
     * unarmed and the failure uncounted, so a Banshee in unusable terrain re-runs its entire
     * envelope sweep on every single tick forever.</p>
     */
    @Test
    void redASearchThatQualifiesNothingStillArmsTheCadenceAndCountsTheFailure() {
        final AtomicInteger sweeps = new AtomicInteger();

        // The defective shape, reproduced: the empty branch returns before arming anything.
        Cadence defectiveCadence = Cadence.every(10);
        int defectiveSweeps = 0;
        for (int tick = 0; tick < 60; tick++) {
            if (defectiveCadence.due()) {
                defectiveSweeps++;
                final Optional<String> found = Optional.empty();
                if (found.isEmpty()) {
                    continue;
                }
                defectiveCadence = defectiveCadence.arm();
            }
            defectiveCadence = defectiveCadence.step();
        }
        assertEquals(60, defectiveSweeps,
            "the defective version swept on every one of the sixty ticks");

        // The primitive over the same sixty ticks with the same fruitless search.
        RouteRequest request = RouteRequest.every(10);
        for (int tick = 0; tick < 60; tick++) {
            final RouteRequest.Attempt<String> attempt = request.attempt(POLICY, () -> {
                sweeps.incrementAndGet();
                return Optional.empty();
            }, _ -> RouteRequest.Outcome.ACCEPTED);
            request = attempt.request();
            assertEquals(RouteRequest.Outcome.NO_CANDIDATE, attempt.outcome());
        }
        assertTrue(sweeps.get() <= 4,
            "the cadence and the backoff throttled it to " + sweeps.get() + " sweeps, not sixty");
        assertTrue(request.consecutiveFailures() >= 2,
            "and the fruitless searches were recorded as failures");
        assertTrue(request.backoffRemaining() > 0, "which armed the backoff");
    }

    @Test
    void redAFailedRouteAlsoArmsRatherThanRetryingImmediately() {
        final AtomicInteger attempts = new AtomicInteger();
        RouteRequest request = RouteRequest.every(4);
        for (int tick = 0; tick < 40; tick++) {
            request = request.attempt(POLICY, () -> {
                attempts.incrementAndGet();
                return Optional.of("destination");
            }, _ -> RouteRequest.Outcome.UNREACHABLE).request();
        }
        assertTrue(attempts.get() < 10,
            "an unreachable destination backs off instead of being re-requested every tick");
        assertTrue(request.consecutiveFailures() >= 2);
    }
}
