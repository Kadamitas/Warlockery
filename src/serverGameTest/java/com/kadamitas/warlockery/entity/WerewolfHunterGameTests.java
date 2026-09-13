package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.EvidenceType;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Intent;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.kadamitas.warlockery.world.CreatureWorldIntegration;
import com.kadamitas.warlockery.world.SilverHuntData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class WerewolfHunterGameTests {
    private WerewolfHunterGameTests() {
    }

    public static void hunterIdentityLoadoutAndRaidContainment(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            helper.assertValueEqual(hunter.getClass().getName(), WerewolfHunterEntity.class.getName(),
                "the exact registered werewolf_hunter must construct the public WerewolfHunterEntity class");
            helper.assertValueEqual(hunter.creatureKind(), CreatureKind.WEREWOLF_HUNTER,
                "the public creature kind stays exact");
            helper.assertValueEqual(hunter.getAttributeValue(Attributes.MAX_HEALTH), 24.0D, "health 24");
            helper.assertValueEqual(hunter.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.0D, "attack 5");
            helper.assertTrue(Math.abs(hunter.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.35D) < 1.0E-6D,
                "speed stays the vanilla Pillager 0.35");
            helper.assertValueEqual(hunter.getAttributeValue(Attributes.FOLLOW_RANGE), 32.0D, "follow range 32");
            helper.assertTrue(hunter.getMainHandItem().is(Items.CROSSBOW),
                "the default loadout keeps the vanilla crossbow");
            helper.assertTrue(hunter.getOffhandItem().is(silverBolt()),
                "the default loadout keeps the existing silver bolt");
            helper.assertValueEqual(hunter.getOffhandItem().getCount(),
                WerewolfHunterRules.DEFAULT_SILVER_BOLTS,
                "the authoritative default reserve is twenty-four bolts");
            helper.assertValueEqual(hunter.operationalTargetGoalCount(), 0,
                "no inherited player, villager, golem, or raid target goal survives containment");
            final List<String> goals = hunter.operationalGoalNames();
            for (final String forbidden : List.of("HoldGroundAttackGoal", "ObtainRaidLeaderBannerGoal",
                "PathfindToRaidGoal", "RaiderMoveThroughVillageGoal", "RaiderCelebration",
                "LongDistancePatrolGoal", "RandomStrollGoal")) {
                helper.assertFalse(goals.stream().anyMatch(name -> name.equals(forbidden)),
                    "inherited raid or patrol goal must be removed: " + forbidden);
            }
            helper.assertFalse(hunter.canJoinRaid(), "the hunter can never join a raid");
            helper.assertFalse(hunter.canJoinPatrol(), "the hunter can never join an illager patrol");
            helper.assertFalse(hunter.canPickUpLoot(), "raid banner pickup stays disabled");

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            hunter.saveWithoutId(output);
            final var saved = output.buildResult().copy();
            saved.putBoolean("CanJoinRaid", true);
            saved.putBoolean("PatrolLeader", true);
            final WerewolfHunterEntity loaded = (WerewolfHunterEntity) ModEntities.WEREWOLF_HUNTER.get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "the registered hunter type must recreate saved state");
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved
            ));
            helper.assertFalse(loaded.canJoinRaid(),
                "legacy raid membership normalizes to a permanent refusal on load");
            helper.assertFalse(loaded.isPatrolLeader(),
                "legacy patrol leadership is removed on load");
            helper.assertTrue(loaded.getCurrentRaid() == null,
                "no raid reference survives load normalization");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterWarrantMatrixAndEvidenceExpiry(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            final WerewolfEntity identityOnly = (WerewolfEntity) fixture.spawn(
                ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
            );
            identityOnly.setNoAi(true);
            final Villager villager = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT);
            villager.setNoAi(true);
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.hunterState().quarryId().isEmpty(),
                "identity alone never creates a quarry: a nearby Werewolf stays neutral");
            helper.assertTrue(hunter.getTarget() == null,
                "no identity-only target acquisition may occur");
            helper.assertFalse(WerewolfHunterRuntime.eligibleTarget(hunter, identityOnly),
                "the containment predicate refuses unwarranted identities");
            helper.assertFalse(WerewolfHunterRuntime.eligibleTarget(hunter, villager),
                "villagers can never become quarry");

            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0), EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            hunter.invulnerableTime = 0;
            helper.assertTrue(hunter.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the warrant fixture needs one real accepted hit");
            final var direct = hunter.hunterState().evidence().stream()
                .filter(entry -> entry.type() == EvidenceType.DIRECT_ATTACK)
                .findFirst();
            helper.assertTrue(direct.isPresent(), "a real direct attack records typed confirmed evidence");
            helper.assertValueEqual(direct.orElseThrow().confidence(),
                WerewolfHunterRules.Confidence.CONFIRMED, "direct attacks confirm immediately");
            helper.assertTrue(direct.orElseThrow().expiresAt()
                    <= helper.getLevel().getGameTime() + WerewolfHunterRules.DIRECT_ATTACK_TICKS,
                "direct evidence lives at most six hundred ticks");

            WerewolfHunterRuntime.recordWitnessedAttack(hunter, attacker.getUUID(), now);
            helper.assertTrue(hunter.hunterState().evidence().size()
                    <= WerewolfHunterRules.MAX_EVIDENCE_RECORDS,
                "the evidence ledger stays at four records or fewer");

            hunter.setHunterState(hunter.hunterState().withEvidence(
                WerewolfHunterRules.pruneEvidence(
                    hunter.hunterState().evidence(),
                    WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.DIRECT_ATTACK_TICKS + 1L)
                )
            ));
            helper.assertTrue(hunter.hunterState().evidence().isEmpty(),
                "expired evidence is removed rather than lingering as a warrant");
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.hunterState().quarryId().isEmpty(),
                "expired evidence releases every quarry claim");

            villager.invulnerableTime = 0;
            helper.assertTrue(villager.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the witnessed-attack fixture needs one real hit on a protected resident");
            makeDue(hunter);
            helper.runAfterDelay(2L, () -> {
                try {
                    helper.assertTrue(hunter.hunterState().evidence().stream().anyMatch(entry ->
                            entry.type() == EvidenceType.WITNESSED_ATTACK
                                && entry.sourceId().map(attacker.getUUID()::equals).orElse(false)
                                && entry.packedPosition().isPresent()),
                        "a live ticking hunter records the witnessed attack with its observed locus");
                    helper.assertValueEqual(hunter.hunterState().quarryId().orElseThrow(),
                        attacker.getUUID(),
                        "the witnessed active attacker becomes the quarry at priority four");
                    helper.assertValueEqual(hunter.hunterState().intent(), Intent.WARN,
                        "the live warrant begins with a warning, never an instant shot");
                    helper.assertTrue(hunter.hunterCounters().observationScans() >= 1L,
                        "the live observation channel is bounded and counted");
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

    public static void hunterWarnsTracksAndReturnsToAnchor(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            erectIsolationShell(fixture, new BlockPos(1, 1, 1));
            final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
            hunter.setHunterState(hunter.hunterState().withAnchors(new WerewolfHunterState.Anchors(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(anchor)
            )));
            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1), EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            hunter.invulnerableTime = 0;
            helper.assertTrue(hunter.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F
            ), "the tracking fixture needs a real direct hit");
            makeDue(hunter);
            helper.runAfterDelay(2L, () -> {
                try {
                helper.assertValueEqual(hunter.hunterState().intent(), Intent.WARN,
                    "the first confirmed decision of a live hunter warns before any engagement");
                helper.assertTrue(hunter.getTarget() == null,
                    "the warning decision must not already commit an attack target");
                helper.assertTrue(hunter.hunterCounters().warnings() >= 1L, "warning work is counted");
                final List<BlockPos> waypoints = WerewolfHunterRuntime.searchWaypoints(
                    attacker.blockPosition(), hunter.getUUID()
                );
                helper.assertValueEqual(waypoints.size(), WerewolfHunterRules.MAX_SEARCH_WAYPOINTS,
                    "a lost-quarry search claims at most four waypoints");
                for (final BlockPos waypoint : waypoints) {
                    helper.assertTrue(attacker.blockPosition().closerThan(waypoint,
                            WerewolfHunterRules.SEARCH_RADIUS + 0.5D),
                        "every search waypoint stays within twelve blocks of the locus");
                }
                attacker.discard();
                makeDue(hunter);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(4L, () -> {
                try {
                helper.assertTrue(hunter.hunterState().evidence().stream().anyMatch(entry ->
                        entry.type() == EvidenceType.LAST_KNOWN && entry.packedPosition().isPresent()),
                    "a lost quarry downgrades to one bounded last-known clue with its observed locus");
                helper.assertValueEqual(hunter.hunterState().intent(), Intent.INVESTIGATE,
                    "losing the quarry begins one bounded live investigation");
                helper.assertTrue(hunter.hunterState().anchors().search().isPresent(),
                    "the live investigation claims the search locus");
                helper.assertTrue(hunter.hunterCounters().navigationRequests() >= 1L,
                    "the live investigation navigates one bounded waypoint per cadence");

                final Zombie secondAttacker = fixture.spawn(
                    EntityTypes.ZOMBIE, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
                );
                secondAttacker.setNoAi(true);
                hunter.invulnerableTime = 0;
                helper.assertTrue(hunter.hurtServer(
                    helper.getLevel(), helper.getLevel().damageSources().mobAttack(secondAttacker), 1.0F
                ), "the re-warn fixture needs a second real direct hit");
                makeDue(hunter);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(6L, () -> {
                try {
                helper.assertValueEqual(hunter.hunterState().intent(), Intent.WARN,
                    "a new quarry restarts the warn-then-engage sequence");
                helper.assertTrue(hunter.hunterCounters().warnings() >= 2L,
                    "warning progress is per engagement, not once per hunter lifetime");
                hunter.setHunterState(hunter.hunterState()
                    .withEvidence(List.of()).withQuarry(Optional.empty()));
                hunter.setTarget(null);
                hunter.snapTo(anchor.getX() + 3.5D, anchor.getY(), anchor.getZ() + 0.5D, 0.0F, 0.0F);
                hunter.setDeltaMovement(Vec3.ZERO);
                makeDue(hunter);
                } catch (final RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
            helper.runAfterDelay(8L, () -> {
                try {
                    helper.assertValueEqual(hunter.hunterState().intent(), Intent.RETURN,
                        "an idle live hunter with a loaded anchor returns to it");
                    helper.assertTrue(hunter.hunterCounters().navigationRequests() >= 2L,
                        "the return requests one bounded route toward the anchor");
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

    public static void hunterCrossbowConsumesFiniteSilverAmmunition(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            hunter.setNoAi(true);
            final Zombie target = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2), EntitySpawnReason.EVENT);
            target.setNoAi(true);
            hunter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(silverBolt(), 3));
            final ItemStack crossbow = hunter.getMainHandItem();
            helper.assertTrue(crossbow.getItem() instanceof CrossbowItem, "the executor is the vanilla crossbow");

            hunter.startUsingItem(InteractionHand.MAIN_HAND);
            crossbow.getItem().onUseTick(helper.getLevel(), hunter, crossbow, 0);
            helper.assertTrue(CrossbowItem.isCharged(crossbow),
                "a full charge loads the compatible held silver bolt");
            helper.assertValueEqual(hunter.silverBoltCount(), 2,
                "loading consumes exactly one bolt through the existing crossbow contract");
            hunter.stopUsingItem();

            hunter.setHunterState(hunter.hunterState().withEvidence(WerewolfHunterRules.recordEvidence(
                List.of(),
                WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK,
                    Optional.of(target.getUUID()), Optional.empty(), helper.getLevel().getGameTime()),
                helper.getLevel().getGameTime()
            )).withQuarry(Optional.of(target.getUUID())));
            hunter.performRangedAttack(target, 1.0F);
            final List<AbstractArrow> projectiles = helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class, hunter.getBoundingBox().inflate(24.0D)
            );
            helper.assertTrue(!projectiles.isEmpty(), "the vanilla executor must fire a real projectile");
            for (final AbstractArrow projectile : projectiles) {
                helper.assertTrue(projectile.getOwner() == hunter,
                    "every fired silver projectile preserves the hunter as its attributed owner");
                helper.assertTrue(projectile.getPickupItemStackOrigin().is(silverBolt()),
                    "the projectile identity stays the existing silver bolt");
                fixture.track(projectile);
            }
            helper.assertFalse(CrossbowItem.isCharged(crossbow), "firing spends the loaded charge");

            hunter.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            hunter.startUsingItem(InteractionHand.MAIN_HAND);
            crossbow.getItem().onUseTick(helper.getLevel(), hunter, crossbow, 0);
            helper.assertFalse(CrossbowItem.isCharged(crossbow),
                "zero compatible bolts refuses the load instead of conjuring vanilla arrows");
            hunter.stopUsingItem();
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.getTarget() == null,
                "zero ammunition forbids any ranged commitment and releases the target");
            helper.assertValueEqual(hunter.hunterState().intent(), Intent.RESUPPLY,
                "an empty quiver moves the hunter to resupply");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterProtectedCrossfireCancelsShot(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(0, 1, 1));
            hunter.setNoAi(true);
            hunter.setDeltaMovement(Vec3.ZERO);
            final Zombie quarry = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            quarry.setNoAi(true);
            final Villager bystander = fixture.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1), EntitySpawnReason.EVENT);
            bystander.setNoAi(true);
            bystander.setDeltaMovement(Vec3.ZERO);
            final float bystanderHealth = bystander.getHealth();

            final long now = helper.getLevel().getGameTime();
            hunter.setHunterState(hunter.hunterState()
                .withEvidence(WerewolfHunterRules.recordEvidence(
                    List.of(),
                    WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK,
                        Optional.of(quarry.getUUID()), Optional.empty(), now),
                    now
                ))
                .withDeadlines(new WerewolfHunterState.Deadlines(
                    Math.max(1L, now - WerewolfHunterRules.WARN_MINIMUM_TICKS - 1L), 0L, 0L, 0L, 0L, 0L
                )));
            makeDue(hunter);
            final long lineOfSightBefore = hunter.hunterCounters().lineOfSightChecks();
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.getTarget() == null,
                "a protected actor inside the projectile corridor cancels the shot");
            helper.assertValueEqual(hunter.hunterState().intent(), Intent.REPOSITION,
                "a blocked corridor repositions instead of firing through residents");
            helper.assertTrue(hunter.hunterCounters().shotCancellations() >= 1L,
                "the cancellation is counted, never silently retried");
            // The counter is a lifetime total that two independently capped scans charge into: the
            // witnessed-attack pass spends up to MAX_LINE_OF_SIGHT_CHECKS across its retained
            // attackers, and the corridor pass spends up to MAX_LINE_OF_SIGHT_CHECKS of its own. One
            // decision pass runs each of them at most once, so the pass budget is twice the constant
            // and the constant itself is right. Comparing the running total against a single scan's
            // budget made this assertion depend on how many protected actors happened to be inside
            // the twenty-four block observation radius, which in a shared GameTest batch includes
            // neighbouring structures, so it failed on arena placement rather than on any overrun.
            final long spentInOnePass = hunter.hunterCounters().lineOfSightChecks() - lineOfSightBefore;
            helper.assertTrue(spentInOnePass <= 2L * WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS,
                "one decision pass charges at most one witnessed-attack budget plus one corridor "
                    + "budget; was " + spentInOnePass);
            // Crowd the corridor past its budget so the cap is what the next assertion measures.
            // Without the break in protectedInCorridor this scan would charge one check per
            // protected actor it finds, which is at least six here, so the equality below cannot
            // pass vacuously and cannot drift with whatever a neighbouring arena contributes.
            final List<Villager> corridorCrowd = new ArrayList<>();
            for (final BlockPos seat : List.of(new BlockPos(0, 1, 0), new BlockPos(0, 1, 2),
                new BlockPos(1, 1, 0), new BlockPos(1, 1, 2), new BlockPos(2, 1, 0))) {
                final Villager extra = fixture.spawn(EntityTypes.VILLAGER, seat, EntitySpawnReason.EVENT);
                extra.setNoAi(true);
                extra.setDeltaMovement(Vec3.ZERO);
                corridorCrowd.add(extra);
            }
            final long corridorBefore = hunter.hunterCounters().lineOfSightChecks();
            final List<LivingEntity> crowded = WerewolfHunterRuntime.protectedInCorridor(
                hunter, helper.getLevel(), quarry
            );
            helper.assertTrue(crowded.size() <= WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS,
                "a corridor scan can never report more blockers than checks it was allowed to spend");
            helper.assertValueEqual(
                hunter.hunterCounters().lineOfSightChecks() - corridorBefore,
                (long) WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS,
                "corridor validation stops at exactly the four-check budget in a crowded corridor"
            );
            corridorCrowd.forEach(Entity::discard);
            helper.assertValueEqual(bystander.getHealth(), bystanderHealth,
                "the protected bystander takes no crossfire damage");
            helper.assertTrue(hunter.hunterCounters().laneSearches() >= 1L,
                "a blocked corridor consults the ranked lane search");
            helper.assertTrue(hunter.hunterCounters().blockReads()
                    <= (long) WerewolfHunterRules.MAX_LANE_BLOCK_READS * hunter.hunterCounters().laneSearches(),
                "lane evaluation stays inside the charged one hundred twenty-eight read budget");

            final ItemStack crossbow = hunter.getMainHandItem();
            hunter.startUsingItem(InteractionHand.MAIN_HAND);
            crossbow.getItem().onUseTick(helper.getLevel(), hunter, crossbow, 0);
            helper.assertTrue(CrossbowItem.isCharged(crossbow),
                "the pre-shot fixture needs a loaded crossbow");
            hunter.stopUsingItem();
            final long cancellationsBefore = hunter.hunterCounters().shotCancellations();
            hunter.performRangedAttack(quarry, 1.0F);
            helper.assertTrue(hunter.hunterCounters().shotCancellations() > cancellationsBefore,
                "immediate pre-shot revalidation cancels the release while the corridor is compromised");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                    AbstractArrow.class, hunter.getBoundingBox().inflate(24.0D)).isEmpty(),
                "no projectile may leave the crossbow through a protected corridor");
            helper.assertValueEqual(bystander.getHealth(), bystanderHealth,
                "the protected bystander survives the attempted release untouched");

            bystander.discard();
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.getTarget() == quarry,
                "a cleared corridor allows the warranted engagement");
            helper.assertValueEqual(hunter.hunterState().intent(), Intent.ENGAGE,
                "engagement resumes only after corridor revalidation");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterRetreatSearchAndHazardPreemptionAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            hunter.setNoAi(true);
            final Zombie quarry = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            quarry.setNoAi(true);
            hunter.setHunterState(hunter.hunterState().withEvidence(WerewolfHunterRules.recordEvidence(
                List.of(),
                WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK,
                    Optional.of(quarry.getUUID()), Optional.empty(), now),
                now
            )).withQuarry(Optional.of(quarry.getUUID())));
            hunter.setTarget(quarry);

            hunter.setHealth(hunter.getMaxHealth() * 0.25F);
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertValueEqual(hunter.hunterState().intent(), Intent.RETREAT,
                "thirty percent health forces a bounded retreat");
            helper.assertTrue(hunter.getTarget() == null, "retreat clears the attack claim");
            helper.assertTrue(hunter.hunterState().deadlines().retreatUntil()
                    <= helper.getLevel().getGameTime() + WerewolfHunterRules.RETREAT_TICKS,
                "the retreat deadline stays at or below one hundred twenty ticks");
            hunter.setHealth(hunter.getMaxHealth());

            hunter.setRemainingFireTicks(100);
            hunter.setTarget(quarry);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.hunterCounters().hazardInterruptions() >= 1L,
                "an active hazard preempts every hunter decision");
            helper.assertTrue(hunter.getTarget() == null,
                "hazard onset cancels the pending shot and target claim");
            helper.assertFalse(hunter.isChargingCrossbow(),
                "hazard onset interrupts any crossbow charge");
            hunter.clearFire();

            helper.assertTrue(hunter.hunterCounters().lineOfSightChecks()
                    <= WerewolfHunterRules.MAX_LINE_OF_SIGHT_CHECKS * Math.max(1L, hunter.hunterCounters().observationScans()),
                "line-of-sight work stays within four checks per observation");
            helper.assertTrue(hunter.hunterCounters().blockReads()
                    <= WerewolfHunterRules.MAX_LANE_BLOCK_READS * Math.max(1L, hunter.hunterCounters().laneSearches()),
                "block reads stay within the charged one hundred twenty-eight read budget");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterResupplyCapsWithoutDuplication(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            hunter.setNoAi(true);
            final ServerPlayer donor = fixture.connectedPlayer(new BlockPos(1, 1, 2));
            donor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(silverBolt(), 20));

            helper.assertValueEqual(hunter.silverBoltCount(), WerewolfHunterRules.DEFAULT_SILVER_BOLTS,
                "the resupply fixture starts from the default reserve");
            helper.assertTrue(hunter.mobInteract(donor, InteractionHand.MAIN_HAND).consumesAction(),
                "a compatible idle offer is accepted");
            helper.assertValueEqual(hunter.silverBoltCount(), WerewolfHunterRules.MAX_SILVER_BOLTS,
                "one interaction fills only the available capacity up to thirty-two");
            helper.assertValueEqual(donor.getMainHandItem().getCount(), 12,
                "exactly the accepted eight bolts leave the donor hand");

            helper.assertFalse(hunter.mobInteract(donor, InteractionHand.MAIN_HAND).consumesAction(),
                "a full reserve refuses further transfer");
            helper.assertValueEqual(donor.getMainHandItem().getCount(), 12,
                "a refused interaction cannot duplicate or consume items");

            hunter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(silverBolt(), 4));
            hunter.setChargingCrossbow(true);
            helper.assertFalse(hunter.mobInteract(donor, InteractionHand.MAIN_HAND).consumesAction(),
                "a charging hunter refuses resupply");
            helper.assertValueEqual(hunter.silverBoltCount(), 4,
                "the refused charging transfer changes no reserve");
            hunter.setChargingCrossbow(false);

            hunter.invulnerableTime = 0;
            helper.assertTrue(hunter.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().playerAttack(donor), 1.0F
            ), "the hostile-donor fixture needs a real hit");
            helper.assertFalse(hunter.mobInteract(donor, InteractionHand.MAIN_HAND).consumesAction(),
                "valid direct-attack evidence against the donor refuses resupply");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void silverHuntTransactionDeduplicatesAndRollsBack(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
            final SilverHuntData hunts = SilverHuntData.get(helper.getLevel());
            final List<UUID> preexisting = hunts.records().stream()
                .map(SilverHuntData.HuntRecord::huntId).toList();
            fixture.onClose(() -> hunts.records().stream()
                .map(SilverHuntData.HuntRecord::huntId)
                .filter(id -> !preexisting.contains(id))
                .toList()
                .forEach(hunts::discard));

            final CreatureWorldIntegration.SilverHuntReport committed =
                CreatureWorldIntegration.runSilverHuntTransaction(helper.getLevel(), anchor, false);
            helper.assertTrue(committed.reserved() && committed.committed(),
                "a clear anchor reserves and commits one hunt");
            helper.assertValueEqual(committed.constructedParticipants(),
                WerewolfHunterRules.HUNT_PARTICIPANT_CONSTRUCTIONS,
                "the transaction constructs exactly one hunter and one Werewolf quarry, no ordinary Pillager");
            final SilverHuntData.HuntRecord record = hunts.record(committed.huntId().orElseThrow()).orElseThrow();
            helper.assertValueEqual(record.stage(), WerewolfHunterRules.HuntStage.ACTIVE,
                "the record activates only after both participant UUIDs are confirmed");
            final WerewolfHunterEntity hunter = (WerewolfHunterEntity) helper.getLevel()
                .getEntity(record.hunterId().orElseThrow());
            final WerewolfEntity quarry = (WerewolfEntity) helper.getLevel()
                .getEntity(record.quarryId().orElseThrow());
            helper.assertTrue(hunter != null && quarry != null, "both participants exist after commit");
            fixture.track(hunter);
            fixture.track(quarry);
            helper.assertTrue(quarry.getTarget() == null,
                "the event quarry keeps full neutrality: no forced target or doctrine");
            helper.assertValueEqual(hunter.hunterState().huntId().orElseThrow(),
                committed.huntId().orElseThrow(), "event evidence binds the hunter to the exact hunt UUID");
            helper.assertTrue(hunter.hunterState().evidence().stream().anyMatch(entry ->
                    entry.type() == EvidenceType.EVENT_QUARRY
                        && entry.targetId().map(quarry.getUUID()::equals).orElse(false)),
                "only the hunter receives the authored event warrant");

            final CreatureWorldIntegration.SilverHuntReport deduplicated =
                CreatureWorldIntegration.runSilverHuntTransaction(helper.getLevel(), anchor.offset(4, 0, 4), false);
            helper.assertFalse(deduplicated.reserved(),
                "a second hunt within one hundred twenty-eight blocks is rejected before construction");

            final BlockPos distant = anchor.offset(130, 0, 0);
            final CreatureWorldIntegration.SilverHuntReport rolledBack =
                CreatureWorldIntegration.runSilverHuntTransaction(helper.getLevel(), distant, true);
            helper.assertTrue(rolledBack.reserved() && !rolledBack.committed(),
                "an injected construction failure aborts the reserved transaction");
            helper.assertTrue(hunts.record(rolledBack.huntId().orElseThrow()).isEmpty(),
                "rollback removes the reserved record in the same tick");
            helper.assertValueEqual(
                helper.getLevel().getEntitiesOfClass(
                    WerewolfEntity.class, new net.minecraft.world.phys.AABB(distant).inflate(8.0D)
                ).size(),
                0,
                "rollback discards every participant the failed transaction added");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterReloadReconcilesSemanticStateOnly(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            hunter.setNoAi(true);
            final BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
            final UUID attackerId = UUID.randomUUID();
            hunter.setHunterState(hunter.hunterState()
                .withEvidence(WerewolfHunterRules.recordEvidence(
                    List.of(),
                    WerewolfHunterRules.createEvidence(EvidenceType.DIRECT_ATTACK,
                        Optional.of(attackerId), Optional.empty(), now),
                    now
                ))
                .withAnchors(new WerewolfHunterState.Anchors(
                    Optional.of(anchor), Optional.empty(), Optional.of(anchor.offset(2, 0, 0)),
                    Optional.of(anchor.offset(0, 0, 2)), Optional.of(anchor)
                ))
                .withDeadlines(new WerewolfHunterState.Deadlines(
                    now, Long.MAX_VALUE - 8L, 0L, Long.MAX_VALUE - 8L, 0L, Long.MAX_VALUE - 8L
                )));
            hunter.setChargingCrossbow(true);
            hunter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(silverBolt(), 64));

            final TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            hunter.saveWithoutId(output);
            final WerewolfHunterEntity loaded = (WerewolfHunterEntity) ModEntities.WEREWOLF_HUNTER.get()
                .create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "the registered hunter type must recreate saved state");
            fixture.track(loaded);
            loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult().copy()
            ));
            final long later = helper.getLevel().getGameTime();
            helper.assertTrue(loaded.hunterState().evidence().stream()
                    .anyMatch(entry -> entry.type() == EvidenceType.DIRECT_ATTACK
                        && entry.sourceId().map(attackerId::equals).orElse(false)),
                "semantic typed evidence survives a real save and reload");
            helper.assertValueEqual(loaded.hunterState().anchors().settlement().orElseThrow(), anchor,
                "the semantic settlement anchor survives reload");
            helper.assertValueEqual(loaded.hunterState().anchors().returnPoint().orElseThrow(), anchor,
                "the semantic return anchor survives reload");
            helper.assertTrue(loaded.hunterState().anchors().lane().isEmpty(),
                "the transient firing-lane claim never persists");
            helper.assertTrue(loaded.hunterState().anchors().search().isEmpty(),
                "the transient search claim never persists");
            helper.assertFalse(loaded.isChargingCrossbow(),
                "a charging commitment never resumes from disk");
            helper.assertTrue(loaded.getNavigation().isDone(), "no path resumes from disk");
            helper.assertTrue(loaded.hunterState().deadlines().engageUntil()
                    <= later + WerewolfHunterRules.MAX_FUTURE_HORIZON_TICKS,
                "implausibly distant engage deadlines clamp to the semantic horizon");
            helper.assertTrue(loaded.hunterState().deadlines().actionBackoffUntil()
                    <= later + WerewolfHunterRules.ROUTE_BACKOFF_TICKS,
                "the backoff deadline clamps to its own maximum horizon");
            helper.assertValueEqual(loaded.silverBoltCount(), WerewolfHunterRules.MAX_SILVER_BOLTS,
                "legacy oversized silver stacks reconcile to the thirty-two bolt cap without duplication");
            helper.assertFalse(loaded.canJoinRaid(), "reload keeps the raid refusal");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void hunterRouteFailuresBackOffAndRelease(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final long now = helper.getLevel().getGameTime();
            final WerewolfHunterEntity hunter = spawnHunter(fixture, new BlockPos(1, 1, 1));
            hunter.setNoAi(true);
            final Zombie quarry = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
            quarry.setNoAi(true);
            hunter.setHunterState(hunter.hunterState()
                .withQuarry(Optional.empty())
                .withAnchors(new WerewolfHunterState.Anchors(
                    Optional.empty(), Optional.empty(),
                    Optional.of(helper.absolutePos(new BlockPos(2, 1, 2))),
                    Optional.of(helper.absolutePos(new BlockPos(0, 1, 2))),
                    Optional.empty()
                )));
            hunter.setTarget(quarry);
            final long generationBefore = hunter.hunterState().intentGeneration();

            WerewolfHunterState state = hunter.hunterState();
            state = WerewolfHunterRuntime.recordRouteFailure(hunter, state, now);
            helper.assertValueEqual(state.routeFailures(), 1, "the first rejected route is counted");
            state = WerewolfHunterRuntime.recordRouteFailure(hunter, state, now);
            helper.assertValueEqual(state.routeFailures(), 2, "the second rejected route is counted");
            state = WerewolfHunterRuntime.recordRouteFailure(hunter, state, now);
            hunter.setHunterState(state);

            helper.assertTrue(hunter.getTarget() == null,
                "three route failures release the target claim");
            helper.assertTrue(state.anchors().lane().isEmpty(),
                "three route failures clear the firing-lane claim");
            helper.assertTrue(state.anchors().search().isEmpty(),
                "three route failures clear the search destination");
            helper.assertTrue(state.intentGeneration() > generationBefore,
                "the action generation increments so stale actions cannot resume");
            helper.assertTrue(state.deadlines().actionBackoffUntil()
                    >= now + WerewolfHunterRules.ROUTE_BACKOFF_TICKS,
                "route requests sleep for at least one hundred ticks");
            helper.assertValueEqual(state.routeFailures(), 0,
                "the failure counter resets once the backoff begins");

            hunter.setHunterState(hunter.hunterState().withDeadlines(new WerewolfHunterState.Deadlines(
                0L, 0L, 0L, 0L, 0L, now + WerewolfHunterRules.ROUTE_BACKOFF_TICKS
            )));
            makeDue(hunter);
            WerewolfHunterRuntime.tick(hunter, helper.getLevel());
            helper.assertTrue(hunter.hunterCounters().navigationRequests() == 0L,
                "no navigation request is issued while the backoff is active");
            helper.succeed();
        } finally {
            fixture.close();
        }
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
                        helper.getLevel().setBlock(
                            pos, net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3
                        );
                        placed.add(pos);
                    }
                }
            }
        }
        fixture.onClose(() -> placed.forEach(pos -> helper.getLevel().setBlock(
            pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3
        )));
    }

    private static Item silverBolt() {
        return ModItems.ALL.get("ingredient_bolt_silver").get();
    }

    private static void makeDue(final WerewolfHunterEntity hunter) {
        hunter.setHunterState(hunter.hunterState().withCadence(new WerewolfHunterState.Cadence(
            0L, 0L, 0L, 0L, 0L
        )));
    }

    private static WerewolfHunterEntity spawnHunter(final FixtureScope fixture, final BlockPos position) {
        final WerewolfHunterEntity hunter = fixture.spawn(
            ModEntities.WEREWOLF_HUNTER.get(), position, EntitySpawnReason.EVENT
        );
        hunter.setDeltaMovement(Vec3.ZERO);
        return hunter;
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
            if (closed) return;
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
            cleanupActions.forEach(Runnable::run);
            cleanupActions.clear();
        }
    }
}
