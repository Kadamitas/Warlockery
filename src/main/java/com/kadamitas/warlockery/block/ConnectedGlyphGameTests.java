package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class ConnectedGlyphGameTests {
    private ConnectedGlyphGameTests() {
    }

    public static void diagonalChalkUpdatesBothEndpointsAndPreservesCardinalRules(final GameTestHelper helper) {
        final BlockPos center = new BlockPos(8, 1, 8);
        BlockPos.betweenClosedStream(new BlockPos(5, 0, 5), new BlockPos(11, 0, 11))
            .forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        helper.setBlock(center, glyph("circleglyphritual"));
        final List<Side> corners = List.of(Side.NORTH_EAST, Side.SOUTH_EAST, Side.SOUTH_WEST, Side.NORTH_WEST);
        final List<String> kinds = List.of("circle", "circleglyphgolden", "circleglyphinfernal", "circleglyph_veil");
        for (int index = 0; index < corners.size(); index++) {
            final Side side = corners.get(index);
            final BlockPos corner = offset(center, side);
            helper.setBlock(corner, glyph(kinds.get(index)));
            assertConnection(helper, center, side, true, "placing a mixed-color corner updates the existing glyph");
            assertConnection(helper, corner, side.rotateQuarterTurns(2), true, "the new corner connects back");
            assertNoCardinals(helper, center);
            helper.setBlock(corner, Blocks.AIR);
            assertConnection(helper, center, side, false, "removing a corner clears the opposite endpoint immediately");
        }
        for (int index = 0; index < corners.size(); index++) {
            helper.setBlock(offset(center, corners.get(index)), glyph(kinds.get(index)));
        }
        helper.runAfterDelay(2, () -> {
            helper.setBlock(center, glyph("circleglyphinfernal"));
            for (final Side side : corners) {
                assertConnection(helper, center, side, true, "recoloring preserves each outgoing corner");
                assertConnection(helper, offset(center, side), side.rotateQuarterTurns(2), true,
                    "recoloring preserves each incoming corner");
            }
            assertNoCardinals(helper, center);
            helper.setBlock(offset(center, Side.NORTH_EAST), Blocks.STONE);
            assertConnection(helper, center, Side.NORTH_EAST, false, "a solid block is not diagonal chalk");
            helper.setBlock(offset(center, Side.NORTH_EAST).above(), glyph("circleglyphritual"));
            assertConnection(helper, center, Side.NORTH_EAST, false, "chalk one block higher cannot connect");
            helper.setBlock(offset(center, Side.SOUTH_WEST).below(), Blocks.AIR);
            helper.assertTrue(helper.getBlockState(offset(center, Side.SOUTH_WEST)).isAir(),
                "removing the support removes its chalk");
            assertConnection(helper, center, Side.SOUTH_WEST, false, "support removal clears the diagonal neighbor");
            helper.setBlock(center.east(), glyph("circleglyph_veil"));
            assertConnection(helper, center, Side.EAST, true, "cardinal connection still updates normally");
            assertConnection(helper, center.east(), Side.WEST, true, "the cardinal endpoint connects back");
            helper.setBlock(center.east(), Blocks.AIR);
            assertConnection(helper, center, Side.EAST, false, "cardinal removal remains independent of corners");
            assertTransforms(helper);
        });
        helper.runAfterDelay(6, () -> {
            assertConnection(helper, center, Side.SOUTH_EAST, true, "an untouched opposite corner stays connected");
            assertConnection(helper, center, Side.NORTH_WEST, true, "the other untouched corner stays connected");
            assertConnection(helper, center, Side.NORTH_EAST, false, "vertical chalk stays excluded after ticks");
            assertConnection(helper, center, Side.SOUTH_WEST, false, "removed chalk stays disconnected after ticks");
            final int savedStateFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            helper.getLevel().setBlock(helper.absolutePos(center), glyph("circleglyphinfernal")
                .setValue(ConnectedGlyphBlock.NORTH_EAST, true), savedStateFlags);
            helper.getLevel().setBlock(helper.absolutePos(offset(center, Side.SOUTH_EAST)),
                glyph("circleglyphgolden"), savedStateFlags);
            helper.getLevel().setBlock(helper.absolutePos(offset(center, Side.NORTH_WEST)),
                glyph("circleglyph_veil"), savedStateFlags);
            assertConnection(helper, center, Side.SOUTH_EAST, false, "saved states can start without diagonal properties enabled");
            assertConnection(helper, center, Side.NORTH_EAST, true, "saved states can contain a stale connection");
            ConnectedGlyphChunkRefresh.queue(helper.getLevel(), ChunkPos.containing(helper.absolutePos(center)));
        });
        helper.runAfterDelay(8, () -> helper.succeedWhen(() -> {
            for (final Side side : List.of(Side.SOUTH_EAST, Side.NORTH_WEST)) {
                assertConnection(helper, center, side, true, "chunk refresh repairs existing circles without redrawing");
                assertConnection(helper, offset(center, side), side.rotateQuarterTurns(2), true,
                    "chunk refresh repairs the opposite saved endpoint");
            }
            assertConnection(helper, center, Side.NORTH_EAST, false, "chunk refresh clears a stale diagonal");
            assertNoCardinals(helper, center);
        }));
    }

    private static void assertTransforms(final GameTestHelper helper) {
        for (final Side side : Side.values()) {
            final BlockState state = glyph("circleglyphritual").setValue(ConnectedGlyphBlock.ALL_CONNECTIONS.get(side), true);
            for (final Side candidate : Side.values()) {
                helper.assertValueEqual(state.rotate(Rotation.CLOCKWISE_90).getValue(ConnectedGlyphBlock.ALL_CONNECTIONS.get(candidate)),
                    candidate == side.rotateQuarterTurns(1), "rotation moves exactly one enabled arm");
                helper.assertValueEqual(state.mirror(Mirror.LEFT_RIGHT).getValue(ConnectedGlyphBlock.ALL_CONNECTIONS.get(candidate)),
                    candidate == side.mirrorZ(), "left-right mirror reverses north and south");
                helper.assertValueEqual(state.mirror(Mirror.FRONT_BACK).getValue(ConnectedGlyphBlock.ALL_CONNECTIONS.get(candidate)),
                    candidate == side.mirrorX(), "front-back mirror reverses east and west");
            }
        }
    }

    private static BlockState glyph(final String id) { return ModBlocks.ALL.get(id).get().defaultBlockState(); }
    private static BlockPos offset(final BlockPos center, final Side side) { return center.offset(side.dx(), 0, side.dz()); }
    private static void assertNoCardinals(final GameTestHelper helper, final BlockPos pos) {
        for (final Side side : List.of(Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST)) {
            assertConnection(helper, pos, side, false, "a diagonal must not enable a cardinal arm");
        }
    }
    private static void assertConnection(final GameTestHelper helper, final BlockPos pos, final Side side,
        final boolean connected, final String message) {
        helper.assertValueEqual(helper.getBlockState(pos).getValue(ConnectedGlyphBlock.ALL_CONNECTIONS.get(side)), connected,
            message + ": " + side.id());
    }
}
