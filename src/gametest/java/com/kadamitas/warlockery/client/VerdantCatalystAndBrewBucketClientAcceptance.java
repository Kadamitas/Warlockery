package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.VerdantCatalystItem;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Native Verdant Catalyst transformations (ordinary and prime, Overworld and Spirit World) and the
 * Colored Brew Water Bucket pour/collect/cauldron-tank round trip, observed without outcome injection.
 */
public final class VerdantCatalystAndBrewBucketClientAcceptance implements FabricClientGameTest {
    private static final List<String> SCENARIOS = List.of(
        "verdant_catalyst", "verdant_catalyst_prime", "verdant_catalyst_spirit_world", "colored_brew_water_bucket");
    /** Mirrors VerdantCatalystItem.TRANSFORMATIONS (src/main/.../item/VerdantCatalystItem.java lines 18-21). */
    private static final Set<String> OVERWORLD_PLANTS = Set.of("warlockery:embermoss", "warlockery:glintweed",
        "warlockery:leapinglily", "warlockery:spanishmoss", "warlockery:hex_sapling", "warlockery:hex_leaves",
        "warlockery:bramble", "warlockery:bloodrose", "warlockery:crittersnare", "warlockery:grassper", "warlockery:somniancotton");
    private static final String NETHER_WART = "minecraft:nether_wart";
    private static final int OVERWORLD_SAMPLES = 40;
    private static final int SPIRIT_SAMPLES = 60;
    private static final Vec3 START = new Vec3(.5, 100, -1.5);
    private static final BlockPos SAMPLE = new BlockPos(0, 100, 1);
    private static final BlockPos PIT = new BlockPos(0, 99, 3);
    private static final BlockPos WATER_PIT = new BlockPos(3, 99, 3);
    private static final BlockPos CAULDRON = new BlockPos(-3, 100, 1);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private int overworldNetherWart = -1;
    private String scenario = "setup";
    private final List<Map<String, Object>> aimAttempts = new ArrayList<>();

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("verdant-catalyst-and-brew-bucket").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            final String configured = System.getProperty("warlockery.verdantScenarioIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(SCENARIOS) : Arrays.stream(configured.split(","))
                .map(String::strip).collect(Collectors.toSet());
            check(!requested.isEmpty() && SCENARIOS.containsAll(requested), "Requested scenarios must exist: " + configured);
            for (String id : SCENARIOS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("not_run", notRun(id));
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : SCENARIOS) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                scenario = id; row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        switch (id) {
                            case "verdant_catalyst" -> ordinaryCatalyst(context, row);
                            case "verdant_catalyst_prime" -> primeCatalyst(context, row);
                            case "verdant_catalyst_spirit_world" -> spiritCatalyst(context, row);
                            case "colored_brew_water_bucket" -> brewBucket(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        write(false);
                    }
                } catch (Throwable failure) { fail(id, row, failure); }
                finally { world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_VERDANT_CATALYST_AND_BREW_BUCKET_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Verdant catalyst / brew bucket evidence: " + evidence, failure);
        }
    }

    private static List<String> notRun(final String id) {
        return switch (id) {
            case "verdant_catalyst", "verdant_catalyst_prime" -> List.of("crafting/cauldron acquisition",
                "creative-mode non-consumption", "every random plant outcome individually", "leaves decay after transformation",
                "save/reload persistence");
            case "verdant_catalyst_spirit_world" -> List.of("dream entry through Sleeping Brew/Apple (player is staged into the real dimension)",
                "prime catalyst inside the Spirit World", "carry-home rules for catalysts", "exact 1/12 distribution");
            default -> List.of("cauldron brewing of the bucket (covered natively by MachineWalkthroughAcceptance cauldron representative)",
                "a machine working that requests Colored Brew Water", "flow/spread behavior of the source",
                "mixture components/effects: the product models this bucket as a plain fluid bucket with no stored mixture data");
        };
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20); player.clearFire();
            platform(player.level());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world; two-block-thick bedrock platform (y98-99) with walls. Target blocks, fluids and the cauldron are staged; every catalyst use, bucket pour/collect and tank transfer is a native right-click. No transformation, fluid or tank state is injected.");
    }

    private static void platform(final net.minecraft.world.level.Level level) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-10, 97, -8), new BlockPos(10, 108, 10))) {
            final boolean wall = pos.getY() <= 101 && (pos.getX() == -10 || pos.getX() == 10 || pos.getZ() == -8 || pos.getZ() == 10);
            level.setBlockAndUpdate(pos, pos.getY() <= 99 || wall ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- ordinary catalyst

    private void ordinaryCatalyst(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("ingredient_verdant_catalyst"), row);
        final Item catalyst = item("ingredient_verdant_catalyst");
        // Every target has its own X column on one row (z=1) so nothing stands between the player and the aimed block.
        final BlockPos flower = new BlockPos(0, 100, 1), sapling = new BlockPos(2, 100, 1), leaves = new BlockPos(-2, 100, 1);
        final BlockPos grass = new BlockPos(4, 100, 1), dirt = new BlockPos(-4, 100, 1), wheat = new BlockPos(6, 100, 1);
        final BlockPos stone = new BlockPos(-6, 100, 1), control = new BlockPos(8, 100, 1);
        server(player -> {
            final var level = player.level();
            level.setBlockAndUpdate(flower, Blocks.DANDELION.defaultBlockState());
            level.setBlockAndUpdate(sapling, Blocks.OAK_SAPLING.defaultBlockState());
            level.setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            level.setBlockAndUpdate(grass, Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(wheat.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(wheat, Blocks.WHEAT.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(control, Blocks.POPPY.defaultBlockState());
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        supply(context, 0, new ItemStack(catalyst, 3));
        final Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("flower", transform(context, flower, null, catalyst, 2, OVERWORLD_PLANTS));
        accepted.put("sapling", transform(context, sapling, null, catalyst, 1, OVERWORLD_PLANTS));
        accepted.put("leaves", transform(context, leaves, null, catalyst, 0, OVERWORLD_PLANTS));
        row.put("accepted_targets", accepted); row.put("accepted_status", "PASSED");
        screenshot(context, "verdant-catalyst-accepted-targets");
        supply(context, 0, new ItemStack(catalyst, 1));
        final Map<String, Object> refused = new LinkedHashMap<>();
        refused.put("grass_block", refuse(context, grass, null, catalyst, 1));
        refused.put("dry_dirt", refuse(context, dirt, null, catalyst, 1));
        refused.put("wheat_crop", refuse(context, wheat, null, catalyst, 1));
        refused.put("stone", refuse(context, stone, null, catalyst, 1));
        row.put("refused_targets", refused); row.put("refusal_status", "PASSED");
        check(serverValue(player -> player.level().getBlockState(control).is(Blocks.POPPY)), "Untreated control flower remains a flower");
        row.put("control", "Untreated poppy at " + control + " stays a poppy through every use");
        screenshot(context, "verdant-catalyst-refusals");
        final Map<String, Integer> distribution = sample(context, catalyst, OVERWORLD_SAMPLES, row);
        overworldNetherWart = distribution.getOrDefault(NETHER_WART, 0);
        check(distribution.keySet().stream().allMatch(OVERWORLD_PLANTS::contains), "Overworld results stay inside the eleven documented plants: " + distribution);
        check(overworldNetherWart == 0, "Overworld never yields Nether Wart");
        row.put("overworld_distribution", distribution); row.put("dimension_status", "PASSED");
        row.put("verified", "Native catalyst use replaced a flower, sapling and leaf block with documented magical plants, consuming one catalyst each; grass, dry dirt, a wheat crop and stone were refused without consumption; " + OVERWORLD_SAMPLES + " Overworld samples never produced Nether Wart.");
        screenshot(context, "verdant-catalyst-sampling");
    }

    // ---------------------------------------------------------------- prime catalyst

    private void primeCatalyst(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("ingredient_verdant_catalyst_prime"), row);
        final Item prime = item("ingredient_verdant_catalyst_prime");
        // One row (z=1), distinct X per target; the two dirt holes sit in the floor at y=98 under x=-2 (wet) and x=-4 (dry).
        final BlockPos grass = new BlockPos(0, 100, 1), mycelium = new BlockPos(2, 100, 1);
        final BlockPos wetDirt = new BlockPos(-2, 98, 1), dryDirt = new BlockPos(-4, 98, 1);
        final BlockPos wheat = new BlockPos(4, 100, 1), flower = new BlockPos(6, 100, 1), stone = new BlockPos(-6, 100, 1);
        final BlockPos control = new BlockPos(-8, 100, 1);
        server(player -> {
            final var level = player.level();
            level.setBlockAndUpdate(grass, Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(mycelium, Blocks.MYCELIUM.defaultBlockState());
            level.setBlockAndUpdate(wetDirt, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(wetDirt.above(), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(dryDirt, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(dryDirt.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(wheat.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(wheat, Blocks.WHEAT.defaultBlockState());
            level.setBlockAndUpdate(flower, Blocks.DANDELION.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(control, Blocks.GRASS_BLOCK.defaultBlockState());
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        check(serverValue(player -> player.level().getFluidState(wetDirt.above()).is(net.minecraft.tags.FluidTags.WATER)
            && player.level().getBlockState(dryDirt.above()).isAir()), "Water sits immediately above the wet dirt and only there");
        supply(context, 0, new ItemStack(prime, 5));
        final Map<String, Object> soil = new LinkedHashMap<>();
        soil.put("grass_to_mycelium", transform(context, grass, null, prime, 4, Set.of("minecraft:mycelium")));
        soil.put("mycelium_to_grass", transform(context, mycelium, null, prime, 3, Set.of("minecraft:grass_block")));
        position(context, new Vec3(-1.5, 100, .5));
        soil.put("wet_dirt_to_clay", transform(context, wetDirt, new Vec3(-1.5, 98.9, 1.5), prime, 2, Set.of("minecraft:clay")));
        position(context, new Vec3(-3.5, 100, .5));
        soil.put("dry_dirt_refused", refuse(context, dryDirt, new Vec3(-3.5, 98.9, 1.5), prime, 2));
        row.put("soil_conversions", soil); row.put("soil_status", "PASSED");
        screenshot(context, "prime-catalyst-soil");
        final Map<String, Object> plants = new LinkedHashMap<>();
        plants.put("wheat_crop", transform(context, wheat, null, prime, 1, OVERWORLD_PLANTS));
        plants.put("flower", transform(context, flower, null, prime, 0, OVERWORLD_PLANTS));
        supply(context, 0, new ItemStack(prime, 1));
        plants.put("stone_refused", refuse(context, stone, null, prime, 1));
        row.put("plant_conversions", plants); row.put("plant_status", "PASSED");
        check(serverValue(player -> player.level().getBlockState(control).is(Blocks.GRASS_BLOCK)), "Untreated control grass block stays grass");
        row.put("control", "Untreated grass block at " + control + " remains grass; dry dirt keeps its catalyst");
        row.put("verified", "Native prime uses turned grass into mycelium, mycelium into grass and water-covered dirt into clay, consuming one catalyst each; dry dirt and stone were refused without consumption; a wheat crop and a flower became documented magical plants.");
        screenshot(context, "prime-catalyst-plants");
    }

    // ---------------------------------------------------------------- spirit world

    private void spiritCatalyst(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("ingredient_verdant_catalyst", "spirit_world_harvest"), row);
        server(player -> {
            final ServerLevel destination = player.level().getServer().getLevel(SpiritWorldRuntime.SPIRIT_WORLD);
            check(destination != null, "The real Spirit World dimension is loaded");
            platform(destination);
            check(player.teleportTo(destination, START.x, START.y, START.z, Set.of(), 0, 0, true), "Fixture moves the player into the real Spirit World dimension");
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets(); context.waitTicks(5);
        server(player -> { platform(player.level()); player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets(); context.waitTicks(5);
        check(serverValue(player -> SpiritWorldRuntime.isSpiritWorld(player.level())), "Player stands in warlockery:spirit_world");
        row.put("dimension_fixture", "Player staged directly into the loaded warlockery:spirit_world level (dream entry is a separate contract); platform re-staged there.");
        row.put("dimension", serverValue(player -> player.level().dimension().identifier().toString()));
        final Map<String, Integer> distribution = sample(context, item("ingredient_verdant_catalyst"), SPIRIT_SAMPLES, row);
        final Set<String> allowed = new java.util.HashSet<>(OVERWORLD_PLANTS); allowed.add(NETHER_WART);
        check(distribution.keySet().stream().allMatch(allowed::contains), "Spirit World results stay inside the twelve documented options: " + distribution);
        final int wart = distribution.getOrDefault(NETHER_WART, 0);
        row.put("spirit_distribution", distribution); row.put("nether_wart_observed", wart);
        row.put("overworld_nether_wart_control", overworldNetherWart < 0 ? "verdant_catalyst scenario not run in this session" : overworldNetherWart);
        check(wart > 0, "Spirit World adds Nether Wart: expected at least one of " + SPIRIT_SAMPLES + " native uses (miss probability (11/12)^" + SPIRIT_SAMPLES + ")");
        if (overworldNetherWart >= 0) check(overworldNetherWart == 0, "Overworld control never produced Nether Wart");
        row.put("verified", SPIRIT_SAMPLES + " native catalyst uses on re-staged flowers inside the real Spirit World produced " + wart + " Nether Wart among the twelve documented outcomes; the Overworld control produced none.");
        screenshot(context, "spirit-world-catalyst-sampling");
    }

    private Map<String, Integer> sample(final ClientGameTestContext context, final Item catalyst, final int samples,
        final Map<String, Object> row) throws Exception {
        supply(context, 0, new ItemStack(catalyst, 64));
        final Map<String, Integer> distribution = new TreeMap<>();
        final List<String> sequence = new ArrayList<>();
        aimAttempts.clear();
        for (int index = 0; index < samples; index++) {
            server(player -> level(player).setBlockAndUpdate(SAMPLE, Blocks.DANDELION.defaultBlockState()));
            world.getConnection().waitForClientboundPackets(); context.waitTicks(1);
            final int expected = 64 - index - 1;
            final String produced = transform(context, SAMPLE, null, catalyst, expected, null);
            distribution.merge(produced, 1, Integer::sum); sequence.add(produced);
            if (index % 10 == 9) { row.put("sampling_progress", index + 1); row.put("sampling_sequence", List.copyOf(sequence)); write(false); }
        }
        check(serverValue(player -> count(player, catalyst) == 64 - samples), "Exactly one catalyst consumed per successful native transformation");
        row.put("sampling_sequence", sequence); row.put("samples", samples); row.put("aim_attempts", List.copyOf(aimAttempts));
        return distribution;
    }

    /** Native use on a target; returns the replacement block id and checks exact consumption. */
    private String transform(final ClientGameTestContext context, final BlockPos target, final Vec3 aim, final Item catalyst,
        final int remaining, final Set<String> allowed) {
        final BlockState before = serverValue(player -> level(player).getBlockState(target));
        use(context, aim == null ? approach(context, target) : aim, target);
        // A single native click can be dropped by the use cooldown after a long sampling run; one honest retry before failing.
        for (int attempt = 0; attempt < 2 && serverValue(player -> level(player).getBlockState(target).equals(before)); attempt++) {
            for (int tick = 0; tick < 20 && serverValue(player -> level(player).getBlockState(target).equals(before)); tick += 2) context.waitTicks(2);
            if (attempt == 0 && serverValue(player -> level(player).getBlockState(target).equals(before))) {
                context.waitTicks(6);
                use(context, aim == null ? approach(context, target) : aim, target);
            }
        }
        await(context, player -> !level(player).getBlockState(target).equals(before), 10, "Native catalyst use replaces the clicked block " + target + " (" + before + ")");
        final String produced = serverValue(player -> BuiltInRegistries.BLOCK.getKey(level(player).getBlockState(target).getBlock()).toString());
        if (allowed != null) check(allowed.contains(produced), "Replacement " + produced + " is a documented outcome for " + target);
        check(serverValue(player -> count(player, catalyst) == remaining), "Successful transformation consumed exactly one catalyst; remaining=" + remaining);
        return produced;
    }

    private Map<String, Object> refuse(final ClientGameTestContext context, final BlockPos target, final Vec3 aim, final Item catalyst, final int remaining) {
        final BlockState before = serverValue(player -> level(player).getBlockState(target));
        use(context, aim == null ? approach(context, target) : aim, target); context.waitTicks(8);
        final BlockState after = serverValue(player -> level(player).getBlockState(target));
        check(before.equals(after), "Refused target keeps its block: " + target + " " + before + " -> " + after);
        check(serverValue(player -> count(player, catalyst) == remaining), "Refusal consumes no catalyst at " + target);
        return Map.of("block", BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString(), "unchanged", true, "catalysts_remaining", remaining);
    }

    // ---------------------------------------------------------------- brew bucket

    private void brewBucket(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("bucketbrew"), row);
        final Item bucketBrew = item("bucketbrew");
        final Block brewLiquid = ModBlocks.ALL.get("brewliquid").get();
        server(player -> {
            player.level().setBlockAndUpdate(PIT, Blocks.AIR.defaultBlockState());
            player.level().setBlockAndUpdate(WATER_PIT, Blocks.AIR.defaultBlockState());
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(2);
        // Pour: aim at the pit's far inner wall so the source lands inside the one-block pit.
        position(context, new Vec3(.5, 100, 2.5));
        supply(context, 0, new ItemStack(bucketBrew));
        use(context, new Vec3(.5, 99.5, 4.0), PIT.south());
        await(context, player -> player.level().getBlockState(PIT).is(brewLiquid), 20, "Native pour places a Colored Brew Water source in the pit");
        check(serverValue(player -> player.level().getFluidState(PIT).getType() == ModFluids.COLORED_BREW_WATER_SOURCE.get()
            && player.getMainHandItem().is(Items.BUCKET)), "Poured fluid is the mod's source and the hand keeps an ordinary empty bucket");
        row.put("poured", Map.of("block", "warlockery:brewliquid", "fluid", BuiltInRegistries.FLUID.getKey(ModFluids.COLORED_BREW_WATER_SOURCE.get()).toString()));
        row.put("pour_status", "PASSED");
        // Identity control: an ordinary Water Bucket poured the same way creates vanilla water, not brew liquid.
        position(context, new Vec3(3.5, 100, 2.5));
        supply(context, 1, new ItemStack(Items.WATER_BUCKET));
        use(context, new Vec3(3.5, 99.5, 4.0), WATER_PIT.south());
        await(context, player -> player.level().getBlockState(WATER_PIT).is(Blocks.WATER), 20, "Control Water Bucket pours vanilla water");
        check(serverValue(player -> !player.level().getBlockState(WATER_PIT).is(brewLiquid) && player.level().getBlockState(PIT).is(brewLiquid)),
            "Control water and brew liquid remain distinct blocks");
        screenshot(context, "brew-bucket-poured-and-control-water");
        // No contact effect: stand inside the brew source for three seconds.
        server(player -> { player.removeAllEffects(); player.setHealth(20); player.clearFire(); });
        position(context, new Vec3(.5, 99, 3.5));
        context.waitTicks(60);
        final Map<String, Object> contact = serverValue(player -> Map.of("in_fluid", player.level().getFluidState(player.blockPosition()).getType() == ModFluids.COLORED_BREW_WATER_SOURCE.get(),
            "effects", player.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName()).toList(),
            "on_fire", player.isOnFire(), "health", player.getHealth()));
        row.put("contact_observation", contact);
        check(Boolean.TRUE.equals(contact.get("in_fluid")), "Player actually stood inside the poured brew source");
        check(((List<?>) contact.get("effects")).isEmpty() && !Boolean.TRUE.equals(contact.get("on_fire")) && (float) contact.get("health") == 20F,
            "Standing in Colored Brew Water applies no effect, fire or damage as the book states");
        row.put("contact_status", "PASSED");
        // Collect: an empty bucket on the source returns the exact bucket item and empties the pit.
        position(context, new Vec3(.5, 100, 2.5));
        sync(context, 0);
        check(serverValue(player -> player.getMainHandItem().is(Items.BUCKET)), "Empty bucket selected for collection");
        use(context, new Vec3(.5, 99.4, 3.5), PIT.below());
        await(context, player -> player.getMainHandItem().is(bucketBrew), 20, "Native empty-bucket use collects the source back as a Colored Brew Water Bucket");
        check(serverValue(player -> player.level().getBlockState(PIT).isAir()), "Collected pit is empty again");
        row.put("collect_status", "PASSED");
        screenshot(context, "brew-bucket-collected");
        // Cauldron tank round trip: bucket -> tank -> bucket with fluid identity preserved.
        position(context, new Vec3(-1.5, 100, -.5));
        supply(context, 2, new ItemStack(item("cauldron")));
        use(context, new Vec3(CAULDRON.getX() + .5, CAULDRON.getY() - .001, CAULDRON.getZ() + .5), CAULDRON.below());
        await(context, player -> player.level().getBlockEntity(CAULDRON) instanceof MagicMachineBlockEntity, 20, "Native placement creates the cauldron");
        sync(context, 0);
        check(serverValue(player -> player.getMainHandItem().is(bucketBrew) && machine(player).getFluidAmount() == 0), "Bucket in hand and an empty tank before transfer");
        use(context, Vec3.atCenterOf(CAULDRON), CAULDRON);
        await(context, player -> machine(player).getFluidAmount() == 1000, 20, "Native bucket use moves exactly 1000 mB into the cauldron tank");
        check(serverValue(player -> tankFluid(player) == ModFluids.COLORED_BREW_WATER_SOURCE.get() && player.getMainHandItem().is(Items.BUCKET)),
            "Tank holds Colored Brew Water (identity preserved) and returns an empty bucket");
        row.put("tank_in", Map.of("mB", 1000, "fluid", "warlockery:colored_brew_water"));
        closeScreen(context);
        use(context, Vec3.atCenterOf(CAULDRON), CAULDRON);
        await(context, player -> player.getMainHandItem().is(bucketBrew), 20, "Native empty-bucket use drains the tank back into a Colored Brew Water Bucket");
        check(serverValue(player -> machine(player).getFluidAmount() == 0), "Tank is empty after collection");
        closeScreen(context);
        row.put("tank_out", Map.of("mB", 0, "item", "warlockery:bucketbrew")); row.put("tank_status", "PASSED");
        row.put("verified", "Native pour created the mod fluid (control water stayed vanilla), three seconds of contact produced no effect/fire/damage, native collection returned the identical bucket item, and a placed cauldron accepted and returned the fluid through its tank with identity preserved.");
        screenshot(context, "brew-bucket-cauldron-round-trip");
    }

    /** The level under test: the real Spirit World level for the spirit scenario, otherwise the player's level. */
    private ServerLevel level(final ServerPlayer player) {
        if (!"verdant_catalyst_spirit_world".equals(scenario)) return player.level();
        final ServerLevel spirit = player.level().getServer().getLevel(SpiritWorldRuntime.SPIRIT_WORLD);
        check(spirit != null && player.level() == spirit, "Player is in the Spirit World level while sampling; actual=" + player.level().dimension().identifier());
        return spirit;
    }
    private static MagicMachineBlockEntity machine(final ServerPlayer player) { return (MagicMachineBlockEntity) player.level().getBlockEntity(CAULDRON); }
    private static net.minecraft.world.level.material.Fluid tankFluid(final ServerPlayer player) {
        final var tank = (net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage) field(machine(player), "fluidStorage");
        return tank.getResource().getFluid();
    }

    // ---------------------------------------------------------------- shared helpers

    private void readGuides(final ClientGameTestContext context, final List<String> requested, final Map<String, Object> row) throws Exception {
        final List<Map<String, Object>> guides = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String section : requested) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
            if (found.isEmpty()) { missing.add(section); continue; }
            final var profile = found.orElseThrow();
            supply(context, 0, new ItemStack(item(profile.id())));
            context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, section);
            final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            check(!body.isBlank() && pages > 0, "Guide " + section + " has readable pages");
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected),
                    "Native book paging visits each page of " + section);
                screenshot(context, "guide-" + section + "-" + page);
            }
            guides.add(Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages));
            closeScreen(context);
        }
        row.put("guides_read", guides); row.put("missing_indexed_guidance", missing); row.put("guidance_complete", missing.isEmpty());
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
    }

    private void supply(final ClientGameTestContext context, final int slot, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(slot, stack); player.inventoryMenu.broadcastChanges(); }); sync(context, slot);
    }
    private void sync(final ClientGameTestContext context, final int slot) {
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1 + slot); context.waitTicks(2);
    }
    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.teleportTo(point.x, point.y, point.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        look(context, point);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets intended block " + expected + "; actual=" + describeHit(context));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }
    private static String describeHit(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if (client.hitResult instanceof BlockHitResult hit)
                return hit.getType() + " " + hit.getBlockPos() + " face=" + hit.getDirection() + " at=" + hit.getLocation() + " block=" + client.level.getBlockState(hit.getBlockPos());
            if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)
                return "ENTITY " + BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()) + " uuid=" + hit.getEntity().getStringUUID()
                    + " at=" + hit.getEntity().position() + " name=" + hit.getEntity().getName().getString();
            return String.valueOf(client.hitResult);
        });
    }
    /**
     * Stand 1.5 blocks north on the target's own X and aim down (~30 degrees) at the top centre of the block's actual
     * offset-aware outline, so the ray drops onto the target instead of skimming neighbours; waits until the client
     * renders the staged block and re-aims if the pointer disagrees.
     */
    private Vec3 approach(final ClientGameTestContext context, final BlockPos target) {
        position(context, new Vec3(target.getX() + .5, 100, target.getZ() - 1.5));
        final List<String> bystanders = serverValue(player -> {
            final ServerLevel level = level(player);
            final List<String> found = new ArrayList<>();
            for (Entity entity : level.getEntities(player, new AABB(target).inflate(4.0), entity -> !(entity instanceof ServerPlayer))) {
                found.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " at " + entity.position() + " name=" + entity.getName().getString());
                entity.discard();
            }
            return found;
        });
        if (!bystanders.isEmpty()) { world.getConnection().waitForClientboundPackets(); context.waitTicks(2); }
        final BlockState staged = serverValue(player -> level(player).getBlockState(target));
        context.waitFor(client -> client.level.getBlockState(target).equals(staged), 40);
        // Candidates from the outline of the level under test: lower half of a short plant, then its top, then the block centre.
        final List<Vec3> candidates = serverValue(player -> {
            final ServerLevel level = level(player);
            final var shape = level.getBlockState(target).getShape(level, target);
            if (shape.isEmpty()) return List.of(Vec3.atCenterOf(target));
            final var box = shape.bounds();
            final double x = target.getX() + (box.minX + box.maxX) / 2, z = target.getZ() + (box.minZ + box.maxZ) / 2;
            return List.of(new Vec3(x, target.getY() + box.minY + .15, z), new Vec3(x, target.getY() + box.maxY - .05, z), Vec3.atCenterOf(target));
        });
        Vec3 aim = candidates.getFirst();
        for (int attempt = 0; attempt < candidates.size(); attempt++) {
            aim = candidates.get(attempt);
            look(context, aim);
            final boolean hit = context.computeOnClient(client -> client.hitResult instanceof BlockHitResult result && result.getBlockPos().equals(target));
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("target", target.toString()); record.put("staged", staged.toString()); record.put("attempt", attempt);
            record.put("aim", aim.toString()); record.put("player", serverValue(player -> player.position() + " in " + player.level().dimension().identifier()));
            record.put("hit", hit); record.put("pointer", describeHit(context)); record.put("discarded_bystanders", bystanders);
            if (aimAttempts.size() < 400) aimAttempts.add(record);
            if (hit) break;
            context.waitTicks(3);
        }
        return aim;
    }
    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) { context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTicks(3); }
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
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
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, scenario + "-" + name, screenshots); }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", stack(failure)); failures.add(id + ": " + failure);
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("all_selected_scenarios_passed", finished && failures.isEmpty()
            && results.values().stream().noneMatch(row -> "NOT_RUN".equals(row.get("status")) || "RUNNING".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key catalyst, bucket and cauldron interactions, normal server ticks. No transformation, fluid or tank outcome is injected.");
        report.put("production_classes", Map.of("catalyst", VerdantCatalystItem.class.getName(), "cauldron", MagicMachineBlockEntity.class.getName()));
        report.put("scenarios", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("verdant-catalyst-and-brew-bucket.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("verdant-catalyst-and-brew-bucket.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
