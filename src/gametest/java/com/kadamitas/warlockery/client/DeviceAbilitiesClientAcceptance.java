package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.LeechChestMemory;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
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

public final class DeviceAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DEVICES = List.of("fumefunnel", "filteredfumefunnel", "shadedglass",
        "shadedglass_active", "leechchest", "doll_shelf");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile DeviceObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("device-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final var current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final String configured = System.getProperty("warlockery.deviceIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(DEVICES) : Arrays.stream(configured.split(","))
                .map(String::strip).map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && DEVICES.containsAll(requested), "Requested devices must have an implemented native scenario");
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
                            case "fumefunnel", "filteredfumefunnel" -> funnel(context, id, row);
                            case "shadedglass", "shadedglass_active" -> glass(context, id, row);
                            case "leechchest" -> leech(context, row);
                            case "doll_shelf" -> shelf(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
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
            System.out.println("WARLOCKERY_DEVICE_ABILITIES_PASSED " + evidence);
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
        row.put("fixture", "Fresh disposable Survival world, stone platform and supplies staged. Devices, upgrades and lever are placed by native item-use; inventories are filled through native GUI clicks. Production processing, redstone, sampling and protection are not invoked directly.");
    }

    private void funnel(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        place(context, item("alchemical_oven"), DEVICE, "alchemical_oven", false);
        final Map<String, Object> baseline = new LinkedHashMap<>();
        row.put("baseline", baseline);
        ovenBatch(context, id + "-baseline", 0, baseline);
        close(context);
        place(context, item(id), DEVICE.above(), id, true);
        final Map<String, Object> upgrade = new LinkedHashMap<>();
        row.put("upgrade", upgrade);
        ovenBatch(context, id + "-upgraded", id.equals("fumefunnel") ? 1 : 2, upgrade);
        row.put("verified", "One sapling and one jar consumed per batch; unchanged ash1; fumes baseline1, ordinary funnel2 or filtered funnel3. Passive observations require real progress increments1/2/3 respectively; all outputs collected through native GUI clicks.");
    }

    private void ovenBatch(final ClientGameTestContext context, final String label, final int rank,
        final Map<String, Object> batch) throws Exception {
        close(context);
        server(player -> {
            player.getInventory().setItem(9, new ItemStack(Items.BIRCH_SAPLING));
            player.getInventory().setItem(10, new ItemStack(item("ingredient_clay_jar")));
            player.getInventory().setItem(11, new ItemStack(Items.COAL));
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
            observation = new DeviceObservation(player, false);
        });
        sync(context, 0);
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        context.waitForScreen(MachineScreen.class);
        final var profile = MachineProfiles.forRecipeType("alchemical_oven").orElseThrow();
        clickPlayerSlot(context, 9); clickSlot(context, 0);
        clickPlayerSlot(context, 10); clickSlot(context, profile.dedicatedInputSlot());
        clickPlayerSlot(context, 11); clickSlot(context, profile.fuelSlot());
        check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), "All staged inputs/fuel accepted");
        await(context, player -> {
            final var oven = (MagicMachineBlockEntity) player.level().getBlockEntity(DEVICE);
            return oven.getItem(0).isEmpty() && oven.getItem(profile.dedicatedInputSlot()).isEmpty()
                && !oven.getItem(profile.outputStart()).isEmpty();
        }, 260, "Native oven completes within its180tick recipe duration");
        final var captured = serverValue(player -> observation.report());
        batch.put("runtime_observation", captured);
        check(serverValue(player -> observation.progressSteps.contains(rank + 1)), "Actual per-tick progress matches installed funnel rank");
        final Map<String, Integer> outputs = serverValue(player -> {
            final var oven = (MagicMachineBlockEntity) player.level().getBlockEntity(DEVICE);
            final Map<String, Integer> values = new LinkedHashMap<>();
            for (int slot = profile.outputStart(); slot < profile.outputEnd(); slot++) {
                final var stack = oven.getItem(slot);
                if (!stack.isEmpty()) values.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
            return values;
        });
        batch.put("outputs", outputs);
        check(outputs.equals(Map.of("warlockery:ingredient_ash_wood", 1, "warlockery:ingredient_breath_of_the_goddess", rank + 1)),
            "Exact upgraded native output quantities: " + outputs);
        screenshot(context, label + "-outputs");
        final var ash = item("ingredient_ash_wood");
        final var fume = item("ingredient_breath_of_the_goddess");
        final Map<String, Integer> before = serverValue(player -> Map.of("ash", count(player, ash), "fume", count(player, fume)));
        batch.put("inventory_before_collection", before);
        final List<Map<String, Object>> transfers = new ArrayList<>();
        batch.put("native_output_transfers", transfers);
        try {
            // Fabric's test mouse events carry modifiers=0 even while Shift is held.
            // Use the same ordinary pickup/place interaction as a player without Shift.
            for (int slot = profile.outputStart(); slot < profile.outputEnd(); slot++) {
                final int source = slot;
                final ItemStack expected = serverValue(player ->
                    ((MagicMachineBlockEntity) player.level().getBlockEntity(DEVICE)).getItem(source).copy());
                if (expected.isEmpty()) continue;
                final int destination = serverValue(player -> java.util.stream.IntStream.range(12, 36)
                    .filter(index -> player.getInventory().getItem(index).isEmpty()).findFirst().orElseThrow());
                final Map<String, Object> transfer = new LinkedHashMap<>();
                transfer.put("machine_slot", source);
                transfer.put("player_inventory_slot", destination);
                transfer.put("item", BuiltInRegistries.ITEM.getKey(expected.getItem()).toString());
                transfer.put("count", expected.getCount());
                transfers.add(transfer);
                clickSlot(context, source);
                check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(expected.getItem())
                    && client.player.containerMenu.getCarried().getCount() == expected.getCount()),
                    "Native output pickup carries the exact stack from machine slot " + source);
                transfer.put("picked_up", true);
                clickPlayerSlot(context, destination);
                await(context, player -> player.containerMenu.getCarried().isEmpty()
                    && player.getInventory().getItem(destination).is(expected.getItem())
                    && player.getInventory().getItem(destination).getCount() == expected.getCount(), 30,
                    "Native output placement deposits the exact stack in player inventory slot " + destination);
                transfer.put("deposited", true);
            }
            await(context, player -> count(player, ash) == before.get("ash") + 1
                && count(player, fume) == before.get("fume") + rank + 1
                && player.containerMenu.getCarried().isEmpty()
                && java.util.stream.IntStream.range(profile.outputStart(), profile.outputEnd()).allMatch(slot ->
                    ((MagicMachineBlockEntity) player.level().getBlockEntity(DEVICE)).getItem(slot).isEmpty()), 30,
                "Native collection adds exact ash and fume quantities, empties outputs and leaves no carried stack");
            check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                "Native client cursor is empty after output collection");
            batch.put("native_output_collection", true);
        } finally {
            batch.put("collection_after", serverValue(player -> Map.of(
                "inventory", Map.of("ash", count(player, ash), "fume", count(player, fume)),
                "machine_outputs", java.util.stream.IntStream.range(profile.outputStart(), profile.outputEnd())
                    .mapToObj(slot -> ((MagicMachineBlockEntity) player.level().getBlockEntity(DEVICE)).getItem(slot).toString()).toList(),
                "carried_stack", player.containerMenu.getCarried().toString())));
        }
        observation = null;
    }

    private void glass(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        place(context, item(id), DEVICE, "shadedglass", false);
        check(serverValue(player -> player.level().getBlockState(DEVICE).propagatesSkylightDown()
            && player.level().getBlockState(DEVICE).getLightDampening() == 0), "Unpowered placement is transparent even when the active variant item was staged");
        final BlockPos lever = DEVICE.east();
        place(context, Items.LEVER, lever, "minecraft:lever", false);
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        sync(context, 0);
        use(context, new Vec3(1.5, 100.15, 0.5), lever);
        await(context, player -> player.level().getBlockState(DEVICE).is(ModBlocks.ALL.get("shadedglass_active").get()), 30,
            "Native lever activation powers and changes Shaded Glass to active");
        check(serverValue(player -> !player.level().getBlockState(DEVICE).propagatesSkylightDown()
            && player.level().getBlockState(DEVICE).getLightDampening() == 15), "Powered glass actually blocks skylight with full dampening");
        screenshot(context, id + "-powered");
        use(context, new Vec3(1.5, 100.15, 0.5), lever);
        await(context, player -> player.level().getBlockState(DEVICE).is(ModBlocks.ALL.get("shadedglass").get()), 30,
            "Native second lever click restores the unpowered block");
        screenshot(context, id + "-unpowered");
        row.put("verified", "Native placement and two lever toggles; observed exact block variants, skylight propagation and light dampening0/15/0. Pixel brightness is not inferred from block state.");
    }

    private void leech(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("leechchest"), DEVICE, "leechchest", false);
        final Mob target = serverValue(player -> {
            final var cow = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(cow != null, "Native registered cow fixture exists");
            cow.snapTo(2.5, 100, 0.5); cow.setNoAi(true);
            check(player.level().addFreshEntity(cow), "One living sample source staged withinfiveblocks");
            player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges();
            return cow;
        });
        sync(context, 0);
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> LeechChestMemory.get(player.level()).samples(DEVICE).stream()
            .anyMatch(binding -> binding.targetId().equals(player.getUUID()))), "Native empty-hand use remembers the visiting player");
        final float health = serverValue(player -> target.getHealth());
        supply(context, 0, item("sympathetic_vial"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem())
            .filter(binding -> binding.targetId().equals(target.getUUID())).isPresent(), 30, "Native chest use transfers the actual nearest cow identity to the vial");
        check(serverValue(player -> target.getHealth()) == health - 1, "Sampling inflicts exactly1native magic damage on the actual living donor");
        screenshot(context, "leechchest-sampled-vial");
        server(player -> target.snapTo(10.5, 100, 0.5));
        supply(context, 0, item("sympathetic_vial"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem())
            .filter(binding -> binding.targetId().equals(target.getUUID())).isPresent(), 30, "With donor outofrange, a new vial receives the chest's actual remembered sample");
        check(serverValue(player -> target.getHealth()) == health - 1, "Remembered transfer does not hurt the distant donor again");
        row.put("sample_target_uuid", target.getUUID().toString());
        row.put("verified", "Native visitor remembrance, actual nearest-donor sympathetic binding transfer and1damage, followed by memory-to-new-vial transfer after staged donor relocation outside5blocks. This device does not transfer ordinary inventory items.");
    }

    private void shelf(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("doll_shelf"), DEVICE, "doll_shelf", false);
        supply(context, 1, item("death_guard_doll"));
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> DollItem.isBound(player.getInventory().getItem(1)), 30, "Actual item-use self-binds Death Guard before shelf installation");
        check(serverValue(player -> SympatheticBinding.read(player.getInventory().getItem(1)).orElseThrow().targetId().equals(player.getUUID())),
            "Doll binds this native player");
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        sync(context, 0);
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        context.waitForScreen(DollShelfScreen.class);
        clickPlayerSlot(context, 1); clickSlot(context, 0);
        await(context, player -> ((DollShelfBlockEntity) player.level().getBlockEntity(DEVICE)).getItem(0).is(item("death_guard_doll")), 30,
            "Native shelf GUI accepts the bound doll");
        check(serverValue(player -> count(player, item("death_guard_doll")) == 0
            && ((DollShelfBlockEntity) player.level().getBlockEntity(DEVICE)).requiresChunkTicket()), "The only protection doll is installed in the chunk-ticketed shelf, absent from player inventory");
        screenshot(context, "doll_shelf-installed-bound-guard");
        close(context);
        server(player -> {
            player.level().setBlockAndUpdate(new BlockPos(0, 99, 4), Blocks.SAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, 4), Blocks.CACTUS.defaultBlockState());
            player.setHealth(1); player.setAbsorptionAmount(0); player.removeAllEffects();
            player.teleportTo(0.5, 100, 3.1); player.setDeltaMovement(Vec3.ZERO);
            observation = new DeviceObservation(player, true);
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { await(context, player -> observation.shelfActivated, 100, "Installed remote doll prevents lethal damage from native movement into a real cactus"); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); }
        check(serverValue(player -> observation.recoveredHealth >= 10 && observation.recoveredHealth <= 11
            && observation.regeneration && player.isAlive()), "Shelf protection restores halfhealth and grants its actual regeneration outcome");
        check(serverValue(player -> ((DollShelfBlockEntity) player.level().getBlockEntity(DEVICE)).getItem(0).getDamageValue() == 1
            && count(player, item("death_guard_doll")) == 0), "Exactly one charge consumed on the shelved doll, not on a carried replacement");
        screenshot(context, "doll_shelf-native-hazard-protected");
        row.put("verified", "Self-bound doll installed with native GUI clicks. Health1 staged, then actual W movement into cactus; installed shelf doll consumed onecharge, player lived with halfhealth and regeneration. Cross-chunk unloading and persistence are separate untested contracts.");
    }

    private void readGuides(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final List<String> requested = switch (id) {
            case "fumefunnel", "filteredfumefunnel" -> List.of("funnels", "machine_recipe_oven_fume_breath_of_the_goddess");
            case "doll_shelf" -> List.of("fetish_doll_shelf", "death_guard_doll");
            case "leechchest" -> List.of("crafting_leech_chest", "leechchest");
            case "shadedglass" -> List.of("crafting_shaded_glass", "shadedglass");
            case "shadedglass_active" -> List.of("crafting_shaded_glass", "shadedglass_active");
            default -> List.of(id);
        };
        final List<Map<String, Object>> guides = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String section : requested) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
            if (found.isEmpty()) { missing.add(section); continue; }
            final var profile = found.orElseThrow();
            supply(context, 0, item(profile.id()));
            context.runOnClient(client -> client.player.setXRot(-70));
            context.waitTicks(2); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
        if (crouch) { context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3); }
        try { use(context, new Vec3(target.getX() + 0.5, target.getY() - 0.001, target.getZ() + 0.5), target.below()); }
        finally { if (crouch) { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(2); } }
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + hotbar); context.waitTicks(2);
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
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
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
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitTicks(3);
        }
    }

    private static final class DeviceObservation {
        final ServerPlayer player;
        final boolean shelf;
        final Set<Integer> progressSteps = new LinkedHashSet<>();
        int previousProgress;
        int ticks;
        boolean shelfActivated;
        float recoveredHealth;
        boolean regeneration;
        DeviceObservation(final ServerPlayer player, final boolean shelf) { this.player = player; this.shelf = shelf; }
        void tick() {
            ticks++;
            if (shelf) {
                final var block = (DollShelfBlockEntity) player.level().getBlockEntity(DEVICE);
                if (!shelfActivated && block != null && block.getItem(0).getDamageValue() > 0) {
                    shelfActivated = true; recoveredHealth = player.getHealth(); regeneration = player.hasEffect(MobEffects.REGENERATION);
                }
            } else if (player.level().getBlockEntity(DEVICE) instanceof MagicMachineBlockEntity block) {
                final int progress = block.getProgress();
                if (progress > previousProgress) progressSteps.add(progress - previousProgress);
                previousProgress = progress;
            }
        }
        Map<String, Object> report() { return Map.of("observed_server_ticks", ticks, "actual_progress_steps", List.copyOf(progressSteps),
            "shelved_guard_activated", shelfActivated, "first_recovery_health", recoveredHealth, "first_recovery_regeneration", regeneration); }
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
        report.put("all_six_native_scenarios_passed", finished && results.size() == DEVICES.size()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key placement and interaction, normal server processing/hazard ticks. No outcome or progress injection.");
        report.put("class_sha256", Map.of("test", classHash(DeviceAbilitiesClientAcceptance.class),
            "machine", classHash(MagicMachineBlockEntity.class), "shelf", classHash(DollShelfBlockEntity.class),
            "machine_upgrades", classHash(com.kadamitas.warlockery.crafting.MachineUpgradeRules.class),
            "shaded_glass", classHash(com.kadamitas.warlockery.block.ShadedGlassBlock.class),
            "leech_interaction", classHash(com.kadamitas.warlockery.block.InteractiveUtilityBlock.class),
            "doll", classHash(DollItem.class)));
        report.put("devices", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("device-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("device-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
