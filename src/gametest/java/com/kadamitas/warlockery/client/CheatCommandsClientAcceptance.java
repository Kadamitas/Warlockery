package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualManager;
import net.minecraft.server.level.ServerLevel;
import com.kadamitas.warlockery.world.VillageAssaultData;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public final class CheatCommandsClientAcceptance implements FabricClientGameTest {
    private static final BlockPos CENTER = new BlockPos(0, 100, 0);
    private static final BlockPos ALTAR = new BlockPos(11, 100, 0);
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<Map<String, String>> commands = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("cheat-commands").resolve(UUID.randomUUID().toString());
        report.put("passed", false);
        report.put("scope", "Rendered native chat commands. Gamemaster permission, flat ground, altar blocks, occupied beds, residents and time are prerequisites. No command dispatcher, ritual action, assault begin, wave spawn or successful outcome is called directly. Ordinary/moderator denial is covered separately by WarlockeryTestCommandsTest; this helper does not claim native denied-player coverage or complete assault combat.");
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var created = context.worldBuilder().create()) {
                world = created;
                prepareWorld(context, 16);
                basicCommands(context);
            }
            for (AssaultKind kind : AssaultKind.values()) {
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    prepareWorld(context, 60);
                    assault(context, kind);
                }
            }
            report.put("passed", true);
            writeReport();
            System.out.println("WARLOCKERY_CHEAT_COMMANDS_PASSED " + evidence);
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            try { writeReport(); } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Cheat command evidence: " + evidence, failure);
        }
    }

    private void prepareWorld(final ClientGameTestContext context, final int radius) throws Exception {
        world.getConnection().waitForChunksRender();
        server(player -> {
            final var server = player.level().getServer();
            server.getPlayerList().op(new NameAndId(player.getGameProfile()),
                Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
            server.getPlayerList().sendPlayerPermissionLevel(player);
            player.setGameMode(GameType.SURVIVAL);
            player.setPermanentlyInvulnerable(true);
            player.getInventory().clearContent();
            for (int x = -radius; x <= radius; x += 16) for (int z = -radius; z <= radius; z += 16) {
                player.level().getChunkAt(CENTER.offset(x, 0, z));
            }
            for (BlockPos pos : BlockPos.betweenClosed(CENTER.offset(-radius, -1, -radius), CENTER.offset(radius, 3, radius))) {
                player.level().setBlockAndUpdate(pos, pos.getY() == CENTER.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(0.5, 100, 0.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(20);
        command(context, "/difficulty normal", "Normal");
        check(WarlockeryConfig.villageAssaults(), "Fixture requires villageAssaults enabled");
    }

    private void basicCommands(final ClientGameTestContext context) throws Exception {
        command(context, "/warlockery test help", "Warlockery cheats");
        final var definition = serverValue(player -> RitualManager.INSTANCE.byId(Identifier.parse("warlockery:manifestation"))
            .orElseThrow().definition());
        final Map<BlockPos, String> expected = new LinkedHashMap<>();
        expected.put(CENTER, "circle");
        ChalkCircleLayout.rings(definition.glyphs()).forEach(ring -> ring.size().offsets()
            .forEach(offset -> expected.put(CENTER.offset(offset), ring.glyph())));
        final Map<BlockPos, BlockState> ground = snapshot(CENTER.offset(-8, -1, -8), CENTER.offset(8, -1, 8));
        final BlockPos obstruction = expected.keySet().stream().filter(pos -> !pos.equals(CENTER)).findFirst().orElseThrow();
        server(player -> player.level().setBlockAndUpdate(obstruction, Blocks.OBSIDIAN.defaultBlockState()));
        final var before = snapshot(CENTER.offset(-8, 0, -8), CENTER.offset(8, 1, 8));
        command(context, "/warlockery test circle warlockery:manifestation", "Nothing changed");
        check(before.equals(snapshot(CENTER.offset(-8, 0, -8), CENTER.offset(8, 1, 8))),
            "An obstructed circle command must leave every staged cell unchanged");
        server(player -> player.level().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()));
        command(context, "/warlockery test circle warlockery:manifestation", "Drew warlockery:manifestation");
        check(serverValue(player -> expected.entrySet().stream().allMatch(mark ->
            player.level().getBlockState(mark.getKey()).is(ModBlocks.ALL.get(mark.getValue()).get()))),
            "Native circle command must place every canonical book ring position and functional ritual heart");
        check(serverValue(player -> RitualManager.isCircleCenter((ServerLevel) player.level(), CENTER)),
            "The actual ritual manager must recognize the command-created center as a usable ritual heart");
        check(ground.equals(snapshot(CENTER.offset(-8, -1, -8), CENTER.offset(8, -1, 8))), "Circle command preserves supporting terrain");
        check(snapshot(CENTER.offset(-8, 0, -8), CENTER.offset(8, 0, 8)).entrySet().stream()
            .filter(entry -> !expected.containsKey(entry.getKey())).allMatch(entry -> entry.getValue().isAir()),
            "Circle command must not place extra marks outside the diagram");
        report.put("circle", "PASSED: obstruction left footprint unchanged; positive control placed exact diagram and preserved ground");
        screenshot(context, "circle-native-command");
        command(context, "/warlockery test altar_power full", "No valid loaded altar");
        server(player -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                player.level().setBlockAndUpdate(ALTAR.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
            }
        });
        waitServer(context, player -> altar(player).isMultiblockValid(), 100, "The staged six-block altar must become valid naturally");
        command(context, "/warlockery test altar_power 1000000", "received");
        check(serverValue(player -> poweredAltar(player).getPower() == poweredAltar(player).getCapacity()), "Amount command caps stored altar power");
        command(context, "/warlockery test altar_power full", "received 0 power");
        check(serverValue(player -> poweredAltar(player).getPower() == poweredAltar(player).getCapacity()), "Full command preserves the altar capacity cap");
        report.put("altar_power", "PASSED: missing altar refused; native fill capped valid existing altar; full altar accepted zero extra");
        command(context, "/warlockery test infusion otherwhere", "Granted permanent otherwhere");
        check(serverValue(player -> MagicPathState.has(player, MagicPath.OTHERWHERE)
            && MagicPathState.reserve(player, MagicPath.OTHERWHERE) == MagicPath.OTHERWHERE.maximumReserve()),
            "Native infusion command must grant the named full path");
        final var paths = pathSnapshot();
        command(context, "/warlockery test infusion not_a_path", "Unknown infusion");
        check(paths.equals(pathSnapshot()), "Invalid infusion command must leave all path states and reserves unchanged");
        command(context, "/warlockery test event goblin", "Stand in an inhabited village");
        check(serverValue(player -> VillageAssaultData.get(player.level()).active().isEmpty()), "Outside-village command cannot create an assault");
        report.put("infusion_and_event_refusal", "PASSED: valid path filled, invalid path unchanged, outside-village event rejected");
        screenshot(context, "basic-command-results");
        writeReport();
    }

    private void assault(final ClientGameTestContext context, final AssaultKind kind) throws Exception {
        check(WarlockeryConfig.villageAssaults(), "Fixture requires villageAssaults enabled; no configuration file is silently rewritten");
        command(context, "/difficulty normal", "Normal");
        command(context, "/time set 18000", "18000");
        final BlockPos foot = new BlockPos(4, 100, 4);
        final BlockPos head = foot.east();
        server(player -> {
            final var level = player.level();
            level.setBlockAndUpdate(foot, Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                .setValue(BedBlock.PART, BedPart.FOOT));
            level.setBlockAndUpdate(head, Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                .setValue(BedBlock.PART, BedPart.HEAD));
        });
        context.waitTicks(5);
        server(player -> {
            final var level = player.level();
            final var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
            level.getPoiManager().add(head, home);
            check(level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(head), head, 1).isPresent(),
                "The staged bed must acquire a real occupied HOME ticket");
            for (int index = 0; index < 4; index++) {
                final var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                check(villager != null, "Resident fixture must create a villager");
                villager.setPos(5.5 + index, 100, 6.5);
                villager.setNoAi(true);
                villager.setPermanentlyInvulnerable(true);
                if (index == 0) villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
                level.addFreshEntity(villager);
            }
        });
        waitServer(context, player -> player.level().isVillage(player.blockPosition()), 120, "Occupied home POI must register a real village");
        command(context, "/warlockery test event " + kind.serializedName(), "Started " + kind.serializedName());
        waitServer(context, player -> VillageAssaultData.get(player.level()).active().filter(state -> state.kind() == kind
            && state.wave() >= 1 && !state.raiderIds().isEmpty()
            && state.raiderIds().stream().map(UUID::fromString).map(player.level()::getEntity)
                .anyMatch(entity -> entity != null && entity.isAlive() && VillageAssaultRuntime.isAssaultRaider(entity))).isPresent(),
            160, "Native event command must progress through normal ticks into a first wave with live recorded raiders");
        final var state = serverValue(player -> VillageAssaultData.get(player.level()).active().orElseThrow());
        command(context, "/warlockery test event " + kind.serializedName(), "already active");
        check(serverValue(player -> VillageAssaultData.get(player.level()).active().orElseThrow().center().equals(state.center())
            && VillageAssaultData.get(player.level()).active().orElseThrow().kind() == kind), "Duplicate event command preserves the original assault");
        final Vec3 focus = serverValue(player -> state.raiderIds().stream().map(UUID::fromString).map(player.level()::getEntity)
            .filter(entity -> entity != null && entity.isAlive()).findFirst().orElseThrow().position());
        server(player -> player.teleportTo(focus.x + 5, focus.y + 2, focus.z + 5));
        awaitCamera(context);
        look(context, focus.add(0, 1, 0));
        report.put("event_" + kind.serializedName(), Map.of("status", "PASSED", "wave", state.wave(),
            "recorded_raiders", state.raiderIds(), "center", state.center().toShortString(),
            "scope", "Real staged occupied-bed village; first natural wave only. Residents are stationary and invulnerable; combat objectives, rewards and later waves are not certified."));
        screenshot(context, "event-" + kind.serializedName() + "-first-wave");
        writeReport();
    }

    private void command(final ClientGameTestContext context, final String command, final String expected) throws Exception {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_T);
        context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars(command);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        context.waitFor(client -> client.gui.screen() == null, 40);
        context.waitFor(client -> chat(client.gui.hud.getChat()).contains(expected), 100);
        commands.add(Map.of("input", command, "response", context.computeOnClient(client -> chat(client.gui.hud.getChat()))));
    }

    private static String chat(final net.minecraft.client.gui.components.ChatComponent chat) {
        try {
            final var field = chat.getClass().getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((List<?>) field.get(chat)).stream().map(GuiMessage.class::cast)
                .map(message -> message.content().getString()).collect(java.util.stream.Collectors.joining("\n"));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private Map<String, String> pathSnapshot() {
        return serverValue(player -> {
            final Map<String, String> paths = new LinkedHashMap<>();
            for (MagicPath path : MagicPath.values()) paths.put(path.id(), MagicPathState.has(player, path) + ":" + MagicPathState.reserve(player, path));
            return paths;
        });
    }

    private Map<BlockPos, BlockState> snapshot(final BlockPos from, final BlockPos to) {
        return serverValue(player -> {
            final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
            BlockPos.betweenClosedStream(from, to).forEach(pos -> cells.put(pos.immutable(), player.level().getBlockState(pos)));
            return cells;
        });
    }

    private static AltarBlockEntity altar(final ServerPlayer player) { return (AltarBlockEntity) player.level().getBlockEntity(ALTAR); }
    private static AltarBlockEntity poweredAltar(final ServerPlayer player) { return AltarPowerNetwork.best(player.level(), player.blockPosition()).orElseThrow(); }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }
    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String failure) {
        for (int tick = 0; tick < ticks; tick++) { if (serverValue(predicate::test)) return; context.waitTicks(1); }
        check(serverValue(predicate::test), failure);
    }
    private void awaitCamera(final ClientGameTestContext context) {
        world.getConnection().waitForClientboundPackets();
        waitServer(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), 120,
            "Camera mismatch: native player loading and teleport acknowledgement did not complete");
        for (int tick = 0; tick < 100; tick++) {
            final Vec3 position = serverValue(ServerPlayer::position);
            if (context.computeOnClient(client -> client.player != null
                && client.player.position().distanceToSqr(position) < 0.04)) return;
            context.waitTicks(1);
        }
        throw new AssertionError("Camera mismatch: client position did not converge with the acknowledged server pose; server="
            + serverValue(ServerPlayer::position) + ", client="
            + context.computeOnClient(client -> client.player == null ? "missing" : client.player.position().toString()));
    }
    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var pending = net.minecraft.server.network.ServerGamePacketListenerImpl.class
                .getDeclaredField("awaitingPositionFromClient");
            pending.setAccessible(true);
            return pending.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Camera mismatch: cannot observe native teleport acknowledgement", failure);
        }
    }
    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
    }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private void writeReport() throws Exception {
        report.put("commands", commands);
        report.put("screenshots", screenshots);
        Files.writeString(evidence.resolve("cheat-commands.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
