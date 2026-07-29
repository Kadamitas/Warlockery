package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.block.WolfAltarRuntime;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.brew.custom.CustomBrewFormula;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.SpiritMob;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
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
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

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
            SpiritMob.class, new AABB(center).inflate(8.0),
            entity -> entity.creatureKind() == ArcaneCreature.CreatureKind.IMP
        ).isEmpty();
        helper.assertTrue(spawned, "summon_imp must create a native Warlockery imp");
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
        helper.assertTrue(hunter.getMainHandItem().is(ModItems.ALL.get("silver_repeater").get()),
            "werewolf hunter must carry the Silver Repeater");
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
        final LivingDamageEvent event = new LivingDamageEvent(player, helper.getLevel().damageSources().generic(), 20.0F);
        DollItem.handleDamage(event);
        helper.assertValueEqual(event.getAmount(), 0.0F, "lethal damage after death guard");
        helper.assertValueEqual(player.getHealth(), 1.0F, "death guard recovery health");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "death guard must use vanilla Totem regeneration");
        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION), "death guard must use vanilla Totem absorption");
        helper.assertTrue(doll.isEmpty(), "one-use death guard must be consumed");
        helper.succeed();
    }

    public static void hungerGuardRestoresHungerAndSaturation(final GameTestHelper helper) {
        final var player = connectedSurvivalPlayer(helper);
        final ItemStack doll = boundDoll(player, "hunger_guard_doll");
        player.getInventory().setItem(0, doll);
        player.setHealth(1.0F);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0F);
        final LivingDamageEvent event = new LivingDamageEvent(player, helper.getLevel().damageSources().starve(), 2.0F);
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
        ((DollItem) doll.getItem()).inventoryTick(doll, helper.getLevel(), player, null);
        helper.assertValueEqual(sword.getDamageValue(), 8, "durability repaired by one doll charge");
        helper.assertValueEqual(doll.getDamageValue(), 1, "mending doll durability spent");
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
        final LivingDamageEvent fall = new LivingDamageEvent(player, helper.getLevel().damageSources().fall(), 2.0F);
        DollItem.handleDamage(fall);
        helper.assertValueEqual(fall.getAmount(), 0.0F, "earth guard lethal fall damage");
        helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "earth guard must apply vanilla Slow Falling");

        player.removeAllEffects();
        player.getInventory().setItem(0, boundDoll(player, "water_guard_doll"));
        player.setHealth(1.0F);
        player.setAirSupply(0);
        final LivingDamageEvent drowning = new LivingDamageEvent(player, helper.getLevel().damageSources().drown(), 2.0F);
        DollItem.handleDamage(drowning);
        helper.assertValueEqual(drowning.getAmount(), 0.0F, "water guard lethal drowning damage");
        helper.assertValueEqual(player.getAirSupply(), player.getMaxAirSupply(), "water guard restored air");
        helper.assertTrue(player.hasEffect(MobEffects.WATER_BREATHING),
            "water guard must apply vanilla Water Breathing");

        player.removeAllEffects();
        player.getInventory().setItem(0, boundDoll(player, "fire_guard_doll"));
        player.setHealth(1.0F);
        final LivingDamageEvent lava = new LivingDamageEvent(player, helper.getLevel().damageSources().lava(), 2.0F);
        DollItem.handleDamage(lava);
        helper.assertValueEqual(lava.getAmount(), 0.0F, "fire guard lethal lava damage");
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
        final IItemHandler top = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
            .orElseThrow(() -> new IllegalStateException("Missing top item handler"));

        final ItemStack simulatedRemainder = top.insertItem(0, new ItemStack(Items.STRING, 8), true);
        helper.assertTrue(simulatedRemainder.isEmpty(), "top pipe must simulate accepting recipe inputs");
        helper.assertTrue(machine.getItem(0).isEmpty(), "simulated pipe insertion must not mutate inventory");
        helper.assertTrue(top.insertItem(0, new ItemStack(Items.STRING, 8), false).isEmpty(),
            "top pipe must insert recipe inputs");
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty(), "top pipe must not extract recipe inputs");

        IntStream.range(0, 160).forEach(_ -> MagicMachineBlockEntity.serverTick(
            helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), machine
        ));

        final IItemHandler bottom = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
            .orElseThrow(() -> new IllegalStateException("Missing bottom item handler"));
        helper.assertTrue(bottom.insertItem(0, new ItemStack(Items.STRING), false).is(Items.STRING),
            "bottom pipe must reject insertion");
        final ItemStack simulatedOutput = bottom.extractItem(0, 1, true);
        helper.assertTrue(simulatedOutput.is(Items.WOOL.white()), "bottom pipe must expose finished output");
        helper.assertTrue(machine.getItem(6).is(Items.WOOL.white()), "simulated extraction must preserve output");
        final ItemStack output = bottom.extractItem(0, 1, false);
        helper.assertTrue(output.is(Items.WOOL.white()), "bottom pipe must extract finished output");
        helper.assertTrue(machine.getItem(6).isEmpty(), "real extraction must remove output");
        helper.succeed();
    }

    public static void fluidPipesConnectToLiquidMachines(final GameTestHelper helper) {
        final BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModBlocks.ALL.get("kettle").get());
        final MagicMachineBlockEntity machine = helper.getBlockEntity(relative, MagicMachineBlockEntity.class);
        final IFluidHandler handler = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
            .orElseThrow(() -> new IllegalStateException("Missing fluid handler"));
        final FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        helper.assertValueEqual(
            handler.fill(water, IFluidHandler.FluidAction.SIMULATE),
            1_000,
            "fluid pipe simulation"
        );
        helper.assertValueEqual(handler.getFluidInTank(0).getAmount(), 0, "simulation must preserve the tank");
        helper.assertValueEqual(
            handler.fill(water, IFluidHandler.FluidAction.EXECUTE),
            1_000,
            "fluid pipe insertion"
        );
        helper.assertValueEqual(handler.getFluidInTank(0).getAmount(), 1_000, "tank amount after insertion");
        helper.assertValueEqual(
            handler.drain(250, IFluidHandler.FluidAction.EXECUTE).getAmount(),
            250,
            "fluid pipe extraction"
        );
        helper.assertValueEqual(handler.getFluidInTank(0).getAmount(), 750, "tank amount after extraction");
        helper.succeed();
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

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
