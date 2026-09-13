package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.AltarBlock;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class MachineModelClientAcceptance implements FabricClientGameTest {
    private static final BlockPos SINGLE = new BlockPos(-6, 100, 0);
    private static final BlockPos WIDE = new BlockPos(0, 100, 0);
    private static final BlockPos LONG = new BlockPos(6, 100, 0);
    private static final BlockPos WHEEL = new BlockPos(-3, 100, 6);
    private static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
        Direction.NORTH, AltarBlock.NORTH, Direction.EAST, AltarBlock.EAST,
        Direction.SOUTH, AltarBlock.SOUTH, Direction.WEST, AltarBlock.WEST);
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("machine-models").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                waitForNativeConnection(context);
                try {
                    setup();
                    final Map<String, Boolean> groundFaces = context.computeOnClient(client -> {
                        final Map<String, Boolean> visible = new LinkedHashMap<>();
                        ModBlocks.ALL.forEach((id, block) -> {
                            if (com.kadamitas.warlockery.registry.SculptedBlockCatalog.contains(id)) {
                                visible.put(id, Block.shouldRenderFace(Blocks.SMOOTH_STONE.defaultBlockState(),
                                    block.get().defaultBlockState(), Direction.UP));
                            }
                        });
                        return visible;
                    });
                    report.put("ground_faces_under_sculpted_blocks", groundFaces);
                    check(!groundFaces.isEmpty() && groundFaces.values().stream().allMatch(Boolean::booleanValue),
                        "Sculpted machines retain the visible top face of the ground underneath them: " + groundFaces);
                    place(context, SINGLE, "altar");
                    capture(context, "altar-single", new Vec3(-8, 100.5, -3), Vec3.atCenterOf(SINGLE));
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) place(context, WIDE.offset(x, 0, z), "altar");
                    for (int x = 0; x < 2; x++) for (int z = 0; z < 3; z++) place(context, LONG.offset(x, 0, z), "altar");
                    context.waitTicks(41);
                    assertConnections(context);
                    server(player -> {
                        check(!altar(player, SINGLE).isMultiblockValid(), "Standalone altar stays invalid");
                        check(altar(player, WIDE).isMultiblockValid(), "Native 3x2 altar is valid");
                        check(altar(player, LONG).isMultiblockValid(), "Native 2x3 altar is valid");
                        check(altar(player, WIDE).receivePower(100) > 0, "Valid altar still accepts actual power");
                        check(altar(player, WIDE).getPower() > 0, "Stored power remains available");
                        final var state = player.level().getBlockState(WIDE);
                        for (var entry : CONNECTIONS.entrySet()) {
                            check(state.rotate(Rotation.CLOCKWISE_90).getValue(CONNECTIONS.get(Rotation.CLOCKWISE_90.rotate(entry.getKey())))
                                == state.getValue(entry.getValue()), "Rotation preserves cardinal adjacency");
                            check(state.mirror(Mirror.LEFT_RIGHT).getValue(CONNECTIONS.get(Mirror.LEFT_RIGHT.mirror(entry.getKey())))
                                == state.getValue(entry.getValue()), "Mirror preserves cardinal adjacency");
                        }
                    });
                    checks.add("Native block-item placement creates valid 3x2 and 2x3 altars; standalone remains invalid; valid altar accepts and retains power; cardinal rotation and mirroring preserve adjacency.");
                    capture(context, "altar-3x2-table", new Vec3(-2, 101, -3), new Vec3(1.5, 100.6, 1));
                    server(player -> player.setGameMode(GameType.CREATIVE));
                    position(context, new Vec3(-1, 100, -1));
                    context.runOnClient(client -> {
                        final Vec3 delta = new Vec3(0.5, 100.15, 0.5).subtract(client.player.getEyePosition());
                        client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                        client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
                    });
                    world.getConnection().waitForChunksRender();
                    context.waitTicks(5);
                    check(context.computeOnClient(client -> client.player.getEyePosition().y > 100),
                        "Normal player ground inspection keeps the camera above the floor");
                    ManualClientAcceptance.saveScreenshot(context, evidence, "altar-normal-player-ground", screenshots);
                    capture(context, "altar-3x2-under-table", new Vec3(-1.5, 98.8, -2), new Vec3(1.5, 100.45, 1));
                    capture(context, "altar-2x3-table", new Vec3(4, 101, -3), new Vec3(7, 100.6, 1.5));
                    erase(context, WIDE.offset(2, 0, 1));
                    context.waitTicks(41);
                    assertConnections(context);
                    server(player -> check(!altar(player, WIDE).isMultiblockValid(), "Five remaining blocks become invalid"));
                    capture(context, "altar-corner-removed", new Vec3(4.8, 101, 4.5), new Vec3(1.5, 100.6, 1));
                    place(context, WIDE.offset(2, 0, 1), "altar");
                    context.waitTicks(41);
                    assertConnections(context);
                    server(player -> check(altar(player, WIDE).isMultiblockValid(), "Native replacement restores validity"));
                    capture(context, "altar-corner-restored", new Vec3(4.8, 101, 4.5), new Vec3(1.5, 100.6, 1));
                    server(player -> {
                        for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                            final BlockPos pos = WIDE.offset(x, 0, z);
                            player.level().setBlock(pos, ModBlocks.ALTAR.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    });
                    context.waitTicks(41);
                    assertConnections(context);
                    capture(context, "altar-legacy-default-bits-refreshed", new Vec3(-2, 101, -3), new Vec3(1.5, 100.6, 1));
                    checks.add("Native corner mining invalidates five blocks and native replacement restores validity. A controlled old-save fixture clears every cardinal bit without neighbor updates; the existing scheduled server tick restores all connections. This checks migration recovery, not an actual disk save/reload.");
                    place(context, WHEEL, "spinningwheel");
                    capture(context, "spinningwheel-front", new Vec3(-2.5, 100, 2.8), new Vec3(-2.5, 100.6, 6.5));
                    capture(context, "spinningwheel-side", new Vec3(0.6, 100, 6.5), new Vec3(-2.5, 100.6, 6.5));
                    capture(context, "spinningwheel-low-angle", new Vec3(-4.4, 98.9, 4.6), new Vec3(-2.5, 100.45, 6.5));
                    server(player -> {
                        final var lit = player.level().getBlockState(WHEEL).setValue(BlockStateProperties.LIT, true);
                        player.level().setBlockAndUpdate(WHEEL, lit);
                        player.level().removeBlockEntity(WHEEL);
                    });
                    capture(context, "spinningwheel-lit-model-preview", new Vec3(-4.3, 100.1, 3.3), new Vec3(-2.5, 100.6, 6.5));
                    checks.add("Spinning wheel is placed through native block-item use and captured from front, side, and low angle. Active model is a controlled visual fixture with lit=true and ticker removed; processing and animation activation are not claimed by this preview.");
                    report.put("passed", true);
                } catch (Throwable failure) {
                    report.put("passed", false);
                    report.put("failure", failure.toString());
                    ManualClientAcceptance.saveScreenshot(context, evidence, "failure-in-world", screenshots);
                    throw failure;
                }
            }
            writeReport();
            System.out.println("WARLOCKERY_MACHINE_MODEL_PASS " + evidence);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            try { writeReport(); } catch (Exception writing) { failure.addSuppressed(writing); }
            throw new AssertionError("Machine model evidence: " + evidence, failure);
        }
    }

    private void setup() {
        server(player -> {
            player.setGameMode(GameType.CREATIVE);
            player.setNoGravity(true);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            for (BlockPos pos : BlockPos.betweenClosed(-12, 99, -8, 14, 106, 12)) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(0.5, 102, -4);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
    }

    private void place(final ClientGameTestContext context, final BlockPos pos, final String id) {
        server(player -> {
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.getInventory().setItem(0, new ItemStack(ModBlocks.ALL.get(id).get().asItem()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        position(context, new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5));
        aimDown(context, pos.below());
        tracePlacement(context, pos, "before-click");
        check(serverValue(player -> player.connection.hasClientLoaded()
            && !awaitingTeleport(player) && player.isWithinBlockInteractionRange(pos.below(), 1.0)),
            "Server is ready to accept this placement input");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 30 && !serverValue(player -> player.level().getBlockState(pos).is(ModBlocks.ALL.get(id).get())); tick++) {
            context.waitTicks(1);
        }
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getBlockState(pos).is(ModBlocks.ALL.get(id).get()), 30);
        tracePlacement(context, pos, "after-click");
        check(serverValue(player -> player.level().getBlockState(pos).is(ModBlocks.ALL.get(id).get())), "Native placement reaches server for " + id);
    }

    private void erase(final ClientGameTestContext context, final BlockPos pos) {
        server(player -> {
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        position(context, new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5));
        aimDown(context, pos);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        for (int tick = 0; tick < 30 && !serverValue(player -> player.level().getBlockState(pos).isAir()); tick++) {
            context.waitTicks(1);
        }
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getBlockState(pos).isAir(), 30);
        check(serverValue(player -> player.level().getBlockState(pos).isAir()), "Native creative mining removes altar corner");
    }

    private static void aimDown(final ClientGameTestContext context, final BlockPos expected) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)), "Native placement ray targets " + expected);
    }

    private void assertConnections(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        check(serverValue(player -> matches(player.level())), "Server altar bits match physical neighbors");
        context.waitFor(client -> matches(client.level), 30);
    }

    private static boolean matches(final BlockGetter level) {
        for (BlockPos pos : BlockPos.betweenClosed(-7, 100, -1, 9, 100, 4)) {
            final var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AltarBlock)) continue;
            for (var entry : CONNECTIONS.entrySet()) {
                if (state.getValue(entry.getValue()) != level.getBlockState(pos.relative(entry.getKey())).is(ModBlocks.ALTAR.get())) return false;
            }
        }
        return true;
    }

    private void capture(final ClientGameTestContext context, final String name, final Vec3 camera, final Vec3 target) throws Exception {
        server(player -> { player.setGameMode(GameType.SPECTATOR); player.getInventory().clearContent(); player.inventoryMenu.broadcastChanges(); });
        position(context, camera);
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(5);
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void position(final ClientGameTestContext context, final Vec3 pos) {
        server(player -> { player.setDeltaMovement(Vec3.ZERO); player.teleportTo(pos.x, pos.y, pos.z); });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
        waitForNativeConnection(context);
    }

    private void waitForNativeConnection(final ClientGameTestContext context) {
        for (int tick = 0; tick < 100 && !serverValue(player -> player.connection.hasClientLoaded() && !awaitingTeleport(player)); tick++) {
            context.waitTicks(1);
        }
        check(serverValue(player -> player.connection.hasClientLoaded() && !awaitingTeleport(player)),
            "Client loading and native teleport acknowledgement complete before item interaction");
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

    private void tracePlacement(final ClientGameTestContext context, final BlockPos pos, final String phase) {
        @SuppressWarnings("unchecked") final List<Object> trace = (List<Object>) report.computeIfAbsent("placement_trace", key -> new ArrayList<>());
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", phase);
        entry.put("target", pos.toShortString());
        entry.put("server", serverValue(player -> Map.of(
            "position", player.position().toString(), "eye", player.getEyePosition().toString(),
            "game_mode", player.gameMode.getGameModeForPlayer().getName(), "loaded", player.connection.hasClientLoaded(),
            "teleport_pending", awaitingTeleport(player), "in_range", player.isWithinBlockInteractionRange(pos.below(), 1.0),
            "hand", player.getMainHandItem().toString(), "target_state", player.level().getBlockState(pos).toString(),
            "support_state", player.level().getBlockState(pos.below()).toString(), "game_time", player.level().getGameTime())));
        entry.put("client", context.computeOnClient(client -> Map.of("position", client.player.position().toString(),
            "eye", client.player.getEyePosition().toString(), "hand", client.player.getMainHandItem().toString(),
            "target_state", client.level.getBlockState(pos).toString(), "game_time", client.level.getGameTime())));
        trace.add(entry);
    }

    private static AltarBlockEntity altar(final ServerPlayer player, final BlockPos pos) {
        return (AltarBlockEntity) player.level().getBlockEntity(pos);
    }
    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }
    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }
    private void writeReport() throws Exception {
        report.put("checks", checks);
        report.put("screenshots", screenshots);
        report.put("fixture", "Flat stage and camera alignment are staged. Altar and idle wheel blocks are placed through native mouse input. Spectator cameras provide unobstructed visual inspection; low shots put the eye above floor level and under the table top.");
        Files.writeString(evidence.resolve("machine-model-acceptance.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
