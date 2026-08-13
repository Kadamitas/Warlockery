package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.GoblinSettlementLifeRules;
import com.kadamitas.warlockery.entity.GoblinSettlementLifeRuntime;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class GoblinSettlementLifeGameTests {
    private GoblinSettlementLifeGameTests() {
    }

    public static void goblinHutConsumesMaterialsAndRespectsPersistentCaps(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-2, 0, -2), new BlockPos(4, 0, 4))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final HobgoblinEntity builder = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(0, 1, 0), EntitySpawnReason.EVENT
        );
        GoblinSettlementLifeData.get(helper.getLevel()).clearForGameTest(
            GoblinSettlementLifeRules.settlementKey(builder.blockPosition(), builder.creatureKind())
        );
        builder.setNoAi(true);
        builder.getInventory().addItem(new ItemStack(Items.DIRT, 64));
        builder.getInventory().addItem(new ItemStack(Items.OAK_LOG, 64));
        final int dirtBefore = count(builder, Items.DIRT);
        final int logsBefore = count(builder, Items.OAK_LOG);
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        BlockPos.betweenClosedStream(relativeCenter.offset(-1, 0, -2), relativeCenter.offset(1, 2, 1))
            .forEach(position -> helper.setBlock(position, Blocks.AIR));
        BlockPos.betweenClosedStream(relativeCenter.offset(-1, -1, -2), relativeCenter.offset(1, -1, 1))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final BlockPos center = helper.absolutePos(relativeCenter);
        helper.assertTrue(GoblinSettlementLifeRuntime.tryBuildHutAt(builder, helper.getLevel(), center),
            "a valid hut must build from complete inventory materials");
        helper.assertValueEqual(
            count(builder, Items.DIRT),
            dirtBefore - GoblinSettlementLifeRules.HUT_DIRT_COST,
            "dirt consumed by one hut"
        );
        helper.assertValueEqual(
            count(builder, Items.OAK_LOG),
            logsBefore - GoblinSettlementLifeRules.HUT_LOG_COST,
            "logs autocrafted into planks by one hut"
        );
        final int dirtAfterBuild = count(builder, Items.DIRT);
        helper.assertTrue(!GoblinSettlementLifeRuntime.tryBuildHutAt(builder, helper.getLevel(), center),
            "an occupied footprint must fail atomically");
        helper.assertValueEqual(count(builder, Items.DIRT), dirtAfterBuild,
            "failed placement must not consume materials");
        final long key = GoblinSettlementLifeRules.settlementKey(builder.blockPosition(), builder.creatureKind());
        final GoblinSettlementLifeData data = GoblinSettlementLifeData.get(helper.getLevel());
        helper.assertTrue(data.reserveHut(key, center.offset(20, 0, 0)), "second hut reservation");
        helper.assertTrue(data.reserveHut(key, center.offset(40, 0, 0)), "third hut reservation");
        helper.assertTrue(!data.reserveHut(key, center.offset(60, 0, 0)),
            "persistent settlement data must reject a fourth hut");
        helper.succeed();
    }

    public static void goblinChildrenGatherDanceAndGiftFlowers(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-4, 0, -4), new BlockPos(6, 0, 6))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        BlockPos.betweenClosedStream(new BlockPos(-4, 1, -4), new BlockPos(6, 3, 6))
            .forEach(position -> helper.setBlock(position, Blocks.AIR));
        final ServerPlayer player = connectedSurvivalPlayer(helper, new BlockPos(2, 1, 2));
        final HobgoblinEntity first = baby(helper, new BlockPos(1, 1, 1));
        final HobgoblinEntity second = baby(helper, new BlockPos(2, 1, 1));
        final HobgoblinEntity third = baby(helper, new BlockPos(1, 1, 2));
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.DIRT);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.POPPY);
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 0)).is(Blocks.POPPY),
            "the flower fixture must survive before the child gathers it");
        helper.assertTrue(first.isBaby(), "the flower gatherer must retain its baby state");
        helper.assertTrue(first.getMainHandItem().isEmpty(), "the flower gatherer must begin with an empty hand");
        helper.assertTrue(helper.getLevel().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING),
            "the flower fixture must permit mob world interaction");
        final BlockPos absoluteFlower = helper.absolutePos(new BlockPos(1, 1, 0));
        helper.assertTrue(first.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(absoluteFlower)) <= 4.0,
            "the flower fixture must begin within immediate gathering range");
        final var nearestFlower = BlockPos.betweenClosedStream(
                first.blockPosition().offset(-6, -2, -6),
                first.blockPosition().offset(6, 2, 6)
            )
            .map(BlockPos::immutable)
            .filter(position -> helper.getLevel().getBlockState(position).is(net.minecraft.tags.BlockTags.SMALL_FLOWERS))
            .min(Comparator.comparingDouble(position ->
                first.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(position))));
        helper.assertTrue(nearestFlower.isPresent(), "the baby goblin must discover the tagged flower fixture");
        helper.assertValueEqual(nearestFlower.orElseThrow(), absoluteFlower, "nearest flower fixture position");
        helper.assertValueEqual(
            GoblinSettlementLifeRuntime.nearestGatherableFlower(first, helper.getLevel()).orElseThrow(),
            absoluteFlower,
            "runtime flower target position"
        );
        helper.assertTrue(GoblinSettlementLifeRuntime.gatherNearestFlower(first, helper.getLevel()),
            "a nearby baby goblin must gather a flower into its visible hand");
        helper.assertTrue(first.getMainHandItem().is(Items.POPPY),
            "the gathered flower must be held rather than silently deleted");
        helper.assertTrue(GoblinSettlementLifeRuntime.danceWithNearbyChildren(second, helper.getLevel()),
            "three matching children must start a bounded circle dance");
        helper.assertTrue(GoblinSettlementLifeRuntime.danceWithNearbyChildren(third, helper.getLevel()),
            "every child in the group must receive a dance path");
        helper.assertTrue(GoblinSettlementLifeRuntime.offerFlower(first, player, helper.getLevel()),
            "a child holding a flower must offer it to a nearby player");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.POPPY)),
            "the offered flower must reach the player's inventory");
        helper.assertTrue(!GoblinSettlementLifeRuntime.offerFlower(first, player, helper.getLevel()),
            "the persisted gift cooldown and empty hand must prevent repeated gifts");
        helper.succeed();
    }

    public static void goblinTunnelIsSingleBoundedAndProtectsContainers(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-2, 0, -2), new BlockPos(12, 4, 4))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final HobgoblinEntity miner = helper.spawn(
            ModEntities.GOBLIN.get(), new BlockPos(0, 1, 1), EntitySpawnReason.EVENT
        );
        miner.setNoAi(true);
        final BlockPos first = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.assertTrue(GoblinSettlementLifeRuntime.tryExcavateTunnelAt(
            miner, helper.getLevel(), first, net.minecraft.core.Direction.EAST
        ), "the first complete tunnel plan must excavate");
        final long air = BlockPos.betweenClosedStream(first, first.east(4).above())
            .filter(position -> helper.getLevel().getBlockState(position).isAir())
            .count();
        helper.assertTrue(air <= GoblinSettlementLifeRules.TUNNEL_EDIT_CAP,
            "a tunnel must never exceed its edit budget");
        helper.assertTrue(!GoblinSettlementLifeRuntime.tryExcavateTunnelAt(
            miner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 3)), net.minecraft.core.Direction.EAST
        ), "persistent settlement data must reject a second tunnel");
        final BlockPos protectedEntrance = helper.absolutePos(new BlockPos(2, 2, 0));
        helper.getLevel().setBlockAndUpdate(protectedEntrance.east(2), Blocks.CHEST.defaultBlockState());
        final HobgoblinEntity otherSpecies = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(0, 1, 0), EntitySpawnReason.EVENT
        );
        otherSpecies.setNoAi(true);
        helper.assertTrue(!GoblinSettlementLifeRuntime.tryExcavateTunnelAt(
            otherSpecies, helper.getLevel(), protectedEntrance, net.minecraft.core.Direction.EAST
        ), "tunnels must never destroy block entities");
        helper.assertTrue(helper.getLevel().getBlockState(protectedEntrance.east(2)).is(Blocks.CHEST),
            "a rejected tunnel must leave protected terrain unchanged");
        helper.succeed();
    }

    private static HobgoblinEntity baby(final GameTestHelper helper, final BlockPos position) {
        final HobgoblinEntity child = helper.spawn(ModEntities.HOBGOBLIN.get(), position, EntitySpawnReason.BREEDING);
        child.setAge(-24_000);
        child.setNoAi(true);
        child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return child;
    }

    private static int count(final HobgoblinEntity goblin, final net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < goblin.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = goblin.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper, final BlockPos position) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos absolute = helper.absolutePos(position);
        player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return player;
    }
}
