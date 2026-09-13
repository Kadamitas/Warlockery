package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
            helper.getLevel().setBlock(helper.absolutePos(center), helper.getBlockState(center)
                .setValue(ConnectedGlyphBlock.SOUTH_EAST, false).setValue(ConnectedGlyphBlock.NORTH_WEST, false)
                .setValue(ConnectedGlyphBlock.NORTH_EAST, true), savedStateFlags);
            helper.getLevel().setBlock(helper.absolutePos(offset(center, Side.SOUTH_EAST)),
                helper.getBlockState(offset(center, Side.SOUTH_EAST)).setValue(ConnectedGlyphBlock.NORTH_WEST, false), savedStateFlags);
            helper.getLevel().setBlock(helper.absolutePos(offset(center, Side.NORTH_WEST)),
                helper.getBlockState(offset(center, Side.NORTH_WEST)).setValue(ConnectedGlyphBlock.SOUTH_EAST, false), savedStateFlags);
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
            assertConnectionEditing(helper, center);
        }));
    }

    private static void assertConnectionEditing(final GameTestHelper helper, final BlockPos center) {
        for (final Side side : Side.values()) {
            helper.setBlock(offset(center, side).below(), Blocks.STONE);
            helper.setBlock(offset(center, side), glyph(side.diagonal() ? "circleglyphgolden" : "circleglyphritual"));
        }
        final Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        final BlockPos absolute = helper.absolutePos(center);
        final var links = ConnectedGlyphLinks.get(helper.getLevel());
        helper.assertValueEqual(helper.getBlockState(center).getBlock().getStateDefinition().getPossibleStates().size(),
            256, "editing must not multiply the chalk blockstate space");
        click(helper, player, center, 0.5, 0.5);
        for (final Side side : Side.values()) assertConnection(helper, center, side, true, "clicking a node does not edit branches");
        for (final Side side : Side.values()) {
            for (final double distance : new double[]{0.15, 0.3, 0.48}) {
                click(helper, player, center, 0.5 + side.dx() * distance, 0.5 + side.dz() * distance);
                assertConnection(helper, center, side, false, "empty-hand clicking the line disconnects its selected edge");
                assertConnection(helper, offset(center, side), side.rotateQuarterTurns(2), false,
                    "the opposite half is disconnected immediately");
                for (final Side other : Side.values()) {
                    if (other != side) assertConnection(helper, center, other, true, "other branches stay connected");
                }
                final Vec3 point = new Vec3(absolute.getX() + 0.5 + side.dx() * distance,
                    absolute.getY(), absolute.getZ() + 0.5 + side.dz() * distance);
                helper.assertTrue(helper.getBlockState(center).getShape(helper.getLevel(), absolute)
                    .clip(point.add(0, 1, 0), point.add(0, -0.1, 0), absolute) != null,
                    "the hidden edge remains selectable for reconnection");
                final Side opposite = side.rotateQuarterTurns(2);
                click(helper, player, offset(center, side), 0.5 + opposite.dx() * distance, 0.5 + opposite.dz() * distance);
                assertConnection(helper, center, side, true, "the other half can reconnect the same edge");
                assertConnection(helper, offset(center, side), opposite, true, "reconnection is symmetric");
            }
        }
        final ItemStack focus = new ItemStack(ModItems.ALL.get("mysticbranch").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, focus);
        helper.assertValueEqual(click(helper, player, center, 0.75, 0.5), InteractionResult.PASS,
            "Arcane Focus must reach its existing ritual item handler");
        assertConnection(helper, center, Side.EAST, true, "held focus does not edit the edge");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.ALL.get("chalkritual").get()));
        helper.assertValueEqual(click(helper, player, center, 0.75, 0.5), InteractionResult.PASS,
            "offhand chalk keeps its native drawing interaction");
        assertConnection(helper, center, Side.EAST, true, "offhand chalk does not edit the edge");
        player.setItemInHand(InteractionHand.OFF_HAND, focus);
        helper.assertValueEqual(click(helper, player, center, 0.75, 0.5), InteractionResult.PASS,
            "offhand Focus keeps its ritual interaction");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        click(helper, player, center, 0.8, 0.8);
        helper.assertTrue(links.disabled(absolute, Side.SOUTH_EAST), "disabled intent exists separately from visible state");
        final var saved = ConnectedGlyphLinks.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, links).getOrThrow();
        final var restored = ConnectedGlyphLinks.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(restored.disabled(absolute.south().east(), Side.NORTH_WEST), "saved intent survives decoding from either endpoint");
        final BlockState rebuilt = ((ConnectedGlyphBlock) helper.getBlockState(center).getBlock())
            .connectedState(helper.getLevel(), absolute);
        helper.assertTrue(!rebuilt.getValue(ConnectedGlyphBlock.SOUTH_EAST), "chunk refresh cannot reconnect an intentionally disabled edge");
        helper.setBlock(offset(center, Side.SOUTH_EAST), Blocks.AIR);
        helper.assertTrue(!links.disabled(absolute, Side.SOUTH_EAST), "erasing an endpoint clears its stale disabled edges");
        helper.setBlock(offset(center, Side.SOUTH_EAST), glyph("circleglyphgolden"));
        assertConnection(helper, center, Side.SOUTH_EAST, true, "redrawing restores default connection behavior");
        click(helper, player, center, 0.2, 0.2);
        helper.setBlock(center, glyph("circle"));
        assertConnection(helper, center, Side.NORTH_WEST, true, "replacing a chalk kind clears stale edge suppression");
        helper.setBlock(center.south().east(), glyph("circle"));
        helper.setBlock(center.east(), glyph("circleglyphgolden"));
        helper.setBlock(center.south(), glyph("circleglyphgolden"));
        click(helper, player, center, 0.98, 0.98);
        assertConnection(helper, center, Side.SOUTH_EAST, false, "a crossed white diagonal can be disconnected");
        assertConnection(helper, center.east(), Side.SOUTH_WEST, true, "the crossing golden diagonal remains connected");
        assertConnection(helper, center.south(), Side.NORTH_EAST, true, "both golden crossing halves remain connected");
        click(helper, player, center.south().east(), 0.02, 0.02);
        assertConnection(helper, center, Side.SOUTH_EAST, true, "the white crossing can reconnect from its far half");
    }

    private static InteractionResult click(final GameTestHelper helper, final Player player, final BlockPos relative,
        final double x, final double z) {
        final BlockPos pos = helper.absolutePos(relative);
        return helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player,
            new BlockHitResult(new Vec3(pos.getX() + x, pos.getY() + 0.005, pos.getZ() + z), Direction.UP, pos, false));
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
