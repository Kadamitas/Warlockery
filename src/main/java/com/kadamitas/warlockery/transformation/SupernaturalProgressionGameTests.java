package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.item.BloodGobletItem;
import com.kadamitas.warlockery.item.BloodGobletState;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

public final class SupernaturalProgressionGameTests {
    private SupernaturalProgressionGameTests() {
    }

    public static void vampirePathInitiatesDiagnosesAndAdvances(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);

        helper.assertTrue(SupernaturalAdvancement.beginVampire(player), "vampire initiation must succeed");
        helper.assertValueEqual(SupernaturalState.getForm(player), SupernaturalForm.VAMPIRE,
            "form after vampire initiation");
        helper.assertValueEqual(
            SupernaturalProgression.level(player, SupernaturalProgression.Path.VAMPIRE),
            1,
            "vampire level after initiation"
        );
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE),
            125,
            "initial vampire blood"
        );
        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, 7);
        helper.assertTrue(SupernaturalAdvancement.beginVampire(player),
            "repeating an already-active vampire initiation must remain harmless");
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE),
            7,
            "repeated vampire initiation must not refill blood"
        );
        helper.assertFalse(SupernaturalAdvancement.beginWerewolf(player),
            "an active vampire path must reject werewolf initiation");
        helper.assertValueEqual(
            SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF),
            0,
            "rejected werewolf level"
        );

        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, 10_000);
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE),
            750,
            "level-one blood capacity"
        );

        final SupernaturalAdvancement.ProgressUpdate rejected = SupernaturalAdvancement.advanceVampireIfReady(player);
        helper.assertFalse(rejected.advanced(), "an incomplete vampire trial must not advance");
        helper.assertValueEqual(
            rejected.messageKey(),
            VampireProgressionRules.Diagnostic.OBSERVATIONS_MANUAL_REQUIRED.messageKey(),
            "missing vampire requirement diagnostic"
        );
        final ModNetwork.SupernaturalSnapshot incomplete = SupernaturalProgressionRuntime.snapshot(player);
        helper.assertValueEqual(incomplete.questTitle(), "quest.warlockery.vampire.brimming_reserve",
            "active vampire quest shown by the HUD");
        helper.assertValueEqual(incomplete.questProgress(), "0 / 1", "incomplete vampire HUD progress");

        SupernaturalAdvancement.recordVampire(
            player,
            VampireProgressionRules.Metric.OBSERVATIONS_MANUAL_OWNED,
            1
        );
        SupernaturalAdvancement.recordVampireValue(
            player,
            VampireProgressionRules.Metric.TORN_PAGES_INSERTED,
            1
        );
        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, 750);
        final SupernaturalAdvancement.ProgressUpdate advanced = SupernaturalAdvancement.recordVampireValue(
            player,
            VampireProgressionRules.Metric.BLOOD_STORED,
            750
        );

        helper.assertTrue(advanced.advanced(), "the completed vampire trial must advance");
        helper.assertValueEqual(advanced.level(), 2, "vampire level after the first survival trial");
        helper.assertValueEqual(
            SupernaturalProgression.maximumResource(SupernaturalProgression.Path.VAMPIRE, advanced.level()),
            1_000,
            "level-two blood capacity"
        );
        final ModNetwork.SupernaturalSnapshot next = SupernaturalProgressionRuntime.snapshot(player);
        helper.assertValueEqual(next.questTitle(), "quest.warlockery.vampire.five_crimson_marks",
            "next vampire quest shown by the HUD");
        helper.succeed();
    }

    public static void vampireCreationRejectsForeignGobletThenCompletesPath(final GameTestHelper helper) {
        final ServerPlayer creator = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginVampire(creator);
        SupernaturalProgression.setLevel(creator, SupernaturalProgression.Path.VAMPIRE, 9);
        SupernaturalProgression.setCounter(
            creator,
            SupernaturalProgression.Path.VAMPIRE,
            VampireProgressionRules.Metric.OBSERVATIONS_MANUAL_OWNED,
            1
        );
        SupernaturalProgression.setCounter(
            creator,
            SupernaturalProgression.Path.VAMPIRE,
            VampireProgressionRules.Metric.TORN_PAGES_INSERTED,
            9
        );

        final BlockPos targetPosition = new BlockPos(1, 1, 1);
        final Villager target = helper.spawn(EntityTypes.VILLAGER, targetPosition);
        final Villager foreignOwner = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 2));
        target.getPersistentData().putBoolean("WarlockeryCreationTargetDrained", true);
        target.getPersistentData().putString("WarlockeryMesmerizedBy", creator.getStringUUID());
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.ALL.get("coffinblock").get());

        final BloodGobletItem gobletItem = (BloodGobletItem) ModItems.ALL.get("glassgoblet").get();
        final ItemStack foreignGoblet = new ItemStack(gobletItem);
        SympatheticBinding.from(foreignOwner).write(foreignGoblet);
        BloodGobletState.setFull(foreignGoblet, true);

        helper.assertValueEqual(
            gobletItem.interactLivingEntity(foreignGoblet, creator, target, InteractionHand.MAIN_HAND),
            InteractionResult.FAIL,
            "foreign-bound goblet interaction result"
        );
        helper.assertTrue(BloodGobletState.isFull(foreignGoblet),
            "a rejected foreign-bound goblet must remain full");
        helper.assertFalse(target.isRemoved(), "a rejected goblet must not remove the target villager");
        helper.assertTrue(vampiresNear(helper, targetPosition).isEmpty(),
            "a rejected goblet must not create a vampire");
        helper.assertValueEqual(
            SupernaturalProgression.level(creator, SupernaturalProgression.Path.VAMPIRE),
            9,
            "vampire level after rejected creation"
        );

        final ItemStack creatorGoblet = new ItemStack(gobletItem);
        SympatheticBinding.from(creator).write(creatorGoblet);
        BloodGobletState.setFull(creatorGoblet, true);
        helper.assertValueEqual(
            gobletItem.interactLivingEntity(creatorGoblet, creator, target, InteractionHand.MAIN_HAND),
            InteractionResult.SUCCESS,
            "creator-bound goblet interaction result"
        );

        helper.assertFalse(BloodGobletState.isFull(creatorGoblet),
            "successful creation must empty the creator's goblet");
        helper.assertTrue(target.isRemoved(), "successful creation must replace the target villager");
        helper.assertValueEqual(vampiresNear(helper, targetPosition).size(), 1,
            "created Warlockery vampire count");
        helper.assertValueEqual(
            SupernaturalProgression.level(creator, SupernaturalProgression.Path.VAMPIRE),
            10,
            "vampire level after completing creation"
        );
        helper.assertValueEqual(
            SupernaturalProgression.counter(
                creator,
                SupernaturalProgression.Path.VAMPIRE,
                VampireProgressionRules.Metric.TORN_PAGES_INSERTED
            ),
            9,
            "all Torn Pages remain recorded after completing the path"
        );
        helper.succeed();
    }

    public static void werewolfAltarDiagnosesAndAdvances(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);

        helper.assertTrue(SupernaturalAdvancement.beginWerewolf(player), "werewolf initiation must succeed");
        helper.assertValueEqual(SupernaturalState.getForm(player), SupernaturalForm.WEREWOLF,
            "form after werewolf initiation");
        helper.assertValueEqual(
            SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF),
            1,
            "werewolf level after initiation"
        );
        helper.assertFalse(SupernaturalAdvancement.beginVampire(player),
            "an active werewolf path must reject vampire initiation");

        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.WEREWOLF, 10_000);
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.WEREWOLF),
            120,
            "level-one ferocity capacity"
        );
        final ModNetwork.SupernaturalSnapshot incomplete = SupernaturalProgressionRuntime.snapshot(player);
        helper.assertValueEqual(incomplete.questTitle(), "quest.warlockery.werewolf.gilded_leash",
            "active werewolf quest shown by the HUD");
        helper.assertValueEqual(incomplete.questProgress(), "0 / 3", "incomplete werewolf HUD progress");

        final ItemStack shortOffering = new ItemStack(Items.GOLD_INGOT, 2);
        final SupernaturalAdvancement.WolfAltarResult rejected = SupernaturalAdvancement.useWolfAltar(
            player,
            shortOffering
        );
        helper.assertFalse(rejected.accepted(), "the altar must reject an incomplete gold offering");
        helper.assertValueEqual(
            rejected.messageKey(),
            WerewolfProgressionRules.Diagnostic.GOLD_INGOTS_REQUIRED.messageKey(),
            "Wolf Altar missing-offering diagnostic"
        );
        helper.assertValueEqual(shortOffering.getCount(), 2, "rejected offering must remain untouched");
        helper.assertValueEqual(
            SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF),
            1,
            "werewolf level after rejected offering"
        );

        final ItemStack completeOffering = new ItemStack(Items.GOLD_INGOT, 3);
        final SupernaturalAdvancement.WolfAltarResult advanced = SupernaturalAdvancement.useWolfAltar(
            player,
            completeOffering
        );
        helper.assertTrue(advanced.accepted(), "the altar must accept the complete gold offering");
        helper.assertTrue(advanced.advanced(), "the complete Wolf Altar trial must advance");
        helper.assertValueEqual(advanced.level(), 2, "werewolf level after the gold trial");
        helper.assertValueEqual(advanced.consumed(), 3, "gold consumed by the Wolf Altar");
        helper.assertTrue(completeOffering.isEmpty(), "accepted gold offering must be consumed");
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.WEREWOLF),
            140,
            "level-two ferocity capacity"
        );
        helper.assertValueEqual(
            player.getInventory().countItem(ModItems.ALL.get("mooncharm").get()),
            1,
            "Moon Charm reward count"
        );
        final ModNetwork.SupernaturalSnapshot next = SupernaturalProgressionRuntime.snapshot(player);
        helper.assertValueEqual(next.questTitle(), "quest.warlockery.werewolf.shepherds_reckoning",
            "next werewolf quest shown by the HUD");
        helper.assertValueEqual(next.questProgress(), "0 / 30", "next werewolf HUD progress");
        helper.succeed();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static java.util.List<Mob> vampiresNear(final GameTestHelper helper, final BlockPos relativePosition) {
        final BlockPos position = helper.absolutePos(relativePosition);
        return helper.getLevel().getEntitiesOfClass(
            Mob.class,
            new AABB(position).inflate(2.0D),
            entity -> entity.getType() == ModEntities.ALL.get("vampire").get()
        );
    }
}
