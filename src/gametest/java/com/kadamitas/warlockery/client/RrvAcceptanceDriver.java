package com.kadamitas.warlockery.client;

import static com.kadamitas.warlockery.client.ViewerAcceptanceReflection.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

final class RrvAcceptanceDriver implements ViewerAcceptanceDriver {
    private final Object cache = field(type("cc.cassian.rrv.client.recipe.ClientRecipeCache"), "INSTANCE");
    private final Object overlay = field(type("cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay"), "INSTANCE");
    @Override public boolean ready() { return list(call(cache, "getRecipes")).stream().anyMatch(this::own); }
    private boolean own(Object recipe) { return recipe.getClass().getName().equals("com.kadamitas.warlockery.compat.rrv.WarlockeryRrvRecipe"); }
    @Override public List<Entry> entries() { return list(call(cache, "getRecipes")).stream().filter(this::own).map(this::entry).toList(); }
    private Entry entry(Object recipe) {
        Object category = call(recipe, "getType");
        return new Entry(call(recipe, "getId").toString(), call(category, "getId").toString(), recipe,
            ingredients(field(recipe, "inputs")), ingredients(field(recipe, "tools")),
            ingredients(call(recipe, "getResults")).stream().flatMap(List::stream).toList(), (boolean) call(recipe, "supportsItemTransfer"),
            list(call(category, "getCraftReferences")).stream().map(value -> item((ItemStack) value)).toList());
    }
    private List<List<Stack>> ingredients(Object values) {
        return list(values).stream().map(value -> list(call(value, "getValidContents")).stream().map(stack -> stack((ItemStack) stack)).toList()).toList();
    }
    private Stack stack(ItemStack stack) {
        Object fluid = call(type("cc.cassian.rrv.common.extra.FluidStack"), "fromItemStack", stack);
        Fluid value = (Fluid) call(fluid, "fluid");
        return value != Fluids.EMPTY ? new Stack("fluid", BuiltInRegistries.FLUID.getKey(value).toString(), ((Number) call(fluid, "amount")).longValue()) : item(stack);
    }
    @Override public void open(Entry recipe) { call(overlay, "openRecipeView", call(recipe.handle(), "getId"), false); }
    @Override public boolean isRecipeScreen(Screen screen) { return screen != null && screen.getClass().getName().equals("cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen"); }
    @Override public Visible visible(Entry recipe, Screen screen) {
        Object menu = call(screen, "getMenu");
        var recipes = list(call(menu, "getCurrentDisplay"));
        int index = recipes.indexOf(recipe.handle());
        check(index >= 0, "Requested RRV recipe is not actually displayed: " + recipe.id());
        int left = integer(screen, "getLeftPos"), top = integer(screen, "getTopPos");
        Object category = call(recipe.handle(), "getType");
        int perRecipe = integer(category, "getSlotCount");
        int x = left + integer(menu, "guiOffsetLeft"), y = top + ((Number) call(menu, "guiOffsetTop", index)).intValue();
        var rendered = new ArrayList<List<Stack>>();
        Point input = null, output = null, transfer = null, next = null;
        for (int slotIndex = index * perRecipe; slotIndex < (index + 1) * perRecipe; slotIndex++) {
            var slot = (Slot) call(menu, "getSlot", slotIndex);
            if (!slot.isActive() || slot.getItem().isEmpty()) continue;
            check(left + slot.x >= 0 && top + slot.y >= 0 && left + slot.x + 16 <= screen.width && top + slot.y + 16 <= screen.height,
                "RRV actual slot clipped: " + recipe.id());
            rendered.add(List.of(stack(slot.getItem())));
            var point = new Point(left + slot.x + 8, top + slot.y + 8);
            int local = slotIndex % perRecipe;
            if (local < 8 && input == null) input = point;
            if (local >= 8 && local < 12 && output == null) output = point;
        }
        for (Object value : list(field(screen, "transferButtons"))) {
            var button = (AbstractWidget) value;
            if (button.visible && button.active) transfer = new Point(button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
        }
        int page = ((Number) field(recipe.handle(), "page")).intValue();
        int pages = integer(recipe.handle(), "pages");
        if (page + 1 < pages) {
            var button = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible && widget.active && widget.getMessage().getString().equals(">")
                    && widget.getX() >= x && widget.getX() < x + 120 && widget.getY() == y + 154).findFirst().orElseThrow();
            next = new Point(button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
        }
        check(!rendered.isEmpty(), "No actual RRV ingredient slot rendered: " + recipe.id());
        return new Visible(rendered, input, output, transfer, next, page, pages, x, y, integer(category, "getDisplayWidth"), integer(category, "getDisplayHeight"));
    }
    @Override public boolean indexed(Entry recipe, boolean output, int slot, int alternative) {
        var raw = list(call(recipe.handle(), output ? "getResults" : "getIngredients"));
        ItemStack value = (ItemStack) list(call(raw.get(slot), "getValidContents")).get(alternative);
        return list(call(cache, output ? "getRecipesForCraftingOutput" : "getRecipesForCraftingInput", value)).contains(recipe.handle());
    }
    @Override public String currentCategory(Screen screen) { return call(call(call(screen, "getMenu"), "getClientRecipeType"), "getId").toString(); }
    @Override public boolean containsVisibleResult(Screen screen) { return isRecipeScreen(screen) && !list(call(call(screen, "getMenu"), "getCurrentDisplay")).isEmpty(); }
}
