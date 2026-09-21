package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.client.model.VampireModel;
import com.kadamitas.warlockery.registry.ModEntities;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders every registered creature (plus Ent variants and both vampire variants) from five angles; capture is not approval. */
public final class MobVisualRosterAcceptance implements FabricClientGameTest {
    private static final List<String> ENT_VARIANTS = List.of("oak", "birch", "spruce", "jungle", "dark_oak", "acacia", "mangrove", "cherry", "pale_oak");
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;
    private int subjectId;
    private double height;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("mob-visual-roster").resolve(UUID.randomUUID().toString());
        final String configured = System.getProperty("warlockery.visualIds", "");
        final List<String> ids = new ArrayList<>(ModEntities.ALL.keySet());
        ids.sort(String::compareTo);
        final List<String> selected = new ArrayList<>();
        for (String id : configured.isBlank() ? ids : Arrays.stream(configured.split(",")).map(String::trim).toList()) {
            if (id.equals("ent")) ENT_VARIANTS.forEach(variant -> selected.add("ent:" + variant));
            else if (id.equals("vampire")) { selected.add("vampire:masculine"); selected.add("vampire:feminine"); }
            else selected.add(id);
        }
        final int originalFov = context.computeOnClient(client -> client.options.fov().get());
        final boolean originalHud = context.computeOnClient(client -> client.gui.hud.isHidden());
        report.put("capture_completed", false);
        report.put("visual_review", "PENDING_IMAGE_REVIEW");
        report.put("scope", "Diagnostic native creature rendering only: staged no-AI subjects at yaw 0 from five angles. Completed capture is not an approved appearance and proves no behavior.");
        report.put("registered_entity_ids", ids);
        report.put("results", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.fov().set(60); client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : selected) results.put(id, new LinkedHashMap<>(Map.of("status", "NOT_RUN")));
            write();
            try (var created = context.worldBuilder().create()) {
                world = created;
                stage(context);
                for (String id : selected) {
                    active = id;
                    row().put("status", "RUNNING");
                    try {
                        creature(context, id);
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
            System.out.println("WARLOCKERY_MOB_VISUAL_ROSTER_CAPTURED " + evidence);
        } catch (Throwable failure) {
            throw new AssertionError("Mob visual roster evidence: " + evidence, failure);
        } finally {
            context.runOnClient(client -> client.options.fov().set(originalFov));
            if (context.computeOnClient(client -> client.gui.hud.isHidden()) != originalHud) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doMobSpawning false");
            final var level = server.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-18, 99, -18), new BlockPos(18, 114, 18))) {
                level.setBlockAndUpdate(pos, pos.getY() != 99 ? Blocks.AIR.defaultBlockState()
                    : Math.abs(pos.getX()) <= 10 && Math.abs(pos.getZ()) <= 10 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            }
            final var player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SPECTATOR);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.teleportTo(level, .5, 102, 8.5, Set.of(), 180, 15, true);
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        for (int i = 0; i < 3 && !context.computeOnClient(c -> c.options.getCameraType().isFirstPerson()); i++) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5);
        if (!context.computeOnClient(client -> client.gui.hud.isHidden())) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
    }

    private void creature(final ClientGameTestContext context, final String key) throws Exception {
        final String id = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
        final String variant = key.contains(":") ? key.substring(key.indexOf(':') + 1) : "";
        world.getServer().runOnServer(server -> {
            final var level = server.overworld();
            final var type = ModEntities.ALL.get(id).get();
            Entity subject = null;
            if (id.equals("vampire")) {
                final VampireModel.Variant wanted = VampireModel.Variant.valueOf(variant.toUpperCase());
                for (int attempt = 0; attempt < 24 && subject == null; attempt++) {
                    final Entity candidate = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    check(candidate != null, "Registry creates the actual vampire");
                    if (VampireModel.variantFor(candidate.getUUID()) == wanted) subject = candidate;
                    else candidate.discard();
                }
                check(subject != null, "A " + variant + " vampire variant appears within 24 native creations");
                subject.snapTo(.5, 100, .5, 0, 0);
                check(level.addFreshEntity(subject), "Vampire joins the world");
            } else {
                final String nbt = id.equals("ent") ? "{PersistenceRequired:1b,WarlockeryEntVariant:\"" + variant + "\"}" : "{PersistenceRequired:1b}";
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "summon warlockery:" + id + " 0.5 100 0.5 " + nbt);
                final List<Entity> found = level.getEntitiesOfClass(Entity.class, new AABB(-4, 98, -4, 5, 112, 5), e -> e.getType() == type);
                check(!found.isEmpty(), "Native summon creates an actual " + id);
                subject = found.getFirst();
                for (Entity extra : found) if (extra != subject) extra.discard();
            }
            if (subject instanceof Mob mob) {
                mob.setNoAi(true); mob.setTarget(null); mob.setPersistenceRequired();
                mob.setYRot(0); mob.setYBodyRot(0); mob.setYHeadRot(0);
            }
            subject.snapTo(.5, 100, .5, 0, 0);
            subject.setDeltaMovement(Vec3.ZERO);
            subjectId = subject.getId();
            height = Math.max(1.0, subject.getBbHeight() * 1.4);
            row().put("entity", Map.of("id", id, "variant", variant, "uuid", subject.getStringUUID(), "class", subject.getClass().getName(),
                "width", subject.getBbWidth(), "height", subject.getBbHeight(), "position", subject.position().toString(),
                "custom_name", subject.getCustomName() == null ? "" : subject.getCustomName().getString()));
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level != null && client.level.getEntity(subjectId) != null, 100);
        context.waitTicks(5);
        final String texture = id.equals("vampire") ? "textures/entity/vampire_" + variant + ".png" : "textures/entity/" + id + ".png";
        row().put("runtime_texture", resource(context, texture));
        row().put("client_position", context.computeOnClient(client -> String.valueOf(client.level.getEntity(subjectId).position())));
        final Vec3 target = new Vec3(.5, 100 + height * .42, .5);
        final double distance = height * 1.5;
        capture(context, "front", target.add(0, height * .07, distance), target);
        capture(context, "oblique", target.add(distance * .75, height * .14, distance * .75), target);
        capture(context, "side", target.add(distance, height * .05, 0), target);
        capture(context, "rear", target.add(0, height * .07, -distance), target);
        capture(context, "play-distance", target.add(1, height * .1, Math.max(7, distance * 1.6)), target);
    }

    private void clear() {
        if (world == null) return;
        world.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(Entity.class, new AABB(-40, 60, -40, 40, 140, 40), entity -> !(entity instanceof ServerPlayer)).forEach(Entity::discard));
    }

    private Map<String, Object> resource(final ClientGameTestContext context, final String path) {
        return context.computeOnClient(client -> {
            try {
                final var identifier = Identifier.parse("warlockery:" + path);
                final var found = client.getResourceManager().getResource(identifier);
                if (found.isEmpty()) return Map.of("requested", identifier.toString(), "status", "DIRECT_RESOURCE_MISSING_CHECK_RENDERER_VARIANT");
                final byte[] bytes;
                try (var stream = found.get().open()) { bytes = stream.readAllBytes(); }
                final var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                return Map.of("id", identifier.toString(), "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    "width", decoded.getWidth(), "height", decoded.getHeight(), "pack", found.get().sourcePackId());
            } catch (Exception e) { throw new AssertionError(e); }
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
        ManualClientAcceptance.saveScreenshot(context, evidence, active.replace(':', '-') + "-" + name, screenshots);
        write();
    }
    private Map<String, Object> row() { return results.get(active); }
    private void write() throws Exception {
        Files.writeString(evidence.resolve("mob-visual-roster.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
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
