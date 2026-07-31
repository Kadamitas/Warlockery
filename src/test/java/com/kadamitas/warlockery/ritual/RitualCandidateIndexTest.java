package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RitualCandidateIndexTest {
    @Test
    void factoryGroupsCandidatesAndBuildsImmutableCounts() {
        final RitualCandidateIndex<Character, String> index = RitualCandidateIndex.create(
            List.of("ash", "alder", "rowan"),
            value -> value.charAt(0)
        );

        assertEquals(List.of("ash", "alder"), index.candidates('a'));
        assertEquals(2, index.counts().get('a'));
        assertEquals(1, index.counts().get('r'));
        assertThrows(UnsupportedOperationException.class, () -> index.counts().put('x', 1));
    }

    @Test
    void indexDoesNotRetainMutableInputCollections() {
        final List<String> candidates = new ArrayList<>(List.of("spirit", "spectre"));
        final RitualCandidateIndex<Integer, String> index = RitualCandidateIndex.create(candidates, String::length);

        candidates.clear();

        assertEquals(List.of("spirit"), index.candidates(6));
        assertThrows(UnsupportedOperationException.class, () -> index.candidates(7).add("banshee"));
    }

    @Test
    void matchingQueriesOperateOnGroupedKeys() {
        final RitualCandidateIndex<Character, String> index = RitualCandidateIndex.create(
            List.of("imp", "illusion", "spectre"),
            value -> value.charAt(0)
        );

        assertTrue(index.anyKey(key -> key == 's'));
        assertEquals('i', index.largestMatching(key -> true).orElseThrow().getKey());
    }
}
