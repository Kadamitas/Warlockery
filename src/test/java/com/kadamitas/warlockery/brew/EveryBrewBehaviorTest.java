package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class EveryBrewBehaviorTest {
    @TestFactory
    Stream<DynamicContainer> oneSuitePerBehaviorPrimitive() {
        return Stream.of(BrewBehavior.values()).map(behavior -> DynamicContainer.dynamicContainer(
            behavior.id(),
            List.of(
                DynamicTest.dynamicTest("identifier resolves", () -> identifierResolves(behavior)),
                DynamicTest.dynamicTest("codec round trips", () -> codecRoundTrips(behavior)),
                DynamicTest.dynamicTest("built-in brew exercises primitive", () -> builtInCoverage(behavior))
            )
        ));
    }

    private static void identifierResolves(final BrewBehavior behavior) {
        assertEquals(behavior, BrewBehavior.find(behavior.id()).orElseThrow());
        assertTrue(BrewBehavior.find("missing_" + behavior.id()).isEmpty());
    }

    private static void codecRoundTrips(final BrewBehavior behavior) {
        final var encoded = BrewBehavior.CODEC.encodeStart(JsonOps.INSTANCE, behavior).result().orElseThrow();
        assertEquals(behavior, BrewBehavior.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    private static void builtInCoverage(final BrewBehavior behavior) {
        assertTrue(
            BrewKind.builtIns().stream().anyMatch(kind -> kind.behaviors().contains(behavior)),
            () -> "No built-in brew exercises " + behavior.id()
        );
    }
}
