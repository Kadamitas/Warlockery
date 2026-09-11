package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

public final class ManualClientAcceptance implements FabricClientGameTest {
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();

    @Override
    public void runTest(final ClientGameTestContext context) {
        final long started = System.currentTimeMillis();
        final Path evidence = Path.of(System.getProperty("warlockery.clientEvidence"))
            .resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_book_circle_magic").get()));
                    player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("chalkheart").get()));
                    player.getInventory().setItem(2, new ItemStack(ModItems.ALL.get("ritual_knife").get()));
                    player.getInventory().setItem(3, new ItemStack(ModItems.ALL.get("ingredient_book_distilling").get()));
                    player.getInventory().setItem(9, new ItemStack(ModItems.ALL.get("ingredient_gypsum").get()));
                    player.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.DYE.yellow()));
                    player.getInventory().setItem(12, new ItemStack(net.minecraft.world.item.Items.DYE.red()));
                    check(server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.RECIPE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("warlockery", "chalkheart_from_gypsum")))
                        .isPresent(), "Actual server recipe manager loads Golden Chalk without rejecting output count");
                    player.getInventory().setSelectedSlot(0);
                    player.inventoryMenu.broadcastChanges();
                });
                world.getConnection().waitForChunksRender();
                world.getConnection().waitForClientboundPackets();
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                context.waitForScreen(ManualScreen.class);
                checks.add("Actual survival item use opens the circle manual.");
                checkFirstChalkChapter(context, evidence);
                for (int scale : new int[] {2, 3}) {
                    context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); });
                    if (scale == 3) checkChalkScrolling(context, evidence);
                    selectSection(context, "golden_chalk");
                    context.waitFor(client -> selected(client.gui.screen()).equals("golden_chalk"));
                    assertArticle(context, "Gypsum", "Quicklime");
                    screenshot(context, evidence, "scale-" + scale + "-golden-chalk");
                    selectSection(context, "arthana");
                    context.waitFor(client -> selected(client.gui.screen()).equals("arthana"));
                    assertArticle(context, "Ritual Knife");
                    screenshot(context, evidence, "scale-" + scale + "-arthana");
                    selectSection(context, "crafting_chalkheart_from_gypsum");
                    context.waitFor(client -> selected(client.gui.screen()).equals("crafting_chalkheart_from_gypsum"));
                    assertArticle(context, "Gypsum");
                    screenshot(context, evidence, "scale-" + scale + "-golden-crafting");
                    selectSection(context, "crafting_ritual_knife");
                    screenshot(context, evidence, "scale-" + scale + "-knife-crafting");
                    assertArticle(context, "Flint", "Stick");
                    selectSection(context, "rite_summon_familiar");
                    context.waitFor(client -> selected(client.gui.screen()).equals("rite_summon_familiar"));
                    check(context.computeOnClient(client -> !article(client.gui.screen()).glyphs().isEmpty()),
                        "Ritual page has an actual chalk-circle diagram");
                    screenshot(context, evidence, "scale-" + scale + "-circle-diagram");
                    assertWidgetsFit(context);
                    assertNavigationLabelsFit(context);
                    if (scale == 3) {
                        assertFamiliarNavigationWraps(context);
                        screenshot(context, evidence, "scale-3-full-familiar-navigation");
                    }
                    checks.add("GUI scale " + scale + ": native search reaches Golden Chalk, Arthana, crafting and a ritual diagram.");
                }
                selectSection(context, "golden_chalk");
                clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final String remembered = context.computeOnClient(client -> selected(client.gui.screen()));
                final int page = context.computeOnClient(client -> (int) field(client.gui.screen(), "bodyPage"));
                check(remembered.equals("golden_chalk") && page > 0, "Next reaches a later page of the long Golden Chalk guide");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                context.waitForScreen(ManualScreen.class);
                check(context.computeOnClient(client -> selected(client.gui.screen()).equals(remembered)
                    && (int) field(client.gui.screen(), "bodyPage") == page), "Reopening preserves section and body page");
                screenshot(context, evidence, "reopened-reading-position");
                checks.add("Closing and reopening through actual item use preserves section and page.");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                context.getInput().pressKey(GLFW.GLFW_KEY_4);
                context.waitFor(client -> client.player.getMainHandItem().is(ModItems.ALL.get("ingredient_book_distilling").get()));
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                context.waitForScreen(ManualScreen.class);
                selectSection(context, "machine_recipe_distill_vitriol");
                assertArticle(context, "Gypsum", "480");
                screenshot(context, evidence, "distilling-gypsum-power");
                clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                check(context.computeOnClient(client -> selected(client.gui.screen()).equals("machine_recipe_distill_vitriol")
                    && (int) field(client.gui.screen(), "bodyPage") > 0), "Distilling results and power continue on the next page");
                screenshot(context, evidence, "distilling-gypsum-results-and-power");
                checks.add("Distilling book displays the real Gypsum recipe and altar power cost.");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                ManualJeiAcceptance.run(context, evidence, screenshots, checks);
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    check(player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).isEmpty(),
                        "Survival crafting consumed Gypsum and Yellow Dye on the server");
                    check(player.getInventory().getItem(11).is(ModItems.ALL.get("chalkheart").get())
                        && player.getInventory().getItem(11).getCount() == 1
                        && player.getInventory().getItem(11).getMaxDamage() == 64
                        && player.getInventory().getItem(11).getDamageValue() == 0,
                        "Survival crafting delivered exactly one undamaged Golden Chalk with 64 uses on the server");
                    check(player.getInventory().getItem(12).is(net.minecraft.world.item.Items.DYE.red())
                        && player.getInventory().getItem(12).getCount() == 1,
                        "The rejected red dye was returned without being consumed");
                });
            }
            Files.writeString(evidence.resolve("pass.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "pid", ProcessHandle.current().pid(), "started_at", started,
                "finished_at", System.currentTimeMillis(), "checks", checks, "screenshots", screenshots
            )));
            System.out.println("WARLOCKERY_CLIENT_ACCEPTANCE_PASS " + evidence);
        } catch (Throwable failure) {
            try {
                saveScreenshot(context, evidence, "failure", screenshots);
                Files.writeString(evidence.resolve("failure.txt"), failure.toString());
            } catch (Exception captureFailure) { failure.addSuppressed(captureFailure); }
            throw new AssertionError("Warlockery client acceptance failed; evidence " + evidence, failure);
        }
    }

    private void checkFirstChalkChapter(final ClientGameTestContext context, final Path evidence) throws Exception {
        final List<String> recipes = List.of("crafting_chalkritual", "crafting_chalkheart_from_gypsum",
            "crafting_chalkinfernal", "crafting_chalk_veil");
        String firstTitle = context.computeOnClient(client -> {
            var first = ((ManualProfile) field(client.gui.screen(), "manual")).chapters().getFirst();
            check(first.sections().containsAll(recipes), "The first Circle Magic chapter contains all four chalk recipes");
            return Component.translatable(first.titleKey()).getString();
        });
        check(firstTitle.equals("Chalk"), "Chalk is the first Circle Magic chapter");
        check(context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex")),
            "A newly opened Circle Magic manual starts at its contents");
        screenshot(context, evidence, "circle-contents-chalk-first");
        clickButton(context, firstTitle);
        assertNavigationLabelsFit(context);
        screenshot(context, evidence, "first-chapter-all-four-chalk-recipes");
        for (String section : recipes) {
            String title = context.computeOnClient(client -> Component.translatable(
                ((ManualProfile) field(client.gui.screen(), "manual")).translatedSectionTitleKey(section)).getString());
            clickButton(context, title);
            context.waitFor(client -> selected(client.gui.screen()).equals(section));
            assertArticle(context, "Produces");
            assertNavigationLabelsFit(context);
            screenshot(context, evidence, "first-chapter-" + section);
        }
        checks.add("Fresh Circle Magic contents opens Chalk first, and native chapter buttons open all four chalk crafting recipes.");
    }

    private static void assertNavigationLabelsFit(final ClientGameTestContext context) {
        List<String> failures = context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.visible && button.getClass().getSimpleName().equals("ManualNavigationButton"))
            .filter(button -> button.getHeight() < Math.max(18, client.font.split(
                ManualTypography.readable(button.getMessage()), Math.max(1, button.getWidth() - 8)).size() * 11 + 8))
            .map(button -> button.getMessage().getString() + " within " + button.getWidth() + "x" + button.getHeight())
            .toList());
        check(failures.isEmpty(), "Every navigation button has room for its complete wrapped label: " + failures);
    }

    private void checkChalkScrolling(final ClientGameTestContext context, final Path evidence) throws Exception {
        search(context, "");
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        clickNavigationButton(context, "Chalk");
        final String selectedBefore = context.computeOnClient(client -> selected(client.gui.screen()));
        final String lastTitle = context.computeOnClient(client -> {
            ManualProfile manual = (ManualProfile) field(client.gui.screen(), "manual");
            return Component.translatable(manual.translatedSectionTitleKey(manual.chapters().getFirst().sections().getLast())).getString();
        });
        int advances = 0;
        while (!buttonVisible(context, lastTitle) && advances < 30) {
            int before = navigationOffset(context);
            scrollNavigation(context, -1);
            check(navigationOffset(context) > before, "Native downward scrolling advances the navigation list");
            check(context.computeOnClient(client -> selected(client.gui.screen()).equals(selectedBefore)),
                "Scrolling leaves the selected article unchanged even when its button is offscreen");
            assertNavigationLabelsFit(context);
            advances++;
        }
        check(advances > 0 && buttonVisible(context, lastTitle), "Native scrolling reaches the last Chalk chapter entry at GUI scale 3");
        screenshot(context, evidence, "scale-3-chalk-navigation-bottom");
        while (navigationOffset(context) > 0) {
            int before = navigationOffset(context);
            scrollNavigation(context, 1);
            check(navigationOffset(context) < before, "Native upward scrolling returns toward the first entry");
        }
        check(buttonVisible(context, "Chalk"), "Scrolling back restores the first Chalk article button");
        check(context.computeOnClient(client -> selected(client.gui.screen()).equals(selectedBefore)),
            "Manual scrolling never changes the selected article");
        screenshot(context, evidence, "scale-3-chalk-navigation-top");
        for (String section : List.of("crafting_chalkritual", "crafting_chalkinfernal", "crafting_chalk_veil", "crafting_chalkheart_from_gypsum")) {
            String title = context.computeOnClient(client -> Component.translatable(
                ((ManualProfile) field(client.gui.screen(), "manual")).translatedSectionTitleKey(section)).getString());
            clickNavigationButton(context, title);
            context.waitFor(client -> selected(client.gui.screen()).equals(section));
            assertNavigationLabelsFit(context);
        }
        checks.add("At GUI scale 3, native scrolling reaches the last Chalk entry and returns to the first without snapping to the selected offscreen article; all four chalk recipes remain accessible.");
    }

    private static int navigationOffset(final ClientGameTestContext context) {
        return context.computeOnClient(client -> (int) field(client.gui.screen(), "sectionOffset"));
    }

    private static void scrollNavigation(final ClientGameTestContext context, final int direction) {
        int[] point = context.computeOnClient(client -> {
            ManualLayout layout = ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height);
            return new int[] {layout.navigationLeft() + 12, layout.sectionListTop() + 12};
        });
        cursor(context, point[0], point[1]);
        context.getInput().scroll(direction);
        context.waitTicks(3);
    }

    private static boolean buttonVisible(final ClientGameTestContext context, final String label) {
        return context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(Button.class::isInstance).map(Button.class::cast)
            .anyMatch(button -> button.visible && button.active && button.getMessage().getString().replace("▶ ", "").equals(label)));
    }

    private static void clickNavigationButton(final ClientGameTestContext context, final String label) {
        for (int attempt = 0; !buttonVisible(context, label) && attempt < 30; attempt++) {
            int before = navigationOffset(context);
            scrollNavigation(context, -1);
            if (navigationOffset(context) == before) break;
        }
        for (int attempt = 0; !buttonVisible(context, label) && navigationOffset(context) > 0 && attempt < 30; attempt++) {
            scrollNavigation(context, 1);
        }
        clickButton(context, label);
    }

    private static void assertFamiliarNavigationWraps(final ClientGameTestContext context) {
        check(context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.visible && button.getMessage().getString().replace("▶ ", "")
                .equals(Component.translatable("ritual.warlockery.summon_familiar.title").getString()))
            .anyMatch(button -> button.getClass().getSimpleName().equals("ManualNavigationButton")
                && client.font.split(ManualTypography.readable(button.getMessage()), button.getWidth() - 8).size() >= 2)),
            "The full Rite of Summoning: Familiar navigation label wraps across multiple lines at GUI scale 3");
    }

    private static void selectSection(final ClientGameTestContext context, final String section) {
        String title = context.computeOnClient(client -> Component.translatable(
            ((ManualProfile) field(client.gui.screen(), "manual")).translatedSectionTitleKey(section)).getString());
        search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        String chapter = context.computeOnClient(client -> Component.translatable(
            ((ManualProfile) field(client.gui.screen(), "manual")).chapterFor(section).titleKey()).getString());
        clickButton(context, chapter);
        clickButton(context, title);
        context.waitFor(client -> selected(client.gui.screen()).equals(section));
    }

    static void search(final ClientGameTestContext context, final String query) {
        final double[] point = context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(EditBox.class::isInstance).map(EditBox.class::cast)
            .map(box -> new double[] {box.getX() + box.getWidth() / 2.0, box.getY() + 10})
            .findFirst().orElseThrow());
        click(context, point[0], point[1]);
        int length = context.computeOnClient(client -> ((String) field(client.gui.screen(), "query")).length());
        context.getInput().pressKey(GLFW.GLFW_KEY_END);
        for (int index = 0; index < length; index++) {
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
            context.waitTicks(1);
        }
        context.getInput().typeChars(query);
        context.waitTicks(4);
        String actual = context.computeOnClient(client -> (String) field(client.gui.screen(), "query"));
        if (!actual.equals(query)) {
            context.takeScreenshot("search-input-mismatch");
            throw new AssertionError("Native manual search expected '" + query + "', got '" + actual + "'");
        }
    }

    static void clickButton(final ClientGameTestContext context, final String label) {
        final double[] point = context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(button -> button.visible && button.active && button.getMessage().getString().replace("▶ ", "").equals(label))
            .map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0})
            .findFirst().orElseGet(() -> new double[] {-1, -1}));
        if (point[0] < 0) {
            Path capture = context.takeScreenshot("missing-button-" + label.replaceAll("[^a-zA-Z0-9]", "_"));
            String visible = context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .map(button -> button.getMessage().getString() + " active=" + button.active).toList().toString());
            throw new AssertionError("Missing active button: " + label + "; actual " + visible + "; screenshot " + capture);
        }
        click(context, point[0], point[1]);
    }

    static void click(final ClientGameTestContext context, final double x, final double y) {
        cursor(context, x, y);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    static void cursor(final ClientGameTestContext context, final double x, final double y) {
        final double[] point = context.computeOnClient(client -> new double[] {
            x * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
            y * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()
        });
        context.getInput().setCursorPos(point[0], point[1]);
    }

    private void screenshot(final ClientGameTestContext context, final Path evidence, final String name) throws Exception {
        saveScreenshot(context, evidence, name, screenshots);
    }

    static void saveScreenshot(final ClientGameTestContext context, final Path evidence, final String name,
        final List<String> screenshots) throws Exception {
        context.waitTicks(3);
        Path captured = context.takeScreenshot(name);
        Path destination = evidence.resolve(name + ".png");
        Files.copy(captured, destination);
        screenshots.add(destination.toString());
    }

    private static void assertWidgetsFit(final ClientGameTestContext context) {
        check(context.computeOnClient(client -> client.gui.screen().children().stream()
            .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.visible).allMatch(widget -> widget.getX() >= 0 && widget.getY() >= 0
                && widget.getX() + widget.getWidth() <= client.gui.screen().width
                && widget.getY() + widget.getHeight() <= client.gui.screen().height)), "Every visible manual control fits the viewport");
    }

    private static void assertArticle(final ClientGameTestContext context, final String... expected) {
        String text = context.computeOnClient(client -> article(client.gui.screen()).body().getString());
        for (String term : expected) check(text.contains(term), "Selected article contains " + term + ": " + text);
    }

    private static ManualArticleCatalog.Article article(final Object screen) {
        return ManualArticleCatalog.article((ManualProfile) field(screen, "manual"), selected(screen));
    }

    private static String selected(final Object screen) { return (String) field(screen, "selectedSection"); }

    private static Object field(final Object object, final String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe manual " + name, failure); }
    }

    static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
