package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ManualNavigationTest {
    @Test
    void wrappedEntriesFitCompletelyWithoutOverlappingTheNextRow() throws Exception {
        assertEquals(2, end(List.of(30, 41, 19, 30), 0, 77));
        assertEquals(3, end(List.of(30, 41, 19, 30), 1, 77));
        assertEquals(4, end(List.of(30, 41, 19, 30), 2, 77));
    }

    @Test
    void selectingBelowTheViewportRevealsTheEntireLongEntry() throws Exception {
        final Class<?> type = helper();
        final var offset = type.getDeclaredMethod("reveal", List.class, int.class, int.class, int.class);
        assertEquals(1, offset.invoke(null, List.of(30, 41, 19, 30), 0, 2, 77));
        assertEquals(2, offset.invoke(null, List.of(30, 41, 19, 30), 0, 3, 77));
        assertEquals(0, offset.invoke(null, List.of(30, 41, 19, 30), 2, 0, 77));
        assertEquals(2, offset.invoke(null, List.of(30, 41, 19, 30), 2, 3, 77));
    }

    @Test
    void emptySearchResultsAndExactFitsHaveValidRanges() throws Exception {
        assertEquals(0, end(List.of(), 0, 100));
        assertEquals(2, end(List.of(30, 41, 19), 0, 73));
        assertEquals(1, end(List.of(30, 41, 19), 0, 72));
    }

    private static Object end(final List<Integer> heights, final int offset, final int space) throws Exception {
        return helper().getDeclaredMethod("end", List.class, int.class, int.class)
            .invoke(null, heights, offset, space);
    }

    private static Class<?> helper() {
        return assertDoesNotThrow(() -> Class.forName("com.kadamitas.warlockery.client.ManualNavigation"));
    }
}
