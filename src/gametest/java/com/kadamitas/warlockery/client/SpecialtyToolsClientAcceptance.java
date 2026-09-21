package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.CritterSnareBlock;
import com.kadamitas.warlockery.block.CritterSnarePayload;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SpectralStoneState;
import com.kadamitas.warlockery.registry.ModBlocks;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Rendered-client checks for specialty tools whose useful behavior is not apparent from their item names. */
public final class SpecialtyToolsClientAcceptance implements FabricClientGameTest {
    private static final BlockPos SNARE_ONE = new BlockPos(0, 100, 3);
    private static final BlockPos SNARE_TWO = new BlockPos(2, 100, 3);
    private static final BlockPos SAFE_POPPY = new BlockPos(-2, 100, 3);
    private static final BlockPos UNSAFE_POPPY = new BlockPos(2, 100, 3);
    private static final AABB ARENA = new AABB(-12, 96, -12, 13, 112, 13);
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
            .resolve("specialty-tools").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                runCase(context, "warlockery:crittersnare", "ingredient_book_herbology", "plant_critter_snare",
                    this::critterSnare);
                runCase(context, "warlockery:ingredient_necro_stone", "ingredient_book_burning",
                    "ingredient_necro_stone", this::necromanticStone);
                runCase(context, "warlockery:spectralstone", "ingredient_book_burning", "spectralstone",
                    this::spectralStone);
                runCase(context, "warlockery:boline", "ingredient_book_herbology", "boline",
                    this::bolineSafeHarvest);
            }
            writeReport(failures.isEmpty());
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n", failures));
            }
            System.out.println("WARLOCKERY_SPECIALTY_TOOLS_PASSED " + evidence);
        } catch (Throwable failure) {
            try {
                writeReport(false);
                Files.writeString(evidence.resolve("failure.txt"), failure.toString());
            } catch (Exception capture) {
                failure.addSuppressed(capture);
            }
            throw new AssertionError("Specialty-tool client acceptance evidence: " + evidence, failure);
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
            readGuide(context, bookId, section, row);
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

    private void readGuide(
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_9);
        look(context, new Vec3(0.5, 96, 5));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))),
            "Native manual search and buttons must open " + section);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString());
        check(!body.isBlank(), "The opened specialty-tool instructions must not be blank");
        row.put("book_text", body);
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot observe specialty-tool book pages", failure);
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        waitServer(context, player -> player.getInventory().getSelectedSlot() == 0, 30,
            "Native hotbar input must return from the book to the specialty tool");
    }

    private void critterSnare(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModBlocks.ALL.get("crittersnare").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(SNARE_ONE.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(SNARE_ONE).is(ModBlocks.ALL.get("crittersnare").get()),
            30, "Native block use must place the empty Critter Snare");

        final UUID capturedBat = stageMob("minecraft:bat", Vec3.atCenterOf(SNARE_ONE));
        waitServer(context, player -> player.level().getBlockState(SNARE_ONE).getValue(CritterSnareBlock.PAYLOAD)
                == CritterSnarePayload.BAT && player.level().getEntity(capturedBat) == null,
            40, "A live bat entering the placed snare must be captured and removed from the world");
        look(context, Vec3.atCenterOf(SNARE_ONE));
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        try {
            waitServer(context, player -> player.level().getBlockState(SNARE_ONE).isAir(), 80,
                "Native mining must break the filled Critter Snare");
        } finally {
            context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        }
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try {
            waitServer(context, player -> inventorySlot(player, "crittersnare") >= 0, 60,
                "Native movement must collect the filled snare item after breaking it");
        } finally {
            context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        }
        final int slot = serverValue(player -> inventorySlot(player, "crittersnare"));
        check(slot >= 0 && slot < 9, "The carried filled snare must occupy a selectable hotbar slot");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + slot);
        waitServer(context, player -> player.getInventory().getSelectedSlot() == slot, 30,
            "Native hotbar input must select the carried filled snare");
        look(context, Vec3.atCenterOf(SNARE_TWO.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(SNARE_TWO).is(ModBlocks.ALL.get("crittersnare").get())
                && player.level().getBlockState(SNARE_TWO).getValue(CritterSnareBlock.PAYLOAD) == CritterSnarePayload.BAT,
            30, "Native replacement must restore the captured bat payload from the carried item");
        check(serverValue(player -> player.getMainHandItem().isEmpty()),
            "Placing the carried snare must leave the hand empty for release");
        look(context, Vec3.atCenterOf(SNARE_TWO));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(SNARE_TWO).getValue(CritterSnareBlock.PAYLOAD)
                == CritterSnarePayload.EMPTY && entities(player, "minecraft:bat").size() == 1,
            30, "Empty-hand use must release the preserved bat and empty the placed snare");
        row.put("fixture", "Survival player, two supported placement sites and one staged live bat; no payload component or block state was written by the test");
        row.put("observed", "Native place, entity capture, break, pickup, carry, replace and empty-hand release all completed");
    }

    private void necromanticStone(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_necro_stone").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        final UUID first = stageMob("minecraft:zombie", new Vec3(0.5, 100, 3.5));
        useOnEntity(context, first);
        waitServer(context, player -> player.level().getEntity(first) instanceof Mob mob
                && CreatureBehaviorState.isOwnedBy(mob, player.getUUID())
                && mob.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
                && mob.getTarget() == null && mob.isPersistenceRequired()
                && player.getMainHandItem().getDamageValue() == 1,
            30, "Native Necromantic Stone use must bind, calm, persist and protect the commandable zombie while spending durability");

        final UUID otherOwner = UUID.randomUUID();
        final UUID claimed = serverValue(player -> {
            final Entity entity = createMob(player, "minecraft:zombie", new Vec3(3.5, 100, 0.5));
            check(CreatureBehaviorState.bind(entity, otherOwner), "Fixture must establish the second zombie's prior owner");
            return entity.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        useOnEntity(context, claimed);
        context.waitTicks(8);
        check(serverValue(player -> player.level().getEntity(claimed) instanceof Mob mob
                && CreatureBehaviorState.owner(mob).filter(otherOwner::equals).isPresent()
                && !mob.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
                && player.getMainHandItem().getDamageValue() == 1),
            "A native command attempt must not steal another owner's creature or spend durability");
        row.put("fixture", "Two live tagged undead: one unowned and one explicitly pre-owned by another UUID; no command result was injected");
        row.put("observed", "First zombie gained ownership, Resistance, persistence and one wear; pre-owned zombie retained its owner with no extra wear");
    }

    private void spectralStone(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("spectralstone").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        final Identifier spectre = Identifier.fromNamespaceAndPath("warlockery", "spectre");
        captureSpectral(context, spectre, 1);

        final UUID incompatible = stageMob("warlockery:banshee", new Vec3(0.5, 100, 3.5));
        useOnEntity(context, incompatible);
        context.waitTicks(8);
        check(serverValue(player -> player.level().getEntity(incompatible) != null
                && SpectralStoneState.read(player.getMainHandItem()).captured().equals(List.of(spectre))),
            "A partly filled Spectral Stone must reject a different spectral species without removing it");
        server(player -> player.level().getEntity(incompatible).discard());
        captureSpectral(context, spectre, 2);
        captureSpectral(context, spectre, 3);

        final UUID overflow = stageMob("warlockery:spectre", new Vec3(0.5, 100, 3.5));
        useOnEntity(context, overflow);
        context.waitTicks(8);
        check(serverValue(player -> player.level().getEntity(overflow) != null
                && SpectralStoneState.read(player.getMainHandItem()).captured().size() == 3),
            "A full Spectral Stone must reject a fourth matching spirit without removing it");
        server(player -> player.level().getEntity(overflow).discard());
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(8, 103, 0.5));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> SpectralStoneState.read(player.getMainHandItem()).captured().size() == 2
                && entities(player, "warlockery:spectre").stream()
                    .anyMatch(entity -> CreatureBehaviorState.isOwnedBy(entity, player.getUUID())
                        && entity instanceof Mob mob && mob.isPersistenceRequired() && mob.getTarget() == null),
            30, "Normal native use must release one stored Spectre as a persistent, calm creature bound to the player");
        row.put("fixture", "Three same-species Spectres, one incompatible Banshee and one overflow Spectre were staged sequentially; no stone state or capture/release result was written");
        row.put("observed", "Native capture reached capacity three, rejected mixed species and overflow, then normal use released one owned Spectre and left two stored");
    }

    private void bolineSafeHarvest(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> {
            final var server = player.level().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.setHealth(10.0F);
            player.damageCooldownTime = 0;
            player.removeAllEffects();
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("boline").get()));
            player.level().setBlockAndUpdate(SAFE_POPPY, ModBlocks.ALL.get("bloodrose").get().defaultBlockState());
            player.level().setBlockAndUpdate(UNSAFE_POPPY, ModBlocks.ALL.get("bloodrose").get().defaultBlockState());
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(SAFE_POPPY));
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        try {
            waitServer(context, player -> player.level().getBlockState(SAFE_POPPY).isAir(), 80,
                "Native Boline mining must break the staged Blood Poppy");
        } finally {
            context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        }
        waitServer(context, player -> bloodPoppyDrops(player, SAFE_POPPY) == 1, 30,
            "Safe Boline harvest must produce the Blood Poppy's ordinary block drop");
        check(serverValue(player -> player.getHealth() == 10.0F && player.getActiveEffects().isEmpty()
                && player.getInventory().getItem(0).getDamageValue() > 0),
            "Safe Boline harvest must leave health and effects unchanged while wearing the real tool");
        screenshot(context, "boline-safe-drop-no-harm");

        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);
        waitServer(context, player -> player.getInventory().getSelectedSlot() == 1
                && player.getMainHandItem().isEmpty(), 30,
            "Native hotbar input must expose an empty hand for the unsafe comparison");
        server(player -> player.damageCooldownTime = 0);
        final float healthBeforeUnsafe = serverValue(ServerPlayer::getHealth);
        row.put("before_unsafe", serverValue(player -> Map.of(
            "health", player.getHealth(),
            "absorption", player.getAbsorptionAmount(),
            "armor", player.getArmorValue(),
            "difficulty", player.level().getDifficulty().name(),
            "natural_regeneration", player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION),
            "invulnerable_time", player.damageCooldownTime,
            "effects", player.getActiveEffects().stream().map(Object::toString).toList()
        )));
        writeReport(false);
        look(context, Vec3.atCenterOf(UNSAFE_POPPY));
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        try {
            waitServer(context, player -> player.level().getBlockState(UNSAFE_POPPY).isAir(), 80,
                "Native empty-hand mining must break the comparison Blood Poppy");
        } finally {
            context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        }
        final float healthAfterUnsafe = serverValue(ServerPlayer::getHealth);
        row.put("after_unsafe", serverValue(player -> Map.of(
            "health", player.getHealth(),
            "damage", healthBeforeUnsafe - player.getHealth(),
            "invulnerable_time", player.damageCooldownTime,
            "effects", player.getActiveEffects().stream().map(Object::toString).toList(),
            "blood_poppy_drops", bloodPoppyDrops(player, UNSAFE_POPPY)
        )));
        writeReport(false);
        check(healthBeforeUnsafe == 10.0F && healthAfterUnsafe == 8.0F
                && serverValue(player -> player.getActiveEffects().isEmpty()
                && bloodPoppyDrops(player, UNSAFE_POPPY) == 0),
            "Unsafe empty-hand harvest must deal exactly two thorn damage, add no effect and suppress the ordinary drop");
        screenshot(context, "boline-unsafe-thorns-no-drop");
        row.put("fixture", "Two identical supported Blood Poppies and a Survival player at 10 health with no effects in Normal difficulty with natural regeneration disabled; no loot, damage or effect outcome was injected");
        row.put("observed", "Native Boline harvest dropped one Blood Poppy with no harm and tool wear; native empty-hand harvest dealt exactly 2 damage and dropped none");
    }

    private void captureSpectral(final ClientGameTestContext context, final Identifier type, final int expected) {
        final UUID target = stageMob(type.toString(), new Vec3(0.5, 100, 3.5));
        useOnEntity(context, target);
        waitServer(context, player -> player.level().getEntity(target) == null
                && SpectralStoneState.read(player.getMainHandItem()).captured().size() == expected
                && SpectralStoneState.read(player.getMainHandItem()).captured().stream().allMatch(type::equals),
            30, "Native Spectral Stone interaction must capture same-species spirit " + expected);
    }

    private UUID stageMob(final String typeId, final Vec3 position) {
        final UUID id = serverValue(player -> createMob(player, typeId, position).getUUID());
        world.getConnection().waitForClientboundPackets();
        return id;
    }

    private static Entity createMob(final ServerPlayer player, final String typeId, final Vec3 position) {
        final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId))
            .create(player.level(), EntitySpawnReason.COMMAND);
        check(entity instanceof LivingEntity, "Fixture must create living entity " + typeId);
        entity.setPos(position.x, position.y, position.z);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        player.level().addFreshEntity(entity);
        return entity;
    }

    private void useOnEntity(final ClientGameTestContext context, final UUID target) {
        final Vec3 position = serverValue(player -> player.level().getEntity(target).getBoundingBox().getCenter());
        look(context, position);
        context.waitFor(client -> client.hitResult instanceof EntityHitResult hit
            && hit.getEntity().getUUID().equals(target), 30);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
            for (BlockPos pos : BlockPos.betweenClosed(-5, 99, -5, 5, 103, 7)) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
    }

    private static int inventorySlot(final ServerPlayer player, final String id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.ALL.get(id).get())) {
                return slot;
            }
        }
        return -1;
    }

    private static List<Entity> entities(final ServerPlayer player, final String typeId) {
        final var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId));
        return player.level().getEntities((Entity) null, ARENA, entity -> entity.getType() == type);
    }

    private static int bloodPoppyDrops(final ServerPlayer player, final BlockPos origin) {
        return player.level().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(origin).inflate(1.5D),
            item -> item.getItem().is(ModItems.ALL.get("bloodrose").get())
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
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
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
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
            + "Creatures and terrain are staged prerequisites; item use, block placement/breaking, capture, release and ownership outcomes are not injected.");
        report.put("scenarios", results);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        Files.writeString(evidence.resolve("specialty-tools.json"),
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
