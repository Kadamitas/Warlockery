package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.DollHexAction;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;

/** Native extension of AllItems: hostile protection, remaining hex actions and repair exhaustion. */
public final class GuardDollAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> CASES = List.of("hex_guard_prick", "hexing_shove", "hexing_ignite", "hexing_drown",
        "tool_mending_exhaustion", "armor_mending_exhaustion", "doll_guard_corruption");
    private final Map<String, Object> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private UUID target;
    private boolean completed;

    @Override public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("guard-doll-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            String configured = System.getProperty("warlockery.guardDollCases", "");
            Set<String> selected = configured.isBlank() ? Set.copyOf(CASES) : Set.copyOf(Arrays.asList(configured.split(",")));
            check(CASES.containsAll(selected), "Known guard doll case selector");
            for (String id : CASES) {
                Map<String, Object> row = new LinkedHashMap<>(); results.put(id, row);
                row.put("status", selected.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
            }
            write();
            for (String id : CASES) {
                @SuppressWarnings("unchecked") final Map<String, Object> row = (Map<String, Object>) results.get(id);
                if (!selected.contains(id)) continue;
                row.put("status", "RUNNING"); write();
                if (id.equals("doll_guard_corruption")) {
                    try {
                        guardedCorruption(context, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", failure.toString()); failures.add(id + ": " + failure);
                    } finally { write(); }
                    continue;
                }
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    world.getConnection().waitForChunksRender();
                    reset(context);
                    try {
                        if (id.contains("mending")) mend(context, id, row);
                        else hex(context, id, row);
                        ManualClientAcceptance.saveScreenshot(context, evidence, id + "-native-outcome", screenshots);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", failure.toString()); failures.add(id + ": " + failure);
                        ManualClientAcceptance.saveScreenshot(context, evidence, id + "-failure", screenshots);
                    } finally {
                        if (target != null) server(player -> {
                            ServerPlayer fixture = player.level().getServer().getPlayerList().getPlayer(target);
                            if (fixture != null) player.level().getServer().getPlayerList().remove(fixture);
                        });
                        target = null;
                        write();
                    }
                }
            }
            completed = true; write(); check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_GUARD_DOLL_ABILITIES_PASS " + evidence);
        } catch (Throwable failure) {
            failures.add("suite: " + failure);
            try { write(); } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Guard doll evidence: " + evidence, failure);
        }
    }
    private void guardedCorruption(ClientGameTestContext context, Map<String, Object> row) throws Exception {
        final Path nested = evidence.resolve("doll-guard-native-ritual").toAbsolutePath();
        final Map<String, String> prior = new LinkedHashMap<>();
        for (String key : List.of("warlockery.clientEvidence", "warlockery.ritualIds", "warlockery.ritualDollGuard"))
            prior.put(key, System.getProperty(key));
        row.put("fixture", "Existing native ritual walkthrough stages a bound Earth Guard and Doll Guard as prerequisites. Actual native chalk, offerings and Begin Rite invoke Corrupt Doll; production must preserve Earth Guard and spend exactly one Doll Guard charge. Binding is separately covered by AllItems.");
        row.put("nested_evidence_directory", nested.toString());
        try {
            System.setProperty("warlockery.clientEvidence", nested.toString());
            System.setProperty("warlockery.ritualIds", "corrupt_doll");
            System.setProperty("warlockery.ritualDollGuard", "true");
            new RitualWalkthroughAcceptance().runTest(context);
            final List<Path> receipts;
            try (var files = Files.walk(nested)) {
                receipts = files.filter(path -> path.getFileName().toString().equals("ritual-walkthrough.json")).toList();
            }
            check(receipts.size() == 1, "Exactly one fresh guarded-corruption native receipt");
            final Path receipt = receipts.getFirst();
            final var recorded = com.google.gson.JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
            check(recorded.get("passed").getAsBoolean(), "Nested ritual walkthrough passes");
            final var selected = recorded.getAsJsonArray("selected_ritual_ids");
            check(selected.size() == 1 && selected.get(0).getAsString().equals("corrupt_doll"), "Nested receipt selects only Corrupt Doll");
            final var result = recorded.getAsJsonObject("ritual_results").getAsJsonObject("corrupt_doll");
            check(result.get("status").getAsString().equals("PASSED"), "Native guarded Corrupt Doll outcome passes");
            row.put("native_ritual_receipt", receipt.toString());
            row.put("native_ritual_result", result);
            row.put("native_ritual_screenshots", recorded.getAsJsonArray("screenshots"));
            row.put("primary", "Native Corrupt Doll leaves Earth Guard unchanged and spends exactly one Doll Guard charge");
        } finally {
            prior.forEach((key, previous) -> {
                if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
            });
        }
    }
    private void reset(ClientGameTestContext context) {
        server(player -> {
            player.level().getServer().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, player.level().getServer());
            for (BlockPos p : BlockPos.betweenClosed(-10, 99, -10, 10, 106, 10))
                player.level().setBlockAndUpdate(p, p.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.teleportTo(0.5, 100, -2); player.setHealth(20); player.removeAllEffects();
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20);
        });
        ready(context);
    }
    private void hex(ClientGameTestContext context, String id, Map<String, Object> row) {
        target = value(player -> {
            var server = player.level().getServer();
            var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GuardFixture");
            var other = new ServerPlayer(server, player.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, other, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            other.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            other.setGameMode(GameType.SURVIVAL); other.teleportTo(0.5, 100, 0.5); other.setHealth(20);
            return other.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getPlayerByUUID(target) != null, 60);
        if (id.equals("hex_guard_prick")) {
            equip(context, "hex_guard_doll"); bindTarget(context);
            server(player -> { victim(player).getInventory().setItem(9, player.getMainHandItem().copy()); player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); });
        }
        equip(context, "hexing_doll"); bindTarget(context);
        DollHexAction action = id.equals("hex_guard_prick") ? DollHexAction.PRICK : DollHexAction.valueOf(id.substring(7).toUpperCase(Locale.ROOT));
        for (int i = 0; i < action.ordinal(); i++) {
            air(context, true);
            final DollHexAction expected = DollHexAction.values()[i + 1];
            await(context, player -> DollItem.hexAction(player.getMainHandItem()) == expected, "Native action cycling");
        }
        server(player -> player.teleportTo(0.5, 100, -8));
        ready(context);
        Vec3 before = value(player -> victim(player).position());
        air(context, false);
        await(context, player -> player.getMainHandItem().getDamageValue() == 1, "Native remote hex spends exactly one charge");
        if (id.equals("hex_guard_prick")) {
            check(value(player -> victim(player).getHealth() == 20 && victim(player).getInventory().getItem(9).getDamageValue() == 1),
                "Bound Hex Guard blocks damaging prick and spends one charge");
            // A second native attack without the guard is the positive damage control.
            server(player -> { victim(player).getInventory().setItem(9, ItemStack.EMPTY); player.clearFire(); player.setHealth(20); });
            context.waitTicks(25); air(context, false);
            await(context, player -> victim(player).getHealth() <= 16 && player.getMainHandItem().getDamageValue() == 2,
                "Same native prick damages an unprotected target");
            row.put("primary", "Protected prick costs guard one charge; removing guard allows four damage from native second prick");
        } else {
            await(context, player -> switch (action) {
                case SHOVE -> victim(player).position().distanceToSqr(before) > 0.1 || victim(player).getDeltaMovement().lengthSqr() > 0.1;
                case IGNITE -> victim(player).isOnFire();
                case DROWN -> victim(player).getAirSupply() < 250;
                default -> false;
            }, "Native selected remote hex changes its actual target");
            row.put("primary", action.id());
        }
        row.put("fixture", "Rendered Survival attacker; connected synthetic ServerPlayer target. Both protective and hexing dolls bound by native entity interaction; guard transferred to bound target as a prerequisite. Real multiplayer transport is not certified.");
    }
    private void mend(ClientGameTestContext context, String id, Map<String, Object> row) {
        boolean armor = id.startsWith("armor");
        equip(context, armor ? "armor_mending_doll" : "tool_mending_doll"); air(context, false);
        await(context, player -> DollItem.isBoundTo(player.getMainHandItem(), player), "Native self binding");
        EquipmentSlot slot = armor ? EquipmentSlot.CHEST : EquipmentSlot.OFFHAND;
        server(player -> {
            player.getMainHandItem().setDamageValue(player.getMainHandItem().getMaxDamage() - 1);
            ItemStack damaged = new ItemStack(armor ? Items.IRON_CHESTPLATE : Items.IRON_PICKAXE); damaged.setDamageValue(5);
            player.setItemSlot(slot, damaged); player.inventoryMenu.broadcastChanges();
        });
        await(context, player -> player.getMainHandItem().isEmpty(), "Last scheduled mending charge exhausts doll");
        check(value(player -> player.getItemBySlot(slot).getDamageValue() == 3), "Final charge repairs exactly two durability");
        context.waitTicks(45);
        check(value(player -> player.getItemBySlot(slot).getDamageValue() == 3), "No repairs occur after doll exhaustion");
        row.put("fixture", "Native self binding; one remaining doll charge and five damaged equipment points staged; ordinary inventory ticks alone repair and exhaust");
    }
    private ServerPlayer victim(ServerPlayer player) {
        return Objects.requireNonNull(player.level().getServer().getPlayerList().getPlayer(target));
    }
    private void equip(ClientGameTestContext context, String id) {
        server(player -> { player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ALL.get(id).get())); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(5);
    }
    private void bindTarget(ClientGameTestContext context) {
        ready(context);
        context.runOnClient(client -> {
            Vec3 delta = client.level.getPlayerByUUID(target).getEyePosition().subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        }); context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(target)), "Native pointer selects intended bound player");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(target)).isPresent(), "Native entity binding");
    }
    private void air(ClientGameTestContext context, boolean secondary) {
        ready(context);
        if (secondary) context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
        try {
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-60); }); context.waitTicks(5);
            check(context.computeOnClient(client -> client.hitResult != null && client.hitResult.getType() == HitResult.Type.MISS), "Native air-use has no entity or block hit");
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        } finally { if (secondary) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); }
    }
    private void ready(ClientGameTestContext context) {
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), "Native player loaded and teleport acknowledged");
        world.getConnection().waitForClientboundPackets(); context.waitTicks(5);
    }
    private static boolean awaitingTeleport(ServerPlayer player) {
        try {
            var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true); return field.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void await(ClientGameTestContext context, Predicate<ServerPlayer> condition, String message) {
        for (int i = 0; i < 80 && !value(condition::test); i++) context.waitTicks(1);
        check(value(condition::test), message); world.getConnection().waitForClientboundPackets();
    }
    private void server(Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T value(Function<ServerPlayer, T> action) {
        AtomicReference<T> result = new AtomicReference<>(); server(player -> result.set(action.apply(player))); return result.get();
    }
    private void write() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cases", results); report.put("screenshots", screenshots); report.put("failures", failures);
        report.put("remaining", List.of("Doll Guard loaded shelf, multiple targets and exhausted guard branches", "Hex Guard Heat Metal and other ritual curse variants, multiple guard requirements, suppression and loaded shelf", "Hexing acquisition, range/dimension and target-refusal branches", "Mending duplicate-doll scheduling, full equipment refusal, shelves and suppression", "Real second-client networking and save/reload"));
        report.put("completed", completed);
        final long selected = results.values().stream().filter(row -> !"NOT_SELECTED".equals(((Map<?, ?>) row).get("status"))).count();
        final long passed = results.values().stream().filter(row -> "PASSED".equals(((Map<?, ?>) row).get("status"))).count();
        report.put("selected_cases", selected); report.put("passed_cases", passed);
        report.put("passed", completed && failures.isEmpty() && selected > 0 && passed == selected);
        report.put("all_item_contracts_complete", false);
        Files.writeString(evidence.resolve("guard-doll-abilities.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
