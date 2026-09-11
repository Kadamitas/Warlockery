package com.kadamitas.warlockery.client;

import java.nio.file.Path;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ManualJeiAcceptance implements IModPlugin {
    private static volatile IJeiRuntime runtime;

    @Override public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath("warlockery_client_tests", "jei_acceptance"); }
    @Override public void onRuntimeAvailable(final IJeiRuntime available) { runtime = available; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    static IJeiRuntime runtime() { return runtime; }

    static void run(final ClientGameTestContext context, final Path evidence,
        final List<String> screenshots, final List<String> checks) throws Exception {
        context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> runtime != null && runtime.getIngredientListOverlay().isListDisplayed());
        ManualClientAcceptance.check(context.computeOnClient(client -> runtime.getRecipeManager().createRecipeLookup(
            com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get("distillery"))
            .get().anyMatch(recipe -> recipe.id().getPath().equals("distill_vitriol"))),
            "Actual registered Warlockery JEI plugin supplies the Gypsum distillation recipe");
        craftGoldenChalk(context, evidence, screenshots);
        ManualClientAcceptance.saveScreenshot(context, evidence, "survival-crafted-golden-chalk", screenshots);
        checks.add("Native survival crafting rejects Gypsum with Red Dye, then consumes Gypsum with Yellow Dye and produces one Golden Chalk with 64 uses.");
        for (String item : List.of("chalkheart", "ingredient_gypsum", "ritual_knife")) {
            String query = "@warlockery " + switch (item) {
                case "chalkheart" -> "Golden Chalk";
                case "ingredient_gypsum" -> "Gypsum";
                default -> "Ritual Knife";
            };
            int[] search = context.computeOnClient(client -> {
                var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
                return new int[] {(properties.guiRight() + properties.screenWidth()) / 2, properties.screenHeight() - 12};
            });
            ManualClientAcceptance.click(context, search[0], search[1]);
            context.waitFor(client -> runtime.getIngredientListOverlay().hasKeyboardFocus());
            int oldLength = context.computeOnClient(client -> runtime.getIngredientFilter().getFilterText().length());
            context.getInput().pressKey(GLFW.GLFW_KEY_END);
            for (int index = 0; index < oldLength; index++) context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
            context.getInput().typeChars(query);
            context.waitFor(client -> runtime.getIngredientFilter().getFilterText().equals(query));
            context.waitTicks(5);
            int[] unfocus = context.computeOnClient(client -> {
                var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
                return new int[] {properties.guiLeft() + 5, properties.guiTop() + 5};
            });
            ManualClientAcceptance.click(context, unfocus[0], unfocus[1]);
            final int[] bounds = context.computeOnClient(client -> {
                var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
                return new int[] {properties.guiRight() + 4, properties.screenWidth(), properties.screenHeight()};
            });
            boolean found = false;
            outer: for (int y = 8; y < bounds[2] - 36; y += 9) {
                for (int x = bounds[0]; x < bounds[1] - 8; x += 9) {
                    ManualClientAcceptance.cursor(context, x, y);
                    if (context.computeOnClient(client -> runtime.getIngredientListOverlay().getIngredientUnderMouse()
                        .filter(ingredient -> ingredient.getIngredient() instanceof ItemStack stack
                            && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(Identifier.fromNamespaceAndPath("warlockery", item)))
                        .isPresent())) {
                        found = true;
                        break outer;
                    }
                }
            }
            ManualClientAcceptance.check(found, "JEI visibly contains " + item);
            context.getInput().pressKey(GLFW.GLFW_KEY_R);
            try {
                context.waitFor(client -> client.gui.screen() != null
                    && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
            } catch (AssertionError failure) {
                ManualClientAcceptance.saveScreenshot(context, evidence, "jei-failed-opening-" + item, screenshots);
                throw new AssertionError("JEI recipe key did not open for " + item + "; actual screen "
                    + context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName()), failure);
            }
            selectCategory(context, item.equals("ingredient_gypsum") ? "Distillery" : "Crafting");
            ManualClientAcceptance.saveScreenshot(context, evidence, "jei-recipe-" + item, screenshots);
            checks.add("Actual JEI recipe key opens recipes for " + item + ".");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(InventoryScreen.class);
        }
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
    }

    private static void selectCategory(final ClientGameTestContext context, final String expected) {
        for (int step = 0; step < 12; step++) {
            String title = context.computeOnClient(client -> {
                Object logic = readField(client.gui.screen(), "logic");
                try {
                    var selected = logic.getClass().getMethod("getSelectedRecipeCategory");
                    selected.setAccessible(true);
                    return ((mezz.jei.api.recipe.category.IRecipeCategory<?>) selected.invoke(logic)).getTitle().getString();
                } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe active JEI category", failure); }
            });
            if (title.equals(expected)) return;
            int[] point = context.computeOnClient(client -> {
                Object button = readField(client.gui.screen(), "nextRecipeCategory");
                return new int[] {integerMethod(button, "getX") + integerMethod(button, "getWidth") / 2,
                    integerMethod(button, "getY") + integerMethod(button, "getHeight") / 2};
            });
            ManualClientAcceptance.click(context, point[0], point[1]);
        }
        throw new AssertionError("Native JEI category navigation did not reach " + expected);
    }

    private static Object readField(final Object value, final String name) {
        try {
            var field = value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe JEI " + name, failure); }
    }

    private static int integerMethod(final Object value, final String method) {
        try { return (int) value.getClass().getMethod(method).invoke(value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe JEI button " + method, failure); }
    }

    private static void craftGoldenChalk(final ClientGameTestContext context, final Path evidence,
        final List<String> screenshots) throws Exception {
        clickPlayerSlot(context, 9);
        clickMenuSlot(context, 1);
        clickPlayerSlot(context, 12);
        clickMenuSlot(context, 2);
        context.waitTicks(5);
        ManualClientAcceptance.check(context.computeOnClient(client ->
            client.player.containerMenu.getSlot(2).getItem().is(net.minecraft.world.item.Items.DYE.red())
                && client.player.containerMenu.getSlot(0).getItem().isEmpty()),
            "Native survival crafting offers no Golden Chalk result for Gypsum and Red Dye");
        ManualClientAcceptance.saveScreenshot(context, evidence, "survival-red-dye-rejected", screenshots);
        clickMenuSlot(context, 2);
        clickPlayerSlot(context, 12);
        clickPlayerSlot(context, 10);
        clickMenuSlot(context, 2);
        context.waitFor(client -> client.player.containerMenu.getSlot(0).getItem().is(
            com.kadamitas.warlockery.registry.ModItems.ALL.get("chalkheart").get()));
        clickMenuSlot(context, 0);
        clickPlayerSlot(context, 11);
        context.waitFor(client -> client.player.getInventory().getItem(11).is(
            com.kadamitas.warlockery.registry.ModItems.ALL.get("chalkheart").get())
            && client.player.containerMenu.getCarried().isEmpty());
    }

    private static void clickPlayerSlot(final ClientGameTestContext context, final int index) {
        int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(candidate -> client.player.containerMenu.getSlot(candidate).container == client.player.getInventory()
                && client.player.containerMenu.getSlot(candidate).getContainerSlot() == index).findFirst().orElseThrow());
        clickMenuSlot(context, slot);
    }

    private static void clickMenuSlot(final ClientGameTestContext context, final int index) {
        int[] position = context.computeOnClient(client -> {
            var properties = runtime.getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            var slot = client.player.containerMenu.getSlot(index);
            return new int[] {properties.guiLeft() + slot.x + 8, properties.guiTop() + slot.y + 8};
        });
        ManualClientAcceptance.click(context, position[0], position[1]);
    }
}
