package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PhaseTimerTest {

    private enum Phase { PETITION, SETTLE, COOLDOWN }

    @Test
    void aPhaseRunsDownAndThenWaitsToBeEnded() {
        PhaseTimer<Phase> timer = PhaseTimer.start(Phase.PETITION, 3);
        assertEquals(3, timer.remaining());
        timer = timer.step();
        timer = timer.step();
        assertTrue(timer.running());
        assertEquals(1, timer.remaining());
        timer = timer.step();
        assertInstanceOf(PhaseTimer.Expired.class, timer);
        assertEquals(Optional.of(Phase.PETITION), timer.expiredPhase());
        assertEquals(0, timer.remaining());
    }

    @Test
    void anUnhandledExpiryStallsInsteadOfLapsingToIdle() {
        PhaseTimer<Phase> timer = PhaseTimer.start(Phase.PETITION, 1);
        for (int tick = 0; tick < 100; tick++) {
            timer = timer.step();
        }
        assertEquals(Optional.of(Phase.PETITION), timer.expiredPhase(),
            "a forgotten expiry stays visible rather than being absorbed");
        assertFalse(timer.idle());
    }

    @Test
    void theSingleExitIsTheOwningBranchCallingEndOrRestart() {
        final PhaseTimer<Phase> expired = PhaseTimer.start(Phase.PETITION, 1).step();
        assertTrue(expired.endExpired().idle());
        final PhaseTimer<Phase> next = expired.restart(Phase.COOLDOWN, 40);
        assertEquals(Optional.of(Phase.COOLDOWN), next.activePhase());
        assertEquals(40, next.remaining());
    }

    @Test
    void endingSomethingStillRunningIsRefused() {
        final PhaseTimer<Phase> running = PhaseTimer.start(Phase.SETTLE, 5);
        assertThrows(IllegalStateException.class, running::endExpired);
        assertThrows(IllegalStateException.class, () -> running.restart(Phase.COOLDOWN, 5));
        assertThrows(IllegalStateException.class, () -> PhaseTimer.none().endExpired());
        assertTrue(running.cancel().idle(), "abandoning a phase outright is still allowed");
    }

    @Test
    void aNonPositiveDurationExpiresAtOnceRatherThanBeingDropped() {
        final PhaseTimer<Phase> zero = PhaseTimer.start(Phase.PETITION, 0);
        assertEquals(Optional.of(Phase.PETITION), zero.expiredPhase());
        assertEquals(Optional.of(Phase.PETITION), PhaseTimer.start(Phase.PETITION, -9).expiredPhase());
    }

    @Test
    void anIdleTimerStaysIdleForever() {
        PhaseTimer<Phase> timer = PhaseTimer.none();
        for (int tick = 0; tick < 50; tick++) {
            timer = timer.step();
        }
        assertTrue(timer.idle());
        assertEquals(Optional.empty(), timer.activePhase());
    }

    /**
     * The historical defect, stated as a type level fact rather than a behaviour.
     *
     * <p>LostSoulState.Episode and SpiritState.Attendance both reconcile in their canonical
     * constructor: {@code if (remainingTicks <= 0) { petitionRemainingTicks = 0; ... }}, and
     * EldritchWatcherState does the same for its action. The pair the owning tick branch tests for,
     * a named phase with zero ticks left, is destroyed by the constructor before the branch can see
     * it, so the branch never runs and its cooldown, backoff or anchor clear never happens.</p>
     *
     * <p>{@link PhaseTimer.Running} refuses to be built with a non positive remainder, so that pair
     * does not exist for a constructor to normalise. There is nothing to reconcile.</p>
     */
    @Test
    void redTheReconciledPairThatDefectiveConstructorsNormalisedCannotBeBuilt() {
        assertThrows(IllegalArgumentException.class,
            () -> new PhaseTimer.Running<>(Phase.PETITION, 0),
            "a running phase with zero ticks left is the shape the defect turned on");
        assertThrows(IllegalArgumentException.class,
            () -> new PhaseTimer.Running<>(Phase.PETITION, -1));

        // What the defective record did, reproduced, so the loss is explicit.
        record DefectiveEpisode(Phase phase, int remainingTicks, int cooldownArmed) {
            DefectiveEpisode {
                if (remainingTicks <= 0) {
                    phase = null;
                }
            }
        }
        final DefectiveEpisode reconciled = new DefectiveEpisode(Phase.PETITION, 0, 0);
        assertEquals(null, reconciled.phase(),
            "the constructor erased the phase the tick branch was waiting for");

        // The same moment through the primitive keeps the phase, so the branch can run.
        final PhaseTimer<Phase> expired = PhaseTimer.start(Phase.PETITION, 1).step();
        assertEquals(Optional.of(Phase.PETITION), expired.expiredPhase());
        final PhaseTimer<Phase> armed = expired.restart(Phase.COOLDOWN, 40);
        assertEquals(Optional.of(Phase.COOLDOWN), armed.activePhase(),
            "the cooldown the defective version never armed is armed here");
    }

    /**
     * The second bug the project ruling warns about: once the reconciling constructor is removed,
     * a normaliser elsewhere can reset state the newly surviving phase depends on. Expressed here as
     * the property that stepping never touches the phase identity, so nothing downstream can observe
     * a phase changing underneath it.
     */
    @Test
    void redSteppingNeverRewritesThePhaseIdentity() {
        PhaseTimer<Phase> timer = PhaseTimer.start(Phase.SETTLE, 6);
        for (int tick = 0; tick < 20; tick++) {
            timer = timer.step();
            assertEquals(Optional.of(Phase.SETTLE), timer.activePhase(),
                "the phase survives every step, running or expired");
        }
    }
}
