package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModFluids;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BrewNatureAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(BrewBehavior.GROW, BrewBehavior.EXTINGUISH,
        BrewBehavior.WASTE, BrewBehavior.HARVEST_CROPS, BrewBehavior.TILL_SOIL, BrewBehavior.REVEAL,
        BrewBehavior.BLIGHT);
    private static final AABB AREA = new AABB(-10, 97, -10, 11, 115, 11);
    private static final List<BlockPos> WATER = List.of(new BlockPos(-1, 99, 1), new BlockPos(0, 99, 2), new BlockPos(1, 99, 1));
    private static final BlockPos OBSIDIAN = new BlockPos(1, 100, 2);
    private static final BlockPos CALCITE = new BlockPos(-1, 100, 1);
    private static final BlockPos PRESERVED = new BlockPos(1, 100, -1);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile ImpactObservation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-nature-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final ImpactObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().stream().anyMatch(COVERED::contains)
                    && !item.kind().behaviors().contains(BrewBehavior.EXPLODE)).sorted().toList();
            check(!ids.isEmpty(), "Actual registry must expose nature-effect brews");
            final String configured = System.getProperty("warlockery.brewNatureIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual nature-family registry census");
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
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
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
            System.out.println("WARLOCKERY_BREW_NATURE_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native thrown brew evidence: " + evidence, failure);
        }
    }

    private void runBrew(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-9, 98, -9), new BlockPos(9, 111, 9))) {
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99
                    ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            resetPosition(player);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        final UUID target = serverValue(player -> {
            final boolean wasting = kind.behaviors().contains(BrewBehavior.WASTE);
            final String targetId = wasting ? "minecraft:zombie" : "minecraft:cow";
            final Entity created = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(targetId))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(created instanceof Mob, "Nature-brew fixture must create its real living target");
            final Mob fixtureTarget = (Mob) created;
            fixtureTarget.setPos(1.5, 100, 1.5);
            fixtureTarget.setNoAi(true);
            player.level().addFreshEntity(fixtureTarget);
            stageNatureFixture(player, kind, fixtureTarget);
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
            observation = new ImpactObservation(player, fixtureTarget, id);
            return fixtureTarget.getUUID();
        });
        row.put("target_uuid", target.toString());
        row.put("fixture", fixtureDescription(kind));
        final Map<String, String> tillBeforeStates = kind.behaviors().contains(BrewBehavior.TILL_SOIL)
            ? serverValue(BrewNatureAbilitiesClientAcceptance::tillVolumeSnapshot) : Map.of();
        if (kind.behaviors().contains(BrewBehavior.TILL_SOIL)) {
            row.put("till_before", serverValue(BrewNatureAbilitiesClientAcceptance::tillDiagnostics));
        }
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100; tick++) {
            if (serverValue(player -> !observation.projectileIds.isEmpty() && observation.projectilesPresent == 0)) break;
            context.waitTicks(1);
        }
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.projectilesPresent == 0),
            "One real owned thrown potion must exist and complete its native impact");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "One native throw consumes the supplied brew");
        context.waitTicks(2);
        final List<String> checks = new ArrayList<>();
        checks.add("Exactly one native owned projectile impacted and consumed the supplied brew.");

        if (kind.behaviors().contains(BrewBehavior.GROW)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 100, 2))
                    .is(Blocks.WHEAT)
                    && player.level().getBlockState(new BlockPos(-1, 100, 2))
                        .getValue(net.minecraft.world.level.block.CropBlock.AGE) == 7),
                "Native growth brew must force the staged immature wheat to maturity");
            checks.add("Immature wheat reached its real maximum growth age.");
        }
        if (kind.behaviors().contains(BrewBehavior.EXTINGUISH)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(2, 100, 1)).isAir()
                    && !player.level().getBlockState(new BlockPos(-2, 100, 1))
                        .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && !player.level().getBlockState(new BlockPos(0, 100, 2))
                        .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && player.level().getEntity(target) instanceof LivingEntity living && !living.isOnFire()),
                "Native extinguishing brew must clear fire, put out campfire and candle, and extinguish the burning creature");
            checks.add("Fire removed; lit campfire and candle extinguished; burning creature extinguished.");
        }
        if (kind.behaviors().contains(BrewBehavior.WASTE)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 100, 2)).isAir()
                    && player.level().getBlockState(new BlockPos(0, 100, 2)).isAir()
                    && player.hasEffect(MobEffects.HUNGER)
                    && player.getEffect(MobEffects.HUNGER).getAmplifier() == 1
                    && player.level().getEntity(target) instanceof LivingEntity living
                    && living.hasEffect(MobEffects.WITHER)
                    && living.getEffect(MobEffects.WITHER).getAmplifier() == 1),
                "Native wasting brew must decay leaves, hunger the player, and wither the hostile creature");
            checks.add("Leaves decayed; player received Hunger II and hostile received Wither II.");
        }
        if (kind.behaviors().contains(BrewBehavior.HARVEST_CROPS)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 100, 2))
                    .getValue(net.minecraft.world.level.block.CropBlock.AGE) == 0
                    && player.level().getBlockState(new BlockPos(1, 100, 2))
                        .getValue(net.minecraft.world.level.block.CropBlock.AGE) == 2
                    && cropYield(player) > 0),
                "Native harvest brew must drop and reset mature wheat while preserving the immature control crop");
            checks.add("Mature wheat dropped harvest and reset to age zero; immature wheat remained age two.");
        }
        if (kind.behaviors().contains(BrewBehavior.TILL_SOIL)) {
            row.put("till_after", serverValue(BrewNatureAbilitiesClientAcceptance::tillDiagnostics));
            row.put("till_first_changed_block", serverValue(player -> firstTillChange(player, tillBeforeStates)));
            check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 99, 2)).is(Blocks.FARMLAND)
                    && player.level().getBlockState(new BlockPos(1, 99, 2)).is(Blocks.FARMLAND)
                    && player.level().getBlockState(new BlockPos(2, 99, 0)).is(Blocks.DIRT)
                    && player.level().getBlockState(new BlockPos(2, 100, 0)).is(Blocks.STONE)),
                "Native tilling brew must convert clear dirt and grass while preserving obstructed dirt");
            checks.add("Clear dirt and grass became farmland; stone-obstructed dirt remained unchanged.");
        }
        if (kind.behaviors().contains(BrewBehavior.REVEAL)) {
            check(serverValue(player -> player.level().getEntity(target) instanceof LivingEntity living
                    && !living.isInvisible() && !living.hasEffect(MobEffects.INVISIBILITY)
                    && living.hasEffect(MobEffects.GLOWING)
                    && living.getEffect(MobEffects.GLOWING).getDuration() >= 295),
                "Native revealing brew must clear invisibility and give the actual target a glowing outline");
            checks.add("Invisible target became visible, lost Invisibility and gained the documented Glowing duration.");
        }
        if (kind.behaviors().contains(BrewBehavior.BLIGHT)) {
            check(serverValue(player -> player.level().getBlockState(new BlockPos(-2, 100, 2)).isAir()
                    && player.level().getBlockState(new BlockPos(-1, 100, 2)).isAir()
                    && player.level().getBlockState(new BlockPos(0, 100, 2)).isAir()
                    && player.level().getBlockState(new BlockPos(1, 100, 2)).isAir()
                    && player.level().getBlockState(new BlockPos(2, 99, 2)).is(Blocks.DIRT)
                    && player.level().getEntity(target) instanceof LivingEntity living
                    && living.hasEffect(MobEffects.WITHER)
                    && living.getEffect(MobEffects.WITHER).getAmplifier() == 0
                    && living.hasEffect(MobEffects.WEAKNESS)
                    && living.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1),
                "Native blight must destroy vegetation, spoil farmland, and afflict the eligible beast");
            check(serverValue(player -> cropYield(player) == 0),
                "Blighted vegetation must not produce ordinary wheat or seed harvest drops");
            checks.add("Crop, flower, leaves and sapling destroyed without crop harvest; farmland became dirt; beast gained Wither I and Weakness II.");
        }
        row.put("checks", checks);
        row.put("remaining", remaining(kind));
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(25));
        screenshot(context, id.getPath() + "-native-nature-outcome");
    }

    private static void stageNatureFixture(final ServerPlayer player, final BrewKind kind, final Mob target) {
        if (kind.behaviors().contains(BrewBehavior.GROW)) {
            player.level().setBlockAndUpdate(new BlockPos(-1, 99, 2), Blocks.FARMLAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-1, 100, 2),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 0));
        }
        if (kind.behaviors().contains(BrewBehavior.EXTINGUISH)) {
            player.level().setBlockAndUpdate(new BlockPos(2, 99, 1), Blocks.NETHERRACK.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 100, 1), Blocks.FIRE.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-2, 100, 1),
                Blocks.CAMPFIRE.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
            player.level().setBlockAndUpdate(new BlockPos(0, 100, 2),
                Blocks.CANDLE.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
            target.igniteForSeconds(30);
        }
        if (kind.behaviors().contains(BrewBehavior.WASTE)) {
            player.level().setBlockAndUpdate(new BlockPos(-1, 100, 2), Blocks.OAK_LEAVES.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, 2), Blocks.BIRCH_LEAVES.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.HARVEST_CROPS)) {
            player.level().setBlockAndUpdate(new BlockPos(-1, 99, 2), Blocks.FARMLAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(1, 99, 2), Blocks.FARMLAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-1, 100, 2),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
            player.level().setBlockAndUpdate(new BlockPos(1, 100, 2),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2));
        }
        if (kind.behaviors().contains(BrewBehavior.TILL_SOIL)) {
            player.level().setBlockAndUpdate(new BlockPos(-1, 99, 2), Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(1, 99, 2), Blocks.GRASS_BLOCK.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 99, 0), Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 100, 0), Blocks.STONE.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.REVEAL)) {
            target.setInvisible(true);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 1200));
        }
        if (kind.behaviors().contains(BrewBehavior.BLIGHT)) {
            player.level().setBlockAndUpdate(new BlockPos(-2, 99, 2), Blocks.FARMLAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-2, 100, 2),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
            player.level().setBlockAndUpdate(new BlockPos(-1, 99, 2), Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-1, 100, 2), Blocks.DANDELION.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, 2), Blocks.OAK_LEAVES.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(1, 99, 2), Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(1, 100, 2), Blocks.OAK_SAPLING.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 99, 2), Blocks.FARMLAND.defaultBlockState());
        }
    }

    private static String fixtureDescription(final BrewKind kind) {
        if (kind.behaviors().contains(BrewBehavior.GROW)) return "Immature age-zero wheat on farmland.";
        if (kind.behaviors().contains(BrewBehavior.EXTINGUISH)) return "Supported fire, lit campfire, lit candle and a burning stationary cow.";
        if (kind.behaviors().contains(BrewBehavior.WASTE)) return "Two real leaf blocks, the Survival thrower and a stationary Zombie.";
        if (kind.behaviors().contains(BrewBehavior.HARVEST_CROPS)) return "Mature wheat and an age-two immature wheat control on farmland.";
        if (kind.behaviors().contains(BrewBehavior.TILL_SOIL)) return "Clear dirt, clear grass, and a stone-obstructed dirt control.";
        if (kind.behaviors().contains(BrewBehavior.REVEAL)) return "Stationary cow with entity invisibility plus the Invisibility effect.";
        if (kind.behaviors().contains(BrewBehavior.BLIGHT)) return "Mature wheat, flower, leaves, sapling, farmland and an eligible stationary cow.";
        throw new AssertionError("Unmapped nature behavior for " + kind.id());
    }

    private static String remaining(final BrewKind kind) {
        if (kind.behaviors().contains(BrewBehavior.GROW)) return "Other BonemealableBlock species, stochastic flower/tree shapes, radius boundary, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.EXTINGUISH)) return "Candle-cake variant, fire spread race, radius boundary, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.WASTE)) return "Other hostile/player distances, every leaf species, radius boundary, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.HARVEST_CROPS)) return "Age-less modded crop path, all crop loot tables, radius boundary, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.TILL_SOIL)) return "Every dirt tag member, hydration, radius boundary, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.REVEAL)) return "Invisible nonliving entities, already-visible control, full expiry, acquisition and persistence.";
        return "Every vegetation/soil/beast tag member, radius boundary, acquisition and persistence.";
    }

    private static Map<String, Object> tillDiagnostics(final ServerPlayer player) {
        final Map<String, Object> diagnostics = new LinkedHashMap<>();
        final List<Map<String, Object>> samples = new ArrayList<>();
        for (BlockPos pos : List.of(
            new BlockPos(-1, 99, 2), new BlockPos(1, 99, 2), new BlockPos(2, 99, 0)
        )) {
            final var state = player.level().getBlockState(pos);
            final var above = player.level().getBlockState(pos.above());
            final Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("position", coordinate(pos));
            sample.put("block", blockStateDescription(state));
            sample.put("in_minecraft_dirt_tag", state.is(BlockTags.DIRT));
            sample.put("above_position", coordinate(pos.above()));
            sample.put("above_block", blockStateDescription(above));
            sample.put("above_is_empty", player.level().isEmptyBlock(pos.above()));
            samples.add(sample);
        }
        diagnostics.put("samples", samples);
        diagnostics.put("first_farmland_in_volume", tillVolumeSnapshot(player).entrySet().stream()
            .filter(entry -> entry.getValue().startsWith("minecraft:farmland"))
            .findFirst().map(entry -> Map.of("position", entry.getKey(), "state", entry.getValue()))
            .orElse(Map.of("status", "NONE")));
        return diagnostics;
    }

    private static Map<String, String> tillVolumeSnapshot(final ServerPlayer player) {
        final Map<String, String> states = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-4, 97, -4), new BlockPos(4, 103, 4))) {
            states.put(coordinate(pos), blockStateDescription(player.level().getBlockState(pos)));
        }
        return states;
    }

    private static Map<String, Object> firstTillChange(
        final ServerPlayer player,
        final Map<String, String> before
    ) {
        for (var entry : tillVolumeSnapshot(player).entrySet()) {
            final String oldState = before.get(entry.getKey());
            if (!entry.getValue().equals(oldState)) {
                final Map<String, Object> change = new LinkedHashMap<>();
                change.put("position", entry.getKey());
                change.put("before", oldState == null ? "NOT_SNAPSHOTTED" : oldState);
                change.put("after", entry.getValue());
                return change;
            }
        }
        return Map.of("status", "NO_BLOCK_CHANGED_IN_VOLUME");
    }

    private static String coordinate(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String blockStateDescription(final net.minecraft.world.level.block.state.BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state.getValues().toList();
    }

    private static int cropYield(final ServerPlayer player) {
        final int loose = player.level().getEntitiesOfClass(ItemEntity.class, AREA, entity ->
            entity.getItem().is(Items.WHEAT) || entity.getItem().is(Items.WHEAT_SEEDS)
        ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        int collected = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS)) collected += stack.getCount();
        }
        return loose + collected;
    }
    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final var candidate = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
        check(candidate.isPresent(), "Player books must index canonical nature-brew guide " + section);
        final ManualProfile profile = candidate.orElseThrow();
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-75));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private double walk(final ClientGameTestContext context, final int ticks) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        final Vec3 before = serverValue(Entity::position);
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { context.waitTicks(ticks); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); }
        return Math.sqrt(serverValue(player -> player.position().subtract(before).horizontalDistanceSqr()));
    }

    private static void resetPosition(final ServerPlayer player) {
        player.teleportTo(0.5, 100, 0.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private static List<BlockPos> positions() {
        return BlockPos.betweenClosedStream(new BlockPos(-8, 99, -8), new BlockPos(8, 108, 8)).map(BlockPos::immutable).toList();
    }

    private static List<BlockPos> blocks(final ServerPlayer player, final Block block) {
        return positions().stream().filter(pos -> player.level().getBlockState(pos).is(block)).toList();
    }

    private static List<String> blockCoordinates(final ServerPlayer player, final Block block) {
        return blocks(player, block).stream().map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()).toList();
    }

    private static long sourceFluids(final ServerPlayer player) {
        return positions().stream().filter(pos -> player.level().getFluidState(pos).isSource()
            && player.level().getFluidState(pos).getType() == ModFluids.EROSION_SOURCE.get()).count();
    }

    private static final class ImpactObservation {
        private final ServerPlayer player;
        private final Mob target;
        private final Identifier item;
        private final float initialHealth;
        private final Set<String> projectileIds = new LinkedHashSet<>();
        private int projectilesPresent;
        private int observedTicks;
        private int maximumFrozen;
        private int maximumFireTicks;
        private int maximumSlownessAmplifier = -1;
        private int maximumErosion;
        private int maximumSnowTrail;
        private int maximumObsidianDrops;
        private Float firstErosionHealth;
        private int firstErosionWear;
        private String lastProjectilePosition;

        private ImpactObservation(final ServerPlayer player, final Mob target, final Identifier item) {
            this.player = player;
            this.target = target;
            this.item = item;
            initialHealth = target.getHealth();
        }

        private void tick() {
            observedTicks++;
            final List<AbstractThrownPotion> projectiles = player.level().getEntitiesOfClass(AbstractThrownPotion.class, AREA,
                potion -> potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)));
            projectilesPresent = projectiles.size();
            for (AbstractThrownPotion projectile : projectiles) {
                projectileIds.add(projectile.getUUID().toString());
                lastProjectilePosition = projectile.position().toString();
            }
            maximumFrozen = Math.max(maximumFrozen, target.getTicksFrozen());
            maximumFireTicks = Math.max(maximumFireTicks, target.getRemainingFireTicks());
            final var slow = target.getEffect(MobEffects.SLOWNESS);
            if (slow != null) maximumSlownessAmplifier = Math.max(maximumSlownessAmplifier, slow.getAmplifier());
            final int erosion = BrewMarkerState.remainingTicks(target, BrewMarkerKind.EROSION);
            maximumErosion = Math.max(maximumErosion, erosion);
            maximumSnowTrail = Math.max(maximumSnowTrail, BrewMarkerState.remainingTicks(target, BrewMarkerKind.SNOW_TRAIL));
            if (erosion > 0 && firstErosionHealth == null) {
                firstErosionHealth = target.getHealth();
                firstErosionWear = target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue();
            }
            final int drops = player.level().getEntitiesOfClass(ItemEntity.class, AREA,
                entity -> entity.getItem().is(Blocks.OBSIDIAN.asItem())).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            maximumObsidianDrops = Math.max(maximumObsidianDrops, drops);
        }

        private Map<String, Object> report() {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("observed_server_ticks", observedTicks);
            result.put("native_projectile_uuids", projectileIds);
            result.put("projectiles_still_present", projectilesPresent);
            result.put("last_observed_projectile_position", lastProjectilePosition);
            result.put("target_initial_health", initialHealth);
            result.put("target_current_health", target.getHealth());
            result.put("target_alive", target.isAlive());
            result.put("maximum_frozen_ticks", maximumFrozen);
            result.put("maximum_fire_ticks", maximumFireTicks);
            result.put("maximum_slowness_amplifier", maximumSlownessAmplifier);
            result.put("maximum_erosion_marker_ticks", maximumErosion);
            result.put("first_erosion_health", firstErosionHealth);
            result.put("first_erosion_helmet_wear", firstErosionWear);
            result.put("current_helmet_wear", target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
            result.put("maximum_obsidian_item_drop_count", maximumObsidianDrops);
            result.put("maximum_snow_trail_ticks", maximumSnowTrail);
            return result;
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
        report.put("class_sha256", Map.of("test", classHash(BrewNatureAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-nature-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-nature-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
