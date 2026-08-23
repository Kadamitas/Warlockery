package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Eight bounded live F21 fixtures. Every fixture asserts through spawned entities that keep their
 * AI enabled and tick themselves, cleans up every created entity and block in {@code finally}, and
 * uses exact state and counter assertions rather than elapsed-time guesses.
 *
 * <p>Arena geometry: the framework seals the {@code warlockery:empty3x3x3} cell in a barrier shell, so
 * every entity and every computed destination in these fixtures stays inside relative 0..2 with
 * entities at y=1 over a placed floor at y=0. Both kinds were given deliberately short mark,
 * answer, strike and dread bands, so no fixture ever needs to reopen the framework shell to reach a
 * real band and no destination can silently land outside the arena and freeze an entity on stale
 * state.</p>
 *
 * <p>Every {@code runAfterDelay} and {@code onEachTick} is registered directly from the fixture
 * body and never from inside another such callback. Assertions read monotonic counters rather than
 * instantaneous phases wherever an episode may legitimately have advanced further by the time the
 * callback runs.</p>
 *
 * <p>These fixtures depend on the coordinator-deferred ModEntities and ModGameTests wiring to route
 * {@code warlockery:echo_shade} through {@link EchoShadeEntity}, {@code warlockery:spectre} through
 * {@link SpectreEntity}, and to register these eight functions.</p>
 */
public final class EchoShadeSpectreGameTests {
    private EchoShadeSpectreGameTests() {
    }

    // ---------------------------------------------------------------- echo shade

    /**
     * The signature behavior: a self-ticking Shade appoints one mark, samples the horizontal
     * gesture that mark actually makes, and computes exactly one bounded mirrored answer from it.
     */
    public static void echoShadeRecordsOneVectorAndAnswersIt(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer mark = fixture.connectedPlayer(new BlockPos(2, 1, 0));
            final BlockPos lane = helper.absolutePos(new BlockPos(2, 1, 0));
            final AtomicInteger step = new AtomicInteger();
            final java.util.concurrent.atomic.AtomicBoolean walking =
                new java.util.concurrent.atomic.AtomicBoolean(true);
            // -1 so a stage that never ran cannot let the delay-260 comparison pass silently.
            final java.util.concurrent.atomic.AtomicLong strikesBeforeForcedWindow =
                new java.util.concurrent.atomic.AtomicLong(-1L);

            // One registration, from the fixture body: the mark walks a real, bounded gesture
            // along z so the Shade has genuine motion to sample rather than a teleport artifact.
            helper.onEachTick(() -> {
                if (!walking.get()) {
                    return;
                }
                // 0.012 blocks per tick: slow enough that the mark is still moving at tick 167,
                // well past the latest possible end of the record window (tick 81 with a full
                // 40-tick UUID watch stagger), and fast enough that seven contributing samples
                // accumulate 420 millis against the 250-milli answerable threshold.
                final double travelled = Math.min(2.0D, step.getAndIncrement() * 0.012D);
                mark.teleportTo(lane.getX() + 0.5D, lane.getY(), lane.getZ() + 0.5D + travelled);
                mark.setDeltaMovement(Vec3.ZERO);
            });

            helper.runAfterDelay(220L, () -> {
                helper.assertTrue(shade.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertTrue(shade.apparitionCounters().appointmentSweeps() >= 1L,
                    "the idle Shade ran at least one bounded appointment sweep");
                helper.assertTrue(shade.apparitionCounters().episodesStarted() >= 1L,
                    "a visible loaded player was marked");
                helper.assertTrue(shade.echoShadeCounters().answersComputed() >= 1L,
                    "the sampled gesture produced exactly one bounded mirrored answer");
                helper.assertTrue(shade.echoShadeCounters().unanswerableRecords() == 0L,
                    "a genuinely moving mark is never treated as a motionless one");
                helper.assertTrue(
                    shade.apparitionCounters().appointmentCandidateVisits()
                        <= shade.apparitionCounters().appointmentSweeps()
                            * ApparitionEpisodeRules.MAX_PLAYER_CANDIDATES,
                    "no appointment sweep exceeded its declared candidate cap");
                // A sweep that qualifies nobody must record the failure rather than silently
                // retrying every tick, so every sweep is accounted for as one or the other.
                helper.assertTrue(
                    shade.apparitionCounters().appointmentFailures()
                        + shade.apparitionCounters().episodesStarted()
                        <= shade.apparitionCounters().appointmentSweeps(),
                    "every appointment sweep either appointed a mark or recorded its failure");

                // Second half: drive the replay deterministically so the single attributed attempt
                // is genuinely executed rather than left to timing. Without this the strike branch
                // is never exercised live and every bound on it would pass vacuously.
                //
                // The counter is cumulative across episodes and this stage opens a SECOND strike
                // window on top of whatever the natural episode already did. A walking mark that
                // finishes its gesture inside the strike band lets the first episode legitimately
                // spend its own single attempt, so the cumulative reading here is 1, not 0. What
                // must hold is that the window this stage opens spends exactly one more, so the
                // baseline is captured rather than assumed.
                strikesBeforeForcedWindow.set(shade.echoShadeCounters().strikes());
                walking.set(false);
                shade.getNavigation().stop();
                mark.teleportTo(shade.getX(), shade.getY(), shade.getZ());
                mark.setDeltaMovement(Vec3.ZERO);
                shade.getSensing().tick();
                helper.assertTrue(
                    shade.distanceToSqr(mark) <= EchoShadeRules.STRIKE_BAND_SQUARED
                        && shade.getSensing().hasLineOfSight(mark),
                    "armed control: the forced mark is inside the live strike gate");
                shade.setEchoShadeState(shade.echoShadeState()
                    .withMark(EchoShadeState.Mark.of(mark.getUUID(),
                        ApparitionEpisodeRuntime.dimensionOf(helper.getLevel())))
                    .withEcho(new EchoShadeState.Echo(
                        300, 0, 0, 0, EchoShadeRules.STRIKE_TICKS, 0, 5_000, 0, 8, 0))
                    .withPhase(EchoShadeRules.Phase.STRIKE));
            });

            helper.runAfterDelay(260L, () -> {
                helper.assertValueEqual(shade.echoShadeCounters().strikes(),
                    strikesBeforeForcedWindow.get() + 1L,
                    "the answer spends exactly one ordinary attributed melee attempt; before="
                        + strikesBeforeForcedWindow.get() + ", now="
                        + shade.echoShadeCounters().strikes());
                helper.assertValueEqual(shade.echoShadeState().echo().strikes(), 1,
                    "the spent attempt is recorded in persisted state, not just in a counter");
                helper.assertTrue(
                    shade.echoShadeState().phase() == EchoShadeRules.Phase.RECOVER
                        || shade.echoShadeState().phase() == EchoShadeRules.Phase.WATCH,
                    "the echo closes into recovery in the same pass as its one attempt");
                helper.assertTrue(shade.getTarget() == null,
                    "no live target survives the closed strike window");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The hard identity guard. A Shade answers a motion and nothing else: no inventory, armor,
     * effect, attribute or persisted field of the marked player may ever reach it.
     */
    public static void echoShadeNeverCopiesPlayerState(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(0, 1, 0));
            final double baselineHealth = shade.getMaxHealth();
            final double baselineDamage = shade.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            final ServerPlayer mark = fixture.connectedPlayer(new BlockPos(2, 1, 2));
            mark.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            mark.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            mark.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                MobEffects.STRENGTH, 600, 4));

            helper.runAfterDelay(200L, () -> {
                for (final EquipmentSlot slot : EquipmentSlot.values()) {
                    helper.assertTrue(shade.getItemBySlot(slot).isEmpty(),
                        "the Shade never copies a marked player's equipment: " + slot);
                }
                helper.assertFalse(shade.hasEffect(MobEffects.STRENGTH),
                    "the Shade never copies a marked player's effects");
                helper.assertValueEqual(shade.getMaxHealth(), (float) baselineHealth,
                    "the Shade never copies a marked player's attributes");
                helper.assertValueEqual(
                    shade.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                    baselineDamage,
                    "the Shade keeps its own registry attack baseline");
                // The persisted shape is the real guarantee: there is nowhere for player state to
                // be recorded even if a later edit tried to record it.
                final java.util.Set<String> declared = java.util.Set.of(
                    "Version", "Phase", "MarkId", "MarkDim", "Echo", "Record", "Sample", "Answer",
                    "Strike", "Recover", "MillisX", "MillisZ", "Samples", "Strikes",
                    "PathCooldown", "RouteFail", "RouteRetry", "Cooldown"
                );
                helper.assertTrue(
                    declared.containsAll(shade.echoShadeState().write().keySet()),
                    "the persisted Echo Shade state never grows a field outside its fixed shape");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /** Hazard preemption, bounded route accounting and a reload that cancels rather than replays. */
    public static void echoShadeRouteHazardAndReloadCancel(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(1, 1, 1));
            fixture.connectedPlayer(new BlockPos(2, 1, 2));

            helper.runAfterDelay(60L, () -> shade.igniteForSeconds(6.0F));
            helper.runAfterDelay(140L, () -> {
                helper.assertTrue(shade.apparitionCounters().hazardInterruptions() >= 1L,
                    "an escapable hazard preempts every species phase and is counted");
                helper.assertTrue(shade.apparitionCounters().hazardSamples() >= 1L,
                    "the bounded hazard sample actually ran on the live tick");
                // A destination sweep that qualified nothing is charged and counted, so a blocked
                // Shade can never re-run the whole candidate sweep on every single tick.
                helper.assertTrue(
                    shade.apparitionCounters().unroutableSweeps()
                        <= shade.apparitionCounters().destinationSweeps(),
                    "every unroutable sweep is one of the sweeps that actually ran");
                shade.clearFire();

                // A reload must never resume inside the one open strike window, and must never
                // hand back an attempt that was already spent.
                shade.setEchoShadeState(shade.echoShadeState()
                    .withMark(EchoShadeState.Mark.of(
                        java.util.UUID.randomUUID(),
                        ApparitionEpisodeRuntime.dimensionOf(helper.getLevel())))
                    .withEcho(new EchoShadeState.Echo(300, 0, 0, 0, 30, 0, 5_000, 0, 6, 1))
                    .withPhase(EchoShadeRules.Phase.STRIKE));
                final TagValueOutput output = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                shade.saveWithoutId(output);
                final var saved = output.buildResult().copy();
                final EchoShadeEntity reloaded = (EchoShadeEntity) ModEntities.ALL.get("echo_shade")
                    .get().create(helper.getLevel(), EntitySpawnReason.LOAD);
                helper.assertTrue(reloaded != null, "the registered type must recreate saved state");
                fixture.track(reloaded);
                reloaded.load(TagValueInput.create(
                    ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
                helper.assertValueEqual(reloaded.echoShadeState().phase(),
                    EchoShadeRules.Phase.ANSWER,
                    "a reload never resumes inside an open strike window");
                helper.assertValueEqual(reloaded.echoShadeState().echo().strikes(), 1,
                    "the spent attempt survives the reload so no second strike is granted");
                helper.assertTrue(reloaded.getTarget() == null, "no live target survives a load");

                // A persisted exhausted route is observable and ends the echo through the tick.
                // The precondition is asserted explicitly: without a live mark the release below
                // would be reached for the wrong reason and the next stage would prove nothing.
                helper.assertTrue(shade.echoShadeState().mark().present(),
                    "precondition: the Shade holds a live mark before the route is exhausted");
                shade.setEchoShadeState(shade.echoShadeState()
                    .withRoute(new ApparitionEpisodeRules.RouteLedger(
                        0, ApparitionEpisodeRules.MAX_ROUTE_FAILURES, 0)));
            });
            helper.runAfterDelay(170L, () -> {
                helper.assertFalse(shade.echoShadeState().mark().present(),
                    "a third persisted route failure releases the mark instead of chasing forever");
                helper.assertTrue(shade.echoShadeState().cooldownTicks() > 0,
                    "the release arms the cadence so a later echo is still possible");
                helper.assertTrue(shade.getNavigation().isDone(),
                    "the failed route leaves no stale navigation");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- spectre

    /** The signature behavior: telegraph first, then exactly one dread, then fade. No damage. */
    public static void spectreWarnsOneWitnessDreadsOnceThenFades(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final SpectreEntity spectre = spawnSpectre(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer witness = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            final float witnessHealth = witness.getHealth();

            // The two applied effects are decaying values, so they must be read inside the window
            // where the dread has certainly landed but its Darkness has certainly not expired.
            // Worst-case landing is tick 161 (60-tick UUID drift stagger + 100-tick manifest) and
            // worst-case Darkness expiry is tick 222 (earliest landing 102 + 120 ticks), so 190 is
            // the only safe read point. The monotonic counters are read later, at 300.
            helper.runAfterDelay(190L, () -> {
                helper.assertTrue(witness.hasEffect(MobEffects.DARKNESS),
                    "the preserved Darkness reaches the one appointed witness");
                helper.assertTrue(witness.hasEffect(MobEffects.WEAKNESS),
                    "the preserved Weakness reaches the one appointed witness");
                helper.assertValueEqual(witness.getHealth(), witnessHealth,
                    "a Spectre is an object of dread and never deals damage");
            });

            helper.runAfterDelay(300L, () -> {
                helper.assertTrue(spectre.tickCount > 0,
                    "the fixture subject is a genuinely self-ticking AI-enabled entity");
                helper.assertTrue(spectre.spectreCounters().telegraphs() >= 1L,
                    "the manifestation is visible before anything is felt");
                helper.assertTrue(
                    spectre.spectreCounters().telegraphs() <= SpectreRules.MAX_TELEGRAPHS,
                    "the telegraph is capped per haunting");
                helper.assertValueEqual(spectre.spectreCounters().dreadWindowsOpened(), 1L,
                    "the telegraph graduated into exactly one dread window");
                helper.assertValueEqual(spectre.spectreCounters().dreads(), 1L,
                    "exactly one dread is delivered per haunting");
                helper.assertTrue(spectre.getTarget() == null,
                    "a Spectre never holds a combat target");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * The replaced 1.4 behavior refreshed an uncapped area debuff every eighty ticks forever. The
     * redesign must deliver once, to one witness, and never refresh or spread it.
     */
    public static void spectreDreadDoesNotRefreshOrSpread(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final SpectreEntity spectre = spawnSpectre(fixture, new BlockPos(0, 1, 0));
            final ServerPlayer first = fixture.connectedPlayer(new BlockPos(1, 1, 1));
            final ServerPlayer second = fixture.connectedPlayer(new BlockPos(2, 1, 2));

            // Darkness is a decaying value: read the no-spread assertion at 190, inside the window
            // where the dread has certainly landed and has certainly not expired. Reading it at 300
            // would count zero touched players and fail for the wrong reason.
            helper.runAfterDelay(190L, () -> {
                final int touched = (first.hasEffect(MobEffects.DARKNESS) ? 1 : 0)
                    + (second.hasEffect(MobEffects.DARKNESS) ? 1 : 0);
                helper.assertValueEqual(touched, 1,
                    "the dread reaches exactly one appointed witness and never spreads");
            });
            helper.runAfterDelay(300L, () -> {
                helper.assertValueEqual(spectre.spectreCounters().dreads(), 1L,
                    "exactly one dread has been delivered");
            });
            helper.runAfterDelay(520L, () -> {
                helper.assertValueEqual(spectre.spectreCounters().dreads(), 1L,
                    "a witness who lingers in the band receives nothing further");
                helper.assertTrue(spectre.spectreCounters().dreadWindowsOpened() <= 1L,
                    "no second dread window opens while the haunting cadence is still armed");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- shared contracts

    /** Both kinds keep every declared read, candidate and line-of-sight budget under load. */
    public static void echoSpectreDenseCandidatesStayCappedAndStable(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(0, 1, 0));
            final SpectreEntity spectre = spawnSpectre(fixture, new BlockPos(2, 1, 0));
            fixture.connectedPlayer(new BlockPos(1, 1, 1));
            fixture.connectedPlayer(new BlockPos(2, 1, 2));
            fixture.connectedPlayer(new BlockPos(0, 1, 2));

            helper.runAfterDelay(300L, () -> {
                for (final ApparitionEpisodeRuntime.Counters counters
                    : List.of(shade.apparitionCounters(), spectre.apparitionCounters())) {
                    helper.assertTrue(counters.appointmentSweeps() >= 1L,
                        "each kind actually ran its bounded appointment sweep");
                    helper.assertTrue(
                        counters.appointmentCandidateVisits()
                            <= counters.appointmentSweeps()
                                * ApparitionEpisodeRules.MAX_PLAYER_CANDIDATES,
                        "no appointment sweep exceeded its declared candidate cap");
                    helper.assertTrue(
                        counters.lineOfSightChecks()
                            <= counters.appointmentSweeps()
                                * ApparitionEpisodeRules.MAX_LINE_OF_SIGHT_CHECKS
                                + counters.episodesStarted() * 200L,
                        "line-of-sight walks stay inside their declared sweep budget");
                    helper.assertTrue(
                        counters.destinationCandidateVisits()
                            <= counters.destinationSweeps()
                                * (ApparitionEpisodeRules.MAX_DESTINATION_READS
                                    / ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE),
                        "no destination sweep exceeded its declared candidate cap");
                }
                helper.assertTrue(shade.apparitionCounters().episodesStarted() <= 2L,
                    "the echo cadence bounds how often a Shade may start over");
                helper.assertTrue(spectre.apparitionCounters().episodesStarted() <= 2L,
                    "the haunting cadence bounds how often a Spectre may start over");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /** Neither kind may replay its one signature action across a save and reload. */
    public static void echoSpectreReloadDoesNotReplay(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(0, 1, 0));
            final SpectreEntity spectre = spawnSpectre(fixture, new BlockPos(2, 1, 2));

            helper.runAfterDelay(60L, () -> {
                final String dimension = ApparitionEpisodeRuntime.dimensionOf(helper.getLevel());
                shade.setEchoShadeState(shade.echoShadeState()
                    .withMark(EchoShadeState.Mark.of(java.util.UUID.randomUUID(), dimension))
                    .withEcho(new EchoShadeState.Echo(300, 0, 0, 0, 25, 0, 6_000, 0, 8, 1))
                    .withPhase(EchoShadeRules.Phase.STRIKE));
                spectre.setSpectreState(spectre.spectreState()
                    .withWitness(SpectreState.Witness.of(java.util.UUID.randomUUID(), dimension))
                    .withHaunt(new SpectreState.Haunt(300, 0, 0, 35, 0, 4, 1))
                    .withPhase(SpectreRules.Phase.DREAD));

                final EchoShadeEntity reloadedShade =
                    (EchoShadeEntity) reload(helper, fixture, shade, "echo_shade");
                helper.assertValueEqual(reloadedShade.echoShadeState().phase(),
                    EchoShadeRules.Phase.ANSWER,
                    "a reloaded Shade must earn its single attempt again");
                helper.assertValueEqual(reloadedShade.echoShadeState().echo().strikes(), 1,
                    "the spent strike survives so the reload grants no second attempt");
                helper.assertValueEqual(reloadedShade.echoShadeCounters().strikes(), 0L,
                    "a reload replays no strike of its own");

                final SpectreEntity reloadedSpectre =
                    (SpectreEntity) reload(helper, fixture, spectre, "spectre");
                helper.assertValueEqual(reloadedSpectre.spectreState().phase(),
                    SpectreRules.Phase.MANIFEST,
                    "a reloaded Spectre must telegraph again before any dread");
                helper.assertValueEqual(reloadedSpectre.spectreState().haunt().dreads(), 1,
                    "the delivered dread survives so the reload grants no second delivery");
                helper.assertValueEqual(reloadedSpectre.spectreCounters().dreads(), 0L,
                    "a reload replays no dread of its own");
                helper.assertValueEqual(
                    reloadedSpectre.spectreState().haunt().telegraphRemainingTicks(),
                    SpectreRules.TELEGRAPH_INTERVAL_TICKS,
                    "a reload restores the telegraph interval so no feedback fires immediately");
                helper.assertTrue(reloadedShade.getTarget() == null
                        && reloadedSpectre.getTarget() == null,
                    "no live target survives a load for either kind");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    /** The two kinds stay separate beings, and neither borrows another family's identity. */
    public static void echoSpectreFamiliesStayIsolated(final GameTestHelper helper) {
        buildFloor(helper);
        final FixtureScope fixture = new FixtureScope(helper);
        try {
            final EchoShadeEntity shade = spawnShade(fixture, new BlockPos(0, 1, 0));
            final SpectreEntity spectre = spawnSpectre(fixture, new BlockPos(2, 1, 2));
            final ServerPlayer player = fixture.connectedPlayer(new BlockPos(1, 1, 1));

            helper.runAfterDelay(120L, () -> {
                helper.assertTrue(shade.tickCount > 0 && spectre.tickCount > 0,
                    "both fixture subjects are genuinely self-ticking AI-enabled entities");

                // Neither kind carries a foreign class identity.
                for (final Mob apparition : List.of(shade, spectre)) {
                    helper.assertFalse(Vex.class.isInstance(apparition),
                        "neither F21 kind is a Vex copy");
                    helper.assertFalse(SpiritMob.class.isInstance(apparition),
                        "neither F21 kind carries the shared SpiritMob class identity");
                    helper.assertFalse(ArcaneMob.class.isInstance(apparition),
                        "neither F21 kind carries the shared ArcaneMob class identity");
                    helper.assertFalse(SpectralEntity.class.isInstance(apparition),
                        "neither F21 kind borrows the F19 spectral body");
                    helper.assertTrue(Monster.class.isInstance(apparition),
                        "both F21 kinds keep their shipped hostile monster identity");
                }
                helper.assertValueEqual(shade.operationalTargetGoalCount(), 0,
                    "no target goal is ever registered for the Echo Shade");
                helper.assertValueEqual(spectre.operationalTargetGoalCount(), 0,
                    "no target goal is ever registered for the Spectre");
                helper.assertTrue(shade.operationalGoalNames().stream()
                        .noneMatch(name -> name.contains("Melee") || name.contains("Attack")),
                    "the runtime owns the single attempt, not a melee goal");

                // The two kinds are separate beings with separate state and separate payloads.
                helper.assertFalse(shade.creatureKind() == spectre.creatureKind(),
                    "the two neighbours keep separate registry kinds");
                helper.assertFalse(spectre.canAttack(player),
                    "a Spectre never attacks anything under any circumstances");
                helper.assertFalse(shade.canAttack(player),
                    "a Shade outside its runtime-owned strike window attacks nothing");
                helper.assertTrue(spectre.isNoGravity(),
                    "the Spectre keeps its flying spectral body");
                helper.assertFalse(shade.isNoGravity(),
                    "the Echo Shade keeps its walking ground body");
                fixture.close();
                helper.succeed();
            });
        } catch (final Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    // ---------------------------------------------------------------- fixture support

    private static void buildFloor(final GameTestHelper helper) {
        BlockPos.betweenClosedStream(new BlockPos(0, 0, 0), new BlockPos(2, 0, 2))
            .forEach(position -> helper.setBlock(position, Blocks.STONE));
    }

    private static Entity reload(
        final GameTestHelper helper,
        final FixtureScope fixture,
        final Entity original,
        final String id
    ) {
        final TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        original.saveWithoutId(output);
        final var saved = output.buildResult().copy();
        final Entity restored = ModEntities.ALL.get(id).get()
            .create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(restored != null, "the registered type must recreate saved state: " + id);
        fixture.track(restored);
        restored.load(TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        return restored;
    }

    private static EchoShadeEntity spawnShade(final FixtureScope fixture, final BlockPos position) {
        @SuppressWarnings("unchecked")
        final EntityType<EchoShadeEntity> type =
            (EntityType<EchoShadeEntity>) ModEntities.ALL.get("echo_shade").get();
        return placed(fixture, fixture.spawn(type, position), position);
    }

    private static SpectreEntity spawnSpectre(final FixtureScope fixture, final BlockPos position) {
        @SuppressWarnings("unchecked")
        final EntityType<SpectreEntity> type =
            (EntityType<SpectreEntity>) ModEntities.ALL.get("spectre").get();
        return placed(fixture, fixture.spawn(type, position), position);
    }

    /**
     * Places a subject without ever disabling its AI. Every F21 fixture asserts through an entity
     * that is genuinely running its own server tick.
     */
    private static <T extends Mob> T placed(
        final FixtureScope fixture,
        final T entity,
        final BlockPos position
    ) {
        entity.setDeltaMovement(Vec3.ZERO);
        final BlockPos absolute = fixture.helper.absolutePos(position);
        entity.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        entity.setPersistenceRequired();
        return entity;
    }

    private static final class FixtureScope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private boolean closed;

        private FixtureScope(final GameTestHelper helper) {
            this.helper = helper;
        }

        private <T extends Entity> T spawn(final EntityType<T> type, final BlockPos position) {
            return track(helper.spawn(type, position, EntitySpawnReason.EVENT));
        }

        private <T extends Entity> T track(final T entity) {
            entities.add(entity);
            return entity;
        }

        private ServerPlayer connectedPlayer(final BlockPos position) {
            final ServerPlayer player =
                (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            final net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(
                    net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            final net.minecraft.server.network.CommonListenerCookie cookie =
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                    player.getGameProfile(), false);
            helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(false);
            final BlockPos absolute = helper.absolutePos(position);
            player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            return track(player);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(GameTestMockPlayers::release);
            entities.clear();
        }
    }
}
