package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.block.WarlockeryCropBlock;
import com.kadamitas.warlockery.block.GrassperBlock;
import com.kadamitas.warlockery.mutation.AdvancedMutationResolver;
import com.kadamitas.warlockery.mutation.AdvancedMutationTags;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class LivingRootsGameTests {
    private static int difficultyLeases;
    private static net.minecraft.world.Difficulty savedDifficulty;
    private LivingRootsGameTests() {}

    public static void mandrakeExtractionWailAndResettleAreBounded(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final BlockPos cropPos = new BlockPos(0, 1, 0);
            final BlockState mature = ModBlocks.ALL.get("mandrake").get().defaultBlockState()
                .setValue(CropBlock.AGE, CropBlock.MAX_AGE);
            fixture.block(cropPos, mature);
            final ServerPlayer harvester = connectedSurvivalPlayer(helper, new BlockPos(2, 1, 2));
            MandrakeEntity mandrake = null;
            for (int attempt = 0; attempt < 64 && mandrake == null; attempt++) {
                ((WarlockeryCropBlock)mature.getBlock()).playerDestroy(helper.getLevel(), harvester,
                    helper.absolutePos(cropPos), mature, null, ItemStack.EMPTY);
                final List<MandrakeEntity> awakened = new ArrayList<>();
                helper.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(MandrakeEntity.class),
                    new net.minecraft.world.phys.AABB(helper.absolutePos(cropPos)).inflate(1), candidate -> true, awakened, 1);
                mandrake = awakened.stream().findFirst().orElse(null);
            }
            helper.assertTrue(mandrake != null, "real mature crop awakening created a dedicated Mandrake");
            fixture.track(mandrake);
            MandrakeRuntime.tick(mandrake, helper.getLevel());
            harvester.teleportTo(harvester.getX(), harvester.getY() + 32.0D, harvester.getZ());
            final LivingEntity observer = fixture.living("sheep", new BlockPos(2, 1, 0));
            final MandrakeEntity dormantControl = fixture.mandrake(new BlockPos(-2, 1, 0));
            final DreamrootEntity dormantDreamrootControl = fixture.dreamroot(new BlockPos(-2, 1, 2));
            dormantDreamrootControl.setDreamrootState(new DreamrootState(1, 200, 0, 0));
            assertAuthenticResettleRouteFailureLadder(helper, fixture);
            final MandrakeEntity harvested = mandrake;
            fixture.after(35, () -> {
                helper.assertTrue(harvested.mandrakeCounters().wails == 1, "one extraction wail");
                helper.assertTrue(harvested.mandrakeCounters().recipients <= 4, "recipient cap");
                helper.assertTrue(observer.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA), "sighted observer affected");
                helper.assertTrue(harvested.mandrakeCounters().nauseaApplications == 1, "one pass-local nausea application");
                helper.assertTrue(harvested.mandrakeTransient().phase() == MandrakeRules.Phase.FLAIL
                    && harvested.mandrakeCounters().resettleEntries == 0,
                    "extraction remains in its null-subject FLAIL window after the wail");
                helper.assertTrue(harvested.mandrakeState().wailCooldownRemaining() > 0,
                    "the emitted wail starts its loaded-tick cooldown");
                helper.assertTrue(dormantControl.mandrakeCounters().rawVisits == 0
                    && dormantControl.mandrakeCounters().sightRays == 0
                    && dormantControl.mandrakeCounters().pathRequests == 0
                    && dormantControl.mandrakeCounters().safeReads == 0
                    && dormantControl.mandrakeCounters().safeEntityVisits == 0,
                    "representative SEEDED Mandrake performs zero perception, path, and safety queries");
                helper.assertTrue(dormantDreamrootControl.dreamrootCounters().thresholdChecks == 0
                    && dormantDreamrootControl.dreamrootCounters().rawVisits == 0
                    && dormantDreamrootControl.dreamrootCounters().sightRays == 0
                    && dormantDreamrootControl.dreamrootCounters().pathRequests == 0
                    && dormantDreamrootControl.dreamrootCounters().safeReads == 0
                    && dormantDreamrootControl.dreamrootCounters().safeEntityVisits == 0
                    && dormantDreamrootControl.dreamrootCounters().nearestPlayerQueries == 0,
                    "representative cooldown ROOTED Dreamroot performs zero perception, path, and safety queries");
                helper.assertTrue(harvested.mandrakeCounters().slownessApplications == 0
                    && harvested.mandrakeCounters().poisonApplications == 0
                    && harvested.mandrakeCounters().weaknessApplications == 0
                    && harvested.mandrakeCounters().explosions == 0, "forbidden effects remain zero");
            });
            fixture.after(135, () -> helper.assertTrue(harvested.mandrakeCounters().resettleEntries == 1,
                "the complete FLAIL window orders RESETTLE afterward"));
            fixture.finish(260, () -> {
                helper.assertTrue(harvested.mandrakeCounters().resettleEntries == 1, "extraction entered RESETTLE exactly once");
                helper.assertTrue(harvested.mandrakeCounters().reanchors >= 1, "RESETTLE completed by actual reanchor");
                helper.assertTrue(LivingRootsRules.rooted(harvested.mandrakeTransient().phase()), "resettled rooted");
                helper.assertTrue(harvested.mandrakeCounters().rawVisits <= 8, "raw visit cap");
                helper.assertTrue(harvested.mandrakeCounters().pathRequests <= 8, "per-episode paths stay bounded");
                helper.assertTrue(harvested.mandrakeCounters().genericBehaviorDispatches == 0
                    && harvested.mandrakeCounters().genericTacticalDispatches == 0
                    && harvested.mandrakeCounters().genericAmbientDispatches == 0
                    && harvested.mandrakeCounters().genericHazardDispatches == 0, "generic dispatch stays absent");
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    public static void mandrakeDisturbanceRequiresFreshAttributionAndSight(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final MandrakeEntity mandrake = fixture.mandrake(new BlockPos(0, 1, 0));
            final LivingEntity blocked = fixture.living("sheep", new BlockPos(2, 1, 0));
            final MandrakeEntity ageZero = fixture.mandrake(new BlockPos(-2, 1, -2));
            final MandrakeEntity ageForty = fixture.mandrake(new BlockPos(0, 1, -2));
            final MandrakeEntity ageFortyOne = fixture.mandrake(new BlockPos(2, 1, -2));
            MandrakeRuntime.acceptedDamageAt(ageZero, blocked, blocked.tickCount);
            MandrakeRuntime.acceptedDamageAt(ageForty, blocked, blocked.tickCount - 40);
            MandrakeRuntime.acceptedDamageAt(ageFortyOne, blocked, blocked.tickCount - 41);
            helper.assertTrue(ageZero.mandrakeCounters().attackerAttributions == 1, "age zero attribution accepted");
            helper.assertTrue(ageForty.mandrakeCounters().attackerAttributions == 1, "age forty attribution accepted");
            helper.assertTrue(ageFortyOne.mandrakeCounters().attackerAttributions == 0
                && ageFortyOne.mandrakeCounters().attackerExpiries == 1, "age forty-one attribution rejected");
            fixture.block(new BlockPos(1, 1, 0), Blocks.BARRIER.defaultBlockState());
            final float healthBefore = mandrake.getHealth();
            helper.assertTrue(mandrake.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(blocked), 1.0F),
                "real accepted attributed damage wakes the mandrake");
            helper.assertTrue(mandrake.getHealth() < healthBefore, "damage is effectively positive");
            helper.assertTrue(mandrake.mandrakeTransient().phase()==MandrakeRules.Phase.DISTURBED
                && mandrake.mandrakeTransient().phaseTicks==0&&mandrake.mandrakeCounters().episodeStarts==1,
                "accepted rooted hit enters one DISTURBED episode at telegraph tick zero");
            for(int tick=0;tick<10;tick++)MandrakeRuntime.tick(mandrake,helper.getLevel());
            final int disturbedTicks=mandrake.mandrakeTransient().phaseTicks;
            final int disturbedRemaining=mandrake.mandrakeState().episodeCooldownRemaining();
            helper.assertTrue(mandrake.hurtServer(helper.getLevel(),helper.getLevel().damageSources().mobAttack(blocked),2.0F),
                "a second effective hit is accepted during DISTURBED");
            helper.assertTrue(mandrake.mandrakeTransient().phase()==MandrakeRules.Phase.DISTURBED
                && mandrake.mandrakeTransient().phaseTicks==disturbedTicks
                && mandrake.mandrakeState().episodeCooldownRemaining()==disturbedRemaining
                && mandrake.mandrakeCounters().episodeStarts==1,
                "DISTURBED hit replaces attribution without restarting telegraph or durable episode time");
            for(int tick=0;tick<10;tick++)MandrakeRuntime.tick(mandrake,helper.getLevel());
            helper.assertTrue(mandrake.mandrakeTransient().phase()==MandrakeRules.Phase.FLAIL
                && mandrake.mandrakeCounters().wails==1,"the original telegraph emits its one wail and enters FLAIL");
            MandrakeRuntime.tick(mandrake,helper.getLevel());
            final int flailTicks=mandrake.mandrakeTransient().phaseTicks;
            final int flailRemaining=mandrake.mandrakeState().episodeCooldownRemaining();
            helper.assertTrue(mandrake.hurtServer(helper.getLevel(),helper.getLevel().damageSources().mobAttack(blocked),3.0F),
                "a third effective hit is accepted during FLAIL");
            helper.assertTrue(mandrake.mandrakeTransient().phase()==MandrakeRules.Phase.FLAIL
                && mandrake.mandrakeTransient().phaseTicks==flailTicks
                && mandrake.mandrakeState().episodeCooldownRemaining()==flailRemaining
                && mandrake.mandrakeCounters().episodeStarts==1&&mandrake.mandrakeCounters().wails==1,
                "FLAIL hit binds the subject without phase reset, episode extension, or second wail");
            fixture.finish(35, () -> {
                helper.assertTrue(mandrake.mandrakeCounters().wails == 1, "one disturbance wail");
                helper.assertTrue(!blocked.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA), "wall blocks observation");
                helper.assertTrue(MandrakeRules.freshAttribution(40) && !MandrakeRules.freshAttribution(41), "freshness boundary");
                helper.assertTrue(mandrake.mandrakeCounters().attackerAttributions == 3, "three legal hits but one episode");
                helper.assertTrue(mandrake.mandrakeCounters().episodeStarts==1,"repeated hits never extend the episode");
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    public static void dreamrootThresholdDreamRequiresRootedGround(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final DreamrootEntity root = fixture.dreamroot(new BlockPos(0, 1, 0));
            final LivingEntity actor = fixture.living("sheep", new BlockPos(2, 1, 0));
            final DreamrootEntity ageZero = fixture.dreamroot(new BlockPos(-2, 1, -2));
            final DreamrootEntity ageForty = fixture.dreamroot(new BlockPos(0, 1, -2));
            final DreamrootEntity ageFortyOne = fixture.dreamroot(new BlockPos(2, 1, -2));
            DreamrootRuntime.acceptedDamageAt(ageZero, actor, actor.tickCount);
            DreamrootRuntime.acceptedDamageAt(ageForty, actor, actor.tickCount - 40);
            DreamrootRuntime.acceptedDamageAt(ageFortyOne, actor, actor.tickCount - 41);
            helper.assertTrue(ageZero.dreamrootCounters().attackerAttributions == 1, "dreamroot age zero accepted");
            helper.assertTrue(ageForty.dreamrootCounters().attackerAttributions == 1, "dreamroot age forty accepted");
            helper.assertTrue(ageFortyOne.dreamrootCounters().attackerAttributions == 0
                && ageFortyOne.dreamrootCounters().attackerExpiries == 1, "dreamroot age forty-one rejected");
            ageZero.discard(); ageForty.discard(); ageFortyOne.discard();
            fixture.finish(100, () -> {
                helper.assertTrue(root.dreamrootCounters().rawVisits <= 8, "threshold scan cap");
                helper.assertTrue(root.dreamrootCounters().dreams == 1, "exactly one application per episode");
                helper.assertTrue(actor.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "dream is darkness");
                helper.assertTrue(root.dreamrootCounters().darknessApplications == 1
                    && root.dreamrootCounters().poisonApplications == 0
                    && root.dreamrootCounters().weaknessApplications == 0
                    && root.dreamrootCounters().explosions == 0, "dream effect identity is exact");
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    public static void dreamrootBulbPopulationAndMutationStayCapped(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final BlockPos absolute = helper.absolutePos(new BlockPos(0, 1, 0));
            final List<ItemEntity> droppedStacks = new ArrayList<>();
            for (final int count : List.of(1, 4, 8, 64)) {
                final ItemEntity dropped = new ItemEntity(helper.getLevel(), absolute.getX()+.5,
                    absolute.getY(), absolute.getZ()+.5, new ItemStack(ModItems.ALL.get("seedsdreamroot").get(), count));
                dropped.setDeltaMovement(0, 0, 0);
                fixture.track(dropped);
                helper.getLevel().addFreshEntity(dropped);
                droppedStacks.add(dropped);
            }
            final BlockPos center = new BlockPos(0, 1, 2);
            fixture.block(center.below(), Blocks.WATER.defaultBlockState());
            fixture.block(center, Blocks.COBWEB.defaultBlockState());
            final List<BlockPos> crops = List.of(center.north(), center.east(), center.south(), center.west());
            for (final BlockPos crop : crops) {
                fixture.block(crop.below(), Blocks.FARMLAND.defaultBlockState());
                fixture.block(crop, ModBlocks.ALL.get("mandrake").get().defaultBlockState()
                    .setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            }
            final List<BlockPos> grasspers = List.of(
                center.offset(1, 0, 1), center.offset(1, 0, -1),
                center.offset(-1, 0, 1), center.offset(-1, 0, -1));
            final List<ItemStack> ingredients = List.of(
                new ItemStack(ModItems.ALL.get("ingredient_verdant_catalyst_prime").get()),
                new ItemStack(ModItems.ALL.get("ingredient_verdant_catalyst_prime").get()),
                new ItemStack(ModItems.ALL.get("ingredient_focused_will").get()),
                new ItemStack(ModItems.ALL.get("ingredient_attuned_stone_charged").get()));
            final ServerPlayer mutator = connectedSurvivalPlayer(helper, new BlockPos(0, 1, 3));
            for (int index = 0; index < grasspers.size(); index++) {
                final BlockPos grassper = grasspers.get(index);
                fixture.block(grassper.below(), Blocks.DIRT.defaultBlockState());
                fixture.block(grassper, ModBlocks.ALL.get("grassper").get().defaultBlockState());
                final ItemStack offered = ingredients.get(index);
                final net.minecraft.world.item.Item expectedIngredient = offered.getItem();
                mutator.setItemInHand(InteractionHand.MAIN_HAND, offered);
                final BlockPos absoluteGrassper = helper.absolutePos(grassper);
                final InteractionResult stored = helper.getLevel().getBlockState(absoluteGrassper).useItemOn(
                    offered, helper.getLevel(), mutator, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absoluteGrassper), Direction.UP, absoluteGrassper, false));
                helper.assertTrue(stored.consumesAction(), "real Grassper use stores the mutation ingredient");
                helper.assertTrue(GrassperBlock.storedItem(helper.getLevel(), absoluteGrassper)
                    .filter(stack -> stack.getCount() == 1 && stack.is(expectedIngredient)).isPresent(),
                    "anchored ItemDisplay holds exactly the offered ingredient");
            }
            final MandrakeEntity mutationHost = fixture.mandrake(center.offset(-2, 0, 0));
            MandrakeRuntime.disturb(mutationHost, mutator);
            fixture.living("creeper", center.offset(2, 0, 0));
            for (final BlockPos crop : crops) {
                final BlockState cropState = helper.getBlockState(crop);
                helper.assertTrue(cropState.is(AdvancedMutationTags.Blocks.MANDRAKE_CROPS)
                    && cropState.getBlock() instanceof CropBlock liveCrop && liveCrop.isMaxAge(cropState),
                    "the real cardinal crop is mature and mutation-tagged: " + cropState);
            }
            final AdvancedMutationResolver.Outcome mutation = AdvancedMutationResolver.attempt(
                helper.getLevel(), helper.absolutePos(center), mutator);
            helper.assertTrue(mutation.success() && mutation.affected() == 4,
                "the actual AdvancedMutationResolver converts all four cardinal crops: " + mutation.diagnostic());
            helper.assertTrue(crops.stream().allMatch(crop -> helper.getBlockState(crop).is(ModBlocks.ALL.get("dreamroot").get())),
                "the live mutation creates four Dreamroot crops");
            helper.assertTrue(mutationHost.isRemoved() && mutationHost.mandrakeCounters().cancellations == 1
                && mutationHost.mandrakeCounters().wails == 0,
                "discarding the dedicated Mandrake host tears down its episode without replay");
            fixture.after(61, () -> {
                final int remaining = droppedStacks.stream().filter(Entity::isAlive)
                    .mapToInt(dropped -> dropped.getItem().getCount()).sum();
                helper.assertTrue(remaining >= 61 && remaining <= 69,
                    "the shared level quota creates between eight and sixteen across two scheduling edges");
            });
            fixture.finish(80, () -> {
                helper.assertTrue(DreamrootRules.bulbsThisWake(64, 8) == 4, "wake cap");
                helper.assertTrue(droppedStacks.stream().allMatch(dropped -> dropped.isRemoved() || dropped.getItem().isEmpty()),
                    "deferred batches preserve and exhaust exact totals for 1, 4, 8, and 64");
                helper.assertTrue(MinedrakeCombatRules.BULB_WAKE_TICKS == 60, "wake timing preserved");
                helper.assertTrue(MinedrakeCombatRules.EXPLOSION_INTERACTION == net.minecraft.world.level.Level.ExplosionInteraction.NONE, "mutation blast data preserved");
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    public static void livingRootsHazardEscapeAndCancellationAreDeterministic(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final MandrakeEntity mandrake = fixture.mandrake(new BlockPos(0, 1, 0));
            final DreamrootEntity dreamroot = fixture.dreamroot(new BlockPos(2, 1, 0));
            final Villager tradingActor = (Villager)fixture.living("villager", new BlockPos(-2, 1, 0));
            final ServerPlayer customer = connectedSurvivalPlayer(helper, new BlockPos(-2, 1, 2));
            MandrakeRuntime.acceptedDamage(mandrake, tradingActor);
            DreamrootRuntime.acceptedDamage(dreamroot, tradingActor);
            tradingActor.setTradingPlayer(customer);
            MandrakeRuntime.tick(mandrake, helper.getLevel());
            DreamrootRuntime.tick(dreamroot, helper.getLevel());
            helper.assertTrue(mandrake.mandrakeCounters().cancellations == 1,
                "a bound actor beginning a real trade cancels the Mandrake episode");
            helper.assertTrue(dreamroot.dreamrootCounters().cancellations == 1,
                "a bound actor beginning a real trade cancels the Dreamroot episode");

            final ServerPlayer sleepingActor = connectedSurvivalPlayer(helper, new BlockPos(0, 1, 2));
            final MandrakeEntity sleepingMandrake = fixture.mandrake(new BlockPos(-1, 1, 2));
            final DreamrootEntity sleepingDreamroot = fixture.dreamroot(new BlockPos(1, 1, 2));
            MandrakeRuntime.acceptedDamage(sleepingMandrake, sleepingActor);
            DreamrootRuntime.acceptedDamage(sleepingDreamroot, sleepingActor);
            sleepingActor.startSleeping(helper.absolutePos(new BlockPos(0, 1, 2)));
            helper.assertTrue(sleepingActor.isSleeping(), "the bound player is in the live sleeping state");
            MandrakeRuntime.tick(sleepingMandrake, helper.getLevel());
            DreamrootRuntime.tick(sleepingDreamroot, helper.getLevel());
            assertCancelledAndCleared(helper, sleepingMandrake, sleepingDreamroot, "sleep");
            sleepingActor.stopSleeping();

            final net.minecraft.world.entity.raid.Raider raidActor =
                (net.minecraft.world.entity.raid.Raider)fixture.living("pillager", new BlockPos(0, 1, 3));
            final MandrakeEntity raidMandrake = fixture.mandrake(new BlockPos(-1, 1, 3));
            final DreamrootEntity raidDreamroot = fixture.dreamroot(new BlockPos(1, 1, 3));
            MandrakeRuntime.acceptedDamage(raidMandrake, raidActor);
            DreamrootRuntime.acceptedDamage(raidDreamroot, raidActor);
            final net.minecraft.world.entity.raid.Raid raid =
                new net.minecraft.world.entity.raid.Raid(raidActor.blockPosition(), helper.getLevel().getDifficulty());
            raid.joinRaid(helper.getLevel(), 1, raidActor, raidActor.blockPosition(), true);
            helper.assertTrue(raidActor.getCurrentRaid() == raid, "the bound raider is a live raid participant");
            MandrakeRuntime.tick(raidMandrake, helper.getLevel());
            DreamrootRuntime.tick(raidDreamroot, helper.getLevel());
            assertCancelledAndCleared(helper, raidMandrake, raidDreamroot, "raid");

            final Villager removedActor = (Villager)fixture.living("villager", new BlockPos(-3, 1, 2));
            final MandrakeEntity removedMandrake = fixture.mandrake(new BlockPos(-3, 1, 1));
            final DreamrootEntity removedDreamroot = fixture.dreamroot(new BlockPos(-3, 1, 3));
            MandrakeRuntime.acceptedDamage(removedMandrake, removedActor);
            DreamrootRuntime.acceptedDamage(removedDreamroot, removedActor);
            removedMandrake.discard();
            removedDreamroot.discard();
            assertCancelledAndCleared(helper, removedMandrake, removedDreamroot, "discard");

            final Villager teleportedActor = (Villager)fixture.living("villager", new BlockPos(3, 1, 2));
            final MandrakeEntity teleportedMandrake = fixture.mandrake(new BlockPos(2, 1, 1));
            final DreamrootEntity teleportedDreamroot = fixture.dreamroot(new BlockPos(2, 1, 3));
            MandrakeRuntime.acceptedDamage(teleportedMandrake, teleportedActor);
            DreamrootRuntime.acceptedDamage(teleportedDreamroot, teleportedActor);
            MandrakeRuntime.tick(teleportedMandrake, helper.getLevel());
            DreamrootRuntime.tick(teleportedDreamroot, helper.getLevel());
            teleportedMandrake.teleportTo(teleportedMandrake.getX() + 9.0D, teleportedMandrake.getY(), teleportedMandrake.getZ());
            teleportedDreamroot.teleportTo(teleportedDreamroot.getX() + 9.0D, teleportedDreamroot.getY(), teleportedDreamroot.getZ());
            MandrakeRuntime.tick(teleportedMandrake, helper.getLevel());
            DreamrootRuntime.tick(teleportedDreamroot, helper.getLevel());
            assertCancelledAndCleared(helper, teleportedMandrake, teleportedDreamroot, "external teleport");

            final Villager dimensionActor = (Villager)fixture.living("villager", new BlockPos(3, 1, -2));
            final MandrakeEntity dimensionMandrake = fixture.mandrake(new BlockPos(2, 1, -3));
            final DreamrootEntity dimensionDreamroot = fixture.dreamroot(new BlockPos(2, 1, -1));
            MandrakeRuntime.acceptedDamage(dimensionMandrake, dimensionActor);
            DreamrootRuntime.acceptedDamage(dimensionDreamroot, dimensionActor);
            MandrakeRuntime.tick(dimensionMandrake, helper.getLevel());
            DreamrootRuntime.tick(dimensionDreamroot, helper.getLevel());
            final net.minecraft.server.level.ServerLevel nether =
                helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(nether != null, "the live Nether dimension is available");
            helper.assertTrue(dimensionMandrake.teleportTo(nether, 0.5D, 80.0D, 0.5D,
                java.util.Set.of(), dimensionMandrake.getYRot(), dimensionMandrake.getXRot(), false),
                "Mandrake changed dimension through the live teleport API");
            helper.assertTrue(dimensionDreamroot.teleportTo(nether, 2.5D, 80.0D, 0.5D,
                java.util.Set.of(), dimensionDreamroot.getYRot(), dimensionDreamroot.getXRot(), false),
                "Dreamroot changed dimension through the live teleport API");
            MandrakeRuntime.tick(dimensionMandrake, nether);
            DreamrootRuntime.tick(dimensionDreamroot, nether);
            assertCancelledAndCleared(helper, dimensionMandrake, dimensionDreamroot, "dimension change");

            final Villager panicActor = (Villager)fixture.living("villager", new BlockPos(-2, 1, -1));
            final MandrakeEntity panicMandrake = fixture.mandrake(new BlockPos(0, 1, -1));
            final DreamrootEntity panicDreamroot = fixture.dreamroot(new BlockPos(2, 1, -1));
            MandrakeRuntime.acceptedDamage(panicMandrake, panicActor);
            DreamrootRuntime.acceptedDamage(panicDreamroot, panicActor);
            panicMandrake.setNoAi(true);
            panicDreamroot.setNoAi(true);
            final net.minecraft.world.entity.monster.zombie.Zombie panicSource =
                (net.minecraft.world.entity.monster.zombie.Zombie)fixture.living("zombie", new BlockPos(-3, 1, -1));
            helper.assertTrue(panicActor.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(panicSource), 1.0F),
                "real Villager harm is accepted");
            fixture.after(20, () -> {
                final boolean activePanic = panicActor.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                    || panicActor.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING);
                helper.assertTrue(activePanic, "real Villager harm and actual Brain ticks entered active panic");
                helper.assertTrue(panicMandrake.mandrakeCounters().cancellations == 0
                    && panicDreamroot.dreamrootCounters().cancellations == 0,
                    "the controllers remain paused until active panic is established");
                MandrakeRuntime.tick(panicMandrake, helper.getLevel());
                DreamrootRuntime.tick(panicDreamroot, helper.getLevel());
                assertCancelledAndCleared(helper, panicMandrake, panicDreamroot, "active panic");
            });
            final net.minecraft.world.entity.animal.Animal breedingActor =
                (net.minecraft.world.entity.animal.Animal)fixture.living("cow", new BlockPos(-2, 1, -2));
            final MandrakeEntity breedingMandrake = fixture.mandrake(new BlockPos(0, 1, -2));
            final DreamrootEntity breedingDreamroot = fixture.dreamroot(new BlockPos(2, 1, -2));
            MandrakeRuntime.acceptedDamage(breedingMandrake, breedingActor);
            DreamrootRuntime.acceptedDamage(breedingDreamroot, breedingActor);
            breedingActor.setInLove(customer);
            MandrakeRuntime.tick(breedingMandrake, helper.getLevel());
            DreamrootRuntime.tick(breedingDreamroot, helper.getLevel());
            helper.assertTrue(breedingMandrake.mandrakeCounters().cancellations == 1
                && breedingDreamroot.dreamrootCounters().cancellations == 1,
                "a bound actor entering a real breeding pair cancels both species");

            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)fixture.block(new BlockPos(x,6,z),Blocks.DIRT.defaultBlockState());
            final MandrakeEntity thirdFailureMandrake = fixture.mandrake(new BlockPos(-2, 7, -2));
            final DreamrootEntity thirdFailureDreamroot = fixture.dreamroot(new BlockPos(2, 7, 2));
            thirdFailureMandrake.setOnGround(true); thirdFailureDreamroot.setOnGround(true);
            thirdFailureMandrake.setRemainingFireTicks(2); thirdFailureDreamroot.setRemainingFireTicks(2);
            MandrakeRuntime.tick(thirdFailureMandrake, helper.getLevel());
            DreamrootRuntime.tick(thirdFailureDreamroot, helper.getLevel());
            final BlockPos retainedMandrakeDestination = thirdFailureMandrake.mandrakeTransient().escapeDestination;
            final BlockPos retainedDreamrootDestination = thirdFailureDreamroot.dreamrootTransient().escapeDestination;
            helper.assertTrue(retainedMandrakeDestination != null && retainedDreamrootDestination != null
                && thirdFailureMandrake.mandrakeCounters().pathsAccepted == 1
                && thirdFailureDreamroot.dreamrootCounters().pathsAccepted == 1,
                "both failure probes first obtain a real accepted safe destination: mandrake="
                    + retainedMandrakeDestination + "/" + thirdFailureMandrake.mandrakeCounters().pathRequests
                    + "/" + thirdFailureMandrake.mandrakeCounters().pathsAccepted + ", dreamroot="
                    + retainedDreamrootDestination + "/" + thirdFailureDreamroot.dreamrootCounters().pathRequests
                    + "/" + thirdFailureDreamroot.dreamrootCounters().pathsAccepted);
            final BlockPos fixtureOrigin = helper.absolutePos(BlockPos.ZERO);
            for(final BlockPos destination : List.of(retainedMandrakeDestination, retainedDreamrootDestination))
                for(int x=-1;x<=1;x++)for(int y=-1;y<=2;y++)for(int z=-1;z<=1;z++)
                    fixture.block(destination.offset(x,y,z).subtract(fixtureOrigin), Blocks.BARRIER.defaultBlockState());
            thirdFailureMandrake.clearFire(); thirdFailureDreamroot.clearFire();
            thirdFailureMandrake.getNavigation().stop(); thirdFailureDreamroot.getNavigation().stop();
            MandrakeRuntime.tick(thirdFailureMandrake, helper.getLevel());
            DreamrootRuntime.tick(thirdFailureDreamroot, helper.getLevel());
            helper.assertTrue(thirdFailureMandrake.mandrakeTransient().routeFailures == 1
                && retainedMandrakeDestination.equals(thirdFailureMandrake.mandrakeTransient().escapeDestination)
                && thirdFailureDreamroot.dreamrootTransient().routeFailures == 1
                && retainedDreamrootDestination.equals(thirdFailureDreamroot.dreamrootTransient().escapeDestination),
                "failed accepted routes retain the same destinations after failure one");
            for(int tick=0;tick<19;tick++){MandrakeRuntime.tick(thirdFailureMandrake,helper.getLevel());DreamrootRuntime.tick(thirdFailureDreamroot,helper.getLevel());}
            helper.assertTrue(thirdFailureMandrake.mandrakeCounters().pathRequests == 2
                && thirdFailureDreamroot.dreamrootCounters().pathRequests == 2
                && thirdFailureMandrake.mandrakeTransient().routeFailures == 2
                && retainedMandrakeDestination.equals(thirdFailureMandrake.mandrakeTransient().escapeDestination)
                && thirdFailureDreamroot.dreamrootTransient().routeFailures == 2
                && retainedDreamrootDestination.equals(thirdFailureDreamroot.dreamrootTransient().escapeDestination),
                "cadenced rejected request two retains each exact destination");
            for(int tick=0;tick<20;tick++){MandrakeRuntime.tick(thirdFailureMandrake,helper.getLevel());DreamrootRuntime.tick(thirdFailureDreamroot,helper.getLevel());}
            helper.assertTrue(thirdFailureMandrake.mandrakeCounters().pathRequests == 3
                && thirdFailureDreamroot.dreamrootCounters().pathRequests == 3
                && thirdFailureMandrake.mandrakeTransient().routeFailures == 3
                && thirdFailureMandrake.mandrakeTransient().routeBackoff >= 100
                && thirdFailureMandrake.mandrakeTransient().escapeDestination == null
                && thirdFailureDreamroot.dreamrootTransient().routeFailures == 3
                && thirdFailureDreamroot.dreamrootTransient().routeBackoff >= 100
                && thirdFailureDreamroot.dreamrootTransient().escapeDestination == null,
                "cadenced rejected request three alone clears destinations and starts full backoff");

            final MandrakeEntity noSafe = fixture.mandrake(new BlockPos(0, 3, 0));
            noSafe.setNoAi(true);
            for (final BlockPos offset : List.of(
                new BlockPos(2,0,0),new BlockPos(-2,0,0),new BlockPos(0,0,2),new BlockPos(0,0,-2),
                new BlockPos(2,0,2),new BlockPos(2,0,-2),new BlockPos(-2,0,2),new BlockPos(-2,0,-2),
                new BlockPos(3,0,0),new BlockPos(-3,0,0),new BlockPos(0,0,3),new BlockPos(0,0,-3),
                new BlockPos(3,0,3),new BlockPos(3,0,-3),new BlockPos(-3,0,3),new BlockPos(-3,0,-3))) {
                fixture.block(new BlockPos(offset.getX(), 3, offset.getZ()), Blocks.BARRIER.defaultBlockState());
            }
            noSafe.setRemainingFireTicks(2);
            MandrakeRuntime.tick(noSafe, helper.getLevel());
            noSafe.clearFire();
            helper.assertTrue(noSafe.mandrakeCounters().pathRequests == 0
                && noSafe.mandrakeCounters().safeReads <= MandrakeRules.SAFE_READ_CAP
                && noSafe.mandrakeCounters().safeEntityVisits <= MandrakeRules.OCCUPANCY_VISITS_PER_SEARCH
                && noSafe.mandrakeTransient().routeBackoff >= 100,
                "fully blocked local geometry issues no path and enters bounded no-safe backoff");
            mandrake.setRemainingFireTicks(2); dreamroot.setRemainingFireTicks(2);
            fixture.finish(160, () -> {
                helper.assertTrue(mandrake.mandrakeCounters().cancellations >= 1, "mandrake cancelled");
                helper.assertTrue(dreamroot.dreamrootCounters().cancellations >= 1, "dreamroot cancelled");
                helper.assertTrue(mandrake.getTarget() == null && dreamroot.getTarget() == null, "targets cleared");
                helper.assertTrue(mandrake.mandrakeCounters().unrootEvents == 1
                    && dreamroot.dreamrootCounters().unrootEvents == 1, "hazard unroots each species once");
                helper.assertTrue(mandrake.mandrakeCounters().hazardEscapeSuccesses == 1
                    && dreamroot.dreamrootCounters().hazardEscapeSuccesses == 1,
                    "each retained route reaches one validated safe destination before rerooting");
                helper.assertTrue(mandrake.mandrakeCounters().safeReads <= MandrakeRules.SAFE_READ_CAP
                    && dreamroot.dreamrootCounters().safeReads <= DreamrootRules.SAFE_READ_CAP,
                    "actual cache-backed reads stay within each exact search cap");
                helper.assertTrue(mandrake.mandrakeCounters().safeEntityVisits <= MandrakeRules.OCCUPANCY_VISITS_PER_SEARCH
                    && dreamroot.dreamrootCounters().safeEntityVisits <= DreamrootRules.OCCUPANCY_VISITS_PER_SEARCH,
                    "always-accept occupancy visits stay within each exact search cap");
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    public static void livingRootsSaveReloadAndZombieLifecycleAreReplaced(final GameTestHelper helper) {
        final Fixture fixture = new Fixture(helper);
        try {
            fixture.shell(); fixture.floor();
            final MandrakeEntity mandrake = fixture.mandrake(new BlockPos(0, 1, 0));
            final DreamrootEntity dreamroot = fixture.dreamroot(new BlockPos(2, 1, 0));
            mandrake.setMandrakeState(new MandrakeState(1, 321, 123));
            dreamroot.setDreamrootState(new DreamrootState(1, 234, 17, 99));
            final MandrakeEntity loadedMandrake = (MandrakeEntity) fixture.reload(mandrake, "mandrake", new BlockPos(0, 1, 1));
            final DreamrootEntity loadedDreamroot = (DreamrootEntity) fixture.reload(dreamroot, "dreamroot", new BlockPos(2, 1, 1));
            final List<MandrakeEntity> mandrakes = new ArrayList<>();
            final List<DreamrootEntity> dreamroots = new ArrayList<>();
            final int[] cohortRawBaseline = {0};
            long cohortTick = 40;
            for (final int population : List.of(1, 16, 64, 128)) {
                final long spawnTick = cohortTick;
                fixture.after(spawnTick, () -> {
                    for (int index = 0; index < population; index++) {
                        mandrakes.add(fixture.mandrake(new BlockPos(index % 7 - 3, 1, index % 7 - 3)));
                        dreamroots.add(fixture.dreamroot(new BlockPos(index % 7 - 3, 2, index % 7 - 3)));
                    }
                    cohortRawBaseline[0] = dreamroots.stream().mapToInt(entity -> entity.dreamrootCounters().rawVisits).sum();
                });
                fixture.after(spawnTick + 1, () -> {
                    final int raw = dreamroots.stream().mapToInt(entity -> entity.dreamrootCounters().rawVisits).sum();
                    helper.assertTrue(raw - cohortRawBaseline[0] <= 128, population + " Dreamroots share raw quota");
                    helper.assertTrue(mandrakes.stream().mapToInt(entity -> entity.mandrakeCounters().rawVisits).sum() == 0,
                        population + " rooted Mandrakes remain perception-free");
                });
                cohortTick += 50;
            }
            fixture.finish(192, () -> {
                helper.assertTrue(!net.minecraft.world.entity.monster.zombie.Zombie.class.isInstance(loadedMandrake), "mandrake non-zombie");
                helper.assertTrue(!net.minecraft.world.entity.monster.zombie.Zombie.class.isInstance(loadedDreamroot), "dreamroot non-zombie");
                helper.assertTrue(loadedMandrake.mandrakeState().wailCooldownRemaining() <= 321, "mandrake cooldown restored without replay");
                helper.assertTrue(loadedDreamroot.dreamrootState().dreamCooldownRemaining() <= 234, "dream cooldown restored without replay");
                helper.assertTrue(loadedMandrake.getTarget()==null && loadedDreamroot.getTarget()==null, "empty target lifecycle");
                helper.assertTrue(LivingRootsRules.rooted(loadedMandrake.mandrakeTransient().phase())
                    && LivingRootsRules.rooted(loadedDreamroot.dreamrootTransient().phase()), "load normalizes rooted phases");
                helper.assertTrue(loadedMandrake.mandrakeCounters().transientReplays == 0
                    && loadedDreamroot.dreamrootCounters().transientReplays == 0, "load replays no transient work");
                helper.assertTrue(mandrakes.stream().mapToInt(entity -> entity.mandrakeCounters().rawVisits).sum() == 0,
                    "1/16/64/128 rooted Mandrakes perform no entity queries");
                helper.assertTrue(mandrakes.stream().allMatch(entity -> entity.mandrakeCounters().genericBehaviorDispatches == 0)
                    && dreamroots.stream().allMatch(entity -> entity.dreamrootCounters().genericBehaviorDispatches == 0),
                    "population stress never reaches generic behavior");
                for (final var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    helper.assertTrue(loadedMandrake.getItemBySlot(slot).isEmpty(), "mandrake equipment empty");
                    helper.assertTrue(loadedDreamroot.getItemBySlot(slot).isEmpty(), "dreamroot equipment empty");
                }
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    private static void assertCancelledAndCleared(GameTestHelper helper,MandrakeEntity mandrake,DreamrootEntity dreamroot,String trigger){
        MandrakeRuntime.TransientState mandrakeState=mandrake.mandrakeTransient();
        DreamrootRuntime.TransientState dreamrootState=dreamroot.dreamrootTransient();
        helper.assertTrue(mandrake.mandrakeCounters().cancellations==1&&dreamroot.dreamrootCounters().cancellations==1,
            trigger+" cancels both live controllers exactly once");
        helper.assertTrue(mandrakeState.subject==null&&mandrakeState.subjectDimension==null
            &&dreamrootState.subject==null&&dreamrootState.subjectDimension==null&&dreamrootState.attacker==null
            &&dreamrootState.attackerDimension==null,
            trigger+" clears subject and attacker state");
        helper.assertTrue(mandrakeState.routeDelay==0&&mandrakeState.routeFailures==0&&mandrakeState.routeBackoff==0
            &&dreamrootState.routeDelay==0&&dreamrootState.routeFailures==0&&dreamrootState.routeBackoff==0
            &&mandrake.getNavigation().isDone()&&dreamroot.getNavigation().isDone(),
            trigger+" clears destination, route, and path state");
        helper.assertTrue(mandrake.getTarget()==null&&dreamroot.getTarget()==null
            &&mandrake.mandrakeCounters().wails==0&&mandrake.mandrakeCounters().nauseaApplications==0
            &&dreamroot.dreamrootCounters().dreams==0&&dreamroot.dreamrootCounters().darknessApplications==0
            &&mandrake.mandrakeCounters().feedbackEmitted==0&&dreamroot.dreamrootCounters().feedbackEmitted==0,
            trigger+" replays no effect, wail, dream, or feedback");
    }

    private static void assertAuthenticResettleRouteFailureLadder(GameTestHelper helper,Fixture fixture){
        final MandrakeEntity probe=fixture.mandrake(new BlockPos(-3,1,-2));
        probe.setMandrakeState(new MandrakeState(1,MandrakeRules.WAIL_COOLDOWN_TICKS,0));
        MandrakeRuntime.tick(probe,helper.getLevel());
        final BlockPos originalAnchor=probe.mandrakeTransient().anchor;
        final BlockPos displaced=helper.absolutePos(new BlockPos(2,1,-2));
        probe.snapTo(displaced.getX()+.5,displaced.getY(),displaced.getZ()+.5);
        probe.setOnGround(true);
        MandrakeRuntime.disturb(probe,null);
        for(int tick=0;tick<MandrakeRules.TELEGRAPH_TICKS+MandrakeRules.FLAIL_TICKS;tick++)
            MandrakeRuntime.tick(probe,helper.getLevel());
        helper.assertTrue(probe.mandrakeTransient().phase()==MandrakeRules.Phase.RESETTLE
            && originalAnchor.equals(probe.mandrakeTransient().anchor),
            "an authentic disturbance and full FLAIL window retain the original RESETTLE anchor");
        MandrakeRuntime.tick(probe,helper.getLevel());
        helper.assertTrue(probe.mandrakeCounters().pathRequests==1
            && probe.mandrakeCounters().pathsAccepted==1&&!probe.getNavigation().isDone(),
            "RESETTLE starts with one real accepted route to its retained anchor");
        probe.setNoAi(true);
        final int startTick=helper.getLevel().getServer().getTickCount();
        final boolean[] complete={false};
        helper.onEachTick(()->{
            if(complete[0]||probe.isRemoved())return;
            final int elapsed=helper.getLevel().getServer().getTickCount()-startTick;
            if(elapsed<25){MandrakeRuntime.tick(probe,helper.getLevel());return;}
            if(elapsed==25){
                helper.assertTrue(probe.mandrakeCounters().pathRequests==1
                    && probe.mandrakeCounters().navigationOverwrites==0
                    && probe.mandrakeTransient().routeFailures==0,
                    "a still-active accepted RESETTLE route is never overwritten at cadence");
                probe.setNoAi(false);probe.getNavigation().stop();MandrakeRuntime.tick(probe,helper.getLevel());probe.setNoAi(true);
                helper.assertTrue(probe.mandrakeTransient().routeFailures==1
                    && probe.mandrakeCounters().pathFailures==1
                    && probe.mandrakeCounters().pathsAccepted==2
                    && originalAnchor.equals(probe.mandrakeTransient().anchor),
                    "an accepted route that finishes short counts failure one and retains its anchor");
                return;
            }
            if(elapsed==26){
                probe.setNoAi(false);probe.getNavigation().stop();MandrakeRuntime.tick(probe,helper.getLevel());probe.setNoAi(true);
                helper.assertTrue(probe.mandrakeTransient().routeFailures==2
                    && probe.mandrakeCounters().pathFailures==2
                    && originalAnchor.equals(probe.mandrakeTransient().anchor),
                    "a second early finish counts once and still retains the exact anchor");
                return;
            }
            if(elapsed<45){MandrakeRuntime.tick(probe,helper.getLevel());return;}
            if(elapsed==45){
                probe.setNoAi(false);MandrakeRuntime.tick(probe,helper.getLevel());probe.setNoAi(true);
                helper.assertTrue(probe.mandrakeCounters().pathRequests==3
                    && probe.mandrakeCounters().pathsAccepted==3,
                    "the third real route request is accepted only on its actual cadence");
                return;
            }
            probe.setNoAi(false);probe.getNavigation().stop();MandrakeRuntime.tick(probe,helper.getLevel());probe.setNoAi(true);
            helper.assertTrue(probe.mandrakeCounters().pathFailures==3
                && probe.mandrakeCounters().pathBackoffs==1
                && probe.mandrakeTransient().routeBackoff>=MandrakeRules.ROUTE_BACKOFF_TICKS
                && probe.mandrakeTransient().phase()==MandrakeRules.Phase.SEEDED
                && probe.mandrakeCounters().reanchors==1
                && !originalAnchor.equals(probe.mandrakeTransient().anchor),
                "exact failure three alone clears the old anchor, reanchors here, and starts full backoff");
            complete[0]=true;
        });
    }

    private static ServerPlayer connectedSurvivalPlayer(GameTestHelper helper,BlockPos relative){ServerPlayer player=(ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);Connection connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);CommonListenerCookie cookie=CommonListenerCookie.createInitial(player.getGameProfile(),false);helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());player.setGameMode(GameType.SURVIVAL);BlockPos p=helper.absolutePos(relative);player.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);return GameTestMockPlayers.autoDisconnect(helper,player);}

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper; private final List<Entity> entities=new ArrayList<>(); private final List<SavedBlock> blocks=new ArrayList<>(); private boolean closed;
        Fixture(GameTestHelper helper){this.helper=helper;if(difficultyLeases++==0){savedDifficulty=helper.getLevel().getServer().getWorldData().getDifficulty();helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);}helper.onEachTick(()->{if(!closed&&helper.getLevel().getServer().getWorldData().getDifficulty()!=net.minecraft.world.Difficulty.NORMAL)helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);});}
        void floor(){for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)block(new BlockPos(x,0,z),Blocks.DIRT.defaultBlockState());}
        void shell(){for(int x=-4;x<=4;x++)for(int y=0;y<=4;y++)for(int z=-4;z<=4;z++)if(Math.abs(x)==4||Math.abs(z)==4||y==4){BlockPos p=new BlockPos(x,y,z);if(helper.getBlockState(p).isAir())block(p,Blocks.BARRIER.defaultBlockState());}}
        void block(BlockPos relative,BlockState state){BlockState before=helper.getBlockState(relative);blocks.add(new SavedBlock(relative,before));helper.setBlock(relative,state);}
        MandrakeEntity mandrake(BlockPos p){Entity e=ModEntities.ALL.get("mandrake").get().create(helper.getLevel(),EntitySpawnReason.EVENT);helper.assertTrue(e instanceof MandrakeEntity,"coordinator must route mandrake factory");return add((MandrakeEntity)e,p);}
        DreamrootEntity dreamroot(BlockPos p){Entity e=ModEntities.ALL.get("dreamroot").get().create(helper.getLevel(),EntitySpawnReason.EVENT);helper.assertTrue(e instanceof DreamrootEntity,"coordinator must route dreamroot factory");return add((DreamrootEntity)e,p);}
        LivingEntity living(String id,BlockPos p){EntityType<?> type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));Entity e=type.create(helper.getLevel(),EntitySpawnReason.EVENT);helper.assertTrue(e instanceof LivingEntity,"fixture living entity created");return add((LivingEntity)e,p);}
        Entity reload(Entity original,String id,BlockPos p){TagValueOutput output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,helper.getLevel().registryAccess());original.saveWithoutId(output);original.discard();Entity restored=ModEntities.ALL.get(id).get().create(helper.getLevel(),EntitySpawnReason.LOAD);helper.assertTrue(restored!=null,"reload entity created");restored.load(TagValueInput.create(ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),output.buildResult().copy()));return add(restored,p);}
        <T extends Entity>T add(T e,BlockPos p){BlockPos a=helper.absolutePos(p);e.snapTo(a.getX()+.5,a.getY(),a.getZ()+.5);helper.getLevel().addFreshEntity(e);entities.add(e);return e;}
        void track(Entity entity){entities.add(entity);}
        void after(long ticks,Runnable assertion){helper.runAfterDelay(ticks,()->{try{assertion.run();}catch(Throwable failure){close();throw failure;}});}
        void finish(long ticks,Runnable assertion){helper.runAfterDelay(ticks,()->{try{assertion.run();close();helper.succeed();}catch(Throwable failure){close();throw failure;}});}
        @Override public void close(){if(closed)return;closed=true;entities.forEach(Entity::discard);for(int i=blocks.size()-1;i>=0;i--){SavedBlock saved=blocks.get(i);helper.setBlock(saved.relative,saved.state);}if(--difficultyLeases==0){helper.getLevel().getServer().setDifficulty(savedDifficulty,true);savedDifficulty=null;}}
        private record SavedBlock(BlockPos relative,BlockState state){}
    }
}
