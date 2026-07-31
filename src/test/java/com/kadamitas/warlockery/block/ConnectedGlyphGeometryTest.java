package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ConnectedGlyphGeometryTest {
    @Test
    void everyChalkGlyphIdUsesTheConnectedImplementation() {
        assertEquals(Set.of(
                "circle", "circleglyphgolden", "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
            ),
            ConnectedGlyphGeometry.IDS);
        assertFalse(ConnectedGlyphGeometry.IDS.contains("pentacle"));
    }

    @Test
    void centerOnlyGeometryStaysInsideTheBlockCenter() {
        final ConnectedGlyphGeometry.Bounds center = ConnectedGlyphGeometry.bounds(Set.of());
        assertEquals(5.0, center.minX());
        assertEquals(5.0, center.minZ());
        assertEquals(11.0, center.maxX());
        assertEquals(11.0, center.maxZ());
        assertEquals(1.0, center.maxY());
    }

    @Test
    void geometryOnlyReachesEnabledEdges() {
        final ConnectedGlyphGeometry.Bounds northEast = ConnectedGlyphGeometry.bounds(Set.of(
            ConnectedGlyphGeometry.Side.NORTH,
            ConnectedGlyphGeometry.Side.EAST
        ));
        assertEquals(0.0, northEast.minZ());
        assertEquals(16.0, northEast.maxX());
        assertEquals(5.0, northEast.minX());
        assertEquals(11.0, northEast.maxZ());

        final ConnectedGlyphGeometry.Bounds cross = ConnectedGlyphGeometry.bounds(EnumSet.allOf(ConnectedGlyphGeometry.Side.class));
        assertEquals(0.0, cross.minX());
        assertEquals(0.0, cross.minZ());
        assertEquals(16.0, cross.maxX());
        assertEquals(16.0, cross.maxZ());
        assertTrue(cross.maxY() <= 1.0);
    }
}
