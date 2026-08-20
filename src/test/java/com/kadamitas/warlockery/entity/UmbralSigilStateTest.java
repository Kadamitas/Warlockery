package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class UmbralSigilStateTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static UmbralSigilState inPhase(final Phase phase, final int remaining) {
        return UmbralSigilState.empty().withTimer(PhaseTimer.start(phase, remaining));
    }

    @Test
    void anEmptyStateIsDormantWithAnUnspentAttemptAndNoCooldown() {
        final UmbralSigilState state = UmbralSigilState.empty();
        assertEquals(Phase.DORMANT, state.phase());
        assertEquals(0, state.remainingTicks());
        assertTrue(state.timer().idle());
        assertFalse(state.struck());
        assertEquals(0, state.cooldownTicks());
        assertEquals(0, state.route().consecutiveFailures());
        assertEquals(0, state.route().backoffRemaining());
    }

    // ---------------------------------------------------------------- the timer shape

    /**
     * The defect this state deliberately does not have. A canonical constructor that zeroed the
     * phase when the timer reached zero would destroy exactly the pair the owning tick branch
     * tests for, so the branch would never run and the recovery, cooldown and latch clear it owns
     * would silently never happen. Here a spent phase becomes {@link PhaseTimer.Expired}: it still
     * names itself, it is a distinct state rather than a tidied-away one, and it stays expired.
     */
    @Test
    void aSpentPhaseExpiresUnderItsOwnNameAndIsNeverReconciledAway() {
        UmbralSigilState state = inPhase(Phase.STRIKE, 1);
        assertTrue(state.timer().running());
        state = state.withTimer(state.timer().step());
        assertTrue(state.timer().expired(), "a phase on its last tick expires");
        assertEquals(Phase.STRIKE, state.phase(), "an expired phase still names itself");
        assertEquals(0, state.remainingTicks());
        state = state.withTimer(state.timer().step());
        assertTrue(state.timer().expired(), "an unhandled expiry stalls visibly rather than lapsing");
        assertEquals(Phase.STRIKE, state.phase());
    }

    @Test
    void enteringAPhaseAlwaysGivesItItsOwnDeclaredDuration() {
        for (final Phase phase : Phase.values()) {
            if (phase == Phase.DORMANT) {
                continue;
            }
            final UmbralSigilState state = UmbralSigilState.empty().enter(phase);
            assertEquals(phase, state.phase());
            assertEquals(UmbralSigilRules.phaseTicks(phase), state.remainingTicks(), phase.name());
        }
    }

    // ---------------------------------------------------------------- episode boundaries

    /**
     * The accumulator defect. Failures gathered while the Sigil was dormant must not be inherited
     * by the seal that follows, or the ending rule releases the new seal before it traces anything.
     * The open backoff window must survive, or the reset becomes a way to spam path requests.
     */
    @Test
    void startingASealClearsTheFailureRunButKeepsAnOpenBackoff() {
        final RouteRequest stalled = new RouteRequest(
            Cadence.armed(UmbralSigilRules.PATH_INTERVAL_TICKS),
            UmbralSigilRules.MAX_ROUTE_FAILURES,
            UmbralSigilRules.ROUTE_BACKOFF_TICKS
        );
        final UmbralSigilState started =
            UmbralSigilState.empty().withRoute(stalled).withStrikes(1).startSeal();
        assertEquals(Phase.INSCRIBE_1, started.phase());
        assertEquals(UmbralSigilRules.INSCRIBE_TICKS, started.remainingTicks());
        assertEquals(0, started.route().consecutiveFailures(),
            "the dormancy's failures belong to the dormancy");
        assertEquals(UmbralSigilRules.ROUTE_BACKOFF_TICKS, started.route().backoffRemaining(),
            "an open backoff survives the boundary");
        assertFalse(started.route().mayRequest(), "so the fresh seal still cannot spam requests");
        assertFalse(started.struck(), "a fresh seal always has its own unspent attempt");
    }

    @Test
    void endingASealArmsTheCooldownClearsTheLatchAndStillKeepsAnOpenBackoff() {
        final UmbralSigilState ended = inPhase(Phase.RECOVER, 1)
            .withStrikes(1)
            .withRoute(new RouteRequest(
                Cadence.armed(UmbralSigilRules.PATH_INTERVAL_TICKS), 2, 40))
            .endSeal();
        assertEquals(Phase.DORMANT, ended.phase());
        assertTrue(ended.timer().idle());
        assertEquals(UmbralSigilRules.SEAL_COOLDOWN_TICKS, ended.cooldownTicks());
        assertFalse(ended.struck(), "the latch is per seal and is cleared exactly here");
        assertEquals(0, ended.route().consecutiveFailures());
        assertEquals(40, ended.route().backoffRemaining(),
            "an ending is not evidence that the surroundings became routable");
    }

    // ---------------------------------------------------------------- clamping

    @Test
    void everyFieldIsClampedIndependentlyAndNoFieldZeroesAnother() {
        final UmbralSigilState state = new UmbralSigilState(
            UmbralSigilState.SCHEMA_VERSION,
            PhaseTimer.start(Phase.CLOSE, UmbralSigilRules.CLOSE_TICKS),
            UmbralSigilRules.freshRoute(),
            99,
            99_999
        );
        assertEquals(UmbralSigilRules.MAX_STRIKES, state.strikes());
        assertEquals(UmbralSigilRules.SEAL_COOLDOWN_TICKS, state.cooldownTicks());
        // The phase is untouched by either clamp: nothing here decides anything ended.
        assertEquals(Phase.CLOSE, state.phase());
        assertEquals(UmbralSigilRules.CLOSE_TICKS, state.remainingTicks());
        assertEquals(0, UmbralSigilState.empty().withStrikes(-4).strikes());
        assertEquals(0, UmbralSigilState.empty().withCooldown(-4).cooldownTicks());
    }

    // ---------------------------------------------------------------- persistence

    @Test
    void aDormantOrRecoveringStateRoundTripsExactly() {
        for (final UmbralSigilState original : Set.of(
            UmbralSigilState.empty().withCooldown(37),
            inPhase(Phase.RECOVER, 11).withStrikes(1)
        )) {
            final UmbralSigilState restored = UmbralSigilState.read(original.write());
            assertEquals(original.phase(), restored.phase());
            assertEquals(original.remainingTicks(), restored.remainingTicks());
            assertEquals(original.strikes(), restored.strikes());
            assertEquals(original.cooldownTicks(), restored.cooldownTicks());
        }
    }

    /**
     * The reload contract. No saved seal may resume, and no save cycle may hand a seal a second
     * attempt. Both halves matter: normalising the phase without preserving the latch would let a
     * save/load pair replay the one attempt the seal already spent.
     */
    @Test
    void everySavedSealPhaseReloadsIntoRecoveryWhileTheSpentAttemptSurvives() {
        for (final Phase phase : Phase.values()) {
            if (!UmbralSigilRules.sealing(phase)) {
                continue;
            }
            final UmbralSigilState restored = UmbralSigilState.read(
                inPhase(phase, UmbralSigilRules.phaseTicks(phase)).withStrikes(1).write()
            );
            assertEquals(Phase.RECOVER, restored.phase(), phase + " must not resume");
            assertEquals(UmbralSigilRules.RECOVER_TICKS, restored.remainingTicks(), phase.name());
            assertTrue(restored.struck(), phase + " must not regain its spent attempt");
        }
    }

    @Test
    void aMissingUnknownOrMalformedTagResetsToASafeDormancy() {
        assertEquals(Phase.DORMANT, UmbralSigilState.read(null).phase());
        final CompoundTag wrongVersion = UmbralSigilState.empty().write();
        wrongVersion.putInt("Version", UmbralSigilState.SCHEMA_VERSION + 1);
        assertEquals(Phase.DORMANT, UmbralSigilState.read(wrongVersion).phase());
        final CompoundTag nonsense = UmbralSigilState.empty().write();
        nonsense.putString("Phase", "not_a_phase");
        nonsense.putInt("Remaining", 999_999);
        nonsense.putInt("Strikes", 42);
        nonsense.putInt("Cooldown", -17);
        final UmbralSigilState restored = UmbralSigilState.read(nonsense);
        assertEquals(Phase.DORMANT, restored.phase());
        assertEquals(0, restored.remainingTicks());
        assertEquals(UmbralSigilRules.MAX_STRIKES, restored.strikes());
        assertEquals(0, restored.cooldownTicks());
    }

    @Test
    void aStoredDurationLongerThanItsOwnPhaseIsPulledBackIntoRange() {
        final CompoundTag tag = inPhase(Phase.RECOVER, UmbralSigilRules.RECOVER_TICKS).write();
        tag.putInt("Remaining", 100_000);
        assertEquals(UmbralSigilRules.RECOVER_TICKS, UmbralSigilState.read(tag).remainingTicks());
    }

    @Test
    void thePersistedShapeIsFixedSmallAndCarriesNoReferenceToAnybody() {
        final CompoundTag tag = inPhase(Phase.CLOSE, 5).withStrikes(1).withCooldown(60).write();
        assertEquals(Set.of("Version", "Phase", "Remaining", "RouteSince", "RouteFail",
            "RouteBackoff", "Strikes", "Cooldown"), tag.keySet(),
            "the persisted Umbral Sigil state never grows a field outside its fixed shape");
        assertTrue(tag.toString().length() <= UmbralSigilRules.MAX_STATE_BYTES,
            "the encoded state stays under the declared ceiling: " + tag);
        assertFalse(tag.toString().toLowerCase(java.util.Locale.ROOT).contains("uuid"));
    }
}
