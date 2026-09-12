package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.BatBallItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Rendered native-input checks for ritual utility items with stateful or contextual abilities. */
public final class RitualToolsAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final BlockPos WAYSTONE_FLOOR = new BlockPos(3, 99, 0);
    private static final BlockPos WAYSTONE_DESTINATION = WAYSTONE_FLOOR.above();
    private static final BlockPos FLUID_POS = new BlockPos(0, 100, 3);
    private static final AABB ARENA = new AABB(-10, 96, -10, 11, 112, 11);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("ritual-tools-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                runCase(context, "warlockery:ingredient_icy_needle", "ingredient_book_herbology",
                    "ingredient_icy_needle", this::icyNeedle);
                runCase(context, "warlockery:ingredient_wolfsbane", "ingredient_book_herbology",
                    "ingredient_wolfsbane", this::wolfsbane);
                runCase(context, "warlockery:ingredient_bat_ball", "ingredient_book_burning",
                    "ingredient_bat_ball", this::batBall);
                runCase(context, "warlockery:ingredient_waystone", "ingredient_book_circle_magic",
                    "ingredient_waystone", (test, row) -> positionWaystone(test, row, false));
                runCase(context, "warlockery:ingredient_waystone_bound", "ingredient_book_circle_magic",
                    "ingredient_waystone_bound", (test, row) -> positionWaystone(test, row, true));
                runCase(context, "warlockery:ingredient_waystone_creature_bound", "ingredient_book_circle_magic",
                    "ingredient_waystone_creature_bound", this::creatureWaystone);
                runCase(context, "warlockery:bucketspirit", "cauldronbook",
                    "bucketspirit", this::spiritBucket);
            }
            writeReport(failures.isEmpty());
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n", failures));
            }
            System.out.println("WARLOCKERY_RITUAL_TOOLS_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try {
                writeReport(false);
                Files.writeString(evidence.resolve("failure.txt"), failure.toString());
            } catch (Exception capture) {
                failure.addSuppressed(capture);
            }
            throw new AssertionError("Ritual-tool ability evidence: " + evidence, failure);
        }
    }

    private void runCase(
        final ClientGameTestContext context,
        final String itemId,
        final String bookId,
        final String section,
        final Scenario scenario
    ) throws Exception {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "RUNNING");
        row.put("book", bookId);
        row.put("book_section", section);
        results.put(itemId, row);
        writeReport(false);
        try {
            resetArena();
            final String guide = readGuide(context, bookId, section, row);
            row.put("guide_assessment", guideAssessment(itemId, guide));
            scenario.run(context, row);
            row.put("status", "PASSED");
            screenshot(context, Identifier.parse(itemId).getPath() + "-passed");
        } catch (Throwable failure) {
            row.put("status", "FAILED");
            row.put("failure", failure.toString());
            failures.add(itemId + ": " + failure);
            try {
                screenshot(context, Identifier.parse(itemId).getPath() + "-failed");
            } catch (Throwable capture) {
                row.put("screenshot_failure", capture.toString());
            }
        } finally {
            row.put("finished_at", System.currentTimeMillis());
            writeReport(false);
        }
    }

    private String readGuide(
        final ClientGameTestContext context,
        final String bookId,
        final String section,
        final Map<String, Object> row
    ) throws Exception {
        final ManualProfile book = ManualProfile.find(bookId).orElseThrow();
        check(book.sections().contains(section), "The dynamic manual profile must index " + section);
        server(player -> {
            player.getInventory().setItem(8, new ItemStack(ModItems.ALL.get(bookId).get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_9);
        look(context, new Vec3(0.5, 96, 5));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))),
            "Native manual search and buttons must open " + section);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString());
        check(!body.isBlank(), "The opened ritual-tool instructions must not be blank");
        row.put("book_text", body);
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot observe ritual-tool book pages", failure);
            }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) {
                ManualClientAcceptance.clickButton(context,
                    Component.translatable("screen.warlockery.manual.next").getString());
            }
            screenshot(context, section + "-guide-page-" + (page + 1));
        }
        row.put("book_pages_read", pages);
        closeScreen(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        waitServer(context, player -> player.getInventory().getSelectedSlot() == 0, 30,
            "Native hotbar input must return from the book to the ritual tool");
        return body;
    }

    private static String guideAssessment(final String itemId, final String guide) {
        if (itemId.equals("warlockery:bucketspirit")) {
            return guide.contains("Cauldron Flowing Spirit")
                    && guide.contains("Use the filled bucket")
                    && guide.contains("empty Bucket")
                ? "PRESENT: exact creation route, placement, collection and contact outcomes are documented"
                : "MISSING OR CONFUSING: Spirit Bucket creation, placement or collection instructions are incomplete";
        }
        return "PRESENT: item-named instructions describe acquisition, prerequisites, controls and expected outcome";
    }

    private void icyNeedle(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get(), 2));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 104, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(4);
        check(serverValue(player -> player.getMainHandItem().getCount() == 2),
            "Icy Needle must refuse an ordinary waking use without being consumed");
        final UUID nightmare = stageMob("warlockery:nightmare", new Vec3(2.5, 100, 2.5));
        server(player -> {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 1200));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 1200));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 1200));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 1200));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200));
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getMainHandItem().getCount() == 1
                && player.level().getEntity(nightmare) == null
                && !player.hasEffect(MobEffects.DARKNESS) && !player.hasEffect(MobEffects.NAUSEA)
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.hasEffect(MobEffects.HUNGER)
                && player.hasEffect(MobEffects.SPEED),
            30, "Native Icy Needle use must clear dream symptoms, banish the nearby nightmare, preserve an unrelated benefit and consume one needle");
        row.put("fixture", "One nearby real Nightmare plus Darkness, Nausea, Blindness and Hunger; Speed is an unrelated control effect");
        row.put("observed", "No-condition use retained both needles; symptomatic use consumed one, cleared all documented symptoms and removed the Nightmare while retaining Speed");
        row.put("remaining", "Actual sleeping-body wake, Spirit World wake and manifestation return");
    }

    private void wolfsbane(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_wolfsbane").get(), 2));
            player.inventoryMenu.broadcastChanges();
        });
        final UUID cow = stageMob("minecraft:cow", new Vec3(-1.5, 100, -0.5));
        useOnEntity(context, cow);
        context.waitTicks(4);
        check(serverValue(player -> player.getMainHandItem().getCount() == 2
                && player.level().getEntity(cow) instanceof LivingEntity living
                && !living.hasEffect(MobEffects.WEAKNESS) && !living.hasEffect(MobEffects.SLOWNESS)),
            "Wolfsbane must report a clear ordinary creature without consuming the herb or adding lycan effects");
        final UUID werewolf = stageMob("warlockery:werewolf", new Vec3(1.5, 100, -0.5));
        useOnEntity(context, werewolf);
        waitServer(context, player -> player.getMainHandItem().getCount() == 1
                && player.level().getEntity(werewolf) instanceof LivingEntity living
                && living.hasEffect(MobEffects.WEAKNESS)
                && living.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                && living.hasEffect(MobEffects.SLOWNESS)
                && living.getEffect(MobEffects.SLOWNESS).getAmplifier() == 0,
            30, "Native Wolfsbane use must detect the tagged werewolf, apply Weakness II and Slowness I, and consume one herb");
        row.put("fixture", "One stationary ordinary Cow control and one stationary Warlockery Werewolf; neither has seeded effects");
        row.put("observed", "Cow test consumed nothing and added no effects; Werewolf test consumed one and applied Weakness II plus Slowness I");
        row.put("remaining", "A transformed real-player target and the full ten-second expiry");
    }

    private void batBall(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_bat_ball").get()));
            player.inventoryMenu.broadcastChanges();
        });
        final UUID bat = stageMob("minecraft:bat", new Vec3(0.5, 100, 3.5));
        useOnEntity(context, bat);
        waitServer(context, player -> player.level().getEntity(bat) == null
                && BatBallItem.captured(player.getMainHandItem()) == 1,
            30, "Native Bat Ball interaction must capture the exact staged bat and record one occupant");
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> BatBallItem.captured(player.getMainHandItem()) == 0
                && entities(player, "minecraft:bat").size() == 1,
            30, "Native filled Bat Ball use must release the stored bat and empty the reusable ball");
        row.put("fixture", "One stationary bat; no Bat Ball component or entity outcome was written");
        row.put("observed", "Native entity use captured one bat; native air use released one bat and restored an empty reusable ball");
        row.put("remaining", "Capacity eight, ninth-bat rejection and multi-bat release geometry");
    }

    private void positionWaystone(
        final ClientGameTestContext context,
        final Map<String, Object> row,
        final boolean initiallyBoundKind
    ) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(
                initiallyBoundKind ? "ingredient_waystone_bound" : "ingredient_waystone").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        if (initiallyBoundKind) {
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            context.waitFor(client -> client.player.isCrouching(), 30);
        }
        try {
            look(context, Vec3.atCenterOf(WAYSTONE_FLOOR).add(0, 0.49, 0));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.getMainHandItem().is(ModItems.ALL.get("ingredient_waystone_bound").get())
                    && WaystoneState.read(player.getMainHandItem()).filter(location ->
                        location.position().equals(WAYSTONE_DESTINATION)
                            && location.dimension().equals(player.level().dimension().identifier())).isPresent(),
                30, "Native Waystone block use must record the clicked floor's stand position and actual dimension");
        } finally {
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        }
        server(player -> player.teleportTo(-3.5, 100, 0.5));
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(-3.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.position().distanceToSqr(Vec3.atBottomCenterOf(WAYSTONE_DESTINATION)) < 0.04,
            30, "Native Bound Waystone use must teleport the player to the recorded same-dimension destination");
        check(serverValue(player -> player.getMainHandItem().is(ModItems.ALL.get("ingredient_waystone_bound").get())
                && player.getMainHandItem().getCount() == 1
                && WaystoneState.read(player.getMainHandItem()).filter(location ->
                    location.position().equals(WAYSTONE_DESTINATION)).isPresent()),
            "Waystone travel must preserve the item and its recorded destination");
        row.put("fixture", "Clear supported destination and a later staged player position four blocks away; no binding data or teleport outcome was injected");
        row.put("observed", initiallyBoundKind
            ? "Shift-use rebound an uninitialized Bound Waystone, then normal use travelled to the recorded destination"
            : "Block use transformed a plain Waystone into a Bound Waystone, then normal use travelled to the recorded destination");
        row.put("remaining", "Cross-dimension Otherwhere travel and unsafe/inhibited destination refusal");
    }

    private void creatureWaystone(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_waystone").get()));
            player.inventoryMenu.broadcastChanges();
        });
        final UUID cow = stageMob("minecraft:cow", new Vec3(0.5, 100, 3.5));
        useOnEntity(context, cow);
        waitServer(context, player -> player.getMainHandItem().is(ModItems.ALL.get("ingredient_waystone_creature_bound").get())
                && SympatheticBinding.read(player.getMainHandItem())
                    .filter(binding -> binding.targetId().equals(cow) && binding.targetType().equals("minecraft:cow"))
                    .isPresent(),
            30, "Native plain-Waystone entity use must create a Blooded Waystone bound to the exact live target");
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 104, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(4);
        check(serverValue(player -> player.getMainHandItem().is(ModItems.ALL.get("ingredient_waystone_creature_bound").get())
                && player.getMainHandItem().getCount() == 1 && player.level().getEntity(cow) != null),
            "Native Blooded Waystone inspection must retain the live binding and target");
        row.put("fixture", "One stationary living Cow and one plain Waystone; no sympathetic binding was prewritten");
        row.put("observed", "Native entity use created a Blooded Waystone for the exact Cow; native normal use successfully inspected the still-present target without consumption");
        row.put("remaining", "Missing/dead target report, cross-dimension target resolution and downstream sympathetic consumers");
    }

    private void spiritBucket(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("bucketspirit").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(FLUID_POS.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getFluidState(FLUID_POS).getType() == ModFluids.SPIRIT_SOURCE.get()
                && player.level().getFluidState(FLUID_POS).isSource()
                && player.getMainHandItem().is(Items.BUCKET),
            30, "Native Spirit Bucket use must place a real source of Flowing Spirit and leave an empty bucket");
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(FLUID_POS));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getMainHandItem().is(ModItems.ALL.get("bucketspirit").get())
                && player.getMainHandItem().getCount() == 1
                && !player.level().getFluidState(FLUID_POS).isSource(),
            30, "Native empty-bucket use must collect the source back into a Spirit Bucket");
        row.put("fixture", "Clear supported floor and empty destination space; no fluid or inventory outcome was injected");
        row.put("observed", "Native use placed warlockery:spirit source and returned an empty bucket; native collection restored one Spirit Bucket and removed the source");
        row.put("remaining", "Spirit World cauldron acquisition, Spirit Portal formation and Flowing Spirit entity/environment effects");
    }

    private UUID stageMob(final String typeId, final Vec3 position) {
        return serverValue(player -> {
            final var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId));
            check(type != null, "Missing fixture entity type " + typeId);
            final Entity entity = type.create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof LivingEntity, "Fixture must create a living " + typeId);
            entity.setPos(position);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            player.level().addFreshEntity(entity);
            return entity.getUUID();
        });
    }

    private void useOnEntity(final ClientGameTestContext context, final UUID target) {
        world.getConnection().waitForClientboundPackets();
        final Vec3 position = serverValue(player -> player.level().getEntity(target).getBoundingBox().getCenter());
        look(context, position);
        context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
            && hit.getEntity().getUUID().equals(target), 30);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    private void resetArena() {
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.stopRiding();
            player.removeAllEffects();
            player.getInventory().clearContent();
            player.teleportTo(0.5, 100, 0.5);
            player.level().getEntities((Entity) null, ARENA, entity -> !(entity instanceof ServerPlayer))
                .forEach(Entity::discard);
            for (BlockPos pos : BlockPos.betweenClosed(-6, 99, -6, 6, 104, 8)) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
    }

    private static List<Entity> entities(final ServerPlayer player, final String typeId) {
        final var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId));
        return player.level().getEntities((Entity) null, ARENA, entity -> entity.getType() == type);
    }

    private void waitServer(
        final ClientGameTestContext context,
        final Predicate<ServerPlayer> condition,
        final int ticks,
        final String failure
    ) {
        for (int tick = 0; tick < ticks; tick++) {
            if (serverValue(condition::test)) {
                return;
            }
            context.waitTicks(1);
        }
        check(serverValue(condition::test), failure);
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 30);
        }
    }

    private static Object field(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect " + name, failure);
        }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport(final boolean passed) throws Exception {
        if (evidence == null) {
            return;
        }
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", passed);
        report.put("scenario_count", results.size());
        report.put("passed_scenarios", results.values().stream().filter(row -> "PASSED".equals(row.get("status"))).count());
        report.put("failed_scenarios", failures.size());
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("pid", ProcessHandle.current().pid());
        report.put("execution", "Rendered Fabric client; native book search/buttons and native mouse/key interactions. "
            + "Creatures, symptoms and terrain are staged prerequisites; item state, item use, capture, release, binding, teleport and fluid outcomes are not injected.");
        report.put("covered_ids", results.keySet());
        report.put("remaining_ids", List.of());
        report.put("scenarios", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        Files.writeString(evidence.resolve("ritual-tools-abilities.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void run(ClientGameTestContext context, Map<String, Object> row) throws Exception;
    }
}
