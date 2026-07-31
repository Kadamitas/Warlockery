package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.block.entity.AltarMultiblockLayout;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

class AltarMultiblockLayoutTest {
    @Test
    void acceptsOnlyAThreeByTwoRectangle() {
        final Set<BlockPos> blocks = rectangle(3, 2);

        assertTrue(AltarMultiblockLayout.inspect(new BlockPos(1, 0, 1), blocks::contains).valid());
        assertTrue(AltarMultiblockLayout.inspect(new BlockPos(2, 0, 0), blocks::contains).valid());
    }

    @Test
    void rejectsSixConnectedBlocksInAnotherShape() {
        final Set<BlockPos> line = IntStream.range(0, 6)
            .mapToObj(x -> new BlockPos(x, 0, 0))
            .collect(Collectors.toSet());

        assertFalse(AltarMultiblockLayout.inspect(BlockPos.ZERO, line::contains).valid());
    }

    @Test
    void rejectsAnExtendedRectangle() {
        final Set<BlockPos> blocks = rectangle(3, 2);
        blocks.add(new BlockPos(3, 0, 0));
        final AltarMultiblockLayout.Result result = AltarMultiblockLayout.inspect(BlockPos.ZERO, blocks::contains);

        assertFalse(result.valid());
        assertEquals(7, result.connectedBlocks());
    }

    private static Set<BlockPos> rectangle(final int width, final int depth) {
        return IntStream.range(0, width)
            .boxed()
            .flatMap(x -> IntStream.range(0, depth).mapToObj(z -> new BlockPos(x, 0, z)))
            .collect(Collectors.toSet());
    }
}
