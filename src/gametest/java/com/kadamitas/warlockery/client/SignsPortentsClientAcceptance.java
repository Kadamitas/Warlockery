package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** Focused rendered acceptance for the 1.5.7 world-events guide. */
public final class SignsPortentsClientAcceptance implements FabricClientGameTest {
    private static final List<String> CHAPTERS = List.of(
        "settlements", "assaults_and_hunts", "creature_habits", "landmarks_and_gatherings"
    );
    private final List<String> checks = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();

    @Override
    public void runTest(final ClientGameTestContext context) {
        final Path evidence = Path.of(System.getProperty("warlockery.clientEvidence"))
            .resolve("signs-portents").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            final ManualProfile signs = ManualProfile.find("ingredient_book_world_events").orElseThrow();
            ManualClientAcceptance.check(signs.chapters().stream().map(ManualProfile.Chapter::id).toList().equals(CHAPTERS),
                "Signs & Portents exposes its four ordered chapter buttons");
            ManualClientAcceptance.check(signs.sections().size() == 37, "Signs & Portents exposes all 37 ordered sections");
            try (var world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_book_world_events").get()));
                    player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("cauldronbook").get()));
                    player.getInventory().setSelectedSlot(0);
                    player.inventoryMenu.broadcastChanges();
                    ManualClientAcceptance.check(server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.RECIPE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("warlockery", "ingredient_book_world_events"))).isPresent(),
                        "The real shapeless book-tag, compass and Whiff of Magic recipe is registered");
                });
                world.getConnection().waitForChunksRender();
                for (int scale : List.of(2, 3)) {
                    context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); });
                    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    ManualClientAcceptance.check(context.computeOnClient(client ->
                        client.gui.screen().getTitle().getString().equals("Signs & Portents")),
                        "Signs & Portents title remains visible at GUI scale " + scale);
                    ManualClientAcceptance.saveScreenshot(context, evidence, "scale-" + scale + "-signs-portents-contents", screenshots);
                    for (String chapter : CHAPTERS) {
                        if (!context.computeOnClient(client -> (boolean) read(client.gui.screen(), "chapterIndex"))) {
                            ManualClientAcceptance.clickButton(context,
                                Component.translatable("screen.warlockery.manual.table_of_contents").getString());
                        }
                        ManualClientAcceptance.clickButton(context,
                            Component.translatable(signs.chapters().stream().filter(value -> value.id().equals(chapter))
                                .findFirst().orElseThrow().titleKey()).getString());
                    }
                    for (String section : signs.sections()) {
                        ManualClientAcceptance.selectSection(context, section);
                        ManualClientAcceptance.check(context.computeOnClient(client ->
                            ManualArticleCatalog.article(signs, section).body().getString().length() > 24),
                            "Readable prose is present for " + section);
                    }
                    final String multiPageSection = context.computeOnClient(client -> signs.sections().stream()
                        .filter(candidate -> pageCount((ManualScreen) client.gui.screen(), candidate) > 1).findFirst().orElse(null));
                    if (multiPageSection != null) {
                        ManualClientAcceptance.selectSection(context, multiPageSection);
                        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                    }
                    final String section = context.computeOnClient(client -> (String) read(client.gui.screen(), "selectedSection"));
                    final int page = context.computeOnClient(client -> (int) read(client.gui.screen(), "bodyPage"));
                    ManualClientAcceptance.check(multiPageSection == null || page > 0,
                        "Real page controls reach a nonzero Signs & Portents page when an article spans multiple pages");
                    context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    ManualClientAcceptance.check(context.computeOnClient(client -> section.equals(read(client.gui.screen(), "selectedSection"))
                        && page == (int) read(client.gui.screen(), "bodyPage")),
                        "Close and reopen preserves the exact Signs & Portents reading position");
                    context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                }
                for (int scale : List.of(2, 3)) {
                    context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); });
                    context.getInput().pressKey(GLFW.GLFW_KEY_2);
                    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    ManualClientAcceptance.check(context.computeOnClient(client ->
                        client.gui.screen().getTitle().getString().equals("Warlocks' Brews")),
                        "Warlocks' Brews header is fully visible at GUI scale " + scale);
                    ManualClientAcceptance.saveScreenshot(context, evidence, "scale-" + scale + "-warlocks-brews-header", screenshots);
                    context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                }
                craftAndCapture(context, world, evidence);
                openJeiAndCapture(context, evidence);
                final ManualProfile immortal = ManualProfile.find("vampirebook").orElseThrow();
                ManualClientAcceptance.check(immortal.sections().equals(java.util.stream.Stream.concat(
                    java.util.stream.Stream.of("preamble", "nami", "blood_audience"),
                    java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(level -> "vampire_level_" + level)
                ).toList()), "Observations of an Immortal retains its frozen 26.2 reading order");
            }
            System.setProperty("warlockery.bookIds", String.join(",", List.of(
                "bookbiomes2", "cauldronbook", "ingredient_book_biomes", "ingredient_book_burning",
                "ingredient_book_circle_magic", "ingredient_book_distilling", "ingredient_book_herbology",
                "ingredient_book_infusions", "ingredient_book_oven", "ingredient_book_wands",
                "ingredient_book_world_events"
            )));
            new AllBooksClientAcceptance().runTest(context);
            Files.writeString(evidence.resolve("pass.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "checks", checks, "screenshots", screenshots,
                "scope", "Native Fabric client acceptance; evidence is private."
            )));
        } catch (Throwable failure) {
            throw new AssertionError("Signs & Portents client acceptance failed; evidence " + evidence, failure);
        }
    }

    private void craftAndCapture(final ClientGameTestContext context, final TestSingleplayerContext world,
        final Path evidence) throws Exception {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.closeContainer();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.teleportTo(0.5, 80, -1.5);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.AIR.defaultBlockState());
            }
            player.level().setBlockAndUpdate(new BlockPos(0, 80, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
            player.getInventory().setItem(9, new ItemStack(net.minecraft.world.item.Items.BOOK));
            player.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.COMPASS));
            player.getInventory().setItem(11, new ItemStack(ModItems.ALL.get("ingredient_whiff_of_magic").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.getInput().lookAt(new BlockPos(0, 80, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(CraftingScreen.class);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var menu = player.containerMenu;
            for (int offset = 0; offset < 3; offset++) {
                final int inventorySlot = 9 + offset;
                final int menuSlot = java.util.stream.IntStream.range(0, menu.slots.size())
                    .filter(candidate -> menu.getSlot(candidate).container == player.getInventory()
                        && menu.getSlot(candidate).getContainerSlot() == inventorySlot).findFirst().orElseThrow();
                menu.clicked(menuSlot, 0, ContainerInput.PICKUP, player);
                menu.clicked(1 + offset, 0, ContainerInput.PICKUP, player);
            }
            menu.broadcastChanges();
        });
        final var expected = ModItems.ALL.get("ingredient_book_world_events").get();
        context.waitFor(client -> client.player.containerMenu.getSlot(0).getItem().is(expected));
        ManualClientAcceptance.saveScreenshot(context, evidence, "shapeless-book-compass-whiff-result", screenshots);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var menu = player.containerMenu;
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            final int target = java.util.stream.IntStream.range(0, menu.slots.size())
                .filter(candidate -> menu.getSlot(candidate).container == player.getInventory()
                    && menu.getSlot(candidate).getContainerSlot() == 12).findFirst().orElseThrow();
            menu.clicked(target, 0, ContainerInput.PICKUP, player);
            menu.broadcastChanges();
        });
        world.getServer().waitFor(server -> {
            var player = world.getConnection().getServerPlayer();
            return player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).isEmpty()
                && player.getInventory().getItem(11).isEmpty() && player.getInventory().getItem(12).is(expected);
        });
        checks.add("Native crafting-table menu consumed one book-tag book, compass and Whiff of Magic and collected Signs & Portents.");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private void openJeiAndCapture(final ClientGameTestContext context, final Path evidence) throws Exception {
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> ManualJeiAcceptance.runtime() != null
            && ManualJeiAcceptance.runtime().getIngredientListOverlay().isListDisplayed());
        final var runtime = ManualJeiAcceptance.runtime();
        int[] search = context.computeOnClient(client -> {
            var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            return new int[] {(properties.guiRight() + properties.screenWidth()) / 2, properties.screenHeight() - 12};
        });
        ManualClientAcceptance.click(context, search[0], search[1]);
        context.waitFor(client -> runtime.getIngredientListOverlay().hasKeyboardFocus());
        int length = context.computeOnClient(client -> runtime.getIngredientFilter().getFilterText().length());
        context.getInput().pressKey(GLFW.GLFW_KEY_END);
        for (int index = 0; index < length; index++) context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
        context.getInput().typeChars("@warlockery Signs & Portents");
        context.waitFor(client -> runtime.getIngredientFilter().getFilterText().equals("@warlockery Signs & Portents"));
        context.waitTicks(5);
        int[] unfocus = context.computeOnClient(client -> {
            var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            return new int[] {properties.guiLeft() + 5, properties.guiTop() + 5};
        });
        ManualClientAcceptance.click(context, unfocus[0], unfocus[1]);
        boolean hovered = false;
        int[] bounds = context.computeOnClient(client -> {
            var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            return new int[] {properties.guiRight() + 4, properties.screenWidth(), properties.screenHeight()};
        });
        outer: for (int y = 8; y < bounds[2] - 36; y += 9) for (int x = bounds[0]; x < bounds[1] - 8; x += 9) {
            ManualClientAcceptance.cursor(context, x, y);
            if (context.computeOnClient(client -> runtime.getIngredientListOverlay().getIngredientUnderMouse()
                .filter(value -> value.getIngredient() instanceof ItemStack stack && BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .equals(Identifier.fromNamespaceAndPath("warlockery", "ingredient_book_world_events"))).isPresent())) {
                hovered = true;
                break outer;
            }
        }
        ManualClientAcceptance.check(hovered, "JEI search visibly resolves Signs & Portents before opening recipes");
        context.getInput().pressKey(GLFW.GLFW_KEY_R);
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        ManualClientAcceptance.saveScreenshot(context, evidence, "jei-signs-portents-recipe", screenshots);
        checks.add("JEI search, hover and R-key opened and captured the Signs & Portents recipe.");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(InventoryScreen.class);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
    }

    private static Object read(final Object value, final String name) {
        Class<?> type = value.getClass();
        while (type != null) {
            try {
                final var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        throw new AssertionError(new NoSuchFieldException(name));
    }

    private static int pageCount(final ManualScreen screen, final String section) {
        try {
            final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
            method.setAccessible(true);
            return ((List<?>) method.invoke(screen, ManualLayout.calculate(screen.width, screen.height), section)).size();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
