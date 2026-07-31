package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ChalkCircleLayoutTest {
    @Test
    void traditionalRingsHaveExactDistinctGeometry() {
        assertRing(ChalkCircleLayout.Size.SMALL, 7, 16);
        assertRing(ChalkCircleLayout.Size.MEDIUM, 11, 28);
        assertRing(ChalkCircleLayout.Size.LARGE, 15, 40);
    }

    @Test
    void legacyCountsChooseClosestDistinctConcentricRings() {
        assertEquals(
            List.of(
                new ChalkCircleLayout.Ring("circleglyph_veil", ChalkCircleLayout.Size.SMALL),
                new ChalkCircleLayout.Ring("circleglyphritual", ChalkCircleLayout.Size.MEDIUM)
            ),
            ChalkCircleLayout.rings(Map.of("circleglyphritual", 12, "circleglyph_veil", 12))
        );
        assertEquals(
            List.of(
                new ChalkCircleLayout.Ring("circleglyph_veil", ChalkCircleLayout.Size.SMALL),
                new ChalkCircleLayout.Ring("circleglyphritual", ChalkCircleLayout.Size.MEDIUM),
                new ChalkCircleLayout.Ring("circleglyphinfernal", ChalkCircleLayout.Size.LARGE)
            ),
            ChalkCircleLayout.rings(Map.of(
                "circleglyphinfernal", 16,
                "circleglyphritual", 12,
                "circleglyph_veil", 8
            ))
        );
    }

    @Test
    void canonicalCountsDescribeWhatPlayersMustActuallyDraw() {
        assertEquals(
            Map.of("circleglyphritual", 16),
            ChalkCircleLayout.canonicalGlyphs(Map.of("circleglyphritual", 8))
        );
        assertEquals(
            Map.of("circleglyph_veil", 16, "circleglyphritual", 28),
            ChalkCircleLayout.canonicalGlyphs(Map.of("circleglyphritual", 12, "circleglyph_veil", 12))
        );
        assertEquals(ChalkCircleLayout.Size.LARGE, ChalkCircleLayout.Size.forMarkCount(40));
        assertEquals(ChalkCircleLayout.Size.SMALL, ChalkCircleLayout.Size.forOfferingCount(1));
        assertEquals(ChalkCircleLayout.Size.MEDIUM, ChalkCircleLayout.Size.forOfferingCount(2));
        assertEquals(ChalkCircleLayout.Size.LARGE, ChalkCircleLayout.Size.forOfferingCount(3));
    }

    private static void assertRing(
        final ChalkCircleLayout.Size size,
        final int diameter,
        final int marks
    ) {
        assertEquals(diameter, size.diameter());
        assertEquals(marks, size.markCount());
        assertEquals(marks, new HashSet<>(size.offsets()).size());
        assertFalse(size.offsets().stream().anyMatch(offset -> offset.getX() == 0 && offset.getZ() == 0));
    }
}
