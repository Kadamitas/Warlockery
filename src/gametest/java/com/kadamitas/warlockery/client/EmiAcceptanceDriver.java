package com.kadamitas.warlockery.client;

import static com.kadamitas.warlockery.client.ViewerAcceptanceReflection.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

final class EmiAcceptanceDriver implements ViewerAcceptanceDriver {
    private final Class<?> api = type("dev.emi.emi.api.EmiApi");
    private Object manager() { return call(api, "getRecipeManager"); }
    @Override public boolean ready() { return (boolean) call(type("dev.emi.emi.runtime.EmiReloadManager"), "isLoaded"); }
    @Override public List<Entry> entries() {
        return list(call(manager(), "getRecipes")).stream()
            .filter(recipe -> recipe.getClass().getName().equals("com.kadamitas.warlockery.compat.emi.WarlockeryEmiRecipe"))
            .map(this::entry).toList();
    }
    private Entry entry(Object recipe) {
        Object category = call(recipe, "getCategory");
        String categoryId = call(category, "getId").toString();
        String id = canonical(call(recipe, "getId").toString());
        Object machine = field(recipe, "machine");
        boolean transfer = machine != null && ((com.kadamitas.warlockery.crafting.MachineRecipeManager.Match) machine).recipe().fluid().isEmpty();
        return new Entry(id, categoryId, recipe, ingredients(call(recipe, "getInputs")), ingredients(call(recipe, "getCatalysts")),
            list(call(recipe, "getOutputs")).stream().flatMap(value -> ingredient(value).stream()).toList(), transfer,
            list(call(manager(), "getWorkstations", category)).stream().flatMap(value -> ingredient(value).stream()).toList());
    }
    private String canonical(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        String[] parts = path.split("/", 5);
        if (path.startsWith("/machine/")) return parts[3] + ":" + parts[4];
        if (path.startsWith("/ritual/") || path.startsWith("/custom_brew/")) {
            String[] rest = path.split("/", 4); return rest[2] + ":" + rest[3];
        }
        if (path.equals("/world/anoint_cauldron")) return "warlockery:anoint_cauldron";
        return id;
    }
    private List<List<Stack>> ingredients(Object values) { return list(values).stream().map(this::ingredient).toList(); }
    private List<Stack> ingredient(Object ingredient) {
        long count = ((Number) call(ingredient, "getAmount")).longValue();
        return list(call(ingredient, "getEmiStacks")).stream().filter(stack -> !(boolean) call(stack, "isEmpty")).map(stack -> {
            Object key = call(stack, "getKey");
            if (key instanceof Item item) return new Stack("item", BuiltInRegistries.ITEM.getKey(item).toString(), count);
            if (key instanceof Fluid fluid) return new Stack("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString(), count);
            throw new AssertionError("Unexpected EMI stack: " + key);
        }).toList();
    }
    @Override public void open(Entry recipe) { call(api, "displayRecipe", recipe.handle()); }
    @Override public boolean isRecipeScreen(Screen screen) { return screen != null && screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen"); }
    @Override public Visible visible(Entry recipe, Screen screen) {
        Object group = list(field(screen, "currentPage")).stream().filter(value -> field(value, "recipe") == recipe.handle()).findFirst().orElseThrow();
        int x = integer(group, "x"), y = integer(group, "y");
        List<List<Stack>> slots = new ArrayList<>();
        Point input = null, output = null, transfer = null, next = null;
        int page = 0, pages = 1;
        for (Object widget : list(field(group, "widgets"))) {
            Object bounds = call(widget, "getBounds");
            int wx = x + integer(bounds, "x"), wy = y + integer(bounds, "y");
            var point = new Point(wx + integer(bounds, "width") / 2, wy + integer(bounds, "height") / 2);
            if (type("dev.emi.emi.api.widget.SlotWidget").isInstance(widget)) {
                var ingredient = ingredient(call(widget, "getStack"));
                if (ingredient.isEmpty()) continue;
                slots.add(ingredient);
                check(wx >= 0 && wy >= 0 && wx + integer(bounds, "width") <= screen.width && wy + integer(bounds, "height") <= screen.height,
                    "EMI ingredient slot outside actual viewport: " + recipe.id());
                if (call(widget, "getRecipe") != null) output = point;
                else if (input == null) input = point;
            }
            if (widget.getClass().getSimpleName().equals("RecipeFillButtonWidget")) transfer = point;
            if (widget.getClass().getSimpleName().equals("EmiPagedTextWidget")) {
                Object paginator = field(widget, "pages");
                page = integer(paginator, "page"); pages = integer(paginator, "pageCount");
                check(wy + integer(bounds, "height") <= y + integer(group, "getHeight"), "EMI text extends outside allocated recipe height");
                if (page + 1 < pages) next = new Point(wx + integer(bounds, "width") - 9, wy + integer(bounds, "height") - 9);
            }
        }
        check(!slots.isEmpty(), "No rendered EMI ingredient slots: " + recipe.id());
        return new Visible(slots, input, output, transfer, next, page, pages, x, y, integer(group, "getWidth"), integer(group, "getHeight"));
    }
    @Override public boolean indexed(Entry recipe, boolean output, int slot, int alternative) {
        var raw = output ? list(call(recipe.handle(), "getOutputs")) : combinedInputs(recipe.handle());
        Object value = list(call(raw.get(slot), "getEmiStacks")).get(alternative);
        return list(call(manager(), output ? "getRecipesByOutput" : "getRecipesByInput", value)).contains(recipe.handle());
    }
    private List<?> combinedInputs(Object recipe) {
        var list = new ArrayList<>(list(call(recipe, "getInputs")));
        list.addAll((java.util.Collection) list(call(recipe, "getCatalysts")));
        return list;
    }
    @Override public String currentCategory(Screen screen) {
        var group = list(field(screen, "currentPage")).stream().filter(value -> field(value, "recipe") != null).findFirst().orElseThrow();
        return call(call(field(group, "recipe"), "getCategory"), "getId").toString();
    }
    @Override public boolean containsVisibleResult(Screen screen) {
        return isRecipeScreen(screen) && list(field(screen, "currentPage")).stream().anyMatch(value -> field(value, "recipe") != null);
    }
}
