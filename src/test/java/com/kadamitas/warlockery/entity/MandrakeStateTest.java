package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class MandrakeStateTest {
    @Test void roundTripAndMalformedInputAreSafe() {
        var state = new MandrakeState(1, 600, 200);
        assertEquals(state, MandrakeState.read(state.write()));
        var malformed = new CompoundTag();
        malformed.putInt("Version", 99);
        assertEquals(MandrakeState.empty(), MandrakeState.read(malformed));
        assertEquals(3, state.write().size());
        final int measured=encode(state.write()).length;
        assertEquals(77,measured,"representative encoded state size is measured and pinned");
        assertEquals(measured,encode(MandrakeState.empty().write()).length,"fixed cardinality cannot grow with use");
    }

    private static byte[] encode(CompoundTag tag){var bytes=new ByteArrayOutputStream();try{NbtIo.write(tag,new DataOutputStream(bytes));}catch(IOException failure){throw new UncheckedIOException(failure);}return bytes.toByteArray();}
}
