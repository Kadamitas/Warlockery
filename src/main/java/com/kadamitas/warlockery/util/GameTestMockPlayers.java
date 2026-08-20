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
        helper.runBeforeTestEnd(() -> disconnect(player));
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
