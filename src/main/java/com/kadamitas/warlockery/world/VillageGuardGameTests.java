package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

public final class VillageGuardGameTests {
    private VillageGuardGameTests() {
    }

    public static void hobgoblinTradingBypassesVillageGuardCommissioning(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final HobgoblinEntity hobgoblin = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER_CHESTPLATE));

        helper.assertTrue(!VillageGuardRuntime.isCommissionableTarget(hobgoblin),
            "hobgoblins must bypass vanilla village guard commissioning");
        player.interactOn(hobgoblin, InteractionHand.MAIN_HAND, hobgoblin.position());
        helper.assertTrue(hobgoblin.isAlive() && !hobgoblin.isRemoved(),
            "opening a hobgoblin trade must not replace or remove the trader");
        helper.assertTrue(player.containerMenu instanceof MerchantMenu,
            "interacting with a hobgoblin must open its merchant menu");
        helper.assertTrue(hobgoblin.getTradingPlayer() == player,
            "the hobgoblin must retain the interacting player as its customer");
        helper.runAfterDelay(60, () -> finishPersistentTradeTest(helper, player, hobgoblin, "hobgoblin"));
    }

    public static void goblinTradingRetainsItsCustomer(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final HobgoblinEntity goblin = helper.spawn(
            ModEntities.GOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );

        player.interactOn(goblin, InteractionHand.MAIN_HAND, goblin.position());
        helper.assertTrue(player.containerMenu instanceof MerchantMenu,
            "interacting with a goblin must open its merchant menu");
        helper.runAfterDelay(60, () -> finishPersistentTradeTest(helper, player, goblin, "goblin"));
    }

    public static void goblinFamiliesProduceMatchingBabies(final GameTestHelper helper) {
        final HobgoblinEntity hobgoblinChild = createBaby(
            helper, ModEntities.HOBGOBLIN.get(), new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(2, 1, 0)
        );
        final HobgoblinEntity goblinChild = createBaby(
            helper, ModEntities.GOBLIN.get(), new BlockPos(0, 1, 2), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2)
        );
        helper.assertTrue(hobgoblinChild.isBaby(), "hobgoblin offspring must use the synchronized baby state");
        helper.assertTrue(goblinChild.isBaby(), "goblin offspring must use the synchronized baby state");
        helper.assertTrue(hobgoblinChild.getBbHeight() < ModEntities.HOBGOBLIN.get().getDimensions().height(),
            "hobgoblin babies must use a smaller physical model");
        helper.assertTrue(goblinChild.getBbHeight() < ModEntities.GOBLIN.get().getDimensions().height(),
            "goblin babies must use a smaller physical model");
        helper.assertTrue(hobgoblinChild.getBreedOffspring(helper.getLevel(), goblinChild) == null,
            "goblins and hobgoblins must not create cross-species offspring");
        helper.succeed();
    }

    public static void goblinRaidWaveIsGroupedAndCoordinated(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-6, 0, -6), new BlockPos(8, 0, 8))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        final BlockPos center = helper.absolutePos(relativeCenter);
        final Villager villager = helper.spawn(EntityTypes.VILLAGER, relativeCenter);
        villager.setNoAi(true);

        final int spawned = GoblinRaidRuntime.spawnWave(helper.getLevel(), center, 1, 4);
        final var raiders = helper.getLevel().getEntitiesOfClass(
            HobgoblinEntity.class,
            new AABB(center).inflate(16.0),
            HobgoblinEntity::isVillageRaider
        );
        raiders.forEach(goblin -> GoblinRaidRuntime.coordinate(goblin, helper.getLevel()));

        helper.assertValueEqual(spawned, GoblinRaidRules.waveSize(1), "first goblin raid wave size");
        helper.assertValueEqual(raiders.size(), GoblinRaidRules.waveSize(1), "tracked goblin raid group size");
        helper.assertTrue(raiders.stream().allMatch(goblin -> goblin.raidCenter().filter(center::equals).isPresent()),
            "every wave member must share the village raid center");
        helper.assertTrue(raiders.stream().allMatch(goblin -> goblin.raidWave() == 1),
            "every wave member must retain its wave number");
        helper.assertValueEqual(raiders.stream().filter(HobgoblinEntity::isRaidLeader).count(), 1L,
            "a goblin raid wave must have exactly one leader");
        helper.assertTrue(raiders.stream().allMatch(goblin -> goblin.getTarget() == villager),
            "the raid group must coordinate on the same human villager target");
        helper.succeed();
    }

    public static void hobgoblinsFleeHumanVillagersAndKeepCustomProfessions(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-5, 0, -5), new BlockPos(7, 0, 7))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final HobgoblinEntity hobgoblin = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );
        final Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        villager.setNoAi(true);
        final double startingDistance = hobgoblin.distanceToSqr(villager);

        helper.assertTrue(hobgoblin.hasCustomName() && hobgoblin.isCustomNameVisible(),
            "a naturally spawned hobgoblin must expose its assigned goblin profession");
        helper.assertTrue(!hobgoblin.getVillagerData().profession().is(VillagerProfession.NITWIT),
            "a hobgoblin's internal profession must never be nitwit");
        helper.assertTrue(hobgoblin.getVillagerData().profession().is(hobgoblin.goblinProfession().engineProfession()),
            "a hobgoblin's internal profession must match its visible custom profession");
        final boolean[] escaped = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(hobgoblin.getTarget() == null,
                "friendly hobgoblins must never target human villagers");
            escaped[0] |= hobgoblin.distanceToSqr(villager) > startingDistance + 4.0;
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(escaped[0],
                "friendly hobgoblins must flee nearby human villagers");
            helper.succeed();
        });
    }

    private static HobgoblinEntity createBaby(
        final GameTestHelper helper,
        final EntityType<HobgoblinEntity> type,
        final BlockPos firstPosition,
        final BlockPos secondPosition,
        final BlockPos childPosition
    ) {
        helper.setBlock(firstPosition.below(), Blocks.STONE);
        helper.setBlock(secondPosition.below(), Blocks.STONE);
        helper.setBlock(childPosition.below(), Blocks.STONE);
        final HobgoblinEntity first = helper.spawn(type, firstPosition, EntitySpawnReason.NATURAL);
        final HobgoblinEntity second = helper.spawn(type, secondPosition, EntitySpawnReason.NATURAL);
        first.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        second.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        helper.assertTrue(first.canBreed() && second.canBreed(),
            "fed adult goblinfolk must be eligible to reproduce in a settlement");
        final var created = first.getBreedOffspring(helper.getLevel(), second);
        helper.assertTrue(created instanceof HobgoblinEntity,
            "goblinfolk breeding must create a Warlockery child instead of a vanilla villager");
        final HobgoblinEntity child = (HobgoblinEntity) created;
        child.setAge(-24_000);
        final BlockPos absolute = helper.absolutePos(childPosition);
        child.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(child);
        helper.assertTrue(child.getType() == type && child.creatureKind() == first.creatureKind(),
            "goblinfolk children must retain their parents' registered species");
        return child;
    }

    private static void finishPersistentTradeTest(
        final GameTestHelper helper,
        final ServerPlayer player,
        final HobgoblinEntity trader,
        final String species
    ) {
        helper.assertTrue(trader.isAlive() && !trader.isRemoved(), species + " trader must remain alive");
        helper.assertTrue(player.containerMenu instanceof MerchantMenu,
            species + " trade menu must remain open across server AI ticks");
        helper.assertTrue(trader.getTradingPlayer() == player,
            species + " trader must retain its customer until the player closes the menu");
        player.closeContainer();
        helper.succeed();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(new BlockPos(1, 1, 2));
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }
}
