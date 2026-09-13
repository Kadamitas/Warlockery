package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.dream.SpiritWorldState;
import com.kadamitas.warlockery.dream.SpiritManifestationState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
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
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class SpiritTravelClientAcceptance implements FabricClientGameTest {
    private static final BlockPos HEART = new BlockPos(0, 100, 3);
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("spirit-travel").resolve(UUID.randomUUID().toString());
        report.put("passed", false);
        report.put("manifestation_permission_scope", "Timed permission is staged solely as the portal ability prerequisite. Native ritual granting to a bound sleeper is covered independently by RitualWalkthroughAcceptance; these are combined behavior checks, not end-to-end multiplayer acquisition.");
        report.put("fixture_scope", "Survival inventory, hunger, invulnerability against random nightmare combat, construction supports, within-dimension camera positions and timed manifestation permission are staged prerequisites. Dream entry, manifestation session creation, inventory transfer, needle consumption, snow placement, fluid placement, portal creation and travel use native inputs.");
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                awaitArrival(context);
                arena();
                readGuide(context, "ingredient_book_burning", "spirit_world_entry");
                readGuide(context, "ingredient_book_burning", "spirit_world_laws");
                readGuide(context, "ingredient_book_herbology", "ingredient_icy_needle");
                readGuide(context, "cauldronbook", "bucketspirit");
                readGuide(context, "ingredient_book_circle_magic", "rite_manifestation");
                enterDream(context);
                select(context, 1);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                waitServer(context, player -> !SpiritWorldState.active(player), 80,
                    "Native Icy Needle use must wake the dreamer");
                check(serverValue(player -> count(player, ModItems.ALL.get("ingredient_icy_needle").get()) == 2
                    && player.getInventory().getItem(2).is(Items.DIAMOND)
                    && player.getInventory().getItem(2).getCount() == 7),
                    "Waking must restore seven original diamonds and exactly two unspent needles");
                check(serverValue(SpiritTravelClientAcceptance::keepsCottonAndCatalysts),
                    "Both catalysts and both cotton types survive entry and wake with their exact original counts");
                report.put("icy_needle_wake", "PASSED: native apple entry, carry-in filtering, one needle consumed, original inventory restored");
                screenshot(context, "icy-needle-wake");
                writeReport();
                enterDream(context);
                constructPortal(context);
                report.put("frame_creation", "PASSED: eight snow blocks placed with native input, Spirit Bucket poured, four portal blocks created");
                screenshot(context, "constructed-spirit-portal");
                writeReport();
                server(player -> {
                    player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 5));
                    player.inventoryMenu.broadcastChanges();
                    SpiritManifestationState.grant(player, player.level().getServer().getTickCount() + 3000);
                    player.teleportTo(0.5, 100, 1.5);
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
                context.getInput().holdKey(GLFW.GLFW_KEY_W);
                try {
                    waitServer(context, SpiritManifestationState::active, 60,
                        "Native forward movement into the constructed portal must begin manifestation");
                } finally {
                    context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                }
                awaitArrival(context);
                check(serverValue(player -> SpiritWorldState.active(player)
                    && player.level().dimension().identifier().equals(SpiritWorldState.read(player).orElseThrow().sourceDimension())
                    && count(player, ModItems.ALL.get("ingredient_icy_needle").get()) == 3
                    && keepsCottonAndCatalysts(player)
                    && player.getInventory().getNonEquipmentItems().stream().allMatch(stack -> stack.isEmpty()
                        || stack.is(com.kadamitas.warlockery.registry.WarlockeryTags.Items.SPIRIT_WORLD_EXPORTS))),
                    "Manifestation must carry the permitted cotton, both catalysts and three needles, leaving ordinary supplies in the dream");
                screenshot(context, "portal-manifestation");
                useNeedle(context);
                waitServer(context, player -> SpiritWorldState.active(player) && !SpiritManifestationState.active(player)
                    && SpiritWorldRuntime.isSpiritWorld(player.level(), player), 80,
                    "Native Icy Needle use must return the manifestation to its dream without waking");
                check(serverValue(player -> count(player, Items.IRON_INGOT) == 5 && count(player, Items.BUCKET) == 1
                    && count(player, ModItems.ALL.get("ingredient_icy_needle").get()) == 2
                    && count(player, Items.DIAMOND) == 0),
                    "Manifestation return restores dream inventory and retains exactly two needles");
                screenshot(context, "needle-manifestation-return");
                useNeedle(context);
                waitServer(context, player -> !SpiritWorldState.active(player), 80,
                    "A second native needle use must wake the returned dreamer");
                check(serverValue(player -> count(player, Items.DIAMOND) == 7 && count(player, Items.IRON_INGOT) == 0
                    && count(player, Items.BUCKET) == 0 && count(player, ModItems.ALL.get("ingredient_icy_needle").get()) == 1),
                    "Waking restores original belongings, exports one surviving needle, and excludes staged ordinary dream items");
                report.put("manifestation_return", "PASSED: native portal contact manifested; first needle restored dream inventory; second needle woke and restored original inventory, with exact consumption and no ordinary item exports");
                screenshot(context, "second-needle-wake");
                writeReport();
                enterDream(context);
                final BlockPos returnPortal = serverValue(player -> SpiritWorldState.read(player).orElseThrow().portal());
                server(player -> {
                    for (BlockPos pos : BlockPos.betweenClosed(returnPortal.offset(-1, -1, -3), returnPortal.offset(1, -1, 0))) {
                        player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    }
                    for (BlockPos pos : BlockPos.betweenClosed(returnPortal.offset(-1, 0, -3), returnPortal.offset(1, 2, -1))) {
                        player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                    player.teleportTo(returnPortal.getX() + 0.5, returnPortal.getY(), returnPortal.getZ() - 2.5);
                });
                world.getConnection().waitForClientboundPackets();
                select(context, 3);
                look(context, Vec3.atCenterOf(returnPortal));
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                waitServer(context, player -> !SpiritWorldState.active(player), 60,
                    "The Silver Cord guide promises that using a Spirit Portal wakes a dreamer without manifestation permission");
                report.put("portal_wake", "PASSED");
                screenshot(context, "portal-wake");
                report.put("passed", true);
                writeReport();
            }
            System.out.println("WARLOCKERY_SPIRIT_TRAVEL_PASSED " + evidence);
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            try {
                screenshot(context, "failure");
                writeReport();
            } catch (Throwable capture) {
                failure.addSuppressed(capture);
            }
            throw new AssertionError("Spirit travel evidence: " + evidence, failure);
        }
    }

    private void useNeedle(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        final int slot = serverValue(player -> java.util.stream.IntStream.range(0, 9)
            .filter(index -> player.getInventory().getItem(index).is(ModItems.ALL.get("ingredient_icy_needle").get()))
            .findFirst().orElseThrow(() -> new AssertionError("A surviving needle must remain in the hotbar")));
        select(context, slot);
        context.runOnClient(client -> client.player.setXRot(-60));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    private static int count(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item))
            .mapToInt(ItemStack::getCount).sum();
    }

    private static boolean keepsCottonAndCatalysts(final ServerPlayer player) {
        return count(player, ModItems.ALL.get("ingredient_verdant_catalyst").get()) == 2
            && count(player, ModItems.ALL.get("ingredient_verdant_catalyst_prime").get()) == 1
            && count(player, ModBlocks.ALL.get("somniancotton").get().asItem()) == 4
            && count(player, ModItems.ALL.get("ingredient_disturbed_cotton").get()) == 3;
    }

    private void enterDream(final ClientGameTestContext context) throws Exception {
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(true);
            player.getFoodData().setFoodLevel(10);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_sleeping_apple").get()));
            player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get(), 3));
            player.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 7));
            player.getInventory().setItem(5, new ItemStack(ModItems.ALL.get("ingredient_verdant_catalyst").get(), 2));
            player.getInventory().setItem(6, new ItemStack(ModItems.ALL.get("ingredient_verdant_catalyst_prime").get()));
            player.getInventory().setItem(7, new ItemStack(ModBlocks.ALL.get("somniancotton").get().asItem(), 4));
            player.getInventory().setItem(8, new ItemStack(ModItems.ALL.get("ingredient_disturbed_cotton").get(), 3));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        select(context, 0);
        context.runOnClient(client -> client.player.setXRot(-40));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            waitServer(context, SpiritWorldState::active, 100, "Eating the actual Sleeping Apple must begin a dream session");
        } finally {
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }
        awaitArrival(context);
        check(serverValue(player -> SpiritWorldRuntime.isSpiritWorld(player.level(), player)
            && player.getInventory().getItem(1).getCount() == 3
            && keepsCottonAndCatalysts(player)
            && player.getInventory().getItem(2).isEmpty()),
            "Actual dream entry must travel to the Spirit World with needles while retaining diamonds outside");
        screenshot(context, "sleeping-apple-dream-" + screenshots.size());
    }

    private void constructPortal(final ClientGameTestContext context) {
        arena();
        final List<BlockPos> frame = List.of(HEART.below(), HEART.east().below(), HEART.above(2),
            HEART.east().above(2), HEART.west(), HEART.west().above(), HEART.east(2), HEART.east(2).above());
        server(player -> {
            frame.forEach(pos -> player.level().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState()));
            player.getInventory().setItem(0, new ItemStack(Items.SNOW_BLOCK, 8));
            player.getInventory().setItem(2, new ItemStack(ModItems.ALL.get("bucketspirit").get()));
            player.getInventory().setItem(3, ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        select(context, 0);
        for (BlockPos pos : frame) {
            look(context, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 1.001));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.level().getBlockState(pos).is(Blocks.SNOW_BLOCK),
                30, "Native block use must place snow frame member " + pos);
        }
        check(serverValue(player -> player.getInventory().getItem(0).isEmpty()), "Eight native frame placements consume eight snow blocks");
        select(context, 2);
        look(context, Vec3.atCenterOf(HEART.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> List.of(HEART, HEART.east(), HEART.above(), HEART.east().above()).stream()
                .allMatch(pos -> player.level().getBlockState(pos).is(ModBlocks.ALL.get("spiritportal").get()))
                && player.getMainHandItem().is(Items.BUCKET),
            60, "Native Spirit Bucket pour must activate all four portal cells and leave an empty bucket");
    }

    private void arena() {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(-4, 98, -3, 5, 104, 6)) {
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 && pos.getZ() <= 2
                    || pos.getY() == 98 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(0.5, 100, 0.5);
        });
        world.getConnection().waitForClientboundPackets();
    }

    private void readGuide(final ClientGameTestContext context, final String bookId, final String section) throws Exception {
        final ManualProfile book = ManualProfile.find(bookId).orElseThrow();
        check(book.sections().contains(section), "Book must index " + section);
        server(player -> {
            player.getInventory().setItem(8, new ItemStack(ModItems.ALL.get(bookId).get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        select(context, 8);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        report.put(section + "_guide", context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString()));
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            screenshot(context, section + "-guide-" + (page + 1));
        }
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 30);
    }

    private void select(final ClientGameTestContext context, final int slot) {
        awaitArrival(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_1 + slot);
        waitServer(context, player -> player.getInventory().getSelectedSlot() == slot, 30, "Native hotbar selection " + slot);
    }

    private void awaitArrival(final ClientGameTestContext context) {
        final var dimension = serverValue(player -> player.level().dimension());
        context.waitFor(client -> client.player != null && client.level != null
            && client.level.dimension().equals(dimension) && client.gui.screen() == null, 300);
        world.getConnection().waitForChunksRender();
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int tick = 0; tick < ticks; tick++) {
            if (serverValue(predicate::test)) return;
            context.waitTicks(1);
        }
        if (!serverValue(predicate::test)) {
            report.put("failed_predicate", message);
            report.put("server_position", serverValue(player -> player.position().toString()));
            report.put("server_dimension", serverValue(player -> player.level().dimension().identifier().toString()));
            report.put("client_screen", context.computeOnClient(client -> String.valueOf(client.gui.screen())));
            try { screenshot(context, "failure-in-world"); } catch (Exception ignored) { }
            throw new AssertionError(message);
        }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport() throws Exception {
        report.put("screenshots", screenshots);
        report.put("updated_at", System.currentTimeMillis());
        Files.writeString(evidence.resolve("spirit-travel.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
