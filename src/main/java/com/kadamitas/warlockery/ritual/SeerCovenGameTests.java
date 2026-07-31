package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SeerCovenGameTests {
    private SeerCovenGameTests() {
    }

    public static void seerStoneCallsOnlyTheOwnersRecruitedCircleMages(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.ALL.get("circle").get());
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.teleportTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);

        final Mob first = circleMage(helper, center.offset(6, 0, 0));
        final Mob second = circleMage(helper, center.offset(-6, 0, 0));
        final Mob unbound = circleMage(helper, center.offset(0, 0, 6));
        CreatureBehaviorState.bind(first, player.getUUID());
        CreatureBehaviorState.bind(second, player.getUUID());
        final Vec3 unboundPosition = unbound.position();

        final ItemStack stone = new ItemStack(ModItems.ALL.get("ingredient_seer_stone").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false);
        final InteractionResult result = stone.getItem().useOn(
            new UseOnContext(player, InteractionHand.MAIN_HAND, hit)
        );

        helper.assertTrue(result.consumesAction(), "the Seer Stone must handle a golden circle invocation");
        helper.assertTrue(first.distanceToSqr(Vec3.atCenterOf(center)) < 20.0,
            "the first recruited Circle Mage must gather around the circle");
        helper.assertTrue(second.distanceToSqr(Vec3.atCenterOf(center)) < 20.0,
            "the second recruited Circle Mage must gather around the circle");
        helper.assertValueEqual(unbound.position(), unboundPosition,
            "an unbound Circle Mage must not answer another player's Seer Stone");
        helper.assertValueEqual(SeerCovenRuntime.countParticipants(helper.getLevel(), center, 8), 3,
            "the player and two bound Circle Mages must satisfy coven participant counts");
        helper.succeed();
    }

    public static void seerStoneLeavesOrdinaryDivinationAvailable(final GameTestHelper helper) {
        final BlockPos ordinaryGlyph = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.ALL.get("circleglyphritual").get());
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack stone = new ItemStack(ModItems.ALL.get("ingredient_seer_stone").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        final BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(ordinaryGlyph), Direction.UP, ordinaryGlyph, false
        );

        helper.assertValueEqual(
            stone.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)),
            InteractionResult.PASS,
            "non-golden blocks must retain the Seer Stone's normal divination fallback"
        );
        helper.succeed();
    }

    private static Mob circleMage(final GameTestHelper helper, final BlockPos position) {
        final Mob mage = (Mob) ModEntities.ALL.get("circle_mage").get()
            .create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        if (mage == null) {
            throw new IllegalStateException("Circle Mage entity creation failed");
        }
        mage.setPos(Vec3.atBottomCenterOf(position));
        mage.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mage);
        return mage;
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
