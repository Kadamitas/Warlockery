package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.dream.SpiritWorldState;
import com.kadamitas.warlockery.dream.SpiritManifestationState;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ManifestationRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class SpiritBoundaryClientAcceptance implements FabricClientGameTest {
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final ArrayList<String> screenshots = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("spirit-boundary").resolve(UUID.randomUUID().toString());
        report.put("passed", false);
        report.put("scope", "Real integrated dimensions; native chat generic transfer and creative entry, actual portal contact and Icy Needle use. Ground, inventory, operator permission and already-earned manifestation permission are prerequisites. This does not certify multiplayer permission acquisition or canceled-transfer handling.");
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                world.getConnection().waitForChunksRender();
                server(player -> {
                    final var server = player.level().getServer();
                    server.getPlayerList().op(new NameAndId(player.getGameProfile()),
                        Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
                    server.getPlayerList().sendPlayerPermissionLevel(player);
                    for (var key : java.util.List.of(Level.OVERWORLD, SpiritWorldRuntime.SPIRIT_WORLD)) {
                        final var level = server.getLevel(key);
                        check(level != null, "Both real dimensions must exist");
                        for (int x = -16; x <= 16; x += 16) for (int z = -16; z <= 16; z += 16)
                            level.getChunkAt(new BlockPos(x, 100, z));
                        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 99, -12), new BlockPos(12, 103, 12)))
                            level.setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    player.setGameMode(GameType.SURVIVAL);
                    player.setInvulnerable(true);
                    player.teleportTo(0.5, 100, 0.5);
                    player.setYRot(37);
                    player.setYHeadRot(37);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get(), 2));
                    player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 3));
                    player.inventoryMenu.broadcastChanges();
                });
                arrival(context);
                final Vec3 original = serverValue(ServerPlayer::position);
                command(context, "/execute in warlockery:spirit_world run tp @s 0.5 100 0.5");
                waitServer(context, player -> SpiritWorldRuntime.isSpiritWorld(player.level()) && SpiritWorldRuntime.isDreaming(player),
                    120, "Generic native dimension command must create a Spirit session");
                arrival(context);
                final var session = serverValue(player -> SpiritWorldState.read(player).orElseThrow());
                check(session.sourceDimension().equals(Level.OVERWORLD.identifier()), "Session records the actual source dimension");
                check(serverValue(player -> {
                    final var body = player.level().getServer().overworld().getEntity(session.body());
                    return body instanceof Mannequin mannequin && body.position().equals(original)
                        && body.hasPose(Pose.SLEEPING) && mannequin.getProfile().partialProfile().equals(player.getGameProfile());
                }), "Generic entry leaves the owner's sleeping avatar at the exact source");
                report.put("generic_entry_body", session.body().toString());
                report.put("source", original.toString());
                check(serverValue(player -> player.getInventory().getItem(4).isEmpty()), "Original iron stays in escrow");
                server(player -> SpiritManifestationState.grant(player, player.level().getServer().getTickCount() + 2000));
                final BlockPos portal = session.portal();
                report.put("portal", portal.toShortString());
                // Positioning establishes the approach only; ordinary movement through the real portal must manifest.
                server(player -> {
                    // The generated portal follows natural terrain, which need not be the staged arrival platform.
                    // Supply a dry, level approach without replacing any portal cell.
                    for (BlockPos pos : BlockPos.betweenClosed(portal.offset(-2, -1, -4), portal.offset(2, 2, 1))) {
                        if (player.level().getBlockState(pos).is(
                            com.kadamitas.warlockery.registry.ModBlocks.ALL.get("spiritportal").get())) continue;
                        player.level().setBlockAndUpdate(pos, pos.getY() == portal.getY() - 1
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    player.setDeltaMovement(Vec3.ZERO);
                    player.teleportTo(portal.getX() + 0.5, portal.getY(), portal.getZ() - 2.5);
                    player.setYRot(0);
                    player.setXRot(0);
                });
                arrival(context);
                look(context, new Vec3(portal.getX() + 0.5,
                    serverValue(player -> player.getEyePosition().y), portal.getZ() + 0.5));
                report.put("portal_approach_server", serverValue(player -> Map.of(
                    "position", player.position().toString(), "eye", player.getEyePosition().toString(),
                    "direction", player.getLookAngle().toString(), "teleport_pending", awaitingTeleport(player))));
                report.put("portal_approach_client", context.computeOnClient(client -> Map.of(
                    "position", client.player.position().toString(), "eye", client.player.getEyePosition().toString(),
                    "direction", client.player.getLookAngle().toString(), "hit", String.valueOf(client.hitResult))));
                screenshot(context, "portal-approach");
                context.getInput().holdKey(GLFW.GLFW_KEY_W);
                try {
                    waitServer(context, ManifestationRuntime::isActive, 120, "Actual portal contact must manifest the observer");
                } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
                arrival(context);
                check(serverValue(player -> player.level().dimension().equals(Level.OVERWORLD)
                    && player.level().getEntity(session.body()) != null), "The original body remains while manifested");
                // Let dream Darkness expire naturally before judging the owner's skin and ground pose.
                context.waitTicks(110);
                for (var view : Map.of("front", original.add(0, 0, -3.5), "side", original.add(3.5, 0, 0),
                    "oblique", original.add(2.5, 0, -2.5)).entrySet()) {
                    server(player -> player.teleportTo(view.getValue().x, view.getValue().y, view.getValue().z));
                    arrival(context);
                    look(context, original.add(0, 0.25, 0));
                    screenshot(context, "sleeping-body-" + view.getKey());
                }
                useNeedle(context);
                waitServer(context, player -> !ManifestationRuntime.isActive(player)
                    && SpiritWorldRuntime.isSpiritWorld(player.level()), 120, "Needle returns to the dream");
                arrival(context);
                check(serverValue(player -> player.level().getServer().overworld().getEntity(session.body()) != null),
                    "Returning to the dream preserves the same body");
                useNeedle(context);
                waitServer(context, player -> !SpiritWorldRuntime.isDreaming(player), 120, "Second needle wakes");
                arrival(context);
                check(serverValue(player -> player.level().getEntity(session.body()) == null
                    && player.getInventory().getItem(4).is(Items.IRON_INGOT)
                    && player.getInventory().getItem(4).getCount() == 3), "Final wake removes body and restores escrow once");
                command(context, "/gamemode creative");
                command(context, "/execute in warlockery:spirit_world run tp @s 0.5 100 0.5");
                waitServer(context, player -> SpiritWorldRuntime.isDreaming(player)
                    && SpiritWorldState.read(player).orElseThrow().body() == null, 120, "Creative generic entry is body-exempt");
                arrival(context);
                command(context, "/gamemode survival");
                context.waitTicks(45);
                check(serverValue(SpiritWorldRuntime::isDreaming), "Entry body exemption survives a later game mode change");
                server(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 32)));
                command(context, "/execute in minecraft:overworld run tp @s 4.5 100 0.5");
                waitServer(context, player -> !SpiritWorldRuntime.isDreaming(player), 120, "External exit recovers bodyless session");
                arrival(context);
                report.put("creative_body_exemption", "PASSED; bodyless across game-mode change and external exit");
                check(serverValue(player -> player.getInventory().getNonEquipmentItems().stream()
                    .noneMatch(stack -> stack.is(Items.DIAMOND))), "Generic departure cannot export staged mining resources");
                report.put("passed", true);
                writeReport();
                System.out.println("WARLOCKERY_SPIRIT_BOUNDARY_PASSED " + evidence);
                } catch (Throwable failure) {
                    report.put("server_position", serverValue(player -> player.position().toString()));
                    report.put("server_dimension", serverValue(player -> player.level().dimension().identifier().toString()));
                    try { screenshot(context, "failure-in-world"); } catch (Throwable ignored) { }
                    throw failure;
                }
            }
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            try { screenshot(context, "failure"); writeReport(); } catch (Throwable ignored) { }
            throw new AssertionError("Spirit boundary evidence: " + evidence, failure);
        }
    }

    private void command(final ClientGameTestContext context, final String command) {
        context.getInput().pressKey(GLFW.GLFW_KEY_T);
        context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars(command);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitTicks(4);
    }

    private void useNeedle(final ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(3);
        context.runOnClient(client -> client.player.setXRot(-85));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    private void arrival(final ClientGameTestContext context) {
        final var dimension = serverValue(player -> player.level().dimension());
        context.waitFor(client -> client.level != null && client.level.dimension().equals(dimension)
            && client.player != null && client.gui.screen() == null, 300);
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        waitServer(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player),
            120, "Native teleport acknowledgement must complete before aiming or movement");
        final Vec3 settled = serverValue(ServerPlayer::position);
        context.waitFor(client -> client.player != null && client.player.position().distanceToSqr(settled) < 0.04, 100);
    }

    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var pending = net.minecraft.server.network.ServerGamePacketListenerImpl.class
                .getDeclaredField("awaitingPositionFromClient");
            pending.setAccessible(true);
            return pending.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot observe native teleport acknowledgement", failure);
        }
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
    }

    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int tick = 0; tick < ticks; tick++) { if (serverValue(predicate::test)) return; context.waitTicks(1); }
        check(serverValue(predicate::test), message);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private void writeReport() throws Exception {
        report.put("screenshots", screenshots);
        Files.writeString(evidence.resolve("spirit-boundary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
