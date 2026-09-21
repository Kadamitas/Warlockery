package com.kadamitas.warlockery.util;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Teardown for the mock players a GameTest fixture connects through
 * {@code PlayerList.placeNewPlayer}.
 *
 * <p>{@link Entity#discard()} does not deregister a {@link ServerPlayer} from the player list, so
 * a merely discarded mock player keeps being reported by {@code ServerLevel.players()} for the
 * rest of the run. Fixtures run many to a batch and many batches to a run, so that list grows
 * monotonically: by the last batches it holds every mock player every earlier fixture ever
 * connected. Acquisition sweeps walk that list under a bounded candidate budget, so they spend
 * the whole budget on the corpses of earlier fixtures and never reach the player standing in
 * their own arena.</p>
 *
 * <p>A fixture therefore has to disconnect what it connected, exactly as it discards what it
 * spawned.</p>
 */
public final class GameTestMockPlayers {
    private GameTestMockPlayers() {
    }

    /** Connects a mock player through the normal packet listener for packet-producing fixtures. */
    public static ServerPlayer connect(final GameTestHelper helper, final net.minecraft.world.level.GameType mode) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
        final net.minecraft.network.Connection connection =
            new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(mode);
        return autoDisconnect(helper, player);
    }

    /** Supplies the real bed required by 26.3's low-level sleep transition. */
    public static void sleepInBed(final GameTestHelper helper, final ServerPlayer player,
                                  final net.minecraft.core.BlockPos head) {
        final var level = helper.getLevel();
        final var foot = head.west();
        final var oldHead = level.getBlockState(head);
        final var oldFoot = level.getBlockState(foot);
        final var bed = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getValue(net.minecraft.resources.Identifier.parse("minecraft:red_bed")).defaultBlockState()
            .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST);
        level.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
            net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        level.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
            net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
        helper.runBeforeTestEnd(() -> {
            player.stopSleeping();
            level.setBlock(head, oldHead, 2);
            level.setBlock(foot, oldFoot, 2);
        });
        helper.assertTrue(player.startSleeping(head), "the real bed accepts the fixture's sleep transition");
    }

    /** Discards an ordinary entity and fully disconnects a player. */
    public static void release(final Entity entity) {
        if (entity instanceof final ServerPlayer player) {
            disconnect(player);
            return;
        }
        entity.discard();
    }

    /**
     * Registers {@code player} to be disconnected once the test finishes, pass or fail, and
     * returns it. For fixtures that connect a player without owning a scope object to close.
     */
    public static ServerPlayer autoDisconnect(final GameTestHelper helper, final ServerPlayer player) {
        helper.addCleanup(passed -> disconnect(player));
        return player;
    }

    /** Removes one connected mock player from the server player list and from its level. */
    public static void disconnect(final ServerPlayer player) {
        final MinecraftServer server = player.level().getServer();
        if (server == null) {
            player.discard();
            return;
        }
        server.getPlayerList().remove(player);
    }
}
