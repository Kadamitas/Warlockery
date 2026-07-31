package com.kadamitas.warlockery.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VampireBloodCapacityRulesTest {
    @Test
    void usesTheDocumentedCapacityAtEveryVampireLevel() {
        assertEquals(
            List.of(0, 750, 1_000, 1_250, 1_500, 1_750, 2_000, 2_250, 2_500, 3_250, 3_500),
            VampireBloodCapacityRules.capacities()
        );
        for (int level = 0; level <= 10; level++) {
            assertEquals(VampireBloodCapacityRules.capacities().get(level), VampireBloodCapacityRules.capacity(level));
        }
    }

    @Test
    void rejectsLevelsOutsideTheProgression() {
        assertThrows(IllegalArgumentException.class, () -> VampireBloodCapacityRules.capacity(-1));
        assertThrows(IllegalArgumentException.class, () -> VampireBloodCapacityRules.capacity(11));
    }
}
