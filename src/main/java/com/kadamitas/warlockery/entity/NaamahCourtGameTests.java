package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalAdvancement;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.VampireProgressionRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class NaamahCourtGameTests {
    private NaamahCourtGameTests() {
    }

    public static void courtPhasesLatchAndRecover(final GameTestHelper helper) {
        buildFloor(helper);
        BlockPos.betweenClosedStream(new BlockPos(-1, 1, -1), new BlockPos(10, 3, 4))
            .forEach(position -> helper.setBlock(position, Blocks.AIR));
        helper.getLevel().clockManager().setTotalTicks(
            helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 18_000L
        );
        final NaamahEntity naamah = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT
        );
        helper.assertFalse(naamah.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof MeleeAttackGoal),
            "Naamah must use an attack-only executor instead of a second navigation authority");
        final Sheep challenger = helper.spawn(EntityTypes.SHEEP, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
        final ServerPlayer invulnerableWitness = connectedPlayer(helper, new BlockPos(2, 1, 2));
        invulnerableWitness.setInvulnerable(true);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        naamah.setHealth(naamah.getMaxHealth() * 0.67F);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().phase(), Phase.CHORUS_OF_WAVES,
            "two-thirds health must latch Chorus of Waves");
        naamah.setHealth(naamah.getMaxHealth());
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().phase(), Phase.CHORUS_OF_WAVES,
            "healing must never regress a latched phase");
        naamah.setCourtState(NaamahCourtState.read(
            naamah.courtState().write(), helper.getLevel().getGameTime(), naamah.getHealth(), naamah.getMaxHealth()
        ));
        helper.assertValueEqual(naamah.courtState().phase(), Phase.CHORUS_OF_WAVES,
            "serialized court state must reload without phase regression");

        final NaamahEntity saveSubject = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(3, 1, 3), EntitySpawnReason.EVENT
        );
        saveSubject.setNoAi(true);
        final long saveNow = helper.getLevel().getGameTime();
        final Sheep savedChallenger = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(5, 1, 3), EntitySpawnReason.EVENT
        );
        savedChallenger.setNoAi(true);
        final BlockPos staleVeilDestination = helper.absolutePos(new BlockPos(7, 1, 1));
        final String actionDimension = helper.getLevel().dimension().identifier().toString();
        saveSubject.setTarget(savedChallenger);
        saveSubject.setCourtState(saveSubject.courtState()
            .withAnchor(actionDimension, saveSubject.blockPosition())
            .latchPhase(saveSubject.getMaxHealth() * 0.3F, saveSubject.getMaxHealth())
            .withChallenger(savedChallenger.getUUID(), saveNow + 200L)
            .rememberAttacker(savedChallenger.getUUID(), saveNow + 200L)
            .withDestination(staleVeilDestination, saveNow + 200L)
            .withSchedule(saveNow + 200L, saveNow, saveNow + 200L, saveNow + 200L, saveNow)
            .beginAction(Action.VEIL_STEP, saveNow - NaamahCourtRules.MIN_WINDUP_TICKS + 2L,
                savedChallenger.getUUID(), actionDimension));
        final TagValueOutput savedOutput = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        saveSubject.saveWithoutId(savedOutput);
        final var savedEntityData = savedOutput.buildResult();
        saveSubject.discard();
        savedChallenger.discard();
        final NaamahEntity reloaded = (NaamahEntity) ModEntities.ALL.get("naamah").get().create(
            helper.getLevel(), EntitySpawnReason.LOAD
        );
        helper.assertTrue(reloaded != null, "the registered Naamah type must recreate a saved entity");
        reloaded.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), savedEntityData.copy()
        ));
        helper.assertTrue(helper.getLevel().addFreshEntity(reloaded),
            "the saved Naamah entity must re-enter the live level");
        helper.assertValueEqual(reloaded.courtState().phase(), Phase.SOVEREIGN_REFUSAL,
            "an actual entity save/load must preserve the highest latched phase");
        helper.assertValueEqual(reloaded.courtState().action(), Action.VEIL_STEP,
            "an actual entity save/load before execution must preserve a valid windup");
        helper.assertValueEqual(reloaded.courtState().actionTarget(), java.util.Optional.of(savedChallenger.getUUID()),
            "an actual entity save/load must retain the immutable action target UUID");
        helper.assertValueEqual(reloaded.courtState().actionDimension(), java.util.Optional.of(actionDimension),
            "an actual entity save/load must retain the immutable action origin dimension");
        helper.assertValueEqual(reloaded.courtState().actionExecuteAt(), saveNow + 2L,
            "an actual entity save/load must preserve the windup deadline");
        final ServerPlayer reloadReplacement = connectedPlayer(helper, new BlockPos(5, 1, 3));
        reloadReplacement.setNoGravity(true);
        final float replacementHealth = reloadReplacement.getHealth();
        final var reloadedOrigin = reloaded.position();
        reloaded.setTarget(null);
        NaamahCourtRuntime.tick(reloaded, helper.getLevel());
        helper.assertTrue(reloaded.getTarget() == null,
            "a due candidate scan must not replace an immutable action target during windup");
        helper.runAfterDelay(3L, () -> {
            NaamahCourtRuntime.tick(reloaded, helper.getLevel());
            helper.assertValueEqual(reloaded.courtState().action(), Action.NONE,
                "reload without the immutable action target must cancel to recovery");
            helper.assertTrue(reloaded.position().distanceToSqr(reloadedOrigin) <= 0.01D,
                "a stale Veil destination must not execute after its bound target disappears");
            helper.assertValueEqual(reloadReplacement.getHealth(), replacementHealth,
                "an old telegraph must not execute against a replacement challenger");
            helper.assertTrue(reloaded.courtState().recoverUntil() >= helper.getLevel().getGameTime()
                    + NaamahCourtRules.MIN_RECOVERY_TICKS - 1L,
                "missing immutable action identity must cancel into bounded recovery");
            reloadReplacement.setGameMode(GameType.SPECTATOR);
            reloaded.discard();
        });

        final NaamahEntity replacedActionSubject = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(8, 1, 3), EntitySpawnReason.EVENT
        );
        replacedActionSubject.setNoAi(true);
        final ServerPlayer boundActionTarget = connectedPlayer(helper, new BlockPos(7, 1, 3));
        final ServerPlayer externallyReplacedTarget = connectedPlayer(helper, new BlockPos(9, 1, 3));
        final long replacedNow = helper.getLevel().getGameTime();
        final var replacedOrigin = replacedActionSubject.position();
        replacedActionSubject.setCourtState(replacedActionSubject.courtState()
            .withAnchor(actionDimension, replacedActionSubject.blockPosition())
            .withChallenger(boundActionTarget.getUUID(), replacedNow + 200L)
            .withDestination(helper.absolutePos(new BlockPos(8, 1, 1)), replacedNow + 200L)
            .withSchedule(replacedNow + 200L, replacedNow + 200L, replacedNow + 200L,
                replacedNow + 200L, replacedNow)
            .beginAction(Action.VEIL_STEP, replacedNow - NaamahCourtRules.MIN_WINDUP_TICKS,
                boundActionTarget.getUUID(), actionDimension));
        replacedActionSubject.setTarget(externallyReplacedTarget);
        NaamahCourtRuntime.tick(replacedActionSubject, helper.getLevel());
        helper.assertValueEqual(replacedActionSubject.courtState().action(), Action.NONE,
            "an externally replaced windup target must cancel the bound action");
        helper.assertTrue(replacedActionSubject.position().distanceToSqr(replacedOrigin) <= 0.01D,
            "a replaced action target must not consume the original target's Veil destination");
        helper.assertTrue(replacedActionSubject.getTarget() == null,
            "replacement cancellation must release the externally installed target");
        boundActionTarget.setGameMode(GameType.SPECTATOR);
        externallyReplacedTarget.setGameMode(GameType.SPECTATOR);
        replacedActionSubject.discard();

        final long now = helper.getLevel().getGameTime();
        NaamahCourtRuntime.rememberAttacker(naamah, challenger, now);
        naamah.setTarget(challenger);
        challenger.invulnerableTime = 0;
        final AtomicBoolean clearChallengerInvulnerability = new AtomicBoolean(true);
        helper.onEachTick(() -> {
            if (clearChallengerInvulnerability.get()) challenger.invulnerableTime = 0;
        });
        final float challengerHealth = challenger.getHealth();
        naamah.setCourtState(naamah.courtState()
            .withChallenger(challenger.getUUID(), now + 200L)
            .beginAction(Action.COURT_WAVE, now, challenger.getUUID(),
                helper.getLevel().dimension().identifier().toString()));
        final long originalRecoveryEnd = naamah.courtState().recoverUntil();
        helper.runAfterDelay(NaamahCourtRules.MIN_WINDUP_TICKS - 1, () ->
            helper.assertValueEqual(naamah.courtState().action(), Action.COURT_WAVE,
                "strong action must remain telegraphed before its execute tick"));
        helper.runAfterDelay(NaamahCourtRules.MIN_WINDUP_TICKS + 1, () -> {
            clearChallengerInvulnerability.set(false);
            helper.assertValueEqual(naamah.courtState().action(), Action.NONE,
                "a strong action must execute once instead of repeating every tick");
            helper.assertTrue(challenger.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                "Court Wave execution must apply its bounded slowing presentation");
            helper.assertTrue(challenger.getHealth() < challengerHealth,
                "Court Wave must execute for bounded real magic damage after its telegraph");
            helper.assertTrue(challenger.getLastDamageSource() != null
                    && challenger.getLastDamageSource().getEntity() == naamah,
                "Court Wave damage must attribute Naamah as the responsible attacker");
            helper.assertFalse(invulnerableWitness.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                "a victim that rejects Court Wave damage must not receive its slowing rider");
            helper.assertTrue(helper.getLevel().getGameTime() < originalRecoveryEnd,
                "the executor must retain the strong action's recovery window");
        });
        final AtomicBoolean automaticMeleeStarted = new AtomicBoolean(false);
        final AtomicBoolean automaticMeleeResolved = new AtomicBoolean(false);
        final AtomicBoolean obstructedMeleeStarted = new AtomicBoolean(false);
        final AtomicBoolean obstructedMeleeResolved = new AtomicBoolean(false);
        final AtomicReference<Witch> drainVictimRef = new AtomicReference<>();
        final AtomicReference<Ravager> obstructedVictimRef = new AtomicReference<>();
        final long[] navigationBaseline = {-1L};
        final long[] lastNavigationCount = {-1L};
        final long[] lastNavigationAt = {Long.MIN_VALUE};
        final double[] approachOriginX = {0.0D};
        final double[] approachOriginZ = {0.0D};
        final float[] healthBeforeDrain = {0.0F};
        helper.runAfterDelay(NaamahCourtRules.MIN_WINDUP_TICKS + 2, () -> {
            challenger.discard();
            naamah.clearFire();
            naamah.setHealth(naamah.getMaxHealth() - 6.0F);
            final Witch drainVictim = helper.spawn(
                EntityTypes.WITCH, new BlockPos(5, 1, 1), EntitySpawnReason.EVENT
            );
            drainVictim.setNoAi(true);
            drainVictim.setNoGravity(true);
            drainVictim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, 200, 0
            ));
            drainVictim.invulnerableTime = 0;
            drainVictimRef.set(drainVictim);
            final long meleeNow = helper.getLevel().getGameTime();
            NaamahCourtRuntime.rememberAttacker(naamah, drainVictim, meleeNow);
            naamah.setTarget(drainVictim);
            final NaamahCourtState meleeState = naamah.courtState();
            naamah.setCourtState(meleeState
                .withChallenger(drainVictim.getUUID(), meleeNow + 200L)
                .withSchedule(meleeNow + 200L, meleeNow + 200L, meleeNow + 200L,
                    meleeNow + 200L, meleeNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS));
            helper.assertTrue(NaamahCourtRuntime.eligibleTarget(naamah, drainVictim)
                    && naamah.canAttack(drainVictim),
                "the automatic approach fixture must begin with an attackable remembered challenger");
            navigationBaseline[0] = naamah.courtCounters().navigationRequests();
            lastNavigationCount[0] = navigationBaseline[0];
            approachOriginX[0] = naamah.getX();
            approachOriginZ[0] = naamah.getZ();
            healthBeforeDrain[0] = naamah.getHealth();
            automaticMeleeStarted.set(true);
        });
        helper.runAfterDelay(NaamahCourtRules.MIN_WINDUP_TICKS
            + NaamahCourtRules.MIN_RECOVERY_TICKS - 1L, () -> {
                helper.assertTrue(automaticMeleeStarted.get(),
                    "the automatic melee fixture must start during the recovery window");
                helper.assertValueEqual(naamah.courtCounters().navigationRequests(), navigationBaseline[0],
                    "recovery must suppress challenger path requests until its deadline");
                final Witch drainVictim = drainVictimRef.get();
                helper.assertTrue(drainVictim != null,
                    "the automatic melee fixture must retain its live challenger reference");
                helper.assertTrue(drainVictim.getEffect(MobEffects.HUNGER) == null,
                    "the attack-only executor must not hit during strong-action recovery");
            });
        helper.onEachTick(() -> {
            if (!automaticMeleeStarted.get() || automaticMeleeResolved.get()) return;
            final Witch drainVictim = drainVictimRef.get();
            if (drainVictim == null) return;
            final BlockPos fixedTarget = helper.absolutePos(new BlockPos(5, 1, 1));
            drainVictim.teleportTo(
                fixedTarget.getX() + 0.5D, fixedTarget.getY(), fixedTarget.getZ() + 0.5D
            );
            drainVictim.setDeltaMovement(0.0D, 0.0D, 0.0D);
            drainVictim.invulnerableTime = 0;
            helper.assertTrue(naamah.getTarget() == drainVictim,
                "the valid automatic challenger must remain selected during approach; target="
                    + naamah.getTarget() + ", recent=" + naamah.courtState().recentAttacker()
                    + ", attackerExpires=" + naamah.courtState().attackerExpiresAt() + ", now="
                    + helper.getLevel().getGameTime() + ", eligible="
                    + NaamahCourtRuntime.eligibleTarget(naamah, drainVictim) + ", canAttack="
                    + naamah.canAttack(drainVictim) + ", alive=" + drainVictim.isAlive()
                    + ", health=" + drainVictim.getHealth() + ", victimPosition="
                    + drainVictim.position() + ", naamahPosition=" + naamah.position()
                    + ", navigation=" + naamah.courtCounters().navigationRequests());
            final long navigationCount = naamah.courtCounters().navigationRequests();
            if (navigationCount > lastNavigationCount[0]) {
                helper.assertValueEqual(navigationCount, lastNavigationCount[0] + 1L,
                    "one server tick must issue at most one court-owned path request");
                final long requestAt = helper.getLevel().getGameTime();
                if (lastNavigationAt[0] != Long.MIN_VALUE) {
                    helper.assertTrue(requestAt - lastNavigationAt[0]
                            >= NaamahCourtRules.NAVIGATION_INTERVAL_TICKS,
                        "ordinary challenger path requests must remain at least twenty ticks apart");
                }
                lastNavigationCount[0] = navigationCount;
                lastNavigationAt[0] = requestAt;
            }
            final var hunger = drainVictim.getEffect(MobEffects.HUNGER);
            if (hunger == null) return;
            helper.assertTrue(originalRecoveryEnd <= helper.getLevel().getGameTime(),
                "the attack-only executor must wait for the complete recovery window");
            helper.assertTrue(naamah.courtCounters().navigationRequests() > navigationBaseline[0],
                "the court runtime must own the real automatic challenger approach");
            final double approachDistance = Math.pow(naamah.getX() - approachOriginX[0], 2.0D)
                + Math.pow(naamah.getZ() - approachOriginZ[0], 2.0D);
            helper.assertTrue(approachDistance > 0.25D,
                "the court-owned path must move Naamah into melee range before the hit");
            helper.assertTrue(Math.abs(naamah.getHealth() - (healthBeforeDrain[0] + 3.0F)) < 0.001F,
                "the automatic melee hit must retain the exact three-health blood drain");
            helper.assertTrue(hunger.getAmplifier() == 1
                    && hunger.getDuration() >= 119 && hunger.getDuration() <= 120,
                "the automatic melee hit must expose Hunger II on its first observable server tick");
            naamah.setNoAi(true);
            naamah.setHealth(naamah.getMaxHealth() - 6.0F);
            final Sheep exactDrainVictim = helper.spawn(
                EntityTypes.SHEEP, new BlockPos(2, 1, 3), EntitySpawnReason.EVENT
            );
            exactDrainVictim.invulnerableTime = 0;
            final float exactDrainHealth = naamah.getHealth();
            helper.assertTrue(naamah.doHurtTarget(helper.getLevel(), exactDrainVictim),
                "the preserved direct blood-drain contract must still land");
            helper.assertTrue(Math.abs(naamah.getHealth() - (exactDrainHealth + 3.0F)) < 0.001F,
                "the preserved direct blood drain must heal exactly three health");
            final var exactHunger = exactDrainVictim.getEffect(MobEffects.HUNGER);
            helper.assertTrue(exactHunger != null && exactHunger.getAmplifier() == 1
                    && exactHunger.getDuration() == 120,
                "the preserved direct blood drain must apply Hunger II for exactly 120 ticks");
            exactDrainVictim.discard();
            drainVictim.discard();
            invulnerableWitness.setGameMode(GameType.SPECTATOR);
            naamah.setNoAi(false);
            final BlockPos obstructedOrigin = helper.absolutePos(new BlockPos(1, 1, 3));
            naamah.teleportTo(
                obstructedOrigin.getX() + 0.5D, obstructedOrigin.getY(), obstructedOrigin.getZ() + 0.5D
            );
            naamah.getNavigation().stop();
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(2, y, 3), Blocks.IRON_BARS);
            }
            final Ravager obstructedVictim = helper.spawn(
                EntityTypes.RAVAGER, new BlockPos(3, 1, 3), EntitySpawnReason.EVENT
            );
            final BlockPos obstructedTarget = helper.absolutePos(new BlockPos(3, 1, 3));
            obstructedVictim.teleportTo(
                obstructedTarget.getX() + 0.59D, obstructedTarget.getY(), obstructedTarget.getZ() + 0.5D
            );
            obstructedVictim.setNoAi(true);
            obstructedVictim.setNoGravity(true);
            obstructedVictim.invulnerableTime = 0;
            obstructedVictimRef.set(obstructedVictim);
            final long obstructedNow = helper.getLevel().getGameTime();
            NaamahCourtRuntime.rememberAttacker(naamah, obstructedVictim, obstructedNow);
            naamah.setTarget(obstructedVictim);
            naamah.setCourtState(naamah.courtState().finishAction()
                .withRouteRetry(0, 0L)
                .withChallenger(obstructedVictim.getUUID(), obstructedNow + 200L)
                .withSchedule(obstructedNow + 200L, obstructedNow + 200L,
                    obstructedNow + 200L, obstructedNow + 200L,
                    obstructedNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS));
            naamah.getSensing().tick();
            helper.assertTrue(naamah.isWithinMeleeAttackRange(obstructedVictim),
                "the obstruction fixture must place its wide challenger inside melee reach");
            helper.assertFalse(naamah.getSensing().hasLineOfSight(obstructedVictim),
                "the stacked-pane fixture must block the attack-only executor's line of sight");
            final long obstructedNavigation = naamah.courtCounters().navigationRequests();
            automaticMeleeResolved.set(true);
            obstructedMeleeStarted.set(true);
            NaamahCourtRuntime.tick(naamah, helper.getLevel());
            helper.assertValueEqual(naamah.courtCounters().navigationRequests(), obstructedNavigation + 1L,
                "melee reach without line of sight must request one bounded court-owned reposition path");
            helper.assertValueEqual(naamah.courtState().routeFailures(), 0,
                "the open route around an adjacent obstruction must be accepted");
            helper.assertFalse(naamah.getNavigation().isDone(),
                "an accepted obstruction route must remain active for real repositioning");
        });
        helper.onEachTick(() -> {
            if (!obstructedMeleeStarted.get() || obstructedMeleeResolved.get()) return;
            final Ravager obstructedVictim = obstructedVictimRef.get();
            if (obstructedVictim == null) return;
            obstructedVictim.setDeltaMovement(0.0D, 0.0D, 0.0D);
            obstructedVictim.invulnerableTime = 0;
            final var hunger = obstructedVictim.getEffect(MobEffects.HUNGER);
            if (hunger == null) return;
            helper.assertTrue(naamah.getSensing().hasLineOfSight(obstructedVictim),
                "court-owned repositioning must expose a real attack-only line of sight");
            helper.assertTrue(hunger.getAmplifier() == 1,
                "the attack-only executor must land after routing around the obstruction");
            obstructedMeleeResolved.set(true);
            helper.succeed();
        });
        helper.runAfterDelay(180L, () -> {
            final Witch drainVictim = drainVictimRef.get();
            helper.assertTrue(automaticMeleeResolved.get() && obstructedMeleeResolved.get(),
                "court-owned challenger approach must reach and strike within the bounded test window; navigation="
                    + naamah.courtCounters().navigationRequests() + ", target=" + naamah.getTarget()
                    + ", position=" + naamah.position() + ", targetPosition="
                    + (drainVictim == null ? "missing" : drainVictim.position()) + ", action="
                    + naamah.courtState().action() + ", recovery=" + naamah.courtState().recoverUntil()
                    + ", retry=" + naamah.courtState().retryAfter() + ", failures="
                    + naamah.courtState().routeFailures() + ", navigationDone="
                    + naamah.getNavigation().isDone() + ", meleeRange="
                    + (drainVictim != null && naamah.isWithinMeleeAttackRange(drainVictim)));
        });
    }

    public static void trialDefeatConcludesAudience(final GameTestHelper helper) {
        buildFloor(helper);
        final ServerPlayer owner = connectedPlayer(helper, new BlockPos(0, 1, 1));
        helper.assertTrue(SupernaturalAdvancement.beginVampire(owner), "trial owner must begin as a vampire");
        SupernaturalProgression.setLevel(owner, SupernaturalProgression.Path.VAMPIRE, 6);
        final NaamahEntity naamah = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        naamah.getPersistentData().putString(SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER,
            owner.getStringUUID());
        naamah.setHealth(10.0F);
        helper.assertTrue(naamah.hurtServer(helper.getLevel(), owner.damageSources().playerAttack(owner), 20.0F),
            "the exact qualifying lethal trial hit must be intercepted");
        helper.assertValueEqual(naamah.getHealth(), 1.0F, "trial defeat must leave exactly one health");
        helper.assertTrue(SupernaturalProgression.counter(
            owner, SupernaturalProgression.Path.VAMPIRE, VampireProgressionRules.Metric.NAAMAH_DEFEATED
        ) == 1L, "trial defeat counter must increment exactly once");
        helper.assertTrue(naamah.courtState().audienceConcluded(), "trial defeat must conclude the audience");
        helper.assertFalse(naamah.canAttack(owner), "concluded owner must be released");
        helper.succeed();
    }

    public static void sunlightWaterAndSingularLifecycle(final GameTestHelper helper) {
        buildFloor(helper);
        final AtomicBoolean ordinaryFireEscapeVerified = new AtomicBoolean(false);
        final NaamahEntity hostileSource = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT
        );
        final Identifier hostileSpawnBonus = Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
        final Identifier hostileLeaderBonus = Identifier.withDefaultNamespace("leader_zombie_bonus");
        hostileSource.setBaby(true);
        hostileSource.setCanBreakDoors(true);
        hostileSource.setCanPickUpLoot(true);
        hostileSource.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        hostileSource.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        hostileSource.getAttribute(Attributes.FOLLOW_RANGE).addPermanentModifier(new AttributeModifier(
            hostileSpawnBonus, 12.0D, AttributeModifier.Operation.ADD_VALUE
        ));
        hostileSource.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(
            hostileLeaderBonus, 20.0D, AttributeModifier.Operation.ADD_VALUE
        ));
        hostileSource.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.75D);
        final TagValueOutput hostileOutput = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
        );
        hostileSource.saveWithoutId(hostileOutput);
        final var hostileData = hostileOutput.buildResult();
        hostileData.putBoolean("PersistenceRequired", false);
        hostileData.putInt("InWaterTime", 600);
        hostileData.putInt("DrownedConversionTime", 1);
        hostileSource.discard();
        final NaamahEntity naamah = (NaamahEntity) ModEntities.ALL.get("naamah").get().create(
            helper.getLevel(), EntitySpawnReason.LOAD
        );
        helper.assertTrue(naamah != null, "the registered Naamah type must recreate hostile legacy NBT");
        naamah.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), hostileData.copy()
        ));
        helper.assertTrue(helper.getLevel().addFreshEntity(naamah),
            "the hostile legacy Naamah load must enter the live level");
        final boolean hostileLifecycleNormalized = !naamah.isBaby()
            && !naamah.canBreakDoors()
            && !naamah.canPickUpLoot()
            && naamah.isPersistenceRequired()
            && java.util.Arrays.stream(EquipmentSlot.values()).allMatch(slot -> naamah.getItemBySlot(slot).isEmpty())
            && naamah.getAttribute(Attributes.FOLLOW_RANGE).getModifier(hostileSpawnBonus) == null
            && naamah.getAttribute(Attributes.MAX_HEALTH).getModifier(hostileLeaderBonus) == null
            && naamah.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) == 0.0D
            && !naamah.isUnderWaterConverting();
        helper.assertTrue(hostileLifecycleNormalized,
            "hostile legacy Zombie NBT must normalize atomically: baby=" + naamah.isBaby()
                + ", doors=" + naamah.canBreakDoors() + ", pickup=" + naamah.canPickUpLoot()
                + ", persistent=" + naamah.isPersistenceRequired()
                + ", mainhandEmpty=" + naamah.getMainHandItem().isEmpty()
                + ", spawnBonus=" + naamah.getAttribute(Attributes.FOLLOW_RANGE).getModifier(hostileSpawnBonus)
                + ", leaderBonus=" + naamah.getAttribute(Attributes.MAX_HEALTH).getModifier(hostileLeaderBonus)
                + ", reinforcement=" + naamah.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                + ", converting=" + naamah.isUnderWaterConverting());
        helper.assertTrue(naamah.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE) != null
                && naamah.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) == 0.0D,
            "spawn-finalized Naamah must have zero same-type reinforcement chance");
        naamah.setAirSupply(1);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.getAirSupply(), naamah.getMaxAirSupply(),
            "water composure must preserve full air");
        helper.assertFalse(naamah.convertsInWater(), "Naamah must never start Drowned conversion");
        final float health = naamah.getHealth();
        helper.assertTrue(naamah.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), 2.0F),
            "ordinary fire damage must no longer be rejected by type immunity");
        helper.assertTrue(naamah.getHealth() < health, "ordinary fire must cause real damage");
        naamah.clearFire();
        naamah.setHealth(naamah.getMaxHealth());
        final BlockPos sunlightColumn = naamah.blockPosition();
        for (int y = sunlightColumn.getY() + 1; y < helper.getLevel().getMaxY(); y++) {
            helper.getLevel().setBlock(sunlightColumn.atY(y), Blocks.AIR.defaultBlockState(), 2);
        }
        helper.assertTrue(helper.getLevel().canSeeSky(naamah.blockPosition()),
            "the daylight assertion requires an actually sky-exposed Naamah");
        helper.getLevel().clockManager().setTotalTicks(
            helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 6_000L
        );
        helper.assertTrue(helper.getLevel().getOverworldClockTime() % 24_000L < 13_000L,
            "the daylight assertion requires the overworld clock to be daytime");
        naamah.tickCount = Math.floorMod(-naamah.getId(), 20);
        naamah.customServerAiStep(helper.getLevel());
        helper.assertTrue(naamah.getRemainingFireTicks() > 0,
            "the retained SUNLIGHT_WEAKNESS profile must ignite sky-exposed Naamah on its scheduled pulse");
        final double sunlightX = naamah.getX();
        final double sunlightY = naamah.getY();
        final double sunlightZ = naamah.getZ();
        final AtomicBoolean pinInSunlight = new AtomicBoolean(true);
        final AtomicBoolean sunlightIgnited = new AtomicBoolean(false);
        final AtomicBoolean grenadeIgnited = new AtomicBoolean(false);
        helper.onEachTick(() -> {
            if (pinInSunlight.get()) {
                naamah.teleportTo(sunlightX, sunlightY, sunlightZ);
                naamah.getNavigation().stop();
                if (naamah.getRemainingFireTicks() > 0) sunlightIgnited.set(true);
            }
            if (!pinInSunlight.get() && naamah.getRemainingFireTicks() > 0) grenadeIgnited.set(true);
        });
        helper.runAfterDelay(1, () -> {
            pinInSunlight.set(false);
            helper.assertTrue(sunlightIgnited.get(),
                "ordinary server ticks must invoke the retained SUNLIGHT_WEAKNESS ignition profile");
            naamah.invulnerableTime = 0;
            final float beforeDaylightFire = naamah.getHealth();
            helper.assertTrue(naamah.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), 1.0F),
                "profile-ignited Naamah must accept the ordinary on-fire damage path");
            helper.assertTrue(naamah.getHealth() < beforeDaylightFire,
                "profile ignition must lead to real Naamah fire damage");
            naamah.clearFire();
            naamah.setHealth(naamah.getMaxHealth());
            naamah.invulnerableTime = 0;
            helper.getLevel().clockManager().setTotalTicks(
                helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 18_000L
            );
            final ServerPlayer thrower = connectedPlayer(helper, new BlockPos(1, 4, 1));
            thrower.setXRot(90.0F);
            final ItemStack grenade = new ItemStack(ModItems.ALL.get("sungrenade").get());
            thrower.setItemInHand(InteractionHand.MAIN_HAND, grenade);
            final float beforeGrenade = naamah.getHealth();
            grenade.use(helper.getLevel(), thrower, InteractionHand.MAIN_HAND);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(naamah.getHealth() < beforeGrenade,
                    "an actual thrown Sun Grenade must cause real Naamah damage");
                helper.assertTrue(grenadeIgnited.get(), "an actual Sun Grenade must ignite Naamah");
                naamah.clearFire();
                naamah.setHealth(naamah.getMaxHealth());
                thrower.setGameMode(GameType.SPECTATOR);
                naamah.setTarget(null);
                naamah.setNoAi(false);
                naamah.setNoGravity(true);
                final BlockPos waterCenter = new BlockPos(1, 1, 1);
                for (int x = 0; x <= 2; x++) {
                    for (int z = 0; z <= 2; z++) {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                        }
                    }
                }
                final BlockPos absoluteWater = helper.absolutePos(waterCenter);
                final AtomicBoolean confineInWater = new AtomicBoolean(true);
                helper.onEachTick(() -> {
                    if (confineInWater.get()) {
                        naamah.teleportTo(absoluteWater.getX() + 0.5D, absoluteWater.getY(), absoluteWater.getZ() + 0.5D);
                    }
                });
                helper.runAfterDelay(620, () -> {
                    confineInWater.set(false);
                    helper.assertTrue(naamah.isAlive() && naamah.getType() == ModEntities.ALL.get("naamah").get(),
                        "long water confinement must preserve Naamah's registered identity");
                    helper.assertFalse(naamah.isUnderWaterConverting(),
                        "more than six hundred submerged ticks must never start Drowned conversion");
                    helper.assertValueEqual(naamah.getAirSupply(), naamah.getMaxAirSupply(),
                        "long water confinement must preserve full air");
                    naamah.discard();
                    thrower.setGameMode(GameType.SPECTATOR);
                    for (int x = -1; x <= 10; x++) {
                        for (int z = -1; z <= 4; z++) {
                            for (int y = 1; y <= 3; y++) {
                                helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                            }
                        }
                    }
                    final NaamahEntity burningEscape = (NaamahEntity) helper.spawn(
                        ModEntities.ALL.get("naamah").get(), new BlockPos(8, 1, 2), EntitySpawnReason.EVENT
                    );
                    helper.runAfterDelay(5L, () -> {
                        helper.getLevel().clockManager().setTotalTicks(
                            helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 18_000L
                        );
                        final long escapeNow = helper.getLevel().getGameTime();
                        final BlockPos escapeOrigin = burningEscape.blockPosition();
                        final var escapeStart = burningEscape.position();
                        burningEscape.clearFire();
                        burningEscape.getNavigation().stop();
                        burningEscape.setCourtState(NaamahCourtState.empty()
                            .withAnchor(helper.getLevel().dimension().identifier().toString(), escapeOrigin)
                            .withSchedule(escapeNow + 200L, escapeNow + 200L, escapeNow,
                                escapeNow + 200L,
                                escapeNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS));
                        burningEscape.igniteForSeconds(20.0F);
                        helper.assertTrue(burningEscape.isAlive() && burningEscape.onGround()
                                && burningEscape.isOnFire(),
                            "the ordinary-fire escape fixture must be live, grounded, and burning on its clear surface");
                        final long navigationBeforeEscape = burningEscape.courtCounters().navigationRequests();
                        NaamahCourtRuntime.tick(burningEscape, helper.getLevel());
                        final var escapeDestination = burningEscape.courtState().destination();
                        helper.assertTrue(escapeDestination.isPresent()
                                && !escapeDestination.orElseThrow().equals(escapeOrigin),
                            "ordinary entity fire must select a genuinely different escape destination; origin="
                                + escapeOrigin + ", destination=" + escapeDestination + ", localHazard="
                                + burningEscape.courtState().localHazard() + ", chargedReads="
                                + burningEscape.courtCounters().maximumBlockStatesPerSearch());
                        helper.assertValueEqual(burningEscape.courtCounters().navigationRequests(),
                            navigationBeforeEscape + 1L,
                            "ordinary entity fire must issue exactly one cadence-gated escape path request");
                        helper.assertFalse(burningEscape.getNavigation().isDone(),
                            "ordinary entity fire must start real navigation toward the different destination");
                        helper.getLevel().clockManager().setTotalTicks(
                            helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 6_000L
                        );
                        helper.runAfterDelay(20L, () -> {
                            helper.assertTrue(burningEscape.position().distanceToSqr(escapeStart) > 0.01D,
                                "ordinary entity fire must produce observable displacement on a clear loaded surface; origin="
                                    + escapeStart + ", current=" + burningEscape.position() + ", destination="
                                    + burningEscape.courtState().destination() + ", navigationDone="
                                    + burningEscape.getNavigation().isDone());
                            burningEscape.clearFire();
                            burningEscape.discard();
                            ordinaryFireEscapeVerified.set(true);
                        });
                    });
                    helper.getLevel().clockManager().setTotalTicks(
                        helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 6_000L
                    );
                    final NaamahEntity resting = (NaamahEntity) helper.spawn(
                        ModEntities.ALL.get("naamah").get(), new BlockPos(3, 1, 3), EntitySpawnReason.EVENT
                    );
                    for (int x = 2; x <= 4; x++) {
                        for (int z = 2; z <= 4; z++) {
                            helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                        }
                    }
                    final BlockPos restRoof = helper.absolutePos(new BlockPos(3, 4, 3));
                    helper.assertTrue(helper.getLevel().getBlockState(restRoof).is(Blocks.STONE),
                        "the veiled-rest fixture must install its roof block");
                    helper.runAfterDelay(10, () -> {
                        final long restNow = helper.getLevel().getGameTime();
                        final BlockPos restCenter = helper.absolutePos(new BlockPos(3, 1, 3));
                        resting.teleportTo(
                            restCenter.getX() + 0.5D, restCenter.getY(), restCenter.getZ() + 0.5D
                        );
                        resting.setDeltaMovement(0.0D, 0.0D, 0.0D);
                        resting.getNavigation().stop();
                        resting.setTarget(null);
                        resting.setCourtState(resting.courtState()
                            .withAnchor(helper.getLevel().dimension().identifier().toString(), resting.blockPosition())
                            .withSchedule(restNow + 100L, restNow + 100L, restNow + 100L, restNow, restNow));
                        final var restPosition = resting.position();
                        resting.clearFire();
                        helper.assertFalse(helper.getLevel().canSeeSky(resting.blockPosition()),
                            "the veiled-rest fixture must place a real roof above Naamah");
                        helper.assertFalse(resting.isInLava() || resting.isOnFire(),
                            "the veiled-rest fixture must begin outside urgent hazards");
                        NaamahCourtRuntime.tick(resting, helper.getLevel());
                        helper.assertValueEqual(resting.courtState().nextAmbientFeedbackAt(), restNow + 400L,
                            "sheltered daylight rest must use the sparse veiled-rest cadence");
                        helper.runAfterDelay(60, () -> {
                            helper.assertTrue(ordinaryFireEscapeVerified.get(),
                                "the lifecycle fixture must complete the real ordinary-fire escape regression");
                            helper.assertTrue(resting.position().distanceToSqr(restPosition) <= 0.25D,
                                "a safe court without challengers must hold position instead of inheriting random stroll; origin="
                                    + restPosition + ", current=" + resting.position() + ", delta="
                                    + resting.getDeltaMovement() + ", onGround=" + resting.onGround()
                                    + ", floor=" + helper.getLevel().getBlockState(resting.blockPosition().below())
                                    + ", navigationDone=" + resting.getNavigation().isDone() + ", target="
                                    + resting.getTarget() + ", canSeeSky="
                                    + helper.getLevel().canSeeSky(resting.blockPosition()));
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    public static void courtReleasesInvalidTargets(final GameTestHelper helper) {
        buildFloor(helper);
        helper.getLevel().clockManager().setTotalTicks(
            helper.getLevel().registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), 18_000L
        );
        final NaamahEntity naamah = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(1, 1, 1), EntitySpawnReason.EVENT
        );
        final NamiEntity nami = helper.spawn(ModEntities.NAMI.get(), new BlockPos(2, 1, 1), EntitySpawnReason.EVENT);
        naamah.setTarget(nami);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.getTarget() != nami, "Nami must be released as a protected target");
        helper.assertFalse(naamah.canAttack(nami), "Naamah must never attack Nami");

        final Villager villager = helper.spawn(
            EntityTypes.VILLAGER, new BlockPos(2, 1, 2), EntitySpawnReason.EVENT
        );
        final IronGolem golem = helper.spawn(
            EntityTypes.IRON_GOLEM, new BlockPos(3, 1, 2), EntitySpawnReason.EVENT
        );
        final Turtle turtle = helper.spawn(
            EntityTypes.TURTLE, new BlockPos(4, 1, 2), EntitySpawnReason.EVENT
        );
        final ArcaneMob vampire = (ArcaneMob) helper.spawn(
            ModEntities.ALL.get("vampire").get(), new BlockPos(2, 1, 3), EntitySpawnReason.EVENT
        );
        final ArcaneMob bloodThrall = (ArcaneMob) helper.spawn(
            ModEntities.ALL.get("blood_thrall").get(), new BlockPos(3, 1, 3), EntitySpawnReason.EVENT
        );
        final NaamahEntity courtAlignedNaamah = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(4, 1, 3), EntitySpawnReason.EVENT
        );
        final List<LivingEntity> protectedCourt = List.of(
            nami, villager, golem, turtle, vampire, bloodThrall, courtAlignedNaamah
        );
        for (final LivingEntity protectedEntity : protectedCourt) {
            final float protectedHealth = protectedEntity.getHealth();
            final var protectedData = protectedEntity.getPersistentData().copy();
            naamah.setTarget(protectedEntity);
            NaamahCourtRuntime.rememberAttacker(naamah, protectedEntity, helper.getLevel().getGameTime());
            NaamahCourtRuntime.tick(naamah, helper.getLevel());
            helper.assertTrue(naamah.getTarget() != protectedEntity,
                "court targeting must release every protected family even after external assignment");
            helper.assertFalse(naamah.canAttack(protectedEntity),
                "court targeting must reject Nami, village, turtle, vampire, thrall, and Naamah families");
            helper.assertValueEqual(protectedEntity.getHealth(), protectedHealth,
                "court presentation must not damage a protected family member");
            helper.assertValueEqual(protectedEntity.getPersistentData(), protectedData,
                "court presentation must not mutate a protected family member's persistent state");
            helper.assertTrue(naamah.courtState().recentAttacker().isEmpty(),
                "protected family members must not enter the direct-attacker memory");
        }
        final int zombieVillagersBefore = helper.getLevel().getEntitiesOfClass(
            ZombieVillager.class, naamah.getBoundingBox().inflate(24.0D)
        ).size();
        villager.kill(helper.getLevel());
        naamah.killedEntity(helper.getLevel(), villager, helper.getLevel().damageSources().mobAttack(naamah));
        helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(
            ZombieVillager.class, naamah.getBoundingBox().inflate(24.0D)
        ).size(), zombieVillagersBefore, "Naamah must never create a Zombie Villager");
        protectedCourt.forEach(entity -> {
            if (entity != nami) entity.discard();
        });

        final NaamahEntity handoffNaamah = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(6, 1, 1), EntitySpawnReason.EVENT
        );
        final Sheep distantHandoff = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(10, 1, 1), EntitySpawnReason.EVENT
        );
        distantHandoff.setNoAi(true);
        distantHandoff.setNoGravity(true);
        final long handoffNow = helper.getLevel().getGameTime();
        NaamahCourtRuntime.rememberAttacker(handoffNaamah, distantHandoff, handoffNow);
        handoffNaamah.setTarget(distantHandoff);
        handoffNaamah.setCourtState(handoffNaamah.courtState()
            .withAnchor(helper.getLevel().dimension().identifier().toString(), handoffNaamah.blockPosition())
            .withChallenger(distantHandoff.getUUID(), handoffNow + 200L)
            .withSchedule(handoffNow + 200L, handoffNow + 200L, handoffNow + 200L,
                handoffNow + 200L, handoffNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS));
        final var attackOnlyGoal = handoffNaamah.getGoalSelector().getAvailableGoals().stream()
            .map(goal -> goal.getGoal())
            .filter(goal -> goal.getClass().getSimpleName().equals("CourtMeleeGoal"))
            .findFirst().orElseThrow();
        helper.assertFalse(handoffNaamah.isWithinMeleeAttackRange(distantHandoff),
            "the melee handoff fixture must begin outside actual attack reach");
        helper.assertTrue(NaamahCourtRuntime.challengerApproachMayRun(handoffNaamah),
            "the court runtime must retain broader challenger approach authority before melee handoff");
        helper.assertFalse(attackOnlyGoal.canUse(),
            "the attack-only melee goal must not own LOOK or aggression before reach and line-of-sight handoff");
        handoffNaamah.discard();
        distantHandoff.discard();

        final NaamahCourtState eligibilityState = naamah.courtState();
        final ServerPlayer creativePlayer = connectedPlayer(helper, new BlockPos(5, 1, 3));
        creativePlayer.setGameMode(GameType.CREATIVE);
        helper.assertFalse(NaamahCourtRuntime.eligibleTarget(naamah, creativePlayer),
            "creative players must be excluded before direct court scans or Wave inspection");
        naamah.setTarget(creativePlayer);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.getTarget() != creativePlayer,
            "an externally assigned creative player must be released immediately");

        final Sheep invulnerableAttacker = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(5, 1, 2), EntitySpawnReason.EVENT
        );
        invulnerableAttacker.setNoAi(true);
        invulnerableAttacker.setInvulnerable(true);
        final long eligibilityNow = helper.getLevel().getGameTime();
        naamah.setCourtState(eligibilityState.rememberAttacker(
            invulnerableAttacker.getUUID(), eligibilityNow + 200L
        ));
        helper.assertFalse(NaamahCourtRuntime.eligibleTarget(naamah, invulnerableAttacker),
            "an invulnerable entity must be excluded even when malformed attacker memory names it");
        naamah.setTarget(invulnerableAttacker);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.getTarget() != invulnerableAttacker,
            "an externally assigned entity that cannot be seen as an enemy must be released");
        naamah.setCourtState(eligibilityState);
        creativePlayer.setGameMode(GameType.SPECTATOR);
        invulnerableAttacker.discard();

        final List<ServerPlayer> crowdedCandidates = new ArrayList<>();
        final ServerPlayer stableCurrent = connectedPlayer(helper, new BlockPos(20, 1, 1));
        stableCurrent.setNoGravity(true);
        crowdedCandidates.add(stableCurrent);
        for (int index = 0; index < 20; index++) {
            final ServerPlayer candidate = connectedPlayer(helper,
                new BlockPos(2 + index % 5, 1, 1 + index / 5));
            candidate.setNoGravity(true);
            crowdedCandidates.add(candidate);
        }
        final ServerPlayer outsideSpatialQuery = connectedPlayer(helper, new BlockPos(40, 1, 1));
        outsideSpatialQuery.setNoGravity(true);
        final long crowdedScanAt = helper.getLevel().getGameTime();
        naamah.setCourtState(naamah.courtState()
            .withChallenger(stableCurrent.getUUID(), crowdedScanAt + 200L)
            .withSchedule(crowdedScanAt + 10L, crowdedScanAt, crowdedScanAt + 40L,
                crowdedScanAt + 200L, crowdedScanAt));
        naamah.setTarget(stableCurrent);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.getTarget(), stableCurrent,
            "a valid current challenger must not flicker under more than sixteen eligible players");
        helper.assertTrue(naamah.courtCounters().maximumCandidatesRetained() <= NaamahCourtRules.MAX_CANDIDATES,
            "crowded candidate retention must remain capped at sixteen");
        helper.assertTrue(naamah.courtCounters().maximumEntitiesVisitedPerCandidateScan()
                >= crowdedCandidates.size(),
            "the live crowded candidate traversal must actually inspect more than sixteen nearby players");
        helper.assertTrue(naamah.getTarget() != outsideSpatialQuery,
            "the spatial candidate traversal must exclude the distant eligible player");

        final long repeatedScanAt = helper.getLevel().getGameTime();
        naamah.setCourtState(naamah.courtState().withSchedule(
            repeatedScanAt + 10L, repeatedScanAt, repeatedScanAt + 40L, repeatedScanAt + 200L, repeatedScanAt
        ));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.getTarget(), stableCurrent,
            "repeated crowded scans must preserve the stable current challenger");

        final Sheep crowdedWaveTarget = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(2, 1, 1), EntitySpawnReason.EVENT
        );
        crowdedWaveTarget.setNoAi(true);
        crowdedWaveTarget.invulnerableTime = 0;
        crowdedCandidates.forEach(player -> player.invulnerableTime = 0);
        final List<Float> crowdedHealthBefore = crowdedCandidates.stream()
            .map(ServerPlayer::getHealth).toList();
        final float crowdedWaveTargetHealth = crowdedWaveTarget.getHealth();
        final long crowdedWaveAt = helper.getLevel().getGameTime();
        NaamahCourtRuntime.rememberAttacker(naamah, crowdedWaveTarget, crowdedWaveAt);
        naamah.setTarget(crowdedWaveTarget);
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(crowdedWaveTarget.getUUID(), crowdedWaveAt + 200L)
            .withSchedule(crowdedWaveAt + 200L, crowdedWaveAt + 200L,
                crowdedWaveAt + 200L, crowdedWaveAt + 200L, crowdedWaveAt)
            .beginAction(Action.COURT_WAVE, crowdedWaveAt - NaamahCourtRules.MIN_WINDUP_TICKS));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        final long crowdedVictimsHurt = java.util.stream.IntStream.range(0, crowdedCandidates.size())
            .filter(index -> crowdedCandidates.get(index).getHealth() < crowdedHealthBefore.get(index))
            .count();
        helper.assertValueEqual(naamah.courtCounters().maximumEntitiesVisitedPerWave(),
            NaamahCourtRules.MAX_CANDIDATES,
            "a crowded Court Wave must abort after exactly sixteen unique living candidates");
        helper.assertTrue(crowdedVictimsHurt <= NaamahCourtRules.MAX_CANDIDATES,
            "a crowded Court Wave must affect no more living victims than it inspected");
        helper.assertTrue(crowdedWaveTarget.getHealth() < crowdedWaveTargetHealth,
            "the preseeded current challenger must receive Wave damage under crowding; before="
                + crowdedWaveTargetHealth + ", after=" + crowdedWaveTarget.getHealth());
        helper.assertTrue(crowdedWaveTarget.getLastDamageSource() != null
                && crowdedWaveTarget.getLastDamageSource().getEntity() == naamah,
            "the preseeded current challenger must retain Naamah attribution under crowding; source="
                + crowdedWaveTarget.getLastDamageSource());

        naamah.setTarget(stableCurrent);
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(stableCurrent.getUUID(), crowdedWaveAt + 200L));

        final BlockPos releasedPosition = helper.absolutePos(new BlockPos(40, 1, 2));
        stableCurrent.teleportTo(releasedPosition.getX() + 0.5D, releasedPosition.getY(), releasedPosition.getZ() + 0.5D);
        final long releaseScanAt = helper.getLevel().getGameTime();
        naamah.setCourtState(naamah.courtState().withSchedule(
            releaseScanAt + 10L, releaseScanAt, releaseScanAt + 40L, releaseScanAt + 200L, releaseScanAt
        ));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.getTarget() != null && naamah.getTarget() != stableCurrent,
            "an invalidated current challenger must release to another bounded eligible candidate");

        final java.util.UUID concludedOwner = java.util.UUID.randomUUID();
        final ServerPlayer otherPlayer = connectedPlayer(helper, new BlockPos(3, 1, 1));
        naamah.setCourtState(naamah.courtState().conclude(concludedOwner).withSchedule(
            helper.getLevel().getGameTime() + 10L, helper.getLevel().getGameTime(),
            helper.getLevel().getGameTime() + 40L, helper.getLevel().getGameTime() + 200L,
            helper.getLevel().getGameTime()
        ));
        naamah.setTarget(null);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.getTarget() != null && !naamah.getTarget().getUUID().equals(concludedOwner)
                && naamah.canAttack(naamah.getTarget()),
            "a concluded audience must still select a valid non-owner challenger");

        final NaamahCourtRuntime.Counters counters = naamah.courtCounters();
        final long localHazardScansBefore = counters.localHazardScans();
        final long localHazardReadsBefore = counters.localHazardBlockStateReads();
        final BlockPos contactHazardPosition = new BlockPos(1, 1, 2);
        helper.setBlock(contactHazardPosition, Blocks.CACTUS);
        final long contactScanAt = helper.getLevel().getGameTime();
        naamah.setCourtState(naamah.courtState().cancelAction(helper.getLevel().getGameTime()).withSchedule(
            naamah.courtState().nextDecisionAt(), naamah.courtState().nextCandidateScanAt(),
            contactScanAt, naamah.courtState().nextAmbientFeedbackAt(),
            naamah.courtState().lastNavigationAt()
        ));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().localHazard(),
            java.util.Optional.of(HazardEscapeRules.Hazard.CONTACT),
            "a charged local scan must remember a detected contact hazard between scans");
        helper.assertValueEqual(counters.localHazardScans(), localHazardScansBefore + 1L,
            "the due contact check must execute exactly one charged local scan");
        helper.assertTrue(counters.localHazardBlockStateReads() > localHazardReadsBefore,
            "the charged contact scan must report its actual block-state reads");
        helper.assertTrue(counters.maximumLocalHazardBlockStatesPerScan() <= 36,
            "the local contact scan must inspect no more than its complete 3x4x3 footprint");

        helper.setBlock(contactHazardPosition, Blocks.AIR);
        naamah.setCourtState(naamah.courtState().beginAction(Action.COURT_WAVE, contactScanAt));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().action(), Action.NONE,
            "a remembered contact hazard must exclude court actions between charged scans");
        helper.assertValueEqual(counters.localHazardScans(), localHazardScansBefore + 1L,
            "ordinary intervening ticks must not repeat the local block-state scan");
        helper.assertValueEqual(naamah.courtState().localHazard(),
            java.util.Optional.of(HazardEscapeRules.Hazard.CONTACT),
            "removing the block must not bypass the cadence before the next charged recheck");

        final NaamahCourtState recheckState = naamah.courtState();
        naamah.setCourtState(recheckState.withSchedule(
            recheckState.nextDecisionAt(), recheckState.nextCandidateScanAt(), contactScanAt,
            recheckState.nextAmbientFeedbackAt(), recheckState.lastNavigationAt()
        ));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(counters.localHazardScans(), localHazardScansBefore + 2L,
            "the next due deadline must perform exactly one charged recheck");
        helper.assertTrue(naamah.courtState().localHazard().isEmpty(),
            "the charged recheck must release a contact hazard that is no longer present");

        helper.assertTrue(counters.candidateScans() > 0L, "the live test must execute a bounded candidate scan");
        helper.assertTrue(counters.destinationSearches() > 0L, "the live test must execute a bounded hazard search");
        helper.assertTrue(counters.maximumCandidatesRetained() <= NaamahCourtRules.MAX_CANDIDATES,
            "candidate retention must stay bounded");
        helper.assertTrue(counters.maximumBlockStatesPerSearch() <= NaamahCourtRules.MAX_DESTINATION_BLOCKS,
            "destination inspection must stay bounded");

        naamah.setNoAi(true);
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        naamah.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        naamah.getNavigation().stop();
        final ServerPlayer collisionTarget = connectedPlayer(helper, new BlockPos(2, 1, 1));
        collisionTarget.setNoGravity(true);
        naamah.setTarget(collisionTarget);
        helper.assertTrue(NaamahCourtRuntime.eligibleTarget(naamah, collisionTarget),
            "destination execution fixtures must use a valid bound action target");
        final BlockPos lowCeilingDestination = helper.absolutePos(new BlockPos(3, 1, 2));
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        helper.assertFalse(NaamahCourtRuntime.safeDestination(
                naamah, helper.getLevel(), lowCeilingDestination
            ),
            "the full Naamah destination AABB must reject a 2-block-high ceiling");
        final long lowCeilingStart = helper.getLevel().getGameTime() - NaamahCourtRules.MIN_WINDUP_TICKS;
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(collisionTarget.getUUID(), helper.getLevel().getGameTime() + 200L)
            .withDestination(lowCeilingDestination, helper.getLevel().getGameTime() + 100L)
            .beginAction(Action.VEIL_STEP, lowCeilingStart, collisionTarget.getUUID(),
                helper.getLevel().dimension().identifier().toString()));
        naamah.setTarget(collisionTarget);
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().action(), Action.NONE,
            "a low-ceiling Veil Step must cancel at execution");
        helper.assertTrue(naamah.position().distanceToSqr(
                lowCeilingDestination.getX() + 0.5D,
                lowCeilingDestination.getY(),
                lowCeilingDestination.getZ() + 0.5D
            ) > 0.25D,
            "Naamah must not teleport into a ceiling intersecting her full height");

        helper.setBlock(new BlockPos(3, 3, 2), Blocks.AIR);
        final BlockPos occupiedDestination = helper.absolutePos(new BlockPos(3, 1, 1));
        final Sheep occupant = helper.spawn(EntityTypes.SHEEP, new BlockPos(3, 1, 1), EntitySpawnReason.EVENT);
        occupant.setNoAi(true);
        occupant.setNoGravity(true);
        helper.assertFalse(NaamahCourtRuntime.safeDestination(
                naamah, helper.getLevel(), occupiedDestination
            ),
            "the full Naamah destination AABB must reject an occupied destination");
        helper.assertFalse(NaamahCourtRuntime.safeDestination(
                naamah, helper.getLevel(), new BlockPos(29_999_900, origin.getY(), 29_999_900)
            ),
            "destination validation must reject an unloaded footprint without accessing it");
        naamah.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        naamah.setTarget(collisionTarget);
        final long occupiedStart = helper.getLevel().getGameTime() - NaamahCourtRules.MIN_WINDUP_TICKS;
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(collisionTarget.getUUID(), helper.getLevel().getGameTime() + 200L)
            .withDestination(occupiedDestination, helper.getLevel().getGameTime() + 100L)
            .beginAction(Action.VEIL_STEP, occupiedStart, collisionTarget.getUUID(),
                helper.getLevel().dimension().identifier().toString()));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtState().action(), Action.NONE,
            "an occupied Veil Step must cancel at execution");
        helper.assertTrue(naamah.position().distanceToSqr(
                occupiedDestination.getX() + 0.5D,
                occupiedDestination.getY(),
                occupiedDestination.getZ() + 0.5D
            ) > 0.25D,
            "Naamah must not teleport into another entity's occupied destination");

        occupant.discard();
        final Sheep veilTarget = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(0, 1, 2), EntitySpawnReason.EVENT
        );
        veilTarget.setNoAi(true);
        veilTarget.setNoGravity(true);
        final BlockPos safeVeilDestination = helper.absolutePos(new BlockPos(0, 1, 0));
        helper.assertTrue(NaamahCourtRuntime.safeDestination(
                naamah, helper.getLevel(), safeVeilDestination
            ),
            "the successful Veil fixture must expose a loaded, collision-free destination");
        final long safeVeilNow = helper.getLevel().getGameTime();
        NaamahCourtRuntime.rememberAttacker(naamah, veilTarget, safeVeilNow);
        naamah.setTarget(veilTarget);
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(veilTarget.getUUID(), safeVeilNow + 200L)
            .withRouteRetry(2, 0L)
            .withDestination(safeVeilDestination, safeVeilNow + 100L)
            .withSchedule(safeVeilNow + 200L, safeVeilNow + 200L,
                safeVeilNow + 200L, safeVeilNow + 200L, safeVeilNow)
            .beginAction(Action.VEIL_STEP,
                safeVeilNow - NaamahCourtRules.MIN_WINDUP_TICKS, veilTarget.getUUID(),
                helper.getLevel().dimension().identifier().toString()));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertTrue(naamah.position().distanceToSqr(
                safeVeilDestination.getX() + 0.5D,
                safeVeilDestination.getY(),
                safeVeilDestination.getZ() + 0.5D
            ) <= 0.01D,
            "a safe Veil Step must complete its validated movement");
        helper.assertValueEqual(naamah.courtState().routeFailures(), 0,
            "a successful Veil Step must reset consecutive route failures");
        helper.assertValueEqual(naamah.courtState().retryAfter(), 0L,
            "a successful Veil Step must clear route retry cooldown");
        veilTarget.discard();
        naamah.setTarget(null);
        naamah.setNoAi(false);
        naamah.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 1 && z == 1) continue;
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        final Sheep dreamTarget = helper.spawn(
            EntityTypes.SHEEP, new BlockPos(4, 1, 2), EntitySpawnReason.EVENT
        );
        dreamTarget.setNoAi(true);
        final long dreamFallbackNow = helper.getLevel().getGameTime();
        final long navigationBeforeDreamFallback = naamah.courtCounters().navigationRequests();
        final var dreamFallbackOrigin = naamah.position();
        NaamahCourtRuntime.rememberAttacker(naamah, dreamTarget, dreamFallbackNow);
        naamah.setTarget(dreamTarget);
        naamah.setCourtState(naamah.courtState().finishAction()
            .withChallenger(dreamTarget.getUUID(), dreamFallbackNow + 200L)
            .withRouteRetry(1, 0L)
            .withSchedule(dreamFallbackNow + 200L, dreamFallbackNow + 200L,
                dreamFallbackNow + 200L, dreamFallbackNow + 200L,
                dreamFallbackNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS)
            .beginAction(Action.DREAM_APPROACH,
                dreamFallbackNow - NaamahCourtRules.MIN_WINDUP_TICKS, dreamTarget.getUUID(),
                helper.getLevel().dimension().identifier().toString()));
        NaamahCourtRuntime.tick(naamah, helper.getLevel());
        helper.assertValueEqual(naamah.courtCounters().navigationRequests(),
            navigationBeforeDreamFallback + 1L,
            "a blocked Dream Approach must make exactly one court-owned path request");
        helper.assertTrue(naamah.position().distanceToSqr(dreamFallbackOrigin) > 0.25D,
            "a blocked Dream Approach must use its validated local veil fallback");
        helper.assertValueEqual(naamah.courtState().routeFailures(), 0,
            "a successful Dream Approach fallback must reset the failed path count");
        helper.assertValueEqual(naamah.courtState().retryAfter(), 0L,
            "a successful Dream Approach fallback must clear route retry cooldown");
        dreamTarget.discard();
        naamah.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        naamah.setTarget(null);

        final BlockPos unreachableDestination = helper.absolutePos(new BlockPos(4, 1, 2));
        final long routeNow = helper.getLevel().getGameTime();
        naamah.setCourtState(naamah.courtState().finishAction()
            .withDestination(unreachableDestination, routeNow + 200L)
            .withRouteRetry(0, 0L)
            .withSchedule(routeNow + 200L, routeNow + 200L, routeNow + 200L,
                routeNow + 200L, routeNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS));
        naamah.igniteForSeconds(20.0F);
        for (int attempt = 1; attempt <= NaamahCourtRules.MAX_ROUTE_FAILURES; attempt++) {
            final NaamahCourtState attemptState = naamah.courtState();
            naamah.setCourtState(attemptState.withSchedule(
                attemptState.nextDecisionAt(), attemptState.nextCandidateScanAt(), attemptState.nextShadeScanAt(),
                attemptState.nextAmbientFeedbackAt(), routeNow - NaamahCourtRules.NAVIGATION_INTERVAL_TICKS
            ));
            NaamahCourtRuntime.tick(naamah, helper.getLevel());
        }
        naamah.clearFire();
        helper.assertValueEqual(naamah.courtState().routeFailures(), NaamahCourtRules.MAX_ROUTE_FAILURES,
            "three rejected unreachable paths must enter the bounded route backoff");
        helper.assertTrue(naamah.courtState().destination().isEmpty(),
            "the third rejected path must clear the unusable destination");
        helper.assertTrue(naamah.courtState().retryAfter() >= routeNow + NaamahCourtRules.ROUTE_RETRY_TICKS,
            "the third rejected path must start at least the required one-hundred-tick cooldown");

        naamah.setNoAi(true);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 1 && z == 1) continue;
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
        crowdedCandidates.forEach(player -> player.setGameMode(GameType.SPECTATOR));
        outsideSpatialQuery.setGameMode(GameType.SPECTATOR);
        otherPlayer.setGameMode(GameType.SPECTATOR);
        collisionTarget.setGameMode(GameType.SPECTATOR);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.TURTLE_EGG);
        final NaamahEntity eggSubject = (NaamahEntity) helper.spawn(
            ModEntities.ALL.get("naamah").get(), new BlockPos(3, 1, 3), EntitySpawnReason.EVENT
        );
        BlockPos.betweenClosedStream(new BlockPos(8, 0, 8), new BlockPos(16, 0, 12))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
        final ServerPlayer automaticTarget = connectedPlayer(helper, new BlockPos(14, 1, 10));
        automaticTarget.setNoGravity(true);
        final AtomicBoolean eggChecked = new AtomicBoolean(false);
        helper.runAfterDelay(80L, () -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 3)).is(Blocks.TURTLE_EGG),
                "dedicated Naamah goals must not inherit turtle-egg destruction");
            eggSubject.discard();
            eggChecked.set(true);
        });

        final AtomicInteger automaticChecks = new AtomicInteger(5);
        awaitAutomaticSelection(helper, automaticTarget, Phase.ENTHRONED, Action.DREAM_APPROACH, automaticChecks);
        awaitAutomaticSelection(helper, automaticTarget, Phase.CHORUS_OF_WAVES, Action.COURT_WAVE, automaticChecks);
        awaitAutomaticSelection(helper, automaticTarget, Phase.SOVEREIGN_REFUSAL, Action.COURT_WAVE, automaticChecks);
        awaitAutomaticSelection(helper, automaticTarget, Phase.SOVEREIGN_REFUSAL, Action.VEIL_STEP, automaticChecks);
        awaitAutomaticSelection(helper, automaticTarget, Phase.SOVEREIGN_REFUSAL, Action.NONE, automaticChecks);
        helper.onEachTick(() -> {
            if (eggChecked.get() && automaticChecks.get() == 0) helper.succeed();
        });
    }

    private static void awaitAutomaticSelection(
        final GameTestHelper helper,
        final ServerPlayer target,
        final Phase phase,
        final Action expected,
        final AtomicInteger remaining
    ) {
        helper.runAfterDelay(1L, () -> {
            final long now = helper.getLevel().getGameTime();
            final long slot = Math.floorDiv(now, NaamahCourtRules.DECISION_INTERVAL_TICKS);
            if (NaamahCourtRules.automaticAction(phase, slot) != expected) {
                awaitAutomaticSelection(helper, target, phase, expected, remaining);
                return;
            }
            final NaamahEntity subject = (NaamahEntity) helper.spawn(
                ModEntities.ALL.get("naamah").get(), new BlockPos(10, 1, 10), EntitySpawnReason.EVENT
            );
            subject.setNoAi(true);
            final float phaseHealth = switch (phase) {
                case ENTHRONED -> subject.getMaxHealth();
                case CHORUS_OF_WAVES -> subject.getMaxHealth() * 0.5F;
                case SOVEREIGN_REFUSAL, AUDIENCE_CONCLUDED -> subject.getMaxHealth() * 0.2F;
            };
            subject.setCourtState(NaamahCourtState.empty()
                .withAnchor(helper.getLevel().dimension().identifier().toString(), subject.blockPosition())
                .latchPhase(phaseHealth, subject.getMaxHealth())
                .withChallenger(target.getUUID(), now + 200L)
                .withSchedule(now, now + 200L, now + 200L, now + 200L, now));
            subject.setTarget(target);
            helper.assertTrue(subject.getTarget() == target && !target.isSpectator()
                    && target.distanceToSqr(subject) <= NaamahCourtRules.CANDIDATE_RADIUS
                        * NaamahCourtRules.CANDIDATE_RADIUS,
                "the automatic scheduler fixture must start with a valid nearby live target; subject="
                    + subject.position() + ", target=" + target.position() + ", spectator=" + target.isSpectator());
            final long navigationBeforeDecision = subject.courtCounters().navigationRequests();
            NaamahCourtRuntime.tick(subject, helper.getLevel());
            helper.assertTrue(subject.courtState().action() == expected,
                "the live semantic scheduler must select " + expected + " automatically; observed="
                    + subject.courtState().action() + ", phase=" + subject.courtState().phase()
                    + ", target=" + (subject.getTarget() == target) + ", eligible="
                    + NaamahCourtRuntime.eligibleTarget(subject, target) + ", distance="
                    + target.distanceToSqr(subject) + ", spectator=" + target.isSpectator() + ", failures="
                    + subject.courtState().routeFailures() + ", destination="
                    + subject.courtState().destination() + ", blockReads="
                    + subject.courtCounters().maximumBlockStatesPerSearch());
            if (expected == Action.NONE) {
                helper.assertTrue(NaamahCourtRuntime.challengerApproachMayRun(subject),
                    "an automatic no-action decision must leave challenger pressure available");
            } else {
                helper.assertValueEqual(subject.courtCounters().navigationRequests(), navigationBeforeDecision,
                    "strong-action selection must not issue a same-tick challenger path request");
                helper.assertFalse(NaamahCourtRuntime.challengerApproachMayRun(subject),
                    "a telegraphed strong action must exclusively own its windup window");
            }
            subject.discard();
            remaining.decrementAndGet();
        });
    }

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(-1, 0, -1), new BlockPos(10, 0, 4))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    private static ServerPlayer connectedPlayer(final GameTestHelper helper, final BlockPos relativePosition) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos position = helper.absolutePos(relativePosition);
        player.teleportTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }
}
