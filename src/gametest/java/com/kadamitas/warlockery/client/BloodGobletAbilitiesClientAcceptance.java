package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.BloodGobletState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Native Glass Goblet (blood goblet) acceptance: real right-click fills on creatures, crouch self-fill,
 * vampire drinking and the level-nine creation rite. Vampire form, level, blood, coffin and the drained /
 * mesmerized villager flags are staged prerequisites; every fill, drink, refusal and conversion arises only
 * from native mouse and key input and is compared with an untreated control.
 */
public final class BloodGobletAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> SCENARIOS = List.of(
        "target_fill", "full_bound_state", "vampire_drinking", "crouched_self_fill", "vampire_creation");
    private static final Vec3 START = new Vec3(.5, 100, -1.5);
    private static final Vec3 TARGET = new Vec3(.5, 100, 1.5);
    private static final Vec3 SECOND = new Vec3(3.5, 100, 1.5);
    private static final Vec3 CONTROL = new Vec3(8.5, 100, 1.5);
    private static final BlockPos COFFIN = new BlockPos(2, 100, 3);
    private static final SupernaturalProgression.Path VAMPIRE = SupernaturalProgression.Path.VAMPIRE;
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private List<Map<String, Object>> pointers = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile SpawnObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("blood-goblet-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : SCENARIOS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("item", "glassgoblet"); row.put("status", "NOT_RUN");
                results.put(id, row);
            }
            for (String id : SCENARIOS) {
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING"); pointers = new ArrayList<>(); row.put("pointer_samples", pointers); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readBook(context, "glassgoblet", row);
                        switch (id) {
                            case "target_fill" -> targetFill(context, row);
                            case "full_bound_state" -> fullBoundState(context, row);
                            case "vampire_drinking" -> vampireDrinking(context, row);
                            case "crouched_self_fill" -> crouchedSelfFill(context, row);
                            case "vampire_creation" -> vampireCreation(context, row);
                            default -> throw new AssertionError("Unexpected scenario " + id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", stack(failure));
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        if (observation != null) row.put("spawn_observation", serverValue(player -> observation.report()));
                        observation = null; write(false);
                    }
                } finally { observation = null; world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BLOOD_GOBLET_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Blood goblet evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            SupernaturalState.setForm(player, SupernaturalForm.NONE);
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 97, -8), new BlockPos(14, 108, 10))) {
                final boolean wall = pos.getX() == -8 || pos.getX() == 14 || pos.getZ() == -8 || pos.getZ() == 10;
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 || wall && pos.getY() <= 103
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
            observation = new SpawnObservation(player);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        check(serverValue(player -> SupernaturalState.getForm(player) == SupernaturalForm.NONE), "Fresh actor starts mortal");
        row.put("fixture", "Fresh disposable Survival world at night; two-block stone floor with walls; natural regeneration off. "
            + "Goblet fills, refusals, drinks, self-fills and conversions arise only from native right-click / crouch input. "
            + "Vampire form, level, blood, head cover, coffin block and drained/mesmerized villager flags are staged prerequisites.");
        row.put("not_run", List.of("crafting acquisition", "Blood Crucible feeding", "goblet used on a Coffin",
            "player-target creation with crouch consent", "save/reload persistence", "sunlight exposure while holding the goblet"));
    }

    private void targetFill(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final Mob target = mob("minecraft:cow", TARGET), control = mob("minecraft:cow", CONTROL);
        supply(context, goblet());
        check(serverValue(player -> !BloodGobletState.isFull(player.getMainHandItem())), "Supplied goblet starts empty");
        lookUp(context); clearOverlay(context); use(context);
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.empty"));
        check(serverValue(player -> !BloodGobletState.isFull(player.getMainHandItem()) && target.getHealth() == 40 && control.getHealth() == 40),
            "Empty goblet used in the air as a mortal is refused and harms nobody");
        row.put("empty_refusal_status", "PASSED");
        screenshot(context, "target-fill-empty-refusal");
        aimEntity(context, target.getUUID()); clearOverlay(context); use(context);
        await(context, player -> BloodGobletState.isFull(player.getMainHandItem()), 30, "Native use on a creature fills the goblet");
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.filled"));
        check(serverValue(player -> target.getHealth() == 38), "One heart of damage lands on the sampled creature; observed=" + target.getHealth());
        check(serverValue(player -> control.getHealth() == 40), "Untreated control creature keeps full health");
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(target)).isPresent()),
            "Filled goblet records the sampled creature's sympathetic binding");
        check(serverValue(player -> target.getLastDamageSource() != null && target.getLastDamageSource().getEntity() == player),
            "Damage is a real player attack source, not fixture injection");
        row.put("fill", Map.of("target_health_before", 40, "target_health_after", serverValue(p -> target.getHealth()),
            "control_health_after", serverValue(p -> control.getHealth()),
            "binding", serverValue(p -> SympatheticBinding.read(p.getMainHandItem()).map(Object::toString).orElse(""))));
        row.put("fill_status", "PASSED");
        screenshot(context, "target-fill-after-native-use");
    }

    private void fullBoundState(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final Mob target = mob("minecraft:cow", TARGET), second = mob("minecraft:cow", SECOND);
        supply(context, goblet()); fillOn(context, target);
        server(player -> player.setHealth(10));
        check(serverValue(player -> player.getMainHandItem().hasFoil()), "Full goblet renders enchanted-style foil");
        lookUp(context); clearOverlay(context); use(context);
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.initiation_required"));
        context.waitTicks(5);
        check(serverValue(player -> player.getHealth() == 10 && BloodGobletState.isFull(player.getMainHandItem())
            && SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(target)).isPresent()),
            "Mortal drinking is refused: no healing, goblet stays full and bound");
        row.put("mortal_drink_refusal_status", "PASSED");
        screenshot(context, "full-bound-mortal-drink-refusal");
        aimEntity(context, second.getUUID()); clearOverlay(context); use(context);
        // interactLivingEntity returns FAIL, which vanilla treats as non-consuming: the client falls through to use(),
        // so the "vampire_required" overlay is immediately overwritten by the drink refusal. Either refusal proves the branch.
        final String observed = awaitOverlayAny(context, List.of(translated(context, "message.warlockery.blood_goblet.vampire_required"),
            translated(context, "message.warlockery.blood_goblet.initiation_required")));
        context.waitTicks(5);
        check(serverValue(player -> second.getHealth() == 40 && BloodGobletState.isFull(player.getMainHandItem())
            && SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(target)).isPresent()),
            "Full goblet on a second creature as a mortal is refused without damage or rebinding");
        row.put("mortal_creation_refusal", Map.of("expected_branch_message", translated(context, "message.warlockery.blood_goblet.vampire_required"),
            "overlay_observed", observed, "deviation", "BloodGobletItem.interactLivingEntity returns InteractionResult.FAIL on refusal; the client then also runs use(), whose drink refusal overlay replaces the creation refusal in the same tick"));
        row.put("mortal_creation_refusal_status", "PASSED");
        row.put("state", Map.of("full", true, "foil", true, "bound_to", target.getUUID().toString(), "second_creature_health", 40));
        screenshot(context, "full-bound-vampire-required-refusal");
    }

    private void vampireDrinking(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final Mob target = mob("minecraft:cow", TARGET);
        supply(context, goblet()); fillOn(context, target);
        server(player -> player.setHealth(10));
        lookUp(context); clearOverlay(context); use(context);
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.initiation_required"));
        context.waitTicks(5);
        final float controlHealth = serverValue(ServerPlayer::getHealth);
        check(controlHealth == 10 && serverValue(player -> BloodGobletState.isFull(player.getMainHandItem())), "Untreated mortal control gains nothing");
        stageVampire(context, 1, 50);
        final int blood = serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE));
        check(blood == 50, "Vampire starts the drink with exactly 50 blood");
        clearOverlay(context); use(context);
        await(context, player -> !BloodGobletState.isFull(player.getMainHandItem()), 30, "Native vampire drink empties the goblet");
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.consumed"));
        check(serverValue(player -> player.getHealth() == 16), "Drinking heals exactly three hearts; observed=" + serverValue(ServerPlayer::getHealth));
        check(serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE) == 80), "Drinking restores exactly 30 blood; observed="
            + serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE)));
        check(serverValue(player -> !player.getMainHandItem().hasFoil()), "Emptied goblet loses its foil");
        row.put("drink", Map.of("health_before", 10, "health_after", 16, "blood_before", 50, "blood_after", 80,
            "mortal_control_health_after_refusal", controlHealth));
        row.put("drink_status", "PASSED");
        screenshot(context, "vampire-drinking-after-native-drink");
    }

    private void crouchedSelfFill(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, goblet());
        server(player -> player.setHealth(10));
        lookUp(context); clearOverlay(context); crouchUse(context);
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.empty"));
        context.waitTicks(5);
        check(serverValue(player -> player.getHealth() == 10 && !BloodGobletState.isFull(player.getMainHandItem())),
            "Mortal crouch-use cannot take blood: refused as empty with no health loss");
        row.put("mortal_crouch_refusal_status", "PASSED");
        stageVampire(context, 1, 50);
        lookUp(context); clearOverlay(context); use(context);
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.empty"));
        context.waitTicks(5);
        check(serverValue(player -> player.getHealth() == 10 && !BloodGobletState.isFull(player.getMainHandItem())),
            "Vampire ordinary use of an empty goblet is refused without health loss (untreated control)");
        row.put("uncrouched_refusal_status", "PASSED");
        clearOverlay(context); crouchUse(context);
        await(context, player -> BloodGobletState.isFull(player.getMainHandItem()), 30, "Native crouch-use fills the goblet from the vampire's own blood");
        awaitOverlay(context, translated(context, "message.warlockery.blood_goblet.filled"));
        check(serverValue(player -> player.getHealth() == 8), "Self-fill costs exactly one heart; observed=" + serverValue(ServerPlayer::getHealth));
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(player)).isPresent()),
            "Self-filled goblet is bound to the vampire's own blood");
        row.put("self_fill", Map.of("health_before", 10, "health_after", 8));
        screenshot(context, "crouched-self-fill-after-native-crouch-use");
        clearOverlay(context); use(context);
        await(context, player -> !BloodGobletState.isFull(player.getMainHandItem()), 30, "Drinking the self-filled goblet empties it");
        check(serverValue(player -> player.getHealth() == 14 && SupernaturalProgression.resource(player, VAMPIRE) == 80), "Own blood drink heals three hearts and restores 30 blood");
        server(player -> player.setHealth(2));
        clearOverlay(context); crouchUse(context);
        await(context, player -> BloodGobletState.isFull(player.getMainHandItem()), 30, "Crouch-fill at one heart still fills");
        check(serverValue(player -> player.getHealth() == 1), "Self-fill never drops below half a heart; observed=" + serverValue(ServerPlayer::getHealth));
        row.put("half_heart_floor", Map.of("health_before", 2, "health_after", 1));
        row.put("self_fill_status", "PASSED");
        screenshot(context, "crouched-self-fill-half-heart-floor");
    }

    private void vampireCreation(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final Mob cow = mob("minecraft:cow", new Vec3(-3.5, 100, 1.5));
        // An awake villager consumes every right-click (trade screen or head shake) before the held item runs, so the
        // goblet only reaches a villager that is asleep, as villagers naturally are at night: both villagers sleep on real beds.
        final Mob villager = sleeper(context, "minecraft:villager", new BlockPos(0, 100, 2)), control = sleeper(context, "minecraft:villager", new BlockPos(8, 100, 2));
        server(player -> {
            for (Mob subject : List.of(villager, control)) {
                WarlockeryEntityData.get(subject).putBoolean("WarlockeryCreationTargetDrained", true);
                WarlockeryEntityData.get(subject).putString("WarlockeryMesmerizedBy", player.getStringUUID());
            }
            player.level().setBlockAndUpdate(COFFIN, ModBlocks.ALL.get("coffinblock").get().defaultBlockState());
        });
        check(serverValue(player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(COFFIN).getBlock()).toString().equals("warlockery:coffinblock")),
            "A real coffin block stands within four blocks of the target");
        row.put("staged_prerequisites", Map.of("coffin", COFFIN.toString(), "target_flags", "WarlockeryCreationTargetDrained=true, WarlockeryMesmerizedBy=actor",
            "control_flags", "identical, but the control villager is never targeted", "villagers", "asleep on real beds at night (Villager.mobInteract passes the click to the item only while sleeping)",
            "note", "Draining and mesmerizing are separate vampire powers; their resulting flags are staged, never the conversion itself"));
        row.put("not_run_extra", List.of("awake villager target (vanilla trade/head-shake consumes the click before the goblet runs)", "createVampire vampire power route via a looked-at target"));
        final Map<String, Object> cases = new LinkedHashMap<>(); row.put("cases", cases);
        final List<String> caseFailures = new ArrayList<>();
        supply(context, goblet()); fillOn(context, cow);
        runCase(context, cases, caseFailures, "mortal_refusal", () -> {
            aimEntity(context, villager.getUUID()); clearOverlay(context); use(context);
            final String observed = awaitOverlayAny(context, List.of(translated(context, "message.warlockery.blood_goblet.vampire_required"),
                translated(context, "message.warlockery.blood_goblet.initiation_required")));
            context.waitTicks(5);
            cases.put("mortal_refusal_overlay", observed);
            check(serverValue(player -> villager.isAlive() && BloodGobletState.isFull(player.getMainHandItem()) && observation.vampires.isEmpty()), "Mortal creation attempt is refused, goblet stays full, no vampire appears");
        });
        stageVampire(context, 1, 125);
        runCase(context, cases, caseFailures, "level_locked_refusal", () -> {
            ensureBoundTo(context, cow);
            final int blood = serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE));
            aimEntity(context, villager.getUUID()); clearOverlay(context); use(context);
            final String locked = translatedOrKey(context, "message.warlockery.blood_goblet.creation_locked");
            final String observed = awaitOverlayAny(context, List.of(locked, translated(context, "message.warlockery.blood_goblet.consumed")));
            context.waitTicks(5);
            cases.put("level_locked_observed", Map.of("overlay", observed, "translation_missing", locked.equals("message.warlockery.blood_goblet.creation_locked"),
                "goblet_full_after", serverValue(player -> BloodGobletState.isFull(player.getMainHandItem())),
                "blood_before", blood, "blood_after", serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE))));
            check(observed.equals(locked), "Guide: a full goblet used on a creature attempts creation instead of drinking; observed overlay=" + observed);
            check(serverValue(player -> villager.isAlive() && BloodGobletState.isFull(player.getMainHandItem()) && observation.vampires.isEmpty()
                && SupernaturalProgression.resource(player, VAMPIRE) == blood), "Level-one vampire is locked out of creation without drinking or spending blood; goblet_full="
                + serverValue(player -> BloodGobletState.isFull(player.getMainHandItem())) + " blood=" + serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE)));
        });
        screenshot(context, "vampire-creation-level-locked-refusal");
        server(player -> { SupernaturalProgression.setLevel(player, VAMPIRE, 9); SupernaturalProgression.setResource(player, VAMPIRE, 500); syncClient(player); });
        check(serverValue(player -> SupernaturalProgression.level(player, VAMPIRE) == 9 && SupernaturalProgression.resource(player, VAMPIRE) == 500), "Staged level nine with 500 blood");
        runCase(context, cases, caseFailures, "foreign_blood_refusal", () -> {
            ensureBoundTo(context, cow);
            final int blood = serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE));
            aimEntity(context, villager.getUUID()); clearOverlay(context); use(context);
            final String conditions = translatedOrKey(context, "message.warlockery.blood_goblet.creation_conditions");
            final String observed = awaitOverlayAny(context, List.of(conditions, translated(context, "message.warlockery.blood_goblet.consumed")));
            context.waitTicks(5);
            cases.put("foreign_blood_observed", Map.of("overlay", observed, "translation_missing", conditions.equals("message.warlockery.blood_goblet.creation_conditions"),
                "goblet_full_after", serverValue(player -> BloodGobletState.isFull(player.getMainHandItem())),
                "blood_before", blood, "blood_after", serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE))));
            check(observed.equals(conditions), "Guide: unmet conditions refuse creation instead of drinking; observed overlay=" + observed);
            check(serverValue(player -> villager.isAlive() && BloodGobletState.isFull(player.getMainHandItem()) && observation.vampires.isEmpty()
                && SupernaturalProgression.resource(player, VAMPIRE) == blood), "A goblet bound to another creature's blood fails the conditions without drinking or cost");
        });
        if (serverValue(player -> BloodGobletState.isFull(player.getMainHandItem()))) {
            lookUp(context); use(context);
            await(context, player -> !BloodGobletState.isFull(player.getMainHandItem()), 30, "Drinking the cow sample empties the goblet");
        }
        clearOverlay(context); crouchUse(context);
        await(context, player -> BloodGobletState.isFull(player.getMainHandItem())
            && SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(player)).isPresent(), 30, "Crouch-fill binds the goblet to the creator's own blood");
        check(serverValue(player -> villager.isAlive() && villager.isSleeping()), "Target villager still sleeps before the creation offer");
        final int bloodBefore = serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE));
        final Vec3 villagerPosition = serverValue(player -> villager.position());
        screenshot(context, "vampire-creation-before-native-offer");
        aimEntity(context, villager.getUUID());
        final Map<String, Object> gobletBefore = serverValue(player -> Map.of("full", BloodGobletState.isFull(player.getMainHandItem()),
            "bound_to", SympatheticBinding.read(player.getMainHandItem()).map(SympatheticBinding::toString).orElse("none"),
            "bound_to_actor", SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(player)).isPresent(),
            "level", SupernaturalProgression.level(player, VAMPIRE), "blood", SupernaturalProgression.resource(player, VAMPIRE),
            "client_form", "synced through the runtime's own sync"));
        row.put("goblet_before_creation_click", gobletBefore);
        check(Boolean.TRUE.equals(gobletBefore.get("full")) && Boolean.TRUE.equals(gobletBefore.get("bound_to_actor")), "Goblet is full and bound to the creator right before the creation click: " + gobletBefore);
        clearOverlay(context); use(context);
        await(context, player -> observation.vampires.size() == 1, 30, "Native full-goblet use on the prepared villager creates exactly one vampire");
        final String creationOverlay = awaitOverlayAny(context, List.of(translated(context, "message.warlockery.blood_goblet.converted"),
            translated(context, "message.warlockery.blood_goblet.empty")));
        row.put("creation_overlay_observed", creationOverlay);
        if (!creationOverlay.equals(translated(context, "message.warlockery.blood_goblet.converted")))
            row.put("creation_overlay_deviation", "Conversion succeeded but a client-side second use() on the emptied goblet replaced the converted overlay in the same tick");
        final Map<String, Object> created = serverValue(player -> Map.copyOf(observation.vampires.getFirst()));
        check(!serverValue(player -> villager.isAlive()) && serverValue(player -> control.isAlive() && control.getType() == villager.getType()), "Target villager is gone while the untreated control villager remains");
        check(villagerPosition.distanceTo(new Vec3((double) created.get("x"), (double) created.get("y"), (double) created.get("z"))) < .05, "Vampire appears at the villager's position");
        check(serverValue(player -> player.getStringUUID()).equals(created.get("owner")), "Created vampire is bound to its creator");
        check(serverValue(player -> !BloodGobletState.isFull(player.getMainHandItem())), "Success empties the goblet");
        check(serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE) == bloodBefore - 125), "Success spends exactly 125 blood; before=" + bloodBefore
            + " after=" + serverValue(player -> SupernaturalProgression.resource(player, VAMPIRE)));
        final UUID vampire = UUID.fromString((String) created.get("uuid"));
        clientWait(context, client -> { for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(vampire)) return true; return false; }, 60,
            "the created vampire " + vampire + " to be rendered by the client");
        look(context, serverValue(player -> player.level().getEntity(vampire).getBoundingBox().getCenter()));
        row.put("creation", Map.of("blood_before", bloodBefore, "blood_after", bloodBefore - 125, "created", created, "control_villager_alive", true));
        row.put("creation_status", "PASSED"); cases.put("creation", "PASSED");
        screenshot(context, "vampire-creation-after-native-conversion");
        check(caseFailures.isEmpty(), "Refusal cases asserted the guide-promised behaviour: " + String.join(" | ", caseFailures));
    }

    private void runCase(final ClientGameTestContext context, final Map<String, Object> cases, final List<String> failures, final String name, final ThrowingRunnable body) throws Exception {
        try { body.run(); cases.put(name, "PASSED"); }
        catch (AssertionError failure) {
            cases.put(name, "FAILED"); cases.put(name + "_failure", failure.getMessage()); failures.add(name + ": " + failure.getMessage());
            try { screenshot(context, "vampire-creation-" + name.replace('_', '-') + "-failure"); } catch (Throwable ignored) { }
        }
    }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

    /**
     * Staging the form through the progression API skips the runtime's own client sync packet, so the client-side
     * item prediction still believes the actor is mortal and falls through to a second use() after every creature
     * interaction. Real play syncs on join and on every change; the fixture invokes the same private sync.
     */
    private static void syncClient(final ServerPlayer player) {
        try {
            final var sync = com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime.class.getDeclaredMethod("sync", ServerPlayer.class);
            sync.setAccessible(true); sync.invoke(null, player);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Supernatural client sync is unavailable", failure); }
    }
    private static String translatedOrKey(final ClientGameTestContext context, final String key) {
        return context.computeOnClient(client -> Component.translatable(key).getString());
    }

    /** Restores a cow-bound full goblet natively after a refusal that (contrary to the guide) drank it. */
    private void ensureBoundTo(final ClientGameTestContext context, final Mob cow) {
        if (serverValue(player -> BloodGobletState.isFull(player.getMainHandItem()) && SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(cow)).isPresent())) return;
        if (serverValue(player -> BloodGobletState.isFull(player.getMainHandItem()))) {
            lookUp(context); use(context);
            await(context, player -> !BloodGobletState.isFull(player.getMainHandItem()), 30, "Native drink empties the goblet before refilling it on the cow");
        }
        fillOn(context, cow);
    }

    private Mob sleeper(final ClientGameTestContext context, final String id, final BlockPos bed) {
        final Mob mob = mob(id, Vec3.atBottomCenterOf(bed));
        server(player -> {
            player.level().setBlockAndUpdate(bed.relative(Direction.NORTH), Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
            player.level().setBlockAndUpdate(bed, Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT));
            mob.startSleeping(bed);
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        check(serverValue(player -> mob.isSleeping() && player.level().getBlockState(bed).getBlock() instanceof BedBlock), "Fixture creature sleeps on a real bed: " + id);
        return mob;
    }

    private void stageVampire(final ClientGameTestContext context, final int level, final int blood) {
        server(player -> {
            SupernaturalState.setForm(player, SupernaturalForm.VAMPIRE);
            SupernaturalProgression.setLevel(player, VAMPIRE, level);
            SupernaturalProgression.setResource(player, VAMPIRE, blood);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            player.inventoryMenu.broadcastChanges();
            syncClient(player);
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        check(serverValue(player -> SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, VAMPIRE) == level && SupernaturalProgression.resource(player, VAMPIRE) == blood),
            "Staged vampire prerequisite: level " + level + " with " + blood + " blood and head cover");
    }

    private void fillOn(final ClientGameTestContext context, final Mob target) {
        aimEntity(context, target.getUUID()); use(context);
        await(context, player -> BloodGobletState.isFull(player.getMainHandItem()), 30, "Native use on the staged creature fills the goblet");
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targets(target)).isPresent()), "Goblet is bound to the sampled creature");
    }

    private Mob mob(final String id, final Vec3 point) {
        return serverValue(player -> {
            final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
            check(mob != null, "Fixture entity exists: " + id);
            mob.snapTo(point.x, point.y, point.z); mob.setNoAi(true); mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); mob.setHealth(40);
            check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
        });
    }

    private static final class SpawnObservation {
        final ServerPlayer player;
        final List<Map<String, Object>> vampires = new ArrayList<>();
        SpawnObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("warlockery:vampire")) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", "warlockery:vampire"); row.put("uuid", entity.getUUID().toString());
                row.put("x", entity.getX()); row.put("y", entity.getY()); row.put("z", entity.getZ());
                row.put("owner", CreatureBehaviorState.owner(entity).map(UUID::toString).orElse(""));
                vampires.add(row);
            }
        }
        Map<String, Object> report() { return Map.of("vampire_spawns", List.copyOf(vampires), "observer", "Passive server entity-load event, no behavior injection"); }
    }

    private static ItemStack goblet() { return new ItemStack(ModItems.ALL.get("glassgoblet").get()); }
    private void supply(final ClientGameTestContext context, final ItemStack item) {
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, item); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(2);
    }
    private static void use(final ClientGameTestContext context) { context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3); }
    private static void crouchUse(final ClientGameTestContext context) {
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        try { use(context); } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(2); }
    }
    private void lookUp(final ClientGameTestContext context) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-70); }); context.waitTicks(2);
        check(context.computeOnClient(client -> !(client.hitResult instanceof EntityHitResult)), "Looking up targets no creature");
    }
    /** Survival entity reach is three blocks: stand 1.8 blocks south of the creature, aim at its bounding-box centre, then verify the client pointer. */
    private void aimEntity(final ClientGameTestContext context, final UUID id) {
        final List<Map<String, Object>> samples = new ArrayList<>();
        // Sleeping villagers have a 0.2-wide hitbox and the client keeps a stale standing box until pose and bed position arrive.
        world.getConnection().waitForClientboundPackets();
        final Vec3 serverCentre = serverValue(player -> player.level().getEntity(id).getBoundingBox().getCenter());
        for (int tick = 0; tick < 40 && !context.computeOnClient(client -> { for (Entity entity : client.level.entitiesForRendering())
            if (entity.getUUID().equals(id)) return entity.getBoundingBox().getCenter().distanceTo(serverCentre) < .15; return false; }); tick++) context.waitTicks(1);
        final Map<String, Object> sync = Map.of("server_bbox", serverValue(player -> player.level().getEntity(id).getBoundingBox().toString()),
            "client_bbox", context.computeOnClient(client -> { for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity.getBoundingBox().toString(); return "not rendered"; }));
        final Vec3[] stances = {new Vec3(0, 0, -1.8), new Vec3(-1.6, 0, 0), new Vec3(1.6, 0, 0)};
        for (Vec3 stance : stances) {
            final Vec3 stand = serverValue(player -> player.level().getEntity(id).position().add(stance));
            server(player -> { player.teleportTo(stand.x, stand.y, stand.z); player.setDeltaMovement(Vec3.ZERO); });
            world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
            for (int candidate = 0; candidate < 2; candidate++) {
                final int which = candidate;
                final Vec3 point = which == 0 ? serverValue(player -> player.level().getEntity(id).getBoundingBox().getCenter())
                    : context.computeOnClient(client -> { for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity.getBoundingBox().getCenter(); return null; });
                if (point == null) { samples.add(Map.of("stance", stance.toString(), "aim", "client entity not rendered yet")); continue; }
                look(context, point); context.waitTicks(2);
                final Map<String, Object> pointer = context.computeOnClient(client -> {
                    final Map<String, Object> value = new LinkedHashMap<>();
                    value.put("stance", stance.toString()); value.put("aim_source", which == 0 ? "server_bbox_centre" : "client_bbox_centre"); value.put("aim", point.toString());
                    value.put("hit_type", client.hitResult == null ? "none" : client.hitResult.getType().name());
                    value.put("hit_entity", client.hitResult instanceof EntityHitResult hit ? hit.getEntity().getType() + " " + hit.getEntity().getUUID() : "none");
                    value.put("intended_entity", id.toString()); value.put("eye", client.player.getEyePosition().toString());
                    value.put("yaw", client.player.getYRot()); value.put("pitch", client.player.getXRot());
                    return value;
                });
                pointer.put("distance", serverValue(player -> player.distanceTo(player.level().getEntity(id))));
                pointer.put("server_pose", serverValue(player -> player.level().getEntity(id).getPose().name()));
                pointer.put("hitbox_sync", sync);
                samples.add(pointer);
                if ("ENTITY".equals(pointer.get("hit_type")) && ((String) pointer.get("hit_entity")).endsWith(id.toString())) { pointers.add(pointer); return; }
            }
        }
        pointers.addAll(samples);
        throw new AssertionError("Native pointer targets exact intended creature; samples=" + samples);
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> { final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)))); });
        context.waitTicks(2);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining--) context.waitTicks(1);
        check(serverValue(predicate::test), message);
    }
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        final String text = context.computeOnClient(client -> Component.translatable(key, arguments).getString());
        check(!text.equals(key), "Goblet diagnostic has readable translated text: " + key); return text;
    }
    private static void clearOverlay(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.setOverlayMessage(Component.empty(), false)); }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value); });
    }
    private static String awaitOverlayAny(final ClientGameTestContext context, final List<String> accepted) {
        for (int tick = 0; tick < 30 && !accepted.contains(overlay(context)); tick++) context.waitTicks(1);
        check(accepted.contains(overlay(context)), "Rendered overlay reports one of " + accepted + "; actual=" + overlay(context));
        return overlay(context);
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports goblet result: " + expected + "; actual=" + overlay(context));
    }
    private void readBook(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst().orElseThrow();
        supply(context, new ItemStack(ModItems.ALL.get(profile.id()).get()));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        clientWait(context, client -> client.gui.screen() instanceof ManualScreen, 60, "manual screen opens after native use of " + profile.id());
        ManualClientAcceptance.selectSection(context, id);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Goblet guide contains actual instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection")) && expected == (int) field(client.gui.screen(), "bodyPage")), "Native book buttons visit each guide page");
            screenshot(context, row.get("item") + "-" + id + "-book-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", id, "text", body, "pages_read", pages)); row.put("guide_status", "PASSED");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        clientWait(context, client -> client.gui.screen() == null, 40, "manual closes after Escape");
    }
    private static void clientWait(final ClientGameTestContext context, final Predicate<net.minecraft.client.Minecraft> predicate, final int ticks, final String message) {
        for (int tick = 0; tick < ticks && !context.computeOnClient(predicate::test); tick++) context.waitTicks(1);
        check(context.computeOnClient(predicate::test), "Timed out waiting for " + message);
    }
    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        final String unique = screenshots.stream().anyMatch(path -> path.endsWith(name + ".png")) ? name + "-" + screenshots.size() : name;
        ManualClientAcceptance.saveScreenshot(context, evidence, unique, screenshots);
    }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }

    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>(); report.put("completed", complete);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty() && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric client; fresh world per scenario; goblet book read through native clicks; fills, drinks, self-fills, refusals and the creation rite arise from real right-click and crouch input against staged creatures with untreated controls.");
        report.put("scenarios", results); report.put("screenshots", screenshots); report.put("failures", failures);
        final Path temp = evidence.resolve("blood-goblet-abilities.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("blood-goblet-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
