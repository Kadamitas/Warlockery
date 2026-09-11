package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class MachineWalkthroughAcceptance implements FabricClientGameTest {
    private static final BlockPos MACHINE = new BlockPos(0, 80, 0);
    private static final BlockPos ALTAR = MACHINE.offset(5, 0, 0);
    private static final Map<String, String> REPRESENTATIVES = Map.of(
        "alchemical_oven", "oven_fume_breath_of_the_goddess",
        "distillery", "distill_vitriol",
        "kettle", "kettle_brew_absorb_magic",
        "cauldron", "cauldron_colored_brew_water",
        "silvervat", "silver_vat_silver_dust",
        "spinningwheel", "spin_wool",
        "brazier", "brazier_summon_spectre"
    );
    private final List<String> screenshots = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private List<String> selectedScope = List.of();

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/new-player-audit"))
            .resolve("machines").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            final Set<String> types = MachineProfiles.blockIds().stream()
                .map(MachineProfiles::forBlock).map(MachineProfile::recipeType).collect(Collectors.toSet());
            check(types.equals(REPRESENTATIVES.keySet()), "Every MachineProfiles recipe type has a walkthrough: " + types);
            final String requested = System.getProperty("warlockery.machineTypes", "");
            final Set<String> selected = requested.isBlank() ? types : java.util.Arrays.stream(requested.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
            check(!selected.isEmpty() && types.containsAll(selected), "Requested machine scope must contain only known types: " + requested);
            selectedScope = selected.stream().sorted().toList();
            try (var created = context.worldBuilder().create()) {
                world = created;
                for (String kind : selectedScope) {
                    final Map<String, Object> result = new LinkedHashMap<>();
                    result.put("machine", kind);
                    result.put("block_ids", MachineProfiles.blockIds().stream()
                        .filter(id -> MachineProfiles.forBlock(id).recipeType().equals(kind)).sorted().toList());
                    result.put("recipe", REPRESENTATIVES.get(kind));
                    result.put("setup", "Fresh survival inventory, placed machine and safe platform, staged ingredients, "
                        + "camera alignment, external heat when required, complete six-block altar charged via receivePower. "
                        + "No outputs or processing progress are injected; real server ticks perform the working.");
                    results.add(result);
                    try {
                        walkthrough(context, kind, result);
                        result.put("passed", true);
                    } catch (Throwable failure) {
                        result.put("passed", false);
                        result.put("failure", failure.toString());
                        failures.add(kind + ": " + failure);
                        try { screenshot(context, kind + "-failure"); } catch (Throwable ignored) { }
                        closeScreen(context);
                    }
                    writeReport();
                }
                passiveClassification();
                writeReport();
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("WARLOCKERY_MACHINE_WALKTHROUGH_PASS " + evidence);
        } catch (Throwable failure) {
            try { Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Machine walkthrough evidence: " + evidence, failure);
        }
    }

    private void walkthrough(final ClientGameTestContext context, final String kind,
        final Map<String, Object> result) throws Exception {
        final MachineProfile profile = MachineProfiles.forRecipeType(kind).orElseThrow();
        final MachineRecipeDefinition recipe = MachineRecipeManager.INSTANCE.byId(
            Identifier.fromNamespaceAndPath("warlockery", REPRESENTATIVES.get(kind))).orElseThrow().recipe();
        final List<Integer> inputSlots = MachineRecipeSlotPlan.inputSlots(profile, recipe);
        stageMachine(context, profile, recipe);
        readBook(context, profile, result);
        if (profile.supportsFluids()) {
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(1).is(Items.BUCKET));
            check(serverValue(player -> machine(player).getFluidAmount()) == 1000,
                "Native water-bucket interaction fills exactly one bucket of water");
            result.put("fluid_interaction", "Right-click water bucket fills 1000 mB and returns empty bucket.");
        } else result.put("fluid_interaction", "This machine profile has no fluid tank.");
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        openMachine(context);
        context.waitFor(client -> jei() != null && jei().getIngredientListOverlay().isListDisplayed());
        check(context.computeOnClient(client -> ((MachineMenu) client.player.containerMenu).kind().equals(kind)),
            "Actual block use opens the expected machine GUI");
        screenshot(context, kind + "-empty-gui");
        rejectOutputInsertion(context, profile);
        if (profile.hasDedicatedInputSlot()) {
            clickPlayerSlot(context, 9);
            clickSlot(context, profile.dedicatedInputSlot());
            context.waitTicks(3);
            check(context.computeOnClient(client -> !client.player.containerMenu.getCarried().isEmpty()
                && client.player.containerMenu.getSlot(profile.dedicatedInputSlot()).getItem().isEmpty()),
                "Dedicated clay-jar slot rejects the ordinary ingredient");
            clickPlayerSlot(context, 9);
            result.put("container_slot", "Native click rejects ordinary ingredient in dedicated jar slot; jars placed there below.");
        }
        final boolean transfer = Set.of("alchemical_oven", "distillery", "spinningwheel").contains(kind);
        if (transfer) {
            transferRecipe(context, kind, profile, inputSlots, result);
        } else {
            for (int index = 0; index < recipe.inputs().size(); index++) {
                clickPlayerSlot(context, 9 + index);
                clickSlot(context, inputSlots.get(index));
                check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    "Recipe ingredient accepted in its shown machine slot");
            }
        }
        final List<String> inputActions = new ArrayList<>();
        for (int index = 0; index < recipe.inputs().size(); index++) {
            inputActions.add(recipe.inputs().get(index).ingredient() + " x" + recipe.inputs().get(index).count()
                + " -> machine slot " + inputSlots.get(index));
        }
        result.put(transfer ? "native_jei_transfer_slots" : "native_input_clicks", inputActions);
        final MachineStatus missing = profile.hasFuelSlot() ? MachineStatus.NO_FUEL
            : profile.requiresExternalHeat() ? MachineStatus.NO_HEAT
            : recipe.altarPower() > 0 ? MachineStatus.NO_ALTAR_POWER : null;
        if (missing != null) {
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == missing);
            screenshot(context, kind + "-missing-" + missing.name().toLowerCase());
            result.put("missing_requirement_status", missing.name());
        }
        checkJeiKeys(context, kind, inputSlots.getFirst(), result);
        if (profile.requiresExternalHeat()) {
            server(player -> player.level().setBlockAndUpdate(MACHINE.below(), Blocks.CAMPFIRE.defaultBlockState()));
        }
        if (recipe.altarPower() > 0) chargeAltar(context);
        if (profile.hasFuelSlot()) {
            clickPlayerSlot(context, 18);
            clickSlot(context, profile.fuelSlot());
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == MachineStatus.PROCESSING);
            result.put("fuel_interaction", "Native inventory click inserts one coal; live runtime consumes the fuel.");
        } else result.put("fuel_interaction", "No internal fuel slot; heat or altar requirement observed separately.");
        if (kind.equals("brazier")) {
            context.waitFor(client -> ((MachineMenu) client.player.containerMenu).status() == MachineStatus.NO_IGNITION);
            screenshot(context, "brazier-awaiting-ignition");
            closeScreen(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_3);
            aim(context, MACHINE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.player.getInventory().getItem(2).getDamageValue() > 0);
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            openMachine(context);
            result.put("ignition", "Actual flint-and-steel right-click ignites the brazier and consumes durability.");
        }
        context.waitFor(client -> ((MachineMenu) client.player.containerMenu).progressPercent() > 0);
        screenshot(context, kind + "-processing");
        context.waitTicks(recipe.processingTime() + 30);
        check(serverValue(player -> inputSlots.stream().allMatch(slot -> machine(player).getItem(slot).isEmpty())),
            "Live machine runtime consumes every staged recipe input");
        for (int index = 0; index < recipe.outputs().size(); index++) {
            final int slot = profile.outputStart() + index;
            final var output = recipe.outputs().get(index);
            check(serverValue(player -> {
                final ItemStack actual = machine(player).getItem(slot);
                return BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(output.item())
                    && actual.getCount() == output.count();
            }), "Expected live machine output " + output.item() + " x" + output.count());
        }
        if (recipe.fluid().isPresent()) {
            check(serverValue(player -> machine(player).getFluidAmount()) == 1000 - recipe.fluid().orElseThrow().amount(),
                "Recipe consumes the displayed fluid amount");
        }
        if (kind.equals("brazier")) {
            check(serverValue(player -> !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(MACHINE).inflate(12),
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals("warlockery:spectre")).isEmpty()),
                "Brazier live completion summons a spectre; the ash alone is not completion evidence");
        }
        screenshot(context, kind + "-completed-output");
        for (int index = 0; index < recipe.outputs().size(); index++) {
            clickSlot(context, profile.outputStart() + index);
            clickPlayerSlot(context, 27 + index);
        }
        check(serverValue(player -> {
            for (int index = 0; index < recipe.outputs().size(); index++) {
                if (!BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(27 + index).getItem()).toString()
                    .equals(recipe.outputs().get(index).item())) return false;
            }
            return true;
        }), "Native output clicks deliver all results to survival inventory");
        result.put("completion", "Actual server tick processing consumed inputs and exact fluid and produced every expected output; "
            + "native output clicks collected results into survival inventory.");
        closeScreen(context);
        if (kind.equals("silvervat")) passiveSilver(context, result);
    }

    private void stageMachine(final ClientGameTestContext context, final MachineProfile profile,
        final MachineRecipeDefinition recipe) {
        closeScreen(context);
        server(player -> {
            final var level = player.level();
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                for (int y = 80; y <= 83; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(MACHINE, ModBlocks.ALL.get(profile.displayBlock()).get().defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = true;
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
            player.getInventory().setItem(2, new ItemStack(Items.FLINT_AND_STEEL));
            player.getInventory().setItem(17, new ItemStack(Items.STONE));
            player.getInventory().setItem(18, new ItemStack(Items.COAL));
            for (int index = 0; index < recipe.inputs().size(); index++) {
                final var input = recipe.inputs().get(index);
                final var ingredient = ItemIngredient.parse(input.ingredient()).orElseThrow();
                final var item = BuiltInRegistries.ITEM.stream().filter(candidate -> ingredient.matches(new ItemStack(candidate)))
                    .findFirst().orElseThrow(() -> new AssertionError("No loaded item matches " + input.ingredient()));
                player.getInventory().setItem(9 + index, new ItemStack(item, input.count()));
            }
            player.teleportTo(0.5, 80, -2.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        aim(context, MACHINE);
    }

    private void readBook(final ClientGameTestContext context, final MachineProfile profile,
        final Map<String, Object> result) throws Exception {
        final String recipeId = REPRESENTATIVES.get(profile.recipeType());
        final String recipeSection = profile.recipeType().equals("kettle")
            ? "brew_entry_" + recipeId.substring("kettle_brew_".length()) : "machine_recipe_" + recipeId;
        final var exact = ManualProfile.profiles().stream().filter(book -> book.sections().contains(recipeSection)).findFirst();
        final String fallback = profile.recipeType().equals("brazier") ? "ingredient_book_burning" : "cauldronbook";
        final ManualProfile book = exact.orElseGet(() -> ManualProfile.find(fallback).orElseThrow());
        final String section = exact.isPresent() ? recipeSection : book.sections().getFirst();
        server(player -> { player.getInventory().setItem(3, new ItemStack(ModItems.ALL.get(book.id()).get()));
            player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_4);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        final String title = context.computeOnClient(client -> Component.translatable(book.translatedSectionTitleKey(section)).getString());
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        ManualClientAcceptance.clickButton(context, Component.translatable(book.chapterFor(section).titleKey()).getString());
        ManualClientAcceptance.clickButton(context, title);
        context.waitFor(client -> field(client.gui.screen(), "selectedSection").equals(section));
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString());
        result.put("book", book.id());
        result.put("book_section", section);
        result.put("book_instructions", text);
        if (profile.recipeType().equals("brazier")) {
            final String ignition = context.computeOnClient(client -> Component.translatable("manual.warlockery.machine_recipe.ignite").getString());
            check(text.contains(ignition) && ignition.contains("Flint and Steel") && ignition.contains("Fire Charge"),
                "The actual Brazier recipe article explains closing its GUI and igniting the block with a held fire source");
            result.put("book_ignition_instructions", ignition);
        }
        result.put("book_coverage", exact.isPresent() ? "Indexed exact recipe or brew entry opened by native book search and buttons."
            : "GAP: no indexed machine_recipe section for this representative; nearest subject book opened. GUI actions below use runtime-derived test data, not an invented claim of book-only guidance.");
        screenshot(context, profile.recipeType() + "-book-guidance");
        checkBookSetupReference(context, profile.recipeType(), book, section, result);
        closeScreen(context);
    }

    private void checkBookSetupReference(final ClientGameTestContext context, final String kind,
        final ManualProfile book, final String section, final Map<String, Object> result) throws Exception {
        final var reference = ManualBookLinks.references(book, section).stream().findFirst().orElseThrow(
            () -> new AssertionError("Machine recipe lacks its setup-book reference: " + kind));
        final String expectedSection = switch (kind) {
            case "alchemical_oven" -> "oven";
            case "distillery" -> "inputs";
            case "brazier" -> "brazier";
            case "spinningwheel" -> "spinningwheel";
            case "silvervat" -> "silvervat";
            default -> "machines";
        };
        check(reference.section().equals(expectedSection), "Recipe reference targets the appropriate machine setup instructions");
        final String label = context.computeOnClient(client -> reference.label().getString());
        double[] point = null;
        for (int page = 0; page < 60; page++) {
            point = context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.visible && button.active && button.getClass().getSimpleName().equals("ManualReferenceButton")
                    && button.getMessage().getString().equals(label))
                .map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0})
                .findFirst().orElse(null));
            screenshot(context, kind + "-recipe-reading-page-" + (page + 1));
            if (point != null) break;
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(section)),
                "Setup reference is reachable within the recipe's own pages");
        }
        check(point != null, "Real machine recipe pages expose their visible setup reference");
        if (!reference.profile().id().equals(book.id())) {
            server(player -> {
                player.getInventory().setItem(4, new ItemStack(ModItems.ALL.get(reference.profile().id()).get()));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(2);
        }
        final Object source = context.computeOnClient(client -> client.gui.screen());
        final int sourcePage = context.computeOnClient(client -> (int) field(source, "bodyPage"));
        ManualClientAcceptance.click(context, point[0], point[1]);
        context.waitFor(client -> client.gui.screen() instanceof ManualScreen && client.gui.screen() != source);
        check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(expectedSection)
            && ((ManualProfile) field(client.gui.screen(), "manual")).id().equals(reference.profile().id())
            && !(boolean) field(client.gui.screen(), "recipePreview")), "Actual owned-book link opens machine setup instructions");
        screenshot(context, kind + "-linked-setup-instructions");
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back").getString());
        context.waitFor(client -> client.gui.screen() == source);
        check(context.computeOnClient(client -> (int) field(source, "bodyPage") == sourcePage
            && field(source, "selectedSection").equals(section)), "Back restores the exact machine recipe page");
        result.put("native_setup_link", reference.profile().id() + "/" + expectedSection);
        result.put("setup_link_fixture", "Source book is staged before native opening; an additional target book is staged only when the link crosses books.");
    }

    private void rejectOutputInsertion(final ClientGameTestContext context, final MachineProfile profile) {
        clickPlayerSlot(context, 17);
        clickSlot(context, profile.outputStart());
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(Items.STONE)
            && client.player.containerMenu.getSlot(profile.outputStart()).getItem().isEmpty()),
            "Actual GUI rejects player insertion into output slot");
        clickPlayerSlot(context, 17);
    }

    private void checkJeiKeys(final ClientGameTestContext context, final String kind, final int ingredientSlot,
        final Map<String, Object> result) throws Exception {
        context.waitFor(client -> jei() != null && jei().getIngredientListOverlay().isListDisplayed());
        cursorSlot(context, ingredientSlot);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_U);
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        screenshot(context, kind + "-jei-ingredient-uses");
        closeScreen(context);
        context.waitForScreen(MachineScreen.class);
        cursorSlot(context, ingredientSlot);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_R);
        context.waitTicks(5);
        final boolean recipeScreen = context.computeOnClient(client -> client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        if (recipeScreen) {
            screenshot(context, kind + "-jei-ingredient-recipes");
            closeScreen(context);
            context.waitForScreen(MachineScreen.class);
        }
        final int[] recipeArea = context.computeOnClient(client -> {
            final var properties = jei().getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            final var layout = ((MachineMenu) client.player.containerMenu).layout();
            return new int[] {properties.guiLeft() + 25 + (layout.width() - 42) / 2,
                properties.guiTop() + layout.statusY() + 8};
        });
        ManualClientAcceptance.click(context, recipeArea[0], recipeArea[1]);
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        check(context.computeOnClient(client -> {
            final Object logic = field(client.gui.screen(), "logic");
            try {
                final var selected = logic.getClass().getMethod("getSelectedRecipeCategory");
                selected.setAccessible(true);
                final var category = (mezz.jei.api.recipe.category.IRecipeCategory<?>) selected.invoke(logic);
                return category.getRecipeType().equals(com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get(kind));
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe clicked machine recipe category", failure); }
        }), "Native status-area click opens the exact " + kind + " JEI category");
        screenshot(context, kind + "-jei-machine-recipe-area");
        closeScreen(context);
        context.waitForScreen(MachineScreen.class);
        result.put("jei", Map.of("native_uses_key", true, "native_recipe_key", recipeScreen,
            "recipe_key_note", recipeScreen ? "Recipe display opened from actual machine slot." : "This input has no recipe shown for R; U remains available.",
            "native_machine_recipe_area", kind,
            "transfer", result.containsKey("transfer_setup") ? result.get("transfer_setup")
                : "This representative uses native slot insertion; fluid recipes do not offer automatic ingredient transfer."));
    }

    @SuppressWarnings("unchecked")
    private void transferRecipe(final ClientGameTestContext context, final String kind, final MachineProfile profile,
        final List<Integer> inputSlots, final Map<String, Object> result) throws Exception {
        final List<ItemStack> expected = context.computeOnClient(client -> java.util.stream.IntStream.range(0, inputSlots.size())
            .mapToObj(index -> client.player.getInventory().getItem(9 + index).copy()).toList());
        context.runOnClient(client -> {
            final var type = com.kadamitas.warlockery.compat.jei.WarlockeryJeiRecipeTypes.MACHINES.get(kind);
            final var category = (mezz.jei.api.recipe.category.IRecipeCategory<MachineRecipeManager.Match>) jei().getRecipeManager()
                .createRecipeCategoryLookup().get().filter(value -> value.getRecipeType().equals(type)).findFirst().orElseThrow();
            final var recipe = jei().getRecipeManager().createRecipeLookup(type).get()
                .filter(value -> value.id().getPath().equals(REPRESENTATIVES.get(kind))).findFirst().orElseThrow();
            jei().getRecipesGui().showRecipes(category, List.of(recipe), List.of());
        });
        context.waitFor(client -> client.gui.screen().getClass().getSimpleName().equals("RecipesGui"));
        context.waitTicks(4);
        final int[] point = context.computeOnClient(client -> {
            final Object layouts = field(client.gui.screen(), "layouts");
            final Object layout = ((List<?>) field(layouts, "recipeLayoutsWithButtons")).getFirst();
            final Object transferController = field(layout, "transferButton");
            final Object button = ((List<?>) field(layout, "buttons")).stream()
                .filter(candidate -> field(candidate, "controller") == transferController).findFirst().orElseThrow();
            try {
                check((boolean) button.getClass().getMethod("isVisible").invoke(button), "JEI transfer button is visible for " + kind);
                return new int[] {(int) button.getClass().getMethod("getX").invoke(button)
                        + (int) button.getClass().getMethod("getWidth").invoke(button) / 2,
                    (int) button.getClass().getMethod("getY").invoke(button)
                        + (int) button.getClass().getMethod("getHeight").invoke(button) / 2};
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe native JEI transfer button", failure); }
        });
        screenshot(context, kind + "-jei-transfer-ready");
        ManualClientAcceptance.click(context, point[0], point[1]);
        context.waitForScreen(MachineScreen.class);
        context.waitFor(client -> java.util.stream.IntStream.range(0, inputSlots.size()).allMatch(index ->
            ItemStack.matches(expected.get(index), client.player.containerMenu.getSlot(inputSlots.get(index)).getItem())));
        if (profile.hasFuelSlot()) check(context.computeOnClient(client -> client.player.containerMenu.getSlot(profile.fuelSlot()).getItem().isEmpty()),
            "JEI transfers recipe ingredients without treating the fuel slot as recipe input");
        screenshot(context, kind + "-jei-transferred-inputs");
        result.put("transfer_setup", "JEI API selects the exact representative recipe; actual rendered + button is clicked. Runtime transfer places every staged ingredient/count in its semantic machine slot; fuel stays empty.");
    }

    private void chargeAltar(final ClientGameTestContext context) {
        server(player -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
            }
        });
        context.waitTicks(41);
        server(player -> {
            final var altar = (AltarBlockEntity) player.level().getBlockEntity(ALTAR);
            check(altar.isMultiblockValid(), "Fixture altar is a real complete six-block multiblock");
            check(altar.receivePower(1000) > 0, "Fixture charges real altar reserve through its power API");
        });
        context.waitTicks(3);
    }

    private void passiveSilver(final ClientGameTestContext context, final Map<String, Object> result) throws Exception {
        final BlockPos furnace = MACHINE.east();
        server(player -> {
            player.level().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
            player.getInventory().setItem(19, new ItemStack(Items.GOLD_ORE));
            player.getInventory().setItem(20, new ItemStack(Items.COAL));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        aim(context, furnace);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(net.minecraft.client.gui.screens.inventory.FurnaceScreen.class);
        clickPlayerSlot(context, 19);
        clickSlot(context, 0);
        clickPlayerSlot(context, 20);
        clickSlot(context, 1);
        screenshot(context, "silvervat-passive-adjacent-furnace");
        context.waitFor(client -> client.player.containerMenu.getSlot(2).getItem().is(Items.GOLD_INGOT), 300);
        closeScreen(context);
        openMachine(context);
        context.waitFor(client -> client.player.containerMenu.getSlot(6).getItem().is(ModItems.ALL.get("ingredient_silverdust").get()));
        screenshot(context, "silvervat-passive-silver-deposit");
        clickSlot(context, 6);
        clickPlayerSlot(context, 31);
        result.put("passive_behavior", "Native furnace GUI insertion smelts staged gold ore with coal; adjacent Silver Vat runtime yields one silver deposit, collected by native vat GUI click.");
        closeScreen(context);
    }

    private void passiveClassification() {
        results.add(Map.of("support_blocks", List.of("altar", "fumefunnel", "filteredfumefunnel"),
            "classification", "Not MachineProfiles processing types. Altar is exercised as a real six-block power source. "
                + "Fume funnels are passive adjacent oven upgrades, have no independent machine GUI or input/output inventory, "
                + "and are classified here rather than silently skipped. Their yield upgrade is not part of this representative-machine GUI run."));
    }

    private void openMachine(final ClientGameTestContext context) {
        aim(context, MACHINE);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(MachineScreen.class);
    }

    private static void aim(final ClientGameTestContext context, final BlockPos target) {
        context.runOnClient(client -> {
            final var delta = net.minecraft.world.phys.Vec3.atCenterOf(target).subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target)),
            "Staged camera ray targets the actual block before native use");
    }

    private static void clickPlayerSlot(final ClientGameTestContext context, final int index) {
        final int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(candidate -> client.player.containerMenu.getSlot(candidate).container == client.player.getInventory()
                && client.player.containerMenu.getSlot(candidate).getContainerSlot() == index).findFirst().orElseThrow());
        clickSlot(context, slot);
    }

    private static void cursorSlot(final ClientGameTestContext context, final int slotIndex) {
        final int[] point = context.computeOnClient(client -> {
            final var properties = jei().getScreenHelper().getGuiProperties(client.gui.screen()).orElseThrow();
            final var slot = client.player.containerMenu.getSlot(slotIndex);
            return new int[] {properties.guiLeft() + slot.x + 8, properties.guiTop() + slot.y + 8};
        });
        ManualClientAcceptance.cursor(context, point[0], point[1]);
    }

    private static void clickSlot(final ClientGameTestContext context, final int slotIndex) {
        cursorSlot(context, slotIndex);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(3);
        }
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport() throws Exception {
        Files.writeString(evidence.resolve("machine-walkthrough.json"), new GsonBuilder().setPrettyPrinting().create()
            .toJson(Map.of("results", results, "failures", failures, "screenshots", screenshots,
                "selected_machine_types", selectedScope,
                "all_machine_types_selected", Set.copyOf(selectedScope).equals(REPRESENTATIVES.keySet()),
                "execution", "Real rendered Fabric client, real server machine ticks, native mouse/key interactions; fixture setup is separately declared.")));
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }

    private static MagicMachineBlockEntity machine(final ServerPlayer player) {
        return (MagicMachineBlockEntity) player.level().getBlockEntity(MACHINE);
    }

    private static IJeiRuntime jei() { return ManualJeiAcceptance.runtime(); }

    private static Object field(final Object object, final String name) {
        try {
            final var field = (object instanceof Class<?> type ? type : object.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object instanceof Class<?> ? null : object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe " + name, failure); }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
