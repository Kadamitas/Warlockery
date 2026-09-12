package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.HandOfDeathItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.PatronArmorRules;
import com.kadamitas.warlockery.item.StonebrokerQuiverRules;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class EquipmentAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final AABB AREA = new AABB(-12, 96, -10, 13, 113, 15);
    private static final List<String> EQUIPMENT = List.of(
        "barkbelt", "bitingbelt", "deathscowl", "deathsfeet", "deathsrobe", "delvealloyboots",
        "delvealloychestplate", "delvealloyhelm", "delvealloyleggings", "earmuffs", "emberstep_slippers",
        "forgewardens_girdle", "hedge_crones_hat", "hellhound_head", "iceslippers", "necromancerrobe",
        "seepingshoes", "silverboots", "silverchestplate", "silverhelm", "silverleggings", "stonebrokers_quiver",
        "twisting_band", "vampireboots", "vampirechaincoat", "vampirechaincoat_female", "vampirecoat",
        "vampirecoat_female", "vampirehat", "vampirehelmet", "vampirelegs", "vampirelegs_kilt",
        "werewolf_hunter_boots", "werewolf_hunter_boots_dawn", "werewolf_hunter_boots_silvered",
        "werewolf_hunter_coat", "werewolf_hunter_coat_dawn", "werewolf_hunter_coat_silvered",
        "werewolf_hunter_hat", "werewolf_hunter_hat_dawn", "werewolf_hunter_hat_silvered",
        "werewolf_hunter_leggings", "werewolf_hunter_leggings_dawn", "werewolf_hunter_leggings_silvered",
        "witchhat", "witchrobe");
    private static final Set<String> ORDINARY_ARMOR = Set.of(
        "delvealloyboots", "delvealloychestplate", "delvealloyhelm", "delvealloyleggings",
        "hellhound_head", "vampireboots", "vampirechaincoat", "vampirechaincoat_female", "vampirecoat",
        "vampirecoat_female", "vampirehat", "vampirehelmet", "vampirelegs", "vampirelegs_kilt");
    private static final List<AbilityCase> CASES = List.of(
        new AbilityCase("earmuffs_remedy", List.of("earmuffs"), "Native Blindness splash followed by native equipping removes the real effect"),
        new AbilityCase("seeping_growth", List.of("seepingshoes"), "Native Poison splash is converted into real crop growth"),
        new AbilityCase("bark_fall", List.of("barkbelt"), "Living-ground absorption pays for a real fall onto stone"),
        new AbilityCase("robe_fire", List.of("deathsrobe"), "Real fire hurts an unarmored control and spares the robed player"),
        new AbilityCase("hood_gaze", List.of("deathscowl"), "Native gaze reduces target speed; darkness grants Night Vision"),
        new AbilityCase("band_gaze", List.of("twisting_band"), "Native gaze applies Weakness II and reduces target speed"),
        new AbilityCase("ice_crossing", List.of("iceslippers"), "Native walking freezes water into a usable crossing"),
        new AbilityCase("girdle_punch", List.of("forgewardens_girdle"), "Barehanded native hit deals four damage and lifts its victim"),
        new AbilityCase("quiver_bow", List.of("stonebrokers_quiver"), "Native arrowless bow shot damages and weakens a live target"),
        new AbilityCase("death_disguise", List.of("deathscowl", "deathsrobe", "deathsfeet", "deathshand"),
            "Complete native disguise permits scythe toggle and hunger drain; actual weapon hit separately demonstrates its damage floor"));
    private final Map<String, Map<String, Object>> items = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("equipment-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                check(EQUIPMENT.size() == 46 && EQUIPMENT.stream().distinct().count() == 46, "Expected 46 unique equipment IDs");
                for (String id : java.util.stream.Stream.concat(EQUIPMENT.stream(), java.util.stream.Stream.of("deathshand")).toList()) {
                    check(ModItems.ALL.containsKey(id), "Missing registered item: " + id);
                    final Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", "NOT_RUN");
                    row.put("ability_complete", false);
                    row.put("reason", ORDINARY_ARMOR.contains(id)
                        ? "Ordinary armor defense is outside this special-ability slice; no special trigger is claimed."
                        : "No executed intended-ability scenario is claimed yet.");
                    row.put("scheduled_cases", CASES.stream().filter(c -> c.items().contains(id)).map(AbilityCase::id).toList());
                    row.put("acquisition_status", "NOT_RUN");
                    items.put("warlockery:" + id, row);
                }
                for (AbilityCase test : CASES) {
                    final Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", "NOT_RUN");
                    row.put("items", test.items());
                    row.put("contract", test.contract());
                    cases.put(test.id(), row);
                }
                writeReport();
                final String configured = System.getProperty("warlockery.equipmentCases", "");
                final Set<String> selected = configured.isBlank() ? Set.of() : Arrays.stream(configured.split(","))
                    .map(String::strip).collect(Collectors.toSet());
                check(selected.isEmpty() || cases.keySet().containsAll(selected), "Unknown equipmentCases selection");
                for (AbilityCase test : CASES) {
                    if (!selected.isEmpty() && !selected.contains(test.id())) continue;
                    final Map<String, Object> row = cases.get(test.id());
                    row.put("status", "RUNNING");
                    writeReport();
                    try {
                        stage(context);
                        readGuidance(context, test, row);
                        row.put("staged_prerequisites", "Disposable Survival world, vulnerable player, stone platform, time, health/hunger, inventory supplies and target creatures. Natural health regeneration, random block ticks and natural mob spawning are disabled; fall/fire damage remain enabled. Equipment is put on by native use. Harmful potion preparation is natively thrown. Damage comes only from native attacks, projectiles, falling or fire contact. No tested damage/effect runtime is called directly.");
                        switch (test.id()) {
                            case "earmuffs_remedy" -> earmuffs(context, row);
                            case "seeping_growth" -> seeping(context, row);
                            case "bark_fall" -> bark(context, row);
                            case "robe_fire" -> fireRobe(context, row);
                            case "hood_gaze" -> gaze(context, row, false);
                            case "band_gaze" -> gaze(context, row, true);
                            case "ice_crossing" -> ice(context, row);
                            case "girdle_punch" -> girdle(context, row);
                            case "quiver_bow" -> quiver(context, row);
                            case "death_disguise" -> deathDisguise(context, row);
                            default -> throw new AssertionError(test.id());
                        }
                        row.put("status", "PASSED");
                        for (String id : test.items()) {
                            final var itemRow = items.get("warlockery:" + id);
                            if (!itemRow.get("status").equals("FAILED")) itemRow.put("status", "PARTIAL");
                            itemRow.put("reason", "Only named native ability scenarios are covered; other abilities and acquisition remain.");
                        }
                        screenshot(context, test.id() + "-outcome");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED");
                        row.put("failure", failure.toString());
                        failures.add(test.id() + ": " + failure);
                        for (String id : test.items()) items.get("warlockery:" + id).put("status", "FAILED");
                        try { screenshot(context, test.id() + "-failure"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        release(context);
                        row.put("finished_at", System.currentTimeMillis());
                        writeReport();
                    }
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("WARLOCKERY_EQUIPMENT_ABILITY_SCENARIOS_COMPLETED " + evidence);
        } catch (Throwable failure) {
            try { writeReport(); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Equipment ability evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context) {
        release(context);
        if (context.computeOnClient(client -> client.gui.screen() != null)) context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        server(player -> {
            final var server = player.level().getServer();
            final var rules = player.level().getGameRules();
            rules.set(GameRules.RANDOM_TICK_SPEED, 0, server);
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            rules.set(GameRules.SPAWN_MOBS, false, server);
            rules.set(GameRules.FALL_DAMAGE, true, server);
            rules.set(GameRules.FIRE_DAMAGE, true, server);
            player.level().getEntities((Entity) null, AREA, entity -> !(entity instanceof Player)).forEach(Entity::discard);
            for (int x = -10; x <= 10; x++) for (int z = -8; z <= 12; z++) {
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
            player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "time set 6000");
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(5);
    }

    private void earmuffs(final ClientGameTestContext context, final Map<String, Object> row) {
        splashSelf(context, "brew_blindness", MobEffects.BLINDNESS);
        context.waitTicks(25);
        check(serverValue(player -> player.hasEffect(MobEffects.BLINDNESS)), "Control must remain blinded before equipping");
        row.put("control", "Native splash produced persistent Blindness before equipping");
        equip(context, "earmuffs");
        waitServer(context, player -> !player.hasEffect(MobEffects.BLINDNESS), 35, "Earmuffs must clear the actual blindness");
        row.put("observed", "Blindness removed by normal equipment ticks");
        row.put("remaining", "Nausea removal and acquisition");
    }

    private void seeping(final ClientGameTestContext context, final Map<String, Object> row) {
        final BlockPos crop = new BlockPos(1, 100, -3);
        splashSelf(context, "brew_poison", MobEffects.POISON);
        server(player -> {
            player.level().setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            player.level().setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
        });
        context.waitTicks(25);
        check(serverValue(player -> player.hasEffect(MobEffects.POISON)
            && player.level().getBlockState(crop).getValue(CropBlock.AGE) == 0), "Control must retain poison and young wheat");
        equip(context, "seepingshoes");
        waitServer(context, player -> !player.hasEffect(MobEffects.POISON)
            && player.level().getBlockState(crop).is(Blocks.WHEAT)
            && player.level().getBlockState(crop).getValue(CropBlock.AGE) > 0, 35,
            "Seeping Shoes must turn the actual poison into crop growth");
        row.put("wheat_age_after", serverValue(player -> player.level().getBlockState(crop).getValue(CropBlock.AGE)));
        row.put("poison_source", "Native Brew of Poison splash; stone surroundings leave the wheat as the only growable target");
        row.put("remaining", "Depth Strider movement and acquisition");
    }

    private void bark(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            for (int z = -4; z <= -3; z++) player.level().setBlockAndUpdate(new BlockPos(0, 105, z), Blocks.GRASS_BLOCK.defaultBlockState());
            player.teleportTo(0.5, 106, -3.5);
            player.resetFallDistance();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(4);
        equip(context, "barkbelt");
        waitServer(context, player -> player.getAbsorptionAmount() >= 8.0F, 35, "Living ground must grant eight absorption points");
        final float health = serverValue(LivingEntity::getHealth);
        final float absorption = serverValue(LivingEntity::getAbsorptionAmount);
        look(context, new Vec3(0.5, 106, 4));
        walk(context, 12);
        waitServer(context, player -> player.onGround() && player.getY() < 100.1, 35, "Native movement must cause a real six-block fall");
        final float after = serverValue(LivingEntity::getAbsorptionAmount);
        row.put("health_before", health);
        row.put("health_after", serverValue(LivingEntity::getHealth));
        row.put("absorption_before", absorption);
        row.put("absorption_after_landing", after);
        check(after < absorption && after > 0, "The actual fall must spend absorption before it expires");
        check(Math.abs(serverValue(LivingEntity::getHealth) - health) < 0.01F, "Absorption must pay for the fall without health loss");
        row.put("remaining", "Repeated living-ground recharge and acquisition");
    }

    private void fireRobe(final ClientGameTestContext context, final Map<String, Object> row) {
        fireLane();
        look(context, new Vec3(0.5, 100.5, 8));
        final float initial = serverValue(LivingEntity::getHealth);
        walk(context, 18);
        waitServer(context, player -> player.getHealth() < initial, 70, "Unarmored control must lose health in the fire");
        row.put("unarmored_damage", initial - serverValue(LivingEntity::getHealth));
        stage(context);
        equip(context, "deathsrobe");
        waitServer(context, player -> player.hasEffect(MobEffects.FIRE_RESISTANCE), 35, "Death robe must grant fire resistance");
        fireLane();
        final float protectedHealth = serverValue(LivingEntity::getHealth);
        look(context, new Vec3(0.5, 100.5, 8));
        walk(context, 18);
        waitServer(context, player -> player.level().getBlockState(player.blockPosition()).is(Blocks.FIRE), 30,
            "Protected player must physically enter the same fire lane");
        context.waitTicks(65);
        check(Math.abs(serverValue(LivingEntity::getHealth) - protectedHealth) < 0.01F, "Prolonged real fire contact must not damage the robed player");
        row.put("protected_damage", protectedHealth - serverValue(LivingEntity::getHealth));
        row.put("remaining", "Other disguise behaviors and acquisition");
    }

    private void fireLane() {
        server(player -> {
            for (int z = -1; z <= 3; z++) {
                player.level().setBlockAndUpdate(new BlockPos(0, 99, z), Blocks.NETHERRACK.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(0, 100, z), Blocks.FIRE.defaultBlockState());
            }
        });
        world.getConnection().waitForClientboundPackets();
    }

    private void gaze(final ClientGameTestContext context, final Map<String, Object> row, final boolean band) {
        final UUID target = cow(context, new Vec3(0.5, 100, 2.5), 100);
        final double initialSpeed = serverValue(player -> living(player, target).getAttributeValue(Attributes.MOVEMENT_SPEED));
        look(context, serverValue(player -> living(player, target).getEyePosition()));
        context.waitTicks(25);
        check(serverValue(player -> !living(player, target).hasEffect(MobEffects.SLOWNESS)), "Control gaze must not slow the target");
        if (!band) server(player -> player.level().getServer().getCommands().performPrefixedCommand(
            player.level().getServer().createCommandSourceStack(), "time set 18000"));
        equip(context, band ? "twisting_band" : "deathscowl");
        look(context, serverValue(player -> living(player, target).getEyePosition()));
        waitServer(context, player -> {
            final LivingEntity creature = living(player, target);
            return creature.hasEffect(MobEffects.SLOWNESS)
                && creature.getEffect(MobEffects.SLOWNESS).getAmplifier() == (band ? 0 : 1)
                && creature.getAttributeValue(Attributes.MOVEMENT_SPEED) < initialSpeed
                && (band ? creature.hasEffect(MobEffects.WEAKNESS)
                    && creature.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1 : player.hasEffect(MobEffects.NIGHT_VISION));
        }, 35, "Native gaze must apply advertised effects and reduce effective movement speed");
        row.put("target_uuid", target.toString());
        row.put("speed_before", initialSpeed);
        row.put("speed_after", serverValue(player -> living(player, target).getAttributeValue(Attributes.MOVEMENT_SPEED)));
        row.put("target_fixture", "Live stationary cow with active potion attributes; no target effect was seeded");
        row.put("remaining", band ? "Second-player turn/hunger effect and acquisition" : "Full disguise encounters and acquisition");
    }

    private void ice(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            for (int x = -3; x <= 3; x++) for (int z = 0; z <= 7; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.WATER.defaultBlockState());
            }
        });
        check(serverValue(player -> iceCount(player) == 0), "Pool must begin unfrozen");
        equip(context, "iceslippers");
        look(context, new Vec3(0.5, 100.5, 10));
        walk(context, 32);
        waitServer(context, player -> iceCount(player) > 0 && player.getZ() > 1 && player.getY() >= 99.95
            && player.onGround(), 25, "Native walking must create ice and carry the player across the pool");
        row.put("frosted_ice_blocks", serverValue(this::iceCount));
        row.put("crossing_position", serverValue(Entity::position).toString());
        row.put("remaining", "Natural ice thaw and acquisition");
    }

    private int iceCount(final ServerPlayer player) {
        return (int) BlockPos.betweenClosedStream(new BlockPos(-3, 99, 0), new BlockPos(3, 99, 7))
            .filter(pos -> player.level().getBlockState(pos).is(Blocks.FROSTED_ICE)).count();
    }

    private void girdle(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID control = cow(context, new Vec3(0.5, 100, -0.5), 100);
        final float ordinary = hit(context, control);
        server(player -> player.level().getEntity(control).discard());
        equip(context, "forgewardens_girdle");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Girdle attack must be barehanded");
        final UUID target = cow(context, new Vec3(0.5, 100, -0.5), 100);
        server(player -> ((net.minecraft.world.entity.Mob) living(player, target)).setNoAi(false));
        final float enhanced = hit(context, target);
        double rise = 0;
        for (int tick = 0; tick < 16; tick++) {
            rise = Math.max(rise, serverValue(player -> living(player, target).getY()) - 100.0);
            context.waitTicks(1);
        }
        check(Math.abs(enhanced - PatronArmorRules.GIRDLE_UNARMED_DAMAGE) < 0.01F && enhanced > ordinary,
            "Girdle punch must deal the source-defined four damage, above the control");
        check(rise > 0.25, "Girdle strike must visibly lift its victim");
        row.put("ordinary_punch_damage", ordinary);
        row.put("girdle_punch_damage", enhanced);
        row.put("maximum_target_rise", rise);
        row.put("remaining", "Second-player quiver synergy and acquisition");
    }

    private void quiver(final ClientGameTestContext context, final Map<String, Object> row) {
        hold(context, Items.BOW);
        look(context, new Vec3(0.5, 100.6, 6.5));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(25);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(4);
        check(serverValue(player -> player.level().getEntitiesOfClass(AbstractArrow.class, AREA).isEmpty()),
            "Arrowless unarmored bow must not create a shot");
        equip(context, "stonebrokers_quiver");
        final UUID target = cow(context, new Vec3(0.5, 100, 6.5), 100);
        hold(context, Items.BOW);
        check(serverValue(player -> player.getInventory().getNonEquipmentItems().stream()
            .noneMatch(stack -> stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW))),
            "Quiver fixture must contain no arrow ammunition");
        look(context, new Vec3(0.5, 100.7, 6.5));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(25);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> living(player, target).getHealth() < 100
            && living(player, target).hasEffect(MobEffects.WEAKNESS), 70,
            "Native arrowless quiver shot must strike and weaken the cow");
        check(serverValue(player -> living(player, target).getEffect(MobEffects.WEAKNESS).getAmplifier()
            == StonebrokerQuiverRules.WEAKNESS_AMPLIFIER), "Quiver impact must apply Weakness I");
        row.put("actual_target_damage", 100 - serverValue(player -> living(player, target).getHealth()));
        row.put("bow_durability", serverValue(player -> player.getMainHandItem().getDamageValue()));
        check(serverValue(player -> player.getMainHandItem().getDamageValue() > 0), "Real shot must spend bow durability");
        row.put("remaining", "Projectile speed, airborne damage, partner synergy and acquisition");
    }

    private void deathDisguise(final ClientGameTestContext context, final Map<String, Object> row) {
        hold(context, ModItems.ALL.get("deathshand").get());
        look(context, new Vec3(0.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        check(serverValue(player -> !HandOfDeathItem.isScythe(player.getMainHandItem())), "Incomplete disguise must refuse scythe toggle");
        for (String id : List.of("deathscowl", "deathsrobe", "deathsfeet")) equip(context, id);
        hold(context, ModItems.ALL.get("deathshand").get());
        look(context, new Vec3(0.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> HandOfDeathItem.isScythe(player.getMainHandItem()), 20, "Complete native disguise must allow toggle");
        final int food = serverValue(player -> player.getFoodData().getFoodLevel());
        waitServer(context, player -> player.getFoodData().getFoodLevel() <= food - 2, 50, "Active scythe must drain hunger over normal ticks");
        row.put("hunger_before", food);
        row.put("hunger_after", serverValue(player -> player.getFoodData().getFoodLevel()));
        final UUID target = cow(context, new Vec3(0.5, 100, -0.5), 100);
        final float damage = hit(context, target);
        check(Math.abs(damage - 15.0F) < 0.01F, "Hand strike must deal its 15%-maximum-health floor to the 100-health cow");
        row.put("actual_hand_damage", damage);
        row.put("contract_distinction", "Full armor gates toggle and hunger drain. The weapon's 15% damage floor is separate and does not require the toggle.");
        row.put("remaining", "Death impersonation encounters, deathsfeet enchantments and acquisition");
    }

    private void splashSelf(final ClientGameTestContext context, final String id, final Holder<MobEffect> effect) {
        hold(context, ModItems.ALL.get(id).get());
        look(context, serverValue(Entity::position).add(0, -0.3, 0.15));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.hasEffect(effect) && player.getMainHandItem().isEmpty(), 40,
            "Native " + id + " splash must produce prerequisite effect and consume the brew");
    }

    private void equip(final ClientGameTestContext context, final String id) {
        hold(context, ModItems.ALL.get(id).get());
        final EquipmentSlot slot = serverValue(player -> player.getMainHandItem().get(DataComponents.EQUIPPABLE).slot());
        look(context, serverValue(Entity::position).add(0, 3, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getItemBySlot(slot).is(ModItems.ALL.get(id).get()) && player.getMainHandItem().isEmpty(),
            25, "Native use must equip " + id + " without duplication");
    }

    private void hold(final ClientGameTestContext context, final Item item) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(item));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(3);
    }

    private UUID cow(final ClientGameTestContext context, final Vec3 position, final float health) {
        final UUID target = serverValue(player -> {
            final var creature = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            check(creature != null, "Cow fixture must exist");
            creature.setPos(position.x, position.y, position.z);
            creature.setNoAi(true);
            creature.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            creature.setHealth(health);
            player.level().addFreshEntity(creature);
            return creature.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getEntities((Entity) null, AREA, entity -> entity.getUUID().equals(target)).size() == 1, 30);
        return target;
    }

    private float hit(final ClientGameTestContext context, final UUID target) {
        final float before = serverValue(player -> living(player, target).getHealth());
        context.waitTicks(25);
        look(context, serverValue(player -> living(player, target).position()).add(0, 0.6, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        waitServer(context, player -> living(player, target).getHealth() < before, 30, "Native attack must hit the live target");
        return before - serverValue(player -> living(player, target).getHealth());
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        check(entity instanceof LivingEntity && entity.isAlive(), "Observed target must remain alive");
        return (LivingEntity) entity;
    }

    private void readGuidance(final ClientGameTestContext context, final AbilityCase test, final Map<String, Object> row) throws Exception {
        final List<String> missing = new ArrayList<>();
        final List<Map<String, String>> read = new ArrayList<>();
        for (String id : test.items()) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(id)).findFirst();
            if (found.isEmpty()) {
                missing.add(id + ": no named entry teaching controls, passive triggers, required set pieces, costs and visible result");
                continue;
            }
            final var profile = found.orElseThrow();
            hold(context, ModItems.ALL.get(profile.id()).get());
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, id);
            read.add(Map.of("item", id, "book", profile.id(), "section", id,
                "text", context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString())));
            screenshot(context, test.id() + "-" + id + "-guide");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 30);
        }
        row.put("book_guidance_read", read);
        row.put("missing_named_guidance", missing);
        row.put("book_scope", "Exact reachable item-named entries are checked first; absence does not prove an item is never mentioned elsewhere.");
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
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
        try { context.waitTicks(ticks); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
    }

    private static void release(final ClientGameTestContext context) {
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int key : List.of(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT)) context.getInput().releaseKey(key);
    }

    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String failure) {
        for (int tick = 0; tick < ticks; tick++) {
            if (serverValue(condition::test)) return;
            context.waitTicks(1);
        }
        check(serverValue(condition::test), failure);
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
        report.put("equipment_count", EQUIPMENT.size());
        report.put("additional_weapon", "warlockery:deathshand");
        report.put("planned_cases", CASES.size());
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("pid", ProcessHandle.current().pid());
        report.put("test_class_sha256", classHash(EquipmentAbilitiesClientAcceptance.class));
        report.put("equipment_runtime_sha256", classHash(com.kadamitas.warlockery.item.EquipmentSetEffects.class));
        report.put("items", items);
        report.put("cases", cases);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        report.put("execution", "Rendered client with native input and server observations. PASS covers only the named scenario, never all equipment.");
        Files.writeString(evidence.resolve("equipment-abilities.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private record AbilityCase(String id, List<String> items, String contract) { }

    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) return "UNAVAILABLE";
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
