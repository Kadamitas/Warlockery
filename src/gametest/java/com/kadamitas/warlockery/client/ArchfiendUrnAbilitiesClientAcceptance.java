package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ArchfiendsUrnState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native Archfiend's Urn storage, refusal, aimed casting and cooldown; the stored brews and their effects arise only from real input. */
public final class ArchfiendUrnAbilitiesClientAcceptance implements FabricClientGameTest {
    /** Eye-to-aim-block distance is ~4.3 blocks, inside the 4.5-block survival block reach (proven in run 1). */
    private static final Vec3 START = new Vec3(-.5, 100, .5);
    private static final BlockPos AIM = new BlockPos(3, 99, 0);
    private static final Vec3 TARGET = new Vec3(5.5, 100, .5);
    private static final Vec3 CONTROL = new Vec3(18.5, 100, .5);
    private static final String URN = "archfiends_urn";
    private static final String STORED = "brew_slow_movement";
    private static final List<String> EXTRA = List.of("brew_night_vision", "brew_fire_resistance", "brew_water_breathing");
    private static final String OVERFLOW = "brew_jump";
    private static final List<String> CASES = List.of("guide", "empty_refusal", "invalid_offhand_refusal", "store_brew",
        "duplicate_refusal", "aimed_cast", "cooldown", "capacity_refusal");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private UUID target;
    private UUID control;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("archfiend-urn-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            for (String id : CASES) { final Map<String, Object> row = new LinkedHashMap<>(); row.put("status", "NOT_RUN"); results.put(id, row); }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            write(false);
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                    stage(context);
                    run(context, "guide", row -> readGuide(context, URN, row));
                    run(context, "empty_refusal", row -> emptyRefusal(context, row));
                    run(context, "invalid_offhand_refusal", row -> invalidOffhand(context, row));
                    run(context, "store_brew", row -> store(context, row));
                    run(context, "duplicate_refusal", row -> duplicate(context, row));
                    run(context, "aimed_cast", row -> cast(context, row));
                    run(context, "cooldown", row -> cooldown(context, row));
                    run(context, "capacity_refusal", row -> capacity(context, row));
                } finally {
                    context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                    context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_ARCHFIEND_URN_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Archfiend's Urn evidence: " + evidence, failure);
        }
    }

    private interface Step { void run(Map<String, Object> row) throws Exception; }

    private void run(final ClientGameTestContext context, final String id, final Step step) throws Exception {
        final Map<String, Object> row = results.get(id);
        row.put("status", "RUNNING"); write(false);
        try {
            step.run(row); row.put("status", "PASSED");
        } catch (Throwable failure) {
            row.put("status", "FAILED"); row.put("failure", stack(failure)); failures.add(id + ": " + failure);
            try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
            throw failure;
        } finally {
            row.put("urn_state_after", serverValue(player -> ArchfiendsUrnState.read(player.getInventory().getItem(0)).brews()));
            write(false);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 97, -8), new BlockPos(24, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
            target = mob(player, "minecraft:cow", TARGET).getUUID();
            control = mob(player, "minecraft:cow", CONTROL).getUUID();
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        final Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("world", "Fresh disposable Survival world, NORMAL difficulty, bedrock platform three blocks thick, no natural spawns or regeneration.");
        fixture.put("staged", "Empty Archfiend's Urn supplied in hotbar slot 1 (acquisition from a player-killed Emberhorn Archfiend is NOT_RUN); finished brews supplied in the offhand; a NoAI cow 5 blocks east as the aimed target and an identical untreated NoAI cow 18 blocks east as the control.");
        fixture.put("inputs", "All storage, refusal, casting and cooldown arise from real right-click use, real crouch (left shift) and real aim; the urn state, brew counts, cooldown and mob effects are only observed.");
        fixture.put("not_run", List.of("Emberhorn Archfiend loot acquisition and Looting chances", "Hex of Hell on Earth rift", "world/terrain brews stored in the urn",
            "casting at the 24-block range limit", "save/reload persistence of stored brews", "creative-mode storage without consumption", "multiplayer",
            "crouch-use while the urn is on cooldown (vanilla skips the urn and throws the offhand brew; observed, not asserted)"));
        results.get("guide").put("fixture", fixture);
    }

    private void emptyRefusal(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supplyHands(context, item(URN), ItemStack.EMPTY);
        check(serverValue(player -> ArchfiendsUrnState.read(player.getMainHandItem()).brews().isEmpty()), "Supplied urn starts empty");
        lookSky(context); clearOverlay(context);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3);
        awaitOverlay(context, "✗ " + translated(context, "item.warlockery." + URN));
        check(serverValue(player -> !player.getCooldowns().isOnCooldown(player.getMainHandItem())), "An empty urn refuses and starts no cooldown");
        check(serverValue(player -> !living(player, target).hasEffect(MobEffects.SLOWNESS) && !living(player, control).hasEffect(MobEffects.SLOWNESS)),
            "Refused empty cast affects no creature");
        row.put("overlay", overlay(context)); row.put("actual", "Empty urn use showed the red refusal, no cooldown, no effects on target or control.");
        screenshot(context, "urn-empty-refusal");
    }

    private void invalidOffhand(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supplyHands(context, item(URN), new ItemStack(Items.COBBLESTONE, 3));
        lookSky(context); clearOverlay(context);
        crouchUse(context);
        awaitOverlay(context, "✗ " + translated(context, "item.warlockery." + URN));
        check(serverValue(player -> player.getOffhandItem().is(Items.COBBLESTONE) && player.getOffhandItem().getCount() == 3
            && ArchfiendsUrnState.read(player.getMainHandItem()).brews().isEmpty()), "A non-brew offhand item is neither stored nor consumed and the urn stays empty");
        row.put("overlay", overlay(context)); row.put("offhand_after", "minecraft:cobblestone*3");
        row.put("actual", "Crouch-use with cobblestone in the offhand fell through to the empty-urn refusal; nothing stored or consumed.");
        screenshot(context, "urn-invalid-offhand-refusal");
    }

    private void store(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supplyHands(context, item(URN), new ItemStack(item(STORED), 2));
        lookSky(context); clearOverlay(context);
        crouchUse(context);
        await(context, player -> ArchfiendsUrnState.read(player.getMainHandItem()).brews().equals(List.of("slow_movement")), 20,
            "Native crouch-use stores the offhand brew kind in the urn");
        awaitOverlay(context, "✓ " + translated(context, "item.warlockery." + STORED));
        check(serverValue(player -> player.getOffhandItem().is(item(STORED)) && player.getOffhandItem().getCount() == 1), "Storing consumes exactly one brew bottle");
        check(serverValue(player -> player.getMainHandItem().getItem().isFoil(player.getMainHandItem())), "A charged urn shows the enchanted glint");
        row.put("overlay", overlay(context)); row.put("offhand_before", STORED + "*2"); row.put("offhand_after", STORED + "*1");
        row.put("actual", "Survival crouch-use stored Slow Movement, consumed one bottle, and the urn began to glint.");
        screenshot(context, "urn-stored-brew");
    }

    private void duplicate(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        lookSky(context); clearOverlay(context);
        crouchUse(context);
        awaitOverlay(context, "✗ " + translated(context, "item.warlockery." + STORED));
        check(serverValue(player -> player.getOffhandItem().getCount() == 1
            && ArchfiendsUrnState.read(player.getMainHandItem()).brews().equals(List.of("slow_movement"))), "A duplicate kind is refused without consuming the bottle");
        row.put("overlay", overlay(context)); row.put("actual", "Second crouch-use with the same kind was refused; bottle count and stored list unchanged.");
        screenshot(context, "urn-duplicate-refusal");
    }

    private void cast(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final float targetHealth = serverValue(player -> living(player, target).getHealth());
        check(serverValue(player -> !living(player, target).hasEffect(MobEffects.SLOWNESS) && !living(player, control).hasEffect(MobEffects.SLOWNESS)),
            "Neither cow carries Slowness before the native cast");
        look(context, Vec3.atBottomCenterOf(AIM.above())); assertBlockPointer(context, AIM);
        check(!context.computeOnClient(client -> client.player.isShiftKeyDown()), "Cast is attempted without crouching");
        clearOverlay(context);
        screenshot(context, "urn-before-aimed-cast");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        await(context, player -> living(player, target).hasEffect(MobEffects.SLOWNESS), 20, "Aimed native cast applies the stored brew to the creature near the aim point");
        awaitOverlay(context, "✓ " + translated(context, "item.warlockery." + URN));
        final Map<String, Object> effect = serverValue(player -> {
            final var slowness = living(player, target).getEffect(MobEffects.SLOWNESS);
            return Map.of("duration", slowness.getDuration(), "amplifier", slowness.getAmplifier(), "target_health", living(player, target).getHealth(),
                "player_slowed", player.hasEffect(MobEffects.SLOWNESS), "player_distance_to_aim", player.position().distanceTo(Vec3.atBottomCenterOf(AIM.above())));
        });
        check((int) effect.get("amplifier") == 1 && (int) effect.get("duration") >= 1_700, "Stored Slow Movement lands at its documented strength");
        check(serverValue(player -> !living(player, control).hasEffect(MobEffects.SLOWNESS)), "Untreated control cow 13 blocks further away receives nothing");
        check(serverValue(player -> living(player, target).getHealth() == targetHealth), "Effect brew harms nothing");
        check(serverValue(player -> ArchfiendsUrnState.read(player.getMainHandItem()).brews().equals(List.of("slow_movement"))
            && player.getOffhandItem().getCount() == 1), "Casting keeps the stored brew and consumes no bottle");
        check(serverValue(player -> player.getCooldowns().isOnCooldown(player.getMainHandItem())
            && player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0) > .85F), "A successful cast starts the three-second cooldown");
        row.put("overlay", overlay(context)); row.put("target_effect", effect); row.put("control_slowed", false);
        row.put("actual", "Non-crouch use at the floor beside the target cow slowed that cow only; the stored brew remained and the urn entered cooldown.");
        look(context, serverValue(player -> living(player, target).getBoundingBox().getCenter()));
        screenshot(context, "urn-aimed-cast-target-slowed-control-clear");
    }

    private void cooldown(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final int before = serverValue(player -> living(player, target).getEffect(MobEffects.SLOWNESS).getDuration());
        look(context, Vec3.atBottomCenterOf(AIM.above())); assertBlockPointer(context, AIM); clearOverlay(context);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(4);
        final int during = serverValue(player -> living(player, target).getEffect(MobEffects.SLOWNESS).getDuration());
        check(during < before, "During cooldown a second use does not refresh the target's effect; before=" + before + " after=" + during);
        check(overlay(context).isEmpty(), "During cooldown no new cast message appears; actual=" + overlay(context));
        row.put("during_cooldown", Map.of("duration_before", before, "duration_after_attempt", during, "overlay", overlay(context)));
        screenshot(context, "urn-cooldown-blocked");
        await(context, player -> !player.getCooldowns().isOnCooldown(player.getMainHandItem()), 70, "Cooldown expires after about three seconds");
        final int expired = serverValue(player -> living(player, target).getEffect(MobEffects.SLOWNESS).getDuration());
        clearOverlay(context); assertBlockPointer(context, AIM);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        await(context, player -> living(player, target).getEffect(MobEffects.SLOWNESS).getDuration() >= expired, 20, "After the cooldown a native use casts again and refreshes the effect");
        awaitOverlay(context, "✓ " + translated(context, "item.warlockery." + URN));
        check(serverValue(player -> !living(player, control).hasEffect(MobEffects.SLOWNESS)), "Control cow still untouched after the second cast");
        row.put("after_cooldown", Map.of("duration_before_recast", expired, "duration_after_recast",
            serverValue(player -> living(player, target).getEffect(MobEffects.SLOWNESS).getDuration()), "overlay", overlay(context)));
        row.put("actual", "Use during cooldown produced no cast; after roughly 60 ticks the next use cast again.");
        screenshot(context, "urn-cooldown-expired-recast");
    }

    private void capacity(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        // The preceding recast left the urn on cooldown. A cooled-down main-hand item is skipped by vanilla use
        // handling, which then falls through to the offhand and throws the brew instead of storing it.
        await(context, player -> !player.getCooldowns().isOnCooldown(player.getMainHandItem()), 70, "Cast cooldown has expired before further storage");
        row.put("cooldown_note", "Storage waits for the cast cooldown: while the urn is on cooldown a crouch-use is skipped by vanilla and the offhand brew is thrown as a splash potion.");
        final List<String> stored = new ArrayList<>(List.of("slow_movement"));
        for (String id : EXTRA) {
            supplyHands(context, null, new ItemStack(item(id), 1));
            check(serverValue(player -> player.getOffhandItem().is(item(id)) && !player.getCooldowns().isOnCooldown(player.getMainHandItem())), "Offhand holds " + id + " and the urn is ready");
            lookSky(context); clearOverlay(context); crouchUse(context);
            stored.add(id.substring("brew_".length()));
            final List<String> expected = List.copyOf(stored);
            await(context, player -> ArchfiendsUrnState.read(player.getMainHandItem()).brews().equals(expected), 20, "Native crouch-use stores " + id);
            awaitOverlay(context, "✓ " + translated(context, "item.warlockery." + id));
            check(serverValue(player -> player.getOffhandItem().isEmpty()), "Each stored bottle is consumed: " + id);
        }
        check(stored.size() == ArchfiendsUrnState.CAPACITY, "Urn holds four distinct kinds");
        supplyHands(context, null, new ItemStack(item(OVERFLOW), 1));
        check(serverValue(player -> !player.getCooldowns().isOnCooldown(player.getMainHandItem())), "Urn is ready before the overflow attempt");
        lookSky(context); clearOverlay(context); crouchUse(context);
        awaitOverlay(context, "✗ " + translated(context, "item.warlockery." + OVERFLOW));
        check(serverValue(player -> player.getOffhandItem().is(item(OVERFLOW)) && player.getOffhandItem().getCount() == 1
            && ArchfiendsUrnState.read(player.getMainHandItem()).brews().equals(stored)), "A full urn refuses a fifth kind without consuming it");
        row.put("stored_kinds", stored); row.put("overflow_overlay", overlay(context));
        row.put("actual", "Three further kinds stored natively; the fifth kind was refused and its bottle kept.");
        screenshot(context, "urn-full-refusal");
    }

    private static Mob mob(final ServerPlayer player, final String id, final Vec3 point) {
        final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
        check(mob != null, "Fixture entity exists: " + id);
        mob.snapTo(point.x, point.y, point.z); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); mob.setHealth(40);
        check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
    }
    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final var entity = player.level().getEntity(id); check(entity instanceof LivingEntity, "Observed creature is loaded: " + id); return (LivingEntity) entity;
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    /** Supplies the main hand (slot 0; null keeps the current urn) and offhand, then selects slot 0 through a real key press. */
    private void supplyHands(final ClientGameTestContext context, final Item main, final ItemStack offhand) {
        server(player -> {
            if (main != null) { player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(main)); }
            player.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
        check(serverValue(player -> player.getMainHandItem().is(item(URN))), "The urn is actually held in the main hand");
    }
    private void crouchUse(final ClientGameTestContext context) {
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3);
        try {
            check(serverValue(ServerPlayer::isShiftKeyDown), "Server sees the real crouch before use");
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3);
        } finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(2); }
    }
    private static void lookSky(final ClientGameTestContext context) { context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-80); }); context.waitTicks(2); }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> { final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)))); });
        context.waitTicks(2);
    }
    private static void assertBlockPointer(final ClientGameTestContext context, final BlockPos expected) {
        final String actual = context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
            ? "block " + hit.getBlockPos() + " type=" + hit.getType() + " at " + hit.getLocation() + " eye=" + client.player.getEyePosition()
                + " distance=" + client.player.getEyePosition().distanceTo(hit.getLocation())
            : String.valueOf(client.hitResult) + " eye=" + client.player.getEyePosition());
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets intended block " + expected + "; actual hit=" + actual);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining--) context.waitTicks(1);
        check(serverValue(predicate::test), message);
    }
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        return context.computeOnClient(client -> Component.translatable(key, arguments).getString());
    }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : value == null ? "" : String.valueOf(value); });
    }
    private static void clearOverlay(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.setOverlayMessage(Component.empty(), false)); context.waitTicks(1); }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports urn result: " + expected + "; actual=" + overlay(context));
    }
    private void readGuide(final ClientGameTestContext context, final String section, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(section)).findFirst().orElseThrow();
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(item(profile.id()))); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
        lookSky(context);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Urn guide contains actual instructions");
        check(body.contains("crouch-use") && body.contains("24 blocks") && body.contains("three-second cooldown") && body.contains("four distinct"),
            "Guide promises crouch storage, 24-block aimed casting, a three-second cooldown and four kinds");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit every urn guide page");
            screenshot(context, section + "-guide-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }
    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", complete);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty() && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("item", "warlockery:" + URN);
        report.put("execution", "Native rendered Fabric client; the Archfiend's Urn is supplied and every storage, refusal, cast and cooldown outcome arises from real mouse, crouch and aim input and is only observed.");
        report.put("scenarios", results); report.put("screenshots", screenshots); report.put("failures", failures);
        report.put("remaining", "Loot acquisition, Hell on Earth rift, terrain brews, range limit, persistence, creative storage and multiplayer are NOT_RUN.");
        final Path temp = evidence.resolve("archfiend-urn-abilities.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("archfiend-urn-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
