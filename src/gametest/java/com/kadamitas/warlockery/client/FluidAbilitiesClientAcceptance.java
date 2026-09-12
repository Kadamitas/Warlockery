package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import com.kadamitas.warlockery.item.ManualProfile;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Rendered native-input checks for every registered arcane-fluid bucket and its contact behavior. */
public final class FluidAbilitiesClientAcceptance implements FabricClientGameTest {
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
            .resolve("fluid-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                runCase(context, "warlockery:bucketspirit", "cauldronbook", "bucketspirit", this::spirit);
                runCase(context, "warlockery:buckethollowtears", "cauldronbook",
                    "buckethollowtears", this::hollowTears);
                runCase(context, "warlockery:bucketerosionbrew", "cauldronbook",
                    "bucketerosionbrew", this::erosion);
                runCase(context, "warlockery:bucketbrew", "cauldronbook", "bucketbrew", this::coloredBrewWater);
            }
            writeReport(failures.isEmpty());
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n", failures));
            }
            System.out.println("WARLOCKERY_FLUID_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try {
                writeReport(false);
                Files.writeString(evidence.resolve("failure.txt"), failure.toString());
            } catch (Exception capture) {
                failure.addSuppressed(capture);
            }
            throw new AssertionError("Fluid ability evidence: " + evidence, failure);
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
        check(!body.isBlank(), "The opened fluid instructions must not be blank");
        row.put("book_text", body);
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot observe fluid book pages", failure);
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
        return switch (Identifier.parse(itemId).getPath()) {
            case "bucketspirit" -> guide.contains("Regeneration I") && guide.contains("Weakness II")
                ? "PRESENT: exact creation route, pour/collect controls, contact split and portal-frame constraint"
                : "MISSING_OR_CONFUSING: Spirit Bucket effects or controls are incomplete";
            case "buckethollowtears" -> guide.contains("Resistance I") && guide.contains("Mining Fatigue I")
                ? "PRESENT: exact distilling route, pour/collect controls and beneficiary/victim outcomes"
                : "MISSING_OR_CONFUSING: Hollow Tears effects or controls are incomplete";
            case "bucketerosionbrew" -> guide.contains("2 magic damage") && guide.contains("durability wear")
                ? "PRESENT: source route, pour/collect controls, damage, wear and marker danger"
                : "MISSING_OR_CONFUSING: Erosion fluid danger or controls are incomplete";
            case "bucketbrew" -> guide.contains("no direct contact effect")
                ? "PRESENT: exact cauldron route, pour/collect controls and inert world-contact constraint"
                : "MISSING_OR_CONFUSING: Colored Brew Water world behavior is incomplete";
            default -> throw new AssertionError(itemId);
        };
    }

    private void spirit(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID cow = stageMob("minecraft:cow", new Vec3(1.5, 100, 3.5));
        final UUID zombie = stageMob("minecraft:zombie", new Vec3(0.5, 100, 4.5));
        final UUID nightmare = stageMob("warlockery:nightmare", new Vec3(-0.5, 100, 3.5));
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(-1, 103, 3, 1, 103, 4)) {
                player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
        });
        pour(context, "bucketspirit", ModFluids.SPIRIT_SOURCE.get());
        waitServer(context, player -> living(player, cow).hasEffect(MobEffects.REGENERATION)
                && living(player, zombie).hasEffect(MobEffects.WEAKNESS)
                && living(player, zombie).getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                && living(player, nightmare).hasEffect(MobEffects.WEAKNESS)
                && living(player, nightmare).getEffect(MobEffects.WEAKNESS).getAmplifier() == 1,
            100, "Actual Flowing Spirit contact must regenerate ordinary life and weaken both undead and nightmares");
        collect(context, "bucketspirit", ModFluids.SPIRIT_SOURCE.get());
        row.put("fixture", "Stationary Cow, roofed Zombie and Nightmare occupy three naturally reached adjacent flow cells; no effect or fluid outcome was injected");
        row.put("observed", "Cow gained Regeneration I; Zombie and Nightmare gained Weakness II; the native bucket source was collected back");
        row.put("remaining", "Spirit Portal frame creation is NOT RUN; demonic beneficiary variants, effect refresh cadence, source acquisition and persistence remain.");
    }

    private void hollowTears(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID zombie = stageMob("minecraft:zombie", new Vec3(1.5, 100, 3.5));
        server(player -> {
            final LivingEntity beneficiary = living(player, zombie);
            beneficiary.setHealth(10.0F);
            beneficiary.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.level().setBlockAndUpdate(new BlockPos(1, 103, 3), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, 4), Blocks.STONE.defaultBlockState());
        });
        pour(context, "buckethollowtears", ModFluids.HOLLOW_TEARS_SOURCE.get());
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try {
            waitServer(context, player -> player.hasEffect(MobEffects.WEAKNESS)
                    && player.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                    && player.hasEffect(MobEffects.MINING_FATIGUE)
                    && living(player, zombie).getHealth() > 10.0F
                    && living(player, zombie).hasEffect(MobEffects.RESISTANCE),
                100, "Native movement into Hollow Tears must harm the player while adjacent fluid contact heals and protects undead");
        } finally {
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
        }
        server(player -> player.teleportTo(0.5, 100, 0.5));
        world.getConnection().waitForClientboundPackets();
        collect(context, "buckethollowtears", ModFluids.HOLLOW_TEARS_SOURCE.get());
        row.put("fixture", "Wounded roofed Zombie in an adjacent flow cell and the rendered Survival player walking natively into the source; no effect, healing or fluid outcome was injected");
        row.put("observed", "Zombie healed and gained Resistance I; player gained Weakness II and Mining Fatigue I; source was collected back");
        row.put("remaining", "Demon beneficiary, effect expiry/refresh cadence, non-player neutral control, acquisition and persistence.");
    }

    private void erosion(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID cow = stageMob("minecraft:cow", new Vec3(1.5, 100, 3.5));
        server(player -> {
            final LivingEntity target = living(player, cow);
            target.setHealth(20.0F);
            target.invulnerableTime = 0;
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        });
        pour(context, "bucketerosionbrew", ModFluids.EROSION_SOURCE.get());
        waitServer(context, player -> living(player, cow).getHealth() < 20.0F
                && living(player, cow).getItemBySlot(EquipmentSlot.HEAD).getDamageValue() >= 1
                && BrewMarkerState.remainingTicks(living(player, cow), BrewMarkerKind.EROSION) > 0,
            100, "Actual Erosion fluid contact must damage the creature, wear real equipment and apply its erosion marker");
        row.put("contact", serverValue(player -> Map.of(
            "health", living(player, cow).getHealth(),
            "helmet_wear", living(player, cow).getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),
            "erosion_ticks", BrewMarkerState.remainingTicks(living(player, cow), BrewMarkerKind.EROSION)
        )));
        collect(context, "bucketerosionbrew", ModFluids.EROSION_SOURCE.get());
        row.put("fixture", "Full-health stationary Cow wearing an undamaged iron helmet in a naturally reached adjacent flow cell; no damage, wear, marker or fluid outcome was injected");
        row.put("observed", "Fluid contact reduced health, wore the helmet and applied the real Erosion marker; source was collected back");
        row.put("remaining", "Exact repeated-contact cadence, all equipment slots, death/breakage boundary, acquisition and persistence.");
    }

    private void coloredBrewWater(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID cow = stageMob("minecraft:cow", new Vec3(1.5, 100, 3.5));
        server(player -> {
            final LivingEntity target = living(player, cow);
            target.setHealth(10.0F);
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        });
        pour(context, "bucketbrew", ModFluids.COLORED_BREW_WATER_SOURCE.get());
        waitServer(context, player -> !player.level().getFluidState(living(player, cow).blockPosition()).isEmpty(),
            40, "Colored Brew Water must naturally flow into the staged target cell");
        context.waitTicks(45);
        check(serverValue(player -> living(player, cow).getHealth() == 10.0F
                && living(player, cow).getActiveEffects().isEmpty()
                && living(player, cow).getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 0
                && BrewMarkerState.remainingTicks(living(player, cow), BrewMarkerKind.EROSION) == 0),
            "Colored Brew Water world contact must remain inert rather than borrowing another arcane fluid's effects");
        collect(context, "bucketbrew", ModFluids.COLORED_BREW_WATER_SOURCE.get());
        row.put("fixture", "Wounded stationary Cow with an undamaged iron helmet in a naturally reached adjacent flow cell; no contact outcome or fluid was injected");
        row.put("observed", "After more than two contact cadences, health, effects, helmet and Erosion marker remained unchanged; source was collected back");
        row.put("remaining", "Actual cauldron transfer/custom-brew mixing is NOT RUN; color/component transfer, acquisition and persistence remain.");
    }

    private void pour(
        final ClientGameTestContext context,
        final String itemId,
        final net.minecraft.world.level.material.Fluid fluid
    ) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(itemId).get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(FLUID_POS.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getFluidState(FLUID_POS).getType() == fluid
                && player.level().getFluidState(FLUID_POS).isSource()
                && player.getMainHandItem().is(Items.BUCKET),
            30, "Native " + itemId + " use must place its exact source fluid and leave an empty bucket");
    }

    private void collect(
        final ClientGameTestContext context,
        final String itemId,
        final net.minecraft.world.level.material.Fluid fluid
    ) {
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(FLUID_POS));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getMainHandItem().is(ModItems.ALL.get(itemId).get())
                && player.getMainHandItem().getCount() == 1
                && !player.level().getFluidState(FLUID_POS).isSource(),
            30, "Native empty-bucket use must collect the source back into " + itemId);
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        check(entity instanceof LivingEntity, "Expected live fixture entity " + id);
        return (LivingEntity) entity;
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
        report.put("execution", "Rendered Fabric client; native book search/buttons, native bucket pours and collections, native player movement, and normal fluid contact ticks. "
            + "Entities, health and equipment are staged prerequisites; fluid placement, collection, healing, effects, damage, wear and marker outcomes are not injected.");
        report.put("covered_ids", results.keySet());
        report.put("remaining_ids", List.of());
        report.put("scenarios", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        Files.writeString(evidence.resolve("fluid-abilities.json"),
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
