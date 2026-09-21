package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.CircleHeartBlockEntity;
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
    private final Map<String, net.minecraft.core.Direction> castingDirections = new LinkedHashMap<>();
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
                + "Between rites, completed outcomes are recorded before previous wards, player effects and motion are cleared and the fixture terrain (floor, vent airspace and any generated monument) is reset; unfinished casts fail isolation. "
                + "No glyph blocks, dropped offerings, ritual session, recipe action, or cooked output are injected. No Ritual Knife attachment is installed.");
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                setup(context);
                initializeRitualResults();
                selectedRituals = selectedRitualIds();
                report.put("selected_ritual_ids", List.copyOf(selectedRituals));
                if (selectedRituals.contains("cook_food")) {
                System.out.println("WARLOCKERY_RITUAL_CASE_START cook_food");
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
                checks.add("Using the Circle Magic book on the golden center opens its saved entry; native article navigation and Perform Ritual expose the missing offering.");
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
                requireBoundOutput("cook_food", Items.COOKED_BEEF, 1, (player, stack) -> true,
                    "Real ritual action produces exactly one cooked beef, on the ground or legitimately collected");
                check(serverValue(player -> dropped(player, Items.BEEF).isEmpty() && dropped(player, Items.COAL).isEmpty()),
                    "Raw food is converted and the coal offering is consumed");
                closeScreen(context);
                screenshot(context, "broiling-output-before-collection");
                final Vec3 cookedPosition = serverValue(player -> dropped(player, Items.COOKED_BEEF).stream()
                    .findFirst().map(ItemEntity::position).orElse(null));
                if (cookedPosition != null) {
                    look(context, cookedPosition);
                    context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                    try {
                        context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                            .anyMatch(stack -> stack.is(Items.COOKED_BEEF)), 100);
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                    }
                    checks.add("Native Begin Rite consumes Coal and converts Raw Beef; normal forward movement collects the Cooked Beef.");
                } else {
                    checks.add("Native Begin Rite consumes Coal and converts Raw Beef; ordinary pickup already collected the Cooked Beef before movement was needed.");
                }
                check(serverValue(player -> player.getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> stack.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum()) == 1,
                    "Exactly one cooked beef reaches the player's inventory");
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
            isolation.put("previous_infusions", com.kadamitas.warlockery.magic.MagicPathState.active(player)
                .stream().map(com.kadamitas.warlockery.magic.MagicPath::id).toList());
            fixtureIsolation.put(activeRitual, isolation);
            check(!previousCastActive, "Previous ritual cast completes before isolating " + activeRitual);
            // This world is reused only to avoid client startup per rite. Outcomes have already been
            // asserted; a prior portable ward must not push the next rite's staged chalk camera.
            player.level().getDataStorage().set(RitualWardData.TYPE, new RitualWardData());
            player.level().getDataStorage().set(
                com.kadamitas.warlockery.ritual.RitualEclipseData.TYPE,
                new com.kadamitas.warlockery.ritual.RitualEclipseData());
            player.removeAllEffects();
            player.clearFire();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            com.kadamitas.warlockery.transformation.SupernaturalProgression.cure(player);
            if (!Boolean.getBoolean("warlockery.ritualPreserveInfusions")) {
                com.kadamitas.warlockery.data.WarlockeryEntityData.get(player).remove("WarlockeryMagicPaths");
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.syncVelocity = true;
            isolation.put("after", playerTrace(player));
            isolation.put("remaining_wards", wardTrace(player));
            syntheticPlayers.forEach(player.level().getServer().getPlayerList()::remove);
            syntheticPlayers.clear();
            player.level().getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(20)).forEach(ItemEntity::discard);
            player.level().getEntities((net.minecraft.world.entity.Entity) null, new AABB(CENTER).inflate(20),
                entity -> entity != player).forEach(net.minecraft.world.entity.Entity::discard);
            isolation.put("terrain_reset", resetTerrain(player.level()));
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
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);
        drawMark(context, CENTER, "circle");
        final List<List<Integer>> positions = new ArrayList<>();
        for (var glyph : glyphs.entrySet()) {
            context.getInput().pressKey(chalkKey(glyph.getKey()));
            final var size = ChalkCircleLayout.Size.forMarkCount(glyph.getValue());
            final boolean goldenRing = glyph.getKey().equals("circleglyphgolden");
            if (goldenRing) context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
            try {
                context.waitTicks(2);
                for (BlockPos offset : size.offsets()) {
                    final BlockPos target = CENTER.offset(offset);
                    drawMark(context, target, glyph.getKey());
                    positions.add(List.of(offset.getX(), offset.getZ()));
                }
            } finally {
                if (goldenRing) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
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
            case "circleglyphritual" -> com.mojang.blaze3d.platform.InputConstants.KEY_3;
            case "circleglyphinfernal" -> com.mojang.blaze3d.platform.InputConstants.KEY_5;
            case "circleglyph_veil" -> com.mojang.blaze3d.platform.InputConstants.KEY_6;
            case "circleglyphgolden" -> com.mojang.blaze3d.platform.InputConstants.KEY_2;
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
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_5);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_6);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q);
        context.waitTicks(3);
        check(serverValue(player -> dropped(player, Items.COAL).size()) == 1, "Native drop key creates the coal offering");
        check(serverValue(player -> dropped(player, Items.BEEF).size()) == 1, "Native drop key puts raw food in the ritual area");
        position(context, 3.0, CENTER.getY(), 0.5);
        checks.add("Native Q drops exactly one Coal offering and one Raw Beef target; player position is staged away to prevent accidental pickup during selection.");
    }

    private void openRitual(final ClientGameTestContext context) {
        check(serverValue(player -> player.getInventory().getNonEquipmentItems().stream()
            .anyMatch(stack -> stack.is(ModItems.ALL.get("ingredient_book_circle_magic").get()))),
            "The caster carries the actual Circle Magic book required by the ritual heart");
        final int bookSlot = serverValue(player -> java.util.stream.IntStream.range(0, 9)
            .filter(slot -> player.getInventory().getItem(slot).is(ModItems.ALL.get("ingredient_book_circle_magic").get()))
            .findFirst().orElseThrow(() -> new AssertionError("Circle Magic book must be accessible in the hotbar")));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + bookSlot);
        look(context, new Vec3(0.5, CENTER.getY() + 0.01, 0.5));
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(CENTER)),
            "Circle Magic book ray targets the Golden Chalk heart");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
    }

    private void selectBroiling(final ClientGameTestContext context) {
        selectRite(context, "cook_food");
    }

    private void selectRite(final ClientGameTestContext context, final String id) {
        ManualClientAcceptance.selectSection(context, "rite_" + id);
        check(context.computeOnClient(client -> field(client.gui.screen(), "selectedSection").equals("rite_" + id)),
            "Native book navigation selects the intended ritual article");
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.perform_ritual").getString());
        context.waitFor(client -> casting(client.gui.screen()).performing() && selectedOnScreen(client.gui.screen()) != null
            && selectedOnScreen(client.gui.screen()).id().equals("warlockery:" + id));
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
                player.level().setBlockAndUpdate(ALTAR.offset(x, -2, z), ModBlocks.ALL.get("demonheart").get().defaultBlockState());
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
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
            ? outputItemCount(player, targetItem(definition.target())) : 0);
        position(context, 0.5, CENTER.getY(), -2.5);
        look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
        if (definition.action().equals("summon_huntsman")) {
            position(context, 2.5, CENTER.getY(), -1.5);
        } else {
            position(context, definition.action().equals("raise_column") ? 4.5 : 3.0, CENTER.getY(), 0.5);
        }
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
        castingDirections.put(id, serverValue(ServerPlayer::getDirection));
        report.put(id + "_caster_before_cast", casterSnapshot());
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.begin").getString());
        context.waitTicks(3);
        check(serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)), "Native Begin starts " + id);
        final Object castingScreen = checkCastingUi(context, id);
        context.waitTicks(option.castingTime() + 12);
        check(!serverValue(player -> RitualSessionData.get(player.level()).isActive(CENTER)), "Real cast completes for " + id);
        report.put(id + "_caster_after_cast", casterSnapshot());
        if (id.equals("teleport_waystone")) {
            check(serverValue(player -> player.distanceToSqr(Vec3.atBottomCenterOf(CENTER.offset(12, 1, 0))) < 0.01),
                "Waystone reaches its exact bound destination before awaiting the out-of-range book terminal update");
        }
        checkAutomaticCompletion(context, castingScreen, id);
        report.put(id + "_caster_after_cast", casterSnapshot());
        if (definition.action().equals("summon_item")) {
            final var target = targetItem(definition.target());
            report.put(id + "_output_observation", serverValue(player -> Map.of(
                "before", existingItems, "after", outputItemCount(player, target),
                "dropped", dropped(player, target).stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "inventory", player.getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> stack.is(target)).mapToInt(ItemStack::getCount).sum())));
            check(serverValue(player -> outputItemCount(player, target))
                    >= existingItems + definition.count(),
                "Actual item output count appears for " + id + ": " + definition.target() + " x" + definition.count());
        } else if (definition.action().equals("summon_entity")) {
            check(serverValue(player -> targetEntities(player, definition.target()).stream()
                    .filter(entity -> !existingTargets.contains(entity.getUUID())).count()) >= definition.count(),
                "Actual summoned entity count appears for " + id + ": " + definition.target() + " x" + definition.count());
        } else {
            if (definition.action().equals("graveyard_mist")) context.waitTicks(30);
            if (definition.action().equals("prior_incarnation")) {
                report.put(id + "_recovery_observation", serverValue(player -> Map.of(
                    "original", String.valueOf(player.level().getEntity(actionTargets.get(id))),
                    "death_record", String.valueOf(com.kadamitas.warlockery.ritual.PriorIncarnationData.get(player.level())
                        .find(player.getUUID())),
                    "inventory_diamonds", player.getInventory().getNonEquipmentItems().stream()
                        .filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum(),
                    "dropped_diamonds", dropped(player, Items.DIAMOND).stream()
                        .map(drop -> Map.of("id", drop.getUUID().toString(), "count", drop.getItem().getCount(),
                            "position", drop.position().toString(), "distance", drop.distanceToSqr(Vec3.atCenterOf(CENTER.above()))))
                        .toList())));
            }
            assertActionOutcome(id, definition);
        }
        assertOfferingSettlement(id, definition, offerings);
        assertEntityRequirementSettlement(id);
        if (definition.action().equals("recharge_path")) {
            server(player -> {
                check(com.kadamitas.warlockery.magic.MagicPathState.spend(player,
                    com.kadamitas.warlockery.magic.MagicPath.OVERWORLD, 80),
                    "Sustained recharge starts with a newly depleted reserve");
                player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            });
            context.waitTicks(22);
            check(serverValue(player -> com.kadamitas.warlockery.magic.MagicPathState.reserve(player,
                    com.kadamitas.warlockery.magic.MagicPath.OVERWORLD) >= 80
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)),
                "The live recharge ward restores another 40 reserve and applies regeneration on ordinary ticks");
        }
        closeScreen(context);
        final Vec3 resultPosition = serverValue(player -> {
            if (definition.action().equals("summon_item")) {
                final var target = targetItem(definition.target());
                return dropped(player, target).stream().findFirst()
                    .map(entity -> entity.position().add(0, 0.15, 0)).orElse(Vec3.atCenterOf(CENTER));
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
        if (definition.action().equals("raise_column") || definition.action().equals("earths_wrath")) {
            position(context, 11.5, CENTER.getY(), -10.5);
            look(context, new Vec3(CENTER.getX() + 0.5,
                CENTER.getY() + (definition.action().equals("raise_column") ? definition.count() / 2.0 : 3.0),
                CENTER.getZ() + 0.5));
            world.getConnection().waitForChunksRender();
        } else {
            look(context, resultPosition);
        }
        screenshot(context, id + "-actual-result");
        if (definition.action().equals("summon_item") && serverValue(player ->
                dropped(player, targetItem(definition.target())).isEmpty())) {
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_E);
            context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
            screenshot(context, id + "-output-picked-up-inventory");
            closeScreen(context);
        }
        if (Set.of("hex_heat_metal", "hex_insanity", "hex_misfortune", "hex_nightmare",
                "hex_overheating", "hex_sinking").contains(id)) {
            report.put(id + "_actual_consequences",
                RitualHexConsequenceAcceptance.verify(context, world, actionTargets.get(id), id));
            screenshot(context, id + "-actual-consequences");
        }
        checks.add("Actual " + id + " cast: book pages, every chalk mark including infernal/veil rings, natively dropped offerings, "
            + "native focus/book article/Perform Ritual/Begin, live cast, exact offering settlement, and verified " + definition.target()
            + " x" + definition.count() + ". Declared time/weather/entity prerequisites are staged before activation.");
        passRitual(id, definition.action().equals("summon_item") || definition.action().equals("summon_entity")
            ? "Normal activation completed and produced " + definition.target() + " x" + definition.count()
            : "Normal activation completed and the explicit " + definition.action() + " world-state assertion passed.");
    }

    /**
     * Every rite of a partition casts in the same world, so earlier rites leave real terrain behind: the Blood
     * Audience fixture generates a vanilla Ocean Monument (y 39 to 62) around the heart, Forestation grows
     * trees and the raise-earth family stacks columns up to sixteen blocks high. Earth's Wrath measures its
     * vent from the heightmap above the heart and refuses any occupied column, rim or basin block, so leftover
     * monument roof above the previous five-block clearing made the live cast report "nothing answers". Every
     * rite therefore starts from the same flat fixture: one stone floor block under the heart and air from
     * twelve blocks below it to twenty above, plus the whole monument footprint whenever one was generated.
     */
    private static Map<String, Integer> resetTerrain(final net.minecraft.server.level.ServerLevel level) {
        int cleared = 0;
        final var monument = level.structureManager().getStructureWithPieceAt(CENTER,
            holder -> holder.is(net.minecraft.world.level.levelgen.structure.BuiltinStructures.OCEAN_MONUMENT));
        if (monument.isValid()) {
            final var box = monument.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - 12, box.minY(), box.minZ() - 12,
                    box.maxX() + 12, Math.max(box.maxY(), CENTER.getY() + 20), box.maxZ() + 12)) {
                if (!level.getBlockState(pos).isAir()
                        && level.setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState())) cleared++;
            }
        }
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            for (int y = CENTER.getY() - 12; y <= CENTER.getY() + 20; y++) {
                final BlockPos pos = new BlockPos(x, y, z);
                final var expected = y == CENTER.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                if (level.getBlockState(pos) != expected && level.setBlockAndUpdate(pos, expected)) cleared++;
            }
        }
        return Map.of("blocks", cleared, "monument", monument.isValid() ? 1 : 0);
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
                System.out.println("WARLOCKERY_RITUAL_CASE_START " + id);
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
        System.out.println("WARLOCKERY_RITUAL_CASE_PASS " + id);
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
        check(requirements.dimension().isBlank()
                || requirements.dimension().equals(player.level().dimension().identifier().toString()),
            id + " requires a separate dimension fixture: " + requirements.dimension());
        final var server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
        if (requirements.thundering()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather thunder");
        } else if (requirements.raining()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather rain");
        }
        if (definition.nightOnly() || requirements.fullMoon() || id.equals("cure_vampire")) {
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
            level.getChunkSource().randomState().createClimateSampler(
                net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()),
            level.getChunkSource().randomState(),
            level.getStructureTemplateManager(),
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
        if (definition.action().equals("glyph_transform")) {
            check(guideGlyphs.size() == 1 && guideGlyphs.keySet().stream()
                    .noneMatch(source -> definition.target().equals("warlockery:" + source)),
                "The book's chalk conversion diagram must show an accepted source distinct from its output: "
                    + guideGlyphs + " -> " + definition.target());
        }
        return guideGlyphs;
    }

    private void stageActionPrerequisites(
        final ServerPlayer player,
        final String id,
        final com.kadamitas.warlockery.ritual.RitualDefinition definition
    ) {
        final var level = player.level();
        if (!id.equals("hex_wolf")) {
            final int companions = Math.max(0, definition.requirements().minimumPlayers() - 1);
            for (int index = 0; index < companions; index++) {
                final var mage = ModEntities.ALL.get("circle_mage").get().create(
                    level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(mage instanceof net.minecraft.world.entity.Mob, id + " recruited coven mage can be created");
                mage.setPos(CENTER.getX() - 3.5 + index * 0.7, CENTER.getY(), CENTER.getZ() - 3.5);
                ((net.minecraft.world.entity.Mob) mage).setNoAi(true);
                level.addFreshEntity(mage);
                com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(mage, player.getUUID());
            }
            if (companions > 0) report.put(id + "_coven_fixture", companions + " living recruited Circle Mages accompany the native caster.");
        }
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
                if (definition.action().equals("graveyard_mist")) {
                    target.setHealth(8.0F);
                    actionMeasurements.put(id, (double) target.getHealth());
                }
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
                    com.kadamitas.warlockery.item.SympatheticBinding.from(player).write(protection);
                    player.getInventory().setItem(9, protection);
                    if (Boolean.getBoolean("warlockery.ritualDollGuard")) {
                        final ItemStack guard = new ItemStack(ModItems.ALL.get("doll_guard").get());
                        com.kadamitas.warlockery.item.SympatheticBinding.from(player).write(guard);
                        player.getInventory().setItem(10, guard);
                        report.put(id + "_doll_guard_fixture", "A bound Doll Guard accompanies the bound Earth Guard; native Corrupted Doll must wear only the Doll Guard.");
                    }
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
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    for (int distance = 1; distance <= definition.radius(); distance++) {
                        level.setBlockAndUpdate(CENTER.relative(direction, distance).below(), Blocks.STONE.defaultBlockState());
                    }
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
                final BlockPos foot = CENTER.offset(0, 0, 1);
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
                final var bedState = level.getBlockState(head);
                final var bed = (net.minecraft.world.level.block.AbstractBedBlock) bedState.getBlock();
                check(dreamer.startSleepInBed(bed, bedState, bed.getBedRule(level, head), head).right().isPresent(),
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
            case "eclipse" -> check(serverValue(player -> player.level().isDarkOutside()
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)),
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
            case "graveyard_mist" -> {
                final var outcome = serverValue(player -> {
                    final var target = (net.minecraft.world.entity.LivingEntity)
                        player.level().getEntity(actionTargets.get(id));
                    check(target != null && target.isAlive(), "Graveyard Mist undead target survives");
                    return Map.of("initial_health", actionMeasurements.get(id),
                        "health", (double) target.getHealth(),
                        "undead_mending", target.hasEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                            .wrapAsHolder(com.kadamitas.warlockery.registry.ModEffects.UNDEAD_MENDING.get())),
                        "undead_invisibility", target.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                        "living_blindness", player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
                        "living_slowness", player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS));
                });
                report.put(id + "_actual_effects", outcome);
                check((double) outcome.get("health") > (double) outcome.get("initial_health")
                        && Boolean.TRUE.equals(outcome.get("undead_mending"))
                        && Boolean.TRUE.equals(outcome.get("undead_invisibility"))
                        && Boolean.TRUE.equals(outcome.get("living_blindness"))
                        && Boolean.TRUE.equals(outcome.get("living_slowness")),
                    "Graveyard Mist restores injured undead health, conceals it, and blinds/slows the living: " + outcome);
            }
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
            case "toad_rain" -> {
                final var toads = serverValue(player -> player.level().getEntities((net.minecraft.world.entity.Entity) null,
                    new AABB(CENTER).inflate(12), entity -> entity.getType().builtInRegistryHolder().is(
                        com.kadamitas.warlockery.registry.WarlockeryTags.EntityTypes.HEX_TOADS)
                        && com.kadamitas.warlockery.ritual.hex.HexEntityMarkers.toad(entity).isPresent()).stream()
                    .map(entity -> Map.of("type", entity.getType().toString(), "position", entity.position().toString(),
                        "role", com.kadamitas.warlockery.ritual.hex.HexEntityMarkers.toad(entity).orElseThrow().role().name()))
                    .toList());
                report.put(id + "_toads", toads);
                check(serverValue(player -> player.level().isRaining()) && toads.size() == definition.count()
                        && toads.stream().anyMatch(toad -> toad.get("role").equals("POISONOUS"))
                        && toads.stream().anyMatch(toad -> toad.get("role").equals("EXPLOSIVE")),
                    "Rain of Toads starts rain and creates all eight tagged frogs/toads with both hazard roles: " + toads);
            }
            case "hell_on_earth" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.offset(-definition.radius(), -2, -definition.radius()),
                    CENTER.offset(definition.radius(), 3, definition.radius()))
                .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE))),
                "Hell on Earth creates its contained soul-fire ring");
            case "raise_column" -> check(serverValue(player -> player.level().getBlockState(CENTER.above()).is(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.parse(definition.target())))),
                id + " raises the declared terrain column");
            case "broken_earth" -> check(serverValue(player -> player.level().isEmptyBlock(CENTER.relative(castingDirections.get(id)).below())),
                "Broken Earth tears open the staged northward stone line");
            case "earths_wrath" -> {
                report.put(id + "_vent_observation", serverValue(player -> {
                    final Map<String, Object> vent = new LinkedHashMap<>();
                    vent.put("heightmap_surface", player.level().getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CENTER.getX(), CENTER.getZ()));
                    vent.put("column", BlockPos.betweenClosedStream(CENTER.above(), CENTER.above(12))
                        .map(pos -> pos.getY() + "=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(player.level().getBlockState(pos).getBlock()).getPath()).toList());
                    vent.put("sources", List.of(CENTER.offset(1, -2, 0), CENTER.offset(-1, -2, 0),
                        CENTER.offset(0, -2, 1), CENTER.offset(0, -2, -1)).stream().map(pos -> pos.toShortString() + "="
                            + (player.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)
                                ? (player.level().getFluidState(pos).isSource() ? "lava_source" : "lava_flowing")
                                : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(player.level().getBlockState(pos).getBlock()).getPath())).toList());
                    return vent;
                }));
                check(serverValue(player -> {
                final var basin = BlockPos.betweenClosedStream(CENTER.above(), CENTER.above(12))
                    .filter(pos -> player.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA))
                    .map(BlockPos::immutable).findFirst();
                return basin.isPresent()
                    && player.level().getBlockState(basin.orElseThrow().below()).is(Blocks.MAGMA_BLOCK)
                    && BlockPos.betweenClosedStream(basin.orElseThrow().offset(-1, 0, -1),
                        basin.orElseThrow().offset(1, 0, 1))
                        .filter(pos -> !pos.equals(basin.orElseThrow()))
                        .allMatch(pos -> player.level().getBlockState(pos).is(Blocks.BASALT))
                    && List.of(CENTER.offset(1, -2, 0), CENTER.offset(-1, -2, 0),
                        CENTER.offset(0, -2, 1), CENTER.offset(0, -2, -1)).stream()
                        .anyMatch(pos -> !player.level().getFluidState(pos).isSource());
                }), "Earth's Wrath moves an underground lava source into a contained basalt vent with a magma floor");
            }
            case "ice_sphere" -> check(serverValue(player -> BlockPos.betweenClosedStream(
                    CENTER.offset(-8, -8, -8), CENTER.offset(8, 8, 8))
                .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.PACKED_ICE))),
                "Icy Expansion creates a packed-ice shell");
            case "transpose_ore" -> {
                check(serverValue(player -> player.level().getBlockState(CENTER.offset(0, -3, 0)).is(Blocks.STONE)),
                    "Ore transposition replaces the staged ore block with stone");
                requireBoundOutput(id, Items.IRON_ORE, 1, (player, stack) -> true,
                    "relocates exactly one staged iron ore item to the circle or its collecting caster");
            }
            case "hex" -> {
                if (id.equals("corrupt_doll")) {
                    check(serverValue(player -> {
                        final ItemStack doll = player.getInventory().getItem(9);
                        if (Boolean.getBoolean("warlockery.ritualDollGuard")) {
                            final ItemStack guard = player.getInventory().getItem(10);
                            return !doll.isEmpty() && doll.getDamageValue() == actionMeasurements.get(id)
                                && !guard.isEmpty() && guard.getDamageValue() == 1;
                        }
                        return doll.isEmpty() || doll.getDamageValue() > actionMeasurements.get(id);
                    }), Boolean.getBoolean("warlockery.ritualDollGuard")
                        ? "Doll Guard spends one use to preserve the caster's Earth Guard against native Corrupted Doll"
                        : "Corrupted Doll damages or destroys the caster's bound protection doll");
                } else {
                    check(serverValue(player -> player.level().getEntity(actionTargets.get(id))
                            instanceof net.minecraft.world.entity.LivingEntity living
                                && (definition.target().equals("blindness")
                                    ? living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                                    : com.kadamitas.warlockery.ritual.HexBehaviors.isActive(living, definition.target()))),
                        id + " applies its exact hex or status effect to the intended victim");
                }
            }
            case "cleanse" -> check(serverValue(player -> player.level().getEntity(actionTargets.get(id))
                    instanceof net.minecraft.world.entity.LivingEntity living
                        && !com.kadamitas.warlockery.ritual.HexBehaviors.isActive(living, definition.target())),
                id + " removes its exact persistent hex behavior from the bound victim");
            case "bind_circle" -> {
                requireBoundOutput(id, ModItems.ALL.get("circletalisman").get(), 1,
                    (player, stack) -> com.kadamitas.warlockery.item.CircleTalismanState.read(stack)
                        .filter(circle -> circle.glyphs().size()
                            == 1 + ChalkCircleLayout.canonicalGlyphs(definition.glyphs()).values().stream()
                                .mapToInt(Integer::intValue).sum()).isPresent(),
                    "preserves one talisman containing every drawn circle glyph");
                check(serverValue(player -> BlockPos.betweenClosedStream(CENTER.offset(-6, -1, -6), CENTER.offset(6, 1, 6))
                        .noneMatch(pos -> player.level().getBlockState(pos)
                            .is(com.kadamitas.warlockery.registry.WarlockeryTags.Blocks.CHALK_GLYPHS))),
                    id + " removes the captured circle's actual glyph blocks");
            }
            case "bind_entity" -> {
                if (definition.target().equals("spectral")) {
                    requireBoundOutput(id, ModItems.ALL.get("spectralstone").get(), 1,
                        (player, stack) -> com.kadamitas.warlockery.item.SpectralStoneState.read(stack).captured()
                            .equals(List.of(net.minecraft.resources.Identifier.parse("warlockery:spirit"))),
                        "preserves one stone containing exactly the staged spirit type");
                    check(serverValue(player -> player.level().getEntity(actionTargets.get(id)) == null),
                        "Spectral binding removes the actual captured spirit from the world");
                } else {
                    check(serverValue(player -> player.level().getEntities(
                            (net.minecraft.world.entity.Entity) null, new AABB(CENTER).inflate(8), entity ->
                                entity instanceof net.minecraft.world.entity.Mob
                                    && com.kadamitas.warlockery.entity.CreatureBehaviorState.isOwnedBy(entity, player.getUUID()))
                            .stream().anyMatch(entity -> entity.getType() == ModEntities.ALL.get("familiar_cat").get())),
                        "Familiar binding binds the staged creature to the caster");
                }
            }
            case "bind_fetish" -> requireBoundOutput(id, targetItem(definition.target()), 1,
                (player, stack) -> com.kadamitas.warlockery.block.FetishBindingState.read(stack)
                    .orElse(null) == com.kadamitas.warlockery.block.FetishMode.GHOST_WALKING,
                "creates exactly one fetish with the staged spectral pattern's Ghost Walking mode");
            case "bind_item" -> requireBoundOutput(id, targetItem(definition.target()), Math.clamp(definition.count(), 1, 64),
                (player, stack) -> com.kadamitas.warlockery.item.SympatheticBinding.read(stack)
                    .filter(binding -> binding.equals(com.kadamitas.warlockery.item.SympatheticBinding.from(player))).isPresent(),
                "creates the exact declared quantity bound to the sampled player's UUID, name and type");
            case "bind_waystone" -> requireBoundOutput(id, ModItems.ALL.get("ingredient_waystone_bound").get(), 1,
                (player, stack) -> com.kadamitas.warlockery.item.WaystoneState.read(stack)
                    .filter(location -> location.position().equals(CENTER)
                        && location.dimension().equals(player.level().dimension().identifier())).isPresent(),
                "transforms exactly one blank waystone into a live binding to the circle");
            case "copy_waystone" -> requireBoundOutput(id, ModItems.ALL.get("ingredient_waystone_bound").get(), 2,
                (player, stack) -> com.kadamitas.warlockery.item.WaystoneState.read(stack)
                    .filter(location -> location.position().equals(CENTER.offset(12, 0, 0))
                        && location.dimension().equals(player.level().dimension().identifier())).isPresent(),
                "preserves the source and copies its exact dimension and position into one blank waystone");
            case "teleport_waystone" -> check(serverValue(player ->
                    player.distanceToSqr(Vec3.atBottomCenterOf(CENTER.offset(12, 1, 0))) < 0.01),
                "Waystone teleport moves the real caster to the recorded destination");
            case "teleport_entity" -> check(serverValue(player -> {
                    final var target = player.level().getEntity(actionTargets.get(id));
                    return target != null && target.distanceToSqr(Vec3.atCenterOf(CENTER.above())) < 2.0;
                }), "Entity teleport moves the bound live creature to the circle");
            case "remove_vampirism", "remove_werewolf" -> check(serverValue(player ->
                    com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player)
                        == com.kadamitas.warlockery.transformation.SupernaturalForm.NONE),
                id + " cures the staged supernatural form");
            case "marriage" -> {
                check(serverValue(player -> {
                    final var bond = com.kadamitas.warlockery.ritual.marriage.MarriageData.get(player.level())
                        .bond(player.getUUID());
                    return bond.isPresent() && bond.orElseThrow().isNami()
                        && bond.orElseThrow().partnerUuid().equals(actionTargets.get(id))
                        && player.level().getEntity(actionTargets.get(id)) instanceof com.kadamitas.warlockery.entity.NamiEntity nami
                        && com.kadamitas.warlockery.entity.CreatureBehaviorState.isOwnedBy(nami, player.getUUID())
                        && nami.getName().getString().equals(bond.orElseThrow().spouseName());
                }), "Marriage creates a persistent bond with the exact staged Nami partner");
                // The recipe offers two wedding rings with consume=false, so both survive the cast, and the
                // live action inscribes the couple's names on the first wedding-ring stack it finds; a merged
                // two-ring stack therefore carries the names on both rings.
                final Map<String, Integer> rings = observeBoundOutput(id, ModItems.ALL.get("wedding_ring").get(),
                    (player, stack) -> com.kadamitas.warlockery.ritual.marriage.MarriageData.get(player.level())
                        .bond(player.getUUID()).map(bond -> stack.getOrDefault(
                            net.minecraft.core.component.DataComponents.LORE,
                            net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
                            .map(Component::getString).anyMatch(line -> line.contains(player.getDisplayName().getString())
                                && line.contains(bond.spouseName()))).orElse(false));
                check(rings.get("dropped") + rings.get("inventory") == 2 && rings.get("matching") >= 1,
                    id + " preserves both offered wedding rings and inscribes the caster and spouse names on at least one: "
                        + rings);
            }
            case "divorce" -> check(serverValue(player ->
                    !com.kadamitas.warlockery.ritual.marriage.MarriageData.get(player.level())
                        .isMarried(player.getUUID())),
                "Divorce removes the caster's staged Nami marriage");
            case "prior_incarnation" -> {
                check(serverValue(player -> {
                    final var lost = player.level().getEntity(actionTargets.get(id));
                    return (lost == null || !lost.isAlive())
                        && com.kadamitas.warlockery.ritual.PriorIncarnationData.get(player.level())
                            .find(player.getUUID()).isEmpty()
                        && dropped(player, Items.DIAMOND).stream().allMatch(drop ->
                            drop.distanceToSqr(Vec3.atBottomCenterOf(CENTER)) < 36.0)
                        && (player.getInventory().getNonEquipmentItems().stream().noneMatch(stack -> stack.is(Items.DIAMOND))
                            || player.distanceToSqr(Vec3.atBottomCenterOf(CENTER)) < 36.0);
                }), "Prior Incarnation removes the original death drop and record; recovered items or their collecting caster remain within six blocks of the circle");
                requireBoundOutput(id, Items.DIAMOND, 2, (player, stack) -> true,
                    "recovers exactly the two recorded diamonds at the circle, including legitimate caster pickup");
            }
            case "summon_huntsman" -> check(serverValue(player ->
                    !targetEntities(player, definition.target()).isEmpty()
                        && com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure.positions(CENTER).stream()
                            .allMatch(position -> player.level().isEmptyBlock(position))),
                "Thorned Pursuer summoning creates the entity and consumes all four bloodied bundles");
            case "climate_shift" -> check(serverValue(player ->
                    player.level().getBiome(CENTER.offset(8, 0, 8)).is(net.minecraft.world.level.biome.Biomes.DESERT)),
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

    private Map<String, Object> casterSnapshot() {
        return serverValue(player -> Map.of(
            "health", player.getHealth(), "alive", player.isAlive(),
            "dead_or_dying", player.isDeadOrDying(), "position", coordinates(player.position()),
            "form", com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player).name(),
            "game_time", player.level().getGameTime(),
            "dropped_vials", dropped(player, ModItems.ALL.get("sympathetic_vial").get()).stream()
                .map(entity -> Map.of("uuid", entity.getUUID().toString(), "position", coordinates(entity.position()),
                    "count", entity.getItem().getCount(), "binding",
                    com.kadamitas.warlockery.item.SympatheticBinding.read(entity.getItem()).toString())).toList(),
            "inventory_vials", java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                .mapToObj(player.getInventory()::getItem)
                .filter(stack -> stack.is(ModItems.ALL.get("sympathetic_vial").get()))
                .mapToInt(ItemStack::getCount).sum()));
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
                if (dropped(player, vialItem).isEmpty()
                    && (definition.action().equals("hex") || definition.action().equals("cleanse"))) {
                    report.put(id + "_targeting", "No sympathetic sample is required or supplied; the native area effect must reach the staged nearby target as described by the book.");
                    return;
                }
                final var vial = dropped(player, vialItem).stream().findFirst().orElseThrow(
                    () -> new AssertionError(id + " must stage its native sympathetic vial offering"));
                check(target instanceof net.minecraft.world.entity.LivingEntity, "The sympathetic target is alive");
                com.kadamitas.warlockery.item.SympatheticBinding.from(
                    (net.minecraft.world.entity.LivingEntity) target).write(vial.getItem());
            }
            if (definition.action().equals("copy_waystone") || definition.action().equals("teleport_waystone")) {
                final BlockPos destination = CENTER.offset(12, 0, 0);
                player.level().setBlockAndUpdate(destination, Blocks.STONE.defaultBlockState());
                final ItemStack bound = dropped(player, ModItems.ALL.get("ingredient_waystone_bound").get()).stream()
                    .findFirst().orElseThrow(() -> new AssertionError(id + " requires its natively dropped bound waystone"))
                    .getItem();
                com.kadamitas.warlockery.item.WaystoneState.write(
                    bound, player.level().dimension().identifier(), destination);
            }
        });
    }

    private void dropSpecialPrerequisite(final ClientGameTestContext context, final String id) {
        if (id.equals("manifestation")) {
            server(player -> {
                player.getInventory().setItem(8, new ItemStack(ModItems.ALL.get("sympathetic_vial").get()));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            position(context, 0.5, CENTER.getY(), -2.5);
            look(context, new Vec3(0.5, CENTER.getY() + 0.1, 0.5));
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_9);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q);
            context.waitTicks(3);
            check(serverValue(player -> !dropped(player, ModItems.ALL.get("sympathetic_vial").get()).isEmpty()),
                "Native Q supplies the sympathetic vial required by the Manifestation guide");
            return;
        }
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_9);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q);
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
                entity.setPos(CENTER.getX() - 3.5 + (offset % 3) * 0.8,
                    CENTER.getY() + 1.0, CENTER.getZ() - 2.0 + (offset / 3) * 0.9);
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
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_5 + index - batchStart);
                for (int item = 0; item < input.count(); item++) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q);
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
        if (declared.equals("#warlockery:sympathetic_containers")) {
            final var vial = ModItems.ALL.get("sympathetic_vial").get();
            check(ingredient.matches(new ItemStack(vial)), "The chosen sympathetic vial matches the declared container tag");
            return vial;
        }
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
            // These preserved offerings become validated outputs in assertActionOutcome.
            // Normal pickup after casting must not make an intact output appear consumed.
            final boolean preservedOutput = !offering.consume() && (
                (definition.action().equals("bind_circle") && offering.item() == ModItems.ALL.get("circletalisman").get())
                || (definition.action().equals("bind_entity") && definition.target().equals("spectral")
                    && offering.item() == ModItems.ALL.get("spectralstone").get())
                || (definition.action().equals("marriage") && offering.item() == ModItems.ALL.get("wedding_ring").get()));
            final int remaining = serverValue(player -> preservedOutput ? outputItemCount(player, offering.item())
                : dropped(player, offering.item()).stream().mapToInt(entity -> entity.getItem().getCount()).sum());
            final boolean transformedOutput = offering.consume()
                && (definition.action().equals("bind_fetish") || definition.action().equals("bind_item"))
                && offering.item() == targetItem(definition.target());
            final boolean preservedAsBoundWaystone = !offering.consume() && definition.action().equals("bind_waystone")
                && offering.item() == ModItems.ALL.get("ingredient_waystone").get();
            final boolean copiedIntoBoundWaystone = !offering.consume() && definition.action().equals("copy_waystone")
                && offering.item() == ModItems.ALL.get("ingredient_waystone").get();
            if (id.equals("divorce") && offering.item() == ModItems.ALL.get("wedding_ring").get()) {
                check(remaining == offering.count() - 1,
                    "Successful Severance consumes exactly one Wedding Ring after the cast");
                return;
            }
            check(transformedOutput || preservedAsBoundWaystone || copiedIntoBoundWaystone
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
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
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
            if (missing) {
                check(context.computeOnClient(client -> {
                    try {
                        final var article = ManualScreen.class.getDeclaredMethod("article", String.class);
                        article.setAccessible(true);
                        final var content = (ManualArticleCatalog.Article) article.invoke(client.gui.screen(),
                            (String) field(client.gui.screen(), "selectedSection"));
                        return content.body().getString().startsWith(Component.translatable(
                            "screen.warlockery.manual.book_required",
                            Component.translatable(distilling.translatedTitleKey())).getString());
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }), "Missing-book preview visibly names the exact required book before its crafting recipe");
            }
            screenshot(context, "book-reference-" + placement);
            if (placement.equals("offhand")) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
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

    private static ManualRitualCasting casting(final Object screen) {
        return (ManualRitualCasting) field(screen, "ritualCasting");
    }

    private static RitualManager.RitualOption selectedOnScreen(final Object screen) {
        return casting(screen).selected();
    }

    private static ManualLayout observedBookLayout(final Object screen) {
        try {
            final var method = ManualScreen.class.getDeclaredMethod("layout");
            method.setAccessible(true);
            return (ManualLayout) method.invoke(screen);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot observe the actual displayed book layout", failure);
        }
    }

    private Object checkCastingUi(final ClientGameTestContext context, final String id) throws Exception {
        context.waitFor(client -> client.gui.screen() instanceof ManualScreen
            && selectedOnScreen(client.gui.screen()) != null
            && com.kadamitas.warlockery.ritual.RitualUiState.castInProgress(selectedOnScreen(client.gui.screen())), 40);
        final Object screen = context.computeOnClient(client -> client.gui.screen());
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Book casting lets the real server cast continue");
        check(context.computeOnClient(client -> casting(screen).performing()), "Book remains in its actual Perform Ritual view");
        check(context.computeOnClient(client -> {
            final var layout = observedBookLayout(client.gui.screen());
            final StringBuilder text = new StringBuilder();
            casting(screen).pages(layout).forEach(page -> page.forEach(line ->
                line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; })));
            final String visible = text.toString().replaceAll("\\s+", "");
            final var option = selectedOnScreen(screen);
            return visible.contains(Component.translatable("screen.warlockery.ritual.casting").getString().replaceAll("\\s+", ""))
                && option.requirements().stream().filter(requirement -> requirement.category().equals("ingredient"))
                    .map(RitualRequirementText::line).map(Component::getString)
                    .noneMatch(line -> visible.contains(line.replaceAll("\\s+", "")));
        }), "Rendered book casting pages show progress without presenting consumed offerings as new blockers");
        check(context.computeOnClient(client -> client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .anyMatch(button -> button.visible && button.active && button.getMessage().getString().equals(
                Component.translatable("screen.warlockery.ritual.stop").getString()))),
            "Active book cast exposes its real cancellation control instead of another Begin action");
        check(context.computeOnClient(client -> client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .noneMatch(button -> button.visible && button.active && button.getMessage().getString().equals(
                Component.translatable("screen.warlockery.ritual.begin").getString()))),
            "The live book cannot start the same cast twice");
        context.waitFor(client -> client.level != null
            && client.level.getBlockEntity(CENTER) instanceof CircleHeartBlockEntity heart
            && heart.total() > 0 && heart.elapsed() > 0 && heart.elapsed() < heart.total(), 40);
        final Map<String, Object> progress = context.computeOnClient(client -> {
            final var heart = (CircleHeartBlockEntity) client.level.getBlockEntity(CENTER);
            final double percent = 100.0 * heart.elapsed() / heart.total();
            check(Double.isFinite(percent) && percent > 0.0 && percent < 100.0,
                "The rendered heart supplies finite, nonzero progress during the actual live cast");
            return Map.<String, Object>of("elapsed", heart.elapsed(), "total", heart.total(), "percent", percent);
        });
        report.put(id + "_observed_active_progress", progress);
        screenshot(context, id + "-active-cast-status");
        return screen;
    }

    private void checkDelayedRefresh(final ClientGameTestContext context) throws Exception {
        final Object book = context.computeOnClient(client -> client.gui.screen());
        final Object section = context.computeOnClient(client -> field(book, "selectedSection"));
        final Object page = context.computeOnClient(client -> field(book, "bodyPage"));
        final String id = selected(context).id();
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.ritual.refresh").getString());
        context.waitTicks(25);
        check(context.computeOnClient(client -> client.gui.screen() == book && casting(book).performing()
            && selectedOnScreen(book).id().equals(id)), "Live server checklist refresh preserves the selected book ritual");
        screenshot(context, "book-live-checklist-refresh");
        ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.back_to_ritual").getString());
        check(context.computeOnClient(client -> client.gui.screen() == book && !casting(book).performing()
            && field(book, "selectedSection").equals(section) && field(book, "bodyPage").equals(page)),
            "Native Back restores the exact article and reading page");
        screenshot(context, "book-back-to-ritual");
        closeScreen(context);
        context.waitTicks(25);
        check(context.computeOnClient(client -> client.gui.screen() == null), "Periodic checklist updates do not reopen a closed book");
        openRitual(context);
        selectRite(context, id.substring(id.indexOf(':') + 1));
        checks.add("Native Refresh preserves the live book selection; Back restores its article page; closing stays closed; heart use reopens the real book.");
    }

    private void checkAutomaticCompletion(final ClientGameTestContext context, final Object screen, final String id) throws Exception {
        context.waitFor(client -> client.gui.screen() == screen
            && selectedOnScreen(screen) != null
            && !com.kadamitas.warlockery.ritual.RitualUiState.castInProgress(selectedOnScreen(screen)), 40);
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Screen remains non-pausing after completion");
        screenshot(context, id + "-automatic-completed-status");
        report.put(id + "_live_screen", "Same non-pausing ritual screen stays open through the real cast; in-progress text replaces consumed-resource blockers; completion refreshes automatically within40clientticks of observed server completion.");
    }

    private void readAllDetails(final ClientGameTestContext context, final String id) throws Exception {
        while (context.computeOnClient(client -> (int) field(casting(client.gui.screen()), "detailPage")) > 0) {
            ManualClientAcceptance.clickButton(context, "\u2039");
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
                check((int) field(casting(screen), "detailPage") == page, "Native detail button advances exactly one page");
                final var layout = observedBookLayout(screen);
                final var pages = casting(screen).pages(layout);
                final var lines = pages.get(page);
                final int capacity = Math.max(1, (layout.controlTop() - 105 - layout.bodyTextTop()) / ManualTypography.BODY_LINE_HEIGHT);
                check(lines.size() <= capacity, "Every displayed requirement stays above the book casting controls");
                lines.forEach(line -> line.accept((index, style, codePoint) -> { seen.appendCodePoint(codePoint); return true; }));
                return page + 1 < pages.size();
            });
            screenshot(context, id + "-details-" + count);
            if (!more) break;
            ManualClientAcceptance.clickButton(context, "\u203a");
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

    private static int outputItemCount(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        return dropped(player, item).stream().mapToInt(entity -> entity.getItem().getCount()).sum()
            + player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private void requireBoundOutput(final String id, final net.minecraft.world.item.Item item, final int expected,
                                    final java.util.function.BiPredicate<ServerPlayer, ItemStack> matches,
                                    final String description) {
        final Map<String, Integer> observed = observeBoundOutput(id, item, matches);
        // The live casting screen does not pause pickup or merging. Observe actual
        // item quantities in both legitimate locations; never create or move output.
        check(observed.get("dropped") + observed.get("inventory") == expected
                && observed.get("matching") == expected,
            id + " " + description + ": expected=" + expected + ", observed=" + observed);
    }

    private Map<String, Integer> observeBoundOutput(final String id, final net.minecraft.world.item.Item item,
                                                    final java.util.function.BiPredicate<ServerPlayer, ItemStack> matches) {
        final Map<String, Integer> observed = serverValue(player -> {
            final var onGround = dropped(player, item).stream().map(ItemEntity::getItem).toList();
            final var inInventory = player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).toList();
            return Map.of(
                "dropped", onGround.stream().mapToInt(ItemStack::getCount).sum(),
                "inventory", inInventory.stream().mapToInt(ItemStack::getCount).sum(),
                "matching", java.util.stream.Stream.concat(onGround.stream(), inInventory.stream())
                    .filter(stack -> matches.test(player, stack)).mapToInt(ItemStack::getCount).sum());
        });
        report.put(id + "_bound_output_observation", observed);
        return observed;
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
        for (int step = 0; step < 2 && context.computeOnClient(client -> client.gui.screen() != null); step++) {
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
            context.waitTicks(2);
        }
        context.waitFor(client -> client.gui.screen() == null);
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
