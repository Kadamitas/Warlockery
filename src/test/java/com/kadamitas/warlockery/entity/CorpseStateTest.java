package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class CorpseStateTest {
    @Test
    void freshStateUsesSafeActiveDefaults() {
        final CorpseState fresh = CorpseState.fresh();
        assertEquals(CorpseRules.SCHEMA_VERSION, CorpseState.SCHEMA_VERSION);
        assertEquals(1_200, fresh.cohesion());
        assertEquals(0, fresh.decayRemainder());
        assertEquals(0, fresh.groundMealCooldown());
        assertFalse(fresh.dormant());
    }

    @Test
    void versionOneValuesRoundTripExactly() {
        final CorpseState state = new CorpseState(451, 7, 1_234);
        final CorpseState reloaded = CorpseState.read(state.write());
        assertEquals(state, reloaded);
        assertEquals(451, reloaded.cohesion());
        assertEquals(7, reloaded.decayRemainder());
        assertEquals(1_234, reloaded.groundMealCooldown());
    }

    @Test
    void durableStateIsExactlySchemaPlusThreeBoundedIntegers() {
        final CompoundTag tag = CorpseState.fresh().write();
        assertEquals(4, tag.size(), "schema + cohesion + remainder + cooldown only");
        assertTrue(tag.getInt("Version").isPresent());
        assertTrue(tag.getInt("Cohesion").isPresent());
        assertTrue(tag.getInt("DecayRemainder").isPresent());
        assertTrue(tag.getInt("GroundMealCooldown").isPresent());
    }

    @Test
    void dormancyIsDerivedFromZeroCohesionAndNotStoredSeparately() {
        assertTrue(new CorpseState(0, 0, 0).dormant());
        assertFalse(new CorpseState(1, 0, 0).dormant());
        assertFalse(CorpseState.fresh().write().getInt("Dormant").isPresent());
    }

    @Test
    void unknownFutureVersionDiscardsOnlyCorpseSemanticsToSafeDefaults() {
        final CompoundTag future = new CorpseState(3, 3, 3).write();
        future.putInt("Version", 2);
        assertEquals(CorpseState.fresh(), CorpseState.read(future));
        assertEquals(CorpseState.fresh(), CorpseState.read(new CompoundTag()), "missing version is legacy-safe");
    }

    @Test
    void malformedAndExtremeValuesClampIndependentlyWithoutCreatingWork() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", CorpseState.SCHEMA_VERSION);
        tag.putInt("Cohesion", Integer.MAX_VALUE);
        tag.putInt("DecayRemainder", -44);
        tag.putInt("GroundMealCooldown", Integer.MIN_VALUE);
        final CorpseState read = CorpseState.read(tag);
        assertEquals(1_200, read.cohesion());
        assertEquals(0, read.decayRemainder());
        assertEquals(0, read.groundMealCooldown());

        final CompoundTag wrongTypes = new CompoundTag();
        wrongTypes.putInt("Version", CorpseState.SCHEMA_VERSION);
        wrongTypes.putString("Cohesion", "many");
        final CorpseState defaulted = CorpseState.read(wrongTypes);
        assertEquals(1_200, defaulted.cohesion(), "a malformed field defaults independently");
        assertEquals(0, defaulted.decayRemainder());
    }

    @Test
    void constructorClampsEveryBoundedField() {
        final CorpseState clamped = new CorpseState(9_999, 99, 99_999);
        assertEquals(1_200, clamped.cohesion());
        assertEquals(19, clamped.decayRemainder());
        assertEquals(4_800, clamped.groundMealCooldown());
        final CorpseState floor = new CorpseState(-5, -5, -5);
        assertEquals(0, floor.cohesion());
        assertEquals(0, floor.decayRemainder());
        assertEquals(0, floor.groundMealCooldown());
    }

    @Test
    void stateHoldsNoUuidPathListMapOrLiveReferenceFields() {
        for (final java.lang.reflect.RecordComponent component : CorpseState.class.getRecordComponents()) {
            assertEquals(int.class, component.getType(),
                component.getName() + " must be a bounded primitive integer");
        }
        assertEquals(3, CorpseState.class.getRecordComponents().length);
    }

    @Test
    void representativeEncodedStateStaysWithinTheDeclaredSizeTarget() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(new CorpseState(1_200, 19, 4_800).write(), output);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(bytes.size() <= 128,
            "representative encoded state was " + bytes.size() + " bytes");
        assertTrue(bytes.size() > 0, "never claim zero bytes");
    }
}
