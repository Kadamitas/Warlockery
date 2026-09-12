package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class AllBooksClientAcceptance implements FabricClientGameTest {
    private final List<Map<String, Object>> sections = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private Path evidence;

    @Override public void runTest(ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence"))
            .resolve("all-books").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                var requestedBooks = java.util.Arrays.stream(System.getProperty("warlockery.bookIds", "").split(","))
                    .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toSet());
                for (var book : ManualProfile.profiles().stream().filter(book -> !book.id().equals("ingredient_vbook_page"))
                    .filter(book -> requestedBooks.isEmpty() || requestedBooks.contains(book.id())).sorted(java.util.Comparator.comparingInt(
                    book -> book.id().equals("ingredient_book_circle_magic") ? 0 : 1)).toList()) {
                    world.getServer().runOnServer(server -> {
                        var player = world.getConnection().getServerPlayer();
                        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                        player.getInventory().clearContent();
                        player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(book.id()).get()));
                        player.getInventory().setSelectedSlot(0);
                        player.inventoryMenu.broadcastChanges();
                    });
                    world.getConnection().waitForClientboundPackets();
                    context.runOnClient(client -> client.player.setXRot(-80));
                    if (book.id().equals("vampirebook")) {
                        for (int unlocked = 4; unlocked < book.sections().size(); unlocked++) {
                            world.getServer().runOnServer(server -> {
                                var player = world.getConnection().getServerPlayer();
                                player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("ingredient_vbook_page").get()));
                                player.inventoryMenu.broadcastChanges();
                            });
                            world.getConnection().waitForClientboundPackets();
                            context.getInput().pressKey(GLFW.GLFW_KEY_2);
                            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                            int expectedUnlocked = unlocked + 1;
                            world.getServer().waitFor(server -> com.kadamitas.warlockery.item.ManualProgress.unlockedSectionCount(
                                book, world.getConnection().getServerPlayer().getInventory().getItem(0)) == expectedUnlocked);
                            ManualClientAcceptance.check(world.getServer().computeOnServer(server ->
                                world.getConnection().getServerPlayer().getInventory().getItem(1).isEmpty()), "Using a torn page must consume it and unlock one section");
                        }
                        context.getInput().pressKey(GLFW.GLFW_KEY_1);
                    }
                    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    boolean firstSection = true;
                    for (var section : book.sections()) {
                        var row = new LinkedHashMap<String, Object>();
                        row.put("book", book.id()); row.put("section", section); row.put("status", "RUNNING");
                        sections.add(row); write(false, "");
                        if (firstSection) {
                            String title = context.computeOnClient(client -> Component.translatable(book.translatedSectionTitleKey(section)).getString());
                            ManualClientAcceptance.search(context, title);
                            if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
                                ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
                            }
                            ManualClientAcceptance.clickButton(context, Component.translatable(book.chapterFor(section).titleKey()).getString());
                            ManualClientAcceptance.clickButton(context, title);
                            firstSection = false;
                        } else {
                            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                        }
                        context.waitFor(client -> section.equals(field(client.gui.screen(), "selectedSection")));
                        String body = context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString());
                        ManualClientAcceptance.check(!body.isBlank(), "Book article must not be empty: " + section);
                        row.put("text", body);
                        int pages = context.computeOnClient(client -> {
                            try {
                                var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                                method.setAccessible(true);
                                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                        });
                        for (int page = 0; page < pages; page++) {
                            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                            int expected = page;
                            ManualClientAcceptance.check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                                && (int) field(client.gui.screen(), "bodyPage") == expected), "Next must expose the requested page");
                            ManualClientAcceptance.check(context.computeOnClient(client -> client.gui.screen().children().stream()
                                .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(widget -> widget.visible)
                                .allMatch(widget -> widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= client.gui.screen().width
                                    && widget.getY() + widget.getHeight() <= client.gui.screen().height)), "Visible book controls must fit");
                            ManualClientAcceptance.saveScreenshot(context, evidence, book.id() + "-" + section + "-" + page, screenshots);
                        }
                        row.put("pages", pages); row.put("status", "PAGES_REACHED"); write(false, "");
                    }
                    String last = context.computeOnClient(client -> (String) field(client.gui.screen(), "selectedSection"));
                    int page = context.computeOnClient(client -> (int) field(client.gui.screen(), "bodyPage"));
                    context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    context.waitForScreen(ManualScreen.class);
                    ManualClientAcceptance.check(context.computeOnClient(client -> last.equals(field(client.gui.screen(), "selectedSection"))
                        && page == (int) field(client.gui.screen(), "bodyPage")), "Each book must reopen at its saved reading position");
                    context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                    context.waitFor(client -> client.gui.screen() == null);
                }
            }
            write(true, "");
        } catch (Throwable failure) {
            try { ManualClientAcceptance.saveScreenshot(context, evidence, "failure", screenshots); write(false, failure.toString()); }
            catch (Throwable capture) { failure.addSuppressed(capture); }
            throw new AssertionError("All-book navigation failed: " + evidence, failure);
        }
    }

    private void write(boolean completed, String failure) throws Exception {
        Files.writeString(evidence.resolve("coverage.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
            "navigation_completed", completed, "failure", failure, "sections", sections, "screenshots", screenshots,
            "scope", "Actual book item use, native chapter/search/page input and reopen checks. Captured text and images require editorial review; this is not proof that described gameplay works.")));
    }

    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
