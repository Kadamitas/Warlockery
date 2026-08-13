package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class NamiLifeGameTests {
    private NamiLifeGameTests() {
    }

    public static void dailyRoutineReturnsHome(final GameTestHelper helper) {
        buildFloor(helper, 2);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
            helper.getLevel().getServer().createCommandSourceStack(), "time set night"
        );
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), new BlockPos(0, 1, 0), EntitySpawnReason.EVENT);
        final BlockPos home = nami.blockPosition();
        NamiLifeRuntime.tick(nami, helper.getLevel());
        nami.teleportTo(
            helper.absolutePos(new BlockPos(2, 1, 2)).getX() + 0.5,
            helper.absolutePos(new BlockPos(2, 1, 2)).getY(),
            helper.absolutePos(new BlockPos(2, 1, 2)).getZ() + 0.5
        );

        helper.runAfterDelay(120, () -> {
            helper.assertValueEqual(nami.lifeState().home(), java.util.Optional.of(home),
                "the first loaded safe position must remain Nami's soft home");
            helper.assertValueEqual(
                NamiLifeRules.scheduledActivity(helper.getLevel().getOverworldClockTime()),
                NamiLifeRules.Activity.SHELTER,
                "the live server clock must map the test to Nami's shelter window"
            );
            helper.assertTrue(nami.blockPosition().distSqr(home) <= 4.0,
                "nighttime shelter must return Nami near her loaded home; position=" + nami.blockPosition()
                    + ", home=" + home + ", state=" + nami.lifeState() + ", counters=" + nami.lifeCounters());
            for (int x = 0; x <= 2; x++) {
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, 1));
            }
            final NamiLifeRuntime.Counters counters = nami.lifeCounters();
            helper.assertTrue(counters.fullDecisions() > 0L, "the semantic controller must expose decision work");
            helper.assertTrue(counters.maximumBlockStatesPerDiscovery() <= NamiLifeRules.MAX_BLOCK_STATES_EXAMINED,
                "one discovery must never inspect more than 256 block states");
            helper.succeed();
        });
    }

    public static void greetingBuildsBoundedTrust(final GameTestHelper helper) {
        buildFloor(helper, 3);
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
        final ServerPlayer first = connectedPlayer(helper, new BlockPos(0, 1, 1));
        final ServerPlayer second = connectedPlayer(helper, new BlockPos(2, 1, 1));
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        helper.assertFalse(nami.mobInteract(first, InteractionHand.MAIN_HAND).consumesAction(),
            "a held item must not become a greeting or be consumed");
        helper.assertValueEqual(first.getMainHandItem().getCount(), 3, "held items must remain untouched");

        first.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(nami.mobInteract(first, InteractionHand.MAIN_HAND).consumesAction(),
            "an empty-hand peaceful interaction must greet Nami on the server");
        helper.assertValueEqual(nami.lifeState().welcomedVisitor(), java.util.Optional.of(first.getUUID()),
            "the greeting must remember one welcomed visitor");
        helper.assertTrue(nami.mobInteract(second, InteractionHand.MAIN_HAND).consumesAction(),
            "a greeting during cooldown is handled without mutating progression");
        helper.assertValueEqual(nami.lifeState().welcomedVisitor(), java.util.Optional.of(first.getUUID()),
            "the greeting cooldown must prevent immediate visitor-memory churn");

        helper.runAfterDelay(NamiLifeRules.GREETING_COOLDOWN_TICKS + 2, () -> {
            helper.assertTrue(nami.mobInteract(second, InteractionHand.MAIN_HAND).consumesAction(),
                "a later peaceful greeting must be accepted");
            helper.assertValueEqual(nami.lifeState().welcomedVisitor(), java.util.Optional.of(second.getUUID()),
                "visitor memory must replace rather than grow beyond one identity");
            helper.assertTrue(MarriageData.get(helper.getLevel()).bond(second.getUUID()).isEmpty(),
                "greeting memory must never create marriage or progression state");
            helper.succeed();
        });
    }

    public static void wardProtectsSpouseAndReleasesStaleThreat(final GameTestHelper helper) {
        buildFloor(helper, 2);
        final ServerPlayer spouse = connectedPlayer(helper, new BlockPos(0, 1, 1));
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
        helper.assertValueEqual(
            MarriageData.get(helper.getLevel()).marryNami(spouse.getUUID(), nami.getUUID()),
            MarriageData.MarriageResult.SUCCESS,
            "the ward scenario requires Nami's existing ritual marriage bond"
        );
        nami.acceptMarriage(spouse, MarriageData.get(helper.getLevel()).bond(spouse.getUUID()).orElseThrow().spouseName());
        final Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        zombie.setNoAi(true);
        zombie.setTarget(spouse);
        final float initialHealth = zombie.getHealth();
        final AtomicBoolean keepThreat = new AtomicBoolean(true);

        helper.onEachTick(() -> {
            if (keepThreat.get()) {
                zombie.setTarget(spouse);
            }
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(zombie.getHealth() <= initialHealth - 6.0F,
                "the charged protective ward must preserve the six-damage indirect-magic ceiling; health="
                    + zombie.getHealth() + ", target=" + zombie.getTarget() + ", state=" + nami.lifeState()
                    + ", counters=" + nami.lifeCounters());
            keepThreat.set(false);
            zombie.setTarget(null);
            nami.setTarget(null);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(nami.lifeState().wardTarget().isEmpty(),
                "a living target that stops attacking Nami or her spouse must be released");
            nami.setHealth(nami.getMaxHealth() * 0.2F);
            zombie.setTarget(spouse);
        });
        helper.runAfterDelay(190, () -> {
            helper.assertValueEqual(nami.lifeState().activity(), NamiLifeRules.Activity.WITHDRAW,
                "low health must outrank ward charging");
            helper.assertFalse(nami.canAttack(spouse), "Nami must never attack her spouse");
            helper.succeed();
        });
    }

    private static ServerPlayer connectedPlayer(final GameTestHelper helper, final BlockPos relativePosition) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(relativePosition);
        player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return player;
    }

    private static void buildFloor(final GameTestHelper helper, final int length) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(length, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }
}
