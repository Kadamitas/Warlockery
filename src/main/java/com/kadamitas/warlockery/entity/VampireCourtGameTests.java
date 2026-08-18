package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import com.kadamitas.warlockery.entity.VampireCourtRules.Intent;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.kadamitas.warlockery.world.VillageAssaultData;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class VampireCourtGameTests {
    private VampireCourtGameTests() {
    }

    public static void dayShelterAndNightHunt(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final VampireCourtEntity vampire = spawn(fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1));
        try {
        vampire.setCourtState(vampire.courtState().withPressure(800, helper.getLevel().getGameTime()));
        VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), true, false);
        helper.assertValueEqual(vampire.courtState().intent(), Intent.SEEK_SHELTER,
            "exposed daylight must interrupt a full Vampire hunt");
        final VampireCourtEntity rival = spawn(
            fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1)
        );
        final BlockPos contestedShelter = helper.absolutePos(new BlockPos(2, 1, 1));
        final long claimTime = helper.getLevel().getGameTime();
        helper.assertTrue(VampireCourtRuntime.tryClaimShelter(
            vampire, helper.getLevel(), contestedShelter, claimTime
        ), "the first loaded Vampire must acquire an available shelter claim");
        helper.assertFalse(VampireCourtRuntime.tryClaimShelter(
            rival, helper.getLevel(), contestedShelter, claimTime
        ),
            "two loaded Vampires must not hold the same unexpired shelter claim");
        vampire.setCourtState(vampire.courtState()
            .withShelter(helper.getLevel().dimension().identifier().toString(), contestedShelter,
                claimTime + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withCadence(0L, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, claimTime));
        VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), false, true);
        helper.assertTrue(vampire.courtState().shelter().isEmpty(),
            "leaving shelter behavior must clear its semantic shelter claim");
        helper.assertTrue(VampireCourtRuntime.tryClaimShelter(
            rival, helper.getLevel(), contestedShelter, claimTime
        ), "a released shelter claim must be immediately reacquirable by another loaded Vampire");
        final java.util.UUID unloadedPrey = java.util.UUID.randomUUID();
        helper.assertTrue(VampireCourtRuntime.tryClaimPrey(
            vampire, helper.getLevel(), unloadedPrey, claimTime
        ), "the first loaded Vampire must acquire an available prey claim");
        helper.assertFalse(VampireCourtRuntime.tryClaimPrey(
            rival, helper.getLevel(), unloadedPrey, claimTime
        ), "two loaded Vampires must not hold the same unexpired prey claim");
        vampire.setCourtState(vampire.courtState().withTarget(
            unloadedPrey, claimTime + VampireCourtRules.MAX_CLAIM_LEASE_TICKS
        ).withIntent(Intent.STALK, claimTime + VampireCourtRules.MAX_CLAIM_LEASE_TICKS));
        VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), true, false);
        helper.assertTrue(VampireCourtRuntime.tryClaimPrey(
            rival, helper.getLevel(), unloadedPrey, claimTime
        ), "an unloaded prey revalidation must release the claim for another loaded Vampire");
        final VampireCourtState shelterState = vampire.courtState();
        vampire.setCourtState(shelterState.withCadence(
            0L, shelterState.nextEntityScanAt(), shelterState.nextShelterScanAt(),
            shelterState.nextFeedbackAt(), shelterState.lastNavigationAt()
        ));
        VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), false, true);
        helper.assertValueEqual(vampire.courtState().intent(), Intent.STALK,
            "a hungry full Vampire must return to its night hunt");
        final long decisions = vampire.courtCounters().decisions();
        VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), false, true);
        helper.assertValueEqual(vampire.courtCounters().decisions(), decisions,
            "ordinary semantic decisions must respect their twenty-tick cadence");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void feedingAndReportsRemainDistinct(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final VampireCourtEntity vampire = spawn(fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1));
        final VampireCourtEntity thrall = spawn(fixture, "blood_thrall", CreatureKind.BLOOD_THRALL, new BlockPos(1, 1, 2));
        final Sheep vampireVictim = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        final Sheep thrallVictim = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
        );
        try {
        vampire.setHealth(vampire.getMaxHealth() - 10.0F);
        thrall.setHealth(thrall.getMaxHealth() - 10.0F);
        vampire.setCourtState(vampire.courtState().withPressure(700, helper.getLevel().getGameTime()));
        final float vampireBefore = vampire.getHealth();
        final float thrallBefore = thrall.getHealth();
        helper.assertTrue(vampire.doHurtTarget(helper.getLevel(), vampireVictim),
            "the full Vampire fixture must land its feeding hit");
        helper.assertTrue(thrall.doHurtTarget(helper.getLevel(), thrallVictim),
            "the Blood Thrall fixture must land ordinary melee");
        helper.assertValueEqual(vampire.getHealth(), vampireBefore + 3.0F,
            "full Vampire blood drain must keep its exact three-point base heal");
        helper.assertTrue(vampireVictim.getEffect(MobEffects.HUNGER) != null
                && vampireVictim.getEffect(MobEffects.HUNGER).getAmplifier() == 1
                && vampireVictim.getEffect(MobEffects.HUNGER).getDuration() == 120,
            "full Vampire blood drain must keep Hunger II for exactly 120 ticks");
        helper.assertValueEqual(vampire.courtState().pressure(), 460,
            "ordinary feeding must reduce pressure by exactly 240");
        helper.assertValueEqual(vampire.courtState().reports().size(), 1,
            "a full Vampire feeding hit must record one bounded victim report");
        helper.assertValueEqual(thrall.getHealth(), thrallBefore,
            "Blood Thrall melee must never lifesteal");
        helper.assertTrue(thrallVictim.getEffect(MobEffects.HUNGER) == null,
            "Blood Thrall melee must never apply the full Vampire Hunger rider");
        helper.assertTrue(thrall.courtState().reports().isEmpty(),
            "Blood Thralls must never retain victim reports");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void bloodThrallBindsInterceptsAndWavers(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final VampireCourtEntity leader = spawn(fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1));
        final VampireCourtEntity thrall = spawn(fixture, "blood_thrall", CreatureKind.BLOOD_THRALL, new BlockPos(2, 1, 1));
        final Sheep attacker = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
        );
        try {
        VampireCourtRuntime.bindAssaultMember(thrall, leader);
        VampireCourtRuntime.rememberAttacker(leader, attacker, helper.getLevel().getGameTime());
        final VampireCourtState bound = thrall.courtState();
        thrall.setCourtState(bound.withCadence(
            bound.nextDecisionAt(), bound.nextEntityScanAt(), bound.nextShelterScanAt(),
            bound.nextFeedbackAt(), 0L
        ));
        VampireCourtRuntime.tickForObservation(thrall, helper.getLevel(), false, true);
        helper.assertValueEqual(thrall.courtState().masterId().orElseThrow(), leader.getUUID(),
            "an authored Thrall must bind to its explicit full-Vampire leader");
        helper.assertValueEqual(thrall.courtState().intent(), Intent.INTERCEPT,
            "one direct leader threat must propagate at depth one");
        helper.assertValueEqual(thrall.courtCounters().navigationRequests(), 1L,
            "an inherited direct threat must issue exactly one bounded intercept route request");
        leader.discard();
        VampireCourtRuntime.tickForObservation(thrall, helper.getLevel(), false, true);
        helper.assertValueEqual(thrall.courtState().intent(), Intent.WAVERING,
            "leader loss must clear the order and start wavering");
        VampireCourtRuntime.tickForObservation(thrall, helper.getLevel(), false, true);
        helper.assertValueEqual(thrall.courtState().intent(), Intent.WAVERING,
            "wavering must survive repeated ticks until its exact deadline");
        helper.assertTrue(thrall.getTarget() == null,
            "wavering must release an inherited target immediately");
        final long now = helper.getLevel().getGameTime();
        VillageAssaultRuntime.markRaider(
            thrall, thrall.blockPosition(), 1, AssaultKind.VAMPIRE, SettlementKind.HUMAN, false, false
        );
        thrall.setCourtState(thrall.courtState().resolveMasterLoss(Intent.UNBOUND, 0L)
            .loseMaster(now - VampireCourtRules.WAVERING_TICKS));
        VampireCourtRuntime.tickForObservation(thrall, helper.getLevel(), false, true);
        helper.assertValueEqual(thrall.courtState().intent(), Intent.RETREAT,
            "an assault Thrall must enter retreat at the exact wavering deadline");
        VampireCourtRuntime.tickForObservation(thrall, helper.getLevel(), false, true);
        helper.assertTrue(thrall.isRemoved(),
            "a leaderless assault Thrall retreat must hand off to the existing escape form");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void assaultCompositionPreservesContracts(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
          for (int wave = 1; wave <= 3; wave++) {
            final VampireCourtRules.AssaultComposition composition = VampireCourtRules.assaultComposition(wave);
            final VampireCourtEntity leader = spawn(
                fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1)
            );
            final List<VampireCourtEntity> guards = new ArrayList<>();
            for (int index = 0; index < composition.guards(); index++) {
                final VampireCourtEntity guard = spawn(
                    fixture, "blood_thrall", CreatureKind.BLOOD_THRALL,
                    new BlockPos(2, 1, 1)
                );
                VampireCourtRuntime.bindAssaultMember(guard, leader);
                guards.add(guard);
            }
            helper.assertValueEqual(1 + guards.size(), composition.total(),
                "Vampire court composition must preserve the existing wave total");
            helper.assertValueEqual(leader.courtState().assaultRole(), AssaultRole.UNBOUND,
                "a leader becomes predatory only when the event marker assigns that role");
            guards.forEach(guard -> helper.assertValueEqual(
                guard.courtState().assaultRole(), AssaultRole.BOUND_GUARD,
                "every nonleader must be a bound Blood Thrall"
            ));
            VampireCourtRuntime.markAssaultLeader(leader);
            VampireCourtRuntime.tickForObservation(leader, helper.getLevel(), true, false);
            helper.assertValueEqual(leader.courtState().intent(), Intent.SEEK_SHELTER,
                "urgent survival must outrank a marked leader assault order");
            leader.discard();
            guards.forEach(VampireCourtEntity::discard);
          }

          BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
              .forEach(position -> helper.setBlock(position, Blocks.STONE));
          final VampireCourtEntity objectiveLeader = spawn(
              fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(0, 1, 1)
          );
          final Villager objective = fixture.spawn(
              net.minecraft.world.entity.EntityTypes.VILLAGER,
              new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
          );
          final BlockPos laneStart = helper.absolutePos(new BlockPos(0, 1, 1));
          final BlockPos laneEnd = helper.absolutePos(new BlockPos(2, 1, 1));
          objectiveLeader.snapTo(laneStart.getX() + 0.1D, laneStart.getY(), laneStart.getZ() + 0.5D,
              0.0F, 0.0F);
          objective.snapTo(laneEnd.getX() + 0.9D, laneEnd.getY(), laneEnd.getZ() + 0.5D,
              0.0F, 0.0F);
          final Villager unassigned = fixture.spawn(
              net.minecraft.world.entity.EntityTypes.VILLAGER,
              new BlockPos(1, 1, 2), EntitySpawnReason.EVENT
          );
          final BlockPos leaderPosition = objectiveLeader.blockPosition();
          final BlockPos objectivePosition = objective.blockPosition();
          BlockPos.betweenClosedStream(
              new BlockPos(Math.min(leaderPosition.getX(), objectivePosition.getX()) - 2,
                  leaderPosition.getY() + 3, leaderPosition.getZ() - 2),
              new BlockPos(Math.max(leaderPosition.getX(), objectivePosition.getX()) + 2,
                  leaderPosition.getY() + 3, leaderPosition.getZ() + 2)
          ).forEach(position -> helper.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState()));
          final BlockPos center = helper.absolutePos(new BlockPos(2, 1, 1));
          final VillageAssaultData data = VillageAssaultData.get(helper.getLevel());
          helper.assertTrue(data.begin(center, AssaultKind.VAMPIRE, SettlementKind.HUMAN,
              helper.getLevel().getGameTime()), "the isolated fixture must begin one Vampire assault");
          fixture.onClose(() -> data.active().filter(state -> state.center().equals(center)).ifPresent(
              ignored -> data.finish(helper.getLevel().getGameTime(), 0L, 1.0D)
          ));
          VillageAssaultRuntime.markRaider(
              objectiveLeader, center, 1, AssaultKind.VAMPIRE, SettlementKind.HUMAN, false, true
          );
          data.update(data.active().orElseThrow().waveSpawned(1)
              .addRaiders(Set.of(objectiveLeader.getStringUUID())));
          objectiveLeader.addEffect(new net.minecraft.world.effect.MobEffectInstance(
              MobEffects.FIRE_RESISTANCE, 200, 0, true, false
          ));
          objectiveLeader.clearFire();
          final float unassignedHealth = unassigned.getHealth();
          objectiveLeader.doHurtTarget(helper.getLevel(), unassigned);
          helper.assertValueEqual(unassigned.getHealth(), unassignedHealth,
              "a marked leader must not damage an unassigned resident through the assault feed event");
          helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 0,
              "an unassigned resident hit must not advance the Vampire objective");
          helper.assertTrue(!VillageAssaultRuntime.isBloodDrained(
              unassigned, helper.getLevel().getGameTime()
          ), "an unassigned resident hit must not create the seventy-two-thousand-tick trade lock");
          unassigned.discard();
          runAssaultObjectiveMovement(helper, fixture, objectiveLeader, objective, center, data);
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void runAssaultObjectiveMovement(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final VampireCourtEntity objectiveLeader,
        final Villager objective,
        final BlockPos center,
        final VillageAssaultData data
    ) {
        try {
            helper.assertTrue(helper.getLevel().getBlockState(
                objectiveLeader.blockPosition().above(3)
            ).blocksMotion(), "the real-distance objective lane must have a solid roof");
            helper.assertTrue(VillageAssaultRuntime.assignVampireObjective(
                helper.getLevel(), objectiveLeader, objective
            ), "the live assault coordinator must publish the marked leader's exact objective");
            helper.assertTrue(objectiveLeader.courtState().targetId()
                .filter(objective.getUUID()::equals).isPresent(),
                "the coordinator must persist the exact objective UUID before execution");
            helper.assertTrue(objectiveLeader.courtBaseMayAttack(objective),
                "the existing ArcaneMob/CreatureBehavior admission must admit the objective resident");
            helper.assertTrue(objective.canBeSeenAsEnemy(),
                "the objective resident must remain a live target candidate");
            helper.assertTrue(VampireCourtRuntime.eligibleTarget(objectiveLeader, objective),
                "the marked-leader objective exception must pass the composed admission predicate");
            final double initialDistance = objectiveLeader.distanceToSqr(objective);
            final float initialHealth = objective.getHealth();
            final AtomicBoolean completed = new AtomicBoolean();
            helper.runAfterDelay(5L, () -> {
                if (completed.get()) return;
                helper.assertTrue(
                    objectiveLeader.courtState().intent() == Intent.ASSAULT_LEAD
                        && objectiveLeader.courtState().targetId().filter(objective.getUUID()::equals).isPresent(),
                    "the published objective must remain committed through the first execution window; intent="
                        + objectiveLeader.courtState().intent() + ", target=" + objectiveLeader.courtState().targetId()
                        + ", now=" + helper.getLevel().getGameTime()
                        + ", intentUntil=" + objectiveLeader.courtState().intentExpiresAt()
                        + ", targetUntil=" + objectiveLeader.courtState().targetExpiresAt()
                        + ", nextDecision=" + objectiveLeader.courtState().nextDecisionAt()
                        + ", sky=" + helper.getLevel().canSeeSky(objectiveLeader.blockPosition())
                        + ", fire=" + objectiveLeader.isOnFire() + ", resistance="
                        + objectiveLeader.hasEffect(MobEffects.FIRE_RESISTANCE)
                        + ", marker=" + VillageAssaultRuntime.isAssaultRaider(objectiveLeader)
                        + ", role=" + objectiveLeader.courtState().assaultRole()
                        + ", resolved=" + helper.getLevel().getEntity(objective.getUUID())
                        + ", alive=" + objective.isAlive() + ", removed=" + objective.isRemoved()
                        + ", sameLevel=" + (objective.level() == objectiveLeader.level())
                        + ", invulnerable=" + objective.isInvulnerable()
                        + ", base=" + objectiveLeader.courtBaseMayAttack(objective)
                        + ", enemy=" + objective.canBeSeenAsEnemy()
                        + ", eligible=" + VampireCourtRuntime.eligibleTarget(objectiveLeader, objective)
                        + ", mobTarget=" + objectiveLeader.getTarget()
                );
            });
            helper.onEachTick(() -> {
                if (data.active().map(VillageAssaultData.AssaultState::objectiveProgress).orElse(0) == 0) return;
                try {
                    helper.assertTrue(objectiveLeader.distanceToSqr(objective) < initialDistance - 0.25D,
                        "the attack-only marked leader must actually path toward its assigned objective");
                    helper.assertTrue(objective.getHealth() > 0.0F && objective.getHealth() < initialHealth,
                        "the marked leader must land an actual nonlethal feeding hit");
                    helper.assertValueEqual(data.active().orElseThrow().objectiveProgress(), 1,
                        "the actual feeding hit must advance the existing objective exactly once");
                    helper.assertTrue(VillageAssaultRuntime.isBloodDrained(
                        objective, helper.getLevel().getGameTime()
                    ), "the actual feeding hit must preserve the resident trade lock");
                    completed.set(true);
                    helper.succeed();
                } finally {
                    if (completed.get()) fixture.close();
                }
            });
            helper.runAfterDelay(180L, () -> {
                if (completed.get()) return;
                fixture.close();
                helper.fail("the marked leader never reached and fed its real-distance objective; position="
                    + objectiveLeader.position() + ", distance=" + objectiveLeader.distanceToSqr(objective)
                    + ", intent=" + objectiveLeader.courtState().intent() + ", target="
                    + objectiveLeader.courtState().targetId() + ", mobTarget=" + objectiveLeader.getTarget()
                    + ", navigation=" + objectiveLeader.courtCounters().navigationRequests()
                    + ", failures=" + objectiveLeader.courtState().routeFailures()
                    + ", sky=" + helper.getLevel().canSeeSky(objectiveLeader.blockPosition())
                    + ", fire=" + objectiveLeader.isOnFire() + ", lava=" + objectiveLeader.isInLava()
                    + ", day=" + helper.getLevel().getOverworldClockTime()
                    + ", assault=" + data.active());
            });
        } catch (final RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public static void identityTargetsAndFailuresAreBounded(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        final VampireCourtEntity vampire = spawn(fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(1, 1, 1));
        final VampireCourtEntity thrall = spawn(fixture, "blood_thrall", CreatureKind.BLOOD_THRALL, new BlockPos(2, 1, 1));
        final Villager villager = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
        );
        final Turtle turtle = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.TURTLE, new BlockPos(1, 1, 2), EntitySpawnReason.EVENT
        );
        final ServerPlayer creator = fixture.connectedPlayer(new BlockPos(0, 1, 1));
        final ServerPlayer player = fixture.connectedPlayer(new BlockPos(4, 1, 1));
        final Sheep sameOwnerMob = fixture.spawn(
            net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(3, 1, 2), EntitySpawnReason.EVENT
        );
        try {
        helper.assertFalse(vampire.canAttack(villager), "ordinary Vampires must not target villagers");
        helper.assertFalse(thrall.canAttack(villager), "ordinary Thralls must not target villagers");
        helper.assertFalse(vampire.canAttack(turtle), "ordinary Vampires must not target turtles");
        helper.assertFalse(vampire.canAttack(thrall), "court family members must be non-prey");
        CreatureBehaviorState.bind(vampire, creator.getUUID());
        CreatureBehaviorState.bind(sameOwnerMob, creator.getUUID());
        helper.assertTrue(VampireCourtRuntime.eligibleTarget(vampire, player),
            "a loaded survival mortal must remain ordinary Vampire prey");
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, creator),
            "the persistent creator must remain protected from ordinary prey selection");
        VampireCourtRuntime.rememberAttacker(vampire, creator, helper.getLevel().getGameTime());
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, creator),
            "the persistent creator must remain protected after direct aggression");
        VampireCourtRuntime.rememberAttacker(vampire, sameOwnerMob, helper.getLevel().getGameTime());
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, sameOwnerMob),
            "a Mob with the same persistent owner must remain protected after direct aggression");

        player.setGameMode(GameType.CREATIVE);
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, player),
            "creative players must never be selected or struck");
        player.setGameMode(GameType.SPECTATOR);
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, player),
            "spectator players must never be selected or struck");
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(true);
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, player),
            "invulnerable players must never be selected or struck");
        player.setInvulnerable(false);
        SupernaturalState.setForm(player, SupernaturalForm.VAMPIRE);
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(vampire, player),
            "a player Vampire must remain ordinary non-prey");
        VampireCourtRuntime.rememberAttacker(vampire, player, helper.getLevel().getGameTime());
        helper.assertTrue(VampireCourtRuntime.eligibleTarget(vampire, player),
            "an otherwise legal player Vampire direct aggressor may be met in bounded self-defense");
        VampireCourtRuntime.rememberAttacker(vampire, thrall, helper.getLevel().getGameTime());
        helper.assertTrue(VampireCourtRuntime.eligibleTarget(vampire, thrall),
            "an otherwise legal family direct aggressor may be met in bounded self-defense");
        VampireCourtRuntime.rememberAttacker(vampire, villager, helper.getLevel().getGameTime());
        helper.assertTrue(VampireCourtRuntime.eligibleTarget(vampire, villager),
            "an otherwise legal settlement direct aggressor may be met in bounded self-defense");
        SupernaturalState.setForm(player, SupernaturalForm.NONE);
        final VampireCourtEntity defender = spawn(
            fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(0, 1, 0)
        );
        final VampireCourtEntity familyAggressor = spawn(
            fixture, "blood_thrall", CreatureKind.BLOOD_THRALL, new BlockPos(0, 1, 2)
        );
        CreatureBehaviorState.bind(defender, creator.getUUID());
        final float defenderHealth = defender.getHealth();
        helper.assertTrue(defender.hurtServer(
            helper.getLevel(), helper.getLevel().damageSources().mobAttack(familyAggressor), 1.0F
        ), "a non-owner direct family attacker must land a real accepted hit");
        helper.assertTrue(defender.getHealth() < defenderHealth,
            "the direct-defense fixture must observe actual accepted damage");
        final VampireCourtState threatened = defender.courtState();
        defender.setCourtState(threatened.withCadence(
            0L, threatened.nextEntityScanAt(), threatened.nextShelterScanAt(),
            threatened.nextFeedbackAt(), 0L
        ));
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertValueEqual(defender.courtState().intent(), Intent.INTERCEPT,
            "an admitted direct family attacker must interrupt ordinary schedule behavior");
        helper.assertTrue(defender.getTarget() == familyAggressor
                && VampireCourtRuntime.meleeExecutorMayRun(defender),
            "the admitted direct attacker must receive bounded real self-defense execution");
        familyAggressor.discard();
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "a removed direct attacker must be released before another melee executor tick");

        final VampireCourtEntity ownerBound = spawn(
            fixture, "vampire", CreatureKind.VAMPIRE, new BlockPos(2, 1, 0)
        );
        CreatureBehaviorState.bind(ownerBound, creator.getUUID());
        final float ownerBoundHealth = ownerBound.getHealth();
        helper.assertTrue(ownerBound.hurtServer(
            helper.getLevel(), helper.getLevel().damageSources().playerAttack(creator), 1.0F
        ), "the creator boundary must be tested after a real accepted hit");
        helper.assertTrue(ownerBound.getHealth() < ownerBoundHealth,
            "creator protection must govern retaliation rather than incoming damage immunity");
        VampireCourtRuntime.tickForObservation(ownerBound, helper.getLevel(), false, true);
        helper.assertTrue(ownerBound.getTarget() == null && ownerBound.courtState().recentAttacker().isEmpty(),
            "a creator must never become a retaliation target after an actual hit");

        final long now = helper.getLevel().getGameTime();
        player.setGameMode(GameType.SURVIVAL);
        defender.setCourtState(defender.courtState().rememberAttacker(null, 0L)
            .withTarget(player.getUUID(), now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withIntent(Intent.STALK, now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withCadence(now + 100L, now + 100L, now + 100L, now + 100L, now));
        defender.setTarget(player);
        player.setGameMode(GameType.CREATIVE);
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "a retained target that changes to creative must be released before cadence is due");
        player.setGameMode(GameType.SURVIVAL);

        defender.setCourtState(defender.courtState()
            .withTarget(player.getUUID(), helper.getLevel().getGameTime())
            .withIntent(Intent.STALK, now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS));
        defender.setTarget(player);
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "an expired semantic lease must also clear the live Mob target before melee");

        final BlockPos far = helper.absolutePos(new BlockPos(20, 1, 1));
        player.teleportTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D);
        defender.setCourtState(defender.courtState()
            .withTarget(player.getUUID(), now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withIntent(Intent.STALK, now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS));
        defender.setTarget(player);
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "a retained target outside the sixteen-block radius must be released immediately");

        final BlockPos near = helper.absolutePos(new BlockPos(2, 1, 1));
        player.teleportTo(near.getX() + 0.9D, near.getY(), near.getZ() + 0.5D);
        final List<BlockPos> occluders = BlockPos.betweenClosedStream(
            new BlockPos(1, 1, 0), new BlockPos(1, 2, 2)
        ).map(BlockPos::immutable).toList();
        occluders.forEach(position -> helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(position), Blocks.STONE.defaultBlockState()
        ));
        defender.setCourtState(defender.courtState()
            .withTarget(player.getUUID(), now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withIntent(Intent.FEED, now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS));
        defender.setTarget(player);
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "a FEED commitment that loses line of sight must release before melee execution");
        occluders.forEach(position -> helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(position), Blocks.AIR.defaultBlockState()
        ));

        defender.setCourtState(defender.courtState()
            .withTarget(player.getUUID(), now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS)
            .withIntent(Intent.STALK, now + VampireCourtRules.MAX_CLAIM_LEASE_TICKS));
        defender.setTarget(player);
        CreatureBehaviorState.unbind(defender);
        helper.assertTrue(CreatureBehaviorState.bind(defender, player.getUUID()),
            "the owner-change fixture must establish the retained target as the new creator");
        VampireCourtRuntime.tickForObservation(defender, helper.getLevel(), false, true);
        helper.assertTrue(defender.getTarget() == null && defender.courtState().targetId().isEmpty(),
            "a retained target that becomes the creator must be released immediately");
        vampire.getPersistentData().putBoolean(VillageAssaultRuntime.RAIDER_MARKER, true);
        vampire.getPersistentData().putBoolean(VillageAssaultRuntime.ASSAULT_LEADER, true);
        VampireCourtRuntime.markAssaultLeader(vampire);
        vampire.setCourtState(vampire.courtState().withTarget(
            villager.getUUID(), helper.getLevel().getGameTime() + VampireCourtRules.MAX_CLAIM_LEASE_TICKS
        ));
        vampire.setTarget(villager);
        helper.assertTrue(VampireCourtRuntime.eligibleTarget(vampire, villager),
            "only a marked full-Vampire leader may attack its assigned assault objective");
        thrall.setTarget(villager);
        helper.assertFalse(VampireCourtRuntime.eligibleTarget(thrall, villager),
            "a Blood Thrall must never inherit the leader's villager objective");
        for (final VampireCourtEntity member : List.of(vampire, thrall)) {
            helper.assertFalse(member.isBaby(), "court entities must remain adults");
            helper.assertFalse(member.convertsInWater(), "water must never erase court identity");
            helper.assertTrue(member.getPassengers().isEmpty(), "court entities must not become jockeys");
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertValueEqual(member.getItemBySlot(slot), ItemStack.EMPTY,
                    "court spawn normalization must remove random Zombie equipment");
            }
            helper.assertValueEqual(member.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0D,
                "court entities must never reinforce");
        }
        final VampireCourtState exhausted = vampire.courtState()
            .withShelter("minecraft:overworld", new BlockPos(8, 1, 1), 1_000L)
            .recordRouteResult(false, 100L).recordRouteResult(false, 120L).recordRouteResult(false, 140L);
        helper.assertTrue(exhausted.shelter().isEmpty() && exhausted.retryAfter() == 240L,
            "three rejected routes must release the destination for a hundred ticks");

        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        vampire.saveWithoutId(output);
        final VampireCourtEntity loaded = (VampireCourtEntity) ModEntities.ALL.get("vampire").get().create(
            helper.getLevel(), EntitySpawnReason.LOAD
        );
        helper.assertTrue(loaded != null, "the registered Vampire type must recreate saved state");
        fixture.track(loaded);
        loaded.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult().copy()
        ));
        helper.assertValueEqual(loaded.creatureKind(), CreatureKind.VAMPIRE,
            "save/load must preserve the exact registered identity");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void populationCapsHold(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
        long appraisals = 0L;
        long navigation = 0L;
        for (int index = 0; index < 64; index++) {
            final VampireCourtEntity vampire = spawn(
                fixture, "vampire", CreatureKind.VAMPIRE,
                new BlockPos(1, 1, 1)
            );
            vampire.setCourtState(vampire.courtState()
                .withPressure(800, helper.getLevel().getGameTime())
                .withCadence(0L, 0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L));
            VampireCourtRuntime.tickForObservation(vampire, helper.getLevel(), false, true);
            appraisals += vampire.courtCounters().candidateAppraisals();
            navigation += vampire.courtCounters().navigationRequests();
            helper.assertTrue(vampire.courtCounters().candidateAppraisals() <= VampireCourtRules.MAX_CANDIDATES,
                "one entity scan must enforce the sixteen-candidate cap");
            helper.assertTrue(vampire.courtCounters().navigationRequests() <= 1,
                "one semantic tick must issue at most one navigation request");
            vampire.discard();
        }
        helper.assertTrue(appraisals <= 64L * VampireCourtRules.MAX_CANDIDATES,
            "population work must scale by the fixed per-member candidate cap");
        helper.assertTrue(navigation <= 64L,
            "population navigation must remain at one request per due member");
          helper.succeed();
        } finally {
            fixture.close();
        }
    }

    private static VampireCourtEntity spawn(
        final FixtureScope fixture,
        final String id,
        final CreatureKind kind,
        final BlockPos position
    ) {
        final VampireCourtEntity member = (VampireCourtEntity) fixture.spawn(
            ModEntities.ALL.get(id).get(), position, EntitySpawnReason.EVENT
        );
        fixture.helper.assertValueEqual(member.creatureKind(), kind, "the registered court kind must remain exact");
        return member;
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
            final Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
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
