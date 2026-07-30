package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class AltarOverlayLayoutTest {
    @Test
    void connectedAltarUsesOneStableAnchorAndCentersItsDisplay() {
        final List<BlockPos> altar = List.of(
            new BlockPos(10, 64, 20), new BlockPos(11, 64, 20),
            new BlockPos(10, 64, 21), new BlockPos(11, 64, 21),
            new BlockPos(10, 64, 22), new BlockPos(11, 64, 22)
        );
        final AltarOverlayLayout.Placement placement = AltarOverlayLayout.place(altar);
        assertEquals(new BlockPos(10, 64, 20), placement.anchor());
        assertEquals(1.0, placement.position().x(), 0.0001);
        assertEquals(1.3, placement.position().y(), 0.0001);
        assertEquals(1.5, placement.position().z(), 0.0001);
    }

    @Test
    void anchorDoesNotDependOnDiscoveryOrder() {
        final Set<BlockPos> reversed = new LinkedHashSet<>(List.of(
            new BlockPos(4, 20, 5),
            new BlockPos(3, 20, 5),
            new BlockPos(4, 20, 4),
            new BlockPos(3, 20, 4)
        ));
        assertEquals(new BlockPos(3, 20, 4), AltarOverlayLayout.place(reversed).anchor());
    }

    @Test
    void emptyAltarCannotCreateADisplay() {
        assertThrows(IllegalArgumentException.class, () -> AltarOverlayLayout.place(List.of()));
    }

    @Test
    void removedAltarProducesNoTransientDisplay() {
        assertTrue(AltarOverlayLayout.placeIfPresent(List.of()).isEmpty());
        assertTrue(AltarOverlayLayout.placeIfPresent(Collections.singletonList(null)).isEmpty());
    }
}
