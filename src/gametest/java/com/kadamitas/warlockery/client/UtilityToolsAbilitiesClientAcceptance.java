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
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import com.kadamitas.warlockery.item.MirrorState;

public final class UtilityToolsAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DEVICES = List.of("divinerwater", "divinerlava", "playercompass", "shelfcompass",
        "brewbag", "mirror", "ingredient_seer_stone", "ruby_slippers");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile BagObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("utility-tools-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final String configured = System.getProperty("warlockery.utilityToolIds", "");
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
                            case "divinerwater", "divinerlava" -> diviner(context, id, row);
                            case "playercompass" -> playerCompass(context, row);
                            case "shelfcompass" -> shelfCompass(context, row);
                            case "brewbag" -> satchel(context, row);
                            case "mirror" -> mirror(context, row);
                            case "ingredient_seer_stone" -> seer(context, row);
                            case "ruby_slippers" -> slippers(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
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
            System.out.println("WARLOCKERY_UTILITY_TOOLS_ABILITIES_PASSED " + evidence);
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
        row.put("fixture", "Fresh disposable Survival world, stone platform and supplies staged. Native item/block interactions and GUI clicks invoke tool behavior. Binding, container contents, messages, navigation targets and teleport outcomes are never injected.");
    }

    private void diviner(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final boolean water = id.equals("divinerwater");
        final String fluid = water ? "water" : "lava";
        final BlockPos support = DEVICE.below();
        server(player -> {
            for (int y = player.level().getMinY(); y < support.getY(); y++)
                player.level().setBlockAndUpdate(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(support.below(3), (water ? Blocks.LAVA : Blocks.WATER).defaultBlockState());
            player.level().setBlockAndUpdate(support.below(7), (water ? Blocks.WATER : Blocks.LAVA).defaultBlockState());
        });
        supply(context, 0, item(id)); clearChat(context);
        use(context, new Vec3(0.5, 99.999, 0.5), support);
        final String expected = translated(context, "message.warlockery.diviner.found", fluid, 7);
        awaitChat(context, expected);
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 1), "Found scan consumes exactly one durability");
        screenshot(context, id + "-found-seven-below");
        row.put("found_message", expected);
        server(player -> player.level().setBlockAndUpdate(support.below(7), Blocks.STONE.defaultBlockState()));
        clearChat(context);
        use(context, new Vec3(0.5, 99.999, 0.5), support);
        final String missing = translated(context, "message.warlockery.diviner.missing", fluid);
        awaitChat(context, missing);
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 2), "Missing scan consumes one additional durability");
        screenshot(context, id + "-missing-own-fluid");
        row.put("missing_message", missing);
        row.put("verified", "Native scans report exact seven-block depth while ignoring the opposite fluid three blocks below; after only target-fluid removal, native message reports missing. Two uses spend two durability.");
    }

    private void playerCompass(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID otherId = serverValue(player -> {
            final var server = player.level().getServer();
            final var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CompassFixture");
            final var other = new ServerPlayer(server, player.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            final var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, other, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            other.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            other.setGameMode(GameType.SURVIVAL); other.teleportTo(0.5, 100, 0.5);
            return other.getUUID();
        });
        row.put("participant_fixture", "A connected synthetic ServerPlayer target, with one rendered activating client. This verifies native targeting and component updates, not two real clients or multiplayer latency.");
        try {
            supply(context, 0, item("playercompass"));
            context.waitFor(client -> client.level.getPlayerByUUID(otherId) != null, 40);
            look(context, new Vec3(0.5, 101, 0.5));
            check(context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity().getUUID().equals(otherId)), "Native pointer hits the other player");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(otherId)).isPresent(),
                30, "Native compass use binds the actual other player UUID");
            server(player -> player.level().getServer().getPlayerList().getPlayer(otherId).teleportTo(7.5, 100, 0.5));
            await(context, player -> trackerMatches(player.getMainHandItem(), player.level().getServer().getPlayerList().getPlayer(otherId)), 40,
                "Actual compass inventory ticks follow the moved player");
            row.put("same_dimension_target", serverValue(player -> tracker(player.getMainHandItem()).toString()));
            screenshot(context, "playercompass-native-bound-target");
            server(player -> {
                final var destination = player.level().getServer().getLevel(Level.NETHER);
                check(destination != null, "Real Nether level exists for the cross-dimension target fixture");
                destination.getChunkAt(new BlockPos(3, 100, 0));
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 99, -1), new BlockPos(4, 102, 1)))
                    destination.setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
                final var other = player.level().getServer().getPlayerList().getPlayer(otherId);
                check(other.teleportTo(destination, 3.5, 100, 0.5, Set.of(), 0, 0, true), "Fixture relocates tracked player to real Nether");
            });
            await(context, player -> trackerMatches(player.getMainHandItem(), player.level().getServer().getPlayerList().getPlayer(otherId))
                && tracker(player.getMainHandItem()).dimension().equals(Level.NETHER), 40,
                "Actual compass inventory ticks update target coordinates and dimension after relocation");
            row.put("cross_dimension_target", serverValue(player -> tracker(player.getMainHandItem()).toString()));
            row.put("target_uuid", otherId.toString());
            row.put("verified", "Native use binds another player; production inventory ticks follow both changed position and changed dimension. Compass visual spinning in a different dimension is not represented as directional tracking.");
        } finally {
            server(player -> {
                final var other = player.level().getServer().getPlayerList().getPlayer(otherId);
                if (other != null) player.level().getServer().getPlayerList().remove(other);
            });
        }
    }

    private void shelfCompass(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("doll_shelf"), DEVICE, "doll_shelf", false);
        supply(context, 0, item("shelfcompass"));
        clearChat(context);
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        try { use(context, Vec3.atCenterOf(DEVICE), DEVICE); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        await(context, player -> tracker(player.getMainHandItem()) != null && tracker(player.getMainHandItem()).pos().equals(DEVICE)
            && tracker(player.getMainHandItem()).dimension().equals(player.level().dimension()), 30, "Native crouch-use records exact clicked shelf and dimension");
        awaitChat(context, translated(context, "message.warlockery.shelf_compass.bound"));
        server(player -> player.teleportTo(8.5, 100, 0.5)); context.waitTicks(10);
        check(serverValue(player -> tracker(player.getMainHandItem()).pos().equals(DEVICE)), "Shelf compass remains bound after its holder moves");
        screenshot(context, "shelfcompass-native-binding");
        row.put("native_target", serverValue(player -> tracker(player.getMainHandItem()).toString()));
        row.put("verified", "Native crouch-use binds the clicked shelf; target remains stable when holder moves. Current tool binds a selected shelf rather than automatically searching for the nearest one.");
    }

    private void satchel(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(item("brewbag")));
            player.getInventory().setItem(9, new ItemStack(item("brew_freeze")));
            player.getInventory().setItem(11, new ItemStack(Items.STONE));
            player.inventoryMenu.broadcastChanges();
            observation = new BagObservation(player);
        });
        sync(context, 0);
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
        clickPlayerSlot(context, 11); clickPlayerSlot(context, 0);
        check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(item("brewbag"))
            && bagCount(client.player.containerMenu.getCarried()) == 0 && client.player.getInventory().getItem(0).is(Items.STONE)),
            "Non-brew insertion is rejected; native inventory swaps rather than hiding stone inside the bag");
        clickPlayerSlot(context, 0); clickPlayerSlot(context, 11);
        clickPlayerSlot(context, 0); clickPlayerSlot(context, 9);
        check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(item("brewbag"))
            && bagCount(client.player.containerMenu.getCarried()) == 1 && client.player.getInventory().getItem(9).isEmpty()),
            "Native carried-bag primary click stores one allowed brew");
        clickPlayerSlotButton(context, 10, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        check(context.computeOnClient(client -> bagCount(client.player.containerMenu.getCarried()) == 0
            && client.player.getInventory().getItem(10).is(item("brew_freeze"))), "Native right-click into an empty inventory slot retrieves the exact brew");
        clickPlayerSlot(context, 10); clickPlayerSlot(context, 0);
        check(serverValue(player -> bagCount(player.getInventory().getItem(0)) == 1), "Native clicks reload the retrieved brew and put the bag in the hotbar");
        screenshot(context, "brewbag-native-store-retrieve");
        close(context); sync(context, 0);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); }); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, player -> observation.projectiles.size() == 1 && bagCount(player.getMainHandItem()) == 0, 30,
            "Native bag use launches exactly one owned projectile carrying the stored brew and consumes its contents");
        check(serverValue(player -> player.getMainHandItem().is(item("brewbag")) && player.getMainHandItem().getCount() == 1),
            "The reusable bag remains after launch");
        check(serverValue(player -> observation.projectiles.values().stream().allMatch(value -> value.equals("warlockery:brew_freeze"))), "Launched projectile carries the exact stored brew ID");
        row.put("native_projectile", serverValue(player -> observation.report()));
        screenshot(context, "brewbag-native-launch");
        row.put("verified", "Actual inventory clicks reject stone, store/retrieve the same brew, reload it, and launch one native owned brew projectile while preserving the empty bag.");
    }

    private void mirror(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("mirror"));
        final Vec3 origin = serverValue(Entity::position);
        useAir(context);
        check(serverValue(player -> MirrorState.read(player.getMainHandItem()).isEmpty() && player.getMainHandItem().getDamageValue() == 0
            && player.position().distanceTo(origin) < 0.1), "Unbound native mirror use does not teleport or spend durability");
        place(context, item("mirrorblock"), DEVICE, "mirrorblock", false);
        supply(context, 0, item("mirror"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> MirrorState.read(player.getMainHandItem()).filter(state -> state.position().equals(DEVICE.above())
            && state.dimension().equals(player.level().dimension().identifier())).isPresent(), 30,
            "Actual mirror-block interaction captures its reflection destination on the handheld mirror");
        final Vec3 destination = new Vec3(0.5, 102, 0.5);
        server(player -> player.teleportTo(8.5, 100, 0.5)); world.getConnection().waitForClientboundPackets();
        useAir(context);
        await(context, player -> player.position().distanceTo(destination) < 1.5 && player.getMainHandItem().getDamageValue() == 1,
            35, "Native mirror use returns to its captured reflection and spends exactly one durability");
        row.put("captured_reflection", serverValue(player -> MirrorState.read(player.getMainHandItem()).orElseThrow().toString()));
        row.put("actual_return_position", serverValue(player -> player.position().toString()));
        screenshot(context, "mirror-native-return");
        row.put("verified", "Missing-reflection gate, native capture from a placed mirror block, then actual handheld return and durability consumption. Cross-dimension mirror travel and blocked destinations are separate untested cases.");
    }

    private void seer(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("ingredient_seer_stone"));
        clearChat(context);
        useAir(context);
        final String expected = context.computeOnClient(client -> Component.translatable("message.warlockery.divination.progression",
            Component.translatable("supernatural_form.warlockery.none"), 0,
            Component.translatable("message.warlockery.divination.no_paths"), 0).getString());
        awaitChat(context, expected);
        row.put("ordinary_progression_message", expected);
        server(player -> { player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item("ingredient_happenstance_oil"), 2)); player.inventoryMenu.broadcastChanges(); });
        world.getServer().runCommand("weather rain");
        await(context, player -> player.level().isRaining(), 120,
            "Staged rain must finish its normal weather transition before divination");
        world.getConnection().waitForClientboundPackets();
        clearChat(context); useAir(context);
        awaitChat(context, translated(context, "message.warlockery.divination.prediction.storm"));
        awaitChat(context, expected);
        check(serverValue(player -> player.getOffhandItem().is(item("ingredient_happenstance_oil")) && player.getOffhandItem().getCount() == 1),
            "Native Seer use consumes exactly one valid catalyst from the opposite hand");
        screenshot(context, "seerstone-native-divination");
        row.put("verified", "Actual native messages report fresh-player form/reserves/paths. A real rainy-world catalyst use reports Storm and consumes one opposite-hand Happenstance Oil. Coven summoning and nondefault supernatural paths are not tested here.");
    }

    private void slippers(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        world.getServer().runCommand("time set day");
        place(context, BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_bed")), DEVICE, "minecraft:red_bed", false);
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); }); sync(context, 0);
        use(context, new Vec3(0.5, 100.4, 0.5), DEVICE);
        await(context, player -> player.getRespawnConfig() != null, 30, "Native bed interaction establishes the player's actual home spawn");
        final GlobalPos home = serverValue(player -> player.getRespawnConfig().respawnData().globalPos());
        row.put("native_home", home.toString());
        supply(context, 0, item("ruby_slippers"));
        server(player -> player.teleportTo(8.5, 100, 0.5)); world.getConnection().waitForClientboundPackets();
        useAir(context);
        await(context, player -> player.level().dimension().equals(home.dimension())
            && player.position().distanceTo(Vec3.atCenterOf(home.pos())) < 3
            && player.getCooldowns().isOnCooldown(player.getMainHandItem()), 40,
            "Native Ruby Slippers use returns to the bed-bound home and starts cooldown");
        check(serverValue(player -> player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0) > 0.98F),
            "Observed fresh cooldown remains near its full five-minute duration");
        screenshot(context, "ruby-slippers-native-home-return");
        server(player -> player.teleportTo(8.5, 100, 0.5)); world.getConnection().waitForClientboundPackets();
        useAir(context); context.waitTicks(8);
        check(serverValue(player -> player.position().distanceTo(new Vec3(8.5, 100, 0.5)) < 0.2
            && player.getMainHandItem().is(item("ruby_slippers")) && player.getMainHandItem().getCount() == 1),
            "Actual cooldown blocks immediate repeat travel without consuming the slippers");
        row.put("verified", "Native bed use sets home; native held Slippers use returns there and starts cooldown; repeat use while cooling down cannot teleport. Bed destruction and fallback world spawn are untested.");
    }

    private static GlobalPos tracker(final ItemStack stack) {
        final var tracker = stack.get(DataComponents.LODESTONE_TRACKER); return tracker == null ? null : tracker.target().orElse(null);
    }
    private static boolean trackerMatches(final ItemStack stack, final ServerPlayer target) {
        final var position = tracker(stack);
        return target != null && position != null && position.pos().equals(target.blockPosition()) && position.dimension().equals(target.level().dimension());
    }
    private static int bagCount(final ItemStack bag) {
        return bag.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopyStream().mapToInt(ItemStack::getCount).sum();
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
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        return context.computeOnClient(client -> Component.translatable(key, arguments).getString());
    }
    private static void clearChat(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); }
    private static List<String> chat(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((List<?>) field(client.gui.hud.getChat(), "allMessages")).stream()
            .map(message -> ((net.minecraft.client.multiplayer.chat.GuiMessage) message).content().getString()).toList());
    }
    private static void awaitChat(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 40 && !chat(context).contains(expected); tick++) context.waitTicks(1);
        check(chat(context).contains(expected), "Native server message reaches rendered chat: " + expected + "; actual=" + chat(context));
    }
    private static void clickPlayerSlotButton(final ClientGameTestContext context, final int index, final int button) {
        final int[] point = context.computeOnClient(client -> {
            final var slot = client.player.containerMenu.slots.stream().filter(candidate -> candidate.container == client.player.getInventory()
                && candidate.getContainerSlot() == index).findFirst().orElseThrow();
            return new int[] {(int) field(client.gui.screen(), "leftPos") + slot.x + 8,
                (int) field(client.gui.screen(), "topPos") + slot.y + 8};
        });
        ManualClientAcceptance.cursor(context, point[0], point[1]); context.getInput().pressMouse(button); context.waitTicks(2);
    }
    private static final class BagObservation {
        final ServerPlayer player;
        final Map<String, String> projectiles = new LinkedHashMap<>();
        BagObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof AbstractThrownPotion potion && potion.getOwner() == player)
                projectiles.put(potion.getUUID().toString(), BuiltInRegistries.ITEM.getKey(potion.getItem().getItem()).toString());
        }
        Map<String, Object> report() { return Map.of("native_projectile_ids_and_items", Map.copyOf(projectiles),
            "observer", "Passive server entity-load event; needed because the bag launches at feet and may collide before end-of-tick."); }
    }

    private void readGuides(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final List<String> requested = switch (id) {
            case "brewbag" -> List.of(id, "delivery");
            case "shelfcompass" -> List.of(id, "gestures", "fetish_doll_shelf");
            default -> List.of(id, "gestures");
        };
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
        report.put("all_eight_native_scenarios_passed", finished && results.size() == DEVICES.size()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key item/block/GUI interactions, real messages, binding and teleport behavior. No outcome/component injection.");
        report.put("class_sha256", Map.of("test", classHash(UtilityToolsAbilitiesClientAcceptance.class),
            "diviner", classHash(com.kadamitas.warlockery.item.FluidDivinerItem.class),
            "player_compass", classHash(com.kadamitas.warlockery.item.PlayerCompassItem.class),
            "shelf_compass", classHash(com.kadamitas.warlockery.item.ShelfCompassItem.class),
            "satchel", classHash(com.kadamitas.warlockery.item.BrewSatchelItem.class),
            "mirror", classHash(com.kadamitas.warlockery.item.MirrorItem.class),
            "seer", classHash(com.kadamitas.warlockery.item.SeerStoneItem.class),
            "slippers", classHash(com.kadamitas.warlockery.item.HomewardSlippersItem.class)));
        report.put("devices", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("utility-tools-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("utility-tools-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
