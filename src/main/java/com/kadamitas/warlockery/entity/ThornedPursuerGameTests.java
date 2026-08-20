package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.block.WickerBundleBlock;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import com.kadamitas.warlockery.transformation.SupernaturalAdvancement;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class ThornedPursuerGameTests {
    private static final int RITE_CELL_MAX = 14;
    private static final BlockPos RITE_CENTER = new BlockPos(7, 2, 7);
    private static final BlockPos RITE_ALTAR = new BlockPos(5, 8, 6);
    private static final int MAX_LOCAL_RESULTS = 256;
    private ThornedPursuerGameTests() {}

    public static void thornedPursuerBaysBeforeItCommitsToACourse(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            owner.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
            var result = owner.getItemInHand(InteractionHand.MAIN_HAND).getItem()
                .use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "the real Horn of the Hunt use succeeds");
            ThornedPursuerEntity pursuer = fixture.findHornPursuer(owner);
            helper.assertValueEqual(pursuer.getPersistentData().getStringOr(
                "WarlockerySummoningOwner", ""), owner.getStringUUID(),
                "the real horn stamps its summoning owner read-only hint");

            Villager equalDistance = fixture.spawnVillager(reflectAcross(pursuer, owner));
            helper.assertValueEqual(pursuer.distanceToSqr(equalDistance), pursuer.distanceToSqr(owner),
                "the ordinary candidate and owner hint begin at equal squared distance");
            pursuer.setPursuerState(new ThornedPursuerState(1, 0, 0, 200));
            equalDistance.setNoAi(true);
            helper.runAfterDelay(180, () -> fixture.step(() -> {
                pursuer.setPursuerState(ThornedPursuerState.defaults());
                pursuer.tickCount = Math.floorMod(-pursuer.getId(), ThornedPursuerRules.QUARRY_SCAN_CADENCE) - 1;
                float ownerHealth = owner.getHealth();
                long paths = pursuer.pursuerCounters().pathRequests;
                helper.runAfterDelay(1, () -> fixture.step(() -> {
                helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.BAY,
                    "the first sight-gated observation opens BAY");
                helper.assertValueEqual(ThornedPursuerRules.selectQuarry(List.of(
                    new ThornedPursuerRules.QuarryCandidate(equalDistance.getUUID(),
                        pursuer.distanceToSqr(equalDistance), false),
                    new ThornedPursuerRules.QuarryCandidate(owner.getUUID(),
                        pursuer.distanceToSqr(owner), true))).orElseThrow(), owner.getUUID(),
                    "the real horn UUID wins only its equal-distance runtime seam");
                helper.assertTrue(pursuer.pursuerCounters().quarryRawVisits <= ThornedPursuerRules.MAX_SCAN_VISITS,
                    "acquisition visits at most eight raw entities");
                helper.assertTrue(pursuer.pursuerCounters().quarrySightRays <= ThornedPursuerRules.MAX_SCAN_SIGHT_RAYS,
                    "acquisition casts at most two sight rays");
                assertBayIsNonCommitting(helper, pursuer, ownerHealth, paths);
                helper.runAfterDelay(39, () -> fixture.step(() -> {
                    assertBayIsNonCommitting(helper, pursuer, ownerHealth, paths);
                    helper.runAfterDelay(1, () -> fixture.step(() -> {
                        helper.assertTrue(pursuer.pursuerRuntime().phase() != ThornedPursuerRules.Phase.BAY,
                            "BAY ends after exactly forty loaded ticks");
                        helper.assertValueEqual(pursuer.pursuerCounters().bayNavigationWrites, 0L,
                            "the complete announcement records zero navigation writes");
                        pursuer.discard();
                        equalDistance.discard();
                        owner.setGameMode(GameType.CREATIVE);
                        runHintExpiry(helper, fixture, owner);
                    }));
                }));
                }));
            }));
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void runHintExpiry(GameTestHelper helper, Fixture fixture, ServerPlayer owner) {
        owner.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
        owner.getItemInHand(InteractionHand.MAIN_HAND).getItem()
            .use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        ThornedPursuerEntity expirySubject = fixture.findHornPursuer(owner);
        expirySubject.setPursuerState(new ThornedPursuerState(1, 0, 0, 200));
        helper.runAfterDelay(ThornedPursuerRules.OWNER_HINT_TICKS, () -> fixture.step(() -> {
            helper.assertValueEqual(expirySubject.pursuerCounters().hintExpiries, 1L,
                "the real horn hint expires once after exactly 1200 loaded ticks");
            helper.assertValueEqual(ThornedPursuerRules.selectQuarry(List.of(
                new ThornedPursuerRules.QuarryCandidate(owner.getUUID(), 4.0D, false),
                new ThornedPursuerRules.QuarryCandidate(new java.util.UUID(0L, 0L), 4.0D, false)))
                .orElseThrow(), new java.util.UUID(0L, 0L),
                "after expiry selection has only distance and UUID ordering");
            expirySubject.discard();
            runOcclusion(helper, fixture, owner);
        }));
    }

    private static void runOcclusion(GameTestHelper helper, Fixture fixture, ServerPlayer owner) {
        ThornedPursuerEntity pursuer = fixture.spawnPursuer(new BlockPos(1, 1, 1));
        Villager first = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
        fixture.place(new BlockPos(2, 1, 1), Blocks.BARRIER);
        fixture.place(new BlockPos(2, 2, 1), Blocks.BARRIER);
        forceScan(pursuer);
        helper.runAfterDelay(1, () -> fixture.step(() -> {
            helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ANCHORED,
                "a locally occluded candidate is never acquired");
            helper.assertValueEqual(pursuer.pursuerCounters().quarryAcquisitions, 0L,
                "occlusion mints no acquisition");
            Villager second = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 2)));
            Villager visibleThird = fixture.spawnVillager(helper.absolutePos(new BlockPos(1, 1, 4)));
            fixture.place(new BlockPos(2, 1, 2), Blocks.BARRIER);
            fixture.place(new BlockPos(2, 2, 2), Blocks.BARRIER);
            pursuer.resetPursuerCounters();
            forceScan(pursuer);
            helper.runAfterDelay(1, () -> fixture.step(() -> {
                helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ANCHORED,
                    "the visible third candidate is not reached after two occluded rays");
                helper.assertValueEqual(pursuer.pursuerCounters().quarrySightRays, 2L,
                    "the scan stops after exactly two sight rays and never widens");
                helper.assertValueEqual(pursuer.pursuerCounters().quarryAcquisitions, 0L,
                    "the no-widen scan acquires no third candidate");
                first.discard(); second.discard(); visibleThird.discard();
                fixture.clearPlaced();
                runIneligiblePopulation(helper, fixture, owner, pursuer);
            }));
        }));
    }

    private static void runIneligiblePopulation(GameTestHelper helper, Fixture fixture,
                                                 ServerPlayer owner, ThornedPursuerEntity pursuer) {
        Villager trading = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
        trading.setNoAi(true);
        trading.setTradingPlayer(owner);
        var golem = fixture.spawn(EntityTypes.IRON_GOLEM, new BlockPos(14, 1, 14));
        var turtle = fixture.spawn(EntityTypes.TURTLE, new BlockPos(13, 1, 14));
        golem.setNoAi(true);
        turtle.setNoAi(true);
        fixture.spawnPursuer(new BlockPos(2, 1, 3));
        forceScan(pursuer);
        ThornedPursuerRuntime.tick(pursuer, helper.getLevel());
        helper.assertValueEqual(pursuer.pursuerCounters().quarryAcquisitions, 0L,
            "a real actively trading villager is excluded at the acquisition seam");
        trading.discard();
        owner.discard();
        BlockPos isolatedCenter = helper.relativePos(pursuer.blockPosition());
        for (int y = 0; y <= 2; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) == 1)
                fixture.place(isolatedCenter.offset(x, y, z), Blocks.BARRIER);
        }
        pursuer.setPursuerState(ThornedPursuerState.defaults());
        pursuer.pursuerRuntime().reset(pursuer);
        pursuer.resetPursuerCounters();
        pursuer.setNoAi(true);
        helper.runAfterDelay(2_000, () -> fixture.step(() -> {
            helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ANCHORED,
                "the dormant isolation probe stays anchored for 2000 loaded world ticks");
            helper.assertValueEqual(pursuer.pursuerCounters().quarryAcquisitions, 0L,
                "neighboring fixture entities cannot mint a quarry into a dormant isolation probe");
            helper.assertValueEqual(pursuer.pursuerCounters().pathRequests, 0L,
                "ineligible relations mint no paths");
            helper.assertValueEqual(pursuer.pursuerCounters().doorBreaks, 0L, "no village door breaks");
            helper.assertValueEqual(pursuer.pursuerCounters().turtleEggBreaks, 0L, "no turtle egg breaks");
            helper.assertValueEqual(pursuer.pursuerCounters().piglinAlerts, 0L, "no piglin alerts");
            fixture.close();
            helper.succeed();
        }));
    }

    private static void forceScan(ThornedPursuerEntity pursuer) {
        pursuer.setPursuerState(ThornedPursuerState.defaults());
        pursuer.tickCount = Math.floorMod(-pursuer.getId(), ThornedPursuerRules.QUARRY_SCAN_CADENCE) - 1;
    }

    private static net.minecraft.world.phys.Vec3 reflectAcross(ThornedPursuerEntity pursuer, ServerPlayer owner) {
        return new net.minecraft.world.phys.Vec3(2.0D * pursuer.getX() - owner.getX(),
            2.0D * pursuer.getY() - owner.getY(), 2.0D * pursuer.getZ() - owner.getZ());
    }

    private static void assertBayIsNonCommitting(GameTestHelper helper, ThornedPursuerEntity pursuer,
                                                  float ownerHealth, long paths) {
        helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.BAY,
            "BAY remains in its exact telegraph window");
        helper.assertValueEqual(pursuer.pursuerCounters().pathRequests, paths, "BAY writes no paths");
        helper.assertTrue(pursuer.getTarget() == null, "BAY writes no Mob target");
        helper.assertValueEqual(pursuer.pursuerCounters().pressAttempts, 0L, "BAY deals no attack damage");
        helper.assertValueEqual(pursuer.pursuerCounters().holdCommits, 0L, "BAY applies no hold");
        helper.assertValueEqual(pursuer.operationalTargetGoalCount(), 0, "target selector stays empty");
        helper.assertTrue(pursuer.operationalGoalNames().stream().allMatch(name -> name.contains("Look")),
            "the dedicated goal selector is LOOK-only");
    }

    public static void thornedPursuerCoursesByTrailAndNeverTeleports(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            helper.runAfterDelay(180, () -> fixture.step(() -> {
                ServerPlayer quarry = fixture.connectedPlayer(new BlockPos(1, 1, 1));
                quarry.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
                quarry.getItemInHand(InteractionHand.MAIN_HAND).getItem()
                    .use(helper.getLevel(), quarry, InteractionHand.MAIN_HAND);
                ThornedPursuerEntity pursuer = fixture.findHornPursuer(quarry);
                var start = pursuer.position();
                pursuer.setPursuerState(new ThornedPursuerState(1,
                    ThornedPursuerRules.SNARE_COOLDOWN, 0, 0));
                ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
                BlockPos visibleCoursePoint = helper.absolutePos(new BlockPos(3, 1, 1));
                quarry.setPos(visibleCoursePoint.getX() + 0.5D, visibleCoursePoint.getY(),
                    visibleCoursePoint.getZ() + 0.5D);
                helper.runAfterDelay(1, () -> fixture.step(() -> {
                    helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.BAY,
                        "a due cooldown opens the same episode independently of world time; scans="
                            + pursuer.pursuerCounters().quarryScans + ", visits="
                            + pursuer.pursuerCounters().quarryRawVisits + ", rays="
                            + pursuer.pursuerCounters().quarrySightRays);
                    helper.runAfterDelay(40, () -> fixture.step(() -> {
                        helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.COURSE,
                            "the exact BAY boundary enters COURSE");
                        helper.assertValueEqual(pursuer.pursuerCounters().courseModifierApplications, 1L,
                            "COURSE applies its speed modifier exactly once");
                        helper.assertTrue(pursuer.position().distanceTo(start) < 16.0D,
                            "course movement is ordinary navigation, never teleportation");
                        quarry.snapTo(quarry.getX() + 2.0D, quarry.getY(), quarry.getZ(), 0.0F, 0.0F);
                        final long trailWritesBeforeSeam = pursuer.pursuerCounters().trailWrites;
                        final long trailExpiriesBeforeSeam = pursuer.pursuerCounters().trailExpiries;
                        ThornedPursuerRuntime.seedTrailPoint(pursuer, quarry.blockPosition());
                        helper.assertValueEqual(pursuer.pursuerCounters().trailWrites - trailWritesBeforeSeam, 1L,
                            "the runtime seam installs one cadence-representative stamped point");
                        for (int loadedTick = 0; loadedTick < ThornedPursuerRules.TRAIL_EXPIRY; loadedTick++)
                            ThornedPursuerRuntime.ageTrailLoadedTick(pursuer);
                        helper.assertValueEqual(pursuer.pursuerCounters().trailExpiries - trailExpiriesBeforeSeam, 1L,
                            "the runtime seam expires the stamped point at exactly 200 loaded ticks");
                        fixture.place(new BlockPos(4, 1, 1), Blocks.BARRIER);
                        fixture.place(new BlockPos(4, 2, 1), Blocks.BARRIER);
                        helper.runAfterDelay(220, () -> fixture.step(() -> {
                                    helper.assertTrue(pursuer.pursuerCounters().trailExpiries > 0,
                                        "trail entries expire after their bounded loaded lifetime");
                                    helper.assertTrue(pursuer.pursuerRuntime().phase() == ThornedPursuerRules.Phase.RECOVER
                                        || pursuer.pursuerRuntime().phase() == ThornedPursuerRules.Phase.ANCHORED,
                                        "empty expired trail disengages to recovery");
                                    helper.assertValueEqual(pursuer.pursuerCounters().courseModifierApplications,
                                        pursuer.pursuerCounters().courseModifierRemovals,
                                        "every COURSE exit removes the transient speed modifier");
                                    helper.assertValueEqual(pursuer.pursuerCounters().teleports, 0L,
                                        "the complete fixture creates zero teleports");
                                    long writesBeforeRemoval = pursuer.pursuerCounters().trailWrites;
                                    quarry.discard();
                                    helper.runAfterDelay(25, () -> fixture.step(() -> {
                                        helper.assertValueEqual(pursuer.pursuerCounters().trailWrites,
                                            writesBeforeRemoval,
                                            "removed quarry mints no synthetic trail point");
                                        fixture.close(); helper.succeed();
                                    }));
                        }));
                    }));
                }));
            }));
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void thornedPursuerSnaresOnceAndPressesOnCadence(GameTestHelper helper) {
        runIsolated(helper, (fixture, pursuer) -> {
            Villager quarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
            quarry.setNoAi(true);
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            ThornedPursuerRuntime.enterCourse(pursuer);
            ThornedPursuerRuntime.enterSet(pursuer);
            float initialHealth = quarry.getHealth();
            helper.runAfterDelay(19, () -> fixture.step(() -> {
                helper.assertTrue(pursuer.pursuerRuntime().phase() == ThornedPursuerRules.Phase.SET,
                    "closing range enters the hold telegraph before ordinary press; phase="
                        + pursuer.pursuerRuntime().phase() + ", rays="
                        + pursuer.pursuerCounters().sightRays + ", paths="
                        + pursuer.pursuerCounters().pathRequests);
                helper.assertValueEqual(pursuer.pursuerCounters().pressAccepted, 0L,
                    "the hold telegraph accepts no damage through the pursuer controller");
                helper.assertTrue(ThornedPursuerRules.holdMayCommit(20, true, true, 9.0D),
                    "the exact 20-tick, distance-nine, sighted boundary permits commit");
                ThornedPursuerRuntime.commitHold(pursuer, helper.getLevel(), quarry);
                helper.runAfterDelay(1, () -> fixture.step(() -> {
                    helper.assertValueEqual(pursuer.pursuerCounters().holdCommits, 1L,
                        "the episode commits exactly one hold");
                    helper.assertValueEqual(pursuer.pursuerCounters().slownessApplications, 1L,
                        "the hold applies exactly one effect to exactly one subject");
                    helper.assertTrue(quarry.hasEffect(MobEffects.SLOWNESS),
                        "the committed hold applies vanilla slowness");
                    helper.assertValueEqual(quarry.getEffect(MobEffects.SLOWNESS).getAmplifier(), 0,
                        "the hold uses amplifier zero");
                    helper.assertValueEqual(quarry.getActiveEffects().size(), 1,
                        "the hold adds no second effect");
                    helper.assertTrue(pursuer.pursuerCounters().pressAttempts <= 2,
                        "press attempts remain on the 20-tick cadence");
                    fixture.close(); helper.succeed();
                }));
            }));
        });
    }

    public static void thornedPursuerEscortIsOwnedCappedAndReleased(GameTestHelper helper) {
        runIsolated(helper, (fixture, pursuer) -> {
            Villager quarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
            quarry.setNoAi(true);
            net.minecraft.world.entity.animal.wolf.Wolf untagged = fixture.track(
                EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.EVENT));
            BlockPos controlPos = helper.absolutePos(new BlockPos(1, 1, 3));
            untagged.snapTo(controlPos.getX() + 0.5D, controlPos.getY(), controlPos.getZ() + 0.5D, 0, 0);
            helper.getLevel().addFreshEntity(untagged);
            ThornedPursuerRuntime.clearBudgetsForTest();
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            helper.assertValueEqual(pursuer.pursuerCounters().escortEvaluations, 1L,
                "BAY evaluates escort creation exactly once");
            helper.assertValueEqual(pursuer.pursuerCounters().escortCreations, 2L,
                "two open candidate positions create exactly the capped pair");
            helper.assertValueEqual(pursuer.pursuerRuntime().escortIds().size(), 2,
                "population accounting uses exactly the two retained UUIDs");
            for (java.util.UUID id : pursuer.pursuerRuntime().escortIds()) {
                Entity entity = helper.getLevel().getEntity(id);
                helper.assertTrue(entity instanceof net.minecraft.world.entity.animal.wolf.Wolf,
                    "every retained escort UUID resolves to one loaded wolf");
                var wolf = (net.minecraft.world.entity.animal.wolf.Wolf) entity;
                helper.assertValueEqual(wolf.getPersistentData().getStringOr("WarlockeryHuntEscort", ""),
                    pursuer.getUUID().toString(), "every escort carries only its pursuer owner tag");
                helper.assertTrue(!wolf.isPersistenceRequired(), "escorts never latch persistence");
                helper.assertTrue(wolf.getTarget() == quarry, "each escort receives the quarry target once");
            }
            Villager replacement = fixture.spawnVillager(helper.absolutePos(new BlockPos(7, 1, 7)));
            replacement.setNoAi(true);
            ThornedPursuerRuntime.afterAcceptedDamage(pursuer, helper.getLevel(), replacement, 1.0F, 0);
            helper.assertValueEqual(ThornedPursuerRuntime.quarryIdForTest(pursuer), replacement.getUUID(),
                "a fresh legal attacker replaces the active quarry");
            helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.BAY,
                "replacement preserves the existing episode phase");
            for (java.util.UUID id : pursuer.pursuerRuntime().escortIds()) {
                var wolf = (net.minecraft.world.entity.animal.wolf.Wolf) helper.getLevel().getEntity(id);
                helper.assertTrue(wolf.getTarget() == quarry,
                    "existing escorts keep the one vanilla target assigned at creation");
            }
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            helper.assertValueEqual(pursuer.pursuerCounters().escortCreations, 2L,
                "the 1200-tick cooldown creates no third escort");
            ThornedPursuerRuntime.breakEpisode(pursuer, helper.getLevel(), ThornedPursuerRules.BreakReason.CANCELLED);
            helper.assertValueEqual(pursuer.pursuerCounters().escortReleases, 2L,
                "BREAK releases both retained tagged escorts");
            helper.assertTrue(untagged.isAlive() && !untagged.isRemoved(),
                "a pre-existing untagged wolf is never adopted or discarded");
            helper.assertTrue(untagged.getTarget() == null, "the untagged wolf is never retargeted");
            fixture.close(); helper.succeed();
        });
    }

    public static void thornedPursuerBreaksRecoversAndCancelsDeterministically(GameTestHelper helper) {
        runIsolated(helper, (fixture, pursuer) -> {
            var matrix = ThornedPursuerRuntime.exerciseCancellationRecoverySeam(pursuer, helper.getLevel());
            helper.assertTrue(matrix.typedReleases(), "dead, removed, illegal, unloaded and dimension releases stay typed");
            helper.assertTrue(matrix.typedScheduledBreaks(), "retention, trail, leash, budget and route breaks stay typed");
            helper.assertTrue(matrix.recoveryTimeout(), "RECOVER re-anchors at the exact 400-loaded-tick timeout");
            helper.assertTrue(matrix.strictFailureSequence() && matrix.boundedBackoff()
                    && matrix.thirdFailureBackoff(),
                "the third strict COURSE/RECOVER route failure cleans up and defers retry for 100 loaded ticks");
            helper.assertTrue(matrix.bayAndSetBudgetArbitration(),
                "the 1200-tick episode budget cancels BAY and quota-denied SET without indefinite deferral");
            exerciseLiveHazardsBedLootAndProgression(helper, fixture, pursuer);
            Villager quarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
            quarry.setNoAi(true);
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            ThornedPursuerRuntime.enterCourse(pursuer);
            Villager freshAttacker = fixture.spawnVillager(helper.absolutePos(new BlockPos(8, 1, 1)));
            freshAttacker.setNoAi(true);
            float healthBeforeAbsorptionHit = pursuer.getHealth();
            long attributionsBeforeAbsorptionHit = pursuer.pursuerCounters().attackerAttributions;
            float absorptionOnlyLoss = ThornedPursuerRules.acceptedEffectiveLoss(
                healthBeforeAbsorptionHit, 4.0F, healthBeforeAbsorptionHit, 3.0F);
            ThornedPursuerRuntime.afterAcceptedDamage(pursuer, helper.getLevel(), freshAttacker,
                absorptionOnlyLoss, 0);
            helper.assertValueEqual(pursuer.getHealth(), healthBeforeAbsorptionHit,
                "the faithful absorption-only accepted-loss seam does not lower health");
            helper.assertValueEqual(pursuer.pursuerCounters().attackerAttributions,
                attributionsBeforeAbsorptionHit + 1,
                "positive absorption-only accepted loss mints attribution");
            helper.assertValueEqual(ThornedPursuerRuntime.quarryIdForTest(pursuer), freshAttacker.getUUID(),
                "a fresh legal attacker replaces the current quarry");
            helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.COURSE,
                "fresh attribution preserves the active episode phase");
            Villager firstContact = fixture.spawnVillager(helper.absolutePos(new BlockPos(2, 1, 2)));
            Villager secondContact = fixture.spawnVillager(helper.absolutePos(new BlockPos(2, 1, 3)));
            firstContact.setNoAi(true);
            secondContact.setNoAi(true);
            float firstHealth = firstContact.getHealth();
            float secondHealth = secondContact.getHealth();
            ThornedPursuerRuntime.afterAcceptedDamage(pursuer, helper.getLevel(), firstContact, 1.0F, 0);
            ThornedPursuerRuntime.afterAcceptedDamage(pursuer, helper.getLevel(), secondContact, 1.0F, 0);
            helper.assertTrue(firstContact.getHealth() < firstHealth && secondContact.getHealth() < secondHealth,
                "two contact attackers receive independent retaliation despite the first attacker's cooldown");
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            ThornedPursuerRuntime.enterCourse(pursuer);
            helper.assertTrue(pursuer.isPreventingPlayerRest(helper.getLevel(), null),
                "an active course prevents rest");
            helper.assertValueEqual(pursuer.pursuerCounters().courseModifierApplications, 1L,
                "the active episode begins with its one transient course modifier");
            long escorts = pursuer.pursuerCounters().escortCreations;
            helper.runAfterDelay(2, () -> fixture.step(() -> {
                final long hazardReads = pursuer.pursuerCounters().hazardReads;
                pursuer.igniteForSeconds(2.0F);
                helper.runAfterDelay(1, () -> fixture.step(() -> {
                    helper.assertValueEqual(pursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ESCAPE,
                        "fire preempts the active episode");
                    helper.assertTrue(pursuer.pursuerCounters().episodeCancels > 0,
                        "hazard cancellation runs full teardown before escape");
                    helper.assertTrue(pursuer.pursuerCounters().hazardReads - hazardReads <= 18,
                        "this footprint observation stays within its read cap");
                    helper.assertValueEqual(pursuer.pursuerCounters().courseModifierRemovals, 1L,
                        "hazard teardown removes the course modifier before escape navigation");
                    helper.assertValueEqual(pursuer.pursuerCounters().escortReleases, escorts,
                        "hazard teardown releases every retained escort before escape navigation");
                    helper.assertTrue(ThornedPursuerRuntime.authorityClearedForTest(pursuer),
                        "hazard teardown clears quarry dimension, escort evaluation, sight and retaliation authority");
                    helper.assertValueEqual(pursuer.pursuerCounters().holdCommits, 0L,
                        "preemption leaves no delayed hold");
                    helper.assertValueEqual(pursuer.pursuerCounters().teleports, 0L,
                        "hazard escape never teleports");
                    helper.assertTrue(!pursuer.isPreventingPlayerRest(helper.getLevel(), null),
                        "ESCAPE and recovery never prevent rest");
                    helper.runAfterDelay(25, () -> fixture.step(() -> {
                        helper.assertValueEqual(pursuer.pursuerCounters().holdCommits, 0L,
                            "the cancelled hold cannot replay later");
                        fixture.close(); helper.succeed();
                    }));
                }));
            }));
        });
    }

    public static void thornedPursuerSaveReloadAndZombieLifecycleAreReplaced(GameTestHelper helper) {
        final Difficulty originalDifficulty = helper.getLevel().getDifficulty();
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        try {
        runIsolated(helper, (fixture, pursuer) -> {
            fixture.onClose(() -> helper.getLevel().getServer().setDifficulty(originalDifficulty, true));
            exerciseFixtureSixMatrix(helper, fixture, pursuer);
            helper.assertTrue(!net.minecraft.world.entity.monster.zombie.Zombie.class.isAssignableFrom(pursuer.getClass()),
                "the dedicated entity has no Zombie lifecycle");
            helper.assertValueEqual(pursuer.pursuerState(), ThornedPursuerState.defaults(),
                "fresh durable state has exactly due cooldowns");
            Villager quarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
            ThornedPursuerRuntime.openEpisode(pursuer, helper.getLevel(), quarry);
            ThornedPursuerRuntime.enterCourse(pursuer);
            helper.assertTrue(ThornedPursuerRuntime.courseModifierPresent(pursuer),
                "the saved source is genuinely mid-COURSE with its transient modifier");
            pursuer.setPursuerState(new ThornedPursuerState(1, 123, 456, 78));
            TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            pursuer.saveWithoutId(output);
            Entity replacement = pursuer.getType().create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(replacement instanceof ThornedPursuerEntity, "reload retains dedicated identity");
            ThornedPursuerEntity loaded = (ThornedPursuerEntity) replacement;
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), output.buildResult().copy()));
            fixture.entities.add(loaded);
            helper.assertValueEqual(loaded.pursuerState(), new ThornedPursuerState(1, 123, 456, 78),
                "only three bounded loaded-tick cooldowns survive reload");
            helper.assertTrue(ThornedPursuerRuntime.authorityClearedForTest(loaded),
                "reload clears every transient quarry, sight, escort and retaliation authority");
            helper.assertValueEqual(loaded.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ANCHORED,
                "reload normalizes every transient phase to ANCHORED");
            helper.assertTrue(loaded.pursuerRuntime().escortIds().isEmpty(),
                "reload retains no transient escort UUIDs");
            helper.assertTrue(!ThornedPursuerRuntime.courseModifierPresent(loaded),
                "reload retains no transient course modifier");
            helper.assertTrue(loaded.getTarget() == null,
                "reload retains no transient Mob target");
            helper.assertValueEqual(loaded.operationalTargetGoalCount(), 0,
                "the reloaded lifecycle still has no Zombie target goals");
            beginRealThornedPursuerRite(helper, fixture);
        });
        } catch (Throwable failure) {
            helper.getLevel().getServer().setDifficulty(originalDifficulty, true);
            throw failure;
        }
    }

    private static void beginRealThornedPursuerRite(GameTestHelper helper, Fixture fixture) {
        final BlockPos relativeCenter = RITE_CENTER;
        final BlockPos center = helper.absolutePos(relativeCenter);
        final Identifier rite = Identifier.fromNamespaceAndPath("warlockery", "summon_thorned_pursuer");
        final var definition = RitualManager.INSTANCE.byId(rite).orElseThrow().definition();
        fixture.placeRite(relativeCenter.below(), Blocks.STONE);
        fixture.placeRite(relativeCenter, ModBlocks.ALL.get("circle").get());
        ChalkCircleLayout.rings(definition.glyphs()).forEach(ring -> ring.size().offsets().forEach(offset -> {
            final BlockPos relative = relativeCenter.offset(offset);
            fixture.placeRite(relative.below(), Blocks.STONE);
            fixture.placeRite(relative, ModBlocks.ALL.get(ring.glyph()).get());
        }));
        for (BlockPos bundle : HuntsmanSummoningStructure.positions(relativeCenter)) {
            fixture.placeRite(bundle.below(), Blocks.STONE);
            fixture.placeRite(bundle, ModBlocks.ALL.get("wickerbundle").get().defaultBlockState()
                .setValue(WickerBundleBlock.BLOODIED, true));
        }
        final BlockPos relativeAltar = RITE_ALTAR;
        BlockPos.betweenClosedStream(relativeAltar, relativeAltar.offset(2, 0, 1))
            .forEach(position -> fixture.placeRite(position, ModBlocks.ALTAR.get()));
        BlockPos.betweenClosedStream(relativeAltar.below(), relativeAltar.offset(2, -1, 0))
            .forEach(position -> fixture.placeRite(position, ModBlocks.ALL.get("demonheart").get()));
        final ServerPlayer caster = fixture.connectedPlayer(relativeCenter.offset(0, 1, 0));
        SupernaturalAdvancement.beginWerewolf(caster);
        SupernaturalProgression.setLevel(caster, SupernaturalProgression.Path.WEREWOLF, 2);
        final int preservedLevel = SupernaturalProgression.level(caster, SupernaturalProgression.Path.WEREWOLF);
        final var clock = helper.getLevel().dimensionType().defaultClock().orElseThrow();
        final long previousDayTime = helper.getLevel().clockManager().getTotalTicks(clock);
        fixture.onClose(() -> helper.getLevel().clockManager().setTotalTicks(clock, previousDayTime));
        helper.getLevel().clockManager().setTotalTicks(clock, 18_000L);

        helper.runAfterDelay(165L, () -> fixture.step(() -> {
            final AltarBlockEntity altar = helper.getLevel().getBlockEntity(
                helper.absolutePos(relativeAltar)) instanceof AltarBlockEntity found ? found : null;
            helper.assertTrue(altar != null && altar.isMultiblockValid(),
                "the real six-block altar is powered and structurally valid after normal warmup");
            final int ambientPower = altar.getPower();
            helper.assertTrue(altar.getCapacity() >= 4_800,
                "three real power-heart blocks support the exact 4800 charge");
            final int requestedPower = Math.max(0, 4_800 - ambientPower);
            final int acceptedPower = altar.receivePower(requestedPower);
            helper.assertValueEqual(acceptedPower, requestedPower,
                "the altar accepts exactly the remaining funded charge after live ambient gain");
            final int fundedPower = ambientPower + acceptedPower;
            helper.assertValueEqual(fundedPower, 4_800,
                "the powered altar exposes the exact 4800 required by the rite");
            final ItemEntity heart = fixture.track(new ItemEntity(helper.getLevel(), center.getX() + 0.35D,
                center.getY() + 1.0D, center.getZ() + 0.5D,
                new ItemStack(ModItems.ALL.get("demonheart").get())));
            final ItemEntity stone = fixture.track(new ItemEntity(helper.getLevel(), center.getX() + 0.65D,
                center.getY() + 1.0D, center.getZ() + 0.5D,
                new ItemStack(ModItems.ALL.get("ingredient_attuned_stone").get())));
            helper.getLevel().addFreshEntity(heart);
            helper.getLevel().addFreshEntity(stone);
            final AABB resultBounds = fixture.riteCellBounds();
            final List<ThornedPursuerEntity> beforeResults = new ArrayList<>(MAX_LOCAL_RESULTS);
            helper.getLevel().getEntities(EntityTypeTest.forClass(ThornedPursuerEntity.class), resultBounds,
                entity -> true, beforeResults, MAX_LOCAL_RESULTS);
            final java.util.Set<java.util.UUID> existingPursuers = beforeResults.stream()
                .map(ThornedPursuerEntity::getUUID)
                .collect(java.util.stream.Collectors.toSet());
            helper.assertTrue(RitualManager.INSTANCE.activate(helper.getLevel(), center, caster, rite).isEmpty(),
                "public RitualManager activation starts the physical four-bundle rite");
            helper.assertTrue(!heart.isAlive() && !stone.isAlive(),
                "activation consumes exactly one demon heart and one attuned stone");
            helper.assertValueEqual(altar.getPower(), fundedPower,
                "activation escrows without draining the exact funded power");
            helper.assertValueEqual(altar.getEscrowedPower(), 4_800,
                "activation escrows exactly 4800 power for the normal cast");
            helper.assertValueEqual(HuntsmanSummoningStructure.completedBundles(helper.getLevel(), center), 4,
                "all four exact bloodied cardinal bundles persist during casting");

            helper.runAfterDelay(241L, () -> fixture.step(() -> {
                helper.assertValueEqual(altar.getEscrowedPower(), 0,
                    "normal 240-tick completion settles escrow exactly once");
                helper.assertTrue(altar.getPower() >= fundedPower - 4_800
                        && altar.getPower() < fundedPower - 4_650,
                    "completion settles the exact 4800 charge with only bounded live ambient gain");
                helper.assertValueEqual(HuntsmanSummoningStructure.completedBundles(helper.getLevel(), center), 0,
                    "completion consumes all four bloodied cardinal bundles");
                final List<ThornedPursuerEntity> localResults = new ArrayList<>(MAX_LOCAL_RESULTS);
                helper.getLevel().getEntities(EntityTypeTest.forClass(ThornedPursuerEntity.class), resultBounds,
                    Entity::isAlive, localResults, MAX_LOCAL_RESULTS);
                final List<ThornedPursuerEntity> results = localResults.stream()
                    .filter(Entity::isAlive)
                    .filter(entity -> !existingPursuers.contains(entity.getUUID()))
                    .toList();
                helper.assertValueEqual(results.size(), 1,
                    "the exact public rite creates one dedicated Thorned Pursuer result");
                final ThornedPursuerEntity result = results.getFirst();
                fixture.track(result);
                helper.assertValueEqual(result.creatureKind(), ArcaneCreature.CreatureKind.THORNED_PURSUER,
                    "the ritual result preserves dedicated acquisition identity");
                helper.assertValueEqual(ThornedPursuerRuntime.anchorForTest(result), result.blockPosition(),
                    "the summoned pursuer anchors exactly where production placed it");
                helper.assertValueEqual(SupernaturalProgression.level(caster,
                    SupernaturalProgression.Path.WEREWOLF), preservedLevel,
                    "ordinary rite completion preserves unrelated supernatural progression");
                fixture.close();
                helper.succeed();
            }));
        }));
    }

    private static void exerciseLiveHazardsBedLootAndProgression(GameTestHelper helper, Fixture fixture,
                                                                  ThornedPursuerEntity control) {
        ServerPlayer sleeper = fixture.connectedPlayer(new BlockPos(1, 1, 2));
        BlockPos bed = new BlockPos(1, 1, 3);
        fixture.place(bed, Blocks.BED.red());
        helper.assertTrue(helper.getBlockState(bed).is(Blocks.BED.red()), "rest probe uses a real bed block");
        helper.assertTrue(!control.isPreventingPlayerRest(helper.getLevel(), sleeper),
            "an anchored pursuer does not interfere with a real player's bed");

        Villager lavaQuarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(-1, 1, 1)));
        ThornedPursuerEntity lavaPursuer = fixture.spawnPursuer(new BlockPos(-1, 1, 2));
        ThornedPursuerRuntime.openEpisode(lavaPursuer, helper.getLevel(), lavaQuarry);
        ThornedPursuerRuntime.enterCourse(lavaPursuer);
        fixture.place(new BlockPos(-1, 1, 2), Blocks.LAVA);
        lavaPursuer.setPos(lavaPursuer.getX(), lavaPursuer.getY(), lavaPursuer.getZ());
        lavaPursuer.tickCount = Math.floorMod(-lavaPursuer.getId(), 20);
        ThornedPursuerRuntime.tick(lavaPursuer, helper.getLevel());
        helper.assertValueEqual(lavaPursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ESCAPE,
            "a live lava cell preempts COURSE after full teardown");
        helper.assertTrue(lavaPursuer.pursuerCounters().episodeCancelsByReason[ThornedPursuerRules.BreakReason.HAZARD.ordinal()] > 0,
            "lava cancellation is typed as HAZARD");

        Villager contactQuarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 3)));
        ThornedPursuerEntity contactPursuer = fixture.spawnPursuer(new BlockPos(2, 1, 3));
        ThornedPursuerRuntime.openEpisode(contactPursuer, helper.getLevel(), contactQuarry);
        ThornedPursuerRuntime.enterCourse(contactPursuer);
        fixture.place(new BlockPos(3, 1, 3), Blocks.CACTUS);
        contactPursuer.tickCount = Math.floorMod(-contactPursuer.getId(), 20);
        ThornedPursuerRuntime.tick(contactPursuer, helper.getLevel());
        helper.assertValueEqual(contactPursuer.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ESCAPE,
            "a live tagged contact hazard preempts COURSE after full teardown");

        ServerPlayer killer = fixture.connectedPlayer(new BlockPos(0, 1, 1));
        SupernaturalAdvancement.beginWerewolf(killer);
        SupernaturalProgression.setLevel(killer, SupernaturalProgression.Path.WEREWOLF, 4);
        SupernaturalProgression.setWerewolfShape(killer, WerewolfShape.WOLF);
        ThornedPursuerEntity victim = fixture.spawnPursuer(new BlockPos(0, 1, 2));
        int before = SupernaturalProgression.counter(killer, SupernaturalProgression.Path.WEREWOLF,
            WerewolfProgressionRules.Metric.HORNED_HUNTSMEN_DEFEATED);
        victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), 1_000.0F);
        helper.assertTrue(!victim.isAlive(), "ordinary attributed player damage kills the dedicated pursuer");
        helper.assertValueEqual(SupernaturalProgression.counter(killer, SupernaturalProgression.Path.WEREWOLF,
            WerewolfProgressionRules.Metric.HORNED_HUNTSMEN_DEFEATED), before + 1,
            "a level-four transformed werewolf kill preserves huntsman progression");
        List<ItemEntity> drops = new ArrayList<>(MAX_LOCAL_RESULTS);
        helper.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class),
            victim.getBoundingBox().inflate(3.0D), item -> !item.getItem().isEmpty(),
            drops, MAX_LOCAL_RESULTS);
        helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(
            com.kadamitas.warlockery.registry.ModItems.ALL.get("ingredient_infernal_blood").get())),
            "ordinary killed-by-player loot evaluates the guaranteed infernal-blood pool");
    }

    private static void exerciseFixtureSixMatrix(GameTestHelper helper, Fixture fixture,
                                                  ThornedPursuerEntity anchored) {
        Villager quarry = fixture.spawnVillager(helper.absolutePos(new BlockPos(3, 1, 1)));
        assertReloadNormalized(helper, fixture, fixture.spawnPursuer(new BlockPos(1, 1, 2)), "ANCHORED");
        ThornedPursuerEntity bay = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(bay, helper.getLevel(), quarry);
        assertReloadNormalized(helper, fixture, bay, "BAY");
        ThornedPursuerEntity course = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(course, helper.getLevel(), quarry);
        ThornedPursuerRuntime.enterCourse(course);
        assertReloadNormalized(helper, fixture, course, "COURSE");
        ThornedPursuerEntity set = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(set, helper.getLevel(), quarry);
        ThornedPursuerRuntime.enterCourse(set);
        ThornedPursuerRuntime.enterSet(set);
        assertReloadNormalized(helper, fixture, set, "SET/HOLD");
        ThornedPursuerEntity press = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(press, helper.getLevel(), quarry);
        ThornedPursuerRuntime.enterSet(press);
        ThornedPursuerRuntime.commitHold(press, helper.getLevel(), quarry);
        assertReloadNormalized(helper, fixture, press, "PRESS");
        ThornedPursuerEntity recover = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(recover, helper.getLevel(), quarry);
        ThornedPursuerRuntime.breakEpisode(recover, helper.getLevel(), ThornedPursuerRules.BreakReason.CANCELLED);
        assertReloadNormalized(helper, fixture, recover, "BREAK/RECOVER");
        ThornedPursuerEntity escape = fixture.spawnPursuer(new BlockPos(1, 1, 2));
        ThornedPursuerRuntime.openEpisode(escape, helper.getLevel(), quarry);
        escape.igniteForSeconds(2.0F);
        ThornedPursuerRuntime.tick(escape, helper.getLevel());
        assertReloadNormalized(helper, fixture, escape, "ESCAPE");

        ServerPlayer owner = fixture.connectedPlayer(new BlockPos(1, 1, 1));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
        owner.getItemInHand(InteractionHand.MAIN_HAND).getItem().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        ThornedPursuerEntity acquired = fixture.findHornPursuer(owner);
        helper.assertValueEqual(acquired.creatureKind(), ArcaneCreature.CreatureKind.THORNED_PURSUER,
            "the preserved real acquisition route creates the dedicated identity");

        Difficulty originalDifficulty = helper.getLevel().getDifficulty();
        try {
            for (Difficulty difficulty : Difficulty.values()) {
                helper.getLevel().getServer().setDifficulty(difficulty, true);
                ThornedPursuerEntity cohort = difficulty == Difficulty.PEACEFUL
                    ? fixture.spawnPursuerDirect(new BlockPos(1, 1, 2))
                    : fixture.spawnPursuer(new BlockPos(1, 1, 2));
                cohort.setCanPickUpLoot(true);
                cohort.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                cohort.getPersistentData().putBoolean("IsBaby", true);
                cohort.getPersistentData().putBoolean("CanBreakDoors", true);
                cohort.getPersistentData().putInt("ConversionTime", 200);
                cohort.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(cohort.blockPosition()),
                    EntitySpawnReason.EVENT, null);
                helper.assertTrue(!cohort.canPickUpLoot() && cohort.getMainHandItem().isEmpty(),
                    difficulty + " finalization strips pickup and hostile equipment state");
                helper.assertValueEqual(cohort.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE),
                    0.0D, difficulty + " finalization keeps reinforcement chance zero");
                helper.assertValueEqual(cohort.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH),
                    ThornedPursuerEntity.BASE_MAX_HEALTH, difficulty + " finalization restores the exact maximum health base");
                helper.assertValueEqual(cohort.operationalTargetGoalCount(), 0,
                    difficulty + " finalization retains no Zombie target lifecycle");
            }
        } finally {
            helper.getLevel().getServer().setDifficulty(originalDifficulty, true);
        }

        java.util.List<ThornedPursuerEntity> population = new ArrayList<>();
        int[] checkpoints = {1, 16, 64, 128};
        for (int index = 0; index < 128; index++) {
            ThornedPursuerEntity member = fixture.spawnPursuer(new BlockPos(1, 1, 1));
            population.add(member);
            ThornedPursuerRuntime.tick(member, helper.getLevel());
            int size = index + 1;
            if (java.util.Arrays.stream(checkpoints).anyMatch(value -> value == size)) {
                long generic = population.stream().map(ThornedPursuerEntity::pursuerCounters)
                    .mapToLong(c -> c.genericBehaviorDispatches + c.genericTacticalDispatches
                        + c.genericAmbientDispatches + c.genericHazardDispatches).sum();
                helper.assertValueEqual(generic, 0L, size + " pursuers never enter a generic dispatcher");
                helper.assertTrue(population.stream().map(ThornedPursuerEntity::pursuerCounters)
                    .mapToLong(c -> c.pathRequests).sum() <= 8L,
                    size + " pursuers stay within the per-level path quota");
            }
        }
    }

    private static void assertReloadNormalized(GameTestHelper helper, Fixture fixture,
                                               ThornedPursuerEntity source, String phase) {
        source.setPursuerState(new ThornedPursuerState(1, 123, 456, 78));
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
            helper.getLevel().registryAccess());
        source.saveWithoutId(output);
        Entity replacement = source.getType().create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(replacement instanceof ThornedPursuerEntity, phase + " reload retains dedicated identity");
        ThornedPursuerEntity loaded = (ThornedPursuerEntity) replacement;
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
            output.buildResult().copy()));
        fixture.entities.add(loaded);
        helper.assertValueEqual(loaded.pursuerRuntime().phase(), ThornedPursuerRules.Phase.ANCHORED,
            phase + " reload normalizes to ANCHORED");
        helper.assertValueEqual(loaded.pursuerState(), new ThornedPursuerState(1, 123, 456, 78),
            phase + " reload preserves only bounded durable cooldowns");
        helper.assertTrue(loaded.pursuerRuntime().escortIds().isEmpty()
            && !ThornedPursuerRuntime.courseModifierPresent(loaded) && loaded.getTarget() == null,
            phase + " reload clears every transient claim without replay");
    }

    private static void runEpisode(GameTestHelper helper, EpisodeAssertion assertion) {
        runIsolated(helper, (fixture, pursuer) -> {
            Villager quarry = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.EVENT);
            helper.assertTrue(quarry != null, "fixture quarry creates");
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 1));
            quarry.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(quarry);
            fixture.entities.add(quarry);
            pursuer.tickCount = Math.floorMod(-pursuer.getId(), ThornedPursuerRules.QUARRY_SCAN_CADENCE) - 1;
            assertion.run(fixture, pursuer, quarry);
        });
    }

    private static void runIsolated(GameTestHelper helper, FixtureAssertion assertion) {
        Fixture fixture = new Fixture(helper);
        try {
            fixture.open();
            Entity created = ModEntities.ALL.get("thorned_pursuer").get()
                .create(helper.getLevel(), EntitySpawnReason.EVENT);
            helper.assertTrue(created instanceof ThornedPursuerEntity,
                "the registry factory creates the dedicated ThornedPursuerEntity");
            ThornedPursuerEntity pursuer = (ThornedPursuerEntity) created;
            BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
            pursuer.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(pursuer);
            fixture.entities.add(pursuer);
            assertion.run(fixture, pursuer);
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    @FunctionalInterface private interface FixtureAssertion { void run(Fixture fixture, ThornedPursuerEntity pursuer); }
    @FunctionalInterface private interface EpisodeAssertion { void run(Fixture fixture, ThornedPursuerEntity pursuer, Villager quarry); }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final List<BlockPos> shell = new ArrayList<>();
        private final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> cleanup = new ArrayList<>();
        Fixture(GameTestHelper helper) { this.helper = helper; }
        <T extends Entity> T track(T entity) { entities.add(entity); return entity; }
        void open() {
            for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) != 4 && Math.abs(z) != 4 && y != 0 && y != 4) continue;
                BlockPos relative = new BlockPos(x + 1, y, z + 1);
                BlockPos absolute = helper.absolutePos(relative);
                if (helper.getLevel().getBlockState(absolute).isAir()) {
                    helper.setBlock(relative, Blocks.BARRIER);
                    shell.add(relative);
                }
            }
        }
        void close() {
            entities.forEach(Entity::discard);
            shell.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
            for (int index = cleanup.size() - 1; index >= 0; index--) cleanup.get(index).run();
            entities.clear(); shell.clear(); cleanup.clear();
        }
        void onClose(Runnable action) { cleanup.add(action); }
        ServerPlayer connectedPlayer(BlockPos relative) {
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos position = helper.absolutePos(relative);
            player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
            entities.add(GameTestMockPlayers.autoDisconnect(helper, player));
            return player;
        }
        ThornedPursuerEntity findHornPursuer(ServerPlayer owner) {
            BlockPos expected = owner.blockPosition().relative(owner.getDirection(), 2).above();
            List<ThornedPursuerEntity> found = new ArrayList<>(4);
            helper.getLevel().getEntities(EntityTypeTest.forClass(ThornedPursuerEntity.class),
                new AABB(expected).inflate(1.0D), entity -> owner.getStringUUID().equals(
                    entity.getPersistentData().getStringOr("WarlockerySummoningOwner", "")), found, 4);
            helper.assertValueEqual(found.size(), 1, "the horn creates exactly one local dedicated pursuer");
            ThornedPursuerEntity pursuer = found.getFirst();
            entities.add(pursuer);
            return pursuer;
        }
        Villager spawnVillager(net.minecraft.world.phys.Vec3 absolute) {
            Villager villager = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.EVENT);
            helper.assertTrue(villager != null, "fixture villager creates");
            villager.snapTo(absolute.x, absolute.y, absolute.z, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(villager);
            entities.add(villager);
            return villager;
        }
        Villager spawnVillager(BlockPos absolute) {
            return spawnVillager(net.minecraft.world.phys.Vec3.atBottomCenterOf(absolute));
        }
        ThornedPursuerEntity spawnPursuer(BlockPos relative) {
            return spawnPursuer(relative, EntitySpawnReason.EVENT);
        }
        ThornedPursuerEntity spawnPursuer(BlockPos relative, EntitySpawnReason reason) {
            Entity created = ModEntities.ALL.get("thorned_pursuer").get()
                .create(helper.getLevel(), reason);
            helper.assertTrue(created instanceof ThornedPursuerEntity,
                "fixture creates a dedicated pursuer");
            ThornedPursuerEntity pursuer = (ThornedPursuerEntity) created;
            BlockPos absolute = helper.absolutePos(relative);
            pursuer.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(pursuer);
            entities.add(pursuer);
            return pursuer;
        }
        @SuppressWarnings("unchecked")
        ThornedPursuerEntity spawnPursuerDirect(BlockPos relative) {
            var type = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.monster.Monster>)
                (net.minecraft.world.entity.EntityType<?>) ModEntities.ALL.get("thorned_pursuer").get();
            ThornedPursuerEntity pursuer = new ThornedPursuerEntity(type, helper.getLevel());
            BlockPos absolute = helper.absolutePos(relative);
            pursuer.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(pursuer);
            entities.add(pursuer);
            return pursuer;
        }
        <T extends Entity> T spawn(net.minecraft.world.entity.EntityType<T> type, BlockPos relative) {
            T entity = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
            helper.assertTrue(entity != null, "fixture entity creates");
            BlockPos absolute = helper.absolutePos(relative);
            entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(entity);
            entities.add(entity);
            return entity;
        }
        void place(BlockPos relative, net.minecraft.world.level.block.Block block) {
            helper.setBlock(relative, block);
            shell.add(relative);
        }
        void place(BlockPos relative, net.minecraft.world.level.block.state.BlockState state) {
            helper.setBlock(relative, state);
            shell.add(relative);
        }
        void placeRite(BlockPos relative, net.minecraft.world.level.block.Block block) {
            assertRiteCellPosition(relative);
            place(relative, block);
        }
        void placeRite(BlockPos relative, net.minecraft.world.level.block.state.BlockState state) {
            assertRiteCellPosition(relative);
            place(relative, state);
        }
        AABB riteCellBounds() {
            assertRiteCellPosition(BlockPos.ZERO);
            assertRiteCellPosition(new BlockPos(RITE_CELL_MAX, RITE_CELL_MAX, RITE_CELL_MAX));
            BlockPos min = helper.absolutePos(BlockPos.ZERO);
            BlockPos max = helper.absolutePos(new BlockPos(RITE_CELL_MAX, RITE_CELL_MAX, RITE_CELL_MAX));
            AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
            helper.assertTrue(bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY
                    && bounds.minZ <= bounds.maxZ,
                "the physical-rite result query is the exact local fixture cell");
            return bounds;
        }
        private void assertRiteCellPosition(BlockPos relative) {
            helper.assertTrue(relative.getX() >= 0 && relative.getX() <= RITE_CELL_MAX
                    && relative.getY() >= 0 && relative.getY() <= RITE_CELL_MAX
                    && relative.getZ() >= 0 && relative.getZ() <= RITE_CELL_MAX,
                "physical-rite mutation stays inside relative cell 0..14: " + relative);
        }
        void clearPlaced() {
            shell.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
            shell.clear();
        }
        void step(Runnable action) {
            try { action.run(); } catch (Throwable failure) { close(); throw failure; }
        }
    }
}
