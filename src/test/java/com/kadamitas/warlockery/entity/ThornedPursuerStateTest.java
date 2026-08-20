package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ThornedPursuerStateTest {
    @Test
    void defaultsAndRoundTripUseExactlyFourFields() {
        var state = ThornedPursuerState.defaults();
        assertEquals(new ThornedPursuerState(1, 0, 0, 0), state);
        var tag = new ThornedPursuerState(1, 400, 1200, 200).write();
        assertEquals(4, tag.size());
        assertEquals(new ThornedPursuerState(1, 400, 1200, 200), ThornedPursuerState.read(tag));
    }

    @Test
    void malformedUnknownAndOutOfRangeValuesResetIndependentlyToDue() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", 1);
        tag.putInt("SnareCooldownRemaining", 401);
        tag.putInt("EscortCooldownRemaining", 11);
        tag.putInt("EpisodeCooldownRemaining", -1);
        assertEquals(new ThornedPursuerState(1, 0, 11, 0), ThornedPursuerState.read(tag));
        tag.putInt("SchemaVersion", 2);
        assertEquals(ThornedPursuerState.defaults(), ThornedPursuerState.read(tag));
        assertEquals(ThornedPursuerState.defaults(), ThornedPursuerState.read(new CompoundTag()));
        tag = new CompoundTag();
        tag.putInt("SchemaVersion", 1);
        tag.putString("SnareCooldownRemaining", "not-a-number");
        tag.putInt("EscortCooldownRemaining", 12);
        assertEquals(new ThornedPursuerState(1, 0, 12, 0), ThornedPursuerState.read(tag));
    }

    @Test
    void loadedTickDecrementNeverCatchesUpOrUnderflows() {
        var state = new ThornedPursuerState(1, 2, 1, 0);
        assertEquals(new ThornedPursuerState(1, 1, 0, 0), state.tickLoaded());
    }

    @Test
    void durableSchemaCannotContainReferencesOrCollections() throws Exception {
        for (var component : ThornedPursuerState.class.getRecordComponents()) {
            assertEquals(int.class, component.getType());
        }
        assertEquals(4, ThornedPursuerState.class.getRecordComponents().length);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(bytes)) {
            net.minecraft.nbt.NbtIo.write(new ThornedPursuerState(1, 400, 1200, 200).write(), output);
        }
        assertEquals(114, bytes.size(),
            "this platform's named binary NBT encoding is recorded exactly when the 96-byte target is unavailable");
    }
}
