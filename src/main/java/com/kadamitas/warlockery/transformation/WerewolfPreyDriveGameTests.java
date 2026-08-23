package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

public final class WerewolfPreyDriveGameTests {
    private WerewolfPreyDriveGameTests() {
    }

    public static void huntsValidPrey(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginWerewolf(player);
        SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLF);
        player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1.0, 2.5)));
        player.setDeltaMovement(0.0, -0.13, 0.0);
        final Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(7, 1, 2));
        cow.setNoAi(true);
        cow.setHealth(4.0F);
        helper.assertTrue(WerewolfPreyDriveRuntime.tryStartEpisode(player),
            "deterministic real selection acquires valid prey");
        helper.assertValueEqual(WerewolfPreyDriveRuntime.targetEntityId(player), cow.getId(), "prey target synchronizes");
        WerewolfPreyDriveRuntime.tick(player);
        helper.assertTrue(player.getDeltaMovement().x > 0.0,
            "server pursuit accelerates toward selected prey");
        helper.assertTrue(player.getDeltaMovement().horizontalDistance() <= WerewolfPreyDriveRules.MAX_PURSUIT_SPEED,
            "server pursuit enforces its horizontal speed bound");
        helper.assertValueEqual(player.getDeltaMovement().y, -0.13,
            "server pursuit preserves vertical physics");
        player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1.0, 2.5)));
        WerewolfPreyDriveRuntime.tick(player);
        helper.assertFalse(cow.isAlive(), "server-authorized prey attack reaches a kill");
        helper.assertValueEqual(WerewolfPreyDriveRuntime.targetEntityId(player), -1,
            "prey target clears after the server-authorized kill");
        helper.assertTrue(WerewolfPreyDriveRuntime.coolingDown(player),
            "the completed hunt preserves its cooldown");
        WerewolfPreyDriveRuntime.release(player);
        helper.succeed();
    }

    public static void releasesInvalidTarget(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginWerewolf(player);
        SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLFMAN);
        player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1.0, 2.5)));
        final Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 2));
        final Horse horse = helper.spawn(EntityTypes.HORSE, new BlockPos(5, 1, 2));
        horse.setTamed(true);
        helper.assertTrue(WerewolfPreyDriveRuntime.protectedIdentity(horse),
            "tamed owned entities outside TamableAnimal remain protected prey");
        final Zombie babyZombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
        babyZombie.setBaby(true);
        babyZombie.setNoAi(true);
        helper.assertTrue(WerewolfPreyDriveRuntime.juvenile(babyZombie),
            "non-AgeableMob juveniles remain protected prey");
        helper.assertTrue(WerewolfPreyDriveRuntime.tryStartEpisode(player),
            "deterministic real selection acquires prey");
        helper.assertValueEqual(WerewolfPreyDriveRuntime.targetEntityId(player), cow.getId(),
            "deterministic real selection acquires prey");
        cow.setCustomName(Component.translatable("entity.minecraft.cow"));
        WerewolfPreyDriveRuntime.tick(player);
        helper.assertValueEqual(WerewolfPreyDriveRuntime.targetEntityId(player), -1, "named prey releases immediately");
        helper.assertTrue(WerewolfPreyDriveRuntime.coolingDown(player), "normal invalidation preserves episode cooldown");
        WerewolfPreyDriveRuntime.release(player);
        helper.assertFalse(WerewolfPreyDriveRuntime.coolingDown(player), "lifecycle release clears ephemeral cooldown");
        helper.succeed();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
