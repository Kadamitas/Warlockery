package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders every machine/device block from five angles plus its lit/active state; capture is not visual approval. */
public final class BlockVisualRosterAcceptance implements FabricClientGameTest {
    private static final List<String> DEFAULT_IDS = List.of(
        "brazier", "altar", "alchemical_oven", "distillery", "distilleryidle", "distilleryburning", "kettle", "cauldron", "spinningwheel",
        "wolfaltar", "statuegoddess", "statueofworship", "broken_hexes_statue", "occluded_summons_statue", "mirrorblock", "mirrorblock2",
        "mirrorwall", "chalice", "candelabra", "coffinblock", "bloodcrucible", "silvervat", "scarecrow", "trent", "wolftrap", "beartrap",
        "dreamcatcher", "dream_weaver_fasting", "dream_weaver_fleet_foot", "dream_weaver_intensity", "dream_weaver_iron_arm",
        "dream_weaver_nightmares", "crystalball", "glowglobe", "leechchest", "refillingchest", "doll_shelf", "daylightcollector",
        "grassper", "glintweed", "wickerbundle", "plantmine", "pentacle", "alluringskull", "demonheart", "garlicgarland", "hex_ladder",
        "crittersnare", "stockade", "icestockade", "abyssal_portal", "spiritportal", "voidbramble", "bramble", "fumefunnel",
        "filteredfumefunnel", "placeditem", "wallgen", "leapinglily", "embermoss", "disease", "force");
    private static final Set<String> STATE_TOGGLES = Set.of("lit", "active", "burning", "powered", "open", "armed", "filled", "occupied");
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("block-visual-roster").resolve(UUID.randomUUID().toString());
        final String configured = System.getProperty("warlockery.visualIds", "");
        final List<String> requested = configured.isBlank() ? DEFAULT_IDS : Arrays.stream(configured.split(",")).map(String::trim).toList();
        final int originalFov = context.computeOnClient(client -> client.options.fov().get());
        final boolean originalHud = context.computeOnClient(client -> client.gui.hud.isHidden());
        report.put("capture_completed", false);
        report.put("visual_review", "PENDING_IMAGE_REVIEW");
        report.put("scope", "Diagnostic native block rendering only: default state plus one toggled boolean state where present. Completed capture is not an approved appearance.");
        report.put("registered_block_ids", new ArrayList<>(ModBlocks.ALL.keySet()));
        report.put("results", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.fov().set(60); client.options.guiScale().set(2); client.resizeGui(); });
            final List<String> selected = new ArrayList<>();
            for (String id : requested) {
                results.put(id, new LinkedHashMap<>(Map.of("status", ModBlocks.ALL.containsKey(id) ? "NOT_RUN" : "SKIPPED_UNKNOWN_ID")));
                if (ModBlocks.ALL.containsKey(id)) selected.add(id);
            }
            write();
            try (var created = context.worldBuilder().create()) {
                world = created;
                stage(context);
                for (String id : selected) {
                    active = id;
                    row().put("status", "RUNNING");
                    try {
                        block(context, id);
                        row().put("status", "CAPTURED_REVIEW_PENDING");
                    } catch (Throwable failure) {
                        row().put("status", "FAILED");
                        row().put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { shot(context, "failure"); } catch (Throwable ignored) { }
                    } finally {
                        clear();
                        write();
                    }
                }
            } finally {
                world = null;
            }
            report.put("capture_completed", failures.isEmpty());
            write();
            check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_BLOCK_VISUAL_ROSTER_CAPTURED " + evidence);
        } catch (Throwable failure) {
            throw new AssertionError("Block visual roster evidence: " + evidence, failure);
        } finally {
            context.runOnClient(client -> client.options.fov().set(originalFov));
            if (context.computeOnClient(client -> client.gui.hud.isHidden()) != originalHud) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            final var level = server.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 99, -12), new BlockPos(12, 110, 12))) {
                level.setBlockAndUpdate(pos, pos.getY() != 99 ? Blocks.AIR.defaultBlockState()
                    : Math.abs(pos.getX()) <= 6 && Math.abs(pos.getZ()) <= 6 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            }
            final var player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SPECTATOR);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.teleportTo(level, .5, 102, 6.5, Set.of(), 180, 15, true);
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        for (int i = 0; i < 3 && !context.computeOnClient(c -> c.options.getCameraType().isFirstPerson()); i++) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5);
        if (!context.computeOnClient(client -> client.gui.hud.isHidden())) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
    }

    private void block(final ClientGameTestContext context, final String id) throws Exception {
        final BlockState state = world.getServer().computeOnServer(server -> {
            final BlockState placed = ModBlocks.ALL.get(id).get().defaultBlockState();
            server.overworld().setBlockAndUpdate(ORIGIN, placed);
            return server.overworld().getBlockState(ORIGIN);
        });
        row().put("block_state", state.toString());
        row().put("properties", state.getProperties().stream().map(Property::getName).toList());
        row().put("placed", world.getServer().computeOnServer(server -> server.overworld().getBlockState(ORIGIN).is(ModBlocks.ALL.get(id).get())));
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        final double height = Math.max(0.5, state.getShape(world.getServer().computeOnServer(server -> server.overworld()), ORIGIN).max(net.minecraft.core.Direction.Axis.Y));
        final Vec3 target = new Vec3(.5, 100 + height * .5, .5);
        final double distance = 2.2 + height;
        capture(context, "front", target.add(0, height * .35, distance), target);
        capture(context, "oblique", target.add(distance * .72, height * .55, distance * .72), target);
        capture(context, "side", target.add(distance, height * .3, 0), target);
        capture(context, "rear", target.add(0, height * .35, -distance), target);
        capture(context, "low-angle", new Vec3(distance * .6, 100.15, distance * .8), new Vec3(.5, 100.2, .5));
        capture(context, "play-distance", target.add(2, 1.4, 7), target);
        final String toggle = state.getProperties().stream().filter(p -> p instanceof BooleanProperty && STATE_TOGGLES.contains(p.getName()))
            .map(Property::getName).findFirst().orElse(null);
        if (toggle != null) {
            world.getServer().runOnServer(server -> {
                final BlockState current = server.overworld().getBlockState(ORIGIN);
                final BooleanProperty property = (BooleanProperty) current.getProperties().stream().filter(p -> p.getName().equals(toggle)).findFirst().orElseThrow();
                server.overworld().setBlockAndUpdate(ORIGIN, current.setValue(property, !current.getValue(property)));
                // Machine block entities extinguish an idle lit state on their next tick; drop the entity so the toggled model stays visible.
                server.overworld().removeBlockEntity(ORIGIN);
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            row().put("toggled_property", toggle);
            row().put("toggled_state", world.getServer().computeOnServer(server -> server.overworld().getBlockState(ORIGIN).toString()));
            capture(context, "toggled-oblique", target.add(distance * .72, height * .55, distance * .72), target);
            capture(context, "toggled-front", target.add(0, height * .35, distance), target);
        }
    }

    private void clear() {
        if (world == null) return;
        world.getServer().runOnServer(server -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-3, 100, -3), new BlockPos(3, 106, 3)))
                server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            server.overworld().getEntitiesOfClass(Entity.class, new AABB(-40, 60, -40, 40, 140, 40), entity -> !(entity instanceof ServerPlayer)).forEach(Entity::discard);
        });
    }

    private void capture(final ClientGameTestContext context, final String name, final Vec3 eye, final Vec3 target) throws Exception {
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        for (int i = 0; i < 100 && world.getServer().computeOnServer(server -> awaitingTeleport(world.getConnection().getServerPlayer())); i++) context.waitTicks(1);
        context.waitFor(client -> client.player != null && client.player.getEyePosition().distanceTo(eye) < .05, 60);
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(4);
        shot(context, name);
    }

    private void shot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, active + "-" + name, screenshots);
        write();
    }
    private Map<String, Object> row() { return results.get(active); }
    private void write() throws Exception {
        Files.writeString(evidence.resolve("block-visual-roster.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true);
            return field.get(player.connection) != null;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(final boolean valid, final String message) { if (!valid) throw new AssertionError(message); }
}
