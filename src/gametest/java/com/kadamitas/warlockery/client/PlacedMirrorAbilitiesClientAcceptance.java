package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.InteractiveUtilityBlock;
import com.kadamitas.warlockery.block.MagicMirrorMemory;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Native placed-mirror controls, with read-only observation of their resulting state. */
public final class PlacedMirrorAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> MIRRORS = List.of("mirrorblock", "mirrorblock2", "mirrorwall");
    private static final BlockPos MIRROR = new BlockPos(0, 100, 0);
    private static final BlockPos PARTNER = new BlockPos(8, 100, 0);
    private static final BlockPos ALTAR = new BlockPos(-5, 100, 2);
    private static final Vec3 START = new Vec3(.5, 100, -2.5);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile ReflectionObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("placed-mirror-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final String configured = System.getProperty("warlockery.placedMirrorIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(MIRRORS) : Arrays.stream(configured.split(","))
                .map(String::strip).map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && MIRRORS.containsAll(requested), "Requested mirrors have native scenarios");
            for (String id : MIRRORS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                row.put("distinct_visitor_history_status", "NOT_RUN");
                row.put("combat_damage_status", "NOT_RUN");
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : MIRRORS) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readGuide(context, id, row);
                        place(context, id, MIRROR, 0);
                        row.put("placement_status", "PASSED");
                        final UUID witness = witness();
                        reading(context, id, witness, row);
                        replication(context, id, row);
                        masquerade(context, id, witness, row);
                        pairing(context, id, row);
                        blockedPartner(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        if (observation != null) row.put("reflection_observation", serverValue(player -> observation.report()));
                        observation = null;
                        write(false);
                    }
                } catch (Throwable failure) { fail(id, row, failure); }
                finally { observation = null; world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_PLACED_MIRROR_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stackTrace(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native placed mirror evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.HARD, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            player.setHealth(100); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(12, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world; platform, input items, a named cow and later six altar blocks/reserve staged. Actor has 100 maximum health with ordinary damage enabled. Mirror placement, readings, memory entries, samples, outputs, names, travel and reflections arise only from native mouse/key actions.");
        row.put("not_run", List.of("crafting acquisition", "save/reload persistence", "multiple distinct visitors", "own-sample masquerade restoration", "goblet sample", "reflection combat damage", "pair range boundary and unloaded partner"));
    }

    private UUID witness() {
        return serverValue(player -> {
            final Cow cow = (Cow) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(cow != null, "Cow prerequisite exists");
            cow.setPos(3.5, 100, -.5); cow.setNoAi(true); cow.setPersistenceRequired();
            cow.setCustomName(Component.literal("Mirror Witness")); cow.setHealth(cow.getMaxHealth()); cow.setAbsorptionAmount(40);
            player.level().addFreshEntity(cow); return cow.getUUID();
        });
    }

    private void reading(final ClientGameTestContext context, final String id, final UUID witness, final Map<String, Object> row) throws Exception {
        emptyHand(context);
        check(serverValue(player -> memory(player).isEmpty()), "No mirror visitor is preloaded");
        final String expected = translated(context, "message.warlockery.mirror.fairest", Component.literal("Mirror Witness"),
            Component.translatable("message.warlockery.spirit_locator.direction.east"));
        for (int visit = 0; visit < 2; visit++) {
            clearChat(context); use(context, Vec3.atCenterOf(MIRROR), MIRROR);
            awaitChat(context, expected);
            check(serverValue(player -> memory(player).equals(List.of(player.getName().getString()))),
                "Actual empty-hand use records one visitor and repeat use deduplicates that visitor");
        }
        row.put("reading_status", "PASSED"); row.put("memory_retention_dedup_status", "PASSED");
        row.put("fairest_message", expected); row.put("fairest_target_uuid", witness.toString());
        row.put("visitor_memory", serverValue(PlacedMirrorAbilitiesClientAcceptance::memory));
        row.put("reading_fixture", "Named full-health cow with 40 absorption has higher fairness than the full-health player and stands east of the mirror. Memory observation only reads the resulting saved-data map.");
        screenshot(context, id + "-reading-and-memory");
    }

    @SuppressWarnings("unchecked")
    private static List<String> memory(final ServerPlayer player) {
        final Map<Long, List<String>> visitors = (Map<Long, List<String>>) field(MagicMirrorMemory.get(player.level()), "visitors");
        return List.copyOf(visitors.getOrDefault(MIRROR.asLong(), List.of()));
    }

    private void replication(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        supply(context, 1, item("ingredient_quartz_sphere"));
        check(serverValue(player -> AltarPowerNetwork.available(player.level(), MIRROR) == 0), "Unpowered fixture has no reachable altar reserve");
        use(context, Vec3.atCenterOf(MIRROR), MIRROR);
        awaitOverlay(context, translated(context, "message.warlockery.mirror.needs_power"));
        check(serverValue(player -> count(player, item("ingredient_quartz_sphere")) == 1 && count(player, item("replication_charge")) == 0),
            "Unpowered native use preserves the sphere and creates no charge");
        row.put("unpowered_refusal_status", "PASSED");
        screenshot(context, id + "-sphere-unpowered-refusal");
        server(player -> {
            for (BlockPos pos : altarPositions()) player.level().setBlockAndUpdate(pos, ModBlocks.ALTAR.get().defaultBlockState());
        });
        context.waitTicks(41);
        check(serverValue(player -> altarPositions().stream().allMatch(pos -> altar(player, pos).isMultiblockValid())), "Six actual altar blocks form a valid multiblock");
        look(context, Vec3.atCenterOf(MIRROR)); assertBlockPointer(context, MIRROR);
        for (int tick = 0; tick < 45 && serverValue(player -> player.level().getGameTime() % 40) > 5; tick++) context.waitTicks(1);
        check(serverValue(player -> player.level().getGameTime() % 40) <= 5, "Power exchange starts before the next recharge boundary");
        server(player -> {
            for (BlockPos pos : altarPositions()) {
                final var altar = altar(player, pos); check(altar.consumePower(altar.availablePower()), "Clear prerequisite reserve");
            }
            check(altar(player, ALTAR).receivePower(500) == 500, "Stage exactly 500 spendable altar power");
        });
        final long beforeTick = serverValue(player -> player.level().getGameTime());
        check(serverValue(player -> totalPower(player) == 500 && AltarPowerNetwork.available(player.level(), MIRROR) == 500), "Exactly 500 total power is reachable before native use");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        await(context, player -> count(player, item("replication_charge")) == 1, 12, "Native powered mirror use creates one Replication Charge");
        final long afterTick = serverValue(player -> player.level().getGameTime());
        check(beforeTick / 40 == afterTick / 40, "Exact power observation excludes natural recharge");
        check(serverValue(player -> count(player, item("ingredient_quartz_sphere")) == 0 && count(player, item("replication_charge")) == 1
            && totalPower(player) == 0), "One sphere and exactly 500 actual power become exactly one charge");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.replication_captured"));
        row.put("powered_conversion_status", "PASSED");
        row.put("conversion", Map.of("sphere_before", 1, "sphere_after", 0, "charge_before", 0, "charge_after", 1,
            "total_altar_power_before", 500, "total_altar_power_after", 0, "game_tick_before", beforeTick, "game_tick_after", afterTick));
        row.put("power_fixture", "Six staged altar blocks form naturally; every reserve is cleared, then one receives 500 through the existing power API. Native exchange is observed within one 40-tick recharge interval.");
        screenshot(context, id + "-sphere-powered-conversion");
    }

    private static List<BlockPos> altarPositions() {
        final List<BlockPos> result = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) result.add(ALTAR.offset(x, 0, z));
        return result;
    }
    private static AltarBlockEntity altar(final ServerPlayer player, final BlockPos pos) { return (AltarBlockEntity) player.level().getBlockEntity(pos); }
    private static int totalPower(final ServerPlayer player) { return altarPositions().stream().mapToInt(pos -> altar(player, pos).getPower()).sum(); }

    private void masquerade(final ClientGameTestContext context, final String id, final UUID witness, final Map<String, Object> row) throws Exception {
        supply(context, 2, item("sympathetic_vial"));
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).isEmpty() && player.getCustomName() == null), "Vial and actor start without an injected sample or disguise");
        position(context, new Vec3(3.5, 100, -2.5));
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
            .anyMatch(entity -> entity.getUUID().equals(witness)), 40);
        look(context, serverValue(player -> player.level().getEntity(witness).getBoundingBox().getCenter()));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(witness)), "Native pointer hits the named cow for sampling");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(witness)
            && binding.targetName().equals("Mirror Witness")).isPresent(), 20, "Native vial use captures the exact living subject");
        position(context, START); use(context, Vec3.atCenterOf(MIRROR), MIRROR);
        await(context, player -> player.getCustomName() != null && player.getCustomName().getString().equals("Mirror Witness"), 20,
            "Native mirror use applies the sampled subject's display name");
        check(serverValue(player -> count(player, item("sympathetic_vial")) == 0
            && WarlockeryEntityData.get(player).getStringOr("WarlockeryMirrorMasquerade", "").equals("Mirror Witness")),
            "Mirror consumes one native sample and records its resulting masquerade");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.masquerade_applied", "Mirror Witness"));
        row.put("native_vial_masquerade_status", "PASSED"); row.put("sampled_subject_uuid", witness.toString());
        screenshot(context, id + "-native-vial-masquerade");
    }

    private void pairing(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final String partner = MIRRORS.get((MIRRORS.indexOf(id) + 1) % MIRRORS.size());
        position(context, new Vec3(8.5, 100, -2.5)); place(context, partner, PARTNER, 3);
        position(context, START); emptyHand(context);
        check(serverValue(player -> player.level().getBlockState(PARTNER.above()).isAir() && player.level().getBlockState(PARTNER.above(2)).isAir()), "Different mirror design has two clear arrival blocks");
        crouchUse(context);
        final Vec3 destination = new Vec3(8.5, 101, .5);
        await(context, player -> player.position().distanceTo(destination) < .15, 20, "Native crouch use travels eight blocks to the different mirror design");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.paired_travel"));
        row.put("cross_design_pairing_status", "PASSED");
        row.put("paired_travel", Map.of("source", id, "destination", partner, "mirror_distance", 8,
            "actual_position", serverValue(player -> player.position().toString())));
        screenshot(context, id + "-native-cross-design-travel");
    }

    private void blockedPartner(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        position(context, START); emptyHand(context);
        server(player -> {
            player.level().setBlockAndUpdate(PARTNER.above(), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(PARTNER.above(2), Blocks.STONE.defaultBlockState());
            observation = new ReflectionObservation(player);
        });
        crouchUse(context);
        await(context, player -> observation.reflections.size() == 1, 20, "A blocked usable-range partner causes one actual native Glass Doppelganger summon");
        check(serverValue(player -> {
            final Map<String, Object> spawned = observation.reflections.values().iterator().next();
            return player.getStringUUID().equals(spawned.get("combat_target")) && player.getStringUUID().equals(spawned.get("reflected_target"))
                && Boolean.TRUE.equals(spawned.get("normal_ai")) && Boolean.TRUE.equals(spawned.get("hostile_category"));
        }), "Actual mirror summon assigns the activating player to the live hostile reflection without fixture targeting");
        check(serverValue(player -> observation.reflections.keySet().stream().allMatch(uuid -> player.level().getEntity(uuid) instanceof Mob mob && mob.isAlive())
            && player.position().distanceTo(START) < 1), "Reflection remains alive and blocked partner does not teleport the player");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.reflection_emerged"));
        row.put("blocked_partner_reflection_status", "PASSED");
        row.put("reflection_fixture", "The previously usable partner remains placed; two staged stone blocks obstruct its arrival space. Origin remains clear. Passive entity-load observation records the real spawn before its ordinary mimic AI can change targeting; no attack damage is claimed.");
        screenshot(context, id + "-blocked-partner-native-reflection");
    }

    private void crouchUse(final ClientGameTestContext context) {
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        try { use(context, Vec3.atCenterOf(MIRROR), MIRROR); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(2); }
    }
    private static final class ReflectionObservation {
        final ServerPlayer player;
        final Map<UUID, Map<String, Object>> reflections = new LinkedHashMap<>();
        ReflectionObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof Mob mob && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("warlockery:glass_doppelganger"))
                reflections.put(entity.getUUID(), Map.of("type", "warlockery:glass_doppelganger", "normal_ai", !mob.isNoAi(),
                    "hostile_category", entity.getType().getCategory() == MobCategory.MONSTER,
                    "combat_target", mob.getTarget() == null ? "none" : mob.getTarget().getStringUUID(),
                    "reflected_target", WarlockeryEntityData.get(mob).getStringOr("WarlockeryReflectedTarget", ""),
                    "name", mob.getDisplayName().getString(), "position", mob.position().toString()));
        }
        Map<String, Object> report() { return Map.of("actual_spawns", Map.copyOf(reflections), "observer", "Passive server entity-load event, no behavior injection"); }
    }

    private void readGuide(final ClientGameTestContext context, final String section, final Map<String, Object> row) throws Exception {
        final var profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(section)).findFirst().orElseThrow();
        supply(context, 0, item(profile.id()));
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
        check(!body.isBlank() && pages > 0, "Indexed placed mirror instructions contain actual pages");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit every mirror guide page");
            screenshot(context, section + "-guide-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages)); row.put("guide_status", "PASSED");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTicks(3);
    }

    private void place(final ClientGameTestContext context, final String id, final BlockPos pos, final int slot) {
        supply(context, slot, item(id));
        use(context, new Vec3(pos.getX() + .5, pos.getY() - .001, pos.getZ() + .5), pos.below());
        await(context, player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString().equals("warlockery:" + id), 30,
            "Native placement creates exact mirror design " + id);
        check(serverValue(player -> player.getInventory().getItem(slot).isEmpty()), "Survival placement consumes exactly one mirror item");
    }
    private void supply(final ClientGameTestContext context, final int slot, final Item item) {
        server(player -> { player.getInventory().setItem(slot, new ItemStack(item)); player.inventoryMenu.broadcastChanges(); }); sync(context, slot);
    }
    private void emptyHand(final ClientGameTestContext context) {
        sync(context, 8); check(serverValue(player -> player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()), "Both hands are actually empty");
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
    private static void assertBlockPointer(final ClientGameTestContext context, final BlockPos expected) {
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)), "Native pointer targets intended block " + expected);
    }
    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        look(context, point); assertBlockPointer(context, expected); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
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
        check(chat(context).contains(expected), "Rendered chat receives expected mirror reading: " + expected + "; actual=" + chat(context));
    }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value); });
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports mirror result: " + expected + "; actual=" + overlay(context));
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
    private static String stackTrace(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", stackTrace(failure)); failures.add(id + ": " + failure);
    }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished); report.put("selected_native_scenarios_passed", finished && failures.isEmpty());
        report.put("all_three_native_scenarios_passed", finished && results.size() == MIRRORS.size() && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites with real mouse/key manual, placement, block and living-entity interactions. Resulting memory, binding, items, power, travel and summons are observed without outcome injection.");
        report.put("class_sha256", Map.of("test", classHash(PlacedMirrorAbilitiesClientAcceptance.class), "mirror_block", classHash(InteractiveUtilityBlock.class),
            "memory", classHash(MagicMirrorMemory.class), "altar", classHash(AltarBlockEntity.class),
            "vial", classHash(com.kadamitas.warlockery.item.SympatheticVialItem.class),
            "reflection", classHash(com.kadamitas.warlockery.entity.GlassDoppelgangerEntity.class)));
        report.put("mirrors", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("placed-mirror-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("placed-mirror-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
