package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

final class BrewBehaviorCodecTest {
    @Test
    void behaviorIdentifiersRoundTripAndRejectUnknownValues() {
        assertTrue(BrewBehavior.find("missing_behavior").isEmpty());
        assertTrue(BrewBehavior.CODEC.parse(JsonOps.INSTANCE,
            new com.google.gson.JsonPrimitive("missing_behavior")).error().isPresent());
        for (final BrewBehavior behavior : BrewBehavior.values()) {
            assertEquals(behavior, BrewBehavior.find(behavior.id()).orElseThrow());
            final var encoded = BrewBehavior.CODEC.encodeStart(JsonOps.INSTANCE, behavior).getOrThrow();
            assertEquals(behavior, BrewBehavior.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
    }
}
