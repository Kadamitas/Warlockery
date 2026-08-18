package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Maneuver;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class TacticalCombatGameTests {
    private TacticalCombatGameTests() {
    }

    public static void rangedCreatureRoutesBehindCoverWhenPlayerDrawsBow(final GameTestHelper helper) {
        buildFloor(helper, -2, 7, -2, 3);
        BlockPos.betweenClosedStream(new BlockPos(4, 1, 0), new BlockPos(4, 3, 0))
            .forEach(position -> helper.setBlock(position, Blocks.STONE_BRICKS));
        final ServerPlayer player = connectedPlayer(helper, new BlockPos(0, 1, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.setInvulnerable(true);
        final WerewolfHunterEntity hunter = helper.spawn(
            ModEntities.WEREWOLF_HUNTER.get(), new BlockPos(5, 1, 2), EntitySpawnReason.EVENT
        );
        hunter.setTarget(player);
        helper.runAfterDelay(2, () -> verifyRangedCover(helper, player, hunter));
    }

    private static void verifyRangedCover(
        final GameTestHelper helper,
        final ServerPlayer player,
        final WerewolfHunterEntity hunter
    ) {
        helper.assertTrue(TacticalCombatRuntime.isRangedThreat(player),
            "a player holding a bow must be recognized as a ranged threat");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(!TacticalCombatRuntime.isRangedThreat(player),
            "an ordinary held item must not masquerade as a ranged weapon");
        helper.assertTrue(TacticalCombatRuntime.isDirectedCombatDamage(
            helper.getLevel().damageSources().indirectMagic(hunter, player)
        ), "player-attributed magic must create a tactical threat");
        helper.assertTrue(TacticalCombatRuntime.isDirectedCombatDamage(
            helper.getLevel().damageSources().thrown(hunter, player)
        ), "player-attributed projectiles from another mod must create a tactical threat");
        helper.assertTrue(TacticalCombatRuntime.isDirectedCombatDamage(
            helper.getLevel().damageSources().explosion(hunter, player)
        ), "player-attributed explosive weapons must create a tactical threat");
        helper.assertTrue(!TacticalCombatRuntime.isDirectedCombatDamage(helper.getLevel().damageSources().generic()),
            "environmental damage must not create a false combat threat");
        helper.assertTrue(hunter.hurtServer(
            helper.getLevel(),
            helper.getLevel().damageSources().indirectMagic(hunter, player),
            1.0F
        ), "directed magic must pass through the mob's damage integration");
        helper.assertTrue(TacticalCombatRuntime.hasCombatThreat(hunter, player, helper.getLevel().getGameTime()),
            "directed damage must remain in tactical memory after the attacker changes items");
        final var profile = TacticalCombatRules.profile(CreatureKind.WEREWOLF_HUNTER);
        final BlockPos expectedCover = helper.absolutePos(new BlockPos(5, 1, 0));
        helper.assertTrue(TacticalCombatRuntime.standableNear(helper.getLevel(), expectedCover).isPresent(),
            "the tested cover position must be standable");
        helper.assertTrue(TacticalCombatRuntime.concealedFrom(helper.getLevel(), hunter, player, expectedCover),
            "the pillar must conceal the tested cover position");
        helper.assertTrue(TacticalCombatRuntime.routeReaches(hunter, expectedCover),
            "the hunter must be able to route around the pillar");
        final var coverResult = TacticalCombatRuntime.findCover(
            hunter, helper.getLevel(), player, profile.coverSearchRadius()
        );
        helper.assertTrue(coverResult.isPresent(),
            "the ranged creature must find routed cover behind the wall");
        final BlockPos cover = coverResult.orElse(hunter.blockPosition());
        helper.assertTrue(cover.getX() > helper.absolutePos(new BlockPos(4, 1, 0)).getX(),
            "chosen cover must put the wall between the creature and the bow user");

        final Maneuver maneuver = TacticalCombatRules.choose(
            profile,
            true,
            true,
            true,
            hunter.distanceTo(player),
            hunter.getHealth(),
            hunter.getMaxHealth()
        );
        helper.assertValueEqual(maneuver, Maneuver.COVER,
            "a ranged hunter must use cover after any player-attributed ranged or magical hit");
        TacticalCombatRuntime.execute(hunter, helper.getLevel(), player, profile, maneuver);
        helper.assertTrue(hunter.getNavigation().getPath() != null
            && hunter.getNavigation().getPath().getDistToTarget() <= 1.0F,
            "the cover maneuver must install a reachable navigation path");
        helper.assertTrue(!TacticalCombatRuntime.hasRecentDirectedThreat(
            hunter,
            helper.getLevel().getGameTime() + 120L
        ), "directed threat memory must expire after six seconds");
        helper.succeed();
    }

    public static void meleeCreatureDisengagesFromUnreachableAttackSlit(final GameTestHelper helper) {
        buildFloor(helper, -1, 7, -3, 3);
        for (int x = 2; x <= 6; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 2 || x == 6 || z == -2 || z == 2) {
                    for (int y = 1; y <= 3; y++) {
                        if (!(x == 2 && y == 2 && z == 0)) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
                        }
                    }
                }
            }
        }
        final ServerPlayer player = connectedPlayer(helper, new BlockPos(0, 1, 0));
        player.setInvulnerable(true);
        // The melee subject is an ordinary ArcaneMob whose specialization seam is the generic one,
        // so this fixture drives a doctrine its kind really reaches. The Werewolf it used to spawn
        // never runs TacticalCombatRuntime: LycanPackRuntime owns that family's combat and the
        // WEREWOLF doctrine row is retired, so a hand-driven werewolf here would have demonstrated a
        // maneuver no werewolf can perform.
        final ArcaneMob melee = (ArcaneMob) helper.spawn(
            ModEntities.ALL.get("illusion_zombie").get(), new BlockPos(4, 1, 0), EntitySpawnReason.EVENT
        );
        melee.setTarget(player);
        helper.runAfterDelay(2, () -> verifyBlockedMeleeDisengagement(helper, player, melee));
    }

    private static void verifyBlockedMeleeDisengagement(
        final GameTestHelper helper,
        final ServerPlayer player,
        final ArcaneMob melee
    ) {
        helper.assertTrue(!TacticalCombatRuntime.routeReaches(melee, player),
            "the attack slit must block the melee creature's path to the player");
        helper.assertTrue(TacticalCombatRules.usesGenericTacticalLayer(melee.creatureKind()),
            "the melee subject must be a kind that still reaches the generic tactical layer");
        final var profile = TacticalCombatRules.profile(melee.creatureKind());
        final Maneuver maneuver = TacticalCombatRules.choose(
            profile,
            false,
            true,
            false,
            melee.distanceTo(player),
            melee.getHealth(),
            melee.getMaxHealth()
        );
        helper.assertValueEqual(maneuver, Maneuver.DISENGAGE,
            "an exposed melee creature with no route must disengage");
        TacticalCombatRuntime.execute(melee, helper.getLevel(), player, profile, maneuver);
        final BlockPos destination = melee.getNavigation().getTargetPos();
        helper.assertTrue(destination != null && destination.distSqr(player.blockPosition())
            > melee.blockPosition().distSqr(player.blockPosition()),
            "the disengage path must carry the creature away from the unreachable attack slit");
        helper.succeed();
    }

    private static void buildFloor(
        final GameTestHelper helper,
        final int minimumX,
        final int maximumX,
        final int minimumZ,
        final int maximumZ
    ) {
        BlockPos.betweenClosedStream(new BlockPos(minimumX, 0, minimumZ), new BlockPos(maximumX, 0, maximumZ))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    private static ServerPlayer connectedPlayer(final GameTestHelper helper, final BlockPos relativePosition) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(relativePosition);
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
