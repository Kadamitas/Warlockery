package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class DreamrootStateTest {
    @Test void roundTripAndLegacyZeroAreSafe() {
        var state = new DreamrootState(1, 400, 39, 200);
        assertEquals(state, DreamrootState.read(state.write()));
        assertEquals(4, state.write().size());
        var legacy = new CompoundTag();
        legacy.putLong(DreamrootState.LEGACY_LAST_BLAST_KEY, 0L);
        assertEquals(DreamrootState.empty(), DreamrootState.migrate(legacy));
        assertTrue(legacy.contains(DreamrootEntity.STATE_KEY), "new nested state must be written before legacy removal");
        assertEquals(0, legacy.getCompoundOrEmpty(DreamrootEntity.STATE_KEY).getIntOr("DreamCooldownRemaining", -1));
        assertFalse(legacy.contains(DreamrootState.LEGACY_LAST_BLAST_KEY));
        final int measured=encode(state.write()).length;
        assertEquals(101,measured,"representative encoded state size is measured and pinned");
        assertEquals(measured,encode(DreamrootState.empty().write()).length,"fixed cardinality cannot grow with use");
    }

    private static byte[] encode(CompoundTag tag){var bytes=new ByteArrayOutputStream();try{NbtIo.write(tag,new DataOutputStream(bytes));}catch(IOException failure){throw new UncheckedIOException(failure);}return bytes.toByteArray();}
}
