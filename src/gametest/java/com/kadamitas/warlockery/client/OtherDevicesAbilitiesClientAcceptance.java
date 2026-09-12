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

public final class OtherDevicesAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DEVICES = List.of("alluringskull", "beartrap", "plantmine",
        "wickerbundle", "demonheart", "crystalball", "daylightcollector", "voidbramble", "dreamcatcher");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("other-devices-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            final String configured = System.getProperty("warlockery.otherDeviceIds", "");
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
                            case "alluringskull" -> alluringSkull(context, row);
                            case "beartrap" -> bearTrap(context, row);
                            case "plantmine" -> plantMine(context, row);
                            case "wickerbundle" -> wickerBundle(context, row);
                            case "demonheart" -> demonHeart(context, row);
                            case "crystalball" -> crystalBall(context, row);
                            case "daylightcollector" -> sunCollector(context, row);
                            case "voidbramble" -> voidBramble(context, row);
                            case "dreamcatcher" -> dreamWeaver(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        write(false);
                    }
                } catch (Throwable failure) {
                    fail(id, row, failure);
                } finally { world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_OTHER_DEVICES_ABILITIES_PASSED " + evidence);
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
        row.put("fixture", "Fresh disposable Survival world with a stone platform, support blocks, catalysts and living targets staged as prerequisites. Every device is placed and operated through rendered-player mouse/key input; hazards, scheduled sampling, navigation and effects run through normal game ticks.");
    }

    private void alluringSkull(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("alluringskull"), DEVICE, "alluringskull", false);
        final UUID target = serverValue(player -> {
            player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "time set 18000");
            final Mob zombie = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(zombie != null, "Registered Zombie lure target exists");
            zombie.snapTo(10.5, 100, 0.5); zombie.setTarget(null); zombie.setPersistenceRequired();
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            check(player.level().addFreshEntity(zombie), "Zombie lure target staged");
            return zombie.getUUID();
        });
        final double before = serverValue(player -> player.level().getEntity(target).distanceToSqr(Vec3.atCenterOf(DEVICE)));
        supply(context, 0, item("ingredient_necro_stone"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.AlluringSkullBlock.ACTIVE)
            && player.level().getEntity(target) instanceof Mob mob && mob.getNavigation().getTargetPos() != null
            && mob.getNavigation().getTargetPos().equals(DEVICE), 60, "Activated skull must issue an actual navigation target to the tagged Zombie");
        await(context, player -> player.level().getEntity(target).distanceToSqr(Vec3.atCenterOf(DEVICE)) < before, 100,
            "Lured Zombie must physically move closer to the active skull");
        row.put("verified", "Native placement and Necromantic Stone activation made a tagged Zombie acquire the skull as its navigation target and move closer.");
        row.put("untested", "Eldritch Watcher specialized lure, every tagged undead, disable toggle, chunk persistence.");
    }

    private void bearTrap(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("beartrap"), DEVICE, "beartrap", false);
        use(context, new Vec3(DEVICE.getX() + .5, DEVICE.getY() + .15, DEVICE.getZ() + .5), DEVICE);
        check(serverValue(player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.BearTrapBlock.TRAP_STATE)
            == com.kadamitas.warlockery.block.BearTrapState.ARMED), "Empty-hand native use arms the Bear Trap");
        server(player -> { player.setHealth(20); player.removeAllEffects(); player.teleportTo(0.5, 100, -2.5); });
        walkInto(context, player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.BearTrapBlock.TRAP_STATE)
            == com.kadamitas.warlockery.block.BearTrapState.SPRUNG, 80, "Walking into the armed trap must spring it");
        check(serverValue(player -> player.getHealth() == 14 && player.hasEffect(MobEffects.SLOWNESS)
            && player.getEffect(MobEffects.SLOWNESS).getAmplifier() == 5), "Bear Trap deals6 damage and applies Slowness VI");
        row.put("verified", "Native placement, empty-hand arming and ordinary forward movement sprung the trap, dealt 6 damage and applied Slowness VI.");
        row.put("untested", "Immune entities, spectator, disarm/reset, repeated restraint duration.");
    }

    private void plantMine(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> player.level().setBlockAndUpdate(DEVICE.below(), Blocks.DIRT.defaultBlockState()));
        place(context, item("plantmine"), DEVICE, "plantmine", false);
        supply(context, 0, item("brew_webs"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.PlantMineBlock.PAYLOAD)
            == com.kadamitas.warlockery.block.PlantMinePayload.WEBS && player.getMainHandItem().isEmpty()),
            "Native Brew of Webs use arms the Plant Mine and consumes one payload");
        server(player -> { player.removeAllEffects(); player.teleportTo(0.5, 100, -2.5); });
        walkInto(context, player -> player.level().getBlockState(DEVICE).isAir(), 80, "Walking over the armed Plant Mine must consume it");
        check(serverValue(player -> player.hasEffect(MobEffects.SLOWNESS)
            && player.getEffect(MobEffects.SLOWNESS).getAmplifier() == 2
            && blocks(player, Blocks.COBWEB) > 0), "Web Plant Mine applies Slowness III and creates actual cobwebs");
        row.put("verified", "Native placement and Brew of Webs loading followed by ordinary movement consumed the mine, slowed the player and placed cobwebs.");
        row.put("untested", "Ink, Sprouting and Thorns payloads; immune entities; wrong payload; terrain caps.");
    }

    private void wickerBundle(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("wickerbundle"), DEVICE, "wickerbundle", false);
        supply(context, 0, Items.BEEF);
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.WickerBundleBlock.BLOODIED)
            && player.getMainHandItem().isEmpty()), "Native raw-meat use bloodies the Wicker Bundle and consumes one source");
        row.put("verified", "Native placement plus one tagged raw-meat blood source changed the bundle to its bloodied state and consumed the source.");
        row.put("untested", "Breaking/replacing portable bloodied state, alternate blood sources, already-bloodied rejection.");
    }

    private void demonHeart(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("demonheart"), DEVICE, "demonheart", false);
        server(player -> { player.removeAllEffects(); player.clearFire(); player.setHealth(20); });
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.level().getBlockState(DEVICE).isAir() && player.isOnFire()
            && player.hasEffect(MobEffects.STRENGTH) && player.hasEffect(MobEffects.HEALTH_BOOST)
            && player.hasEffect(MobEffects.FIRE_RESISTANCE)), "Consuming the placed Demon Heart removes it, ignites the player and grants its real effects");
        row.put("verified", "Native empty-hand consumption removed the placed heart, ignited the player, and granted Strength, Health Boost and Fire Resistance among its actual effect set.");
        row.put("untested", "Every effect amplifier/duration and interaction with supernatural weaknesses.");
    }

    private void crystalBall(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("crystalball"), DEVICE, "crystalball", false);
        supply(context, 0, item("ingredient_happenstance_oil"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.getMainHandItem().isEmpty() && player.hasEffect(MobEffects.LUCK)
            && player.getEffect(MobEffects.LUCK).getDuration() >= 1_190),
            "Native Happenstance Oil divination consumes the catalyst and grants actual Luck");
        row.put("verified", "Native placed Crystal Ball use with its tagged Happenstance Oil catalyst consumed one oil and granted the prediction's 1200-tick Luck effect.");
        row.put("untested", "Prediction distribution, bound-Waystone remote view, Baba Yaga night encounter.");
    }

    private void sunCollector(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> {
            player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "time set 6000");
            player.level().setBlockAndUpdate(DEVICE.east(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER, 15));
        });
        place(context, item("daylightcollector"), DEVICE, "daylightcollector", false);
        await(context, player -> player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.SunCollectorBlock.STRENGTH) == 15,
            80, "Sun Collector must sample the adjacent powered daylight detector during normal ticks");
        supply(context, 0, item("ingredient_quartz_sphere"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.getMainHandItem().is(item("sungrenade"))
            && player.level().getBlockState(DEVICE).getValue(com.kadamitas.warlockery.block.SunCollectorBlock.STRENGTH) == 0
            && player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getIntOr("WarlockerySunlightStrength", 0) == 15),
            "Native collection transmutes the Quartz Sphere into a strength15 Sun Grenade and empties the collector");
        row.put("verified", "Native placement and normal scheduled sampling reached strength 15; native Quartz Sphere use produced a strength-15 Sun Grenade and reset storage.");
        row.put("untested", "Weather/sky obstruction, partial strengths, Sun Grenade flight and detonation.");
    }

    private void voidBramble(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> player.level().setBlockAndUpdate(DEVICE.below(), Blocks.DIRT.defaultBlockState()));
        place(context, item("voidbramble"), DEVICE, "voidbramble", false);
        server(player -> { player.removeAllEffects(); player.teleportTo(0.5, 100, -2.5); player.setDeltaMovement(Vec3.ZERO); });
        final Vec3 before = serverValue(net.minecraft.world.entity.Entity::position);
        walkInto(context, player -> player.hasEffect(MobEffects.WEAKNESS) && player.hasEffect(MobEffects.SLOWNESS)
            && player.position().distanceToSqr(before) > 4.0, 120, "Entering owned Void Bramble must debuff and actually teleport the player");
        row.put("verified", "Native placement recorded ownership; ordinary movement into the bramble applied Weakness/Slowness and teleported the player through the normal collision path.");
        row.put("untested", "Cooldown expiry, non-owner breaking, cross-height destinations, nearby magic suppression consumers.");
    }

    private void dreamWeaver(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, item("dreamcatcher"), DEVICE, "dreamcatcher", false);
        final var before = serverValue(player -> player.level().getBlockState(DEVICE)
            .getValue(com.kadamitas.warlockery.block.DreamWeaverBlock.MODE));
        supply(context, 0, item("arcane_focus"));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        check(serverValue(player -> player.level().getBlockState(DEVICE)
            .getValue(com.kadamitas.warlockery.block.DreamWeaverBlock.MODE) == before.next()),
            "Native Arcane Focus use advances the Dream Weaver to its next real mode");
        row.put("verified", "Native placement and Arcane Focus use advanced the actual Dream Weaver mode from " + before + " to " + before.next() + ".");
        row.put("untested", "Every mode's sleep/nightmare probability effect, full cycle, wrong-focus response.");
    }

    private void walkInto(final ClientGameTestContext context, final Predicate<ServerPlayer> outcome, final int ticks, final String message) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { await(context, outcome, ticks, message); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
    }

    private static long blocks(final ServerPlayer player, final net.minecraft.world.level.block.Block block) {
        return BlockPos.betweenClosedStream(new BlockPos(-6, 99, -6), new BlockPos(6, 106, 6))
            .filter(pos -> player.level().getBlockState(pos).is(block)).count();
    }
    private void readGuides(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final List<String> requested = switch (id) {
            case "alluringskull" -> List.of("crafting_alluringskull", "device_alluring_skull");
            case "beartrap" -> List.of("crafting_beartrap", "device_bear_trap");
            case "plantmine" -> List.of("crafting_plantmine", "device_plant_mine");
            case "wickerbundle" -> List.of("crafting_wickerbundle", "device_wicker_bundle");
            case "demonheart" -> List.of("device_demon_heart");
            case "dreamcatcher" -> List.of("crafting_dreamcatcher", "device_dream_weaver");
            case "voidbramble" -> List.of("crafting_voidbramble", "device_void_bramble");
            case "daylightcollector" -> List.of("crafting_daylightcollector", "device_sun_collector");
            case "crystalball" -> List.of("device_crystal_ball", "rite_infuse_crystal_ball");
            default -> throw new AssertionError("Missing device guide " + id);
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
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key placement and interaction, normal server processing/hazard ticks. No outcome or progress injection.");
        report.put("class_sha256", Map.of("test", classHash(OtherDevicesAbilitiesClientAcceptance.class),
            "alluring_skull", classHash(com.kadamitas.warlockery.block.AlluringSkullBlock.class),
            "bear_trap", classHash(com.kadamitas.warlockery.block.BearTrapBlock.class),
            "plant_mine", classHash(com.kadamitas.warlockery.block.PlantMineBlock.class),
            "dream_weaver", classHash(com.kadamitas.warlockery.block.DreamWeaverBlock.class),
            "sun_collector", classHash(com.kadamitas.warlockery.block.SunCollectorBlock.class),
            "utility_devices", classHash(com.kadamitas.warlockery.block.UtilityDeviceBlockFactory.class)));
        report.put("devices", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("other-devices-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("other-devices-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
