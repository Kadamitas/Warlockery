package com.kadamitas.warlockery.dream;

import com.kadamitas.warlockery.registry.ModChunkTickets;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.util.GameTestCleanup;
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
        player.setInvulnerable(true);
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
        helper.assertValueEqual(
            SpiritWorldRuntime.enter(player, false).diagnostic(),
            SpiritWorldRules.EntryDiagnostic.ALREADY_DREAMING,
            "second entry diagnostic"
        );
        final ServerPlayer second = connectedSurvivalPlayer(helper);
        second.setInvulnerable(true);
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
        helper.assertValueEqual(count(player, ModItems.ALL.get("somniancotton").get()), 0,
            "Overworld cotton not copied into dream");
        player.getInventory().getItem(0).shrink(1);
        player.getInventory().add(new ItemStack(ModItems.ALL.get("ingredient_disturbed_cotton").get()));
        player.getInventory().add(new ItemStack(Items.DIAMOND));
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
        player.invulnerableTime = 0;
        player.hurtServer(player.level(), player.damageSources().magic(), 20.0F);
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(SpiritWorldRuntime.isDreaming(player), "fatal dream damage must wake the player");
            helper.assertTrue(player.isAlive() && player.getHealth() >= 1.0F,
                "fatal dream damage must not kill the waking body");
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
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(connection);
        final var server = helper.getLevel().getServer();
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        server.getConnection().getConnections().add(connection);
        GameTestCleanup.add(helper, passed -> {
            server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        });
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        SpiritWorldRuntime.useGameTestDestination(player);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
