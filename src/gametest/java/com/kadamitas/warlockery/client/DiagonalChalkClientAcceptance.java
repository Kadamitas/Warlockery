package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class DiagonalChalkClientAcceptance implements FabricClientGameTest {
    private static final BlockPos CENTER = new BlockPos(0, 100, 0);
    private static final Map<String, BlockPos> DIRECTIONS = Map.of(
        "north", new BlockPos(0, 0, -1), "north_east", new BlockPos(1, 0, -1),
        "east", new BlockPos(1, 0, 0), "south_east", new BlockPos(1, 0, 1),
        "south", new BlockPos(0, 0, 1), "south_west", new BlockPos(-1, 0, 1),
        "west", new BlockPos(-1, 0, 0), "north_west", new BlockPos(-1, 0, -1));
    private final List<String> checks = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("diagonal-chalk").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                try {
                    for (String glyph : List.of("circleglyphritual", "circleglyphgolden",
                            "circleglyphinfernal", "circleglyph_veil")) {
                        resetArena();
                        draw(context, CENTER, "circle");
                        final List<BlockPos> ring = ChalkCircleLayout.Size.LARGE.offsets().stream()
                            .map(CENTER::offset).toList();
                        for (BlockPos pos : ring) {
                            draw(context, pos, glyph);
                            assertConnections(context);
                        }
                        check(serverValue(player -> ChalkCircleLayout.present(player.level(), CENTER,
                            new ChalkCircleLayout.Ring(glyph, ChalkCircleLayout.Size.LARGE))) == ring.size(),
                            "Native drawing preserves the complete canonical ritual footprint for " + glyph);
                        checks.add(glyph + ": complete native ring, every server/client cardinal and diagonal connection, unchanged ritual footprint");
                        overview(context, glyph + "-complete-ring");
                    }
                    resetArena();
                    draw(context, CENTER, "circleglyphritual");
                    final List<String> colors = List.of("circle", "circleglyphgolden", "circleglyphinfernal", "circleglyph_veil");
                    final List<BlockPos> corners = List.of(CENTER.offset(1, 0, -1), CENTER.offset(1, 0, 1),
                        CENTER.offset(-1, 0, 1), CENTER.offset(-1, 0, -1));
                    for (int i = 0; i < corners.size(); i++) {
                        draw(context, corners.get(i), colors.get(i));
                        assertConnections(context);
                    }
                    overview(context, "mixed-colors-four-diagonals");
                    erase(context, corners.getFirst());
                    assertConnections(context);
                    overview(context, "erased-north-east-disconnects-both-ends");
                    draw(context, corners.getFirst(), "circleglyphritual");
                    assertConnections(context);
                    recolor(context, CENTER, "circleglyphinfernal");
                    assertConnections(context);
                    overview(context, "recolored-center-preserves-diagonals");
                    erase(context, CENTER);
                    assertConnections(context);
                    overview(context, "erased-center-clears-all-four-corners");
                    checks.add("All five glyph IDs connect across mixed colors; native corner and center erasure remove reciprocal arms; recoloring preserves connections");
                    verifyConsecutiveDiagonalSeams(context);
                    verifyEditableLines(context);
                    report.put("passed", true);
                } catch (Throwable failure) {
                    report.put("passed", false);
                    report.put("failure", failure.toString());
                    screenshot(context, "failure-in-world");
                    throw failure;
                }
            }
            writeReport();
            System.out.println("WARLOCKERY_DIAGONAL_CHALK_PASS " + evidence);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            try { writeReport(); } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Diagonal chalk evidence: " + evidence, failure);
        }
    }

    private void resetArena() {
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.stopRiding();
            player.removeAllEffects();
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            for (BlockPos pos : BlockPos.betweenClosed(-10, 99, -10, 10, 108, 10)) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(0.5, 100, 0.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
    }

    private void verifyEditableLines(final ClientGameTestContext context) throws Exception {
        resetArena();
        draw(context, CENTER, "circleglyphritual");
        for (final Side side : Side.values()) {
            draw(context, CENTER.offset(side.dx(), 0, side.dz()), "circleglyphritual");
        }
        assertConnections(context);
        emptyMainHand(context);
        overview(context, "thin-all-eight-pronounced-node-straight-diagonals");
        clickLine(context, CENTER, 0.5, 0.5);
        assertConnections(context);
        overview(context, "center-node-click-preserves-all-eight");
        for (final Side side : Side.values()) {
            final BlockPos neighbor = CENTER.offset(side.dx(), 0, side.dz());
            final Side opposite = side.rotateQuarterTurns(2);
            for (final double distance : new double[]{0.15, 0.3, 0.48}) {
                clickLine(context, CENTER, 0.5 + side.dx() * distance, 0.5 + side.dz() * distance);
                assertEdge(context, CENTER, side, false);
                assertEdge(context, neighbor, opposite, false);
                for (final Side other : Side.values()) {
                    if (other != side) assertEdge(context, CENTER, other, true);
                }
                if (distance == 0.3 && (side == Side.EAST || side == Side.SOUTH_EAST)) {
                    overview(context, side.id() + "-one-line-hidden-other-branches-intact");
                }
                clickLine(context, neighbor, 0.5 + opposite.dx() * distance, 0.5 + opposite.dz() * distance);
                assertEdge(context, CENTER, side, true);
                assertEdge(context, neighbor, opposite, true);
            }
        }
        checks.add("Native empty-hand clicks edit one reciprocal edge at three points on all eight arms; either endpoint reconnects its hidden half; node click and unrelated branches remain unchanged");
        overview(context, "all-eight-restored-from-opposite-halves");
        resetArena();
        draw(context, CENTER, "circleglyphritual");
        draw(context, CENTER.south().east(), "circleglyphritual");
        draw(context, CENTER.east(), "circleglyphgolden");
        draw(context, CENTER.south(), "circleglyphgolden");
        emptyMainHand(context);
        clickLine(context, CENTER, 0.8, 0.5);
        clickLine(context, CENTER, 0.5, 0.8);
        clickLine(context, CENTER.south().east(), 0.2, 0.5);
        clickLine(context, CENTER.south().east(), 0.5, 0.2);
        assertEdge(context, CENTER, Side.SOUTH_EAST, true);
        assertEdge(context, CENTER.east(), Side.SOUTH_WEST, true);
        overview(context, "different-colors-cross-as-independent-straight-lines");
        closeup(context, new Vec3(1, 100.03, 1), "mixed-crossing-closeup-both-lines");
        clickLine(context, CENTER, 0.98, 0.98);
        assertEdge(context, CENTER, Side.SOUTH_EAST, false);
        assertEdge(context, CENTER.south().east(), Side.NORTH_WEST, false);
        assertEdge(context, CENTER.east(), Side.SOUTH_WEST, true);
        assertEdge(context, CENTER.south(), Side.NORTH_EAST, true);
        overview(context, "white-crossing-hidden-golden-crossing-intact");
        clickLine(context, CENTER.south().east(), 0.02, 0.02);
        assertEdge(context, CENTER, Side.SOUTH_EAST, true);
        clickLine(context, CENTER.east(), 0.02, 0.98);
        assertEdge(context, CENTER.east(), Side.SOUTH_WEST, false);
        assertEdge(context, CENTER.south(), Side.NORTH_EAST, false);
        assertEdge(context, CENTER, Side.SOUTH_EAST, true);
        overview(context, "golden-crossing-hidden-white-crossing-intact");
        clickLine(context, CENTER.south(), 0.98, 0.02);
        assertEdge(context, CENTER.east(), Side.SOUTH_WEST, true);
        overview(context, "both-colored-crossings-restored");
        checks.add("Native clicks independently hide and restore both different-colored diagonal crossing edges from either half without changing the other crossing");
    }

    private void verifyConsecutiveDiagonalSeams(final ClientGameTestContext context) throws Exception {
        resetArena();
        for (int step = -2; step <= 2; step++) draw(context, CENTER.offset(step, 0, step), "circleglyphritual");
        assertConnections(context);
        emptyMainHand(context);
        closeup(context, new Vec3(1, 100.03, 1), "consecutive-diagonal-shared-corner-closeup");
        closeup(context, new Vec3(0.5, 100.03, 0.5), "consecutive-diagonal-center-node-closeup");
        overview(context, "five-consecutive-native-diagonal-marks");
        checks.add("Five consecutive diagonal marks drawn natively; shared-corner and center-node closeups captured for continuity review");
    }

    private void closeup(final ClientGameTestContext context, final Vec3 target, final String name) throws Exception {
        position(context, new Vec3(target.x, 101.25, target.z - 1.25));
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(5);
        screenshot(context, name);
    }

    private void emptyMainHand(final ClientGameTestContext context) {
        server(player -> {
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
    }

    private void clickLine(final ClientGameTestContext context, final BlockPos pos, final double x, final double z) {
        position(context, new Vec3(pos.getX() + x, pos.getY(), pos.getZ() + z));
        aimDown(context, pos);
        check(context.computeOnClient(client -> client.player.getMainHandItem().isEmpty()), "Connection editing uses a genuinely empty main hand");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        world.getConnection().waitForClientboundPackets();
    }

    private void assertEdge(final ClientGameTestContext context, final BlockPos pos, final Side side, final boolean connected) {
        final BooleanProperty property = ConnectedGlyphBlock.ALL_CONNECTIONS.get(side);
        check(serverValue(player -> player.level().getBlockState(pos).getValue(property)) == connected,
            "Native click sets server " + side.id() + " to " + connected + " at " + pos);
        context.waitFor(client -> client.level.getBlockState(pos).getValue(property) == connected, 30);
    }

    private void draw(final ClientGameTestContext context, final BlockPos pos, final String glyph) {
        check(serverValue(player -> player.level().getBlockState(pos).isAir()), "Drawing target is empty: " + pos);
        useChalk(context, pos, glyph, false);
    }

    private void recolor(final ClientGameTestContext context, final BlockPos pos, final String glyph) {
        check(serverValue(player -> player.level().getBlockState(pos).getBlock() instanceof ConnectedGlyphBlock),
            "Recoloring target is existing chalk");
        useChalk(context, pos, glyph, true);
    }

    private void useChalk(final ClientGameTestContext context, final BlockPos pos, final String glyph, final boolean recolor) {
        final String item = switch (glyph) {
            case "circle", "circleglyphgolden" -> "chalkheart";
            case "circleglyphritual" -> "chalkritual";
            case "circleglyphinfernal" -> "chalkinfernal";
            case "circleglyph_veil" -> "chalk_veil";
            default -> throw new AssertionError(glyph);
        };
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(item).get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        position(context, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (glyph.equals("circleglyphgolden")) context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        try {
            aimDown(context, recolor ? pos : pos.below());
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(client -> client.level.getBlockState(pos).is(ModBlocks.ALL.get(glyph).get()), 30);
            check(serverValue(player -> player.level().getBlockState(pos).is(ModBlocks.ALL.get(glyph).get())),
                "Native chalk use places the server glyph " + glyph);
            check(serverValue(player -> player.getMainHandItem().getDamageValue()) == 1,
                "Native Survival drawing consumes exactly one chalk use");
        } finally {
            if (glyph.equals("circleglyphgolden")) context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        }
    }

    private void erase(final ClientGameTestContext context, final BlockPos pos) {
        position(context, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        aimDown(context, pos);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        for (int tick = 0; tick < 30 && !serverValue(player -> player.level().getBlockState(pos).isAir()); tick++) {
            context.waitTicks(1);
        }
        context.waitFor(client -> client.level.getBlockState(pos).isAir(), 30);
        check(serverValue(player -> player.level().getBlockState(pos).isAir()), "Native mining erases chalk on the server");
    }

    private static void aimDown(final ClientGameTestContext context, final BlockPos expected) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
            && hit.getBlockPos().equals(expected)), "Native ray targets " + expected);
    }

    private void assertConnections(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        check(serverValue(player -> connectionsMatch(player.level())), "Server has exactly the physically adjacent glyph connections");
        context.waitFor(client -> connectionsMatch(client.level), 30);
    }

    private static boolean connectionsMatch(final BlockGetter level) {
        for (BlockPos pos : BlockPos.betweenClosed(-8, 100, -8, 8, 100, 8)) {
            final var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ConnectedGlyphBlock)) continue;
            for (var entry : DIRECTIONS.entrySet()) {
                final var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                check(property instanceof BooleanProperty, "Glyph exposes direction " + entry.getKey());
                final boolean expected = level.getBlockState(pos.offset(entry.getValue())).getBlock() instanceof ConnectedGlyphBlock;
                if (state.getValue((BooleanProperty) property) != expected) return false;
            }
        }
        return true;
    }

    private void overview(final ClientGameTestContext context, final String name) throws Exception {
        position(context, name.endsWith("complete-ring")
            ? new Vec3(0.5, 113, -9.5) : new Vec3(0.5, 104, -3.5));
        context.runOnClient(client -> {
            final Vec3 delta = new Vec3(0.5, 100.03, 0.5).subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(5);
        screenshot(context, name);
    }

    private void position(final ClientGameTestContext context, final Vec3 pos) {
        server(player -> { player.setDeltaMovement(Vec3.ZERO); player.teleportTo(pos.x, pos.y, pos.z); });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.player.position().distanceToSqr(pos) < 0.01, 30);
        context.waitTicks(2);
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void writeReport() throws Exception {
        report.put("execution", "Rendered Fabric client; native Survival right-clicks draw, recolor and toggle individual connections; native left-clicks erase. Both halves of eight directions and different-colored diagonal crossings are exercised. Terrain, camera positions and chalk supplies are staged; glyph placement and connection outcomes are never injected.");
        report.put("checks", checks);
        report.put("screenshots", screenshots);
        report.put("visual_review", "Screenshots require separate inspection for gaps, color readability and texture defects.");
        Files.writeString(evidence.resolve("diagonal-chalk.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
