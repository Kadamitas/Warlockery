package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class MagicPathOfferingGameTests {
    private MagicPathOfferingGameTests() { }

    public static void passiveMagnetPreservesOfferingsAndResumesAfterHeartRemoval(final GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(20, 1, 16)))
            helper.setBlock(pos, Blocks.STONE);
        final BlockPos heart = new BlockPos(5, 2, 8);
        helper.setBlock(heart, ModBlocks.ALL.get("circle").get());
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        GameTestMockPlayers.autoDisconnect(helper, player);
        // EmbeddedChannel is not registered in the server connection listener. Supply its normal
        // network tick so player events and pickup run; never invoke the passive or inject movement.
        helper.onEachTick(() -> player.connection.tick());
        final BlockPos stance = helper.absolutePos(new BlockPos(11, 2, 8));
        player.teleportTo(stance.getX() + .5, stance.getY(), stance.getZ() + .5);
        MagicPathState.grantPermanent(player, MagicPath.OVERWORLD);
        final ItemEntity offering = drop(helper, heart, Items.GLOWSTONE_DUST);
        final ItemEntity ordinary = drop(helper, new BlockPos(15, 2, 8), Items.IRON_INGOT);
        final double offeringX = offering.getX(), ordinaryX = ordinary.getX();
        helper.assertTrue(offering.getItem().is(MagicCompatibilityTags.METAL_DROPS), "Glowstone actually qualifies for the passive magnet");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(offering.isAlive() && Math.abs(offering.getX() - offeringX) < .1,
                "Normal player ticks leave an uncast heart's Glowstone offering in place");
            helper.assertTrue(!ordinary.isAlive() || ordinary.getX() < ordinaryX - .5,
                "The same passive still attracts ordinary metal outside the offering zone; player=" + player.position()
                    + " playerTicks=" + player.tickCount + " alive=" + player.isAlive()
                    + " sameLevel=" + (player.level() == helper.getLevel())
                    + " infused=" + MagicPathState.has(player, MagicPath.OVERWORLD)
                    + " ordinary=" + ordinary.position() + " initialX=" + ordinaryX
                    + " velocity=" + ordinary.getDeltaMovement()
                    + " tagged=" + ordinary.getItem().is(MagicCompatibilityTags.METAL_DROPS)
                    + " zones=" + RitualOfferingProtection.nearbyZones(helper.getLevel(), player.getBoundingBox().inflate(6)));
            helper.setBlock(heart, Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(!offering.isAlive() && player.getInventory().countItem(Items.GLOWSTONE_DUST) == 1,
                "Removing the physical heart lets natural passive ticks pull and pick up the former offering");
            helper.succeed();
        });
    }

    private static ItemEntity drop(final GameTestHelper helper, final BlockPos relative, final net.minecraft.world.item.Item item) {
        final BlockPos pos = helper.absolutePos(relative);
        final ItemEntity drop = new ItemEntity(helper.getLevel(), pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5,
            new ItemStack(item));
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(drop);
        return drop;
    }
}
