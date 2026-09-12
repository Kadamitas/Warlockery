package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import com.kadamitas.warlockery.brew.BrewPersistentRuntime;
import com.kadamitas.warlockery.brew.BrewCompatibilityTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.function.Predicate;

public final class BrewRemainingAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(
        BrewBehavior.LIFT,
        BrewBehavior.PUSH,
        BrewBehavior.PULL,
        BrewBehavior.REPEL_ANIMALS,
        BrewBehavior.FEAR,
        BrewBehavior.PULL_TO_OWNER,
        BrewBehavior.EXPLODE,
        BrewBehavior.RANDOM_TELEPORT,
        BrewBehavior.BOTTLE_YIELD,
        BrewBehavior.SHIFT_SEASONS,
        BrewBehavior.APPLY_SINKING,
        BrewBehavior.APPLY_SNOW_TRAIL,
        BrewBehavior.APPLY_RESIZING,
        BrewBehavior.APPLY_ILL_FITTING,
        BrewBehavior.APPLY_OVERHEATING,
        BrewBehavior.APPLY_DEPTHS,
        BrewBehavior.APPLY_GROTESQUE,
        BrewBehavior.MOONLIGHT,
        BrewBehavior.APPLY_MOONSHINE,
        BrewBehavior.DARKNESS_PREY);
    private static final AABB AREA = new AABB(-40, 90, -40, 41, 125, 41);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile ImpactObservation observation;
    private double baselineWalk;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-remaining-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final ImpactObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().stream().anyMatch(COVERED::contains)).sorted().toList();
            check(!ids.isEmpty(), "Actual registry must expose remaining-effect brews");
            for (BrewBehavior behavior : COVERED) {
                check(ids.stream().anyMatch(id -> ((BrewItem) BuiltInRegistries.ITEM.getValue(id))
                    .kind().behaviors().contains(behavior)), "Registry census must expose remaining behavior " + behavior);
            }
            final String configured = System.getProperty("warlockery.brewRemainingIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual remaining-family registry census");
            for (Identifier id : ids) {
                final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", selected.contains(id.toString()) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("kind", kind.id());
                row.put("behaviors", kind.behaviors().stream().map(Enum::name).toList());
                row.put("radius", kind.radius());
                row.put("potency", kind.potency());
                row.put("canonical_guide", "brew_entry_" + kind.id());
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                results.put(id.toString(), row);
            }
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (Identifier id : ids) {
                if (!selected.contains(id.toString())) continue;
                final Map<String, Object> row = results.get(id.toString());
                row.put("status", "RUNNING");
                row.put("started_at", System.currentTimeMillis());
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        runBrew(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        recordFailure(id, row, failure);
                        try { screenshot(context, id.getPath() + "-failure-in-world"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        final ImpactObservation current = observation;
                        if (current != null) row.put("impact_observation", serverValue(player -> current.report()));
                        observation = null;
                        row.put("finished_at", System.currentTimeMillis());
                        write(false);
                    }
                } catch (Throwable failure) {
                    recordFailure(id, row, failure);
                    write(false);
                } finally {
                    observation = null;
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BREW_REMAINING_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native thrown brew evidence: " + evidence, failure);
        }
    }


    private void runBrew(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
        world.getServer().runOnServer(server -> {
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.getAbilities().invulnerable = true; player.onUpdateAbilities(); player.setHealth(player.getMaxHealth());
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-18, 94, -18), new BlockPos(22, 108, 18)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            resetPosition(player); time(player, 6000);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        if (has(kind, BrewBehavior.APPLY_ILL_FITTING)) {
            baselineWalk = walk(context, 16);
            server(BrewRemainingAbilitiesClientAcceptance::resetPosition); world.getConnection().waitForClientboundPackets();
        }
        if (has(kind, BrewBehavior.APPLY_MOONSHINE)) {
            final double loss = cactus(context);
            check(Math.abs(loss - 1.0) < .01, "Untreated native cactus control causes one damage");
            row.put("untreated_cactus_damage", loss);
            server(player -> { resetPosition(player); player.setHealth(player.getMaxHealth()); player.getAbilities().invulnerable = true; player.onUpdateAbilities(); });
        }
        server(player -> {
            final Mob target = mob(player, 2.5, 100, .5, !isMotion(kind));
            final Mob control = mob(player, 16.5, 100, .5, true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80); target.setHealth(80);
            control.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80); control.setHealth(80);
            observation = new ImpactObservation(player, target, control, id);
            stage(player, kind);
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
        });
        row.put("fixture", "Fresh Survival world; player temporarily invulnerable while target hazards are checked. Staged blocks, mobs, prerequisite armor/effects and biome/time are named in scenario checks. No marker, brew outcome, damage call, resulting block or velocity is injected. Moving targets have normal AI/travel enabled.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); }); context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        server(player -> observation.begin());
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, player -> observation.finishedImpact(), 100, "Actual owned thrown brew reaches a normal impact");
        check(serverValue(player -> observation.projectileIds.size() == 1
            && !player.getInventory().getItem(0).is(BuiltInRegistries.ITEM.getValue(id))),
            "One native throw consumes exactly one supplied brew");
        verify(context, kind, row);
        row.put("tested_families", kind.behaviors().stream().filter(COVERED::contains).map(Enum::name).toList());
        row.put("other_kind_behaviors", kind.behaviors().stream().filter(value -> !COVERED.contains(value)).map(Enum::name).toList());
        row.put("remaining", remaining(kind));
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(18));
        screenshot(context, id.getPath() + "-native-outcome");
    }
    private static boolean has(final BrewKind kind, final BrewBehavior behavior) { return kind.behaviors().contains(behavior); }
    private static boolean isMotion(final BrewKind kind) {
        return kind.behaviors().stream().anyMatch(value -> Set.of(BrewBehavior.LIFT, BrewBehavior.PUSH, BrewBehavior.PULL,
            BrewBehavior.PULL_TO_OWNER, BrewBehavior.REPEL_ANIMALS, BrewBehavior.FEAR).contains(value));
    }
    private static Mob mob(final ServerPlayer player, final double x, final double y, final double z, final boolean stationary) {
        final Entity created = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow")).create(player.level(), EntitySpawnReason.COMMAND);
        check(created instanceof Mob, "Actual cow fixture exists");
        final Mob target = (Mob) created; target.snapTo(x, y, z); target.setNoAi(stationary); target.setPersistenceRequired();
        check(player.level().addFreshEntity(target), "Prerequisite cow enters actual world"); return target;
    }
    private static void time(final ServerPlayer player, final int ticks) {
        player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), "time set " + ticks);
    }
    private static void biome(final ServerPlayer player, final String id) {
        final var biome = player.level().registryAccess().lookupOrThrow(Registries.BIOME)
            .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
        net.minecraft.server.commands.FillBiomeCommand.fill(player.level(), new BlockPos(-16, 94, -16), new BlockPos(20, 108, 16), biome);
    }
    private void stage(final ServerPlayer player, final BrewKind kind) {
        final var level = player.level(); final var target = observation.target;
        if (has(kind, BrewBehavior.EXPLODE)) {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-2, 99, -2), new BlockPos(2, 99, 2)))
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(14, 99, 0), Blocks.DIRT.defaultBlockState());
        }
        if (has(kind, BrewBehavior.RANDOM_TELEPORT)) {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-32, 99, -32), new BlockPos(32, 107, 32)))
                level.setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(0, 107, 0), Blocks.BEDROCK.defaultBlockState());
            player.teleportTo(.5, 108, .5); target.teleportTo(2.5, 108, .5); target.setNoGravity(true);
        }
        if (has(kind, BrewBehavior.SHIFT_SEASONS)) {
            time(player, 90000); biome(player, "minecraft:plains");
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(14, 99, 0), Blocks.WATER.defaultBlockState());
        }
        if (has(kind, BrewBehavior.APPLY_ILL_FITTING)) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            observation.baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        }
        if (has(kind, BrewBehavior.APPLY_OVERHEATING)) {
            biome(player, "minecraft:desert");
            observation.control.teleportTo(3.5, 100, 1.5);
            observation.control.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
        }
        if (has(kind, BrewBehavior.APPLY_GROTESQUE)) {
            observation.fleeing = mob(player, 6.5, 100, .5, false);
            observation.fleeStart = observation.fleeing.position();
        }
        if (has(kind, BrewBehavior.MOONLIGHT)) {
            time(player, 18000); target.setHealth(60);
            observation.control.teleportTo(3.5, 100, 1.5); observation.control.setHealth(60);
            level.setBlockAndUpdate(new BlockPos(3, 103, 1), Blocks.BEDROCK.defaultBlockState());
        }
        if (has(kind, BrewBehavior.DARKNESS_PREY)) time(player, 6000);
    }
    private void verify(final ClientGameTestContext context, final BrewKind kind, final Map<String, Object> row) throws Exception {
        if (isMotion(kind)) {
            if (has(kind, BrewBehavior.LIFT)) {
                await(context, player -> observation.maxY >= observation.start.y + .75, 45, "Native lifting velocity results in real upward travel");
            } else if (has(kind, BrewBehavior.PULL) || has(kind, BrewBehavior.PULL_TO_OWNER)) {
                await(context, player -> observation.minX < observation.start.x - .4, 35, "Native pulling moves the actual cow toward the impact/owner");
            } else {
                await(context, player -> observation.maxX > observation.start.x + .4, 35, "Native repulsion physically moves the actual cow away");
            }
            check(serverValue(player -> observation.control.getHealth() == 80), "Out-of-range living control is unharmed");
            row.put("movement", serverValue(player -> observation.motion()));
        }
        if (has(kind, BrewBehavior.EXPLODE)) {
            await(context, player -> observation.target.getHealth() < 80, 35, "Actual explosion damages nearby target");
            check(serverValue(player -> dirt(player, -2, 2) < 25 && player.level().getBlockState(new BlockPos(14, 99, 0)).is(Blocks.DIRT)),
                "Explosion destroys nearby staged dirt while distant control block survives");
            check(serverValue(player -> observation.control.getHealth() == 80), "Distant creature is outside explosion damage");
            row.put("actual_explosion_health", serverValue(player -> observation.target.getHealth()));
            if (has(kind, BrewBehavior.IGNITE)) check(serverValue(player -> observation.target.isOnFire()), "Inferno also actually ignites its target");
        }
        if (has(kind, BrewBehavior.RANDOM_TELEPORT)) {
            check(serverValue(player -> observation.target.position().distanceTo(observation.start) > .5 && observation.target.getY() >= 99.9
                && player.level().getBlockState(observation.target.blockPosition().below()).is(Blocks.BEDROCK)),
                "Native Transpose chooses a changed, supported actual destination; terrain covers the full random range");
            row.put("actual_teleport_destination", serverValue(player -> observation.target.position().toString()));
        }
        if (has(kind, BrewBehavior.BOTTLE_YIELD)) {
            final int expected = Math.clamp((int) Math.ceil(kind.potency() * 3), 1, 8);
            await(context, player -> available(player, Items.GLASS_BOTTLE) == expected, 25, "Native bottling impact creates exact formula bottle count");
            row.put("actual_bottles", expected);
        }
        if (has(kind, BrewBehavior.SHIFT_SEASONS)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 99, 1)).is(Blocks.ICE)
                && player.level().getBlockState(new BlockPos(14, 99, 0)).is(Blocks.WATER)), "Winter seasonal throw freezes nearby source water but not distant control");
            check(serverValue(player -> player.level().getBiome(new BlockPos(0, 100, 0)).is(BrewCompatibilityTags.Biomes.WINTER)),
                "Native seasonal impact changes actual loaded biome to a winter-tag member");
            row.put("winter_biome", serverValue(player -> player.level().getBiome(new BlockPos(0, 100, 0)).unwrapKey().orElseThrow().identifier().toString()));
        }
        if (has(kind, BrewBehavior.APPLY_RESIZING)) {
            await(context, player -> Math.abs(observation.target.getAttributeValue(Attributes.SCALE) - .5) < .001, 30, "Native resizing changes live scale attribute to half");
            await(context, player -> observation.target.getBoundingBox().getYsize() < observation.baseHeight * .6
                && observation.control.getAttributeValue(Attributes.SCALE) == 1, 30,
                "Actual collision height shrinks while outside control remains full size");
            row.put("actual_height", serverValue(player -> observation.target.getBoundingBox().getYsize()));
        }
        if (has(kind, BrewBehavior.APPLY_SNOW_TRAIL)) {
            await(context, player -> snow(player, player.blockPosition()) > 0, 45, "Native snow-trail runtime places actual snow at the marked player");
            final double travelled = walk(context, 55);
            check(travelled > kind.radius() + 1, "Native walking leaves the initial snow-burst footprint");
            row.put("snow_trail_distance", travelled);
            await(context, player -> snow(player, player.blockPosition()) > 0, 45, "Snow follows the player's native walking to a new location");
            check(serverValue(player -> snow(player, observation.control.blockPosition()) == 0), "Untreated distant cow leaves no snow trail");
            row.put("native_walk_destination", serverValue(player -> player.position().toString()));
        }
        if (has(kind, BrewBehavior.APPLY_ILL_FITTING)) {
            await(context, player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED) < observation.baseSpeed * .5, 45,
                "Four armor pieces under native Ill Fitting produce actual movement-speed reduction");
            check(serverValue(player -> !observation.target.hasEffect(MobEffects.SLOWNESS)), "Unarmored marked cow is a negative Ill Fitting control");
            final double burdened = walk(context, 16);
            check(burdened < baselineWalk * .75 && burdened > 0, "Native walking under burden is slower than same player's untreated walk");
            row.put("untreated_walk_distance", baselineWalk); row.put("burdened_walk_distance", burdened);
            server(player -> { for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) player.setItemSlot(slot, ItemStack.EMPTY); });
            context.waitTicks(45);
            check(serverValue(player -> player.getAttributeValue(Attributes.MOVEMENT_SPEED) >= observation.baseSpeed * .99),
                "Removing armor lets transient burden expire while brew marker persists");
        }
        if (has(kind, BrewBehavior.APPLY_OVERHEATING)) {
            await(context, player -> observation.target.getHealth() < 80 && observation.target.isOnFire(), 60, "Marked target really burns and loses health in a hot biome");
            check(serverValue(player -> observation.control.getHealth() == 80), "Fire-resistant marked control takes no overheating or ignition damage");
            server(player -> { observation.target.clearFire(); observation.target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0)); });
            final float health = serverValue(player -> observation.target.getHealth()); context.waitTicks(42);
            check(serverValue(player -> observation.target.getHealth() == health), "Adding fire resistance stops subsequent overheating damage");
        }
        if (has(kind, BrewBehavior.DARKNESS_PREY)) {
            check(serverValue(player -> observation.target.getHealth() == 80), "Bright daytime is a negative control for Grue's Prey damage");
            server(player -> {
                time(player, 18000);
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 103, -12), new BlockPos(12, 106, 12)))
                    player.level().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            });
            await(context, player -> player.level().getMaxLocalRawBrightness(observation.target.blockPosition()) <= 5, 60, "Fixture reaches actual low light");
            await(context, player -> observation.target.getHealth() < 80, 80, "Marked target suffers actual recurring damage after entering darkness");
            row.put("actual_darkness_health", serverValue(player -> observation.target.getHealth()));
        }
        if (has(kind, BrewBehavior.APPLY_SINKING)) sinking(context, row);
        if (has(kind, BrewBehavior.APPLY_DEPTHS)) depths(context, row);
        if (has(kind, BrewBehavior.APPLY_GROTESQUE)) {
            await(context, player -> observation.fleeing.position().distanceTo(observation.target.position())
                > observation.fleeStart.distanceTo(observation.target.position()) + 1, 100, "Nearby untreated mob actually flees the grotesque subject through native navigation");
            row.put("fleeing_mob_position", serverValue(player -> observation.fleeing.position().toString()));
        }
        if (has(kind, BrewBehavior.MOONLIGHT)) moonshine(context, row);
    }
    private void sinking(final ClientGameTestContext context, final Map<String, Object> row) {
        check(serverValue(player -> observation.target.getY() >= 99.9), "Sinking does not pull target through dry solid ground");
        server(player -> {
            pool(player, 2); pool(player, 16);
            observation.target.setNoAi(false); observation.control.setNoAi(false);
            observation.target.teleportTo(2.5, 100, .5); observation.control.teleportTo(16.5, 100, .5);
            observation.target.setDeltaMovement(Vec3.ZERO); observation.control.setDeltaMovement(Vec3.ZERO);
        });
        await(context, player -> observation.target.getY() < observation.control.getY() - .5, 80,
            "Actual marked cow sinks below untreated swimming cow in matching water pools");
        row.put("sinking_y", serverValue(player -> observation.target.getY())); row.put("untreated_swimmer_y", serverValue(player -> observation.control.getY()));
    }
    private void depths(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            pool(player, 2); observation.target.teleportTo(2.5, 97, .5); observation.target.setAirSupply(40);
        });
        await(context, player -> observation.target.getAirSupply() >= observation.target.getMaxAirSupply() - 20, 45,
            "Native Depths refills actual air while target is submerged");
        row.put("submerged_air", serverValue(player -> observation.target.getAirSupply()));
        server(player -> { observation.target.teleportTo(7.5, 100, .5); observation.target.setAirSupply(observation.target.getMaxAirSupply()); });
        final float before = serverValue(player -> observation.target.getHealth());
        await(context, player -> observation.target.getHealth() < before, 220,
            "Starting from normal full air, native Depths must actually suffocate its target on dry land");
        row.put("dry_land_health", serverValue(player -> observation.target.getHealth()));
    }
    private void moonshine(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        await(context, player -> observation.target.getHealth() > 60, 80, "Moonlit subject actually heals from native Moonshine regeneration");
        check(serverValue(player -> !observation.control.hasEffect(MobEffects.REGENERATION) && !observation.control.hasEffect(MobEffects.LUCK)
            && observation.control.getHealth() == 60), "Roofed subject is excluded from the moonlight blessing");
        final double damage = cactus(context);
        check(Math.abs(damage - .5) < .01, "Moonshine halves actual normal cactus damage compared with untreated one-damage control");
        row.put("moonshine_cactus_damage", damage);
        final float saturation = serverValue(player -> {
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(1);
            return player.getFoodData().getSaturationLevel();
        });
        context.waitTicks(360);
        check(serverValue(player -> player.getFoodData().getSaturationLevel() < saturation || player.getFoodData().getFoodLevel() < 20),
            "Moonshine's recurring exhaustion really spends food saturation while standing still");
    }
    private double cactus(final ClientGameTestContext context) {
        server(player -> {
            player.getAbilities().invulnerable = false; player.onUpdateAbilities(); player.setHealth(player.getMaxHealth());
            player.level().setBlockAndUpdate(new BlockPos(0, 98, -4), Blocks.BEDROCK.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 99, -4), Blocks.SAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.CACTUS.defaultBlockState());
            player.teleportTo(.5, 100, -5.1); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(2);
        final float before = serverValue(ServerPlayer::getHealth);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { await(context, player -> player.getHealth() < before, 45, "Native walk contacts staged cactus and takes normal game damage"); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        final float after = serverValue(ServerPlayer::getHealth);
        server(player -> { player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.AIR.defaultBlockState());
            player.getAbilities().invulnerable = true; player.onUpdateAbilities(); player.setDeltaMovement(Vec3.ZERO); });
        return before - after;
    }
    private double walk(final ClientGameTestContext context, final int ticks) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(2);
        final Vec3 before = serverValue(ServerPlayer::position);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { context.waitTicks(ticks); } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        context.waitTicks(2); return serverValue(player -> player.position().distanceTo(before));
    }
    private static void pool(final ServerPlayer player, final int x) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x - 1, 94, -1), new BlockPos(x + 1, 100, 1)))
            player.level().setBlockAndUpdate(pos, pos.getY() == 94 ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x - 2, 94, -2), new BlockPos(x + 2, 100, 2)))
            if (pos.getX() == x - 2 || pos.getX() == x + 2 || pos.getZ() == -2 || pos.getZ() == 2)
                player.level().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
    }
    private static long dirt(final ServerPlayer player, final int minimum, final int maximum) {
        return BlockPos.betweenClosedStream(new BlockPos(minimum, 99, minimum), new BlockPos(maximum, 99, maximum))
            .filter(pos -> player.level().getBlockState(pos).is(Blocks.DIRT)).count();
    }
    private static long snow(final ServerPlayer player, final BlockPos center) {
        return BlockPos.betweenClosedStream(center.offset(-1, -1, -1), center.offset(1, 0, 1))
            .filter(pos -> player.level().getBlockState(pos).is(Blocks.SNOW)).count();
    }
    private static int available(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        int count = player.level().getEntitiesOfClass(ItemEntity.class, AREA, Entity::isAlive).stream()
            .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(item)) count += player.getInventory().getItem(i).getCount();
        return count;
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int i = 0; i < ticks && !serverValue(condition::test); i++) context.waitTicks(1);
        check(serverValue(condition::test), message);
    }
    private static String remaining(final BrewKind kind) {
        if (has(kind, BrewBehavior.SHIFT_SEASONS)) return "Spring, summer and autumn branches; biome tag alternatives; acquisition/persistence.";
        if (has(kind, BrewBehavior.APPLY_DEPTHS)) return "Other fluids and player control; acquisition/persistence. Dry-land full-air suffocation is required here, not merely marker presence.";
        if (has(kind, BrewBehavior.RANDOM_TELEPORT)) return "Blocked destination, multiple random layouts and ender-inhibition interaction; acquisition/persistence.";
        return "All delivery variants, complete duration/expiry, multiplayer attribution, acquisition and persistence. Only the listed families and actual outcomes are checked.";
    }
    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final var candidate = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
        check(candidate.isPresent(), "Player books must index canonical remaining-brew guide " + section);
        final ManualProfile profile = candidate.orElseThrow();
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-75));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        check(!text.isBlank(), "Canonical brew guide must contain readable text");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                && (int) field(client.gui.screen(), "bodyPage") == expected), "Native page input reaches the canonical brew instructions");
            screenshot(context, id.getPath() + "-canonical-book-" + page);
        }
        row.put("book", profile.id());
        row.put("book_status", "ALL_PAGES_REACHED");
        row.put("book_pages_read", pages);
        row.put("book_text", text);
        row.put("guide_mapping", "This exact registry item uses BrewKind " + kind.id() + "; canonical section " + section
            + " is read for that shared behavior. This does not assert identical acquisition recipes for aliases.");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void resetPosition(final ServerPlayer player) {
        player.teleportTo(0.5, 100, 0.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }


    private static final class ImpactObservation {
        final ServerPlayer player; final Mob target; final Mob control; final Identifier item;
        final Set<String> projectileIds = new LinkedHashSet<>();
        Mob fleeing; Vec3 fleeStart; Vec3 start; double maxY, minX, maxX, baseHeight, baseSpeed;
        int ticks; boolean started;
        ImpactObservation(final ServerPlayer player, final Mob target, final Mob control, final Identifier item) {
            this.player = player; this.target = target; this.control = control; this.item = item;
        }
        void begin() { start = target.position(); maxY = start.y; minX = start.x; maxX = start.x; baseHeight = target.getBoundingBox().getYsize(); started = true; }
        void loaded(final Entity entity) {
            if (entity instanceof AbstractThrownPotion potion && potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)))
                projectileIds.add(entity.getUUID().toString());
        }
        void tick() {
            if (!started) return; ticks++;
            maxY = Math.max(maxY, target.getY()); minX = Math.min(minX, target.getX()); maxX = Math.max(maxX, target.getX());
        }
        boolean finishedImpact() { return !projectileIds.isEmpty() && projectileIds.stream().allMatch(id -> player.level().getEntity(UUID.fromString(id)) == null); }
        Map<String, Object> motion() { return Map.of("start", String.valueOf(start), "current", target.position().toString(),
            "max_y", maxY, "min_x", minX, "max_x", maxX, "target_no_ai", target.isNoAi()); }
        Map<String, Object> report() {
            final Map<String, Object> data = new LinkedHashMap<>(); data.put("native_projectile_uuids", List.copyOf(projectileIds));
            data.put("observed_server_ticks", ticks); data.put("target_uuid", target.getUUID().toString()); data.put("motion", motion());
            data.put("target_health", target.getHealth()); data.put("target_air", target.getAirSupply()); data.put("target_on_fire", target.isOnFire());
            data.put("control_health", control.getHealth()); data.put("target_scale", target.getAttributeValue(Attributes.SCALE));
            data.put("control_position", control.position().toString());
            data.put("target_fluid_height", target.getFluidHeight(net.minecraft.tags.FluidTags.WATER));
            data.put("target_light", player.level().getMaxLocalRawBrightness(target.blockPosition()));
            data.put("target_effects", target.getActiveEffects().stream().map(Object::toString).toList());
            data.put("player_health", player.getHealth()); data.put("player_position", player.position().toString());
            return data;
        }
    }
    private static Object field(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer()));
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void recordFailure(final Identifier id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED");
        row.put("failure", failure.toString());
        failures.add(id + ": " + failure);
    }

    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes: " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private void write(final boolean finished) throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("selected_scenarios_passed", finished && failures.isEmpty());
        report.put("all_censused_aliases_passed", finished && !results.isEmpty()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("execution", "Rendered Fabric development-classpath client. Actual native potion throws and normal collision/world/entity behavior; "
            + "passive end-of-server-tick observations. Fresh staged worlds. No direct impact/damage/world-effect invocation.");
        report.put("class_sha256", Map.of("test", classHash(BrewRemainingAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class), "persistent_runtime", classHash(BrewPersistentRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-remaining-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-remaining-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
