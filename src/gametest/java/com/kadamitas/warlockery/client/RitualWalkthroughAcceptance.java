package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualRequirementText;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.ritual.RitualWardData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final BlockPos CENTER = new BlockPos(0, 50, 0);
    private static final BlockPos ALTAR = CENTER.offset(8, 0, 0);
    private static final BlockPos ACTION_BLOCK = CENTER.offset(1, 0, 1);
    private static final BlockPos ACTION_BLOCK_TWO = CENTER.offset(-1, 0, 1);
    private static final String SECTION = "rite_cook_food";
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> ritualResults = new LinkedHashMap<>();
    private final Map<String, List<EntityEvidence>> entityOfferings = new LinkedHashMap<>();
    private final Map<String, UUID> actionTargets = new LinkedHashMap<>();
    private final Map<String, Double> actionMeasurements = new LinkedHashMap<>();
    private final List<ServerPlayer> syntheticPlayers = new ArrayList<>();
    private final List<Map<String, Object>> chalkRayTrace = new ArrayList<>();
    private final Map<String, Map<String, Object>> fixtureIsolation = new LinkedHashMap<>();
    private String activeRitual = "cook_food";
    private Set<String> selectedRituals = Set.of();
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
                + "Between rites, completed outcomes are recorded before previous wards, player effects and motion are cleared; unfinished casts fail isolation. "
                + "No glyph blocks, dropped offerings, ritual session, recipe action, or cooked output are injected. No Ritual Knife attachment is installed.");
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                setup(context);
                initializeRitualResults();
                selectedRituals = selectedRitualIds();
                report.put("selected_ritual_ids", List.copyOf(selectedRituals));
                if (selectedRituals.contains("cook_food")) {
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
                passRitual("cook_food", "Native activation converted dropped raw beef and consumed coal.");
                }
                walkImplementedActionFamilies(context);
                report.put("passed", true);
                report.put("runtime_output", ritualResults.entrySet().stream()
                    .filter(entry -> entry.getValue().get("status").equals("PASSED"))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get("evidence"))));
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
            final boolean previousCastActive = RitualSessionData.get(player.level()).isActive(CENTER);
            final Map<String, Object> isolation = new LinkedHashMap<>();
            isolation.put("before", playerTrace(player));
            isolation.put("previous_cast_active", previousCastActive);
            isolation.put("previous_wards", wardTrace(player));
            fixtureIsolation.put(activeRitual, isolation);
            check(!previousCastActive, "Previous ritual cast completes before isolating " + activeRitual);
            // This world is reused only to avoid client startup per rite. Outcomes have already been
            // asserted; a prior portable ward must not push the next rite's staged chalk camera.
            player.level().getDataStorage().set(RitualWardData.TYPE, new RitualWardData());
            player.removeAllEffects();
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            isolation.put("after", playerTrace(player));
            isolation.put("remaining_wards", wardTrace(player));
            syntheticPlayers.forEach(player.level().getServer().getPlayerList()::remove);
            syntheticPlayers.clear();
            player.level().getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(20)).forEach(ItemEntity::discard);
            player.level().getEntities((net.minecraft.world.entity.Entity) null, new AABB(CENTER).inflate(20),
                entity -> entity != player).forEach(net.minecraft.world.entity.Entity::discard);
            for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, CENTER.getY() - 1, z), Blocks.STONE.defaultBlockState());
                for (int y = CENTER.getY(); y <= CENTER.getY() + 4; y++)
                    player.level().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
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
            player.teleportTo(0.5, CENTER.getY(), -2.5);
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
            context.getInput().pressKey(chalkKey(glyph.getKey()));
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
        check(serverValue(player -> player.getInventory().getItem(1).getDamageValue())
                == 1 + glyphs.getOrDefault("circleglyphgolden", 0),
            "Golden Chalk loses one use for the center and every declared golden-ring mark");
        check(serverValue(player -> player.getInventory().getItem(2).getDamageValue()) == glyphs.getOrDefault("circleglyphritual", 0),
            "Ritual Chalk loses one use per actual mark, proving placements used the chalk item");
        if (glyphs.containsKey("circleglyphinfernal")) {
            check(serverValue(player -> player.getInventory().getItem(4).getDamageValue()) == glyphs.get("circleglyphinfernal"),
                "Infernal Chalk loses one use per actual infernal mark");
        }
        if (glyphs.containsKey("circleglyph_veil")) {
            check(serverValue(player -> player.getInventory().getItem(5).getDamageValue()) == glyphs.get("circleglyph_veil"),
                "Veil Chalk loses one use per actual second-ring mark");
        }
        report.put("native_chalk_offsets_" + id, positions);
        position(context, CENTER.getX() + 0.5, CENTER.getY(), CENTER.getZ() - 2.5);
        look(context, new Vec3(0.5, CENTER.getY() + 0.01, 0.5));
        screenshot(context, id + "-actual-chalk-ring");
        checks.add("Golden center and every displayed Ritual Chalk grid mark are drawn with native right-clicks; exact server positions and durability are verified.");
    }

    private static int chalkKey(final String glyph) {
        return switch (glyph) {
            case "circleglyphritual" -> GLFW.GLFW_KEY_3;
            case "circleglyphinfernal" -> GLFW.GLFW_KEY_5;
            case "circleglyph_veil" -> GLFW.GLFW_KEY_6;
            case "circleglyphgolden" -> GLFW.GLFW_KEY_2;
            default -> throw new AssertionError("No native chalk item is staged for " + glyph);
        };
    }

    private void drawMark(final ClientGameTestContext context, final BlockPos target, final String glyph) {
        final Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("ritual", activeRitual);
        trace.put("glyph", glyph);
        trace.put("target", blockCoordinates(target));
        trace.put("support", blockCoordinates(target.below()));
        trace.put("staged_position", List.of(target.getX() + 0.5, (double) target.getY(), target.getZ() - 2.0));
        if (chalkRayTrace.size() == 12) chalkRayTrace.removeFirst();
        chalkRayTrace.add(trace);
        trace.put("before_position", chalkTrace(context, target));
        position(context, target.getX() + 0.5, target.getY(), target.getZ() - 2.0);
        trace.put("after_position", chalkTrace(context, target));
        look(context, new Vec3(target.getX() + 0.5, target.getY() - 0.01, target.getZ() + 0.5), trace);
        trace.put("after_look", chalkTrace(context, target));
        final boolean supportingFloor = context.computeOnClient(client -> {
            trace.put("checked_client", clientChalkTrace(client.player, client.hitResult, target));
            return client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target.below());
        });
        trace.put("supporting_floor_matched", supportingFloor);
        if (!supportingFloor) System.out.println("WARLOCKERY_CHALK_RAY_FAILURE " + new GsonBuilder().create().toJson(trace));
        check(supportingFloor, "Native chalk ray targets the supporting floor at " + target);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.level.getBlockState(target).is(ModBlocks.ALL.get(glyph).get()));
        trace.put("actual_chalk_placed", true);
    }

    private Map<String, Object> chalkTrace(final ClientGameTestContext context, final BlockPos target) {
        return Map.of(
            "client", context.computeOnClient(client -> clientChalkTrace(client.player, client.hitResult, target)),
            "server", serverValue(player -> Map.of("player", playerTrace(player), "wards", wardTrace(player)))
        );
    }

    private static Map<String, Object> clientChalkTrace(
        final net.minecraft.world.entity.player.Player player,
        final net.minecraft.world.phys.HitResult hit,
        final BlockPos target
    ) {
        final Map<String, Object> trace = new LinkedHashMap<>(playerTrace(player));
        trace.put("support_state", player.level().getBlockState(target.below()).toString());
        trace.put("target_state", player.level().getBlockState(target).toString());
        trace.put("hit_type", hit == null ? "NONE" : hit.getType().name());
        if (hit != null) trace.put("hit_location", coordinates(hit.getLocation()));
        if (hit instanceof BlockHitResult blockHit) {
            trace.put("hit_block", blockCoordinates(blockHit.getBlockPos()));
            trace.put("hit_face", blockHit.getDirection().name());
        }
        return trace;
    }

    private static Map<String, Object> playerTrace(final net.minecraft.world.entity.player.Player player) {
        final Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("game_time", player.level().getGameTime());
        trace.put("player_tick", player.tickCount);
        trace.put("position", coordinates(player.position()));
        trace.put("eye", coordinates(player.getEyePosition()));
        trace.put("velocity", coordinates(player.getDeltaMovement()));
        trace.put("yaw", player.getYRot());
        trace.put("pitch", player.getXRot());
        trace.put("on_ground", player.onGround());
        trace.put("effects", player.getActiveEffects().stream().map(Object::toString).toList());
        return trace;
    }

    private static List<Map<String, Object>> wardTrace(final ServerPlayer player) {
        return ((List<?>) field(RitualWardData.get(player.level()), "wards")).stream()
            .map(RitualWardData.Ward.class::cast).map(ward -> {
                final Map<String, Object> trace = new LinkedHashMap<>();
                final Vec3 center = Vec3.atCenterOf(BlockPos.of(ward.center()));
                trace.put("type", ward.type().name());
                trace.put("center", coordinates(center));
                trace.put("radius", ward.radius());
                trace.put("expires_at", ward.expiration());
                trace.put("remaining_ticks", ward.expiration() - player.level().getGameTime());
                trace.put("powered", ward.powered());
                trace.put("player_distance", player.position().distanceTo(center));
                return trace;
            }).toList();
    }

    private static List<Double> coordinates(final Vec3 position) {
        return List.of(position.x, position.y, position.z);
    }

    private static List<Integer> blockCoordinates(final BlockPos position) {
        return List.of(position.getX(), position.getY(), position.getZ());
    }

    private void dropOfferings(final ClientGameTestContext context) {
        position(context, 0.5, CENTER.getY(), -2.5);
        look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
        context.getInput().pressKey(GLFW.GLFW_KEY_5);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.getInput().pressKey(GLFW.GLFW_KEY_6);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.waitTicks(3);
        check(serverValue(player -> dropped(player, Items.COAL).size()) == 1, "Native drop key creates the coal offering");
        check(serverValue(player -> dropped(player, Items.BEEF).size()) == 1, "Native drop key puts raw food in the ritual area");
        position(context, 3.0, CENTER.getY(), 0.5);
        checks.add("Native Q drops exactly one Coal offering and one Raw Beef target; player position is staged away to prevent accidental pickup during selection.");
    }

    private void openRitual(final ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_4);
        look(context, new Vec3(0.5, CENTER.getY() + 0.01, 0.5));
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
        activeRitual = id;
        if (id.equals("blood_audience")) report.put(id + "_fixture_scope",
            "The test places an actual Ocean Monument structure and stages its cleared state; it does not certify survival discovery or guardian combat.");
        if (id.equals("hex_wolf")) report.put(id + "_fixture_scope",
            "The participant count is supplied by six live owned Circle Mage entities plus the rendered caster; this is not multiplayer networking coverage.");
        if (id.equals("manifestation")) report.put(id + "_fixture_scope",
            "A connected synthetic ServerPlayer sleeps through normal bed mechanics as the bound target; the rendered player performs every ritual UI action.");
        setup(context);
        final var definition = RitualManager.INSTANCE.byId(net.minecraft.resources.Identifier.fromNamespaceAndPath("warlockery", id))
            .orElseThrow().definition();
        server(player -> {
            prepareDeclaredConditions(player, id, definition);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
                player.level().setBlockAndUpdate(ALTAR.offset(x, -1, z), ModBlocks.ALL.get("demonheart").get().defaultBlockState());
            }
            player.getInventory().setItem(4, new ItemStack(ModItems.ALL.get("chalkinfernal").get()));
            player.getInventory().setItem(5, new ItemStack(ModItems.ALL.get("chalk_veil").get()));
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
        final Map<String, Integer> guideGlyphs = readGuide(context, "rite_" + id);
        final Map<String, Integer> glyphs = castingGlyphs(definition, guideGlyphs);
        drawCircle(context, glyphs, id);
        if (id.equals("summon_familiar")) check(glyphs.size() == 2, "Summoning exercises a real two-ring diagram");
        server(player -> stageEntityRequirements(player, id, definition));
        server(player -> stageActionPrerequisites(player, id, definition));
        context.waitTicks(2);
        final List<OfferingEvidence> offerings = dropDeclaredOfferings(context, definition);
        dropSpecialPrerequisite(context, id);
        configureBoundOfferings(id, definition);
        final Set<UUID> existingTargets = serverValue(player -> targetEntities(player, definition.target()).stream()
            .map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet()));
        final int existingItems = serverValue(player -> definition.action().equals("summon_item")
            ? dropped(player, targetItem(definition.target())).stream().mapToInt(entity -> entity.getItem().getCount()).sum() : 0);
        position(context, 0.5, CENTER.getY(), -2.5);
        look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
        position(context, 3.0, CENTER.getY(), 0.5);
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
            final var target = targetItem(definition.target());
            check(serverValue(player -> dropped(player, target).stream().mapToInt(entity -> entity.getItem().getCount()).sum())
                    >= existingItems + definition.count(),
                "Actual item output count appears for " + id + ": " + definition.target() + " x" + definition.count());
        } else if (definition.action().equals("summon_entity")) {
            check(serverValue(player -> targetEntities(player, definition.target()).stream()
                    .filter(entity -> !existingTargets.contains(entity.getUUID())).count()) >= definition.count(),
                "Actual summoned entity count appears for " + id + ": " + definition.target() + " x" + definition.count());
        } else {
            assertActionOutcome(id, definition);
        }
        assertOfferingSettlement(id, definition, offerings);
        assertEntityRequirementSettlement(id);
        closeScreen(context);
        final Vec3 resultPosition = serverValue(player -> {
            if (definition.action().equals("summon_item")) {
                final var target = targetItem(definition.target());
                return dropped(player, target).getFirst().position().add(0, 0.15, 0);
            }
            if (definition.action().equals("summon_entity")) {
                final var result = targetEntities(player, definition.target()).stream()
                    .filter(entity -> !existingTargets.contains(entity.getUUID())).findFirst().orElseThrow();
                if (id.equals("summon_familiar")) {
                    check(result instanceof com.kadamitas.warlockery.entity.SpectralFamiliarEntity,
                        "summon_familiar creates the declared Spectral Familiar, not the separate cat familiar");
                }
                return result.getEyePosition();
            }
            return Vec3.atCenterOf(CENTER);
        });
        look(context, resultPosition);
        screenshot(context, id + "-actual-result");
        checks.add("Actual " + id + " cast: book pages, every chalk mark including infernal/veil rings, natively dropped offerings, "
            + "native focus/selection/Begin, live cast, exact offering settlement, and verified " + definition.target()
            + " x" + definition.count() + ". Declared time/weather/entity prerequisites are staged before activation.");
        passRitual(id, definition.action().equals("summon_item") || definition.action().equals("summon_entity")
            ? "Normal activation completed and produced " + definition.target() + " x" + definition.count()
            : "Normal activation completed and the explicit " + definition.action() + " world-state assertion passed.");
    }

    private void initializeRitualResults() {
        RitualManager.INSTANCE.all().stream()
            .filter(entry -> entry.id().getNamespace().equals("warlockery"))
            .sorted(java.util.Comparator.comparing(entry -> entry.id().getPath()))
            .forEach(entry -> ritualResults.put(entry.id().getPath(), result("NOT_RUN",
                "No native activation outcome assertion has completed for this ritual.")));
        check(ritualResults.size() == 109, "Acceptance catalog must account for all 109 built-in rituals");
        report.put("ritual_results", ritualResults);
    }

    private Set<String> selectedRitualIds() {
        final Set<String> all = ritualResults.keySet();
        final String configured = System.getProperty("warlockery.ritualIds", "").trim();
        if (configured.isEmpty()) return Set.copyOf(all);
        final Set<String> selected = new java.util.LinkedHashSet<>();
        for (String token : configured.split(",")) {
            final String requested = token.trim();
            if (requested.isEmpty()) continue;
            final int separator = requested.indexOf(':');
            final String namespace = separator < 0 ? "warlockery" : requested.substring(0, separator);
            final String path = separator < 0 ? requested : requested.substring(separator + 1);
            check(namespace.equals("warlockery") && all.contains(path),
                "Unknown built-in ritual requested by warlockery.ritualIds: " + requested);
            selected.add(path);
        }
        check(!selected.isEmpty(), "warlockery.ritualIds did not select any built-in ritual");
        return Set.copyOf(selected);
    }

    private void walkImplementedActionFamilies(final ClientGameTestContext context) throws Exception {
        final Set<String> implementedActions = Set.of(
            "summon_item", "summon_entity", "storm", "skys_wrath", "eclipse", "glyph_transform",
            "fertility", "forestation", "natures_power", "drain_growth", "protection_ward",
            "sanctity_ward", "imprisonment_ward", "infuse_path", "recharge_path", "effect",
            "anguish_undead", "fortify_undead", "graveyard_mist", "banish", "call_beasts",
            "call_familiar", "blight", "toad_rain", "hell_on_earth", "raise_column",
            "broken_earth", "earths_wrath", "ice_sphere", "transpose_ore", "hex", "cleanse",
            "bind_circle", "bind_entity", "bind_fetish", "bind_item", "bind_waystone", "copy_waystone",
            "teleport_waystone", "teleport_entity", "remove_vampirism", "remove_werewolf",
            "marriage", "divorce", "prior_incarnation", "summon_huntsman", "climate_shift",
            "transform_nami", "transform_werewolf", "manifest"
        );
        for (var entry : RitualManager.INSTANCE.all().stream()
            .filter(candidate -> candidate.id().getNamespace().equals("warlockery"))
            .sorted(java.util.Comparator.comparing(candidate -> candidate.id().getPath())).toList()) {
            final String id = entry.id().getPath();
            if (id.equals("cook_food") || !selectedRituals.contains(id)
                || !implementedActions.contains(entry.definition().action())) continue;
            try {
                extraRite(context, id);
            } catch (Exception | Error failure) {
                ritualResults.put(id, result("FAILED", failure.toString()));
                throw failure;
            }
        }
        final List<String> notRun = ritualResults.entrySet().stream()
            .filter(entry -> selectedRituals.contains(entry.getKey()))
            .filter(entry -> entry.getValue().get("status").equals("NOT_RUN"))
            .map(Map.Entry::getKey).toList();
        final long passed = ritualResults.values().stream()
            .filter(entry -> entry.get("status").equals("PASSED")).count();
        report.put("ritual_summary", Map.of(
            "total", ritualResults.size(),
            "selected", selectedRituals.size(),
            "passed", passed,
            "selected_not_run", notRun.size(),
            "selected_not_run_ids", notRun,
            "not_run", ritualResults.values().stream().filter(entry -> entry.get("status").equals("NOT_RUN")).count()
        ));
        if (!notRun.isEmpty()) {
            throw new AssertionError("Ritual walkthrough remains incomplete; explicit outcome assertions NOT RUN: "
                + String.join(", ", notRun));
        }
    }

    private void passRitual(final String id, final String evidence) {
        ritualResults.put(id, result("PASSED", evidence));
        try {
            writeReport();
        } catch (Exception failure) {
            throw new AssertionError("Cannot preserve completed ritual evidence", failure);
        }
    }

    private static Map<String, Object> result(final String status, final String evidence) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("evidence", evidence);
        return result;
    }

    private static void prepareDeclaredConditions(
        final ServerPlayer player,
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        final var requirements = definition.requirements();
        check(requirements.dimension().isBlank(), id + " requires a separate dimension fixture: " + requirements.dimension());
        check(requirements.minimumPlayers() <= 1 || id.equals("hex_wolf"),
            id + " requires an unsupported participant fixture: " + requirements.minimumPlayers());
        final var server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
        if (requirements.thundering()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather thunder");
        } else if (requirements.raining()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather rain");
        }
        if (definition.nightOnly() || requirements.fullMoon()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
        } else if (requirements.dayOnly()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
        }
        if (id.equals("blood_audience")) stageClearedOceanMonument(player);
    }

    private static void stageClearedOceanMonument(final ServerPlayer player) {
        final var level = player.level();
        final var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        final var monument = structures.get(
            net.minecraft.world.level.levelgen.structure.BuiltinStructures.OCEAN_MONUMENT).orElseThrow();
        final var deepOcean = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .get(net.minecraft.world.level.biome.Biomes.DEEP_OCEAN).orElseThrow();
        final var generator = level.getChunkSource().getGenerator();
        final var origin = net.minecraft.world.level.ChunkPos.containing(CENTER);
        final var generated = monument.value().generate(
            monument,
            level.dimension(),
            level.registryAccess(),
            generator,
            new net.minecraft.world.level.biome.FixedBiomeSource(deepOcean),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            origin,
            0,
            level,
            biome -> true
        );
        check(generated.isValid(), "Blood Audience fixture generates a real vanilla Ocean Monument StructureStart");
        final var originChunk = level.getChunk(origin.x(), origin.z());
        originChunk.setStartForStructure(monument.value(), generated);
        originChunk.markUnsaved();
        generated.getBoundingBox().intersectingChunks().forEach(chunkPosition -> {
            final var chunk = level.getChunk(chunkPosition.x(), chunkPosition.z());
            chunk.addReferenceForStructure(monument.value(), origin.pack());
            chunk.markUnsaved();
            generated.placeInChunk(
                level,
                level.structureManager(),
                generator,
                level.getRandom(),
                new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    chunkPosition.getMinBlockX(), level.getMinY(), chunkPosition.getMinBlockZ(),
                    chunkPosition.getMaxBlockX(), level.getMaxY() + 1, chunkPosition.getMaxBlockZ()),
                chunkPosition
            );
        });
        final var start = level.structureManager().getStructureWithPieceAt(CENTER,
            holder -> holder.is(net.minecraft.world.level.levelgen.structure.BuiltinStructures.OCEAN_MONUMENT));
        check(start.isValid(), "Blood Audience circle center lies inside the generated Ocean Monument structure start");
        level.getEntitiesOfClass(net.minecraft.world.entity.monster.ElderGuardian.class,
                AABB.of(start.getBoundingBox()), net.minecraft.world.entity.LivingEntity::isAlive)
            .forEach(net.minecraft.world.entity.Entity::discard);
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            level.setBlockAndUpdate(new BlockPos(x, CENTER.getY() - 1, z), Blocks.STONE.defaultBlockState());
            for (int y = CENTER.getY(); y <= CENTER.getY() + 4; y++)
                level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        check(level.getEntitiesOfClass(net.minecraft.world.entity.monster.ElderGuardian.class,
                AABB.of(start.getBoundingBox()), net.minecraft.world.entity.LivingEntity::isAlive).isEmpty(),
            "Blood Audience fixture clears every live Elder Guardian through ordinary entity removal");
    }

    private static Map<String, Integer> castingGlyphs(
        final com.kadamitas.warlockery.ritual.RitualDefinition definition,
        final Map<String, Integer> guideGlyphs
    ) {
        if (!definition.action().equals("glyph_transform")) return guideGlyphs;
        final int marks = guideGlyphs.values().stream().findFirst().orElseThrow();
        final String source = definition.target().equals("warlockery:circleglyphritual")
            ? "circleglyph_veil" : "circleglyphritual";
        return Map.of(source, marks);
    }

    private void stageActionPrerequisites(
        final ServerPlayer player,
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        final var level = player.level();
        switch (definition.action()) {
            case "fertility" -> {
                level.setBlockAndUpdate(ACTION_BLOCK.below(), Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK, Blocks.WHEAT.defaultBlockState());
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.POISON, 600));
            }
            case "forestation" -> {
                for (int x = -5; x <= 5; x += 2) for (int z = -5; z <= 5; z += 2) {
                    level.setBlockAndUpdate(CENTER.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
                }
            }
            case "natures_power" -> {
                level.setBlockAndUpdate(ACTION_BLOCK.below(), Blocks.COARSE_DIRT.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK_TWO.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK_TWO, Blocks.DEAD_BUSH.defaultBlockState());
            }
            case "drain_growth" -> {
                level.setBlockAndUpdate(ACTION_BLOCK.below(), Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK,
                    Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
            }
            case "skys_wrath" -> {
                final var target = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(target != null, "Sky's Wrath target can be created");
                target.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                target.setNoAi(true);
                level.addFreshEntity(target);
                actionTargets.put(id, target.getUUID());
            }
            case "effect", "anguish_undead", "fortify_undead", "graveyard_mist" -> {
                final var target = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(target != null, id + " effect target can be created");
                target.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                target.setNoAi(true);
                level.addFreshEntity(target);
                actionTargets.put(id, target.getUUID());
            }
            case "banish" -> {
                final var demon = com.kadamitas.warlockery.registry.ModEntities.ALL.get("imp").get().create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(demon != null, id + " demon target can be created");
                demon.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                ((net.minecraft.world.entity.Mob) demon).setNoAi(true);
                level.addFreshEntity(demon);
                actionTargets.put(id, demon.getUUID());
            }
            case "call_beasts" -> {
                final var beast = net.minecraft.world.entity.EntityTypes.COW.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(beast != null, "Beastial Call target can be created");
                beast.setPos(Vec3.atBottomCenterOf(CENTER.offset(12, 0, 0)));
                level.addFreshEntity(beast);
                actionTargets.put(id, beast.getUUID());
                actionMeasurements.put(id, beast.distanceToSqr(Vec3.atCenterOf(CENTER)));
            }
            case "call_familiar" -> {
                final var familiar = com.kadamitas.warlockery.registry.ModEntities.ALL.get("familiar_cat").get().create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(familiar instanceof net.minecraft.world.entity.Mob, "Familiar recall target can be created");
                familiar.setPos(Vec3.atBottomCenterOf(CENTER.offset(10, 0, 0)));
                level.addFreshEntity(familiar);
                com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(
                    (net.minecraft.world.entity.Mob) familiar, player.getUUID());
                actionTargets.put(id, familiar.getUUID());
            }
            case "blight" -> {
                level.setBlockAndUpdate(ACTION_BLOCK.below(), Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK,
                    Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
                level.setBlockAndUpdate(ACTION_BLOCK_TWO.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(ACTION_BLOCK_TWO, Blocks.DANDELION.defaultBlockState());
            }
            case "hex", "cleanse" -> {
                if (id.equals("corrupt_doll")) {
                    final var familiar = ModEntities.ALL.get("familiar_cat").get().create(
                        level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                    check(familiar instanceof net.minecraft.world.entity.Mob, "Corrupted Doll familiar can be created");
                    familiar.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                    level.addFreshEntity(familiar);
                    com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(familiar, player.getUUID());
                    final ItemStack protection = new ItemStack(ModItems.ALL.get("earth_guard_doll").get());
                    new com.kadamitas.warlockery.item.SympatheticBinding(
                        player.getUUID(), player.getName().getString(), "player").write(protection);
                    player.getInventory().setItem(9, protection);
                    actionMeasurements.put(id, (double) protection.getDamageValue());
                    actionTargets.put(id, player.getUUID());
                    break;
                }
                final var victim = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(victim != null, id + " sympathetic victim can be created");
                victim.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                victim.setNoAi(true);
                level.addFreshEntity(victim);
                actionTargets.put(id, victim.getUUID());
                if (definition.action().equals("cleanse")) {
                    com.kadamitas.warlockery.ritual.HexBehaviors.require(definition.target())
                        .apply(victim, 4_000);
                    check(com.kadamitas.warlockery.ritual.HexBehaviors.isActive(victim, definition.target()),
                        id + " fixture starts with the exact live hex active");
                }
            }
            case "broken_earth" -> {
                for (int distance = 1; distance <= definition.radius(); distance++) {
                    level.setBlockAndUpdate(CENTER.north(distance).below(), Blocks.STONE.defaultBlockState());
                }
            }
            case "earths_wrath" -> {
                for (BlockPos source : List.of(CENTER.offset(1, -2, 0), CENTER.offset(-1, -2, 0),
                    CENTER.offset(0, -2, 1), CENTER.offset(0, -2, -1))) {
                    level.setBlockAndUpdate(source, Blocks.LAVA.defaultBlockState());
                }
            }
            case "transpose_ore" -> level.setBlockAndUpdate(CENTER.offset(0, -3, 0), Blocks.IRON_ORE.defaultBlockState());
            case "recharge_path" -> {
                final var path = com.kadamitas.warlockery.magic.MagicPath.OVERWORLD;
                com.kadamitas.warlockery.magic.MagicPathState.grantPermanent(player, path);
                check(com.kadamitas.warlockery.magic.MagicPathState.spend(player, path, 100),
                    "Recharge fixture drains a real infused reserve before casting");
            }
            case "bind_entity" -> {
                final String entityId = definition.target().equals("spectral") ? "spirit" : "familiar_cat";
                final var candidate = ModEntities.ALL.get(entityId).get().create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(candidate instanceof net.minecraft.world.entity.Mob, id + " binding candidate can be created");
                candidate.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                ((net.minecraft.world.entity.Mob) candidate).setNoAi(true);
                level.addFreshEntity(candidate);
                actionTargets.put(id, candidate.getUUID());
            }
            case "bind_fetish" -> {
                for (String entityId : List.of("spirit", "spirit", "spirit", "spectre", "banshee")) {
                    final var spirit = ModEntities.ALL.get(entityId).get().create(
                        level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                    check(spirit instanceof net.minecraft.world.entity.Mob, id + " spectral pattern member can be created");
                    spirit.setPos(CENTER.getX() + 0.5 + level.getRandom().nextDouble(), CENTER.getY() + 1.0,
                        CENTER.getZ() + 0.5 + level.getRandom().nextDouble());
                    ((net.minecraft.world.entity.Mob) spirit).setNoAi(true);
                    level.addFreshEntity(spirit);
                }
            }
            case "teleport_entity" -> {
                final var target = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(target != null, "Teleport target can be created");
                target.setPos(Vec3.atBottomCenterOf(CENTER.offset(5, 0, 0)));
                target.setNoAi(true);
                level.addFreshEntity(target);
                actionTargets.put(id, target.getUUID());
            }
            case "remove_vampirism" -> com.kadamitas.warlockery.transformation.SupernaturalProgression.beginPath(
                player, com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.VAMPIRE);
            case "remove_werewolf" -> com.kadamitas.warlockery.transformation.SupernaturalProgression.beginPath(
                player, com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.WEREWOLF);
            case "marriage" -> {
                final var nami = ModEntities.NAMI.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(nami != null, "Marriage Nami partner can be created");
                nami.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                level.addFreshEntity(nami);
                actionTargets.put(id, nami.getUUID());
            }
            case "divorce" -> {
                final var nami = ModEntities.NAMI.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(nami != null, "Divorce Nami spouse can be created");
                nami.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                level.addFreshEntity(nami);
                final var marriages = com.kadamitas.warlockery.ritual.marriage.MarriageData.get(level);
                check(marriages.marryNami(player.getUUID(), nami.getUUID())
                        == com.kadamitas.warlockery.ritual.marriage.MarriageData.MarriageResult.SUCCESS,
                    "Divorce fixture establishes a real Nami marriage");
                final String spouseName = marriages.bond(player.getUUID()).orElseThrow().spouseName();
                nami.acceptMarriage(player, spouseName);
                actionTargets.put(id, nami.getUUID());
            }
            case "prior_incarnation" -> {
                final BlockPos death = CENTER.offset(10, 0, 0);
                final long deathTime = level.getGameTime();
                final var lost = new ItemEntity(level, death.getX() + 0.5, death.getY() + 1.0,
                    death.getZ() + 0.5, new ItemStack(Items.DIAMOND, 2));
                com.kadamitas.warlockery.data.WarlockeryEntityData.get(lost)
                    .putString("WarlockeryPriorOwner", player.getUUID().toString());
                com.kadamitas.warlockery.data.WarlockeryEntityData.get(lost)
                    .putLong("WarlockeryPriorDeathTime", deathTime);
                level.addFreshEntity(lost);
                com.kadamitas.warlockery.ritual.PriorIncarnationData.get(level).record(
                    player.getUUID(), level.dimension().identifier(), death, deathTime);
                actionTargets.put(id, lost.getUUID());
            }
            case "summon_huntsman" -> com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure.positions(CENTER)
                .forEach(position -> level.setBlockAndUpdate(position,
                    ModBlocks.ALL.get("wickerbundle").get().defaultBlockState().setValue(
                        com.kadamitas.warlockery.block.WickerBundleBlock.BLOODIED, true)));
            case "transform_nami" -> actionTargets.put(id,
                entityOfferings.getOrDefault(id, List.of()).stream().findFirst()
                    .orElseThrow(() -> new AssertionError("Blood Audience requires its staged Nami")).uuid());
            case "transform_werewolf" -> {
                final var familiar = ModEntities.ALL.get("familiar_cat").get().create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(familiar instanceof net.minecraft.world.entity.Mob, "Wolf Hex familiar can be created");
                familiar.setPos(Vec3.atBottomCenterOf(ACTION_BLOCK));
                level.addFreshEntity(familiar);
                com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(familiar, player.getUUID());
                for (int index = 0; index < 6; index++) {
                    final var mage = ModEntities.ALL.get("circle_mage").get().create(
                        level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                    check(mage instanceof net.minecraft.world.entity.Mob, "Wolf Hex coven mage can be created");
                    final double angle = index * Math.PI / 3.0;
                    mage.setPos(CENTER.getX() + 0.5 + Math.cos(angle) * 5.0, CENTER.getY() + 1.0,
                        CENTER.getZ() + 0.5 + Math.sin(angle) * 5.0);
                    ((net.minecraft.world.entity.Mob) mage).setNoAi(true);
                    level.addFreshEntity(mage);
                    com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(mage, player.getUUID());
                }
            }
            case "manifest" -> {
                final BlockPos foot = CENTER.offset(0, 0, 5);
                final BlockPos head = foot.east();
                level.setBlockAndUpdate(foot, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_bed")).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                level.setBlockAndUpdate(head, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_bed")).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                final ServerPlayer dreamer = connectedFixturePlayer(player, "RiteDreamer", foot);
                check(dreamer.startSleepInBed(foot).right().isPresent(),
                    "Manifestation fixture uses normal bed sleep for its connected target");
                check(dreamer.isSleeping(), "Manifestation target is actually sleeping before native activation");
                actionTargets.put(id, dreamer.getUUID());
            }
            default -> {
            }
        }
    }

    private ServerPlayer connectedFixturePlayer(
        final ServerPlayer caster,
        final String name,
        final BlockPos position
    ) {
        final var server = caster.level().getServer();
        final var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        final var fixture = new ServerPlayer(
            server, caster.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
        final var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        final var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        server.getPlayerList().placeNewPlayer(connection, fixture, cookie);
        fixture.connection.handleAcceptPlayerLoad(
            new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        fixture.setGameMode(GameType.SURVIVAL);
        fixture.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        syntheticPlayers.add(fixture);
        return fixture;
    }

    private void assertActionOutcome(
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        switch (definition.action()) {
            case "storm" -> check(serverValue(player -> player.level().getWeatherData().isRaining()
                    && player.level().getWeatherData().isThundering()),
                id + " starts real rain and thunder");
            case "skys_wrath" -> {
                check(serverValue(player -> player.level().getWeatherData().isThundering()),
                    "Sky's Wrath starts thunder");
                check(serverValue(player -> {
                    final var target = player.level().getEntity(actionTargets.get(id));
                    return target == null || !target.isAlive()
                        || target instanceof net.minecraft.world.entity.LivingEntity living
                            && living.getHealth() < living.getMaxHealth();
                }), "Sky's Wrath damages or kills its staged nearest target");
            }
            case "eclipse" -> check(serverValue(player -> player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)),
                id + " applies supernatural darkness to a participant");
            case "glyph_transform" -> check(serverValue(player -> {
                final var target = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    net.minecraft.resources.Identifier.parse(definition.target()));
                final int marks = definition.requirements().ingredients().getFirst().count();
                final var size = ChalkCircleLayout.Size.forOfferingCount(marks);
                return size.offsets().stream().allMatch(offset -> player.level().getBlockState(CENTER.offset(offset)).is(target));
            }), id + " recolors the selected ring to " + definition.target());
            case "fertility" -> check(serverValue(player ->
                    player.level().getBlockState(ACTION_BLOCK).getValue(net.minecraft.world.level.block.CropBlock.AGE) > 0
                        && !player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)),
                id + " grows the staged crop and cures poison");
            case "forestation" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.offset(-definition.radius(), -1, -definition.radius()),
                    CENTER.offset(definition.radius(), 12, definition.radius()))
                .anyMatch(pos -> player.level().getBlockState(pos).is(com.kadamitas.warlockery.registry.WarlockeryTags.Blocks.RITUAL_SAPLINGS)
                    || player.level().getBlockState(pos).is(com.kadamitas.warlockery.registry.WarlockeryTags.Blocks.RITUAL_LOGS))),
                "Forestation places or grows a tagged ritual tree");
            case "natures_power" -> check(serverValue(player -> player.level().getBlockState(ACTION_BLOCK.below()).is(Blocks.GRASS_BLOCK)
                    && player.level().getBlockState(ACTION_BLOCK_TWO).is(Blocks.SHORT_GRASS)),
                "Nature's Power repairs soil and dead vegetation");
            case "drain_growth" -> check(serverValue(player ->
                    player.level().getBlockState(ACTION_BLOCK).getValue(net.minecraft.world.level.block.CropBlock.AGE) == 0),
                "Drain Growth resets a mature crop to age zero");
            case "protection_ward", "sanctity_ward", "imprisonment_ward" -> {
                final var type = switch (definition.action()) {
                    case "protection_ward" -> com.kadamitas.warlockery.ritual.RitualWardType.PROTECTION;
                    case "sanctity_ward" -> com.kadamitas.warlockery.ritual.RitualWardType.SANCTITY;
                    default -> com.kadamitas.warlockery.ritual.RitualWardType.IMPRISONMENT;
                };
                check(serverValue(player -> com.kadamitas.warlockery.ritual.RitualWardData.get(player.level())
                        .contains(type, Vec3.atCenterOf(CENTER), player.level().getGameTime())),
                    id + " creates the declared live ward");
            }
            case "infuse_path" -> check(serverValue(player -> com.kadamitas.warlockery.magic.MagicPathState.has(
                    player, com.kadamitas.warlockery.magic.MagicPath.require(definition.target()))),
                id + " grants the declared permanent magic path");
            case "recharge_path" -> check(serverValue(player -> {
                final var path = com.kadamitas.warlockery.magic.MagicPath.OVERWORLD;
                return com.kadamitas.warlockery.magic.MagicPathState.reserve(player, path) == path.maximumReserve()
                    && com.kadamitas.warlockery.ritual.RitualWardData.get(player.level()).contains(
                        com.kadamitas.warlockery.ritual.RitualWardType.RECHARGE,
                        player.position(), player.level().getGameTime());
            }), "Recharge restores a real infused reserve and creates its sustaining ward");
            case "effect" -> check(serverValue(player -> player.hasEffect(
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(
                        net.minecraft.resources.Identifier.parse(definition.effect())).orElseThrow())),
                id + " applies its declared live status effect");
            case "anguish_undead" -> check(targetHasEffect(id, net.minecraft.world.effect.MobEffects.STRENGTH),
                "Anguish applies Strength to the staged nearby creature");
            case "fortify_undead" -> check(targetHasEffect(id, net.minecraft.world.effect.MobEffects.RESISTANCE),
                "Fortification applies Resistance to the staged undead");
            case "graveyard_mist" -> check(targetHasEffect(id, net.minecraft.world.effect.MobEffects.REGENERATION)
                    && serverValue(player -> player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)),
                "Graveyard Mist regenerates undead and blinds the living caster");
            case "banish" -> check(serverValue(player -> {
                final var target = player.level().getEntity(actionTargets.get(id));
                return target == null || !target.isAlive();
            }),
                id + " removes the staged tagged demon");
            case "call_beasts" -> check(serverValue(player -> {
                final var beast = player.level().getEntity(actionTargets.get(id));
                return beast != null && beast.distanceToSqr(Vec3.atCenterOf(CENTER)) < actionMeasurements.get(id);
            }), "Beastial Call makes the staged animal move toward the circle");
            case "call_familiar" -> check(serverValue(player -> {
                final var familiar = player.level().getEntity(actionTargets.get(id));
                return familiar != null && familiar.distanceToSqr(Vec3.atCenterOf(CENTER)) < 4.0;
            }), "Calling teleports the caster's owned familiar to the circle");
            case "blight" -> check(serverValue(player -> !player.level().getBlockState(ACTION_BLOCK).is(Blocks.WHEAT)
                    && !player.level().getBlockState(ACTION_BLOCK_TWO).is(Blocks.DANDELION)),
                "Blight destroys the staged mature crop and flower");
            case "toad_rain" -> check(serverValue(player -> player.level().isRaining()
                    && targetEntities(player, definition.target()).size() >= definition.count()),
                "Rain of Toads starts rain and creates the declared frog count");
            case "hell_on_earth" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.offset(-definition.radius(), -2, -definition.radius()),
                    CENTER.offset(definition.radius(), 3, definition.radius()))
                .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE))),
                "Hell on Earth creates its contained soul-fire ring");
            case "raise_column" -> check(serverValue(player -> player.level().getBlockState(CENTER.above()).is(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.parse(definition.target())))),
                id + " raises the declared terrain column");
            case "broken_earth" -> check(serverValue(player -> player.level().isEmptyBlock(CENTER.north().below())),
                "Broken Earth tears open the staged northward stone line");
            case "earths_wrath" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.above(), CENTER.above(12)).anyMatch(pos -> !player.level().getFluidState(pos).isEmpty())),
                "Earth's Wrath raises staged lava above the circle");
            case "ice_sphere" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.offset(-8, -8, -8), CENTER.offset(8, 8, 8))
                .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.PACKED_ICE))),
                "Icy Expansion creates a packed-ice shell");
            case "transpose_ore" -> check(serverValue(player -> player.level().getBlockState(CENTER.offset(0, -3, 0)).is(Blocks.STONE)
                    && !dropped(player, Items.IRON_ORE).isEmpty()),
                "Ore transposition replaces staged ore with stone and drops it at the circle");
            case "hex" -> {
                if (id.equals("corrupt_doll")) {
                    check(serverValue(player -> {
                        final ItemStack doll = player.getInventory().getItem(9);
                        return doll.isEmpty() || doll.getDamageValue() > actionMeasurements.get(id);
                    }), "Corrupted Doll damages or destroys the caster's bound protection doll");
                } else {
                    check(serverValue(player -> player.level().getEntity(actionTargets.get(id))
                            instanceof net.minecraft.world.entity.LivingEntity living
                                && com.kadamitas.warlockery.ritual.HexBehaviors.isActive(living, definition.target())),
                        id + " applies its exact persistent hex behavior to the bound victim");
                }
            }
            case "cleanse" -> check(serverValue(player -> player.level().getEntity(actionTargets.get(id))
                    instanceof net.minecraft.world.entity.LivingEntity living
                        && !com.kadamitas.warlockery.ritual.HexBehaviors.isActive(living, definition.target())),
                id + " removes its exact persistent hex behavior from the bound victim");
            case "bind_circle" -> check(serverValue(player ->
                    dropped(player, ModItems.ALL.get("circletalisman").get()).stream()
                        .map(ItemEntity::getItem).anyMatch(stack ->
                            com.kadamitas.warlockery.item.CircleTalismanState.read(stack).isPresent())
                    && BlockPos.betweenClosedStream(CENTER.offset(-6, -1, -6), CENTER.offset(6, 1, 6))
                        .noneMatch(pos -> player.level().getBlockState(pos)
                            .is(com.kadamitas.warlockery.registry.WarlockeryTags.Blocks.CHALK_GLYPHS))),
                id + " stores the actual drawn circle in the talisman and removes its glyphs");
            case "bind_entity" -> {
                if (definition.target().equals("spectral")) {
                    check(serverValue(player -> dropped(player, ModItems.ALL.get("spectralstone").get()).stream()
                            .map(ItemEntity::getItem)
                            .anyMatch(stack -> !com.kadamitas.warlockery.item.SpectralStoneState.read(stack).captured().isEmpty())),
                        "Spectral binding captures a live spectral creature in the preserved stone");
                } else {
                    check(serverValue(player -> player.level().getEntities(
                            (net.minecraft.world.entity.Entity) null, new AABB(CENTER).inflate(8), entity ->
                                entity instanceof net.minecraft.world.entity.Mob
                                    && com.kadamitas.warlockery.entity.CreatureBehaviorState.isOwnedBy(entity, player.getUUID()))
                            .stream().anyMatch(entity -> entity.getType() == ModEntities.ALL.get("familiar_cat").get())),
                        "Familiar binding replaces and binds the staged creature to the caster");
                }
            }
            case "bind_fetish" -> check(serverValue(player -> dropped(player, targetItem(definition.target())).stream()
                    .map(ItemEntity::getItem)
                    .anyMatch(stack -> com.kadamitas.warlockery.block.FetishBindingState.read(stack).isPresent())),
                id + " creates a fetish carrying the spectral-pattern mode");
            case "bind_item" -> check(serverValue(player -> dropped(player, targetItem(definition.target())).stream()
                    .map(ItemEntity::getItem).map(com.kadamitas.warlockery.item.SympatheticBinding::read)
                    .flatMap(java.util.Optional::stream).anyMatch(binding -> binding.targets(player))),
                id + " creates the declared item bound to the sampled player");
            case "bind_waystone" -> check(serverValue(player -> dropped(
                    player, ModItems.ALL.get("ingredient_waystone_bound").get()).stream()
                    .map(ItemEntity::getItem).map(com.kadamitas.warlockery.item.WaystoneState::read)
                    .flatMap(java.util.Optional::stream).anyMatch(location -> location.position().equals(CENTER)
                        && location.dimension().equals(player.level().dimension().identifier()))),
                id + " transforms the blank waystone into a live binding to the circle");
            case "copy_waystone" -> check(serverValue(player -> dropped(
                    player, ModItems.ALL.get("ingredient_waystone_bound").get()).stream()
                    .map(ItemEntity::getItem).map(com.kadamitas.warlockery.item.WaystoneState::read)
                    .flatMap(java.util.Optional::stream)
                    .filter(location -> location.position().equals(CENTER.offset(12, 0, 0))).count() >= 2),
                id + " copies the source location into the blank waystone");
            case "teleport_waystone" -> check(serverValue(player ->
                    player.distanceToSqr(Vec3.atCenterOf(CENTER.offset(12, 1, 0))) < 2.0),
                "Waystone teleport moves the real caster to the recorded destination");
            case "teleport_entity" -> check(serverValue(player -> {
                    final var target = player.level().getEntity(actionTargets.get(id));
                    return target != null && target.distanceToSqr(Vec3.atCenterOf(CENTER.above())) < 2.0;
                }), "Entity teleport moves the bound live creature to the circle");
            case "remove_vampirism", "remove_werewolf" -> check(serverValue(player ->
                    com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player)
                        == com.kadamitas.warlockery.transformation.SupernaturalForm.NONE),
                id + " cures the staged supernatural form");
            case "marriage" -> check(serverValue(player -> {
                    final var bond = com.kadamitas.warlockery.ritual.marriage.MarriageData.get(player.level())
                        .bond(player.getUUID());
                    return bond.isPresent() && bond.orElseThrow().isNami()
                        && bond.orElseThrow().partnerUuid().equals(actionTargets.get(id))
                        && dropped(player, ModItems.ALL.get("wedding_ring").get()).stream()
                            .map(ItemEntity::getItem).anyMatch(stack -> !stack.getOrDefault(
                                net.minecraft.core.component.DataComponents.LORE,
                                net.minecraft.world.item.component.ItemLore.EMPTY).lines().isEmpty());
                }), "Marriage creates a persistent Nami bond and writes the couple onto a preserved wedding ring");
            case "divorce" -> check(serverValue(player ->
                    !com.kadamitas.warlockery.ritual.marriage.MarriageData.get(player.level())
                        .isMarried(player.getUUID())),
                "Divorce removes the caster's staged Nami marriage");
            case "prior_incarnation" -> check(serverValue(player -> {
                    final var lost = player.level().getEntity(actionTargets.get(id));
                    return (lost == null || !lost.isAlive())
                        && dropped(player, Items.DIAMOND).stream().anyMatch(drop ->
                            drop.distanceToSqr(Vec3.atCenterOf(CENTER.above())) < 4.0
                                && drop.getItem().getCount() == 2);
                }), "Prior Incarnation relocates the exact recorded death drop to the circle");
            case "summon_huntsman" -> check(serverValue(player ->
                    !targetEntities(player, definition.target()).isEmpty()
                        && com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure.positions(CENTER).stream()
                            .allMatch(position -> player.level().isEmptyBlock(position))),
                "Thorned Pursuer summoning creates the entity and consumes all four bloodied bundles");
            case "climate_shift" -> check(serverValue(player ->
                    player.level().getBiome(CENTER).is(net.minecraft.world.level.biome.Biomes.DESERT)),
                "Climate Change changes the circle chunk to the recorded Desert biome");
            case "transform_nami" -> check(serverValue(player -> {
                    final var original = player.level().getEntity(actionTargets.get(id));
                    return (original == null || !original.isAlive())
                        && !targetEntities(player, "warlockery:naamah").isEmpty();
                }), "Blood Audience transforms the staged unmarried Nami into a Naamah");
            case "transform_werewolf" -> check(serverValue(player ->
                    com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player)
                        == com.kadamitas.warlockery.transformation.SupernaturalForm.WEREWOLF),
                "Wolf Hex initiates the rendered caster's live werewolf progression");
            case "manifest" -> check(serverValue(player -> {
                    final var target = player.level().getEntity(actionTargets.get(id));
                    return target instanceof ServerPlayer dreamer
                        && com.kadamitas.warlockery.dream.SpiritManifestationState.granted(
                            dreamer, player.level().getServer().getTickCount());
                }), "Manifestation grants the live timed manifestation state to the bound sleeping player");
            default -> throw new AssertionError("No explicit observable outcome assertion exists for " + id
                + " action " + definition.action());
        }
    }

    private boolean targetHasEffect(
        final String id,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect
    ) {
        return serverValue(player -> player.level().getEntity(actionTargets.get(id))
            instanceof net.minecraft.world.entity.LivingEntity living && living.hasEffect(effect));
    }

    private void configureBoundOfferings(
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        server(player -> {
            if (Set.of("hex", "cleanse", "bind_item", "teleport_entity", "remove_vampirism",
                    "remove_werewolf", "prior_incarnation", "transform_werewolf", "manifest")
                .contains(definition.action())) {
                final boolean entityTarget = definition.action().equals("hex")
                    || definition.action().equals("cleanse") || definition.action().equals("teleport_entity")
                    || definition.action().equals("manifest");
                final var target = entityTarget && actionTargets.containsKey(id)
                    ? player.level().getEntity(actionTargets.get(id)) : player;
                check(target != null, id + " has a live sympathetic target");
                final var vialItem = ModItems.ALL.get("sympathetic_vial").get();
                final var vial = dropped(player, vialItem).stream().findFirst().orElseThrow(
                    () -> new AssertionError(id + " must stage its native sympathetic vial offering"));
                new com.kadamitas.warlockery.item.SympatheticBinding(
                    target.getUUID(), target.getName().getString(), target instanceof ServerPlayer ? "player" : "entity")
                    .write(vial.getItem());
            }
            if (definition.action().equals("copy_waystone") || definition.action().equals("teleport_waystone")) {
                final BlockPos destination = CENTER.offset(12, 0, 0);
                final ItemStack bound = dropped(player, ModItems.ALL.get("ingredient_waystone_bound").get()).stream()
                    .findFirst().orElseThrow(() -> new AssertionError(id + " requires its natively dropped bound waystone"))
                    .getItem();
                com.kadamitas.warlockery.item.WaystoneState.write(
                    bound, player.level().dimension().identifier(), destination);
            }
        });
    }

    private void dropSpecialPrerequisite(final ClientGameTestContext context, final String id) {
        if (!id.equals("climate_change")) return;
        server(player -> {
            final ItemStack book = new ItemStack(ModItems.ALL.get("bookbiomes2").get());
            com.kadamitas.warlockery.item.BiomeNoteState.write(
                book, net.minecraft.world.level.biome.Biomes.DESERT.identifier());
            player.getInventory().setItem(8, book);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        position(context, 0.5, CENTER.getY(), -2.5);
        look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
        context.getInput().pressKey(GLFW.GLFW_KEY_9);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        context.waitTicks(3);
        check(serverValue(player -> dropped(player, ModItems.ALL.get("bookbiomes2").get()).stream()
                .map(ItemEntity::getItem).map(com.kadamitas.warlockery.item.BiomeNoteState::read)
                .flatMap(java.util.Optional::stream)
                .anyMatch(net.minecraft.world.level.biome.Biomes.DESERT.identifier()::equals)),
            "Native Q stages a Book of Biomes recorded to Desert for climate_change");
    }

    private void stageEntityRequirements(
        final ServerPlayer player,
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        final List<EntityEvidence> staged = new ArrayList<>();
        int offset = 0;
        for (var requirement : definition.requirements().entities()) {
            final net.minecraft.world.entity.EntityType<?> type = matchingEntityType(requirement.entity());
            for (int count = 0; count < requirement.count(); count++) {
                final net.minecraft.world.entity.Entity entity = type.create(
                    player.level(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(entity != null, "Entity offering can be created for " + id + ": " + requirement.entity());
                entity.setPos(CENTER.getX() + 0.5 + ((offset % 5) - 2) * 0.7,
                    CENTER.getY() + 1.0, CENTER.getZ() + 0.5 + ((offset / 5) - 1) * 0.7);
                if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                player.level().addFreshEntity(entity);
                staged.add(new EntityEvidence(entity.getUUID(), requirement.entity(), requirement.consume()));
                offset++;
            }
        }
        entityOfferings.put(id, List.copyOf(staged));
    }

    private static net.minecraft.world.entity.EntityType<?> matchingEntityType(final String declared) {
        if (!declared.startsWith("#")) {
            return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.parse(declared));
        }
        final var tag = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            net.minecraft.resources.Identifier.parse(declared.substring(1))
        );
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.listElements()
            .filter(holder -> holder.is(tag)).map(holder -> holder.value())
            .findFirst().orElseThrow(() -> new AssertionError("No entity matches " + declared));
    }

    private List<OfferingEvidence> dropDeclaredOfferings(
        final ClientGameTestContext context,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        final List<OfferingEvidence> evidence = new ArrayList<>();
        final var requirements = definition.requirements().ingredients();
        for (int start = 0; start < requirements.size(); start += 5) {
            final int batchStart = start;
            final int batchEnd = Math.min(start + 5, requirements.size());
            server(player -> {
                for (int slot = 4; slot <= 8; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
                for (int index = batchStart; index < batchEnd; index++) {
                    final var input = requirements.get(index);
                    player.getInventory().setItem(4 + index - batchStart,
                        new ItemStack(matchingItem(input.ingredient()), input.count()));
                }
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            position(context, 0.5, CENTER.getY(), -2.5);
            look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
            for (int index = batchStart; index < batchEnd; index++) {
                final var input = requirements.get(index);
                context.getInput().pressKey(GLFW.GLFW_KEY_5 + index - batchStart);
                for (int item = 0; item < input.count(); item++) context.getInput().pressKey(GLFW.GLFW_KEY_Q);
                evidence.add(new OfferingEvidence(matchingItem(input.ingredient()), input.count(), input.consume(), input.ingredient()));
            }
            context.waitTicks(3);
        }
        evidence.forEach(offering -> check(serverValue(player -> dropped(player, offering.item()).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum()) >= offering.count(),
            "Native Q stages declared offering " + offering.declared() + " x" + offering.count()));
        return List.copyOf(evidence);
    }

    private static net.minecraft.world.item.Item matchingItem(final String declared) {
        final var ingredient = com.kadamitas.warlockery.util.ItemIngredient.parse(declared).orElseThrow();
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .filter(candidate -> ingredient.matches(new ItemStack(candidate))).findFirst()
            .orElseThrow(() -> new AssertionError("No item matches " + declared));
    }

    private void assertOfferingSettlement(
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition,
        final List<OfferingEvidence> offerings
    ) {
        offerings.forEach(offering -> {
            final int remaining = serverValue(player -> dropped(player, offering.item()).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum());
            final boolean transformedOutput = offering.consume()
                && (definition.action().equals("bind_fetish") || definition.action().equals("bind_item"))
                && offering.item() == targetItem(definition.target());
            final boolean preservedAsBoundWaystone = !offering.consume() && definition.action().equals("bind_waystone")
                && offering.item() == ModItems.ALL.get("ingredient_waystone").get();
            final boolean copiedIntoBoundWaystone = !offering.consume() && definition.action().equals("copy_waystone")
                && offering.item() == ModItems.ALL.get("ingredient_waystone").get();
            final boolean severedWeddingRing = id.equals("divorce") && !offering.consume()
                && offering.item() == ModItems.ALL.get("wedding_ring").get() && remaining == 0;
            check(transformedOutput || preservedAsBoundWaystone || copiedIntoBoundWaystone || severedWeddingRing
                || (offering.consume()
                    ? remaining == 0 : remaining >= offering.count()),
                (offering.consume() ? "Consumed" : "Preserved") + " offering settles correctly: " + offering.declared());
        });
    }

    private void assertEntityRequirementSettlement(final String id) {
        entityOfferings.getOrDefault(id, List.of()).forEach(offering -> {
            final boolean alive = serverValue(player -> {
                final var entity = player.level().getEntity(offering.uuid());
                return entity != null && entity.isAlive();
            });
            final boolean transformedNami = id.equals("blood_audience") && !offering.consume() && !alive;
            check(transformedNami || (offering.consume() ? !alive : alive),
                (offering.consume() ? "Consumed" : "Presence-only") + " entity settles correctly: " + offering.declared());
        });
    }

    private static net.minecraft.world.item.Item targetItem(final String target) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(target));
    }

    private static List<net.minecraft.world.entity.Entity> targetEntities(final ServerPlayer player, final String target) {
        return player.level().getEntities((net.minecraft.world.entity.Entity) null, new AABB(CENTER).inflate(12),
            entity -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(target));
    }

    private record OfferingEvidence(net.minecraft.world.item.Item item, int count, boolean consume, String declared) {}

    private record EntityEvidence(UUID uuid, String declared, boolean consume) {}

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
        look(context, target, null);
    }

    private static void look(final ClientGameTestContext context, final Vec3 target, final Map<String, Object> trace) {
        context.runOnClient(client -> {
            if (trace != null) trace.put("aim", Map.of("target", coordinates(target), "player", playerTrace(client.player)));
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
        report.put("fixture_isolation", fixtureIsolation);
        report.put("recent_chalk_ray_trace", chalkRayTrace);
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
