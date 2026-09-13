package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.kadamitas.warlockery.entity.ImpLifeRules.Authority;
import com.kadamitas.warlockery.entity.ImpLifeRules.Duty;
import com.kadamitas.warlockery.entity.ImpLifeRules.InfernalOrder;
import com.kadamitas.warlockery.entity.ImpLifeRules.Observation;
import com.kadamitas.warlockery.entity.ImpLifeRules.ObservationType;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderAction;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderRank;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ImpGameTests {
    private ImpGameTests() {
    }

    public static void impContractBindingFavorAndSpellsRemainExact(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(imp.getClass().getName(), ImpEntity.class.getName(),
                "the exact registered warlockery:imp must construct the public ImpEntity class");
            helper.assertValueEqual(imp.getAttributeValue(Attributes.MAX_HEALTH), 24.0D, "health 24");
            helper.assertValueEqual(imp.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.0D, "attack 5");
            helper.assertTrue(imp.fireImmune(), "inherent fire immunity stays exact");

            final ServerPlayer warlock = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            warlock.experienceLevel = 30;
            warlock.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("ingredient_contract").get(), 1));
            helper.assertTrue(imp.mobInteract(warlock, InteractionHand.MAIN_HAND).consumesAction(),
                "a survival infernal contract with 25 levels binds the imp");
            helper.assertValueEqual(warlock.experienceLevel, 5,
                "binding costs exactly twenty-five levels");
            helper.assertTrue(warlock.getMainHandItem().isEmpty(),
                "binding consumes exactly one contract item");
            helper.assertValueEqual(CreatureBehaviorState.owner(imp).orElseThrow(), warlock.getUUID(),
                "the common creature owner key records the binder");
            helper.assertTrue(imp.isPersistenceRequired(), "a bound imp requires persistence");
            helper.assertValueEqual(CreatureBehaviorState.impFavor(imp), 0,
                "binding seeds no favor beyond the existing default");

            for (int gift = 1; gift <= 7; gift++) {
                warlock.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 1));
                imp.mobInteract(warlock, InteractionHand.MAIN_HAND);
            }
            helper.assertValueEqual(CreatureBehaviorState.impFavor(imp), 6,
                "each gift increments favor once to the exact cap of six");

            warlock.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("ingredient_contract_fiery_touch").get(), 1));
            helper.assertTrue(imp.mobInteract(warlock, InteractionHand.MAIN_HAND).consumesAction(),
                "favor six satisfies the fiery touch spell gate through the live interaction route");
            helper.assertTrue(warlock.getMainHandItem().isEmpty(),
                "a successful spell consumes exactly one contract item");

            final ImpEntity fresh = spawnImp(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer pauper = fixture.connectedPlayer(new BlockPos(0, 1, 2));
            pauper.experienceLevel = 0;
            pauper.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("ingredient_contract").get(), 1));
            helper.assertFalse(fresh.mobInteract(pauper, InteractionHand.MAIN_HAND).consumesAction(),
                "a survival player without twenty-five levels is refused");
            helper.assertTrue(CreatureBehaviorState.owner(fresh).isEmpty(),
                "the refused binding writes no owner");
            helper.assertValueEqual(pauper.getMainHandItem().getCount(), 1,
                "the refused binding consumes nothing");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void impFamiliarBindRecallAndOwnerConflictRemainExact(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer ownerA = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            final ServerPlayer strangerB = fixture.connectedPlayer(new BlockPos(2, 1, 2));
            ownerA.experienceLevel = 30;
            strangerB.experienceLevel = 30;
            ownerA.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("ingredient_contract").get(), 1));
            helper.assertTrue(imp.mobInteract(ownerA, InteractionHand.MAIN_HAND).consumesAction(),
                "the binding fixture binds the imp to player A");

            strangerB.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("ingredient_contract").get(), 1));
            helper.assertFalse(imp.mobInteract(strangerB, InteractionHand.MAIN_HAND).consumesAction(),
                "a differently owned imp refuses a second binding");
            helper.assertValueEqual(strangerB.experienceLevel, 30,
                "the refused contract consumes no levels");
            helper.assertValueEqual(strangerB.getMainHandItem().getCount(), 1,
                "the refused contract consumes no item");
            helper.assertValueEqual(CreatureBehaviorState.owner(imp).orElseThrow(), ownerA.getUUID(),
                "the original owner key survives the conflicting attempt untouched");
            helper.assertFalse(CreatureBehaviorState.bind(imp, strangerB.getUUID()),
                "the shared owner transaction refuses silent retargeting");

            imp.getPersistentData().putString(InfernalPactEffects.OWNER_KEY, strangerB.getStringUUID());
            final long now = helper.getLevel().getGameTime();
            helper.assertValueEqual(ImpLifeRuntime.effectiveAuthority(imp, now), Authority.CONFLICTED,
                "different players in the two legacy keys resolve to a refused conflict");
            helper.assertFalse(ImpLifeRules.infernalSacrificeAuthorized(Authority.CONFLICTED),
                "a conflicting infernal key cannot consume the imp");
            ownerA.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(imp.mobInteract(ownerA, InteractionHand.MAIN_HAND).consumesAction(),
                "the contract owner keeps duty command under the conflict");
            strangerB.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            final boolean strangerCommanded = imp.mobInteract(strangerB, InteractionHand.MAIN_HAND)
                .consumesAction();
            helper.assertFalse(strangerCommanded && imp.lifeState().steadyDuty().isEmpty(),
                "the conflicting infernal holder gains no duty command");
            helper.assertValueEqual(CreatureBehaviorState.owner(imp).orElseThrow(), ownerA.getUUID(),
                "neither legacy key is transferred or deleted by the conflict");
            helper.assertValueEqual(
                imp.getPersistentData().getStringOr(InfernalPactEffects.OWNER_KEY, ""),
                strangerB.getStringUUID(),
                "the conflicting infernal key also survives without settlement");

            imp.getPersistentData().putString(InfernalPactEffects.OWNER_KEY, ownerA.getStringUUID());
            helper.assertValueEqual(ImpLifeRuntime.effectiveAuthority(imp, now),
                Authority.SAME_PLAYER_DUAL,
                "a same-player dual record stays fully compatible");
            helper.assertTrue(ImpLifeRules.infernalSacrificeAuthorized(Authority.SAME_PLAYER_DUAL),
                "the same player keeps the existing fire-sacrifice outcome");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void impFollowWatchAndScoutReturnAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            CreatureBehaviorState.bind(imp, owner.getUUID());
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            helper.assertTrue(imp.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "the first empty-hand command toggles the default FOLLOW duty to WATCH");
            helper.assertValueEqual(imp.lifeState().steadyDuty().orElseThrow(), Duty.WATCH,
                "the toggle lands on WATCH");
            helper.assertValueEqual(imp.lifeState().anchor().orElseThrow().position(),
                imp.blockPosition(),
                "WATCH stores the imp's current validated position as the anchor");

            helper.assertTrue(imp.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "the second empty-hand command toggles back to FOLLOW");
            helper.assertValueEqual(imp.lifeState().steadyDuty().orElseThrow(), Duty.FOLLOW,
                "the toggle lands on FOLLOW");
            helper.assertTrue(imp.lifeState().anchor().isEmpty(),
                "toggling to FOLLOW clears the watch anchor");

            owner.setShiftKeyDown(true);
            helper.assertTrue(imp.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "a crouching empty-hand command begins the one-shot scout");
            owner.setShiftKeyDown(false);
            helper.assertValueEqual(imp.lifeState().action(), Action.SCOUT_OUT,
                "the scout action starts outbound");
            helper.assertValueEqual(imp.lifeState().priorDuty().orElseThrow(), Duty.FOLLOW,
                "the prior steady duty is remembered for resumption");
            helper.assertValueEqual(imp.lifeState().anchor().orElseThrow().position(),
                owner.blockPosition(),
                "the scout return anchor is the owner's loaded position");
            helper.assertTrue(imp.lifeState().actionTimeoutAt()
                    <= helper.getLevel().getGameTime() + ImpLifeRules.SCOUT_TOTAL_TICKS,
                "the whole scout is bounded at six hundred ticks");

            helper.runAfterDelay(12L, () -> {
                try {
                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    helper.assertTrue(imp.scoutChargedReads() <= ImpLifeRules.SCOUT_TOTAL_READ_BUDGET,
                        "scout waypoint selection stays inside the charged 192-read budget");
                    helper.assertTrue(imp.lifeCounters().navigationRequests() <= 2L,
                        "each scout decision issues at most one bounded navigation request");

                    // Straight up into open sky: far enough to force recovery, and guaranteed not
                    // to land inside a neighboring batch cell or its isolation shell.
                    final BlockPos far = imp.blockPosition().offset(0, 30, 0);
                    owner.teleportTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D);
                    imp.setLifeState(imp.lifeState()
                        .withAction(Action.NONE)
                        .withDestination(Optional.empty())
                        .withDuties(Optional.of(Duty.FOLLOW), Optional.empty())
                        .withAnchor(Optional.empty())
                        .withScout(0, true));
                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    final double recovered = imp.distanceTo(owner);
                    helper.assertTrue(recovered <= ImpLifeRules.FOLLOW_PATH_DISTANCE + 1.0D,
                        "a far owner triggers safe recovery or bounded navigation, never a lost imp");
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(16L, () -> {
                try {
                    helper.assertTrue(owner.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                        "the exact level zero fire resistance aura reaches a same-dimension owner");
                    final var aura = owner.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
                    helper.assertValueEqual(aura.getAmplifier(), 0, "the aura stays amplifier zero");
                    helper.assertTrue(aura.getDuration() <= ImpLifeRules.OWNER_AURA_DURATION_TICKS,
                        "the aura keeps its exact sixty-tick refresh window");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impScoutInterruptReloadAndReportOnce(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            CreatureBehaviorState.bind(imp, owner.getUUID());
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setShiftKeyDown(true);
            helper.assertTrue(imp.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
                "the scout fixture starts through the real crouch command");
            owner.setShiftKeyDown(false);

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            imp.saveWithoutId(output);
            final ImpEntity reloaded = (ImpEntity) impType()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(reloaded != null, "the registered imp type must recreate saved state");
            fixture.track(reloaded);
            reloaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult().copy()));
            helper.assertValueEqual(reloaded.lifeState().action(), Action.SCOUT_OUT,
                "an outbound scout with a valid anchor resumes after reload");
            helper.assertValueEqual(reloaded.lifeState().anchor().orElseThrow().position(),
                imp.lifeState().anchor().orElseThrow().position(),
                "the return anchor survives the reload exactly");
            helper.assertTrue(reloaded.getNavigation().isDone(),
                "no raw path resumes from disk");
            helper.assertValueEqual(reloaded.scoutChargedReads(), 0,
                "reload performs no catch-up reads");

            final Zombie hostile = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1),
                EntitySpawnReason.EVENT);
            hostile.setNoAi(true);
            helper.runAfterDelay(12L, () -> {
                try {
                    imp.setLifeState(imp.lifeState()
                        .withObservations(ImpLifeRules.recordObservation(
                            imp.lifeState().observations(),
                            new Observation(ObservationType.HOSTILE,
                                hostile.blockPosition().asLong(),
                                Optional.of(hostile.getUUID()),
                                helper.getLevel().getGameTime(),
                                helper.getLevel().getGameTime(),
                                1_000,
                                helper.getLevel().getGameTime() + ImpLifeRules.OBSERVATION_EXPIRY_TICKS),
                            helper.getLevel().getGameTime()))
                        .withAction(Action.SCOUT_RETURN)
                        .withScout(ImpLifeRules.SCOUT_LEGS, false));
                    final BlockPos anchor = imp.lifeState().anchor().orElseThrow().position();
                    imp.snapTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 0.0F, 0.0F);
                    imp.setDeltaMovement(Vec3.ZERO);
                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    helper.assertTrue(imp.lifeState().reportDelivered(),
                        "arriving at the anchor with a live owner delivers the report");
                    helper.assertValueEqual(imp.lifeCounters().reportsDelivered(), 1L,
                        "the report is delivered exactly once");
                    helper.assertValueEqual(imp.lifeState().steadyDuty().orElseThrow(), Duty.FOLLOW,
                        "delivery resumes the remembered prior duty");

                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    helper.assertValueEqual(imp.lifeCounters().reportsDelivered(), 1L,
                        "duplicate ticks cannot repeat the delivered report");

                    final TagValueOutput afterOutput = TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    imp.saveWithoutId(afterOutput);
                    final ImpEntity afterReload = (ImpEntity) impType()
                        .create(helper.getLevel(), EntitySpawnReason.LOAD);
                    fixture.track(afterReload);
                    afterReload.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                        afterOutput.buildResult().copy()));
                    helper.assertTrue(afterReload.lifeState().reportDelivered(),
                        "the delivered flag survives reload so reconnect cannot repeat the report");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impCuriosityInspectsWithoutStorageMutation(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            // (4,1,1) is the deterministically sampled curiosity offset (+3,0,0) from the imp at
            // (1,1,1); the discovery pattern samples radii 3/6/8 only, so a two-block offset is
            // never read. Sampling reads blocks directly, so the enclosing barrier wall between
            // the imp and the stimulus does not hide it.
            final BlockPos stimulus = helper.absolutePos(new BlockPos(4, 1, 1));
            helper.getLevel().setBlock(stimulus, Blocks.CAMPFIRE.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                stimulus, Blocks.AIR.defaultBlockState(), 3));
            final var stimulusState = helper.getLevel().getBlockState(stimulus);

            final BlockPos chestPos = helper.absolutePos(new BlockPos(2, 1, 0));
            helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                chestPos, Blocks.AIR.defaultBlockState(), 3));
            final var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)
                helper.getLevel().getBlockEntity(chestPos);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));

            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.assertValueEqual(imp.lifeCounters().curiosityScans(), 1L,
                "an idle unbound imp runs exactly one curiosity discovery per cadence");
            helper.assertTrue(imp.lifeCounters().blockReads() <= ImpLifeRules.CURIOSITY_READ_BUDGET,
                "the discovery charges at most ninety-six actual block reads");
            helper.assertTrue(imp.lifeState().observations().stream()
                    .anyMatch(row -> row.type() == ObservationType.HEAT),
                "the campfire is retained as a typed heat stimulus");
            helper.assertTrue(imp.lifeState().observations().size() <= ImpLifeRules.MAX_OBSERVATIONS,
                "stimulus memory holds at most four rows");
            helper.assertValueEqual(imp.lifeState().action(), Action.INSPECT,
                "a reachable stimulus starts one committed inspect action");

            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.runAfterDelay(6L, () -> {
                try {
                    helper.assertValueEqual(helper.getLevel().getBlockState(stimulus), stimulusState,
                        "inspect never mutates the stimulus block");
                    helper.assertValueEqual(chest.getItem(0).getCount(), 3,
                        "inspect never opens, reads, or moves container inventory");
                    helper.assertTrue(imp.getMainHandItem().isEmpty() && imp.getOffhandItem().isEmpty(),
                        "the imp never picks an item up during mischief");

                    helper.getLevel().setBlock(stimulus, Blocks.AIR.defaultBlockState(), 3);
                    imp.setLifeState(imp.lifeState()
                        .withAction(Action.INSPECT)
                        .withDestination(Optional.of(new ImpLifeState.Anchor(
                            helper.getLevel().dimension().identifier().toString(), stimulus))));
                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    helper.assertValueEqual(imp.lifeState().action(), Action.NONE,
                        "a destroyed stimulus cancels the inspect cleanly");
                    helper.assertTrue(imp.lifeState().deadlines().curiosityBackoffUntil()
                            >= helper.getLevel().getGameTime() + ImpLifeRules.CURIOSITY_BACKOFF_TICKS - 1L,
                        "a failed stimulus backs curiosity off six hundred ticks");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impPerchCollisionBorderAndChunkEdgeFailSafely(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final BlockPos blocked = helper.absolutePos(new BlockPos(2, 1, 1));
            helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                blocked, Blocks.AIR.defaultBlockState(), 3));

            final int[] budget = {ImpLifeRules.WAYPOINT_READ_BUDGET};
            helper.assertValueEqual(
                ImpLifeRuntime.validateDestination(imp, helper.getLevel(), blocked, budget),
                ImpLifeRuntime.DestinationCheck.COLLISION,
                "a candidate that fails the full imp AABB collision test is refused");
            helper.assertValueEqual(
                ImpLifeRuntime.validateDestination(imp, helper.getLevel(),
                    new BlockPos(0, 5_000, 0), budget),
                ImpLifeRuntime.DestinationCheck.OUTSIDE_BORDER,
                "a candidate above the world build limit is refused before any read");
            helper.assertValueEqual(
                ImpLifeRuntime.validateDestination(imp, helper.getLevel(),
                    imp.blockPosition().offset(100_000, 0, 0), budget),
                ImpLifeRuntime.DestinationCheck.UNLOADED,
                "an unloaded footprint reports UNLOADED and never loads the chunk");
            final int[] exhausted = {2};
            helper.assertValueEqual(
                ImpLifeRuntime.validateDestination(imp, helper.getLevel(),
                    helper.absolutePos(new BlockPos(1, 2, 2)), exhausted),
                ImpLifeRuntime.DestinationCheck.BUDGET_EXHAUSTED,
                "an exhausted shared read budget defers cleanly instead of widening the search");
            // The test cell is enclosed by barriers hugging the 3x3x3 interior (ceiling at
            // relative y=3), so the hover candidate must sit inside the interior over open air.
            final BlockPos hover = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.assertValueEqual(
                ImpLifeRuntime.validatePerch(imp, helper.getLevel(), hover, budget),
                ImpLifeRuntime.DestinationCheck.NO_SUPPORT,
                "a perch without sturdy support is refused while a hover may still allow it");

            imp.setLifeState(imp.lifeState().withDestination(Optional.of(new ImpLifeState.Anchor(
                helper.getLevel().dimension().identifier().toString(), blocked))));
            ImpLifeState state = imp.lifeState();
            final long before = helper.getLevel().getGameTime();
            state = ImpLifeRuntime.recordRouteFailure(imp, state, before);
            helper.assertValueEqual(state.routeFailures(), 1, "the first route failure is counted");
            state = ImpLifeRuntime.recordRouteFailure(imp, state, before);
            state = ImpLifeRuntime.recordRouteFailure(imp, state, before);
            imp.setLifeState(state);
            helper.assertTrue(state.destination().isEmpty(),
                "the third route failure clears the destination");
            helper.assertValueEqual(state.routeFailures(), 0,
                "the counter resets once the backoff begins");
            helper.assertTrue(state.deadlines().recoveryUntil()
                    >= before + ImpLifeRules.ROUTE_BACKOFF_TICKS,
                "three failures impose at least one hundred ticks of recovery");
            helper.assertTrue(imp.getNavigation().isDone(),
                "three failures stop the live navigator");

            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.assertTrue(imp.isAlive() && imp.lifeState().action() != Action.PERCH,
                "the recovering imp holds safely instead of forcing a perch");

            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            // The recovering first imp is done; remove it so entity pushing cannot displace the
            // live imp off its validated roost inside the narrow enclosed cell.
            imp.discard();
            // Roost at the interior floor band: the imp is 1.05 tall, so feet at relative y=1
            // keep the whole AABB inside the barrier-enclosed interior (ceiling at y=3).
            final BlockPos roost = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.getLevel().setBlock(roost.below(), Blocks.STONE.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                roost.below(), Blocks.AIR.defaultBlockState(), 3));
            // (4,1,1) is the deterministically sampled curiosity offset (+3,0,0) from the roost;
            // discovery samples blocks directly, so the intervening barrier wall does not hide it,
            // and at distance three the arrival path promotes without needing navigation.
            final BlockPos stimulus = helper.absolutePos(new BlockPos(4, 1, 1));
            helper.getLevel().setBlock(stimulus, Blocks.CAMPFIRE.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                stimulus, Blocks.AIR.defaultBlockState(), 3));
            final ImpEntity liveImp = spawnImp(fixture, new BlockPos(1, 1, 1));
            liveImp.snapTo(roost.getX() + 0.5D, roost.getY(), roost.getZ() + 0.5D, 0.0F, 0.0F);
            liveImp.setDeltaMovement(Vec3.ZERO);
            makeDue(liveImp);
            helper.runAfterDelay(2L, () -> {
                try {
                    helper.assertValueEqual(liveImp.lifeState().action(), Action.INSPECT,
                        "the live self-ticking imp discovers the stimulus and commits an inspect");
                    liveImp.snapTo(roost.getX() + 0.5D, roost.getY(), roost.getZ() + 0.5D, 0.0F, 0.0F);
                    liveImp.setDeltaMovement(Vec3.ZERO);
                    makeDue(liveImp);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(4L, () -> {
                try {
                    helper.assertValueEqual(liveImp.lifeState().action(), Action.PERCH,
                        "arrival over sturdy support promotes the live inspect to a real perch");
                    helper.assertTrue(liveImp.lifeState().actionTimeoutAt()
                            <= helper.getLevel().getGameTime() + ImpLifeRules.INSPECT_MAX_TICKS,
                        "the live perch dwell stays inside the eighty-tick ceiling");
                    helper.assertTrue(liveImp.getNavigation().isDone(),
                        "the perched imp holds its roost without further navigation");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impRangedLaneWindupAndRetreatAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos cellCenter = new BlockPos(7, 1, 7);
            final ImpEntity imp = spawnImp(fixture, cellCenter);
            erectFrameworkCell(fixture, cellCenter);
            erectIsolationShell(fixture, cellCenter);
            // The 3x3x3 cell is enclosed by barriers, so the ranged lane is staged in the open-air
            // corridor east of the cell, inside the radius-five isolation shell. The zombie needs
            // its own support block because the world outside the cell floor is open air.
            final BlockPos targetSupport = helper.absolutePos(new BlockPos(11, 0, 3));
            helper.getLevel().setBlock(targetSupport, Blocks.STONE.defaultBlockState(), 3);
            fixture.onClose(() -> helper.getLevel().setBlock(
                targetSupport, Blocks.AIR.defaultBlockState(), 3));
            final Zombie target = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(11, 1, 3),
                EntitySpawnReason.EVENT);
            target.setNoAi(true);
            imp.invulnerableTime = 0;
            helper.assertTrue(imp.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(target), 1.0F
            ), "the combat fixture needs one real attributed hit");
            imp.setTarget(target);

            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.assertTrue(imp.lifeCounters().lineOfSightChecks()
                    <= 2L + ImpLifeRules.LANE_LINE_OF_SIGHT_CHECKS,
                "one combat decision stays inside the line-of-sight budget");
            helper.assertTrue(imp.lifeCounters().laneSearches() <= 1L,
                "at most one lane search runs per decision");
            helper.assertTrue(imp.lifeCounters().blockReads() <= ImpLifeRules.LANE_READ_BUDGET,
                "lane validation charges at most sixty-four actual reads");

            // The lane search above owns navigation and move-control state. Use a fresh live body
            // for the windup so the test observes combat timing without residual route steering.
            imp.discard();
            // Eight blocks south of the target along the clear corridor east of the barrier cell:
            // inside the preferred eight-to-twelve band with unobstructed line of sight.
            final BlockPos firingSpot = helper.absolutePos(new BlockPos(11, 1, 11));
            final ImpEntity shooter = spawnImp(fixture, new BlockPos(11, 1, 11));
            final boolean[] windupStarted = {false};
            final long[] windupStart = {-1L};
            final boolean[] shotObserved = {false};
            helper.onEachTick(() -> {
                if (windupStarted[0] || shooter.tickCount <= 0) {
                    return;
                }
                // Begin the bound only after the server has promoted the newly inserted body to
                // live entity ticking; padding and structure placement alone do not commit that
                // promotion before the GameTest's initial callback on every chunk alignment.
                shooter.snapTo(firingSpot.getX() + 0.5D, firingSpot.getY(), firingSpot.getZ() + 0.5D,
                    0.0F, 0.0F);
                shooter.setDeltaMovement(Vec3.ZERO);
                shooter.setTarget(target);
                shooter.getSensing().tick();
                makeDue(shooter);
                ImpLifeRuntime.tick(shooter, helper.getLevel());
                helper.assertValueEqual(shooter.lifeState().action(), Action.RANGED_WINDUP,
                    "a preferred-band target with line of sight starts the ten-tick windup");
                windupStart[0] = shooter.lifeState().deadlines().windupStartedAt();
                helper.assertTrue(windupStart[0] > 0L, "the windup records its start tick");
                windupStarted[0] = true;
            });
            helper.onEachTick(() -> {
                if (shotObserved[0] || shooter.lifeCounters().shotsFired() != 1L) {
                    return;
                }
                shotObserved[0] = true;
                try {
                    helper.assertValueEqual(shooter.lifeCounters().shotsFired(), 1L,
                        "the completed windup releases exactly one projectile");
                    helper.assertFalse(helper.getLevel().getEntitiesOfClass(
                            SmallFireball.class, shooter.getBoundingBox().inflate(24.0D)).isEmpty(),
                        "a real small fireball leaves the live ticking imp");
                    helper.getLevel().getEntitiesOfClass(
                        SmallFireball.class, shooter.getBoundingBox().inflate(24.0D)).forEach(fixture::track);
                    helper.assertTrue(shooter.lifeState().deadlines().lastShotAt() > 0L,
                        "the shot stamps the thirty-tick cadence gate");
                    makeDue(shooter);
                    ImpLifeRuntime.tick(shooter, helper.getLevel());
                    helper.assertFalse(shooter.lifeState().action() == Action.RANGED_WINDUP,
                        "no second windup may start inside the thirty-tick cadence");

                    shooter.setHealth(shooter.getMaxHealth() * 0.20F);
                    makeDue(shooter);
                    ImpLifeRuntime.tick(shooter, helper.getLevel());
                    helper.assertTrue(shooter.lifeState().retreatLatched(),
                        "twenty percent health latches the retreat");
                    helper.assertValueEqual(shooter.lifeState().action(), Action.DISENGAGE,
                        "the latched imp disengages instead of firing");
                    helper.assertTrue(shooter.getTarget() == null,
                        "retreat releases the target claim");

                    shooter.setHealth(shooter.getMaxHealth() * 0.50F);
                    makeDue(shooter);
                    ImpLifeRuntime.tick(shooter, helper.getLevel());
                    helper.assertFalse(shooter.lifeState().retreatLatched(),
                        "forty-five percent health with a safe lane releases the latch");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
            // A newly inserted mob may take its first entity tick on either side of the GameTest
            // callback phase. Await the observable shot, but keep the production windup's own
            // fifteen-tick action window as a hard deadline.
            helper.onEachTick(() -> {
                if (shotObserved[0] || !windupStarted[0]
                    || helper.getLevel().getGameTime() - windupStart[0]
                        <= ImpLifeRules.WINDUP_TICKS + 5L) {
                    return;
                }
                try {
                    helper.assertTrue(false,
                        "the live windup must release its projectile inside the bounded action window");
                } finally {
                    fixture.close();
                }
            });
            helper.runAfterDelay(40L, () -> {
                if (windupStarted[0]) {
                    return;
                }
                try {
                    helper.assertTrue(false, "the fresh shooter must enter live entity ticking");
                } finally {
                    fixture.close();
                }
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impProjectileAlliesGriefingAndProtectedBlocksAreSafe(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final var level = helper.getLevel();
            final boolean previousGriefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
            fixture.onClose(() -> level.getGameRules().set(
                GameRules.MOB_GRIEFING, previousGriefing, level.getServer()));
            // Griefing stays off for the ally pass: the ember that passes through the filtered
            // ally strikes the cell's enclosing barrier wall, and fire placed there would ignite
            // the in-cell fixture. The griefing and protected-block passes set the rule they need.
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());

            // Feet at relative y=1: the imp's 1.05-block hitbox stays clear of the barrier ceiling
            // at y=3, and both imps stand inside the enclosed interior instead of inside its walls.
            final ImpEntity shooter = spawnImp(fixture, new BlockPos(0, 1, 1));
            shooter.setNoAi(true);
            final ImpEntity ally = spawnImp(fixture, new BlockPos(2, 1, 1));
            ally.setNoAi(true);
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(0, 1, 0));
            CreatureBehaviorState.bind(shooter, owner.getUUID());
            CreatureBehaviorState.bind(ally, owner.getUUID());
            final float allyHealth = ally.getHealth();

            shooter.performRangedAttack(ally, 1.0F);
            final List<SmallFireball> embers = level.getEntitiesOfClass(
                SmallFireball.class, shooter.getBoundingBox().inflate(16.0D));
            helper.assertFalse(embers.isEmpty(), "the ranged attack spawns one real ember");
            for (final SmallFireball ember : embers) {
                fixture.track(ember);
                helper.assertTrue(ember instanceof ImpFireball,
                    "the live ember carries the server-only relationship filter");
                helper.assertTrue(ember.getOwner() == shooter,
                    "the ember keeps the imp as its attributed owner");
                helper.assertFalse(ember.shouldBeSaved(),
                    "the ember never survives chunk serialization as an unfiltered vanilla fireball");
            }
            helper.runAfterDelay(10L, () -> {
                try {
                    helper.assertValueEqual(ally.getHealth(), allyHealth,
                        "an effective ally of the same owner takes no ember collision");
                    helper.assertFalse(ally.isOnFire(), "the ally is never ignited");

                    level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
                    final BlockPos wall = helper.absolutePos(new BlockPos(5, 2, 1));
                    level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                    fixture.onClose(() -> level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3));
                    final ImpFireball griefTest = new ImpFireball(
                        level, shooter, new Vec3(1.0D, 0.0D, 0.0D));
                    griefTest.setPos(wall.getX() - 0.5D, wall.getY() + 0.5D, wall.getZ() + 0.5D);
                    level.addFreshEntity(griefTest);
                    fixture.track(griefTest);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(20L, () -> {
                try {
                    final BlockPos wall = helper.absolutePos(new BlockPos(5, 2, 1));
                    helper.assertFalse(anyFireNear(helper, wall),
                        "mobGriefing false forbids ember fire placement exactly as vanilla Forge does");

                    level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
                    final BlockPos altar = helper.absolutePos(new BlockPos(5, 2, 3));
                    level.setBlock(altar,
                        com.kadamitas.warlockery.registry.ModBlocks.ALTAR.get().defaultBlockState(), 3);
                    fixture.onClose(() -> level.setBlock(altar, Blocks.AIR.defaultBlockState(), 3));
                    helper.assertTrue(level.getBlockState(altar).is(ImpFireball.PROTECTED_BLOCKS),
                        "the opt-out tag covers the protected ritual infrastructure by default");
                    final ImpFireball protectedTest = new ImpFireball(
                        level, shooter, new Vec3(1.0D, 0.0D, 0.0D));
                    protectedTest.setPos(altar.getX() - 0.5D, altar.getY() + 0.5D, altar.getZ() + 0.5D);
                    level.addFreshEntity(protectedTest);
                    fixture.track(protectedTest);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(30L, () -> {
                try {
                    final BlockPos altar = helper.absolutePos(new BlockPos(5, 2, 3));
                    helper.assertFalse(anyFireNear(helper, altar),
                        "a protected block refuses ember fire even with mobGriefing on");
                    helper.assertTrue(level.getBlockState(altar).is(
                            com.kadamitas.warlockery.registry.ModBlocks.ALTAR.get()),
                        "the protected altar itself survives the impact");

                    final Zombie victim = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2),
                        EntitySpawnReason.EVENT);
                    victim.setNoAi(true);
                    victim.invulnerableTime = 0;
                    helper.assertTrue(shooter.doHurtTarget(level, victim),
                        "the cornered melee path lands one ordinary attributed attack");
                    helper.assertTrue(victim.getRemainingFireTicks() >= 70
                            && victim.getRemainingFireTicks() <= 80,
                        "the successful melee keeps the exact four-second fire rider");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impBoundEnvironmentalImmunityDoesNotTransferDamage(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final ImpEntity bound = spawnImp(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            CreatureBehaviorState.bind(bound, owner.getUUID());
            final float ownerHealth = owner.getHealth();
            final float boundHealth = bound.getHealth();

            bound.invulnerableTime = 0;
            helper.assertFalse(bound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().fall(), 6.0F
            ), "a validly bound imp ignores fall damage");
            bound.invulnerableTime = 0;
            helper.assertFalse(bound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().drown(), 6.0F
            ), "a validly bound imp ignores drowning damage");
            helper.assertValueEqual(bound.getHealth(), boundHealth,
                "ignored environmental sources change no health");

            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1),
                EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            bound.invulnerableTime = 0;
            helper.assertTrue(bound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 2.0F
            ), "attacker-caused damage remains fully effective on a bound imp");
            bound.invulnerableTime = 0;
            helper.assertTrue(bound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().magic(), 2.0F
            ), "magic damage remains effective on a bound imp");
            helper.assertValueEqual(owner.getHealth(), ownerHealth,
                "no imp damage transfers to the owner");

            final ImpEntity unbound = spawnImp(fixture, new BlockPos(0, 1, 0));
            final float unboundHealth = unbound.getHealth();
            unbound.invulnerableTime = 0;
            helper.assertTrue(unbound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().fall(), 4.0F
            ), "an unbound imp gains no new familiar safety beyond inherent fire immunity");
            helper.assertTrue(unbound.getHealth() < unboundHealth,
                "the unbound fall damage really applies");
            unbound.invulnerableTime = 0;
            helper.assertFalse(unbound.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().inFire(), 4.0F
            ), "inherent fire immunity stays for every imp");
            helper.assertFalse(ImpLifeRules.familiarDamageTransfers(),
                "the imp remains excluded from classic familiar damage transfer");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void impInfernalOrdersAuthorityConflictsAndLeaderLossAreSafe(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final Zombie issuer = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1),
                EntitySpawnReason.EVENT);
            issuer.setNoAi(true);
            final UUID group = UUID.randomUUID();
            final InfernalOrder order = new InfernalOrder(
                issuer.getUUID(), OrderRank.ARCHFIEND, group, 1L, OrderAction.WATCH,
                Optional.empty(), now, now + 5_000L);

            helper.assertFalse(ImpLifeRuntime.offerOrder(imp, helper.getLevel(), order, 2, 2, false),
                "a full two-imp slot allocation refuses the archfiend order");
            helper.assertFalse(ImpLifeRuntime.offerOrder(imp, helper.getLevel(), order, 4, 0, false),
                "a full four-subordinate squad refuses the archfiend order");
            helper.assertFalse(ImpLifeRuntime.offerOrder(imp, helper.getLevel(), order, 0, 0, true),
                "an imp can never delegate an order to another imp");

            helper.assertTrue(ImpLifeRuntime.offerOrder(imp, helper.getLevel(), order, 1, 0, false),
                "a valid unbound loaded same-dimension imp accepts one eligible order");
            helper.assertValueEqual(imp.lifeCounters().ordersAccepted(), 1L, "acceptance is counted");
            final InfernalOrder stored = imp.lifeState().order().orElseThrow();
            helper.assertTrue(stored.expiresAt() <= now + ImpLifeRules.ORDER_MAX_TICKS,
                "the stored expiry clamps to the six-hundred-tick maximum");

            helper.assertFalse(ImpLifeRuntime.offerOrder(imp, helper.getLevel(),
                new InfernalOrder(issuer.getUUID(), OrderRank.REGENT, group, 1L,
                    OrderAction.SCOUT, Optional.empty(), now, now + 100L), 0, 0, false),
                "an equal or lower epoch cannot replace the active order");

            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(0, 1, 2));
            // The mock server player pins gameMode() to its construction argument, so a creative
            // check needs a dedicated creative mock rather than setGameMode on the survival one.
            final ServerPlayer creative = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
            fixture.track(creative);
            helper.assertFalse(ImpLifeRuntime.mayAttack(imp, creative),
                "HARASS can never authorize an attack on a creative player");

            final ImpEntity bound = spawnImp(fixture, new BlockPos(0, 1, 0));
            CreatureBehaviorState.bind(bound, player.getUUID());
            helper.assertFalse(ImpLifeRuntime.offerOrder(bound, helper.getLevel(), order, 0, 0, false),
                "a player-bound imp refuses every NPC rank order");
            helper.assertFalse(ImpLifeRuntime.mayAttack(bound, player),
                "a bound imp never acquires its own neutral owner-side player");

            // Combat legitimately outranks the order band, and an unbound imp discovers survival
            // players within twenty-four blocks; park the fixture players high overhead so the
            // NPC_ORDER claim is observable.
            player.teleportTo(player.getX(), player.getY() + 100.0D, player.getZ());
            imp.setTarget(null);
            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.assertValueEqual(imp.lifeState().action(), Action.NPC_ORDER,
                "the accepted order claims the NPC order band while the issuer lives");

            issuer.discard();
            makeDue(imp);
            ImpLifeRuntime.tick(imp, helper.getLevel());
            helper.assertTrue(imp.lifeState().order().isEmpty(),
                "issuer loss clears the order and its group reservation");
            helper.assertTrue(imp.lifeCounters().ordersCleared() >= 1L, "the clearance is counted");
            helper.assertFalse(imp.lifeState().action() == Action.NPC_ORDER,
                "the cleared order releases the action band");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void impStateMigrationCorruptionAndExpiryAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final ImpEntity imp = spawnImp(fixture, new BlockPos(1, 1, 1));
            final ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            CreatureBehaviorState.bind(imp, owner.getUUID());

            final CompoundTag corrupt = new CompoundTag();
            corrupt.putInt("Version", 999);
            imp.setLifeState(ImpLifeState.read(corrupt, imp.getUUID(), now));
            helper.assertValueEqual(imp.lifeState().action(), Action.NONE,
                "an unknown schema version becomes a safe empty action state");
            helper.assertValueEqual(CreatureBehaviorState.owner(imp).orElseThrow(), owner.getUUID(),
                "the existing owner key survives the discarded semantic payload");

            final CompoundTag malformed = new CompoundTag();
            malformed.putInt("Version", ImpLifeState.SCHEMA_VERSION);
            malformed.putString("Duty", "dance");
            malformed.putString("Action", "ranged_windup");
            malformed.putString("ThreatId", "not-a-uuid");
            malformed.putString("OrderIssuer", "also-not-a-uuid");
            malformed.putString("OrderRank", "archfiend");
            malformed.putLong("RecoveryUntil", Long.MAX_VALUE - 5L);
            malformed.putInt("ObservationCount", 12);
            for (int index = 0; index < 12; index++) {
                final CompoundTag row = new CompoundTag();
                row.putString("Type", index % 2 == 0 ? "shiny" : "sideways");
                row.putLong("Pos", new BlockPos(index, 64, index).asLong());
                row.putLong("ExpiresAt", now + 500L);
                malformed.put("Observation" + index, row);
            }
            final ImpLifeState reconciled = ImpLifeState.read(malformed, imp.getUUID(), now);
            helper.assertTrue(reconciled.steadyDuty().isEmpty(),
                "an unknown duty enum is dropped rather than guessed");
            helper.assertValueEqual(reconciled.action(), Action.NONE,
                "a non-resumable saved action reconciles to NONE");
            helper.assertTrue(reconciled.threat().isEmpty(), "a malformed threat UUID is dropped");
            helper.assertTrue(reconciled.order().isEmpty(),
                "an incomplete order row is rejected as a whole");
            helper.assertTrue(reconciled.observations().size() <= ImpLifeRules.MAX_OBSERVATIONS,
                "oversized observation lists truncate deterministically to four");
            helper.assertTrue(reconciled.observations().stream()
                    .allMatch(row -> row.type() == ObservationType.SHINY),
                "unknown observation types are dropped row by row");
            helper.assertTrue(reconciled.deadlines().recoveryUntil()
                    <= now + ImpLifeRules.MAX_FUTURE_HORIZON_TICKS,
                "hostile future deadlines clamp to the bounded horizon");

            final CompoundTag scoutNoAnchor = new CompoundTag();
            scoutNoAnchor.putInt("Version", ImpLifeState.SCHEMA_VERSION);
            scoutNoAnchor.putString("Action", "scout_return");
            helper.assertValueEqual(
                ImpLifeState.read(scoutNoAnchor, imp.getUUID(), now).action(), Action.NONE,
                "a scout return without a valid anchor cancels rather than inventing one");

            imp.setLifeState(ImpLifeState.empty(imp.getUUID(), now).withOrder(Optional.of(
                new InfernalOrder(UUID.randomUUID(), OrderRank.REGENT, UUID.randomUUID(), 1L,
                    OrderAction.WATCH, Optional.empty(), now, now + 2L))));
            helper.runAfterDelay(5L, () -> {
                try {
                    makeDue(imp);
                    ImpLifeRuntime.tick(imp, helper.getLevel());
                    helper.assertTrue(imp.lifeState().order().isEmpty(),
                        "an order expiring while time passes is removed on the next live decision");
                    helper.assertValueEqual(imp.lifeCounters().shotsFired(), 0L,
                        "no catch-up combat happens after idle or unloaded time");
                    helper.assertValueEqual(imp.lifeCounters().reportsDelivered(), 0L,
                        "no catch-up report happens after idle or unloaded time");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void impPopulationCadenceAndOperationBudgetsHold(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final List<ImpEntity> imps = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                final ImpEntity imp = spawnImp(fixture,
                    new BlockPos(index % 4, 1, index / 4));
                imps.add(imp);
            }
            final long distinctStagger = imps.stream()
                .map(imp -> imp.lifeState().cadence().nextDecisionAt())
                .distinct()
                .count();
            helper.assertTrue(distinctStagger >= 2L,
                "UUID staggering spreads the initial decision ticks instead of synchronizing them");

            for (final ImpEntity imp : imps) {
                makeDue(imp);
                ImpLifeRuntime.tick(imp, helper.getLevel());
            }
            for (final ImpEntity imp : imps) {
                helper.assertTrue(imp.lifeCounters().curiosityScans() <= 1L,
                    "each imp runs at most one curiosity discovery per due cadence");
                helper.assertTrue(imp.lifeCounters().blockReads()
                        <= ImpLifeRules.CURIOSITY_READ_BUDGET + ImpLifeRules.WAYPOINT_READ_BUDGET,
                    "each imp charges every actual block read against its own budget");
                helper.assertTrue(imp.lifeCounters().observationScans() <= 1L,
                    "each imp runs at most one bounded entity query per due cadence");
                helper.assertTrue(imp.lifeCounters().navigationRequests() <= 1L,
                    "each imp issues at most one navigation request per twenty ticks");
                helper.assertTrue(imp.lifeState().cadence().nextDecisionAt()
                        > helper.getLevel().getGameTime(),
                    "every decision reschedules itself instead of running each tick");
            }

            for (final ImpEntity imp : imps) {
                ImpLifeRuntime.tick(imp, helper.getLevel());
            }
            for (final ImpEntity imp : imps) {
                helper.assertTrue(imp.lifeCounters().curiosityScans() <= 1L,
                    "an undue tick performs no additional discovery work");
            }

            final ImpEntity sample = imps.get(0);
            final CompoundTag saved = sample.lifeState().write();
            helper.assertTrue(saved.getIntOr("ObservationCount", 0) <= ImpLifeRules.MAX_OBSERVATIONS,
                "saved state retains at most four observations");
            helper.assertFalse(saved.contains("Path"),
                "no raw path or navigation node is ever serialized");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    private static boolean anyFireNear(final GameTestHelper helper, final BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (helper.getLevel().getBlockState(center.offset(dx, dy, dz)).is(Blocks.FIRE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void erectIsolationShell(final FixtureScope fixture, final BlockPos centerRelative) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(centerRelative);
        final int radius = 5;
        final int height = 5;
        final List<BlockPos> placed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue;
                }
                for (int dy = 0; dy <= height; dy++) {
                    final BlockPos pos = new BlockPos(
                        center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (helper.getLevel().getBlockState(pos).isAir()) {
                        helper.getLevel().setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                        placed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.AIR.defaultBlockState(), 3)));
    }

    /** Recreates the sealed three-block Forge staging cell inside the force-ticked 15-cube fixture. */
    private static void erectFrameworkCell(final FixtureScope fixture, final BlockPos centerRelative) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(centerRelative);
        final List<BlockPos> placed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    final boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 2;
                    if (!shell) {
                        continue;
                    }
                    final BlockPos pos = center.offset(dx, dy, dz);
                    if (helper.getLevel().getBlockState(pos).isAir()) {
                        helper.getLevel().setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                        placed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.AIR.defaultBlockState(), 3)));
    }

    private static void makeDue(final ImpEntity imp) {
        imp.setLifeState(imp.lifeState().withCadence(new ImpLifeState.Cadence(
            0L, 0L, 0L, 0L, 0L, 0L)));
    }

    @SuppressWarnings("unchecked")
    private static EntityType<ImpEntity> impType() {
        return (EntityType<ImpEntity>) ModEntities.ALL.get("imp").get();
    }

    private static ImpEntity spawnImp(final FixtureScope fixture, final BlockPos position) {
        final ImpEntity imp = fixture.spawn(impType(), position, EntitySpawnReason.EVENT);
        imp.setDeltaMovement(Vec3.ZERO);
        return imp;
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanupActions = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private <T extends Entity> T spawn(
            final EntityType<T> type,
            final BlockPos position,
            final EntitySpawnReason reason
        ) {
            return track(helper.spawn(type, position, reason));
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        private void onClose(final Runnable action) {
            cleanupActions.add(action);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            cleanupActions.forEach(Runnable::run);
            cleanupActions.clear();
        }
    }
}
