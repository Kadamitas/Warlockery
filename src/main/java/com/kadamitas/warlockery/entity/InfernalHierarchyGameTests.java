package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.OrderKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class InfernalHierarchyGameTests {
    private static final long SPAWN_INDEX_SETTLE_TICKS = 10L;

    private InfernalHierarchyGameTests() {
    }

    public static void infernalRanksNormalizeWithoutIdentityDrift(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            record RankContract(String id, CreatureKind kind, Rank rank, double health, double attack, double armor) {
            }
            final List<RankContract> contracts = List.of(
                new RankContract("demon", CreatureKind.DEMON, Rank.DEMON, 60.0D, 9.0D, 6.0D),
                new RankContract("emberhorn_archfiend", CreatureKind.EMBERHORN_ARCHFIEND,
                    Rank.EMBERHORN_ARCHFIEND, 100.0D, 11.0D, 8.0D),
                new RankContract("abyssal_regent", CreatureKind.ABYSSAL_REGENT, Rank.ABYSSAL_REGENT,
                    AbyssalRegentRules.MAX_HEALTH, AbyssalRegentRules.ATTACK_DAMAGE, AbyssalRegentRules.ARMOR)
            );
            int column = 0;
            for (final RankContract contract : contracts) {
                final EntityType<?> type = ModEntities.ALL.get(contract.id()).get();
                helper.assertTrue(type.fireImmune(),
                    "the special factory must keep fire immunity: " + contract.id());
                helper.assertFalse(type.isAllowedInPeaceful(),
                    "the special factory must keep the peaceful exclusion: " + contract.id());
                final InfernalHierarchyEntity entity = spawnRank(fixture, contract.id(),
                    new BlockPos(column++, 1, 1));
                helper.assertValueEqual(entity.getClass().getName(), InfernalHierarchyEntity.class.getName(),
                    "the exact registered id must construct the dedicated hierarchy adapter: " + contract.id());
                helper.assertValueEqual(entity.creatureKind(), contract.kind(), "public kind stays exact");
                helper.assertValueEqual(entity.hierarchyRank(), contract.rank(), "hierarchy rank stays exact");
                helper.assertValueEqual(entity.getAttributeValue(Attributes.MAX_HEALTH), contract.health(),
                    "health stays exact: " + contract.id());
                helper.assertValueEqual(entity.getAttributeValue(Attributes.ATTACK_DAMAGE), contract.attack(),
                    "attack stays exact: " + contract.id());
                helper.assertValueEqual(entity.getAttributeValue(Attributes.ARMOR), contract.armor(),
                    "armor stays exact: " + contract.id());
                helper.assertValueEqual(entity.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
                    "reinforcement chance is forced to zero: " + contract.id());
                // Zero target goals is the design: targeting is owned by the hierarchy runtime's
                // acquisition contract on the live decision path, never by a generic goal selector.
                // The live acquisition itself is proven in the acquisition and leader-loss descriptors.
                helper.assertValueEqual(entity.operationalTargetGoalCount(), 0,
                    "no inherited Zombie target goal survives; targeting is runtime-owned: " + contract.id());
                final List<String> goals = entity.operationalGoalNames();
                for (final String forbidden : List.of("ZombieAttackGoal", "MoveThroughVillageGoal",
                    "RemoveBlockGoal", "BreakDoorGoal", "NearestAttackableTargetGoal",
                    "ZombieVillagerConversion", "WaterAvoidingRandomStrollGoal")) {
                    helper.assertFalse(goals.stream().anyMatch(name -> name.contains(forbidden)),
                        "inherited goal must be removed: " + forbidden);
                }
                helper.assertFalse(entity.isBaby(), "every route is adult");
                helper.assertFalse(entity.canPickUpLoot(), "no equipment pickup drift");
                helper.assertFalse(entity.isUnderWaterConverting(), "no Drowned conversion");
                for (final EquipmentSlot slot : EquipmentSlot.values()) {
                    helper.assertTrue(entity.getItemBySlot(slot).isEmpty(),
                        "every route is empty-handed: " + slot);
                }
            }

            final InfernalHierarchyEntity demon = spawnRank(fixture, "demon", new BlockPos(1, 1, 0));
            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            demon.saveWithoutId(output);
            final CompoundTag saved = output.buildResult().copy();
            saved.putBoolean("IsBaby", true);
            saved.putBoolean("CanBreakDoors", true);
            saved.putInt("DrownedConversionTime", 100);
            saved.putInt("InWaterTime", 500);
            final InfernalHierarchyEntity loaded = createRank(helper, "demon", EntitySpawnReason.LOAD);
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            helper.assertFalse(loaded.isBaby(), "legacy baby state normalizes to adult on load");
            helper.assertFalse(loaded.isUnderWaterConverting(),
                "legacy Drowned conversion timers are cleared on load");
            final TagValueOutput resaved = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            loaded.saveWithoutId(resaved);
            final CompoundTag round = resaved.buildResult();
            helper.assertFalse(round.getBooleanOr("IsBaby", false), "no baby state persists");
            helper.assertFalse(round.getBooleanOr("CanBreakDoors", false), "no door breaking persists");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void demonConflictingOwnersPreserveDirectPact(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final InfernalHierarchyEntity demon = spawnRank(fixture, "demon", new BlockPos(1, 1, 1));
            final UUID direct = UUID.randomUUID();
            final UUID animus = UUID.randomUUID();
            helper.assertTrue(CreatureBehaviorState.bind(demon, direct),
                "the direct-bargain fixture binds the legacy owner key");
            demon.getPersistentData().putString(InfernalPactEffects.OWNER_KEY, animus.toString());
            helper.assertValueEqual(InfernalHierarchyRuntime.directPactOwner(demon).orElseThrow(), direct,
                "the direct legacy key stays readable");
            helper.assertValueEqual(InfernalHierarchyRuntime.animusOwner(demon).orElseThrow(), animus,
                "the conflicting Animus key stays readable rather than deleted");
            makeDue(demon);
            InfernalHierarchyRuntime.tick(demon, helper.getLevel());
            helper.assertValueEqual(demon.hierarchyState().authorityClass(),
                InfernalHierarchyRules.AuthorityClass.DIRECT_PACT,
                "conflicting owners resolve to the direct pact");
            helper.assertValueEqual(demon.hierarchyState().authorityId().orElseThrow(), direct,
                "the effective authority is the direct-bargain player");
            helper.assertFalse(InfernalHierarchyRules.commandAccepted(
                animus,
                InfernalHierarchyRuntime.directPactOwner(demon),
                InfernalHierarchyRuntime.animusOwner(demon)
            ), "commands from the conflicting Animus key are rejected");

            final ServerPlayer thief = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            final ItemStack animusStack = new ItemStack(ModItems.ALL.get("ingredient_infernal_animus").get(), 2);
            thief.setItemInHand(InteractionHand.MAIN_HAND, animusStack);
            final InteractionResult theft = animusStack.getItem().interactLivingEntity(
                animusStack, thief, demon, InteractionHand.MAIN_HAND
            );
            helper.assertValueEqual(theft, InteractionResult.FAIL,
                "Animus cannot steal a Demon with a different direct owner");
            helper.assertValueEqual(animusStack.getCount(), 2, "a refused Animus is not consumed");
            helper.assertValueEqual(CreatureBehaviorState.owner(demon).orElseThrow(), direct,
                "the direct pact survives the attempted theft");
            helper.assertValueEqual(InfernalHierarchyRuntime.animusOwner(demon).orElseThrow(), animus,
                "the stored legacy Animus value survives the attempted theft");

            for (final String immune : List.of("emberhorn_archfiend", "abyssal_regent")) {
                final InfernalHierarchyEntity boss = spawnRank(fixture, immune, new BlockPos(0, 1, 2));
                final InteractionResult refused = animusStack.getItem().interactLivingEntity(
                    animusStack, thief, boss, InteractionHand.MAIN_HAND
                );
                helper.assertValueEqual(refused, InteractionResult.PASS,
                    "enthrallment-immune ranks refuse the Animus: " + immune);
                helper.assertValueEqual(animusStack.getCount(), 2,
                    "a refused boss Animus is not consumed: " + immune);
                helper.assertTrue(InfernalHierarchyRuntime.animusOwner(boss).isEmpty(),
                    "no ownership is written to an immune rank: " + immune);
            }

            final InfernalHierarchyEntity unbound = spawnRank(fixture, "demon", new BlockPos(2, 1, 2));
            final InteractionResult bound = animusStack.getItem().interactLivingEntity(
                animusStack, thief, unbound, InteractionHand.MAIN_HAND
            );
            helper.assertValueEqual(bound, InteractionResult.SUCCESS,
                "a valid unbound Demon still accepts the Animus binding");
            helper.assertValueEqual(animusStack.getCount(), 1, "a successful binding consumes exactly one item");
            helper.assertValueEqual(InfernalHierarchyRuntime.animusOwner(unbound).orElseThrow(),
                thief.getUUID(), "the Animus key records the binding player");
            helper.assertTrue(unbound.isPersistenceRequired(), "a bound Demon persists");
            makeDue(unbound);
            InfernalHierarchyRuntime.tick(unbound, helper.getLevel());
            helper.assertValueEqual(unbound.hierarchyState().authorityClass(),
                InfernalHierarchyRules.AuthorityClass.ANIMUS,
                "an Animus-only Demon resolves to Animus authority");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void demonTruceMoraleRetreatAndReturnAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            final InfernalHierarchyEntity demon = spawnRank(fixture, "demon", new BlockPos(1, 1, 1));
            CreatureBehaviorState.bind(demon, UUID.randomUUID());
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            player.getInventory().add(new ItemStack(ModItems.ALL.get("silver_tongue_charm").get()));
            // The legacy pacification predicate returned false on one of every four ticks. The stable
            // carriage predicate must form the truce on exactly such a tick, so the flicker tick is
            // forced rather than dodged.
            while (Math.floorMod(demon.tickCount + demon.getId(), 4) != 0) {
                demon.tickCount++;
            }
            final long now = helper.getLevel().getGameTime();

            demon.setHierarchyState(InfernalHierarchyRuntime.maybeRefreshTruce(
                demon, demon.hierarchyState(), player, now
            ));
            helper.assertValueEqual(demon.hierarchyState().trucePlayerId().orElseThrow(), player.getUUID(),
                "a valid charm carrier forms the single stable truce");
            helper.assertTrue(demon.hierarchyState().truceExpiresAt()
                    <= now + InfernalHierarchyRules.TRUCE_TICKS,
                "the truce lives at most two hundred ticks");
            helper.assertValueEqual(demon.hierarchyCounters().truceRefreshes(), 1L, "the refresh is counted");
            demon.setHierarchyState(InfernalHierarchyRuntime.maybeRefreshTruce(
                demon, demon.hierarchyState(), player, now
            ));
            helper.assertValueEqual(demon.hierarchyCounters().truceRefreshes(), 1L,
                "the truce refreshes no faster than every twenty ticks, replacing the old flicker");
            helper.assertFalse(InfernalHierarchyRuntime.eligibleTarget(demon, player),
                "an active truce suppresses targeting of that exact player");
            helper.assertFalse(demon.canAttack(player), "the live predicate honors the truce");

            demon.invulnerableTime = 0;
            helper.assertTrue(demon.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 6.0F
            ), "the breach fixture needs one real accepted hit");
            helper.assertTrue(demon.hierarchyState().trucePlayerId().isEmpty(),
                "a direct attributed attack breaches the truce immediately");
            helper.assertTrue(demon.hierarchyState().truceBreachUntil()
                    >= helper.getLevel().getGameTime() + InfernalHierarchyRules.TRUCE_BREACH_TICKS - 1L,
                "the breach blocks a new truce for six hundred ticks");
            helper.assertValueEqual(demon.hierarchyState().aggressorId().orElseThrow(), player.getUUID(),
                "the breaching player becomes the bounded direct aggressor");
            helper.assertValueEqual(demon.hierarchyState().morale(),
                InfernalHierarchyRules.MORALE_BASELINE
                    - InfernalHierarchyRules.damageMoralePenalty(6.0F, demon.getMaxHealth()),
                "direct damage applies the exact closed-form morale penalty");
            demon.setHierarchyState(InfernalHierarchyRuntime.maybeRefreshTruce(
                demon, demon.hierarchyState(), player, helper.getLevel().getGameTime()
            ));
            helper.assertTrue(demon.hierarchyState().trucePlayerId().isEmpty(),
                "no new truce forms during the breach window");

            demon.setHierarchyState(InfernalHierarchyRuntime.applyMoraleEvent(
                demon, demon.hierarchyState(), -400, helper.getLevel().getGameTime()
            ));
            helper.assertTrue(InfernalHierarchyRules.moraleRetreatRequired(
                demon.hierarchyState().morale(), 1.0F
            ), "broken morale forces a retreat");
            helper.assertFalse(InfernalHierarchyRules.mayReenterPressure(
                demon.hierarchyState().morale(), false, false
            ), "hysteresis blocks re-entry until morale recovers to five hundred");
            helper.assertValueEqual(InfernalHierarchyRules.recoveredMorale(
                demon.hierarchyState().morale(), helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime() + 40_000L
            ), InfernalHierarchyRules.MORALE_BASELINE,
                "closed-form recovery advances only toward the six hundred fifty baseline");

            final Zombie obstacle = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            obstacle.setNoAi(true);
            demon.setTarget(obstacle);
            InfernalHierarchyState state = demon.hierarchyState();
            final long failuresNow = helper.getLevel().getGameTime();
            state = InfernalHierarchyRuntime.recordRouteFailure(demon, state, failuresNow);
            state = InfernalHierarchyRuntime.recordRouteFailure(demon, state, failuresNow);
            state = InfernalHierarchyRuntime.recordRouteFailure(demon, state, failuresNow);
            demon.setHierarchyState(state);
            helper.assertTrue(demon.getTarget() == null, "three route failures release the target claim");
            helper.assertValueEqual(state.intent(), Intent.RETURN,
                "route exhaustion turns into a bounded return");
            helper.assertTrue(state.actionBackoffUntil()
                    >= failuresNow + InfernalHierarchyRules.ROUTE_BACKOFF_TICKS,
                "route requests sleep for at least one hundred ticks");
            helper.assertValueEqual(state.routeFailures(), 0, "the failure counter resets with the backoff");

            demon.setHierarchyState(demon.hierarchyState()
                .withAggressor(Optional.empty(), 0L)
                .withTruce(
                    Optional.of(player.getUUID()),
                    helper.getLevel().getGameTime() + InfernalHierarchyRules.TRUCE_TICKS,
                    0L,
                    0L
                ));
            for (int index = 0; index < 20; index++) {
                final Zombie crowd = fixture.spawn(
                    EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.EVENT
                );
                crowd.setNoAi(true);
                crowd.setDeltaMovement(Vec3.ZERO);
            }
            final long refreshesBefore = demon.hierarchyCounters().truceRefreshes();
            makeDue(demon);
            InfernalHierarchyRuntime.tick(demon, helper.getLevel());
            helper.assertTrue(demon.hierarchyCounters().truceRefreshes() > refreshesBefore,
                "the current truce player is preseeded ahead of far more generic candidates than the cap");
            helper.assertTrue(demon.hierarchyCounters().candidateVisits()
                    <= InfernalHierarchyRuntime.retainedCandidateCap(Rank.DEMON)
                        * Math.max(1L, demon.hierarchyCounters().observationScans()),
                "preseeding never widens the retained candidate budget");
            helper.assertTrue(demon.hierarchyCounters().observationScans() >= 1L,
                "the live observation channel runs on its bounded cadence");
            helper.assertTrue(demon.hierarchyCounters().candidateVisits()
                    <= InfernalHierarchyRuntime.retainedCandidateCap(Rank.DEMON)
                        * Math.max(1L, demon.hierarchyCounters().observationScans()),
                "observation work stays within the twelve-candidate budget");

            // Route failure and backoff must engage on the live navigation path, never only under a
            // direct helper call. A pact-bound Demon whose owner stands sixteen blocks straight up on
            // a barrier perch enters PACT_FOLLOW, and every navigation episode ends without closing
            // the distance, so the live ladder must reach the bounded backoff and return by itself.
            player.getInventory().clearContent();
            final BlockPos perch = helper.absolutePos(new BlockPos(1, 17, 1));
            fixture.placeBlock(perch.below(), Blocks.BARRIER.defaultBlockState());
            player.teleportTo(perch.getX() + 0.5D, perch.getY(), perch.getZ() + 0.5D);
            final InfernalHierarchyEntity walled = spawnRank(fixture, "demon", new BlockPos(0, 1, 0));
            CreatureBehaviorState.bind(walled, player.getUUID());
            walled.setPersistenceRequired();
            walled.setNoAi(false);
            final java.util.concurrent.atomic.AtomicBoolean routeDone =
                new java.util.concurrent.atomic.AtomicBoolean(false);
            helper.onEachTick(() -> {
                if (routeDone.get()) {
                    return;
                }
                player.teleportTo(perch.getX() + 0.5D, perch.getY(), perch.getZ() + 0.5D);
                final InfernalHierarchyState live = walled.hierarchyState();
                if (InfernalHierarchyRules.due(live.actionBackoffUntil(), helper.getLevel().getGameTime())) {
                    return;
                }
                routeDone.set(true);
                try {
                    helper.assertTrue(walled.hierarchyCounters().navigationRequests() >= 2L,
                        "the live navigation channel issued real bounded path requests");
                    helper.assertValueEqual(live.intent(), Intent.RETURN,
                        "three live rejected navigation episodes turn into a bounded return");
                    helper.assertValueEqual(live.routeFailures(), 0,
                        "the live failure counter resets with the backoff");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
            helper.runAfterDelay(250L, () -> {
                try {
                    helper.assertTrue(routeDone.get(),
                        "the live tick path must reach route backoff without a direct helper call"
                            + " [intent=" + walled.hierarchyState().intent()
                            + " authority=" + walled.hierarchyState().authorityClass()
                            + " requests=" + walled.hierarchyCounters().navigationRequests()
                            + " failures=" + walled.hierarchyState().routeFailures()
                            + " backoff=" + walled.hierarchyState().actionBackoffUntil()
                            + " dist=" + walled.distanceTo(player)
                            + " navDone=" + walled.getNavigation().isDone() + "]");
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

    public static void archfiendAnchorSquadAndEmberFrontAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(2, 1, 2));
            final InfernalHierarchyEntity archfiend = spawnRank(fixture, "emberhorn_archfiend", new BlockPos(2, 1, 2));
            final BlockPos office = archfiend.blockPosition().offset(2, 0, 0);
            fixture.placeBlock(office, Blocks.CAULDRON.defaultBlockState());
            makeDue(archfiend);
            InfernalHierarchyRuntime.tick(archfiend, helper.getLevel());
            final long now = helper.getLevel().getGameTime();
            helper.assertValueEqual(archfiend.hierarchyState().anchorPos().orElseThrow(), office.asLong(),
                "the archfiend claims the one loaded magical cauldron as its office");
            helper.assertTrue(archfiend.hierarchyState().anchorExpiresAt()
                    <= now + InfernalHierarchyRules.ANCHOR_CLAIM_TICKS,
                "the office claim expires rather than persisting forever");
            helper.assertTrue(archfiend.hierarchyCounters().blockReads()
                    <= InfernalHierarchyRules.ARCHFIEND_ANCHOR_BLOCK_READS + 2L,
                "anchor search stays inside the charged one hundred twenty-eight read budget");
            helper.getLevel().setBlock(office, Blocks.AIR.defaultBlockState(), 3);
            makeDue(archfiend);
            InfernalHierarchyRuntime.tick(archfiend, helper.getLevel());
            helper.assertTrue(archfiend.hierarchyState().anchorPos().isEmpty(),
                "a destroyed office releases the claim instead of anchoring to air");

            // The truce is reachable for every infernal rank, not only the Demon. The Archfiend aura
            // branches on truce state, so the archfiend forms the truce through the same live
            // observation path, and it forms on exactly a tick the legacy flicker predicate refused.
            final ServerPlayer envoy = fixture.connectedPlayer(new BlockPos(2, 1, 1));
            envoy.getInventory().add(new ItemStack(ModItems.ALL.get("silver_tongue_charm").get()));
            while (Math.floorMod(archfiend.tickCount + archfiend.getId(), 4) != 0) {
                archfiend.tickCount++;
            }
            makeDue(archfiend);
            InfernalHierarchyRuntime.tick(archfiend, helper.getLevel());
            helper.assertValueEqual(archfiend.hierarchyState().trucePlayerId().orElseThrow(),
                envoy.getUUID(),
                "the single truce is reachable for the archfiend rank on a stable carriage predicate");
            helper.assertFalse(archfiend.canAttack(envoy), "the archfiend honors its own truce");
            archfiend.setHierarchyState(archfiend.hierarchyState()
                .withTruce(Optional.empty(), 0L, 0L, 0L));
            envoy.getInventory().clearContent();

            final List<InfernalHierarchyEntity> squad = new ArrayList<>();
            final List<InfernalHierarchyState.Member> preseed = new ArrayList<>();
            for (final BlockPos position : List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
                new BlockPos(1, 1, 3), new BlockPos(3, 1, 3))) {
                final InfernalHierarchyEntity member = spawnRank(fixture, "demon", position);
                squad.add(member);
                preseed.add(new InfernalHierarchyState.Member(
                    member.getUUID(), Rank.DEMON, helper.getLevel().getGameTime() + 100L
                ));
                // The squad scan radius is twenty four blocks, well past the eight to ten block batch
                // grid, so the reciprocal lease is written here to keep this pass local. Without it a
                // neighbouring instance's leader can claim these members first.
                member.setHierarchyState(member.hierarchyState().withLeader(
                    Optional.of(archfiend.getUUID()),
                    Optional.of(Rank.EMBERHORN_ARCHFIEND),
                    helper.getLevel().getGameTime() + 100L
                ));
            }
            final InfernalHierarchyEntity extra = spawnRank(fixture, "demon", new BlockPos(2, 1, 0));
            final InfernalHierarchyEntity bound = spawnRank(fixture, "demon", new BlockPos(0, 1, 2));
            CreatureBehaviorState.bind(bound, UUID.randomUUID());
            final Zombie challengerZombie = fixture.spawn(
                EntityTypes.ZOMBIE, new BlockPos(2, 1, 3), EntitySpawnReason.EVENT
            );
            challengerZombie.setNoAi(true);
            challengerZombie.setDeltaMovement(Vec3.ZERO);
            final Zombie rival = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 2), EntitySpawnReason.EVENT);
            rival.setNoAi(true);
            final Villager bystander = fixture.spawn(
                EntityTypes.VILLAGER, new BlockPos(1, 1, 2), EntitySpawnReason.EVENT
            );
            bystander.setNoAi(true);
            afterSpawnsAreIndexed(helper, fixture, () ->
                archfiendSquadAndFrontStage(helper, fixture, archfiend, squad, preseed, extra, bound,
                    challengerZombie, rival, bystander));
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void archfiendSquadAndFrontStage(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final InfernalHierarchyEntity archfiend,
        final List<InfernalHierarchyEntity> squad,
        final List<InfernalHierarchyState.Member> preseed,
        final InfernalHierarchyEntity extra,
        final InfernalHierarchyEntity bound,
        final Zombie challengerZombie,
        final Zombie rival,
        final Villager bystander
    ) {
            archfiend.setHierarchyState(archfiend.hierarchyState().withRoster(preseed, 1L));
            makeDue(archfiend);
            InfernalHierarchyRuntime.tick(archfiend, helper.getLevel());
            final long refreshNow = helper.getLevel().getGameTime();
            final InfernalHierarchyState squadState = archfiend.hierarchyState();
            helper.assertValueEqual(squadState.roster().size(), InfernalHierarchyRules.SQUAD_MEMBER_CAP,
                "the squad retains at most four members");
            for (final InfernalHierarchyEntity member : squad) {
                helper.assertTrue(squadState.roster().stream()
                        .anyMatch(row -> row.id().equals(member.getUUID())),
                    "preseeded current members are retained before generic candidates");
                helper.assertValueEqual(member.hierarchyState().leaderId().orElseThrow(), archfiend.getUUID(),
                    "each squad member records its one leader");
                final InfernalHierarchyState.Order order = member.hierarchyState().order().orElseThrow();
                helper.assertValueEqual(order.kind(), OrderKind.HOLD_POST,
                    "the squad order is a squad order [leaderIntent=" + archfiend.hierarchyState().intent()
                        + ", health=" + archfiend.getHealth() + "/" + archfiend.getMaxHealth()
                        + ", underwater=" + archfiend.isUnderWater()
                        + ", air=" + archfiend.getAirSupply() + "/" + archfiend.getMaxAirSupply()
                        + ", leaderOrder=" + archfiend.hierarchyState().order() + "]");
                helper.assertValueEqual(order.issuerRank(), Rank.EMBERHORN_ARCHFIEND, "the issuer is exact");
                helper.assertTrue(order.expiresAt() <= refreshNow + InfernalHierarchyRules.ARCHFIEND_ORDER_TICKS,
                    "archfiend orders live at most two hundred ticks");
                helper.assertTrue(member.hierarchyState().membershipLeaseUntil()
                        <= refreshNow + InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS,
                    "membership leases live at most four hundred ticks");
            }
            helper.assertFalse(squadState.roster().stream()
                    .anyMatch(row -> row.id().equals(bound.getUUID())),
                "a player-bound Demon is never squad-eligible");
            helper.assertTrue(bound.hierarchyState().leaderId().isEmpty(),
                "the bound Demon receives no lease");
            helper.assertFalse(squadState.roster().stream()
                    .anyMatch(row -> row.id().equals(extra.getUUID())),
                "a full squad rejects the extra candidate at the cap");

            archfiend.invulnerableTime = 0;
            helper.assertTrue(archfiend.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(challengerZombie), 1.0F
            ), "provocation requires one real accepted hit");
            helper.assertValueEqual(archfiend.hierarchyState().challengerId().orElseThrow(),
                challengerZombie.getUUID(), "a direct attributed attack creates the challenger");
            archfiend.invulnerableTime = 0;
            helper.assertTrue(archfiend.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(rival), 1.0F
            ), "the stability fixture needs a second real hit");
            helper.assertValueEqual(archfiend.hierarchyState().challengerId().orElseThrow(),
                challengerZombie.getUUID(), "the stable current challenger wins over new candidates");
            helper.assertTrue(archfiend.getLastHurtByMob() == rival,
                "the engine attributes the most recent hit to the rival");
            helper.assertTrue(archfiend.canAttack(challengerZombie),
                "restraint releases against the stable challenger, not merely the latest attacker");
            helper.assertFalse(archfiend.canAttack(squad.get(0)),
                "a provoked Archfiend stays restrained against its own squad");

            final float bystanderHealth = bystander.getHealth();
            final float allyHealth = squad.get(0).getHealth();
            final float challengerHealth = challengerZombie.getHealth();
            // This stage characterizes the helper directly, so the same Archfiend's own live decision
            // cadence is parked to keep the two drivers from racing across the telegraph window.
            parkDecisions(archfiend, helper);
            archfiend.setHierarchyState(archfiend.hierarchyState().withRouteFailures(0, 0L));
            helper.assertFalse(InfernalHierarchyRuntime.attemptEmberFront(archfiend, helper.getLevel()),
                "the first eligible call arms the telegraph rather than committing");
            helper.assertValueEqual(archfiend.hierarchyState().intent(), Intent.EMBER_FRONT,
                "the telegraph declares the action intent");
            helper.assertTrue(archfiend.hierarchyState().actionBackoffUntil()
                    <= helper.getLevel().getGameTime() + InfernalHierarchyRules.EMBER_FRONT_TELEGRAPH_TICKS,
                "the telegraph lasts twenty ticks before any damage");
            helper.assertValueEqual(challengerZombie.getHealth(), challengerHealth,
                "no damage lands during the telegraph");
            final java.util.concurrent.atomic.AtomicBoolean commitStageRan =
                new java.util.concurrent.atomic.AtomicBoolean(false);
            helper.runAfterDelay(InfernalHierarchyRules.EMBER_FRONT_TELEGRAPH_TICKS + 5L, () -> {
                if (!commitStageRan.compareAndSet(false, true)) {
                    return;
                }
                try {
                    helper.assertTrue(InfernalHierarchyRuntime.attemptEmberFront(archfiend, helper.getLevel()),
                        "the committed front executes after the telegraph");
                    helper.assertTrue(challengerZombie.getHealth() < challengerHealth,
                        "the challenger takes the attributed indirect-magic damage");
                    helper.assertTrue(challengerZombie.getRemainingFireTicks() > 0,
                        "fire applies only after the damage succeeded");
                    helper.assertValueEqual(squad.get(0).getHealth(), allyHealth,
                        "same-squad allies are never ember front recipients");
                    helper.assertValueEqual(bystander.getHealth(), bystanderHealth,
                        "neutral villagers are never ember front recipients");
                    final long committedAt = helper.getLevel().getGameTime();
                    helper.assertTrue(archfiend.hierarchyState().actionBackoffUntil()
                            >= committedAt + InfernalHierarchyRules.EMBER_FRONT_SPACING_TICKS - 1L,
                        "the next front waits at least one hundred twenty ticks");
                    final float afterCommit = challengerZombie.getHealth();
                    helper.assertFalse(InfernalHierarchyRuntime.attemptEmberFront(archfiend, helper.getLevel()),
                        "an immediate re-attempt is refused during recovery and spacing");
                    helper.assertValueEqual(challengerZombie.getHealth(), afterCommit,
                        "the refused re-attempt deals no damage");
                    challengerZombie.clearFire();

                    assertCauldronAuraIsBoundedAndFiltered(helper, fixture, archfiend);

                    // The Ember Front must occur in real play, not only under a direct helper call.
                    // This Archfiend has its AI enabled and is never touched by a runtime helper here,
                    // so the telegraph, the damage, and the fire can only come from the live tick path.
                    final BlockPos marshalSite = helper.absolutePos(new BlockPos(0, 1, 0));
                    final BlockPos quarrySite = helper.absolutePos(new BlockPos(0, 1, 3));
                    openStandingSite(fixture, marshalSite);
                    openStandingSite(fixture, quarrySite);
                    final InfernalHierarchyEntity marshal = spawnRank(
                        fixture, "emberhorn_archfiend", new BlockPos(0, 1, 0)
                    );
                    // A creeper, not a zombie: sunlight ignition would otherwise make the fire signal
                    // ambiguous and turn the live assertion into noise.
                    final net.minecraft.world.entity.Mob quarry = fixture.spawn(
                        EntityTypes.CREEPER, new BlockPos(0, 1, 3), EntitySpawnReason.EVENT
                    );
                    quarry.setNoAi(true);
                    quarry.setNoGravity(true);
                    quarry.setDeltaMovement(Vec3.ZERO);
                    quarry.snapTo(quarrySite.getX() + 0.5D, quarrySite.getY(),
                        quarrySite.getZ() + 0.5D, 0.0F, 0.0F);
                    marshal.invulnerableTime = 0;
                    helper.assertTrue(marshal.hurtServer(
                        helper.getLevel(), helper.getLevel().damageSources().mobAttack(quarry), 1.0F
                    ), "the live fixture needs one real accepted provocation");
                    helper.assertValueEqual(marshal.hierarchyState().challengerId().orElseThrow(),
                        quarry.getUUID(), "the live marshal records its stable challenger");
                    marshal.setNoAi(false);
                    marshal.setNoGravity(true);
                    marshal.setPersistenceRequired();
                    // Only the decision channel is live. The squad channel is parked behind a bounded
                    // future sentinel so this live Archfiend cannot lease members across the batch grid.
                    final long parkedGroup = helper.getLevel().getGameTime() + 10_000L;
                    marshal.setHierarchyState(marshal.hierarchyState().withCadence(
                        new InfernalHierarchyState.Cadence(
                            0L, 0L, parkedGroup, parkedGroup, 0L, 0L
                        )
                    ));
                    final java.util.concurrent.atomic.AtomicBoolean telegraphed =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                    final java.util.concurrent.atomic.AtomicBoolean committed =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                    final java.util.concurrent.atomic.AtomicLong beforeCommit =
                        new java.util.concurrent.atomic.AtomicLong(
                            Float.floatToIntBits(quarry.getHealth())
                        );
                    helper.onEachTick(() -> {
                        if (committed.get()) {
                            return;
                        }
                        quarry.snapTo(quarrySite.getX() + 0.5D, quarrySite.getY(),
                            quarrySite.getZ() + 0.5D, 0.0F, 0.0F);
                        final InfernalHierarchyState live = marshal.hierarchyState();
                        final boolean spaced = live.actionBackoffUntil()
                            >= helper.getLevel().getGameTime()
                                + InfernalHierarchyRules.EMBER_FRONT_SPACING_TICKS - 1L;
                        if (!spaced) {
                            if (live.intent() == Intent.EMBER_FRONT) {
                                telegraphed.set(true);
                            }
                            beforeCommit.set(Float.floatToIntBits(quarry.getHealth()));
                            return;
                        }
                        committed.set(true);
                        try {
                            helper.assertTrue(telegraphed.get(),
                                "a live ticking Archfiend telegraphs before the front commits");
                            helper.assertTrue(
                                quarry.getHealth()
                                    < Float.intBitsToFloat((int) beforeCommit.get()),
                                "the live front deals its attributed damage in real play");
                            helper.assertTrue(quarry.getRemainingFireTicks() > 0,
                                "the live front applies fire only after the damage succeeded");
                            helper.assertValueEqual(live.intent(), Intent.FOCUS,
                                "the committed front leaves an ordinary melee window behind it");
                        } finally {
                            fixture.close();
                        }
                        helper.succeed();
                    });
                    // Bounded deadline. If the live tick path never reaches the front, this fails loudly
                    // and still releases every fixture rather than leaking entities into the next batch.
                    helper.runAfterDelay(200L, () -> {
                        try {
                            helper.assertTrue(committed.get(),
                                "the live tick path must reach the ember front without a direct helper call");
                        } finally {
                            fixture.close();
                        }
                        helper.succeed();
                    });
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
    }

    /**
     * Freshly spawned entities are not visible to a loaded AABB query until the level has indexed them,
     * which at world start can span a chunk boundary and a tick. Every scan-dependent stage therefore
     * waits about ten ticks, and runs exactly once even though a delayed stage can be re-entered.
     */
    private static void afterSpawnsAreIndexed(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final Runnable stage
    ) {
        final java.util.concurrent.atomic.AtomicBoolean ran =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        helper.runAfterDelay(SPAWN_INDEX_SETTLE_TICKS, () -> {
            if (!ran.compareAndSet(false, true)) {
                return;
            }
            try {
                stage.run();
            } catch (final RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    /**
     * The relocated legacy cauldron aura scanned a seventeen by seven by seventeen volume of two thousand
     * and twenty three unguarded positions and applied Luck to every nearby player. This pins the
     * approved read budget and the relationship filter instead.
     */
    private static void assertCauldronAuraIsBoundedAndFiltered(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final InfernalHierarchyEntity archfiend
    ) {
        final BlockPos office = archfiend.blockPosition().offset(1, 0, 0);
        fixture.placeBlock(office, Blocks.CAULDRON.defaultBlockState());
        final long now = helper.getLevel().getGameTime();
        final ServerPlayer truced = fixture.connectedPlayer(new BlockPos(1, 1, 2));
        final ServerPlayer provoker = fixture.connectedPlayer(new BlockPos(3, 1, 2));
        final ServerPlayer bystander = fixture.connectedPlayer(new BlockPos(2, 1, 1));
        for (final ServerPlayer player : List.of(truced, provoker, bystander)) {
            player.removeAllEffects();
        }
        final InfernalHierarchyState previous = archfiend.hierarchyState();
        archfiend.setHierarchyState(previous
            .withAnchor(Optional.of(office.asLong()),
                now + InfernalHierarchyRules.ANCHOR_CLAIM_TICKS)
            .withTruce(Optional.of(truced.getUUID()),
                now + InfernalHierarchyRules.TRUCE_TICKS, now, 0L)
            .withChallenger(Optional.of(provoker.getUUID()),
                now + InfernalHierarchyRules.PROVOCATION_TICKS));
        final long readsBefore = archfiend.hierarchyCounters().blockReads();
        InfernalHierarchyRuntime.tickCauldronAura(archfiend, helper.getLevel());
        final long charged = archfiend.hierarchyCounters().blockReads() - readsBefore;
        helper.assertTrue(charged > 0L,
            "the cauldron evaluation runs through the charged and chunk-guarded read path");
        helper.assertTrue(charged <= InfernalHierarchyRules.CAULDRON_SCAN_BLOCK_READS,
            "the cauldron evaluation charges at most one hundred twenty eight actual reads");
        helper.assertTrue(charged < 2_023L,
            "the relocated two thousand and twenty three position volume scan is gone");
        helper.assertTrue(truced.hasEffect(MobEffects.LUCK),
            "the one valid truce player receives the beneficial side only");
        helper.assertFalse(truced.hasEffect(MobEffects.WEAKNESS),
            "the truce player never receives the telegraphed hostile side");
        helper.assertValueEqual(truced.getEffect(MobEffects.LUCK).getDuration(),
            InfernalHierarchyRules.CAULDRON_LUCK_TICKS, "the exact luck duration is preserved");
        helper.assertTrue(provoker.hasEffect(MobEffects.WEAKNESS),
            "a valid challenger receives the telegraphed hostile side");
        helper.assertValueEqual(provoker.getEffect(MobEffects.WEAKNESS).getAmplifier(),
            InfernalHierarchyRules.CAULDRON_WEAKNESS_AMPLIFIER,
            "the exact weakness two envelope is preserved");
        helper.assertFalse(provoker.hasEffect(MobEffects.LUCK),
            "a challenger never receives the beneficial side");
        helper.assertFalse(bystander.hasEffect(MobEffects.LUCK),
            "a neutral bystander receives neither side of the duality");
        helper.assertFalse(bystander.hasEffect(MobEffects.WEAKNESS),
            "a neutral bystander receives neither side of the duality");
        archfiend.setHierarchyState(previous);
    }

    public static void regentCourtOrdersPhaseAndReinforcementsCleanup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(2, 1, 2));
            final InfernalHierarchyEntity regent = spawnRank(fixture, "abyssal_regent", new BlockPos(2, 1, 2));
            final InfernalHierarchyEntity courtArchfiend = spawnRank(
                fixture, "emberhorn_archfiend", new BlockPos(1, 1, 1)
            );
            final List<InfernalHierarchyEntity> courtDemons = new ArrayList<>();
            for (final BlockPos position : List.of(new BlockPos(3, 1, 1), new BlockPos(1, 1, 3),
                new BlockPos(3, 1, 3), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2), new BlockPos(3, 1, 2))) {
                courtDemons.add(spawnRank(fixture, "demon", position));
            }
            final List<InfernalHierarchyState.Member> preseed = new ArrayList<>();
            preseed.add(new InfernalHierarchyState.Member(
                courtArchfiend.getUUID(), Rank.EMBERHORN_ARCHFIEND, helper.getLevel().getGameTime() + 100L
            ));
            for (final InfernalHierarchyEntity member : courtDemons) {
                preseed.add(new InfernalHierarchyState.Member(
                    member.getUUID(), Rank.DEMON, helper.getLevel().getGameTime() + 100L
                ));
            }
            // The court scan radius is thirty two blocks, far past the eight to ten block batch grid, so
            // every preseeded member records its reciprocal lease before a neighbouring court can bid.
            final List<InfernalHierarchyEntity> courtBodies = new ArrayList<>(courtDemons);
            courtBodies.add(courtArchfiend);
            for (final InfernalHierarchyEntity member : courtBodies) {
                member.setHierarchyState(member.hierarchyState().withLeader(
                    Optional.of(regent.getUUID()), Optional.of(Rank.ABYSSAL_REGENT),
                    helper.getLevel().getGameTime() + 100L
                ));
            }
            afterSpawnsAreIndexed(helper, fixture, () -> {
                regentCourtStage(helper, fixture, regent, courtArchfiend, courtDemons, preseed);
                fixture.close();
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void regentCourtStage(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final InfernalHierarchyEntity regent,
        final InfernalHierarchyEntity courtArchfiend,
        final List<InfernalHierarchyEntity> courtDemons,
        final List<InfernalHierarchyState.Member> preseed
    ) {
            regent.setHierarchyState(regent.hierarchyState().withRoster(preseed, 1L));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            final long courtNow = helper.getLevel().getGameTime();
            final InfernalHierarchyState courtState = regent.hierarchyState();
            helper.assertValueEqual(courtState.roster().size(), InfernalHierarchyRules.COURT_MEMBER_CAP,
                "the court retains at most seven members");
            helper.assertValueEqual(courtState.roster().stream()
                    .filter(row -> row.rank() == Rank.EMBERHORN_ARCHFIEND).count(), 1L,
                "the court retains at most one archfiend");
            for (final InfernalHierarchyEntity member : courtDemons) {
                final InfernalHierarchyState.Order order = member.hierarchyState().order().orElseThrow();
                helper.assertValueEqual(order.kind(), OrderKind.HOLD_COURT, "court orders are court orders");
                helper.assertValueEqual(order.issuerRank(), Rank.ABYSSAL_REGENT,
                    "every direct order comes from the regent, never a copied chain");
                helper.assertValueEqual(order.epoch(), courtState.orderEpoch(),
                    "orders carry the exact court epoch");
                helper.assertTrue(order.expiresAt() <= courtNow + InfernalHierarchyRules.REGENT_ORDER_TICKS,
                    "regent orders live at most three hundred ticks");
            }
            makeDue(courtArchfiend);
            InfernalHierarchyRuntime.tick(courtArchfiend, helper.getLevel());
            for (final InfernalHierarchyEntity member : courtDemons) {
                helper.assertValueEqual(member.hierarchyState().leaderId().orElseThrow(), regent.getUUID(),
                    "the member archfiend cannot recursively adopt court demons into a second squad");
            }

            // The command doctrine executes on the live path. A real accepted hit gives the regent an
            // engaged challenger, and the next bounded court refresh issues the focus order carrying
            // its exact target to every member, which is what the ordered Demon then presses through.
            final Zombie challengerMob = fixture.spawn(
                EntityTypes.ZOMBIE, new BlockPos(2, 1, 0), EntitySpawnReason.EVENT
            );
            challengerMob.setNoAi(true);
            challengerMob.setDeltaMovement(Vec3.ZERO);
            regent.invulnerableTime = 0;
            helper.assertTrue(regent.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(challengerMob), 1.0F
            ), "the doctrine fixture needs one real accepted provocation");
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            final InfernalHierarchyEntity focused = courtDemons.get(0);
            final InfernalHierarchyState.Order focusOrder = focused.hierarchyState().order().orElseThrow();
            helper.assertValueEqual(focusOrder.kind(), OrderKind.FOCUS_CHALLENGER,
                "an engaged challenger draws a live focus order rather than a hard-coded hold");
            helper.assertValueEqual(focusOrder.targetId().orElseThrow(), challengerMob.getUUID(),
                "the live focus order carries its exact target");
            makeDue(focused);
            InfernalHierarchyRuntime.tick(focused, helper.getLevel());
            helper.assertValueEqual(focused.hierarchyState().intent(), Intent.PRESS,
                "the Demon press-through-focus arm executes in live play");
            helper.assertTrue(focused.getTargetUnchecked() == challengerMob,
                "the higher-rank focus slot acquires the ordered target on the live path");
            focused.setTarget(null);
            regent.setHierarchyState(regent.hierarchyState().withChallenger(Optional.empty(), 0L));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(focused.hierarchyState().order().orElseThrow().kind(),
                OrderKind.SCREEN_REGENT,
                "a threatened but unengaged court receives the live screen order per the regent contract");
            regent.setHierarchyState(regent.hierarchyState().withAggressor(Optional.empty(), 0L));
            challengerMob.discard();
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(focused.hierarchyState().order().orElseThrow().kind(),
                OrderKind.HOLD_COURT, "a quiet court returns to the hold order");
            regent.setTarget(null);

            // The leader-loss command contract, proven through a real death rather than injected
            // state. A court with a rostered loaded archfiend withdraws behind its withdrawal
            // captain; a court without one dissolves immediately.
            final InfernalHierarchyEntity doomed = spawnRank(fixture, "abyssal_regent", new BlockPos(0, 1, 0));
            final InfernalHierarchyEntity captain = spawnRank(
                fixture, "emberhorn_archfiend", new BlockPos(0, 1, 1)
            );
            final InfernalHierarchyEntity wardDemon = spawnRank(fixture, "demon", new BlockPos(1, 1, 0));
            parkDecisions(doomed, helper);
            parkDecisions(captain, helper);
            parkDecisions(wardDemon, helper);
            final long doomLease = helper.getLevel().getGameTime() + 300L;
            doomed.setHierarchyState(doomed.hierarchyState().withRoster(List.of(
                new InfernalHierarchyState.Member(captain.getUUID(), Rank.EMBERHORN_ARCHFIEND, doomLease),
                new InfernalHierarchyState.Member(wardDemon.getUUID(), Rank.DEMON, doomLease)
            ), 1L));
            captain.setHierarchyState(captain.hierarchyState().withLeader(
                Optional.of(doomed.getUUID()), Optional.of(Rank.ABYSSAL_REGENT), doomLease
            ));
            wardDemon.setHierarchyState(wardDemon.hierarchyState().withLeader(
                Optional.of(doomed.getUUID()), Optional.of(Rank.ABYSSAL_REGENT), doomLease
            ));
            doomed.invulnerableTime = 0;
            helper.assertTrue(doomed.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().magic(), 10_000.0F
            ), "the captain fixture needs a real death rather than a discard");
            helper.assertFalse(doomed.isAlive(), "the doomed regent is dead");
            helper.assertValueEqual(captain.hierarchyState().order().orElseThrow().kind(),
                OrderKind.WITHDRAW_TO_ANCHOR,
                "a court with a rostered archfiend selects its withdrawal captain at the death itself");
            helper.assertValueEqual(wardDemon.hierarchyState().order().orElseThrow().kind(),
                OrderKind.WITHDRAW_TO_ANCHOR,
                "every loaded member carries the court's parting instruction");
            makeDue(captain);
            InfernalHierarchyRuntime.tick(captain, helper.getLevel());
            helper.assertTrue(captain.hierarchyState().leaderId().isEmpty(),
                "the captain still releases through ordinary lease reconciliation");
            helper.assertValueEqual(captain.hierarchyState().order().orElseThrow().kind(),
                OrderKind.WITHDRAW_TO_ANCHOR,
                "the parting instruction is the one order that survives the release");
            helper.assertValueEqual(captain.hierarchyState().intent(), Intent.WITHDRAW,
                "withdrawOrdered executes on the live decision path");
            makeDue(wardDemon);
            InfernalHierarchyRuntime.tick(wardDemon, helper.getLevel());
            helper.assertValueEqual(wardDemon.hierarchyState().order().orElseThrow().kind(),
                OrderKind.WITHDRAW_TO_ANCHOR,
                "the member's parting instruction survives its own release");
            // The stacked ally-loss and leader-loss morale penalties may legitimately select the
            // deeper RETREAT over the plain regroup; both are the designed cancellation family.
            helper.assertTrue(wardDemon.hierarchyState().intent() == Intent.RETURN
                    || wardDemon.hierarchyState().intent() == Intent.RETREAT,
                "an ordered withdrawing Demon regroups through the bounded cancellation family");

            final InfernalHierarchyEntity lone = spawnRank(fixture, "abyssal_regent", new BlockPos(3, 1, 0));
            final InfernalHierarchyEntity strayDemon = spawnRank(fixture, "demon", new BlockPos(0, 1, 3));
            parkDecisions(lone, helper);
            parkDecisions(strayDemon, helper);
            lone.setHierarchyState(lone.hierarchyState().withRoster(List.of(
                new InfernalHierarchyState.Member(strayDemon.getUUID(), Rank.DEMON, doomLease)
            ), 1L));
            strayDemon.setHierarchyState(strayDemon.hierarchyState().withLeader(
                Optional.of(lone.getUUID()), Optional.of(Rank.ABYSSAL_REGENT), doomLease
            ));
            lone.invulnerableTime = 0;
            helper.assertTrue(lone.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().magic(), 10_000.0F
            ), "the dissolution fixture needs a real death too");
            helper.assertValueEqual(strayDemon.hierarchyState().order().orElseThrow().kind(),
                OrderKind.DISSOLVE,
                "a court without a rostered archfiend dissolves immediately at the death");
            makeDue(strayDemon);
            InfernalHierarchyRuntime.tick(strayDemon, helper.getLevel());
            helper.assertTrue(strayDemon.hierarchyState().intent() == Intent.RETURN
                    || strayDemon.hierarchyState().intent() == Intent.RETREAT,
                "the dissolved member enters the bounded cancellation family");

            final ServerPlayer witness = fixture.connectedPlayer(new BlockPos(2, 1, 3));
            final ServerPlayer truced = fixture.connectedPlayer(new BlockPos(1, 1, 3));
            final ServerPlayer spectator = fixture.connectedPlayer(new BlockPos(3, 1, 3));
            spectator.setGameMode(GameType.SPECTATOR);

            // A fear pulse that begins a new combat episode is preceded by at least ten ticks of
            // existing feedback. The first passive-cadence call arms the telegraph and applies
            // nothing, an immediate second call is still inside the ten-tick warning, the pulse
            // lands only after the warning elapsed, and a continuing episode never replays the
            // transition feedback.
            witness.removeAllEffects();
            helper.assertValueEqual(regent.hierarchyCounters().fearPulses(), 0L,
                "no fear pulse has run before the episode begins");
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().fearTelegraphs(), 1L,
                "the new combat episode arms the fear telegraph first");
            helper.assertValueEqual(regent.hierarchyCounters().fearPulses(), 0L,
                "the telegraph itself applies no pulse");
            helper.assertFalse(witness.hasEffect(MobEffects.DARKNESS),
                "no darkness lands during the fear telegraph");
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().fearPulses(), 0L,
                "the pulse waits out the full ten-tick warning");
            regent.hierarchyCounters().fearTelegraphAt = helper.getLevel().getGameTime()
                - InfernalHierarchyRules.FEAR_TELEGRAPH_TICKS;
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().fearPulses(), 1L,
                "the pulse commits once the warning elapsed");
            helper.assertTrue(witness.hasEffect(MobEffects.DARKNESS)
                    && witness.hasEffect(MobEffects.WEAKNESS),
                "the committed pulse applies the exact fear effects");
            witness.removeAllEffects();
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().fearTelegraphs(), 1L,
                "a continuing episode never replays the transition feedback");
            helper.assertValueEqual(regent.hierarchyCounters().fearPulses(), 2L,
                "a continuing episode pulses at its ordinary cadence");
            witness.removeAllEffects();

            final long phaseNow = helper.getLevel().getGameTime();
            regent.setHierarchyState(regent.hierarchyState().withTruce(
                Optional.of(truced.getUUID()),
                phaseNow + InfernalHierarchyRules.TRUCE_TICKS,
                phaseNow,
                0L
            ));
            for (final ServerPlayer player : List.of(witness, truced, spectator)) {
                player.removeAllEffects();
            }
            regent.removeAllEffects();
            regent.setHealth(250.0F);
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertTrue(regent.getPersistentData().getBooleanOr(
                InfernalHierarchyEntity.LEGACY_PHASE_KEY, false
            ), "the half-health phase latches through the exact legacy key");
            helper.assertValueEqual(regent.hierarchyState().phaseState(), PhaseState.TELEGRAPH,
                "the phase begins with its telegraph");
            helper.assertValueEqual(regent.hierarchyState().intent(), Intent.PHASE_TELEGRAPH,
                "the telegraph is the declared semantic action");
            helper.assertTrue(regent.hierarchyState().phaseDeadline()
                    >= helper.getLevel().getGameTime() + InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS - 1L,
                "the telegraph runs its full thirty ticks before anything commits");
            helper.assertFalse(regent.hasEffect(MobEffects.RESISTANCE),
                "the telegraph is a real warning window, so no regent buff exists yet");
            helper.assertFalse(regent.hasEffect(MobEffects.STRENGTH),
                "the telegraph applies no strength before the commit");
            helper.assertFalse(witness.hasEffect(MobEffects.SLOWNESS),
                "the telegraph applies no player debuff before the commit");
            witness.removeAllEffects();
            final long transitions = regent.hierarchyCounters().phaseTransitions();
            InfernalHierarchyRuntime.tickAbyssalTorment(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().phaseTransitions(), transitions,
                "crossing the threshold again never replays the phase");
            helper.assertValueEqual(regent.hierarchyState().phaseState(), PhaseState.TELEGRAPH,
                "the telegraph window is never restarted by a repeated threshold crossing");
            helper.assertFalse(witness.hasEffect(MobEffects.DARKNESS),
                "no new fear pulse begins inside the phase window");

            regent.setHierarchyState(regent.hierarchyState().withPhase(PhaseState.TELEGRAPH, true, 0L));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyState().phaseState(), PhaseState.COMMIT,
                "the commit is an observable state rather than being overwritten inside the same call");
            helper.assertTrue(regent.hasEffect(MobEffects.RESISTANCE) && regent.hasEffect(MobEffects.STRENGTH),
                "the commit applies the exact phase buffs to the regent");
            helper.assertValueEqual(regent.getEffect(MobEffects.RESISTANCE).getAmplifier(), 1,
                "resistance two is preserved exactly");
            helper.assertValueEqual(regent.getEffect(MobEffects.STRENGTH).getDuration(),
                InfernalHierarchyRules.PHASE_EFFECT_TICKS, "the exact two hundred forty tick duration holds");
            helper.assertTrue(witness.hasEffect(MobEffects.SLOWNESS) && witness.hasEffect(MobEffects.DARKNESS)
                    && witness.hasEffect(MobEffects.WEAKNESS),
                "the commit applies the exact phase debuffs to a relationship-valid hostile player");
            helper.assertValueEqual(witness.getEffect(MobEffects.SLOWNESS).getAmplifier(), 2,
                "slowness three is preserved exactly");
            helper.assertValueEqual(witness.getEffect(MobEffects.WEAKNESS).getAmplifier(), 1,
                "weakness two is preserved exactly");
            helper.assertFalse(truced.hasEffect(MobEffects.SLOWNESS),
                "the one valid truce player is never a phase debuff recipient");
            helper.assertFalse(spectator.hasEffect(MobEffects.SLOWNESS),
                "a spectator is never a phase debuff recipient");
            final List<net.minecraft.world.entity.player.Player> recipients =
                InfernalHierarchyRuntime.phaseRecipients(
                    regent, helper.getLevel(), regent.hierarchyState(), helper.getLevel().getGameTime()
                );
            helper.assertTrue(recipients.size() <= InfernalHierarchyRules.PHASE_PLAYER_CAP,
                "the phase never exceeds the thirty two nearest deterministic recipients");
            helper.assertTrue(recipients.contains(witness),
                "the deterministic recipient set contains the relationship-valid hostile player");
            helper.assertFalse(recipients.contains(truced), "relationship filtering excludes the truce player");
            helper.assertFalse(recipients.contains(spectator), "relationship filtering excludes the spectator");
            helper.assertValueEqual(
                InfernalHierarchyRuntime.phaseRecipients(
                    regent, helper.getLevel(), regent.hierarchyState(), helper.getLevel().getGameTime()
                ),
                recipients,
                "the recipient order is deterministic rather than engine iteration order"
            );
            helper.assertTrue(regent.hierarchyState().summons().isEmpty(),
                "a full court refuses the optional reinforcement transaction");
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyState().phaseState(), PhaseState.RECOVERY,
                "recovery follows the commit rather than replacing it");
            helper.assertTrue(regent.hierarchyState().phaseDeadline()
                    >= helper.getLevel().getGameTime() + InfernalHierarchyRules.PHASE_RECOVERY_TICKS - 1L,
                "recovery runs its full sixty ticks");
            regent.setHierarchyState(regent.hierarchyState().withPhase(PhaseState.RECOVERY, true, 0L));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyState().phaseState(), PhaseState.DONE,
                "recovery ends in the completed phase");
            regent.setHierarchyState(regent.hierarchyState().withTruce(Optional.empty(), 0L, 0L, 0L));

            CreatureBehaviorState.bind(courtArchfiend, UUID.randomUUID());
            for (final InfernalHierarchyEntity member : courtDemons) {
                CreatureBehaviorState.bind(member, UUID.randomUUID());
            }
            for (final int[] offset : new int[][]{{2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}}) {
                final BlockPos candidate = regent.blockPosition().offset(offset[0], offset[1], offset[2]);
                fixture.placeBlock(candidate.below(), Blocks.STONE.defaultBlockState());
                fixture.placeBlock(candidate, Blocks.AIR.defaultBlockState());
                fixture.placeBlock(candidate.above(), Blocks.AIR.defaultBlockState());
            }
            regent.setHierarchyState(regent.hierarchyState()
                .withRoster(List.of(), courtState.orderEpoch() + 1L)
                .withPhase(PhaseState.TELEGRAPH, true, 0L));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            final long summonNow = helper.getLevel().getGameTime();
            final List<UUID> summonIds = regent.hierarchyState().summons();
            helper.assertValueEqual(summonIds.size(), InfernalHierarchyRules.PHASE_SUMMON_CAP,
                "an eligible transaction constructs exactly two temporary demons");
            helper.assertValueEqual(regent.hierarchyCounters().summonConstructions(), 2L,
                "constructions are counted");
            helper.assertTrue(regent.hierarchyState().summonExpiresAt()
                    <= summonNow + InfernalHierarchyRules.SUMMON_LIFE_TICKS,
                "temporary summons live at most twelve hundred ticks");
            final List<InfernalHierarchyEntity> summonEntities = new ArrayList<>();
            for (final UUID id : summonIds) {
                final Entity summon = helper.getLevel().getEntity(id);
                helper.assertTrue(summon instanceof InfernalHierarchyEntity,
                    "each summon is a real hierarchy Demon");
                final InfernalHierarchyEntity demon = (InfernalHierarchyEntity) summon;
                fixture.track(demon);
                summonEntities.add(demon);
                helper.assertValueEqual(demon.hierarchyRank(), Rank.DEMON, "summons are exact demons");
                helper.assertValueEqual(demon.hierarchyState().summonerId().orElseThrow(), regent.getUUID(),
                    "each summon records its summoner");
                helper.assertValueEqual(demon.hierarchyState().leaderId().orElseThrow(), regent.getUUID(),
                    "each summon joins the court rather than exceeding it elsewhere");
                helper.assertTrue(InfernalHierarchyRuntime.directPactOwner(demon).isEmpty(),
                    "temporary summons never carry a player owner");
            }
            regent.setHierarchyState(regent.hierarchyState().withSummons(summonIds, summonNow));
            makeDue(regent);
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertTrue(regent.hierarchyState().summons().isEmpty(),
                "expired summons are removed from the bounded roster");
            for (final InfernalHierarchyEntity summon : summonEntities) {
                helper.assertFalse(summon.isAlive(),
                    "expired summons are discarded rather than killed for loot");
            }
            helper.assertTrue(regent.hierarchyCounters().summonCleanups() >= 2L, "cleanups are counted");

            final InfernalHierarchyEntity orphan = spawnRank(fixture, "demon", new BlockPos(2, 1, 0));
            orphan.setHierarchyState(orphan.hierarchyState()
                .withSummoner(Optional.of(UUID.randomUUID()))
                .withSummons(List.of(), 0L));
            makeDue(orphan);
            InfernalHierarchyRuntime.tick(orphan, helper.getLevel());
            helper.assertFalse(orphan.isAlive(), "a summon with a missing summoner discards itself");
    }

    public static void infernalLeaderLossAndUnloadedAuthorityCancelExecution(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            final InfernalHierarchyEntity leader = spawnRank(fixture, "emberhorn_archfiend", new BlockPos(1, 1, 1));
            final InfernalHierarchyEntity member = spawnRank(fixture, "demon", new BlockPos(2, 1, 1));
            final InfernalHierarchyEntity stranded = spawnRank(fixture, "demon", new BlockPos(0, 1, 1));
            final InfernalHierarchyEntity survivorLeader = spawnRank(
                fixture, "emberhorn_archfiend", new BlockPos(1, 1, 0)
            );
            final InfernalHierarchyEntity lapsed = spawnRank(fixture, "demon", new BlockPos(2, 1, 2));
            final Zombie quarry = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0), EntitySpawnReason.EVENT);
            quarry.setNoAi(true);
            quarry.setDeltaMovement(Vec3.ZERO);
            // The squad scan radius exceeds the batch grid, so the reciprocal lease keeps this pass local.
            member.setHierarchyState(member.hierarchyState().withLeader(
                Optional.of(leader.getUUID()), Optional.of(Rank.EMBERHORN_ARCHFIEND),
                helper.getLevel().getGameTime() + 100L
            ));
            // The squad provocation actors are staged up front so that every schedule registration
            // happens here in the test body. The vanilla runAtTickTime map is iterated while due
            // tasks run, so a task that registers further tasks mutates the map mid-iteration and
            // can corrupt the iterator; the single state machine below never registers anything.
            // The GameTest world is solid rock outside the three-by-three template, so every site on
            // the scenario's line, including the sight corridor between marshal and raider, is
            // opened first, three layers tall because the Archfiend stands two point four blocks.
            for (int z = 0; z <= 3; z++) {
                fixture.placeBlock(helper.absolutePos(new BlockPos(3, 0, z)), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    fixture.placeBlock(helper.absolutePos(new BlockPos(3, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
            final InfernalHierarchyEntity marshal = spawnRank(fixture, "emberhorn_archfiend", new BlockPos(3, 1, 3));
            final InfernalHierarchyEntity wardDemon = spawnRank(fixture, "demon", new BlockPos(3, 1, 1));
            final long wardLease = helper.getLevel().getGameTime() + 400L;
            // The squad scan radius exceeds the batch grid, so the reciprocal lease keeps this pass local.
            wardDemon.setHierarchyState(wardDemon.hierarchyState().withLeader(
                Optional.of(marshal.getUUID()), Optional.of(Rank.EMBERHORN_ARCHFIEND), wardLease
            ));
            marshal.setHierarchyState(marshal.hierarchyState().withRoster(List.of(
                new InfernalHierarchyState.Member(wardDemon.getUUID(), Rank.DEMON, wardLease)
            ), 1L));
            final ServerPlayer raider = fixture.connectedPlayer(new BlockPos(3, 1, 0));
            final java.util.concurrent.atomic.AtomicInteger chain =
                new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicReference<InfernalHierarchyEntity> keeperRef =
                new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.atomic.AtomicReference<ServerPlayer> wandererRef =
                new java.util.concurrent.atomic.AtomicReference<>();
            final BlockPos hearth = helper.absolutePos(new BlockPos(0, 1, 0));
            helper.runAfterDelay(SPAWN_INDEX_SETTLE_TICKS, () -> {
                if (!chain.compareAndSet(0, 1)) {
                    return;
                }
                try {
                    leaderLossStage(helper, leader, member, stranded, survivorLeader, lapsed, quarry);
                    squadProvocationHit(helper, marshal, wardDemon, raider);
                    chain.set(2);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.onEachTick(() -> {
                switch (chain.get()) {
                    case 2 -> {
                        // The live marshal must genuinely acquire the raider on its own tick path.
                        if (marshal.getTargetUnchecked() == raider) {
                            // Freeze the marshal and retire the raider so the territorial ladder
                            // below cannot be contaminated by this scenario's actors.
                            marshal.setNoAi(true);
                            marshal.setTarget(null);
                            raider.setGameMode(GameType.SPECTATOR);
                            fixture.placeBlock(hearth, Blocks.CAMPFIRE.defaultBlockState());
                            final InfernalHierarchyEntity keeper = spawnRank(fixture, "demon", new BlockPos(0, 1, 2));
                            keeper.setPersistenceRequired();
                            keeper.setNoAi(false);
                            keeperRef.set(keeper);
                            wandererRef.set(fixture.connectedPlayer(new BlockPos(2, 1, 2)));
                            chain.set(3);
                        }
                    }
                    case 3 -> {
                        final InfernalHierarchyState live = keeperRef.get().hierarchyState();
                        if (live.anchorPos().isPresent()) {
                            helper.assertValueEqual(live.anchorPos().orElseThrow(), hearth.asLong(),
                                "an unbound demon claims the loaded lit campfire as its warm post");
                            chain.set(4);
                        }
                    }
                    case 4 -> {
                        final Intent intent = keeperRef.get().hierarchyState().intent();
                        if (intent == Intent.POST_WATCH || intent == Intent.APPRAISE) {
                            chain.set(5);
                        }
                    }
                    case 5 -> {
                        if (keeperRef.get().hierarchyState().intent() == Intent.WARN) {
                            helper.assertTrue(keeperRef.get().getTarget() == null,
                                "the twenty-tick warning window presses nothing");
                            chain.set(6);
                        }
                    }
                    case 6 -> {
                        if (keeperRef.get().hierarchyState().intent() == Intent.PRESS) {
                            chain.set(7);
                            try {
                                helper.assertTrue(keeperRef.get().getTarget() == wandererRef.get(),
                                    "revalidated territorial pressure acquires the intruder on the live path");
                            } finally {
                                fixture.close();
                            }
                            helper.succeed();
                        }
                    }
                    default -> {
                    }
                }
            });
            // Bounded deadline, registered here in the test body. If the live chain stalls this
            // fails loudly with the reached stage and still releases every fixture.
            helper.runAfterDelay(560L, () -> {
                if (chain.get() >= 7) {
                    return;
                }
                try {
                    helper.assertTrue(chain.get() >= 7,
                        "the live chain must reach squad provocation, acquisition, post, warning,"
                            + " and pressure without a helper call: stage " + chain.get());
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

    /**
     * The designed squad-member-attack provocation, raised at the accepted hit itself. A leased
     * Demon takes one real hit from a raider within sixteen blocks of its leader; the propagation
     * must seed the leader's aggressor and stable challenger, release restraint, and the marshal
     * then goes live so its own tick path can acquire the raider with no further helper call. The
     * marshal's squad channel stays parked behind a bounded future sentinel so it cannot lease
     * members across the batch grid.
     */
    private static void squadProvocationHit(
        final GameTestHelper helper,
        final InfernalHierarchyEntity marshal,
        final InfernalHierarchyEntity ward,
        final ServerPlayer raider
    ) {
        helper.assertFalse(marshal.canAttack(raider),
            "an unprovoked Archfiend is restrained against the raider");
        ward.invulnerableTime = 0;
        helper.assertTrue(ward.hurtServer(
            helper.getLevel(), helper.getLevel().damageSources().playerAttack(raider), 2.0F
        ), "the squad member takes one real accepted hit");
        helper.assertTrue(marshal.hierarchyState().aggressorId()
                .filter(raider.getUUID()::equals).isPresent(),
            "a direct attack on a valid squad member within sixteen blocks provokes the leader");
        helper.assertValueEqual(marshal.hierarchyState().challengerId().orElseThrow(),
            raider.getUUID(), "the squad provocation seeds the stable challenger");
        helper.assertTrue(marshal.hierarchyCounters().squadProvocations() >= 1L,
            "the propagation is counted and line of sight gated");
        helper.assertTrue(marshal.canAttack(raider),
            "restraint releases through the propagated provocation");
        marshal.setNoAi(false);
        marshal.setNoGravity(true);
        marshal.setPersistenceRequired();
        final long parkedGroup = helper.getLevel().getGameTime() + 10_000L;
        marshal.setHierarchyState(marshal.hierarchyState().withCadence(
            new InfernalHierarchyState.Cadence(0L, 0L, parkedGroup, parkedGroup, 0L, 0L)
        ));
    }

    private static void leaderLossStage(
        final GameTestHelper helper,
        final InfernalHierarchyEntity leader,
        final InfernalHierarchyEntity member,
        final InfernalHierarchyEntity stranded,
        final InfernalHierarchyEntity survivorLeader,
        final InfernalHierarchyEntity lapsed,
        final Zombie quarry
    ) {
            leader.setHierarchyState(leader.hierarchyState().withRoster(List.of(
                new InfernalHierarchyState.Member(member.getUUID(), Rank.DEMON,
                    helper.getLevel().getGameTime() + 100L)
            ), 1L));
            makeDue(leader);
            InfernalHierarchyRuntime.tick(leader, helper.getLevel());
            helper.assertValueEqual(member.hierarchyState().leaderId().orElseThrow(), leader.getUUID(),
                "the loss fixture starts from a live lease");
            member.setTarget(quarry);
            leader.discard();
            makeDue(member);
            InfernalHierarchyRuntime.tick(member, helper.getLevel());
            helper.assertTrue(member.hierarchyState().leaderId().isEmpty(),
                "leader death releases the membership");
            helper.assertTrue(member.hierarchyState().order().isEmpty(), "leader death cancels the order");
            helper.assertTrue(member.getTarget() == null, "leader death cancels active execution");
            helper.assertValueEqual(member.hierarchyState().morale(),
                InfernalHierarchyRules.MORALE_BASELINE - InfernalHierarchyRules.MORALE_LEADER_LOSS_PENALTY,
                "leader loss applies the exact morale penalty once");
            helper.assertValueEqual(member.hierarchyState().intent(), Intent.RETURN,
                "the unmoored member regroups instead of resolving the leader globally");
            helper.assertTrue(member.hierarchyCounters().orderCancellations() >= 1L,
                "the cancellation is counted");

            final long now = helper.getLevel().getGameTime();
            stranded.setHierarchyState(stranded.hierarchyState()
                .withLeader(Optional.of(UUID.randomUUID()), Optional.of(Rank.EMBERHORN_ARCHFIEND), now + 300L)
                .withOrder(Optional.of(new InfernalHierarchyState.Order(
                    OrderKind.FOCUS_CHALLENGER, Optional.of(quarry.getUUID()), now + 100L, 1L,
                    Rank.EMBERHORN_ARCHFIEND
                ))));
            stranded.setTarget(quarry);
            makeDue(stranded);
            InfernalHierarchyRuntime.tick(stranded, helper.getLevel());
            helper.assertTrue(stranded.hierarchyState().leaderId().isEmpty(),
                "an unloaded issuer cancels execution without force-loading it");
            helper.assertTrue(stranded.hierarchyState().order().isEmpty(),
                "no order survives a missing issuer");
            helper.assertTrue(stranded.getTarget() == null, "the execution claim is canceled");
            helper.assertValueEqual(stranded.hierarchyState().intent(), Intent.RETURN,
                "the stranded member enters bounded regroup behavior");

            lapsed.setHierarchyState(lapsed.hierarchyState()
                .withLeader(Optional.of(survivorLeader.getUUID()), Optional.of(Rank.EMBERHORN_ARCHFIEND),
                    helper.getLevel().getGameTime())
                .withOrder(Optional.of(new InfernalHierarchyState.Order(
                    OrderKind.HOLD_POST, Optional.empty(), helper.getLevel().getGameTime() + 100L, 1L,
                    Rank.EMBERHORN_ARCHFIEND
                ))));
            lapsed.setTarget(quarry);
            makeDue(lapsed);
            InfernalHierarchyRuntime.tick(lapsed, helper.getLevel());
            helper.assertTrue(lapsed.hierarchyState().leaderId().isEmpty(),
                "an expired lease releases the membership naturally");
            helper.assertTrue(lapsed.hierarchyState().order().isEmpty(),
                "no order outlives its membership lease");
            helper.assertValueEqual(lapsed.hierarchyState().morale(), InfernalHierarchyRules.MORALE_BASELINE,
                "a natural lease expiry is not a leader-loss morale event");
            helper.assertTrue(lapsed.getTarget() == quarry,
                "natural expiry releases the lease without canceling unrelated execution");
    }

    public static void infernalSaveReloadTruncatesAndMigratesState(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final InfernalHierarchyEntity demon = spawnRank(fixture, "demon", new BlockPos(1, 1, 1));
            demon.setHierarchyState(demon.hierarchyState()
                .withLeader(Optional.of(UUID.randomUUID()), Optional.of(Rank.ABYSSAL_REGENT), now + 50_000L)
                .withOrder(Optional.of(new InfernalHierarchyState.Order(
                    OrderKind.HOLD_COURT, Optional.empty(), now + 50_000L, 1L, Rank.ABYSSAL_REGENT
                )))
                .withTruce(Optional.of(UUID.randomUUID()), Long.MAX_VALUE, now, Long.MAX_VALUE)
                .withAggressor(Optional.of(UUID.randomUUID()), Long.MAX_VALUE)
                .withMorale(200, now)
                .withIntent(Intent.PRESS));
            final CompoundTag saved = save(helper, demon);
            final InfernalHierarchyEntity loaded = createRank(helper, "demon", EntitySpawnReason.LOAD);
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            final long later = helper.getLevel().getGameTime();
            final InfernalHierarchyState reloaded = loaded.hierarchyState();
            helper.assertTrue(reloaded.membershipLeaseUntil()
                    <= later + InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS,
                "implausible lease deadlines clamp to the four hundred tick horizon");
            helper.assertTrue(reloaded.order().isEmpty() || reloaded.order().orElseThrow().expiresAt()
                    <= later + InfernalHierarchyRules.REGENT_ORDER_TICKS,
                "order deadlines clamp to the issuer lifetime");
            helper.assertTrue(reloaded.truceExpiresAt() <= later + InfernalHierarchyRules.TRUCE_TICKS,
                "truce deadlines clamp to two hundred ticks");
            helper.assertTrue(reloaded.truceBreachUntil() <= later + InfernalHierarchyRules.TRUCE_BREACH_TICKS,
                "breach deadlines clamp to six hundred ticks");
            helper.assertTrue(reloaded.aggressorExpiresAt() <= later + InfernalHierarchyRules.AGGRESSOR_TICKS,
                "aggressor deadlines clamp to six hundred ticks");
            helper.assertValueEqual(reloaded.intent(), Intent.IDLE,
                "no live semantic action resumes from disk");
            helper.assertTrue(loaded.getTarget() == null, "no target resumes from disk");
            helper.assertTrue(loaded.getNavigation().isDone(), "no path resumes from disk");
            helper.assertValueEqual(reloaded.morale(), 200,
                "morale reloads without free recovery at the same instant");

            final InfernalHierarchyEntity regent = spawnRank(fixture, "abyssal_regent", new BlockPos(2, 1, 2));
            final CompoundTag oversized = save(helper, regent);
            final CompoundTag stateTag = oversized.getCompound(InfernalHierarchyEntity.STATE_KEY).orElseThrow();
            stateTag.putInt("MemberCount", 12);
            for (int index = 0; index < 12; index++) {
                final CompoundTag row = new CompoundTag();
                row.putString("Id", new UUID(50L, index).toString());
                row.putString("Rank", "demon");
                row.putLong("LeaseUntil", helper.getLevel().getGameTime() + 300L);
                stateTag.put("Member" + index, row);
            }
            stateTag.putInt("SummonCount", 5);
            for (int index = 0; index < 5; index++) {
                stateTag.putString("Summon" + index, new UUID(60L, index).toString());
            }
            stateTag.putLong("SummonExpiresAt", helper.getLevel().getGameTime() + 100L);
            oversized.put(InfernalHierarchyEntity.STATE_KEY, stateTag);
            final InfernalHierarchyEntity truncated = createRank(helper, "abyssal_regent", EntitySpawnReason.LOAD);
            fixture.track(truncated);
            truncated.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), oversized
            ));
            helper.assertValueEqual(truncated.hierarchyState().roster().size(),
                InfernalHierarchyRules.COURT_MEMBER_CAP,
                "oversized rosters truncate deterministically to the court cap");
            helper.assertValueEqual(truncated.hierarchyState().summons().size(),
                InfernalHierarchyRules.PHASE_SUMMON_CAP,
                "oversized summon lists truncate to the two-summon cap");

            final InfernalHierarchyEntity summon = spawnRank(fixture, "demon", new BlockPos(0, 1, 0));
            final UUID summoner = UUID.randomUUID();
            final long summonExpiry = helper.getLevel().getGameTime()
                + InfernalHierarchyRules.SUMMON_LIFE_TICKS;
            summon.setHierarchyState(summon.hierarchyState()
                .withSummoner(Optional.of(summoner), summonExpiry));
            helper.assertTrue(summon.hierarchyState().summons().isEmpty(),
                "a temporary phase demon owns no summons of its own");
            final CompoundTag summonTag = save(helper, summon);
            final CompoundTag summonState = summonTag
                .getCompound(InfernalHierarchyEntity.STATE_KEY).orElseThrow();
            helper.assertValueEqual(summonState.getIntOr("SummonCount", -1), 0,
                "the persisted summon list stays empty for a temporary body");
            helper.assertValueEqual(summonState.getLongOr("SummonExpiresAt", 0L), summonExpiry,
                "the temporary body persists its own life deadline");
            final InfernalHierarchyEntity reloadedSummon = createRank(helper, "demon", EntitySpawnReason.LOAD);
            fixture.track(reloadedSummon);
            reloadedSummon.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), summonTag
            ));
            helper.assertValueEqual(reloadedSummon.hierarchyState().summonerId().orElseThrow(), summoner,
                "the summoner link survives reload");
            helper.assertTrue(reloadedSummon.hierarchyState().summonExpiresAt() > 0L,
                "a reloaded reinforcement keeps a live expiry instead of leaking permanently");
            helper.assertTrue(reloadedSummon.hierarchyState().summonExpiresAt()
                    <= helper.getLevel().getGameTime() + InfernalHierarchyRules.SUMMON_LIFE_TICKS,
                "the reloaded expiry stays inside the twelve hundred tick bound");
            reloadedSummon.setHierarchyState(reloadedSummon.hierarchyState()
                .withSummoner(Optional.of(summoner), helper.getLevel().getGameTime()));
            makeDue(reloadedSummon);
            InfernalHierarchyRuntime.tick(reloadedSummon, helper.getLevel());
            helper.assertFalse(reloadedSummon.isAlive(),
                "a reloaded reinforcement still self-expires on its own preserved deadline");

            final InfernalHierarchyEntity legacy = spawnRank(fixture, "abyssal_regent", new BlockPos(0, 1, 2));
            final CompoundTag legacyTag = save(helper, legacy);
            legacyTag.remove(InfernalHierarchyEntity.STATE_KEY);
            final CompoundTag forgeData = legacyTag.getCompound("NeoForgeData").orElseGet(CompoundTag::new);
            forgeData.putBoolean(InfernalHierarchyEntity.LEGACY_PHASE_KEY, true);
            legacyTag.put("NeoForgeData", forgeData);
            final InfernalHierarchyEntity migrated = createRank(helper, "abyssal_regent", EntitySpawnReason.LOAD);
            fixture.track(migrated);
            migrated.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacyTag
            ));
            helper.assertTrue(migrated.hierarchyState().phaseCompleted(),
                "a true 1.4.0 latch migrates to a completed phase");
            helper.assertValueEqual(migrated.hierarchyState().phaseState(), PhaseState.DONE,
                "migration marks the phase done");
            helper.assertFalse(AbyssalRegentRules.beginsTormentPhase(100.0D,
                migrated.hierarchyState().phaseCompleted()), "migration never replays the phase");
            legacyTag.getCompound("NeoForgeData").orElseThrow()
                .remove(InfernalHierarchyEntity.LEGACY_PHASE_KEY);
            final InfernalHierarchyEntity untriggered = createRank(helper, "abyssal_regent", EntitySpawnReason.LOAD);
            fixture.track(untriggered);
            untriggered.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacyTag
            ));
            helper.assertFalse(untriggered.hierarchyState().phaseCompleted(),
                "an absent latch migrates to the untriggered phase");

            // A save inside the thirty-tick telegraph window resumes that exact window on reload.
            // Mapping it to a completed phase would permanently cancel the once-per-Regent phase.
            final InfernalHierarchyEntity phased = spawnRank(fixture, "abyssal_regent", new BlockPos(1, 1, 2));
            phased.setHealth(250.0F);
            InfernalHierarchyRuntime.tickAbyssalTorment(phased, helper.getLevel());
            helper.assertValueEqual(phased.hierarchyState().phaseState(), PhaseState.TELEGRAPH,
                "the resume fixture saves from inside a real telegraph window");
            final CompoundTag midPhase = save(helper, phased);
            final InfernalHierarchyEntity resumed = createRank(helper, "abyssal_regent", EntitySpawnReason.LOAD);
            fixture.track(resumed);
            resumed.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), midPhase
            ));
            helper.assertValueEqual(resumed.hierarchyState().phaseState(), PhaseState.TELEGRAPH,
                "a save during the telegraph resumes the telegraph rather than cancelling the phase");
            helper.assertTrue(resumed.hierarchyState().phaseCompleted(),
                "the resumed window keeps the once-per-regent latch, so the phase can never replay");
            helper.assertTrue(resumed.hierarchyState().phaseDeadline()
                    <= helper.getLevel().getGameTime() + InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS,
                "the resumed telegraph deadline clamps to the thirty-tick window");
            // The reinforcement branch is parked through the one-phase-group rule so a reload fixture
            // never constructs bodies, and the due telegraph still commits its exact effects.
            resumed.setHierarchyState(resumed.hierarchyState()
                .withSummons(List.of(phased.getUUID()), helper.getLevel().getGameTime() + 100L)
                .withPhase(PhaseState.TELEGRAPH, true, 0L));
            makeDue(resumed);
            InfernalHierarchyRuntime.tick(resumed, helper.getLevel());
            helper.assertValueEqual(resumed.hierarchyState().phaseState(), PhaseState.COMMIT,
                "the resumed telegraph still commits on its own preserved deadline");
            helper.assertTrue(resumed.hasEffect(MobEffects.RESISTANCE),
                "the resumed phase applies its exact commit effects");

            final InfernalHierarchyEntity owned = spawnRank(fixture, "demon", new BlockPos(2, 1, 0));
            final UUID direct = UUID.randomUUID();
            final UUID animus = UUID.randomUUID();
            final CompoundTag ownedTag = save(helper, owned);
            final CompoundTag ownerData = ownedTag.getCompound("NeoForgeData").orElseGet(CompoundTag::new);
            ownerData.putString("WarlockeryCreatureOwner", direct.toString());
            ownerData.putString(InfernalPactEffects.OWNER_KEY, animus.toString());
            ownedTag.put("NeoForgeData", ownerData);
            final InfernalHierarchyEntity inherited = createRank(helper, "demon", EntitySpawnReason.LOAD);
            fixture.track(inherited);
            inherited.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), ownedTag
            ));
            helper.assertValueEqual(InfernalHierarchyRuntime.directPactOwner(inherited).orElseThrow(), direct,
                "the 1.4.0 direct owner key survives reload");
            helper.assertValueEqual(InfernalHierarchyRuntime.animusOwner(inherited).orElseThrow(), animus,
                "the conflicting legacy Animus value is preserved rather than deleted");
            final InfernalHierarchyState resolved = InfernalHierarchyRuntime.reconcileAuthority(
                inherited, inherited.hierarchyState()
            );
            helper.assertValueEqual(resolved.authorityClass(),
                InfernalHierarchyRules.AuthorityClass.DIRECT_PACT,
                "reconciliation resolves the conflict to the direct pact");
            helper.assertValueEqual(resolved.authorityId().orElseThrow(), direct,
                "the direct owner remains authoritative after reload");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void infernalCollisionBorderAndChunkEdgeFailSafely(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            final InfernalHierarchyEntity regent = spawnRank(fixture, "abyssal_regent", new BlockPos(1, 1, 1));
            final long now = helper.getLevel().getGameTime();
            final List<BlockPos> candidates = new ArrayList<>();
            for (final int[] offset : new int[][]{{2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}}) {
                candidates.add(regent.blockPosition().offset(offset[0], offset[1], offset[2]));
            }
            for (final BlockPos candidate : candidates) {
                fixture.placeBlock(candidate, Blocks.STONE.defaultBlockState());
            }
            final long readsBefore = regent.hierarchyCounters().blockReads();
            final InfernalHierarchyState blocked = InfernalHierarchyRuntime.attemptReinforcementTransaction(
                regent, helper.getLevel(), regent.hierarchyState(), now
            );
            helper.assertTrue(blocked.summons().isEmpty(),
                "fully blocked collision candidates refuse the transaction");
            helper.assertTrue(countDemonsNear(helper, regent) == 0,
                "no partial participant survives a blocked transaction");
            helper.assertTrue(regent.hierarchyCounters().blockReads() - readsBefore <= 12L,
                "spawn-site validation charges actual reads, at most three per candidate");

            openStandingSite(fixture, candidates.get(0));
            final InfernalHierarchyState single = InfernalHierarchyRuntime.attemptReinforcementTransaction(
                regent, helper.getLevel(), regent.hierarchyState(), now
            );
            helper.assertTrue(single.summons().isEmpty(),
                "one safe position is not enough for the exact two-body transaction");
            helper.assertTrue(countDemonsNear(helper, regent) == 0,
                "the two-body transaction never spawns a lone partial summon");

            openStandingSite(fixture, candidates.get(1));
            final InfernalHierarchyState committed = InfernalHierarchyRuntime.attemptReinforcementTransaction(
                regent, helper.getLevel(), regent.hierarchyState(), now
            );
            helper.assertValueEqual(committed.summons().size(), InfernalHierarchyRules.PHASE_SUMMON_CAP,
                "two validated positions commit the exact pair");
            for (final UUID id : committed.summons()) {
                final Entity summon = helper.getLevel().getEntity(id);
                helper.assertTrue(summon instanceof InfernalHierarchyEntity, "committed summons exist");
                fixture.track(summon);
                helper.assertTrue(helper.getLevel().getWorldBorder().isWithinBounds(summon.blockPosition()),
                    "every accepted spawn site is world-border valid");
                helper.assertTrue(helper.getLevel().hasChunkAt(summon.blockPosition()),
                    "every accepted spawn site is naturally loaded");
                helper.assertTrue(helper.getLevel().getBlockState(summon.blockPosition())
                        .getCollisionShape(helper.getLevel(), summon.blockPosition()).isEmpty(),
                    "every accepted spawn site is collision-free");
            }

            final InfernalHierarchyEntity edgeDemon = spawnRank(fixture, "demon", new BlockPos(0, 1, 0));
            makeDue(edgeDemon);
            InfernalHierarchyRuntime.tick(edgeDemon, helper.getLevel());
            helper.assertTrue(edgeDemon.hierarchyCounters().candidateVisits()
                    <= InfernalHierarchyRuntime.retainedCandidateCap(Rank.DEMON),
                "a cell-edge observation spanning foreign chunks stays inside its candidate budget");
            helper.assertTrue(edgeDemon.isAlive(), "cell-edge scans fail safely without engine errors");

            // An open-sky Regent must charge its world-border and sky reads before those filters run.
            // On a barrier perch every nearby candidate is open sky, so an uncharged filter walk would
            // visit thousands of positions; the charged walk stops at the one hundred twenty eight
            // read budget and claims nothing.
            final BlockPos perch = helper.absolutePos(new BlockPos(2, 7, 0));
            fixture.placeBlock(perch.below(), Blocks.BARRIER.defaultBlockState());
            final InfernalHierarchyEntity skyRegent = spawnRank(fixture, "abyssal_regent", new BlockPos(2, 7, 0));
            skyRegent.setNoGravity(true);
            final long parkedChannels = helper.getLevel().getGameTime() + 10_000L;
            skyRegent.setHierarchyState(skyRegent.hierarchyState().withCadence(
                new InfernalHierarchyState.Cadence(0L, parkedChannels, parkedChannels, 0L, 0L, 0L)
            ));
            final long skyReadsBefore = skyRegent.hierarchyCounters().blockReads();
            final long skyVisitsBefore = skyRegent.hierarchyCounters().anchorCandidateVisits();
            InfernalHierarchyRuntime.tick(skyRegent, helper.getLevel());
            final long skyReads = skyRegent.hierarchyCounters().blockReads() - skyReadsBefore;
            final long skyVisits = skyRegent.hierarchyCounters().anchorCandidateVisits() - skyVisitsBefore;
            helper.assertTrue(skyRegent.hierarchyState().anchorPos().isEmpty(),
                "no open-sky candidate becomes a deep anchor");
            helper.assertTrue(skyReads >= InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS - 3L,
                "the sky and border filters charge the read budget before they run");
            helper.assertTrue(skyReads <= InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS,
                "the deep anchor search never exceeds its one hundred twenty eight read budget");
            helper.assertTrue(skyVisits <= InfernalHierarchyRules.REGENT_ANCHOR_BLOCK_READS,
                "the anchor walk is bounded by its charges rather than filtering positions for free");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void infernalAcquisitionPathsPreserveTargetsAndContracts(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final Zombie authored = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 0), EntitySpawnReason.EVENT);
            authored.setNoAi(true);
            for (final String id : List.of("demon", "emberhorn_archfiend", "abyssal_regent")) {
                final InfernalHierarchyEntity summoned = createRank(helper, id, EntitySpawnReason.MOB_SUMMONED);
                fixture.track(summoned);
                final BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
                summoned.snapTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
                summoned.setTarget(authored);
                summoned.setPersistenceRequired();
                helper.assertTrue(helper.getLevel().addFreshEntity(summoned),
                    "the acquisition route adds exactly its own entity: " + id);
                summoned.finalizeSpawn(
                    helper.getLevel(),
                    helper.getLevel().getCurrentDifficultyAt(summoned.blockPosition()),
                    EntitySpawnReason.MOB_SUMMONED,
                    null
                );
                helper.assertValueEqual(summoned.getType(), ModEntities.ALL.get(id).get(),
                    "normalization never swaps the registered type: " + id);
                helper.assertTrue(summoned.getTargetUnchecked() == authored,
                    "the authored initial target survives entity-side normalization: " + id);
                helper.assertTrue(summoned.isPersistenceRequired(),
                    "the acquisition persistence result survives normalization: " + id);
                if (summoned.hierarchyRank() == Rank.EMBERHORN_ARCHFIEND) {
                    helper.assertTrue(summoned.getTarget() == null,
                        "the exact restrained Archfiend contract still gates an unprovoked objective");
                } else {
                    helper.assertTrue(summoned.getTarget() == authored,
                        "unrestrained ranks expose the authored objective directly: " + id);
                }
                helper.assertFalse(summoned.isBaby(), "every acquisition route is adult: " + id);
                helper.assertTrue(summoned.getPassengers().isEmpty(),
                    "no jockey is constructed by normalization: " + id);
                helper.assertValueEqual(
                    summoned.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
                    "finalize keeps reinforcement chance at zero: " + id);
                summoned.discard();
            }

            final Entity brewResult = ModEntities.ALL.get("emberhorn_archfiend").get().spawn(
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), EntitySpawnReason.EVENT
            );
            helper.assertTrue(brewResult instanceof InfernalHierarchyEntity,
                "the summon_abyssal_regent route still constructs the hierarchy adapter");
            final InfernalHierarchyEntity archfiend = (InfernalHierarchyEntity) brewResult;
            fixture.track(archfiend);
            archfiend.setNoAi(true);
            helper.assertValueEqual(archfiend.creatureKind(), CreatureKind.EMBERHORN_ARCHFIEND,
                "the tested brew mismatch keeps creating an Emberhorn Archfiend");
            archfiend.setTarget(authored);
            archfiend.setPersistenceRequired();
            makeDue(archfiend);
            InfernalHierarchyRuntime.tick(archfiend, helper.getLevel());
            helper.assertTrue(archfiend.getTargetUnchecked() == authored,
                "the hierarchy decision cadence never clears the authored acquisition target");
            helper.assertTrue(archfiend.isPersistenceRequired(),
                "the acquisition persistence flag survives the hierarchy runtime");

            final InfernalHierarchyEntity pursuer = createRank(helper, "demon", EntitySpawnReason.TRIGGERED);
            fixture.track(pursuer);
            final BlockPos pursuerPos = helper.absolutePos(new BlockPos(0, 1, 2));
            pursuer.snapTo(pursuerPos.getX() + 0.5D, pursuerPos.getY(), pursuerPos.getZ() + 0.5D, 0.0F, 0.0F);
            final ServerPlayer dreamer = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            pursuer.setTarget(dreamer);
            pursuer.setPersistenceRequired();
            helper.getLevel().addFreshEntity(pursuer);
            makeDue(pursuer);
            InfernalHierarchyRuntime.tick(pursuer, helper.getLevel());
            helper.assertTrue(pursuer.getTarget() == dreamer,
                "the authored pursuer objective survives the first live decision");
            helper.assertTrue(pursuer.canAttack(dreamer),
                "an authored player objective remains attackable without a truce");

            // No rank could acquire a combat target in production before the acquisition contract was
            // wired onto the live path. This Demon is AI-enabled, provoked by one real accepted hit,
            // and never touched by a runtime helper afterwards, so the target claim can only come from
            // the live tick's target priority contract.
            final InfernalHierarchyEntity liveDemon = spawnRank(fixture, "demon", new BlockPos(2, 1, 0));
            final Zombie provoker = fixture.spawn(
                EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
            );
            provoker.setNoAi(true);
            provoker.setDeltaMovement(Vec3.ZERO);
            liveDemon.invulnerableTime = 0;
            helper.assertTrue(liveDemon.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(provoker), 1.0F
            ), "the live fixture needs one real accepted hit");
            liveDemon.setPersistenceRequired();
            liveDemon.setNoAi(false);
            final java.util.concurrent.atomic.AtomicBoolean acquired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
            helper.onEachTick(() -> {
                if (acquired.get() || liveDemon.getTarget() != provoker) {
                    return;
                }
                acquired.set(true);
                try {
                    // Under repeat the batch grid is narrower than the leader scan radii, so a
                    // neighbouring instance's leader may legitimately lease this Demon first. The
                    // acquisition contract is what is pinned: the claim exists, it belongs to an
                    // engaged combat intent, and it resolved through the single hierarchy predicate.
                    helper.assertTrue(
                        InfernalHierarchyRules.engagesTarget(liveDemon.hierarchyState().intent()),
                        "the live decision holds an engaged combat intent for its acquired target");
                    helper.assertTrue(liveDemon.canAttack(provoker),
                        "the acquisition path resolves through the single hierarchy predicate");
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
            helper.runAfterDelay(120L, () -> {
                try {
                    helper.assertTrue(acquired.get(),
                        "a provoked rank must acquire its combat target on the live tick path");
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

    public static void infernalPopulationCapsAndScanBudgetsHold(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            erectIsolationShell(fixture, new BlockPos(2, 1, 2));
            // The approved stress scenario is sixty four unbound Demons, eight Archfiends, and two
            // Regents in one loaded area. Anything smaller leaves the generic candidate cap branch
            // untaken and turns every cap assertion into a vacuous upper bound.
            final List<InfernalHierarchyEntity> regents = new ArrayList<>();
            final List<InfernalHierarchyEntity> archfiends = new ArrayList<>();
            final List<InfernalHierarchyEntity> demons = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                regents.add(spawnRank(fixture, "abyssal_regent", populationSite(index)));
            }
            for (int index = 0; index < 8; index++) {
                archfiends.add(spawnRank(fixture, "emberhorn_archfiend", populationSite(2 + index)));
            }
            for (int index = 0; index < 64; index++) {
                demons.add(spawnRank(fixture, "demon", populationSite(10 + index)));
            }
            helper.assertValueEqual(demons.size(), 64, "the stress scenario stages sixty four Demons");
            helper.assertValueEqual(archfiends.size(), 8, "the stress scenario stages eight Archfiends");
            helper.assertValueEqual(regents.size(), 2, "the stress scenario stages two Regents");

            afterSpawnsAreIndexed(helper, fixture, () -> {
                populationCapStage(helper, regents, archfiends, demons);
                fixture.close();
                helper.succeed();
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void populationCapStage(
        final GameTestHelper helper,
        final List<InfernalHierarchyEntity> regents,
        final List<InfernalHierarchyEntity> archfiends,
        final List<InfernalHierarchyEntity> demons
    ) {
            final InfernalHierarchyEntity regent = regents.get(0);
            final InfernalHierarchyEntity courtArchfiend = archfiends.get(0);
            final InfernalHierarchyEntity freeArchfiend = archfiends.get(1);
            final List<InfernalHierarchyState.Member> preseed = new ArrayList<>();
            preseed.add(new InfernalHierarchyState.Member(
                courtArchfiend.getUUID(), Rank.EMBERHORN_ARCHFIEND, helper.getLevel().getGameTime() + 100L
            ));
            for (int index = 0; index < 6; index++) {
                preseed.add(new InfernalHierarchyState.Member(
                    demons.get(index).getUUID(), Rank.DEMON, helper.getLevel().getGameTime() + 100L
                ));
            }
            regent.setHierarchyState(regent.hierarchyState().withRoster(preseed, 1L));
            for (final InfernalHierarchyEntity leader : regents) {
                makeDue(leader);
                InfernalHierarchyRuntime.tick(leader, helper.getLevel());
            }
            for (final InfernalHierarchyEntity leader : archfiends) {
                makeDue(leader);
                InfernalHierarchyRuntime.tick(leader, helper.getLevel());
            }
            for (final InfernalHierarchyEntity demon : demons) {
                makeDue(demon);
                InfernalHierarchyRuntime.tick(demon, helper.getLevel());
            }
            final long now = helper.getLevel().getGameTime();

            for (final InfernalHierarchyEntity leader : regents) {
                helper.assertTrue(leader.hierarchyState().roster().size()
                        <= InfernalHierarchyRules.COURT_MEMBER_CAP, "the court cap holds under load");
                helper.assertTrue(leader.hierarchyState().roster().stream()
                        .filter(row -> row.rank() == Rank.EMBERHORN_ARCHFIEND).count()
                        <= InfernalHierarchyRules.COURT_ARCHFIEND_CAP,
                    "the one-archfiend court cap holds under load");
            }
            for (final InfernalHierarchyEntity leader : archfiends) {
                helper.assertTrue(leader.hierarchyState().roster().size()
                        <= InfernalHierarchyRules.SQUAD_MEMBER_CAP, "squad caps hold under load");
            }
            final List<UUID> courtIds = regent.hierarchyState().roster().stream()
                .map(InfernalHierarchyState.Member::id).toList();
            for (final InfernalHierarchyEntity leader : List.of(courtArchfiend, freeArchfiend)) {
                helper.assertTrue(leader.hierarchyState().roster().stream()
                        .noneMatch(row -> courtIds.contains(row.id())),
                    "no member belongs to more than one squad or court");
            }
            for (final InfernalHierarchyEntity member : demons) {
                helper.assertTrue(member.hierarchyState().membershipLeaseUntil()
                        <= now + InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS,
                    "every lease under load stays within four hundred ticks");
                helper.assertTrue(member.hierarchyState().order().isEmpty()
                        || member.hierarchyState().order().orElseThrow().expiresAt()
                            <= now + InfernalHierarchyRules.REGENT_ORDER_TICKS,
                    "every order under load stays within its issuer lifetime");
            }

            helper.assertValueEqual(regent.hierarchyState().intent(), Intent.COMMAND,
                "the live tick selects a rank intent rather than leaving the intent model unreachable");
            helper.assertTrue(regent.hierarchyState().morale() >= InfernalHierarchyRules.MORALE_RETREAT_BELOW,
                "the morale model is read by live selection rather than being write-only");

            // Two-sided bounds. The lower bound proves the staged population actually drove both the
            // retained observation cap and the generic group cap to their limits in this pass.
            helper.assertValueEqual(regent.hierarchyCounters().observationScans(), 1L,
                "one due decision runs exactly one bounded observation");
            helper.assertTrue(regent.hierarchyCounters().candidateVisits()
                    >= InfernalHierarchyRules.REGENT_RETAINED_CANDIDATES
                        + InfernalHierarchyRules.REGENT_GENERIC_CANDIDATE_CAP,
                "the staged population reaches both regent caps rather than staying under them");
            helper.assertTrue(regent.hierarchyCounters().candidateVisits()
                    <= InfernalHierarchyRules.REGENT_RETAINED_CANDIDATES
                        + InfernalHierarchyRules.REGENT_GENERIC_CANDIDATE_CAP
                        + InfernalHierarchyRules.COURT_MEMBER_CAP,
                "regent candidate work is capped regardless of population");
            for (final InfernalHierarchyEntity leader : archfiends) {
                helper.assertValueEqual(leader.hierarchyCounters().observationScans(), 1L,
                    "each archfiend runs exactly one bounded observation per due decision");
                helper.assertTrue(leader.hierarchyCounters().candidateVisits()
                        >= InfernalHierarchyRules.ARCHFIEND_RETAINED_CANDIDATES
                            + InfernalHierarchyRules.ARCHFIEND_GENERIC_CANDIDATE_CAP,
                    "the staged population reaches both archfiend caps rather than staying under them");
                helper.assertTrue(leader.hierarchyCounters().candidateVisits()
                        <= InfernalHierarchyRules.ARCHFIEND_RETAINED_CANDIDATES
                            + InfernalHierarchyRules.ARCHFIEND_GENERIC_CANDIDATE_CAP
                            + InfernalHierarchyRules.SQUAD_MEMBER_CAP,
                    "archfiend candidate work is capped regardless of population");
                helper.assertTrue(leader.hierarchyCounters().blockReads()
                        <= InfernalHierarchyRules.ARCHFIEND_ANCHOR_BLOCK_READS + 2L,
                    "anchor search work is capped regardless of population");
                helper.assertTrue(leader.hierarchyCounters().lineOfSightChecks()
                        <= InfernalHierarchyRules.ARCHFIEND_LINE_OF_SIGHT_CHECKS,
                    "line of sight work stays inside the per-observation budget");
            }
            for (final InfernalHierarchyEntity demon : demons) {
                helper.assertValueEqual(demon.hierarchyCounters().observationScans(), 1L,
                    "each demon runs exactly one bounded observation per due decision");
                helper.assertValueEqual(demon.hierarchyCounters().candidateVisits(),
                    (long) InfernalHierarchyRules.DEMON_RETAINED_CANDIDATES,
                    "the staged population drives demon observation exactly to its twelve candidate cap");
                helper.assertValueEqual(demon.hierarchyCounters().navigationRequests(), 0L,
                    "capped scans never issue navigation work");
                helper.assertTrue(demon.hierarchyCounters().lineOfSightChecks()
                        <= InfernalHierarchyRules.DEMON_LINE_OF_SIGHT_CHECKS,
                    "line of sight work stays inside the per-observation budget");
            }

            final long regentVisits = regent.hierarchyCounters().candidateVisits();
            final long regentScans = regent.hierarchyCounters().observationScans();
            InfernalHierarchyRuntime.tick(regent, helper.getLevel());
            helper.assertValueEqual(regent.hierarchyCounters().candidateVisits(), regentVisits,
                "a decision inside the twenty-tick cadence is a strict no-op");
            helper.assertValueEqual(regent.hierarchyCounters().observationScans(), regentScans,
                "no observation runs faster than its cadence");
            helper.assertTrue(regent.hierarchyState().cadence().nextGroupRefreshAt() > now,
                "the group refresh cadence is scheduled rather than continuous");
    }

    /**
     * A deterministic staging grid clamped inside the structure. Positions repeat, which is what the
     * scenario wants: a dense loaded area that saturates every candidate cap.
     */
    private static BlockPos populationSite(final int index) {
        return new BlockPos(Math.floorMod(index, 4), 1, Math.floorMod(index / 4, 4));
    }

    private static int countDemonsNear(final GameTestHelper helper, final InfernalHierarchyEntity regent) {
        return helper.getLevel().getEntitiesOfClass(
            InfernalHierarchyEntity.class,
            regent.getBoundingBox().inflate(4.0D),
            candidate -> candidate != regent && candidate.hierarchyRank() == Rank.DEMON
        ).size();
    }

    private static CompoundTag save(final GameTestHelper helper, final InfernalHierarchyEntity entity) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        entity.saveWithoutId(output);
        return output.buildResult().copy();
    }

    private static InfernalHierarchyEntity createRank(
        final GameTestHelper helper,
        final String id,
        final EntitySpawnReason reason
    ) {
        final Entity created = ModEntities.ALL.get(id).get().create(helper.getLevel(), reason);
        if (!(created instanceof InfernalHierarchyEntity entity)) {
            throw new IllegalStateException("The registered id must create the hierarchy adapter: " + id);
        }
        return entity;
    }

    private static void makeDue(final InfernalHierarchyEntity entity) {
        entity.setHierarchyState(entity.hierarchyState()
            .withCadence(InfernalHierarchyState.Cadence.due()));
    }

    /**
     * Freezes the live decision cadence behind a bounded future sentinel so a direct characterization of
     * one helper cannot race the same entity's natural server tick. The sentinel stays well inside the
     * approved twenty thousand tick horizon and is never Long.MAX_VALUE.
     */
    private static void parkDecisions(final InfernalHierarchyEntity entity, final GameTestHelper helper) {
        final long parked = helper.getLevel().getGameTime() + 10_000L;
        entity.setHierarchyState(entity.hierarchyState().withCadence(
            new InfernalHierarchyState.Cadence(parked, parked, parked, parked, parked, parked)
        ));
    }

    private static InfernalHierarchyEntity spawnRank(
        final FixtureScope fixture,
        final String id,
        final BlockPos position
    ) {
        final InfernalHierarchyEntity entity = (InfernalHierarchyEntity) fixture.spawn(
            ModEntities.ALL.get(id).get(), position, EntitySpawnReason.EVENT
        );
        entity.setNoAi(true);
        entity.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        return entity;
    }

    private static void openStandingSite(final FixtureScope fixture, final BlockPos absolute) {
        fixture.placeBlock(absolute.below(), Blocks.STONE.defaultBlockState());
        fixture.placeBlock(absolute, Blocks.AIR.defaultBlockState());
        fixture.placeBlock(absolute.above(), Blocks.AIR.defaultBlockState());
    }

    private static void erectIsolationShell(final FixtureScope fixture, final BlockPos centerRelative) {
        final GameTestHelper helper = fixture.helper;
        final BlockPos center = helper.absolutePos(centerRelative);
        final int radius = 4;
        final int height = 4;
        final List<BlockPos> placed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy = 0; dy <= height; dy++) {
                    final BlockPos pos = new BlockPos(
                        center.getX() + dx, center.getY() + dy, center.getZ() + dz
                    );
                    if (helper.getLevel().getBlockState(pos).isAir()) {
                        helper.getLevel().setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                        placed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, Blocks.AIR.defaultBlockState(), 3
        )));
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

        private void placeBlock(final BlockPos position, final net.minecraft.world.level.block.state.BlockState state) {
            final net.minecraft.world.level.block.state.BlockState previous =
                helper.getLevel().getBlockState(position);
            helper.getLevel().setBlock(position, state, 3);
            onClose(() -> helper.getLevel().setBlock(position, previous, 3));
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
            if (closed) return;
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            cleanupActions.forEach(Runnable::run);
            cleanupActions.clear();
        }
    }
}
