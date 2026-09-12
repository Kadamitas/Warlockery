package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;

import com.kadamitas.warlockery.item.AbyssalBanishment;
import com.kadamitas.warlockery.item.BiomeNoteState;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.item.ReplicationSelection;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
public final class RemainingToolsAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DEVICES = List.of("biomenote", "replication_staff", "replication_charge",
        "universal_antidote", "ingredient_purified_milk", "ingredient_warm_blood", "ingredient_infernal_animus",
        "sungrenade", "ingredient_soul_of_torment");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile ToolObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("remaining-tools-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final var current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final String configured = System.getProperty("warlockery.remainingToolIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(DEVICES) : Arrays.stream(configured.split(","))
                .map(String::strip).map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && DEVICES.containsAll(requested), "Requested resource IDs must have an implemented native scenario");
            for (String id : DEVICES) check(ModItems.ALL.containsKey(id), "Registered remaining tool ID exists: " + id);
            for (String id : DEVICES) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : DEVICES) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readGuides(context, id, row);
                        switch (id) {
                            case "biomenote" -> biome(context, row);
                            case "replication_staff" -> staff(context, row);
                            case "replication_charge" -> charge(context, row);
                            case "universal_antidote", "ingredient_purified_milk" -> cure(context, id, row);
                            case "ingredient_warm_blood" -> blood(context, row);
                            case "ingredient_infernal_animus" -> animus(context, row);
                            case "sungrenade" -> sunlight(context, row);
                            case "ingredient_soul_of_torment" -> torment(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        if (observation != null) row.put("last_runtime_observation", serverValue(player -> observation.report()));
                        observation = null;
                        write(false);
                    }
                } catch (Throwable failure) {
                    fail(id, row, failure);
                } finally { observation = null; world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_REMAINING_TOOLS_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native device evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.HARD, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(12, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(0.5, 100, -2.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world, stone platform and supplies staged. Native item actions invoke resource behavior. Specific scenario prerequisites are disclosed separately; no resource outcome or item binding is injected.");
    }



    private void biome(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("biomenote"));
        useAir(context);
        check(serverValue(player -> BiomeNoteState.read(player.getMainHandItem()).isEmpty()), "Air use does not invent a biome record");
        final var expected = serverValue(player -> player.level().getBiome(DEVICE.below()).unwrapKey().orElseThrow().identifier());
        use(context, new Vec3(.5, 99.999, .5), DEVICE.below());
        await(context, player -> BiomeNoteState.read(player.getMainHandItem()).filter(expected::equals).isPresent(), 25,
            "Native block use records exact loaded biome ID at clicked block");
        check(serverValue(player -> count(player, item("biomenote")) == 1 && player.getMainHandItem().has(DataComponents.CUSTOM_NAME)
            && player.getMainHandItem().has(DataComponents.LORE)), "Biome note remains usable and receives recorded name/lore");
        context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
        final int[] point = context.computeOnClient(client -> {
            final var slot = client.player.containerMenu.slots.stream().filter(value -> value.container == client.player.getInventory()
                && value.getContainerSlot() == 0).findFirst().orElseThrow();
            return new int[] {(int) field(client.gui.screen(), "leftPos") + slot.x + 8, (int) field(client.gui.screen(), "topPos") + slot.y + 8};
        });
        ManualClientAcceptance.cursor(context, point[0], point[1]); context.waitTicks(3);
        screenshot(context, "biome-note-recorded-tooltip"); close(context);
        row.put("actual_biome", expected.toString());
        row.put("checked", List.of("air-use negative control", "native clicked biome ID", "recorded name/lore", "item retained", "native inventory tooltip"));
        row.put("not_run", List.of("rewriting at different biome", "persistence"));
    }
    private void staff(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final BlockPos one = new BlockPos(-1, 100, 0), two = new BlockPos(0, 100, 0), destination = new BlockPos(-1, 100, -1);
        server(player -> {
            player.level().setBlockAndUpdate(one, Blocks.GOLD_BLOCK.defaultBlockState());
            player.level().setBlockAndUpdate(two, Blocks.DIAMOND_BLOCK.defaultBlockState());
        });
        supply(context, 0, item("replication_staff"));
        use(context, Vec3.atCenterOf(one), one);
        await(context, player -> ReplicationSelection.read(player.getMainHandItem()).filter(value -> value.first().equals(one)
            && value.second().isEmpty()).isPresent(), 20, "First native click records exact first corner only");
        check(serverValue(player -> player.level().getBlockState(destination).isAir() && player.getMainHandItem().getDamageValue() == 0),
            "Incomplete selection produces nothing and uses no durability");
        use(context, Vec3.atCenterOf(two), two);
        await(context, player -> ReplicationSelection.read(player.getMainHandItem()).filter(value -> value.second().filter(two::equals).isPresent()
            && value.volume() == 2).isPresent(), 20, "Second native click completes two-block selection");
        use(context, new Vec3(destination.getX() + .5, 99.999, destination.getZ() + .5), destination.below());
        await(context, player -> player.level().getBlockState(destination).is(Blocks.GOLD_BLOCK)
            && player.level().getBlockState(destination.east()).is(Blocks.DIAMOND_BLOCK), 30, "Native third click copies both exact block states at clicked-face destination");
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 1 && player.level().getBlockState(one).is(Blocks.GOLD_BLOCK)
            && player.level().getBlockState(two).is(Blocks.DIAMOND_BLOCK)), "Copy preserves originals and costs exactly one durability");
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        try { use(context, Vec3.atCenterOf(destination), destination); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        await(context, player -> ReplicationSelection.read(player.getMainHandItem()).isEmpty(), 20, "Native crouch click clears selection");
        row.put("checked", List.of("first/second native corner selection", "exact two-block copy", "one durability", "originals preserved", "native crouch clear"));
        row.put("not_run", List.of("block-entity copy exclusion (native inventory block interaction precedence)", "512-volume limit", "cross-dimension selection rejection"));
        screenshot(context, "replication-staff-native-copy");
    }
    private void charge(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> observation = new ToolObservation(player));
        throwItem(context, "replication_charge");
        await(context, player -> observation.projectilesFinished(), 70, "Empty-area charge actually collides");
        check(serverValue(player -> observation.duplicates.isEmpty()), "Charge without nearby target creates no duplicate");
        final UUID cow = spawn("minecraft:cow", 2.5, 100, .5);
        server(player -> observation = new ToolObservation(player));
        throwItem(context, "replication_charge");
        await(context, player -> observation.projectilesFinished() && observation.duplicates.size() == 1, 80,
            "Native charge impact creates exactly one glass doppelganger beside the intended target");
        final Map<String, Object> duplicate = serverValue(player -> observation.duplicates.values().iterator().next());
        check(cow.toString().equals(duplicate.get("reflected_target")) && cow.toString().equals(duplicate.get("combat_target")),
            "Actual duplicate remembers and targets the nearby cow, not just any spawned mob");
        check(serverValue(player -> count(player, item("replication_charge")) == 0), "Native charge consumes exactly one staged item");
        row.put("actual_duplicate", duplicate);
        row.put("checked", List.of("no-target gate", "actual owned projectile and natural impact", "exact glass doppelganger", "correct reflected UUID/combat target", "one charge consumed"));
        row.put("not_run", List.of("obstructed spawn rejection", "nearest of several targets", "combat result"));
        screenshot(context, "replication-charge-native-reflection");
    }
    private void cure(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        server(player -> {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 1200, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 1200, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 1200, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 1200, 0));
        });
        supply(context, 0, item(id));
        eat(context, item(id));
        if (id.equals("universal_antidote")) {
            check(serverValue(player -> !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER)
                && player.hasEffect(MobEffects.SLOWNESS) && player.hasEffect(MobEffects.SPEED)),
                "Antidote removes Poison/Wither only; harmful Slowness and beneficial Speed are controls");
        } else {
            check(serverValue(player -> player.getActiveEffects().size() == 3 && player.hasEffect(MobEffects.SPEED)),
                "Purified Milk removes exactly one harmful effect and preserves beneficial Speed");
        }
        check(serverValue(player -> count(player, Items.GLASS_BOTTLE) == 1), "Actual drinking returns exactly one glass bottle");
        row.put("remaining_effects", serverValue(player -> player.getActiveEffects().stream().map(effect -> effect.getEffect().unwrapKey().orElseThrow().identifier().toString()).toList()));
        row.put("checked", List.of("native held drink", "selective effect removal with negative controls", "one item consumed", "one empty bottle"));
        row.put("fixture", "Four known effects staged; source removal functions never called by harness.");
        screenshot(context, id + "-selective-cure");
    }
    private void blood(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("ingredient_warm_blood"));
        eat(context, item("ingredient_warm_blood"));
        check(serverValue(player -> com.kadamitas.warlockery.transformation.SupernaturalState.getReserve(player) == 0
            && player.hasEffect(MobEffects.HUNGER) && player.getEffect(MobEffects.HUNGER).getAmplifier() == 1
            && player.getEffect(MobEffects.HUNGER).getDuration() >= 580), "Human receives Hunger II for 600 ticks and no vampire reserve");
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
        clickPlayerSlot(context, 0); clickPlayerSlot(context, 9); close(context);
        check(serverValue(player -> count(player, Items.GLASS_BOTTLE) == 1), "Native inventory click preserves first drink's empty bottle");
        server(player -> {
            player.removeAllEffects();
            player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), "time set 18000");
            com.kadamitas.warlockery.transformation.SupernaturalState.setForm(player, com.kadamitas.warlockery.transformation.SupernaturalForm.VAMPIRE);
            com.kadamitas.warlockery.transformation.SupernaturalProgression.setResource(player,
                com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.VAMPIRE, 40);
            player.setHealth(player.getMaxHealth());
        });
        supply(context, 0, item("ingredient_warm_blood"));
        eat(context, item("ingredient_warm_blood"));
        check(serverValue(player -> com.kadamitas.warlockery.transformation.SupernaturalState.getReserve(player) == 60
            && !player.hasEffect(MobEffects.HUNGER)), "Vampire gains exactly 20 reserve and no Hunger from native drinking");
        check(serverValue(player -> count(player, Items.GLASS_BOTTLE) == 2), "Both drinks return bottles without loss");
        row.put("checked", List.of("human Hunger II/no reserve", "vampire exact +20 reserve/no Hunger", "two native drinks/two bottles"));
        row.put("fixture", "Vampire form and initial reserve40 staged via progression prerequisite APIs after human control; nighttime excludes sunlight expenditure. No resulting drink reserve/effect injected.");
        row.put("not_run", List.of("reserve capacity clamp", "survival vampire initiation"));
        screenshot(context, "warm-blood-human-vampire-contrast");
    }
    private void animus(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID cow = spawn("minecraft:cow", .5, 100, .5);
        supply(context, 0, item("ingredient_infernal_animus")); useEntity(context, cow);
        check(serverValue(player -> count(player, item("ingredient_infernal_animus")) == 1 && owner(player, cow).isEmpty()),
            "Non-demon target rejects animus without consuming it");
        server(player -> living(player, cow).teleportTo(10.5, 100, .5));
        final UUID imp = spawn("warlockery:imp", .5, 100, .5);
        final UUID otherOwner = UUID.randomUUID();
        server(player -> CreatureBehaviorState.bind(living(player, imp), otherOwner));
        useEntity(context, imp);
        check(serverValue(player -> count(player, item("ingredient_infernal_animus")) == 1 && owner(player, imp).isEmpty()),
            "Demon already owned by another player rejects animus");
        server(player -> { CreatureBehaviorState.unbind(living(player, imp)); ((Mob) living(player, imp)).setTarget(player); });
        useEntity(context, imp);
        await(context, player -> owner(player, imp).equals(player.getStringUUID()) && count(player, item("ingredient_infernal_animus")) == 0
            && ((Mob) living(player, imp)).getTarget() == null, 25, "Native animus binds the exact demon, consumes one and clears hostility");
        row.put("checked", List.of("non-demon gate", "other-owner gate", "actual infernal owner UUID", "hostility cleared", "one consumed animus"));
        row.put("fixture", "Imp and foreign-owner relation staged only for the negative gate, then removed before native positive action.");
        row.put("not_run", List.of("immune bosses", "owner command following/combat", "save/load"));
        screenshot(context, "infernal-animus-native-binding");
    }
    private String owner(final ServerPlayer player, final UUID entity) {
        return com.kadamitas.warlockery.data.WarlockeryEntityData.get(living(player, entity)).getStringOr(InfernalPactEffects.OWNER_KEY, "");
    }
    private void sunlight(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), "time set 18000"));
        final UUID undead = spawn("minecraft:skeleton", -.5, 100, .5), ordinary = spawn("minecraft:cow", 1.5, 100, .5);
        server(player -> {
            for (UUID id : List.of(undead, ordinary)) {
                living(player, id).getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); living(player, id).setHealth(40);
            }
            observation = new ToolObservation(player); observation.hazards.addAll(List.of(undead, ordinary));
        });
        throwItem(context, "sungrenade");
        await(context, player -> observation.projectilesFinished() && observation.firstDamage.containsKey(undead.toString())
            && observation.firstDamage.containsKey(ordinary.toString()), 90, "Real sun grenade impact damages both staged targets");
        final Map<String, Float> damage = serverValue(player -> Map.copyOf(observation.firstDamage));
        check(Math.abs(damage.get(ordinary.toString()) - 5.2F) < .01F, "Non-vulnerable target takes default strength3 base damage5.2");
        check(damage.get(undead.toString()) >= 10.39F && damage.get(undead.toString()) <= 11.41F,
            "Sunlight-vulnerable undead takes doubled10.4 damage, allowing one naturally scheduled burning tick");
        check(serverValue(player -> living(player, undead).isOnFire() && !living(player, ordinary).isOnFire()),
            "Only sunlight-vulnerable target ignites; ordinary cow is a negative fire control");
        row.put("first_observed_damage", damage);
        row.put("checked", List.of("real owned throw/impact", "strength3 base5.2 damage", "double undead damage", "undead ignition/ordinary no ignition", "one grenade consumed"));
        row.put("fixture", "Nighttime excludes ambient sunlight; two stationary targets at40health. Damage observed passively on actual server ticks.");
        row.put("not_run", List.of("Sun Collector charging", "vampire-player progression record", "other charge strengths"));
        screenshot(context, "sun-grenade-vulnerability-control");
    }
    private void torment(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("ingredient_soul_of_torment"));
        useAir(context);
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 0 && !player.getCooldowns().isOnCooldown(player.getMainHandItem())),
            "Soul used without a living target neither wears nor starts cooldown");
        check(serverValue(player -> player.level().getServer().getLevel(AbyssalBanishment.DIMENSION) != null), "Actual Abyss dimension is loaded");
        final UUID cow = spawn("minecraft:cow", .5, 100, .5);
        final BlockPos expected = AbyssalBanishment.arrivalFor(cow);
        server(player -> player.level().getServer().getCommands().performPrefixedCommand(
            player.level().getServer().createCommandSourceStack(), "execute in warlockery:abyss run forceload add "
                + expected.getX() + " " + expected.getZ()));
        context.waitTicks(30);
        useEntity(context, cow);
        await(context, player -> player.level().getEntity(cow) == null && player.level().getServer().getLevel(AbyssalBanishment.DIMENSION).getEntity(cow) instanceof LivingEntity,
            100, "Native Soul of Torment interaction transfers actual target UUID into the Abyss");
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 1 && player.getCooldowns().isOnCooldown(player.getMainHandItem())),
            "Successful banishment consumes exactly one durability and starts cooldown");
        check(serverValue(player -> {
            final var destination = player.level().getServer().getLevel(AbyssalBanishment.DIMENSION);
            final var entity = destination.getEntity(cow);
            return entity.blockPosition().equals(expected) && destination.getBlockState(expected.below()).is(Blocks.BLACKSTONE);
        }), "Actual banished target arrives at its deterministic safe blackstone platform");
        row.put("arrival", expected.toShortString());
        row.put("destination_observation", "The remote destination chunk is kept loaded as a fixture prerequisite so its real entity manager exposes the arriving cow without a second player. No destination entity, platform, teleport or item result is staged.");
        row.put("checked", List.of("no-target control", "native entity interaction", "same UUID in actual Abyss", "safe generated arrival platform", "one wear and cooldown"));
        row.put("not_run", List.of("player target rejection", "repeated cooldown action", "return journey"));
        screenshot(context, "soul-of-torment-target-banished");
    }
    private void throwItem(final ClientGameTestContext context, final String id) {
        supply(context, 0, item(id));
        // Downward-forward throw lands ahead of the player, leaving room for a collision-checked duplicate.
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(35); }); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, player -> count(player, item(id)) == 0 && !observation.ownedProjectiles.isEmpty(), 20,
            "Native item throw consumes one and creates an owned projectile");
    }
    private UUID spawn(final String id, final double x, final double y, final double z) {
        return serverValue(player -> {
            final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof Mob, "Fixture registry ID is an actual mob: " + id);
            final Mob mob = (Mob) entity; mob.snapTo(x, y, z); mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired();
            check(player.level().addFreshEntity(mob), "Fixture entity enters actual world"); return mob.getUUID();
        });
    }
    private static LivingEntity living(final ServerPlayer player, final UUID id) { return (LivingEntity) player.level().getEntity(id); }
    private void useEntity(final ClientGameTestContext context, final UUID id) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false).anyMatch(entity -> entity.getUUID().equals(id)));
        final Vec3 point = serverValue(player -> living(player, id).position().add(0, living(player, id).getBbHeight() * .55, 0));
        look(context, point);
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)), "Native pointer hits exact prerequisite creature");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private void eat(final ClientGameTestContext context, final Item expected) {
        context.runOnClient(client -> client.player.setXRot(-75)); context.waitTicks(2);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try { await(context, player -> count(player, expected) == 0, 55, "Native held use completes eating and consumes one item"); }
        finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void useAir(final ClientGameTestContext context) {
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private static final class ToolObservation {
        final ServerPlayer player;
        final Map<String, String> ownedProjectiles = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> duplicates = new LinkedHashMap<>();
        final List<UUID> hazards = new ArrayList<>();
        final Map<String, Float> firstDamage = new LinkedHashMap<>();
        ToolObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof Projectile projectile && projectile.getOwner() == player)
                ownedProjectiles.put(entity.getUUID().toString(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            if (entity instanceof Mob mob && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("warlockery:glass_doppelganger"))
                duplicates.put(entity.getUUID().toString(), Map.of("type", "warlockery:glass_doppelganger",
                    "reflected_target", com.kadamitas.warlockery.data.WarlockeryEntityData.get(mob).getStringOr("WarlockeryReflectedTarget", ""),
                    "combat_target", mob.getTarget() == null ? "none" : mob.getTarget().getStringUUID(),
                    "name", mob.getDisplayName().getString()));
        }
        void tick() {
            for (UUID id : hazards) {
                final Entity entity = player.level().getEntity(id);
                if (entity instanceof LivingEntity living && living.getHealth() < 40)
                    firstDamage.putIfAbsent(id.toString(), 40 - living.getHealth());
            }
        }
        boolean projectilesFinished() { return !ownedProjectiles.isEmpty()
            && ownedProjectiles.keySet().stream().allMatch(id -> player.level().getEntity(UUID.fromString(id)) == null); }
        Map<String, Object> report() { return Map.of("owned_projectiles", Map.copyOf(ownedProjectiles), "duplicates", Map.copyOf(duplicates),
            "first_damage", Map.copyOf(firstDamage), "observer", "Passive actual entity-load and end-server-tick observation, no outcome injection"); }
    }
    private void readGuides(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final List<String> requested = List.of(id, "gestures");
        final List<Map<String, Object>> guides = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String section : requested) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
            if (found.isEmpty()) { missing.add(section); continue; }
            final var profile = found.orElseThrow();
            supply(context, 0, item(profile.id()));
            context.runOnClient(client -> client.player.setXRot(-70));
            context.waitTicks(2); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, section);
            final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                    method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            check(!body.isBlank() && pages > 0, "Available guide has actual readable pages");
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                    && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book paging visits each instruction page");
                screenshot(context, id + "-guide-" + section + "-" + page);
            }
            guides.add(Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages));
            close(context);
        }
        row.put("guides_read", guides);
        row.put("missing_indexed_guidance", missing);
        row.put("guidance_complete", missing.isEmpty());
    }

    private void place(final ClientGameTestContext context, final Item item, final BlockPos target, final String expectedId,
        final boolean crouch) {
        supply(context, 0, item);
        if (crouch) { context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3); }
        try { use(context, new Vec3(target.getX() + 0.5, target.getY() - 0.001, target.getZ() + 0.5), target.below()); }
        finally { if (crouch) { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(2); } }
        final Identifier expected = expectedId.contains(":") ? Identifier.parse(expectedId) : Identifier.fromNamespaceAndPath("warlockery", expectedId);
        await(context, player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(target).getBlock()).equals(expected), 30,
            "Native placement produces the expected device at " + target);
        check(serverValue(player -> player.getInventory().getItem(0).isEmpty()), "Survival placement consumes exactly the one staged block item");
    }

    private void supply(final ClientGameTestContext context, final int slot, final Item item) {
        server(player -> { player.getInventory().setItem(slot, new ItemStack(item)); player.inventoryMenu.broadcastChanges(); });
        sync(context, slot);
    }

    private void sync(final ClientGameTestContext context, final int hotbar) {
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1 + hotbar); context.waitTicks(2);
    }

    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        });
        context.waitTicks(2);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets the intended support/device: " + expected);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }

    private static void clickPlayerSlot(final ClientGameTestContext context, final int index) {
        final int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(candidate -> client.player.containerMenu.getSlot(candidate).container == client.player.getInventory()
                && client.player.containerMenu.getSlot(candidate).getContainerSlot() == index).findFirst().orElseThrow());
        clickSlot(context, slot);
    }

    private static void clickSlot(final ClientGameTestContext context, final int index) {
        final int[] point = context.computeOnClient(client -> {
            final var slot = client.player.containerMenu.getSlot(index);
            return new int[] {(int) field(client.gui.screen(), "leftPos") + slot.x + 8,
                (int) field(client.gui.screen(), "topPos") + slot.y + 8};
        });
        ManualClientAcceptance.click(context, point[0], point[1]); context.waitTicks(2);
    }

    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
    }

    private static void close(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTicks(3);
        }
    }

    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    private static int count(final ServerPlayer player, final Item item) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(item)) result += player.getInventory().getItem(slot).getCount();
        return result;
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", failure.toString()); failures.add(id + ": " + failure);
    }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("selected_native_scenarios_passed", finished && failures.isEmpty());
        report.put("all_nine_native_scenarios_passed", finished && results.size() == DEVICES.size()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key item/block/GUI interactions, real block, entity, food, power and item behavior. Scenario prerequisites explicitly staged; no resulting resource behavior/components injected.");
        final Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("test", classHash(RemainingToolsAbilitiesClientAcceptance.class));
        for (String id : DEVICES) hashes.put(id, classHash(item(id).getClass()));
        report.put("class_sha256", hashes);
        report.put("devices", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("remaining-tools-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("remaining-tools-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
