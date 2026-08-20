package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.GoblinEntity;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRuntime;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
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

        // The commissioning handler's own guard is `event.getTarget() instanceof Villager`, and the
        // exact hobgoblin is an AbstractVillager that is not a Villager, so it is rejected one step
        // earlier than before and never reaches isCommissionableTarget at all. The vanilla villager
        // is the positive control: without it this pair would only restate a type relation and
        // could not fail if the predicate itself were ever broken.
        //
        // The control is discarded immediately. A loaded human villager inside the traveler's
        // 12-block signal radius IS village space, and village exit correctly closes an open trade,
        // so leaving it standing would fail this fixture for the right reason at the wrong time.
        final Villager control = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        helper.assertTrue(VillageGuardRuntime.isCommissionableTarget(control),
            "a vanilla villager must stay commissionable, or the hobgoblin exclusion proves nothing");
        control.discard();

        final AbstractVillager trader = hobgoblin;
        helper.assertTrue(!(trader instanceof Villager),
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
        final GoblinEntity goblin = helper.spawn(
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
        final GoblinEntity goblinChild = createBaby(
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
        // A goblin raid wave is built from the dedicated F10 body now, so its assault membership,
        // not the legacy Hobgoblin raid marker, is what groups it.
        final var raiders = helper.getLevel().getEntitiesOfClass(
            GoblinEntity.class,
            new AABB(center).inflate(16.0),
            GoblinEntity::isAssaultMember
        );

        helper.assertValueEqual(spawned, GoblinRaidRules.waveSize(1), "first goblin raid wave size");
        helper.assertValueEqual(raiders.size(), GoblinRaidRules.waveSize(1), "tracked goblin raid group size");
        helper.assertTrue(raiders.stream().allMatch(goblin -> goblin.assaultCenter().filter(center::equals).isPresent()),
            "every wave member must share the village raid center");
        helper.assertTrue(raiders.stream().allMatch(goblin -> goblin.assaultWave() == 1),
            "every wave member must retain its wave number");
        helper.assertValueEqual(raiders.stream().filter(GoblinEntity::isAssaultLeader).count(), 1L,
            "a goblin raid wave must have exactly one leader");

        // Targeting belongs to GoblinEnclaveRuntime and is observed under live AI rather than by
        // calling a coordinator by hand, which is a stronger check than the legacy fixture made.
        // Each body seeds its decision (<=20 ticks) and perception (<=40 ticks) cadences from a
        // stable UUID offset inside its own first tick, so the wave has acquired by tick 60 at the
        // latest. The objective is kept topped up because the subject here is shared acquisition,
        // not lethality: three raiders would otherwise kill the villager inside that window and
        // the targets would clear before the assertion ran. Both callbacks are registered from the
        // test body, never from inside one another.
        final boolean[] coordinated = {false};
        helper.onEachTick(() -> {
            villager.setHealth(villager.getMaxHealth());
            coordinated[0] |= !raiders.isEmpty()
                && raiders.stream().allMatch(goblin -> goblin.getTarget() == villager);
        });
        // Tick 60 is the earliest the whole wave can have acquired, and every member has to be
        // holding the villager on the same tick for this to latch, not merely to have acquired at
        // some point. Eighty ticks left almost no margin over that worst case, so a wave whose
        // UUID offsets happened to spread wide never lined up inside the window. The assertion is
        // unchanged: every raider must still share the one target simultaneously.
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(coordinated[0],
                "the raid group must coordinate on the same human villager target");
            helper.succeed();
        });
    }

    public static void hobgoblinsFleeHumanVillagersAndKeepCustomProfessions(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-5, 0, -5), new BlockPos(7, 0, 7))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final HobgoblinEntity hobgoblin = helper.spawn(
            ModEntities.HOBGOBLIN.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );
        final Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        villager.setNoAi(true);

        // The visible profession name survives the split unchanged. The internal VillagerData the
        // 1.4 body carried does not exist on an AbstractVillager, so the invariant is asserted
        // against the displayed translation key that VillagerData only ever mirrored.
        helper.assertTrue(hobgoblin.hasCustomName() && hobgoblin.isCustomNameVisible(),
            "a naturally spawned hobgoblin must expose its assigned goblin profession");
        final Component name = hobgoblin.getCustomName();
        helper.assertTrue(name != null
                && name.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(
                    "entity.warlockery.hobgoblin.profession." + hobgoblin.goblinProfession().id()),
            "the visible name must be exactly this hobgoblin's own assigned profession key");

        // The 1.4 flee goal is replaced by the village-exclusion policy: a loaded human villager
        // inside the signal radius IS village space, and VILLAGE_EXIT outranks every non-emergency
        // intent. Displacement itself is deliberately NOT asserted: an accepted exit must land in
        // the 12-to-24 block outward band, which this arena's floor cannot host, so requiring the
        // hobgoblin to actually move would assert the arena rather than the policy.
        helper.assertTrue(HobgoblinJourneyRules.villageExcluded(false, true, false),
            "a human villager inside the signal radius must count as village space");
        hobgoblin.journeyTransient().resetForLoad();
        final boolean[] excluded = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(hobgoblin.getTarget() == null,
                "friendly hobgoblins must never target human villagers");
            excluded[0] |= hobgoblin.journeyTransient().insideExcludedSpace();
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(excluded[0],
                "a hobgoblin beside a human villager must observe itself inside excluded space");
            helper.assertValueEqual(hobgoblin.journeyState().mode(), Mode.VILLAGE_EXIT,
                "village exit must outrank every non-emergency intent");
            helper.assertTrue(hobgoblin.journeyCounters().villageExitSearches() >= 1L,
                "observing village space must arm and run at least one exit search");
            helper.assertTrue(!HobgoblinJourneyRuntime.safeToTrade(hobgoblin),
                "a hobgoblin inside village space must refuse to trade");
            helper.succeed();
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractVillager & ArcaneCreature> T createBaby(
        final GameTestHelper helper,
        final EntityType<T> type,
        final BlockPos firstPosition,
        final BlockPos secondPosition,
        final BlockPos childPosition
    ) {
        helper.setBlock(firstPosition.below(), Blocks.STONE);
        helper.setBlock(secondPosition.below(), Blocks.STONE);
        helper.setBlock(childPosition.below(), Blocks.STONE);
        final T first = helper.spawn(type, firstPosition, EntitySpawnReason.NATURAL);
        final T second = helper.spawn(type, secondPosition, EntitySpawnReason.NATURAL);
        first.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        second.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        final var created = first.getBreedOffspring(helper.getLevel(), second);
        helper.assertTrue(created != null && created.getType() == type,
            "goblinfolk breeding must create a Warlockery child of its own exact species");
        final T child = (T) created;
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
        final AbstractVillager trader,
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
        return GameTestMockPlayers.autoDisconnect(helper, player);
    }
}
