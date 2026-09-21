package com.kadamitas.warlockery.dream;

import com.kadamitas.warlockery.registry.ModChunkTickets;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.TicketStorage;

public final class SpiritWorldGameTests {
    private SpiritWorldGameTests() {
    }

    public static void entryCreatesStateBodyAndDiagnostic(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.setPermanentlyInvulnerable(true);
        final ServerLevel source = player.level();
        final int initialTicks = player.tickCount;
        final SpiritWorldRuntime.EntryResult entry = SpiritWorldRuntime.enter(player, false);
        helper.assertTrue(entry.entered(), "ordinary dream entry must succeed");
        helper.assertTrue(SpiritWorldRuntime.isSpiritWorld(player.level(), player),
            "dreamer must reach the Spirit World");
        final SpiritWorldState.Session session = SpiritWorldState.read(player).orElseThrow();
        final Entity body = source.getEntity(session.body());
        helper.assertTrue(body != null && SpiritWorldRuntime.isSleepingBody(body),
            "entry must leave a linked sleeping body");
        helper.assertTrue(body instanceof net.minecraft.world.entity.decoration.Mannequin,
            "the body must be a native player avatar rather than a wooden armor stand");
        helper.assertTrue(body.hasPose(net.minecraft.world.entity.Pose.SLEEPING),
            "the native avatar must lie asleep");
        helper.assertValueEqual(((net.minecraft.world.entity.decoration.Mannequin) body).getProfile().partialProfile(),
            player.getGameProfile(), "body skin uses the owner's resolved profile");
        helper.assertValueEqual(body.position(), new net.minecraft.world.phys.Vec3(
            session.sourceX(), session.sourceY(), session.sourceZ()), "body retains the exact entry coordinates");
        helper.assertValueEqual(
            SpiritWorldRuntime.enter(player, false).diagnostic(),
            SpiritWorldRules.EntryDiagnostic.ALREADY_DREAMING,
            "second entry diagnostic"
        );
        final ServerPlayer second = connectedSurvivalPlayer(helper);
        second.setPermanentlyInvulnerable(true);
        second.teleportTo(session.sourceX() + 0.25, session.sourceY(), session.sourceZ());
        final int secondInitialTicks = second.tickCount;
        helper.assertTrue(SpiritWorldRuntime.enter(second, false).entered(), "second dreamer must enter");
        final SpiritWorldState.Session secondSession = SpiritWorldState.read(second).orElseThrow();
        helper.assertFalse(session.body().equals(secondSession.body()), "dreamers must have distinct sleeping bodies");
        helper.assertValueEqual(bodyChunk(session), bodyChunk(secondSession), "sleeping bodies must share one source chunk");
        helper.runAfterDelay(90, () -> {
            assertDreamBodyRetained(helper, source, player, session, initialTicks);
            assertDreamBodyRetained(helper, source, second, secondSession, secondInitialTicks);
            helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL),
                "first dreamer must return through its portal");
            helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "first wake must clear its dream state");
            helper.assertTrue(source.getEntity(session.body()) == null, "first wake must remove its sleeping body");
            helper.assertTrue(hasBodyTicket(source, secondSession), "first wake must retain the other dreamer's body ticket");
            final int secondTicksAfterFirstWake = second.tickCount;
            helper.runAfterDelay(90, () -> {
                assertDreamBodyRetained(helper, source, second, secondSession, secondTicksAfterFirstWake);
                helper.assertTrue(SpiritWorldRuntime.wake(second, SpiritWorldRules.WakeCause.RETURN_PORTAL),
                    "second dreamer must return through its portal");
                helper.assertFalse(SpiritWorldRuntime.isDreaming(second), "second wake must clear its dream state");
                helper.assertTrue(source.getEntity(secondSession.body()) == null, "second wake must remove its sleeping body");
                helper.runAfterDelay(90, () -> {
                    helper.assertFalse(hasBodyTicket(source, session), "body ticket must expire after both dreams end");
                    helper.succeed();
                });
            });
        });
    }

    private static void assertDreamBodyRetained(
        final GameTestHelper helper,
        final ServerLevel source,
        final ServerPlayer player,
        final SpiritWorldState.Session session,
        final int initialTicks
    ) {
        helper.assertTrue(player.tickCount - initialTicks > 80, "connected dreamer must receive more than eighty normal ticks");
        helper.assertTrue(SpiritWorldRuntime.isDreaming(player), "normal ticks must preserve the dream session");
        helper.assertTrue(SpiritWorldRuntime.isSpiritWorld(player.level(), player), "dreamer must remain in its dream destination");
        helper.assertValueEqual(SpiritWorldState.read(player).orElseThrow().body(), session.body(),
            "normal ticks must preserve the original body link");
        final Entity body = source.getEntity(session.body());
        helper.assertTrue(body != null && body.isAlive() && SpiritWorldRuntime.isSleepingBody(body),
            "normal ticks must retain the living sleeping body");
        helper.assertTrue(body.hasPose(net.minecraft.world.entity.Pose.SLEEPING),
            "the body remains lying down on ordinary ticks without a bed block");
        helper.assertValueEqual(body.position(), new net.minecraft.world.phys.Vec3(
            session.sourceX(), session.sourceY(), session.sourceZ()), "sleeping body does not drift from the source");
        helper.assertValueEqual(SpiritWorldRuntime.bodyDreamer(body).orElseThrow(), player.getUUID(),
            "sleeping body must remain linked to its own dreamer");
        helper.assertTrue(hasBodyTicket(source, session), "normal player ticks must refresh the expiring body ticket");
    }

    private static long bodyChunk(final SpiritWorldState.Session session) {
        return ChunkPos.pack(BlockPos.containing(session.sourceX(), session.sourceY(), session.sourceZ()));
    }

    private static boolean hasBodyTicket(final ServerLevel source, final SpiritWorldState.Session session) {
        final TicketStorage tickets = source.getDataStorage().get(TicketStorage.TYPE);
        return tickets != null && tickets.getTickets(bodyChunk(session)).stream()
            .anyMatch(ticket -> ticket.getType() == ModChunkTickets.SLEEPING_BODY.get() && !ticket.isTimedOut());
    }

    public static void carryInAndExportsRestoreWithoutDuplication(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack needle = new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get(), 2);
        final ItemStack cotton = new ItemStack(ModItems.ALL.get("somniancotton").get());
        player.getInventory().setItem(0, needle);
        player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setItem(2, cotton);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_icy_needle").get()), 2,
            "Icy Needles carried into the dream");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 0, "ordinary inventory hidden in dream");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 1,
            "Cotton crosses into the dream without an escrow duplicate");
        player.getInventory().getItem(0).shrink(1);
        player.getInventory().add(new ItemStack(ModItems.ALL.get("ingredient_disturbed_cotton").get()));
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.getInventory().add(new ItemStack(ModItems.ALL.get("ingredient_subdued_spirit").get()));
        helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL),
            "wake must restore inventory");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "original items restored");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 1,
            "original cotton restored once");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_icy_needle").get()), 1,
            "only the unspent carried needle returned");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_disturbed_cotton").get()), 1,
            "documented dream export returned");
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "unapproved dream loot filtered out");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_subdued_spirit").get()), 1,
            "the native Spirit creature drop is exportable");
        helper.succeed();
    }

    public static void creativeEntryRemainsBodyExemptAfterGameModeChange(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "creative entry must succeed");
        helper.assertTrue(SpiritWorldState.read(player).orElseThrow().body() == null,
            "creative entry persists an explicitly bodyless session");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(SpiritWorldRuntime.wakeIfBodyMissing(player),
            "body exemption belongs to this entry, not the player's current game mode");
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(SpiritWorldRuntime.isDreaming(player), "normal ticks must not wake a bodyless session");
            helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL),
                "bodyless session still supports ordinary inventory restoration and cleanup");
            helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "creative entry's escrow is restored once");
            helper.succeed();
        });
    }

    public static void boundEntryKeepsItsExactSourceAndRequestedArrival(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ServerLevel source = player.level();
        final net.minecraft.world.phys.Vec3 original = player.position();
        final net.minecraft.world.phys.Vec3 arrival = original.add(8.25, 1.0, 2.75);
        helper.assertTrue(SpiritWorldRuntime.enterAt(player, arrival), "bound entry must succeed");
        final var session = SpiritWorldState.read(player).orElseThrow();
        helper.assertValueEqual(session.sourceDimension(), source.dimension().identifier(), "exact source dimension");
        helper.assertValueEqual(source.getEntity(session.body()).position(), original, "body stays at exact source pose");
        helper.assertValueEqual(player.position(), arrival, "bound travel retains its requested destination");
        helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL), "bound traveler wakes");
        helper.assertValueEqual(player.position(), original, "wake returns to the stored source position");
        helper.succeed();
    }

    public static void manifestationKeepsOneBodyAndSeparatesBoundaryInventories(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ServerLevel source = player.level();
        player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        final var session = SpiritWorldState.read(player).orElseThrow();
        final Entity body = source.getEntity(session.body());
        player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        player.getInventory().add(new ItemStack(ModItems.ALL.get("somniancotton").get(), 3));
        player.getInventory().add(new ItemStack(ModItems.ALL.get("ingredient_subdued_spirit").get()));
        final BlockPos portal = player.blockPosition().offset(5, 0, 0);
        for (BlockPos pos : BlockPos.betweenClosed(portal.offset(-2, -1, -2), portal.offset(2, 2, 2))) {
            source.setBlockAndUpdate(pos, pos.getY() == portal.getY() - 1
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        SpiritManifestationState.grant(player, source.getServer().getTickCount() + 1000);
        helper.assertTrue(com.kadamitas.warlockery.ritual.ManifestationRuntime.enterPortal(player, portal),
            "granted dreamer may manifest");
        helper.assertTrue(source.getEntity(session.body()) == body && body.isAlive(),
            "manifestation must retain the same original sleeping body");
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "dream mining loot stays in dream escrow");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 3, "cotton crosses outward");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_subdued_spirit").get()), 1,
            "curated Spirit drop crosses outward");
        player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
        helper.assertTrue(com.kadamitas.warlockery.ritual.ManifestationRuntime.returnToSpiritWorld(player,
            com.kadamitas.warlockery.ritual.ManifestationRuntime.ReturnCause.PORTAL), "manifestation returns");
        helper.assertTrue(source.getEntity(session.body()) == body && body.isAlive(),
            "return to dreaming keeps the original body");
        helper.assertValueEqual(count(player, Items.COBBLESTONE), 0, "waking building materials do not enter the dream");
        helper.assertValueEqual(count(player, Items.DIAMOND), 2, "dream inventory restores once");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 3, "cotton is not duplicated");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_subdued_spirit").get()), 0,
            "an export-only drop is left in the waking world on return");
        helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL), "final wake succeeds");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "original waking inventory restores once");
        helper.assertTrue(source.getEntity(session.body()) == null, "final wake cleans up the body");
        helper.succeed();
    }

    public static void canceledExternalTransferRestoresBodyPortalAndInventory(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ServerLevel source = player.level();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get(), 2));
        player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setSelectedSlot(4);
        final var original = player.position();
        final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new java.util.HashMap<>();
        BlockPos.betweenClosedStream(player.blockPosition().offset(-8, -8, -8),
            player.blockPosition().offset(8, 8, 8)).forEach(pos -> blocks.put(pos.immutable(), source.getBlockState(pos)));
        helper.assertTrue(SpiritWorldRuntime.beginExternalTransfer(player).entered(),
            "transaction preparation runs before the native transfer");
        final var session = SpiritWorldState.read(player).orElseThrow();
        helper.assertTrue(source.getEntity(session.body()) != null, "preparation created a linked body");
        SpiritWorldRuntime.finishExternalTransfer(player, false);
        helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "canceled transfer clears the prepared session");
        helper.assertTrue(source.getEntity(session.body()) == null, "canceled transfer removes its body");
        helper.assertValueEqual(player.position(), original, "preparation did not move the source player");
        helper.assertValueEqual(player.getInventory().getSelectedSlot(), 4, "selected slot restored");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "escrow restored exactly once");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_icy_needle").get()), 2,
            "carried items restored exactly once");
        helper.assertTrue(blocks.entrySet().stream().allMatch(entry ->
            source.getBlockState(entry.getKey()).equals(entry.getValue())), "canceled transfer restores replaced portal terrain");
        SpiritWorldRuntime.recoverPendingTransfer(player);
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "later recovery cannot replay inventory restoration");
        helper.succeed();
    }

    public static void canceledExternalExitRestoresTheDreamInventory(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        final var bodyId = SpiritWorldState.read(player).orElseThrow().body();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 32));
        player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("somniancotton").get(), 3));
        SpiritWorldRuntime.beginExternalExit(player);
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "PRE removes mining resources before native departure");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 3, "PRE retains allowed exports");
        SpiritWorldRuntime.finishExternalTransfer(player, false);
        helper.assertValueEqual(count(player, Items.DIAMOND), 32, "failed departure restores all dream possessions");
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 3, "failed departure does not copy exports");
        helper.assertValueEqual(SpiritWorldState.read(player).orElseThrow().body(), bodyId, "canceled departure retains the same body");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    /** Requires a server with the real Spirit dimension; the same-level GameTest alias is not sufficient. */
    public static void genericNativeTeleportLeavesBodyAtSource(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper, false);
        final ServerLevel source = player.level();
        final ServerLevel destination = source.getServer().getLevel(SpiritWorldRuntime.SPIRIT_WORLD);
        helper.assertTrue(destination != null, "native hook coverage requires the actual Spirit dimension");
        final var original = player.position();
        player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        player.inventoryMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 2));
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 4));
        final var target = original.add(8, 0, 4);
        destination.getChunkAt(BlockPos.containing(target));
        final ServerPlayer moved = player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
            destination, target, net.minecraft.world.phys.Vec3.ZERO, player.getYRot(), player.getXRot(),
            net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        helper.assertTrue(moved != null && player.level() == destination, "raw native teleport succeeds");
        final var session = SpiritWorldState.read(player).orElseThrow();
        helper.assertValueEqual(session.sourceDimension(), source.dimension().identifier(), "PRE captured the source dimension");
        helper.assertValueEqual(source.getEntity(session.body()).position(), original, "PRE captured exact source position");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 0, "generic transfer enforces inventory escrow");
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 32));
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.DIAMOND, 5));
        final ServerPlayer returned = player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
            source, original.add(2, 0, 0), net.minecraft.world.phys.Vec3.ZERO, player.getYRot(), player.getXRot(),
            net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        helper.assertTrue(returned != null && !SpiritWorldRuntime.isDreaming(player),
            "raw native departure completes waking before returning to the caller");
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "no same-tick mining-item export window");
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "generic departure restores original inventory once");
        helper.assertValueEqual(count(player, Items.GOLD_INGOT), 2, "native entry cursor restored once");
        helper.assertValueEqual(count(player, Items.EMERALD), 4, "native entry crafting inputs restored once");
        player.closeContainer();
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "closing inventory after native exit cannot import hidden diamonds");
        helper.succeed();
    }

    public static void nativeManifestationReturnFiltersPhysicalMenuAcquisitions(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper, false);
        final ServerLevel source = player.level();
        final ServerLevel spirit = source.getServer().getLevel(SpiritWorldRuntime.SPIRIT_WORLD);
        helper.assertTrue(spirit != null, "native manifestation return requires the real Spirit dimension");
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "real dream entry");
        final var session = SpiritWorldState.read(player).orElseThrow();
        final BlockPos portal = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(portal.offset(-2, -1, -2), portal.offset(2, 2, 2)))
            source.setBlockAndUpdate(pos, pos.getY() == portal.getY() - 1
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        SpiritManifestationState.grant(player, source.getServer().getTickCount() + 1000);
        helper.assertTrue(com.kadamitas.warlockery.ritual.ManifestationRuntime.enterPortal(player, portal), "manifest through actual runtime");
        final var physical = player.position();
        player.inventoryMenu.setCarried(new ItemStack(Items.COBBLESTONE, 7));
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, 5));
        final ServerPlayer returned = player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
            spirit, net.minecraft.world.phys.Vec3.atBottomCenterOf(portal), net.minecraft.world.phys.Vec3.ZERO,
            player.getYRot(), player.getXRot(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        helper.assertTrue(returned != null && player.level() == spirit, "raw native return succeeds");
        helper.assertFalse(com.kadamitas.warlockery.ritual.ManifestationRuntime.isActive(player), "external return finishes manifestation synchronously");
        helper.assertValueEqual(count(player, Items.DIAMOND), 2, "stored dream inventory restored once");
        player.closeContainer();
        helper.assertValueEqual(count(player, Items.COBBLESTONE), 0, "physical cursor cannot enter dream");
        helper.assertValueEqual(count(player, Items.GOLD_INGOT), 0, "physical crafting grid cannot enter dream");
        final var drops = source.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(physical, physical).inflate(3));
        helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(Items.COBBLESTONE))
            .mapToInt(item -> item.getItem().getCount()).sum(), 7, "cursor acquisitions stay at physical departure");
        helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(Items.GOLD_INGOT))
            .mapToInt(item -> item.getItem().getCount()).sum(), 5, "crafting acquisitions stay at physical departure");
        helper.assertTrue(source.getEntity(session.body()) != null, "original body survives external manifestation return");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void menuContentsSettleBeforeSpiritInventorySnapshots(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.getInventory().clearContent();
        player.inventoryMenu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, 2));
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "entry settles native cursor and 2x2 grid");
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && player.inventoryMenu.getSlot(1).getItem().isEmpty(),
            "no menu contents survive outside the escrow snapshot");
        player.containerMenu = new net.minecraft.world.inventory.CraftingMenu(1, player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), player.blockPosition()));
        player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 7));
        player.containerMenu.getSlot(1).set(new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL), "wake settles 3x3 crafting inputs");
        player.closeContainer();
        helper.assertValueEqual(count(player, Items.IRON_INGOT), 3, "entry cursor restored once");
        helper.assertValueEqual(count(player, Items.GOLD_INGOT), 2, "entry crafting input restored once");
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "neither exit cursor nor crafting grid bypasses export filtering");
        helper.succeed();
    }

    public static void canceledBoundaryDoesNotReplayDroppedOrConsumedItems(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry");
        final var cotton = ModItems.ALL.get("somniancotton").get();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(cotton, 3));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 4));
        SpiritWorldRuntime.beginExternalExit(player);
        final var dropped = player.drop(player.getInventory().getItem(0).split(1), false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.getInventory().getItem(0).shrink(1);
        player.getInventory().setItem(2, new ItemStack(Items.STICK, 2));
        SpiritWorldRuntime.finishExternalTransfer(player, false);
        helper.assertValueEqual(count(player, cotton), 1, "rollback retains current cotton without replaying dropped or consumed units");
        helper.assertTrue(dropped != null && dropped.getItem().is(cotton) && dropped.getItem().getCount() == 1,
            "one legitimately dropped cotton remains on source ground");
        helper.assertValueEqual(count(player, Items.DIAMOND), 4, "withheld dream items restore exactly once");
        helper.assertValueEqual(count(player, Items.STICK), 2, "new items acquired during pending cancellation are retained");
        SpiritWorldRuntime.recoverPendingTransfer(player);
        helper.assertValueEqual(count(player, cotton), 1, "later recovery does not replay completed rollback");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void canceledManifestationReturnRestoresSettledMenus(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry");
        final var body = SpiritWorldState.read(player).orElseThrow().body();
        SpiritManifestationState.begin(player, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot(), java.util.List.of(), 0);
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 7));
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, 2));
        com.kadamitas.warlockery.ritual.ManifestationRuntime.prepareExternalReturn(player);
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "PRE excludes cursor acquisitions");
        helper.assertValueEqual(count(player, Items.GOLD_INGOT), 0, "PRE excludes crafting acquisitions");
        com.kadamitas.warlockery.ritual.ManifestationRuntime.finishExternalReturn(player, false);
        helper.assertValueEqual(count(player, Items.DIAMOND), 7, "canceled return restores settled cursor once");
        helper.assertValueEqual(count(player, Items.GOLD_INGOT), 2, "canceled return restores crafting inputs once");
        helper.assertTrue(com.kadamitas.warlockery.ritual.ManifestationRuntime.isActive(player), "canceled return retains manifestation");
        helper.assertValueEqual(SpiritWorldState.read(player).orElseThrow().body(), body, "body retained");
        SpiritManifestationState.finish(player);
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void sleepingAppleForcesOnlyAStandardNightmare(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack apple = new ItemStack(ModItems.ALL.get("ingredient_sleeping_apple").get());
        apple.getItem().finishUsingItem(apple, player.level(), player);
        helper.assertTrue(SpiritWorldRuntime.isNightmare(player), "Sleeping Apple must force a nightmare");
        helper.assertFalse(SpiritWorldRuntime.isDemonicNightmare(player),
            "Sleeping Apple must never open a demonic nightmare");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void icyNeedleWakesAndIsSpent(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.getInventory().setItem(4, new ItemStack(Items.STICK));
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_icy_needle").get()));
        player.getInventory().setSelectedSlot(0);
        player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "Icy Needle must wake the dreamer");
        helper.assertValueEqual(count(player, Items.STICK), 1, "original inventory restored after needle wake");
        helper.assertValueEqual(count(player, ModItems.ALL.get("ingredient_icy_needle").get()), 0,
            "used Icy Needle must be consumed");
        helper.succeed();
    }

    public static void fatalDreamDamageWakesBeforeDeath(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        player.setHealth(4.0F);
        player.setInvulnerableTime(0);
        player.hurtServer(player.level(), player.damageSources().magic(), 20.0F);
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "fatal dream damage must wake the player");
            helper.assertTrue(player.isAlive() && player.getHealth() >= 1.0F,
                "fatal dream damage must not kill the waking body");
            helper.succeed();
        });
    }

    public static void dispenserOfferedBodyEquipmentReturnsOnWake(final GameTestHelper helper) {
        equippedBodyScenario(helper, false);
    }

    public static void dispenserOfferedBodyEquipmentDropsOnceOnDeath(final GameTestHelper helper) {
        equippedBodyScenario(helper, true);
    }

    private static void equippedBodyScenario(final GameTestHelper helper, final boolean killBody) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ServerLevel source = player.level();
        player.getInventory().clearContent();
        player.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 3));
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry");
        final var session = SpiritWorldState.read(player).orElseThrow();
        final var body = (net.minecraft.world.entity.decoration.Mannequin) source.getEntity(session.body());
        final BlockPos dispenser = body.blockPosition().north();
        source.setBlockAndUpdate(dispenser, net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState()
            .setValue(net.minecraft.world.level.block.DispenserBlock.FACING, net.minecraft.core.Direction.SOUTH));
        ((net.minecraft.world.level.block.entity.DispenserBlockEntity) source.getBlockEntity(dispenser))
            .setItem(0, new ItemStack(Items.IRON_HELMET));
        source.setBlockAndUpdate(dispenser.west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "native powered dispenser equips the sleeping body");
            if (killBody) {
                body.hurtServer(source, source.damageSources().genericKill(), Float.MAX_VALUE);
                helper.assertFalse(body.isAlive(), "native lethal damage kills the body");
                helper.assertTrue(SpiritWorldRuntime.wakeIfBodyMissing(player), "body death wakes its owner");
            } else {
                helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL), "normal wake");
            }
            final var drops = source.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(body.blockPosition()).inflate(3));
            helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(Items.IRON_HELMET))
                .mapToInt(item -> item.getItem().getCount()).sum(), 1, "actual body helmet is returned exactly once");
            helper.assertTrue(body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),
                "body slot clears before drop so death and cleanup cannot duplicate equipment");
            helper.assertValueEqual(count(player, Items.DIAMOND), 3, "owner escrow restores once");
            helper.assertFalse(drops.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND)),
                "body drops never copy owner escrow");
            helper.succeed();
        });
    }

    public static void destroyedBodyForcesWake(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ServerLevel source = player.level();
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        final SpiritWorldState.Session session = SpiritWorldState.read(player).orElseThrow();
        final Entity body = source.getEntity(session.body());
        helper.assertTrue(body != null, "sleeping body must exist before destruction");
        body.discard();
        helper.assertTrue(SpiritWorldRuntime.wakeIfBodyMissing(player), "missing body must force wake");
        helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "body wake must clear dream state");
        helper.succeed();
    }

    public static void spiritWorldInhibitsEveryCircleRitual(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        final var options = RitualManager.INSTANCE.options(player.level(), player.blockPosition(), player);
        helper.assertFalse(options.isEmpty(), "ritual catalog must remain inspectable in the Spirit World");
        helper.assertTrue(options.stream().allMatch(option -> !option.ready()),
            "no Circle Magic option may be ready in the Spirit World");
        helper.assertTrue(options.stream().allMatch(option -> option.requirements().stream().anyMatch(requirement ->
            requirement.label().equals("spirit_world_circle_magic") && !requirement.met()
        )), "every ritual diagnostic must report the Spirit World boundary");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void demonicNightmareFlagPersistsInSession(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered(), "dream entry must succeed");
        final SpiritWorldState.Session existing = SpiritWorldState.read(player).orElseThrow();
        SpiritWorldState.begin(player, new SpiritWorldState.Session(
            true,
            true,
            existing.sourceDimension(),
            existing.sourceX(),
            existing.sourceY(),
            existing.sourceZ(),
            existing.sourceYaw(),
            existing.sourcePitch(),
            existing.body(),
            existing.portal(),
            existing.originalInventory(),
            existing.selectedSlot()
        ));
        helper.assertTrue(SpiritWorldState.read(player).orElseThrow().demonicNightmare(),
            "demonic nightmare state must survive serialization");
        SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    private static int count(final ServerPlayer player, final net.minecraft.world.item.Item item) {
        return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
            .map(slot -> player.getInventory().getItem(slot).is(item)
                ? player.getInventory().getItem(slot).getCount()
                : 0)
            .sum();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        return connectedSurvivalPlayer(helper, true);
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper, final boolean alias) {
        final var server = helper.getLevel().getServer();
        final java.util.UUID id = java.util.UUID.randomUUID();
        final ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
            new com.mojang.authlib.GameProfile(id, "spirit_" + id.toString().substring(0, 8)),
            net.minecraft.server.level.ClientInformation.createDefault());
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        server.getConnection().getConnections().add(connection);
        helper.addCleanup(passed -> {
            server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        });
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (alias) SpiritWorldRuntime.useGameTestDestination(player);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
