package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.StormSimianState.Cooldowns;
import com.kadamitas.warlockery.entity.StormSimianState.Route;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping, coupled invariants and the episode boundary of the Storm Simian state. */
final class StormSimianStateTest {

    private static StormSimianState populated() {
        return StormSimianState.empty()
            .withCharge(64)
            .withGrip(new BlockPos(120, 71, -340))
            .withCooldowns(new Cooldowns(40, 90, 20))
            .withRoute(new Route(7, 2, 60))
            .withCompletedObservation(70);
    }

    private static byte[] encode(final CompoundTag tag) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(tag, new DataOutputStream(bytes));
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }

    @Test
    void aPopulatedStateSurvivesAWriteAndReadRoundTripExactly() {
        final StormSimianState before = populated();
        assertEquals(before, StormSimianState.read(before.write()));
        assertEquals(StormSimianState.empty(),
            StormSimianState.read(StormSimianState.empty().write()));
    }

    @Test
    void anUnknownOrMissingSchemaResetsToASafeEmptyState() {
        assertEquals(StormSimianState.empty(), StormSimianState.read(null));
        final CompoundTag foreign = populated().write();
        foreign.putInt("Version", StormSimianState.SCHEMA_VERSION + 7);
        assertEquals(StormSimianState.empty(), StormSimianState.read(foreign));
    }

    /**
     * The legitimate identity shape: a hold countdown is time spent on a grip, so with no grip the
     * two fields cannot be allowed to disagree. This is the type enforcing a coupled invariant, not
     * a constructor deciding that something ended.
     */
    @Test
    void theCoupledInvariantZeroesTheHoldWhenThereIsNoGripToHold() {
        final StormSimianState nothingHeld = new StormSimianState(
            StormSimianState.SCHEMA_VERSION, 10, Optional.empty(), 250,
            Cooldowns.none(), Route.fresh(), 0L);
        assertEquals(0, nothingHeld.gripHoldTicks(),
            "a hold with nothing to hold is not representable");
        assertTrue(StormSimianState.empty().withGrip(BlockPos.ZERO).gripHoldTicks() > 0,
            "taking a grip and starting its hold is one indivisible move");
        assertEquals(0, StormSimianState.empty().withGrip(BlockPos.ZERO).withoutGrip()
            .gripHoldTicks());
    }

    /**
     * Recurring defect two, the timer shape, is deliberately absent. A grip whose hold has run out
     * must remain a grip: the canopy branch is what tests for exactly that pair, and it is what
     * releases the grip, reseeds the scan cursor and arms the route cadence. A constructor that
     * dropped the grip at zero would destroy the pair and the branch would never run.
     */
    @Test
    void aGripWhoseHoldHasExpiredIsStillHeldSoTheCanopyBranchCanObserveIt() {
        final StormSimianState expired = StormSimianState.empty()
            .withGrip(new BlockPos(4, 70, 9))
            .withGripHold(0);
        assertTrue(expired.grip().isPresent(),
            "the expired pair (grip present, hold zero) is representable and is the branch's cue");
        assertEquals(0, expired.gripHoldTicks());
    }

    @Test
    void everyStoredCountdownIsClampedBackIntoRangeOnRead() {
        final CompoundTag corrupt = populated().write();
        corrupt.putInt("Charge", 9_000);
        corrupt.putInt("GripHold", 900_000);
        corrupt.putInt("AlarmCooldown", -50);
        corrupt.putInt("RouteFail", 99);
        corrupt.putInt("RouteBackoff", 10_000_000);
        final StormSimianState read = StormSimianState.read(corrupt);
        assertEquals(StormSimianRules.MAX_CHARGE, read.charge());
        assertEquals(StormSimianRules.GRIP_HOLD_TICKS, read.gripHoldTicks());
        assertEquals(0, read.cooldowns().alarmTicks());
        assertEquals(StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF,
            read.route().consecutiveFailures());
        assertEquals(StormSimianRules.ROUTE_BACKOFF_MAX_TICKS, read.route().backoffTicks());
    }

    @Test
    void oneLoadedTickAdvancesEveryCountdownAndNothingElse() {
        final StormSimianState stepped = populated().step();
        assertEquals(populated().charge(), stepped.charge(),
            "charge is semantic, not a countdown: time alone never changes it");
        assertEquals(populated().observationEpoch(), stepped.observationEpoch());
        assertEquals(populated().gripHoldTicks() - 1, stepped.gripHoldTicks());
        assertEquals(populated().cooldowns().alarmTicks() - 1, stepped.cooldowns().alarmTicks());
        assertEquals(populated().route().backoffTicks() - 1, stepped.route().backoffTicks());
        assertEquals(populated().route().consecutiveFailures(),
            stepped.route().consecutiveFailures(),
            "a failure run is cleared by an outcome, never by the passage of time");
    }

    /**
     * Recurring defect six. The failure run belongs to the routine stretch that accrued it and must
     * not be inherited; the backoff window describes the neighbourhood and must survive, or an
     * unusable spot is hammered again the moment anything interrupts the simian.
     */
    @Test
    void aFreshRoutineStretchClearsTheFailureRunAndPreservesTheOpenBackoffWindow() {
        final StormSimianState inherited = StormSimianState.empty()
            .withRoute(new Route(20, StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF, 80));
        final StormSimianState restarted = inherited.startRoutineStretch();
        assertEquals(0, restarted.route().consecutiveFailures());
        assertEquals(80, restarted.route().backoffTicks(),
            "the open backoff window is not a stretch fact and survives the boundary");
        assertEquals(20, restarted.route().sinceLastRequest());
        assertSame(restarted, restarted.startRoutineStretch(),
            "an already clean ledger is left untouched rather than rebuilt");
    }

    @Test
    void aCompletedObservationAdvancesTheEpochAndRearmsItsOwnCooldownOnly() {
        final StormSimianState before = StormSimianState.empty()
            .withCooldowns(new Cooldowns(33, 44, 0));
        final StormSimianState after = before.withCompletedObservation(21);
        assertEquals(21, after.charge());
        assertEquals(before.observationEpoch() + 1L, after.observationEpoch());
        assertEquals(StormSimianRules.OBSERVATION_COOLDOWN_TICKS,
            after.cooldowns().observationTicks());
        assertEquals(33, after.cooldowns().alarmTicks());
        assertEquals(44, after.cooldowns().curiosityTicks());
    }

    @Test
    void noPathTargetCandidateListOrAlarmRecipientIsEverPersisted() {
        final CompoundTag tag = populated().write();
        for (final String key : tag.keySet()) {
            final String lower = key.toLowerCase(java.util.Locale.ROOT);
            assertFalse(lower.contains("target") || lower.contains("path")
                    || lower.contains("recipient") || lower.contains("uuid")
                    || lower.contains("item") || lower.contains("window"),
                "transient execution facts must never reach the save: " + key);
        }
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        assertTrue(encode(populated().write()).length < StormSimianRules.MAX_STATE_BYTES);
        assertTrue(encode(StormSimianState.empty().write()).length
            < StormSimianRules.MAX_STATE_BYTES);
    }

    @Test
    void theRouteLedgerRoundTripsThroughTheSharedRouteRequestWithoutDrift() {
        final Route route = new Route(11, 2, 40);
        assertEquals(route, Route.of(route.request()));
        assertEquals(StormSimianRules.ROUTE_PERIOD_TICKS, route.request().cadence().period());
        assertFalse(route.request().mayRequest(),
            "an open backoff window forbids a request even when the cadence would allow one");
        assertTrue(new Route(StormSimianRules.ROUTE_PERIOD_TICKS, 0, 0).request().mayRequest());
    }
}
