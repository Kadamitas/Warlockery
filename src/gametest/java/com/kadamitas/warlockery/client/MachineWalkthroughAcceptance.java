package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class MachineWalkthroughAcceptance implements FabricClientGameTest {
    private static final BlockPos MACHINE = new BlockPos(0, 80, 0);
    private static final BlockPos ALTAR = MACHINE.offset(5, 0, 0);
    private static final Map<String, String> REPRESENTATIVES = Map.of(
        "alchemical_oven", "oven_fume_breath_of_the_goddess",
        "distillery", "distill_vitriol",
        "kettle", "kettle_brew_absorb_magic",
        "cauldron", "cauldron_colored_brew_water",
        "silvervat", "silver_vat_silver_dust",
        "spinningwheel", "spin_wool",
        "brazier", "brazier_summon_spectre"
    );
    private final List<String> screenshots = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private List<String> selectedScope = List.of();
    private final List<Map<String, Object>> recipeResults = new ArrayList<>();
    private volatile RecipeObservation observation;
    private int requiredRecipeCount;
    private List<String> representativeScope = List.of();
    private List<String> requestedRecipeIds = List.of();
    private int availableRecipeCount;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/new-player-audit"))
            .resolve("machines").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
                final RecipeObservation current = observation;
                if (current != null) current.tick();
            });
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            final Set<String> types = MachineProfiles.blockIds().stream()
                .map(MachineProfiles::forBlock).map(MachineProfile::recipeType).collect(Collectors.toSet());
            check(types.equals(REPRESENTATIVES.keySet()), "Every MachineProfiles recipe type has a walkthrough: " + types);
            final String requested = System.getProperty("warlockery.machineTypes", "");
            final Set<String> selected = requested.isBlank() ? types : java.util.Arrays.stream(requested.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
            check(!selected.isEmpty() && types.containsAll(selected), "Requested machine scope must contain only known types: " + requested);
            selectedScope = selected.stream().sorted().toList();
            final String representativeRequest = System.getProperty("warlockery.machineRepresentativeTypes", "");
            representativeScope = representativeRequest.equals("none") ? List.of() : representativeRequest.isBlank()
                ? selectedScope : java.util.Arrays.stream(representativeRequest.split(",")).map(String::strip).distinct().sorted().toList();
            check(selected.containsAll(representativeScope), "Representative types must be inside the selected machine scope");
            final String recipeRequest = System.getProperty("warlockery.machineRecipeIds", "");
            requestedRecipeIds = recipeRequest.isBlank() ? List.of() : java.util.Arrays.stream(recipeRequest.split(","))
                .map(String::strip).map(value -> value.equals("none") || value.contains(":") ? value : "warlockery:" + value).distinct().sorted().toList();
            final List<MachineRecipeManager.Match> loadedRecipes;
            try (var created = context.worldBuilder().create()) {
                world = created;
                for (String kind : representativeScope) {
                    final Map<String, Object> result = new LinkedHashMap<>();
                    result.put("machine", kind);
                    result.put("block_ids", MachineProfiles.blockIds().stream()
                        .filter(id -> MachineProfiles.forBlock(id).recipeType().equals(kind)).sorted().toList());
                    result.put("recipe", REPRESENTATIVES.get(kind));
                    result.put("setup", "Fresh survival inventory, placed machine and safe platform, staged ingredients, "
                        + "camera alignment, external heat when required, complete six-block altar charged via receivePower. "
                        + "No outputs or processing progress are injected; real server ticks perform the working.");
                    results.add(result);
                    try {
                        walkthrough(context, kind, result);
                        result.put("passed", true);
                    } catch (Throwable failure) {
                        result.put("passed", false);
                        result.put("failure", failure.toString());
                        result.put("failure_stack", stackTrace(failure));
                        failures.add(kind + ": " + failure);
                        try { screenshot(context, kind + "-failure"); } catch (Throwable ignored) { }
                        closeScreen(context);
                    }
                    writeReport();
                }
                passiveClassification();
                final var availableRecipes = serverValue(player -> MachineRecipeManager.INSTANCE.all().stream()
                    .filter(match -> selectedScope.contains(match.recipe().machine()))
                    .sorted(java.util.Comparator.comparing(match -> match.id().toString())).toList());
                availableRecipeCount = availableRecipes.size();
                final var availableIds = availableRecipes.stream().map(match -> match.id().toString()).collect(Collectors.toSet());
                check(requestedRecipeIds.equals(List.of("none")) || availableIds.containsAll(requestedRecipeIds),
                    "Requested recipe IDs must exist in the selected machine scope: " + requestedRecipeIds);
                loadedRecipes = availableRecipes.stream().filter(match -> requestedRecipeIds.isEmpty()
                    || requestedRecipeIds.contains(match.id().toString())).toList();
                writeReport();
            }
            if (!requestedRecipeIds.equals(List.of("none"))) sweepRecipes(context, loadedRecipes);
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("WARLOCKERY_MACHINE_WALKTHROUGH_PASS " + evidence);
        } catch (Throwable failure) {
            try { Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Machine walkthrough evidence: " + evidence, failure);
        } finally {
            observation = null;
        }
    }

    /** Each ID gets its own fresh world: no prior effects, entities, upgrades or tank contents can satisfy it. */
    private void sweepRecipes(final ClientGameTestContext context, final List<MachineRecipeManager.Match> recipes) throws Exception {
        check(!recipes.isEmpty(), "The loaded machine recipe census must not be empty");
        requiredRecipeCount = recipes.size();
        for (MachineRecipeManager.Match match : recipes) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("recipe", match.id().toString());
            result.put("machine", match.recipe().machine());
            result.put("status", "running");
            result.put("passed", false);
            recipeResults.add(result);
            writeReport();
            try (var isolated = context.worldBuilder().create()) {
                world = isolated;
                try {
                    processRecipe(context, match, result);
                    result.put("passed", true);
                    result.put("status", "passed");
                } catch (Throwable failure) {
                    result.put("status", failure instanceof UnsupportedOperationException ? "unsupported" : "failed");
                    result.put("failure", failure.toString());
                    result.put("failure_stack", stackTrace(failure));
                    failures.add(match.id() + ": " + failure);
                    try { screenshot(context, match.id().getPath() + "-recipe-failure"); } catch (Throwable ignored) { }
                } finally {
                    if (observation != null) result.put("runtime_observation", observation.report());
                    observation = null;
                    final Path record = evidence.resolve("recipe-" + recipeResults.size() + ".json");
                    Files.writeString(record, new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    result.put("receipt_file", record.toString());
                    writeReport();
                }
            } catch (Throwable failure) {
                result.put("passed", false);
                result.put("status", "failed");
                result.put("world_failure", failure.toString());
                failures.add(match.id() + " world: " + failure);
                writeReport();
            } finally {
                observation = null;
            }
        }
    }

    private void processRecipe(final ClientGameTestContext context, final MachineRecipeManager.Match match,
        final Map<String, Object> result) throws Exception {
        final var recipe = match.recipe();
        final var profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        result.put("fixture", "New disposable survival world per recipe; staged machine, supplies, safe floor and natural "
            + "Demon Heart altar capacity; reserve charged via receivePower. Inputs, buckets, fuel, ignition and output "
            + "collection use native mouse/key input. No outputs, progress, matching or completion are injected. "
            + "One actual matcher-winning set of tag alternatives is selected; this is not every tag combination.");
        result.put("processing_ticks", recipe.processingTime());
        result.put("declared_power", recipe.altarPower());
        result.put("power_mode", recipe.powerMode().id());
        stageMachine(context, profile, recipe);
        if (!serverValue(player -> com.kadamitas.warlockery.crafting.SpiritWorldMachineRules.allows(match.id(),
            player.level().dimension().identifier()))) {
            server(player -> {
                final var destination = player.level().getServer().getLevel(com.kadamitas.warlockery.dream.SpiritWorldRuntime.SPIRIT_WORLD);
                check(destination != null, "Recipe-required Spirit World is loaded");
                check(player.teleportTo(destination, 0.5, 80, -2.5, Set.of(), 0, 0, true), "Fixture moves player to the actual required dimension");
            });
            stageMachine(context, profile, recipe);
            result.put("dimension_fixture", "Player staged in the real Spirit World for the dimension-restricted recipe; dream entry is not tested here.");
        }
        final var water = new com.kadamitas.warlockery.util.FluidContents(
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER), 1000);
        final int buckets = recipe.fluid().map(input -> (input.amount() + 999) / 1000).orElse(0);
        if (buckets > 4 || recipe.fluid().filter(input -> !com.kadamitas.warlockery.util.FluidIngredient
            .parse(input.ingredient()).orElseThrow().matches(water)).isPresent()) {
            throw new UnsupportedOperationException("Native bucket fixture supports only water recipes within the real 4000 mB tank.");
        }
        final var fluid = buckets == 0 ? com.kadamitas.warlockery.util.FluidContents.EMPTY
            : new com.kadamitas.warlockery.util.FluidContents(water.variant(), buckets * 1000);
        final List<ItemStack> witness = serverValue(player -> winningInputs(match, profile, fluid, result));
        final List<Integer> slots = MachineRecipeSlotPlan.inputSlots(profile, recipe);
        final int coalDuration = serverValue(player -> player.level().fuelValues().burnDuration(new ItemStack(Items.COAL)));
        check(coalDuration > 0, "Loaded fuel registry defines a positive coal burn duration");
        final int fuelCount = recipe.requiresFuel() ? Math.ceilDiv(recipe.processingTime(), coalDuration) : 0;
        server(player -> {
            for (int index = 0; index < witness.size(); index++) player.getInventory().setItem(9 + index, witness.get(index).copy());
            player.getInventory().setItem(18, new ItemStack(Items.COAL, Math.max(1, fuelCount)));
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++)
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
            for (int x = 2; x < 6; x++) for (int z = 2; z < 6; z++)
                player.level().setBlockAndUpdate(ALTAR.offset(x, -1, z), ModBlocks.ALL.get("demonheart").get().defaultBlockState());
            stageFamiliar(player, match.id(), result);
            player.inventoryMenu.broadcastChanges();
        });
        context.waitTicks(41);
        server(player -> {
            int available = 0;
            for (BlockPos pos : altarPositions()) {
                final var altar = (AltarBlockEntity) player.level().getBlockEntity(pos);
                check(altar.isMultiblockValid(), "Real fixture altar formed before processing");
                final int required = Math.max(altar.getCapacity() / 10, recipe.powerMode().requiredAvailablePower(recipe.altarPower()));
                altar.receivePower(Math.max(0, required - altar.getPower()));
                available += altar.availablePower();
            }
            check(available >= recipe.altarPower(), "Staged natural capacity supplies the complete declared power cost");
            check(com.kadamitas.warlockery.crafting.AltarPowerNetwork.available(player.level(), MACHINE)
                >= recipe.powerMode().requiredAvailablePower(recipe.altarPower()), "One reachable altar meets the actual per-advance/completion requirement");
            observation = new RecipeObservation(player, match);
        });
        world.getConnection().waitForClientboundPackets();
        for (int bucket = 0; bucket < buckets; bucket++) {
            server(player -> { player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET)); player.inventoryMenu.broadcastChanges(); });
            world.getConnection().waitForClientboundPackets();
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(1).is(Items.BUCKET));
            final int expected = (bucket + 1) * 1000;
            check(serverValue(player -> machine(player).getFluidAmount()) == expected, "Native bucket increases tank by exactly 1000 mB");
        }
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        openMachine(context);
        for (int index = 0; index < witness.size(); index++) {
            clickPlayerSlot(context, 9 + index);
            clickSlot(context, slots.get(index));
            check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), "Native recipe input accepted");
        }
        result.put("native_inputs", java.util.stream.IntStream.range(0, witness.size()).mapToObj(index -> Map.of(
            "slot", slots.get(index), "item", BuiltInRegistries.ITEM.getKey(witness.get(index).getItem()).toString(),
            "count", witness.get(index).getCount())).toList());
        if (recipe.requiresFuel()) { clickPlayerSlot(context, 18); clickSlot(context, profile.fuelSlot()); }
        if (profile.requiresExternalHeat()) server(player -> player.level().setBlockAndUpdate(MACHINE.below(), Blocks.CAMPFIRE.defaultBlockState()));
        if (recipe.machine().equals("brazier")) {
            if (observation.effect.equals("GRAVEYARD_MIST")) check(!mistParticlesVisible(context), "Fresh fixture has no explosion particles before ignition");
            closeScreen(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_3);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(2).getDamageValue() > 0);
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            openMachine(context);
            result.put("brazier_ash", "Ash appears at ignition; it is not accepted as completion or effect evidence.");
        }
        screenshot(context, match.id().getPath() + "-native-inputs");
        final int limit = Math.addExact(recipe.processingTime(), 1600);
        final long startedAt = serverValue(player -> player.level().getGameTime());
        server(player -> player.level().getServer().tickRateManager().setTickRate(20.0F));
        result.put("tick_rate", "Normal 20 TPS requested. Fabric client GameTest synchronizes server and client ticks; increasing only server TPS cannot accelerate its 50 ms client timer. Production processing is never directly invoked.");
        final long wallStart = System.nanoTime();
        long lastReceiptTick = startedAt - 40;
        long lastAdvanceTick = startedAt;
        int previousProgress = -1;
        while (!serverValue(player -> inputsEmpty(machine(player), profile))
            && serverValue(player -> player.level().getGameTime()) - startedAt < limit) {
            context.waitTicks(2);
            final long now = serverValue(player -> player.level().getGameTime());
            final int progress = serverValue(player -> machine(player).getProgress());
            if (progress != previousProgress) { previousProgress = progress; lastAdvanceTick = now; }
            if (observation.effect.equals("GRAVEYARD_MIST") && mistParticlesVisible(context)) observation.effectObserved = true;
            if (now - lastReceiptTick >= 40) {
                result.put("processing_checkpoint", processingCheckpoint(context, startedAt, wallStart));
                result.put("runtime_observation", observation.report());
                writeReport();
                lastReceiptTick = now;
            }
            check(now - lastAdvanceTick <= 400, "No processing progress for 400 actual server ticks: " + result.get("processing_checkpoint"));
            check((System.nanoTime() - wallStart) / 1_000_000_000L <= Math.max(120L, limit / 5L),
                "Processing exceeded the bounded wall-clock allowance; last checkpoint: " + result.get("processing_checkpoint"));
        }
        result.put("processing_checkpoint", processingCheckpoint(context, startedAt, wallStart));
        check(serverValue(player -> inputsEmpty(machine(player), profile) && machine(player).getProgress() == 0),
            "Runtime completes and consumes every input within the declared time plus bounded setup allowance");
        final var observed = observation;
        check(observed.started, "Actual runtime progress was observed for this recipe ID");
        check(observed.error == null, "Per-server-tick power observation: " + observed.error);
        check(observed.spent() == recipe.altarPower(), "Exact altar debit including observed natural recharge equals declared recipe power");
        if (recipe.powerMode() == com.kadamitas.warlockery.crafting.PowerMode.CONTINUOUS && recipe.altarPower() > 0)
            check(observed.continuousDebit, "Continuous recipe consumes power while inputs remain and progress is incomplete");
        final int remainingFluid = buckets * 1000 - recipe.fluid().map(MachineRecipeDefinition.FluidInput::amount).orElse(0);
        check(serverValue(player -> machine(player).getFluidAmount()) == remainingFluid,
            "Exact loaded fluid amount is consumed, leaving the precise tank remainder");
        if (remainingFluid > 0) check(serverValue(player -> {
            final var tank = (net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage) field(machine(player), "fluidStorage");
            return tank.getResource().getFluid() == net.minecraft.world.level.material.Fluids.WATER;
        }), "Remaining tank contents retain the exact input fluid identity");
        result.put("fluid", Map.of("filled_mB", buckets * 1000, "consumed_mB", recipe.fluid().map(MachineRecipeDefinition.FluidInput::amount).orElse(0),
            "remaining_mB", remainingFluid, "input_fluid", buckets > 0 ? "minecraft:water" : "none"));
        final Map<String, Integer> expected = new LinkedHashMap<>();
        recipe.outputs().forEach(output -> expected.merge(output.item(), output.count(), Integer::sum));
        final Map<String, Integer> actual = serverValue(player -> outputCounts(machine(player), profile));
        check(actual.equals(expected), "Every output item/count matches with no missing or extra output: expected=" + expected + ", actual=" + actual);
        check(serverValue(player -> {
            for (int index = 0; index < recipe.outputs().size(); index++) {
                final var output = recipe.outputs().get(index);
                final var expectedStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(output.item())), output.count());
                if (!ItemStack.matches(expectedStack, machine(player).getItem(profile.outputStart() + index))) return false;
            }
            return true;
        }), "Output slots contain exact declared item components and counts");
        result.put("outputs_before_collection", actual);
        if (recipe.requiresFuel()) {
            final int fuelRemaining = serverValue(player -> machine(player).getItem(profile.fuelSlot()).getCount());
            check(fuelRemaining == 0, "Runtime consumes exactly the coal count required by the loaded burn duration");
            result.put("fuel", Map.of("coal_burn_ticks", coalDuration, "inserted_count", fuelCount, "consumed_count", fuelCount - fuelRemaining));
        }
        if (recipe.machine().equals("brazier")) {
            if (!observed.effectObserved) throw new UnsupportedOperationException("Brazier inputs/time/power/ash checked, but world effect lacks a positive observer: " + observed.effect);
            result.put("effect", observed.effect + ": observed actual live entity/effect during or after burning");
        }
        result.put("runtime_observation", observed.report());
        observation = null;
        screenshot(context, match.id().getPath() + "-runtime-completed");
        for (int index = 0; index < profile.outputSlots(); index++) {
            final int slot = profile.outputStart() + index;
            if (context.computeOnClient(client -> !client.player.containerMenu.getSlot(slot).getItem().isEmpty())) {
                clickSlot(context, slot);
                clickPlayerSlot(context, 27 + index);
            }
        }
        check(serverValue(player -> {
            final Map<String, Integer> collected = new LinkedHashMap<>();
            for (int index = 0; index < profile.outputSlots(); index++) addCount(collected, player.getInventory().getItem(27 + index));
            return collected.equals(expected) && outputCounts(machine(player), profile).isEmpty();
        }), "Native output collection delivers exact results and empties every output slot");
        result.put("completion", "Native inventory input/output actions; live server consumption, exact fluid remainder and recharge-adjusted power debit passed.");
        closeScreen(context);
    }

    private static List<ItemStack> winningInputs(final MachineRecipeManager.Match target, final MachineProfile profile,
        final com.kadamitas.warlockery.util.FluidContents fluid, final Map<String, Object> result) {
        final List<List<ItemStack>> alternatives = target.recipe().inputs().stream().map(input -> BuiltInRegistries.ITEM.stream()
            .filter(item -> ItemIngredient.parse(input.ingredient()).orElseThrow().matches(new ItemStack(item)))
            .map(item -> new ItemStack(item, input.count())).toList()).toList();
        long combinations = 1;
        for (List<ItemStack> options : alternatives) {
            check(!options.isEmpty(), "Each loaded recipe ingredient resolves to registered items");
            combinations = Math.multiplyExact(combinations, options.size());
        }
        result.put("input_cartesian_cardinality", combinations);
        if (combinations > 100_000) throw new UnsupportedOperationException("Witness search exceeds bounded 100000 combinations: " + combinations);
        final List<Integer> slots = MachineRecipeSlotPlan.inputSlots(profile, target.recipe());
        for (long candidate = 0; candidate < combinations; candidate++) {
            long remaining = candidate;
            final var inventory = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
            final List<ItemStack> selected = new ArrayList<>();
            for (int index = 0; index < alternatives.size(); index++) {
                final var options = alternatives.get(index);
                final var stack = options.get((int) (remaining % options.size())).copy();
                remaining /= options.size();
                selected.add(stack);
                inventory.set(slots.get(index), stack);
            }
            if (MachineRecipeManager.INSTANCE.find(profile, inventory, fluid).map(match -> match.id().equals(target.id())).orElse(false)) {
                result.put("witness_candidates_examined", candidate + 1);
                return List.copyOf(selected);
            }
        }
        throw new AssertionError("No matcher-winning input witness exists for loaded recipe " + target.id());
    }

    private static void stageFamiliar(final ServerPlayer player, final Identifier recipe, final Map<String, Object> result) {
        final String familiar = switch (recipe.getPath()) {
            case "kettle_brew_bodega" -> "owl";
            case "kettle_brew_cursed_leaping" -> "familiar_cat";
            case "kettle_brew_frogs_tongue" -> "toad";
            default -> "";
        };
        if (familiar.isEmpty()) return;
        final var entity = com.kadamitas.warlockery.registry.ModEntities.ALL.get(familiar).get()
            .create(player.level(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
        check(entity != null, "Required familiar fixture can be created");
        entity.snapTo(2.5, 80, 2.5);
        if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        check(com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(entity, player.getUUID()), "Fixture familiar belongs to actual native brewer");
        check(player.level().addFreshEntity(entity), "Required bound familiar exists in the real world");
        result.put("familiar_fixture", familiar + " staged and bound to brewer; familiar acquisition is outside this processing test.");
    }

    private static List<BlockPos> altarPositions() {
        return java.util.stream.IntStream.range(0, 6).mapToObj(index -> ALTAR.offset(index / 2, 0, index % 2)).toList();
    }

    private static boolean inputsEmpty(final MagicMachineBlockEntity machine, final MachineProfile profile) {
        return java.util.stream.IntStream.range(0, profile.inputSlots()).allMatch(slot -> machine.getItem(slot).isEmpty());
    }

    private static Map<String, Integer> outputCounts(final MagicMachineBlockEntity machine, final MachineProfile profile) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = profile.outputStart(); slot < profile.outputStart() + profile.outputSlots(); slot++) addCount(counts, machine.getItem(slot));
        return counts;
    }

    private static void addCount(final Map<String, Integer> counts, final ItemStack stack) {
        if (!stack.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
    }

    private static boolean mistParticlesVisible(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            final var groups = (Map<?, ?>) field(client.particleEngine, "particles");
            try {
                final var particles = net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");
                particles.setAccessible(true);
                for (Object group : groups.values()) for (Object particle : (Iterable<?>) particles.get(group)) {
                    if (particle instanceof net.minecraft.client.particle.HugeExplosionParticle
                        || particle instanceof net.minecraft.client.particle.ExplodeParticle) return true;
                }
                return false;
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe actual native mist particles", failure); }
        });
    }

    /** Passive END_SERVER_TICK observer: never invokes processing, output, power-consumption or effect methods. */
    private static final class RecipeObservation {
        private final ServerPlayer player;
        private final MachineRecipeManager.Match recipe;
        private final MachineProfile profile;
        private final List<AltarBlockEntity> altars;
        private final long initialPower;
        private volatile long recharge;
        private long lastTick;
        private volatile List<Integer> previousPower;
        private volatile boolean started;
        private volatile boolean continuousDebit;
        private volatile boolean effectObserved;
        private volatile String error;
        private final String effect;
        private final List<BlockPos> drainTargets = new ArrayList<>();
        private net.minecraft.world.entity.monster.zombie.Zombie drainUndead;

        RecipeObservation(final ServerPlayer player, final MachineRecipeManager.Match recipe) {
            this.player = player;
            this.recipe = recipe;
            profile = MachineProfiles.forRecipeType(recipe.recipe().machine()).orElseThrow();
            altars = altarPositions().stream().map(pos -> (AltarBlockEntity) player.level().getBlockEntity(pos)).toList();
            previousPower = altars.stream().map(AltarBlockEntity::getPower).toList();
            initialPower = previousPower.stream().mapToLong(Integer::longValue).sum();
            lastTick = player.level().getGameTime();
            effect = com.kadamitas.warlockery.crafting.BrazierEffectRuntime.Effect.fromRecipe(recipe.id()).map(Enum::name).orElse("none");
            if (effect.equals("DRAIN_GROWTH")) {
                for (int x = -3; x <= 3; x++) for (int z = 1; z <= 3; z++) {
                    final var target = MACHINE.offset(x, 0, z);
                    player.level().setBlockAndUpdate(target.below(), Blocks.FARMLAND.defaultBlockState());
                    player.level().setBlockAndUpdate(target, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(1));
                    drainTargets.add(target);
                }
                final var zombieType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie"));
                check(zombieType != null, "Vanilla zombie entity type is registered");
                drainUndead = (net.minecraft.world.entity.monster.zombie.Zombie) zombieType.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
                check(drainUndead != null, "Drain Growth has an actual wounded undead target");
                drainUndead.snapTo(2.5, 80, -0.5);
                drainUndead.setNoAi(true);
                drainUndead.setInvulnerable(true);
                drainUndead.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                drainUndead.setHealth(1.0F);
                check(player.level().addFreshEntity(drainUndead), "Wounded undead fixture spawned without invoking the Brazier effect");
            }
        }

        synchronized void tick() {
            try {
                final long now = player.level().getGameTime();
                if (now <= lastTick) return;
                check(now == lastTick + 1, "Power observer sees every server tick");
                if (now % 40 == 0) for (int index = 0; index < altars.size(); index++) {
                    final var altar = altars.get(index);
                    final var display = altar.getDisplay();
                    final int regenerated = Math.max(1, display.environmentalPower() / 10) * display.rechargeMultiplier();
                    check(previousPower.get(index) + regenerated < altar.getCapacity(), "Power fixture retains recharge headroom; capped regeneration is not inferred");
                    recharge += regenerated;
                }
                previousPower = altars.stream().map(AltarBlockEntity::getPower).toList();
                lastTick = now;
                final var machine = machine(player);
                if (machine.getProgress() > 0) {
                    started = true;
                    check(recipe.id().toString().equals(field(machine, "activeRecipe")), "Actual processing selected the requested recipe ID");
                    if (spent() > 0) continuousDebit = true;
                    if (recipe.recipe().powerMode() == com.kadamitas.warlockery.crafting.PowerMode.ON_COMPLETE)
                        check(spent() == 0, "Completion-cost recipe does not debit power before completion");
                }
                observeEffect();
            } catch (Throwable failure) {
                if (error == null) error = failure.toString();
            }
        }

        private void observeEffect() {
            if (effectObserved || !recipe.recipe().machine().equals("brazier")) return;
            if (effect.equals("DRAIN_GROWTH")) {
                final boolean drained = drainTargets.stream().anyMatch(pos -> {
                    final var state = player.level().getBlockState(pos);
                    return state.is(Blocks.WHEAT) && ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getAge(state) == 0;
                });
                if (drained && drainUndead.getHealth() > 1.0F) {
                    effectObserved = true;
                    // Remove only unused fixture crops after proving both consequences, bounding repeated 800-tick extensions.
                    drainTargets.forEach(pos -> player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
                }
                return;
            }
            final String summoned = switch (effect) {
                case "SUMMON_SPECTRE" -> "warlockery:spectre";
                case "SUMMON_BANSHEE" -> "warlockery:banshee";
                case "SUMMON_POLTERGEIST" -> "warlockery:poltergeist";
                default -> "";
            };
            if (!summoned.isEmpty()) effectObserved = !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(MACHINE).inflate(16), mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals(summoned)).isEmpty();
            else effectObserved = switch (effect) {
                case "ANGUISH_OF_THE_DEAD" -> player.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
                case "FORTIFICATION_OF_THE_CORPSE" -> player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
                case "DEATHLY_VEIL" -> player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
                default -> false;
            };
        }

        synchronized long spent() { return initialPower + recharge - previousPower.stream().mapToLong(Integer::longValue).sum(); }
        synchronized Map<String, Object> report() {
            final Map<String, Object> report = new LinkedHashMap<>();
            report.put("started_requested_recipe", started);
            report.put("initial_power", initialPower);
            report.put("observed_recharge", recharge);
            report.put("actual_power_debit", spent());
            report.put("continuous_debit_before_completion", continuousDebit);
            report.put("brazier_effect", effect);
            report.put("brazier_effect_observed", effectObserved);
            if (effect.equals("DRAIN_GROWTH")) report.put("drain_fixture", "21 age-one crops and a wounded, stationary, protected zombie; actual crop age reduction plus actual healing required. Remaining test crops removed after first proven effect to bound repeat burn extensions.");
            if (effect.equals("GRAVEYARD_MIST")) report.put("mist_observer", "Actual Minecraft explosion particles in the client particle engine, absent before native ignition and present during burning; no particles injected.");
            if (error != null) report.put("observation_error", error);
            return report;
        }
    }

    private void walkthrough(final ClientGameTestContext context, final String kind,
        final Map<String, Object> result) throws Exception {
        final MachineProfile profile = MachineProfiles.forRecipeType(kind).orElseThrow();
        final MachineRecipeDefinition recipe = MachineRecipeManager.INSTANCE.byId(
            Identifier.fromNamespaceAndPath("warlockery", REPRESENTATIVES.get(kind))).orElseThrow().recipe();
        final List<Integer> inputSlots = MachineRecipeSlotPlan.inputSlots(profile, recipe);
        stageMachine(context, profile, recipe);
        readBook(context, profile, result);
        if (profile.supportsFluids()) {
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(1).is(Items.BUCKET));
            check(serverValue(player -> machine(player).getFluidAmount()) == 1000,
                "Native water-bucket interaction fills exactly one bucket of water");
            result.put("fluid_interaction", "Right-click water bucket fills 1000 mB and returns empty bucket.");
        } else result.put("fluid_interaction", "This machine profile has no fluid tank.");
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        openMachine(context);
        context.waitFor(client -> jei() != null && jei().getIngredientListOverlay().isListDisplayed());
        check(context.computeOnClient(client -> ((MachineMenu) client.player.containerMenu).kind().equals(kind)),
            "Actual block use opens the expected machine GUI");
        screenshot(context, kind + "-empty-gui");
        result.put("jei_catalog_before_actions", jeiCatalogCheckpoint(context, kind));
        writeReport();
        rejectOutputInsertion(context, profile);
        if (profile.hasDedicatedInputSlot()) {
            clickPlayerSlot(context, 9);
            clickSlot(context, profile.dedicatedInputSlot());
            context.waitTicks(3);
            check(context.computeOnClient(client -> !client.player.containerMenu.getCarried().isEmpty()
                && client.player.containerMenu.getSlot(profile.dedicatedInputSlot()).getItem().isEmpty()),
                "Dedicated clay-jar slot rejects the ordinary ingredient");
            clickPlayerSlot(context, 9);
            result.put("container_slot", "Native click rejects ordinary ingredient in dedicated jar slot; jars placed there below.");
        }
        final boolean transfer = Set.of("alchemical_oven", "distillery", "spinningwheel").contains(kind);
        if (transfer) {
            transferRecipe(context, kind, profile, inputSlots, result);
        } else {
            for (int index = 0; index < recipe.inputs().size(); index++) {
                clickPlayerSlot(context, 9 + index);
                clickSlot(context, inputSlots.get(index));
                check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    "Recipe ingredient accepted in its shown machine slot");
            }
        }
        final List<String> inputActions = new ArrayList<>();
        for (int index = 0; index < recipe.inputs().size(); index++) {
            inputActions.add(recipe.inputs().get(index).ingredient() + " x" + recipe.inputs().get(index).count()
                + " -> machine slot " + inputSlots.get(index));
        }
        result.put(transfer ? "native_jei_transfer_slots" : "native_input_clicks", inputActions);
        final MachineStatus missing = profile.hasFuelSlot() ? MachineStatus.NO_FUEL
            : profile.requiresExternalHeat() ? MachineStatus.NO_HEAT
            : recipe.altarPower() > 0 ? MachineStatus.NO_ALTAR_POWER : null;
        if (missing != null) {
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == missing);
            screenshot(context, kind + "-missing-" + missing.name().toLowerCase());
            result.put("missing_requirement_status", missing.name());
        }
        checkJeiKeys(context, kind, inputSlots.getFirst(), result);
        if (profile.requiresExternalHeat()) {
            server(player -> player.level().setBlockAndUpdate(MACHINE.below(), Blocks.CAMPFIRE.defaultBlockState()));
        }
        if (recipe.altarPower() > 0) chargeAltar(context);
        if (profile.hasFuelSlot()) {
            clickPlayerSlot(context, 18);
            clickSlot(context, profile.fuelSlot());
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == MachineStatus.PROCESSING);
            result.put("fuel_interaction", "Native inventory click inserts one coal; live runtime consumes the fuel.");
        } else result.put("fuel_interaction", "No internal fuel slot; heat or altar requirement observed separately.");
        if (kind.equals("brazier")) {
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == MachineStatus.NO_IGNITION);
            screenshot(context, "brazier-awaiting-ignition");
            closeScreen(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_3);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(2).getDamageValue() > 0);
            context.waitTicks(12);
            screenshot(context, "brazier-lit-world");
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            openMachine(context);
            result.put("ignition", "Actual flint-and-steel right-click ignites the brazier and consumes durability.");
        }
        context.waitFor(client -> ((MachineMenu) client.player.containerMenu).progressPercent() > 0);
        screenshot(context, kind + "-processing");
        context.waitTicks(recipe.processingTime() + 30);
        check(serverValue(player -> inputSlots.stream().allMatch(slot -> machine(player).getItem(slot).isEmpty())),
            "Live machine runtime consumes every staged recipe input");
        for (int index = 0; index < recipe.outputs().size(); index++) {
            final int slot = profile.outputStart() + index;
            final var output = recipe.outputs().get(index);
            check(serverValue(player -> {
                final ItemStack actual = machine(player).getItem(slot);
                return BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(output.item())
                    && actual.getCount() == output.count();
            }), "Expected live machine output " + output.item() + " x" + output.count());
        }
        if (recipe.fluid().isPresent()) {
            check(serverValue(player -> machine(player).getFluidAmount()) == 1000 - recipe.fluid().orElseThrow().amount(),
                "Recipe consumes the displayed fluid amount");
        }
        if (kind.equals("brazier")) {
            check(serverValue(player -> !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(MACHINE).inflate(12),
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals("warlockery:spectre")).isEmpty()),
                "Brazier live completion summons a spectre; the ash alone is not completion evidence");
        }
        screenshot(context, kind + "-completed-output");
        for (int index = 0; index < recipe.outputs().size(); index++) {
            clickSlot(context, profile.outputStart() + index);
            clickPlayerSlot(context, 27 + index);
        }
        check(serverValue(player -> {
            for (int index = 0; index < recipe.outputs().size(); index++) {
                if (!BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(27 + index).getItem()).toString()
                    .equals(recipe.outputs().get(index).item())) return false;
            }
            return true;
        }), "Native output clicks deliver all results to survival inventory");
        result.put("completion", "Actual server tick processing consumed inputs and exact fluid and produced every expected output; "
            + "native output clicks collected results into survival inventory.");
        closeScreen(context);
        if (kind.equals("silvervat")) passiveSilver(context, result);
    }

    private void stageMachine(final ClientGameTestContext context, final MachineProfile profile,
        final MachineRecipeDefinition recipe) {
        closeScreen(context);
        server(player -> {
            final var level = player.level();
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                for (int y = 80; y <= 83; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(MACHINE, ModBlocks.ALL.get(profile.displayBlock()).get().defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = true;
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
            player.getInventory().setItem(2, new ItemStack(Items.FLINT_AND_STEEL));
            player.getInventory().setItem(17, new ItemStack(Items.STONE));
            player.getInventory().setItem(18, new ItemStack(Items.COAL));
            for (int index = 0; index < recipe.inputs().size(); index++) {
                final var input = recipe.inputs().get(index);
                final var ingredient = ItemIngredient.parse(input.ingredient()).orElseThrow();
                final var item = BuiltInRegistries.ITEM.stream().filter(candidate -> ingredient.matches(new ItemStack(candidate)))
                    .findFirst().orElseThrow(() -> new AssertionError("No loaded item matches " + input.ingredient()));
                player.getInventory().setItem(9 + index, new ItemStack(item, input.count()));
            }
            player.teleportTo(0.5, 80, -2.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        aim(context, MACHINE);
    }

    private void readBook(final ClientGameTestContext context, final MachineProfile profile,
        final Map<String, Object> result) throws Exception {
        final String recipeId = REPRESENTATIVES.get(profile.recipeType());
        final String recipeSection = profile.recipeType().equals("kettle")
            ? "brew_entry_" + recipeId.substring("kettle_brew_".length()) : "machine_recipe_" + recipeId;
        final var exact = ManualProfile.profiles().stream().filter(book -> book.sections().contains(recipeSection)).findFirst();
        final String fallback = profile.recipeType().equals("brazier") ? "ingredient_book_burning" : "cauldronbook";
        final ManualProfile book = exact.orElseGet(() -> ManualProfile.find(fallback).orElseThrow());
        final String section = exact.isPresent() ? recipeSection : book.sections().getFirst();
        server(player -> { player.getInventory().setItem(3, new ItemStack(ModItems.ALL.get(book.id()).get()));
            player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_4);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        final String title = context.computeOnClient(client -> Component.translatable(book.translatedSectionTitleKey(section)).getString());
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        ManualClientAcceptance.clickButton(context, Component.translatable(book.chapterFor(section).titleKey()).getString());
        ManualClientAcceptance.clickButton(context, title);
        context.waitFor(client -> field(client.gui.screen(), "selectedSection").equals(section));
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString());
        result.put("book", book.id());
        result.put("book_section", section);
        result.put("book_instructions", text);
        if (profile.recipeType().equals("brazier")) {
            final String ignition = context.computeOnClient(client -> Component.translatable("manual.warlockery.machine_recipe.ignite").getString());
            check(text.contains(ignition) && ignition.contains("Flint and Steel") && ignition.contains("Fire Charge"),
                "The actual Brazier recipe article explains closing its GUI and igniting the block with a held fire source");
            result.put("book_ignition_instructions", ignition);
        }
        result.put("book_coverage", exact.isPresent() ? "Indexed exact recipe or brew entry opened by native book search and buttons."
            : "GAP: no indexed machine_recipe section for this representative; nearest subject book opened. GUI actions below use runtime-derived test data, not an invented claim of book-only guidance.");
        screenshot(context, profile.recipeType() + "-book-guidance");
        checkBookSetupReference(context, profile.recipeType(), book, section, result);
        closeScreen(context);
    }

    private void checkBookSetupReference(final ClientGameTestContext context, final String kind,
        final ManualProfile book, final String section, final Map<String, Object> result) throws Exception {
        final var reference = ManualBookLinks.references(book, section).stream().findFirst().orElseThrow(
            () -> new AssertionError("Machine recipe lacks its setup-book reference: " + kind));
        final String expectedSection = switch (kind) {
            case "alchemical_oven" -> "oven";
            case "distillery" -> "inputs";
            case "brazier" -> "brazier";
            case "spinningwheel" -> "spinningwheel";
            case "silvervat" -> "silvervat";
            default -> "machines";
        };
        check(reference.section().equals(expectedSection), "Recipe reference targets the appropriate machine setup instructions");
        final String label = context.computeOnClient(client -> reference.label().getString());
        double[] point = null;
        for (int page = 0; page < 60; page++) {
            point = context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.visible && button.active && button.getClass().getSimpleName().equals("ManualReferenceButton")
                    && button.getMessage().getString().equals(label))
                .map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0})
                .findFirst().orElse(null));
            screenshot(context, kind + "-recipe-reading-page-" + (page + 1));
            if (point != null) break;
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(section)),
                "Setup reference is reachable within the recipe's own pages");
        }
        check(point != null, "Real machine recipe pages expose their visible setup reference");
        if (!reference.profile().id().equals(book.id())) {
            server(player -> {
                player.getInventory().setItem(4, new ItemStack(ModItems.ALL.get(reference.profile().id()).get()));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(2);
        }
        final Object source = context.computeOnClient(client -> client.gui.screen());
        final int sourcePage = context.computeOnClient(client -> (int) field(source, "bodyPage"));
        ManualClientAcceptance.click(context, point[0], point[1]);
        context.waitFor(client -> client.gui.screen() instanceof ManualScreen && client.gui.screen() != source);
        check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(expectedSection)
            && ((ManualProfile) field(client.gui.screen(), "manual")).id().equals(reference.profile().id())
            && !(boolean) field(client.gui.screen(), "recipePreview")), "Actual owned-book link opens machine setup instructions");
        screenshot(context, kind + "-linked-setup-instructions");
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back").getString());
        context.waitFor(client -> client.gui.screen() == source);
        check(context.computeOnClient(client -> (int) field(source, "bodyPage") == sourcePage
            && field(source, "selectedSection").equals(section)), "Back restores the exact machine recipe page");
        result.put("native_setup_link", reference.profile().id() + "/" + expectedSection);
        result.put("setup_link_fixture", "Source book is staged before native opening; an additional target book is staged only when the link crosses books.");
    }

    private void rejectOutputInsertion(final ClientGameTestContext context, final MachineProfile profile) {
        clickPlayerSlot(context, 17);
        clickSlot(context, profile.outputStart());
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(Items.STONE)
            && client.player.containerMenu.getSlot(profile.outputStart()).getItem().isEmpty()),
            "Actual GUI rejects player insertion into output slot");
        clickPlayerSlot(context, 17);
    }

    private void checkJeiKeys(final ClientGameTestContext context, final String kind, final int ingredientSlot,
        final Map<String, Object> result) throws Exception {
        context.waitFor(client -> jei() != null && jei().getIngredientListOverlay().isListDisplayed());
        cursorSlot(context, ingredientSlot);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_U);
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        screenshot(context, kind + "-jei-ingredient-uses");
        closeScreen(context);
        context.waitForScreen(MachineScreen.class);
        cursorSlot(context, ingredientSlot);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_R);
        context.waitTicks(5);
        final boolean recipeScreen = context.computeOnClient(client -> client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        if (recipeScreen) {
            screenshot(context, kind + "-jei-ingredient-recipes");
            closeScreen(context);
            context.waitForScreen(MachineScreen.class);
        }
        final int[] recipeArea = context.computeOnClient(client -> {
            final var properties = jei().getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            final var layout = ((MachineMenu) client.player.containerMenu).layout();
            return new int[] {properties.guiLeft() + 25 + (layout.width() - 42) / 2,
                properties.guiTop() + layout.statusY() + 8};
        });
        ManualClientAcceptance.click(context, recipeArea[0], recipeArea[1]);
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        check(context.computeOnClient(client -> {
            final Object logic = field(client.gui.screen(), "logic");
            try {
                final var selected = logic.getClass().getMethod("getSelectedRecipeCategory");
                selected.setAccessible(true);
                final var category = (mezz.jei.api.recipe.category.IRecipeCategory<?>) selected.invoke(logic);
                return category.getRecipeType().equals(com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get(kind));
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe clicked machine recipe category", failure); }
        }), "Native status-area click opens the exact " + kind + " JEI category");
        screenshot(context, kind + "-jei-machine-recipe-area");
        closeScreen(context);
        context.waitForScreen(MachineScreen.class);
        result.put("jei", Map.of("native_uses_key", true, "native_recipe_key", recipeScreen,
            "recipe_key_note", recipeScreen ? "Recipe display opened from actual machine slot." : "This input has no recipe shown for R; U remains available.",
            "native_machine_recipe_area", kind,
            "transfer", result.containsKey("transfer_setup") ? result.get("transfer_setup")
                : "This representative uses native slot insertion; fluid recipes do not offer automatic ingredient transfer."));
    }

    @SuppressWarnings("unchecked")
    private void transferRecipe(final ClientGameTestContext context, final String kind, final MachineProfile profile,
        final List<Integer> inputSlots, final Map<String, Object> result) throws Exception {
        final List<ItemStack> expected = context.computeOnClient(client -> java.util.stream.IntStream.range(0, inputSlots.size())
            .mapToObj(index -> client.player.getInventory().getItem(9 + index).copy()).toList());
        context.runOnClient(client -> {
            final var type = com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get(kind);
            final var category = (mezz.jei.api.recipe.category.IRecipeCategory<MachineRecipeManager.Match>) jei().getRecipeManager()
                .createRecipeCategoryLookup().get().filter(value -> value.getRecipeType().equals(type)).findFirst()
                .orElseThrow(() -> new AssertionError("JEI has no visible machine category for " + kind
                    + "; runtime census: " + result.get("jei_catalog_before_actions")));
            final var recipe = jei().getRecipeManager().createRecipeLookup(type).get()
                .filter(value -> value.id().getPath().equals(REPRESENTATIVES.get(kind))).findFirst()
                .orElseThrow(() -> new AssertionError("JEI has no visible representative " + REPRESENTATIVES.get(kind)
                    + "; runtime census: " + result.get("jei_catalog_before_actions")));
            jei().getRecipesGui().showRecipes(category, List.of(recipe), List.of());
        });
        context.waitFor(client -> client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        context.waitTicks(4);
        final int[] point = context.computeOnClient(client -> {
            final Object layouts = field(client.gui.screen(), "layouts");
            final Object layout = ((List<?>) field(layouts, "recipeLayoutsWithButtons")).getFirst();
            final Object transferController = field(layout, "transferButton");
            final Object button = ((List<?>) field(layout, "buttons")).stream()
                .filter(candidate -> field(candidate, "controller") == transferController).findFirst().orElseThrow();
            try {
                check((boolean) button.getClass().getMethod("isVisible").invoke(button), "JEI transfer button is visible for " + kind);
                return new int[] {(int) button.getClass().getMethod("getX").invoke(button)
                        + (int) button.getClass().getMethod("getWidth").invoke(button) / 2,
                    (int) button.getClass().getMethod("getY").invoke(button)
                        + (int) button.getClass().getMethod("getHeight").invoke(button) / 2};
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe native JEI transfer button", failure); }
        });
        screenshot(context, kind + "-jei-transfer-ready");
        ManualClientAcceptance.click(context, point[0], point[1]);
        context.waitForScreen(MachineScreen.class);
        context.waitFor(client -> java.util.stream.IntStream.range(0, inputSlots.size()).allMatch(index ->
            ItemStack.matches(expected.get(index), client.player.containerMenu.getSlot(inputSlots.get(index)).getItem())));
        if (profile.hasFuelSlot()) check(context.computeOnClient(client -> client.player.containerMenu.getSlot(profile.fuelSlot()).getItem().isEmpty()),
            "JEI transfers recipe ingredients without treating the fuel slot as recipe input");
        screenshot(context, kind + "-jei-transferred-inputs");
        result.put("transfer_setup", "JEI API selects the exact representative recipe; actual rendered + button is clicked. Runtime transfer places every staged ingredient/count in its semantic machine slot; fuel stays empty.");
    }

    private void chargeAltar(final ClientGameTestContext context) {
        server(player -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
            }
        });
        context.waitTicks(41);
        server(player -> {
            final var altar = (AltarBlockEntity) player.level().getBlockEntity(ALTAR);
            check(altar.isMultiblockValid(), "Fixture altar is a real complete six-block multiblock");
            check(altar.receivePower(1000) > 0, "Fixture charges real altar reserve through its power API");
        });
        context.waitTicks(3);
    }

    private void passiveSilver(final ClientGameTestContext context, final Map<String, Object> result) throws Exception {
        final BlockPos furnace = MACHINE.east();
        server(player -> {
            player.level().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
            player.getInventory().setItem(19, new ItemStack(Items.GOLD_ORE));
            player.getInventory().setItem(20, new ItemStack(Items.COAL));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        aim(context, furnace);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(net.minecraft.client.gui.screens.inventory.FurnaceScreen.class);
        clickPlayerSlot(context, 19);
        clickSlot(context, 0);
        clickPlayerSlot(context, 20);
        clickSlot(context, 1);
        screenshot(context, "silvervat-passive-adjacent-furnace");
        context.waitFor(client -> client.player.containerMenu.getSlot(2).getItem().is(Items.GOLD_INGOT), 300);
        closeScreen(context);
        openMachine(context);
        context.waitFor(client -> client.player.containerMenu.getSlot(6).getItem().is(ModItems.ALL.get("ingredient_silverdust").get()));
        screenshot(context, "silvervat-passive-silver-deposit");
        clickSlot(context, 6);
        clickPlayerSlot(context, 31);
        result.put("passive_behavior", "Native furnace GUI insertion smelts staged gold ore with coal; adjacent Silver Vat runtime yields one silver deposit, collected by native vat GUI click.");
        closeScreen(context);
    }

    private void passiveClassification() {
        results.add(Map.of("support_blocks", List.of("altar", "fumefunnel", "filteredfumefunnel"),
            "classification", "Not MachineProfiles processing types. Altar is exercised as a real six-block power source. "
                + "Fume funnels are passive adjacent oven upgrades, have no independent machine GUI or input/output inventory, "
                + "and are classified here rather than silently skipped. Their yield upgrade is not part of this representative-machine GUI run."));
    }

    private void openMachine(final ClientGameTestContext context) {
        aim(context, MACHINE);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(MachineScreen.class);
    }

    private static void aim(final ClientGameTestContext context, final BlockPos target) {
        context.runOnClient(client -> {
            final var delta = net.minecraft.world.phys.Vec3.atCenterOf(target).subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target)),
            "Staged camera ray targets the actual block before native use");
    }

    private static void clickPlayerSlot(final ClientGameTestContext context, final int index) {
        final int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(candidate -> client.player.containerMenu.getSlot(candidate).container == client.player.getInventory()
                && client.player.containerMenu.getSlot(candidate).getContainerSlot() == index).findFirst().orElseThrow());
        clickSlot(context, slot);
    }

    private static void cursorSlot(final ClientGameTestContext context, final int slotIndex) {
        final int[] point = context.computeOnClient(client -> {
            final var properties = jei().getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            final var slot = client.player.containerMenu.getSlot(slotIndex);
            return new int[] {properties.guiLeft() + slot.x + 8, properties.guiTop() + slot.y + 8};
        });
        ManualClientAcceptance.cursor(context, point[0], point[1]);
    }

    private static void clickSlot(final ClientGameTestContext context, final int slotIndex) {
        cursorSlot(context, slotIndex);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(3);
        }
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private Map<String, Object> processingCheckpoint(final ClientGameTestContext context, final long startedAt, final long wallStart) {
        final Map<String, Object> checkpoint = serverValue(player -> {
            final var block = machine(player);
            final Map<String, Object> values = new LinkedHashMap<>();
            values.put("server_game_time", player.level().getGameTime());
            values.put("elapsed_server_ticks", player.level().getGameTime() - startedAt);
            values.put("progress", block.getProgress());
            values.put("active_recipe", String.valueOf(field(block, "activeRecipe")));
            values.put("status", block.getMachineDisplay().status().name());
            values.put("fluid_mB", block.getFluidAmount());
            values.put("available_altar_power", block.getAvailableAltarPower());
            values.put("burn_ticks_remaining", block.getBurnTime());
            values.put("machine_inventory", java.util.stream.IntStream.range(0, block.getContainerSize())
                .filter(slot -> !block.getItem(slot).isEmpty()).mapToObj(slot -> Map.of("slot", slot,
                    "item", BuiltInRegistries.ITEM.getKey(block.getItem(slot).getItem()).toString(),
                    "count", block.getItem(slot).getCount())).toList());
            return values;
        });
        final double seconds = (System.nanoTime() - wallStart) / 1_000_000_000.0;
        checkpoint.put("elapsed_wall_seconds", seconds);
        checkpoint.put("observed_server_ticks_per_second", ((Number) checkpoint.get("elapsed_server_ticks")).doubleValue() / Math.max(seconds, 0.001));
        checkpoint.put("client_screen", context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName()));
        return checkpoint;
    }

    private Map<String, Object> jeiCatalogCheckpoint(final ClientGameTestContext context, final String kind) {
        return context.computeOnClient(client -> {
            final var type = com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get(kind);
            final var manager = jei().getRecipeManager();
            final List<String> visible = manager.createRecipeLookup(type).get().map(match -> match.id().toString()).sorted().toList();
            final List<String> includingHidden = manager.createRecipeLookup(type).includeHidden().get()
                .map(match -> match.id().toString()).sorted().toList();
            return Map.of("machine", kind, "visible_recipe_ids", visible, "including_hidden_recipe_ids", includingHidden,
                "visible_count", visible.size(), "including_hidden_count", includingHidden.size(),
                "visible_category", manager.createRecipeCategoryLookup().get().anyMatch(category -> category.getRecipeType().equals(type)),
                "authoritative_client_recipe_ids", com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalog.machines().stream()
                    .filter(match -> match.recipe().machine().equals(kind)).map(match -> match.id().toString()).sorted().toList());
        });
    }

    private static String stackTrace(final Throwable failure) {
        final var text = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }

    private void writeReport() throws Exception {
        final Path pending = evidence.resolve("machine-walkthrough.json.tmp");
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("results", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        report.put("selected_machine_types", selectedScope);
        report.put("all_machine_types_selected", Set.copyOf(selectedScope).equals(REPRESENTATIVES.keySet()));
        report.put("selected_representative_types", representativeScope);
        report.put("requested_recipe_ids", requestedRecipeIds);
        report.put("available_recipe_count_in_machine_scope", availableRecipeCount);
        report.put("all_recipe_ids_selected", requestedRecipeIds.isEmpty());
        report.put("recipe_results", recipeResults);
        report.put("required_recipe_count", requiredRecipeCount);
        report.put("recipe_sweep_complete", requiredRecipeCount > 0 && recipeResults.size() == requiredRecipeCount
            && recipeResults.stream().allMatch(result -> Boolean.TRUE.equals(result.get("passed"))));
        report.put("execution", "Real rendered Fabric client, real server machine ticks, native mouse/key interactions; fixture setup is separately declared.");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create()
            .toJson(report));
        try { Files.move(pending, evidence.resolve("machine-walkthrough.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
            Files.move(pending, evidence.resolve("machine-walkthrough.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }

    private static MagicMachineBlockEntity machine(final ServerPlayer player) {
        return (MagicMachineBlockEntity) player.level().getBlockEntity(MACHINE);
    }

    private static IJeiRuntime jei() { return ManualJeiAcceptance.runtime(); }

    private static Object field(final Object object, final String name) {
        try {
            final var field = (object instanceof Class<?> type ? type : object.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object instanceof Class<?> ? null : object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe " + name, failure); }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
