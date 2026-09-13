package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.*;

import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ConnectedGlyphLinksTest {
    @Test
    void togglingEitherEndpointChangesOnlyThatUndirectedEdge() {
        final var links = new ConnectedGlyphLinks();
        final var origin = new BlockPos(15, 64, 15);
        for (final Side side : Side.values()) {
            final var neighbor = origin.offset(side.dx(), 0, side.dz());
            assertFalse(links.disabled(origin, side));
            assertTrue(links.toggle(origin, side));
            assertTrue(links.disabled(neighbor, side.rotateQuarterTurns(2)));
            for (final Side other : Side.values()) {
                assertEquals(other == side, links.disabled(origin, other));
            }
            assertFalse(links.toggle(neighbor, side.rotateQuarterTurns(2)));
            assertFalse(links.disabled(origin, side));
        }
    }

    @Test
    void persistencePreservesDisabledCrossingWithoutAffectingTheOtherDiagonal() {
        final var links = new ConnectedGlyphLinks();
        final var origin = new BlockPos(-1, 80, -1);
        links.toggle(origin, Side.SOUTH_EAST);
        final var encoded = ConnectedGlyphLinks.CODEC.encodeStart(JsonOps.INSTANCE, links).getOrThrow();
        final var restored = ConnectedGlyphLinks.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.disabled(origin, Side.SOUTH_EAST));
        assertTrue(restored.disabled(origin.south().east(), Side.NORTH_WEST));
        assertFalse(restored.disabled(origin.east(), Side.SOUTH_WEST));
        assertFalse(restored.disabled(origin.above(), Side.SOUTH_EAST));
        assertFalse(restored.toggle(origin.south().east(), Side.NORTH_WEST));
    }

    @Test
    void removingAnEndpointClearsItsEdgesAndPreservesUnrelatedEdges() {
        final var links = new ConnectedGlyphLinks();
        final var origin = new BlockPos(0, 64, 0);
        for (final Side side : Side.values()) links.toggle(origin, side);
        links.toggle(origin.east(), Side.SOUTH);
        links.remove(origin);
        for (final Side side : Side.values()) {
            assertFalse(links.disabled(origin, side));
            assertFalse(links.disabled(origin.offset(side.dx(), 0, side.dz()), side.rotateQuarterTurns(2)));
        }
        assertTrue(links.disabled(origin.east(), Side.SOUTH));
    }

    @Test
    void eachArmCanBePickedAlongItsWholeLengthWhileTheNodeDoesNothing() {
        for (final Side side : Side.values()) {
            for (final double distance : new double[]{0.15, 0.25, 0.4, 0.49}) {
                assertEquals(Optional.of(side), ConnectedGlyphLinks.clickedSide(
                    0.5 + side.dx() * distance, 0.5 + side.dz() * distance));
                final Side opposite = side.rotateQuarterTurns(2);
                assertEquals(Optional.of(opposite), ConnectedGlyphLinks.clickedSide(
                    0.5 + opposite.dx() * distance, 0.5 + opposite.dz() * distance));
            }
        }
        for (final double x : new double[]{0.45, 0.5, 0.55}) {
            for (final double z : new double[]{0.45, 0.5, 0.55}) {
                assertTrue(ConnectedGlyphLinks.clickedSide(x, z).isEmpty());
            }
        }
        assertTrue(ConnectedGlyphLinks.clickedSide(0.25, 0.4).isEmpty());
    }
}
