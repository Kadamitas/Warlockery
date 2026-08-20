package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The durable record is four fields wide and stays that way. These tests pin the fixed cardinality,
 * the independent clamping, and the load floor that closes the unload-reload re-tenancy loophole.
 */
final class ParasyticLouseStateTest {

    @Test
    void theRecordIsExactlyFourKeysWideAndNeverGrows() {
        final Set<String> declared = Set.of("Version", "Nourishment", "DecayRemaining", "SeekCooldown");
        assertEquals(declared, ParasyticLouseState.empty().write().keySet());
        assertEquals(declared,
            new ParasyticLouseState(1, 3, 120, 400).write().keySet(),
            "no host, candidate, attacker, owner copy, payload copy, path or timestamp is ever stored");
    }

    @Test
    void aFreshRecordIsReadyToScanAtOnceAndCarriesAFullDecayWindow() {
        final ParasyticLouseState empty = ParasyticLouseState.empty();
        assertEquals(0, empty.nourishment());
        assertEquals(0, empty.seekCooldownRemainingTicks(),
            "zero means due, never fired at world tick zero");
        assertEquals(ParasyticLouseState.MAX_DECAY_REMAINDER, empty.decayRemainingTicks());
    }

    @Test
    void everyFieldClampsIndependentlyOfEveryOther() {
        final ParasyticLouseState clamped = new ParasyticLouseState(1, 99, 100_000, -40);
        assertEquals(ParasyticLouseTenancyRules.MAX_NOURISHMENT, clamped.nourishment());
        assertEquals(ParasyticLouseState.MAX_DECAY_REMAINDER, clamped.decayRemainingTicks());
        assertEquals(0, clamped.seekCooldownRemainingTicks());
    }

    @Test
    void aFullNourishmentDoesNotZeroItsDecayRemainderAndAnEmptyOneDoesNotZeroTheCooldown() {
        // The timer shape of reconciliation is deliberately absent: no field here decides that
        // another has ended, because that decision belongs to a tick branch that must run and arm
        // whatever the ending implies.
        final ParasyticLouseState state = new ParasyticLouseState(1, 4, 0, 0);
        assertEquals(4, state.nourishment());
        assertEquals(0, state.decayRemainingTicks());
        assertEquals(0, state.seekCooldownRemainingTicks());
        final ParasyticLouseState drained = new ParasyticLouseState(1, 0, 399, 600);
        assertEquals(0, drained.nourishment());
        assertEquals(399, drained.decayRemainingTicks());
        assertEquals(600, drained.seekCooldownRemainingTicks());
    }

    @Test
    void aRoundTripPreservesEveryDurableValue() {
        final ParasyticLouseState written = new ParasyticLouseState(1, 3, 251, 480);
        final ParasyticLouseState read = ParasyticLouseState.read(written.write());
        assertEquals(3, read.nourishment());
        assertEquals(251, read.decayRemainingTicks());
        assertEquals(480, read.seekCooldownRemainingTicks());
    }

    @Test
    void aReloadedCooldownIsAlwaysAtLeastTheLoadFloor() {
        final ParasyticLouseState read = ParasyticLouseState.read(
            new ParasyticLouseState(1, 4, 10, 0).write()
        );
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            read.seekCooldownRemainingTicks(),
            "a reload cannot immediately restart a tenancy, so unload cycling cannot renew a term");
        assertEquals(4, read.nourishment(), "durable nourishment survives the reload unchanged");
    }

    @Test
    void aMissingRecordDefaultsSafelyAndStillCarriesTheLoadFloor() {
        final ParasyticLouseState read = ParasyticLouseState.read(null);
        assertEquals(0, read.nourishment());
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            read.seekCooldownRemainingTicks());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 99, -1})
    void anUnknownOrLegacySchemaDiscardsOnlyF31SemanticValues(final int version) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", version);
        tag.putInt("Nourishment", 4);
        tag.putInt("SeekCooldown", 600);
        final ParasyticLouseState read = ParasyticLouseState.read(tag);
        assertEquals(0, read.nourishment());
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            read.seekCooldownRemainingTicks());
    }

    @Test
    void aMalformedRecordDefaultsEachMissingFieldWithoutDiscardingItsNeighbours() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", ParasyticLouseState.SCHEMA_VERSION);
        tag.putInt("Nourishment", 2);
        final ParasyticLouseState read = ParasyticLouseState.read(tag);
        assertEquals(2, read.nourishment());
        assertEquals(ParasyticLouseState.MAX_DECAY_REMAINDER, read.decayRemainingTicks());
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            read.seekCooldownRemainingTicks());
    }

    @Test
    void aFarFutureSentinelIsTreatedAsCorruptRatherThanParked() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", ParasyticLouseState.SCHEMA_VERSION);
        tag.putInt("SeekCooldown", Integer.MAX_VALUE);
        tag.putInt("DecayRemaining", Integer.MAX_VALUE);
        final ParasyticLouseState read = ParasyticLouseState.read(tag);
        assertEquals(ParasyticLouseTenancyRules.SEEK_COOLDOWN_TICKS,
            read.seekCooldownRemainingTicks());
        assertEquals(ParasyticLouseState.MAX_DECAY_REMAINDER, read.decayRemainingTicks());
        assertTrue(read.seekCooldownRemainingTicks() < 20_000,
            "no value in this family is ever parked beyond the loaded horizon");
    }

    @Test
    void negativeStoredValuesReadAsDueRatherThanAsRecentlyFired() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", ParasyticLouseState.SCHEMA_VERSION);
        tag.putInt("SeekCooldown", -5);
        tag.putInt("DecayRemaining", -5);
        tag.putInt("Nourishment", -5);
        final ParasyticLouseState read = ParasyticLouseState.read(tag);
        assertEquals(ParasyticLouseTenancyRules.LOAD_SEEK_COOLDOWN_FLOOR_TICKS,
            read.seekCooldownRemainingTicks());
        assertEquals(0, read.decayRemainingTicks());
        assertEquals(0, read.nourishment());
    }

    @Test
    void theHostHolderCollapsesAHalfWrittenIdentityToNone() {
        // The identity shape of reconciliation, and the only one this family writes: two halves of
        // one identity cannot disagree. It touches no duration, so no tick branch loses its ending.
        assertTrue(ParasyticLouseRuntime.Tenancy.Host.of(
            java.util.UUID.randomUUID(), "minecraft:overworld").present());
        assertEquals(ParasyticLouseRuntime.Tenancy.Host.none(),
            new ParasyticLouseRuntime.Tenancy.Host(
                java.util.Optional.of(java.util.UUID.randomUUID()), java.util.Optional.empty()));
        assertEquals(ParasyticLouseRuntime.Tenancy.Host.none(),
            new ParasyticLouseRuntime.Tenancy.Host(
                java.util.Optional.empty(), java.util.Optional.of("minecraft:overworld")));
        assertEquals(ParasyticLouseRuntime.Tenancy.Host.none(),
            new ParasyticLouseRuntime.Tenancy.Host(
                java.util.Optional.of(java.util.UUID.randomUUID()), java.util.Optional.of("  ")),
            "a blank dimension is not a dimension");
    }
}
