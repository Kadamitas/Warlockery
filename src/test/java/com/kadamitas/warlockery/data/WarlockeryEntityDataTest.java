package com.kadamitas.warlockery.data;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarlockeryEntityDataTest {
    @Test
    void attachmentPersistsWithoutChangingExplicitDeathCopyRules() {
        final var type = WarlockeryEntityData.type();

        assertNotNull(type.persistenceCodec());
        assertFalse(type.copyOnDeath());

        final var initializer = Objects.requireNonNull(type.initializer());
        final CompoundTag first = initializer.get();
        final CompoundTag second = initializer.get();

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        assertNotSame(first, second);
    }
}
