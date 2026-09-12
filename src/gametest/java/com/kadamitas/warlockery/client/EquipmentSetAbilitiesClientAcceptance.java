package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.BitingBeltState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class EquipmentSetAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final AABB AREA = new AABB(-20, 96, -20, 21, 114, 21);
    private static final List<String> ORDINARY = List.of(
        "delvealloyboots", "delvealloychestplate", "delvealloyhelm", "delvealloyleggings", "hellhound_head",
        "vampireboots", "vampirechaincoat", "vampirechaincoat_female", "vampirecoat", "vampirecoat_female",
        "vampirehat", "vampirehelmet", "vampirelegs", "vampirelegs_kilt");
    private static final List<String> SILVER = List.of("silverhelm", "silverchestplate", "silverleggings", "silverboots");
    private static final List<AbilityCase> CASES = cases();
    private final Map<String, Map<String, Object>> itemRows = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> caseRows = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile DamageWatch damageWatch;

    private static List<AbilityCase> cases() {
        final List<AbilityCase> cases = new ArrayList<>();
        for (String id : List.of("witchhat", "witchrobe", "hedge_crones_hat"))
            cases.add(new AbilityCase("creeper_" + id, List.of(id), "Live creeper loses its target after native equipping",
                "Random extra brew yields; the hedge hat's infused reserve-cost evasion; acquisition. Target clearing is not permanent creeper immunity."));
        cases.add(new AbilityCase("undead_necromancerrobe", List.of("necromancerrobe"),
            "Live undead loses its target after native equipping",
            "Random extra brazier yields and acquisition. Undead can acquire a target again."));
        for (String variant : List.of("", "_silvered", "_dawn"))
            cases.add(new AbilityCase("hunter_magic" + variant, hunterSet(variant),
                "Native Harming potion deals half damage only after all four matching pieces are equipped",
                "Native hex rejection and armor cost; protection-doll suppression; single-piece lycanthropy prevention; werewolf/vampire damage bonuses and retaliation; acquisition."));
        cases.add(new AbilityCase("bitingbelt_store_and_retaliate", List.of("bitingbelt"),
            "Native potion storage renews movement speed; a real attacker then suffers stored Weakness and belt retaliation",
            "Other potion effects, potion replacement variants and acquisition."));
        cases.add(new AbilityCase("emberstep_fire", List.of("emberstep_slippers"),
            "Native walking into fire causes less real damage than equally protective plain leather boots",
            "Other fire sources and acquisition."));
        for (String id : SILVER)
            cases.add(new AbilityCase("silver_retaliation_" + id, List.of(id),
                "A real werewolf hit triggers one-piece retaliation and armor durability loss",
                "Multi-piece scaling, ordinary physical protection and acquisition. A werewolf that avoids attacking leaves this scenario NOT_RUN."));
        for (String id : ORDINARY)
            cases.add(new AbilityCase("armor_defense_" + id, List.of(id),
                "Native equipped armor reduces an actual zombie hit and loses durability",
                "Other damage classes, repair and acquisition; no special passive ability is asserted."));
        return List.copyOf(cases);
    }

    private static List<String> hunterSet(final String variant) {
        return List.of("werewolf_hunter_hat" + variant, "werewolf_hunter_coat" + variant,
            "werewolf_hunter_leggings" + variant, "werewolf_hunter_boots" + variant);
    }

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("equipment-set-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final DamageWatch watch = damageWatch;
                if (watch != null && watch.player.level().getServer() == server) watch.tick();
            });
            context.getInput().resizeWindow(1280, 800);
            for (AbilityCase test : CASES) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", "NOT_RUN");
                row.put("items", test.items());
                row.put("contract", test.contract());
                row.put("remaining", test.remaining());
                caseRows.put(test.id(), row);
                for (String id : test.items()) itemRows.computeIfAbsent("warlockery:" + id, ignored -> {
                    final Map<String, Object> item = new LinkedHashMap<>();
                    item.put("status", "NOT_RUN");
                    item.put("ability_complete", false);
                    item.put("acquisition_status", "NOT_RUN");
                    item.put("scheduled_cases", CASES.stream().filter(candidate -> candidate.items().contains(id))
                        .map(AbilityCase::id).toList());
                    item.put("ordinary_armor", ORDINARY.contains(id));
                    return item;
                });
            }
            check(itemRows.size() == 36, "This supplement must declare exactly the remaining 36 equipment IDs");
            final String request = System.getProperty("warlockery.equipmentSetCases", "");
            final Set<String> selected = request.isBlank() ? Set.of() : Arrays.stream(request.split(","))
                .map(String::strip).collect(Collectors.toSet());
            check(selected.isEmpty() || caseRows.keySet().containsAll(selected), "Unknown equipmentSetCases selection");
            writeReport();
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                for (AbilityCase test : CASES) {
                    if (!selected.isEmpty() && !selected.contains(test.id())) continue;
                    final Map<String, Object> row = caseRows.get(test.id());
                    row.put("status", "RUNNING");
                    writeReport();
                    try {
                        stage(context);
                        readGuidance(context, test, row);
                        row.put("staged_prerequisites", "Survival player, flat stone arena, night, normal difficulty, health/hunger, inventory supplies, adult unarmed enemies with fixed health and attack strength. Zombie armor and toughness are zeroed so their innate armor cannot obscure the belt's two-point retaliation. Natural regeneration and natural spawns disabled. Mobs retain normal AI. Creature target and movement attributes may be staged for a bounded fixture. Native input equips armor, stores or drinks potions, moves into danger. Server reads observe outcomes; no tested hurt/effect/retaliation/hex or equipment runtime method is invoked.");
                        if (test.id().startsWith("creeper_")) clearAggro(context, row, test.items().getFirst(), false);
                        else if (test.id().equals("undead_necromancerrobe")) clearAggro(context, row, "necromancerrobe", true);
                        else if (test.id().startsWith("hunter_magic")) hunterMagic(context, row, test.items());
                        else if (test.id().equals("bitingbelt_store_and_retaliate")) bitingBelt(context, row);
                        else if (test.id().equals("emberstep_fire")) emberstep(context, row);
                        else if (test.id().startsWith("silver_retaliation_")) silverRetaliation(context, row, test.items().getFirst());
                        else if (test.id().startsWith("armor_defense_")) ordinaryDefense(context, row, test.items().getFirst());
                        else throw new AssertionError(test.id());
                        row.put("status", "PASSED");
                        for (String id : test.items()) {
                            final var item = itemRows.get("warlockery:" + id);
                            if (!"FAILED".equals(item.get("status"))) item.put("status", "PARTIAL");
                            item.put("remaining", test.remaining());
                        }
                        screenshot(context, test.id() + "-outcome");
                    } catch (UnsupportedOperationException fixture) {
                        row.put("status", "NOT_RUN");
                        row.put("reason", fixture.toString());
                        row.put("execution_incomplete", true);
                        screenshot(context, test.id() + "-fixture-blocked");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED");
                        row.put("failure", failure.toString());
                        failures.add(test.id() + ": " + failure);
                        for (String id : test.items()) itemRows.get("warlockery:" + id).put("status", "FAILED");
                        try { screenshot(context, test.id() + "-failure"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        damageWatch = null;
                        release(context);
                        row.put("finished_at", System.currentTimeMillis());
                        writeReport();
                    }
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("WARLOCKERY_EQUIPMENT_SET_SCENARIOS_COMPLETED " + evidence);
        } catch (Throwable failure) {
            try { writeReport(); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Equipment set evidence: " + evidence, failure);
        } finally {
            damageWatch = null;
        }
    }

    private void stage(final ClientGameTestContext context) {
        damageWatch = null;
        release(context);
        if (context.computeOnClient(client -> client.gui.screen() != null)) context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        server(player -> {
            final var server = player.level().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
            player.level().getGameRules().set(GameRules.FIRE_DAMAGE, true, server);
            player.level().getEntities((Entity) null, AREA, entity -> !(entity instanceof Player)).forEach(Entity::discard);
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                for (int y = 100; y <= 110; y++) player.level().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = false;
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
            player.stopUsingItem();
            player.removeAllEffects();
            player.clearFire();
            player.getInventory().clearContent();
            for (EquipmentSlot slot : EquipmentSlot.VALUES) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5);
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(0.5, 100, -3.5);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(5);
    }

    private void clearAggro(final ClientGameTestContext context, final Map<String, Object> row,
        final String garment, final boolean undead) {
        final UUID id = enemy(context, undead ? "zombie" : "creeper", new Vec3(0.5, 100, 4.5), true);
        context.waitTicks(25);
        check(serverValue(player -> ((Mob) living(player, id)).getTarget() == player),
            "Unclothed control must retain the live enemy's target for 25 ticks");
        row.put("control", "Live AI, stationary movement fixture at eight blocks, target retained for 25 ticks");
        equip(context, garment);
        waitServer(context, player -> ((Mob) living(player, id)).getTarget() == null, 35,
            "Normal equipped ticks must clear the live target");
        row.put("observed", "Target cleared after equipping; creature remains alive with normal AI");
        row.put("target_alive_with_ai", serverValue(player -> !((Mob) living(player, id)).isNoAi()));
        row.put("limitation", "This verifies periodic target clearing, not permanent immunity or prevention of target reacquisition.");
    }

    private void hunterMagic(final ClientGameTestContext context, final Map<String, Object> row, final List<String> armor) {
        final float naked = harmPotion(context);
        restoreHealth(context);
        for (String id : armor.subList(0, 3)) equip(context, id);
        final float incomplete = harmPotion(context);
        restoreHealth(context);
        equip(context, armor.getLast());
        final float complete = harmPotion(context);
        check(Math.abs(naked - 6.0F) < 0.01F, "Native standard Harming potion must deal six health points to control");
        check(Math.abs(incomplete - naked) < 0.01F, "Three matching armor pieces must not grant the full-set magic reduction");
        check(Math.abs(complete - naked * 0.5F) < 0.01F, "Four matching pieces must halve actual potion damage");
        row.put("health_loss", Map.of("unarmored", naked, "three_pieces", incomplete, "complete_set", complete));
        row.put("trigger", "Each potion is natively drunk and returns a glass bottle; magic bypasses ordinary armor points.");
    }

    private float harmPotion(final ClientGameTestContext context) {
        hold(context, PotionContents.createItemStack(Items.POTION, Potions.HARMING));
        final float before = serverValue(LivingEntity::getHealth);
        look(context, new Vec3(0.5, 105, 6));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            waitServer(context, player -> player.getMainHandItem().is(Items.GLASS_BOTTLE), 60,
                "Native drinking must consume the Harming potion and return its bottle");
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        final float lost = before - serverValue(LivingEntity::getHealth);
        check(lost > 0, "Harming potion must cause real damage");
        return lost;
    }

    private void bitingBelt(final ClientGameTestContext context, final Map<String, Object> row) {
        final double baseSpeed = serverValue(player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        bindBelt(context, Potions.SWIFTNESS);
        equipHeld(context);
        waitServer(context, player -> player.hasEffect(MobEffects.SPEED)
            && player.getAttributeValue(Attributes.MOVEMENT_SPEED) > baseSpeed, 35,
            "Normally equipped belt must apply the stored beneficial effect");
        final Vec3 start = serverValue(Entity::position);
        look(context, new Vec3(0.5, 101.6, 8));
        walk(context, 12);
        final double moved = serverValue(player -> player.position().distanceTo(start));
        check(moved > 1.0, "Player must actually move while the stored speed is active");
        row.put("helpful_storage", Map.of("speed_attribute_before", baseSpeed,
            "speed_attribute_after", serverValue(player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED)), "walk_distance", moved));
        server(player -> {
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.removeAllEffects();
            player.teleportTo(0.5, 100, -3.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        bindBelt(context, Potions.WEAKNESS);
        equipHeld(context);
        final UUID attacker = enemy(context, "zombie", new Vec3(0.5, 100, -1.3), false);
        final Hit result = receiveHit(context, attacker, false);
        check(result.attackerHealth() <= 98.01F, "Biting Belt must retaliate against the real attacker for two health points");
        check(serverValue(player -> living(player, attacker).hasEffect(MobEffects.WEAKNESS)),
            "The actual attacker must receive the stored harmful effect");
        check(serverValue(player -> living(player, attacker).getEffect(MobEffects.SLOWNESS) != null
            && living(player, attacker).getEffect(MobEffects.SLOWNESS).getAmplifier() == 1),
            "The real attacker must also receive belt Slowness II");
        row.put("retaliation", result);
    }

    private void bindBelt(final ClientGameTestContext context, final net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        hold(context, new ItemStack(ModItems.ALL.get("bitingbelt").get()));
        server(player -> {
            player.setItemSlot(EquipmentSlot.OFFHAND, PotionContents.createItemStack(Items.POTION, potion));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 105, 6));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getOffhandItem().isEmpty()
            && !BitingBeltState.read(player.getMainHandItem()).equals(BitingBeltState.EMPTY), 30,
            "Native belt use must store the other hand's potion and consume it");
    }

    private void emberstep(final ClientGameTestContext context, final Map<String, Object> row) {
        hold(context, new ItemStack(Items.LEATHER_BOOTS));
        equipHeld(context);
        final float ordinary = fireContact(context);
        stage(context);
        equip(context, "emberstep_slippers");
        final float enchanted = fireContact(context);
        check(enchanted > 0.0F && enchanted < ordinary * 0.8F,
            "Fire Protection IV must reduce an actual fire hit compared with plain boots, without claiming immunity");
        row.put("first_fire_damage", Map.of("plain_leather_boots", ordinary, "emberstep_slippers", enchanted));
    }

    private float fireContact(final ClientGameTestContext context) {
        server(player -> {
            for (int z = -1; z <= 3; z++) {
                player.level().setBlockAndUpdate(new BlockPos(0, 99, z), Blocks.NETHERRACK.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(0, 100, z), Blocks.FIRE.defaultBlockState());
            }
        });
        final DamageWatch watch = serverValue(player -> new DamageWatch(player, null));
        damageWatch = watch;
        look(context, new Vec3(0.5, 101.6, 8));
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { waitServer(context, player -> watch.hit != null, 65, "Native walking must enter fire and cause actual damage"); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); damageWatch = null; }
        return watch.hit.playerDamage();
    }

    private void silverRetaliation(final ClientGameTestContext context, final Map<String, Object> row, final String armor) {
        equip(context, armor);
        final UUID attacker = enemy(context, "werewolf", new Vec3(0.5, 100, -1.3), false);
        look(context, serverValue(player -> living(player, attacker).getEyePosition()));
        context.waitTicks(22);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        waitServer(context, player -> living(player, attacker).getHealth() < 100.0F, 20,
            "Native punch must provoke the actual werewolf before its response is tested");
        final float afterProvocation = serverValue(player -> living(player, attacker).getHealth());
        server(player -> ((Mob) living(player, attacker)).setNoAi(false));
        final Hit hit = receiveHit(context, attacker, true);
        check(hit.attackerHealth() < afterProvocation, "Silver must additionally injure the werewolf that actually struck the player");
        check(hit.armorDamage().getOrDefault(armor, 0) >= 1, "The worn silver piece must pay durability for retaliation");
        row.put("first_native_werewolf_hit", hit);
        row.put("health_after_native_provocation", afterProvocation);
        row.put("observed_retaliation_health", afterProvocation - hit.attackerHealth());
        row.put("damage_note", "The one-point retaliation still passes through the werewolf's own damage rules.");
    }

    private void ordinaryDefense(final ClientGameTestContext context, final Map<String, Object> row, final String armor) {
        final UUID control = enemy(context, "zombie", new Vec3(0.5, 100, -1.3), false);
        final Hit naked = receiveHit(context, control, false);
        stage(context);
        equip(context, armor);
        final UUID attacker = enemy(context, "zombie", new Vec3(0.5, 100, -1.3), false);
        final Hit protectedHit = receiveHit(context, attacker, false);
        check(protectedHit.playerDamage() > 0 && protectedHit.playerDamage() < naked.playerDamage(),
            "This exact armor piece must reduce a real native zombie strike");
        check(protectedHit.armorDamage().getOrDefault(armor, 0) > 0,
            "Ordinary defense must consume real armor durability");
        row.put("unarmored_hit", naked);
        row.put("equipped_hit", protectedHit);
        row.put("special_ability_exemption", "Source assigns ordinary armor attributes; no extra activation or set passive is claimed for this item.");
    }

    private UUID enemy(final ClientGameTestContext context, final String kind, final Vec3 position, final boolean stationary) {
        final UUID id = serverValue(player -> {
            final Mob mob = kind.equals("werewolf")
                ? (Mob) ModEntities.ALL.get("werewolf").get().create(player.level(), EntitySpawnReason.COMMAND)
                : kind.equals("creeper") ? EntityTypes.CREEPER.create(player.level(), EntitySpawnReason.COMMAND)
                : EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
            check(mob != null, "Enemy fixture must exist");
            mob.setPos(position.x, position.y, position.z);
            mob.setNoAi(kind.equals("werewolf"));
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
            mob.setHealth(100.0F);
            if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);
            if (stationary) mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
            if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                zombie.setBaby(false);
                if (!kind.equals("werewolf")) {
                    zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
                    zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
                }
            }
            for (EquipmentSlot slot : EquipmentSlot.VALUES) mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.setPersistenceRequired();
            mob.setTarget(player);
            if (!stationary) damageWatch = new DamageWatch(player, mob.getUUID());
            player.level().addFreshEntity(mob);
            return mob.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
        return id;
    }

    private Hit receiveHit(final ClientGameTestContext context, final UUID attacker, final boolean mayAvoid) {
        final DamageWatch watch = damageWatch;
        check(watch != null && attacker.equals(watch.attacker), "Passive damage observation must start before the enemy can attack");
        look(context, serverValue(player -> living(player, attacker).getEyePosition()));
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try {
            for (int tick = 0; tick < 160 && watch.hit == null; tick++) context.waitTicks(1);
        } finally {
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            damageWatch = null;
        }
        if (watch.hit == null && mayAvoid)
            throw new UnsupportedOperationException("Live werewolf did not strike within 160 ticks; silver avoidance or AI fixture prevented triggering retaliation. No ability pass is claimed.");
        check(watch.hit != null, "The normal AI enemy must deliver a real melee hit: "
            + serverValue(player -> "player=" + player.position() + ", target=" + living(player, attacker).position()
                + ", target_health=" + living(player, attacker).getHealth()
                + ", target_ai=" + !((Mob) living(player, attacker)).isNoAi()
                + ", targeting=" + (((Mob) living(player, attacker)).getTarget() == player)));
        check(attacker.toString().equals(watch.hit.sourceOwner()), "Observed damage must come from the staged enemy, not a fall or another hazard");
        server(player -> ((Mob) living(player, attacker)).setNoAi(true));
        return watch.hit;
    }

    private static final class DamageWatch {
        private final ServerPlayer player;
        private final UUID attacker;
        private final float before;
        private volatile Hit hit;
        private DamageWatch(final ServerPlayer player, final UUID attacker) {
            this.player = player;
            this.attacker = attacker;
            this.before = player.getHealth();
        }
        private void tick() {
            if (hit != null || player.getHealth() >= before) return;
            final Map<String, Integer> armorDamage = new LinkedHashMap<>();
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                final ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) armorDamage.put(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getDamageValue());
            }
            final Entity entity = attacker == null ? null : player.level().getEntity(attacker);
            final var source = player.getLastDamageSource();
            hit = new Hit(before - player.getHealth(), entity instanceof LivingEntity living ? living.getHealth() : -1.0F, armorDamage,
                source == null || source.getEntity() == null ? "" : source.getEntity().getUUID().toString());
        }
    }

    private void restoreHealth(final ClientGameTestContext context) {
        server(player -> { player.setHealth(player.getMaxHealth()); player.clearFire(); });
        context.waitTicks(25);
    }

    private void equip(final ClientGameTestContext context, final String id) {
        hold(context, new ItemStack(ModItems.ALL.get(id).get()));
        equipHeld(context);
    }

    private void equipHeld(final ClientGameTestContext context) {
        final ItemStack held = serverValue(player -> player.getMainHandItem().copy());
        check(held.has(DataComponents.EQUIPPABLE), "Held item must declare an equipment slot");
        final EquipmentSlot slot = held.get(DataComponents.EQUIPPABLE).slot();
        check(serverValue(player -> player.getItemBySlot(slot).isEmpty()), "Equipment slot must start empty");
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(InventoryScreen.class);
        final int[] point = context.computeOnClient(client -> {
            final var screen = client.gui.screen();
            final var menuSlot = client.player.containerMenu.getSlot(36);
            return new int[] {(int) field(screen, "leftPos") + menuSlot.x + 8,
                (int) field(screen, "topPos") + menuSlot.y + 8};
        });
        ManualClientAcceptance.click(context, point[0], point[1]);
        final int[] destination = context.computeOnClient(client -> {
            final int index = switch (slot) {
                case HEAD -> 5;
                case CHEST -> 6;
                case LEGS -> 7;
                case FEET -> 8;
                default -> throw new AssertionError("Unexpected armor slot " + slot);
            };
            final var armorSlot = client.player.containerMenu.getSlot(index);
            return new int[] {(int) field(client.gui.screen(), "leftPos") + armorSlot.x + 8,
                (int) field(client.gui.screen(), "topPos") + armorSlot.y + 8};
        });
        ManualClientAcceptance.click(context, destination[0], destination[1]);
        waitServer(context, player -> ItemStack.isSameItemSameComponents(player.getItemBySlot(slot), held)
            && player.getMainHandItem().isEmpty(), 30, "Native inventory pickup and armor-slot placement must equip the exact held item");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 30);
    }

    private void hold(final ClientGameTestContext context, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(0, stack.copy()); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(3);
    }

    private void readGuidance(final ClientGameTestContext context, final AbilityCase test, final Map<String, Object> row) throws Exception {
        final List<Map<String, String>> read = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String id : test.items()) {
            final var match = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(id)).findFirst();
            if (match.isEmpty()) {
                if (!ORDINARY.contains(id)) missing.add(id + ": needs reachable named usage, conditions, set pieces, costs and result; recipes alone do not teach the ability.");
                continue;
            }
            final var profile = match.orElseThrow();
            hold(context, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, id);
            read.add(Map.of("item", id, "book", profile.id(), "section", id,
                "text", context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString())));
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                    method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(),
                        ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context,
                    net.minecraft.network.chat.Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection"))
                    && (int) field(client.gui.screen(), "bodyPage") == expected), "Native paging visits every equipment instruction page");
                screenshot(context, test.id() + "-" + id + "-guide-" + page);
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 30);
        }
        row.put("book_guidance_read", read);
        row.put("missing_named_guidance", missing);
        row.put("guidance_scope", "Exact reachable item-named entries. Missing exact entries do not prove there are no incidental mentions.");
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
    }

    private static Object field(final Object target, final String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
        }
        throw new AssertionError("Missing observation field " + name);
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        check(entity instanceof LivingEntity && entity.isAlive(), "Observed target must remain alive");
        return (LivingEntity) entity;
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static void walk(final ClientGameTestContext context, final int ticks) {
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { context.waitTicks(ticks); } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
    }

    private static void release(final ClientGameTestContext context) {
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int key : List.of(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT)) context.getInput().releaseKey(key);
    }

    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String failure) {
        for (int i = 0; i < ticks; i++) {
            if (serverValue(predicate::test)) return;
            context.waitTicks(1);
        }
        check(serverValue(predicate::test), failure);
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport() throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("all_equipment_abilities_complete", false);
        report.put("manifest_total", 46);
        report.put("previous_primary_slice_equipment", 10);
        report.put("supplement_equipment", itemRows.size());
        report.put("ordinary_defense_items", ORDINARY.size());
        report.put("special_equipment_items", itemRows.size() - ORDINARY.size());
        report.put("planned_cases", CASES.size());
        report.put("updated_at", System.currentTimeMillis());
        report.put("pid", ProcessHandle.current().pid());
        report.put("class_sha256", classHash(EquipmentSetAbilitiesClientAcceptance.class));
        report.put("equipment_runtime_sha256", classHash(com.kadamitas.warlockery.item.EquipmentSetEffects.class));
        report.put("items", itemRows);
        report.put("cases", caseRows);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        report.put("execution", "Rendered native interaction and normal server ticking. Cases claim only the observed behavior; secondary abilities and acquisition stay explicit.");
        Files.writeString(evidence.resolve("equipment-set-abilities.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) return "UNAVAILABLE";
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private record AbilityCase(String id, List<String> items, String contract, String remaining) { }
    private record Hit(float playerDamage, float attackerHealth, Map<String, Integer> armorDamage, String sourceOwner) { }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
