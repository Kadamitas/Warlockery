package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Six bounded live F19 fixtures. Every fixture asserts through spawned entities that are either
 * self ticking or directly dispatched into their species runtime, cleans up every created entity
 * and block in {@code finally} including mid-sequence stages, and uses exact state and counter
 * assertions instead of elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell, so
 * every entity, memorial block and computed destination in these fixtures stays inside relative
 * 0..2 at y=1. Both species were given deliberately short petition, attendance, withdrawal and
 * strike bands, so no fixture ever needs to reopen the framework shell to reach a real band and
 * no destination can silently land outside the arena and freeze an entity on stale state.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred ModEntities and ModGameTests wiring to
 * route {@code warlockery:lost_soul} through {@link LostSoulEntity}, {@code warlockery:spirit}
 * through {@link SpiritEntity}, and to register these six functions.</p>
 */
public final class LostSoulSpiritGameTests {
    private LostSoulSpiritGameTests() {
    }

    // ---------------------------------------------------------------- lost soul

    public static void lostSoulPetitionsThenSettlesAtMemorial(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final LostSoulEntity soul = spawnLostSoul(fixture, new BlockPos(0, 1, 0));
            soul.setNoAi(true);
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.SOUL_LANTERN);

            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertTrue(soul.lostSoulState().anchor().present(),
                "one loaded memorial anchor is chosen from the capped soul-light scan");
            helper.assertValueEqual(soul.lostSoulState().anchor().position().orElseThrow(),
                helper.absolutePos(new BlockPos(2, 1, 2)),
                "the nearest soul light by stable distance ordering is the chosen anchor");
            helper.assertValueEqual(soul.lostSoulCounters().episodesStarted(), 1L,
                "exactly one memorial episode is started");
            helper.assertTrue(soul.lostSoulCounters().anchorReads()
                    <= LostSoulRules.MAX_ANCHOR_READS,
                "the memorial scan never exceeds its declared read ceiling");

            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.PETITION,
                "arriving inside the petition band begins a visible petition");
            helper.assertTrue(soul.getTarget() == null,
                "a petitioning Lost Soul never writes a combat target");

            for (int tick = 0; tick < LostSoulRules.PETITION_TICKS; tick++) {
                makeDue(helper, soul);
                LostSoulRuntime.tick(soul, helper.getLevel());
            }
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.SETTLE,
                "the petition is finite and settles nearby afterwards");
            helper.assertTrue(soul.lostSoulCounters().petitionPulses() >= 1L
                    && soul.lostSoulCounters().petitionPulses() <= LostSoulRules.MAX_PETITION_PULSES,
                "at most three petition pulses are emitted per episode");

            for (int tick = 0; tick <= LostSoulRules.SETTLE_TICKS + 1; tick++) {
                makeDue(helper, soul);
                LostSoulRuntime.tick(soul, helper.getLevel());
            }
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.COOLDOWN,
                "the whole episode is finite and enters a cooldown");
            helper.assertFalse(soul.lostSoulState().anchor().present(),
                "the anchor is released with the episode");
            helper.assertTrue(soul.lostSoulState().cadence().cooldownTicks() > 0,
                "the cooldown prevents an immediate second episode");
            helper.assertValueEqual(soul.lostSoulCounters().episodesEnded(), 1L,
                "the episode end is counted exactly once");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void lostSoulBindingCancelsPetitionWithoutCombat(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final LostSoulEntity soul = spawnLostSoul(fixture, new BlockPos(0, 1, 0));
            soul.setNoAi(true);
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.SOUL_LANTERN);
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.PETITION,
                "the shade is petitioning before the binding arrives");

            final ServerPlayer binder = fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);
            binder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_LANTERN));
            soul.mobInteract(binder, InteractionHand.MAIN_HAND);

            helper.assertTrue(CreatureBehaviorState.isOwnedBy(soul, binder.getUUID()),
                "the audited spirit-binder tag still writes the one generic owner UUID");
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.BOUND,
                "the very interaction that bound the shade cancelled its episode");
            helper.assertFalse(soul.lostSoulState().anchor().present(),
                "no memorial anchor survives the binding");
            helper.assertFalse(soul.lostSoulState().episode().active(),
                "no petition survives the binding");
            helper.assertTrue(soul.getTarget() == null,
                "binding never produces a combat target");

            final Zombie aggressor = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1),
                EntitySpawnReason.EVENT);
            aggressor.setNoAi(true);
            binder.setLastHurtByMob(aggressor);
            helper.assertFalse(soul.canAttack(aggressor),
                "a bound Lost Soul never copies an owner target and never attacks for its owner");
            for (int tick = 0; tick < LostSoulRules.AURA_INTERVAL_TICKS * 2; tick++) {
                LostSoulRuntime.tick(soul, helper.getLevel());
            }
            helper.assertTrue(soul.getTarget() == null,
                "quiet bound attendance never acquires a target");
            helper.assertTrue(aggressor.isAlive() && aggressor.getHealth() == aggressor.getMaxHealth(),
                "a bound Lost Soul deals no damage on its owner's behalf");
            helper.assertTrue(binder.hasEffect(MobEffects.NIGHT_VISION),
                "the preserved Night Vision owner aura still reaches the bound owner");
            helper.assertTrue(soul.lostSoulCounters().auraPulses() >= 1L,
                "aura work is counted");
            helper.assertTrue(soul.isAlive(),
                "a bound Lost Soul is never consumed or completed as a reward");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- spirit

    public static void spiritWaryBindingTransitionIsFinite(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final SpiritEntity spirit = spawnSpirit(fixture, new BlockPos(0, 1, 0));
            spirit.setNoAi(true);
            final ServerPlayer stranger =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);

            makeDue(helper, spirit);
            SpiritRuntime.tick(spirit, helper.getLevel());
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.WARY,
                "an unbound Spirit keeps a bounded wary radius from an approaching player");
            helper.assertValueEqual(spirit.spiritCounters().waryReactions(), 1L,
                "exactly one wary reaction is opened");
            helper.assertTrue(spirit.spiritCounters().proximityVisits()
                    <= SpiritRules.MAX_PROXIMITY_CANDIDATES,
                "the proximity inspection stays inside its declared candidate cap");

            for (int tick = 0; tick <= SpiritRules.WARY_TICKS; tick++) {
                makeDue(helper, spirit);
                SpiritRuntime.tick(spirit, helper.getLevel());
            }
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.WANDER,
                "the wary reaction is finite: it never becomes permanent flight");
            helper.assertTrue(spirit.spiritState().wary().cooldownTicks() > 0,
                "a finished wary reaction starts its own cooldown");

            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_LANTERN));
            spirit.setSpiritState(spirit.spiritState()
                .withWary(SpiritState.Wary.started())
                .withPhase(SpiritRules.Phase.WARY));
            spirit.mobInteract(stranger, InteractionHand.MAIN_HAND);
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(spirit, stranger.getUUID()),
                "the audited spirit-binder tag still writes the one generic owner UUID");
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.BOUND,
                "binding atomically stops avoidance in the same interaction");
            helper.assertFalse(spirit.spiritState().wary().active(),
                "no wary reaction survives the binding");
            helper.assertTrue(stranger.hasEffect(MobEffects.NIGHT_VISION),
                "the preserved Night Vision owner aura still reaches the bound owner");

            final ServerPlayer rival =
                fixture.connectedPlayer(new BlockPos(0, 1, 2), GameType.SURVIVAL);
            rival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_LANTERN));
            spirit.mobInteract(rival, InteractionHand.MAIN_HAND);
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(spirit, stranger.getUUID()),
                "a second binder never replaces the established single owner");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void spiritDefendsOnceWithAttributionThenRecovers(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final SpiritEntity spirit = spawnSpirit(fixture, new BlockPos(1, 1, 1));
            spirit.setNoAi(true);
            final ServerPlayer owner =
                fixture.connectedPlayer(new BlockPos(0, 1, 0), GameType.SURVIVAL);
            CreatureBehaviorState.bind(spirit, owner.getUUID());
            spirit.setSpiritState(spirit.spiritState().bind());

            final Zombie attacker = fixture.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1),
                EntitySpawnReason.EVENT);
            attacker.setNoAi(true);
            final float attackerHealth = attacker.getHealth();

            makeDue(helper, spirit);
            SpiritRuntime.tick(spirit, helper.getLevel());
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.BOUND,
                "an owner who has not been attacked never opens a defence");
            helper.assertTrue(spirit.getTarget() == null,
                "a bound Spirit never proactively targets anything");

            owner.setLastHurtByMob(attacker);
            makeDue(helper, spirit);
            SpiritRuntime.tick(spirit, helper.getLevel());
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.WARN,
                "the owner's recent valid direct attacker opens a visible warning first");
            helper.assertValueEqual(spirit.spiritState().guard().attackerId().orElseThrow(),
                attacker.getUUID(), "the one accepted subject is the owner's own attacker");
            helper.assertValueEqual(attacker.getHealth(), attackerHealth,
                "the warning itself applies no damage");

            for (int tick = 0; tick <= SpiritRules.WARN_TICKS; tick++) {
                makeDue(helper, spirit);
                owner.setLastHurtByMob(attacker);
                SpiritRuntime.tick(spirit, helper.getLevel());
                if (spirit.spiritState().phase() != SpiritRules.Phase.WARN) {
                    break;
                }
            }
            helper.assertTrue(spirit.spiritCounters().warnPulses() >= 1L
                    && spirit.spiritCounters().warnPulses() <= SpiritRules.MAX_WARN_PULSES,
                "the warning is visible and capped");

            for (int tick = 0; tick <= SpiritRules.DEFEND_TICKS; tick++) {
                makeDue(helper, spirit);
                owner.setLastHurtByMob(attacker);
                SpiritRuntime.tick(spirit, helper.getLevel());
                if (spirit.spiritCounters().strikes() >= 1L) {
                    break;
                }
            }
            helper.assertValueEqual(spirit.spiritCounters().defenceWindowsOpened(), 1L,
                "the warning graduated into a real defence window rather than expiring");
            helper.assertValueEqual(spirit.spiritCounters().strikes(), 1L,
                "exactly one ordinary attributed strike is permitted per defence window");
            helper.assertTrue(attacker.getHealth() < attackerHealth,
                "the one strike lands as ordinary attributed melee damage");
            helper.assertValueEqual(attacker.getLastHurtByMob(), spirit,
                "the strike is attributed to the Spirit itself, never to its owner");
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.RECOVER,
                "the defence closes into a bounded recovery");
            helper.assertTrue(spirit.getTarget() == null,
                "no live target survives the closed defence window");

            final float afterStrike = attacker.getHealth();
            for (int tick = 0; tick < 40; tick++) {
                makeDue(helper, spirit);
                owner.setLastHurtByMob(attacker);
                SpiritRuntime.tick(spirit, helper.getLevel());
            }
            helper.assertValueEqual(spirit.spiritCounters().strikes(), 1L,
                "the recovery window forbids an immediate second defence");
            helper.assertValueEqual(attacker.getHealth(), afterStrike,
                "no further damage is dealt during the recovery window");
            helper.assertFalse(spirit.canAttack(owner),
                "the owner is never a legal defence subject");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- shared spectral contracts

    public static void spectralReloadHazardAndFamilyIsolation(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final LostSoulEntity soul = spawnLostSoul(fixture, new BlockPos(0, 1, 0));
            soul.setNoAi(true);
            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.SOUL_LANTERN);
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.PETITION,
                "the saved shade is genuinely mid petition");

            final TagValueOutput soulOutput = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            soul.saveWithoutId(soulOutput);
            final var savedSoul = soulOutput.buildResult().copy();
            final LostSoulEntity reloadedSoul = (LostSoulEntity) ModEntities.ALL.get("lost_soul")
                .get().create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(reloadedSoul != null, "the registered type must recreate saved state");
            fixture.track(reloadedSoul);
            reloadedSoul.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), savedSoul
            ));
            helper.assertValueEqual(reloadedSoul.lostSoulState().phase(), Phase.APPROACH,
                "a reload never resumes mid petition and never replays its feedback");
            helper.assertValueEqual(reloadedSoul.lostSoulCounters().petitionPulses(), 0L,
                "no petition pulse replays on load");
            helper.assertTrue(reloadedSoul.getTarget() == null,
                "no live target survives a load");

            final SpiritEntity spirit = spawnSpirit(fixture, new BlockPos(2, 1, 0));
            spirit.setNoAi(true);
            final ServerPlayer owner =
                fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);
            CreatureBehaviorState.bind(spirit, owner.getUUID());
            spirit.setSpiritState(spirit.spiritState().bind()
                .withGuard(new SpiritState.Guard(
                    Optional.of(owner.getUUID()), Optional.of(SpectralEntity.dimensionOf(helper.getLevel())),
                    0, 0, 2, SpiritRules.DEFEND_TICKS, 1, 0
                ))
                .withPhase(SpiritRules.Phase.DEFEND));
            final TagValueOutput spiritOutput = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
            );
            spirit.saveWithoutId(spiritOutput);
            final var savedSpirit = spiritOutput.buildResult().copy();
            final SpiritEntity reloadedSpirit = (SpiritEntity) ModEntities.ALL.get("spirit")
                .get().create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(reloadedSpirit != null,
                "the registered type must recreate saved state");
            fixture.track(reloadedSpirit);
            reloadedSpirit.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), savedSpirit
            ));
            helper.assertValueEqual(reloadedSpirit.spiritState().phase(), SpiritRules.Phase.WARN,
                "a reload never lands inside an open strike window");
            helper.assertValueEqual(reloadedSpirit.spiritState().guard().strikes(), 1,
                "the spent strike survives the reload so no second attack is granted");
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(reloadedSpirit, owner.getUUID()),
                "exactly one owner identity survives the reload");
            helper.assertTrue(reloadedSpirit.getTarget() == null,
                "no live target survives a load");

            // Hazard priority: a burning shade abandons its petition and escapes.
            soul.setLostSoulState(soul.lostSoulState());
            soul.igniteForSeconds(4.0F);
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertTrue(soul.lostSoulTransient().hazardActive(),
                "an escapable hazard preempts every species phase");
            helper.assertTrue(soul.lostSoulCounters().hazardInterruptions() >= 1L,
                "the hazard interruption is counted");
            soul.clearFire();

            // Family isolation: neither species carries Death, Corpse, Spectre, or Vex identity.
            for (final SpectralEntity spectral : List.of(soul, spirit)) {
                helper.assertFalse(Vex.class.isInstance(spectral),
                    "the dedicated spectral neighbours are not Vex copies");
                helper.assertFalse(SpiritMob.class.isInstance(spectral),
                    "neither species carries the shared SpiritMob class identity");
                helper.assertFalse(Enemy.class.isInstance(spectral),
                    "neither species is an Enemy: no sleep prevention and no golem auto-targeting");
                helper.assertValueEqual(spectral.operationalTargetGoalCount(), 0,
                    "no target goal is ever registered for either species");
                helper.assertTrue(spectral.getTarget() == null,
                    "neither species ever holds a target outside an accepted defence");
            }
            helper.assertFalse(soul.creatureKind() == spirit.creatureKind(),
                "the two neighbours keep separate registry kinds");
            // Original intent: F19's own delegation must not collaterally strip other families'
            // generic ambient rows. F21 retargeted this probe onto UMBRAL_SIGIL as the last vigil
            // family; F22 delegated that kind too and retired the whole SOUL_LANTERN_VIGIL row,
            // because the profile constructor rejects an empty kind set. The probe therefore moves
            // to an unrelated still-generic family, and the retired row is asserted retired rather
            // than silently dropped.
            helper.assertTrue(
                AmbientActivityProfile.forType(
                    AmbientActivityProfile.ActivityType.SOUL_LANTERN_VIGIL) == null,
                "F22 retired the whole soul-lantern vigil row rather than emptying its kind set");
            helper.assertTrue(
                AmbientActivityProfile.forKind(ArcaneCreature.CreatureKind.TOAD).size() >= 1,
                "an unrelated generic ambient family keeps its own routine");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    public static void spectralOwnerRaceAndRouteFailureCleanup(final GameTestHelper helper) {
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final LostSoulEntity soul = spawnLostSoul(fixture, new BlockPos(0, 1, 0));
            soul.setNoAi(true);
            final ServerPlayer first =
                fixture.connectedPlayer(new BlockPos(1, 1, 1), GameType.SURVIVAL);
            final ServerPlayer second =
                fixture.connectedPlayer(new BlockPos(2, 1, 2), GameType.SURVIVAL);
            first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_LANTERN));
            second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_LANTERN));
            soul.mobInteract(first, InteractionHand.MAIN_HAND);
            soul.mobInteract(second, InteractionHand.MAIN_HAND);
            helper.assertTrue(CreatureBehaviorState.isOwnedBy(soul, first.getUUID()),
                "a simultaneous binding race resolves to exactly one owner");
            helper.assertFalse(CreatureBehaviorState.isOwnedBy(soul, second.getUUID()),
                "the losing binder never becomes a second owner");
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.BOUND,
                "the winning binding enters quiet attendance");

            CreatureBehaviorState.unbind(soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.WANDER,
                "an invalid owner stops attendance and clears every transient claim");
            helper.assertTrue(soul.getNavigation().isDone(),
                "losing an owner stops navigation instead of leaving a stale route");

            fixture.placeBlock(new BlockPos(2, 1, 2), Blocks.SOUL_LANTERN);
            soul.setLostSoulState(soul.lostSoulState()
                .withCadence(new LostSoulState.Cadence(0, 0, 0)));
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertTrue(soul.lostSoulState().anchor().present(),
                "a fresh episode is available after the owner is gone");
            soul.setLostSoulState(soul.lostSoulState().withCadence(
                new LostSoulState.Cadence(0, LostSoulRules.MAX_ROUTE_FAILURES, 0)
            ));
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.COOLDOWN,
                "a third persisted route failure is observable and ends the episode");
            helper.assertFalse(soul.lostSoulState().anchor().present(),
                "the anchor is released with the failed episode");
            helper.assertValueEqual(soul.lostSoulState().cadence().routeFailures(), 0,
                "the release resets the failure counter after it was observed");
            helper.assertTrue(soul.getNavigation().isDone(),
                "the failed route leaves no stale navigation");

            // An episode that simply runs out of loaded time must release its anchor and arm the
            // cooldown through the live tick. Before the state records stopped reconciling an
            // expired episode away, this release could never be observed and the stale anchor
            // permanently blocked every future episode.
            soul.setLostSoulState(soul.lostSoulState()
                .withCadence(new LostSoulState.Cadence(0, 0, 0)));
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertTrue(soul.lostSoulState().anchor().present(),
                "a second episode starts once the cooldown has elapsed");
            soul.setLostSoulState(soul.lostSoulState()
                .withEpisode(new LostSoulState.Episode(1, 0, 0, 0, 0)));
            makeDue(helper, soul);
            LostSoulRuntime.tick(soul, helper.getLevel());
            helper.assertValueEqual(soul.lostSoulState().phase(), Phase.COOLDOWN,
                "an expired episode is observed by the tick and ends the episode");
            helper.assertFalse(soul.lostSoulState().anchor().present(),
                "the expired episode releases its anchor instead of stranding it");
            helper.assertTrue(soul.lostSoulState().cadence().cooldownTicks() > 0,
                "the expiry arms the cooldown so a later episode is still possible");

            final SpiritEntity spirit = spawnSpirit(fixture, new BlockPos(2, 1, 0));
            spirit.setNoAi(true);
            final ServerPlayer owner = first;
            CreatureBehaviorState.bind(spirit, owner.getUUID());
            spirit.setSpiritState(spirit.spiritState().bind()
                .withGuard(SpiritState.Guard.warning(
                    second.getUUID(), SpectralEntity.dimensionOf(helper.getLevel()), 0
                ))
                .withPhase(SpiritRules.Phase.WARN)
                .withCadence(new SpiritState.Cadence(SpiritRules.MAX_ROUTE_FAILURES, 0, 0)));
            makeDue(helper, spirit);
            SpiritRuntime.tick(spirit, helper.getLevel());
            helper.assertValueEqual(spirit.spiritState().phase(), SpiritRules.Phase.RECOVER,
                "an exhausted route ends the guard instead of chasing forever");
            helper.assertFalse(spirit.spiritState().guard().present(),
                "the guard subject is released with the guard");
            helper.assertTrue(spirit.getTarget() == null,
                "no target survives an ended guard");

            CreatureBehaviorState.unbind(spirit);
            SpiritRuntime.tick(spirit, helper.getLevel());
            helper.assertFalse(spirit.spiritState().guard().present(),
                "losing an owner cancels every guard claim");
            helper.assertTrue(spirit.getNavigation().isDone(),
                "losing an owner stops navigation instead of leaving a stale route");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    // ---------------------------------------------------------------- fixture support

    /**
     * Claims every species cadence so an assertion observes exactly the work this pass asked for
     * rather than an unrelated wander, scan, or hazard sample that happened to come due.
     */
    private static void makeDue(final GameTestHelper helper, final LostSoulEntity soul) {
        // The one-shot load reconciliation re-seeds the discovery stagger from inside the first
        // tick, so it has to be settled before these cadences are cleared rather than after.
        LostSoulRuntime.reconcileForFixture(soul, helper.getLevel());
        final LostSoulRuntime.TransientState scratch = soul.lostSoulTransient();
        scratch.pathCooldownTicks = 0;
        scratch.hazardCooldownTicks = 0;
        scratch.discoveryCooldownTicks = 0;
        scratch.wanderCooldownTicks = LostSoulRules.WANDER_INTERVAL_TICKS;
    }

    private static void makeDue(final GameTestHelper helper, final SpiritEntity spirit) {
        // The one-shot load reconciliation re-seeds the proximity stagger from inside the first
        // tick, so it has to be settled before these cadences are cleared rather than after.
        SpiritRuntime.reconcileForFixture(spirit, helper.getLevel());
        final SpiritRuntime.TransientState scratch = spirit.spiritTransient();
        scratch.pathCooldownTicks = 0;
        scratch.hazardCooldownTicks = 0;
        scratch.proximityCooldownTicks = 0;
        scratch.attendCooldownTicks = SpiritRules.WANDER_INTERVAL_TICKS;
        scratch.wanderCooldownTicks = SpiritRules.WANDER_INTERVAL_TICKS;
    }

    private static LostSoulEntity spawnLostSoul(final FixtureScope fixture, final BlockPos position) {
        @SuppressWarnings("unchecked")
        final EntityType<LostSoulEntity> type =
            (EntityType<LostSoulEntity>) ModEntities.ALL.get("lost_soul").get();
        return placed(fixture, fixture.spawn(type, position, EntitySpawnReason.EVENT), position);
    }

    private static SpiritEntity spawnSpirit(final FixtureScope fixture, final BlockPos position) {
        @SuppressWarnings("unchecked")
        final EntityType<SpiritEntity> type =
            (EntityType<SpiritEntity>) ModEntities.ALL.get("spirit").get();
        return placed(fixture, fixture.spawn(type, position, EntitySpawnReason.EVENT), position);
    }

    private static <T extends SpectralEntity> T placed(
        final FixtureScope fixture,
        final T entity,
        final BlockPos position
    ) {
        entity.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return entity;
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

        private void placeBlock(
            final BlockPos position,
            final net.minecraft.world.level.block.Block block
        ) {
            final BlockPos absolute = helper.absolutePos(position);
            // The sealed warlockery:empty3x3x3 cell has no interior floor at relative y=0, so a block
            // that needs support (a soul lantern does) is popped off by the neighbour update that
            // setBlock triggers. Give it a floor first, and restore both on close.
            final BlockPos absoluteSupport = absolute.below();
            final var previousSupport = helper.getLevel().getBlockState(absoluteSupport);
            if (previousSupport.isAir()) {
                helper.getLevel().setBlock(
                    absoluteSupport, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                onClose(() -> helper.getLevel().setBlock(absoluteSupport, previousSupport, 3));
            }
            final var previous = helper.getLevel().getBlockState(absolute);
            helper.getLevel().setBlock(absolute, block.defaultBlockState(), 3);
            onClose(() -> helper.getLevel().setBlock(absolute, previous, 3));
        }

        private ServerPlayer connectedPlayer(final BlockPos position, final GameType gameType) {
            final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(gameType);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(gameType);
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
            for (int index = cleanupActions.size() - 1; index >= 0; index--) {
                cleanupActions.get(index).run();
            }
            cleanupActions.clear();
        }
    }
}
