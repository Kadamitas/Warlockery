package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.WolfAltarRuntime;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.brew.custom.CustomBrewFormula;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.ImpEntity;
import com.kadamitas.warlockery.entity.LycanVillagerEntity;
import com.kadamitas.warlockery.entity.StormSimianEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.DollMendingSchedule;
import com.kadamitas.warlockery.item.DollRules;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class WarlockeryGameTests {
    private WarlockeryGameTests() {
    }

    private static CustomBrewFormula customStrengthFormula(final boolean skipEntities) {
        return new CustomBrewFormula(
            List.of("warlockery:test_strength"),
            List.of("minecraft:strength"),
            CustomBrewDelivery.DRINKABLE,
            List.of(new BrewEffectSpec("minecraft:strength", 200, 0)),
            List.of(),
            8,
            1,
            0,
            1,
            1,
            1,
            0,
            0x932423,
            1.0F,
            1.0F,
            false,
            false,
            skipEntities,
            false,
            0
        );
    }

    public static void ritualCatalogLoads(final GameTestHelper helper) {
        helper.assertValueEqual(RitualManager.INSTANCE.ids().size(), 101, "loaded ritual count");
        helper.assertTrue(
            RitualManager.INSTANCE.ids().contains(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "sanctity")),
            "the built-in ritual catalog must remain loaded"
        );
        helper.succeed();
    }

    public static void ritualSessionsRejectDuplicateCenters(final GameTestHelper helper) {
        final RitualSessionData sessions = RitualSessionData.get(helper.getLevel());
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Identifier ritual = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "sanctity");
        helper.assertTrue(sessions.start(center, ritual, UUID.randomUUID(), 40), "first ritual session should start");
        helper.assertFalse(sessions.start(center, ritual, UUID.randomUUID(), 40), "duplicate ritual session must be rejected");
        helper.assertTrue(sessions.isActive(center), "session should remain active");
        helper.succeed();
    }

    public static void drinkableCustomBrewAppliesItsFormula(final GameTestHelper helper) {
        final Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        final CustomBrewFormula blocked = customStrengthFormula(true);
        helper.assertValueEqual(
            CustomBrewRuntime.applyDrinkEffects(helper.getLevel(), blocked, target),
            0,
            "skip-entity formula result"
        );
        helper.assertFalse(target.hasEffect(MobEffects.STRENGTH), "skip-entity formula must not affect the drinker");
        final CustomBrewFormula active = customStrengthFormula(false);
        helper.assertValueEqual(
            CustomBrewRuntime.applyDrinkEffects(helper.getLevel(), active, target),
            1,
            "drinkable formula result"
        );
        helper.assertTrue(target.hasEffect(MobEffects.STRENGTH), "drinkable formula must affect the drinker");
        helper.succeed();
    }

    public static void sanctityWardRepelsHostilesImmediately(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        final Zombie target = helper.spawn(EntityTypes.ZOMBIE, relativeCenter);
        RitualManager.INSTANCE.complete(
            helper.getLevel(), helper.absolutePos(relativeCenter), null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "sanctity")
        );
        helper.assertTrue(
            RitualWardData.get(helper.getLevel()).contains(
                RitualWardType.SANCTITY,
                target.position(),
                helper.getLevel().getGameTime()
            ),
            "sanctity must create an active ward"
        );
        helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "sanctity must weaken hostile mobs immediately");
        helper.succeed();
    }

    public static void summonImpCreatesWarlockeryCreature(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "summon_imp")
        );
        final boolean spawned = !helper.getLevel().getEntitiesOfClass(
            ImpEntity.class, new AABB(center).inflate(8.0)
        ).isEmpty();
        helper.assertTrue(spawned, "summon_imp must create a native Warlockery imp");
        helper.succeed();
    }

    public static void murderousFlockSpawnsTargetedHexBats(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        final BlockPos center = helper.absolutePos(relativeCenter);
        final Zombie target = helper.spawn(EntityTypes.ZOMBIE, relativeCenter);
        final BrewRuntime.ImpactResult result = BrewRuntime.handleImpact(
            helper.getLevel(), BrewKind.MURDEROUS_FLOCK, Vec3.atCenterOf(center), null, null
        );
        final List<Mob> flock = helper.getLevel().getEntitiesOfClass(
            Mob.class,
            new AABB(center).inflate(8.0),
            entity -> entity instanceof ArcaneCreature creature
                && creature.creatureKind() == ArcaneCreature.CreatureKind.HEX_BAT
        );
        helper.assertTrue(result.affectedEntities() >= 3, "murderous flock must report its summoned bats");
        helper.assertTrue(flock.size() >= 3, "murderous flock must summon at least three hex bats");
        helper.assertTrue(flock.stream().allMatch(entity -> entity.getTarget() == target),
            "every summoned hex bat must acquire a nearby victim");
        helper.succeed();
    }

    public static void wingedCreaturesUseCustomEntityClasses(final GameTestHelper helper) {
        final BlockPos impPos = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos simianPos = helper.absolutePos(new BlockPos(2, 1, 1));
        final Entity imp = ModEntities.ALL.get("imp").get().spawn(
            helper.getLevel(), impPos, EntitySpawnReason.TRIGGERED
        );
        final Entity simian = ModEntities.ALL.get("storm_simian").get().spawn(
            helper.getLevel(), simianPos, EntitySpawnReason.TRIGGERED
        );
        helper.assertTrue(imp instanceof ImpEntity, "imp registry must instantiate the custom imp");
        helper.assertTrue(simian instanceof StormSimianEntity,
            "storm simian registry must instantiate the custom simian");
        helper.assertFalse(imp instanceof Vex, "imp must not reuse the Vex entity implementation");
        helper.assertFalse(simian instanceof Vex, "storm simian must not reuse the Vex entity implementation");
        helper.succeed();
    }

    public static void lycanVillagerTradesOnlyWithWerewolves(final GameTestHelper helper) {
        final BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        final Entity created = ModEntities.ALL.get("lycan_villager").get().spawn(
            helper.getLevel(), position, EntitySpawnReason.TRIGGERED
        );
        helper.assertTrue(created instanceof LycanVillagerEntity, "lycan villager must use its trade-gated class");
        final LycanVillagerEntity villager = (LycanVillagerEntity) created;
        helper.assertTrue(villager.getOffers().size() >= 3, "lycan villager must stock werewolf supplies");

        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.snapTo(position.getX() + 1.0, position.getY(), position.getZ() + 0.5);
        SupernaturalState.setForm(player, SupernaturalForm.NONE);
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
            "non-werewolves must not open the lycan villager trade menu");

        SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF);
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu != player.inventoryMenu,
            "werewolves must be able to open the lycan villager trade menu");
        player.closeContainer();
        helper.succeed();
    }

    public static void fertilityGrowsAndCures(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 1, 1);
        final BlockPos center = helper.absolutePos(relativeCenter);
        final BlockPos cropRelative = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.FARMLAND);
        helper.setBlock(cropRelative, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.snapTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
        final ZombieVillager zombieVillager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, new BlockPos(0, 1, 1));

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, player,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "fertility")
        );

        helper.assertTrue(helper.getBlockState(cropRelative).getValue(CropBlock.AGE) > 0,
            "fertility must grow tagged crops");
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "fertility must cure poison");
        helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "fertility must cure nausea");
        helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "fertility must cure blindness");
        helper.assertTrue(zombieVillager.isRemoved(), "fertility must cure zombie villagers");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(center).inflate(4.0)).isEmpty(),
            "fertility must create a restored villager");
        helper.succeed();
    }

    public static void naturesPowerRepairsGround(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos soilRelative = new BlockPos(2, 0, 1);
        final BlockPos vegetationRelative = new BlockPos(0, 1, 1);
        helper.setBlock(soilRelative, Blocks.COARSE_DIRT);
        helper.setBlock(new BlockPos(0, 0, 1), Blocks.DIRT);
        helper.setBlock(vegetationRelative, Blocks.DEAD_BUSH);

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "natures_power")
        );

        helper.assertBlockPresent(Blocks.GRASS_BLOCK, soilRelative);
        helper.assertBlockPresent(Blocks.SHORT_GRASS, vegetationRelative);
        helper.succeed();
    }

    public static void brokenEarthCreatesFissure(final GameTestHelper helper) {
        final BlockPos relativeCenter = new BlockPos(1, 2, 1);
        final BlockPos north = new BlockPos(1, 1, 0);
        final BlockPos south = new BlockPos(1, 1, 2);
        helper.setBlock(north, Blocks.STONE);
        helper.setBlock(south, Blocks.STONE);

        RitualManager.INSTANCE.complete(
            helper.getLevel(), helper.absolutePos(relativeCenter), null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "part_earth")
        );

        helper.assertBlockNotPresent(Blocks.STONE, north);
        helper.assertBlockPresent(Blocks.STONE, south);
        helper.succeed();
    }

    public static void earthsWrathMovesVolcanicFluid(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final RitualManager.RequirementStatus missing = RitualManager.actionEnvironmentRequirement(
            RitualAction.EARTHS_WRATH, 6, helper.getLevel(), center
        ).orElseThrow();
        helper.assertFalse(missing.met(), "Earth's Wrath UI must report a missing volcanic pool");
        final java.util.List<BlockPos> sources = java.util.List.of(
            helper.absolutePos(new BlockPos(0, 0, 0)),
            helper.absolutePos(new BlockPos(0, 0, 1)),
            helper.absolutePos(new BlockPos(1, 0, 0)),
            helper.absolutePos(new BlockPos(1, 0, 1))
        );
        sources.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState()));
        final RitualManager.RequirementStatus ready = RitualManager.actionEnvironmentRequirement(
            RitualAction.EARTHS_WRATH, 6, helper.getLevel(), center
        ).orElseThrow();
        helper.assertTrue(ready.met(), "Earth's Wrath UI must accept a sufficient volcanic pool");
        helper.assertValueEqual(ready.present(), 4, "volcanic sources shown in the ritual UI");

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "volcano")
        );

        helper.assertTrue(sources.stream().anyMatch(pos -> helper.getLevel().getFluidState(pos).isEmpty()),
            "Earth's Wrath must draw from underground fluid");
        helper.assertTrue(BlockPos.betweenClosedStream(center.above(), center.above(16))
            .anyMatch(pos -> helper.getLevel().getFluidState(pos).typeHolder().is(WarlockeryTags.Fluids.VOLCANIC_FLUIDS)),
            "Earth's Wrath must raise volcanic fluid above the circle");
        helper.succeed();
    }

    public static void skysWrathCallsTargetedLightning(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "storm")
        );

        helper.assertTrue(helper.getLevel().getWeatherData().isThundering(), "Sky's Wrath must start thunder");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(
            LightningBolt.class, target.getBoundingBox().inflate(2.0)
        ).isEmpty(), "Sky's Wrath must strike its target");
        helper.succeed();
    }

    public static void hellOnEarthUsesTaggedDemons(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        RitualTerrainPlan.fireRing(center, 9, 12).forEach(column -> {
            helper.getLevel().setBlockAndUpdate(column.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(column, Blocks.AIR.defaultBlockState());
        });

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hell_on_earth")
        );

        helper.assertTrue(!helper.getLevel().getEntities(
            (Entity) null,
            new AABB(center).inflate(16.0),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)
        ).isEmpty(), "Hell on Earth must summon tagged demons");
        helper.assertTrue(RitualTerrainPlan.fireRing(center, 9, 12).stream()
            .anyMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)),
            "Hell on Earth must create contained fire");
        helper.succeed();
    }

    public static void forestationPlacesTaggedSaplings(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        RitualTerrainPlan.forestColumns(center, 12).stream().limit(16)
            .forEach(column -> helper.getLevel().setBlockAndUpdate(column.below(), Blocks.DIRT.defaultBlockState()));

        RitualManager.INSTANCE.complete(
            helper.getLevel(), center, null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "forestation")
        );

        helper.assertTrue(BlockPos.betweenClosedStream(center.offset(-12, -1, -12), center.offset(12, 16, 12))
            .anyMatch(pos -> {
                final var state = helper.getLevel().getBlockState(pos);
                return state.is(WarlockeryTags.Blocks.RITUAL_SAPLINGS)
                    || state.is(WarlockeryTags.Blocks.RITUAL_LOGS);
            }), "forestation must place or grow tagged saplings");
        helper.succeed();
    }

    public static void allRegisteredCreaturesInstantiate(final GameTestHelper helper) {
        helper.assertValueEqual(ModEntities.ALL.size(), 47, "registered creature count");
        ModEntities.ALL.forEach((id, registration) -> {
            final Entity entity = registration.get().create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            helper.assertTrue(entity != null, "entity factory returned null for " + id);
            if (entity != null) entity.discard();
        });
        helper.succeed();
    }

    public static void werewolfHunterCarriesSilverAmmunition(final GameTestHelper helper) {
        final WerewolfHunterEntity hunter = helper.spawn(
            ModEntities.WEREWOLF_HUNTER.get(), new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL
        );
        helper.assertTrue(hunter.getMainHandItem().is(Items.CROSSBOW),
            "werewolf hunter must carry a vanilla crossbow");
        helper.assertTrue(hunter.getOffhandItem().is(ModItems.ALL.get("ingredient_bolt_silver").get()),
            "werewolf hunter must carry silver bolts");
        helper.succeed();
    }

    public static void wolfAltarFinalTrialAwardsHornOnce(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack offerings = new ItemStack(Items.BEEF, 2);
        SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, 9);

        final var finalTrial = WolfAltarRuntime.completeTrial(player, offerings);
        helper.assertTrue(finalTrial.hornEarned(), "final Wolf Altar trial must award the horn");
        helper.assertValueEqual(
            SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF),
            10,
            "werewolf level after final trial"
        );
        helper.assertValueEqual(offerings.getCount(), 1, "offering consumed by final trial");
        helper.assertValueEqual(hornCount(player), 1, "Horn of the Hunt reward count");

        final var completedPath = WolfAltarRuntime.completeTrial(player, offerings);
        helper.assertFalse(completedPath.advanced(), "completed Wolf Altar path must not advance again");
        helper.assertValueEqual(offerings.getCount(), 1, "completed path must not consume another offering");
        helper.assertValueEqual(hornCount(player), 1, "completed path must not duplicate its horn");
        helper.succeed();
    }

    public static void deathGuardUsesTotemRecoveryWithoutVanillaTrigger(final GameTestHelper helper) {
        final var player = connectedSurvivalPlayer(helper);
        final ItemStack doll = boundDoll(player, "death_guard_doll");
        player.getInventory().setItem(0, doll);
        player.setHealth(4.0F);
        final LivingDamageEvent.Pre event = damageEvent(player, helper.getLevel().damageSources().generic(), 20.0F);
        DollItem.handleDamage(event);
        helper.assertValueEqual(event.getNewDamage(), 0.0F, "lethal damage after death guard");
        helper.assertValueEqual(player.getHealth(), 1.0F, "death guard recovery health");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "death guard must use vanilla Totem regeneration");
        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION), "death guard must use vanilla Totem absorption");
        helper.assertFalse(doll.isEmpty(), "death guard must remain reusable after activation");
        helper.assertValueEqual(doll.getDamageValue(), 1, "death guard durability spent");
        helper.succeed();
    }

    public static void hungerGuardRestoresHungerAndSaturation(final GameTestHelper helper) {
        final var player = connectedSurvivalPlayer(helper);
        final ItemStack doll = boundDoll(player, "hunger_guard_doll");
        player.getInventory().setItem(0, doll);
        player.setHealth(1.0F);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0F);
        final LivingDamageEvent.Pre event = damageEvent(player, helper.getLevel().damageSources().starve(), 2.0F);
        DollItem.handleDamage(event);
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 20, "restored hunger");
        helper.assertTrue(player.getFoodData().getSaturationLevel() >= 10.0F, "hunger guard must restore saturation");
        helper.assertTrue(player.hasEffect(MobEffects.SATURATION), "hunger guard must apply vanilla Saturation");
        helper.succeed();
    }

    public static void mendingDollTradesItsDurability(final GameTestHelper helper) {
        final var player = connectedSurvivalPlayer(helper);
        final ItemStack doll = boundDoll(player, "tool_mending_doll");
        final ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(10);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        helper.assertTrue(
            DollItem.tryMendBoundEquipment(doll, helper.getLevel(), player),
            "bound mending doll must repair eligible equipment"
        );
        helper.assertValueEqual(sword.getDamageValue(), 8, "durability repaired by one doll charge");
        helper.assertValueEqual(doll.getDamageValue(), 1, "mending doll durability spent");
        helper.succeed();
    }

    public static void shelvedMendingDollsRepairOncePerSecond(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(20);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        final ItemStack firstDoll = boundDoll(player, "tool_mending_doll");
        final ItemStack secondDoll = boundDoll(player, "tool_mending_doll");
        final BlockPos firstShelfPosition = new BlockPos(0, 1, 1);
        final BlockPos secondShelfPosition = new BlockPos(2, 1, 1);
        helper.setBlock(firstShelfPosition, ModBlocks.ALL.get("doll_shelf").get());
        helper.setBlock(secondShelfPosition, ModBlocks.ALL.get("doll_shelf").get());
        final DollShelfBlockEntity firstShelf = helper.getBlockEntity(
            firstShelfPosition,
            DollShelfBlockEntity.class
        );
        final DollShelfBlockEntity secondShelf = helper.getBlockEntity(
            secondShelfPosition,
            DollShelfBlockEntity.class
        );
        firstShelf.setItem(0, firstDoll);
        secondShelf.setItem(0, secondDoll);

        final int[] previousDamage = {sword.getDamageValue()};
        final int[] repairs = {0};
        final long[] previousRepairTick = {-1L};
        helper.onEachTick(() -> {
            final int currentDamage = sword.getDamageValue();
            if (currentDamage == previousDamage[0]) {
                return;
            }
            helper.assertValueEqual(
                previousDamage[0] - currentDamage,
                DollRules.DURABILITY_REPAIRED_PER_CHARGE,
                "one shelf mending repair per cycle"
            );
            if (previousRepairTick[0] >= 0L) {
                helper.assertTrue(
                    helper.getTick() - previousRepairTick[0] >= DollMendingSchedule.INTERVAL_TICKS,
                    "duplicate shelved dolls must not repair inside the same second"
                );
            }
            repairs[0]++;
            previousDamage[0] = currentDamage;
            previousRepairTick[0] = helper.getTick();
            helper.assertValueEqual(
                firstDoll.getDamageValue() + secondDoll.getDamageValue(),
                repairs[0],
                "exactly one shelved doll charge per repair"
            );
            if (repairs[0] == 2) {
                helper.succeed();
            }
        });
    }

    public static void selfAppliedDollRemainsActiveOnShelf(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack doll = new ItemStack(ModItems.ALL.get("death_guard_doll").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, doll);
        ((DollItem) doll.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(DollItem.isBoundTo(doll, player), "right-clicking air must bind an eligible doll to its user");

        final BlockPos shelfPosition = new BlockPos(1, 1, 1);
        helper.setBlock(shelfPosition, ModBlocks.ALL.get("doll_shelf").get());
        final DollShelfBlockEntity shelf = helper.getBlockEntity(shelfPosition, DollShelfBlockEntity.class);
        helper.assertTrue(
            shelf.canPlaceItem(0, new ItemStack(ModItems.ALL.get("sympathetic_vial").get())),
            "doll shelves must accept sympathetic containers"
        );
        helper.assertFalse(shelf.canPlaceItem(0, new ItemStack(Items.COBBLESTONE)),
            "doll shelves must reject unrelated items");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        shelf.setItem(0, doll);
        helper.assertTrue(shelf.requiresChunkTicket(), "a shelf containing a bound doll must retain its chunk");

        player.setHealth(1.0F);
        final LivingDamageEvent.Pre event = damageEvent(
            player,
            helper.getLevel().damageSources().generic(),
            2.0F
        );
        DollItem.handleDamage(event);
        helper.assertValueEqual(event.getNewDamage(), 0.0F, "a shelved death guard must prevent lethal damage");
        helper.assertFalse(shelf.getItem(0).isEmpty(), "a shelved death guard must remain reusable");
        helper.assertValueEqual(shelf.getItem(0).getDamageValue(), 1, "shelved doll durability spent");
        shelf.setItem(0, ItemStack.EMPTY);
        helper.assertFalse(shelf.requiresChunkTicket(), "an empty shelf must release its chunk ticket");
        helper.succeed();
    }

    public static void altarAttachmentsInstallRenderAndShiftRemove(final GameTestHelper helper) {
        final BlockPos relativePosition = new BlockPos(1, 1, 1);
        final BlockPos position = helper.absolutePos(relativePosition);
        helper.setBlock(relativePosition, ModBlocks.ALTAR.get());
        final AltarBlockEntity altar = helper.getBlockEntity(relativePosition, AltarBlockEntity.class);
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);

        final ItemStack ritualKnife = new ItemStack(ModItems.ALL.get("ritual_knife").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ritualKnife);
        helper.getLevel().getBlockState(position).useItemOn(
            ritualKnife,
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        helper.assertTrue(altar.hasRangeFocus(), "ritual knife attachment must activate the altar range focus");
        helper.assertValueEqual(altar.attachmentCount(), 1, "ritual knife attachment count");

        final ItemStack candelabra = new ItemStack(ModItems.ALL.get("candelabra").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, candelabra);
        helper.getLevel().getBlockState(position).useItemOn(
            candelabra,
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        helper.assertValueEqual(altar.attachmentCount(), 2, "altar upgrade attachment count");
        helper.assertValueEqual(
            AltarUpgradeResolver.resolve(altar.attachmentUpgrades()).rechargeMultiplier(),
            2,
            "attached candelabra recharge multiplier"
        );
        helper.assertValueEqual(altar.attachmentStacks().size(), 2, "client-visible attachment snapshots");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.getLevel().getBlockState(position).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertValueEqual(altar.attachmentCount(), 1, "first shift-click removes the latest attachment");
        helper.getLevel().getBlockState(position).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertValueEqual(altar.attachmentCount(), 0, "second shift-click removes the ritual knife");
        helper.assertFalse(altar.hasRangeFocus(), "removing the ritual knife must clear the range focus");
        player.setShiftKeyDown(false);
        helper.succeed();
    }

    public static void chalkPlacesConnectedGlyphsAndSpendsDurability(final GameTestHelper helper) {
        final BlockPos firstSupport = new BlockPos(0, 1, 1);
        final BlockPos secondSupport = new BlockPos(1, 1, 1);
        helper.setBlock(firstSupport, Blocks.STONE);
        helper.setBlock(secondSupport, Blocks.STONE);
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack chalk = new ItemStack(ModItems.ALL.get("chalkritual").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, chalk);

        useOnTop(helper, player, firstSupport);
        helper.assertTrue(
            helper.getBlockState(firstSupport.above()).is(ModBlocks.ALL.get("circleglyphritual").get()),
            "ritual chalk must place its ritual glyph"
        );
        helper.assertValueEqual(chalk.getDamageValue(), 1, "chalk durability after first glyph");

        useOnTop(helper, player, secondSupport);
        final BlockState firstGlyph = helper.getBlockState(firstSupport.above());
        final BlockState secondGlyph = helper.getBlockState(secondSupport.above());
        helper.assertTrue(firstGlyph.getValue(ConnectedGlyphBlock.EAST), "first glyph must connect east");
        helper.assertTrue(secondGlyph.getValue(ConnectedGlyphBlock.WEST), "second glyph must connect west");
        helper.assertValueEqual(chalk.getDamageValue(), 2, "chalk durability after second glyph");

        useOnTop(helper, player, secondSupport);
        helper.assertValueEqual(chalk.getDamageValue(), 2, "failed placement must not spend chalk durability");
        helper.succeed();
    }

    public static void hexGuardBlocksHostileHex(final GameTestHelper helper) {
        final var player = connectedSurvivalPlayer(helper);
        final ItemStack doll = boundDoll(player, "hex_guard_doll");
        player.getInventory().setItem(0, doll);
        helper.assertTrue(DollItem.tryBlockHex(player), "hex guard must report that it blocked the hex");
        helper.assertValueEqual(doll.getDamageValue(), 1, "hex guard durability spent");
        helper.succeed();
    }

    public static void hexBehaviorAppliesAndRemovesItsEffect(final GameTestHelper helper) {
        final Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        final HexBehavior behavior = HexBehaviors.forTarget("misfortune");
        behavior.apply(target, 200);
        helper.assertTrue(target.hasEffect(MobEffects.UNLUCK), "misfortune must apply vanilla Unluck");
        behavior.remove(target);
        helper.assertFalse(target.hasEffect(MobEffects.UNLUCK), "hex removal must remove vanilla Unluck");
        helper.succeed();
    }

    public static void elementalGuardDollsUseVanillaRecovery(final GameTestHelper helper) {
        final ServerPlayer player = connectedSurvivalPlayer(helper);

        player.getInventory().setItem(0, boundDoll(player, "earth_guard_doll"));
        player.setHealth(1.0F);
        final LivingDamageEvent.Pre fall = damageEvent(player, helper.getLevel().damageSources().fall(), 2.0F);
        DollItem.handleDamage(fall);
        helper.assertValueEqual(fall.getNewDamage(), 0.0F, "earth guard lethal fall damage");
        helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "earth guard must apply vanilla Slow Falling");

        player.removeAllEffects();
        player.getInventory().setItem(0, boundDoll(player, "water_guard_doll"));
        player.setHealth(1.0F);
        player.setAirSupply(0);
        final LivingDamageEvent.Pre drowning = damageEvent(player, helper.getLevel().damageSources().drown(), 2.0F);
        DollItem.handleDamage(drowning);
        helper.assertValueEqual(drowning.getNewDamage(), 0.0F, "water guard lethal drowning damage");
        helper.assertValueEqual(player.getAirSupply(), player.getMaxAirSupply(), "water guard restored air");
        helper.assertTrue(player.hasEffect(MobEffects.WATER_BREATHING),
            "water guard must apply vanilla Water Breathing");

        player.removeAllEffects();
        player.getInventory().setItem(0, boundDoll(player, "fire_guard_doll"));
        player.setHealth(1.0F);
        final LivingDamageEvent.Pre lava = damageEvent(player, helper.getLevel().damageSources().lava(), 2.0F);
        DollItem.handleDamage(lava);
        helper.assertValueEqual(lava.getNewDamage(), 0.0F, "fire guard lethal lava damage");
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE),
            "fire guard must apply vanilla Fire Resistance");
        helper.succeed();
    }

    public static void machineProfileProcessesARealInventory(final GameTestHelper helper) {
        final BlockPos relative = new BlockPos(1, 1, 1);
        final BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.ALL.get("spinningwheel").get());
        final MagicMachineBlockEntity machine = helper.getBlockEntity(relative, MagicMachineBlockEntity.class);
        machine.setItem(0, new ItemStack(Items.STRING, 8));
        IntStream.range(0, 160).forEach(_ -> MagicMachineBlockEntity.serverTick(
            helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), machine
        ));
        helper.assertTrue(machine.getItem(0).isEmpty(), "spinning wheel must consume eight string");
        helper.assertTrue(machine.getItem(6).is(Items.WOOL.white()), "spinning wheel must produce white wool");
        helper.assertValueEqual(machine.getItem(6).getCount(), 1, "spinning wheel output count");
        helper.succeed();
    }

    public static void commonMaterialAndWoodTagsArePopulated(final GameTestHelper helper) {
        final ItemStack silverIngot = new ItemStack(ModItems.ALL.get("silver_ingot").get());
        final ItemStack delvealloyIngot = new ItemStack(ModItems.ALL.get("ingredient_delvealloyingot").get());
        helper.assertTrue(silverIngot.is(itemTag("ingots/silver")), "silver ingot must use c:ingots/silver");
        helper.assertTrue(silverIngot.is(itemTag("ingots")), "silver ingot must use the c:ingots parent");
        helper.assertTrue(delvealloyIngot.is(itemTag("ingots/delvealloy")),
            "delvealloy ingot must use c:ingots/delvealloy");
        helper.assertTrue(delvealloyIngot.is(itemTag("ingots")), "delvealloy ingot must use the c:ingots parent");

        final Block silverOre = ModBlocks.ALL.get("silver_ore").get();
        final Block delvealloyBlock = ModBlocks.ALL.get("delvealloy_block").get();
        helper.assertTrue(silverOre.defaultBlockState().is(blockTag("ores/silver")),
            "silver ore must use c:ores/silver");
        helper.assertTrue(silverOre.defaultBlockState().is(blockTag("ores")),
            "silver ore must use the c:ores parent");
        helper.assertTrue(delvealloyBlock.defaultBlockState().is(blockTag("storage_blocks/delvealloy")),
            "delvealloy block must use c:storage_blocks/delvealloy");

        final ItemStack hexLog = new ItemStack(ModItems.ALL.get("hex_log").get());
        final ItemStack hexwood = new ItemStack(ModItems.ALL.get("hexwood").get());
        helper.assertTrue(hexLog.is(ItemTags.LOGS), "hex log must be interchangeable with tagged logs");
        helper.assertTrue(ModBlocks.ALL.get("hex_log").get().defaultBlockState().is(BlockTags.LOGS),
            "placed hex log must be in minecraft:logs");
        helper.assertTrue(hexwood.is(ItemTags.PLANKS), "hexwood must be interchangeable with tagged planks");
        helper.assertTrue(ModBlocks.ALL.get("hexwood").get().defaultBlockState().is(BlockTags.PLANKS),
            "placed hexwood must be in minecraft:planks");
        helper.succeed();
    }

    public static void pipeAutomationUsesSidedItemHandlers(final GameTestHelper helper) {
        final BlockPos relative = new BlockPos(1, 1, 1);
        final BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.ALL.get("spinningwheel").get());
        final MagicMachineBlockEntity machine = helper.getBlockEntity(relative, MagicMachineBlockEntity.class);
        final ResourceHandler<ItemResource> top = requireCapability(
            helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.UP),
            "top item handler"
        );

        helper.assertValueEqual(
            insert(top, 0, ItemResource.of(Items.IRON_SWORD), 1, false),
            0,
            "top pipe must reject items unrelated to every spinning-wheel recipe"
        );
        helper.assertValueEqual(
            insert(top, 0, ItemResource.of(Items.STRING), 8, false),
            8,
            "top pipe must simulate accepting recipe inputs"
        );
        helper.assertTrue(machine.getItem(0).isEmpty(), "simulated pipe insertion must not mutate inventory");
        helper.assertValueEqual(
            insert(top, 0, ItemResource.of(Items.STRING), 8, true),
            8,
            "top pipe must insert recipe inputs"
        );
        helper.assertValueEqual(
            extract(top, 0, ItemResource.of(Items.STRING), 1, true),
            0,
            "top pipe must not extract recipe inputs"
        );

        IntStream.range(0, 160).forEach(_ -> MagicMachineBlockEntity.serverTick(
            helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), machine
        ));

        final ResourceHandler<ItemResource> bottom = requireCapability(
            helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.DOWN),
            "bottom item handler"
        );
        helper.assertValueEqual(
            insert(bottom, 0, ItemResource.of(Items.STRING), 1, true),
            0,
            "bottom pipe must reject insertion"
        );
        final ItemResource outputResource = bottom.getResource(0);
        helper.assertTrue(outputResource.is(Items.WOOL.white()), "bottom pipe must expose finished output");
        helper.assertValueEqual(
            extract(bottom, 0, outputResource, 1, false),
            1,
            "bottom pipe must simulate finished output extraction"
        );
        helper.assertTrue(machine.getItem(6).is(Items.WOOL.white()), "simulated extraction must preserve output");
        helper.assertValueEqual(
            extract(bottom, 0, outputResource, 1, true),
            1,
            "bottom pipe must extract finished output"
        );
        helper.assertTrue(machine.getItem(6).isEmpty(), "real extraction must remove output");

        final BlockPos ovenRelative = new BlockPos(2, 1, 1);
        helper.setBlock(ovenRelative, ModBlocks.ALL.get("alchemical_oven").get());
        final MagicMachineBlockEntity oven = helper.getBlockEntity(ovenRelative, MagicMachineBlockEntity.class);
        final ItemStack ovenInput = new ItemStack(ModItems.ALL.get("ingredient_odd_porkchop_raw").get());
        helper.assertTrue(oven.canPlaceItem(0, ovenInput),
            "exact recipe ingredients must enter input slots");
        helper.assertTrue(!oven.canPlaceItem(0, new ItemStack(Items.COAL)),
            "fuel must not enter ordinary input slots");
        helper.assertTrue(oven.canPlaceItem(oven.machineProfile().fuelSlot(), new ItemStack(Items.COAL)),
            "fuel must enter the dedicated fuel slot");
        helper.assertTrue(!oven.canPlaceItem(oven.machineProfile().fuelSlot(), ovenInput),
            "non-fuel recipe ingredients must not enter the dedicated fuel slot");
        helper.assertTrue(!oven.canPlaceItem(oven.machineProfile().outputStart(), ovenInput),
            "output slots must reject insertion");

        final BlockPos cauldronRelative = new BlockPos(0, 1, 1);
        helper.setBlock(cauldronRelative, ModBlocks.ALL.get("cauldron").get());
        final MagicMachineBlockEntity cauldron = helper.getBlockEntity(cauldronRelative, MagicMachineBlockEntity.class);
        final ItemStack customBrew = new ItemStack(ModItems.ALL.get("brew_murderous_flock").get());
        helper.assertTrue(cauldron.canPlaceItem(0, customBrew),
            "reloadable custom brew components must enter cauldron input slots");
        helper.succeed();
    }

    public static void fluidPipesConnectToLiquidMachines(final GameTestHelper helper) {
        final BlockPos relative = new BlockPos(1, 1, 1);
        final BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.ALL.get("kettle").get());
        final ResourceHandler<FluidResource> handler = requireCapability(
            helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, absolute, Direction.NORTH),
            "fluid handler"
        );
        final FluidResource water = FluidResource.of(Fluids.WATER);
        helper.assertValueEqual(
            insert(handler, 0, water, 1_000, false),
            1_000,
            "fluid pipe simulation"
        );
        helper.assertValueEqual(handler.getAmountAsInt(0), 0, "simulation must preserve the tank");
        helper.assertValueEqual(
            insert(handler, 0, water, 1_000, true),
            1_000,
            "fluid pipe insertion"
        );
        helper.assertValueEqual(handler.getAmountAsInt(0), 1_000, "tank amount after insertion");
        helper.assertValueEqual(
            extract(handler, 0, water, 250, true),
            250,
            "fluid pipe extraction"
        );
        helper.assertValueEqual(handler.getAmountAsInt(0), 750, "tank amount after extraction");
        helper.succeed();
    }

    private static LivingDamageEvent.Pre damageEvent(
        final net.minecraft.world.entity.LivingEntity target,
        final net.minecraft.world.damagesource.DamageSource source,
        final float amount
    ) {
        return new LivingDamageEvent.Pre(target, new DamageContainer(source, amount));
    }

    private static <T extends net.neoforged.neoforge.transfer.resource.Resource> int insert(
        final ResourceHandler<T> handler,
        final int index,
        final T resource,
        final int amount,
        final boolean commit
    ) {
        try (Transaction transaction = Transaction.openRoot()) {
            final int inserted = handler.insert(index, resource, amount, transaction);
            if (commit) {
                transaction.commit();
            }
            return inserted;
        }
    }

    private static <T extends net.neoforged.neoforge.transfer.resource.Resource> int extract(
        final ResourceHandler<T> handler,
        final int index,
        final T resource,
        final int amount,
        final boolean commit
    ) {
        try (Transaction transaction = Transaction.openRoot()) {
            final int extracted = handler.extract(index, resource, amount, transaction);
            if (commit) {
                transaction.commit();
            }
            return extracted;
        }
    }

    private static <T> T requireCapability(final T capability, final String name) {
        if (capability == null) {
            throw new IllegalStateException("Missing " + name);
        }
        return capability;
    }

    private static int hornCount(final ServerPlayer player) {
        return IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("hornofthehunt").get()))
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    private static TagKey<Item> itemTag(final String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Block> blockTag(final String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    private static ItemStack boundDoll(
        final net.minecraft.server.level.ServerPlayer player,
        final String id
    ) {
        final ItemStack stack = new ItemStack(ModItems.ALL.get(id).get());
        ((DollItem) stack.getItem()).interactLivingEntity(stack, player, player, InteractionHand.MAIN_HAND);
        return stack;
    }

    private static void useOnTop(
        final GameTestHelper helper,
        final ServerPlayer player,
        final BlockPos relativeSupport
    ) {
        final BlockPos support = helper.absolutePos(relativeSupport);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
        player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
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
