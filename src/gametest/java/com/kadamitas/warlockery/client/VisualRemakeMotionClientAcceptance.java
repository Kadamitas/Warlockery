package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.entity.DeathEntity;
import com.kadamitas.warlockery.entity.DeathRules;
import com.kadamitas.warlockery.entity.NaamahCourtRules;
import com.kadamitas.warlockery.entity.NaamahEntity;
import com.kadamitas.warlockery.entity.SpiritEntity;
import com.kadamitas.warlockery.entity.SpiritRules;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Private native-capture helper for the four approved visual remakes.
 *
 * <p>Health, inventory, arena geometry, and starting positions are disclosed fixtures. Every
 * appointment, court action, companion defence, attack swing, crossbow charge, projectile, and
 * damage consequence below is reached through the normal entity AI. The helper never calls a
 * phase/action sync method or an entity attack/damage outcome method.</p>
 */
public final class VisualRemakeMotionClientAcceptance implements FabricClientGameTest {
    private static final List<String> IDS = List.of("death", "naamah", "spirit", "werewolf_hunter");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("visual-remake-motion").resolve(UUID.randomUUID().toString());
        final String configured = System.getProperty("warlockery.visualRemakeMotionIds", "");
        final List<String> selected = configured.isBlank() ? IDS : Arrays.stream(configured.split(","))
            .map(String::strip).toList();
        check(!selected.isEmpty() && IDS.containsAll(selected), "Every selected case has a bounded native scenario");
        final int originalFov = context.computeOnClient(client -> client.options.fov().get());
        final boolean originalHud = context.computeOnClient(client -> client.gui.hud.isHidden());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                client.options.fov().set(58);
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            for (String id : selected) results.put(id, new LinkedHashMap<>(Map.of("status", "NOT_RUN")));
            write(false);
            for (String id : selected) {
                active = id;
                row().put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        switch (id) {
                            case "death" -> death(context);
                            case "naamah" -> naamah(context);
                            case "spirit" -> spirit(context);
                            case "werewolf_hunter" -> hunter(context);
                            default -> throw new AssertionError("Unhandled visual remake " + id);
                        }
                        row().put("status", "CAPTURED_REVIEW_PENDING");
                    } catch (Throwable failure) {
                        row().put("status", "FAILED");
                        row().put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        write(false);
                    }
                } finally {
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_VISUAL_REMAKE_MOTION_CAPTURED " + evidence);
        } catch (Throwable failure) {
            throw new AssertionError("Visual remake motion evidence: " + evidence, failure);
        } finally {
            context.runOnClient(client -> client.options.fov().set(originalFov));
            if (context.computeOnClient(client -> client.gui.hud.isHidden()) != originalHud) {
                context.getInput().pressKey(GLFW.GLFW_KEY_F1);
            }
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            final ServerPlayer player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(40.0F);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 90, 3, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 90, 0, false, false));
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-18, 99, -18), new BlockPos(18, 110, 18))) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.SMOOTH_STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState());
            }
        });
        stand(context, new Vec3(0.5, 100.0, -7.5));
        for (int i = 0; i < 3 && !context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()); i++) {
            context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        }
        if (!context.computeOnClient(client -> client.gui.hud.isHidden())) context.getInput().pressKey(GLFW.GLFW_KEY_F1);
        row().put("fixture", "Fresh Survival world at night; flat arena, starting positions, supplies, health thresholds, Resistance IV, and absorption are staged. AI remains enabled. Native mouse input supplies binding and player attacks. No presentation state or attack result is forced.");
    }

    private void death(final ClientGameTestContext context) throws Exception {
        stand(context, new Vec3(0.5, 100.0, -2.25));
        final UUID id = spawn("warlockery:death", new Vec3(0.5, 100.0, 0.5), false);
        final List<Map<String, Object>> frames = frames();
        await(context, player -> entity(player, id, DeathEntity.class).presentationPhase() == DeathRules.Phase.TELEGRAPH,
            180, "A visible non-disguised Survival player within 24 blocks naturally reaches Death's telegraph");
        captureFrame(context, id, "death-telegraph-early", frames);
        context.waitTicks(14);
        captureFrame(context, id, "death-telegraph-late", frames);
        final float protectedBefore = protectedHealth();
        await(context, player -> entity(player, id, DeathEntity.class).presentationPhase() == DeathRules.Phase.RECOVER
                && protectedHealth(player) < protectedBefore,
            50, "Death naturally completes its telegraph and lands its real reap against vanilla protection");
        captureFrame(context, id, "death-reap-impact", frames);
        context.waitTicks(2);
        captureFrame(context, id, "death-reap-follow-through-1", frames);
        context.waitTicks(2);
        captureFrame(context, id, "death-reap-follow-through-2", frames);
        row().put("trigger", "Fresh visible Survival player at 2.75 blocks; natural appointment, 40-tick TELEGRAPH, actual melee reap, then RECOVER.");
    }

    private void naamah(final ClientGameTestContext context) throws Exception {
        final UUID id = spawn("warlockery:naamah", new Vec3(0.5, 100.0, 0.5), false);
        hold(context, Items.BOW, new ItemStack(Items.ARROW, 16));
        final List<Map<String, Object>> frames = frames();

        server(player -> entity(player, id, NaamahEntity.class).setHealth(55.0F));
        stand(context, new Vec3(0.5, 100.0, -7.5));
        nativeAttack(context, id);
        await(context, player -> entity(player, id, NaamahEntity.class).getHealth() <= 67.0F,
            30, "The staged CHORUS health prerequisite remains below its threshold after native engagement");
        stand(context, new Vec3(0.5, 100.0, -8.0));
        await(context, player -> entity(player, id, NaamahEntity.class).presentationAction() == NaamahCourtRules.Action.COURT_WAVE,
            120, "CHORUS chooses COURT_WAVE through the court decision schedule");
        captureFrame(context, id, "naamah-court-wave-windup-1", frames);
        context.waitTicks(4);
        captureFrame(context, id, "naamah-court-wave-windup-2", frames);
        context.waitTicks(4);
        captureFrame(context, id, "naamah-court-wave-windup-3", frames);
        await(context, player -> entity(player, id, NaamahEntity.class).presentationAction() == NaamahCourtRules.Action.NONE,
            80, "Court wave executes and returns through normal recovery");

        server(player -> entity(player, id, NaamahEntity.class).setHealth(22.0F));
        stand(context, new Vec3(0.5, 100.0, -7.5));
        nativeAttack(context, id);
        await(context, player -> entity(player, id, NaamahEntity.class).getHealth() <= 34.0F,
            30, "The staged SOVEREIGN_REFUSAL health prerequisite remains below its threshold after native engagement");
        stand(context, new Vec3(0.5, 100.0, -8.0));
        await(context, player -> entity(player, id, NaamahEntity.class).presentationAction() == NaamahCourtRules.Action.DROWNING_SURGE,
            240, "SOVEREIGN_REFUSAL chooses DROWNING_SURGE through the court decision schedule");
        captureFrame(context, id, "naamah-drowning-surge-windup-1", frames);
        context.waitTicks(4);
        captureFrame(context, id, "naamah-drowning-surge-windup-2", frames);
        context.waitTicks(4);
        captureFrame(context, id, "naamah-drowning-surge-windup-3", frames);
        row().put("trigger", "Staged 55/22 health prerequisites select the phase bands; native bow input engages the court and actual AI chooses its actions. This verifies rendered action poses, not natural health-threshold crossing or a full survival boss fight.");
    }

    private void spirit(final ClientGameTestContext context) throws Exception {
        stand(context, new Vec3(0.5, 100.0, -2.25));
        final UUID spiritId = spawn("warlockery:spirit", new Vec3(0.5, 100.5, 0.5), true);
        hold(context, Items.SOUL_LANTERN, ItemStack.EMPTY);
        useEntity(context, spiritId);
        await(context, player -> CreatureBehaviorState.owner(entity(player, spiritId, SpiritEntity.class))
                .map(player.getUUID()::equals).orElse(false),
            40, "Native right-click with the tagged soul lantern binds the real Spirit");
        server(player -> entity(player, spiritId, SpiritEntity.class).setNoAi(false));

        final UUID attackerId = spawn("minecraft:zombie", new Vec3(0.5, 100.0, 5.5), false);
        server(player -> entity(player, attackerId, Zombie.class).setTarget(player));
        final List<Map<String, Object>> frames = frames();
        await(context, player -> entity(player, spiritId, SpiritEntity.class).presentationPhase() == SpiritRules.Phase.WARN,
            160, "A real zombie directly damages the owner and opens the Spirit's WARN window");
        captureFrame(context, spiritId, "spirit-warn-hover-1", frames);
        context.waitTicks(10);
        captureFrame(context, spiritId, "spirit-warn-hover-2", frames);
        await(context, player -> entity(player, spiritId, SpiritEntity.class).presentationPhase() == SpiritRules.Phase.DEFEND,
            70, "The bounded warning naturally advances to DEFEND");
        captureFrame(context, spiritId, "spirit-defend-flight-1", frames);
        context.waitTicks(2);
        captureFrame(context, spiritId, "spirit-defend-strike-1", frames);
        context.waitTicks(2);
        captureFrame(context, spiritId, "spirit-defend-strike-2", frames);
        row().put("attacker_alive_after_frames", serverValue(player -> {
            final Entity attacker = player.level().getEntity(attackerId);
            return attacker != null && attacker.isAlive();
        }));
        row().put("trigger", "Native soul-lantern binding followed by a normal zombie melee hit on the owner; Spirit AI performs WARN, flying DEFEND pursuit, and its one bounded strike.");
    }

    private void hunter(final ClientGameTestContext context) throws Exception {
        stand(context, new Vec3(0.5, 100.0, -6.5));
        final UUID id = spawn("warlockery:werewolf_hunter", new Vec3(0.5, 100.0, 0.5), false);
        hold(context, Items.BOW, new ItemStack(Items.ARROW, 8));
        final float healthBefore = serverValue(player -> entity(player, id, WerewolfHunterEntity.class).getHealth());
        aimEntity(context, id);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try { context.waitTicks(24); } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        await(context, player -> entity(player, id, WerewolfHunterEntity.class).getHealth() < healthBefore,
            50, "A native player bow shot records direct-attack evidence at non-close range");
        final List<Map<String, Object>> frames = frames();
        await(context, player -> entity(player, id, WerewolfHunterEntity.class).presentationIntent() == WerewolfHunterRules.Intent.WARN,
            70, "Direct-attack evidence naturally selects WARN");
        captureFrame(context, id, "hunter-warning", frames);
        await(context, player -> entity(player, id, WerewolfHunterEntity.class).presentationIntent() == WerewolfHunterRules.Intent.ENGAGE,
            70, "The 20-tick warning naturally advances to ENGAGE");
        captureFrame(context, id, "hunter-engage-approach", frames);
        final int boltsBefore = serverValue(player -> entity(player, id, WerewolfHunterEntity.class).silverBoltCount());
        await(context, player -> entity(player, id, WerewolfHunterEntity.class).isChargingCrossbow(),
            160, "The actual ranged goal starts charging the Hunter's crossbow");
        captureFrame(context, id, "hunter-crossbow-charge-1", frames);
        context.waitTicks(4);
        captureFrame(context, id, "hunter-crossbow-charge-2", frames);
        context.waitTicks(4);
        captureFrame(context, id, "hunter-crossbow-charge-3", frames);
        await(context, player -> entity(player, id, WerewolfHunterEntity.class).silverBoltCount() < boltsBefore,
            100, "The real crossbow attack fires and consumes one finite silver bolt");
        captureFrame(context, id, "hunter-crossbow-fired", frames);
        row().put("bolts_before", boltsBefore);
        row().put("bolts_after", serverValue(player -> entity(player, id, WerewolfHunterEntity.class).silverBoltCount()));
        row().put("trigger", "Native bow hit at seven blocks supplies direct-attack evidence without close-range warning bypass; actual WARN, ENGAGE, crossbow charge, projectile fire, and finite silver ammunition consumption follow.");
    }

    private List<Map<String, Object>> frames() {
        final List<Map<String, Object>> value = new ArrayList<>();
        row().put("motion_frames", value);
        return value;
    }

    private void captureFrame(
        final ClientGameTestContext context,
        final UUID id,
        final String name,
        final List<Map<String, Object>> frames
    ) throws Exception {
        final Map<String, Object> frame = serverValue(player -> {
            final LivingEntity subject = living(player, id);
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("game_time", player.level().getGameTime());
            value.put("position", subject.position().toString());
            value.put("velocity", subject.getDeltaMovement().toString());
            value.put("grounded", subject.onGround());
            value.put("using_item", subject.isUsingItem());
            value.put("main_hand", subject.getMainHandItem().toString());
            if (subject instanceof DeathEntity death) value.put("phase", death.presentationPhase().name());
            if (subject instanceof NaamahEntity naamah) {
                value.put("phase", naamah.presentationPhase().name());
                value.put("action", naamah.presentationAction().name());
                value.put("health", naamah.getHealth());
            }
            if (subject instanceof SpiritEntity spirit) value.put("phase", spirit.presentationPhase().name());
            if (subject instanceof WerewolfHunterEntity hunter) {
                value.put("intent", hunter.presentationIntent().name());
                value.put("charging_crossbow", hunter.isChargingCrossbow());
                value.put("silver_bolts", hunter.silverBoltCount());
            }
            return value;
        });
        frames.add(frame);
        cameraShot(context, id, name);
    }

    private UUID spawn(final String id, final Vec3 position, final boolean noAi) {
        final UUID result = serverValue(player -> {
            final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof Mob, "Fixture ID is a real mob: " + id);
            final Mob mob = (Mob) entity;
            mob.snapTo(position.x, position.y, position.z);
            mob.finalizeSpawn(
                player.level(),
                player.level().getCurrentDifficultyAt(mob.blockPosition()),
                EntitySpawnReason.COMMAND,
                null
            );
            mob.setNoAi(noAi);
            mob.setPersistenceRequired();
            if (mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            }
            check(player.level().addFreshEntity(mob), "Fixture enters the native world: " + id);
            return mob.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        return result;
    }

    private void nativeAttack(final ClientGameTestContext context, final UUID id) {
        aimEntity(context, id);
        row().put("native_hit_health_before", serverValue(player -> living(player, id).getHealth()));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try { context.waitTicks(25); } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        context.waitTicks(9);
        row().put("native_hit_health_after", serverValue(player -> living(player, id).getHealth()));
    }
    private void useEntity(final ClientGameTestContext context, final UUID id) {
        aimEntity(context, id);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(4);
    }

    private void aimEntity(final ClientGameTestContext context, final UUID id) {
        final Vec3 target = serverValue(player -> {
            final LivingEntity subject = living(player, id);
            return subject.position().add(0.0, subject.getBbHeight() * 0.55, 0.0);
        });
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
            client.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private void cameraShot(final ClientGameTestContext context, final UUID id, final String name) throws Exception {
        final Vec3 target = serverValue(player -> {
            final LivingEntity subject = living(player, id);
            return subject.position().add(0.0, subject.getBbHeight() * 0.52, 0.0);
        });
        final Vec3 eye = target.add(4.0, 2.0, 4.0);
        context.runOnClient(client -> {
            final LivingEntity camera = (LivingEntity) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:armor_stand")).create(client.level, EntitySpawnReason.COMMAND);
            check(camera != null, "Temporary client-only review camera can be created");
            final Vec3 delta = target.subtract(eye);
            camera.snapTo(
                eye.x, eye.y - camera.getEyeHeight(), eye.z,
                (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0),
                (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))
            );
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
            camera.setYHeadRot(camera.getYRot());
            camera.yHeadRotO = camera.getYRot();
            camera.setYBodyRot(camera.getYRot());
            camera.yBodyRotO = camera.getYRot();
            client.setCameraEntity(camera);
        });
        try {
            context.waitTicks(2);
            screenshot(context, name);
        } finally {
            context.runOnClient(client -> client.setCameraEntity(client.player));
            context.waitTicks(1);
        }
    }

    private void hold(final ClientGameTestContext context, final Item mainHand, final ItemStack offHand) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(mainHand));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, offHand);
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
    }

    private void stand(final ClientGameTestContext context, final Vec3 position) {
        server(player -> {
            player.teleportTo(position.x, position.y, position.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.player.position().distanceTo(position) < 0.15, 60);
        context.waitTicks(4);
    }

    private float protectedHealth() {
        return serverValue(VisualRemakeMotionClientAcceptance::protectedHealth);
    }

    private static float protectedHealth(final ServerPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }

    private void await(
        final ClientGameTestContext context,
        final Predicate<ServerPlayer> predicate,
        final int ticks,
        final String message
    ) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining--) {
            context.waitTicks(1);
        }
        check(serverValue(predicate::test), message);
    }

    private void server(final java.util.function.Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer()));
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        check(entity instanceof LivingEntity && entity.isAlive(), "Observed subject remains alive: " + id);
        return (LivingEntity)entity;
    }

    private static <T extends LivingEntity> T entity(final ServerPlayer player, final UUID id, final Class<T> type) {
        final LivingEntity entity = living(player, id);
        check(type.isInstance(entity), "Observed subject keeps expected type " + type.getSimpleName());
        return type.cast(entity);
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
        write(false);
    }

    private Map<String, Object> row() {
        return results.get(active);
    }

    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("capture_completed", finished && failures.isEmpty());
        report.put("visual_review", "PENDING_IMAGE_REVIEW");
        report.put("results", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        report.put("scope", "Rendered native Fabric Survival scenarios. Phase/action synchronization and direct attack/damage outcome calls are prohibited. Disclosed fixtures only establish arena, survival protection, starting positions, equipment, and Naamah threshold-adjacent health. Each requested motion is driven by native input and ordinary AI progression; images require human visual review.");
        final Path pending = evidence.resolve("visual-remake-motion.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("visual-remake-motion.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean valid, final String message) {
        if (!valid) throw new AssertionError(message);
    }
}
