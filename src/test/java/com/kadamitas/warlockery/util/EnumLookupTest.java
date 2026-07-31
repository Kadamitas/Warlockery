package com.kadamitas.warlockery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

final class EnumLookupTest {
    private static final EnumLookup<Value> LOOKUP = EnumLookup.create("test value", Value.values());

    @Test
    void factoryBuildsConstantTimeImmutableLookups() {
        assertEquals(Value.FIRST, LOOKUP.find("first").orElseThrow());
        assertFalse(LOOKUP.find("missing").isPresent());
        assertEquals(Value.SECOND, LOOKUP.findOrElse("missing", Value.SECOND));
        assertThrows(UnsupportedOperationException.class, () -> LOOKUP.byId().put("third", Value.FIRST));
    }

    @Test
    void strictAndFallbackCodecsRetainTheirDistinctSemantics() {
        assertEquals(Value.SECOND, LOOKUP.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("second")).getOrThrow());
        assertFalse(LOOKUP.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("missing")).result().isPresent());
        assertEquals(
            Value.FIRST,
            LOOKUP.fallbackCodec(Value.FIRST).parse(JsonOps.INSTANCE, new JsonPrimitive("missing")).getOrThrow()
        );
    }

    private enum Value implements StringIdentified {
        FIRST("first"),
        SECOND("second");

        private final String id;

        Value(final String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
