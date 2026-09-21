package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class RecipeBookAcceptance implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Path evidence = Path.of(System.getProperty("warlockery.clientEvidence")).resolve("recipebook").resolve(UUID.randomUUID().toString());
        List<String> unlocked = new ArrayList<>();
        List<String> screenshots = new ArrayList<>();
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    var recipes = server.getRecipeManager().getRecipes().stream()
                        .filter(recipe -> recipe.id().identifier().getNamespace().equals("warlockery") && !recipe.value().isSpecial()).toList();
                    for (var recipe : recipes) {
                        var item = recipe.value().placementInfo().ingredients().stream().flatMap(ingredient -> ingredient.items()).findFirst().orElseThrow();
                        player.getInventory().clearContent();
                        player.getInventory().add(new ItemStack(item.value()));
                        player.inventoryMenu.broadcastChanges();
                    }
                    for (var recipe : recipes) {
                        ManualClientAcceptance.check(player.getRecipeBook().contains(recipe.id()), "Ingredient acquisition unlocks " + recipe.id().identifier());
                        unlocked.add(recipe.id().identifier().toString());
                    }
                    ManualClientAcceptance.check(recipes.size() == 180 && unlocked.size() == recipes.size(),
                        "All 180 native recipes unlock from ingredient acquisition without recipe commands");
                    player.getInventory().clearContent();
                    player.getInventory().setItem(9, new ItemStack(ModItems.ALL.get("ingredient_gypsum").get()));
                    player.getInventory().setItem(10, new ItemStack(Items.DYE.yellow()));
                    player.inventoryMenu.broadcastChanges();
                });
                world.getConnection().waitForClientboundPackets();
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_E);
                context.waitForScreen(InventoryScreen.class);
                if (!context.computeOnClient(client -> book(client.gui.screen()).isVisible())) {
                    double[] point = context.computeOnClient(client -> client.gui.screen().children().stream()
                        .filter(ImageButton.class::isInstance).map(ImageButton.class::cast)
                        .filter(button -> button.visible).map(button -> new double[]{button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0})
                        .findFirst().orElseThrow());
                    ManualClientAcceptance.click(context, point[0], point[1]);
                }
                context.waitFor(client -> book(client.gui.screen()).isVisible());
                double[] search = context.computeOnClient(client -> {
                    var input = (EditBox) field(book(client.gui.screen()), "searchBox");
                    return new double[]{input.getX() + 12, input.getY() + 8};
                });
                ManualClientAcceptance.click(context, search[0], search[1]);
                context.getInput().typeChars("Golden Chalk");
                context.waitTicks(5);
                var buttonPoint = context.computeOnClient(client -> {
                    var page = field(book(client.gui.screen()), "recipeBookPage");
                    @SuppressWarnings("unchecked") var buttons = (List<RecipeButton>) field(page, "buttons");
                    return buttons.stream().filter(button -> button.visible && button.active).map(button -> new double[]{button.getX() + 12, button.getY() + 12})
                        .findFirst().orElseThrow(() -> new AssertionError("Golden Chalk is missing from Minecraft recipe-book search"));
                });
                ManualClientAcceptance.saveScreenshot(context, evidence, "golden-chalk-native-search", screenshots);
                ManualClientAcceptance.click(context, buttonPoint[0], buttonPoint[1]);
                context.waitFor(client -> client.player.containerMenu.getSlot(0).getItem().is(ModItems.ALL.get("chalkheart").get()));
                ManualClientAcceptance.saveScreenshot(context, evidence, "native-recipe-book-fills-golden-chalk", screenshots);
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("warlockery", "chalkheart_from_gypsum"));
                    ManualClientAcceptance.check(player.getRecipeBook().contains(key), "Golden Chalk remains known after recipe placement");
                });
                Files.writeString(evidence.resolve("pass.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "passed", true, "unlocked", unlocked, "screenshots", screenshots,
                    "fixture", "Fresh survival world. Inventory acquisition fixtures exercise vanilla advancement triggers for every recipe; no recipe-grant commands. Native inventory, recipe-book button, search, and recipe click fill the actual Golden Chalk crafting grid.")));
            }
        } catch (Throwable failure) {
            try { Files.writeString(evidence.resolve("failure.txt"), failure.toString()); } catch (Exception ignored) { }
            throw new AssertionError("Native recipe book acceptance failed: " + evidence, failure);
        }
    }

    private static RecipeBookComponent<?> book(Object screen) { return (RecipeBookComponent<?>) field(screen, "recipeBookComponent"); }
    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("Missing observed field " + name);
    }
}
