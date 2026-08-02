package com.kadamitas.warlockery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

final class WeightedPoolTest {
    @Test
    void selectionIsStableAndReachesEveryWeightedEntry() {
        final WeightedPool<String> pool = WeightedPool.of(
            WeightedPool.entry("common", 12),
            WeightedPool.entry("uncommon", 4),
            WeightedPool.entry("rare", 1)
        );

        assertEquals(pool.select(42L), pool.select(42L));
        assertEquals(17, pool.totalWeight());
        assertEquals(Set.of("common", "uncommon", "rare"), LongStream.range(0, 10_000)
            .mapToObj(pool::select)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void invalidPoolsCannotHideBrokenWeights() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedPool<>(java.util.List.of()));
        assertThrows(IllegalArgumentException.class, () -> WeightedPool.entry("broken", 0));
        assertTrue(WeightedPool.entry("valid", 1).weight() > 0);
    }
}

