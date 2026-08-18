package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
        helper.assertValueEqual(SeerCovenRuntime.countParticipants(helper.getLevel(), center, 8, player.getUUID()), 3,
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

    public static void aRiteCountsOnlyTheCovenOfThePlayerWhoStartedIt(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final ServerPlayer caster = connectedSurvivalPlayer(helper);
        caster.teleportTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
        final UUID stranger = UUID.randomUUID();

        final Mob own = circleMage(helper, center.offset(2, 0, 0));
        CreatureBehaviorState.bind(own, caster.getUUID());
        final Mob borrowed = circleMage(helper, center.offset(-2, 0, 0));
        CreatureBehaviorState.bind(borrowed, stranger);
        final Mob unbound = circleMage(helper, center.offset(0, 0, 2));

        helper.assertTrue(
            SeerCovenRuntime.isBoundCircleMage(borrowed),
            "the borrowed Mage must be a bound Circle Mage, or this proves nothing"
        );
        helper.assertFalse(SeerCovenRuntime.isBoundCircleMage(unbound), "the unbound Mage answers to nobody");

        helper.assertValueEqual(
            SeerCovenRuntime.countParticipants(
                helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, caster.getUUID()
            ),
            2,
            "a rite must count its caster and the caster's own Mage, and nobody else's"
        );
        helper.assertValueEqual(
            SeerCovenRuntime.countParticipants(
                helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, stranger
            ),
            2,
            "the same circle counts the other player's Mage only for that other player"
        );
        helper.assertValueEqual(
            SeerCovenRuntime.countParticipants(
                helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, null
            ),
            1,
            "a caster who cannot be resolved has no coven, though the players present still stand there"
        );
        helper.succeed();
    }

    /**
     * Two warlocks stand in one circle, each with their own bound Circle Mages. A rite counts the caster's
     * coven and nobody else's, so the same arena yields a different total depending on who is casting.
     *
     * <p>The Mage counts are deliberately unequal, two against three, so that the correct answers and the
     * answer the old any-owner rule gave are three different numbers rather than one number reached two
     * ways.</p>
     */
    public static void twoCovensInOneCircleAreCountedSeparately(final GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
            final ServerPlayer first = connectedSurvivalPlayerAt(helper, new BlockPos(1, 1, 1));
            final ServerPlayer second = connectedSurvivalPlayerAt(helper, new BlockPos(1, 1, 2));

            // The participant scan reaches eight blocks, which is further than this arena. Before either
            // coven is gathered the circle must hold exactly these two warlocks: a player or a Mage picked up
            // from a neighbouring arena would leave every count below measuring something other than what it
            // claims, and would do it silently.
            helper.assertValueEqual(
                SeerCovenRuntime.countParticipants(
                    helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, UUID.randomUUID()
                ),
                2,
                "participants in range before either coven is gathered"
            );

            final List<Mob> firstCoven = List.of(
                boundMage(helper, new BlockPos(0, 1, 0), first.getUUID()),
                boundMage(helper, new BlockPos(2, 1, 0), first.getUUID())
            );
            final List<Mob> secondCoven = List.of(
                boundMage(helper, new BlockPos(0, 1, 2), second.getUUID()),
                boundMage(helper, new BlockPos(2, 1, 2), second.getUUID()),
                boundMage(helper, new BlockPos(1, 1, 0), second.getUUID())
            );
            Stream.concat(firstCoven.stream(), secondCoven.stream()).forEach(mage -> helper.assertTrue(
                SeerCovenRuntime.isBoundCircleMage(mage),
                "every Mage here must be a bound Circle Mage, or excluding one proves nothing"
            ));

            helper.assertValueEqual(
                SeerCovenRuntime.countParticipants(
                    helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, first.getUUID()
                ),
                4,
                "a rite cast by the first warlock counts both warlocks and only their own two Mages"
            );
            helper.assertValueEqual(
                SeerCovenRuntime.countParticipants(
                    helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, second.getUUID()
                ),
                5,
                "the same circle counts the second warlock's three Mages only for the second warlock"
            );
            helper.assertValueEqual(
                SeerCovenRuntime.countParticipants(
                    helper.getLevel(), center, SeerCovenRuntime.PARTICIPANT_RADIUS, null
                ),
                2,
                "an unresolvable caster has no coven and must not fall back to counting everyone's"
            );
            helper.succeed();
        });
    }

    private static Mob boundMage(final GameTestHelper helper, final BlockPos position, final UUID owner) {
        final Mob mage = circleMage(helper, helper.absolutePos(position));
        mage.setNoAi(true);
        CreatureBehaviorState.bind(mage, owner);
        return mage;
    }

    private static ServerPlayer connectedSurvivalPlayerAt(final GameTestHelper helper, final BlockPos position) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final BlockPos absolute = helper.absolutePos(position);
        player.teleportTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
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
