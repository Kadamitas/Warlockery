package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.transformation.WerewolfShape;
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
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Native Moon Charm holds: form and level gates, early-release control, wolf and wolfman changes in both directions, and charm wear. */
public final class MoonCharmAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(.5, 100, .5);
    private static final String CHARM = "mooncharm";
    private static final int HOLD = 70;
    private static final int EARLY = 30;
    private static final List<String> CASES = List.of("guide", "locked_not_werewolf", "locked_level_1", "wolfman_locked_level_2",
        "early_release_control", "shift_to_wolf", "shift_back_to_human", "wolfman_at_level_5", "wolfman_back_to_human", "durability");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("moon-charm-abilities").resolve(UUID.randomUUID().toString());
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
                    run(context, "guide", row -> readGuide(context, CHARM, row));
                    run(context, "locked_not_werewolf", row -> locked(context, row, false, "message.warlockery.moon_charm.locked", "not-werewolf"));
                    server(player -> check(SupernaturalProgression.beginPath(player, SupernaturalProgression.Path.WEREWOLF)
                        && SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 1, "Staged a level 1 werewolf"));
                    run(context, "locked_level_1", row -> locked(context, row, false, "message.warlockery.moon_charm.locked", "level-1"));
                    server(player -> SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, 2));
                    run(context, "wolfman_locked_level_2", row -> locked(context, row, true, "message.warlockery.moon_charm.wolfman_locked", "level-2-crouched"));
                    run(context, "early_release_control", row -> earlyRelease(context, row));
                    run(context, "shift_to_wolf", row -> shift(context, row, false, WerewolfShape.HUMAN, WerewolfShape.WOLF, 1));
                    run(context, "shift_back_to_human", row -> shift(context, row, false, WerewolfShape.WOLF, WerewolfShape.HUMAN, 2));
                    server(player -> SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, 5));
                    run(context, "wolfman_at_level_5", row -> shift(context, row, true, WerewolfShape.HUMAN, WerewolfShape.WOLFMAN, 3));
                    run(context, "wolfman_back_to_human", row -> shift(context, row, true, WerewolfShape.WOLFMAN, WerewolfShape.HUMAN, 4));
                    run(context, "durability", row -> durability(context, row));
                } finally {
                    context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                    context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_MOON_CHARM_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Moon Charm evidence: " + evidence, failure);
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
        } finally {
            context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            row.put("state_after", serverValue(MoonCharmAbilitiesClientAcceptance::state)); write(false);
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
            SupernaturalProgression.cure(player);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 97, -8), new BlockPos(8, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        check(serverValue(player -> SupernaturalState.getForm(player) == SupernaturalForm.NONE && SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN),
            "Actor starts as an ordinary human");
        final Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("world", "Fresh disposable Survival world at its starting dawn (no full-moon night is reached) on a three-block bedrock platform; no natural spawns or regeneration.");
        fixture.put("staged", "An undamaged Moon Charm is supplied in hotbar slot 1 (Wolf Altar acquisition NOT_RUN). Lycanthropy is staged through the progression API only as a prerequisite: none, then level 1, level 2 and level 5. Shape is never set by the test.");
        fixture.put("inputs", "Every attempt is a real right-mouse hold (released early or held past the three-second use), with real left-shift crouch for the wolfman branch; the resulting shape, use state, overlay and charm damage are only observed.");
        fixture.put("not_run", List.of("Wolf Altar acquisition with gold ingots", "full-moon forced change prevention while carrying the charm", "charm breaking at the 49th use",
            "level-5 direct wolf-to-wolfman change", "creative-mode wear exemption", "save/reload", "multiplayer"));
        results.get("guide").put("fixture", fixture);
    }

    private void locked(final ClientGameTestContext context, final Map<String, Object> row, final boolean crouch, final String key, final String name) throws Exception {
        supply(context);
        final int damage = serverValue(player -> player.getMainHandItem().getDamageValue());
        lookSky(context); clearOverlay(context);
        if (crouch) { context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3); check(serverValue(ServerPlayer::isShiftKeyDown), "Server sees the real crouch"); }
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try {
            context.waitTicks(HOLD);
            awaitOverlay(context, translated(context, key));
            check(serverValue(player -> !player.isUsingItem()), "A locked charm never starts the three-second use");
        } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); if (crouch) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3); }
        check(serverValue(player -> SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN && player.getMainHandItem().getDamageValue() == damage),
            "A refused hold changes no shape and wears nothing");
        row.put("overlay", overlay(context)); row.put("crouched", crouch); row.put("held_ticks", HOLD);
        row.put("actual", "Holding use for " + HOLD + " ticks" + (crouch ? " while crouched" : "") + " showed the red lock message; no use began, shape stayed human, damage unchanged.");
        screenshot(context, "moon-charm-locked-" + name);
    }

    private void earlyRelease(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context); lookSky(context); clearOverlay(context);
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        int remaining;
        try {
            await(context, ServerPlayer::isUsingItem, 10, "A level 2 werewolf begins the charm use natively");
            context.waitTicks(EARLY);
            remaining = serverValue(ServerPlayer::getUseItemRemainingTicks);
            check(serverValue(player -> player.isUsingItem() && player.getUseItemRemainingTicks() > 10), "Use is still in progress when released early; remaining=" + remaining);
            screenshot(context, "moon-charm-holding-before-early-release");
        } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(5); }
        check(serverValue(player -> !player.isUsingItem() && SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN && player.getMainHandItem().getDamageValue() == 0),
            "Releasing early completes nothing: shape human and charm unworn");
        check(!overlay(context).equals(translated(context, "message.warlockery.moon_charm.shifted", translated(context, "shape.warlockery.wolf"))), "No shift message after an early release");
        row.put("remaining_use_ticks_at_release", remaining); row.put("overlay", overlay(context));
        row.put("actual", "Level 2 use started, was released with " + remaining + " ticks left, and neither shape nor durability changed (untreated control for the full hold).");
        screenshot(context, "moon-charm-early-release-control");
    }

    private void shift(final ClientGameTestContext context, final Map<String, Object> row, final boolean crouch, final WerewolfShape from,
        final WerewolfShape to, final int expectedDamage) throws Exception {
        supply(context);
        check(serverValue(player -> SupernaturalProgression.werewolfShape(player) == from && player.getMainHandItem().getDamageValue() == expectedDamage - 1),
            "Shape and wear before the hold are " + from + "/" + (expectedDamage - 1));
        lookSky(context); clearOverlay(context);
        if (crouch) { context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3); check(serverValue(ServerPlayer::isShiftKeyDown), "Server sees the real crouch"); }
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try {
            await(context, ServerPlayer::isUsingItem, 10, "The charm use begins natively");
            check(serverValue(player -> player.getUseItemRemainingTicks() <= 60 && player.getUseItemRemainingTicks() > 40), "Use duration is the documented three seconds");
            await(context, player -> SupernaturalProgression.werewolfShape(player) == to, HOLD, "Holding through the full use changes shape to " + to);
        } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); if (crouch) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3); }
        awaitOverlay(context, translated(context, "message.warlockery.moon_charm.shifted", translated(context, "shape.warlockery." + to.id())));
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == expectedDamage), "Each completed change wears the charm by one; expected " + expectedDamage);
        check(serverValue(player -> !player.isUsingItem() && player.getMainHandItem().is(item(CHARM))), "The charm survives the change and the use ends");
        row.put("overlay", overlay(context)); row.put("crouched", crouch); row.put("shape_before", from.name()); row.put("shape_after", to.name()); row.put("charm_damage", expectedDamage);
        row.put("actual", "A real " + (crouch ? "crouched " : "") + "three-second hold changed " + from + " to " + to + " and wore the charm to " + expectedDamage + ".");
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT)); context.waitTicks(3);
        screenshot(context, "moon-charm-" + from.id() + "-to-" + to.id());
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON)); context.waitTicks(2);
    }

    private void durability(final ClientGameTestContext context, final Map<String, Object> row) {
        final Map<String, Object> wear = serverValue(player -> Map.of("damage", player.getMainHandItem().getDamageValue(), "max_damage", player.getMainHandItem().getMaxDamage(),
            "completed_changes", 4, "refused_or_early_attempts", 4));
        check((int) wear.get("damage") == 4 && (int) wear.get("max_damage") == 49, "Four completed changes cost exactly four of 49 uses; refused and early attempts cost nothing");
        row.put("wear", wear); row.put("actual", "After four native completed changes and four non-completing attempts the charm shows damage 4 of 49.");
    }

    private static Map<String, Object> state(final ServerPlayer player) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("form", SupernaturalState.getForm(player).name()); value.put("werewolf_level", SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF));
        value.put("shape", SupernaturalProgression.werewolfShape(player).name()); value.put("using_item", player.isUsingItem());
        value.put("charm_damage", player.getMainHandItem().is(item(CHARM)) ? player.getMainHandItem().getDamageValue() : -1); value.put("y", player.getY());
        return value;
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    /** Keeps the same charm stack across cases so wear accumulates; supplies a fresh one only when none is held. */
    private void supply(final ClientGameTestContext context) {
        server(player -> {
            if (!player.getInventory().getItem(0).is(item(CHARM))) { player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(item(CHARM))); }
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
        check(serverValue(player -> player.getMainHandItem().is(item(CHARM))), "The Moon Charm is actually held");
    }
    private static void lookSky(final ClientGameTestContext context) { context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-80); }); context.waitTicks(2); }
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
        check(overlay(context).equals(expected), "Rendered overlay reports charm result: " + expected + "; actual=" + overlay(context));
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
        check(!body.isBlank() && pages > 0, "Moon Charm guide contains actual instructions");
        check(body.contains("level 2 or higher") && body.contains("three seconds") && body.contains("level 5") && body.contains("Releasing early does not complete") && body.contains("wears the charm"),
            "Guide promises the level 2 gate, three-second hold, level 5 wolfman, early-release rule and wear");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit every charm guide page");
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
        report.put("item", "warlockery:" + CHARM);
        report.put("execution", "Native rendered Fabric client; the Moon Charm is supplied and lycanthropy level is staged as a prerequisite, while every shape change, refusal, early release and wear arises from real held mouse and crouch input and is only observed.");
        report.put("scenarios", results); report.put("screenshots", screenshots); report.put("failures", failures);
        report.put("remaining", "Wolf Altar acquisition, full-moon prevention, final-use breakage, direct wolf-to-wolfman at level 5, creative wear exemption, persistence and multiplayer are NOT_RUN.");
        final Path temp = evidence.resolve("moon-charm-abilities.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("moon-charm-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
