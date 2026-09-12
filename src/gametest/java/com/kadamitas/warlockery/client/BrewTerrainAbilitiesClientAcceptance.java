package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModBlocks;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class BrewTerrainAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(
        BrewBehavior.FELL_LOGS, BrewBehavior.PRUNE_LEAVES, BrewBehavior.PLACE_LILIES,
        BrewBehavior.LEVEL_LAND, BrewBehavior.PULVERIZE_ROCK, BrewBehavior.RAISE_LAND,
        BrewBehavior.PLACE_THORNS, BrewBehavior.TRANSPOSE_ORES, BrewBehavior.DISSIPATE_GAS,
        BrewBehavior.PLACE_WATER, BrewBehavior.PART_WATER, BrewBehavior.PART_LAVA,
        BrewBehavior.PLANT_DROPS, BrewBehavior.SPROUT_BRANCHES, BrewBehavior.SUBSTITUTE_BLOCKS,
        BrewBehavior.SOLIDIFY_STONE, BrewBehavior.SOLIDIFY_DIRT, BrewBehavior.SOLIDIFY_SAND,
        BrewBehavior.SOLIDIFY_SANDSTONE, BrewBehavior.SOLIDIFY_EROSION
    );
    private static final AABB AREA = new AABB(-10, 97, -10, 11, 115, 11);
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
            .resolve("brew-terrain-abilities").resolve(UUID.randomUUID().toString());
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
            check(!ids.isEmpty(), "Actual registry must expose terrain-effect brews");
            for (BrewBehavior behavior : COVERED) {
                check(ids.stream().anyMatch(id -> ((BrewItem) BuiltInRegistries.ITEM.getValue(id))
                    .kind().behaviors().contains(behavior)), "Registry census must expose terrain behavior " + behavior);
            }
            final String configured = System.getProperty("warlockery.brewTerrainIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual terrain-family registry census");
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
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-9, 96, -9), new BlockPos(9, 111, 9))) {
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99
                    ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            resetPosition(player);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        final UUID target = serverValue(player -> {
            final Entity created = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(created instanceof Mob, "Terrain fixture must create its real living control target");
            final Mob cow = (Mob) created;
            cow.setPos(2.5, 100, 2.5);
            cow.setNoAi(true);
            player.level().addFreshEntity(cow);
            stageTerrainFixture(player, kind);
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
            observation = new ImpactObservation(player, cow, id);
            return cow.getUUID();
        });
        row.put("target_uuid", target.toString());
        row.put("fixture", fixtureDescription(kind));
        row.put("staged_prerequisites", "Fresh bedrock arena plus only the blocks, source fluids or loose plant/block item required by this behavior. One stationary Cow is present for thorn trapping. No resulting blocks, drops or effects are injected.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100; tick++) {
            if (serverValue(player -> !observation.projectileIds.isEmpty() && observation.projectilesPresent == 0)) break;
            context.waitTicks(1);
        }
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.projectilesPresent == 0),
            "One real owned thrown potion must exist and complete its native impact");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "One native throw consumes the supplied brew");
        context.waitTicks(2);
        assertTerrainOutcome(kind, target, row);
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(25));
        screenshot(context, id.getPath() + "-native-terrain-outcome");
    }

    private static void stageTerrainFixture(final ServerPlayer player, final BrewKind kind) {
        final var level = player.level();
        if (kind.behaviors().contains(BrewBehavior.FELL_LOGS)) {
            for (int y = 100; y <= 102; y++) level.setBlockAndUpdate(new BlockPos(1, y, 1), Blocks.OAK_LOG.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PRUNE_LEAVES)) {
            level.setBlockAndUpdate(new BlockPos(-1, 100, 1), Blocks.OAK_LEAVES.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 100, 1), Blocks.BIRCH_LEAVES.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PLACE_LILIES)) {
            level.setBlockAndUpdate(new BlockPos(-1, 99, 1), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.WATER.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.LEVEL_LAND)) {
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 100, 1), Blocks.DIRT.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PULVERIZE_ROCK)) {
            level.setBlockAndUpdate(new BlockPos(-1, 100, 1), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 100, 1), Blocks.DEEPSLATE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(0, 100, 2), Blocks.SANDSTONE.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.RAISE_LAND)) {
            level.setBlockAndUpdate(new BlockPos(-1, 99, 1), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PLACE_THORNS)) {
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(-1, 99, 1), Blocks.SAND.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(2, 99, 2), Blocks.DIRT.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.TRANSPOSE_ORES)) {
            level.setBlockAndUpdate(new BlockPos(4, 100, 0), Blocks.COAL_ORE.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.DISSIPATE_GAS)) {
            level.setBlockAndUpdate(new BlockPos(1, 100, 1), ModBlocks.ALL.get("brewgas").get().defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PART_WATER)) {
            level.setBlockAndUpdate(new BlockPos(-1, 99, 1), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.WATER.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PART_LAVA)) {
            level.setBlockAndUpdate(new BlockPos(-1, 99, 1), Blocks.LAVA.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 99, 1), Blocks.LAVA.defaultBlockState());
        }
        if (kind.behaviors().contains(BrewBehavior.PLANT_DROPS)) {
            level.setBlockAndUpdate(new BlockPos(-2, 99, 1), Blocks.DIRT.defaultBlockState());
            addDrop(player, new ItemStack(Items.DANDELION), -2.5, 100.2, 1.5);
        }
        if (kind.behaviors().contains(BrewBehavior.SUBSTITUTE_BLOCKS)) {
            level.setBlockAndUpdate(new BlockPos(1, 100, 1), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 100, 2), Blocks.STONE.defaultBlockState());
            addDrop(player, new ItemStack(Items.DIRT, 4), 2.5, 100.2, 1.5);
        }
        if (kind.behaviors().stream().anyMatch(BrewTerrainAbilitiesClientAcceptance::isSolidify)) {
            level.setBlockAndUpdate(new BlockPos(-1, 100, 1),
                ModFluids.HOLLOW_TEARS_SOURCE.get().defaultFluidState().createLegacyBlock());
            level.setBlockAndUpdate(new BlockPos(1, 100, 1),
                ModFluids.HOLLOW_TEARS_SOURCE.get().defaultFluidState().createLegacyBlock());
        }
        if (kind.behaviors().contains(BrewBehavior.SOLIDIFY_EROSION)) {
            level.setBlockAndUpdate(new BlockPos(1, 103, 1),
                ModFluids.HOLLOW_TEARS_SOURCE.get().defaultFluidState().createLegacyBlock());
            for (int y = 100; y <= 102; y++) level.setBlockAndUpdate(new BlockPos(1, y, 1), Blocks.DIRT.defaultBlockState());
        }
    }

    private static void addDrop(final ServerPlayer player, final ItemStack stack, final double x, final double y, final double z) {
        final ItemEntity drop = new ItemEntity(player.level(), x, y, z, stack);
        drop.setPickUpDelay(32_767);
        player.level().addFreshEntity(drop);
    }

    private void assertTerrainOutcome(final BrewKind kind, final UUID target, final Map<String, Object> row) {
        final List<String> checks = new ArrayList<>();
        checks.add("Exactly one native owned projectile impacted and consumed the supplied brew.");
        for (BrewBehavior behavior : kind.behaviors()) {
            if (!COVERED.contains(behavior)) continue;
            switch (behavior) {
                case FELL_LOGS -> check(serverValue(player -> countBlock(player, Blocks.OAK_LOG) == 0
                    && countAvailable(player, Items.OAK_LOG) >= 3), "Fell Logs must break the connected tree and create ordinary log drops");
                case PRUNE_LEAVES -> check(serverValue(player -> countBlock(player, Blocks.OAK_LEAVES) == 0
                    && countBlock(player, Blocks.BIRCH_LEAVES) == 0), "Prune Leaves must remove both staged leaf species");
                case PLACE_LILIES -> check(serverValue(player -> countBlock(player, Blocks.LILY_PAD) >= 2),
                    "Grow Lily must place lily pads above source water");
                case LEVEL_LAND -> check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 100, 1)).isAir()
                    && player.level().getBlockState(new BlockPos(1, 99, 1)).is(Blocks.DIRT)),
                    "Level Land must remove the staged terrain above the impact plane and preserve its base");
                case PULVERIZE_ROCK -> check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 100, 1)).is(Blocks.COBBLESTONE)
                    && player.level().getBlockState(new BlockPos(1, 100, 1)).is(Blocks.COBBLED_DEEPSLATE)
                    && player.level().getBlockState(new BlockPos(0, 100, 2)).is(Blocks.SAND)),
                    "Pulverize Rock must apply the actual stone, deepslate and sandstone transformations");
                case RAISE_LAND -> check(serverValue(player -> player.level().getBlockState(new BlockPos(-1, 100, 1)).is(Blocks.DIRT)
                    && player.level().getBlockState(new BlockPos(1, 100, 1)).is(Blocks.GRASS_BLOCK)),
                    "Raise Land must copy both staged terrain surfaces one block upward");
                case PLACE_THORNS -> check(serverValue(player -> countBlock(player, Blocks.SWEET_BERRY_BUSH) > 0
                    && countBlock(player, Blocks.CACTUS) >= 3
                    && player.level().getEntity(target) instanceof LivingEntity living
                    && living.hasEffect(MobEffects.SLOWNESS) && living.getEffect(MobEffects.SLOWNESS).getAmplifier() == 4),
                    "Thorns must grow berry/cactus hazards and apply its actual Slowness V trap");
                case TRANSPOSE_ORES -> check(serverValue(player -> player.level().getBlockState(new BlockPos(4, 100, 0)).is(Blocks.STONE)
                    && countBlock(player, Blocks.COAL_ORE) == 1
                    && !player.level().getBlockState(new BlockPos(4, 100, 0)).is(Blocks.COAL_ORE)),
                    "Transpose Ore must replace the source with stone and move the ore near impact");
                case DISSIPATE_GAS -> check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 100, 1)).isAir()),
                    "Dissipate Gas must remove the staged tagged gas block");
                case PLACE_WATER -> check(serverValue(player -> countSourceFluid(player, net.minecraft.world.level.material.Fluids.WATER) > 0),
                    "Endless Water must place real source water into replaceable spaces");
                case PART_WATER -> check(serverValue(player -> countBlock(player, Blocks.FROSTED_ICE) >= 2),
                    "Part Water must temporarily replace exposed source water with frosted ice");
                case PART_LAVA -> check(serverValue(player -> countBlock(player, Blocks.BASALT) >= 2),
                    "Part Lava must temporarily replace exposed source lava with basalt");
                case PLANT_DROPS -> check(serverValue(player -> countBlock(player, Blocks.DANDELION) >= 1
                    && countLoose(player, Items.DANDELION) == 0), "Planting must consume the loose flower item and plant its block");
                case SPROUT_BRANCHES -> check(serverValue(player -> countTagged(player, BlockTags.LOGS) > 0
                    && countBlock(player, ModBlocks.ALL.get("hex_leaves").get()) > 0),
                    "Sprouting must place a directed branch path and its foliage");
                case SUBSTITUTE_BLOCKS -> check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 100, 1)).is(Blocks.DIRT)
                    && player.level().getBlockState(new BlockPos(1, 100, 2)).is(Blocks.DIRT)
                    && countLoose(player, Items.DIRT) == 2), "Substitution must consume one offered block per matching replacement");
                case SOLIDIFY_STONE -> solidified(Blocks.STONE, "Stone");
                case SOLIDIFY_DIRT -> solidified(Blocks.DIRT, "Dirt");
                case SOLIDIFY_SAND -> solidified(Blocks.SAND, "Sand");
                case SOLIDIFY_SANDSTONE -> solidified(Blocks.SANDSTONE, "Sandstone");
                case SOLIDIFY_EROSION -> {
                    row.put("erosion_column_after", serverValue(player -> java.util.stream.IntStream.rangeClosed(99, 103)
                        .mapToObj(y -> y + ": " + player.level().getBlockState(new BlockPos(1, y, 1))).toList()));
                    check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 103, 1))
                    .getFluidState().is(com.kadamitas.warlockery.registry.WarlockeryTags.Fluids.HOLLOW_TEARS)
                    && java.util.stream.IntStream.rangeClosed(100, 102).allMatch(y -> {
                        final var state = player.level().getBlockState(new BlockPos(1, y, 1));
                        return state.isAir() || state.getFluidState().is(
                            com.kadamitas.warlockery.registry.WarlockeryTags.Fluids.HOLLOW_TEARS);
                    }) && player.level().getBlockState(new BlockPos(1, 99, 1)).is(Blocks.BEDROCK)),
                    "Solidify Erosion must preserve Hollow Tears while eroding its destructible column downward: "
                        + row.get("erosion_column_after"));
                }
                default -> throw new AssertionError("Unmapped terrain behavior " + behavior);
            }
            checks.add(behavior.name() + " actual block/item outcome observed after native impact.");
        }
        row.put("checks", checks);
        row.put("remaining", remaining(kind));
    }

    private void solidified(final Block expected, final String name) {
        check(serverValue(player -> countBlock(player, expected) >= 2
            && countSourceFluid(player, ModFluids.HOLLOW_TEARS_SOURCE.get()) == 0),
            "Solidify " + name + " must replace both Hollow Tears sources with " + name);
    }

    private static boolean isSolidify(final BrewBehavior behavior) {
        return behavior == BrewBehavior.SOLIDIFY_STONE || behavior == BrewBehavior.SOLIDIFY_DIRT
            || behavior == BrewBehavior.SOLIDIFY_SAND || behavior == BrewBehavior.SOLIDIFY_SANDSTONE;
    }

    private static String fixtureDescription(final BrewKind kind) {
        return "Behavior fixture for " + kind.behaviors().stream().filter(COVERED::contains).map(Enum::name).toList();
    }

    private static String remaining(final BrewKind kind) {
        if (kind.behaviors().contains(BrewBehavior.DISSIPATE_GAS)) return "Gas-delivery AreaEffectCloud removal and spectral-entity damage; additional gas tags; acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.PLACE_THORNS)) return "Toad-familiar enhanced height/cage; every support tag member; acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.TRANSPOSE_ORES)) return "Deepslate source replacement and every common ore; acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.LEVEL_LAND) || kind.behaviors().contains(BrewBehavior.RAISE_LAND)) return "Uneven multi-column boundary cases, block entities, every levelable terrain tag member, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.SUBSTITUTE_BLOCKS)) return "Replacement survival failures, block entities, mixed target types, 64-block/item cap, acquisition and persistence.";
        if (kind.behaviors().contains(BrewBehavior.SOLIDIFY_EROSION)) return "32-block depth cap, unbreakable/block-entity stop, multiple columns, acquisition and persistence.";
        return "Additional compatible block species, radius/cap boundary, acquisition recipe and save/reload persistence.";
    }

    private static long countBlock(final ServerPlayer player, final Block block) {
        return positions().stream().filter(pos -> player.level().getBlockState(pos).is(block)).count();
    }

    private static long countTagged(final ServerPlayer player, final net.minecraft.tags.TagKey<Block> tag) {
        return positions().stream().filter(pos -> player.level().getBlockState(pos).is(tag)).count();
    }

    private static int countLoose(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        return player.level().getEntitiesOfClass(ItemEntity.class, AREA, Entity::isAlive).stream()
            .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static int countAvailable(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        int count = countLoose(player, item);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static long countSourceFluid(final ServerPlayer player, final net.minecraft.world.level.material.Fluid fluid) {
        return positions().stream().filter(pos -> player.level().getFluidState(pos).isSource()
            && player.level().getFluidState(pos).getType() == fluid).count();
    }
    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final var candidate = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
        check(candidate.isPresent(), "Player books must index canonical terrain-brew guide " + section);
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

    private static List<BlockPos> positions() {
        return BlockPos.betweenClosedStream(new BlockPos(-8, 99, -8), new BlockPos(8, 108, 8)).map(BlockPos::immutable).toList();
    }

    private static final class ImpactObservation {
        private final ServerPlayer player;
        private final Mob target;
        private final Identifier item;
        private final Set<String> projectileIds = new LinkedHashSet<>();
        private int projectilesPresent;
        private int observedTicks;
        private String lastProjectilePosition;

        private ImpactObservation(final ServerPlayer player, final Mob target, final Identifier item) {
            this.player = player;
            this.target = target;
            this.item = item;
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
        }

        private Map<String, Object> report() {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("observed_server_ticks", observedTicks);
            result.put("native_projectile_uuids", projectileIds);
            result.put("projectiles_still_present", projectilesPresent);
            result.put("last_observed_projectile_position", lastProjectilePosition);
            result.put("target_uuid", target.getUUID().toString());
            result.put("target_alive", target.isAlive());
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
        report.put("class_sha256", Map.of("test", classHash(BrewTerrainAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-terrain-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-terrain-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
