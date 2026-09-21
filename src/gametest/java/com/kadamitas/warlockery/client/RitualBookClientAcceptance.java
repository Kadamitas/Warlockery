package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class RitualBookClientAcceptance implements FabricClientGameTest {
    @Override
    public void runTest(final ClientGameTestContext context) {
        final Path evidence = Path.of(System.getProperty("warlockery.clientEvidence"))
            .resolve("ritual-book").resolve(UUID.randomUUID().toString());
        final var screenshots = new ArrayList<String>();
        final BlockPos heart = new BlockPos(0, 100, 0);
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                try {
                    world.getConnection().waitForChunksRender();
                    world.getServer().runOnServer(server -> {
                        final var player = world.getConnection().getServerPlayer();
                        player.setGameMode(GameType.SURVIVAL);
                        player.setPermanentlyInvulnerable(true);
                        for (BlockPos pos : BlockPos.betweenClosed(-3, 99, -4, 3, 103, 3)) {
                            player.level().setBlockAndUpdate(pos, pos.getY() == 99
                                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        player.level().setBlockAndUpdate(heart, ModBlocks.ALL.get("circle").get().defaultBlockState());
                        player.getInventory().clearContent();
                        player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("arcane_focus").get()));
                        player.teleportTo(0.5, 100, -2.5);
                        player.inventoryMenu.broadcastChanges();
                    });
                    world.getConnection().waitForClientboundPackets();
                    context.waitFor(client -> client.gui.screen() == null, 300);
                    context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
                    aim(context, Vec3.atCenterOf(heart).add(0, -0.495, 0));
                    context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    context.waitTicks(20);
                    check(context.computeOnClient(client -> client.gui.screen() == null),
                        "Golden heart must refuse casting without a Circle Magic book");
                    ManualClientAcceptance.saveScreenshot(context, evidence, "missing-book", screenshots);
                    world.getServer().runOnServer(server -> {
                        final var player = world.getConnection().getServerPlayer();
                        player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_book_circle_magic").get()));
                        player.inventoryMenu.broadcastChanges();
                    });
                    world.getConnection().waitForClientboundPackets();
                    context.runOnClient(client -> client.player.setXRot(-50));
                    context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    ManualClientAcceptance.selectSection(context, "rite_manifestation");
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                    final int savedPage = page(context);
                    check(savedPage > 0, "The back-button regression needs a nonzero reading page");
                    context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
                    aim(context, Vec3.atCenterOf(heart).add(0, -0.495, 0));
                    context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    check(page(context) == savedPage, "Heart use must reopen the saved reading page");
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.perform_ritual").getString());
                    ManualClientAcceptance.saveScreenshot(context, evidence, "perform-ritual", screenshots);
                    checkCastingNavigation(context);
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back_to_ritual").getString());
                    check(page(context) == savedPage, "Back must restore the exact ritual entry page");
                    check(context.computeOnClient(client -> client.gui.screen().children().stream()
                        .anyMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox)),
                        "Returning to the ritual entry must restore book search");
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.perform_ritual").getString());
                    context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
                    check(context.computeOnClient(client -> client.gui.screen() instanceof ManualScreen),
                        "Escape from Perform Ritual must return to its book entry");
                    check(page(context) == savedPage, "Escape must restore the reading page");
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.perform_ritual").getString());
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.close").getString());
                    aim(context, Vec3.atCenterOf(heart).add(0, -0.495, 0));
                    context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    check(page(context) == savedPage, "Closing Perform Ritual must persist its normal reading page");
                    ManualClientAcceptance.saveScreenshot(context, evidence, "reopened-entry", screenshots);
                    context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.perform_ritual").getString());
                    ManualClientAcceptance.saveScreenshot(context, evidence, "perform-scale-three", screenshots);
                    checkCastingNavigation(context);
                    ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back_to_ritual").getString());
                    ManualClientAcceptance.saveScreenshot(context, evidence, "back-scale-three", screenshots);
                    context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
                    context.waitTicks(20);
                    ManualClientAcceptance.saveScreenshot(context, evidence, "selected-heart-display", screenshots);
                    Files.writeString(evidence.resolve("passed.txt"), "Native missing-book refusal, saved-page heart opening, Perform/Back, Escape and close/reopen at scales 2/3 passed.\n" + String.join("\n", screenshots));
                } catch (Throwable failure) {
                    ManualClientAcceptance.saveScreenshot(context, evidence, "failure-in-world", screenshots);
                    throw failure;
                }
            }
            System.out.println("WARLOCKERY_RITUAL_BOOK_PASSED " + evidence);
        } catch (Throwable failure) {
            throw new AssertionError("Ritual book evidence: " + evidence, failure);
        }
    }

    private static void checkCastingNavigation(final ClientGameTestContext context) {
        check(context.computeOnClient(client -> client.gui.screen().children().stream()
            .noneMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox
                || child instanceof ManualNavigationButton)),
            "Perform Ritual must hide book search and the other ritual entries");
    }

    private static int page(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            try {
                final var field = ManualScreen.class.getDeclaredField("bodyPage");
                field.setAccessible(true);
                return field.getInt(client.gui.screen());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    private static void aim(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
