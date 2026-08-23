package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.event.BreakSpeedContext;
import com.kadamitas.warlockery.fabric.event.PlayerCloneContext;
import com.kadamitas.warlockery.item.BloodGobletItem;
import com.kadamitas.warlockery.item.BloodGobletState;
import com.kadamitas.warlockery.item.ManualItem;
import com.kadamitas.warlockery.item.ManualProgress;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class SupernaturalProgressionGameTests {
    private SupernaturalProgressionGameTests() {
    }

    public static void tornPageUseRevealsOnlyTheNextImmortalLesson(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack observations = new ItemStack(ModItems.ALL.get("vampirebook").get());
        final ItemStack pages = new ItemStack(ModItems.ALL.get("ingredient_vbook_page").get(), 2);
        player.getInventory().setItem(1, observations);
        player.setItemInHand(InteractionHand.MAIN_HAND, pages);

        helper.assertValueEqual(
            pages.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND),
            InteractionResult.SUCCESS,
            "using a Torn Page while carrying Observations of an Immortal"
        );

        final ManualItem manual = (ManualItem) observations.getItem();
        helper.assertValueEqual(
            ManualProgress.insertedTornPages(manual.profile(), observations),
            1,
            "inserted Torn Page count"
        );
        helper.assertValueEqual(
            ManualProgress.visibleSections(manual.profile(), observations).getLast(),
            "vampire_level_2",
            "only the next vampire lesson"
        );
        helper.assertValueEqual(pages.getCount(), 1, "remaining Torn Pages");
        helper.succeed();
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
        WarlockeryEntityData.get(target).putBoolean("WarlockeryCreationTargetDrained", true);
        WarlockeryEntityData.get(target).putString("WarlockeryMesmerizedBy", creator.getStringUUID());
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

    public static void transformedWerewolvesDigDirtAndSandFaster(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginWerewolf(player);
        SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, 3);
        SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLF);

        final BreakSpeedContext dirt = new BreakSpeedContext(player, Blocks.DIRT.defaultBlockState(), 1.0F);
        final BreakSpeedContext sand = new BreakSpeedContext(player, Blocks.SAND.defaultBlockState(), 1.0F);
        final BreakSpeedContext stone = new BreakSpeedContext(player, Blocks.STONE.defaultBlockState(), 1.0F);
        SupernaturalProgressionRuntime.handleBreakSpeed(dirt);
        SupernaturalProgressionRuntime.handleBreakSpeed(sand);
        SupernaturalProgressionRuntime.handleBreakSpeed(stone);

        helper.assertValueEqual(dirt.getNewSpeed(), SupernaturalAbilityRules.WOLF_DIG_MIN_SPEED,
            "wolf-form dirt digging speed");
        helper.assertValueEqual(sand.getNewSpeed(), SupernaturalAbilityRules.WOLF_DIG_MIN_SPEED,
            "wolf-form sand digging speed");
        helper.assertValueEqual(stone.getNewSpeed(), 1.0F,
            "wolf-form stone digging speed");

        SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLFMAN);
        final BreakSpeedContext wolfmanDirt = new BreakSpeedContext(
            player, Blocks.DIRT.defaultBlockState(), 1.0F
        );
        SupernaturalProgressionRuntime.handleBreakSpeed(wolfmanDirt);
        helper.assertValueEqual(wolfmanDirt.getNewSpeed(), 1.0F,
            "wolfman dirt digging speed");

        player.setShiftKeyDown(true);
        final BreakSpeedContext crouchingWolfmanSand = new BreakSpeedContext(
            player, Blocks.SAND.defaultBlockState(), 1.0F
        );
        SupernaturalProgressionRuntime.handleBreakSpeed(crouchingWolfmanSand);
        helper.assertValueEqual(crouchingWolfmanSand.getNewSpeed(), 1.0F,
            "crouching wolfman sand digging speed");

        SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLF);
        final BreakSpeedContext crouchingWolfDirt = new BreakSpeedContext(
            player, Blocks.DIRT.defaultBlockState(), 1.0F
        );
        SupernaturalProgressionRuntime.handleBreakSpeed(crouchingWolfDirt);
        helper.assertValueEqual(crouchingWolfDirt.getNewSpeed(), 30.0F,
            "crouching four-legged wolf dirt digging speed");
        player.setShiftKeyDown(false);
        helper.succeed();
    }

    public static void vampireBloodReplacesHungerAndRegenerates(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginVampire(player);
        SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.VAMPIRE, 10);
        final int maximum = SupernaturalProgression.maximumResource(SupernaturalProgression.Path.VAMPIRE, 10);
        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, maximum);
        helper.assertTrue(SupernaturalProgression.sanguine(player),
            "filling blood latches persisted Sanguine immediately");
        helper.assertTrue(SupernaturalProgressionRuntime.snapshot(player).sanguine(),
            "the immediate full-blood snapshot includes Sanguine");
        final ServerPlayer copied = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        SupernaturalState.copyAfterClone(new PlayerCloneContext(player, copied, false));
        helper.assertTrue(SupernaturalProgression.sanguine(copied),
            "Sanguine survives the Fabric attachment clone path");
        player.setHealth(player.getMaxHealth() - 4.0F);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.getFoodData().setFoodLevel(1);
        player.getFoodData().setSaturation(5.0F);
        player.causeFoodExhaustion(4.0F);
        player.tickCount = VampireSustenanceRules.REGENERATION_INTERVAL_TICKS;
        player.getFoodData().tick(player);
        SupernaturalProgressionRuntime.tick(player);
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), VampireSustenanceRules.NEUTRAL_FOOD_LEVEL,
            "the Fabric living-tail hook restores neutral food after vanilla FoodData updates");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0.0F,
            "the Fabric living-tail hook restores zero saturation after vanilla FoodData updates");
        helper.assertValueEqual(player.getHealth(), player.getMaxHealth() - 3.0F,
            "tail-ordered Sanguine heals exactly one health");
        helper.assertValueEqual(SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE),
            maximum - VampireSustenanceRules.regenerationBloodCost(maximum),
            "tail-ordered Sanguine spends one percent blood");
        for (int tick = 1; tick <= 3; tick++) {
            player.causeFoodExhaustion(8.0F);
            player.tickCount = VampireSustenanceRules.REGENERATION_INTERVAL_TICKS + tick;
            player.getFoodData().tick(player);
            SupernaturalProgressionRuntime.tick(player);
            helper.assertValueEqual(
                player.getFoodData().getFoodLevel(),
                VampireSustenanceRules.NEUTRAL_FOOD_LEVEL,
                "repeated exhaustion cannot move vampire food away from the public neutral value"
            );
            helper.assertValueEqual(
                player.getFoodData().getSaturationLevel(),
                0.0F,
                "repeated exhaustion cannot create vampire saturation"
            );
            helper.assertValueEqual(
                player.getHealth(),
                player.getMaxHealth() - 3.0F,
                "the public neutral food value cannot trigger vanilla natural healing"
            );
        }
        final int belowThreshold = maximum * 9 / 10 - 1;
        final int current = SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE);
        helper.assertTrue(SupernaturalProgression.spend(
            player, SupernaturalProgression.Path.VAMPIRE, current - belowThreshold
        ), "threshold-crossing blood spend succeeds");
        helper.assertFalse(SupernaturalProgression.sanguine(player),
            "threshold-crossing spend clears persisted Sanguine immediately");
        helper.assertFalse(SupernaturalProgressionRuntime.snapshot(player).sanguine(),
            "the next snapshot cannot expose stale Sanguine");
        helper.succeed();
    }

    public static void vampireSunlightIgnoresFireResistance(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        SupernaturalAdvancement.beginVampire(player);
        SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.VAMPIRE, 5);
        final int maximum = SupernaturalProgression.maximumResource(SupernaturalProgression.Path.VAMPIRE, 5);
        final int cost = SupernaturalAbilityRules.sunlightBloodCost(5, maximum);
        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, cost);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        player.invulnerableTime = 0;
        final float health = player.getHealth();
        final var source = VampireDamageTypes.sunlight(helper.getLevel());
        helper.assertFalse(source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE), "owned sunlight is not fire damage");
        player.tickCount = 40;
        SupernaturalState.applyVampireSunlight(player, 5);
        helper.assertValueEqual(player.getHealth(), health,
            "a successful Sun Resistance blood payment prevents owned sunlight damage");
        helper.assertValueEqual(SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE), 0,
            "Sun Resistance consumes its exact blood payment");
        player.invulnerableTime = 0;
        player.tickCount = 60;
        SupernaturalState.applyVampireSunlight(player, 5);
        helper.assertTrue(player.getHealth() < health, "Fire Resistance does not cancel owned sunlight");

        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, maximum);
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        helper.assertTrue(player.hurtServer(helper.getLevel(), player.damageSources().generic(), 50.0F),
            "an ordinary lethal hit still lands on a vampire protected by the death ward");
        helper.assertTrue(player.isAlive(), "the supernatural death ward preserves the vampire");
        helper.assertValueEqual(player.getHealth(), 1.0F,
            "the supernatural death ward clamps real health to half a heart");
        helper.assertTrue(player.hurtTime > 0,
            "the nonzero clamped damage preserves vanilla hit reaction and knockback processing");
        helper.assertFalse(player.hasEffect(MobEffects.ABSORPTION),
            "the supernatural death ward grants no absorption effect");
        helper.assertValueEqual(player.getAbsorptionAmount(), 0.0F,
            "the supernatural death ward grants no yellow absorption hearts");

        final int wardReserve = 150;
        helper.assertTrue(wardReserve < cost, "the lethal regression begins below the Sun Resistance payment");
        SupernaturalProgression.setResource(player, SupernaturalProgression.Path.VAMPIRE, wardReserve);
        player.setHealth(0.5F);
        player.invulnerableTime = 0;
        player.tickCount = 80;
        SupernaturalState.applyVampireSunlight(player, 5);
        helper.assertFalse(player.isAlive(), "owned sunlight bypasses the vampire death ward and kills");
        helper.assertValueEqual(
            SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE),
            wardReserve,
            "owned sunlight never spends the vampire death-ward reserve");
        helper.succeed();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return GameTestMockPlayers.autoDisconnect(helper, player);
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
