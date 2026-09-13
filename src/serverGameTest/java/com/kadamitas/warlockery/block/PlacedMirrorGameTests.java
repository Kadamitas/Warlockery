package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacedMirrorGameTests {
    private PlacedMirrorGameTests() { }

    public static void crouchUseArrivesAtNearestUsablePlacedMirror(final GameTestHelper helper) {
        final var level=helper.getLevel();
        final var server=level.getServer();
        final UUID id=UUID.randomUUID();
        final ServerPlayer player=new ServerPlayer(server,level,
            new GameProfile(id,"mirror_"+id.toString().substring(0,8)),ClientInformation.createDefault());
        final Connection connection=new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel=new EmbeddedChannel(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection,player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
            server.getConnection().getConnections().add(connection);
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            final BlockPos source=helper.absolutePos(new BlockPos(10,2,10));
            final BlockPos nearest=source.east(4);
            final BlockPos farther=source.south(8);
            final List<String> designs=List.of("mirrorblock","mirrorblock2","mirrorwall");
            for(int index=0;index<designs.size();index++) {
                level.setBlockAndUpdate(source,ModBlocks.ALL.get(designs.get(index)).get().defaultBlockState());
                level.setBlockAndUpdate(nearest,ModBlocks.ALL.get(designs.get((index+1)%designs.size())).get().defaultBlockState());
                level.setBlockAndUpdate(farther,ModBlocks.ALL.get(designs.get((index+2)%designs.size())).get().defaultBlockState());
                for(BlockPos mirror:List.of(source,nearest,farther)) {
                    level.setBlockAndUpdate(mirror.below(),Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(mirror.above(),Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(mirror.above(2),Blocks.AIR.defaultBlockState());
                }
                useAndAssertArrival(helper,player,source,nearest,"nearest different mirror design from "+designs.get(index));
                level.setBlockAndUpdate(nearest.above(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(nearest.above(2),Blocks.STONE.defaultBlockState());
                useAndAssertArrival(helper,player,source,farther,"farther usable mirror when the nearest arrival is obstructed");
            }
        } finally {
            GameTestMockPlayers.disconnect(player);
            server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static void useAndAssertArrival(final GameTestHelper helper,final ServerPlayer player,
        final BlockPos source,final BlockPos target,final String context) {
        player.teleportTo(source.getX()+.5,source.getY()+1,source.getZ()+.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.setShiftKeyDown(true);
        try {
            helper.getLevel().getBlockState(source).useWithoutItem(helper.getLevel(),player,
                new BlockHitResult(Vec3.atCenterOf(source),Direction.UP,source,false));
        } finally {
            player.setShiftKeyDown(false);
        }
        final Vec3 expected=new Vec3(target.getX()+.5,target.getY()+1,target.getZ()+.5);
        helper.assertTrue(player.level()==helper.getLevel(),"Mirror travel stays in the source dimension");
        helper.assertTrue(player.position().distanceToSqr(expected)<1.0E-8,
            "Native block interaction arrives exactly above the "+context+"; expected="+expected+", actual="+player.position());
        helper.assertTrue(player.level().noCollision(player),"Selected mirror has collision-free arrival space");
        helper.assertTrue(player.level().getBlockCollisions(player,player.getBoundingBox().move(0,-.02,0)).iterator().hasNext(),
            "The actual selected mirror supports the arriving player's feet");
    }
}
