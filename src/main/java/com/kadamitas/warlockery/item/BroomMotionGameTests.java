package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.BroomEntity;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class BroomMotionGameTests {
    private BroomMotionGameTests() {
    }

    public static void mountedBroomMovesForwardAndKeepsItsRider(final GameTestHelper helper) {
        final ServerPlayer rider = connectedSurvivalPlayer(helper);
        rider.setYRot(0.0F);
        rider.setYHeadRot(0.0F);
        rider.setXRot(0.0F);
        final Vec3 expectedDirection = rider.getLookAngle().normalize();
        final ItemStack broomStack = new ItemStack(ModItems.ALL.get("ingredient_broom_enchanted").get());
        rider.setItemInHand(InteractionHand.MAIN_HAND, broomStack);

        broomStack.use(helper.getLevel(), rider, InteractionHand.MAIN_HAND);
        helper.assertTrue(rider.getVehicle() instanceof BroomEntity,
            "using an enchanted broom must create and mount its vehicle");
        final BroomEntity broom = (BroomEntity) rider.getVehicle();
        broom.setControlInput(new FlyingBroomRules.ControlInput(0.0D, 1.0D, false));
        final Vec3 startingPosition = broom.position();

        helper.runAfterDelay(12, () -> {
            final Vec3 displacement = broom.position().subtract(startingPosition);
            helper.assertTrue(broom.isAlive() && !broom.isRemoved(),
                "the broom must remain alive while its rider is attached");
            helper.assertTrue(rider.getVehicle() == broom && broom.getControllingPassenger() == rider,
                "vehicle and passenger state must remain synchronized during motion");
            helper.assertTrue(broom.getPassengers().contains(rider),
                "the broom passenger list must retain its rider");
            helper.assertTrue(displacement.length() > 0.75D,
                "the mounted broom must move meaningfully over live server ticks");
            helper.assertTrue(displacement.dot(expectedDirection) > 0.6D,
                "pressing forward must drive the mounted broom along the rider's yaw");
            helper.assertTrue(rider.position().distanceToSqr(broom.position()) < 2.0D,
                "the rider position must remain attached to the moving broom");
            rider.stopRiding();
            helper.succeed();
        });
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 4, 1));
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
