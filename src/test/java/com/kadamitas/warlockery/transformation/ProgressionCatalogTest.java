package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProgressionCatalogTest {
    @Test
    void precomputesCumulativeImmutableUnlocksForEveryLevel() {
        final ProgressionCatalog<TestAbility, TestQuest> catalog = ProgressionCatalog.create(
            List.of(
                new TestQuest(1, Set.of(TestAbility.SIGHT)),
                new TestQuest(2, Set.of(TestAbility.SPEED)),
                new TestQuest(3, Set.of(TestAbility.STRENGTH))
            ),
            TestAbility.class
        );

        assertEquals(Set.of(), catalog.abilitiesAt(-1));
        assertEquals(Set.of(TestAbility.SIGHT), catalog.abilitiesAt(1));
        assertEquals(Set.of(TestAbility.SIGHT, TestAbility.SPEED), catalog.abilitiesAt(2));
        assertEquals(Set.of(TestAbility.SIGHT, TestAbility.SPEED, TestAbility.STRENGTH), catalog.abilitiesAt(99));
        assertEquals(2, catalog.minimumLevel(TestAbility.SPEED));
        assertThrows(UnsupportedOperationException.class, () -> catalog.abilitiesAt(3).clear());
    }

    @Test
    void indexesOrderedQuestsAndRejectsCatalogGaps() {
        final ProgressionCatalog<TestAbility, TestQuest> catalog = ProgressionCatalog.create(
            List.of(
                new TestQuest(1, Set.of(TestAbility.SIGHT)),
                new TestQuest(2, Set.of(TestAbility.SPEED))
            ),
            TestAbility.class
        );

        assertEquals(1, catalog.activeQuest(-10).orElseThrow().targetLevel());
        assertEquals(2, catalog.questForTargetLevel(2).orElseThrow().targetLevel());
        assertTrue(catalog.activeQuest(2).isEmpty());
        assertTrue(catalog.questForTargetLevel(0).isEmpty());
        assertThrows(
            IllegalArgumentException.class,
            () -> ProgressionCatalog.create(
                List.of(new TestQuest(2, Set.of(TestAbility.SIGHT))),
                TestAbility.class
            )
        );
    }

    @Test
    void enumCollectionCopiesAreIndependentAndRetainOnlyAcceptedEntries() {
        final EnumMap<TestAbility, Integer> source = new EnumMap<>(TestAbility.class);
        source.put(TestAbility.SIGHT, 1);
        source.put(TestAbility.SPEED, 0);

        final Map<TestAbility, Integer> positive = ProgressionCollections.immutableEnumMap(
            TestAbility.class,
            source,
            (ability, value) -> value > 0
        );
        source.put(TestAbility.SIGHT, 5);

        assertEquals(Map.of(TestAbility.SIGHT, 1), positive);
        assertThrows(UnsupportedOperationException.class, () -> positive.put(TestAbility.STRENGTH, 1));
    }

    private enum TestAbility {
        SIGHT,
        SPEED,
        STRENGTH
    }

    private record TestQuest(int targetLevel, Set<TestAbility> abilities)
        implements ProgressionQuest<TestAbility> {
    }
}
