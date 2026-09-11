package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualRequirementText;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class RitualWalkthroughAcceptance implements FabricClientGameTest {
    private static final BlockPos CENTER = new BlockPos(0, 80, 0);
    private static final BlockPos ALTAR = CENTER.offset(8, 0, 0);
    private static final String SECTION = "rite_cook_food";
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private Path evidence;
    private TestSingleplayerContext world;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/new-player-audit"))
            .resolve("ritual").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            report.put("setup", "Isolated survival world: flat stone floor, Circle Magic book, one Golden Chalk, one Ritual Chalk, "
                + "Arcane Focus, one Coal and one Raw Beef are staged. Six altar blocks and 1000 power are staged through the real altar API. "
                + "Player/camera alignment is staged at each mark so the test isolates the diagram and actual chalk interaction; walking between marks is not claimed. "
                + "No glyph blocks, dropped offerings, ritual session, recipe action, or cooked output are injected. No Ritual Knife attachment is installed.");
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                setup(context);
                final Map<String, Integer> glyphs = readGuide(context, SECTION);
                checkBookLinks(context);
                drawCircle(context, glyphs, "cook_food");
                openRitual(context);
                selectBroiling(context);
                final RitualManager.RitualOption missing = selected(context);
                check(!missing.ready(), "Coal held in inventory is not silently counted as an offering");
                check(missing.requirements().stream().anyMatch(requirement -> requirement.category().equals("ingredient") && !requirement.met()),
                    "Before dropping coal, the real ritual checklist names a missing ingredient");
                report.put("missing_checklist", checklist(context));
                readAllDetails(context, "broiling-missing");
                screenshot(context, "broiling-missing-offering");
                checks.add("Golden-center focus interaction opens the real ritual selection screen; native paging selects Broiling and exposes the missing offering.");
                checkDelayedRefresh(context);
                closeScreen(context);
                dropOfferings(context);
                openRitual(context);
                selectBroiling(context);
                final RitualManager.RitualOption ready = selected(context);
                check(ready.ready(), "The book's chalk, altar power and dropped coal make Broiling ready: " + checklist(context));
                report.put("ready_checklist", checklist(context));
                screenshot(context, "broiling-ready-no-ritual-knife");
                ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.begin").getString());
                context.waitTicks(3);
                check(serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)),
                    "Native Begin Rite click starts a real server-authoritative casting session");
                final Object castingScreen = checkCastingUi(context, "broiling");
                screenshot(context, "broiling-casting");
                context.waitTicks(ready.castingTime() + 12);
                check(!serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)),
                    "Ritual casting reaches completion");
                checkAutomaticCompletion(context, castingScreen, "broiling");
                check(serverValue(player -> dropped(player, Items.COOKED_BEEF).size()) == 1,
                    "Real ritual action converts the natively dropped raw beef into cooked beef");
                check(serverValue(player -> dropped(player, Items.BEEF).isEmpty() && dropped(player, Items.COAL).isEmpty()),
                    "Raw food is converted and the coal offering is consumed");
                closeScreen(context);
                screenshot(context, "broiling-cooked-beef-on-ground");
                final Vec3 cookedPosition = serverValue(player -> dropped(player, Items.COOKED_BEEF).getFirst().position());
                look(context, cookedPosition);
                context.getInput().holdKey(GLFW.GLFW_KEY_W);
                try {
                    context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                        .anyMatch(stack -> stack.is(Items.COOKED_BEEF)), 100);
                } finally {
                    context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                }
                checks.add("Native Begin Rite starts the real cast, consumes one dropped Coal, converts dropped Raw Beef, and normal forward movement collects the Cooked Beef.");
                screenshot(context, "broiling-output-collected");
                extraRite(context, "charge_attuned_stone");
                extraRite(context, "summon_familiar");
                report.put("passed", true);
                report.put("runtime_output", "One Cooked Beef collected; no active casting session remains; coal and raw beef drops are gone.");
                } catch (Throwable failure) {
                    report.put("passed", false);
                    report.put("failure", failure.toString());
                    try {
                        screenshot(context, "failure-in-world");
                        writeReport();
                    } catch (Throwable capture) { failure.addSuppressed(capture); }
                    throw failure;
                }
            }
            writeReport();
            System.out.println("WARLOCKERY_RITUAL_WALKTHROUGH_PASS " + evidence);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            try { screenshot(context, "failure"); writeReport(); }
            catch (Throwable capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Ritual walkthrough evidence: " + evidence, failure);
        }
    }

    private void setup(final ClientGameTestContext context) {
        server(player -> {
            player.level().getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(20)).forEach(ItemEntity::discard);
            for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                for (int y = 80; y <= 84; y++) player.level().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_book_circle_magic").get()));
            player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("chalkheart").get()));
            player.getInventory().setItem(2, new ItemStack(ModItems.ALL.get("chalkritual").get()));
            player.getInventory().setItem(3, new ItemStack(ModItems.ALL.get("arcane_focus").get()));
            player.getInventory().setItem(4, new ItemStack(Items.COAL));
            player.getInventory().setItem(5, new ItemStack(Items.BEEF));
            player.getInventory().setSelectedSlot(0);
            player.teleportTo(0.5, 80, -2.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.waitTicks(41);
        server(player -> {
            final AltarBlockEntity altar = (AltarBlockEntity) player.level().getBlockEntity(ALTAR);
            check(altar.isMultiblockValid(), "Fixture altar forms a valid six-block structure");
            check(altar.receivePower(1000) > 0, "Fixture charges an actual altar reserve");
            check(!altar.hasRangeFocus(), "No optional Ritual Knife range attachment is installed");
        });
    }

    private Map<String, Integer> readGuide(final ClientGameTestContext context, final String section) throws Exception {
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        final ManualProfile book = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final String title = Component.translatable(book.translatedSectionTitleKey(section)).getString();
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        ManualClientAcceptance.clickButton(context, Component.translatable(book.chapterFor(section).titleKey()).getString());
        ManualClientAcceptance.clickButton(context, title);
        context.waitFor(client -> field(client.gui.screen(), "selectedSection").equals(section));
        final var article = context.computeOnClient(client -> ManualArticleCatalog.article(book, section));
        report.put("book_text_" + section, context.computeOnClient(client -> article.body().getString()));
        report.put("book_section_" + section, section);
        report.put("displayed_chalk_counts_" + section, article.glyphs());
        check(!article.glyphs().isEmpty(), "The selected ritual's real manual article supplies its chalk diagram");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe book page count", failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(section)),
                "Native Next remains within the " + section + " article");
            screenshot(context, section + "-book-page-" + (page + 1));
        }
        report.put("book_pages_read_" + section, pages);
        checks.add("Actual Circle Magic item use and native book search/buttons open " + section + "; every article page is advanced and captured.");
        closeScreen(context);
        return article.glyphs();
    }

    private void drawCircle(final ClientGameTestContext context, final Map<String, Integer> glyphs, final String id) throws Exception {
        context.getInput().pressKey(GLFW.GLFW_KEY_2);
        drawMark(context, CENTER, "circle");
        final List<List<Integer>> positions = new ArrayList<>();
        for (var glyph : glyphs.entrySet()) {
            check(glyph.getKey().equals("circleglyphritual") || glyph.getKey().equals("circleglyph_veil"),
                "Representative rite uses Ritual or Veil Chalk");
            context.getInput().pressKey(glyph.getKey().equals("circleglyph_veil") ? GLFW.GLFW_KEY_7 : GLFW.GLFW_KEY_3);
            final var size = ChalkCircleLayout.Size.forMarkCount(glyph.getValue());
            for (BlockPos offset : size.offsets()) {
                final BlockPos target = CENTER.offset(offset);
                drawMark(context, target, glyph.getKey());
                positions.add(List.of(offset.getX(), offset.getZ()));
            }
        }
        check(serverValue(player -> glyphs.entrySet().stream().allMatch(glyph ->
            ChalkCircleLayout.present(player.level(), CENTER,
                new ChalkCircleLayout.Ring(glyph.getKey(), ChalkCircleLayout.Size.forMarkCount(glyph.getValue()))) == glyph.getValue())),
            "Every actual chalk placement matches the diagram's canonical block offsets");
        check(serverValue(player -> player.getInventory().getItem(1).getDamageValue()) == 1,
            "Golden Chalk loses one use for the actual center placement");
        check(serverValue(player -> player.getInventory().getItem(2).getDamageValue()) == glyphs.getOrDefault("circleglyphritual", 0),
            "Ritual Chalk loses one use per actual mark, proving placements used the chalk item");
        if (glyphs.containsKey("circleglyph_veil")) {
            check(serverValue(player -> player.getInventory().getItem(6).getDamageValue()) == glyphs.get("circleglyph_veil"),
                "Veil Chalk loses one use per actual second-ring mark");
        }
        report.put("native_chalk_offsets_" + id, positions);
        position(context, CENTER.getX() + 0.5, CENTER.getY(), CENTER.getZ() - 2.5);
        look(context, new Vec3(0.5, 80.01, 0.5));
        screenshot(context, id + "-actual-chalk-ring");
        checks.add("Golden center and every displayed Ritual Chalk grid mark are drawn with native right-clicks; exact server positions and durability are verified.");
    }

    private void drawMark(final ClientGameTestContext context, final BlockPos target, final String glyph) {
        position(context, target.getX() + 0.5, target.getY(), target.getZ() - 2.0);
        look(context, new Vec3(target.getX() + 0.5, target.getY() - 0.01, target.getZ() + 0.5));
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target.below())),
            "Native chalk ray targets the supporting floor at " + target);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.level.getBlockState(target).is(ModBlocks.ALL.get(glyph).get()));
    }

    private void dropOfferings(final ClientGameTestContext context) {
        position(context, 0.5, 80, -2.5);
        look(context, new Vec3(0.5, 80.1, 0.5));
        context.getInput().pressKey(GLFW.GLFW_KEY_5);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.getInput().pressKey(GLFW.GLFW_KEY_6);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.waitTicks(3);
        check(serverValue(player -> dropped(player, Items.COAL).size()) == 1, "Native drop key creates the coal offering");
        check(serverValue(player -> dropped(player, Items.BEEF).size()) == 1, "Native drop key puts raw food in the ritual area");
        position(context, 3.0, 80, 0.5);
        checks.add("Native Q drops exactly one Coal offering and one Raw Beef target; player position is staged away to prevent accidental pickup during selection.");
    }

    private void openRitual(final ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_4);
        look(context, new Vec3(0.5, 80.01, 0.5));
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(CENTER)),
            "Arcane Focus ray targets the Golden Chalk heart");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(RitualSelectionScreen.class);
    }

    private void selectBroiling(final ClientGameTestContext context) {
        selectRite(context, "cook_food");
    }

    private void selectRite(final ClientGameTestContext context, final String id) {
        final String label = Component.translatable("ritual.warlockery." + id + ".title").getString();
        for (int page = 0; page < 100; page++) {
            final double[] button = context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.visible && candidate.active && candidate.getMessage().getString().endsWith(label))
                .map(candidate -> new double[] {candidate.getX() + candidate.getWidth() / 2.0,
                    candidate.getY() + candidate.getHeight() / 2.0}).findFirst().orElse(null));
            if (button != null) {
                ManualClientAcceptance.click(context, button[0], button[1]);
                check(selected(context).id().equals("warlockery:" + id), "Native ritual button selects " + id);
                return;
            }
            ManualClientAcceptance.clickButton(context, "›");
        }
        throw new AssertionError(id + " is not reachable through native ritual pagination");
    }

    private void extraRite(final ClientGameTestContext context, final String id) throws Exception {
        setup(context);
        final var definition = RitualManager.INSTANCE.byId(net.minecraft.resources.Identifier.fromNamespaceAndPath("warlockery", id))
            .orElseThrow().definition();
        server(player -> {
            player.level().setBlockAndUpdate(ALTAR.north(), ModBlocks.ALL.get("demonheart").get().defaultBlockState());
            player.level().setBlockAndUpdate(ALTAR.north().east(), ModBlocks.ALL.get("demonheart").get().defaultBlockState());
            if (definition.nightOnly()) {
                final var server = player.level().getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
            }
            player.getInventory().setItem(6, new ItemStack(ModItems.ALL.get("chalk_veil").get()));
            for (int index = 0; index < definition.requirements().ingredients().size(); index++) {
                final var input = definition.requirements().ingredients().get(index);
                final var ingredient = com.kadamitas.warlockery.util.ItemIngredient.parse(input.ingredient()).orElseThrow();
                final var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(candidate -> ingredient.matches(new ItemStack(candidate))).findFirst().orElseThrow();
                player.getInventory().setItem(4 + index, new ItemStack(item, input.count()));
            }
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(41);
        server(player -> {
            final var altar = (AltarBlockEntity) player.level().getBlockEntity(ALTAR);
            altar.receivePower(definition.power());
            check(altar.availablePower() >= definition.power(), "Natural fixture altar capacity supports " + id);
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        final Map<String, Integer> glyphs = readGuide(context, "rite_" + id);
        drawCircle(context, glyphs, id);
        if (id.equals("summon_familiar")) check(glyphs.size() == 2, "Summoning exercises a real two-ring diagram");
        position(context, 0.5, 80, -2.5);
        look(context, new Vec3(0.5, 80.1, 0.5));
        for (int index = 0; index < definition.requirements().ingredients().size(); index++) {
            context.getInput().pressKey(GLFW.GLFW_KEY_5 + index);
            for (int item = 0; item < definition.requirements().ingredients().get(index).count(); item++) {
                context.getInput().pressKey(GLFW.GLFW_KEY_Q);
            }
        }
        position(context, 3.0, 80, 0.5);
        openRitual(context);
        selectRite(context, id);
        final RitualManager.RitualOption option = selected(context);
        check(option.ready(), "Native checklist is ready for " + id + ": " + checklist(context));
        report.put(id + "_checklist", checklist(context));
        readAllDetails(context, id);
        if (id.equals("summon_familiar")) {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
            context.waitTicks(3);
            check(context.computeOnClient(client -> client.gui.screen().width < 640 && client.gui.screen().height < 400),
                "GUI3 produces an actually smaller logical ritual screen for pagination coverage");
            readAllDetails(context, id + "-compact");
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            context.waitTicks(3);
        }
        screenshot(context, id + "-ready-checklist");
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.begin").getString());
        context.waitTicks(3);
        check(serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)), "Native Begin starts " + id);
        final Object castingScreen = checkCastingUi(context, id);
        context.waitTicks(option.castingTime() + 12);
        check(!serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)), "Real cast completes for " + id);
        checkAutomaticCompletion(context, castingScreen, id);
        if (definition.action().equals("summon_item")) {
            final var target = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(definition.target()));
            check(serverValue(player -> !dropped(player, target).isEmpty()), "Actual charged-item output appears for " + id);
        } else {
            check(serverValue(player -> !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTER).inflate(12), mob -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(mob.getType()).toString().equals(definition.target())).isEmpty()),
                "Actual summoned creature appears for " + id);
        }
        closeScreen(context);
        final Vec3 resultPosition = serverValue(player -> {
            if (definition.action().equals("summon_item")) {
                final var target = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                    net.minecraft.resources.Identifier.parse(definition.target()));
                return dropped(player, target).getFirst().position().add(0, 0.15, 0);
            }
            return player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTER).inflate(12), mob -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(mob.getType()).toString().equals(definition.target())).getFirst().getEyePosition();
        });
        look(context, resultPosition);
        screenshot(context, id + "-actual-result");
        checks.add("Actual " + id + " cast: book pages, every chalk mark, dropped offerings, native focus/selection/Begin, live cast, verified "
            + definition.target() + ". Fixture adds two Demon Hearts for altar capacity and sets night only when required.");
    }

    private void checkBookLinks(final ClientGameTestContext context) throws Exception {
        final ManualProfile circle = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        final ManualProfile distilling = ManualProfile.find("ingredient_book_distilling").orElseThrow();
        final String label = Component.translatable("screen.warlockery.manual.book_link",
            Component.translatable(distilling.translatedTitleKey()),
            Component.translatable(distilling.translatedSectionTitleKey("machine_recipe_distill_vitriol"))).getString();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        final String title = Component.translatable(circle.translatedSectionTitleKey("golden_chalk")).getString();
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        ManualClientAcceptance.clickButton(context, Component.translatable(circle.chapterFor("golden_chalk").titleKey()).getString());
        ManualClientAcceptance.clickButton(context, title);
        for (int page = 0; page < 60 && !referenceVisible(context, label); page++) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals("golden_chalk")),
                "Golden Chalk reference must appear within its own article");
        }
        check(referenceVisible(context, label), "Golden Chalk exposes a real clickable Distilling reference");
        for (String placement : List.of("main", "offhand", "missing")) {
            server(player -> {
                player.getInventory().setItem(9, placement.equals("main")
                    ? new ItemStack(ModItems.ALL.get(distilling.id()).get()) : ItemStack.EMPTY);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, placement.equals("offhand")
                    ? new ItemStack(ModItems.ALL.get(distilling.id()).get()) : ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(2);
            final Object source = context.computeOnClient(client -> client.gui.screen());
            final int page = context.computeOnClient(client -> (int) field(source, "bodyPage"));
            final String query = context.computeOnClient(client -> (String) field(source, "query"));
            final Map<String, Integer> inventory = inventorySnapshot();
            clickReference(context, label);
            context.waitFor(client -> client.gui.screen() instanceof ManualScreen && client.gui.screen() != source);
            final boolean missing = placement.equals("missing");
            check(context.computeOnClient(client -> ((ManualProfile) field(client.gui.screen(), "manual")).id()
                .equals(missing ? "recipe_" + distilling.id() : distilling.id())), "Book reference resolves " + placement + " ownership");
            check(context.computeOnClient(client -> (boolean) field(client.gui.screen(), "recipePreview")) == missing,
                "Only an unowned book opens a recipe-only preview");
            check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals(missing
                ? "crafting_" + distilling.id() : "machine_recipe_distill_vitriol")), "Reference opens the exact chapter or book recipe");
            check(context.computeOnClient(client -> (int) field(client.gui.screen(), "bodyPage")) == 0,
                "A referenced chapter opens at its first page");
            check(inventorySnapshot().equals(inventory), "Cross-book navigation grants or consumes no inventory items");
            screenshot(context, "book-reference-" + placement);
            if (placement.equals("offhand")) context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            else ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back").getString());
            context.waitFor(client -> client.gui.screen() == source);
            check(context.computeOnClient(client -> (int) field(source, "bodyPage") == page && field(source, "query").equals(query)),
                "Back/Escape restores the exact source page and search query");
        }
        closeScreen(context);
        checks.add("Actual Golden Chalk link handles owned main-inventory book, owned offhand book and missing-book recipe preview; no item grants; Back/Escape restores source page and query.");
    }

    private Map<String, Integer> inventorySnapshot() {
        return serverValue(player -> {
            final Map<String, Integer> items = new java.util.TreeMap<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                final ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) items.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
            return items;
        });
    }

    private static boolean referenceVisible(final ClientGameTestContext context, final String label) {
        return context.computeOnClient(client -> client.gui.screen().children().stream().filter(Button.class::isInstance)
            .map(Button.class::cast).anyMatch(button -> button.visible && button.active
                && button.getClass().getSimpleName().equals("ManualReferenceButton") && button.getMessage().getString().equals(label)));
    }

    private static void clickReference(final ClientGameTestContext context, final String label) {
        final double[] point = context.computeOnClient(client -> client.gui.screen().children().stream().filter(Button.class::isInstance)
            .map(Button.class::cast).filter(button -> button.visible && button.active
                && button.getClass().getSimpleName().equals("ManualReferenceButton") && button.getMessage().getString().equals(label))
            .map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0}).findFirst().orElseThrow());
        ManualClientAcceptance.click(context, point[0], point[1]);
    }

    private static RitualManager.RitualOption selected(final ClientGameTestContext context) {
        return context.computeOnClient(client -> selectedOnScreen(client.gui.screen()));
    }

    private static RitualManager.RitualOption selectedOnScreen(final Object screen) {
            @SuppressWarnings("unchecked") final List<RitualManager.RitualOption> options =
                (List<RitualManager.RitualOption>) field(screen, "options");
            final String selected = (String) field(screen, "selectedId");
            return options.stream().filter(option -> option.id().equals(selected)).findFirst().orElseThrow();
    }

    private Object checkCastingUi(final ClientGameTestContext context, final String id) throws Exception {
        context.waitFor(client -> client.gui.screen() instanceof RitualSelectionScreen
            && com.kadamitas.warlockery.ritual.RitualUiState.castInProgress(selectedOnScreen(client.gui.screen())), 40);
        final Object screen = context.computeOnClient(client -> client.gui.screen());
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Ritual screen lets the real server cast continue");
        context.runOnClient(client -> {
            final var option = selectedOnScreen(screen);
            final List<String> body = RitualSelectionScreen.detailContents(option).stream().map(Component::getString).toList();
            final String casting = Component.translatable("screen.warlockery.ritual.casting").getString();
            check(body.contains(casting), "Active cast body says Rite in Progress");
            check(!body.contains(Component.translatable("screen.warlockery.ritual.power", option.altarPower(), option.power()).getString()),
                "Escrowed power is not presented as a new missing-resource blocker during casting");
            option.requirements().stream().filter(requirement -> requirement.category().equals("ingredient"))
                .map(RitualRequirementText::line).map(Component::getString).forEach(line ->
                    check(!body.contains(line), "Consumed ingredient is not presented as a casting blocker"));
            check(client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.visible && !button.active && button.getMessage().getString().equals(casting)),
                "Actual Begin button reports the active cast and cannot start it twice");
        });
        screenshot(context, id + "-active-cast-status");
        return screen;
    }

    private void checkDelayedRefresh(final ClientGameTestContext context) throws Exception {
        final RitualManager.RitualOption option = selected(context);
        final Object original = context.computeOnClient(client -> client.gui.screen());
        @SuppressWarnings("unchecked") final List<RitualManager.RitualOption> originalOptions =
            context.computeOnClient(client -> List.copyOf((List<RitualManager.RitualOption>) field(original, "options")));
        final List<RitualManager.RitualOption> updated = List.of(option);
        context.runOnClient(client -> {
            RitualSelectionScreen.openOrUpdate(CENTER, updated, false);
            check(client.gui.screen() == original, "Matching refresh preserves the exact open ritual screen");
            check(field(original, "options").equals(updated), "Matching refresh applies its new option snapshot");
            RitualSelectionScreen.openOrUpdate(CENTER.offset(1, 0, 0), List.of(), false);
            check(client.gui.screen() == original && field(original, "options").equals(updated),
                "Refresh for a different ritual center cannot replace or change the current screen");
        });
        screenshot(context, "delayed-refresh-matching-screen");
        closeScreen(context);
        context.waitTicks(3);
        context.runOnClient(client -> RitualSelectionScreen.openOrUpdate(CENTER, originalOptions, false));
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.gui.screen() == null),
            "A delayed update after native Escape cannot reopen the ritual screen");
        screenshot(context, "delayed-refresh-after-escape");

        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        final Object book = context.computeOnClient(client -> client.gui.screen());
        final Object section = context.computeOnClient(client -> field(book, "selectedSection"));
        final Object page = context.computeOnClient(client -> field(book, "bodyPage"));
        context.runOnClient(client -> RitualSelectionScreen.openOrUpdate(CENTER, originalOptions, false));
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.gui.screen() == book
            && field(book, "selectedSection").equals(section) && field(book, "bodyPage").equals(page)),
            "A delayed ritual update preserves the natively opened manual and its reading position");
        screenshot(context, "delayed-refresh-preserves-manual");
        closeScreen(context);
        context.runOnClient(client -> RitualSelectionScreen.openOrUpdate(CENTER, originalOptions, true));
        context.waitForScreen(RitualSelectionScreen.class);
        check(context.computeOnClient(client -> ((RitualSelectionScreen) client.gui.screen()).center().equals(CENTER)
            && field(client.gui.screen(), "options").equals(originalOptions)),
            "An explicitly authorized initial response can still open the ritual screen");
        screenshot(context, "delayed-refresh-explicit-initial-open");
        report.put("delayed_refresh_fixture", "Recorded real server option snapshots are delivered through the production client response API with mayOpen=false/true. Escape, book use, and screen closing are native input; response timing is a controlled client fixture, not a claimed network roundtrip.");
        checks.add("Matching refresh applies in place; a different center is ignored; delayed refresh cannot reopen after Escape or replace a manual; an explicit initial response still opens.");
    }

    private void checkAutomaticCompletion(final ClientGameTestContext context, final Object screen, final String id) throws Exception {
        context.waitFor(client -> client.gui.screen() == screen
            && !com.kadamitas.warlockery.ritual.RitualUiState.castInProgress(selectedOnScreen(screen)), 40);
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Screen remains non-pausing after completion");
        screenshot(context, id + "-automatic-completed-status");
        report.put(id + "_live_screen", "Same non-pausing ritual screen stays open through the real cast; in-progress text replaces consumed-resource blockers; completion refreshes automatically within40clientticks of observed server completion.");
    }

    private void readAllDetails(final ClientGameTestContext context, final String id) throws Exception {
        while (context.computeOnClient(client -> (int) field(client.gui.screen(), "detailPage")) > 0) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.previous_details").getString());
        }
        final var option = selected(context);
        final List<String> required = context.computeOnClient(client -> com.kadamitas.warlockery.ritual.RitualUiState
            .checklistRows(option).stream().map(RitualRequirementText::line).map(Component::getString).toList());
        final StringBuilder seen = new StringBuilder();
        int count = 0;
        while (true) {
            final int page = count++;
            final boolean more = context.computeOnClient(client -> {
                final var screen = client.gui.screen();
                check((int) field(screen, "detailPage") == page, "Native detail button advances exactly one page");
                final var layout = RitualSelectionLayout.calculate(screen.width, screen.height);
                try {
                    final var method = RitualSelectionScreen.class.getDeclaredMethod("detailPages", RitualSelectionLayout.class);
                    method.setAccessible(true);
                    @SuppressWarnings("unchecked") final List<List<net.minecraft.util.FormattedCharSequence>> pages =
                        (List<List<net.minecraft.util.FormattedCharSequence>>) method.invoke(screen, layout);
                    final var lines = pages.get(page);
                    check(lines.size() <= layout.detailCapacity(), "Every displayed requirement stays above the detail controls");
                    lines.forEach(line -> line.accept((index, style, codePoint) -> { seen.appendCodePoint(codePoint); return true; }));
                    return page + 1 < pages.size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe rendered detail page", failure); }
            });
            screenshot(context, id + "-details-" + count);
            if (!more) break;
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.next_details").getString());
        }
        final String visible = seen.toString().replaceAll("\\s+", "");
        if (id.endsWith("-compact")) check(count > 1, "Compact Familiar actually exercises the native Next Details button");
        required.forEach(requirement -> check(visible.contains(requirement.replaceAll("\\s+", "")),
            "Actual native detail pages expose complete requirement: " + requirement));
        report.put(id + "_detail_pages_read", count);
        report.put(id + "_observed_display", context.computeOnClient(client -> Map.of(
            "gui_width", client.gui.screen().width, "gui_height", client.gui.screen().height,
            "requested_gui_scale", client.options.guiScale().get(),
            "actual_gui_scale", client.getWindow().getGuiScale())));
        checks.add(id + ": every runtime checklist row reached through native detail paging and captured, including wrapped text.");
    }

    private static List<String> checklist(final ClientGameTestContext context) {
        final var option = selected(context);
        return context.computeOnClient(client -> option.requirements().stream()
            .map(RitualRequirementText::line).map(Component::getString).toList());
    }

    private static List<ItemEntity> dropped(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        return player.level().getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(6),
            entity -> entity.isAlive() && entity.getItem().is(item));
    }

    private void position(final ClientGameTestContext context, final double x, final double y, final double z) {
        server(player -> player.teleportTo(x, y, z));
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null);
        }
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport() throws Exception {
        report.put("checks", checks);
        report.put("screenshots", screenshots);
        Files.writeString(evidence.resolve("ritual-walkthrough.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }

    private static Object field(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe " + name, failure); }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
