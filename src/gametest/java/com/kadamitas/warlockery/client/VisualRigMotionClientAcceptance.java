package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.entity.GoblinPatronRules;
import com.kadamitas.warlockery.entity.IllusionCreeperEntity;
import com.kadamitas.warlockery.entity.MimicryRules;
import com.kadamitas.warlockery.entity.StonebrokerEntity;
import com.kadamitas.warlockery.entity.StormSimianEntity;
import com.kadamitas.warlockery.entity.StormSimianRules;
import com.kadamitas.warlockery.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Private native-client motion diagnosis for the three repaired rigs whose important poses are
 * driven by authoritative AI. This helper observes public runtime state and projectiles; it never
 * writes a presentation phase, charge, action, target, path, velocity, animation clock or model.
 */
public final class VisualRigMotionClientAcceptance implements FabricClientGameTest {
    private static final List<String> CASES = List.of(
        "stonebroker", "storm_simian", "illusion_creeper"
    );
    private static final AABB ARENA = new AABB(-24, 98, -24, 25, 116, 25);

    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;
    private int subjectId;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty(
            "warlockery.clientEvidence", ".codex-local/client-acceptance"
        )).resolve("visual-rig-motion").resolve(UUID.randomUUID().toString());
        final int originalFov = context.computeOnClient(client -> client.options.fov().get());
        final boolean originalHud = context.computeOnClient(client -> client.gui.hud.isHidden());
        report.put("capture_completed", false);
        report.put("visual_review", "PENDING_IMAGE_REVIEW");
        report.put("scope", "Real AI motion and projectile capture; no presentation-state forcing.");
        report.put("results", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                client.options.fov().set(60);
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            hud(context, true);
            for (int i = 0; i < 3
                && !context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()); i++) {
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5);
            }
            for (final String id : CASES) {
                results.put(id, new LinkedHashMap<>(Map.of("status", "NOT_RUN")));
            }
            for (final String id : CASES) {
                active = id;
                row().put("status", "RUNNING");
                write();
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stageArena();
                        switch (id) {
                            case "stonebroker" -> stonebroker(context);
                            case "storm_simian" -> stormSimian(context);
                            case "illusion_creeper" -> illusionCreeper(context);
                            default -> throw new AssertionError("Unexpected case " + id);
                        }
                        row().put("status", "CAPTURED_REVIEW_PENDING");
                    } catch (final Throwable failure) {
                        row().put("status", "FAILED");
                        row().put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try {
                            shot(context, "failure");
                        } catch (final Throwable ignored) {
                        }
                    } finally {
                        write();
                    }
                } finally {
                    world = null;
                }
            }
            report.put("capture_completed", failures.isEmpty());
            write();
            check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_VISUAL_RIG_MOTION_CAPTURED " + evidence);
        } catch (final Throwable failure) {
            throw new AssertionError("Visual rig motion evidence: " + evidence, failure);
        } finally {
            context.runOnClient(client -> client.options.fov().set(originalFov));
            if (context.computeOnClient(client -> client.gui.hud.isHidden()) != originalHud) {
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            }
        }
    }

    private void stageArena() {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            final var level = server.overworld();
            for (final BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(-24, 99, -24), new BlockPos(24, 115, 24)
            )) {
                level.setBlockAndUpdate(pos,
                    pos.getY() == 99 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setPermanentlyInvulnerable(false);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
    }

    private void stonebroker(final ClientGameTestContext context) throws Exception {
        world.getServer().runOnServer(server -> {
            final var level = server.overworld();
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            // Survival invulnerability remains a legal challenger: patron protection excludes only
            // creative/spectator players, while this prevents the diagnostic target dying.
            player.setPermanentlyInvulnerable(true);
            player.teleportTo(level, 0.5, 100, 8.5, Set.of(), 180.0F, 5.0F, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon warlockery:stonebroker 0.5 100 0.5 {PersistenceRequired:1b}");
            final StonebrokerEntity broker = one(StonebrokerEntity.class, level,
                entity -> entity.getType() == ModEntities.ALL.get("stonebroker").get());
            subjectId = broker.getId();
            row().put("entity_uuid", broker.getStringUUID());
            row().put("staging", Map.of("target", "survival_invulnerable_player", "distance", 8.0));
        });
        awaitClientSubject(context);
        final List<Map<String, Object>> trace = new ArrayList<>();
        row().put("trace", trace);
        boolean windupCaptured = false;
        boolean shotCaptured = false;
        for (int tick = 0; tick < 180 && !shotCaptured; tick++) {
            context.waitTicks(1);
            final Map<String, Object> sample = world.getServer().computeOnServer(server -> {
                final StonebrokerEntity broker = requireSubject(server.overworld(), StonebrokerEntity.class);
                final var combat = broker.goblinPatronState().combat();
                final List<String> arrows = server.overworld().getEntitiesOfClass(AbstractArrow.class, ARENA)
                    .stream().filter(arrow -> arrow.getOwner() == broker)
                    .map(arrow -> arrow.getType() + " " + arrow.position()).toList();
                final Map<String, Object> item = basic(broker);
                item.put("action", broker.presentationAction().name());
                item.put("tell_remaining", combat.tellRemainingTicks());
                item.put("commit_remaining", combat.commitRemainingTicks());
                item.put("arrows_remaining", combat.arrowsRemaining());
                item.put("owned_arrows", arrows);
                return item;
            });
            trace.add(sample);
            final String action = (String) sample.get("action");
            if (!windupCaptured && action.equals(GoblinPatronRules.Action.LEDGER_VOLLEY.name())) {
                syncAndAimAtSubject(context);
                shot(context, "volley-windup");
                windupCaptured = true;
            }
            if (windupCaptured && !((List<?>) sample.get("owned_arrows")).isEmpty()) {
                world.getConnection().waitForClientboundPackets();
                shot(context, "volley-arrow-release");
                shotCaptured = true;
            }
        }
        check(windupCaptured, "Real AI never entered LEDGER_VOLLEY");
        check(shotCaptured, "Real LEDGER_VOLLEY never created an owned arrow");
        row().put("observed", Map.of("windup", true, "owned_arrow", true));
    }

    private void stormSimian(final ClientGameTestContext context) throws Exception {
        world.getServer().runOnServer(server -> {
            final var level = server.overworld();
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, 8.5, 103, 8.5, Set.of(), 135.0F, 5.0F, true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "weather thunder 1200");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon warlockery:storm_simian 0.5 104 0.5 {PersistenceRequired:1b}");
            final StormSimianEntity simian = one(StormSimianEntity.class, level,
                entity -> entity.getType() == ModEntities.ALL.get("storm_simian").get());
            subjectId = simian.getId();
            row().put("entity_uuid", simian.getStringUUID());
            row().put("staging", Map.of("weather", "thunder", "spawn_height", 104));
        });
        awaitClientSubject(context);
        final List<Map<String, Object>> trace = new ArrayList<>();
        row().put("trace", trace);
        trackedShot(context, "airborne-wings-0");
        for (int tick = 0; tick < 620; tick++) {
            context.waitTicks(1);
            final Map<String, Object> sample = world.getServer().computeOnServer(server -> {
                final StormSimianEntity simian = requireSubject(server.overworld(), StormSimianEntity.class);
                final Map<String, Object> item = basic(simian);
                item.put("charge", simian.presentationCharge());
                item.put("charged_ready", StormSimianRules.chargedGustReady(simian.presentationCharge()));
                item.put("has_grip", simian.presentationHasGrip());
                return item;
            });
            trace.add(sample);
            if (tick == 10 || tick == 25) {
                trackedShot(context, "airborne-wings-" + tick);
            }
            if (Boolean.TRUE.equals(sample.get("charged_ready"))) {
                trackedShot(context, "charged-wings-spread");
                break;
            }
        }
        final int readyCharge = world.getServer().computeOnServer(server ->
            requireSubject(server.overworld(), StormSimianEntity.class).presentationCharge());
        check(StormSimianRules.chargedGustReady(readyCharge),
            "Four real thunder observations did not reach charged-gust readiness");

        world.getServer().runOnServer(server -> {
            final var level = server.overworld();
            final StormSimianEntity simian = requireSubject(level, StormSimianEntity.class);
            final Vec3 targetPos = simian.position().add(8.0, 0.0, 0.0);
            final Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            check(zombie != null, "Create real hostile target");
            zombie.setPos(targetPos);
            final var health = zombie.getAttribute(Attributes.MAX_HEALTH);
            check(health != null, "Zombie has max health");
            health.setBaseValue(200.0);
            zombie.setHealth(200.0F);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            level.addFreshEntity(zombie);
            row().put("gust_target", Map.of(
                "type", "minecraft:zombie", "uuid", zombie.getStringUUID(),
                "distance", simian.distanceTo(zombie), "health", zombie.getHealth()
            ));
        });
        boolean gustCaptured = false;
        for (int tick = 0; tick < 120 && !gustCaptured; tick++) {
            context.waitTicks(1);
            final Map<String, Object> sample = world.getServer().computeOnServer(server -> {
                final StormSimianEntity simian = requireSubject(server.overworld(), StormSimianEntity.class);
                final List<String> gusts = server.overworld().getEntitiesOfClass(WindCharge.class, ARENA)
                    .stream().filter(gust -> gust.getOwner() == simian)
                    .map(gust -> gust.getType() + " " + gust.position()).toList();
                final Map<String, Object> item = basic(simian);
                item.put("charge", simian.presentationCharge());
                item.put("target", simian.getTarget() == null ? "none" : simian.getTarget().getStringUUID());
                item.put("owned_wind_charges", gusts);
                return item;
            });
            trace.add(sample);
            if (!((List<?>) sample.get("owned_wind_charges")).isEmpty()) {
                trackedShot(context, "charged-gust-release");
                gustCaptured = true;
            }
        }
        check(gustCaptured, "Real ranged AI never created an owned wind charge");
        row().put("observed", Map.of(
            "airborne_wing_frames", 3, "ready_charge", readyCharge, "owned_wind_charge", true
        ));
    }

    private void illusionCreeper(final ClientGameTestContext context) throws Exception {
        world.getServer().runOnServer(server -> {
            final var level = server.overworld();
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, 8.5, 103, 8.5, Set.of(), 135.0F, 10.0F, true);
            // A fixed, invisible armour stand is a real eligible LivingEntity. It cannot randomly
            // stare back for twenty ticks and prematurely trigger discovery like a cow can.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon minecraft:armor_stand 0.5 100 6.5 {Invisible:1b,Invulnerable:1b,PersistenceRequired:1b}");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon warlockery:illusion_creeper 0.5 100 0.5 {PersistenceRequired:1b}");
            final IllusionCreeperEntity creeper = one(IllusionCreeperEntity.class, level,
                entity -> entity.getType() == ModEntities.ALL.get("illusion_creeper").get());
            subjectId = creeper.getId();
            row().put("entity_uuid", creeper.getStringUUID());
            row().put("staging", Map.of(
                "observer", "invisible_fixed_armor_stand", "initial_distance", 6.0
            ));
        });
        awaitClientSubject(context);
        final List<Map<String, Object>> trace = new ArrayList<>();
        row().put("trace", trace);
        final Set<MimicryRules.Phase> captured = new java.util.LinkedHashSet<>();
        for (int tick = 0; tick < 240 && !captured.contains(MimicryRules.Phase.SPENT); tick++) {
            context.waitTicks(1);
            final Map<String, Object> sample = world.getServer().computeOnServer(server -> {
                final IllusionCreeperEntity creeper =
                    requireSubject(server.overworld(), IllusionCreeperEntity.class);
                final Map<String, Object> item = basic(creeper);
                item.put("phase", creeper.presentationPhase().name());
                return item;
            });
            trace.add(sample);
            final MimicryRules.Phase phase = MimicryRules.Phase.valueOf((String) sample.get("phase"));
            if (List.of(
                MimicryRules.Phase.APPROACH, MimicryRules.Phase.TELL,
                MimicryRules.Phase.HOLD, MimicryRules.Phase.COLLAPSE,
                MimicryRules.Phase.SPENT
            ).contains(phase) && captured.add(phase)) {
                trackedShot(context, phase.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (final MimicryRules.Phase required : List.of(
            MimicryRules.Phase.APPROACH, MimicryRules.Phase.TELL,
            MimicryRules.Phase.HOLD, MimicryRules.Phase.COLLAPSE,
            MimicryRules.Phase.SPENT
        )) {
            check(captured.contains(required), "Real mimic routine never reached " + required);
        }
        row().put("observed_phases", captured.stream().map(Enum::name).toList());
    }

    private Map<String, Object> basic(final Mob mob) {
        final Map<String, Object> item = new LinkedHashMap<>();
        item.put("tick", mob.tickCount);
        item.put("position", mob.position().toString());
        item.put("velocity", mob.getDeltaMovement().toString());
        item.put("grounded", mob.onGround());
        item.put("target", mob.getTarget() == null ? "none" : mob.getTarget().getStringUUID());
        return item;
    }

    private void awaitClientSubject(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level != null && client.level.getEntity(subjectId) != null, 100);
    }

    private void syncAndAimAtSubject(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
        context.runOnClient(client -> {
            final Entity subject = client.level.getEntity(subjectId);
            check(subject != null, "Client subject exists");
            aim(client.player, subject.getBoundingBox().getCenter());
        });
        context.waitTicks(2);
    }

    private void trackedShot(final ClientGameTestContext context, final String name) throws Exception {
        final Vec3 subject = context.computeOnClient(client -> {
            final Entity found = client.level.getEntity(subjectId);
            check(found != null, "Client subject exists");
            return found.getBoundingBox().getCenter();
        });
        final Vec3 eye = subject.add(3.0, 0.6, 3.0);
        world.getServer().runOnServer(server -> {
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.teleportTo(server.overworld(), eye.x, eye.y - player.getEyeHeight(), eye.z,
                Set.of(), player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.player != null
            && client.player.getEyePosition().distanceTo(eye) < 0.05, 60);
        context.runOnClient(client -> aim(client.player, subject));
        context.waitTicks(2);
        shot(context, name);
    }

    private static void aim(final Entity camera, final Vec3 target) {
        final Vec3 delta = target.subtract(camera.getEyePosition());
        camera.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        camera.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }

    private <T extends Entity> T requireSubject(final net.minecraft.server.level.ServerLevel level,
                                                 final Class<T> type) {
        final Entity entity = level.getEntity(subjectId);
        check(type.isInstance(entity) && entity.isAlive(), "Motion subject remains alive");
        return type.cast(entity);
    }

    private static <T extends Entity> T one(final Class<T> type,
                                            final net.minecraft.server.level.ServerLevel level,
                                            final java.util.function.Predicate<T> predicate) {
        final List<T> found = level.getEntitiesOfClass(type, ARENA, predicate);
        check(found.size() == 1, "Expected one " + type.getSimpleName() + ", found " + found.size());
        return found.getFirst();
    }

    private void hud(final ClientGameTestContext context, final boolean hidden) {
        if (context.computeOnClient(client -> client.gui.hud.isHidden()) != hidden) {
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        }
    }

    private void shot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, active + "-" + name, screenshots);
        write();
    }

    private Map<String, Object> row() {
        return results.get(active);
    }

    private void write() throws Exception {
        Files.writeString(evidence.resolve("visual-rig-motion.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void check(final boolean valid, final String message) {
        if (!valid) {
            throw new AssertionError(message);
        }
    }
}
