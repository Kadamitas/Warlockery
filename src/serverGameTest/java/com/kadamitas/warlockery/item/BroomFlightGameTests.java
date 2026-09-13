package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.entity.BroomEntity;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BroomFlightGameTests {
    private BroomFlightGameTests() {
    }

    public static void mountStoresDamagesAndReturnsTheExactBroom(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack broom = new ItemStack(ModItems.ALL.get("ingredient_broom_enchanted").get());
        broom.setDamageValue(37);
        player.setItemInHand(InteractionHand.MAIN_HAND, broom);
        broom.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(),
            "mounting must transfer the enchanted broom out of the player's hand");
        helper.assertTrue(player.getVehicle() instanceof BroomEntity,
            "using an enchanted broom in the air must create a rideable broom entity");
        final BroomEntity vehicle = (BroomEntity) player.getVehicle();
        helper.assertValueEqual(vehicle.getBroomStack().getDamageValue(), 37,
            "stored broom damage before flight");
        sendGlideHeartbeat(player);
        // The durability tick fires on the vehicle's own 20th tick, which leaves one tick of
        // slack against a fixed tick-21 assertion; under batch load the entity clock can slip
        // behind the test clock. Poll for the spent durability instead, bounded by max_ticks.
        final boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) {
                return;
            }
            sendGlideHeartbeat(player);
            final int damage = vehicle.getBroomStack().getDamageValue();
            if (damage == 37) {
                return;
            }
            finished[0] = true;
            helper.assertTrue(vehicle.isGliding(),
                "the server-approved glide state must persist while fresh control heartbeats arrive");
            helper.assertValueEqual(damage, 38,
                "one durability is spent after one second of mounted flight");
            player.stopRiding();
            helper.assertTrue(player.getMainHandItem().is(ModItems.ALL.get("ingredient_broom_enchanted").get()),
                "dismounting must return the stored broom to its preferred hand");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 38,
                "returned broom damage");
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(vehicle.isRemoved(),
                    "an unmounted broom entity must be removed on the next safe server tick");
                helper.succeed();
            });
        });
    }

    public static void logoutReturnsBroomBeforePlayerSave(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack broom = new ItemStack(ModItems.ALL.get("ingredient_broom_enchanted").get());
        broom.setDamageValue(91);
        player.setItemInHand(InteractionHand.OFF_HAND, broom);
        broom.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getVehicle() instanceof BroomEntity,
            "the logout lifecycle test requires an active broom mount");
        final BroomEntity vehicle = (BroomEntity) player.getVehicle();
        FlyingBroomItem.handleLogout(player);
        helper.assertTrue(!player.isPassenger(), "logout must dismount the rider before player data is saved");
        helper.assertTrue(vehicle.isRemoved(), "logout must remove the transient broom mount");
        helper.assertTrue(player.getOffhandItem().is(ModItems.ALL.get("ingredient_broom_enchanted").get()),
            "logout must return the exact stored broom before inventory serialization");
        helper.assertValueEqual(player.getOffhandItem().getDamageValue(), 91,
            "logout-returned broom damage");
        helper.succeed();
    }

    public static void legacyCreativeFlightStateIsRemovedOnLogin(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        WarlockeryEntityData.get(player).putBoolean("WarlockeryBroomFlight", true);
        WarlockeryEntityData.get(player).putBoolean("WarlockeryBroomPreviousMayFly", false);
        WarlockeryEntityData.get(player).putFloat("WarlockeryBroomPreviousSpeed", 0.04F);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(0.075F);
        FlyingBroomItem.handleLogin(player);
        helper.assertTrue(!player.getAbilities().mayfly,
            "legacy broom state must not leave survival players with creative flight");
        helper.assertTrue(!player.getAbilities().flying,
            "legacy broom state must stop active creative-style flight");
        helper.assertValueEqual(player.getAbilities().getFlyingSpeed(), 0.04F,
            "pre-broom flying speed restored during migration");
        helper.assertTrue(!WarlockeryEntityData.get(player).contains("WarlockeryBroomFlight"),
            "legacy active marker removed");
        helper.assertTrue(!WarlockeryEntityData.get(player).contains("WarlockeryBroomPreviousMayFly"),
            "legacy mayfly marker removed");
        helper.assertTrue(!WarlockeryEntityData.get(player).contains("WarlockeryBroomPreviousSpeed"),
            "legacy speed marker removed");
        helper.succeed();
    }

    public static void deathUsesVanillaDropKeepAndVanishingRules(final GameTestHelper helper) {
        final var broomItem = ModItems.ALL.get("ingredient_broom_enchanted").get();
        final ServerPlayer droppedPlayer = connectedSurvivalPlayer(helper);
        final ItemStack droppedBroom = new ItemStack(broomItem);
        droppedBroom.setDamageValue(41);
        mount(droppedPlayer, droppedBroom);
        helper.assertTrue(droppedPlayer.getVehicle() instanceof BroomEntity,
            "normal death case requires a mounted broom");
        final Vec3 droppedAt = droppedPlayer.position();
        withKeepInventory(helper, false, () ->
            droppedPlayer.hurtServer(helper.getLevel(), droppedPlayer.damageSources().generic(), 1_000.0F)
        );
        helper.assertTrue(droppedPlayer.isDeadOrDying(), "normal death case must reach vanilla death loot");
        helper.assertTrue(droppedPlayer.getMainHandItem().isEmpty(),
            "normal death must remove the returned broom from the dead player's inventory");

        helper.runAfterDelay(1, () -> {
            final List<ItemEntity> dropped = droppedBrooms(helper, droppedAt);
            helper.assertValueEqual(dropped.size(), 1, "normal death broom item entity count");
            helper.assertValueEqual(dropped.getFirst().getItem().getCount(), 1,
                "normal death dropped broom stack size");
            helper.assertValueEqual(dropped.getFirst().getItem().getDamageValue(), 41,
                "normal death dropped broom damage");
            dropped.forEach(Entity::discard);

            ServerPlayer player = withKeepInventory(
                helper,
                false,
                () -> respawn(droppedPlayer)
            );
            placeAtTestPosition(helper, player);
            final ItemStack retained = new ItemStack(broomItem);
            retained.setDamageValue(73);
            mount(player, retained);
            helper.assertTrue(player.getVehicle() instanceof BroomEntity,
                "keepInventory case requires a mounted broom");
            final ServerPlayer keepInventoryPlayer = player;
            player = withKeepInventory(helper, true, () -> {
                keepInventoryPlayer.hurtServer(
                    helper.getLevel(),
                    keepInventoryPlayer.damageSources().generic(),
                    1_000.0F
                );
                return respawn(keepInventoryPlayer);
            });
            placeAtTestPosition(helper, player);
            helper.assertTrue(player.getMainHandItem().is(broomItem),
                "keepInventory must retain a mounted broom through respawn");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 73,
                "keepInventory-retained broom damage");

            final ItemStack vanishing = new ItemStack(broomItem);
            vanishing.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.VANISHING_CURSE),
                1
            );
            mount(player, vanishing);
            helper.assertTrue(player.getVehicle() instanceof BroomEntity broom && broom.getBroomStack().isEnchanted(),
                "vanishing case requires the cursed broom to be stored on the mount");
            final ServerPlayer vanishingPlayer = player;
            final Vec3 vanishedAt = player.position();
            withKeepInventory(helper, false, () ->
                vanishingPlayer.hurtServer(
                    helper.getLevel(),
                    vanishingPlayer.damageSources().generic(),
                    1_000.0F
                )
            );
            helper.assertTrue(vanishingPlayer.isDeadOrDying(),
                "vanishing case must reach vanilla death loot");
            helper.assertTrue(vanishingPlayer.getMainHandItem().isEmpty(),
                "Curse of Vanishing must consume a mounted broom during vanilla death loot");
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(droppedBrooms(helper, vanishedAt).isEmpty(),
                    "Curse of Vanishing must not create a broom item entity");
                helper.succeed();
            });
        });
    }

    private static List<ItemEntity> droppedBrooms(final GameTestHelper helper, final Vec3 position) {
        final var broom = ModItems.ALL.get("ingredient_broom_enchanted").get();
        return helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            AABB.ofSize(position, 8.0D, 8.0D, 8.0D),
            entity -> entity.isAlive() && entity.getItem().is(broom)
        );
    }

    private static <T> T withKeepInventory(
        final GameTestHelper helper,
        final boolean keepInventory,
        final Supplier<T> action
    ) {
        final var gameRules = helper.getLevel().getGameRules();
        final var server = helper.getLevel().getServer();
        final boolean previous = gameRules.get(GameRules.KEEP_INVENTORY);
        gameRules.set(GameRules.KEEP_INVENTORY, keepInventory, server);
        try {
            return action.get();
        } finally {
            gameRules.set(GameRules.KEEP_INVENTORY, previous, server);
        }
    }

    private static void mount(final ServerPlayer player, final ItemStack broom) {
        player.setItemInHand(InteractionHand.MAIN_HAND, broom);
        broom.use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    private static void sendGlideHeartbeat(final ServerPlayer player) {
        FlyingBroomItem.setControls(player, FlyingBroomRules.ControlInput.IDLE, true);
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        placeAtTestPosition(helper, player);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }

    private static void placeAtTestPosition(final GameTestHelper helper, final ServerPlayer player) {
        final BlockPos position = helper.absolutePos(new BlockPos(1, 4, 1));
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
    }

    private static ServerPlayer respawn(final ServerPlayer player) {
        final var connection = player.connection;
        connection.handleClientCommand(new ServerboundClientCommandPacket(
            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
        ));
        connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return connection.player;
    }
}
