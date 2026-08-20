package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EntStateTest {
    @Test void freshStateHasExactFixedCardinalityAndDueCooldowns() {
        EntState state = EntState.fresh(1, 2, 3);
        assertEquals(8, EntState.class.getRecordComponents().length);
        assertEquals(new EntState(1, 1, 1, 2, 3, 0, 0, 0), state);
    }

    @Test void normalizationClampsEveryIndependentField() {
        assertEquals(new EntState(1, 1, 30_000_000, -64, -30_000_000, 100, 600, 6000),
            EntState.normalize(1, 5, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -64, 320));
        assertEquals(new EntState(1, 0, 0, 0, 0, 0, 0, 0),
            EntState.normalize(-1, -1, 0, 0, 0, -1, -1, -1, -64, 320));
    }

    @Test void corruptOrMissingAnchorIsReplacedAtLoadPosition() {
        assertEquals(EntState.fresh(65, 0, 0), EntState.fresh(0, 0, 0).reconcileAnchor(65, 0, 0));
        EntState unanchored = new EntState(1, 0, 20, 20, 20, 30, 40, 50);
        assertEquals(new EntState(1, 1, 2, 3, 4, 30, 40, 50), unanchored.reconcileAnchor(2, 3, 4));
    }

    @Test void migrationTreatsZeroAsNeverFiredAndClampsRemaining() {
        assertEquals(0, EntState.migrateLegacyCooldown(0, 100));
        assertEquals(0, EntState.migrateLegacyCooldown(-1, 100));
        assertEquals(25, EntState.migrateLegacyCooldown(125, 100));
        assertEquals(6000, EntState.migrateLegacyCooldown(10_000, 100));
    }

    @Test void durableRecordContainsNoTransientOrGrowableField() {
        for (RecordComponent component : EntState.class.getRecordComponents()) {
            assertFalse(UUID.class.isAssignableFrom(component.getType()));
            assertFalse(Collection.class.isAssignableFrom(component.getType()));
            assertFalse(component.getName().matches("(?i).*phase|target|subject|attacker|path|time.*"));
        }
        assertTrue(Arrays.stream(EntState.class.getRecordComponents()).allMatch(c -> c.getType() == int.class));
    }
}
