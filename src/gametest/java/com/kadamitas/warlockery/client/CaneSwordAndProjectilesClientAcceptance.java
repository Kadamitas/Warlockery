package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.CaneSwordItem;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Native Cane Sword, bolt and Wooden Stake acceptance. Every toggle, walk, melee strike and bow shot comes
 * from real key and mouse input; each bolt is compared against a vanilla arrow fired at an identical
 * untreated creature, and the stake finisher against awake, non-vampire and non-stake controls.
 */
public final class CaneSwordAndProjectilesClientAcceptance implements FabricClientGameTest {
    private static final List<String> SCENARIOS = List.of(
        "cane_sword", "splitting_bolt", "holy_bolt", "silver_bolt", "wooden_bolt", "nullifying_bolt", "wooden_stake");
    private static final Vec3 START = new Vec3(.5, 100, .5);
    private static final double BASE_WALK_SPEED = .1;
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private List<Map<String, Object>> pointers = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile ArrowObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("cane-sword-and-projectiles").resolve(UUID.randomUUID().toString());
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
                row.put("item", item(id)); row.put("status", "NOT_RUN");
                results.put(id, row);
            }
            for (String id : SCENARIOS) {
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING"); pointers = new ArrayList<>(); row.put("pointer_samples", pointers); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        switch (id) {
                            case "cane_sword" -> caneSword(context, row);
                            case "splitting_bolt" -> splittingBolt(context, row);
                            case "holy_bolt" -> boltVersusCreature(context, row, "ingredient_bolt_holy", "warlockery:vampire", 1.5F, true);
                            case "silver_bolt" -> boltVersusCreature(context, row, "ingredient_bolt_silver", "warlockery:werewolf", 2.0F, true);
                            case "wooden_bolt" -> boltVersusCreature(context, row, "ingredient_bolt_stake", "warlockery:ent", 2.0F, false);
                            case "nullifying_bolt" -> nullifyingBolt(context, row);
                            case "wooden_stake" -> woodenStake(context, row);
                            default -> throw new AssertionError("Unexpected scenario " + id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", stack(failure));
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                        if (observation != null) row.put("arrow_observation", serverValue(player -> observation.report()));
                        observation = null; write(false);
                    }
                } finally { observation = null; world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_CANE_SWORD_AND_PROJECTILES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Cane sword and projectile evidence: " + evidence, failure);
        }
    }

    private static String item(final String scenario) {
        return switch (scenario) {
            case "cane_sword" -> "canesword";
            case "splitting_bolt" -> "ingredient_bolt_splitting";
            case "holy_bolt" -> "ingredient_bolt_holy";
            case "silver_bolt" -> "ingredient_bolt_silver";
            case "wooden_bolt" -> "ingredient_bolt_stake";
            case "nullifying_bolt" -> "ingredient_bolt_anti_magic";
            default -> "ingredient_stake";
        };
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
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-14, 97, -14), new BlockPos(14, 108, 14))) {
                final boolean wall = Math.abs(pos.getX()) == 14 || Math.abs(pos.getZ()) == 14;
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 || wall && pos.getY() <= 105
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
            observation = new ArrowObservation(player);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world at night (vampire and zombie fixtures must not burn); two-block stone floor with "
            + "five-block walls that stop stray arrows; natural regeneration off. Staged NoAI creatures have 200 max health and zero armor so "
            + "health loss reflects the mod's damage adjustment. Every toggle, walk, strike and shot is native key/mouse input.");
    }

    private void caneSword(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readBook(context, "canesword", row, true);
        row.put("not_run", List.of("crafting acquisition", "durability exhaustion", "sprint-jump critical strikes", "save/reload persistence"));
        final Mob first = mob("minecraft:cow", new Vec3(.5, 100, 3.5), 40), second = mob("minecraft:cow", new Vec3(4.5, 100, 3.5), 40),
            control = mob("minecraft:cow", new Vec3(-4.5, 100, 3.5), 40);
        supply(context, modItem("canesword")); context.waitTicks(5);
        check(serverValue(player -> !CaneSwordItem.isDrawn(player.getMainHandItem())), "Supplied cane starts sheathed");
        check(serverValue(player -> Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - CaneSwordItem.movementSpeed(false, BASE_WALK_SPEED)) < 1e-6
            && player.getAttributeValue(Attributes.ATTACK_DAMAGE) == CaneSwordItem.attackDamage(false)),
            "Sheathed cane grants the 15% walking bonus and only bare attack damage; speed=" + serverValue(player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        lookUp(context); clearOverlay(context); use(context); context.waitTicks(5);
        check(serverValue(player -> !CaneSwordItem.isDrawn(player.getMainHandItem())) && !overlay(context).equals(translated(context, "message.warlockery.cane_sword.drawn")),
            "Ordinary use without crouching does not draw the blade (control)");
        row.put("uncrouched_use_control_status", "PASSED");
        final double sheathedWalk = walk(context);
        final float sheathedLoss = strike(context, first, 30);
        check(Math.abs(sheathedLoss - CaneSwordItem.attackDamage(false)) < .6, "Sheathed strike deals bare-hand damage; observed=" + sheathedLoss);
        screenshot(context, "cane-sword-sheathed-after-native-strike");
        lookUp(context); clearOverlay(context); crouchUse(context);
        await(context, player -> CaneSwordItem.isDrawn(player.getMainHandItem()), 30, "Native crouch-use draws the hidden blade");
        awaitOverlay(context, translated(context, "message.warlockery.cane_sword.drawn"));
        context.waitTicks(5);
        check(serverValue(player -> CaneSwordItem.DRAWN_MODEL.equals(player.getMainHandItem().get(DataComponents.ITEM_MODEL))), "Drawn cane changes its item model");
        check(serverValue(player -> Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - BASE_WALK_SPEED) < 1e-6
            && player.getAttributeValue(Attributes.ATTACK_DAMAGE) == CaneSwordItem.DRAWN_ATTACK_DAMAGE),
            "Drawn blade sets attack damage 7 and stops the walking bonus; speed=" + serverValue(player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        final double drawnWalk = walk(context);
        check(sheathedWalk > drawnWalk * 1.08, "Actual native walking is faster with the sheathed cane: sheathed=" + sheathedWalk + " drawn=" + drawnWalk);
        final float drawnLoss = strike(context, second, 30);
        check(Math.abs(drawnLoss - CaneSwordItem.DRAWN_ATTACK_DAMAGE) < .6, "Drawn strike deals seven damage before armor; observed=" + drawnLoss);
        check(serverValue(player -> control.getHealth() == 40), "Untreated control cow is never harmed");
        screenshot(context, "cane-sword-drawn-after-native-strike");
        lookUp(context); clearOverlay(context); crouchUse(context);
        await(context, player -> !CaneSwordItem.isDrawn(player.getMainHandItem()), 30, "Second crouch-use sheathes the blade");
        awaitOverlay(context, translated(context, "message.warlockery.cane_sword.sheathed"));
        context.waitTicks(5);
        check(serverValue(player -> Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - CaneSwordItem.movementSpeed(false, BASE_WALK_SPEED)) < 1e-6
            && CaneSwordItem.CANE_MODEL.equals(player.getMainHandItem().get(DataComponents.ITEM_MODEL))), "Sheathing restores the walking bonus and cane model");
        row.put("cane", Map.of("sheathed_walk_blocks_20_ticks", sheathedWalk, "drawn_walk_blocks_20_ticks", drawnWalk,
            "sheathed_strike_loss", sheathedLoss, "drawn_strike_loss", drawnLoss, "control_cow_health", 40,
            "durability_used", serverValue(player -> player.getMainHandItem().getDamageValue())));
        row.put("draw_status", "PASSED"); row.put("sheath_status", "PASSED"); row.put("walk_status", "PASSED"); row.put("strike_status", "PASSED");
        screenshot(context, "cane-sword-resheathed");
    }

    private void splittingBolt(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readBook(context, "ingredient_bolt_splitting", row, true);
        row.put("not_run", List.of("crafting acquisition", "bolt taken from the inventory instead of the off hand", "side-arrow damage on a creature", "pickup attempt of side arrows"));
        // Refusal control: bolts are crossbow ammunition only, so a bow with only a bolt in the off hand fires and spends nothing.
        position(context, START); arm(context, Items.BOW, modItem("ingredient_bolt_splitting"), 4);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(2);
        server(player -> observation.arrows.clear());
        check(serverValue(player -> player.getProjectile(player.getMainHandItem()).isEmpty()), "A bow finds no usable ammunition among bolts");
        drawBow(context);
        final Map<String, Object> bowRefusal = Map.of("weapon", "minecraft:bow", "offhand", "warlockery:ingredient_bolt_splitting",
            "arrows_launched", serverValue(player -> List.copyOf(observation.arrows)), "offhand_count_after", serverValue(player -> player.getOffhandItem().getCount()));
        check(serverValue(player -> observation.arrows.isEmpty() && player.getOffhandItem().getCount() == 4), "A full bow draw with only bolts fires nothing and consumes nothing; observed=" + bowRefusal);
        row.put("bow_refusal", bowRefusal); row.put("bow_refusal_status", "PASSED");
        screenshot(context, "splitting-bolt-bow-refusal");
        final Map<String, Object> treated = volley(context, modItem("ingredient_bolt_splitting"));
        final List<Map<String, Object>> arrows = arrowRows(treated);
        check(arrows.size() == 3, "A fired Splitting Bolt produces a three-arrow fan; observed=" + arrows);
        check(arrows.stream().filter(arrow -> "DISALLOWED".equals(arrow.get("pickup"))).count() == 2
            && arrows.stream().filter(arrow -> "DISALLOWED".equals(arrow.get("pickup"))).allMatch(arrow -> (double) arrow.get("base_damage") < 2.0),
            "Two side arrows cannot be picked up and carry reduced base damage; observed=" + arrows);
        check(arrows.stream().filter(arrow -> !"DISALLOWED".equals(arrow.get("pickup"))).count() == 1, "Exactly one main shot remains an ordinary arrow");
        screenshot(context, "splitting-bolt-after-native-shot");
        final Map<String, Object> control = volley(context, new ItemStack(Items.ARROW));
        check(arrowRows(control).size() == 1, "Untreated vanilla arrow control produces exactly one arrow; observed=" + arrowRows(control));
        row.put("volley", Map.of("splitting", treated, "vanilla_control", control)); row.put("split_status", "PASSED");
        screenshot(context, "splitting-bolt-vanilla-control");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> arrowRows(final Map<String, Object> volley) { return (List<Map<String, Object>>) volley.get("arrows"); }

    private Map<String, Object> volley(final ClientGameTestContext context, final ItemStack ammo) {
        position(context, START); arm(context, ammo, 4);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(2);
        final int ammoBefore = serverValue(player -> player.getOffhandItem().getCount());
        server(player -> observation.arrows.clear());
        final Map<String, Object> charge = fire(context);
        await(context, player -> !observation.arrows.isEmpty(), 20, "Native charged crossbow click launches at least one arrow; charge=" + charge);
        context.waitTicks(5);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("weapon", "minecraft:crossbow"); result.put("crossbow_charge", charge);
        result.put("ammo", BuiltInRegistries.ITEM.getKey(ammo.getItem()).toString());
        result.put("ammo_before", ammoBefore); result.put("ammo_after", serverValue(player -> player.getOffhandItem().getCount()));
        result.put("arrows", serverValue(player -> List.copyOf(observation.arrows)));
        check((int) result.get("ammo_after") == ammoBefore - 1, "Exactly one piece of ammunition is spent per shot");
        return result;
    }

    private void boltVersusCreature(final ClientGameTestContext context, final Map<String, Object> row, final String bolt,
        final String creature, final float multiplier, final boolean supernatural) throws Exception {
        readBook(context, bolt, row, true);
        row.put("not_run", List.of("crafting acquisition", "bolt taken from the inventory instead of the off hand", "bolt against a non-weak creature (no bonus)", "player targets with a supernatural form"));
        final Mob treated = mob(creature, new Vec3(-3.5, 100, 6.5), 200), control = mob(creature, new Vec3(4.5, 100, 6.5), 200);
        final Map<String, Object> controlShot = shoot(context, new ItemStack(Items.ARROW), control);
        final Map<String, Object> treatedShot = shoot(context, modItem(bolt), treated);
        final float controlLoss = (float) controlShot.get("health_loss"), treatedLoss = (float) treatedShot.get("health_loss");
        // A crossbow arrow deals 6..11 before adjustment (velocity 3.15, random critical bonus). Supernatural kinds take 15% (floor 0.25) of untreated hits.
        final float controlMaximum = supernatural ? 1.7F : 11.5F, treatedMinimum = 6 * multiplier - .5F;
        check(controlLoss > 0 && controlLoss <= controlMaximum, "Vanilla arrow control on " + creature + " lands within the untreated range; observed=" + controlLoss);
        check(treatedLoss >= treatedMinimum && treatedLoss >= controlLoss, bolt + " deals the documented x" + multiplier + " weakness damage; treated=" + treatedLoss + " control=" + controlLoss);
        check(modItem(bolt).getItem() == BuiltInRegistries.ITEM.getValue(Identifier.parse((String) treatedShot.get("pickup_origin"))), "Treated hit came from the actual bolt projectile");
        row.put("comparison", Map.of("creature", creature, "vanilla_arrow_control", controlShot, "bolt", treatedShot, "documented_multiplier", multiplier,
            "untreated_supernatural_reduction", supernatural ? "15% floor 0.25" : "none"));
        row.put("weakness_status", "PASSED");
        look(context, serverValue(player -> treated.getBoundingBox().getCenter()));
        screenshot(context, bolt + "-after-native-shots");
    }

    private void nullifyingBolt(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readBook(context, "ingredient_bolt_anti_magic", row, true);
        row.put("not_run", List.of("crafting acquisition", "bolt taken from the inventory instead of the off hand", "Silvered and Dawn set variants", "player victim reserve drain", "Wither persistence"));
        final Mob treated = mob("minecraft:cow", new Vec3(-4.5, 100, 6.5), 200), control = mob("minecraft:cow", new Vec3(.5, 100, 6.5), 200),
            incomplete = mob("minecraft:cow", new Vec3(5.5, 100, 6.5), 200);
        server(player -> {
            for (Mob cow : List.of(treated, control, incomplete)) {
                cow.addEffect(new MobEffectInstance(MobEffects.SPEED, 6000, 0)); cow.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 6000, 0));
                cow.addEffect(new MobEffectInstance(MobEffects.POISON, 6000, 0));
            }
            wearHunterSet(player, true);
        });
        context.waitTicks(3);
        check(serverValue(EquipmentSetEffects::wearsCompleteHunterSet), "Actor wears the complete base Werewolf Hunter set");
        final Map<String, Object> controlShot = shoot(context, new ItemStack(Items.ARROW), control);
        context.waitTicks(5);
        check(serverValue(player -> effects(control).containsAll(List.of("minecraft:speed", "minecraft:jump_boost", "minecraft:poison"))), "Vanilla arrow control keeps every effect; observed=" + serverValue(player -> effects(control)));
        final List<String> treatedBefore = serverValue(player -> effects(treated));
        check(serverValue(EquipmentSetEffects::wearsCompleteHunterSet), "Complete hunter set is still worn after the control shot");
        final Map<String, Object> treatedShot = shoot(context, modItem("ingredient_bolt_anti_magic"), treated);
        treatedShot.put("victim_effects_before", treatedBefore);
        treatedShot.put("shooter_wore_complete_set_at_shot", serverValue(EquipmentSetEffects::wearsCompleteHunterSet));
        treatedShot.put("arrow_entities", serverValue(player -> List.copyOf(observation.arrows)));
        await(context, player -> !treated.hasEffect(MobEffects.SPEED), 10, "Nullifying hit removes the victim's potion effects; before=" + treatedBefore
            + " after=" + serverValue(player -> effects(treated)) + " shot=" + treatedShot);
        treatedShot.put("victim_effects_after", serverValue(player -> effects(treated)));
        check(serverValue(player -> !treated.hasEffect(MobEffects.JUMP_BOOST) && treated.hasEffect(MobEffects.POISON)), "Nullification keeps Poison; observed=" + serverValue(player -> effects(treated)));
        screenshot(context, "nullifying-bolt-after-native-shot");
        server(player -> player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY));
        context.waitTicks(3);
        check(serverValue(player -> !EquipmentSetEffects.wearsCompleteHunterSet(player)), "Removing the boots breaks the set");
        final Map<String, Object> incompleteShot = shoot(context, modItem("ingredient_bolt_anti_magic"), incomplete);
        context.waitTicks(5);
        check(serverValue(player -> effects(incomplete).containsAll(List.of("minecraft:speed", "minecraft:jump_boost", "minecraft:poison"))), "Without the complete set the bolt shoots but strips nothing; observed=" + serverValue(player -> effects(incomplete)));
        row.put("comparison", Map.of("vanilla_arrow_control", controlShot, "control_effects_after", serverValue(player -> effects(control)),
            "bolt_with_complete_set", treatedShot, "treated_effects_after", serverValue(player -> effects(treated)),
            "bolt_without_complete_set", incompleteShot, "incomplete_effects_after", serverValue(player -> effects(incomplete))));
        row.put("nullification_status", "PASSED");
        screenshot(context, "nullifying-bolt-incomplete-set-control");
    }

    private void woodenStake(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readBook(context, "weaknesses", row, false);
        row.put("not_run", List.of("crafting acquisition", "sleeping vampire player target", "stake on Naamah or Blood Thrall", "vampire mob native sleep (no code path puts the mob to sleep; sleep is staged on a real bed)"));
        final Mob stickTarget = sleeper(context, "warlockery:vampire", new BlockPos(-6, 100, 4));
        final Mob zombie = sleeper(context, "minecraft:zombie", new BlockPos(-2, 100, 4));
        final Mob awake = mob("warlockery:vampire", new Vec3(2.5, 100, 4.5), 200);
        final Mob finisher = sleeper(context, "warlockery:vampire", new BlockPos(6, 100, 4));
        final float stickLoss = strike(context, stickTarget, 30, new ItemStack(Items.STICK));
        check(serverValue(player -> stickTarget.isAlive()) && stickLoss < 5, "A stick on a sleeping vampire is no finisher; loss=" + stickLoss);
        final float awakeLoss = strike(context, awake, 30, modItem("ingredient_stake"));
        check(serverValue(player -> awake.isAlive()) && awakeLoss < 5, "The stake on an awake vampire is no finisher; loss=" + awakeLoss);
        final float zombieLoss = strike(context, zombie, 30, modItem("ingredient_stake"));
        check(serverValue(player -> zombie.isAlive()) && zombieLoss < 5, "The stake on a sleeping non-vampire is no finisher; loss=" + zombieLoss);
        screenshot(context, "wooden-stake-controls-survive");
        check(serverValue(player -> finisher.isSleeping() && finisher.getHealth() == 200), "Finisher target still sleeps at full health before the native strike");
        final float finisherLoss = strike(context, finisher, 30, modItem("ingredient_stake"));
        await(context, player -> !finisher.isAlive(), 20, "A Wooden Stake driven into a sleeping vampire's heart finishes it; loss=" + finisherLoss);
        row.put("comparison", Map.of("stick_on_sleeping_vampire_loss", stickLoss, "stake_on_awake_vampire_loss", awakeLoss,
            "stake_on_sleeping_zombie_loss", zombieLoss, "stake_on_sleeping_vampire_loss", finisherLoss, "sleeping_vampire_dead", true));
        row.put("finisher_status", "PASSED");
        screenshot(context, "wooden-stake-after-native-finisher");
    }

    private Mob sleeper(final ClientGameTestContext context, final String id, final BlockPos bed) {
        final Mob mob = mob(id, Vec3.atBottomCenterOf(bed), 200);
        server(player -> {
            player.level().setBlockAndUpdate(bed.relative(Direction.NORTH), Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
            player.level().setBlockAndUpdate(bed, Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT));
            mob.startSleeping(bed);
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        check(serverValue(player -> mob.isSleeping() && player.level().getBlockState(bed).getBlock() instanceof BedBlock), "Fixture creature sleeps on a real bed: " + id);
        return mob;
    }

    private static List<String> effects(final Mob mob) {
        return mob.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName()).sorted().toList();
    }

    private static void wearHunterSet(final ServerPlayer player, final boolean complete) {
        player.setItemSlot(EquipmentSlot.HEAD, modItem("werewolf_hunter_hat")); player.setItemSlot(EquipmentSlot.CHEST, modItem("werewolf_hunter_coat"));
        player.setItemSlot(EquipmentSlot.LEGS, modItem("werewolf_hunter_leggings")); player.setItemSlot(EquipmentSlot.FEET, complete ? modItem("werewolf_hunter_boots") : ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }

    /** Fires a fully drawn bow natively from four blocks south of the target until an actual hit lands (at most four shots). */
    private Map<String, Object> shoot(final ClientGameTestContext context, final ItemStack ammo, final Mob target) {
        final Vec3 stand = serverValue(player -> target.position().add(0, 0, -4));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ammo", BuiltInRegistries.ITEM.getKey(ammo.getItem()).toString());
        result.put("target", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        int shots = 0; float loss = 0;
        for (int attempt = 0; attempt < 4 && loss <= 0; attempt++) {
            position(context, stand); arm(context, ammo, 8);
            world.getConnection().waitForClientboundPackets();
            look(context, serverValue(player -> target.getBoundingBox().getCenter()));
            final float before = serverValue(player -> target.getHealth());
            server(player -> observation.arrows.clear());
            result.put("crossbow_charge_" + shots, fire(context)); shots++;
            // Poison ticks (nullifying fixture) also lower health, so a hit is only an arrow-sourced drop.
            for (int tick = 0; tick < 40 && !serverValue(player -> target.getHealth() < before && target.getLastDamageSource() != null
                && target.getLastDamageSource().getDirectEntity() instanceof AbstractArrow); tick++) context.waitTicks(1);
            final float after = serverValue(player -> target.getHealth());
            loss = serverValue(player -> target.getLastDamageSource() != null && target.getLastDamageSource().getDirectEntity() instanceof AbstractArrow) ? before - after : 0;
            if (loss > 0) {
                result.put("health_before", before); result.put("health_after", after);
                result.put("last_hurt", serverValue(player -> field(target, "lastHurt")));
                final DamageSource source = serverValue(player -> target.getLastDamageSource());
                result.put("direct_entity", source == null || source.getDirectEntity() == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()).toString());
                result.put("pickup_origin", source != null && source.getDirectEntity() instanceof AbstractArrow arrow
                    ? BuiltInRegistries.ITEM.getKey(arrow.getPickupItemStackOrigin().getItem()).toString() : "none");
                result.put("shooter_is_actor", serverValue(player -> source != null && source.getEntity() == player));
            }
        }
        result.put("shots_fired", shots); result.put("health_loss", loss);
        check(loss > 0, "A native crossbow shot actually hits the staged creature within four attempts: " + result);
        check(Boolean.TRUE.equals(result.get("shooter_is_actor")) && "minecraft:arrow".equals(result.get("direct_entity")), "The hit is the actor's own arrow: " + result);
        return result;
    }

    /** Bolts are crossbow ammunition only: hold right-click until the crossbow is charged, release, then click once to fire. */
    private Map<String, Object> fire(final ClientGameTestContext context) {
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try {
            for (int tick = 0; tick < 60 && !serverValue(player -> player.isUsingItem() && player.getTicksUsingItem() >= CrossbowItem.getChargeDuration(player.getMainHandItem(), player) + 2); tick++) context.waitTicks(1);
        } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); }
        context.waitTicks(2);
        final Map<String, Object> charge = serverValue(player -> Map.of("charged", CrossbowItem.isCharged(player.getMainHandItem()),
            "charged_projectiles", player.getMainHandItem().getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).itemCopies()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).toList()));
        check(Boolean.TRUE.equals(charge.get("charged")), "Native hold-and-release loads the crossbow; state=" + charge);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        return charge;
    }

    /** Bow refusal control: a full draw with only bolts carried fires nothing and spends nothing. */
    private void drawBow(final ClientGameTestContext context) {
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try { context.waitTicks(25); } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); }
        context.waitTicks(5);
    }

    private void arm(final ClientGameTestContext context, final ItemStack ammo, final int count) { arm(context, Items.CROSSBOW, ammo, count); }
    private void arm(final ClientGameTestContext context, final Item weapon, final ItemStack ammo, final int count) {
        server(player -> {
            // Inventory.clearContent() also strips armor, which silently broke the complete hunter set: keep worn armor.
            final Map<EquipmentSlot, ItemStack> worn = new LinkedHashMap<>();
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) worn.put(slot, player.getItemBySlot(slot).copy());
            player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(weapon)); player.getInventory().setSelectedSlot(0);
            worn.forEach(player::setItemSlot);
            player.setItemSlot(EquipmentSlot.OFFHAND, ammo.copyWithCount(count)); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
        check(serverValue(player -> player.getMainHandItem().is(weapon) && player.getOffhandItem().getCount() == count && !CrossbowItem.isCharged(player.getMainHandItem())),
            "Weapon in the main hand (uncharged) and ammunition in the off hand");
    }

    private float strike(final ClientGameTestContext context, final Mob target, final int cooldownTicks) { return strike(context, target, cooldownTicks, null); }
    private float strike(final ClientGameTestContext context, final Mob target, final int cooldownTicks, final ItemStack weapon) {
        if (weapon != null) supply(context, weapon);
        // Recover the attack cooldown before aiming so the crosshair verified below is the one that clicks.
        context.waitTicks(cooldownTicks);
        check(serverValue(player -> player.getAttackStrengthScale(0) >= .99F), "Attack cooldown has fully recovered before the native strike");
        final float before = serverValue(player -> target.getHealth());
        float after = before;
        final List<Map<String, Object>> attempts = new ArrayList<>();
        for (int attempt = 0; attempt < 3 && after >= before && serverValue(player -> target.isAlive()); attempt++) {
            aimEntity(context, target.getUUID());
            final Map<String, Object> click = new LinkedHashMap<>(pointers.getLast());
            click.put("attempt", attempt);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            for (int tick = 0; tick < 15 && serverValue(player -> target.getHealth()) >= before && serverValue(player -> target.isAlive()); tick++) context.waitTicks(1);
            after = serverValue(player -> target.isAlive() ? target.getHealth() : 0);
            click.put("health_after_click", after); attempts.add(click);
            if (after >= before) context.waitTicks(cooldownTicks);
        }
        pointers.add(Map.of("strike_attempts", List.copyOf(attempts)));
        check(after < before, "Native left-click actually strikes the staged creature; attempts=" + attempts);
        check(serverValue(player -> target.getLastDamageSource() != null && target.getLastDamageSource().getEntity() == player), "Strike is the actor's own attack");
        context.waitTicks(10);
        return before - after;
    }

    private double walk(final ClientGameTestContext context) {
        position(context, START);
        context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(0); }); context.waitTicks(3);
        final Vec3 from = serverValue(ServerPlayer::position);
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { context.waitTicks(20); } finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); }
        context.waitTicks(3);
        final Vec3 to = serverValue(ServerPlayer::position);
        check(Math.abs(to.y - from.y) < .01 && to.z < from.z - .5, "Native walking crosses the level arena floor toward -z; from=" + from + " to=" + to);
        return from.distanceTo(to);
    }

    private static final class ArrowObservation {
        final ServerPlayer player;
        final List<Map<String, Object>> arrows = new ArrayList<>();
        ArrowObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof AbstractArrow arrow && arrow.getOwner() == player) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()); row.put("class", entity.getClass().getSimpleName());
                row.put("pickup", arrow.pickup.name()); row.put("base_damage", field(arrow, "baseDamage"));
                row.put("origin", BuiltInRegistries.ITEM.getKey(arrow.getPickupItemStackOrigin().getItem()).toString());
                row.put("velocity", arrow.getDeltaMovement().toString());
                arrows.add(row);
            }
        }
        Map<String, Object> report() { return Map.of("last_volley", List.copyOf(arrows), "observer", "Passive server entity-load event for arrows owned by the actor; no projectile is created or steered"); }
    }

    private Mob mob(final String id, final Vec3 point, final float health) {
        return serverValue(player -> {
            final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
            check(mob != null, "Fixture entity exists: " + id);
            mob.snapTo(point.x, point.y, point.z); mob.setNoAi(true); mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health); mob.setHealth(health);
            if (mob.getAttribute(Attributes.ARMOR) != null) mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
            check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
        });
    }
    private static ItemStack modItem(final String id) { return new ItemStack(ModItems.ALL.get(id).get()); }
    private void supply(final ClientGameTestContext context, final ItemStack item) {
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, item); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
    }
    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.teleportTo(point.x, point.y, point.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private static void use(final ClientGameTestContext context) { context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3); }
    private static void crouchUse(final ClientGameTestContext context) {
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3);
        try { use(context); } finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(2); }
    }
    private void lookUp(final ClientGameTestContext context) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-70); }); context.waitTicks(2);
        check(context.computeOnClient(client -> !(client.hitResult instanceof EntityHitResult)), "Looking up targets no creature");
    }
    /**
     * Survival entity reach is three blocks. Stand close on the south, west or east side (sleeping creatures lie on a
     * bed whose head block sits north of them and their hitbox is only 0.2 wide), aim at the server bounding-box centre
     * or the client's own box centre, and keep every pointer sample so a miss explains itself.
     */
    private void aimEntity(final ClientGameTestContext context, final UUID id) {
        final List<Map<String, Object>> samples = new ArrayList<>();
        // The client keeps a stale standing hitbox until the sleeping pose and bed position arrive: wait for agreement.
        world.getConnection().waitForClientboundPackets();
        final Vec3 serverCentre = serverValue(player -> player.level().getEntity(id).getBoundingBox().getCenter());
        for (int tick = 0; tick < 40 && !context.computeOnClient(client -> { for (Entity entity : client.level.entitiesForRendering())
            if (entity.getUUID().equals(id)) return entity.getBoundingBox().getCenter().distanceTo(serverCentre) < .15; return false; }); tick++) context.waitTicks(1);
        samples.add(Map.of("server_bbox", serverValue(player -> player.level().getEntity(id).getBoundingBox().toString()),
            "client_bbox", context.computeOnClient(client -> { for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity.getBoundingBox().toString(); return "not rendered"; })));
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
                samples.add(pointer);
                if ("ENTITY".equals(pointer.get("hit_type")) && ((String) pointer.get("hit_entity")).endsWith(id.toString())) { pointer.put("hitbox_sync", samples.getFirst()); pointers.add(pointer); return; }
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
        check(!text.equals(key), "Diagnostic has readable translated text: " + key); return text;
    }
    private static void clearOverlay(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.setOverlayMessage(Component.empty(), false)); }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value); });
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports result: " + expected + "; actual=" + overlay(context));
    }
    private void readBook(final ClientGameTestContext context, final String id, final Map<String, Object> row, final boolean required) throws Exception {
        final var indexed = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst();
        if (indexed.isEmpty()) {
            check(!required, "No indexed guide section for " + id);
            row.put("guide", Map.of("section", id, "note", "No indexed manual section; the handheld stake is only described inside the vampire weaknesses entry")); row.put("guide_status", "NOT_INDEXED");
            return;
        }
        final ManualProfile profile = indexed.orElseThrow();
        supply(context, modItem(profile.id()));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        clientWait(context, client -> client.gui.screen() instanceof ManualScreen, 60, "manual screen opens after native use of " + profile.id());
        selectGuideSection(context, profile, id, row);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Guide section contains actual instructions: " + id);
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection")) && expected == (int) field(client.gui.screen(), "bodyPage")), "Native book buttons visit each guide page");
            screenshot(context, row.get("item") + "-" + id + "-book-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", id, "text", body, "pages_read", pages)); row.put("guide_status", "PASSED");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        clientWait(context, client -> client.gui.screen() == null, 40, "manual closes after Escape");
    }

    /**
     * Crafting and usage entries share one translated title (for example two "Cane Sword" buttons), so the shared
     * selectSection helper can stop on the recipe subchapter. Search natively, open the chapter, then click every
     * same-titled entry in turn until the screen reports the requested section; every attempt is recorded.
     */
    private void selectGuideSection(final ClientGameTestContext context, final ManualProfile profile, final String section, final Map<String, Object> row) {
        final String title = context.computeOnClient(client -> Component.translatable(profile.translatedSectionTitleKey(section)).getString());
        final String chapter = context.computeOnClient(client -> Component.translatable(profile.chapterFor(section).titleKey()).getString());
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex")))
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        ManualClientAcceptance.clickButton(context, chapter);
        final List<String> attempts = new ArrayList<>();
        row.put("guide_selection_attempts", attempts);
        for (int index = 0; index < 8; index++) {
            final int wanted = index;
            final double[] point = context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(button -> button.visible && button.active && button.getMessage().getString().replace("▶ ", "").equals(title))
                .map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0})
                .skip(wanted).findFirst().orElseGet(() -> new double[] {-1, -1}));
            if (point[0] < 0) break;
            ManualClientAcceptance.click(context, point[0], point[1]);
            context.waitTicks(3);
            final String selected = context.computeOnClient(client -> String.valueOf(field(client.gui.screen(), "selectedSection")));
            attempts.add("button " + wanted + " titled '" + title + "' -> " + selected);
            if (section.equals(selected)) return;
        }
        throw new AssertionError("No native '" + title + "' button in chapter '" + chapter + "' opens section " + section + "; attempts=" + attempts
            + "; visible=" + context.computeOnClient(client -> client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(button -> button.visible).map(button -> button.getMessage().getString()).toList()));
    }
    private static void clientWait(final ClientGameTestContext context, final Predicate<Minecraft> predicate, final int ticks, final String message) {
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
        report.put("execution", "Native rendered Fabric client; fresh world per scenario; guide pages read through native clicks; cane toggles, walks, strikes and bow shots are real key/mouse input against staged NoAI creatures with vanilla-arrow, awake, non-vampire and non-stake controls.");
        report.put("scenarios", results); report.put("screenshots", screenshots); report.put("failures", failures);
        final Path temp = evidence.resolve("cane-sword-and-projectiles.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("cane-sword-and-projectiles.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
