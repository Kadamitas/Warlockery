package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping, coupled invariants and reload contracts for the durable Sentinel record. */
final class IronboundSentinelStateTest {

    private static IronboundSentinelState populated() {
        return IronboundSentinelState.empty()
            .stationedAt(new BlockPos(-1200, 71, 8400))
            .withBearing(2)
            .withStrain(137);
    }

    @Test
    void aFreshRecordIsChargedUnstationedAndUnstrained() {
        final IronboundSentinelState empty = IronboundSentinelState.empty();
        assertEquals(IronboundSentinelState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Charge.CHARGED, empty.charge(), "the making is the commissioning");
        assertEquals(0, empty.transitionRemaining());
        assertFalse(empty.stationed());
        assertTrue(empty.station().isEmpty());
        assertEquals(0, empty.bearing());
        assertEquals(0, empty.strain());
    }

    @Test
    void aCompleteVersionOneRoundTripPreservesEveryField() {
        final IronboundSentinelState original = populated();
        final IronboundSentinelState restored = IronboundSentinelState.read(original.write());
        assertEquals(original, restored);
        assertEquals(new BlockPos(-1200, 71, 8400), restored.station().orElseThrow());
        assertEquals(2, restored.bearing());
        assertEquals(137, restored.strain());
    }

    @Test
    void everyStoredValueIsClampedIndependentlyOfEveryOther() {
        final IronboundSentinelState corrupt = new IronboundSentinelState(
            999, Charge.WAKING, Integer.MAX_VALUE, true, 5, 6, 7, 11, Integer.MIN_VALUE
        );
        assertEquals(IronboundSentinelState.SCHEMA_VERSION, corrupt.schemaVersion());
        assertEquals(IronboundSentinelRules.MAX_TRANSITION_TICKS, corrupt.transitionRemaining());
        assertEquals(3, corrupt.bearing(), "an out-of-range bearing wraps into 0..3");
        assertEquals(0, corrupt.strain(), "a negative strain clamps to no strain");
        assertEquals(new BlockPos(5, 6, 7), corrupt.station().orElseThrow(),
            "one corrupt field never poisons another");
    }

    /**
     * The one coupled invariant, and it is the identity shape rather than the timer shape: the
     * stationed flag is the identity and the coordinates are its dependents, so an unstationed
     * record cannot smuggle coordinates through and {@code (0, 0, 0)} can never be misread as
     * "no station".
     */
    @Test
    void anUnstationedRecordZeroesItsCoordinatesBecauseTheFlagIsTheIdentity() {
        final IronboundSentinelState unstationed = new IronboundSentinelState(
            1, Charge.CHARGED, 0, false, 900, -60, -900, 1, 4
        );
        assertFalse(unstationed.stationed());
        assertEquals(0, unstationed.stationX());
        assertEquals(0, unstationed.stationY());
        assertEquals(0, unstationed.stationZ());
        assertTrue(unstationed.station().isEmpty());

        final IronboundSentinelState atOrigin = IronboundSentinelState.empty()
            .stationedAt(BlockPos.ZERO);
        assertTrue(atOrigin.stationed());
        assertEquals(BlockPos.ZERO, atOrigin.station().orElseThrow(),
            "a zero coordinate is a legal post, not an absent one");
    }

    /** The second arm of the same identity shape: a settled charge describes no transition. */
    @Test
    void aSettledChargeCarriesNoTransitionCounterWhateverWasStored() {
        assertEquals(0, new IronboundSentinelState(
            1, Charge.CHARGED, 45, true, 0, 64, 0, 0, 0).transitionRemaining());
        assertEquals(0, new IronboundSentinelState(
            1, Charge.INERT, 45, true, 0, 64, 0, 0, 0).transitionRemaining());
        assertEquals(45, new IronboundSentinelState(
            1, Charge.WAKING, 45, true, 0, 64, 0, 0, 0).transitionRemaining());
    }

    /**
     * The defect this record deliberately does not carry. A timer-shaped reconcile would end the
     * transition the moment the counter reached zero, and the tick branch that owns ending it, and
     * that also emits the one bounded feedback event and clears strain on the way into
     * {@code INERT}, tests for exactly the pair such a reconcile destroys.
     */
    @Test
    void aZeroCounterOnATransitionalArmSurvivesSoTheTickBranchCanStillObserveIt() {
        final IronboundSentinelState waking =
            new IronboundSentinelState(1, Charge.WAKING, 0, true, 0, 64, 0, 0, 0);
        assertEquals(Charge.WAKING, waking.charge(),
            "the constructor must not decide that the waking has finished");
        assertEquals(0, waking.transitionRemaining());

        final IronboundSentinelState standingDown =
            new IronboundSentinelState(1, Charge.STANDING_DOWN, 0, true, 0, 64, 0, 0, 200);
        assertEquals(Charge.STANDING_DOWN, standingDown.charge());
        assertEquals(200, standingDown.strain(),
            "strain is cleared by the branch that enters INERT, never by the constructor");
    }

    @Test
    void bothTransitionalArmsResumeAtSixtyAtOneAndAtZero() {
        for (final Charge charge : new Charge[] {Charge.WAKING, Charge.STANDING_DOWN}) {
            for (final int stored : new int[] {60, 1, 0}) {
                final IronboundSentinelState saved =
                    new IronboundSentinelState(1, charge, stored, true, 0, 64, 0, 0, 0);
                final IronboundSentinelState restored =
                    IronboundSentinelState.read(saved.write());
                assertEquals(charge, restored.charge());
                assertEquals(stored, restored.transitionRemaining(),
                    charge + " resumes its persisted counter rather than restarting or stranding");
            }
        }
    }

    @Test
    void enteringAChargeArmLoadsItsDeclaredDurationAndClearsStrainOnTheTwoArmsThatClearIt() {
        final IronboundSentinelState strained = populated();
        assertEquals(0, strained.withCharge(Charge.INERT).strain());
        assertEquals(0, strained.withCharge(Charge.WAKING).strain(),
            "seating a fresh charge starts the ledger over");
        assertEquals(137, strained.withCharge(Charge.STANDING_DOWN).strain(),
            "going down keeps the strain that caused it until INERT is actually reached");
        assertEquals(60, strained.withCharge(Charge.WAKING).transitionRemaining());
        assertEquals(60, strained.withCharge(Charge.STANDING_DOWN).transitionRemaining());
        assertEquals(0, strained.withCharge(Charge.CHARGED).transitionRemaining());
        assertEquals(new BlockPos(-1200, 71, 8400),
            strained.withCharge(Charge.INERT).station().orElseThrow(),
            "the station outlives every charge transition");
    }

    @Test
    void anAbsentMalformedOrFutureSchemaDefaultsToAChargedUnstrainedSentinel() {
        assertEquals(IronboundSentinelState.empty(), IronboundSentinelState.read(null));
        assertEquals(IronboundSentinelState.empty(), IronboundSentinelState.read(new CompoundTag()));
        final CompoundTag future = populated().write();
        future.putInt(IronboundSentinelState.VERSION_KEY, 7);
        assertEquals(IronboundSentinelState.empty(), IronboundSentinelState.read(future));
        final CompoundTag garbledCharge = populated().write();
        garbledCharge.putString(IronboundSentinelState.CHARGE_KEY, "not-a-charge");
        assertEquals(Charge.CHARGED, IronboundSentinelState.read(garbledCharge).charge());
        assertEquals(137, IronboundSentinelState.read(garbledCharge).strain(),
            "one malformed field defaults on its own without discarding the rest");
    }

    @Test
    void theRecordHoldsNoIdentityPathTimestampOrCollection() {
        final var components = java.util.Arrays.stream(
                IronboundSentinelState.class.getRecordComponents())
            .toList();
        assertEquals(9, components.size(), "fixed cardinality, exactly nine fields");
        components.forEach(component -> {
            final Class<?> type = component.getType();
            assertTrue(type.isPrimitive() || type == Charge.class,
                component.getName() + " must be a primitive or the charge enum, got " + type);
        });
        final var names = components.stream()
            .map(java.lang.reflect.RecordComponent::getName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();
        assertTrue(names.stream().noneMatch(name -> name.contains("uuid") || name.contains("subject")
                || name.contains("attacker") || name.contains("phase") || name.contains("path")
                || name.contains("time") || name.contains("owner") || name.contains("dimension")),
            "no live reference, no phase and no absolute time may persist; got " + names);
    }

    @Test
    void theLegacyAmbientCooldownKeyIsNeitherReadNorWritten() {
        final String legacy = "WarlockeryAmbientCooldownVILLAGE_WATCH";
        assertFalse(populated().write().keySet().contains(legacy));
        final CompoundTag carrying = populated().write();
        carrying.putLong(legacy, 123_456L);
        final IronboundSentinelState restored = IronboundSentinelState.read(carrying);
        assertEquals(populated(), restored,
            "an older Sentinel's legacy ambient stamp is left in place and never consulted");
    }

    /**
     * An honest measurement rather than a claimed target. The pinned number is the exact encoded
     * size of a representative populated record, so a field added to the contract without a
     * corresponding budget decision fails here instead of passing under a generous ceiling.
     */
    @Test
    void aRepresentativePopulatedStateEncodesAtTheMeasuredSizeAndBelowTheDeclaredCeiling() {
        final int measured = encode(populated().write()).length;
        assertEquals(IronboundSentinelRules.REPRESENTATIVE_STATE_BYTES, measured,
            "the pinned representative encoded size must be measured, never estimated");
        assertTrue(measured < IronboundSentinelRules.MAX_STATE_BYTES, "measured " + measured);
        assertEquals(measured, encode(IronboundSentinelState.empty().write()).length,
            "the record is fixed cardinality, so an empty Sentinel costs exactly what a fully "
                + "populated one costs and no save can grow with use");
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
}
