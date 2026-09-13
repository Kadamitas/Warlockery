package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class LegacyPlantGameTests {
    private LegacyPlantGameTests() { }

    public static void hexSaplingNativeBonemealGrowsExistingWoodAndDecayableLeaves(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(8, 2, 8);
        final ServerPlayer player = player(helper);
        BlockPos.betweenClosedStream(new BlockPos(3, 1, 3), new BlockPos(13, 14, 13))
            .forEach(p -> helper.setBlock(p, p.getY() == 1 ? Blocks.DIRT : Blocks.AIR));
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(pos.north(2))));
        helper.assertTrue(block("hex_sapling") instanceof SaplingBlock, "Hex Sapling must have native sapling behavior");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block("hex_sapling").asItem()));
        use(helper, player, pos.below(), Direction.UP);
        helper.assertTrue(helper.getBlockState(pos).is(block("hex_sapling")), "native item use plants the sapling");
        final ItemStack meal = new ItemStack(Items.BONE_MEAL, 64);
        player.setItemInHand(InteractionHand.MAIN_HAND, meal);
        for (int attempt = 0; attempt < 64 && helper.getBlockState(pos).is(block("hex_sapling")); attempt++)
            use(helper, player, pos, Direction.UP);
        helper.assertTrue(helper.getBlockState(pos).is(block("hex_log")), "native bonemeal creates the existing Hex Log trunk");
        helper.assertTrue(meal.getCount() < 64, "growth consumes actual bonemeal");
        final long foliage = BlockPos.betweenClosedStream(new BlockPos(3, 2, 3), new BlockPos(13, 14, 13))
            .map(helper::getBlockState).filter(state -> state.is(block("hex_leaves")))
            .peek(state -> helper.assertTrue(state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT),
                "tree foliage has normal decay behavior, unlike deliberately persistent brew foliage"))
            .count();
        helper.assertTrue(foliage > 0, "native configured tree creates existing Hex Leaves");
        helper.succeed();
    }

    public static void vineNativeAttachmentSpreadAndSupportRemoval(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(8, 8, 8);
        final ServerPlayer player = player(helper);
        BlockPos.betweenClosedStream(new BlockPos(5, 1, 7), new BlockPos(11, 13, 9))
            .forEach(p -> helper.setBlock(p, p.getZ() == 9 ? Blocks.STONE : Blocks.AIR));
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(pos.north(2))));
        helper.assertTrue(block("vine") instanceof VineBlock, "Vine must have native attachment and spread behavior");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block("vine").asItem(), 2));
        use(helper, player, pos.south(), Direction.NORTH);
        helper.assertTrue(helper.getBlockState(pos).is(block("vine")) && helper.getBlockState(pos).getValue(VineBlock.SOUTH),
            "native placement attaches only to the clicked supporting wall");
        // Deterministic server regression of vanilla's growth algorithm; the rendered suite waits for natural ticks.
        final RandomSource random = RandomSource.create(73L);
        for (int tick = 0; tick < 512 && countVines(helper) == 1; tick++)
            helper.getBlockState(pos).randomTick(helper.getLevel(), helper.absolutePos(pos), random);
        helper.assertTrue(countVines(helper) > 1, "native vine random ticks grow beyond the planted segment");
        final BlockPos ceiling = new BlockPos(19, 7, 8);
        helper.setBlock(ceiling.above(), Blocks.STONE);
        use(helper, player, ceiling.above(), Direction.DOWN);
        helper.assertTrue(helper.getBlockState(ceiling).is(block("vine")) && helper.getBlockState(ceiling).getValue(VineBlock.UP),
            "native placement supports hanging beneath a ceiling");
        helper.setBlock(ceiling.above(), Blocks.AIR);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getBlockState(ceiling).isAir(), "unsupported isolated vine is removed by native neighbor updates");
            helper.succeed();
        });
    }

    private static long countVines(final GameTestHelper helper) {
        return BlockPos.betweenClosedStream(new BlockPos(5, 1, 7), new BlockPos(11, 13, 9))
            .filter(pos -> helper.getBlockState(pos).is(block("vine"))).count();
    }

    private static Block block(final String id) { return ModBlocks.ALL.get(id).get(); }

    private static void use(final GameTestHelper helper, final ServerPlayer player, final BlockPos pos, final Direction face) {
        final BlockPos absolute = helper.absolutePos(pos);
        final Vec3 hit = Vec3.atCenterOf(absolute).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
        player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(hit, face, absolute, false)));
    }

    private static ServerPlayer player(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
