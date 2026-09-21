package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class JeiCatalogAcceptance implements FabricClientGameTest {
    private static final boolean MACHINES_ONLY = Boolean.getBoolean("warlockery.jeiAuditMachineOnly");
    private final Map<String, Coverage> coverage = new LinkedHashMap<>();
    private final Map<String, Expected> expected = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> navigation = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private Path evidence;
    private List<Map<String, Object>> matcherAudit = List.of();
    private final List<Map<String, Object>> componentAudit = new ArrayList<>();
    private final Map<String, List<String>> expectedFluids = new LinkedHashMap<>();

    @Override public void runTest(ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence")).getParent()
            .resolve("new-player-audit/jei").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                world.getConnection().waitForClientboundPackets();
                world.getServer().runOnServer(server -> {
                    matcherAudit = MachineRecipeMatcherAudit.run();
                    var level = server.overworld();
                    var displayContext = SlotDisplayContext.fromLevel(level);
                    for (var holder : server.getRecipeManager().getRecipes()) {
                        String id = holder.id().identifier().toString();
                        if (!id.startsWith("warlockery:")) continue;
                        var outputs = holder.value().display().stream().flatMap(display ->
                            display.result().resolveForStacks(displayContext).stream()).map(JeiCatalogAcceptance::stack).distinct().toList();
                        expected.put(id, new Expected("vanilla", holder.value().getClass().getName(),
                            holder.value().placementInfo().ingredients().size(), outputs, holder.value().isSpecial(),
                            holder.value().placementInfo().ingredients().stream().map(input -> input.items()
                                .map(item -> stack(new ItemStack(item.value()))).sorted().toList()).toList()));
                    }
                    for (var recipe : MachineRecipeManager.INSTANCE.all()) {
                        expected.put(recipe.id().toString(), new Expected("machine", recipe.recipe().machine(),
                            recipe.recipe().inputs().size() + (recipe.recipe().fluid().isPresent() ? 1 : 0),
                            recipe.recipe().outputs().stream().map(output -> output.item() + " x" + output.count()).toList(), false,
                            recipe.recipe().inputs().stream().map(input -> resolve(input.ingredient(), input.count())).toList()));
                        recipe.recipe().fluid().ifPresent(required -> {
                            var parsed = com.kadamitas.warlockery.util.FluidIngredient.parse(required.ingredient()).orElseThrow();
                            expectedFluids.put(recipe.id().toString(), BuiltInRegistries.FLUID.stream().filter(fluid -> fluid != net.minecraft.world.level.material.Fluids.EMPTY)
                                .map(fluid -> new com.kadamitas.warlockery.util.FluidContents(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid), required.amount()))
                                .filter(parsed::matches).map(fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid()) + " x"
                                    + com.kadamitas.warlockery.util.FluidContents.dropletsFromMilliBuckets(required.amount()) + " droplets").toList());
                        });
                    }
                    for (var ritual : RitualManager.INSTANCE.all()) {
                        if (!ritual.definition().visible()) continue;
                        var definition = ritual.definition();
                        String output = switch (definition.action()) {
                            case "summon_item", "bind_item", "bind_circle" -> definition.target() + " x" + definition.count();
                            case "bind_fetish" -> definition.target() + " x1";
                            case "bind_waystone", "copy_waystone" -> "warlockery:ingredient_waystone_bound x1";
                            default -> "";
                        };
                        expected.put(ritual.id().toString(), new Expected("ritual", definition.action(),
                            definition.requirements().ingredients().size(), output.isEmpty() ? List.of() : List.of(output), false,
                            definition.requirements().ingredients().stream().map(input -> resolve(input.ingredient(), input.count())).toList()));
                    }
                    var components = com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager.INSTANCE;
                    for (var id : components.ids()) {
                        var definition = components.byId(id).orElseThrow();
                        expected.put(id.toString(), new Expected("custom_component", definition.role().id(), 1, List.of(), true,
                            List.of(resolve(definition.ingredient(), 1))));
                        Map<String, String> selections = new LinkedHashMap<>();
                        var parsed = com.kadamitas.warlockery.util.ItemIngredient.parse(definition.ingredient()).orElseThrow();
                        BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(parsed::matches).forEach(stack -> {
                            try {
                                var method = components.getClass().getDeclaredMethod("match", ItemStack.class);
                                method.setAccessible(true);
                                var selected = (Optional<?>) method.invoke(components, stack);
                                selections.put(stack(stack), selected.map(value -> invoke(value, "id").toString()).orElse("NONE"));
                            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe custom component selection", failure); }
                        });
                        componentAudit.add(Map.of("id", id.toString(), "inputSelections", selections,
                            "allAlternativesSelectThisComponent", !selections.isEmpty() && selections.values().stream().allMatch(id.toString()::equals)));
                    }
                    expected.put("warlockery:anoint_cauldron", new Expected("world_interaction", "Use paste on a placed cauldron", 2,
                        List.of("warlockery:cauldron x1"), true,
                        List.of(List.of("warlockery:ingredient_annointing_paste x1"), List.of("minecraft:cauldron x1"))));
                });
                if (MACHINES_ONLY) expected.entrySet().removeIf(entry -> !entry.getValue().kind.equals("machine"));
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_E);
                context.waitFor(client -> ManualJeiAcceptance.runtime() != null);
                var batches = context.computeOnClient(client -> inventory());
                write("baseline.json", false);
                System.out.println("WARLOCKERY_JEI_BASELINE " + evidence + " expected=" + expected.size() + " indexed=" + coverage.size());
                for (var batch : batches.entrySet()) {
                    IRecipeCategory category = batch.getKey();
                    List<Object> recipes = batch.getValue();
                    context.runOnClient(client -> ManualJeiAcceptance.runtime().getRecipesGui().showRecipes(category, recipes, List.of()));
                    context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
                    Set<String> visited = new HashSet<>();
                    for (int page = 0; page <= recipes.size(); page++) {
                        context.waitTicks(2);
                        var layouts = context.computeOnClient(client -> visibleLayouts(client.gui.screen()));
                        List<String> visible = context.computeOnClient(client -> layouts.stream().map(layout -> {
                            if (!layout.getRecipeCategory().getRecipeType().equals(category.getRecipeType()))
                                throw new AssertionError("Catalog batch " + category.getRecipeType().getUid()
                                    + " unexpectedly displays " + layout.getRecipeCategory().getRecipeType().getUid());
                            return key(layout.getRecipeCategory(), layout.getRecipe());
                        }).toList());
                        if (visible.isEmpty() || visible.stream().allMatch(visited::contains)) break;
                        String shot = "category-" + safe(category.getRecipeType().getUid().toString()) + "-page-" + page;
                        ManualClientAcceptance.saveScreenshot(context, evidence, shot, screenshots);
                        String screenshot = evidence.resolve(shot + ".png").toString();
                        context.runOnClient(client -> {
                            for (var layout : layouts) inspectRendered(layout, screenshot);
                        });
                        visited.addAll(visible);
                        if (page == 0) nativeKeys(context, category, recipes);
                        if (visited.size() >= recipes.size()) break;
                        clickField(context, "nextPage");
                        navigation.add("Native next-page click: " + category.getRecipeType().getUid() + " after page " + page);
                    }
                    write("coverage-progress.json", false);
                }
                nativeCategories(context, batches.keySet().stream().toList());
                for (String id : expected.keySet()) {
                    if (coverage.values().stream().noneMatch(record -> record.id.equals(id))) errors.add("Missing authoritative recipe: " + id);
                }
                for (var record : coverage.values()) {
                    if (!record.rendered) record.problems.add("No actual RecipesGui render observed");
                    if (!record.problems.isEmpty()) errors.add(record.key + ": " + record.problems);
                }
                write("coverage.json", errors.isEmpty());
                errors.forEach(error -> System.err.println("WARLOCKERY_JEI_COVERAGE_FAILURE " + error));
                System.out.println("WARLOCKERY_JEI_CATALOG_COMPLETE " + evidence + " entries=" + coverage.size() + " errors=" + errors.size());
                if (!errors.isEmpty()) throw new AssertionError(errors.size() + " recipe coverage failures; see coverage.json");
            }
        } catch (Throwable failure) {
            errors.add(failure.toString());
            try { write("failure.json", false); } catch (Exception ignored) { }
            throw new AssertionError("JEI catalog audit failed; evidence " + evidence, failure);
        }
    }

    private Map<IRecipeCategory, List<Object>> inventory() {
        var runtime = ManualJeiAcceptance.runtime();
        var manager = runtime.getRecipeManager();
        Map<IRecipeCategory, List<Object>> batches = new LinkedHashMap<>();
        var categories = manager.createRecipeCategoryLookup().includeHidden().get().toList();
        for (IRecipeCategory category : categories) {
            String type = category.getRecipeType().getUid().toString();
            if (MACHINES_ONLY && !type.startsWith("warlockery:machine/")) continue;
            List<Object> all = manager.createRecipeLookup(category.getRecipeType()).includeHidden().get().toList();
            List<Object> visible = manager.createRecipeLookup(category.getRecipeType()).get().toList();
            categoryCounts.put(type, all.size());
            boolean informational = type.contains("information") || type.contains("tag");
            for (Object recipe : all) {
                String id = identifier(category, recipe);
                var supplier = manager.getRecipeIngredients(category, recipe);
                boolean own = id.startsWith("warlockery:");
                boolean involvesMod = Arrays.stream(RecipeIngredientRole.values()).flatMap(role -> supplier.getIngredients(role).stream())
                    .map(ingredient -> ((ITypedIngredient<?>) ingredient).getItemStack()).flatMap(Optional::stream)
                    .anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("warlockery"));
                if (informational || (!own && !involvesMod)) continue;
                String key = key(category, recipe);
                Expected contract = expected.get(id);
                if (contract != null && contract.kind.equals("machine") && contract.type.equals("alchemical_oven")) {
                    var census = matcherAudit.stream().filter(row -> row.get("id").equals(id)).findFirst().orElseThrow();
                    List<List<String>> winning = ((List<Set<String>>) census.get("winningInputAlternatives")).stream()
                        .map(alternatives -> alternatives.stream().sorted().toList()).toList();
                    contract = new Expected(contract.kind, contract.type, contract.inputSlots, contract.outputs, contract.special, winning);
                }
                if (contract == null && recipe instanceof RecipeHolder<?> holder) {
                    contract = new Expected("vanilla_compatibility", holder.value().getClass().getName(),
                        holder.value().placementInfo().ingredients().size(), holder.value().display().stream()
                        .flatMap(display -> display.result().resolveForStacks(SlotDisplayContext.fromLevel(net.minecraft.client.Minecraft.getInstance().level)).stream())
                        .map(JeiCatalogAcceptance::stack).distinct().toList(), holder.value().isSpecial(),
                        holder.value().placementInfo().ingredients().stream().map(input -> input.items().map(item -> stack(new ItemStack(item.value()))).sorted().toList()).toList());
                } else if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe dynamic) {
                    contract = new Expected("dynamic", "anvil", 2, dynamic.getOutputs().stream().map(JeiCatalogAcceptance::stack).distinct().toList(), true,
                        List.of(dynamic.getLeftInputs().stream().map(JeiCatalogAcceptance::stack).sorted().toList(), dynamic.getRightInputs().stream().map(JeiCatalogAcceptance::stack).sorted().toList()));
                } else if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe dynamic) {
                    contract = new Expected("dynamic", "grindstone", 1, dynamic.getOutputs().stream().filter(stack -> !stack.isEmpty()).map(JeiCatalogAcceptance::stack).distinct().toList(), true,
                        java.util.stream.Stream.of(dynamic.getTopInputs(), dynamic.getBottomInputs()).map(inputs -> inputs.stream().filter(stack -> !stack.isEmpty()).map(JeiCatalogAcceptance::stack).sorted().toList()).filter(inputs -> !inputs.isEmpty()).toList());
                } else if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe dynamic) {
                    contract = new Expected("dynamic", "fuel burn ticks=" + dynamic.getBurnTime(), 1, List.of(), true,
                        List.of(dynamic.getInputs().stream().map(JeiCatalogAcceptance::stack).sorted().toList()));
                }
                Coverage record = new Coverage(key, id, type, recipe.getClass().getName(), contract);
                if (record.expected != null && record.expected.kind.equals("custom_component")) {
                    componentAudit.stream().filter(row -> row.get("id").equals(id)).findFirst().ifPresent(row -> {
                        if (!(Boolean) row.get("allAlternativesSelectThisComponent")) record.problems.add("Custom component input is shadowed by another loaded component; see census");
                    });
                }
                if (record.expected != null && record.expected.kind.equals("machine")) {
                    matcherAudit.stream().filter(row -> row.get("id").equals(id)).findFirst().ifPresent(row -> {
                        if (!(Boolean) row.get("reachableOnDeclaredInputs")) record.problems.add("Live machine matcher never selects this recipe on declared input alternatives");
                        else if (!record.expected.type.equals("alchemical_oven") && !((Map<?, ?>) row.get("mismatchWitnesses")).isEmpty()) record.problems.add("Some advertised tag alternatives select a different machine recipe; see matcher census");
                    });
                }
                if (!visible.contains(recipe)) record.problems.add("Registered but hidden from JEI lookup");
                coverage.put(key, record);
                batches.computeIfAbsent(category, ignored -> new ArrayList<>()).add(recipe);
                for (RecipeIngredientRole role : List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT)) {
                    var ingredients = (List<ITypedIngredient<?>>) supplier.getIngredients(role);
                    for (var ingredient : ingredients) discover(category, recipe, ingredient, role, record);
                }
                var stations = manager.createCraftingStationLookup(category.getRecipeType()).get().toList();
                for (var station : stations) discover(category, recipe, station, RecipeIngredientRole.CRAFTING_STATION, record);
                record.craftingStations = stations.stream().map(JeiCatalogAcceptance::ingredient).toList();
                if ((record.expected != null && (record.expected.kind.equals("machine") || record.expected.kind.equals("ritual") || record.expected.kind.equals("custom_component"))) && stations.isEmpty()) record.problems.add("No category crafting station");
            }
        }
        return batches;
    }

    private void discover(IRecipeCategory category, Object recipe, ITypedIngredient ingredient,
        RecipeIngredientRole role, Coverage record) {
        var runtime = ManualJeiAcceptance.runtime();
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, ingredient);
        boolean found = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
            .limitFocus(List.of(focus)).get().anyMatch(candidate -> Objects.equals(candidate, recipe));
        String description = role + " " + ingredient(ingredient);
        record.discovery.put(description, found);
        if (!found) record.problems.add("Not discoverable by " + description);
    }

    private void inspectRendered(IRecipeLayoutDrawable<?> layout, String screenshot) {
        Coverage record = coverage.get(key(layout.getRecipeCategory(), layout.getRecipe()));
        if (record == null) { errors.add("Unexpected rendered recipe " + layout.getRecipe()); return; }
        record.rendered = true;
        record.screenshot = screenshot;
        var slots = layout.getRecipeSlotsView().getSlotViews();
        record.slots = slots.stream().map(slot -> Map.of("role", slot.getRole().toString(), "ingredients",
            slot.getAllIngredientsList().stream().map(JeiCatalogAcceptance::ingredient).toList())).toList();
        long inputs = slots.stream().filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT && !slot.isEmpty()).count();
        List<String> outputs = slots.stream().filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT
                || (record.category.equals("minecraft:grindstone") && slot.getRole() == RecipeIngredientRole.RENDER_ONLY))
            .flatMap(slot -> slot.getItemStacks()).map(JeiCatalogAcceptance::stack).distinct().toList();
        if (inputs == 0) record.problems.add("No nonempty input slots rendered");
        if (record.expected != null) {
            if (inputs < record.expected.inputSlots) record.problems.add("Expected at least " + record.expected.inputSlots + " input slots; rendered " + inputs);
            for (String output : record.expected.outputs) if (!outputs.contains(output)) record.problems.add("Expected output absent/wrong count: " + output + "; actual " + outputs);
            // The contract compares item identity and count, not component variants.
            // JEI expands potions, enchanted books and tipped arrows into many
            // component variants of the same item. Deduplicate within each slot,
            // never across slots: repeated recipe inputs must still match separately.
            List<List<String>> actualInputs = new ArrayList<>(slots.stream().filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
                .map(slot -> slot.getItemStacks().map(JeiCatalogAcceptance::stack).distinct().sorted().toList()).toList());
            for (var alternatives : record.expected.inputAlternatives) {
                if (alternatives.isEmpty()) record.problems.add("Authoritative input has no resolved item alternatives");
                // Dynamic anvil/grindstone definitions also contain component
                // variants: normalize the authoritative side by the same contract.
                var expectedAlternatives = alternatives.stream().distinct().sorted().toList();
                if (!actualInputs.remove(expectedAlternatives)) record.problems.add("Missing or incorrect input/count/alternatives: " + expectedAlternatives);
            }
        } else if (outputs.isEmpty() && !record.category.endsWith("_fuel")) record.problems.add("Dynamic recipe has no item output; needs explicit semantic review");
        List<String> allIngredients = slots.stream().flatMap(slot -> slot.getAllIngredientsList().stream()).map(JeiCatalogAcceptance::ingredient).toList();
        for (String fluid : expectedFluids.getOrDefault(record.id, List.of())) {
            if (!allIngredients.contains(fluid)) record.problems.add("Fluid identity or native amount incorrect: expected " + fluid + "; actual " + allIngredients);
        }
        if (layout.getRecipe() instanceof MachineRecipeManager.Match machine) {
            var category = layout.getRecipeCategory();
            if (machine.recipe().powerMode() == com.kadamitas.warlockery.crafting.PowerMode.CONTINUOUS)
                checkTextFits(record, category, "manual.warlockery.machine_recipe.continuous_power", "POWER_TEXT_HEIGHT");
            if (machine.recipe().machine().equals("brazier")) {
                checkTextFits(record, category, "manual.warlockery.machine_recipe.ignite", "IGNITION_TEXT_HEIGHT");
                com.kadamitas.warlockery.crafting.BrazierEffectRuntime.Effect.fromRecipe(machine.id()).ifPresent(effect ->
                    checkTextFits(record, category, "jei.warlockery.effect." + effect.recipePath(), "EFFECT_TEXT_HEIGHT"));
            }
        }
        var rect = layout.getRect();
        record.layoutBounds = List.of(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }

    private static void checkTextFits(Coverage record, IRecipeCategory<?> category, String key, String heightField) {
        var text = net.minecraft.network.chat.Component.translatable(key);
        int height = (int) read(category, heightField);
        int width = category.getWidth() - 4;
        int lines = net.minecraft.client.Minecraft.getInstance().font.split(text, width).size();
        record.textBounds.add(Map.of("text", text.getString(), "width", width, "height", height, "wrappedLines", lines));
        if (lines * 11 > height) record.problems.add("Operational instruction clips: " + key + " needs " + lines * 11 + " px; allocated " + height);
    }
    private void nativeKeys(ClientGameTestContext context, IRecipeCategory category, List<Object> recipes) {
        List<String> firstPage = context.computeOnClient(client -> visibleLayouts(client.gui.screen()).stream()
            .map(layout -> key(layout.getRecipeCategory(), layout.getRecipe())).toList());
        for (RecipeIngredientRole role : List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT)) {
            try {
                context.waitTicks(2);
                int[] point = context.computeOnClient(client -> {
                    // Back navigation can rebuild layouts; don't reuse stale slot coordinates.
                    for (var layout : visibleLayouts(client.gui.screen())) {
                        var rect = layout.getRect();
                        for (int y = rect.getY(); y < rect.getY() + rect.getHeight(); y += 8) {
                            for (int x = rect.getX(); x < rect.getX() + rect.getWidth(); x += 8) {
                                var slot = layout.getSlotUnderMouse(x, y);
                                if (slot.isPresent()) {
                                    var observed = slot.get();
                                    Object drawable = invoke(observed, "slot");
                                    if (drawable instanceof mezz.jei.api.gui.ingredient.IRecipeSlotView view && view.getRole() == role && !view.isEmpty()) {
                                        boolean cyclingTag = view.getTagKey().isPresent()
                                            && view.getDisplayedIngredients().limit(2).count() > 1;
                                        return new int[] {x, y, cyclingTag ? 1 : 0};
                                    }
                                }
                            }
                        }
                    }
                    return null;
                });
                if (point == null) continue;
                ManualClientAcceptance.cursor(context, point[0], point[1]);
                context.waitTicks(2);
                // JEI accepts Shift+R/U only for an active pinned ingredient tooltip.
                // Ordinary slots use unmodified keys; tagged cycling slots need pause
                // to select one ingredient rather than browse the whole tag.
                // Move first so Shift cannot pin an unrelated previous mouse target.
                if (point[2] != 0) {
                    context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                    context.waitTicks(2);
                }
                boolean hovered = context.computeOnClient(client -> ManualJeiAcceptance.runtime().getIngredientManager()
                    .getRegisteredIngredientTypes().stream().anyMatch(type -> ManualJeiAcceptance.runtime().getRecipesGui()
                        .getIngredientUnderMouse(type).isPresent()));
                if (!hovered) throw new AssertionError("Native key target has no displayed ingredient for "
                    + category.getRecipeType().getUid() + " " + role);
                context.getInput().pressKey(role == RecipeIngredientRole.OUTPUT ? com.mojang.blaze3d.platform.InputConstants.KEY_R : com.mojang.blaze3d.platform.InputConstants.KEY_U);
                context.waitTicks(2);
                boolean focused = context.computeOnClient(client -> {
                    var group = (mezz.jei.api.recipe.IFocusGroup) invoke(read(read(client.gui.screen(), "logic"), "state"), "getFocuses");
                    return group.getFocuses(role).findAny().isPresent() && !visibleLayouts(client.gui.screen()).isEmpty();
                });
                if (!focused) errors.add("Native recipe/use key failed to establish " + role + " focus for " + category.getRecipeType().getUid());
                navigation.add("Native " + (point[2] != 0 ? "Shift+" : "") + (role == RecipeIngredientRole.OUTPUT ? "R" : "U") + " from rendered " + category.getRecipeType().getUid() + " " + role);
                context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE);
                context.waitTicks(2);
            } finally {
                context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                // A native focus lookup can enter another category or recipe set.
                // Restore this audit's explicit batch, rather than assuming JEI's
                // history returns to that exact subset before the next shortcut/page.
                context.runOnClient(client -> ManualJeiAcceptance.runtime().getRecipesGui().showRecipes(category, recipes, List.of()));
                context.waitTicks(2);
                List<String> restored = context.computeOnClient(client -> visibleLayouts(client.gui.screen()).stream()
                    .map(layout -> key(layout.getRecipeCategory(), layout.getRecipe())).toList());
                if (!restored.equals(firstPage))
                    throw new AssertionError("Failed to restore first catalog page for " + category.getRecipeType().getUid());
            }
        }
    }

    private void nativeCategories(ClientGameTestContext context, List<IRecipeCategory> categories) {
        if (categories.size() < 2) return;
        context.runOnClient(client -> ManualJeiAcceptance.runtime().getRecipesGui().showTypes((List) categories.stream().map(IRecipeCategory::getRecipeType).toList()));
        context.waitTicks(2);
        for (int index = 0; index < categories.size(); index++) {
            String before = context.computeOnClient(client -> ((IRecipeCategory<?>) invoke(read(client.gui.screen(), "logic"), "getSelectedRecipeCategory")).getRecipeType().getUid().toString());
            clickField(context, "nextRecipeCategory");
            context.waitTicks(2);
            String after = context.computeOnClient(client -> ((IRecipeCategory<?>) invoke(read(client.gui.screen(), "logic"), "getSelectedRecipeCategory")).getRecipeType().getUid().toString());
            navigation.add("Native category transition " + before + " -> " + after);
            if (before.equals(after)) errors.add("Native category navigation did not advance from " + before);
        }
    }

    private void write(String name, boolean passed) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", passed);
        report.put("scope", MACHINES_ONLY ? "All loaded machine recipe categories; focused rerun after card layout change" : "All Warlockery and Warlockery-related recipes" );
        report.put("execution", "Rendered Fabric development client; fresh disposable world. Recipe batches selected with JEI showRecipes API; pages, category transitions and representative recipe/use shortcuts use native mouse/keys. Every recorded recipe inspected in actual RecipesGui layout, with independent live focus lookup for every indexed input/output/station ingredient.");
        report.put("expected", expected);
        report.put("machineMatcherCensus", matcherAudit);
        report.put("customComponentCensus", componentAudit);
        report.put("expectedNativeFluidAmounts", expectedFluids);
        report.put("coverage", coverage);
        report.put("categoryCountsIncludingHidden", categoryCounts);
        report.put("navigation", navigation);
        report.put("errors", errors);
        report.put("screenshots", screenshots);
        Files.writeString(evidence.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static List<IRecipeLayoutDrawable<?>> visibleLayouts(Object screen) {
        List<?> buttons = (List<?>) read(read(screen, "layouts"), "recipeLayoutsWithButtons");
        return buttons.stream().<IRecipeLayoutDrawable<?>>map(button -> (IRecipeLayoutDrawable<?>) invoke(button, "getRecipeLayout")).toList();
    }
    private static void clickField(ClientGameTestContext context, String field) {
        int[] point = context.computeOnClient(client -> {
            Object button = read(client.gui.screen(), field);
            return new int[] {(int) invoke(button, "getX") + (int) invoke(button, "getWidth") / 2,
                (int) invoke(button, "getY") + (int) invoke(button, "getHeight") / 2};
        });
        ManualClientAcceptance.click(context, point[0], point[1]);
    }
    private static Object read(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (Exception failure) { throw new AssertionError("Cannot observe " + name, failure); }
    }
    private static Object invoke(Object object, String name) {
        try { var method = object.getClass().getMethod(name); method.setAccessible(true); return method.invoke(object); }
        catch (Exception failure) { throw new AssertionError("Cannot observe " + name, failure); }
    }
    private static String identifier(IRecipeCategory category, Object recipe) {
        var id = category.getIdentifier(recipe);
        return id == null ? "dynamic:" + recipe.getClass().getSimpleName() + ":" + Integer.toHexString(recipe.hashCode()) : id.toString();
    }
    private static String key(IRecipeCategory category, Object recipe) { return category.getRecipeType().getUid() + "/" + identifier(category, recipe); }
    private static String stack(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount(); }
    private static String ingredient(ITypedIngredient<?> ingredient) {
        if (ingredient == null) return "<empty optional alternative>";
        if (ingredient.getIngredient() instanceof mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient fluid)
            return BuiltInRegistries.FLUID.getKey(fluid.getFluidVariant().getFluid()) + " x" + fluid.getAmount() + " droplets";
        return ingredient.getItemStack().map(JeiCatalogAcceptance::stack).orElseGet(() -> ingredient.getIngredient().toString());
    }
    private static String safe(String text) { return text.replaceAll("[^a-zA-Z0-9_-]", "_"); }
    private static List<String> resolve(String value, int count) {
        var parsed = com.kadamitas.warlockery.util.ItemIngredient.parse(value).orElseThrow();
        return BuiltInRegistries.ITEM.stream().map(item -> new ItemStack(item, count)).filter(parsed::matches)
            .map(JeiCatalogAcceptance::stack).sorted().toList();
    }
    private record Expected(String kind, String type, int inputSlots, List<String> outputs, boolean special,
        List<List<String>> inputAlternatives) { }
    private static final class Coverage {
        final String key, id, category, recipeClass;
        final Expected expected;
        final List<Map<String, Object>> textBounds = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final Map<String, Boolean> discovery = new LinkedHashMap<>();
        List<String> craftingStations = List.of();
        List<Map<String, Object>> slots = List.of();
        List<Integer> layoutBounds = List.of();
        String screenshot = "";
        boolean rendered;
        Coverage(String key, String id, String category, String recipeClass, Expected expected) {
            this.key = key; this.id = id; this.category = category; this.recipeClass = recipeClass; this.expected = expected;
        }
    }
}
